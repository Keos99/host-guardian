package com.example.guardian.api;

/**
 * REST representation of a logical service group.
 *
 * @param id persistent group identifier
 * @param name unique group name
 * @param description optional group description
 */
public record GroupResponse(
        Long id,
        String name,
        String description
) {
}
