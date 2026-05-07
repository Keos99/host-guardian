package com.example.guardian.controller;

import com.example.guardian.TestFixtures;
import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.DashboardResponse;
import com.example.guardian.api.GroupRequest;
import com.example.guardian.api.GroupResponse;
import com.example.guardian.api.HostRequest;
import com.example.guardian.api.HostResponse;
import com.example.guardian.api.MonitoredServiceRequest;
import com.example.guardian.api.MonitoredServiceResponse;
import com.example.guardian.model.HostConfig;
import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceGroup;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.service.ConfigurationService;
import com.example.guardian.service.ServiceMonitor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerTest {

    private final ApiMapper apiMapper = new ApiMapper();

    @Test
    void dashboardControllerReturnsSummaryGroupsAndServices() {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
        DashboardController controller = new DashboardController(configurationService, serviceMonitor, apiMapper);
        HostConfig host = TestFixtures.localHost(1);
        ServiceGroup group = TestFixtures.group(2);
        List<MonitoredService> services = List.of(
                monitoredService(1, host, group),
                monitoredService(2, host, group),
                monitoredService(3, host, group),
                monitoredService(4, host, group),
                monitoredService(5, host, group),
                monitoredService(6, host, group)
        );
        when(configurationService.getServices(List.of(2L))).thenReturn(services);
        when(configurationService.getGroups()).thenReturn(List.of(group));
        when(serviceMonitor.getRuntimeSnapshots()).thenReturn(Map.of(
                1L, snapshot(ServiceHealthStatus.UP),
                2L, snapshot(ServiceHealthStatus.DOWN),
                3L, snapshot(ServiceHealthStatus.PAUSED),
                4L, snapshot(ServiceHealthStatus.RESTARTING),
                5L, snapshot(ServiceHealthStatus.ERROR)
        ));

        DashboardResponse response = controller.getDashboard(List.of(2L));

        assertThat(response.groups()).extracting(GroupResponse::name).containsExactly("Payments");
        assertThat(response.services()).hasSize(6);
        assertThat(response.summary().total()).isEqualTo(6);
        assertThat(response.summary().up()).isEqualTo(1);
        assertThat(response.summary().down()).isEqualTo(1);
        assertThat(response.summary().paused()).isEqualTo(1);
        assertThat(response.summary().restarting()).isEqualTo(1);
        assertThat(response.summary().error()).isEqualTo(1);
        assertThat(response.summary().unknown()).isEqualTo(1);
    }

    @Test
    void hostControllerDelegatesCrudOperations() {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        HostController controller = new HostController(configurationService, apiMapper);
        HostConfig host = TestFixtures.sshHost(1);
        HostRequest request = new HostRequest("Remote", HostConnectionMode.SSH, "10.0.0.5", 2222, "deploy", "/key", "desc");
        when(configurationService.getHosts()).thenReturn(List.of(host));
        when(configurationService.createHost(request)).thenReturn(host);
        when(configurationService.updateHost(1L, request)).thenReturn(host);

        List<HostResponse> hosts = controller.listHosts();
        HostResponse created = controller.createHost(request);
        HostResponse updated = controller.updateHost(1L, request);
        controller.deleteHost(1L);

        assertThat(hosts).hasSize(1);
        assertThat(created.name()).isEqualTo("Remote host");
        assertThat(updated.id()).isEqualTo(1);
        verify(configurationService).deleteHost(1L);
    }

    @Test
    void serviceGroupControllerDelegatesCrudOperations() {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        ServiceGroupController controller = new ServiceGroupController(configurationService, apiMapper);
        ServiceGroup group = TestFixtures.group(2);
        GroupRequest request = new GroupRequest("Payments", "desc");
        when(configurationService.getGroups()).thenReturn(List.of(group));
        when(configurationService.createGroup(request)).thenReturn(group);
        when(configurationService.updateGroup(2L, request)).thenReturn(group);

        List<GroupResponse> groups = controller.listGroups();
        GroupResponse created = controller.createGroup(request);
        GroupResponse updated = controller.updateGroup(2L, request);
        controller.deleteGroup(2L);

        assertThat(groups).hasSize(1);
        assertThat(created.name()).isEqualTo("Payments");
        assertThat(updated.id()).isEqualTo(2);
        verify(configurationService).deleteGroup(2L);
    }

    @Test
    void monitoredServiceControllerDelegatesCrudAndManualActions() {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
        MonitoredServiceController controller = new MonitoredServiceController(configurationService, serviceMonitor, apiMapper);
        MonitoredService service = TestFixtures.service(3, TestFixtures.sshHost(1), TestFixtures.group(2));
        MonitoredServiceRequest request = new MonitoredServiceRequest(
                "billing-api",
                1L,
                2L,
                "billing-api.jar",
                "/opt/billing-api",
                "start",
                true,
                "restart",
                "http://localhost/health",
                3L,
                60L,
                600L,
                3,
                true,
                "desc"
        );
        when(configurationService.getServices(List.of(2L))).thenReturn(List.of(service));
        when(configurationService.getService(3L)).thenReturn(service);
        when(configurationService.createService(request)).thenReturn(service);
        when(configurationService.updateService(3L, request)).thenReturn(service);
        when(configurationService.setMonitoringEnabled(3L, false)).thenReturn(service);

        List<MonitoredServiceResponse> services = controller.listServices(List.of(2L));
        MonitoredServiceResponse found = controller.getService(3L);
        MonitoredServiceResponse created = controller.createService(request);
        MonitoredServiceResponse updated = controller.updateService(3L, request);
        MonitoredServiceResponse toggled = controller.setMonitoringEnabled(3L, false);
        controller.restartService(3L);
        controller.checkServiceNow(3L);
        controller.deleteService(3L);

        assertThat(services).hasSize(1);
        assertThat(found.id()).isEqualTo(3);
        assertThat(created.name()).isEqualTo("billing-api");
        assertThat(updated.hostId()).isEqualTo(1);
        assertThat(toggled.id()).isEqualTo(3);
        verify(serviceMonitor).restartNow(3L);
        verify(serviceMonitor).refreshSingle(3L);
        verify(configurationService).deleteService(3L);
    }

    private MonitoredService monitoredService(long id, HostConfig host, ServiceGroup group) {
        return TestFixtures.service(id, host, group);
    }

    private ServiceRuntimeSnapshot snapshot(ServiceHealthStatus status) {
        return new ServiceRuntimeSnapshot(status, true, 1234L, true, true, status.name(), Instant.now(), null);
    }
}
