package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.model.HostConfig;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceMonitorTest {

    @Mock
    private MonitoredServiceRepository monitoredServiceRepository;

    @Mock
    private LinuxProcessInspector processInspector;

    @Mock
    private HttpHealthChecker healthChecker;

    @Mock
    private HostShellExecutor hostShellExecutor;

    private ServiceMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new ServiceMonitor(monitoredServiceRepository, processInspector, healthChecker, hostShellExecutor);
    }

    @Test
    void runtimeSnapshotIsEmptyBeforeFirstCheck() {
        assertThat(monitor.getRuntimeSnapshot(1L)).isEmpty();
        assertThat(monitor.getRuntimeSnapshots()).isEmpty();
    }

    @Test
    void checkAllMarksHealthyServiceUp() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(true);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshot.processRunning()).isTrue();
        assertThat(snapshot.healthCheckPassed()).isTrue();
        assertThat(snapshot.lastMessage()).isEqualTo("Service is healthy");
        assertThat(snapshot.lastCheckAt()).isNotNull();
        verify(hostShellExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void checkAllSkipsHttpHealthCheckWhenUrlIsBlank() {
        MonitoredService service = monitoredService(1);
        service.setHealthUrl(" ");
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(true);

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshot.healthCheckPassed()).isTrue();
        verify(healthChecker, never()).isHealthy(any(), any(), any());
    }

    @Test
    void checkAllMarksPausedService() {
        MonitoredService service = monitoredService(1);
        service.setMonitoringEnabled(false);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.PAUSED);
        assertThat(snapshot.processRunning()).isFalse();
        assertThat(snapshot.healthCheckPassed()).isFalse();
        assertThat(snapshot.lastMessage()).isEqualTo("Monitoring is paused");
    }

    @Test
    void checkAllRestartsUnhealthyServiceAndStoresRestartingSnapshot() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(false);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), service.getRestartCommand(), Duration.ofSeconds(20)))
                .thenReturn(new CommandExecutor.CommandResult(0, "restarted", null));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        assertThat(snapshot.processRunning()).isFalse();
        assertThat(snapshot.healthCheckPassed()).isFalse();
        assertThat(snapshot.lastMessage()).isEqualTo("Restart command executed successfully");
        assertThat(snapshot.lastRestartAt()).isNotNull();
    }

    @Test
    void checkAllStoresRestartErrorMessageFromCommandResult() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(false);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);
        when(hostShellExecutor.execute(service.getHost(), service.getRestartCommand(), Duration.ofSeconds(20)))
                .thenReturn(new CommandExecutor.CommandResult(1, "command output", " "));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.ERROR);
        assertThat(snapshot.lastMessage()).isEqualTo("Restart failed: command output");
    }

    @Test
    void checkAllBlocksSecondRestartDuringCooldown() {
        MonitoredService service = monitoredService(1);
        service.setRestartCooldownSeconds(3600);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(false);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), service.getRestartCommand(), Duration.ofSeconds(20)))
                .thenReturn(new CommandExecutor.CommandResult(0, "restarted", null));

        monitor.checkAll();
        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.DOWN);
        assertThat(snapshot.lastMessage()).isEqualTo("Process is missing and health-check failed. Restart blocked by policy");
        verify(hostShellExecutor).execute(service.getHost(), service.getRestartCommand(), Duration.ofSeconds(20));
    }

    @Test
    void checkAllMarksErrorWhenServiceCheckThrows() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch()))
                .thenThrow(new IllegalStateException("process checker failed"));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.ERROR);
        assertThat(snapshot.processRunning()).isFalse();
        assertThat(snapshot.healthCheckPassed()).isFalse();
        assertThat(snapshot.lastMessage()).isEqualTo("Unexpected monitoring error: process checker failed");
    }

    @Test
    void refreshSingleChecksServiceOrMarksPaused() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(true);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);

        monitor.refreshSingle(1L);
        assertThat(monitor.getRuntimeSnapshot(1L).orElseThrow().status()).isEqualTo(ServiceHealthStatus.UP);

        MonitoredService paused = monitoredService(2);
        paused.setMonitoringEnabled(false);
        when(monitoredServiceRepository.findById(2L)).thenReturn(Optional.of(paused));

        monitor.refreshSingle(2L);
        assertThat(monitor.getRuntimeSnapshot(2L).orElseThrow().status()).isEqualTo(ServiceHealthStatus.PAUSED);
    }

    @Test
    void refreshSingleAndRestartNowRaiseNotFound() {
        when(monitoredServiceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitor.refreshSingle(404L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> monitor.restartNow(404L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void restartNowBypassesHealthChecksAndUpdatesSnapshot() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(hostShellExecutor.execute(service.getHost(), service.getRestartCommand(), Duration.ofSeconds(20)))
                .thenReturn(new CommandExecutor.CommandResult(0, "ok", null));

        monitor.restartNow(1L);

        Optional<ServiceRuntimeSnapshot> snapshot = monitor.getRuntimeSnapshot(1L);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        verify(processInspector, never()).isRunning(any(), any());
    }

    @Test
    void getRuntimeSnapshotsReturnsAllKnownStates() {
        MonitoredService first = monitoredService(1);
        MonitoredService second = monitoredService(2);
        second.setMonitoringEnabled(false);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(first, second));
        when(processInspector.isRunning(first.getHost(), first.getProcessMatch())).thenReturn(true);
        when(healthChecker.isHealthy(first.getHost(), first.getHealthUrl(), first.getHealthTimeout())).thenReturn(true);

        monitor.checkAll();

        Map<Long, ServiceRuntimeSnapshot> snapshots = monitor.getRuntimeSnapshots();
        assertThat(snapshots).containsOnlyKeys(1L, 2L);
        assertThat(snapshots.get(1L).status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshots.get(2L).status()).isEqualTo(ServiceHealthStatus.PAUSED);
    }

    @Test
    void unhealthyMessageReflectsOnlyFailedHealthCheck() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.isRunning(service.getHost(), service.getProcessMatch())).thenReturn(true);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(eq(service.getHost()), eq(service.getRestartCommand()), any()))
                .thenReturn(new CommandExecutor.CommandResult(1, "", "restart failed"));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.ERROR);
        assertThat(snapshot.lastMessage()).isEqualTo("Restart failed: restart failed");
    }

    private MonitoredService monitoredService(long id) {
        HostConfig host = TestFixtures.localHost(id);
        MonitoredService service = TestFixtures.service(id, host, null);
        service.setRestartCooldownSeconds(60);
        service.setRestartWindowSeconds(600);
        service.setMaxRestartsInWindow(3);
        return service;
    }
}
