package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
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
    private final List<SshCommandProvider> sshProviders;
    private final MonitorProperties monitorProperties;

    /**
     * Creates a host-aware shell command executor.
     *
     * @param commandExecutor low-level command execution component
     */
    public HostShellExecutor(CommandExecutor commandExecutor,
                             List<SshCommandProvider> sshProviders,
                             MonitorProperties monitorProperties) {
        this.commandExecutor = commandExecutor;
        this.sshProviders = sshProviders;
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

        return sshProviders.stream()
                .filter(provider -> provider.provider() == monitorProperties.getSsh().getProvider())
                .findFirst()
                .map(provider -> provider.execute(host, shellCommand, timeout))
                .orElseGet(() -> new CommandExecutor.CommandResult(
                        -1,
                        "",
                        "SSH provider is not available: " + monitorProperties.getSsh().getProvider()
                ));
    }
}
