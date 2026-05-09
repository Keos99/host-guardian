package com.example.guardian.service;

import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceState;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.repository.MonitoredServiceRepository;
import com.example.guardian.config.MonitorProperties;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Центральный сервис мониторинга, который проверяет состояние приложений
 * и принимает решение о необходимости их рестарта.
 *
 * <p>Класс объединяет несколько источников информации:
 *
 * <ul>
 * <li>конфигурацию сервисов из базы данных;</li>
 * <li>проверку наличия процесса через {@link LinuxProcessInspector};</li>
 * <li>опциональную HTTP-проверку через {@link HttpHealthChecker};</li>
 * <li>выполнение restart-команд через {@link HostShellExecutor}.</li>
 * </ul>
 *
 * <p>Кроме самой проверки, сервис отвечает за защиту от слишком частых рестартов:
 * хранит runtime-состояние по каждому сервису, контролирует cooldown и ограничивает
 * число рестартов в заданном временном окне.
 */
@Service
public class ServiceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServiceMonitor.class);

    private final MonitoredServiceRepository monitoredServiceRepository;
    private final LinuxProcessInspector processInspector;
    private final HttpHealthChecker healthChecker;
    private final HostShellExecutor hostShellExecutor;
    private final MonitorProperties monitorProperties;

    private final Map<Long, ServiceState> states = new ConcurrentHashMap<>();

    /**
     * Создает сервис мониторинга.
     *
     * @param monitoredServiceRepository репозиторий конфигураций отслеживаемых сервисов
     * @param processInspector компонент для поиска процессов на хосте
     * @param healthChecker компонент для HTTP health-check
     * @param hostShellExecutor компонент для выполнения shell-команд на нужном хосте
     */
    public ServiceMonitor(MonitoredServiceRepository monitoredServiceRepository,
                          LinuxProcessInspector processInspector,
                          HttpHealthChecker healthChecker,
                          HostShellExecutor hostShellExecutor,
                          MonitorProperties monitorProperties) {
        this.monitoredServiceRepository = monitoredServiceRepository;
        this.processInspector = processInspector;
        this.healthChecker = healthChecker;
        this.hostShellExecutor = hostShellExecutor;
        this.monitorProperties = monitorProperties;
    }

    /**
     * Выполняет один полный цикл проверки по всем сервисам из конфигурации.
     *
     * <p>Ошибки обработки одного сервиса не должны останавливать мониторинг других,
     * поэтому каждая проверка изолирована в собственном {@code try/catch}.
     */
    public void checkAll() {
        for (MonitoredService service : monitoredServiceRepository.findAllByOrderByNameAsc()) {
            try {
                if (!service.isMonitoringEnabled()) {
                    markPaused(service);
                    continue;
                }
                checkOne(service, true);
            } catch (Exception e) {
                log.error("Unexpected error while checking service {}", service.getName(), e);
                markError(service, "Unexpected monitoring error: " + e.getMessage());
            }
        }
    }

    /**
     * Проверяет один сервис и при необходимости инициирует его рестарт.
     *
     * <p>Алгоритм работы такой:
     *
     * <ol>
     * <li>проверить наличие процесса;</li>
     * <li>если настроен health endpoint, дополнительно проверить его;</li>
     * <li>если сервис выглядит исправным, завершить обработку;</li>
     * <li>если сервис неисправен и процесс не найден, проверить ограничения recovery;</li>
     * <li>если recovery разрешен, выполнить только start-команду.</li>
     * </ol>
     *
     * @param service конфигурация конкретного сервиса
     * @param allowRecoveryStart {@code true}, если текущая проверка может запустить отсутствующий сервис
     */
    private void checkOne(MonitoredService service, boolean allowRecoveryStart) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        ServiceCheckResult checkResult = inspectService(service, state);

        if (checkResult.serviceHealthy()) {
            log.debug("Service {} is OK", service.getName());
            markHealthy(state, "Service is healthy");
            return;
        }

        log.warn("Service {} is unhealthy. processRunning={}, healthy={}",
                service.getName(), checkResult.processRunning(), checkResult.healthCheckEnabled()
                        ? checkResult.healthCheckPassed()
                        : null);
        markUnhealthy(state, checkResult);

        if (!allowRecoveryStart) {
            return;
        }

        if (checkResult.processRunning()) {
            return;
        }

        if (!canRunRecoveryAction(service)) {
            log.error("Start denied by cooldown/window policy for service {}", service.getName());
            state.setLastMessage(state.getLastMessage() + ". Start blocked by policy");
            return;
        }

        start(service);
    }

    /**
     * Определяет, можно ли сейчас выполнять recovery-действие для сервиса.
     *
     * <p>Метод проверяет два ограничения:
     *
     * <ul>
     * <li>прошел ли cooldown после последнего recovery-действия;</li>
     * <li>не превышен ли лимит recovery-действий внутри временного окна.</li>
     * </ul>
     *
     * <p>Устаревшие записи истории, которые уже не попадают в окно, автоматически
     * удаляются перед вычислением текущего лимита.
     *
     * @param service конфигурация сервиса
     * @return {@code true}, если recovery-действие разрешено; иначе {@code false}
     */
    private boolean canRunRecoveryAction(MonitoredService service) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        Instant now = Instant.now();

        if (state.getLastRestartAt() != null) {
            Duration sinceLast = Duration.between(state.getLastRestartAt(), now);
            if (sinceLast.compareTo(service.getRestartCooldown()) < 0) {
                return false;
            }
        }

        Instant windowStart = now.minus(service.getRestartWindow());
        while (!state.getRestartHistory().isEmpty()
                && state.getRestartHistory().peekFirst().isBefore(windowStart)) {
            state.getRestartHistory().pollFirst();
        }

        return state.getRestartHistory().size() < service.getMaxRestartsInWindow();
    }

    /**
     * Выполняет restart-команду сервиса и обновляет его runtime-состояние при успехе.
     *
     * <p>После успешного выполнения команда считается завершенной, а в памяти
     * фиксируются время последнего рестарта и новая запись в истории рестартов.
     * Если команда завершается с ошибкой, состояние не обновляется, а подробности
     * записываются в лог.
     *
     * @param service конфигурация сервиса, который нужно перезапустить
     */
    private void restart(MonitoredService service) {
        log.warn("Restarting service {}", service.getName());

        CommandExecutor.CommandResult restartResult = service.isManualRestartEnabled()
                ? executeServiceCommand(service, service.getRestartCommand(), monitorProperties.getCommand().getRestartTimeout())
                : processInspector.stop(
                        service.getHost(),
                        service.getProcessMatch(),
                        service.getLastKnownPid(),
                        monitorProperties.getCommand().getStopTimeout()
                );

        if (!restartResult.success()) {
            ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
            state.setStatus(ServiceHealthStatus.ERROR);
            state.setLastMessage("Restart failed: " + firstNonBlank(restartResult.error(), restartResult.output(), "Unknown error"));
            log.error("Restart failed for service {}. exitCode={}, error={}, output={}",
                    service.getName(), restartResult.exitCode(), restartResult.error(), restartResult.output());
            return;
        }

        CommandExecutor.CommandResult startResult = executeServiceCommand(
                service,
                service.getStartCommand(),
                monitorProperties.getCommand().getStartTimeout()
        );

        if (startResult.success()) {
            ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
            recordSuccessfulServiceAction(state, "Restart and start commands executed successfully");

            log.info("Restart and start commands executed for service {}. Restart output: {}. Start output: {}",
                    service.getName(), restartResult.output(), startResult.output());
        } else {
            ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
            state.setStatus(ServiceHealthStatus.ERROR);
            state.setLastMessage("Start failed: " + firstNonBlank(startResult.error(), startResult.output(), "Unknown error"));
            log.error("Start failed for service {}. exitCode={}, error={}, output={}",
                    service.getName(), startResult.exitCode(), startResult.error(), startResult.output());
        }
    }

    private void start(MonitoredService service) {
        log.warn("Starting service {}", service.getName());

        CommandExecutor.CommandResult startResult = executeServiceCommand(
                service,
                service.getStartCommand(),
                monitorProperties.getCommand().getStartTimeout()
        );

        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        if (startResult.success()) {
            recordSuccessfulServiceAction(state, "Start command executed successfully");
            log.info("Start command executed for service {}. Start output: {}",
                    service.getName(), startResult.output());
        } else {
            state.setStatus(ServiceHealthStatus.ERROR);
            state.setLastMessage("Start failed: " + firstNonBlank(startResult.error(), startResult.output(), "Unknown error"));
            log.error("Start failed for service {}. exitCode={}, error={}, output={}",
                    service.getName(), startResult.exitCode(), startResult.error(), startResult.output());
        }
    }

    /**
     * Checks the current service state and executes the operator-requested recovery action.
     *
     * <p>If the process is missing, only the start command is executed. The restart
     * flow is executed only when an HTTP health-check is configured and fails.
     *
     * @param serviceId identifier of the service to restart
     */
    public void restartNow(Long serviceId) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Service not found: " + serviceId));
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        ServiceCheckResult checkResult = inspectService(service, state);

        if (!checkResult.processRunning()) {
            markUnhealthy(state, checkResult);
            start(service);
            return;
        }

        if (!checkResult.healthCheckEnabled()) {
            markHealthy(state, "Restart skipped: health-check is not configured");
            return;
        }

        if (checkResult.healthCheckPassed()) {
            markHealthy(state, "Restart skipped: health-check is healthy");
            return;
        }

        markUnhealthy(state, checkResult);
        restart(service);
    }

    /**
     * Returns the latest runtime snapshot for one service if it has been checked.
     *
     * @param serviceId service identifier
     * @return optional immutable snapshot of the in-memory state
     */
    public Optional<ServiceRuntimeSnapshot> getRuntimeSnapshot(Long serviceId) {
        ServiceState state = states.get(serviceId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(state));
    }

    /**
     * Returns runtime snapshots for all services known to the monitor.
     *
     * @return map keyed by monitored service identifier
     */
    public Map<Long, ServiceRuntimeSnapshot> getRuntimeSnapshots() {
        Map<Long, ServiceRuntimeSnapshot> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Long, ServiceState> entry : states.entrySet()) {
            snapshot.put(entry.getKey(), toSnapshot(entry.getValue()));
        }
        return snapshot;
    }

    /**
     * Runs an immediate monitoring check for one service.
     *
     * @param serviceId identifier of the service to check
     */
    public void refreshSingle(Long serviceId) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Service not found: " + serviceId));
        if (!service.isMonitoringEnabled()) {
            markPaused(service);
            return;
        }
        checkOne(service, true);
    }

    /**
     * Marks a service as paused in the runtime state map.
     *
     * @param service service whose monitoring is disabled
     */
    private void markPaused(MonitoredService service) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        state.setLastCheckAt(Instant.now());
        state.setStatus(ServiceHealthStatus.PAUSED);
        state.setProcessRunning(false);
        state.setLastKnownPid(service.getLastKnownPid());
        state.setHealthCheckEnabled(hasHealthCheck(service));
        state.setHealthCheckPassed(false);
        state.setLastMessage("Monitoring is paused");
    }

    /**
     * Marks a service as failed because the monitor itself hit an error.
     *
     * @param service service whose check failed
     * @param message human-readable failure message
     */
    private void markError(MonitoredService service, String message) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        state.setLastCheckAt(Instant.now());
        state.setStatus(ServiceHealthStatus.ERROR);
        state.setProcessRunning(false);
        state.setLastKnownPid(service.getLastKnownPid());
        state.setHealthCheckEnabled(hasHealthCheck(service));
        state.setHealthCheckPassed(false);
        state.setLastMessage(message);
    }

    /**
     * Converts mutable runtime state into an immutable API snapshot.
     *
     * @param state mutable in-memory service state
     * @return immutable snapshot for API consumers
     */
    private ServiceRuntimeSnapshot toSnapshot(ServiceState state) {
        return new ServiceRuntimeSnapshot(
                state.getStatus(),
                state.isProcessRunning(),
                state.getLastKnownPid(),
                state.isHealthCheckEnabled(),
                state.isHealthCheckPassed(),
                state.getLastMessage(),
                state.getLastCheckAt(),
                state.getLastRestartAt()
        );
    }

    /**
     * Builds a concise status message for an unhealthy service.
     *
     * @param processRunning whether the process lookup succeeded
     * @param healthy whether the optional HTTP health-check succeeded
     * @return operator-facing explanation of the failed checks
     */
    private String buildUnhealthyMessage(boolean processRunning, boolean healthCheckEnabled, boolean healthy) {
        if (!processRunning && healthCheckEnabled && !healthy) {
            return "Process is missing and health-check failed";
        }
        if (!processRunning) {
            return "Process is missing";
        }
        return "Health-check failed";
    }

    private ServiceCheckResult inspectService(MonitoredService service, ServiceState state) {
        state.setLastCheckAt(Instant.now());

        Optional<LinuxProcessInspector.ProcessInfo> foundProcess = processInspector.findFirst(
                service.getHost(),
                service.getProcessMatch()
        );
        Long pid = resolveAndPersistPid(service, foundProcess);
        boolean pidRunning = pid != null && processInspector.isPidRunning(service.getHost(), pid);
        boolean processRunning = foundProcess.isPresent() && pidRunning;
        boolean healthCheckEnabled = hasHealthCheck(service);
        boolean healthy = false;
        if (healthCheckEnabled) {
            healthy = healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout());
        }

        state.setProcessRunning(processRunning);
        state.setLastKnownPid(pid);
        state.setHealthCheckEnabled(healthCheckEnabled);
        state.setHealthCheckPassed(healthCheckEnabled && healthy);

        return new ServiceCheckResult(processRunning, healthCheckEnabled, healthCheckEnabled && healthy);
    }

    private void markHealthy(ServiceState state, String message) {
        state.setStatus(ServiceHealthStatus.UP);
        state.setLastMessage(message);
    }

    private void markUnhealthy(ServiceState state, ServiceCheckResult checkResult) {
        state.setStatus(ServiceHealthStatus.DOWN);
        state.setLastMessage(buildUnhealthyMessage(
                checkResult.processRunning(),
                checkResult.healthCheckEnabled(),
                checkResult.healthCheckPassed()
        ));
    }

    private void recordSuccessfulServiceAction(ServiceState state, String message) {
        Instant now = Instant.now();
        state.setLastRestartAt(now);
        state.getRestartHistory().addLast(now);
        state.setStatus(ServiceHealthStatus.RESTARTING);
        state.setLastMessage(message);
    }

    private Long resolveAndPersistPid(MonitoredService service,
                                      Optional<LinuxProcessInspector.ProcessInfo> foundProcess) {
        Long currentPid = service.getLastKnownPid();
        if (foundProcess.isEmpty()) {
            return currentPid;
        }

        Long foundPid = foundProcess.orElseThrow().pid();
        if (!foundPid.equals(currentPid)) {
            service.setLastKnownPid(foundPid);
            monitoredServiceRepository.save(service);
        }
        return foundPid;
    }

    private boolean hasHealthCheck(MonitoredService service) {
        return service.getHealthUrl() != null && !service.getHealthUrl().isBlank();
    }

    private CommandExecutor.CommandResult executeServiceCommand(MonitoredService service,
                                                                String command,
                                                                Duration timeout) {
        return hostShellExecutor.execute(service.getHost(), withExecutionPath(service, command), timeout);
    }

    private String withExecutionPath(MonitoredService service, String command) {
        if (service.getExecutionPath() == null || service.getExecutionPath().isBlank()) {
            return command;
        }
        return "cd " + shellQuote(service.getExecutionPath()) + " && " + command;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Returns the first non-blank value from a candidate list.
     *
     * @param values candidate values in priority order
     * @return first non-blank value, or {@code null} when all values are blank
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ServiceCheckResult(boolean processRunning,
                                      boolean healthCheckEnabled,
                                      boolean healthCheckPassed) {

        private boolean serviceHealthy() {
            return processRunning && (!healthCheckEnabled || healthCheckPassed);
        }
    }
}
