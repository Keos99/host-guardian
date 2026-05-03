package com.example.guardian.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * REST representation of a logical service group.
 *
 * @param id persistent group identifier
 * @param name unique group name
 * @param description optional group description
 */
@Schema(description = "Logical group used to organize and filter monitored services.")
public record GroupResponse(
        @Schema(description = "Persistent group identifier.", example = "2")
        Long id,

        @Schema(description = "Unique group name.", example = "Payments")
        String name,

        @Schema(description = "Optional group description.", example = "Services that process payment transactions.", nullable = true)
        String description
) {
}
