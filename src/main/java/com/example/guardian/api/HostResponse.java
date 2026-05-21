package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * REST representation of a configured host.
 *
 * @param id persistent host identifier
 * @param name host display name
 * @param connectionMode command execution mode for the host
 * @param address host address or loopback alias
 * @param sshPort SSH port configured for remote access
 * @param sshUser SSH user configured for remote access
 * @param privateKeyPath path to the configured private key
 * @param description optional host description
 */
@Schema(description = "Configured host where monitored services can run.")
public record HostResponse(
        @Schema(description = "Persistent host identifier.", example = "1")
        Long id,

        @Schema(description = "Host display name.", example = "Production host")
        String name,

        @Schema(description = "How commands are executed on the host.", example = "SSH")
        HostConnectionMode connectionMode,

        @Schema(description = "Host address used for local or SSH access.", example = "10.10.0.15")
        String address,

        @Schema(description = "SSH port configured for remote access.", example = "22")
        int sshPort,

        @Schema(description = "SSH username configured for remote access.", example = "deploy", nullable = true)
        String sshUser,

        @Schema(description = "Path to the configured private key.", example = "/home/app/.ssh/id_ed25519", nullable = true)
        String privateKeyPath,

        @Schema(description = "Optional host description.", example = "Primary Linux host for payment services.", nullable = true)
        String description
) {
}
