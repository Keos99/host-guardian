package com.example.guardian.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload used to create or update a logical service group.
 *
 * @param name unique human-readable group name
 * @param description optional free-form description shown in the UI
 */
@Schema(description = "Payload used to create or update a logical service group.")
public record GroupRequest(
        @Schema(description = "Unique group name.", example = "Payments", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "Optional group description.", example = "Services that process payment transactions.", nullable = true)
        String description
) {
}
