package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FlatIndexTest {

    private FlatIndex index;
    private static final int DIMENSION = 128;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        index = new FlatIndex(DIMENSION);
    }

    @Test
    void testInsertAndGet() {
        float[] data = new float[DIMENSION];
        data[0] = 1.0f;
        Vector vector = new Vector(data);  // Auto-generates UUID v7

        String id = index.insert(vector);
        assertNotNull(id);
        assertTrue(id.length() > 0);  // UUID v7 string
        assertEquals(1, index.size());
        assertEquals(1, index.getActiveCount());

        Vector retrieved = index.get(id);
        assertNotNull(retrieved);
        assertEquals(id, retrieved.getId());
        assertEquals(1.0f, retrieved.getData()[0], 0.0001f);
    }

    @Test
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
    void testDelete() {
        float[] data = new float[DIMENSION];
        Vector v = new Vector(data);
        String id = index.insert(v);

        assertNotNull(index.get(id));
        index.delete(id);

        assertEquals(1, index.size());
        assertEquals(0, index.getActiveCount());
        assertNull(index.get(id));
    }

    @Test
    void testSearch() {
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

        assertEquals(3, results.length);
        // First result should be the one with first dimension = 0
        assertEquals(ids[0], results[0].getVectorId());
        assertTrue(results[0].getScore() < results[1].getScore());
        assertTrue(results[1].getScore() < results[2].getScore());
    }

    @Test
    void testSearchWithCosine() {
        float[] v1 = {1, 0, 0};
        float[] v2 = {0, 1, 0};
        float[] v3 = {0, 0, 1};

        index = new FlatIndex(3);
        String id1 = index.insert(new Vector(v1));
        String id2 = index.insert(new Vector(v2));
        String id3 = index.insert(new Vector(v3));

        // Query vector [1, 0, 0] should be closest to v1
        float[] query = {1, 0, 0};
        SearchResult[] results = index.search(query, 2, DistanceType.COSINE);

        assertEquals(2, results.length);
        assertEquals(id1, results[0].getVectorId());
        assertEquals(0, results[0].getScore(), 0.0001f); // Same vector, distance = 0
    }

    @Test
    void testSearchKGreaterThanSize() {
        index.insert(new Vector(new float[DIMENSION]));
        index.insert(new Vector(new float[DIMENSION]));

        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 10, DistanceType.L2);

        assertEquals(2, results.length);
    }

    @Test
    void testSearchEmptyIndex() {
        float[] query = new float[DIMENSION];
        SearchResult[] results = index.search(query, 5, DistanceType.L2);

        assertEquals(0, results.length);
    }

    @Test
    void testClear() {
        index.insert(new Vector(new float[DIMENSION]));
        index.insert(new Vector(new float[DIMENSION]));

        index.clear();

        assertEquals(0, index.size());
        assertEquals(0, index.getActiveCount());
    }

    @Test
    void testDimensionMismatch() {
        index.insert(new Vector(new float[DIMENSION]));

        float[] wrongDimension = new float[DIMENSION + 1];
        assertThrows(IllegalArgumentException.class, () -> {
            index.search(wrongDimension, 5, DistanceType.L2);
        });
    }

    @Test
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

        // Save to file
        File file = tempDir.resolve("test_index.bin").toFile();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            index.save(out);
        }

        // Create new index and load
        FlatIndex loadedIndex = new FlatIndex(DIMENSION);
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            loadedIndex.load(in);
        }

        // Verify
        assertEquals(index.size(), loadedIndex.size());
        assertEquals(index.getActiveCount(), loadedIndex.getActiveCount());

        for (int i = 0; i < 5; i++) {
            Vector v = loadedIndex.get(originalIds[i]);
            assertNotNull(v);
            assertEquals(originalIds[i], v.getId());
            assertEquals(i, v.getData()[0], 0.0001f);
            assertEquals("Sample text " + i, v.getText());
            assertEquals(i, v.getMetadata().getLong("index"));
            assertEquals("test", v.getMetadata().getString("category"));
        }
    }

    @Test
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
    void testGetDimension() {
        assertEquals(DIMENSION, index.getDimension());
    }
}
