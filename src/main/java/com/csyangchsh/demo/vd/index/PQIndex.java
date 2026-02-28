package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import com.csyangchsh.demo.vd.util.DistanceUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Product Quantization (PQ) Index for memory-efficient vector storage.
 *
 * PQ divides vectors into sub-vectors and quantizes each sub-vector separately
 * using learned centroids. This provides 8-16x compression with minimal accuracy loss.
 *
 * Algorithm:
 * 1. Split dimension into M sub-vectors of dimension D/M
 * 2. Train K centroids for each sub-vector (typically K=256)
 * 3. Encode each vector as M centroid IDs (1 byte each)
 * 4. Use asymmetric distance computation for search
 *
 * Memory: Original size = N*D*4 bytes
 *         PQ size     = N*M*1 + K*D/M*4 bytes (centroids + codes)
 *         Compression: ~8-16x for typical values
 *
 * Trade-offs:
 * + Massive memory savings
 * + Faster distance computation (lookup table + byte codes)
 * - Training time for centroids
 * - Small accuracy loss (typically 1-3%)
 */
public class PQIndex implements Index {

    private final int dimension;
    private final int numSubvectors;       // M: number of sub-vectors
    private final int numCentroids;        // K: centroids per sub-vector (256 = 1 byte)
    private final int subvectorDim;        // D/M
    private final DistanceType distanceType;

    // Centroids: [subvector_index][centroid_index][subvector_dim]
    private float[][][] centroids;

    // Encoded vectors: stored as list for fast iteration during search
    private List<byte[]> codes;

    // Map from vector UUID to index in codes list
    private Map<String, Integer> idToIndex;

    // Original vectors (kept for exact reconstruction) - stored by UUID
    private Map<String, Vector> vectors;

    /**
     * Create PQ index with default parameters (M=8, K=256)
     */
    public PQIndex(int dimension) {
        this(dimension, 8, 256, DistanceType.L2);
    }

    /**
     * Create PQ index with custom parameters
     *
     * @param dimension      Vector dimension
     * @param numSubvectors  Number of sub-vectors (M), must divide dimension evenly
     * @param numCentroids   Number of centroids per sub-vector (K), typically 256
     * @param distanceType   Distance metric
     */
    public PQIndex(int dimension, int numSubvectors, int numCentroids, DistanceType distanceType) {
        if (dimension % numSubvectors != 0) {
            throw new IllegalArgumentException(
                    "Dimension must be divisible by numSubvectors: " + dimension + " % " + numSubvectors + " != 0");
        }

        this.dimension = dimension;
        this.numSubvectors = numSubvectors;
        this.numCentroids = numCentroids;
        this.subvectorDim = dimension / numSubvectors;
        this.distanceType = distanceType;

        this.centroids = new float[numSubvectors][numCentroids][subvectorDim];
        this.codes = new ArrayList<>();
        this.idToIndex = new HashMap<>();
        this.vectors = new HashMap<>();
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
        Metadata metadata = vector.getMetadata() != null ? vector.getMetadata().copy() : null;

        // Store the vector
        Vector newVector = new Vector(id, data, vector.isDeleted(), text, metadata);
        vectors.put(id, newVector);

        // Encode the vector and store the code
        byte[] code = encode(data);
        int codeIndex = codes.size();
        codes.add(code);
        idToIndex.put(id, codeIndex);

        return id;
    }

