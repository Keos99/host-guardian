package com.example.guardian.api;

import jakarta.validation.constraints.NotBlank;

public record GroupRequest(
        @NotBlank String name,
        String description
) {
}
