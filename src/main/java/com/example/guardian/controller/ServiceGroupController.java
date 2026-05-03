package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.GroupRequest;
import com.example.guardian.api.GroupResponse;
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
@RequestMapping("/api/groups")
public class ServiceGroupController {

    private final ConfigurationService configurationService;
    private final ApiMapper apiMapper;

    public ServiceGroupController(ConfigurationService configurationService, ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.apiMapper = apiMapper;
    }

    @GetMapping
    public List<GroupResponse> listGroups() {
        return configurationService.getGroups().stream()
                .map(apiMapper::toGroupResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@Valid @RequestBody GroupRequest request) {
        return apiMapper.toGroupResponse(configurationService.createGroup(request));
    }

    @PutMapping("/{id}")
    public GroupResponse updateGroup(@PathVariable Long id,
                                     @Valid @RequestBody GroupRequest request) {
        return apiMapper.toGroupResponse(configurationService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long id) {
        configurationService.deleteGroup(id);
    }
}
