package com.example.guardian.service;

import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceState;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.repository.MonitoredServiceRepository;
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
                          HostShellExecutor hostShellExecutor) {
        this.monitoredServiceRepository = monitoredServiceRepository;
        this.processInspector = processInspector;
        this.healthChecker = healthChecker;
        this.hostShellExecutor = hostShellExecutor;
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
     * <li>если сервис неисправен, проверить ограничения на рестарт;</li>
     * <li>если рестарт разрешен, выполнить restart-команду.</li>
     * </ol>
     *
     * @param service конфигурация конкретного сервиса
     * @param allowRestart {@code true}, если текущая проверка может инициировать рестарт
     */
    private void checkOne(MonitoredService service, boolean allowRestart) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        state.setLastCheckAt(Instant.now());

        boolean processRunning = processInspector.isRunning(service.getHost(), service.getProcessMatch());
        boolean healthy = true;
        if (service.getHealthUrl() != null && !service.getHealthUrl().isBlank()) {
            healthy = healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout());
        }

        state.setProcessRunning(processRunning);
        state.setHealthCheckPassed(healthy);

        if (processRunning && healthy) {
            log.debug("Service {} is OK", service.getName());
            state.setStatus(ServiceHealthStatus.UP);
            state.setLastMessage("Service is healthy");
            return;
        }

        log.warn("Service {} is unhealthy. processRunning={}, healthy={}",
                service.getName(), processRunning, healthy);
        state.setStatus(ServiceHealthStatus.DOWN);
        state.setLastMessage(buildUnhealthyMessage(processRunning, healthy));

        if (!allowRestart) {
            return;
        }

        if (!canRestart(service)) {
            log.error("Restart denied by cooldown/window policy for service {}", service.getName());
            state.setLastMessage(state.getLastMessage() + ". Restart blocked by policy");
            return;
        }

        restart(service);
    }

    /**
     * Определяет, можно ли сейчас выполнять рестарт сервиса.
     *
     * <p>Метод проверяет два ограничения:
     *
     * <ul>
     * <li>прошел ли cooldown после последнего рестарта;</li>
     * <li>не превышен ли лимит рестартов внутри временного окна.</li>
     * </ul>
     *
     * <p>Устаревшие записи истории, которые уже не попадают в окно, автоматически
     * удаляются перед вычислением текущего лимита.
     *
     * @param service конфигурация сервиса
     * @return {@code true}, если рестарт разрешен; иначе {@code false}
     */
    private boolean canRestart(MonitoredService service) {
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

        CommandExecutor.CommandResult result = hostShellExecutor.execute(
                service.getHost(),
                service.getRestartCommand(),
                Duration.ofSeconds(20)
        );

        if (result.success()) {
            ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
            Instant now = Instant.now();
            state.setLastRestartAt(now);
            state.getRestartHistory().addLast(now);
            state.setStatus(ServiceHealthStatus.RESTARTING);
            state.setLastMessage("Restart command executed successfully");

            log.info("Restart command executed for service {}. Output: {}",
                    service.getName(), result.output());
        } else {
            ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
            state.setStatus(ServiceHealthStatus.ERROR);
            state.setLastMessage("Restart failed: " + firstNonBlank(result.error(), result.output(), "Unknown error"));
            log.error("Restart failed for service {}. exitCode={}, error={}, output={}",
                    service.getName(), result.exitCode(), result.error(), result.output());
        }
    }

    public void restartNow(Long serviceId) {
        MonitoredService service = monitoredServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Service not found: " + serviceId));
        restart(service);
    }

    public Optional<ServiceRuntimeSnapshot> getRuntimeSnapshot(Long serviceId) {
        ServiceState state = states.get(serviceId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(state));
    }

    public Map<Long, ServiceRuntimeSnapshot> getRuntimeSnapshots() {
        Map<Long, ServiceRuntimeSnapshot> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Long, ServiceState> entry : states.entrySet()) {
            snapshot.put(entry.getKey(), toSnapshot(entry.getValue()));
        }
        return snapshot;
    }

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

    private void markPaused(MonitoredService service) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        state.setLastCheckAt(Instant.now());
        state.setStatus(ServiceHealthStatus.PAUSED);
        state.setProcessRunning(false);
        state.setHealthCheckPassed(false);
        state.setLastMessage("Monitoring is paused");
    }

    private void markError(MonitoredService service, String message) {
        ServiceState state = states.computeIfAbsent(service.getId(), k -> new ServiceState());
        state.setLastCheckAt(Instant.now());
        state.setStatus(ServiceHealthStatus.ERROR);
        state.setProcessRunning(false);
        state.setHealthCheckPassed(false);
        state.setLastMessage(message);
    }

    private ServiceRuntimeSnapshot toSnapshot(ServiceState state) {
        return new ServiceRuntimeSnapshot(
                state.getStatus(),
                state.isProcessRunning(),
                state.isHealthCheckPassed(),
                state.getLastMessage(),
                state.getLastCheckAt(),
                state.getLastRestartAt()
        );
    }

    private String buildUnhealthyMessage(boolean processRunning, boolean healthy) {
        if (!processRunning && !healthy) {
            return "Process is missing and health-check failed";
        }
        if (!processRunning) {
            return "Process is missing";
        }
        return "Health-check failed";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
