package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class SystemSshCommandProvider implements SshCommandProvider {

    private final CommandExecutor commandExecutor;
    private final MonitorProperties monitorProperties;

    public SystemSshCommandProvider(CommandExecutor commandExecutor,
                                    MonitorProperties monitorProperties) {
        this.commandExecutor = commandExecutor;
        this.monitorProperties = monitorProperties;
    }

    @Override
    public MonitorProperties.Ssh.Provider provider() {
        return MonitorProperties.Ssh.Provider.SYSTEM;
    }

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

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
