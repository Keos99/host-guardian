package com.example.guardian.api;

import java.util.List;

public record DashboardResponse(
        DashboardSummaryResponse summary,
        List<GroupResponse> groups,
        List<DashboardServiceResponse> services
) {
}
