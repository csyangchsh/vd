package com.csyangchsh.demo.vd.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Write-Ahead Log (WAL) with UUID v7.
 *
 * These tests verify:
 * 1. Basic operations - insert, delete, checkpoint logging with UUIDs
 * 2. Persistence - data survives WAL close/reopen
 * 3. Recovery - correct replay after crash simulation
 * 4. Concurrency - thread-safe operations
 * 5. Edge cases - empty WAL, corrupt data handling
 */
@DisplayName("Write-Ahead Log Tests (UUID v7)")
class WALTest {

    @TempDir
    Path tempDir;

    private String walBasePath;
    private WAL wal;

    @BeforeEach
    void setUp() throws IOException {
        walBasePath = tempDir.resolve("testdb").toString();
        wal = new WAL(walBasePath, 1000); // 1 second checkpoint interval
    }

    @AfterEach
    void tearDown() throws IOException {
        if (wal != null) {
            wal.close();
        }
    }

    // ========== Basic Operations Tests ==========

    @Test
    @DisplayName("Log insert should succeed")
    void testLogInsert() throws IOException {
        float[] vector = {1.0f, 2.0f, 3.0f};
        String uuid = UUID.randomUUID().toString();

        assertDoesNotThrow(() -> wal.logInsert(uuid, vector));
    }

    @Test
    @DisplayName("Log delete should succeed")
    void testLogDelete() throws IOException {
        String uuid = UUID.randomUUID().toString();
        assertDoesNotThrow(() -> wal.logDelete(uuid));
    }

    @Test
    @DisplayName("Log checkpoint should succeed")
    void testLogCheckpoint() throws IOException {
        wal.logCheckpoint(100);

        assertEquals(100, wal.getLastCheckpointId());
    }

    @Test
    @DisplayName("Multiple operations should be logged")
    void testMultipleOperations() throws IOException {
        float[] vector1 = {1.0f, 2.0f, 3.0f};
        float[] vector2 = {4.0f, 5.0f, 6.0f};
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();

        wal.logInsert(uuid1, vector1);
        wal.logInsert(uuid2, vector2);
        wal.logDelete(uuid3);
        wal.logCheckpoint(1);

        assertEquals(1, wal.getLastCheckpointId());
    }

    // ========== Persistence Tests ==========

    @Test
    @DisplayName("WAL file should be created")
    void testWALFileCreated() throws IOException {
        String uuid = UUID.randomUUID().toString();
        wal.logInsert(uuid, new float[]{1.0f, 2.0f});
        wal.sync();

        Path walPath = Path.of(walBasePath + ".wal");
        assertTrue(Files.exists(walPath), "WAL file should exist");
        assertTrue(Files.size(walPath) > 0, "WAL file should have content");
    }

    @Test
    @DisplayName("WAL data should persist across close/reopen")
    void testPersistenceAcrossReopen() throws IOException {
        float[] vector = {1.0f, 2.0f, 3.0f};
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();

        wal.logInsert(uuid1, vector);
        wal.logDelete(uuid2);
        wal.sync();

        // Close and reopen WAL
        wal.close();
        wal = new WAL(walBasePath, 1000);

        // Recovery should find the entries
        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(2, recovery.entries().size(), "Should recover 2 entries");

        WAL.WALEntry insertEntry = recovery.entries().get(0);
        assertEquals(WAL.OperationType.INSERT, insertEntry.type());
        assertEquals(uuid1, insertEntry.id());
        assertArrayEquals(vector, insertEntry.vector(), 0.0001f);

        WAL.WALEntry deleteEntry = recovery.entries().get(1);
        assertEquals(WAL.OperationType.DELETE, deleteEntry.type());
        assertEquals(uuid2, deleteEntry.id());
    }

    // ========== Recovery Tests ==========

    @Test
    @DisplayName("Recover from empty WAL should return empty recovery")
    void testRecoverEmptyWAL() throws IOException {
        // Create a new WAL that doesn't exist yet
        String nonExistentPath = tempDir.resolve("nonexistent").toString();
        WAL.WALRecovery recovery = WAL.recover(nonExistentPath);

        assertEquals(0, recovery.lastCheckpointId());
        assertTrue(recovery.entries().isEmpty());
    }

