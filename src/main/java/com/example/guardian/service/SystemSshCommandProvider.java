package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * SSH provider backed by the operating system {@code ssh} executable.
 *
 * <p>This provider preserves the original command execution behavior. It builds
 * a non-interactive {@code ssh} invocation and delegates process execution to
 * {@link CommandExecutor}.
 */
@Component
public class SystemSshCommandProvider implements SshCommandProvider {

    private final CommandExecutor commandExecutor;
    private final MonitorProperties monitorProperties;

    /**
     * Creates a provider that runs SSH through the system command line client.
     *
     * @param commandExecutor low-level process executor
     * @param monitorProperties global monitoring and SSH timeout settings
     */
    public SystemSshCommandProvider(CommandExecutor commandExecutor,
                                    MonitorProperties monitorProperties) {
        this.commandExecutor = commandExecutor;
        this.monitorProperties = monitorProperties;
    }

    @Override
    public MonitorProperties.Ssh.Provider provider() {
        return MonitorProperties.Ssh.Provider.SYSTEM;
    }

    /**
     * Builds and runs a system {@code ssh} command for a remote host.
     *
     * @param host remote host configuration
     * @param shellCommand command body to run inside {@code bash -lc}
     * @param timeout maximum process execution time
     * @return captured command result from the system SSH process
     */
    @Override
    public CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout) {
        List<String> command = new ArrayList<>();
        command.add("ssh");
        command.add("-o");
        command.add("BatchMode=yes");
        command.add("-o");
        command.add("ConnectTimeout=" + Math.max(1, monitorProperties.getCommand().getSshConnectTimeout().toSeconds()));
        command.add("-p");
        command.add(String.valueOf(host.getSshPort()));

        if (host.getPrivateKeyPath() != null && !host.getPrivateKeyPath().isBlank()) {
            command.add("-i");
            command.add(host.getPrivateKeyPath());
        }

        command.add(host.getSshUser() + "@" + host.getAddress());
        command.add("bash -lc " + shellQuote(shellCommand));
        return commandExecutor.execute(command, timeout);
    }

    /**
     * Quotes a value so it can be passed as one shell argument.
     *
     * @param value raw shell argument
     * @return safely single-quoted shell argument
     */
    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
