package com.example.guardian.api;

import com.example.guardian.model.HostConfig;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceGroup;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import org.springframework.stereotype.Component;

/**
 * Converts domain entities and runtime snapshots into REST DTOs.
 *
 * <p>The mapper keeps controllers focused on transport concerns and centralizes
 * response-shaping rules used by both CRUD endpoints and the dashboard.
 */
@Component
public class ApiMapper {

    /**
     * Maps a host entity to its REST representation.
     *
     * @param host persisted host configuration
     * @return serialized host payload for API responses
     */
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

    /**
     * Maps a logical service group to its REST representation.
     *
     * @param group persisted service group
     * @return serialized group payload for API responses
     */
    public GroupResponse toGroupResponse(ServiceGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription()
        );
    }

    /**
     * Maps a monitored service configuration to the DTO used by CRUD endpoints.
     *
     * @param service persisted monitored service entity
     * @return serialized service configuration payload
     */
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

    /**
     * Builds a dashboard row by combining static configuration with runtime state.
     *
     * <p>If no runtime snapshot exists yet, the mapper derives a neutral status from
     * the monitoring flag so the UI can distinguish paused services from unchecked ones.
     *
     * @param service monitored service configuration
     * @param snapshot latest in-memory runtime state, or {@code null} when absent
     * @return dashboard view model for the service
     */
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
