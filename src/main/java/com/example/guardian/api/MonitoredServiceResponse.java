package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;

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
