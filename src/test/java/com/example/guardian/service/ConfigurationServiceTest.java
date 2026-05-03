package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.api.GroupRequest;
import com.example.guardian.api.HostRequest;
import com.example.guardian.api.MonitoredServiceRequest;
import com.example.guardian.model.HostConfig;
import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceGroup;
import com.example.guardian.repository.HostConfigRepository;
import com.example.guardian.repository.MonitoredServiceRepository;
import com.example.guardian.repository.ServiceGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private HostConfigRepository hostConfigRepository;

    @Mock
    private ServiceGroupRepository serviceGroupRepository;

    @Mock
    private MonitoredServiceRepository monitoredServiceRepository;

    private ConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new ConfigurationService(hostConfigRepository, serviceGroupRepository, monitoredServiceRepository);
    }

    @Test
    void getHostsReturnsRepositoryOrder() {
        List<HostConfig> hosts = List.of(TestFixtures.localHost(1));
        when(hostConfigRepository.findAllByOrderByNameAsc()).thenReturn(hosts);

        assertThat(service.getHosts()).isSameAs(hosts);
    }

    @Test
    void createLocalHostNormalizesOptionalFieldsAndDefaultsAddress() {
        HostRequest request = new HostRequest(
                " Local ",
                HostConnectionMode.LOCAL,
                " ",
                22,
                " user ",
                " /key ",
                " "
        );
        when(hostConfigRepository.existsByNameIgnoreCase(" Local ")).thenReturn(false);
        when(hostConfigRepository.save(any(HostConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HostConfig created = service.createHost(request);

        assertThat(created.getName()).isEqualTo("Local");
        assertThat(created.getConnectionMode()).isEqualTo(HostConnectionMode.LOCAL);
        assertThat(created.getAddress()).isEqualTo("127.0.0.1");
        assertThat(created.getSshPort()).isEqualTo(22);
        assertThat(created.getSshUser()).isEqualTo("user");
        assertThat(created.getPrivateKeyPath()).isEqualTo("/key");
        assertThat(created.getDescription()).isNull();
    }

    @Test
    void createSshHostRequiresSshUserAndPrivateKey() {
        HostRequest missingUser = new HostRequest("Remote", HostConnectionMode.SSH, "10.0.0.5", 22, " ", "/key", null);
        HostRequest missingKey = new HostRequest("Remote", HostConnectionMode.SSH, "10.0.0.5", 22, "deploy", " ", null);

        assertThatThrownBy(() -> service.createHost(missingUser))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createHost(missingKey))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createHostRejectsDuplicateName() {
        HostRequest request = new HostRequest("Remote", HostConnectionMode.LOCAL, null, 22, null, null, null);
        when(hostConfigRepository.existsByNameIgnoreCase("Remote")).thenReturn(true);

        assertThatThrownBy(() -> service.createHost(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateHostAllowsSameNameAndRejectsConflictingNewName() {
        HostConfig existing = TestFixtures.localHost(1);
        when(hostConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(hostConfigRepository.save(any(HostConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HostConfig updated = service.updateHost(1L,
                new HostRequest("localhost", HostConnectionMode.LOCAL, "192.168.1.10", 22, null, null, "updated"));

        assertThat(updated.getName()).isEqualTo("localhost");
        assertThat(updated.getAddress()).isEqualTo("192.168.1.10");
        assertThat(updated.getDescription()).isEqualTo("updated");

        when(hostConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(hostConfigRepository.existsByNameIgnoreCase("Other")).thenReturn(true);

        assertThatThrownBy(() -> service.updateHost(1L,
                new HostRequest("Other", HostConnectionMode.LOCAL, null, 22, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deleteHostRejectsReferencedHostAndDeletesUnreferencedHost() {
        HostConfig host = TestFixtures.localHost(1);
        when(hostConfigRepository.findById(1L)).thenReturn(Optional.of(host));
        when(monitoredServiceRepository.countByHostId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteHost(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(monitoredServiceRepository.countByHostId(1L)).thenReturn(0L);

        service.deleteHost(1L);

        verify(hostConfigRepository).delete(host);
    }

    @Test
    void getHostRaisesNotFound() {
        when(hostConfigRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHost(404L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void groupCrudAppliesUniquenessAndReferenceRules() {
        GroupRequest request = new GroupRequest(" Payments ", " ");
        when(serviceGroupRepository.findAllByOrderByNameAsc()).thenReturn(List.of(TestFixtures.group(1)));
        when(serviceGroupRepository.existsByNameIgnoreCase(" Payments ")).thenReturn(false);
        when(serviceGroupRepository.save(any(ServiceGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.getGroups()).hasSize(1);

        ServiceGroup created = service.createGroup(request);
        assertThat(created.getName()).isEqualTo("Payments");
        assertThat(created.getDescription()).isNull();

        ServiceGroup existing = TestFixtures.group(1);
        when(serviceGroupRepository.findById(1L)).thenReturn(Optional.of(existing));
        ServiceGroup updated = service.updateGroup(1L, new GroupRequest("Payments", "Updated"));
        assertThat(updated.getDescription()).isEqualTo("Updated");

        when(serviceGroupRepository.existsByNameIgnoreCase("Other")).thenReturn(true);
        assertThatThrownBy(() -> service.updateGroup(1L, new GroupRequest("Other", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        when(serviceGroupRepository.existsByNameIgnoreCase("Duplicate")).thenReturn(true);
        assertThatThrownBy(() -> service.createGroup(new GroupRequest("Duplicate", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        when(monitoredServiceRepository.countByGroupId(1L)).thenReturn(1L);
        assertThatThrownBy(() -> service.deleteGroup(1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(monitoredServiceRepository.countByGroupId(1L)).thenReturn(0L);
        service.deleteGroup(1L);
        verify(serviceGroupRepository).delete(existing);
    }

    @Test
    void getGroupRaisesNotFound() {
        when(serviceGroupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroup(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getServicesUsesUnfilteredOrGroupFilteredQueries() {
        List<MonitoredService> services = List.of(TestFixtures.service(1, TestFixtures.localHost(1), null));
        when(monitoredServiceRepository.findAllByOrderByNameAsc()).thenReturn(services);
        when(monitoredServiceRepository.findAllByGroupIdInOrderByNameAsc(List.of(2L, 3L))).thenReturn(services);

        assertThat(service.getServices(null)).isSameAs(services);
        assertThat(service.getServices(List.of())).isSameAs(services);
        assertThat(service.getServices(List.of(2L, 3L))).isSameAs(services);
    }

    @Test
    void createServiceResolvesRelationshipsAndNormalizesFields() {
        HostConfig host = TestFixtures.localHost(1);
        ServiceGroup group = TestFixtures.group(2);
        MonitoredServiceRequest request = new MonitoredServiceRequest(
                " api ",
                1L,
                2L,
                " java -jar api.jar ",
                " systemctl restart api ",
                " ",
                5L,
                60L,
                600L,
                3,
                true,
                " "
        );
        when(monitoredServiceRepository.existsByNameIgnoreCase(" api ")).thenReturn(false);
        when(hostConfigRepository.findById(1L)).thenReturn(Optional.of(host));
        when(serviceGroupRepository.findById(2L)).thenReturn(Optional.of(group));
        when(monitoredServiceRepository.save(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredService created = service.createService(request);

        assertThat(created.getName()).isEqualTo("api");
        assertThat(created.getHost()).isSameAs(host);
        assertThat(created.getGroup()).isSameAs(group);
        assertThat(created.getProcessMatch()).isEqualTo("java -jar api.jar");
        assertThat(created.getRestartCommand()).isEqualTo("systemctl restart api");
        assertThat(created.getHealthUrl()).isNull();
        assertThat(created.getHealthTimeoutSeconds()).isEqualTo(5);
        assertThat(created.getRestartCooldownSeconds()).isEqualTo(60);
        assertThat(created.getRestartWindowSeconds()).isEqualTo(600);
        assertThat(created.getMaxRestartsInWindow()).isEqualTo(3);
        assertThat(created.isMonitoringEnabled()).isTrue();
        assertThat(created.getDescription()).isNull();
    }

    @Test
    void serviceCrudRejectsDuplicatesAndSupportsUpdatesDeletesAndMonitoringFlag() {
        HostConfig host = TestFixtures.localHost(1);
        MonitoredService existing = TestFixtures.service(10, host, null);
        existing.setName("api");
        MonitoredServiceRequest request = new MonitoredServiceRequest(
                "api", 1L, null, "api.jar", "restart api", null,
                5L, 60L, 600L, 3, false, "updated"
        );

        when(monitoredServiceRepository.existsByNameIgnoreCase("api")).thenReturn(true);
        assertThatThrownBy(() -> service.createService(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        when(monitoredServiceRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(hostConfigRepository.findById(1L)).thenReturn(Optional.of(host));
        when(monitoredServiceRepository.save(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredService updated = service.updateService(10L, request);
        assertThat(updated.getName()).isEqualTo("api");
        assertThat(updated.getGroup()).isNull();
        assertThat(updated.isMonitoringEnabled()).isFalse();

        updated = service.setMonitoringEnabled(10L, true);
        assertThat(updated.isMonitoringEnabled()).isTrue();

        service.deleteService(10L);
        verify(monitoredServiceRepository).delete(existing);

        when(monitoredServiceRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getService(404L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateServiceRejectsConflictingNewName() {
        MonitoredService existing = TestFixtures.service(10, TestFixtures.localHost(1), null);
        when(monitoredServiceRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(monitoredServiceRepository.existsByNameIgnoreCase("other")).thenReturn(true);

        MonitoredServiceRequest request = new MonitoredServiceRequest(
                "other", 1L, null, "api.jar", "restart api", null,
                5L, 60L, 600L, 3, true, null
        );

        assertThatThrownBy(() -> service.updateService(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
