package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.ServiceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Центральный сервис мониторинга, который проверяет состояние приложений
 * и принимает решение о необходимости их рестарта.
 *
 * <p>Класс объединяет несколько источников информации:
 *
 * <ul>
 * <li>конфигурацию сервисов из {@link MonitorProperties};</li>
 * <li>проверку наличия процесса через {@link LinuxProcessInspector};</li>
 * <li>опциональную HTTP-проверку через {@link HttpHealthChecker};</li>
 * <li>выполнение restart-команд через {@link CommandExecutor}.</li>
 * </ul>
 *
 * <p>Кроме самой проверки, сервис отвечает за защиту от слишком частых рестартов:
 * хранит runtime-состояние по каждому сервису, контролирует cooldown и ограничивает
 * число рестартов в заданном временном окне.
 */
@Service
public class ServiceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServiceMonitor.class);

    private final MonitorProperties properties;
    private final LinuxProcessInspector processInspector;
    private final HttpHealthChecker healthChecker;
    private final CommandExecutor commandExecutor;

    private final Map<String, ServiceState> states = new ConcurrentHashMap<>();

    /**
     * Создает сервис мониторинга.
     *
     * @param properties конфигурация мониторинга и список отслеживаемых сервисов
     * @param processInspector компонент для поиска процессов на хосте
     * @param healthChecker компонент для HTTP health-check
     * @param commandExecutor компонент для выполнения restart-команд
     */
    public ServiceMonitor(MonitorProperties properties,
                          LinuxProcessInspector processInspector,
                          HttpHealthChecker healthChecker,
                          CommandExecutor commandExecutor) {
        this.properties = properties;
        this.processInspector = processInspector;
        this.healthChecker = healthChecker;
        this.commandExecutor = commandExecutor;
    }

    /**
     * Выполняет один полный цикл проверки по всем сервисам из конфигурации.
     *
     * <p>Ошибки обработки одного сервиса не должны останавливать мониторинг других,
     * поэтому каждая проверка изолирована в собственном {@code try/catch}.
     */
    public void checkAll() {
        for (MonitorProperties.MonitoredService service : properties.getServices()) {
            try {
                checkOne(service);
            } catch (Exception e) {
                log.error("Unexpected error while checking service {}", service.getName(), e);
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
     */
    private void checkOne(MonitorProperties.MonitoredService service) {
        boolean processRunning = processInspector.isRunning(service.getProcessMatch());

        boolean healthy = true;
        if (service.getHealthUrl() != null && !service.getHealthUrl().isBlank()) {
            healthy = healthChecker.isHealthy(service.getHealthUrl(), service.getHealthTimeout());
        }

        if (processRunning && healthy) {
            log.debug("Service {} is OK", service.getName());
            return;
        }

        log.warn("Service {} is unhealthy. processRunning={}, healthy={}",
                service.getName(), processRunning, healthy);

        if (!canRestart(service)) {
            log.error("Restart denied by cooldown/window policy for service {}", service.getName());
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
    private boolean canRestart(MonitorProperties.MonitoredService service) {
        ServiceState state = states.computeIfAbsent(service.getName(), k -> new ServiceState());
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
    private void restart(MonitorProperties.MonitoredService service) {
        log.warn("Restarting service {}", service.getName());

        CommandExecutor.CommandResult result = commandExecutor.execute(
                java.util.List.of("bash", "-lc", service.getRestartCommand()),
                Duration.ofSeconds(20)
        );

        if (result.success()) {
            ServiceState state = states.computeIfAbsent(service.getName(), k -> new ServiceState());
            Instant now = Instant.now();
            state.setLastRestartAt(now);
            state.getRestartHistory().addLast(now);

            log.info("Restart command executed for service {}. Output: {}",
                    service.getName(), result.output());
        } else {
            log.error("Restart failed for service {}. exitCode={}, error={}, output={}",
                    service.getName(), result.exitCode(), result.error(), result.output());
        }
    }
}
