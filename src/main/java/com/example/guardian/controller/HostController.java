package com.example.guardian.controller;

import com.example.guardian.api.ApiMapper;
import com.example.guardian.api.HostRequest;
import com.example.guardian.api.HostResponse;
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
 * REST controller for managing monitored host configurations.
 */
@RestController
@RequestMapping("/api/hosts")
@Tag(name = "Hosts")
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
    @Operation(
            summary = "List hosts",
            description = "Returns every configured host ordered by name. Hosts describe where service checks and restart commands are executed."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Hosts were loaded successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = HostResponse.class))
            )
    )
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
    @Operation(
            summary = "Create host",
            description = """
                    Creates a host configuration. For LOCAL hosts, address defaults to 127.0.0.1 when omitted.
                    For SSH hosts, sshUser and privateKeyPath are required so the watcher can execute commands remotely.
                    Host names are unique case-insensitively.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Host was created successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed or SSH-specific fields are missing",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A host with the same name already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HostResponse createHost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Host configuration to create",
                    content = @Content(schema = @Schema(implementation = HostRequest.class))
            )
            @Valid @RequestBody HostRequest request) {
        return apiMapper.toHostResponse(configurationService.createHost(request));
    }

    /**
     * Updates an existing host configuration.
     *
     * @param id host identifier
     * @param request validated replacement payload
     * @return updated host as a REST DTO
     */
    @Operation(
            summary = "Update host",
            description = """
                    Replaces an existing host configuration. The same validation rules as creation apply:
                    SSH hosts must include sshUser and privateKeyPath, and names remain unique case-insensitively.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Host was updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed or SSH-specific fields are missing",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Host was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Another host with the same name already exists",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public HostResponse updateHost(
            @Parameter(description = "Identifier of the host to update", example = "1")
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Replacement host configuration",
                    content = @Content(schema = @Schema(implementation = HostRequest.class))
            )
            @Valid @RequestBody HostRequest request) {
        return apiMapper.toHostResponse(configurationService.updateHost(id, request));
    }

    /**
     * Deletes a host configuration when no services reference it.
     *
     * @param id host identifier
     */
    @Operation(
            summary = "Delete host",
            description = "Deletes a host configuration. Deletion is rejected while any monitored service still references the host."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Host was deleted successfully", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "Host cannot be deleted because services still reference it",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Host was not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHost(
            @Parameter(description = "Identifier of the host to delete", example = "1")
            @PathVariable Long id) {
        configurationService.deleteHost(id);
    }
}
