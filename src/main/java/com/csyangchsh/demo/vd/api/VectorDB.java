package com.csyangchsh.demo.vd.api;

import com.csyangchsh.demo.vd.core.VectorCollection;
import com.csyangchsh.demo.vd.model.*;
import com.csyangchsh.demo.vd.storage.DBStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for Vector Database
 *
 * Features:
 * - UUID v7 for distributed, client-side ID generation
 * - Text and metadata storage with vectors
 * - Metadata filtering during search
 * - WAL (Write-Ahead Logging) for durability
 *
 * Usage example:
 * <pre>
 * // Create a new database
 * VectorDB db = VectorDB.create(128);
 *
 * // Enable WAL for durability
 * db.enableWAL("./data");
 *
 * // Insert vector with text and metadata
 * Metadata metadata = new Metadata()
 *     .put("category", "news")
 *     .put("timestamp", System.currentTimeMillis());
 * String id = db.insert(vector, "Sample text", metadata);
 *
 * // Search with filter
 * Filter filter = Filter.and(
 *     Filter.eq("category", "news"),
 *     Filter.gte("timestamp", startTime)
 * );
 * SearchResult[] results = db.search(query, 10, filter);
 *
 * // Create checkpoint
 * db.checkpoint("./data");
 *
 * // Save to disk
 * db.save("mydb.bin");
 *
 * // Load from disk (automatically recovers from WAL)
 * VectorDB db2 = VectorDB.load("mydb.bin");
 * </pre>
 */
