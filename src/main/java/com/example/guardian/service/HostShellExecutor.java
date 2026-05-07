package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
import com.example.guardian.config.MonitorProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Выполняет shell-команды либо локально, либо на удаленном хосте через SSH.
 *
 * <p>Для режима {@code SSH} используется системный клиент {@code ssh}, поэтому
 * на хосте, где запущен мониторинг, он должен быть установлен и доступен в PATH.
 * Аутентификация реализована через ключ, путь к которому хранится в конфигурации
 * хоста.
 */
@Component
public class HostShellExecutor {

    private final CommandExecutor commandExecutor;
    private final MonitorProperties monitorProperties;

    /**
     * Creates a host-aware shell command executor.
     *
     * @param commandExecutor low-level command execution component
     */
    public HostShellExecutor(CommandExecutor commandExecutor, MonitorProperties monitorProperties) {
        this.commandExecutor = commandExecutor;
        this.monitorProperties = monitorProperties;
    }

    /**
     * Executes a shell command on the configured host.
     *
     * <p>Local hosts run through {@code bash -lc}; remote hosts run through the
     * system SSH client with non-interactive options and optional key authentication.
     *
     * @param host target host configuration
     * @param shellCommand shell command to execute
     * @param timeout maximum execution time
     * @return structured command execution result
     */
    public CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout) {
        if (host.isLocal()) {
            return commandExecutor.execute(List.of("bash", "-lc", shellCommand), timeout);
        }

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
     * Quotes a value so it can be passed as a single shell argument.
     *
     * @param value raw shell argument value
     * @return safely single-quoted shell argument
     */
    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
