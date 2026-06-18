package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.DashboardResponse;
import com.example.guardian.api.DashboardServiceResponse;
import com.example.guardian.api.DashboardSummaryResponse;
import com.example.guardian.api.GroupResponse;
import com.example.guardian.api.NotificationStatusResponse;
import com.example.guardian.model.ServiceHealthStatus;
import com.example.guardian.model.ServiceRuntimeSnapshot;
import com.example.guardian.notification.NotificationProperties;
import com.example.guardian.notification.NotificationSettingsService;
import com.example.guardian.service.ConfigurationService;
import com.example.guardian.service.ServiceMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes the dashboard aggregate view.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final ConfigurationService configurationService;
    private final ServiceMonitor serviceMonitor;
    private final ApiMapper apiMapper;
    private final NotificationProperties notificationProperties;
    private final NotificationSettingsService notificationSettings;

    /**
     * Creates a dashboard controller with configuration and runtime data sources.
     *
     * @param configurationService service used to load persisted configuration
     * @param serviceMonitor service used to read current runtime snapshots
     * @param apiMapper mapper that converts entities into response DTOs
     * @param notificationProperties notification feature configuration
     * @param notificationSettings runtime global notification switch storage
     */
    public DashboardController(ConfigurationService configurationService,
                               ServiceMonitor serviceMonitor,
                               ApiMapper apiMapper,
                               NotificationProperties notificationProperties,
                               NotificationSettingsService notificationSettings) {
        this.configurationService = configurationService;
        this.serviceMonitor = serviceMonitor;
        this.apiMapper = apiMapper;
        this.notificationProperties = notificationProperties;
        this.notificationSettings = notificationSettings;
    }

    /**
     * Returns dashboard summary, filters, and service rows.
     *
     * @param groupId optional group identifiers used to filter monitored services
     * @return aggregated dashboard response for the selected services
     */
    @Operation(
            summary = "Get dashboard data",
            description = """
                    Returns the complete dashboard model in a single request: summary counters,
                    all configured groups for filter controls, and service rows enriched with
                    the latest in-memory monitoring snapshot. If group identifiers are provided,
                    only services from those groups are included in the service list and summary.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Dashboard data was loaded successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DashboardResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The groupId query parameter could not be parsed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping
    public DashboardResponse getDashboard(
            @Parameter(
                    description = "Optional group identifiers. Repeat the parameter to filter by multiple groups.",
                    example = "1"
            )
            @RequestParam(required = false) List<Long> groupId) {
        Map<Long, ServiceRuntimeSnapshot> runtimeSnapshots = serviceMonitor.getRuntimeSnapshots();
        List<DashboardServiceResponse> services = configurationService.getServices(groupId).stream()
                .map(service -> apiMapper.toDashboardServiceResponse(service, runtimeSnapshots.get(service.getId())))
                .toList();

        List<GroupResponse> groups = configurationService.getGroups().stream()
                .map(apiMapper::toGroupResponse)
                .toList();

        NotificationStatusResponse notifications = new NotificationStatusResponse(
                notificationProperties.isEnabled(),
                notificationSettings.isGlobalEnabled()
        );

        return new DashboardResponse(buildSummary(services), notifications, groups, services);
    }

    /**
     * Builds status counters for the provided dashboard rows.
     *
     * @param services dashboard rows included in the current response
     * @return summary counters grouped by health status
     */
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

    /**
     * Counts dashboard rows matching a specific health status.
     *
     * @param services dashboard rows to inspect
     * @param status health status to count
     * @return number of rows with the requested status
     */
    private long countByStatus(List<DashboardServiceResponse> services, ServiceHealthStatus status) {
        return services.stream()
                .filter(service -> service.status() == status)
                .count();
    }
}
