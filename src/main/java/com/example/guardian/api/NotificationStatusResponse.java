package com.example.guardian.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Current state of the chat notification feature.
 *
 * @param featureEnabled whether the feature is enabled in {@code application.yml};
 * when {@code false} the dashboard hides every notification control
 * @param globalEnabled whether the runtime global switch is currently on
 */
@Schema(description = "Current state of the chat notification feature.")
public record NotificationStatusResponse(
        @Schema(description = "Whether the notification feature is enabled by application configuration.", example = "true")
        boolean featureEnabled,

        @Schema(description = "Whether the runtime global notification switch is on.", example = "true")
        boolean globalEnabled
) {
}
