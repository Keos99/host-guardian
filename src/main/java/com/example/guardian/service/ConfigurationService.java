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

@Service
public class ConfigurationService {

    private final HostConfigRepository hostConfigRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    public ConfigurationService(HostConfigRepository hostConfigRepository,
                                ServiceGroupRepository serviceGroupRepository,
                                MonitoredServiceRepository monitoredServiceRepository) {
        this.hostConfigRepository = hostConfigRepository;
        this.serviceGroupRepository = serviceGroupRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
    }

    public List<HostConfig> getHosts() {
        return hostConfigRepository.findAllByOrderByNameAsc();
    }

    public HostConfig createHost(HostRequest request) {
        if (hostConfigRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Host with name '" + request.name() + "' already exists");
        }

        HostConfig host = new HostConfig();
        applyHostRequest(host, request);
        return hostConfigRepository.save(host);
    }

    public HostConfig updateHost(Long id, HostRequest request) {
        HostConfig host = getHost(id);
        if (!host.getName().equalsIgnoreCase(request.name())
                && hostConfigRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Host with name '" + request.name() + "' already exists");
        }

        applyHostRequest(host, request);
        return hostConfigRepository.save(host);
    }

    public void deleteHost(Long id) {
        HostConfig host = getHost(id);
        if (monitoredServiceRepository.countByHostId(host.getId()) > 0) {
            throw badRequest("Cannot delete host while services still reference it");
        }
        hostConfigRepository.delete(host);
    }

    public HostConfig getHost(Long id) {
        return hostConfigRepository.findById(id)
                .orElseThrow(() -> notFound("Host not found: " + id));
    }

    public List<ServiceGroup> getGroups() {
        return serviceGroupRepository.findAllByOrderByNameAsc();
    }

    public ServiceGroup createGroup(GroupRequest request) {
        if (serviceGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Group with name '" + request.name() + "' already exists");
        }

        ServiceGroup group = new ServiceGroup();
        applyGroupRequest(group, request);
        return serviceGroupRepository.save(group);
    }

    public ServiceGroup updateGroup(Long id, GroupRequest request) {
        ServiceGroup group = getGroup(id);
        if (!group.getName().equalsIgnoreCase(request.name())
                && serviceGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Group with name '" + request.name() + "' already exists");
        }

        applyGroupRequest(group, request);
        return serviceGroupRepository.save(group);
    }

    public void deleteGroup(Long id) {
        ServiceGroup group = getGroup(id);
        if (monitoredServiceRepository.countByGroupId(group.getId()) > 0) {
            throw badRequest("Cannot delete group while services still reference it");
        }
        serviceGroupRepository.delete(group);
    }

    public ServiceGroup getGroup(Long id) {
        return serviceGroupRepository.findById(id)
                .orElseThrow(() -> notFound("Group not found: " + id));
    }

    public List<MonitoredService> getServices(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return monitoredServiceRepository.findAllByOrderByNameAsc();
        }
        return monitoredServiceRepository.findAllByGroupIdInOrderByNameAsc(groupIds);
    }

    public MonitoredService getService(Long id) {
        return monitoredServiceRepository.findById(id)
                .orElseThrow(() -> notFound("Service not found: " + id));
    }

    public MonitoredService createService(MonitoredServiceRequest request) {
        if (monitoredServiceRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Service with name '" + request.name() + "' already exists");
        }

        MonitoredService service = new MonitoredService();
        applyServiceRequest(service, request);
        return monitoredServiceRepository.save(service);
    }

    public MonitoredService updateService(Long id, MonitoredServiceRequest request) {
        MonitoredService service = getService(id);
        if (!service.getName().equalsIgnoreCase(request.name())
                && monitoredServiceRepository.existsByNameIgnoreCase(request.name())) {
            throw conflict("Service with name '" + request.name() + "' already exists");
        }

        applyServiceRequest(service, request);
        return monitoredServiceRepository.save(service);
    }

    public MonitoredService setMonitoringEnabled(Long id, boolean enabled) {
        MonitoredService service = getService(id);
        service.setMonitoringEnabled(enabled);
        return monitoredServiceRepository.save(service);
    }

    public void deleteService(Long id) {
        monitoredServiceRepository.delete(getService(id));
    }

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

    private void applyGroupRequest(ServiceGroup group, GroupRequest request) {
        group.setName(request.name().trim());
        group.setDescription(trimToNull(request.description()));
    }

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

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
