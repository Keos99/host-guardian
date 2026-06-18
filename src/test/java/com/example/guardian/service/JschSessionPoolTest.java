package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JschSessionPoolTest {

    @Mock
    private JschFacade jschFacade;

    private JschSessionPool pool;

    @BeforeEach
    void setUp() {
        pool = new JschSessionPool(jschFacade, new MonitorProperties());
    }

    @Test
    void reusesTheSameSessionAcrossCalls() throws Exception {
        HostConfig host = TestFixtures.sshHost(1);
        Session session = mock(Session.class);
        when(session.isConnected()).thenReturn(true);
        when(jschFacade.openSession(eq(host), any(), any())).thenReturn(session);

        String first = pool.withSession(host, s -> "first");
        String second = pool.withSession(host, s -> "second");

        assertThat(first).isEqualTo("first");
        assertThat(second).isEqualTo("second");
        verify(jschFacade, times(1)).openSession(eq(host), any(), any());
    }

    @Test
    void reopensSessionAndRetriesOnceAfterTransportFailure() throws Exception {
        HostConfig host = TestFixtures.sshHost(1);
        Session dead = mock(Session.class);
        Session fresh = mock(Session.class);
        when(jschFacade.openSession(eq(host), any(), any())).thenReturn(dead, fresh);

        AtomicInteger attempts = new AtomicInteger();
        String result = pool.withSession(host, session -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("session dropped");
            }
            return session == fresh ? "recovered" : "wrong-session";
        });

        assertThat(result).isEqualTo("recovered");
        verify(jschFacade, times(2)).openSession(eq(host), any(), any());
        verify(dead).disconnect();
    }

    @Test
    void closeAllDisconnectsPooledSessions() throws Exception {
        HostConfig host = TestFixtures.sshHost(1);
        Session session = mock(Session.class);
        when(jschFacade.openSession(eq(host), any(), any())).thenReturn(session);

        pool.withSession(host, s -> "x");
        pool.closeAll();

        verify(session).disconnect();
    }
}
