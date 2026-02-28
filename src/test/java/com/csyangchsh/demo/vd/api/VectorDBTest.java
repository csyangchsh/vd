package com.csyangchsh.demo.vd.api;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.IndexType;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchRequest;
import com.csyangchsh.demo.vd.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorDB Tests - Enhanced with Java 25 features and UUID v7
 */
class VectorDBTest {

    @Test
    void testCreate() {
        VectorDB db = VectorDB.create(128);
        assertNotNull(db);
        assertEquals(128, db.getDimension());
        assertEquals(0, db.size());
    }

    @Test
    void testInsertAndSearch() {
        VectorDB db = VectorDB.create(128);

        // Insert vectors (auto-generates UUID v7)
        String id1 = db.insert(new float[128]);
        String id2 = db.insert(new float[128]);

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);  // UUIDs should be unique
        assertEquals(2, db.size());
        assertEquals(2, db.getActiveCount());

        // Search
        SearchResult[] results = db.search(new float[128], 2);
        assertEquals(2, results.length);
    }

    @Test
    void testDelete() {
        VectorDB db = VectorDB.create(128);

        String id = db.insert(new float[128]);
        db.delete(id);

        assertEquals(1, db.size());
        assertEquals(0, db.getActiveCount());
    }

    @Test
    void testClear() {
        VectorDB db = VectorDB.create(128);

        db.insert(new float[128]);
        db.insert(new float[128]);

        db.clear();

        assertEquals(0, db.size());
        assertEquals(0, db.getActiveCount());
    }

    @Test
    void testSearchWithRequest() {
        VectorDB db = VectorDB.create(128);

        db.insert(new float[128]);

        SearchRequest request = new SearchRequest(new float[128], 5, DistanceType.L2);
        request.setEfSearch(100);

        SearchResult[] results = db.search(request);
        assertEquals(1, results.length);
    }

    @Test
    void testGetVector() {
        VectorDB db = VectorDB.create(128);

        float[] data = new float[128];
        data[0] = 42.0f;
        String text = "test text";
        Metadata metadata = new Metadata()
            .put("category", "test")
            .put("score", 0.95);

        String id = db.insert(data, text, metadata);

        var vector = db.get(id);
        assertNotNull(vector);
        assertEquals(id, vector.getId());
        assertEquals(42.0f, vector.getData()[0], 0.0001f);
        assertEquals(text, vector.getText());
        assertEquals("test", vector.getMetadata().getString("category"));
        assertEquals(0.95, vector.getMetadata().getDouble("score"), 0.0001);
    }

    @Test
    void testBatchInsert() {
        VectorDB db = VectorDB.create(128);

        float[][] vectors = new float[10][128];
        for (var i = 0; i < 10; i++) {
            vectors[i][0] = i;
        }

        String[] ids = db.insert(vectors);

        assertEquals(10, ids.length);
        assertEquals(10, db.size());

        // Verify all IDs are unique (UUID v7)
        for (var i = 0; i < 10; i++) {
            assertNotNull(ids[i]);
            for (var j = i + 1; j < 10; j++) {
                assertNotEquals(ids[i], ids[j]);
            }
        }
    }

    @Test
    void testDifferentDistanceTypes() {
        VectorDB db = VectorDB.create(3);

        float[] v1 = {1, 0, 0};
        float[] v2 = {0, 1, 0};

        String id1 = db.insert(v1);
        String id2 = db.insert(v2);

        // L2 search
        SearchResult[] l2Results = db.search(v1, 2, DistanceType.L2);
        assertEquals(id1, l2Results[0].getVectorId());
        assertEquals(0, l2Results[0].getScore(), 0.0001f); // Distance to self is 0

        // Cosine search
        SearchResult[] cosineResults = db.search(v1, 2, DistanceType.COSINE);
        assertEquals(id1, cosineResults[0].getVectorId());
        assertEquals(0, cosineResults[0].getScore(), 0.0001f); // Distance to self is 0
    }

    @Test
    void testCollections() {
        VectorDB db = VectorDB.create(128);

        var collection1 = db.getOrCreateCollection("col1", IndexType.FLAT);
        var collection2 = db.getOrCreateCollection("col2", IndexType.HNSW);

        assertEquals("col1", collection1.getName());
        assertEquals("col2", collection2.getName());

        // Get existing collection
        var retrieved = db.getCollection("col1");
        assertNotNull(retrieved);
        assertEquals("col1", retrieved.getName());
    }

    @Test
    void testSaveAndLoad(@TempDir Path tempDir) throws Exception {
        VectorDB db = VectorDB.create(64, DistanceType.L2);

        // Insert some vectors
        String[] originalIds = new String[10];
        for (var i = 0; i < 10; i++) {
            float[] vector = new float[64];
            vector[0] = i;
            originalIds[i] = db.insert(vector);
        }

        // Save
        File file = tempDir.resolve("testdb.bin").toFile();
        db.save(file.getAbsolutePath());

        // Load
        VectorDB loadedDb = VectorDB.load(file.getAbsolutePath());

        assertEquals(64, loadedDb.getDimension());
        assertEquals(DistanceType.L2, loadedDb.getDefaultDistanceType());
        assertEquals(10, loadedDb.size());

        // Verify vectors can be retrieved by original IDs
        for (var i = 0; i < 10; i++) {
            var v = loadedDb.get(originalIds[i]);
            assertNotNull(v);
            assertEquals(originalIds[i], v.getId());
            assertEquals(i, v.getData()[0], 0.0001f);
        }

        // Search in loaded DB
        float[] query = new float[64];
        SearchResult[] results = loadedDb.search(query, 5);
        assertEquals(5, results.length);
    }

    @Test
    void testCreateWithHNSW() {
        VectorDB db = VectorDB.createWithHNSW(128, DistanceType.L2, 16);
        assertNotNull(db);
        assertEquals(IndexType.HNSW, db.getDefaultCollection().getIndexType());
    }

    @Test
    void testClose() {
        VectorDB db = VectorDB.create(128);
        assertDoesNotThrow(() -> db.close());
    }

    // Java 25 Feature Test: Pattern matching with records
    @Test
    void testRecordPatternMatching() {
        String uuid = "01234567-89ab-cdef-0123-456789abcdef";
        SearchResult result = new SearchResult(uuid, 0.85f, null);

        // Using Java 25 record pattern matching
        String description = switch (result) {
            case SearchResult(String id, float score, byte[] payload) when score < 0.5f ->
                "Very similar vector %s with score %.2f".formatted(id, score);
            case SearchResult(String id, float score, byte[] payload) when score < 0.9f ->
                "Moderately similar vector %s with score %.2f".formatted(id, score);
            case SearchResult(String id, float score, byte[] payload) ->
                "Less similar vector %s with score %.2f".formatted(id, score);
        };

        assertTrue(description.contains("Moderately similar"));
        assertTrue(description.contains(uuid));
    }

    @Test
    void testFormattedStringOutput() {
        VectorDB db = VectorDB.create(128);
        db.insert(new float[128]);

        // Test Java 25 formatted string in toString
        String dbString = db.toString();
        assertTrue(dbString.contains("dimension=128"));
    }

    @Test
    void testUUIDFormat() {
        VectorDB db = VectorDB.create(128);

        String id = db.insert(new float[128]);

        // UUID v7 format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertNotNull(id);
        assertTrue(id.matches("^[0-9a-f-]{36}$"), "ID should be UUID format");
        assertEquals(4, id.chars().filter(ch -> ch == '-').count(), "UUID should have 4 dashes");
    }
}
