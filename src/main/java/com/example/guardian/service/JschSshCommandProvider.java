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

@Component
public class JschSshCommandProvider implements SshCommandProvider {

    private final JschFacade jschFacade;
    private final MonitorProperties monitorProperties;

    public JschSshCommandProvider(JschFacade jschFacade,
                                  MonitorProperties monitorProperties) {
        this.jschFacade = jschFacade;
        this.monitorProperties = monitorProperties;
    }

    @Override
    public MonitorProperties.Ssh.Provider provider() {
        return MonitorProperties.Ssh.Provider.JSCH;
    }

    @Override
    public CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout) {
        Session session = null;
        ChannelExec channel = null;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try {
            session = jschFacade.openSession(
                    host,
                    monitorProperties.getSsh(),
                    monitorProperties.getCommand().getSshConnectTimeout()
            );
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandExecutor.CommandResult(-1, stdout.toString(StandardCharsets.UTF_8), e.getMessage());
        } catch (Exception e) {
            return new CommandExecutor.CommandResult(-1, stdout.toString(StandardCharsets.UTF_8), e.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

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

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
