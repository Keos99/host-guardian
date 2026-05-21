package com.example.guardian.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to create or update a monitored service definition.
 *
 * @param name unique service name
 * @param hostId identifier of the host where the service runs
 * @param groupId optional identifier of the logical group that owns the service
 * @param processMatch pattern used by {@code pgrep -af} to locate the process
 * @param executionPath optional directory used as working path before start or manual restart commands
 * @param startCommand shell command used to start the service
 * @param manualRestartEnabled whether the operator provides a custom restart command
 * @param restartCommand optional manual restart command
 * @param healthUrl optional HTTP endpoint used for additional health validation
 * @param healthTimeoutSeconds timeout for the health-check request in seconds
 * @param restartCooldownSeconds minimum delay between restart attempts
 * @param restartWindowSeconds rolling window used to enforce restart limits
 * @param maxRestartsInWindow maximum number of restarts allowed in the window
 * @param monitoringEnabled whether periodic monitoring is active for the service
 * @param description optional service description shown in the UI
 */
@Schema(description = "Payload used to create or update a monitored service definition.")
public record MonitoredServiceRequest(
        @Schema(description = "Unique service name.", example = "billing-api", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "Identifier of the host where the service runs.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long hostId,

        @Schema(description = "Optional identifier of the logical service group.", example = "2", nullable = true)
        Long groupId,

        @Schema(description = "Pattern passed to pgrep -af to locate the service process.", example = "billing-api.jar", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String processMatch,

        @Schema(description = "Optional directory used as working path before start or manual restart commands.", example = "/opt/billing-api", nullable = true)
        String executionPath,

        @Schema(description = "Shell command executed to start the service.", example = "systemctl start billing-api", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String startCommand,

        @Schema(description = "When true, restartCommand is executed before startCommand. When false, the monitor kills the matched process before startCommand.", example = "false")
        boolean manualRestartEnabled,

        @Schema(description = "Optional manual restart command executed before the start command when manualRestartEnabled is true.", example = "systemctl stop billing-api", nullable = true)
        String restartCommand,

        @Schema(description = "Optional HTTP health endpoint checked after process lookup.", example = "http://127.0.0.1:8080/actuator/health", nullable = true)
        String healthUrl,

        @Schema(description = "Health-check timeout in seconds.", example = "3", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Long healthTimeoutSeconds,

        @Schema(description = "Minimum delay between automatic restart attempts in seconds.", example = "60", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Long restartCooldownSeconds,

        @Schema(description = "Rolling window size for restart limits in seconds.", example = "600", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Long restartWindowSeconds,

        @Schema(description = "Maximum automatic restarts allowed inside the rolling window.", example = "3", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Integer maxRestartsInWindow,

        @Schema(description = "Whether periodic monitoring is active for the service.", example = "true")
        boolean monitoringEnabled,

        @Schema(description = "Optional service description.", example = "Main billing backend process.", nullable = true)
        String description
) {
}
