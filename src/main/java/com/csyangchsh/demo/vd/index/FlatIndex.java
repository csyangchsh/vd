package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import com.csyangchsh.demo.vd.util.DistanceUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Flat index - brute force exact search
 * Maintains all vectors and performs linear scan for search
 * Uses UUID v7 as vector identifiers
 */
public class FlatIndex implements Index {

    private final int dimension;
    private final List<Vector> vectors;
    private final Map<String, Integer> idToIndex;

    public FlatIndex(int dimension) {
        this.dimension = dimension;
        this.vectors = new ArrayList<>();
        this.idToIndex = new HashMap<>();
    }

    @Override
    public String insert(Vector vector) {
        if (vector.getDimension() != dimension) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: expected " + dimension + ", got " + vector.getDimension());
        }

        // Vector should already have UUID v7 ID assigned
        String id = vector.getId();

        // Clone vector data, text, and metadata
        float[] data = vector.getData().clone();
        String text = vector.getText();
        com.csyangchsh.demo.vd.model.Metadata metadata = vector.getMetadata() != null
            ? vector.getMetadata().copy()
            : null;

        Vector newVector = new Vector(id, data, vector.isDeleted(), text, metadata);
        vectors.add(newVector);
        idToIndex.put(id, vectors.size() - 1);
        return id;
    }

    @Override
    public void delete(String vectorId) {
        Integer index = idToIndex.get(vectorId);
        if (index != null) {
            Vector vector = vectors.get(index);
            vector.setDeleted(true);
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
                (a, b) -> Float.compare(b.getScore(), a.getScore()) // max-heap by distance
        );

        for (Vector vector : vectors) {
            if (vector.isDeleted()) {
                continue;
            }

            float distance = DistanceUtil.distance(query, vector.getData(), distanceType);

            if (topK.size() < k) {
                topK.add(new SearchResult(vector.getId(), distance, null));
            } else if (distance < topK.peek().getScore()) {
                topK.poll();
                topK.add(new SearchResult(vector.getId(), distance, null));
            }
        }

        // Extract and sort results by distance (ascending)
        SearchResult[] results = topK.toArray(new SearchResult[0]);
        Arrays.sort(results);
        return results;
    }

    @Override
    public Vector get(String vectorId) {
        Integer index = idToIndex.get(vectorId);
        if (index == null) {
            return null;
        }
        Vector vector = vectors.get(index);
        return vector.isDeleted() ? null : vector;
    }

    @Override
    public int size() {
        return vectors.size();
    }

    @Override
    public int getActiveCount() {
        int count = 0;
        for (Vector vector : vectors) {
            if (!vector.isDeleted()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void clear() {
        vectors.clear();
        idToIndex.clear();
    }

    @Override
    public void save(DataOutput out) throws IOException {
        // Write dimension
        out.writeInt(dimension);

        // Write vector count
        out.writeInt(vectors.size());

        // Write each vector
        for (Vector vector : vectors) {
            VectorIO.saveVector(vector, out);
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
        vectors.clear();
        idToIndex.clear();

        // Read each vector
        for (int i = 0; i < count; i++) {
            Vector vector = VectorIO.loadVector(in, dimension);
            vectors.add(vector);
            idToIndex.put(vector.getId(), i);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Iterable<String> getAllIds() {
        return new java.util.ArrayList<>(idToIndex.keySet());
    }
}
