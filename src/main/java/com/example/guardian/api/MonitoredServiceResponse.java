package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;

/**
 * REST representation of a monitored service configuration.
 *
 * @param id persistent service identifier
 * @param name service display name
 * @param hostId identifier of the host where the service runs
 * @param hostName display name of the host
 * @param hostConnectionMode host execution mode
 * @param hostAddress host address or loopback alias
 * @param groupId optional identifier of the logical service group
 * @param groupName optional name of the logical service group
 * @param processMatch process lookup pattern
 * @param restartCommand restart command stored for the service
 * @param healthUrl optional health endpoint URL
 * @param healthTimeoutSeconds health-check timeout in seconds
 * @param restartCooldownSeconds cooldown between restart attempts in seconds
 * @param restartWindowSeconds rolling restart window size in seconds
 * @param maxRestartsInWindow maximum allowed restarts in the rolling window
 * @param monitoringEnabled whether automatic monitoring is enabled
 * @param description optional service description
 */
public record MonitoredServiceResponse(
        Long id,
        String name,
        Long hostId,
        String hostName,
        HostConnectionMode hostConnectionMode,
        String hostAddress,
        Long groupId,
        String groupName,
        String processMatch,
        String restartCommand,
        String healthUrl,
        long healthTimeoutSeconds,
        long restartCooldownSeconds,
        long restartWindowSeconds,
        int maxRestartsInWindow,
        boolean monitoringEnabled,
        String description
) {
}
