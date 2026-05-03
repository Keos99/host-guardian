package com.example.guardian.api;

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
 * @param restartCommand shell command used to restart the service
 * @param healthUrl optional HTTP endpoint used for additional health validation
 * @param healthTimeoutSeconds timeout for the health-check request in seconds
 * @param restartCooldownSeconds minimum delay between restart attempts
 * @param restartWindowSeconds rolling window used to enforce restart limits
 * @param maxRestartsInWindow maximum number of restarts allowed in the window
 * @param monitoringEnabled whether periodic monitoring is active for the service
 * @param description optional service description shown in the UI
 */
public record MonitoredServiceRequest(
        @NotBlank String name,
        @NotNull Long hostId,
        Long groupId,
        @NotBlank String processMatch,
        @NotBlank String restartCommand,
        String healthUrl,
        @NotNull @Min(1) Long healthTimeoutSeconds,
        @NotNull @Min(1) Long restartCooldownSeconds,
        @NotNull @Min(1) Long restartWindowSeconds,
        @NotNull @Min(1) Integer maxRestartsInWindow,
        boolean monitoringEnabled,
        String description
) {
}
