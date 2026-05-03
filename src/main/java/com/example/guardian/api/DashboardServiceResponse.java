package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.ServiceHealthStatus;

import java.time.Instant;

/**
 * Dashboard projection for one monitored service.
 *
 * @param id unique service identifier
 * @param name display name of the monitored service
 * @param hostId identifier of the host where the service runs
 * @param hostName display name of the host
 * @param hostConnectionMode mode used to execute commands on the host
 * @param hostAddress host address or loopback alias
 * @param groupId optional identifier of the logical service group
 * @param groupName optional name of the logical service group
 * @param monitoringEnabled flag showing whether automatic monitoring is active
 * @param status last calculated service health status
 * @param processRunning whether the service process was found during the last check
 * @param healthCheckPassed whether the last HTTP health-check succeeded
 * @param lastMessage human-readable explanation of the current state
 * @param lastCheckAt timestamp of the latest monitoring pass
 * @param lastRestartAt timestamp of the latest successful restart command
 */
public record DashboardServiceResponse(
        Long id,
        String name,
        Long hostId,
        String hostName,
        HostConnectionMode hostConnectionMode,
        String hostAddress,
        Long groupId,
        String groupName,
        boolean monitoringEnabled,
        ServiceHealthStatus status,
        boolean processRunning,
        boolean healthCheckPassed,
        String lastMessage,
        Instant lastCheckAt,
        Instant lastRestartAt
) {
}
