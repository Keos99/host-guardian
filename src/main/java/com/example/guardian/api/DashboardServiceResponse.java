package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.ServiceHealthStatus;

import java.time.Instant;

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
