package com.example.guardian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void hostConfigStoresAllPropertiesAndDetectsLocalMode() {
        HostConfig host = new HostConfig();

        host.setId(10L);
        host.setName("Host");
        host.setConnectionMode(HostConnectionMode.SSH);
        host.setAddress("10.1.1.1");
        host.setSshPort(2022);
        host.setSshUser("deploy");
        host.setPrivateKeyPath("/tmp/key");
        host.setDescription("Description");

        assertThat(host.getId()).isEqualTo(10L);
        assertThat(host.getName()).isEqualTo("Host");
        assertThat(host.getConnectionMode()).isEqualTo(HostConnectionMode.SSH);
        assertThat(host.getAddress()).isEqualTo("10.1.1.1");
        assertThat(host.getSshPort()).isEqualTo(2022);
        assertThat(host.getSshUser()).isEqualTo("deploy");
        assertThat(host.getPrivateKeyPath()).isEqualTo("/tmp/key");
        assertThat(host.getDescription()).isEqualTo("Description");
        assertThat(host.isLocal()).isFalse();

        host.setConnectionMode(HostConnectionMode.LOCAL);
        assertThat(host.isLocal()).isTrue();
    }

    @Test
    void serviceGroupStoresAllProperties() {
        ServiceGroup group = new ServiceGroup();

        group.setId(2L);
        group.setName("Core");
        group.setDescription("Core services");

        assertThat(group.getId()).isEqualTo(2L);
        assertThat(group.getName()).isEqualTo("Core");
        assertThat(group.getDescription()).isEqualTo("Core services");
    }

    @Test
    void monitoredServiceStoresConfigurationAndExposesDurations() {
        HostConfig host = new HostConfig();
        ServiceGroup group = new ServiceGroup();
        MonitoredService service = new MonitoredService();

        service.setId(3L);
        service.setName("api");
        service.setHost(host);
        service.setGroup(group);
        service.setProcessMatch("api.jar");
        service.setExecutionPath("/opt/api");
        service.setRestartCommand("restart");
        service.setStartCommand("start");
        service.setManualRestartEnabled(true);
        service.setLastKnownPid(1234L);
        service.setHealthUrl("http://localhost/health");
        service.setHealthTimeoutSeconds(4);
        service.setRestartCooldownSeconds(70);
        service.setRestartWindowSeconds(700);
        service.setMaxRestartsInWindow(5);
        service.setMonitoringEnabled(false);
        service.setNotificationsEnabled(false);
        service.setDescription("Service");

        assertThat(service.getId()).isEqualTo(3L);
        assertThat(service.getName()).isEqualTo("api");
        assertThat(service.getHost()).isSameAs(host);
        assertThat(service.getGroup()).isSameAs(group);
        assertThat(service.getProcessMatch()).isEqualTo("api.jar");
        assertThat(service.getExecutionPath()).isEqualTo("/opt/api");
        assertThat(service.getRestartCommand()).isEqualTo("restart");
        assertThat(service.getStartCommand()).isEqualTo("start");
        assertThat(service.isManualRestartEnabled()).isTrue();
        assertThat(service.getLastKnownPid()).isEqualTo(1234L);
        assertThat(service.getHealthUrl()).isEqualTo("http://localhost/health");
        assertThat(service.getHealthTimeoutSeconds()).isEqualTo(4);
        assertThat(service.getRestartCooldownSeconds()).isEqualTo(70);
        assertThat(service.getRestartWindowSeconds()).isEqualTo(700);
        assertThat(service.getMaxRestartsInWindow()).isEqualTo(5);
        assertThat(service.isMonitoringEnabled()).isFalse();
        assertThat(service.isNotificationsEnabled()).isFalse();
        assertThat(service.getDescription()).isEqualTo("Service");
        assertThat(service.getHealthTimeout()).hasSeconds(4);
        assertThat(service.getRestartCooldown()).hasSeconds(70);
        assertThat(service.getRestartWindow()).hasSeconds(700);
    }

    @Test
    void monitoredServiceCommandColumnsUseVarcharMapping() throws Exception {
        assertThat(commandColumn("restartCommand").length()).isEqualTo(4096);
        assertThat(commandColumn("startCommand").length()).isEqualTo(4096);
        assertThat(MonitoredService.class.getDeclaredField("restartCommand").isAnnotationPresent(Lob.class)).isFalse();
        assertThat(MonitoredService.class.getDeclaredField("startCommand").isAnnotationPresent(Lob.class)).isFalse();
    }

    @Test
    void flywayMigrationsDoNotUseClobForCommandColumns() throws Exception {
        Path migrationDir = Path.of("src/main/resources/db/migration");

        try (var migrations = Files.list(migrationDir)) {
            String sql = migrations
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .reduce("", (left, right) -> left + "\n" + right)
                    .toLowerCase();

            assertThat(sql).doesNotContain("clob");
            assertThat(sql).contains("restart_command varchar(4096) not null");
            assertThat(sql).contains("start_command varchar(4096)");
        }
    }

    @Test
    void newMonitoredServiceHasNotificationsEnabledByDefault() {
        assertThat(new MonitoredService().isNotificationsEnabled()).isTrue();
    }

    @Test
    void appSettingStoresKeyAndValue() {
        AppSetting setting = new AppSetting();

        setting.setKey("notifications.global-enabled");
        setting.setValue("false");

        assertThat(setting.getKey()).isEqualTo("notifications.global-enabled");
        assertThat(setting.getValue()).isEqualTo("false");
    }

    @Test
    void serviceStateTracksAlertDeduplicationFlags() {
        ServiceState state = new ServiceState();

        assertThat(state.isDownAlertSent()).isFalse();
        assertThat(state.isStartFailureAlertSent()).isFalse();
        assertThat(state.isRestartLimitAlertSent()).isFalse();

        state.setDownAlertSent(true);
        state.setStartFailureAlertSent(true);
        state.setRestartLimitAlertSent(true);

        assertThat(state.isDownAlertSent()).isTrue();
        assertThat(state.isStartFailureAlertSent()).isTrue();
        assertThat(state.isRestartLimitAlertSent()).isTrue();
    }

    @Test
    void serviceStateStartsUnknownAndStoresRuntimeState() {
        ServiceState state = new ServiceState();
        Instant check = Instant.parse("2026-05-03T10:15:30Z");
        Instant restart = Instant.parse("2026-05-03T10:16:30Z");

        assertThat(state.getStatus()).isEqualTo(ServiceHealthStatus.UNKNOWN);
        assertThat(state.getLastMessage()).isEqualTo("Service has not been checked yet");
        assertThat(state.getRestartHistory()).isEmpty();

        state.setLastCheckAt(check);
        state.setLastRestartAt(restart);
        state.setStatus(ServiceHealthStatus.UP);
        state.setProcessRunning(true);
        state.setHealthCheckPassed(true);
        state.setLastMessage("ok");
        state.getRestartHistory().addLast(restart);

        assertThat(state.getLastCheckAt()).isEqualTo(check);
        assertThat(state.getLastRestartAt()).isEqualTo(restart);
        assertThat(state.getStatus()).isEqualTo(ServiceHealthStatus.UP);
        assertThat(state.isProcessRunning()).isTrue();
        assertThat(state.isHealthCheckPassed()).isTrue();
        assertThat(state.getLastMessage()).isEqualTo("ok");
        assertThat(state.getRestartHistory()).containsExactly(restart);
    }

    private Column commandColumn(String fieldName) throws NoSuchFieldException {
        Field field = MonitoredService.class.getDeclaredField(fieldName);
        return field.getAnnotation(Column.class);
    }
}
