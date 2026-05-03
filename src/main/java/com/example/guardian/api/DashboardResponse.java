package com.example.guardian.api;

import java.util.List;

/**
 * Aggregated payload returned by the dashboard endpoint.
 *
 * @param summary counters grouped by service health status
 * @param groups all groups available for dashboard filtering
 * @param services dashboard rows for the selected services
 */
public record DashboardResponse(
        DashboardSummaryResponse summary,
        List<GroupResponse> groups,
        List<DashboardServiceResponse> services
) {
}
