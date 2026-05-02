package com.example.guardian.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public class ServiceState {

    private Instant lastRestartAt;
    private final Deque<Instant> restartHistory = new ArrayDeque<>();

    public Instant getLastRestartAt() {
        return lastRestartAt;
    }

    public void setLastRestartAt(Instant lastRestartAt) {
        this.lastRestartAt = lastRestartAt;
    }

    public Deque<Instant> getRestartHistory() {
        return restartHistory;
    }
}
