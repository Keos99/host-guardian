package com.example.guardian.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Aggregated payload returned by the dashboard endpoint.
 *
 * @param summary counters grouped by service health status
 * @param notifications current state of the chat notification feature
 * @param groups all groups available for dashboard filtering
 * @param services dashboard rows for the selected services
 */
@Schema(description = "Complete dashboard payload with summary counters, available filters, and service rows.")
public record DashboardResponse(
        @Schema(description = "Counters grouped by current service health status.")
        DashboardSummaryResponse summary,

        @Schema(description = "Current state of the chat notification feature.")
        NotificationStatusResponse notifications,

        @Schema(description = "All configured groups available for dashboard filtering.")
        List<GroupResponse> groups,

        @Schema(description = "Services included in the current dashboard view.")
        List<DashboardServiceResponse> services
) {
}
