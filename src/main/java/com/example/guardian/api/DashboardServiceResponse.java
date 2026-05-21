package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.ServiceHealthStatus;
import io.swagger.v3.oas.annotations.media.Schema;

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
 * @param lastKnownPid latest persisted or runtime PID
 * @param healthCheckEnabled whether the service has HTTP health-check URL configured
 * @param healthCheckPassed whether the last HTTP health-check succeeded
 * @param lastMessage human-readable explanation of the current state
 * @param lastCheckAt timestamp of the latest monitoring pass
 * @param lastRestartAt timestamp of the latest successful restart command
 */
@Schema(description = "Dashboard row that combines service configuration and latest runtime state.")
public record DashboardServiceResponse(
        @Schema(description = "Persistent service identifier.", example = "1")
        Long id,

        @Schema(description = "Service display name.", example = "billing-api")
        String name,

        @Schema(description = "Identifier of the host where the service runs.", example = "1")
        Long hostId,

        @Schema(description = "Display name of the host.", example = "Production host")
        String hostName,

        @Schema(description = "Command execution mode configured for the host.", example = "SSH")
        HostConnectionMode hostConnectionMode,

        @Schema(description = "Host address used for local or SSH access.", example = "10.10.0.15")
        String hostAddress,

        @Schema(description = "Optional identifier of the logical service group.", example = "2", nullable = true)
        Long groupId,

        @Schema(description = "Optional name of the logical service group.", example = "Payments", nullable = true)
        String groupName,

        @Schema(description = "Whether automatic monitoring is enabled.", example = "true")
        boolean monitoringEnabled,

        @Schema(description = "Latest calculated health status.", example = "UP")
        ServiceHealthStatus status,

        @Schema(description = "Whether the process was found during the latest check.", example = "true")
        boolean processRunning,

        @Schema(description = "Latest PID found by the monitor.", example = "1234", nullable = true)
        Long lastKnownPid,

        @Schema(description = "Whether HTTP health-check is configured for this service.", example = "true")
        boolean healthCheckEnabled,

        @Schema(description = "Whether the latest HTTP health-check passed.", example = "true")
        boolean healthCheckPassed,

        @Schema(description = "Human-readable explanation of the latest state.", example = "Service is healthy")
        String lastMessage,

        @Schema(description = "Timestamp of the latest monitoring pass.", example = "2026-05-03T19:45:30Z", nullable = true)
        Instant lastCheckAt,

        @Schema(description = "Timestamp of the latest successful restart command.", example = "2026-05-03T19:40:00Z", nullable = true)
        Instant lastRestartAt
) {
}
