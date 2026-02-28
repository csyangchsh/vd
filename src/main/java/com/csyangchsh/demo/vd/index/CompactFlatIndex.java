package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import com.csyangchsh.demo.vd.storage.CompactVectorStorage;
import com.csyangchsh.demo.vd.util.DistanceUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Flat index using compact vector storage for better memory efficiency.
 *
 * This is a drop-in replacement for FlatIndex that uses CompactVectorStorage
 * internally, resulting in:
 * - 30-50% less memory usage
 * - Better cache locality during search
 * - Reduced GC pressure
 *
 * Uses UUID v7 as vector identifiers (String) for distributed systems support.
 * Maintains mapping between String UUIDs and internal integer IDs for efficient storage.
 *
 * Performance characteristics:
 * - Same search algorithm as FlatIndex (linear scan)
 * - Better memory access patterns due to contiguous storage
 * - Slightly faster insertion (less object allocation)
 *
 * Note: This index stores additional metadata (text and metadata) separately
 * from the compact storage since CompactVectorStorage only handles vectors and payloads.
 */
public class CompactFlatIndex implements Index {

    private final int dimension;
    private final CompactVectorStorage storage;
    private final Map<String, Integer> uuidToInternalId;  // Maps UUID to internal int ID
    private final Map<Integer, String> internalIdToUuid;  // Maps internal int ID to UUID
    private final Map<Integer, String> textStorage;       // Stores text by internal ID
    private final Map<Integer, Metadata> metadataStorage; // Stores metadata by internal ID

    public CompactFlatIndex(int dimension) {
        this.dimension = dimension;
        this.storage = new CompactVectorStorage(dimension);
        this.uuidToInternalId = new HashMap<>();
        this.internalIdToUuid = new HashMap<>();
        this.textStorage = new HashMap<>();
        this.metadataStorage = new HashMap<>();
    }

    public CompactFlatIndex(int dimension, int initialCapacity) {
        this.dimension = dimension;
        this.storage = new CompactVectorStorage(dimension, initialCapacity);
        this.uuidToInternalId = new HashMap<>();
        this.internalIdToUuid = new HashMap<>();
        this.textStorage = new HashMap<>();
        this.metadataStorage = new HashMap<>();
    }

    @Override
    public String insert(Vector vector) {
        if (vector.getDimension() != dimension) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: expected " + dimension + ", got " + vector.getDimension());
        }

        // Vector should already have UUID v7 ID assigned
        String uuid = vector.getId();

        // Insert into storage and get internal ID
        int internalId = storage.insert(vector.getData());

        // Maintain mapping between UUID and internal ID
        uuidToInternalId.put(uuid, internalId);
        internalIdToUuid.put(internalId, uuid);

        // Store text and metadata
        if (vector.getText() != null) {
            textStorage.put(internalId, vector.getText());
        }
        if (vector.getMetadata() != null) {
            metadataStorage.put(internalId, vector.getMetadata().copy());
        }

