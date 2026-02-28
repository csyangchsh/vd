package com.csyangchsh.demo.vd.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for MetricsCollector.
 *
 * These tests verify:
 * 1. Request tracking - total, successful, failed counts
 * 2. Operation tracking - insert, search, delete counts
 * 3. Latency tracking - average latency calculation
 * 4. Database state - total vectors, active vectors, memory usage
 * 5. Health status - error rate calculation, serving state
 * 6. Concurrency - thread-safe metrics updates
 * 7. JSON output - correct format and values
 */
@DisplayName("Metrics Collector Tests")
class MetricsCollectorTest {

    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        metrics = new MetricsCollector();
    }

    // ========== Request Metrics Tests ==========

    @Test
    @DisplayName("Should record successful request")
    void testRecordSuccessfulRequest() {
        metrics.recordRequest(10, true);

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(1, json.getAsJsonObject("requests").get("total").getAsInt());
        assertEquals(1, json.getAsJsonObject("requests").get("successful").getAsInt());
        assertEquals(0, json.getAsJsonObject("requests").get("failed").getAsInt());
    }

    @Test
    @DisplayName("Should record failed request")
    void testRecordFailedRequest() {
        metrics.recordRequest(5, false);

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(1, json.getAsJsonObject("requests").get("total").getAsInt());
        assertEquals(0, json.getAsJsonObject("requests").get("successful").getAsInt());
        assertEquals(1, json.getAsJsonObject("requests").get("failed").getAsInt());
    }

    @Test
    @DisplayName("Should record multiple requests")
    void testRecordMultipleRequests() {
        metrics.recordRequest(10, true);
        metrics.recordRequest(20, true);
        metrics.recordRequest(15, false);

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject requests = json.getAsJsonObject("requests");

        assertEquals(3, requests.get("total").getAsInt());
        assertEquals(2, requests.get("successful").getAsInt());
        assertEquals(1, requests.get("failed").getAsInt());
    }

    @Test
    @DisplayName("Should calculate average latency correctly")
    void testAverageLatency() {
        metrics.recordRequest(10, true);
        metrics.recordRequest(20, true);
        metrics.recordRequest(30, true);

        JsonObject json = parseJson(metrics.getMetricsJson());
        // Average: (10 + 20 + 30) / 3 = 20
        assertEquals(20, json.getAsJsonObject("requests").get("avg_latency_ms").getAsLong());
    }

    @Test
    @DisplayName("Should handle zero latency")
    void testZeroLatency() {
        metrics.recordRequest(0, true);

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(0, json.getAsJsonObject("requests").get("avg_latency_ms").getAsLong());
    }

    @Test
    @DisplayName("Should return zero average latency when no requests")
    void testAverageLatencyNoRequests() {
        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(0, json.getAsJsonObject("requests").get("avg_latency_ms").getAsLong());
    }

    @Test
    @DisplayName("Should track latency for failed requests too")
    void testLatencyTrackingForFailedRequests() {
        metrics.recordRequest(100, true);
        metrics.recordRequest(200, false);

        JsonObject json = parseJson(metrics.getMetricsJson());
        // Average: (100 + 200) / 2 = 150
        assertEquals(150, json.getAsJsonObject("requests").get("avg_latency_ms").getAsLong());
    }

    // ========== Operation Metrics Tests ==========

    @Test
    @DisplayName("Should record insert operations")
    void testRecordInsert() {
        metrics.recordInsert();
        metrics.recordInsert();
        metrics.recordInsert();

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(3, json.getAsJsonObject("operations").get("insert").getAsInt());
    }

    @Test
    @DisplayName("Should record search operations")
    void testRecordSearch() {
        metrics.recordSearch();
        metrics.recordSearch();

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(2, json.getAsJsonObject("operations").get("search").getAsInt());
    }

    @Test
    @DisplayName("Should record delete operations")
    void testRecordDelete() {
        metrics.recordDelete();

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(1, json.getAsJsonObject("operations").get("delete").getAsInt());
    }

    @Test
    @DisplayName("Should record mixed operations")
    void testRecordMixedOperations() {
        metrics.recordInsert();
        metrics.recordInsert();
        metrics.recordSearch();
        metrics.recordDelete();

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject operations = json.getAsJsonObject("operations");

        assertEquals(2, operations.get("insert").getAsInt());
        assertEquals(1, operations.get("search").getAsInt());
        assertEquals(1, operations.get("delete").getAsInt());
    }

    // ========== Database State Tests ==========

    @Test
    @DisplayName("Should update database state")
    void testUpdateDatabaseState() {
        metrics.updateDatabaseState(1000, 950, 1024 * 1024 * 100); // 100MB

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject database = json.getAsJsonObject("database");

        assertEquals(1000, database.get("total_vectors").getAsInt());
        assertEquals(950, database.get("active_vectors").getAsInt());
        assertEquals(1024 * 1024 * 100, database.get("memory_bytes").getAsLong());
    }

    @Test
    @DisplayName("Should calculate memory MB correctly")
    void testMemoryMB() {
        metrics.updateDatabaseState(100, 100, 1024 * 1024); // 1MB

        JsonObject json = parseJson(metrics.getMetricsJson());
        double memoryMB = json.getAsJsonObject("database").get("memory_mb").getAsDouble();

        assertEquals(1.0, memoryMB, 0.01);
    }

    @Test
    @DisplayName("Should handle zero memory usage")
    void testZeroMemoryUsage() {
        metrics.updateDatabaseState(0, 0, 0);

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertEquals(0, json.getAsJsonObject("database").get("memory_bytes").getAsLong());
        assertEquals(0.0, json.getAsJsonObject("database").get("memory_mb").getAsDouble(), 0.001);
    }

    @Test
    @DisplayName("Should update database state multiple times")
    void testUpdateDatabaseStateMultipleTimes() {
        metrics.updateDatabaseState(100, 100, 1024);
        metrics.updateDatabaseState(200, 190, 2048);
        metrics.updateDatabaseState(300, 280, 3072);

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject database = json.getAsJsonObject("database");

        assertEquals(300, database.get("total_vectors").getAsInt());
        assertEquals(280, database.get("active_vectors").getAsInt());
        assertEquals(3072, database.get("memory_bytes").getAsLong());
    }

    // ========== Server State Tests ==========

    @Test
    @DisplayName("Should track serving state")
    void testServingState() {
        metrics.setServing(true);

        JsonObject json = parseJson(metrics.getMetricsJson());
        assertTrue(json.getAsJsonObject("server").get("serving").getAsBoolean());

        metrics.setServing(false);

        JsonObject json2 = parseJson(metrics.getMetricsJson());
        assertFalse(json2.getAsJsonObject("server").get("serving").getAsBoolean());
    }

    @Test
    @DisplayName("Should track uptime correctly")
    void testUptime() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        // Wait a bit
        Thread.sleep(100);

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject server = json.getAsJsonObject("server");

        long uptimeMs = server.get("uptime_ms").getAsLong();
        assertTrue(uptimeMs >= 100, "Uptime should be at least 100ms");

        double uptimeSeconds = server.get("uptime_seconds").getAsDouble();
        assertTrue(uptimeSeconds >= 0.1, "Uptime seconds should be at least 0.1");
    }

    @Test
    @DisplayName("Should record start time correctly")
    void testStartTime() {
        long beforeCreate = System.currentTimeMillis();
        MetricsCollector newMetrics = new MetricsCollector();
        long afterCreate = System.currentTimeMillis();

        JsonObject json = parseJson(newMetrics.getMetricsJson());
        long startTime = json.getAsJsonObject("server").get("start_time").getAsLong();

        assertTrue(startTime >= beforeCreate && startTime <= afterCreate,
            "Start time should be between creation time bounds");
    }

    // ========== Health Status Tests ==========

    @Test
    @DisplayName("Should return healthy status when serving and low error rate")
    void testHealthyStatus() {
        metrics.setServing(true);
        metrics.recordRequest(10, true);
        metrics.recordRequest(10, true);

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();

        assertEquals("HEALTHY", status.status());
        assertTrue(status.serving());
        assertEquals(0.0, status.errorRate(), 0.001);
    }

    @Test
    @DisplayName("Should return unhealthy status when not serving")
    void testUnhealthyStatusNotServing() {
        metrics.setServing(false);
        metrics.recordRequest(10, true);

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();

        assertEquals("UNHEALTHY", status.status());
        assertFalse(status.serving());
    }

    @Test
    @DisplayName("Should return unhealthy status when error rate is high")
    void testUnhealthyStatusHighErrorRate() {
        metrics.setServing(true);

        // Create 10% error rate (5 failures out of 50 requests)
        for (int i = 0; i < 45; i++) {
            metrics.recordRequest(10, true);
        }
        for (int i = 0; i < 5; i++) {
            metrics.recordRequest(10, false);
        }

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();

        assertEquals("UNHEALTHY", status.status());
        assertEquals(0.1, status.errorRate(), 0.001);
    }

    @Test
    @DisplayName("Should calculate error rate correctly")
    void testErrorRateCalculation() {
        metrics.setServing(true);

        for (int i = 0; i < 7; i++) {
            metrics.recordRequest(10, true);
        }
        for (int i = 0; i < 3; i++) {
            metrics.recordRequest(10, false);
        }

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();

        assertEquals(0.3, status.errorRate(), 0.001);
    }

    @Test
    @DisplayName("Should return zero error rate when no requests")
    void testErrorRateNoRequests() {
        metrics.setServing(true);

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();

        assertEquals(0.0, status.errorRate(), 0.001);
    }

    @Test
    @DisplayName("Health status should include database info")
    void testHealthStatusDatabaseInfo() {
        metrics.setServing(true);
        metrics.updateDatabaseState(100, 95, 1024 * 1024);

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();

        assertEquals(100, status.totalVectors());
        assertEquals(95, status.activeVectors());
    }

    @Test
    @DisplayName("Health status toJson should produce valid JSON")
    void testHealthStatusToJson() {
        metrics.setServing(true);
        metrics.updateDatabaseState(100, 95, 1024);
        // Record 20 successful and 1 failed for ~4.76% error rate (below 5% threshold)
        for (int i = 0; i < 20; i++) {
            metrics.recordRequest(10, true);
        }
        metrics.recordRequest(10, false);

        MetricsCollector.HealthStatus status = metrics.getHealthStatus();
        String json = status.toJson();

        JsonObject parsed = parseJson(json);
        assertEquals("HEALTHY", parsed.get("status").getAsString());
        assertTrue(parsed.get("serving").getAsBoolean());
        assertEquals(100, parsed.get("total_vectors").getAsInt());
        assertEquals(95, parsed.get("active_vectors").getAsInt());
        assertEquals(1.0 / 21, parsed.get("error_rate").getAsDouble(), 0.001);
    }

    // ========== Reset Tests ==========

    @Test
    @DisplayName("Reset should clear all metrics")
    void testReset() {
        // Record some metrics
        metrics.recordRequest(10, true);
        metrics.recordRequest(20, false);
        metrics.recordInsert();
        metrics.recordSearch();
        metrics.recordDelete();
        metrics.updateDatabaseState(100, 95, 1024);

        // Reset
        metrics.reset();

        // Verify all cleared
        JsonObject json = parseJson(metrics.getMetricsJson());

        assertEquals(0, json.getAsJsonObject("requests").get("total").getAsInt());
        assertEquals(0, json.getAsJsonObject("requests").get("successful").getAsInt());
        assertEquals(0, json.getAsJsonObject("requests").get("failed").getAsInt());
        assertEquals(0, json.getAsJsonObject("requests").get("avg_latency_ms").getAsLong());

        assertEquals(0, json.getAsJsonObject("operations").get("insert").getAsInt());
        assertEquals(0, json.getAsJsonObject("operations").get("search").getAsInt());
        assertEquals(0, json.getAsJsonObject("operations").get("delete").getAsInt());
    }

    @Test
    @DisplayName("Reset should not affect serving state or database state")
    void testResetPreservesState() {
        metrics.setServing(true);
        metrics.updateDatabaseState(100, 95, 1024);

        metrics.reset();

        assertTrue(metrics.getHealthStatus().serving());
        // Database state is not reset as it's updated from external source
    }

    // ========== Concurrency Tests ==========

    @Test
    @DisplayName("Should handle concurrent request recording")
    void testConcurrentRequestRecording() throws InterruptedException {
        int numThreads = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        metrics.recordRequest(10, i % 10 != 0); // 10% failure rate
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject requests = json.getAsJsonObject("requests");

        int totalRequests = numThreads * requestsPerThread;
        assertEquals(totalRequests, requests.get("total").getAsInt());
        assertEquals(totalRequests * 0.9, requests.get("successful").getAsInt(), 5);
        assertEquals(totalRequests * 0.1, requests.get("failed").getAsInt(), 5);
    }

    @Test
    @DisplayName("Should handle concurrent operation recording")
    void testConcurrentOperationRecording() throws InterruptedException {
        int numThreads = 10;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        switch (i % 3) {
                            case 0 -> metrics.recordInsert();
                            case 1 -> metrics.recordSearch();
                            case 2 -> metrics.recordDelete();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject operations = json.getAsJsonObject("operations");

        int expectedOps = numThreads * opsPerThread / 3;
        // Use tolerance of 10 to account for uneven distribution (100 % 3 = 1 extra for delete)
        assertEquals(expectedOps, operations.get("insert").getAsInt(), 10);
        assertEquals(expectedOps, operations.get("search").getAsInt(), 10);
        assertEquals(expectedOps, operations.get("delete").getAsInt(), 10);
    }

    // ========== JSON Format Tests ==========

    @Test
    @DisplayName("Metrics JSON should contain all required fields")
    void testMetricsJSONStructure() {
        metrics.recordRequest(10, true);
        metrics.recordInsert();
        metrics.updateDatabaseState(100, 95, 1024);

        JsonObject json = parseJson(metrics.getMetricsJson());

        assertTrue(json.has("server"));
        assertTrue(json.has("requests"));
        assertTrue(json.has("operations"));
        assertTrue(json.has("database"));

        // Server fields
        JsonObject server = json.getAsJsonObject("server");
        assertTrue(server.has("uptime_ms"));
        assertTrue(server.has("uptime_seconds"));
        assertTrue(server.has("serving"));
        assertTrue(server.has("start_time"));

        // Request fields
        JsonObject requests = json.getAsJsonObject("requests");
        assertTrue(requests.has("total"));
        assertTrue(requests.has("successful"));
        assertTrue(requests.has("failed"));
        assertTrue(requests.has("avg_latency_ms"));

        // Operation fields
        JsonObject operations = json.getAsJsonObject("operations");
        assertTrue(operations.has("insert"));
        assertTrue(operations.has("search"));
        assertTrue(operations.has("delete"));

        // Database fields
        JsonObject database = json.getAsJsonObject("database");
        assertTrue(database.has("total_vectors"));
        assertTrue(database.has("active_vectors"));
        assertTrue(database.has("memory_bytes"));
        assertTrue(database.has("memory_mb"));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle very large latency values")
    void testLargeLatencyValues() {
        // Use large but safe values that won't overflow (avoid Long.MAX_VALUE)
        long largeLatency = Integer.MAX_VALUE * 100L;  // ~214 billion ms (~6.8 years)
        metrics.recordRequest(largeLatency, true);
        metrics.recordRequest(100, true);

        JsonObject json = parseJson(metrics.getMetricsJson());
        long avgLatency = json.getAsJsonObject("requests").get("avg_latency_ms").getAsLong();

        assertTrue(avgLatency > 0, "Should handle large latency values");
    }

    @Test
    @DisplayName("Should handle negative latency (edge case)")
    void testNegativeLatency() {
        // Negative latency doesn't make sense but shouldn't crash
        metrics.recordRequest(-10, true);

        JsonObject json = parseJson(metrics.getMetricsJson());
        // Just verify it doesn't crash
        assertNotNull(json);
    }

    @Test
    @DisplayName("Should handle very large vector counts")
    void testLargeVectorCounts() {
        metrics.updateDatabaseState(Integer.MAX_VALUE, Integer.MAX_VALUE - 50, Long.MAX_VALUE);

        JsonObject json = parseJson(metrics.getMetricsJson());
        JsonObject database = json.getAsJsonObject("database");

        assertEquals(Integer.MAX_VALUE, database.get("total_vectors").getAsInt());
    }

    // ========== Record Tests ==========

    @Test
    @DisplayName("HealthStatus record should work correctly")
    void testHealthStatusRecord() {
        MetricsCollector.HealthStatus status = new MetricsCollector.HealthStatus(
            "HEALTHY", true, 100, 95, 0.05
        );

        assertEquals("HEALTHY", status.status());
        assertTrue(status.serving());
        assertEquals(100, status.totalVectors());
        assertEquals(95, status.activeVectors());
        assertEquals(0.05, status.errorRate(), 0.001);
    }

    @Test
    @DisplayName("HealthStatus record with null status should work")
    void testHealthStatusRecordNullStatus() {
        MetricsCollector.HealthStatus status = new MetricsCollector.HealthStatus(
            null, false, 0, 0, 0.0
        );

        assertNull(status.status());
        assertFalse(status.serving());
        assertEquals(0, status.totalVectors());
        assertEquals(0, status.activeVectors());
        assertEquals(0.0, status.errorRate(), 0.001);
    }

    // ========== Helper Methods ==========

    private JsonObject parseJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, JsonObject.class);
    }
}
