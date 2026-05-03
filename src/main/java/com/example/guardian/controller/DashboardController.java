package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.DashboardResponse;
import com.example.guardian.api.DashboardServiceResponse;
import com.example.guardian.api.DashboardSummaryResponse;
import com.example.guardian.api.GroupResponse;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.service.ConfigurationService;
import com.example.guardian.service.ServiceMonitor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ConfigurationService configurationService;
    private final ServiceMonitor serviceMonitor;
    private final ApiMapper apiMapper;

    public DashboardController(ConfigurationService configurationService,
                               ServiceMonitor serviceMonitor,
                               ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.serviceMonitor = serviceMonitor;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public DashboardResponse getDashboard(@RequestParam(required = false) List<Long> groupId) {
        Map<Long, ServiceRuntimeSnapshot> runtimeSnapshots = serviceMonitor.getRuntimeSnapshots();
        List<DashboardServiceResponse> services = configurationService.getServices(groupId).stream()
                .map(service -> apiMapper.toDashboardServiceResponse(service, runtimeSnapshots.get(service.getId())))
                .toList();

        List<GroupResponse> groups = configurationService.getGroups().stream()
                .map(apiMapper::toGroupResponse)
                .toList();

        return new DashboardResponse(buildSummary(services), groups, services);
    }

    private DashboardSummaryResponse buildSummary(List<DashboardServiceResponse> services) {
        long up = countByStatus(services, ServiceHealthStatus.UP);
        long down = countByStatus(services, ServiceHealthStatus.DOWN);
        long paused = countByStatus(services, ServiceHealthStatus.PAUSED);
        long restarting = countByStatus(services, ServiceHealthStatus.RESTARTING);
        long error = countByStatus(services, ServiceHealthStatus.ERROR);
        long unknown = countByStatus(services, ServiceHealthStatus.UNKNOWN);

        return new DashboardSummaryResponse(
                services.size(),
                up,
                down,
                paused,
                restarting,
                error,
                unknown
        );
    }

    private long countByStatus(List<DashboardServiceResponse> services, ServiceHealthStatus status) {
        return services.stream()
                .filter(service -> service.status() == status)
                .count();
    }
}
