package com.example.guardian.scheduler;

import com.example.guardian.service.ServiceMonitor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitoringScheduler {

    private final ServiceMonitor serviceMonitor;

    public MonitoringScheduler(ServiceMonitor serviceMonitor) {
        this.serviceMonitor = serviceMonitor;
    }

    @Scheduled(fixedDelayString = "#{@monitorProperties.interval.toMillis()}")
    public void monitor() {
        serviceMonitor.checkAll();
    }
}
