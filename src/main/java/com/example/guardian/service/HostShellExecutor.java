package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
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

    public HostShellExecutor(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout) {
        if (host.isLocal()) {
            return commandExecutor.execute(List.of("bash", "-lc", shellCommand), timeout);
        }

        List<String> command = new ArrayList<>();
        command.add("ssh");
        command.add("-o");
        command.add("BatchMode=yes");
        command.add("-o");
        command.add("ConnectTimeout=5");
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
