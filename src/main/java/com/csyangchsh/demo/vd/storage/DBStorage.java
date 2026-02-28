package com.csyangchsh.demo.vd.storage;

import com.csyangchsh.demo.vd.api.VectorDB;
import com.csyangchsh.demo.vd.core.VectorCollection;
import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.IndexType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Storage layer for saving and loading VectorDB
 */
public class DBStorage {

    private static final Logger logger = LoggerFactory.getLogger(DBStorage.class);

    // File format constants
    private static final String MAGIC_NUMBER = "VDB1";
    private static final int CURRENT_VERSION = 1;

    /**
     * Save VectorDB to file
     */
    public static void save(VectorDB db, String path) throws IOException {
        Path filePath = Paths.get(path);

        // Create parent directories if needed
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path)))) {
            save(db, out);
            logger.debug("Saved VectorDB to {}", path);
        }
    }

    /**
     * Save VectorDB to DataOutput
     */
    public static void save(VectorDB db, DataOutput out) throws IOException {
        // Write magic number
        out.writeBytes(MAGIC_NUMBER);

        // Write version
        out.writeInt(CURRENT_VERSION);

        // Write dimension
        out.writeInt(db.getDimension());

        // Write default distance type
        out.writeInt(db.getDefaultDistanceType().ordinal());

        // Write default index type (for new collections)
        // Get the index type from the default collection
        IndexType defaultIndexType = db.getDefaultCollection().getIndexType();
        out.writeInt(defaultIndexType.ordinal());

        // Write collection count
        Map<String, VectorCollection> collections = db.getCollections();
        out.writeInt(collections.size());

        // Write each collection
        for (Map.Entry<String, VectorCollection> entry : collections.entrySet()) {
            saveCollection(entry.getValue(), out);
        }
    }

    /**
     * Load VectorDB from file and recover from WAL
     */
    public static VectorDB load(String path) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(path)))) {
            VectorDB db = load(in);
            logger.debug("Loaded VectorDB from {}", path);

            // Recover from WAL
            recoverFromWAL(db, path);
            logger.info("WAL recovery completed for {}", path);

            return db;
        }
    }

    /**
     * Recover from WAL for all collections
     */
    private static void recoverFromWAL(VectorDB db, String basePath) throws IOException {
        String baseDir = basePath.contains(".")
            ? basePath.substring(0, basePath.lastIndexOf('.'))
            : basePath;

        for (VectorCollection collection : db.getCollections().values()) {
            String collectionWalPath = baseDir + "/" + collection.getName();

            // Check if WAL exists
            java.nio.file.Path walFilePath = java.nio.file.Paths.get(collectionWalPath + ".wal");
            if (!java.nio.file.Files.exists(walFilePath)) {
                logger.debug("No WAL found for collection '{}'", collection.getName());
                continue;
            }

            // Recover from WAL
            logger.info("Recovering collection '{}' from WAL", collection.getName());
            WAL.WALRecovery recovery = WAL.recover(collectionWalPath);

            if (recovery.entries().isEmpty()) {
                logger.info("No uncheckpointed entries in WAL for collection '{}'", collection.getName());
            } else {
                logger.info("Replaying {} WAL entries for collection '{}'", recovery.entries().size(), collection.getName());
                replayWALEntries(collection, recovery.entries());
            }

            // Enable WAL for the collection
            collection.enableWAL(baseDir);
        }
    }

    /**
     * Replay WAL entries to a collection
     * Creates Vector objects with specific IDs, text, and metadata from WAL
     */
    private static void replayWALEntries(VectorCollection collection, java.util.List<WAL.WALEntry> entries) {
        int appliedCount = 0;
        int deleteCount = 0;

        for (WAL.WALEntry entry : entries) {
            try {
                switch (entry.type()) {
                    case INSERT -> {
                        // Create Vector with the exact ID, deleted flag, text, and metadata from WAL
                        com.csyangchsh.demo.vd.model.Vector v =
                            new com.csyangchsh.demo.vd.model.Vector(
                                entry.id(),
                                entry.vector(),
                                entry.deleted(),
                                entry.text(),
                                entry.metadata());
                        // Insert directly into index to avoid re-generating UUID
                        collection.getIndex().insert(v);
                        appliedCount++;
                    }
                    case DELETE -> {
                        // Delete by ID from WAL
                        collection.getIndex().delete(entry.id());
                        deleteCount++;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to replay WAL entry: type={}, id={}",
                    entry.type(), entry.id(), e);
            }
        }

        logger.info("WAL replay completed for collection '{}': {} inserts, {} deletes",
            collection.getName(), appliedCount, deleteCount);
    }

    /**
     * Load VectorDB from DataInput
     */
    public static VectorDB load(DataInput in) throws IOException {
        // Read and verify magic number
        byte[] magic = new byte[4];
        in.readFully(magic);
        String magicStr = new String(magic);
        if (!MAGIC_NUMBER.equals(magicStr)) {
            throw new IOException("Invalid file format. Expected magic number: " + MAGIC_NUMBER +
                    ", got: " + magicStr);
        }

        // Read version
        int version = in.readInt();
        if (version != CURRENT_VERSION) {
            throw new IOException("Unsupported database version: " + version);
        }

        // Read dimension
        int dimension = in.readInt();

        // Read default distance type
        int distanceTypeOrdinal = in.readInt();
        DistanceType distanceType = DistanceType.values()[distanceTypeOrdinal];

        // Read default index type
        int indexTypeOrdinal = in.readInt();
        IndexType defaultIndexType = IndexType.values()[indexTypeOrdinal];

        // Create DB
        VectorDB db = VectorDB.create(dimension, distanceType, defaultIndexType);

        // Clear default collection (we'll load collections)
        db.clearAll();

        // Read collection count
        int collectionCount = in.readInt();

        // Read each collection
        for (int i = 0; i < collectionCount; i++) {
            VectorCollection collection = loadCollection(in);

            // Add collection to the internal collections map via reflection
            try {
                var collectionsField = db.getClass().getDeclaredField("collections");
                collectionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, VectorCollection> collections = (Map<String, VectorCollection>) collectionsField.get(db);
                collections.put(collection.getName(), collection);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IOException("Failed to add collection to database", e);
            }

            // Set as default if it's the default collection
            if ("default".equals(collection.getName())) {
                try {
                    var field = db.getClass().getDeclaredField("defaultCollection");
                    field.setAccessible(true);
                    field.set(db, collection);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new IOException("Failed to set default collection", e);
                }
            }
        }

        return db;
    }

    private static void saveCollection(VectorCollection collection, DataOutput out) throws IOException {
        // Write collection name
        byte[] nameBytes = collection.getName().getBytes();
        out.writeInt(nameBytes.length);
        out.write(nameBytes);

        // Write dimension
        out.writeInt(collection.getDimension());

        // Write distance type
        out.writeInt(collection.getDefaultDistanceType().ordinal());

        // Write index type
        out.writeInt(collection.getIndexType().ordinal());

        // Write index data
        collection.getIndex().save(out);
    }

    private static VectorCollection loadCollection(DataInput in) throws IOException {
        // Read collection name
        int nameLength = in.readInt();
        byte[] nameBytes = new byte[nameLength];
        in.readFully(nameBytes);
        String name = new String(nameBytes);

        // Read dimension
        int dimension = in.readInt();

        // Read distance type
        int distanceTypeOrdinal = in.readInt();
        DistanceType distanceType = DistanceType.values()[distanceTypeOrdinal];

        // Read index type
        int indexTypeOrdinal = in.readInt();
        IndexType indexType = IndexType.values()[indexTypeOrdinal];

        // Create collection with appropriate index and correct dimension
        VectorCollection collection = switch (indexType) {
            case FLAT -> VectorCollection.create(name, dimension, distanceType, IndexType.FLAT);
            case COMPACT_FLAT -> VectorCollection.create(name, dimension, distanceType, IndexType.COMPACT_FLAT);
            case HNSW -> VectorCollection.create(name, dimension, distanceType, IndexType.HNSW);
            case PQ -> VectorCollection.create(name, dimension, distanceType, IndexType.PQ);
        };

        // Load index data
        collection.getIndex().load(in);

        return collection;
    }
}
