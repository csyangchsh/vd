package com.csyangchsh.demo.vd.server;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collector for monitoring vector database server performance
 *
 * Tracks:
 * - Request counts and latency
 * - Database operations
 * - Resource usage
 * - Error rates
 */
public class MetricsCollector {

    // Request metrics
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    // Operation metrics
    private final LongAdder insertCount = new LongAdder();
    private final LongAdder searchCount = new LongAdder();
    private final LongAdder deleteCount = new LongAdder();

    // Database state
    private volatile int totalVectors;
    private volatile int activeVectors;
    private volatile long memoryUsageBytes;

    // Server state
    private final long startTime;
    private volatile boolean serving;

    public MetricsCollector() {
        this.startTime = System.currentTimeMillis();
        this.serving = false;
    }

    /**
     * Record a request
     */
    public void recordRequest(long latencyMs, boolean success) {
        totalRequests.increment();
        totalLatencyMs.addAndGet(latencyMs);
        if (success) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
        }
    }

    /**
     * Record an insert operation
     */
    public void recordInsert() {
        insertCount.increment();
    }

    /**
     * Record a search operation
     */
    public void recordSearch() {
        searchCount.increment();
    }

    /**
     * Record a delete operation
     */
    public void recordDelete() {
        deleteCount.increment();
    }

    /**
     * Update database state
     */
    public void updateDatabaseState(int total, int active, long memoryBytes) {
        this.totalVectors = total;
        this.activeVectors = active;
        this.memoryUsageBytes = memoryBytes;
    }

    /**
     * Set serving state
     */
    public void setServing(boolean serving) {
        this.serving = serving;
    }

    /**
     * Get metrics as JSON string
     */
    public String getMetricsJson() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long avgLatency = totalRequests.sum() > 0 ? totalLatencyMs.get() / totalRequests.sum() : 0;

        return String.format("""
            {
              "server": {
                "uptime_ms": %d,
                "uptime_seconds": %.2f,
                "serving": %b,
                "start_time": %d
              },
              "requests": {
                "total": %d,
                "successful": %d,
                "failed": %d,
                "avg_latency_ms": %d
              },
              "operations": {
                "insert": %d,
                "search": %d,
                "delete": %d
              },
              "database": {
                "total_vectors": %d,
                "active_vectors": %d,
                "memory_bytes": %d,
                "memory_mb": %.2f
              }
            }
            """,
            uptimeMs,
            uptimeMs / 1000.0,
            serving,
            startTime,
            totalRequests.sum(),
            successfulRequests.sum(),
            failedRequests.sum(),
            avgLatency,
            insertCount.sum(),
            searchCount.sum(),
            deleteCount.sum(),
            totalVectors,
            activeVectors,
            memoryUsageBytes,
            memoryUsageBytes / (1024.0 * 1024.0)
        );
    }

    /**
     * Get health status
     */
    public HealthStatus getHealthStatus() {
        double errorRate = totalRequests.sum() > 0
                ? (double) failedRequests.sum() / totalRequests.sum()
                : 0.0;

        boolean healthy = serving && errorRate < 0.05; // Less than 5% error rate

        return new HealthStatus(
                healthy ? "HEALTHY" : "UNHEALTHY",
                serving,
                totalVectors,
                activeVectors,
                errorRate
        );
    }

    /**
     * Reset all metrics
     */
    public void reset() {
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        totalLatencyMs.set(0);
        insertCount.reset();
        searchCount.reset();
        deleteCount.reset();
    }

    /**
     * Health status record
     */
    public record HealthStatus(
            String status,
            boolean serving,
            int totalVectors,
            int activeVectors,
            double errorRate
    ) {
        public String toJson() {
            return String.format("""
                {
                  "status": "%s",
                  "serving": %b,
                  "total_vectors": %d,
                  "active_vectors": %d,
                  "error_rate": %.4f
                }
                """, status, serving, totalVectors, activeVectors, errorRate);
        }
    }
}