public class VectorDB implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(VectorDB.class);

    private final int dimension;
    private final DistanceType defaultDistanceType;
    private final Map<String, VectorCollection> collections;
    private VectorCollection defaultCollection;
    private String walBasePath;  // Base path for WAL files

    private VectorDB(int dimension, DistanceType distanceType) {
        this.dimension = dimension;
        this.defaultDistanceType = distanceType;
        this.collections = new ConcurrentHashMap<>();
    }

    /**
     * Create a new in-memory vector database
     *
     * @param dimension Vector dimension
     * @return New VectorDB instance
     */
    public static VectorDB create(int dimension) {
        return create(dimension, DistanceType.L2);
    }

    /**
     * Create a new in-memory vector database with specified distance type
     *
     * @param dimension     Vector dimension
     * @param distanceType  Default distance metric
     * @return New VectorDB instance
     */
    public static VectorDB create(int dimension, DistanceType distanceType) {
        VectorDB db = new VectorDB(dimension, distanceType);
        db.defaultCollection = VectorCollection.create("default", dimension, distanceType);
        db.collections.put("default", db.defaultCollection);
        logger.info("Created VectorDB with dimension={}, distanceType={}", dimension, distanceType);
        return db;
    }

    /**
     * Create a new vector database with specified index type
     *
     * @param dimension     Vector dimension
     * @param distanceType  Default distance metric
     * @param indexType     Index type (FLAT or HNSW)
     * @return New VectorDB instance
     */
    public static VectorDB create(int dimension, DistanceType distanceType, IndexType indexType) {
        VectorDB db = new VectorDB(dimension, distanceType);
        db.defaultCollection = VectorCollection.create("default", dimension, distanceType, indexType);
        db.collections.put("default", db.defaultCollection);
        logger.info("Created VectorDB with dimension={}, distanceType={}, indexType={}",
                dimension, distanceType, indexType);
        return db;
    }

    /**
     * Create a new vector database with HNSW index
     *
     * @param dimension     Vector dimension
     * @param distanceType  Default distance metric
     * @param M             HNSW M parameter (max connections per node)
     * @return New VectorDB instance
     */
    public static VectorDB createWithHNSW(int dimension, DistanceType distanceType, int M) {
        VectorDB db = new VectorDB(dimension, distanceType);
        db.defaultCollection = VectorCollection.createWithHNSW("default", dimension, distanceType, M);
        db.collections.put("default", db.defaultCollection);
        logger.info("Created VectorDB with dimension={}, distanceType={}, HNSW(M={})",
                dimension, distanceType, M);
        return db;
    }

    /**
     * Load a vector database from disk
     *
     * @param path Path to the database file
     * @return Loaded VectorDB instance
     * @throws IOException If loading fails
     */
    public static VectorDB load(String path) throws IOException {
        return DBStorage.load(path);
    }

    // ========== Insert Methods ==========

    /**
     * Insert a vector into the default collection
     * @return Assigned vector ID (UUID v7)
     */
    public String insert(float[] vector) {
        return defaultCollection.insert(vector);
    }

    /**
     * Insert a vector with text and metadata
     * @return Assigned vector ID (UUID v7)
     */
    public String insert(float[] vector, String text, Metadata metadata) {
        return defaultCollection.insert(vector, text, metadata);
    }

    /**
     * Insert a Vector object
     * @return Assigned vector ID (UUID v7)
     */
    public String insert(Vector vector) {
        return defaultCollection.insert(vector);
    }

    /**
     * Insert multiple vectors into the default collection
     * @return Array of assigned vector IDs (UUID v7)
     */
    public String[] insert(float[][] vectors) {
        return defaultCollection.insert(vectors);
    }

    // ========== Delete Methods ==========

    /**
     * Delete a vector by ID from the default collection
     * @param vectorId UUID v7 of the vector to delete
     */
    public void delete(String vectorId) {
        defaultCollection.delete(vectorId);
    }

    /**
     * Delete multiple vectors from the default collection
     * @param vectorIds Array of UUID v7s to delete
     */
    public void delete(String[] vectorIds) {
        defaultCollection.delete(vectorIds);
    }

    // ========== Search Methods ==========

    /**
     * Search for k nearest neighbors in the default collection
     * @param query Query vector
     * @param k     Number of results to return
     * @return Array of search results
     */
    public SearchResult[] search(float[] query, int k) {
        return defaultCollection.search(query, k);
    }

    /**
     * Search for k nearest neighbors with specific distance type
     * @param query       Query vector
     * @param k           Number of results to return
     * @param distanceType Distance metric to use
     * @return Array of search results
     */
    public SearchResult[] search(float[] query, int k, DistanceType distanceType) {
        return defaultCollection.search(query, k, distanceType);
    }

    /**
     * Search for k nearest neighbors with metadata filter
     * @param query Query vector
     * @param k     Number of results to return
     * @param filter Metadata filter (null means no filtering)
     * @return Array of search results matching the filter
     */
    public SearchResult[] search(float[] query, int k, Filter filter) {
        return defaultCollection.search(query, k, filter);
    }

    /**
     * Search for k nearest neighbors with specific distance type and filter
     */
    public SearchResult[] search(float[] query, int k, DistanceType distanceType, Filter filter) {
        return defaultCollection.search(query, k, distanceType, filter);
    }

    /**
     * Search with request configuration (supports filter)
     * @param request Search request with all parameters
     * @return Array of search results
     */
    public SearchResult[] search(SearchRequest request) {
        return defaultCollection.search(request);
    }

    // ========== Getters ==========

    /**
     * Get a vector by ID from the default collection
     * @param vectorId UUID v7 of the vector
     * @return Vector or null if not found
     */
    public Vector get(String vectorId) {
        return defaultCollection.get(vectorId);
    }

    /**
     * Get vector dimension
     * @return Vector dimension
     */
    public int getDimension() {
        return dimension;
    }

    /**
     * Get default distance type
     * @return Default distance metric
     */
    public DistanceType getDefaultDistanceType() {
        return defaultDistanceType;
    }

    /**
     * Get total number of vectors in the default collection
     * @return Vector count
     */
    public int size() {
        return defaultCollection.size();
    }

    /**
     * Get number of active (non-deleted) vectors in the default collection
     * @return Active vector count
     */
    public int getActiveCount() {
        return defaultCollection.getActiveCount();
    }

    /**
     * Clear all vectors from the default collection
     */
    public void clear() {
        defaultCollection.clear();
    }

    /**
     * Clear all vectors from all collections
     */
    public void clearAll() {
        collections.values().forEach(VectorCollection::clear);
    }

    /**
     * Save database to disk
     * @param path Path to save the database file
     * @throws IOException If saving fails
     */
    public void save(String path) throws IOException {
        DBStorage.save(this, path);
        logger.info("Saved VectorDB to {}", path);
    }

    // ========== Collection Methods ==========

    /**
     * Create or get a named collection
     * @param name     Collection name
     * @param indexType Index type to use if creating new collection
     * @return Collection instance
     */
    public VectorCollection getOrCreateCollection(String name, IndexType indexType) {
        return collections.computeIfAbsent(name,
                n -> VectorCollection.create(n, dimension, defaultDistanceType, indexType));
    }

    /**
     * Get an existing collection by name
     * @param name Collection name
     * @return Collection or null if not found
     */
    public VectorCollection getCollection(String name) {
        return collections.get(name);
    }

    /**
     * Get the default collection
     * @return Default collection
     */
    public VectorCollection getDefaultCollection() {
        return defaultCollection;
    }

    /**
     * Get all collections
     * @return Map of collection name to collection
     */
    public Map<String, VectorCollection> getCollections() {
        return new ConcurrentHashMap<>(collections);
    }

    // ========== WAL Methods ==========

    /**
     * Enable WAL for all collections with default checkpoint interval (5 minutes)
     * @param basePath Base path for WAL files
     */
    public void enableWAL(String basePath) throws IOException {
        enableWAL(basePath, 5 * 60 * 1000);
    }

    /**
     * Enable WAL for all collections with custom checkpoint interval
     * @param basePath            Base path for WAL files
     * @param checkpointIntervalMs Milliseconds between checkpoints
     */
    public void enableWAL(String basePath, long checkpointIntervalMs) throws IOException {
        this.walBasePath = basePath;

        for (VectorCollection collection : collections.values()) {
            collection.enableWAL(basePath, checkpointIntervalMs);
        }

        logger.info("WAL enabled for all collections: basePath={}, interval={}ms",
            basePath, checkpointIntervalMs);
    }

    /**
     * Disable WAL for all collections
     */
    public void disableWAL() throws IOException {
        for (VectorCollection collection : collections.values()) {
            collection.disableWAL();
        }

        this.walBasePath = null;
        logger.info("WAL disabled for all collections");
    }

    /**
     * Create checkpoints for all collections using the stored WAL base path
     */
    public void checkpoint() throws IOException {
        if (walBasePath == null) {
            throw new IllegalStateException("WAL is not enabled. Call enableWAL() first.");
        }

        for (VectorCollection collection : collections.values()) {
            if (collection.isWALEnabled()) {
                collection.checkpoint(walBasePath);
            }
        }

        logger.info("Checkpoint created for all WAL-enabled collections");
    }

    /**
     * Create checkpoints for all collections
     * @param basePath Base path for checkpoint files
     */
    public void checkpoint(String basePath) throws IOException {
        for (VectorCollection collection : collections.values()) {
            if (collection.isWALEnabled()) {
                collection.checkpoint(basePath);
            }
        }

        logger.info("Checkpoint created for all WAL-enabled collections");
    }

    /**
     * Check if WAL is enabled
     * @return true if WAL is enabled for any collection
     */
    public boolean isWALEnabled() {
        return collections.values().stream().anyMatch(VectorCollection::isWALEnabled);
    }

    /**
     * Check if checkpoint is needed for any collection
     * @return true if any collection needs checkpoint
     */
    public boolean shouldCheckpoint() {
        return collections.values().stream().anyMatch(VectorCollection::shouldCheckpoint);
    }

    @Override
    public void close() {
        // Close WAL for all collections
        for (VectorCollection collection : collections.values()) {
            try {
                collection.closeWAL();
            } catch (IOException e) {
                logger.error("Failed to close WAL for collection '{}'", collection.getName(), e);
            }
        }

        logger.info("Closed VectorDB");
    }

    @Override
    public String toString() {
        // Using Java 25 formatted string
        return "VectorDB{dimension=%d, distanceType=%s, collections=%d, defaultCollection.size=%d}".formatted(
                dimension, defaultDistanceType, collections.size(), defaultCollection.size());
    }
}
