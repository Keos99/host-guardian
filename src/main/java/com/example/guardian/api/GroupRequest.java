package com.example.guardian.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload used to create or update a logical service group.
 *
 * @param name unique human-readable group name
 * @param description optional free-form description shown in the UI
 */
public record GroupRequest(
        @NotBlank String name,
        String description
) {
}
