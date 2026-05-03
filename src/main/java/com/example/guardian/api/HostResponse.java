package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;

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
public record HostResponse(
        Long id,
        String name,
        HostConnectionMode connectionMode,
        String address,
        int sshPort,
        String sshUser,
        String privateKeyPath,
        String description
) {
}
