package com.example.guardian.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Status counters displayed in the dashboard summary cards.
 *
 * @param total total number of services included in the response
 * @param up number of services currently reported as healthy
 * @param down number of services currently reported as unhealthy
 * @param paused number of services with monitoring paused
 * @param restarting number of services currently being restarted
 * @param error number of services whose monitoring failed with an internal error
 * @param unknown number of services that have not been checked yet
 */
@Schema(description = "Aggregated health counters for the services included in the dashboard response.")
public record DashboardSummaryResponse(
        @Schema(description = "Total number of services in the current dashboard response.", example = "12")
        long total,

        @Schema(description = "Number of services currently reported as healthy.", example = "8")
        long up,

        @Schema(description = "Number of services currently reported as unhealthy.", example = "2")
        long down,

        @Schema(description = "Number of services with monitoring paused.", example = "1")
        long paused,

        @Schema(description = "Number of services currently marked as restarting.", example = "1")
        long restarting,

        @Schema(description = "Number of services whose monitoring failed with an error.", example = "0")
        long error,

        @Schema(description = "Number of services that have not been checked yet.", example = "0")
        long unknown
) {
}
