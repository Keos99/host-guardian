package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Configured monitored service with resolved host and group details.")
public record MonitoredServiceResponse(
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

        @Schema(description = "Pattern passed to pgrep -af to locate the service process.", example = "billing-api.jar")
        String processMatch,

        @Schema(description = "Shell command executed to restart the service.", example = "systemctl restart billing-api")
        String restartCommand,

        @Schema(description = "Optional HTTP health endpoint.", example = "http://127.0.0.1:8080/actuator/health", nullable = true)
        String healthUrl,

        @Schema(description = "Health-check timeout in seconds.", example = "3")
        long healthTimeoutSeconds,

        @Schema(description = "Minimum delay between automatic restart attempts in seconds.", example = "60")
        long restartCooldownSeconds,

        @Schema(description = "Rolling window size for restart limits in seconds.", example = "600")
        long restartWindowSeconds,

        @Schema(description = "Maximum automatic restarts allowed inside the rolling window.", example = "3")
        int maxRestartsInWindow,

        @Schema(description = "Whether automatic monitoring is enabled.", example = "true")
        boolean monitoringEnabled,

        @Schema(description = "Optional service description.", example = "Main billing backend process.", nullable = true)
        String description
) {
}
