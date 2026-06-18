package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches one long-lived JSch {@link Session} per host and reuses it across commands.
 *
 * <p>Opening an SSH session performs a full TCP and authentication handshake. The
 * legacy behaviour opened and tore down a session for every single command, so a
 * monitoring cycle paid that cost dozens or hundreds of times. This pool pays the
 * handshake once per host and then runs cheap {@code exec} channels on the kept
 * session, which is the dominant speedup for large fleets.
 *
 * <p>Sessions are validated before reuse and transparently reopened when the
 * remote side has dropped them. Creation is guarded by a per-host lock so two
 * threads never open duplicate sessions for the same host, while different hosts
 * still connect in parallel.
 */
@Component
public class JschSessionPool {

    private static final Logger log = LoggerFactory.getLogger(JschSessionPool.class);

    private final JschFacade jschFacade;
    private final MonitorProperties monitorProperties;

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Object> hostLocks = new ConcurrentHashMap<>();

    /**
     * Creates a session pool.
     *
     * @param jschFacade adapter that opens raw JSch sessions
     * @param monitorProperties global SSH and timeout settings
     */
    public JschSessionPool(JschFacade jschFacade, MonitorProperties monitorProperties) {
        this.jschFacade = jschFacade;
        this.monitorProperties = monitorProperties;
    }

    /**
     * Runs work on a pooled session, reopening the session once if it was stale.
     *
     * <p>Transport-level failures (dead session, refused channel) are retried a
     * single time with a freshly opened session. Command-level outcomes (non-zero
     * exit codes, timeouts) are expected to be returned by {@code work}, not thrown,
     * and therefore never trigger a reconnect.
     *
     * @param host target SSH host
     * @param work action to execute against a connected session
     * @param <T> work result type
     * @return value produced by {@code work}
     * @throws Exception when the work fails even after one reconnect attempt
     */
    public <T> T withSession(HostConfig host, SessionWork<T> work) throws Exception {
        try {
            return work.apply(acquire(host));
        } catch (Exception first) {
            log.debug("SSH session for host {} failed, reopening and retrying once", host.getName(), first);
            evict(host);
            return work.apply(acquire(host));
        }
    }

    /**
     * Returns a connected session for the host, creating one if needed.
     *
     * @param host target SSH host
     * @return connected JSch session
     * @throws JSchException when a new session cannot be opened
     */
    private Session acquire(HostConfig host) throws JSchException {
        Long id = host.getId();
        Session cached = sessions.get(id);
        if (cached != null && cached.isConnected()) {
            return cached;
        }

        synchronized (hostLocks.computeIfAbsent(id, k -> new Object())) {
            Session current = sessions.get(id);
            if (current != null && current.isConnected()) {
                return current;
            }
            if (current != null) {
                current.disconnect();
            }
            Session fresh = jschFacade.openSession(
                    host,
                    monitorProperties.getSsh(),
                    monitorProperties.getCommand().getSshConnectTimeout()
            );
            sessions.put(id, fresh);
            return fresh;
        }
    }

    /**
     * Drops the cached session for a host so the next call reopens it.
     *
     * @param host host whose session should be discarded
     */
    private void evict(HostConfig host) {
        Session removed = sessions.remove(host.getId());
        if (removed != null) {
            removed.disconnect();
        }
    }

    /**
     * Disconnects every pooled session on application shutdown.
     */
    @PreDestroy
    public void closeAll() {
        sessions.values().forEach(Session::disconnect);
        sessions.clear();
    }

    /**
     * Work executed against a borrowed SSH session.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface SessionWork<T> {

        /**
         * Runs the action on a connected session.
         *
         * @param session connected SSH session owned by the pool
         * @return work result
         * @throws Exception when execution fails
         */
        T apply(Session session) throws Exception;
    }
}
