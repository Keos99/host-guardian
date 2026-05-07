package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.MonitoredServiceRequest;
import com.example.guardian.api.MonitoredServiceResponse;
import com.example.guardian.service.ConfigurationService;
import com.example.guardian.service.ServiceMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing monitored service definitions and manual actions.
 */
@RestController
@RequestMapping("/api/services")
@Tag(name = "Monitored services")
public class MonitoredServiceController {

    private final ConfigurationService configurationService;
    private final ServiceMonitor serviceMonitor;
    private final ApiMapper apiMapper;

    /**
     * Creates a monitored service controller.
     *
     * @param configurationService service that owns configuration persistence rules
     * @param serviceMonitor service used for immediate restart and check actions
     * @param apiMapper mapper used to serialize service entities
     */
    public MonitoredServiceController(ConfigurationService configurationService,
                                      ServiceMonitor serviceMonitor,
                                      ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.serviceMonitor = serviceMonitor;
        this.apiMapper = apiMapper;
    }

    /**
     * Lists monitored services, optionally filtered by group identifiers.
     *
     * @param groupId optional group identifiers used as a filter
     * @return configured services as REST DTOs
     */
    @Operation(
            summary = "List monitored services",
            description = """
                    Returns monitored service definitions ordered by name. When groupId values are supplied,
                    the response includes only services that belong to any of those groups.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Services were loaded successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = MonitoredServiceResponse.class))
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
    public List<MonitoredServiceResponse> listServices(
            @Parameter(
                    description = "Optional group identifiers. Repeat the parameter to filter by multiple groups.",
                    example = "1"
            )
            @RequestParam(required = false) List<Long> groupId) {
        return configurationService.getServices(groupId).stream()
                .map(apiMapper::toServiceResponse)
                .toList();
    }

    /**
     * Returns one monitored service by identifier.
     *
     * @param id service identifier
     * @return service configuration as a REST DTO
     */
    @Operation(
            summary = "Get monitored service",
            description = "Returns one monitored service definition with host and optional group details."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Service was loaded successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MonitoredServiceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public MonitoredServiceResponse getService(
            @Parameter(description = "Identifier of the monitored service", example = "1")
            @PathVariable Long id) {
        return apiMapper.toServiceResponse(configurationService.getService(id));
    }

    /**
     * Creates a new monitored service definition.
     *
     * @param request validated service payload
     * @return created service as a REST DTO
     */
    @Operation(
            summary = "Create monitored service",
            description = """
                    Creates a service definition on an existing host. The processMatch value is passed to
                    pgrep -af during monitoring. healthUrl is optional; when present it must be reachable
                    from the target host. Restart cooldown and window settings define automatic restart limits.
                    Service names are unique case-insensitively.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Service was created successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MonitoredServiceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Referenced host or group was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A service with the same name already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitoredServiceResponse createService(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Monitored service definition to create",
                    content = @Content(schema = @Schema(implementation = MonitoredServiceRequest.class))
            )
            @Valid @RequestBody MonitoredServiceRequest request) {
        return apiMapper.toServiceResponse(configurationService.createService(request));
    }

    /**
     * Updates an existing monitored service definition.
     *
     * @param id service identifier
     * @param request validated replacement payload
     * @return updated service as a REST DTO
     */
    @Operation(
            summary = "Update monitored service",
            description = """
                    Replaces an existing service definition. The host and group references are re-resolved,
                    restart policy values are overwritten, and the monitoring enabled flag is stored as supplied.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Service was updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MonitoredServiceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service, referenced host, or referenced group was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Another service with the same name already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public MonitoredServiceResponse updateService(
            @Parameter(description = "Identifier of the monitored service to update", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Replacement monitored service definition",
                    content = @Content(schema = @Schema(implementation = MonitoredServiceRequest.class))
            )
            @Valid @RequestBody MonitoredServiceRequest request) {
        return apiMapper.toServiceResponse(configurationService.updateService(id, request));
    }

    /**
     * Enables or disables automatic monitoring for a service.
     *
     * @param id service identifier
     * @param enabled requested monitoring flag
     * @return updated service as a REST DTO
     */
    @Operation(
            summary = "Enable or disable monitoring",
            description = """
                    Updates only the monitoringEnabled flag. Disabled services are skipped by scheduled
                    monitoring and appear as paused in the dashboard after the next check.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Monitoring flag was updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MonitoredServiceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The enabled query parameter could not be parsed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PatchMapping("/{id}/monitoring")
    public MonitoredServiceResponse setMonitoringEnabled(
            @Parameter(description = "Identifier of the monitored service", example = "1")
            @PathVariable Long id,
            @Parameter(description = "New automatic monitoring flag", example = "true")
            @RequestParam boolean enabled) {
        return apiMapper.toServiceResponse(configurationService.setMonitoringEnabled(id, enabled));
    }

    /**
     * Triggers the configured restart command immediately.
     *
     * @param id service identifier
     */
    @Operation(
            summary = "Restart service now",
            description = """
                    Executes the service's configured restart command immediately on its host.
                    Manual restarts bypass automatic cooldown and rolling-window limits because
                    the action is initiated explicitly by an operator.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Restart command was submitted", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restartService(
            @Parameter(description = "Identifier of the monitored service to restart", example = "1")
            @PathVariable Long id) {
        serviceMonitor.restartNow(id);
    }

    /**
     * Runs an immediate monitoring check for a single service.
     *
     * @param id service identifier
     */
    @Operation(
            summary = "Check service now",
            description = """
                    Runs a single monitoring pass for the service immediately. The check updates in-memory
                    runtime state and may trigger an automatic restart if the service is unhealthy and
                    restart policy allows it.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Monitoring check was executed", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkServiceNow(
            @Parameter(description = "Identifier of the monitored service to check", example = "1")
            @PathVariable Long id) {
        serviceMonitor.refreshSingle(id);
    }

    /**
     * Deletes a monitored service definition.
     *
     * @param id service identifier
     */
    @Operation(
            summary = "Delete monitored service",
            description = "Deletes a monitored service definition and stops future scheduled checks for it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Service was deleted successfully", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(
            @Parameter(description = "Identifier of the monitored service to delete", example = "1")
            @PathVariable Long id) {
        configurationService.deleteService(id);
    }
}
