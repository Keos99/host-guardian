package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to create or update a monitored host.
 *
 * @param name unique host name used in the UI and API
 * @param connectionMode command execution mode for the host
 * @param address host address or loopback alias
 * @param sshPort SSH port used when the host is remote
 * @param sshUser SSH user used when the host is remote
 * @param privateKeyPath path to the private key used for remote access
 * @param description optional free-form host description
 */
@Schema(description = "Payload used to create or update a host configuration.")
public record HostRequest(
        @Schema(description = "Unique host name.", example = "Production host", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "How commands should be executed on the host.", example = "SSH", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull HostConnectionMode connectionMode,

        @Schema(description = "Host address. Defaults to 127.0.0.1 when omitted.", example = "10.10.0.15", nullable = true)
        String address,

        @Schema(description = "SSH port for remote hosts.", example = "22", minimum = "1", maximum = "65535")
        @Min(1) @Max(65535) int sshPort,

        @Schema(description = "SSH username required for SSH hosts.", example = "deploy", nullable = true)
        String sshUser,

        @Schema(description = "Path to the private key required for SSH hosts.", example = "/home/app/.ssh/id_ed25519", nullable = true)
        String privateKeyPath,

        @Schema(description = "Optional host description.", example = "Primary Linux host for payment services.", nullable = true)
        String description
) {
}
