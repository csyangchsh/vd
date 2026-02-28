package com.csyangchsh.demo.vd.core;

import com.csyangchsh.demo.vd.index.FlatIndex;
import com.csyangchsh.demo.vd.index.HNSWIndex;
import com.csyangchsh.demo.vd.index.Index;
import com.csyangchsh.demo.vd.model.*;
import com.csyangchsh.demo.vd.storage.WAL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Core collection for managing vectors and index
 *
 * Supports:
 * - Text and metadata storage with vectors
 * - Metadata filtering during search
 * - WAL (Write-Ahead Logging) for durability
 *
 * Example usage:
 * <pre>
 * // Create collection
 * VectorCollection collection = VectorCollection.create("docs", 128, DistanceType.COSINE);
 *
 * // Insert with text and metadata
 * Metadata metadata = new Metadata()
 *     .put("category", "news")
 *     .put("timestamp", System.currentTimeMillis());
 * String id = collection.insert(vector, "Sample text", metadata);
 *
 * // Search with filter
 * Filter filter = Filter.and(
 *     Filter.eq("category", "news"),
 *     Filter.gte("timestamp", startTime)
 * );
 * SearchResult[] results = collection.search(query, 10, filter);
 * </pre>
 */
public class VectorCollection {

    private static final Logger logger = LoggerFactory.getLogger(VectorCollection.class);

    private final String name;
    private final int dimension;
    private final DistanceType defaultDistanceType;
    private final Index index;
    private final IndexType indexType;
    private WAL wal;  // Optional WAL for durability

    private VectorCollection(String name, int dimension, DistanceType defaultDistanceType, Index index, IndexType indexType) {
        this.name = name;
        this.dimension = dimension;
        this.defaultDistanceType = defaultDistanceType;
        this.index = index;
        this.indexType = indexType;
    }

    /**
     * Create a new collection with Flat index
     */
    public static VectorCollection create(String name, int dimension, DistanceType distanceType) {
        FlatIndex flatIndex = new FlatIndex(dimension);
        return new VectorCollection(name, dimension, distanceType, flatIndex, IndexType.FLAT);
    }

    /**
     * Create a new collection with specified index type
     */
    public static VectorCollection create(String name, int dimension, DistanceType distanceType, IndexType indexType) {
        Index index = switch (indexType) {
            case FLAT -> new FlatIndex(dimension);
            case COMPACT_FLAT -> new com.csyangchsh.demo.vd.index.CompactFlatIndex(dimension);
            case HNSW -> new HNSWIndex(dimension, 16, distanceType);
            case PQ -> new com.csyangchsh.demo.vd.index.PQIndex(dimension, 8, 256, distanceType);
        };
        return new VectorCollection(name, dimension, distanceType, index, indexType);
    }

    /**
     * Create a new collection with HNSW index and custom M parameter
     */
    public static VectorCollection createWithHNSW(String name, int dimension, DistanceType distanceType, int M) {
        HNSWIndex hnswIndex = new HNSWIndex(dimension, M, distanceType);
        return new VectorCollection(name, dimension, distanceType, hnswIndex, IndexType.HNSW);
    }

    // ========== Insert Methods ==========

    /**
     * Insert a vector
     * @return The assigned vector ID (UUID v7)
     */
    public String insert(float[] vector) {
        // Vector auto-generates UUID v7 in constructor
        Vector v = new Vector(vector);
        return insert(v, true);
    }

    /**
     * Insert a vector with text and metadata
     * @return The assigned vector ID (UUID v7)
     */
    public String insert(float[] vector, String text, Metadata metadata) {
        // Vector auto-generates UUID v7 in constructor
        Vector v = new Vector(vector, text, metadata);
        return insert(v, true);
    }

    /**
     * Insert a Vector object
     * @return The assigned vector ID (UUID v7)
     */
    public String insert(Vector vector) {
        return insert(vector, true);
    }

    /**
     * Insert a Vector object, optionally logging to WAL
     * @return The assigned vector ID (UUID v7)
     */
    public String insert(Vector vector, boolean logToWAL) {
        String id = index.insert(vector);

        // Log to WAL if enabled and requested
        if (logToWAL && wal != null) {
            try {
                wal.logInsert(id, vector.getData(), vector.isDeleted(), vector.getText(), vector.getMetadata());
                wal.asyncFlush();  // Async flush for performance
            } catch (IOException e) {
                logger.error("Failed to log insert to WAL", e);
            }
        }

        return id;
    }

    /**
     * Insert multiple vectors
     * @return Array of assigned vector IDs (UUID v7)
     */
    public String[] insert(float[][] vectors) {
        String[] ids = new String[vectors.length];
        for (int i = 0; i < vectors.length; i++) {
            ids[i] = insert(vectors[i]);
        }
        return ids;
    }

    // ========== Delete Methods ==========

    /**
     * Delete a vector by ID
     * @param vectorId UUID v7 of the vector to delete
     */
    public void delete(String vectorId) {
        delete(vectorId, true);
    }

    /**
     * Delete a vector by ID, optionally logging to WAL
     * @param vectorId The UUID v7 of the vector to delete
     * @param logToWAL Whether to log this operation to WAL
     */
    public void delete(String vectorId, boolean logToWAL) {
        // Log to WAL first if enabled and requested
        if (logToWAL && wal != null) {
            try {
                wal.logDelete(vectorId);
                wal.asyncFlush();
            } catch (IOException e) {
                logger.error("Failed to log delete to WAL", e);
            }
        }

        index.delete(vectorId);
    }

