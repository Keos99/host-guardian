package com.example.guardian.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "monitor")
public class MonitorProperties {

    private Duration interval = Duration.ofSeconds(30);

    @Valid
    @NotEmpty
    private List<MonitoredService> services = new ArrayList<>();

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public List<MonitoredService> getServices() {
        return services;
    }

    public void setServices(List<MonitoredService> services) {
        this.services = services;
    }

    public static class MonitoredService {
        @NotBlank
        private String name;

        @NotBlank
        private String processMatch;

        @NotBlank
        private String restartCommand;

        private String healthUrl;

        private Duration healthTimeout = Duration.ofSeconds(3);

        private Duration restartCooldown = Duration.ofMinutes(1);

        private int maxRestartsInWindow = 3;

        private Duration restartWindow = Duration.ofMinutes(10);

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProcessMatch() {
            return processMatch;
        }

        public void setProcessMatch(String processMatch) {
            this.processMatch = processMatch;
        }

        public String getRestartCommand() {
            return restartCommand;
        }

        public void setRestartCommand(String restartCommand) {
            this.restartCommand = restartCommand;
        }

        public String getHealthUrl() {
            return healthUrl;
        }

        public void setHealthUrl(String healthUrl) {
            this.healthUrl = healthUrl;
        }

        public Duration getHealthTimeout() {
            return healthTimeout;
        }

        public void setHealthTimeout(Duration healthTimeout) {
            this.healthTimeout = healthTimeout;
        }

        public Duration getRestartCooldown() {
            return restartCooldown;
        }

        public void setRestartCooldown(Duration restartCooldown) {
            this.restartCooldown = restartCooldown;
        }

        public int getMaxRestartsInWindow() {
            return maxRestartsInWindow;
        }

        public void setMaxRestartsInWindow(int maxRestartsInWindow) {
            this.maxRestartsInWindow = maxRestartsInWindow;
        }

        public Duration getRestartWindow() {
            return restartWindow;
        }

        public void setRestartWindow(Duration restartWindow) {
            this.restartWindow = restartWindow;
        }
    }
}
