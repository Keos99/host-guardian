package com.example.guardian.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable snapshot of the processes running on a single host, captured by one
 * command and matched locally afterwards.
 *
 * <p>Instead of running {@code pgrep} once per monitored service, the monitor takes
 * a single process listing per host and tests every service's {@code processMatch}
 * against it in memory. This collapses N remote round trips into one and keeps the
 * matching semantics close to {@code pgrep -f}: the pattern is applied as a regular
 * expression searched anywhere in the full command line, with a literal-substring
 * fallback when the pattern is not a valid regex.
 *
 * <p>A snapshot only ever contains live processes (the listing comes from
 * {@code ps}), so presence in the snapshot already means the process is running and
 * no extra liveness probe is needed.
 */
public final class ProcessSnapshot {

    private final boolean available;
    private final List<LinuxProcessInspector.ProcessInfo> processes;

    private ProcessSnapshot(boolean available, List<LinuxProcessInspector.ProcessInfo> processes) {
        this.available = available;
        this.processes = processes;
    }

    /**
     * Creates a snapshot from a successfully captured process list.
     *
     * @param processes live processes on the host
     * @return populated, available snapshot
     */
    public static ProcessSnapshot of(List<LinuxProcessInspector.ProcessInfo> processes) {
        return new ProcessSnapshot(true, List.copyOf(processes));
    }

    /**
     * Creates a snapshot for a host whose process list could not be captured.
     *
     * @return unavailable, empty snapshot
     */
    public static ProcessSnapshot unavailable() {
        return new ProcessSnapshot(false, List.of());
    }

    /**
     * Checks whether the process list was captured successfully.
     *
     * @return {@code true} when the snapshot reflects a real listing
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Finds the first process whose command line matches the pattern.
     *
     * @param processMatch pattern applied to the full command line
     * @return first matching process, or empty when none matches
     */
    public Optional<LinuxProcessInspector.ProcessInfo> findFirst(String processMatch) {
        Predicate<String> matcher = matcher(processMatch);
        return processes.stream()
                .filter(process -> matcher.test(process.commandLine()))
                .findFirst();
    }

    /**
     * Builds a command-line matcher with {@code pgrep -f}-like semantics.
     *
     * @param processMatch raw pattern from the service configuration
     * @return predicate that matches a command line as a regex, or by substring
     * when the pattern is not a valid regular expression
     */
    private Predicate<String> matcher(String processMatch) {
        try {
            Pattern pattern = Pattern.compile(processMatch);
            return commandLine -> pattern.matcher(commandLine).find();
        } catch (PatternSyntaxException e) {
            return commandLine -> commandLine.contains(processMatch);
        }
    }
}
