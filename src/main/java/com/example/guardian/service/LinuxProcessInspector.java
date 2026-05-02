package com.example.guardian.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class LinuxProcessInspector {

    private final CommandExecutor commandExecutor;

    public LinuxProcessInspector(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public boolean isRunning(String processMatch) {
        CommandExecutor.CommandResult result = commandExecutor.execute(
                List.of("bash", "-lc", "pgrep -af \"" + escape(processMatch) + "\""),
                Duration.ofSeconds(5)
        );

        if (!result.success()) {
            return false;
        }

        String output = result.output();
        return output != null && !output.isBlank();
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
