package com.csyangchsh.demo.vd;

import com.csyangchsh.demo.vd.api.VectorDB;
import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.IndexType;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.util.VectorUtil;

/**
 * Performance benchmark for VectorDB with UUID v7
 */
public class PerformanceTest {

    private static final int DIMENSION = 128;
    private static final int NUM_VECTORS = 10000;
    private static final int NUM_QUERIES = 100;
    private static final int TOP_K = 10;

    public static void main(String[] args) {
        System.out.println("=== VectorDB Performance Test (UUID v7) ===\n");

        testFlatIndex();
        System.out.println();
        testHNSWIndex();
        System.out.println();
        compareRecall();
    }

    private static void testFlatIndex() {
        System.out.println("--- Flat Index Performance ---");

        VectorDB db = VectorDB.create(DIMENSION, DistanceType.L2, IndexType.FLAT);

        // Build time
        long buildStart = System.currentTimeMillis();
        String[] ids = new String[NUM_VECTORS];
        for (int i = 0; i < NUM_VECTORS; i++) {
            float[] vector = VectorUtil.random(DIMENSION);
            ids[i] = db.insert(vector);
        }
        long buildTime = System.currentTimeMillis() - buildStart;

        System.out.printf("Build time: %d ms (%.2f vectors/s)%n",
                buildTime, NUM_VECTORS * 1000.0 / buildTime);
        System.out.printf("Inserted %d vectors with UUID v7 IDs%n", NUM_VECTORS);

        // Verify UUIDs are unique
        int uniqueIds = (int) java.util.Arrays.stream(ids).distinct().count();
        System.out.printf("UUID uniqueness: %d/%d (%.2f%%)%n", uniqueIds, NUM_VECTORS, uniqueIds * 100.0 / NUM_VECTORS);

        // Query time
        long queryStart = System.currentTimeMillis();
        for (int i = 0; i < NUM_QUERIES; i++) {
            float[] query = VectorUtil.random(DIMENSION);
            db.search(query, TOP_K);
        }
        long queryTime = System.currentTimeMillis() - queryStart;

        System.out.printf("Query time: %d ms (%.2f queries/s, %.4f ms per query)%n",
                queryTime, NUM_QUERIES * 1000.0 / queryTime, queryTime * 1.0 / NUM_QUERIES);

        System.out.printf("QPS: %.2f%n", NUM_QUERIES * 1000.0 / queryTime);
    }

    private static void testHNSWIndex() {
        System.out.println("--- HNSW Index Performance ---");

        VectorDB db = VectorDB.createWithHNSW(DIMENSION, DistanceType.L2, 16);

        // Build time
        long buildStart = System.currentTimeMillis();
        String[] ids = new String[NUM_VECTORS];
        for (int i = 0; i < NUM_VECTORS; i++) {
            float[] vector = VectorUtil.random(DIMENSION);
            ids[i] = db.insert(vector);
        }
        long buildTime = System.currentTimeMillis() - buildStart;

        System.out.printf("Build time: %d ms (%.2f vectors/s)%n",
                buildTime, NUM_VECTORS * 1000.0 / buildTime);
        System.out.printf("Inserted %d vectors with UUID v7 IDs%n", NUM_VECTORS);

        // Verify UUIDs are unique
        int uniqueIds = (int) java.util.Arrays.stream(ids).distinct().count();
        System.out.printf("UUID uniqueness: %d/%d (%.2f%%)%n", uniqueIds, NUM_VECTORS, uniqueIds * 100.0 / NUM_VECTORS);

        // Query time
        long queryStart = System.currentTimeMillis();
        for (int i = 0; i < NUM_QUERIES; i++) {
            float[] query = VectorUtil.random(DIMENSION);
            db.search(query, TOP_K);
        }
        long queryTime = System.currentTimeMillis() - queryStart;

        System.out.printf("Query time: %d ms (%.2f queries/s, %.4f ms per query)%n",
                queryTime, NUM_QUERIES * 1000.0 / queryTime, queryTime * 1.0 / NUM_QUERIES);

        System.out.printf("QPS: %.2f%n", NUM_QUERIES * 1000.0 / queryTime);
    }

    private static void compareRecall() {
        System.out.println("--- Recall Comparison (HNSW vs Flat) ---");

        // Create both indexes with same data
        VectorDB flatDb = VectorDB.create(DIMENSION, DistanceType.L2, IndexType.FLAT);
        VectorDB hnswDb = VectorDB.createWithHNSW(DIMENSION, DistanceType.L2, 16);

        // Generate random vectors
        float[][] vectors = new float[NUM_VECTORS][DIMENSION];
        String[] flatIds = new String[NUM_VECTORS];
        String[] hnswIds = new String[NUM_VECTORS];
        for (int i = 0; i < NUM_VECTORS; i++) {
            vectors[i] = VectorUtil.random(DIMENSION);
            flatIds[i] = flatDb.insert(vectors[i]);
            hnswIds[i] = hnswDb.insert(vectors[i]);
        }

        System.out.printf("Inserted %d vectors into both indexes with UUID v7 IDs%n", NUM_VECTORS);

        // Test recall
        int totalMatches = 0;
        int totalResults = NUM_QUERIES * TOP_K;

        for (int i = 0; i < NUM_QUERIES; i++) {
            float[] query = VectorUtil.random(DIMENSION);

            SearchResult[] flatResults = flatDb.search(query, TOP_K);
            SearchResult[] hnswResults = hnswDb.search(query, TOP_K);

            // Count how many results match (by UUID)
            for (SearchResult hnswResult : hnswResults) {
                for (SearchResult flatResult : flatResults) {
                    if (hnswResult.getVectorId().equals(flatResult.getVectorId())) {
                        totalMatches++;
                        break;
                    }
                }
            }
        }

        double recall = totalMatches * 100.0 / totalResults;
        System.out.printf("Recall: %.2f%%%n", recall);
        System.out.printf("Total matches: %d / %d%n", totalMatches, totalResults);
    }
}
