package com.example.guardian.api;

import com.example.guardian.model.HostConfig;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceGroup;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import org.springframework.stereotype.Component;

@Component
public class ApiMapper {

    public HostResponse toHostResponse(HostConfig host) {
        return new HostResponse(
                host.getId(),
                host.getName(),
                host.getConnectionMode(),
                host.getAddress(),
                host.getSshPort(),
                host.getSshUser(),
                host.getPrivateKeyPath(),
                host.getDescription()
        );
    }

    public GroupResponse toGroupResponse(ServiceGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription()
        );
    }

    public MonitoredServiceResponse toServiceResponse(MonitoredService service) {
        return new MonitoredServiceResponse(
                service.getId(),
                service.getName(),
                service.getHost().getId(),
                service.getHost().getName(),
                service.getHost().getConnectionMode(),
                service.getHost().getAddress(),
                service.getGroup() != null ? service.getGroup().getId() : null,
                service.getGroup() != null ? service.getGroup().getName() : null,
                service.getProcessMatch(),
                service.getRestartCommand(),
                service.getHealthUrl(),
                service.getHealthTimeoutSeconds(),
                service.getRestartCooldownSeconds(),
                service.getRestartWindowSeconds(),
                service.getMaxRestartsInWindow(),
                service.isMonitoringEnabled(),
                service.getDescription()
        );
    }

    public DashboardServiceResponse toDashboardServiceResponse(MonitoredService service,
                                                               ServiceRuntimeSnapshot snapshot) {
        ServiceHealthStatus status = snapshot != null
                ? snapshot.status()
                : service.isMonitoringEnabled() ? ServiceHealthStatus.UNKNOWN : ServiceHealthStatus.PAUSED;

        return new DashboardServiceResponse(
                service.getId(),
                service.getName(),
                service.getHost().getId(),
                service.getHost().getName(),
                service.getHost().getConnectionMode(),
                service.getHost().getAddress(),
                service.getGroup() != null ? service.getGroup().getId() : null,
                service.getGroup() != null ? service.getGroup().getName() : null,
                service.isMonitoringEnabled(),
                status,
                snapshot != null && snapshot.processRunning(),
                snapshot != null && snapshot.healthCheckPassed(),
                snapshot != null ? snapshot.lastMessage() : "Service has not been checked yet",
                snapshot != null ? snapshot.lastCheckAt() : null,
                snapshot != null ? snapshot.lastRestartAt() : null
        );
    }
}
