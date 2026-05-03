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

@RestController
@RequestMapping("/api/hosts")
public class HostController {

    private final ConfigurationService configurationService;
    private final ApiMapper apiMapper;

    public HostController(ConfigurationService configurationService, ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public List<HostResponse> listHosts() {
        return configurationService.getHosts().stream()
                .map(apiMapper::toHostResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HostResponse createHost(@Valid @RequestBody HostRequest request) {
        return apiMapper.toHostResponse(configurationService.createHost(request));
    }

    @PutMapping("/{id}")
    public HostResponse updateHost(@PathVariable Long id,
                                   @Valid @RequestBody HostRequest request) {
        return apiMapper.toHostResponse(configurationService.updateHost(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHost(@PathVariable Long id) {
        configurationService.deleteHost(id);
    }
}
