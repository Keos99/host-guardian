package com.example.guardian.api;

import com.example.guardian.model.HostConnectionMode;

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
