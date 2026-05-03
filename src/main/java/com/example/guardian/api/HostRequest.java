package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;
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
