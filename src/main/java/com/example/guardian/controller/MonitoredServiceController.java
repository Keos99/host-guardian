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

@RestController
@RequestMapping("/api/services")
public class MonitoredServiceController {

    private final ConfigurationService configurationService;
    private final ServiceMonitor serviceMonitor;
    private final ApiMapper apiMapper;

    public MonitoredServiceController(ConfigurationService configurationService,
                                      ServiceMonitor serviceMonitor,
                                      ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.serviceMonitor = serviceMonitor;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public List<MonitoredServiceResponse> listServices(@RequestParam(required = false) List<Long> groupId) {
        return configurationService.getServices(groupId).stream()
                .map(apiMapper::toServiceResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public MonitoredServiceResponse getService(@PathVariable Long id) {
        return apiMapper.toServiceResponse(configurationService.getService(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitoredServiceResponse createService(@Valid @RequestBody MonitoredServiceRequest request) {
        return apiMapper.toServiceResponse(configurationService.createService(request));
    }

    @PutMapping("/{id}")
    public MonitoredServiceResponse updateService(@PathVariable Long id,
                                                  @Valid @RequestBody MonitoredServiceRequest request) {
        return apiMapper.toServiceResponse(configurationService.updateService(id, request));
    }

    @PatchMapping("/{id}/monitoring")
    public MonitoredServiceResponse setMonitoringEnabled(@PathVariable Long id,
                                                         @RequestParam boolean enabled) {
        return apiMapper.toServiceResponse(configurationService.setMonitoringEnabled(id, enabled));
    }

    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restartService(@PathVariable Long id) {
        serviceMonitor.restartNow(id);
    }

    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkServiceNow(@PathVariable Long id) {
        serviceMonitor.refreshSingle(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long id) {
        configurationService.deleteService(id);
    }
}