    /**
     * Delete multiple vectors
     * @param vectorIds Array of UUID v7s to delete
     */
    public void delete(String[] vectorIds) {
        for (String id : vectorIds) {
            index.delete(id);
        }
    }

    // ========== Search Methods ==========

    /**
     * Search for k nearest neighbors
     */
    public SearchResult[] search(float[] query, int k) {
        return search(query, k, defaultDistanceType);
    }

    /**
     * Search for k nearest neighbors with specific distance type
     */
    public SearchResult[] search(float[] query, int k, DistanceType distanceType) {
        return index.search(query, k, distanceType);
    }

    /**
     * Search for k nearest neighbors with metadata filter
     * @param query Query vector
     * @param k Number of results to return
     * @param filter Metadata filter (null means no filtering)
     * @return Search results matching the filter, sorted by distance
     */
    public SearchResult[] search(float[] query, int k, Filter filter) {
        return index.search(query, k, defaultDistanceType, filter);
    }

    /**
     * Search for k nearest neighbors with specific distance type and filter
     */
    public SearchResult[] search(float[] query, int k, DistanceType distanceType, Filter filter) {
        return index.search(query, k, distanceType, filter);
    }

    /**
     * Search with request configuration (supports filter)
     */
    public SearchResult[] search(SearchRequest request) {
        if (index instanceof HNSWIndex hnswIndex) {
            if (request.hasFilter()) {
                return hnswIndex.search(
                    request.getQueryVector(),
                    request.getTopK(),
                    request.getDistanceType(),
                    request.getFilter(),
                    request.getEfSearch()
                );
            }
            return hnswIndex.search(
                request.getQueryVector(),
                request.getTopK(),
                request.getDistanceType(),
                request.getEfSearch()
            );
        }
        return index.search(
            request.getQueryVector(),
            request.getTopK(),
            request.getDistanceType(),
            request.getFilter()
        );
    }

    // ========== Getters and Utilities ==========

    /**
     * Get a vector by ID
     * @param vectorId UUID v7 of the vector
     * @return Vector or null if not found
     */
    public Vector get(String vectorId) {
        return index.get(vectorId);
    }

    /**
     * Get collection name
     */
    public String getName() {
        return name;
    }

    /**
     * Get vector dimension
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * Get default distance type
     */
    public DistanceType getDefaultDistanceType() {
        return defaultDistanceType;
    }

    /**
     * Get current index type
     */
    public IndexType getIndexType() {
        return indexType;
    }

    /**
     * Get total number of vectors
     */
    public int size() {
        return index.size();
    }

    /**
     * Get number of active (non-deleted) vectors
     */
    public int getActiveCount() {
        return index.getActiveCount();
    }

    /**
     * Get all vector IDs in this collection
     * @return Iterable of all vector IDs
     */
    public Iterable<String> getAllIds() {
        return index.getAllIds();
    }

    /**
     * Get all vectors in this collection
     * @return List of all vectors
     */
    public java.util.List<Vector> getAllVectors() {
        return index.getAllVectors();
    }

    /**
     * Clear all vectors
     */
    public void clear() {
        index.clear();
    }

    /**
     * Get the underlying index
     */
    public Index getIndex() {
        return index;
    }

    // ========== WAL Methods ==========

    /**
     * Set the WAL for this collection
     */
    public void setWAL(WAL wal) {
        this.wal = wal;
    }

    /**
     * Get the WAL for this collection
     */
    public WAL getWAL() {
        return wal;
    }

    /**
     * Enable WAL with default checkpoint interval (5 minutes)
     */
    public WAL enableWAL(String basePath) throws IOException {
        return enableWAL(basePath, 5 * 60 * 1000);
    }

    /**
     * Enable WAL with custom checkpoint interval
     */
    public WAL enableWAL(String basePath, long checkpointIntervalMs) throws IOException {
        String walPath = basePath + "/" + name;
        this.wal = new WAL(walPath, checkpointIntervalMs);
        return wal;
    }

    /**
     * Disable WAL for this collection
     */
    public void disableWAL() throws IOException {
        if (wal != null) {
            wal.close();
            wal = null;
        }
    }

    /**
     * Create a checkpoint (save current state to disk and truncate WAL)
     */
    public void checkpoint(String basePath) throws IOException {
        if (wal != null) {
            // Save the index to disk
            String indexPath = basePath + "/" + name + ".idx";
            java.nio.file.Path path = java.nio.file.Paths.get(indexPath);

            // Create parent directory
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }

            // Save index
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                    new java.io.FileOutputStream(path.toFile()))) {
                index.save(out);
            }

            // Log checkpoint and truncate WAL
            long checkpointId = wal.getLastCheckpointId() + 1;
            wal.logCheckpoint(checkpointId);
            wal.sync();  // Ensure checkpoint marker is written
            wal.truncate();

            logger.info("Checkpoint created for collection '{}': checkpointId={}", name, checkpointId);
        }
    }

    /**
     * Check if WAL is enabled
     */
    public boolean isWALEnabled() {
        return wal != null;
    }

    /**
     * Check if checkpoint is needed
     */
    public boolean shouldCheckpoint() {
        return wal != null && wal.shouldCheckpoint();
    }

    /**
     * Close WAL if enabled
     */
    public void closeWAL() throws IOException {
        if (wal != null) {
            wal.close();
        }
    }
}
