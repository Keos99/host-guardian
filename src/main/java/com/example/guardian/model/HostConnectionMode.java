package com.example.guardian.model;

/**
 * Describes how the watcher should execute commands on a host.
 */
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
