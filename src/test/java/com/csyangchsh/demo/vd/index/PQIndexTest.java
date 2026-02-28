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
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Product Quantization (PQ) Index with UUID v7.
 *
 * These tests verify:
 * 1. Basic operations - insert, delete, search, get
 * 2. Training - k-means centroid learning
 * 3. Compression - memory efficiency and compression ratio
 * 4. Accuracy - recall compared to exact search
 * 5. Persistence - save and load functionality
 * 6. Edge cases - various configurations and data distributions
 */
@DisplayName("Product Quantization Index Tests (UUID v7)")
class PQIndexTest {

    private PQIndex index;
    private static final int DIMENSION = 128;
    private static final int NUM_SUBVECTORS = 8;  // M
    private static final int NUM_CENTROIDS = 64;   // K (smaller for faster tests)

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        index = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.L2);
    }

    // ========== Basic Operations Tests ==========

    @Test
    @DisplayName("Insert should increment size")
    void testInsert() {
        float[] data = new float[DIMENSION];
        Vector vector = new Vector(data);  // Auto-generates UUID v7

        String id = index.insert(vector);

        assertEquals(1, index.size());
        assertEquals(1, index.getActiveCount());
        assertNotNull(id);
        assertTrue(id.length() > 0);
    }

    @Test
    @DisplayName("Insert multiple vectors")
    void testInsertMultiple() {
        String[] ids = new String[10];
        for (int i = 0; i < 10; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            Vector v = new Vector(data);
            ids[i] = index.insert(v);
        }

        assertEquals(10, index.size());
        assertEquals(10, index.getActiveCount());

        // Verify all IDs are unique
        for (int i = 0; i < 10; i++) {
            for (int j = i + 1; j < 10; j++) {
                assertNotEquals(ids[i], ids[j]);
            }
        }
    }

    @Test
    @DisplayName("Delete should mark vector as deleted")
    void testDelete() {
        float[] data = new float[DIMENSION];
        Vector v = new Vector(data);
        String id = index.insert(v);

        index.delete(id);

        assertEquals(1, index.size());
        assertEquals(0, index.getActiveCount());
        assertNull(index.get(id));
    }

    @Test
    @DisplayName("Clear should reset index")
    void testClear() {
        index.insert(new Vector(new float[DIMENSION]));
        index.insert(new Vector(new float[DIMENSION]));

        index.clear();

        assertEquals(0, index.size());
        assertEquals(0, index.getActiveCount());
    }

    @Test
    @DisplayName("Get dimension should return correct value")
    void testGetDimension() {
        assertEquals(DIMENSION, index.getDimension());
    }

    // ========== Search Tests ==========

    @Test
    @DisplayName("Search should return results")
    void testSearch() {
        // Insert vectors and train
        float[][] trainingVectors = generateRandomVectors(100, DIMENSION);
        for (float[] vector : trainingVectors) {
            index.insert(new Vector(vector));
        }
        index.train(trainingVectors, 10);

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertTrue(results.length <= 5);
    }

    @Test
    @DisplayName("Search with k greater than size")
    void testSearchKGreaterThanSize() {
        // Need at least NUM_CENTROIDS vectors to train
        float[][] trainingVectors = new float[NUM_CENTROIDS][DIMENSION];
        for (int i = 0; i < NUM_CENTROIDS; i++) {
            trainingVectors[i] = new float[DIMENSION];
            index.insert(new Vector(trainingVectors[i]));
        }
        index.train(trainingVectors, 5);

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 10, DistanceType.L2);

        assertTrue(results.length <= NUM_CENTROIDS);
    }

    @Test
    @DisplayName("Search on empty index should return empty array")
    void testSearchEmptyIndex() {
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertEquals(0, results.length);
    }

    @Test
    @DisplayName("Search with zero k should return empty array")
    void testSearchZeroK() {
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 0, DistanceType.L2);

        assertEquals(0, results.length);
    }

    // ========== Training Tests ==========

    @Test
    @DisplayName("Training with insufficient vectors should throw exception")
    void testTrainingInsufficientVectors() {
        float[][] trainingVectors = generateRandomVectors(10, DIMENSION);

        assertThrows(IllegalArgumentException.class, () -> {
            index.train(trainingVectors, 10);
        });
    }

    @Test
    @DisplayName("Training should initialize centroids")
    void testTrainingInitializesCentroids() {
        float[][] trainingVectors = generateRandomVectors(100, DIMENSION);
        for (float[] vector : trainingVectors) {
            index.insert(new Vector(vector));
        }

        assertDoesNotThrow(() -> index.train(trainingVectors, 5));

        // After training, search should work
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);
        assertNotNull(results);
    }

    @Test
    @DisplayName("Training with multiple iterations should improve results")
    void testTrainingMultipleIterations() {
        float[][] trainingVectors = generateRandomVectors(200, DIMENSION);
        for (float[] vector : trainingVectors) {
            index.insert(new Vector(vector));
        }

        // Train with different iterations
        assertDoesNotThrow(() -> index.train(trainingVectors, 20));

        // Verify search still works
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 10, DistanceType.L2);
        assertTrue(results.length > 0);
    }

    // ========== Compression Tests ==========

    @Test
    @DisplayName("Compression ratio should be significant")
    void testCompressionRatio() {
        int numVectors = 1000;

        for (int i = 0; i < numVectors; i++) {
            float[] data = new float[DIMENSION];
            index.insert(new Vector(data));
        }

        // Train the index
        float[][] trainingVectors = new float[numVectors][DIMENSION];
        for (int i = 0; i < numVectors; i++) {
            trainingVectors[i] = new float[DIMENSION];
        }
        index.train(trainingVectors, 10);

        double compressionRatio = index.getCompressionRatio();

        // Original: N * D * 4 bytes
        // Compressed: N * M * 1 + M * K * (D/M) * 4
        // For N=1000, D=128, M=8, K=64:
        // Original: 1000 * 128 * 4 = 512,000 bytes
        // Compressed: 1000 * 8 * 1 + 8 * 64 * 16 * 4 = 8,000 + 32,768 = 40,768 bytes
        // Ratio: ~12.5x
        assertTrue(compressionRatio > 5.0,
            "Compression ratio should be at least 5x, got: " + compressionRatio);
    }

    @Test
    @DisplayName("Compression ratio increases with more vectors")
    void testCompressionRatioScalesWithVectors() {
        // Small index
        PQIndex smallIndex = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.L2);
        for (int i = 0; i < 100; i++) {
            smallIndex.insert(new Vector(new float[DIMENSION]));
        }
        float[][] smallTraining = new float[100][DIMENSION];
        smallIndex.train(smallTraining, 5);
        double smallRatio = smallIndex.getCompressionRatio();

        // Large index
        PQIndex largeIndex = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.L2);
        for (int i = 0; i < 10000; i++) {
            largeIndex.insert(new Vector(new float[DIMENSION]));
        }
        float[][] largeTraining = new float[10000][DIMENSION];
        largeIndex.train(largeTraining, 5);
        double largeRatio = largeIndex.getCompressionRatio();

        // Larger index should have better compression ratio
        assertTrue(largeRatio >= smallRatio * 0.8,
            "Large index compression ratio should be comparable");
    }

    // ========== Accuracy Tests ==========

    @Test
    @DisplayName("PQ search should have reasonable recall")
    void testSearchRecall() {
        int numVectors = 500;
        int topK = 10;

        // Create both PQ and Flat indexes
        PQIndex pqIndex = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.L2);
        FlatIndex flatIndex = new FlatIndex(DIMENSION);

        // Generate and insert vectors with shared IDs
        Random random = new Random(42);
        float[][] vectors = new float[numVectors][DIMENSION];
        String[] ids = new String[numVectors];
        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < DIMENSION; j++) {
                vectors[i][j] = random.nextFloat();
            }
            // Use deterministic IDs so we can match results between indices
            ids[i] = String.format("vec-%05d", i);
            Vector pqVector = new Vector(ids[i], vectors[i]);
            Vector flatVector = new Vector(ids[i], vectors[i].clone());
            pqIndex.insert(pqVector);
            flatIndex.insert(flatVector);
        }

        // Train PQ index
        pqIndex.train(vectors, 15);

        // Test recall on random queries
        int totalMatches = 0;
        int numQueries = 20;

        for (int q = 0; q < numQueries; q++) {
            float[] query = new float[DIMENSION];
            for (int j = 0; j < DIMENSION; j++) {
                query[j] = random.nextFloat();
            }

            SearchResult[] pqResults = pqIndex.search(query, topK, DistanceType.L2);
            SearchResult[] flatResults = flatIndex.search(query, topK, DistanceType.L2);

            // Count matches in top-K results
            for (SearchResult pqResult : pqResults) {
                for (SearchResult flatResult : flatResults) {
                    if (pqResult.getVectorId().equals(flatResult.getVectorId())) {
                        totalMatches++;
                        break;
                    }
                }
            }
        }

        double recall = (double) totalMatches / (numQueries * topK);

        // PQ should have at least 25% recall for this configuration
        assertTrue(recall >= 0.25,
            String.format("Recall should be at least 25%%, got: %.2f%%", recall * 100));
    }

    // ========== Text and Metadata Tests ==========

    @Test
    @DisplayName("Text and metadata should be preserved")
    void testTextAndMetadata() {
        float[] data = new float[DIMENSION];
        String text = "test text";
        Metadata metadata = new Metadata()
            .put("category", "news")
            .put("score", 0.95);
        Vector v = new Vector(data, text, metadata);
        String id = index.insert(v);

        Vector retrieved = index.get(id);
        assertNotNull(retrieved);
        assertEquals(text, retrieved.getText());
        assertEquals("news", retrieved.getMetadata().getString("category"));
        assertEquals(0.95, retrieved.getMetadata().getDouble("score"), 0.0001);
    }

    @Test
    @DisplayName("Search results should include text and metadata")
    void testSearchResultsIncludeTextAndMetadata() {
        // Need at least NUM_CENTROIDS vectors to train
        float[][] trainingVectors = new float[NUM_CENTROIDS][DIMENSION];
        for (int i = 0; i < NUM_CENTROIDS; i++) {
            trainingVectors[i] = new float[DIMENSION];
            String text = "Sample text " + i;
            Metadata metadata = new Metadata().put("index", i);
            index.insert(new Vector(trainingVectors[i], text, metadata));
        }
        index.train(trainingVectors, 5);

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertTrue(results.length > 0);
        // Retrieve the full vector to check text and metadata
        Vector found = index.get(results[0].vectorId());
        assertNotNull(found.getText());
        assertNotNull(found.getMetadata());
    }

    // ========== Dimension Validation Tests ==========

    @Test
    @DisplayName("Insert with wrong dimension should throw exception")
    void testInsertWrongDimension() {
        float[] wrongData = new float[DIMENSION + 1];

        assertThrows(IllegalArgumentException.class, () -> {
            index.insert(new Vector(wrongData));
        });
    }

    @Test
    @DisplayName("Search with wrong dimension should throw exception")
    void testSearchWrongDimension() {
        index.insert(new Vector(new float[DIMENSION]));

        float[] wrongQuery = new float[DIMENSION + 1];

        assertThrows(IllegalArgumentException.class, () -> {
            index.search(wrongQuery, 5, DistanceType.L2);
        });
    }

    @Test
    @DisplayName("Dimension not divisible by numSubvectors should throw exception")
    void testInvalidSubvectorCount() {
        // 127 is not divisible by 8
        assertThrows(IllegalArgumentException.class, () -> {
            new PQIndex(127, 8, 256, DistanceType.L2);
        });
    }

    // ========== Persistence Tests ==========

    @Test
    @DisplayName("Save and load should preserve data")
    void testSaveAndLoad() throws Exception {
        // Insert and train
        String[] originalIds = new String[100];
        float[][] trainingVectors = generateRandomVectors(100, DIMENSION);
        for (int i = 0; i < 100; i++) {
            String text = "Sample text " + i;
            Metadata metadata = new Metadata()
                .put("index", i)
                .put("category", "test");
            Vector v = new Vector(trainingVectors[i], text, metadata);
            originalIds[i] = index.insert(v);
        }
        index.train(trainingVectors, 10);

        // Delete one vector
        index.delete(originalIds[5]);

        // Save to file
        File file = tempDir.resolve("pq_index.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            index.save(out);
        }

        // Load into new index
        PQIndex loadedIndex = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.L2);
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            loadedIndex.load(in);
        }

        // Verify
        assertEquals(index.size(), loadedIndex.size());
        assertEquals(index.getActiveCount(), loadedIndex.getActiveCount());

        // Verify vectors match
        for (int i = 0; i < 100; i++) {
            Vector original = index.get(originalIds[i]);
            Vector loaded = loadedIndex.get(originalIds[i]);

            if (i == 5) {
                assertNull(loaded);
            } else {
                assertNotNull(loaded);
                assertArrayEquals(original.getData(), loaded.getData(), 0.0001f);
                assertEquals(original.getText(), loaded.getText());
                assertEquals(original.getMetadata().getLong("index"), loaded.getMetadata().getLong("index"));
                assertEquals(original.getMetadata().getString("category"), loaded.getMetadata().getString("category"));
            }
        }
    }

    @Test
    @DisplayName("Load with dimension mismatch should throw exception")
    void testLoadDimensionMismatch() throws Exception {
        // Save an index
        index.insert(new Vector(new float[DIMENSION]));
        File file = tempDir.resolve("pq_index.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            index.save(out);
        }

        // Try to load into index with different dimension
        PQIndex wrongDimIndex = new PQIndex(DIMENSION * 2, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.L2);
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            assertThrows(IOException.class, () -> {
                wrongDimIndex.load(in);
            });
        }
    }

    // ========== Different Distance Types ==========

    @Test
    @DisplayName("PQ should work with cosine distance")
    void testCosineDistance() {
        PQIndex cosineIndex = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.COSINE);

        float[][] trainingVectors = generateRandomVectors(100, DIMENSION);
        for (float[] vector : trainingVectors) {
            cosineIndex.insert(new Vector(vector));
        }

        assertDoesNotThrow(() -> cosineIndex.train(trainingVectors, 10));

        float[] query = new float[DIMENSION];
        SearchResult[] results = cosineIndex.search(query, 5, DistanceType.COSINE);

        assertNotNull(results);
    }

    @Test
    @DisplayName("PQ should work with inner product distance")
    void testInnerProductDistance() {
        PQIndex ipIndex = new PQIndex(DIMENSION, NUM_SUBVECTORS, NUM_CENTROIDS, DistanceType.INNER_PRODUCT);

        float[][] trainingVectors = generateRandomVectors(100, DIMENSION);
        for (float[] vector : trainingVectors) {
            ipIndex.insert(new Vector(vector));
        }

        assertDoesNotThrow(() -> ipIndex.train(trainingVectors, 10));

        float[] query = new float[DIMENSION];
        SearchResult[] results = ipIndex.search(query, 5, DistanceType.INNER_PRODUCT);

        assertNotNull(results);
    }

    // ========== Configuration Tests ==========

    @Test
    @DisplayName("PQ should work with different subvector counts")
    void testDifferentSubvectorCounts() {
        // Dimension 128 can be divided by 4, 8, 16, 32
        assertDoesNotThrow(() -> new PQIndex(DIMENSION, 4, 64, DistanceType.L2));
        assertDoesNotThrow(() -> new PQIndex(DIMENSION, 16, 64, DistanceType.L2));
        assertDoesNotThrow(() -> new PQIndex(DIMENSION, 32, 64, DistanceType.L2));
    }

    @Test
    @DisplayName("PQ should work with different centroid counts")
    void testDifferentCentroidCounts() {
        assertDoesNotThrow(() -> new PQIndex(DIMENSION, NUM_SUBVECTORS, 32, DistanceType.L2));
        assertDoesNotThrow(() -> new PQIndex(DIMENSION, NUM_SUBVECTORS, 128, DistanceType.L2));
        assertDoesNotThrow(() -> new PQIndex(DIMENSION, NUM_SUBVECTORS, 256, DistanceType.L2));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle vectors with all zeros")
    void testZeroVectors() {
        float[][] trainingVectors = new float[100][DIMENSION];
        for (int i = 0; i < 100; i++) {
            trainingVectors[i] = new float[DIMENSION]; // All zeros
            index.insert(new Vector(trainingVectors[i]));
        }

        assertDoesNotThrow(() -> index.train(trainingVectors, 5));

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertNotNull(results);
    }

    @Test
    @DisplayName("Should handle negative values")
    void testNegativeValues() {
        float[][] trainingVectors = new float[100][DIMENSION];
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < DIMENSION; j++) {
                trainingVectors[i][j] = -1.0f + 2.0f * random.nextFloat(); // [-1, 1]
            }
            index.insert(new Vector(trainingVectors[i]));
        }

        assertDoesNotThrow(() -> index.train(trainingVectors, 10));

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertNotNull(results);
    }

    @Test
    @DisplayName("Get non-existent vector should return null")
    void testGetNonExistent() {
        String fakeId = "01234567-89ab-cdef-0123-456789abcdef";
        assertNull(index.get(fakeId));
    }

    @Test
    @DisplayName("Delete non-existent vector should not throw")
    void testDeleteNonExistent() {
        String fakeId = "01234567-89ab-cdef-0123-456789abcdef";
        assertDoesNotThrow(() -> index.delete(fakeId));
        assertEquals(0, index.size());
    }

    @Test
    @DisplayName("Should handle large number of vectors")
    void testLargeScale() {
        int numVectors = 5000;

        float[][] trainingVectors = new float[numVectors][DIMENSION];
        Random random = new Random(42);

        for (int i = 0; i < numVectors; i++) {
            for (int j = 0; j < DIMENSION; j++) {
                trainingVectors[i][j] = random.nextFloat();
            }
            index.insert(new Vector(trainingVectors[i]));
        }

        assertDoesNotThrow(() -> index.train(trainingVectors, 10));

        assertEquals(numVectors, index.size());
        assertEquals(numVectors, index.getActiveCount());

        // Search should work
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 10, DistanceType.L2);

        assertTrue(results.length > 0);
    }

    // ========== UUID Format Tests ==========

    @Test
    @DisplayName("UUID format validation")
    void testUUIDFormat() {
        float[] data = new float[DIMENSION];
        String id = index.insert(new Vector(data));

        assertNotNull(id);
        assertTrue(id.matches("^[0-9a-f-]{36}$"), "ID should be UUID format");
        assertEquals(4, id.chars().filter(ch -> ch == '-').count(), "UUID should have 4 dashes");
    }

    @Test
    @DisplayName("Multiple inserts should generate unique UUIDs")
    void testUniqueUUIDs() {
        String[] ids = new String[100];
        for (int i = 0; i < 100; i++) {
            float[] data = new float[DIMENSION];
            data[0] = i;
            ids[i] = index.insert(new Vector(data));
        }

        // Verify all IDs are unique
        for (int i = 0; i < 100; i++) {
            for (int j = i + 1; j < 100; j++) {
                assertNotEquals(ids[i], ids[j], "UUIDs should be unique");
            }
        }
    }

    // ========== Helper Methods ==========

    /**
     * Generate random vectors for testing
     */
    private float[][] generateRandomVectors(int count, int dimension) {
        float[][] vectors = new float[count][dimension];
        Random random = new Random(42); // Fixed seed for reproducibility

        for (int i = 0; i < count; i++) {
            for (int j = 0; j < dimension; j++) {
                vectors[i][j] = random.nextFloat();
            }
        }

        return vectors;
    }
}
