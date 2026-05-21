package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private MonitorProperties monitorProperties;
    private ServiceMonitor monitor;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        monitor = new ServiceMonitor(
                monitoredServiceRepository,
                processInspector,
                healthChecker,
                hostShellExecutor,
                monitorProperties
        );
    }

    @Test
    void runtimeSnapshotIsEmptyBeforeFirstCheck() {
        assertThat(monitor.getRuntimeSnapshot(1L)).isEmpty();
        assertThat(monitor.getRuntimeSnapshots()).isEmpty();
    }

    @Test
    void checkAllMarksHealthyServiceUpAndStoresFirstPid() {
        MonitoredService service = monitoredService(1);
        service.setLastKnownPid(null);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessFound(service, 1234L);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);
        when(monitoredServiceRepository.save(service)).thenReturn(service);

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshot.processRunning()).isTrue();
        assertThat(snapshot.lastKnownPid()).isEqualTo(1234L);
        assertThat(snapshot.healthCheckEnabled()).isTrue();
        assertThat(snapshot.healthCheckPassed()).isTrue();
        assertThat(snapshot.lastMessage()).isEqualTo("Service is healthy");
        assertThat(snapshot.lastCheckAt()).isNotNull();
        assertThat(service.getLastKnownPid()).isEqualTo(1234L);
        verify(monitoredServiceRepository).save(service);
        verify(hostShellExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void checkAllUpdatesStoredPidWhenProcessSearchFindsNewPid() {
        MonitoredService service = monitoredService(1);
        service.setLastKnownPid(1111L);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessFound(service, 2222L);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);

        monitor.checkAll();

        assertThat(service.getLastKnownPid()).isEqualTo(2222L);
        assertThat(monitor.getRuntimeSnapshot(1L).orElseThrow().lastKnownPid()).isEqualTo(2222L);
        verify(monitoredServiceRepository).save(service);
    }

    @Test
    void checkAllUsesPidCheckAsAdditionalAvailabilitySignal() {
        MonitoredService service = monitoredService(1);
        service.setLastKnownPid(1234L);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.findFirst(service.getHost(), service.getProcessMatch()))
                .thenReturn(Optional.of(new LinuxProcessInspector.ProcessInfo(1234L, "java -jar billing-api.jar")));
        when(processInspector.isPidRunning(service.getHost(), 1234L)).thenReturn(false);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);
        when(hostShellExecutor.execute(eq(service.getHost()), eq(service.getStartCommand()), any()))
                .thenReturn(new CommandExecutor.CommandResult(0, "started", null));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        assertThat(snapshot.processRunning()).isFalse();
        assertThat(snapshot.lastMessage()).isEqualTo("Start command executed successfully");
        verify(hostShellExecutor, never()).execute(eq(service.getHost()), eq(service.getRestartCommand()), any());
    }

    @Test
    void checkAllMarksHealthCheckOffWhenUrlIsBlank() {
        MonitoredService service = monitoredService(1);
        service.setHealthUrl(" ");
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessFound(service, 1234L);

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshot.healthCheckEnabled()).isFalse();
        assertThat(snapshot.healthCheckPassed()).isFalse();
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
        assertThat(snapshot.healthCheckEnabled()).isTrue();
        assertThat(snapshot.healthCheckPassed()).isFalse();
        assertThat(snapshot.lastMessage()).isEqualTo("Monitoring is paused");
    }

    @Test
    void checkAllStartsMissingServiceFromExecutionPathWithoutManualRestart() {
        MonitoredService service = monitoredService(1);
        service.setExecutionPath("/opt/billing");
        service.setRestartCommand("./stop.sh");
        service.setStartCommand("./start.sh");
        service.setManualRestartEnabled(true);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessMissing(service);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), "cd '/opt/billing' && ./start.sh", monitorProperties.getCommand().getStartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(0, "started", null));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        assertThat(snapshot.lastMessage()).isEqualTo("Start command executed successfully");
        assertThat(snapshot.lastRestartAt()).isNotNull();
        verify(hostShellExecutor, never()).execute(service.getHost(), "cd '/opt/billing' && ./stop.sh", monitorProperties.getCommand().getRestartTimeout());
        verify(hostShellExecutor).execute(service.getHost(), "cd '/opt/billing' && ./start.sh", monitorProperties.getCommand().getStartTimeout());
    }

    @Test
    void checkAllStartsMissingServiceWithoutAutomaticKillWhenManualRestartIsDisabled() {
        MonitoredService service = monitoredService(1);
        service.setManualRestartEnabled(false);
        service.setStartCommand("./start.sh");
        service.setLastKnownPid(1234L);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessMissing(service);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), "./start.sh", monitorProperties.getCommand().getStartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(0, "started", null));

        monitor.checkAll();

        assertThat(monitor.getRuntimeSnapshot(1L).orElseThrow().status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        verify(processInspector, never()).stop(service.getHost(), service.getProcessMatch(), 1234L, monitorProperties.getCommand().getStopTimeout());
        verify(hostShellExecutor).execute(service.getHost(), "./start.sh", monitorProperties.getCommand().getStartTimeout());
    }

    @Test
    void checkAllStoresStartErrorMessageFromCommandResult() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessMissing(service);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);
        when(hostShellExecutor.execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(1, "command output", " "));

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.ERROR);
        assertThat(snapshot.lastMessage()).isEqualTo("Start failed: command output");
        verify(hostShellExecutor, never()).execute(service.getHost(), service.getRestartCommand(), monitorProperties.getCommand().getRestartTimeout());
    }

    @Test
    void checkAllBlocksSecondStartDuringCooldown() {
        MonitoredService service = monitoredService(1);
        service.setRestartCooldownSeconds(3600);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        stubProcessMissing(service);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(0, "started", null));

        monitor.checkAll();
        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.DOWN);
        assertThat(snapshot.lastMessage()).isEqualTo("Process is missing and health-check failed. Start blocked by policy");
        verify(hostShellExecutor, never())
                .execute(service.getHost(), service.getRestartCommand(), monitorProperties.getCommand().getRestartTimeout());
        verify(hostShellExecutor, times(1))
                .execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout());
    }

    @Test
    void checkAllMarksErrorWhenServiceCheckThrows() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(service));
        when(processInspector.findFirst(service.getHost(), service.getProcessMatch()))
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
        stubProcessFound(service, 1234L);
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
    void restartNowRunsRestartOnlyWhenConfiguredHealthCheckFails() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        stubProcessFound(service, 1234L);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), service.getRestartCommand(), monitorProperties.getCommand().getRestartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(0, "stopped", null));
        when(hostShellExecutor.execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(0, "started", null));

        monitor.restartNow(1L);

        Optional<ServiceRuntimeSnapshot> snapshot = monitor.getRuntimeSnapshot(1L);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        InOrder inOrder = inOrder(hostShellExecutor);
        inOrder.verify(hostShellExecutor).execute(service.getHost(), service.getRestartCommand(), monitorProperties.getCommand().getRestartTimeout());
        inOrder.verify(hostShellExecutor).execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout());
    }

    @Test
    void restartNowStartsOnlyWhenServiceIsMissing() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        stubProcessMissing(service);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);
        when(hostShellExecutor.execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout()))
                .thenReturn(new CommandExecutor.CommandResult(0, "started", null));

        monitor.restartNow(1L);

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.RESTARTING);
        assertThat(snapshot.lastMessage()).isEqualTo("Start command executed successfully");
        verify(hostShellExecutor, never()).execute(service.getHost(), service.getRestartCommand(), monitorProperties.getCommand().getRestartTimeout());
        verify(hostShellExecutor).execute(service.getHost(), service.getStartCommand(), monitorProperties.getCommand().getStartTimeout());
    }

    @Test
    void restartNowSkipsRestartWhenHealthCheckIsMissing() {
        MonitoredService service = monitoredService(1);
        service.setHealthUrl(" ");
        when(monitoredServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        stubProcessFound(service, 1234L);

        monitor.restartNow(1L);

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshot.healthCheckEnabled()).isFalse();
        assertThat(snapshot.lastMessage()).isEqualTo("Restart skipped: health-check is not configured");
        verify(healthChecker, never()).isHealthy(any(), any(), any());
        verify(hostShellExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void restartNowSkipsRestartWhenHealthCheckIsHealthy() {
        MonitoredService service = monitoredService(1);
        when(monitoredServiceRepository.findById(1L)).thenReturn(Optional.of(service));
        stubProcessFound(service, 1234L);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(true);

        monitor.restartNow(1L);

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(snapshot.healthCheckPassed()).isTrue();
        assertThat(snapshot.lastMessage()).isEqualTo("Restart skipped: health-check is healthy");
        verify(hostShellExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void getRuntimeSnapshotsReturnsAllKnownStates() {
        MonitoredService first = monitoredService(1);
        MonitoredService second = monitoredService(2);
        second.setMonitoringEnabled(false);
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(List.of(first, second));
        stubProcessFound(first, 1234L);
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
        stubProcessFound(service, 1234L);
        when(healthChecker.isHealthy(service.getHost(), service.getHealthUrl(), service.getHealthTimeout())).thenReturn(false);

        monitor.checkAll();

        ServiceRuntimeSnapshot snapshot = monitor.getRuntimeSnapshot(1L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ServiceHealthStatus.DOWN);
        assertThat(snapshot.lastMessage()).isEqualTo("Health-check failed");
        verify(hostShellExecutor, never()).execute(any(), any(), any());
    }

    private void stubProcessFound(MonitoredService service, long pid) {
        when(processInspector.findFirst(service.getHost(), service.getProcessMatch()))
                .thenReturn(Optional.of(new LinuxProcessInspector.ProcessInfo(pid, pid + " " + service.getProcessMatch())));
        when(processInspector.isPidRunning(service.getHost(), pid)).thenReturn(true);
    }

    private void stubProcessMissing(MonitoredService service) {
        when(processInspector.findFirst(service.getHost(), service.getProcessMatch())).thenReturn(Optional.empty());
    }

    private MonitoredService monitoredService(long id) {
        HostConfig host = TestFixtures.localHost(id);
        MonitoredService service = TestFixtures.service(id, host, null);
        service.setExecutionPath(null);
        service.setRestartCooldownSeconds(60);
        service.setRestartWindowSeconds(600);
        service.setMaxRestartsInWindow(3);
        return service;
    }
}
