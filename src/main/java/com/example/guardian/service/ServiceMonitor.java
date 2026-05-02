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

@Service
public class ServiceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServiceMonitor.class);

    private final MonitorProperties properties;
    private final LinuxProcessInspector processInspector;
    private final HttpHealthChecker healthChecker;
    private final CommandExecutor commandExecutor;

    private final Map<String, ServiceState> states = new ConcurrentHashMap<>();

    public ServiceMonitor(MonitorProperties properties,
                          LinuxProcessInspector processInspector,
                          HttpHealthChecker healthChecker,
                          CommandExecutor commandExecutor) {
        this.properties = properties;
        this.processInspector = processInspector;
        this.healthChecker = healthChecker;
        this.commandExecutor = commandExecutor;
    }

    public void checkAll() {
        for (MonitorProperties.MonitoredService service : properties.getServices()) {
            try {
                checkOne(service);
            } catch (Exception e) {
                log.error("Unexpected error while checking service {}", service.getName(), e);
            }
        }
    }

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
