package com.example.guardian.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Describes how the watcher should execute commands on a host.
 */
@Schema(description = "Mode used by the watcher to execute commands on a host.")
public enum HostConnectionMode {
    /**
     * Executes commands on the same machine as the watcher process.
     */
    LOCAL,

    /**
     * Executes commands on a remote machine using SSH.
     */
    SSH
}
