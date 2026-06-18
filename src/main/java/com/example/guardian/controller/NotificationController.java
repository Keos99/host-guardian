package com.example.guardian.controller;

import com.example.guardian.api.NotificationStatusResponse;
import com.example.guardian.notification.NotificationProperties;
import com.example.guardian.notification.NotificationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for the global chat notification switch.
 *
 * <p>The build-time feature flag from {@code application.yml} cannot be changed
 * here; this controller only reads it and manages the runtime switch that
 * operators toggle from the dashboard.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationProperties notificationProperties;
    private final NotificationSettingsService notificationSettings;

    /**
     * Creates a notification controller.
     *
     * @param notificationProperties notification feature configuration
     * @param notificationSettings runtime global notification switch storage
     */
    public NotificationController(NotificationProperties notificationProperties,
                                  NotificationSettingsService notificationSettings) {
        this.notificationProperties = notificationProperties;
        this.notificationSettings = notificationSettings;
    }

    /**
     * Returns the current state of the notification feature.
     *
     * @return feature flag and runtime switch state
     */
    @Operation(
            summary = "Get notification status",
            description = """
                    Returns the state of the chat notification feature: the configuration flag from
                    application.yml and the runtime global switch controlled from the dashboard.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification status was loaded successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = NotificationStatusResponse.class)
                    )
            )
    })
    @GetMapping
    public NotificationStatusResponse getStatus() {
        return new NotificationStatusResponse(
                notificationProperties.isEnabled(),
                notificationSettings.isGlobalEnabled()
        );
    }

    /**
     * Toggles the runtime global notification switch.
     *
     * @param enabled requested switch value
     * @return updated notification status
     */
    @Operation(
            summary = "Enable or disable notifications globally",
            description = """
                    Updates the runtime global notification switch. The switch is persisted in the
                    database and survives restarts. When the feature is disabled by configuration,
                    the request is rejected with 409.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Global notification switch was updated successfully",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = NotificationStatusResponse.class)
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
                    responseCode = "409",
                    description = "The notification feature is disabled by application configuration",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    @PatchMapping
    public NotificationStatusResponse setGlobalEnabled(
            @Parameter(description = "New global notification switch value", example = "false")
            @RequestParam boolean enabled) {
        if (!notificationProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Notifications are disabled by application configuration");
        }

        return new NotificationStatusResponse(
                notificationProperties.isEnabled(),
                notificationSettings.setGlobalEnabled(enabled)
        );
    }
}
