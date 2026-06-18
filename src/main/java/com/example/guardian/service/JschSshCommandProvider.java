package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * SSH provider backed by the JSch Java library.
 *
 * <p>This provider avoids invoking the external {@code ssh} binary. Commands are
 * still executed on the remote host through {@code bash -lc} so process checks,
 * health checks, restart commands, and working-directory wrapping keep the same
 * shell semantics as the system SSH provider.
 *
 * <p>Sessions are borrowed from {@link JschSessionPool} and reused across commands;
 * only the lightweight {@code exec} channel is opened and closed per command, while
 * the underlying session (and its handshake cost) is shared.
 */
@Component
public class JschSshCommandProvider implements SshCommandProvider {

    private final JschSessionPool sessionPool;
    private final MonitorProperties monitorProperties;

    /**
     * Creates a JSch-backed SSH command provider.
     *
     * @param sessionPool pool that supplies reusable SSH sessions per host
     * @param monitorProperties global monitoring and SSH settings
     */
    public JschSshCommandProvider(JschSessionPool sessionPool,
                                  MonitorProperties monitorProperties) {
        this.sessionPool = sessionPool;
        this.monitorProperties = monitorProperties;
    }

    @Override
    public MonitorProperties.Ssh.Provider provider() {
        return MonitorProperties.Ssh.Provider.JSCH;
    }

    /**
     * Executes a remote command through a JSch {@code exec} channel.
     *
     * @param host remote host configuration
     * @param shellCommand command body to run inside {@code bash -lc}
     * @param timeout maximum command execution time
     * @return exit code, stdout, and stderr captured from the SSH channel
     */
    @Override
    public CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout) {
        try {
            return sessionPool.withSession(host, session -> runOnSession(session, shellCommand, timeout));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandExecutor.CommandResult(-1, "", e.getMessage());
        } catch (Exception e) {
            return new CommandExecutor.CommandResult(-1, "", e.getMessage());
        }
    }

    /**
     * Runs one command on an already connected, pooled session.
     *
     * <p>Only the {@code exec} channel is opened and closed here; the session is
     * owned by {@link JschSessionPool} and stays connected for the next command.
     * Transport failures propagate so the pool can reopen the session and retry.
     *
     * @param session pooled SSH session
     * @param shellCommand command body to run inside {@code bash -lc}
     * @param timeout maximum command execution time
     * @return exit code, stdout, and stderr captured from the channel
     * @throws Exception when the channel cannot be opened or read
     */
    private CommandExecutor.CommandResult runOnSession(Session session, String shellCommand, Duration timeout)
            throws Exception {
        ChannelExec channel = null;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("bash -lc " + shellQuote(shellCommand));
            InputStream outputStream = channel.getInputStream();
            channel.setErrStream(stderr);
            channel.connect(JschFacade.toTimeoutMillis(monitorProperties.getCommand().getSshConnectTimeout()));

            CommandExecutor.CommandResult timeoutResult = waitForCompletion(channel, outputStream, stdout, stderr, timeout);
            if (timeoutResult != null) {
                return timeoutResult;
            }

            drainAvailable(outputStream, stdout);
            return new CommandExecutor.CommandResult(
                    channel.getExitStatus(),
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.size() > 0 ? stderr.toString(StandardCharsets.UTF_8) : null
            );
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    /**
     * Waits until the channel closes or the command timeout expires.
     *
     * @param channel active JSch exec channel
     * @param outputStream channel stdout stream
     * @param stdout accumulated stdout buffer
     * @param stderr accumulated stderr buffer
     * @param timeout maximum command execution time
     * @return timeout result when the deadline is reached; otherwise {@code null}
     * @throws IOException when stdout cannot be read
     * @throws InterruptedException when the wait loop is interrupted
     */
    private CommandExecutor.CommandResult waitForCompletion(ChannelExec channel,
                                                            InputStream outputStream,
                                                            ByteArrayOutputStream stdout,
                                                            ByteArrayOutputStream stderr,
                                                            Duration timeout)
            throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!channel.isClosed()) {
            drainAvailable(outputStream, stdout);
            if (!Instant.now().isBefore(deadline)) {
                channel.disconnect();
                return new CommandExecutor.CommandResult(
                        -1,
                        stdout.toString(StandardCharsets.UTF_8),
                        "Command timed out after " + timeout
                                + (stderr.size() > 0 ? ": " + stderr.toString(StandardCharsets.UTF_8) : "")
                );
            }
            Thread.sleep(Math.min(50, Math.max(1, Duration.between(Instant.now(), deadline).toMillis())));
        }
        return null;
    }

    /**
     * Copies currently available bytes from a channel stream into a buffer.
     *
     * @param inputStream stream to drain
     * @param outputStream destination buffer
     * @throws IOException when the source stream cannot be read
     */
    private void drainAvailable(InputStream inputStream, ByteArrayOutputStream outputStream) throws IOException {
        byte[] buffer = new byte[4096];
        while (inputStream.available() > 0) {
            int read = inputStream.read(buffer);
            if (read < 0) {
                return;
            }
            outputStream.write(buffer, 0, read);
        }
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
