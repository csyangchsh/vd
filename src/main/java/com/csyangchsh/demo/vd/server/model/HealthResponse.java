package com.csyangchsh.demo.vd.server.model;

import com.google.gson.annotations.SerializedName;

/**
 * Health check response
 */
public record HealthResponse(
        @SerializedName("status") String status,
        @SerializedName("serving") boolean serving,
        @SerializedName("total_vectors") long totalVectors,
        @SerializedName("active_vectors") long activeVectors,
        @SerializedName("error_rate") double errorRate
) {
    public static HealthResponse healthy(long totalVectors, long activeVectors, double errorRate) {
        return new HealthResponse("HEALTHY", true, totalVectors, activeVectors, errorRate);
    }

    public static HealthResponse notServing(long totalVectors, long activeVectors, double errorRate) {
        return new HealthResponse("NOT_SERVING", false, totalVectors, activeVectors, errorRate);
    }
}
