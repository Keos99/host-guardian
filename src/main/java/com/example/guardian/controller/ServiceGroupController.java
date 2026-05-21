package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.GroupRequest;
import com.example.guardian.api.GroupResponse;
import com.example.guardian.service.ConfigurationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing logical service groups.
 */
@RestController
@RequestMapping("/api/groups")
@Tag(name = "Service groups")
public class ServiceGroupController {

    private final ConfigurationService configurationService;
    private final ApiMapper apiMapper;

    /**
     * Creates a service group controller.
     *
     * @param configurationService service that owns group persistence rules
     * @param apiMapper mapper used to serialize group entities
     */
    public ServiceGroupController(ConfigurationService configurationService, ApiMapper apiMapper) {
        this.configurationService = configurationService;
        this.apiMapper = apiMapper;
    }

    /**
     * Lists all logical service groups ordered by name.
     *
     * @return configured groups as REST DTOs
     */
    @Operation(
            summary = "List service groups",
            description = "Returns every logical service group ordered by name. Groups are used by the dashboard and service list filters."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Groups were loaded successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = GroupResponse.class))
            )
    )
    @GetMapping
    public List<GroupResponse> listGroups() {
        return configurationService.getGroups().stream()
                .map(apiMapper::toGroupResponse)
                .toList();
    }

    /**
     * Creates a new service group.
     *
     * @param request validated group payload
     * @return created group as a REST DTO
     */
    @Operation(
            summary = "Create service group",
            description = "Creates a logical group for organizing monitored services. Group names are unique case-insensitively."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Group was created successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GroupResponse.class)
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
                    responseCode = "409",
                    description = "A group with the same name already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Service group to create",
                    content = @Content(schema = @Schema(implementation = GroupRequest.class))
            )
            @Valid @RequestBody GroupRequest request) {
        return apiMapper.toGroupResponse(configurationService.createGroup(request));
    }

    /**
     * Updates an existing service group.
     *
     * @param id group identifier
     * @param request validated replacement payload
     * @return updated group as a REST DTO
     */
    @Operation(
            summary = "Update service group",
            description = "Replaces the name and description of an existing group. The new name must remain unique case-insensitively."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Group was updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GroupResponse.class)
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
                    description = "Group was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Another group with the same name already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public GroupResponse updateGroup(
            @Parameter(description = "Identifier of the service group to update", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Replacement service group",
                    content = @Content(schema = @Schema(implementation = GroupRequest.class))
            )
            @Valid @RequestBody GroupRequest request) {
        return apiMapper.toGroupResponse(configurationService.updateGroup(id, request));
    }

    /**
     * Deletes a service group when no services reference it.
     *
     * @param id group identifier
     */
    @Operation(
            summary = "Delete service group",
            description = "Deletes a logical group. Deletion is rejected while any monitored service still references the group."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Group was deleted successfully", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "Group cannot be deleted because services still reference it",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Group was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(
            @Parameter(description = "Identifier of the service group to delete", example = "1")
            @PathVariable Long id) {
        configurationService.deleteGroup(id);
    }
}
