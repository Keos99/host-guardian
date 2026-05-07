package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;

import java.time.Duration;

/**
 * Executes shell commands on a remote host through a concrete SSH implementation.
 *
 * <p>Implementations hide transport details from {@link HostShellExecutor}.
 * The executor selects one provider using {@link MonitorProperties.Ssh#getProvider()}.
 */
public interface SshCommandProvider {

    /**
     * Returns the configuration value handled by this provider.
     *
     * @return SSH provider identifier supported by this implementation
     */
    MonitorProperties.Ssh.Provider provider();

    /**
     * Executes a shell command on the given remote host.
     *
     * @param host remote host configuration
     * @param shellCommand command body to run inside {@code bash -lc}
     * @param timeout maximum command execution time
     * @return exit code, stdout, and stderr captured from the command execution
     */
    CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout);
}
