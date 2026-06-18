package com.example.guardian.api;

import com.example.guardian.TestFixtures;
import com.example.guardian.model.HostConfig;
import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceGroup;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApiMapperTest {

    private final ApiMapper mapper = new ApiMapper();

    @Test
    void mapsHostToResponse() {
        HostConfig host = TestFixtures.sshHost(1);

        HostResponse response = mapper.toHostResponse(host);

        assertThat(response.id()).isEqualTo(1);
        assertThat(response.name()).isEqualTo("Remote host");
        assertThat(response.connectionMode()).isEqualTo(HostConnectionMode.SSH);
        assertThat(response.address()).isEqualTo("10.0.0.5");
        assertThat(response.sshPort()).isEqualTo(2222);
        assertThat(response.sshUser()).isEqualTo("deploy");
        assertThat(response.privateKeyPath()).isEqualTo("/home/deploy/.ssh/id_ed25519");
        assertThat(response.description()).isEqualTo("Remote host");
    }

    @Test
    void mapsGroupToResponse() {
        ServiceGroup group = TestFixtures.group(2);

        GroupResponse response = mapper.toGroupResponse(group);

        assertThat(response.id()).isEqualTo(2);
        assertThat(response.name()).isEqualTo("Payments");
        assertThat(response.description()).isEqualTo("Payment services");
    }

    @Test
    void mapsServiceWithGroupToResponse() {
        MonitoredService service = TestFixtures.service(3, TestFixtures.sshHost(1), TestFixtures.group(2));

        MonitoredServiceResponse response = mapper.toServiceResponse(service);

        assertThat(response.id()).isEqualTo(3);
        assertThat(response.name()).isEqualTo("billing-api");
        assertThat(response.hostId()).isEqualTo(1);
        assertThat(response.hostName()).isEqualTo("Remote host");
        assertThat(response.hostConnectionMode()).isEqualTo(HostConnectionMode.SSH);
        assertThat(response.hostAddress()).isEqualTo("10.0.0.5");
        assertThat(response.groupId()).isEqualTo(2);
        assertThat(response.groupName()).isEqualTo("Payments");
        assertThat(response.processMatch()).isEqualTo("billing-api.jar");
        assertThat(response.executionPath()).isNull();
        assertThat(response.restartCommand()).isEqualTo("systemctl restart billing-api");
        assertThat(response.startCommand()).isEqualTo("systemctl start billing-api");
        assertThat(response.manualRestartEnabled()).isTrue();
        assertThat(response.lastKnownPid()).isNull();
        assertThat(response.healthUrl()).isEqualTo("http://127.0.0.1:8080/actuator/health");
        assertThat(response.healthTimeoutSeconds()).isEqualTo(3);
        assertThat(response.restartCooldownSeconds()).isEqualTo(60);
        assertThat(response.restartWindowSeconds()).isEqualTo(600);
        assertThat(response.maxRestartsInWindow()).isEqualTo(3);
        assertThat(response.monitoringEnabled()).isTrue();
        assertThat(response.notificationsEnabled()).isTrue();
        assertThat(response.description()).isEqualTo("Billing service");
    }

    @Test
    void mapsServiceWithoutGroupToNullGroupFields() {
        MonitoredService service = TestFixtures.service(3, TestFixtures.localHost(1), null);

        MonitoredServiceResponse response = mapper.toServiceResponse(service);

        assertThat(response.groupId()).isNull();
        assertThat(response.groupName()).isNull();
    }

    @Test
    void mapsDashboardServiceWithSnapshot() {
        MonitoredService service = TestFixtures.service(3, TestFixtures.sshHost(1), TestFixtures.group(2));
        service.setNotificationsEnabled(false);
        Instant checkAt = Instant.parse("2026-05-03T10:15:30Z");
        Instant restartAt = Instant.parse("2026-05-03T10:10:30Z");
        ServiceRuntimeSnapshot snapshot = new ServiceRuntimeSnapshot(
                ServiceHealthStatus.UP,
                true,
                1234L,
                true,
                true,
                "Service is healthy",
                checkAt,
                restartAt
        );

        DashboardServiceResponse response = mapper.toDashboardServiceResponse(service, snapshot);

        assertThat(response.status()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(response.notificationsEnabled()).isFalse();
        assertThat(response.processRunning()).isTrue();
        assertThat(response.lastKnownPid()).isEqualTo(1234L);
        assertThat(response.healthCheckEnabled()).isTrue();
        assertThat(response.healthCheckPassed()).isTrue();
        assertThat(response.lastMessage()).isEqualTo("Service is healthy");
        assertThat(response.lastCheckAt()).isEqualTo(checkAt);
        assertThat(response.lastRestartAt()).isEqualTo(restartAt);
    }

    @Test
    void mapsDashboardServiceWithoutSnapshotAsUnknownOrPaused() {
        MonitoredService enabled = TestFixtures.service(3, TestFixtures.localHost(1), null);
        MonitoredService paused = TestFixtures.service(4, TestFixtures.localHost(1), null);
        paused.setMonitoringEnabled(false);

        DashboardServiceResponse enabledResponse = mapper.toDashboardServiceResponse(enabled, null);
        DashboardServiceResponse pausedResponse = mapper.toDashboardServiceResponse(paused, null);

        assertThat(enabledResponse.status()).isEqualTo(ServiceHealthStatus.UNKNOWN);
        assertThat(enabledResponse.lastMessage()).isEqualTo("Service has not been checked yet");
        assertThat(enabledResponse.lastCheckAt()).isNull();
        assertThat(enabledResponse.lastRestartAt()).isNull();
        assertThat(pausedResponse.status()).isEqualTo(ServiceHealthStatus.PAUSED);
    }

    @Test
    void mapsDashboardServiceWithBlankHealthUrlAsHealthCheckDisabled() {
        MonitoredService service = TestFixtures.service(3, TestFixtures.localHost(1), null);
        service.setHealthUrl(" ");
        ServiceRuntimeSnapshot snapshot = new ServiceRuntimeSnapshot(
                ServiceHealthStatus.UP,
                true,
                1234L,
                false,
                false,
                "Service is healthy",
                Instant.parse("2026-05-03T10:15:30Z"),
                null
        );

        DashboardServiceResponse response = mapper.toDashboardServiceResponse(service, snapshot);

        assertThat(response.healthCheckEnabled()).isFalse();
        assertThat(response.healthCheckPassed()).isFalse();
    }
}
