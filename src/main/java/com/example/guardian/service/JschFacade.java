package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Small adapter around JSch session creation.
 *
 * <p>The facade keeps JSch construction and authentication setup isolated from
 * the command provider. Tests can inject a custom factory without opening real
 * network connections.
 */
@Component
public class JschFacade {

    /**
     * Keepalive probe interval for pooled sessions, in milliseconds. Chosen below
     * the default monitoring interval so an idle session is not dropped by the
     * remote SSH server between two monitoring cycles.
     */
    private static final int KEEP_ALIVE_INTERVAL_MS = 15_000;

    private final Supplier<JSch> jschFactory;

    /**
     * Creates a facade that constructs a fresh {@link JSch} instance per session.
     */
    public JschFacade() {
        this(JSch::new);
    }

    /**
     * Creates a facade with an injectable JSch factory.
     *
     * @param jschFactory factory used to create JSch clients
     */
    JschFacade(Supplier<JSch> jschFactory) {
        this.jschFactory = jschFactory;
    }

    /**
     * Opens and connects an SSH session for a configured host.
     *
     * @param host target SSH host
     * @param sshProperties global SSH provider settings
     * @param connectTimeout maximum time allowed for session connection
     * @return connected JSch session
     * @throws JSchException when identity loading or session connection fails
     */
    public Session openSession(HostConfig host,
                               MonitorProperties.Ssh sshProperties,
                               Duration connectTimeout) throws JSchException {
        JSch jsch = jschFactory.get();
        if (host.getPrivateKeyPath() != null && !host.getPrivateKeyPath().isBlank()) {
            jsch.addIdentity(host.getPrivateKeyPath());
        }

        Session session = jsch.getSession(host.getSshUser(), host.getAddress(), host.getSshPort());
        session.setConfig("StrictHostKeyChecking", sshProperties.isStrictHostKeyChecking() ? "yes" : "no");
        session.setServerAliveInterval(KEEP_ALIVE_INTERVAL_MS);
        session.connect(toTimeoutMillis(connectTimeout));
        return session;
    }

    /**
     * Converts a {@link Duration} to a positive JSch timeout in milliseconds.
     *
     * @param timeout timeout duration
     * @return timeout in milliseconds, capped at {@link Integer#MAX_VALUE}
     */
    static int toTimeoutMillis(Duration timeout) {
        long millis = Math.max(1, timeout.toMillis());
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
