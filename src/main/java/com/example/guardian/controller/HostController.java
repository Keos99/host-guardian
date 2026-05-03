package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.HostRequest;
import com.example.guardian.api.HostResponse;
import com.example.guardian.service.ConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing monitored host configurations.
 */
@RestController
@RequestMapping("/api/hosts")
public class HostController {

    private final ConfigurationService configurationService;
    private final ApiMapper apiMapper;

    /**
     * Creates a host controller.
     *
     * @param configurationService service that owns host persistence rules
     * @param apiMapper mapper used to serialize host entities
     */
    public HostController(ConfigurationService configurationService, ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.apiMapper = apiMapper;
    }

    /**
     * Lists all configured hosts ordered by name.
     *
     * @return configured hosts as REST DTOs
     */
    @GetMapping
    public List<HostResponse> listHosts() {
        return configurationService.getHosts().stream()
                .map(apiMapper::toHostResponse)
                .toList();
    }

    /**
     * Creates a new host configuration.
     *
     * @param request validated host payload
     * @return created host as a REST DTO
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HostResponse createHost(@Valid @RequestBody HostRequest request) {
        return apiMapper.toHostResponse(configurationService.createHost(request));
    }

    /**
     * Updates an existing host configuration.
     *
     * @param id host identifier
     * @param request validated replacement payload
     * @return updated host as a REST DTO
     */
    @PutMapping("/{id}")
    public HostResponse updateHost(@PathVariable Long id,
                                   @Valid @RequestBody HostRequest request) {
        return apiMapper.toHostResponse(configurationService.updateHost(id, request));
    }

    /**
     * Deletes a host configuration when no services reference it.
     *
     * @param id host identifier
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHost(@PathVariable Long id) {
        configurationService.deleteHost(id);
    }
}
