package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Component for process lookup and process termination on Linux hosts.
 */
@Component
public class LinuxProcessInspector {

    private final HostShellExecutor hostShellExecutor;
    private final MonitorProperties monitorProperties;

    public LinuxProcessInspector(HostShellExecutor hostShellExecutor,
                                 MonitorProperties monitorProperties) {
        this.hostShellExecutor = hostShellExecutor;
        this.monitorProperties = monitorProperties;
    }

    public boolean isRunning(HostConfig host, String processMatch) {
        return findFirst(host, processMatch).isPresent();
    }

    public Optional<ProcessInfo> findFirst(HostConfig host, String processMatch) {
        CommandExecutor.CommandResult result = hostShellExecutor.execute(
                host,
                "pgrep -af \"" + escapeDoubleQuoted(processMatch) + "\"",
                monitorProperties.getCommand().getProcessLookupTimeout()
        );

        if (!result.success() || result.output() == null || result.output().isBlank()) {
            return Optional.empty();
        }

        return result.output().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::parseProcessInfo)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public boolean isPidRunning(HostConfig host, Long pid) {
        if (pid == null || pid <= 0) {
            return false;
        }

        CommandExecutor.CommandResult result = hostShellExecutor.execute(
                host,
                "kill -0 " + pid,
                monitorProperties.getCommand().getPidCheckTimeout()
        );
        return result.success();
    }

    public CommandExecutor.CommandResult stop(HostConfig host,
                                              String processMatch,
                                              Long knownPid,
                                              Duration gracefulTimeout) {
        long waitSeconds = Math.max(1, gracefulTimeout.toSeconds());
        String pattern = shellQuote(processMatch);
        String knownPidText = knownPid != null && knownPid > 0 ? knownPid.toString() : "";
        String command = "pids=$(pgrep -f " + pattern + " || true)\n"
                + "known_pid=" + shellQuote(knownPidText) + "\n"
                + "if [ -n \"$known_pid\" ] && kill -0 \"$known_pid\" 2>/dev/null; then\n"
                + "  if ps -p \"$known_pid\" -o args= 2>/dev/null | grep -F -- " + pattern + " >/dev/null; then\n"
                + "    pids=\"$pids $known_pid\"\n"
                + "  fi\n"
                + "fi\n"
                + "pids=$(printf '%s\\n' $pids | awk 'NF && !seen[$1]++')\n"
                + "if [ -z \"$pids\" ]; then exit 0; fi\n"
                + "kill $pids 2>/dev/null || true\n"
                + "deadline=$((SECONDS+" + waitSeconds + "))\n"
                + "while [ $SECONDS -lt $deadline ]; do\n"
                + "  alive=\"\"\n"
                + "  for pid in $pids; do\n"
                + "    if kill -0 \"$pid\" 2>/dev/null; then alive=\"$alive $pid\"; fi\n"
                + "  done\n"
                + "  if [ -z \"$alive\" ]; then exit 0; fi\n"
                + "  sleep 1\n"
                + "done\n"
                + "if [ -n \"$alive\" ]; then kill -9 $alive 2>/dev/null || true; fi";

        return hostShellExecutor.execute(
                host,
                command,
                gracefulTimeout.plus(monitorProperties.getCommand().getStopCommandExtraTimeout())
        );
    }

    private Optional<ProcessInfo> parseProcessInfo(String line) {
        int separator = line.indexOf(' ');
        String pidText = separator >= 0 ? line.substring(0, separator) : line;
        try {
            long pid = Long.parseLong(pidText);
            String commandLine = separator >= 0 ? line.substring(separator + 1).trim() : "";
            return Optional.of(new ProcessInfo(pid, commandLine));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String escapeDoubleQuoted(String value) {
        return value.replace("\"", "\\\"");
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public record ProcessInfo(long pid, String commandLine) {
    }
}
