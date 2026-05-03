package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.MonitoredServiceRequest;
import com.example.guardian.api.MonitoredServiceResponse;
import com.example.guardian.service.ConfigurationService;
import com.example.guardian.service.ServiceMonitor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    @GetMapping
    public List<MonitoredServiceResponse> listServices(@RequestParam(required = false) List<Long> groupId) {
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
    @GetMapping("/{id}")
    public MonitoredServiceResponse getService(@PathVariable Long id) {
        return apiMapper.toServiceResponse(configurationService.getService(id));
    }

    /**
     * Creates a new monitored service definition.
     *
     * @param request validated service payload
     * @return created service as a REST DTO
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitoredServiceResponse createService(@Valid @RequestBody MonitoredServiceRequest request) {
        return apiMapper.toServiceResponse(configurationService.createService(request));
    }

    /**
     * Updates an existing monitored service definition.
     *
     * @param id service identifier
     * @param request validated replacement payload
     * @return updated service as a REST DTO
     */
    @PutMapping("/{id}")
    public MonitoredServiceResponse updateService(@PathVariable Long id,
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
    @PatchMapping("/{id}/monitoring")
    public MonitoredServiceResponse setMonitoringEnabled(@PathVariable Long id,
                                                         @RequestParam boolean enabled) {
        return apiMapper.toServiceResponse(configurationService.setMonitoringEnabled(id, enabled));
    }

    /**
     * Triggers the configured restart command immediately.
     *
     * @param id service identifier
     */
    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restartService(@PathVariable Long id) {
        serviceMonitor.restartNow(id);
    }

    /**
     * Runs an immediate monitoring check for a single service.
     *
     * @param id service identifier
     */
    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkServiceNow(@PathVariable Long id) {
        serviceMonitor.refreshSingle(id);
    }

    /**
     * Deletes a monitored service definition.
     *
     * @param id service identifier
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long id) {
        configurationService.deleteService(id);
    }
}
