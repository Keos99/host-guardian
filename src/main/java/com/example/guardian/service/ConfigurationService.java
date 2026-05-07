package com.example.guardian.service;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service that owns CRUD rules for hosts, groups, and monitored services.
 *
 * <p>The class validates uniqueness and relationship constraints before delegating
 * persistence to repositories, keeping controllers free of domain-specific checks.
 */
@Service
public class ConfigurationService {

    private final HostConfigRepository hostConfigRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    /**
     * Creates a configuration service.
     *
     * @param hostConfigRepository repository for host configurations
     * @param serviceGroupRepository repository for logical service groups
     * @param monitoredServiceRepository repository for monitored service definitions
     */
    public ConfigurationService(HostConfigRepository hostConfigRepository,
                                ServiceGroupRepository serviceGroupRepository,
                                MonitoredServiceRepository monitoredServiceRepository) {
        this.hostConfigRepository = hostConfigRepository;
        this.serviceGroupRepository = serviceGroupRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
    }

    /**
     * Returns all configured hosts ordered by name.
     *
     * @return ordered host configurations
     */
    public List<HostConfig> getHosts() {
        return hostConfigRepository.findAllByOrderByNameAsc();
    }

    /**
     * Creates a new host after checking name uniqueness.
     *
     * @param request validated host payload
     * @return persisted host configuration
     */
    public HostConfig createHost(HostRequest request) {
        if (hostConfigRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Host with name '" + request.name() + "' already exists");
        }

        HostConfig host = new HostConfig();
        applyHostRequest(host, request);
        return hostConfigRepository.save(host);
    }

    /**
     * Updates an existing host after checking name uniqueness.
     *
     * @param id host identifier
     * @param request validated replacement payload
     * @return persisted host configuration
     */
    public HostConfig updateHost(Long id, HostRequest request) {
        HostConfig host = getHost(id);
        if (!host.getName().equalsIgnoreCase(request.name())
                && hostConfigRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Host with name '" + request.name() + "' already exists");
        }

        applyHostRequest(host, request);
        return hostConfigRepository.save(host);
    }

    /**
     * Deletes a host if no monitored services still reference it.
     *
     * @param id host identifier
     */
    public void deleteHost(Long id) {
        HostConfig host = getHost(id);
        if (monitoredServiceRepository.countByHostId(host.getId()) > 0) {
            throw badRequest("Cannot delete host while services still reference it");
        }
        hostConfigRepository.delete(host);
    }

    /**
     * Loads a host by identifier or raises a REST 404 error.
     *
     * @param id host identifier
     * @return host configuration
     */
    public HostConfig getHost(Long id) {
        return hostConfigRepository.findById(id)
                .orElseThrow(() -> notFound("Host not found: " + id));
    }

    /**
     * Returns all logical service groups ordered by name.
     *
     * @return ordered service groups
     */
    public List<ServiceGroup> getGroups() {
        return serviceGroupRepository.findAllByOrderByNameAsc();
    }

    /**
     * Creates a new logical group after checking name uniqueness.
     *
     * @param request validated group payload
     * @return persisted service group
     */
    public ServiceGroup createGroup(GroupRequest request) {
        if (serviceGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Group with name '" + request.name() + "' already exists");
        }

        ServiceGroup group = new ServiceGroup();
        applyGroupRequest(group, request);
        return serviceGroupRepository.save(group);
    }

    /**
     * Updates an existing logical group after checking name uniqueness.
     *
     * @param id group identifier
     * @param request validated replacement payload
     * @return persisted service group
     */
    public ServiceGroup updateGroup(Long id, GroupRequest request) {
        ServiceGroup group = getGroup(id);
        if (!group.getName().equalsIgnoreCase(request.name())
                && serviceGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Group with name '" + request.name() + "' already exists");
        }

        applyGroupRequest(group, request);
        return serviceGroupRepository.save(group);
    }

    /**
     * Deletes a group if no monitored services still reference it.
     *
     * @param id group identifier
     */
    public void deleteGroup(Long id) {
        ServiceGroup group = getGroup(id);
        if (monitoredServiceRepository.countByGroupId(group.getId()) > 0) {
            throw badRequest("Cannot delete group while services still reference it");
        }
        serviceGroupRepository.delete(group);
    }

    /**
     * Loads a group by identifier or raises a REST 404 error.
     *
     * @param id group identifier
     * @return service group
     */
    public ServiceGroup getGroup(Long id) {
        return serviceGroupRepository.findById(id)
                .orElseThrow(() -> notFound("Group not found: " + id));
    }