    @Override
    public void delete(String vectorId) {
        Vector vector = vectors.get(vectorId);
        if (vector != null) {
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

        // Build distance lookup table: [subvector_index][centroid_id] -> distance
        float[][] lookupTable = buildLookupTable(query);

        // Use max-heap to maintain top k results
        PriorityQueue<SearchResult> topK = new PriorityQueue<>(
                k,
                (a, b) -> Float.compare(b.getScore(), a.getScore())
        );

        // Compute distances using lookup table
        for (Map.Entry<String, Integer> entry : idToIndex.entrySet()) {
            String vectorId = entry.getKey();
            int codeIndex = entry.getValue();

            Vector vector = vectors.get(vectorId);
            if (vector == null || vector.isDeleted()) {
                continue;
            }

            float distance = asymmetricDistance(lookupTable, codes.get(codeIndex));

            if (topK.size() < k) {
                topK.add(new SearchResult(vectorId, distance, null));
            } else if (distance < topK.peek().getScore()) {
                topK.poll();
                topK.add(new SearchResult(vectorId, distance, null));
            }
        }

        SearchResult[] results = topK.toArray(new SearchResult[0]);
        Arrays.sort(results);
        return results;
    }

    @Override
    public Vector get(String vectorId) {
        Vector vector = vectors.get(vectorId);
        return (vector != null && !vector.isDeleted()) ? vector : null;
    }

    @Override
    public int size() {
        return vectors.size();
    }

    @Override
    public int getActiveCount() {
        int count = 0;
        for (Vector vector : vectors.values()) {
            if (!vector.isDeleted()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void clear() {
        codes.clear();
        idToIndex.clear();
        vectors.clear();
    }

    @Override
    public void save(DataOutput out) throws IOException {
        out.writeInt(dimension);
        out.writeInt(numSubvectors);
        out.writeInt(numCentroids);
        out.writeInt(distanceType.ordinal());

        // Save centroids
        for (int m = 0; m < numSubvectors; m++) {
            for (int k = 0; k < numCentroids; k++) {
                for (int d = 0; d < subvectorDim; d++) {
                    out.writeFloat(centroids[m][k][d]);
                }
            }
        }

        // Save codes
        out.writeInt(codes.size());
        for (byte[] code : codes) {
            out.write(code);
        }

        // Save vectors
        out.writeInt(vectors.size());
        for (Map.Entry<String, Vector> entry : vectors.entrySet()) {
            VectorIO.saveVector(entry.getValue(), out);
        }
    }

    @Override
    public void load(DataInput in) throws IOException {
        int readDimension = in.readInt();
        if (readDimension != dimension) {
            throw new IOException("Dimension mismatch: expected " + dimension + ", got " + readDimension);
        }

        int readNumSubvectors = in.readInt();
        int readNumCentroids = in.readInt();
        int distanceTypeOrdinal = in.readInt();

        // Load centroids
        for (int m = 0; m < numSubvectors; m++) {
            for (int k = 0; k < numCentroids; k++) {
                for (int d = 0; d < subvectorDim; d++) {
                    centroids[m][k][d] = in.readFloat();
                }
            }
        }

        // Load codes
        codes.clear();
        int codeCount = in.readInt();
        for (int i = 0; i < codeCount; i++) {
            byte[] code = new byte[numSubvectors];
            in.readFully(code);
            codes.add(code);
        }

        // Load vectors
        vectors.clear();
        idToIndex.clear();
        int vectorCount = in.readInt();
        for (int i = 0; i < vectorCount; i++) {
            Vector vector = VectorIO.loadVector(in, dimension);
            vectors.put(vector.getId(), vector);

            // Rebuild idToIndex mapping
            idToIndex.put(vector.getId(), i);
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Iterable<String> getAllIds() {
        return new java.util.ArrayList<>(vectors.keySet());
    }

    /**
     * Train centroids on a set of vectors using k-means
     *
     * @param trainingVectors Training data
     * @param iterations      Number of k-means iterations
     */
    public void train(float[][] trainingVectors, int iterations) {
        if (trainingVectors.length < numCentroids) {
            throw new IllegalArgumentException(
                    "Not enough training vectors: need at least " + numCentroids);
        }

        // Train each sub-vector independently
        for (int m = 0; m < numSubvectors; m++) {
            int offset = m * subvectorDim;
            trainSubvector(m, trainingVectors, offset, iterations);
        }

        // Re-encode all vectors with the new centroids
        reencodeAllVectors();
    }

    /**
     * Re-encode all vectors with the current centroids.
     * Called after training to update codes with the learned centroids.
     */
    private void reencodeAllVectors() {
        int index = 0;
        for (Map.Entry<String, Vector> entry : vectors.entrySet()) {
            String vectorId = entry.getKey();
            Vector vector = entry.getValue();
            byte[] code = encode(vector.getData());

            // Update the code in the list
            Integer codeIndex = idToIndex.get(vectorId);
            if (codeIndex != null && codeIndex < codes.size()) {
                codes.set(codeIndex, code);
            }
        }
    }

    /**
     * Get the compression ratio
     */
    public double getCompressionRatio() {
        long originalSize = (long) size() * dimension * 4; // 4 bytes per float
        long compressedSize = codes.size() * numSubvectors +  // codes (1 byte each)
                numSubvectors * numCentroids * subvectorDim * 4;  // centroids
        return (double) originalSize / compressedSize;
    }

    // ========== Private methods ==========

    /**
     * Train a single sub-vector's centroids using k-means
     */
    private void trainSubvector(int m, float[][] trainingVectors, int offset, int iterations) {
        Random random = new Random(42); // Fixed seed for reproducibility

        // Initialize centroids randomly from training data
        for (int k = 0; k < numCentroids; k++) {
            int idx = random.nextInt(trainingVectors.length);
            System.arraycopy(trainingVectors[idx], offset, centroids[m][k], 0, subvectorDim);
        }

        // K-means iterations
        for (int iter = 0; iter < iterations; iter++) {
            // Assign each training vector to nearest centroid
            int[] assignments = new int[trainingVectors.length];
            float[][] sums = new float[numCentroids][subvectorDim];
            int[] counts = new int[numCentroids];

            for (int i = 0; i < trainingVectors.length; i++) {
                int nearest = findNearestCentroid(m, trainingVectors[i], offset);
                assignments[i] = nearest;
                counts[nearest]++;

                // Accumulate for new centroid
                for (int d = 0; d < subvectorDim; d++) {
                    sums[nearest][d] += trainingVectors[i][offset + d];
                }
            }

            // Update centroids
            for (int k = 0; k < numCentroids; k++) {
                if (counts[k] > 0) {
                    for (int d = 0; d < subvectorDim; d++) {
                        centroids[m][k][d] = sums[k][d] / counts[k];
                    }
                }
            }
        }
    }

    /**
     * Find nearest centroid for a sub-vector
     */
    private int findNearestCentroid(int m, float[] vector, int offset) {
        int nearest = 0;
        float minDist = Float.MAX_VALUE;

        float[] subvector = new float[subvectorDim];
        System.arraycopy(vector, offset, subvector, 0, subvectorDim);

        for (int k = 0; k < numCentroids; k++) {
            float dist = DistanceUtil.distance(subvector, centroids[m][k], distanceType);
            if (dist < minDist) {
                minDist = dist;
                nearest = k;
            }
        }

        return nearest;
    }

    /**
     * Encode a vector into PQ codes
     */
    private byte[] encode(float[] vector) {
        byte[] code = new byte[numSubvectors];

        for (int m = 0; m < numSubvectors; m++) {
            int offset = m * subvectorDim;
            int centroidId = findNearestCentroid(m, vector, offset);
            code[m] = (byte) centroidId;
        }

        return code;
    }

    /**
     * Build distance lookup table for a query
     * lookupTable[m][k] = distance(query[m], centroid[m][k])
     */
    private float[][] buildLookupTable(float[] query) {
        float[][] table = new float[numSubvectors][numCentroids];

        for (int m = 0; m < numSubvectors; m++) {
            int offset = m * subvectorDim;
            float[] subquery = new float[subvectorDim];
            System.arraycopy(query, offset, subquery, 0, subvectorDim);

            for (int k = 0; k < numCentroids; k++) {
                table[m][k] = DistanceUtil.distance(subquery, centroids[m][k], distanceType);
            }
        }

        return table;
    }

    /**
     * Compute asymmetric distance using lookup table
     * distance = sum of lookupTable[m][code[m]] for all m
     */
    private float asymmetricDistance(float[][] lookupTable, byte[] code) {
        float sum = 0.0f;
        for (int m = 0; m < numSubvectors; m++) {
            int centroidId = code[m] & 0xFF; // Convert byte to unsigned
            sum += lookupTable[m][centroidId];
        }
        return sum;
    }
}
