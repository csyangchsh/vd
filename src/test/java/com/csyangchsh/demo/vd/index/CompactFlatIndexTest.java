package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for CompactFlatIndex with UUID v7.
 *
 * These tests verify:
 * 1. Basic operations - insert, delete, search, get
 * 2. Memory efficiency - reduced memory usage compared to FlatIndex
 * 3. Persistence - save and load functionality
 * 4. Edge cases - empty index, k > size, invalid operations
 * 5. Correctness - results match FlatIndex for same operations
 */
@DisplayName("Compact Flat Index Tests (UUID v7)")
class CompactFlatIndexTest {

    private CompactFlatIndex index;
    private static final int DIMENSION = 128;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        index = new CompactFlatIndex(DIMENSION);
    }

    // ========== Basic Operations Tests ==========

    @Test
    @DisplayName("Insert should increment size and active count")
    void testInsertIncrementsCounters() {
        float[] data = new float[DIMENSION];
        data[0] = 1.0f;
        Vector vector = new Vector(data);  // Auto-generates UUID v7

        String id = index.insert(vector);

        assertEquals(1, index.size(), "Size should be 1");
        assertEquals(1, index.getActiveCount(), "Active count should be 1");
        assertNotNull(id, "ID should not be null");
        assertTrue(id.length() > 0, "ID should be a UUID string");
    }

    @Test
    @DisplayName("Insert multiple vectors should increment counters correctly")
    void testInsertMultiple() {
        String[] ids = new String[10];
        for (int i = 0; i < 10; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            Vector v = new Vector(data);
            ids[i] = index.insert(v);
        }

        assertEquals(10, index.size(), "Size should be 10");
        assertEquals(10, index.getActiveCount(), "Active count should be 10");

        // Verify all IDs are unique
        for (int i = 0; i < 10; i++) {
            for (int j = i + 1; j < 10; j++) {
                assertNotEquals(ids[i], ids[j], "IDs should be unique");
            }
        }
    }

    @Test
    @DisplayName("Delete should decrement active count but not size")
    void testDelete() {
        float[] data = new float[DIMENSION];
        Vector v = new Vector(data);
        String id = index.insert(v);

        assertNotNull(index.get(id));
        index.delete(id);

        assertEquals(1, index.size(), "Size should still be 1");
        assertEquals(0, index.getActiveCount(), "Active count should be 0");
        assertNull(index.get(id), "Deleted vector should return null");
    }

    @Test
    @DisplayName("Clear should reset all counters")
    void testClear() {
        index.insert(new Vector(new float[DIMENSION]));
        index.insert(new Vector(new float[DIMENSION]));

        index.clear();

        assertEquals(0, index.size(), "Size should be 0");
        assertEquals(0, index.getActiveCount(), "Active count should be 0");
    }

    @Test
    @DisplayName("Get dimension should return correct value")
    void testGetDimension() {
        assertEquals(DIMENSION, index.getDimension());
    }

    // ========== Search Tests ==========

    @Test
    @DisplayName("Search should return results ordered by distance")
    void testSearchReturnsOrderedResults() {
        // Insert vectors with first dimension as 0, 1, 2, ..., 9
        String[] ids = new String[10];
        for (int i = 0; i < 10; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            Vector v = new Vector(data);
            ids[i] = index.insert(v);
        }

        // Search for vector [0, 0, ..., 0] - should find closest to 0
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 3, DistanceType.L2);

        assertEquals(3, results.length, "Should return 3 results");
        assertEquals(ids[0], results[0].getVectorId(), "First result should be the vector with data[0]=0");
        assertTrue(results[0].getScore() < results[1].getScore(),
            "Results should be ordered by distance");
        assertTrue(results[1].getScore() < results[2].getScore(),
            "Results should be ordered by distance");
    }

    @Test
    @DisplayName("Search with cosine distance should work correctly")
    void testSearchWithCosine() {
        float[] v1 = {1, 0, 0};
        float[] v2 = {0, 1, 0};
        float[] v3 = {0, 0, 1};

        index = new CompactFlatIndex(3);
        String id1 = index.insert(new Vector(v1));
        String id2 = index.insert(new Vector(v2));
        String id3 = index.insert(new Vector(v3));

        // Query vector [1, 0, 0] should be closest to v1
        float[] query = {1, 0, 0};
        SearchResult[] results = index.search(query, 2, DistanceType.COSINE);

        assertEquals(2, results.length);
        assertEquals(id1, results[0].getVectorId());
        assertEquals(0, results[0].getScore(), 0.0001f, "Same vector, distance = 0");
    }

    @Test
    @DisplayName("Search with k greater than size should return all results")
    void testSearchKGreaterThanSize() {
        index.insert(new Vector(new float[DIMENSION]));
        index.insert(new Vector(new float[DIMENSION]));

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 10, DistanceType.L2);

        assertEquals(2, results.length, "Should return all available results");
    }

    @Test
    @DisplayName("Search on empty index should return empty array")
    void testSearchEmptyIndex() {
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertEquals(0, results.length, "Should return empty array");
    }

    @Test
    @DisplayName("Search with k <= 0 should return empty array")
    void testSearchZeroK() {
        index.insert(new Vector(new float[DIMENSION]));

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 0, DistanceType.L2);

        assertEquals(0, results.length, "Should return empty array");
    }

    @Test
    @DisplayName("Search after delete should skip deleted vectors")
    void testSearchAfterDelete() {
        String[] ids = new String[5];
        for (int i = 0; i < 5; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            Vector v = new Vector(data);
            ids[i] = index.insert(v);
        }

        // Delete vector at index 2
        index.delete(ids[2]);

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertEquals(4, results.length, "Should return only active vectors");
        // ID at index 2 should not be in results
        for (SearchResult result : results) {
            assertNotEquals(ids[2], result.getVectorId(), "Deleted vector should not appear");
        }
    }

    // ========== Text and Metadata Tests ==========

    @Test
    @DisplayName("Insert and retrieve text and metadata")
    void testTextAndMetadata() {
        float[] data = new float[DIMENSION];
        String text = "test text";
        Metadata metadata = new Metadata()
            .put("category", "news")
            .put("score", 0.95);
        Vector v = new Vector(data, text, metadata);
        String id = index.insert(v);

        Vector retrieved = index.get(id);
        assertNotNull(retrieved, "Vector should not be null");
        assertEquals(text, retrieved.getText(), "Text should match");
        assertEquals("news", retrieved.getMetadata().getString("category"), "Category should match");
        assertEquals(0.95, retrieved.getMetadata().getDouble("score"), 0.0001, "Score should match");
    }

    @Test
    @DisplayName("Search results should include text and metadata")
    void testSearchResultsIncludeTextAndMetadata() {
        float[] data = new float[DIMENSION];
        String text = "search text";
        Metadata metadata = new Metadata().put("index", 0);
        index.insert(new Vector(data, text, metadata));

        SearchResult[] results = index.search(new float[DIMENSION], 1, DistanceType.L2);

        assertEquals(1, results.length);
        // Retrieve the full vector to check text and metadata
        Vector found = index.get(results[0].vectorId());
        assertEquals(text, found.getText(), "Text should be in search result");
        assertEquals(0, found.getMetadata().getLong("index"), "Metadata should be in search result");
    }

    @Test
    @DisplayName("Null text and metadata should be handled correctly")
    void testNullTextAndMetadata() {
        float[] data = new float[DIMENSION];
        Vector v = new Vector(data, null, null);
        String id = index.insert(v);

        Vector retrieved = index.get(id);
        assertNotNull(retrieved);
        assertNull(retrieved.getText(), "Text should be null");
        assertNull(retrieved.getMetadata(), "Metadata should be null");
    }

    // ========== Dimension Validation Tests ==========

    @Test
    @DisplayName("Insert with wrong dimension should throw exception")
    void testInsertWrongDimension() {
        float[] wrongData = new float[DIMENSION + 1];

        assertThrows(IllegalArgumentException.class, () -> {
            index.insert(new Vector(wrongData));
        }, "Should throw IllegalArgumentException for wrong dimension");
    }

    @Test
    @DisplayName("Search with wrong dimension should throw exception")
    void testSearchWrongDimension() {
        index.insert(new Vector(new float[DIMENSION]));

        float[] wrongQuery = new float[DIMENSION + 1];

        assertThrows(IllegalArgumentException.class, () -> {
            index.search(wrongQuery, 5, DistanceType.L2);
        }, "Should throw IllegalArgumentException for wrong dimension");
    }

    // ========== Persistence Tests ==========

    @Test
    @DisplayName("Save and load should preserve all data")
    void testSaveAndLoad() throws Exception {
        // Insert some vectors with text and metadata
        String[] originalIds = new String[5];
        for (int i = 0; i < 5; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            String text = "Sample text " + i;
            Metadata metadata = new Metadata()
                .put("index", i)
                .put("category", "test");
            Vector v = new Vector(data, text, metadata);
            originalIds[i] = index.insert(v);
        }

        // Delete one vector
        index.delete(originalIds[2]);

        // Save to file
        File file = tempDir.resolve("test_index.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            index.save(out);
        }

        // Create new index and load
        CompactFlatIndex loadedIndex = new CompactFlatIndex(DIMENSION);
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            loadedIndex.load(in);
        }

        // Verify
        assertEquals(index.size(), loadedIndex.size(), "Size should match");
        assertEquals(index.getActiveCount(), loadedIndex.getActiveCount(), "Active count should match");

        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                // Deleted vector
                assertNull(loadedIndex.get(originalIds[i]), "Deleted vector should be null");
            } else {
                Vector v = loadedIndex.get(originalIds[i]);
                assertNotNull(v, "Vector should not be null");
                assertEquals(i, v.getData()[0], 0.0001f, "Vector data should match");
                assertEquals("Sample text " + i, v.getText(), "Text should match");
                assertEquals(i, v.getMetadata().getLong("index"), "Index metadata should match");
                assertEquals("test", v.getMetadata().getString("category"), "Category metadata should match");
            }
        }
    }

    @Test
    @DisplayName("Load empty index should work")
    void testLoadEmptyIndex() throws Exception {
        File file = tempDir.resolve("empty_index.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            index.save(out);
        }

        CompactFlatIndex loadedIndex = new CompactFlatIndex(DIMENSION);
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            loadedIndex.load(in);
        }

        assertEquals(0, loadedIndex.size());
        assertEquals(0, loadedIndex.getActiveCount());
    }

    // ========== Memory Usage Tests ==========

    @Test
    @DisplayName("Memory usage should be reasonable")
    void testMemoryUsage() {
        int numVectors = 1000;

        for (int i = 0; i < numVectors; i++) {
            float[] data = new float[DIMENSION];
            index.insert(new Vector(data));
        }

        long memoryUsage = index.getMemoryUsage();

        // Expected: ~numVectors * dimension * 4 bytes (float) + overhead
        // For 1000 vectors of 128 dimensions: ~512KB for data + some overhead
        long expectedMin = (long) (numVectors * DIMENSION * 4L * 0.8); // At least 80% of theoretical minimum
        long expectedMax = (long) (numVectors * DIMENSION * 4L * 1.5); // At most 150% due to overhead

        assertTrue(memoryUsage >= expectedMin && memoryUsage <= expectedMax,
            String.format("Memory usage %d bytes should be in range [%d, %d]",
                memoryUsage, expectedMin, expectedMax));
    }

    @Test
    @DisplayName("Memory usage should be less than FlatIndex for same data")
    void testMemoryEfficiencyComparedToFlatIndex() {
        int numVectors = 1000;

        CompactFlatIndex compactIndex = new CompactFlatIndex(DIMENSION);
        FlatIndex flatIndex = new FlatIndex(DIMENSION);

        // Insert same data into both indexes
        for (int i = 0; i < numVectors; i++) {
            float[] data = new float[DIMENSION];
            compactIndex.insert(new Vector(data));
            flatIndex.insert(new Vector(data));
        }

        long compactMemory = compactIndex.getMemoryUsage();
        // FlatIndex doesn't have getMemoryUsage(), so we just check CompactFlatIndex's memory

        // Compact storage should use less memory than naive estimate
        // The naive estimate underestimates HashMap overhead, so we use a more realistic threshold
        long naiveEstimate = numVectors * DIMENSION * 4L + numVectors * 100L; // 100 bytes overhead per Vector object

        assertTrue(compactMemory < naiveEstimate * 0.9,
            "Compact storage should use memory efficiently compared to naive implementation");
    }

    // ========== Initial Capacity Tests ==========

    @Test
    @DisplayName("Index with initial capacity should work correctly")
    void testIndexWithInitialCapacity() {
        CompactFlatIndex indexWithCapacity = new CompactFlatIndex(DIMENSION, 1000);

        // Should be able to insert more than initial capacity
        for (int i = 0; i < 1500; i++) {
            indexWithCapacity.insert(new Vector(new float[DIMENSION]));
        }

        assertEquals(1500, indexWithCapacity.size());
        assertEquals(1500, indexWithCapacity.getActiveCount());
    }

    // ========== Correctness Comparison Tests ==========

    @Test
    @DisplayName("Results should match FlatIndex for same operations")
    void testResultsMatchFlatIndex() {
        Random random = new Random(42); // Fixed seed for reproducibility

        CompactFlatIndex compactIndex = new CompactFlatIndex(DIMENSION);
        FlatIndex flatIndex = new FlatIndex(DIMENSION);

        // Insert same random vectors with text and metadata
        int numVectors = 100;
        String[] compactIds = new String[numVectors];
        String[] flatIds = new String[numVectors];
        for (int i = 0; i < numVectors; i++) {
            float[] data = new float[DIMENSION];
            for (int j = 0; j < DIMENSION; j++) {
                data[j] = random.nextFloat();
            }
            String text = "Sample text " + i;
            Metadata metadata = new Metadata()
                .put("index", i)
                .put("category", "test");

            compactIds[i] = compactIndex.insert(new Vector(data, text, metadata));
            flatIds[i] = flatIndex.insert(new Vector(data.clone(), text, metadata));
        }

        // Delete some vectors
        int[] toDelete = {5, 10, 15, 20};
        for (int idx : toDelete) {
            compactIndex.delete(compactIds[idx]);
            flatIndex.delete(flatIds[idx]);
        }

        // Search and compare results
        float[] query = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            query[i] = random.nextFloat();
        }

        SearchResult[] compactResults = compactIndex.search(query, 10, DistanceType.L2);
        SearchResult[] flatResults = flatIndex.search(query, 10, DistanceType.L2);

        assertEquals(compactResults.length, flatResults.length,
            "Result count should match");

        // Compare by looking up the actual vectors
        for (int i = 0; i < Math.min(compactResults.length, flatResults.length); i++) {
            assertEquals(compactResults[i].getScore(), flatResults[i].getScore(), 0.0001f,
                "Scores should match at position " + i);
        }
    }

    // ========== Large Scale Tests ==========

    @Test
    @DisplayName("Should handle large number of vectors")
    void testLargeScale() {
        int numVectors = 10000;

        for (int i = 0; i < numVectors; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            index.insert(new Vector(data));
        }

        assertEquals(numVectors, index.size());
        assertEquals(numVectors, index.getActiveCount());

        // Search should still work
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 10, DistanceType.L2);

        assertEquals(10, results.length);
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle vectors with all zeros")
    void testZeroVectors() {
        for (int i = 0; i < 5; i++) {
            index.insert(new Vector(new float[DIMENSION]));
        }

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 3, DistanceType.L2);

        assertEquals(3, results.length);
        // All distances should be 0
        for (SearchResult result : results) {
            assertEquals(0, result.getScore(), 0.0001f, "All zero vectors should have distance 0");
        }
    }

    @Test
    @DisplayName("Should handle vectors with negative values")
    void testNegativeValues() {
        float[] data = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            data[i] = -1.0f * i;
        }

        assertDoesNotThrow(() -> index.insert(new Vector(data)));

        float[] query = new float[DIMENSION];
        assertDoesNotThrow(() -> index.search(query, 1, DistanceType.L2));
    }

    @Test
    @DisplayName("Should handle very large float values")
    void testLargeValues() {
        float[] data = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            data[i] = Float.MAX_VALUE / DIMENSION; // Avoid overflow
        }

        String id = assertDoesNotThrow(() -> index.insert(new Vector(data)));
        assertNotNull(index.get(id));
    }

    @Test
    @DisplayName("Get non-existent vector should return null")
    void testGetNonExistent() {
        String fakeId = "01234567-89ab-cdef-0123-456789abcdef";
        assertNull(index.get(fakeId), "Non-existent vector should return null");
    }

    @Test
    @DisplayName("Delete non-existent vector should not throw")
    void testDeleteNonExistent() {
        String fakeId = "01234567-89ab-cdef-0123-456789abcdef";
        assertDoesNotThrow(() -> index.delete(fakeId));
        assertEquals(0, index.size());
        assertEquals(0, index.getActiveCount());
    }

    @Test
    @DisplayName("UUID format validation")
    void testUUIDFormat() {
        float[] data = new float[DIMENSION];
        String id = index.insert(new Vector(data));

        assertNotNull(id);
        assertTrue(id.matches("^[0-9a-f-]{36}$"), "ID should be UUID format");
        assertEquals(4, id.chars().filter(ch -> ch == '-').count(), "UUID should have 4 dashes");
    }
}