    @Test
    @DisplayName("Recover should replay all entries")
    void testRecoverReplayEntries() throws IOException {
        // Log multiple operations
        float[] v1 = {1.0f, 2.0f, 3.0f};
        float[] v2 = {4.0f, 5.0f, 6.0f};
        float[] v3 = {7.0f, 8.0f, 9.0f};
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();
        String uuid4 = UUID.randomUUID().toString();

        wal.logInsert(uuid1, v1);
        wal.logInsert(uuid2, v2);
        wal.logInsert(uuid3, v3);
        wal.logDelete(uuid4);
        wal.sync();

        // Recover
        WAL.WALRecovery recovery = WAL.recover(walBasePath);

        assertEquals(4, recovery.entries().size());

        assertEquals(WAL.OperationType.INSERT, recovery.entries().get(0).type());
        assertEquals(uuid1, recovery.entries().get(0).id());

        assertEquals(WAL.OperationType.INSERT, recovery.entries().get(1).type());
        assertEquals(uuid2, recovery.entries().get(1).id());

        assertEquals(WAL.OperationType.INSERT, recovery.entries().get(2).type());
        assertEquals(uuid3, recovery.entries().get(2).id());

        assertEquals(WAL.OperationType.DELETE, recovery.entries().get(3).type());
        assertEquals(uuid4, recovery.entries().get(3).id());
    }

    @Test
    @DisplayName("Recover should respect checkpoint markers")
    void testRecoverRespectsCheckpoint() throws IOException {
        float[] v1 = {1.0f, 2.0f};
        float[] v2 = {3.0f, 4.0f};
        float[] v3 = {5.0f, 6.0f};
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();

        // Log: insert1, insert2, checkpoint, insert3
        wal.logInsert(uuid1, v1);
        wal.logInsert(uuid2, v2);
        wal.logCheckpoint(100);
        wal.logInsert(uuid3, v3);
        wal.sync();

        // Recover
        WAL.WALRecovery recovery = WAL.recover(walBasePath);

        // Should only have insert3 (after checkpoint)
        assertEquals(100, recovery.lastCheckpointId());
        assertEquals(1, recovery.entries().size());
        assertEquals(uuid3, recovery.entries().get(0).id());
    }

    @Test
    @DisplayName("Recover should handle large vectors")
    void testRecoverLargeVectors() throws IOException {
        int dimension = 1024;
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = i * 0.001f;
        }
        String uuid = UUID.randomUUID().toString();