    /**
     * Returns monitored services, optionally filtered by group identifiers.
     *
     * @param groupIds optional group identifiers used as a filter
     * @return ordered monitored services
     */
    public List<MonitoredService> getServices(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return monitoredServiceRepository.findAllByOrderByNameAsc();
        }
        return monitoredServiceRepository.findAllByGroupIdInOrderByNameAsc(groupIds);
    }

    /**
     * Loads a monitored service by identifier or raises a REST 404 error.
     *
     * @param id service identifier
     * @return monitored service definition
     */
    public MonitoredService getService(Long id) {
        return monitoredServiceRepository.findById(id)
                .orElseThrow(() -> notFound("Service not found: " + id));
    }

    /**
     * Creates a new monitored service after checking name uniqueness.
     *
     * @param request validated service payload
     * @return persisted monitored service
     */
    public MonitoredService createService(MonitoredServiceRequest request) {
        if (monitoredServiceRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Service with name '" + request.name() + "' already exists");
        }

        MonitoredService service = new MonitoredService();
        applyServiceRequest(service, request);
        return monitoredServiceRepository.save(service);
    }

    /**
     * Updates an existing monitored service after checking name uniqueness.
     *
     * @param id service identifier
     * @param request validated replacement payload
     * @return persisted monitored service
     */
    public MonitoredService updateService(Long id, MonitoredServiceRequest request) {
        MonitoredService service = getService(id);
        if (!service.getName().equalsIgnoreCase(request.name())
                && monitoredServiceRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Service with name '" + request.name() + "' already exists");
        }

        applyServiceRequest(service, request);
        return monitoredServiceRepository.save(service);
    }

    /**
     * Updates only the automatic monitoring flag of a service.
     *
     * @param id service identifier
     * @param enabled requested monitoring flag
     * @return persisted monitored service
     */
    public MonitoredService setMonitoringEnabled(Long id, boolean enabled) {
        MonitoredService service = getService(id);
        service.setMonitoringEnabled(enabled);
        return monitoredServiceRepository.save(service);
    }

    /**
     * Deletes a monitored service definition.
     *
     * @param id service identifier
     */
    public void deleteService(Long id) {
        monitoredServiceRepository.delete(getService(id));
    }

    /**
     * Applies normalized request fields to a host entity.
     *
     * <p>Remote hosts must provide SSH user and private key path; local hosts keep
     * those fields optional because commands run on the watcher machine.
     *
     * @param host target host entity
     * @param request source request payload
     */
    private void applyHostRequest(HostConfig host, HostRequest request) {
        host.setName(request.name().trim());
        host.setConnectionMode(request.connectionMode());
        host.setAddress(blankToDefault(request.address(), "127.0.0.1"));
        host.setSshPort(request.sshPort());
        host.setDescription(trimToNull(request.description()));

        if (request.connectionMode() == HostConnectionMode.LOCAL) {
            host.setSshUser(trimToNull(request.sshUser()));
            host.setPrivateKeyPath(trimToNull(request.privateKeyPath()));
            return;
        }

        if (request.sshUser() == null || request.sshUser().isBlank()) {
            throw badRequest("sshUser is required for SSH hosts");
        }
        if (request.privateKeyPath() == null || request.privateKeyPath().isBlank()) {
            throw badRequest("privateKeyPath is required for SSH hosts");
        }

        host.setSshUser(request.sshUser().trim());
        host.setPrivateKeyPath(request.privateKeyPath().trim());
    }

    /**
     * Applies normalized request fields to a group entity.
     *
     * @param group target group entity
     * @param request source request payload
     */
    private void applyGroupRequest(ServiceGroup group, GroupRequest request) {
        group.setName(request.name().trim());
        group.setDescription(trimToNull(request.description()));
    }

    /**
     * Applies normalized request fields and resolved relationships to a service entity.
     *
     * @param service target monitored service entity
     * @param request source request payload
     */
    private void applyServiceRequest(MonitoredService service, MonitoredServiceRequest request) {
        HostConfig host = getHost(request.hostId());
        ServiceGroup group = request.groupId() != null ? getGroup(request.groupId()) : null;

        service.setName(request.name().trim());
        service.setHost(host);
        service.setGroup(group);
        service.setProcessMatch(request.processMatch().trim());
        service.setRestartCommand(request.restartCommand().trim());
        service.setHealthUrl(trimToNull(request.healthUrl()));
        service.setHealthTimeoutSeconds(request.healthTimeoutSeconds());
        service.setRestartCooldownSeconds(request.restartCooldownSeconds());
        service.setRestartWindowSeconds(request.restartWindowSeconds());
        service.setMaxRestartsInWindow(request.maxRestartsInWindow());
        service.setMonitoringEnabled(request.monitoringEnabled());
        service.setDescription(trimToNull(request.description()));
    }

    /**
     * Trims a value or returns the provided default for blank input.
     *
     * @param value candidate input value
     * @param defaultValue fallback value used when input is blank
     * @return trimmed value or fallback value
     */
    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * Trims a value and converts blank input to {@code null}.
     *
     * @param value candidate input value
     * @return trimmed value or {@code null} when input is blank
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Creates a REST 404 exception.
     *
     * @param message response error message
     * @return exception carrying {@link HttpStatus#NOT_FOUND}
     */
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    /**
     * Creates a REST 400 exception.
     *
     * @param message response error message
     * @return exception carrying {@link HttpStatus#BAD_REQUEST}
     */
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Creates a REST 409 exception.
     *
     * @param message response error message
     * @return exception carrying {@link HttpStatus#CONFLICT}
     */
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
