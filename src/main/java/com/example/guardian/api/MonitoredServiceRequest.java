package com.example.guardian.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
