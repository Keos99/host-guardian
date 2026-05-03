package com.example.guardian.api;

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
