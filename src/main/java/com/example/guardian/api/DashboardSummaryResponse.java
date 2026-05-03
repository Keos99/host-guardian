package com.example.guardian.api;

public record DashboardSummaryResponse(
        long total,
        long up,
        long down,
        long paused,
        long restarting,
        long error,
        long unknown
) {
}
