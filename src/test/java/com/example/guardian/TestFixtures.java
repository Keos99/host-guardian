package com.example.guardian;

import com.example.guardian.model.HostConfig;
import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.model.ServiceGroup;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static HostConfig localHost(long id) {
        HostConfig host = new HostConfig();
        host.setId(id);
        host.setName("Localhost");
        host.setConnectionMode(HostConnectionMode.LOCAL);
        host.setAddress("127.0.0.1");
        host.setSshPort(22);
        host.setSshUser("local-user");
        host.setPrivateKeyPath("/keys/local");
        host.setDescription("Local host");
        return host;
    }

    public static HostConfig sshHost(long id) {
        HostConfig host = new HostConfig();
        host.setId(id);
        host.setName("Remote host");
        host.setConnectionMode(HostConnectionMode.SSH);
        host.setAddress("10.0.0.5");
        host.setSshPort(2222);
        host.setSshUser("deploy");
        host.setPrivateKeyPath("/home/deploy/.ssh/id_ed25519");
        host.setDescription("Remote host");
        return host;
    }

    public static ServiceGroup group(long id) {
        ServiceGroup group = new ServiceGroup();
        group.setId(id);
        group.setName("Payments");
        group.setDescription("Payment services");
        return group;
    }

    public static MonitoredService service(long id, HostConfig host, ServiceGroup group) {
        MonitoredService service = new MonitoredService();
        service.setId(id);
        service.setName("billing-api");
        service.setHost(host);
        service.setGroup(group);
        service.setProcessMatch("billing-api.jar");
        service.setRestartCommand("systemctl restart billing-api");
        service.setStartCommand("systemctl start billing-api");
        service.setManualRestartEnabled(true);
        service.setHealthUrl("http://127.0.0.1:8080/actuator/health");
        service.setHealthTimeoutSeconds(3);
        service.setRestartCooldownSeconds(60);
        service.setRestartWindowSeconds(600);
        service.setMaxRestartsInWindow(3);
        service.setMonitoringEnabled(true);
        service.setDescription("Billing service");
        return service;
    }
}
