package com.csyangchsh.demo.vd.server.model;

import com.google.gson.annotations.SerializedName;

/**
 * Metrics response
 */
public record MetricsResponse(
        @SerializedName("server") ServerMetrics server,
        @SerializedName("requests") RequestMetrics requests,
        @SerializedName("operations") OperationsMetrics operations,
        @SerializedName("database") DatabaseMetrics database
) {
    public record ServerMetrics(
            @SerializedName("uptime_ms") long uptimeMs,
            @SerializedName("uptime_seconds") double uptimeSeconds,
            @SerializedName("serving") boolean serving,
            @SerializedName("start_time") long startTime
    ) {}

    public record RequestMetrics(
            @SerializedName("total") long total,
            @SerializedName("successful") long successful,
            @SerializedName("failed") long failed,
            @SerializedName("avg_latency_ms") double avgLatencyMs
    ) {}

    public record OperationsMetrics(
            @SerializedName("insert") long insert,
            @SerializedName("search") long search,
            @SerializedName("delete") long delete
    ) {}

    public record DatabaseMetrics(
            @SerializedName("total_vectors") long totalVectors,
            @SerializedName("active_vectors") long activeVectors,
            @SerializedName("memory_bytes") long memoryBytes,
            @SerializedName("memory_mb") double memoryMb
    ) {}
}
