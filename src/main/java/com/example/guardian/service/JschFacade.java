package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class JschFacade {

    private final Supplier<JSch> jschFactory;

    public JschFacade() {
        this(JSch::new);
    }

    JschFacade(Supplier<JSch> jschFactory) {
        this.jschFactory = jschFactory;
    }

    public Session openSession(HostConfig host,
                               MonitorProperties.Ssh sshProperties,
                               Duration connectTimeout) throws JSchException {
        JSch jsch = jschFactory.get();
        if (host.getPrivateKeyPath() != null && !host.getPrivateKeyPath().isBlank()) {
            jsch.addIdentity(host.getPrivateKeyPath());
        }

        Session session = jsch.getSession(host.getSshUser(), host.getAddress(), host.getSshPort());
        session.setConfig("StrictHostKeyChecking", sshProperties.isStrictHostKeyChecking() ? "yes" : "no");
        session.connect(toTimeoutMillis(connectTimeout));
        return session;
    }

    static int toTimeoutMillis(Duration timeout) {
        long millis = Math.max(1, timeout.toMillis());
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }
}
