package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.repository.HostConfigRepository;
import com.example.guardian.scheduler.MonitoringScheduler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAndSchedulerTest {

    @Test
    void dataBootstrapDoesNothingWhenHostsAlreadyExist() {
        HostConfigRepository repository = mock(HostConfigRepository.class);
        when(repository.count()).thenReturn(1L);
        DataBootstrap bootstrap = new DataBootstrap(repository);

        bootstrap.run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void dataBootstrapCreatesDefaultLocalhostForEmptyDatabase() {
        HostConfigRepository repository = mock(HostConfigRepository.class);
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(HostConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DataBootstrap bootstrap = new DataBootstrap(repository);

        bootstrap.run(null);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(host ->
                "Localhost".equals(host.getName())
                        && host.getConnectionMode() == HostConnectionMode.LOCAL
                        && "127.0.0.1".equals(host.getAddress())
                        && "Default local host created automatically".equals(host.getDescription())
        ));
    }

    @Test
    void monitoringSchedulerDelegatesToServiceMonitor() {
        ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
        MonitoringScheduler scheduler = new MonitoringScheduler(serviceMonitor);

        scheduler.monitor();

        verify(serviceMonitor).checkAll();
    }

    @Test
    void monitorPropertiesStoresInterval() {
        com.example.guardian.config.MonitorProperties properties = new com.example.guardian.config.MonitorProperties();

        assertThat(properties.getInterval()).hasSeconds(30);

        properties.setInterval(java.time.Duration.ofMinutes(2));

        assertThat(properties.getInterval()).hasMinutes(2);
    }
}
