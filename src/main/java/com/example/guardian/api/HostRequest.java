package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HostRequest(
        @NotBlank String name,
        @NotNull HostConnectionMode connectionMode,
        String address,
        @Min(1) @Max(65535) int sshPort,
        String sshUser,
        String privateKeyPath,
        String description
) {
}