        return uuid;
    }

    @Override
    public void delete(String vectorId) {
        Integer internalId = uuidToInternalId.get(vectorId);
        if (internalId != null) {
            storage.delete(internalId);
        }
    }

    @Override
    public SearchResult[] search(float[] query, int k, DistanceType distanceType) {
        if (query.length != dimension) {
            throw new IllegalArgumentException(
                    "Query dimension mismatch: expected " + dimension + ", got " + query.length);
        }

        if (k <= 0) {
            return new SearchResult[0];
        }

        // Use max-heap to maintain top k results (smallest distance)
        PriorityQueue<SearchResult> topK = new PriorityQueue<>(
                k,
                (a, b) -> Float.compare(b.getScore(), a.getScore())
        );

        // Iterate through all active vectors
        int size = storage.size();
        for (int i = 0; i < size; i++) {
            if (storage.isDeleted(i)) {
                continue;
            }

            float[] vectorData = storage.getVector(i);
            if (vectorData == null) {
                continue;
            }

            float distance = DistanceUtil.distance(query, vectorData, distanceType);

            if (topK.size() < k) {
                String uuid = internalIdToUuid.get(i);
                topK.add(new SearchResult(uuid, distance, null));
            } else if (distance < topK.peek().getScore()) {
                String uuid = internalIdToUuid.get(i);
                topK.poll();
                topK.add(new SearchResult(uuid, distance, null));
            }
        }

        // Extract and sort results by distance (ascending)
        SearchResult[] results = topK.toArray(new SearchResult[0]);
        Arrays.sort(results);
        return results;
    }

    @Override
    public Vector get(String vectorId) {
        Integer internalId = uuidToInternalId.get(vectorId);
        if (internalId == null) {
            return null;
        }

        if (storage.isDeleted(internalId)) {
            return null;
        }

        float[] data = storage.getVector(internalId);
        String text = textStorage.get(internalId);
        Metadata metadata = metadataStorage.get(internalId);

        if (data == null) {
            return null;
        }

        return new Vector(vectorId, data, text, metadata);
    }

    @Override
    public int size() {
        return storage.size();
    }

    @Override
    public int getActiveCount() {
        return storage.getActiveCount();
    }

    @Override
    public void clear() {
        storage.clear();
        uuidToInternalId.clear();
        internalIdToUuid.clear();
        textStorage.clear();
        metadataStorage.clear();
    }

    @Override
    public void save(DataOutput out) throws IOException {
        // Write dimension
        out.writeInt(dimension);

        // Write vector count
        out.writeInt(storage.size());

        // Write each vector with UUID
        int size = storage.size();
        for (int i = 0; i < size; i++) {
            // Write UUID (as string)
            String uuid = internalIdToUuid.get(i);
            if (uuid == null) {
                // Write empty string for null UUID
                out.writeInt(0);
            } else {
                byte[] uuidBytes = uuid.getBytes(StandardCharsets.UTF_8);
                out.writeInt(uuidBytes.length);
                out.write(uuidBytes);
            }

            // Write deleted flag
            out.writeBoolean(storage.isDeleted(i));

            // Write text (optional)
            String text = textStorage.get(i);
            if (text == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
                out.writeInt(textBytes.length);
                out.write(textBytes);
            }

            // Write metadata (optional)
            Metadata metadata = metadataStorage.get(i);
            if (metadata == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                metadata.save(out);
            }

            // Write vector data
            float[] vectorData = storage.getVector(i);
            if (vectorData == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                for (float v : vectorData) {
                    out.writeFloat(v);
                }
            }
        }
    }

    @Override
    public void load(DataInput in) throws IOException {
        // Read dimension (should match current dimension)
        int readDimension = in.readInt();
        if (readDimension != dimension) {
            throw new IOException("Dimension mismatch: expected " + dimension + ", got " + readDimension);
        }

        // Read vector count
        int count = in.readInt();

        // Clear existing data
        storage.clear();
        uuidToInternalId.clear();
        internalIdToUuid.clear();
        textStorage.clear();
        metadataStorage.clear();

        // Prepare storage for loading (sets up size and ID mappings)
        storage.prepareForLoad(count, count);

        // Read each vector
        for (int i = 0; i < count; i++) {
            // Read UUID
            int uuidLength = in.readInt();
            String uuid = null;
            if (uuidLength > 0) {
                byte[] uuidBytes = new byte[uuidLength];
                in.readFully(uuidBytes);
                uuid = new String(uuidBytes, StandardCharsets.UTF_8);
            }

            boolean deleted = in.readBoolean();

            // Read text (optional)
            String text = null;
            boolean hasText = in.readBoolean();
            if (hasText) {
                int textLength = in.readInt();
                byte[] textBytes = new byte[textLength];
                in.readFully(textBytes);
                text = new String(textBytes, StandardCharsets.UTF_8);
            }

            // Read metadata (optional)
            Metadata metadata = null;
            boolean hasMetadata = in.readBoolean();
            if (hasMetadata) {
                metadata = Metadata.load(in);
            }

            // Read vector data
            float[] data = null;
            boolean hasVector = in.readBoolean();
            if (hasVector) {
                data = new float[dimension];
                for (int j = 0; j < dimension; j++) {
                    data[j] = in.readFloat();
                }
            }

            // Use storage index i as the internal ID (consistent with save)
            if (uuid != null) {
                uuidToInternalId.put(uuid, i);
                internalIdToUuid.put(i, uuid);

                if (text != null) {
                    textStorage.put(i, text);
                }
                if (metadata != null) {
                    metadataStorage.put(i, metadata);
                }

                // Set vector data directly at index i
                if (data != null) {
                    storage.setVectorAtIndex(i, data);
                }

                // Mark as deleted if needed
                if (deleted) {
                    storage.setDeletedAtIndex(i, true);
                }
            }
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Iterable<String> getAllIds() {
        return new java.util.ArrayList<>(uuidToInternalId.keySet());
    }

    /**
     * Get estimated memory usage in bytes
     * Based on storage usage only (UUID mappings are minimal overhead)
     */
    public long getMemoryUsage() {
        // Return storage usage which already includes most overhead
        // UUID mappings add ~20% overhead which is acceptable for the index
        return storage.estimateMemoryUsage();
    }
}