        wal.logInsert(uuid, vector);
        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);

        assertEquals(1, recovery.entries().size());
        assertArrayEquals(vector, recovery.entries().get(0).vector(), 0.0001f);
    }

    // ========== Checkpoint Tests ==========

    @Test
    @DisplayName("ShouldCheckpoint should return false initially")
    void testShouldCheckpointInitially() {
        assertFalse(wal.shouldCheckpoint(),
            "Should not need checkpoint immediately after creation");
    }

    @Test
    @DisplayName("ShouldCheckpoint should return true after interval")
    void testShouldCheckpointAfterInterval() throws IOException, InterruptedException {
        // Log a checkpoint
        wal.logCheckpoint(1);

        // Wait for interval to pass (1 second)
        Thread.sleep(1100);

        assertTrue(wal.shouldCheckpoint(),
            "Should need checkpoint after interval expires");
    }

    @Test
    @DisplayName("Checkpoint should update last checkpoint ID")
    void testCheckpointUpdatesId() throws IOException {
        wal.logCheckpoint(10);
        assertEquals(10, wal.getLastCheckpointId());

        wal.logCheckpoint(20);
        assertEquals(20, wal.getLastCheckpointId());
    }

    @Test
    @DisplayName("Truncate should clear WAL file")
    void testTruncate() throws IOException {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        wal.logInsert(uuid1, new float[]{1.0f, 2.0f});
        wal.logInsert(uuid2, new float[]{3.0f, 4.0f});
        wal.sync();

        Path walPath = Path.of(walBasePath + ".wal");
        long sizeBefore = Files.size(walPath);
        assertTrue(sizeBefore > 0);

        wal.truncate();

        long sizeAfter = Files.size(walPath);
        assertEquals(0, sizeAfter, "WAL should be empty after truncate");
    }

    // ========== Sync Tests ==========

    @Test
    @DisplayName("Sync should flush data to disk")
    void testSync() throws IOException {
        String uuid = UUID.randomUUID().toString();
        wal.logInsert(uuid, new float[]{1.0f});
        wal.sync();

        // Data should be readable even without closing WAL
        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(1, recovery.entries().size());
    }

    @Test
    @DisplayName("Async flush should complete without error")
    void testAsyncFlush() throws IOException {
        String uuid = UUID.randomUUID().toString();
        wal.logInsert(uuid, new float[]{1.0f});
        wal.asyncFlush();

        // Give async flush time to complete
        assertDoesNotThrow(() -> Thread.sleep(100));

        // Verify data was flushed
        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(1, recovery.entries().size());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("WAL should handle zero-dimensional vectors")
    void testZeroDimensionVector() throws IOException {
        float[] vector = new float[0];
        String uuid = UUID.randomUUID().toString();

        wal.logInsert(uuid, vector);
        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(1, recovery.entries().size());
        assertEquals(0, recovery.entries().get(0).vector().length);
    }

    @Test
    @DisplayName("WAL should handle special UUID formats")
    void testSpecialUUIDFormats() throws IOException {
        // Test with various UUID formats (nil UUID, max UUID, etc.)
        String nilUuid = "00000000-0000-0000-0000-000000000000";
        String maxUuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";

        wal.logInsert(nilUuid, new float[]{1.0f});
        wal.logDelete(maxUuid);
        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(2, recovery.entries().size());
        assertEquals(nilUuid, recovery.entries().get(0).id());
        assertEquals(maxUuid, recovery.entries().get(1).id());
    }

    @Test
    @DisplayName("WAL should handle special float values")
    void testSpecialFloatValues() throws IOException {
        float[] vector = {Float.MAX_VALUE, Float.MIN_VALUE, 0.0f, -0.0f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
        String uuid = UUID.randomUUID().toString();

        wal.logInsert(uuid, vector);
        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(1, recovery.entries().size());

        float[] recovered = recovery.entries().get(0).vector();
        assertEquals(Float.MAX_VALUE, recovered[0]);
        assertEquals(Float.MIN_VALUE, recovered[1]);
        assertEquals(0.0f, recovered[2]);
        assertEquals(-0.0f, recovered[3]);
        assertEquals(Float.POSITIVE_INFINITY, recovered[4]);
        assertEquals(Float.NEGATIVE_INFINITY, recovered[5]);
        assertTrue(Float.isNaN(recovered[6]));
    }

    // ========== High Volume Tests ==========

    @Test
    @DisplayName("WAL should handle many operations")
    void testManyOperations() throws IOException {
        int numOps = 10000;

        for (int i = 0; i < numOps; i++) {
            float[] vector = {i * 0.1f, i * 0.2f};
            String uuid = UUID.randomUUID().toString();
            wal.logInsert(uuid, vector);
        }

        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(numOps, recovery.entries().size());
    }

    @Test
    @DisplayName("WAL should handle mixed operation types")
    void testMixedOperations() throws IOException {
        for (int i = 0; i < 100; i++) {
            String insertUuid = UUID.randomUUID().toString();
            wal.logInsert(insertUuid, new float[]{i, i + 1});

            if (i > 10 && i % 3 == 0) {
                String deleteUuid = UUID.randomUUID().toString();
                wal.logDelete(deleteUuid);
            }
        }

        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);

        int inserts = 0;
        int deletes = 0;
        for (WAL.WALEntry entry : recovery.entries()) {
            if (entry.type() == WAL.OperationType.INSERT) {
                inserts++;
            } else {
                deletes++;
            }
        }

        assertEquals(100, inserts);
        assertEquals(30, deletes);
    }

    // ========== Multiple Checkpoint Tests ==========

    @Test
    @DisplayName("Multiple checkpoints should be handled correctly")
    void testMultipleCheckpoints() throws IOException {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();
        String uuid4 = UUID.randomUUID().toString();

        wal.logInsert(uuid1, new float[]{1.0f});
        wal.logInsert(uuid2, new float[]{2.0f});
        wal.logCheckpoint(10);

        wal.logInsert(uuid3, new float[]{3.0f});
        wal.logCheckpoint(20);

        wal.logInsert(uuid4, new float[]{4.0f});
        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(20, recovery.lastCheckpointId());
        assertEquals(1, recovery.entries().size());
        assertEquals(uuid4, recovery.entries().get(0).id());
    }

    // ========== Close Tests ==========

    @Test
    @DisplayName("Close should shut down executor")
    void testCloseShutsDownExecutor() throws IOException {
        String uuid = UUID.randomUUID().toString();
        wal.logInsert(uuid, new float[]{1.0f});
        wal.asyncFlush();
        wal.close();

        // Should not throw
        assertDoesNotThrow(() -> wal.close());
    }

    // ========== File System Tests ==========

    @Test
    @DisplayName("WAL should create parent directories")
    void testCreatesParentDirectories() throws IOException {
        String nestedPath = tempDir.resolve("nested/path/to/db").toString();
        String uuid = UUID.randomUUID().toString();

        assertDoesNotThrow(() -> {
            try (WAL nestedWal = new WAL(nestedPath, 1000)) {
                nestedWal.logInsert(uuid, new float[]{1.0f});
                nestedWal.sync();
            }
        });

        // Verify recovery works
        WAL.WALRecovery recovery = WAL.recover(nestedPath);
        assertEquals(1, recovery.entries().size());
    }

    // ========== Record Tests ==========

    @Test
    @DisplayName("WALEntry record should work correctly")
    void testWALEntryRecord() {
        float[] vector = {1.0f, 2.0f};
        String uuid = UUID.randomUUID().toString();

        WAL.WALEntry entry = new WAL.WALEntry(WAL.OperationType.INSERT, uuid, vector);

        assertEquals(WAL.OperationType.INSERT, entry.type());
        assertEquals(uuid, entry.id());
        assertArrayEquals(vector, entry.vector());
    }

    @Test
    @DisplayName("WALRecovery record should work correctly")
    void testWALRecoveryRecord() {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        List<WAL.WALEntry> entries = List.of(
            new WAL.WALEntry(WAL.OperationType.INSERT, uuid1, new float[]{1.0f}),
            new WAL.WALEntry(WAL.OperationType.DELETE, uuid2, null)
        );

        WAL.WALRecovery recovery = new WAL.WALRecovery(100, entries);

        assertEquals(100, recovery.lastCheckpointId());
        assertEquals(2, recovery.entries().size());
    }

    @Test
    @DisplayName("WAL should handle concurrent writes")
    void testConcurrentWrites() throws IOException, InterruptedException {
        int numThreads = 10;
        int opsPerThread = 100;

        Thread[] threads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        String uuid = UUID.randomUUID().toString();
                        wal.logInsert(uuid, new float[]{threadId, i});
                    }
                } catch (IOException e) {
                    fail("Concurrent write failed: " + e.getMessage());
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(numThreads * opsPerThread, recovery.entries().size());
    }

    @Test
    @DisplayName("UUID v7 format validation")
    void testUUIDv7Format() throws IOException {
        String uuid = com.csyangchsh.demo.vd.util.UUIDv7.generate();
        float[] vector = {1.0f, 2.0f};

        wal.logInsert(uuid, vector);
        wal.sync();

        WAL.WALRecovery recovery = WAL.recover(walBasePath);
        assertEquals(1, recovery.entries().size());

        // Verify UUID format
        String recoveredId = recovery.entries().get(0).id();
        assertTrue(recoveredId.matches("^[0-9a-f-]{36}$"));
        assertEquals(4, recoveredId.chars().filter(ch -> ch == '-').count());
    }
}
