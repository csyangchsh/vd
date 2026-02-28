package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;
import com.csyangchsh.demo.vd.util.DistanceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * HNSW (Hierarchical Navigable Small World) Index
 * Implements approximate nearest neighbor search with high recall
 *
 * Thread-safe: Uses ReadWriteLock for concurrent access
 * - Multiple concurrent reads allowed
 * - Writes are exclusive
 *
 * Based on paper: "Efficient and robust approximate nearest neighbor search"
 * by Malkov and Yashunin (2018)
 */
public class HNSWIndex implements Index {

    private static final Logger logger = LoggerFactory.getLogger(HNSWIndex.class);

    // HNSW parameters
    private final int M;              // Max connections per node (default: 16)
    private final int maxM;           // MaxM = M (used in algorithm)
    private final int maxM0;          // MaxM0 = 2 * M (for layer 0)
    private final int ml;             // Level normalization factor
    private final double levelLambda; // For level generation

    // Index state
    private final int dimension;
    private final DistanceType distanceType;  // Distance metric for this index
    private final Map<String, Vector> vectors;
    private final List<Map<String, Node>> graphs;  // One graph per level
    private String entryPoint;
    private int maxLevel;

    // Distance cache for current search (thread-local for multi-threading support)
    private final ThreadLocal<Map<String, Float>> distanceCache = ThreadLocal.withInitial(HashMap::new);

    // Read-write lock for thread safety
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Create HNSW index with default parameters
     */
    public HNSWIndex(int dimension) {
        this(dimension, 16, DistanceType.L2);
    }

    /**
     * Create HNSW index with custom M parameter
     *
     * @param dimension Vector dimension
     * @param M         Max connections per node (recommend 16)
     */
    public HNSWIndex(int dimension, int M) {
        this(dimension, M, DistanceType.L2);
    }

    /**
     * Create HNSW index with custom M parameter and distance type
     *
     * @param dimension   Vector dimension
     * @param M           Max connections per node (recommend 16)
     * @param distanceType Distance metric to use
     */
    public HNSWIndex(int dimension, int M, DistanceType distanceType) {
        this.dimension = dimension;
        this.distanceType = distanceType;
        this.M = M;
        this.maxM = M;
        this.maxM0 = 2 * M;
        this.ml = (int) Math.log(1.0 * M);
        this.levelLambda = 1.0 / Math.log(1.0 * M);

        this.vectors = new HashMap<>();
        this.graphs = new ArrayList<>();
        this.entryPoint = null;
        this.maxLevel = -1;
    }

    @Override
    public String insert(Vector vector) {
        rwLock.writeLock().lock();
        try {
            if (vector.getDimension() != dimension) {
                throw new IllegalArgumentException(
                        "Vector dimension mismatch: expected " + dimension + ", got " + vector.getDimension());
            }

            // Vector should already have UUID v7 ID assigned
            String id = vector.getId();
            float[] data = vector.getData().clone();
            String text = vector.getText();
            Metadata metadata = vector.getMetadata() != null ? vector.getMetadata().copy() : null;
            Vector newVector = new Vector(id, data, vector.isDeleted(), text, metadata);
            vectors.put(id, newVector);

            // Determine level for this node
            int level = getRandomLevel();

            // Ensure we have enough layers
            while (graphs.size() <= level) {
                graphs.add(new HashMap<>());
            }
            if (level > maxLevel) {
                maxLevel = level;
            }

            // Search for entry point and insertion position
            String currObj = entryPoint;
            if (entryPoint == null) {
                // First node
                entryPoint = id;
            } else {
                // Search from top level down
                for (int lc = maxLevel; lc > level; lc--) {
                    currObj = searchLayer(data, currObj, 1, lc);
                }

                // Insert at each level
                for (int lc = Math.min(level, maxLevel); lc >= 0; lc--) {
                    // Search for candidates using priority queue method
                    Set<String> visited = new HashSet<>();
                    PriorityQueue<Candidate> candidateResults = searchLayerPriorityQueue(
                            data, currObj, getEfConstruction(lc), lc, visited);
                    Set<String> candidates = new HashSet<>();
                    while (!candidateResults.isEmpty()) {
                        candidates.add(candidateResults.poll().nodeId);
                    }

                    // Select best neighbors from candidates
                    Set<String> selectedNeighbors = selectNeighborsHeuristic(candidates, data, lc);

                    // Add bidirectional connections
                    for (String neighbor : selectedNeighbors) {
                        addLink(lc, id, neighbor);
                        addLink(lc, neighbor, id);
                    }

                    // Shrink connections if needed
                    Set<String> currentNeighbors = getNeighbors(lc, id);
                    int maxConnections = (lc == 0) ? maxM0 : maxM;
                    if (currentNeighbors.size() > maxConnections) {
                        Set<String> newNeighbors = selectNeighborsHeuristic(currentNeighbors, getVectorData(id), lc);
                        setNeighbors(lc, id, newNeighbors);
                    }

                    // Update current object for next level
                    if (!selectedNeighbors.isEmpty()) {
                        currObj = selectedNeighbors.iterator().next();
                    }
                }

                // Update entry point if this node is at a higher or equal level
                if (level >= maxLevel) {
                    entryPoint = id;
                }
            }

            return id;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String vectorId) {
        rwLock.writeLock().lock();
        try {
            Vector vector = vectors.get(vectorId);
            if (vector == null) {
                return;
            }

            // Mark vector as deleted
            vector.setDeleted(true);

            // Remove from all layers of the graph
            for (int level = 0; level < graphs.size(); level++) {
                Map<String, Node> graph = graphs.get(level);
                Node node = graph.get(vectorId);

                if (node != null) {
                    // Remove all connections to/from this node
                    Set<String> neighbors = new HashSet<>(node.getNeighbors());

                    // Remove this node from each neighbor's neighbor list
                    for (String neighborId : neighbors) {
                        Node neighbor = graph.get(neighborId);
                        if (neighbor != null) {
                            neighbor.getNeighbors().remove(vectorId);
                        }
                    }

                    // Remove the node from the graph
                    graph.remove(vectorId);
                }
            }

            // Update entry point if needed
            if (entryPoint.equals(vectorId)) {
                // Find a new entry point (use any remaining node)
                entryPoint = vectors.keySet().stream()
                        .filter(id -> !id.equals(vectorId))
                        .findFirst()
                        .orElse(null);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public SearchResult[] search(float[] query, int k, DistanceType distanceType) {
        return search(query, k, 50); // default efSearch
    }

    @Override
    public SearchResult[] search(float[] query, int k, DistanceType distanceType, int efSearch) {
        return search(query, k, efSearch);
    }

    /**
     * Search using the index's configured distance type
     * Thread-safe: Uses read lock to allow concurrent searches
     */
    private SearchResult[] search(float[] query, int k, int efSearch) {
        rwLock.readLock().lock();
        try {
            if (query.length != dimension) {
                throw new IllegalArgumentException(
                        "Query dimension mismatch: expected " + dimension + ", got " + query.length);
            }

            if (entryPoint == null || vectors.isEmpty()) {
                return new SearchResult[0];
            }

            // Clear distance cache
            distanceCache.get().clear();

            // Search from top level down
            String currObj = entryPoint;
            for (int level = maxLevel; level > 0; level--) {
                currObj = searchLayer(query, currObj, 1, level);
            }

            // Search at bottom level with efSearch
            Set<String> visited = new HashSet<>();
            PriorityQueue<Candidate> W = searchLayerPriorityQueue(query, currObj, efSearch, 0, visited);

            // Get top k results (only non-deleted vectors)
            List<SearchResult> validResults = new java.util.ArrayList<>();
            while (!W.isEmpty() && validResults.size() < k) {
                Candidate candidate = W.poll();
                Vector vector = vectors.get(candidate.nodeId);
                if (vector != null && !vector.isDeleted()) {
                    validResults.add(new SearchResult(
                            candidate.nodeId,
                            candidate.distance,
                            null
                    ));
                }
            }

            return validResults.toArray(new SearchResult[0]);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public Vector get(String vectorId) {
        rwLock.readLock().lock();
        try {
            Vector vector = vectors.get(vectorId);
            return (vector != null && !vector.isDeleted()) ? vector : null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        rwLock.readLock().lock();
        try {
            return vectors.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public int getActiveCount() {
        rwLock.readLock().lock();
        try {
            int count = 0;
            for (Vector vector : vectors.values()) {
                if (!vector.isDeleted()) {
                    count++;
                }
            }
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        rwLock.writeLock().lock();
        try {
            vectors.clear();
            graphs.clear();
            entryPoint = null;
            maxLevel = -1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void save(DataOutput out) throws IOException {
        rwLock.readLock().lock();
        try {
            // Write dimension
            out.writeInt(dimension);

            // Write distance type
            out.writeInt(distanceType.ordinal());

            // Write parameters
            out.writeInt(M);
            out.writeInt(maxLevel);

            // Write entry point (as UTF-8 string)
            if (entryPoint == null) {
                out.writeInt(0);
            } else {
                byte[] entryPointBytes = entryPoint.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(entryPointBytes.length);
                out.write(entryPointBytes);
            }

            // Write graph count
            out.writeInt(graphs.size());

            // Write each graph
            for (Map<String, Node> graph : graphs) {
                out.writeInt(graph.size());
                for (Map.Entry<String, Node> entry : graph.entrySet()) {
                    // Write node ID as UTF-8 string
                    byte[] nodeIdBytes = entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    out.writeInt(nodeIdBytes.length);
                    out.write(nodeIdBytes);
                    entry.getValue().save(out);
                }
            }

            // Write vectors
            out.writeInt(vectors.size());
            for (Map.Entry<String, Vector> entry : vectors.entrySet()) {
                VectorIO.saveVector(entry.getValue(), out);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void load(DataInput in) throws IOException {
        rwLock.writeLock().lock();
        try {
            // Read dimension
            int readDimension = in.readInt();
            if (readDimension != dimension) {
                throw new IOException("Dimension mismatch: expected " + dimension + ", got " + readDimension);
            }

            // Read distance type
            int distanceTypeOrdinal = in.readInt();
            DistanceType readDistanceType = DistanceType.values()[distanceTypeOrdinal];
            if (readDistanceType != this.distanceType) {
                logger.warn("DistanceType mismatch: expected {}, got {}", this.distanceType, readDistanceType);
            }

            // Read parameters
            int readM = in.readInt();
            if (readM != this.M) {
                logger.warn("M parameter mismatch: expected {}, got {}", this.M, readM);
            }

            maxLevel = in.readInt();

            // Read entry point (as UTF-8 string)
            int entryPointLength = in.readInt();
            if (entryPointLength == 0) {
                entryPoint = null;
            } else {
                byte[] entryPointBytes = new byte[entryPointLength];
                in.readFully(entryPointBytes);
                entryPoint = new String(entryPointBytes, java.nio.charset.StandardCharsets.UTF_8);
            }

            // Read graphs
            graphs.clear();
            int graphCount = in.readInt();
            for (int i = 0; i < graphCount; i++) {
                Map<String, Node> graph = new HashMap<>();
                int nodeCount = in.readInt();
                for (int j = 0; j < nodeCount; j++) {
                    // Read node ID as UTF-8 string
                    int nodeIdLength = in.readInt();
                    byte[] nodeIdBytes = new byte[nodeIdLength];
                    in.readFully(nodeIdBytes);
                    String nodeId = new String(nodeIdBytes, java.nio.charset.StandardCharsets.UTF_8);

                    Node node = new Node(nodeId);
                    node.load(in);
                    graph.put(nodeId, node);
                }
                graphs.add(graph);
            }

            // Read vectors
            vectors.clear();
            int vectorCount = in.readInt();
            for (int i = 0; i < vectorCount; i++) {
                Vector vector = VectorIO.loadVector(in, dimension);
                vectors.put(vector.getId(), vector);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Iterable<String> getAllIds() {
        rwLock.readLock().lock();
        try {
            return new java.util.ArrayList<>(vectors.keySet());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ========== Private helper methods ==========

    private int getRandomLevel() {
        double r = Math.random();
        return (int) (-Math.log(r) * levelLambda);
    }

    private int getEfConstruction(int level) {
        // Higher ef at higher levels for better precision during construction
        return (level == 0) ? 200 : 50;
    }

    private String searchLayer(float[] query, String entryPoint, int ef, int level) {
        if (entryPoint == null || level >= graphs.size()) {
            return entryPoint;
        }

        Set<String> visited = new HashSet<>();
        visited.add(entryPoint);

        float entryDist = getDistance(query, entryPoint);
        String nearest = entryPoint;
        float minDist = entryDist;

        // Greedy search
        boolean changed;
        do {
            changed = false;
            Set<String> neighbors = getNeighbors(level, nearest);
            if (neighbors != null) {
                for (String candidate : neighbors) {
                    if (visited.contains(candidate)) {
                        continue;
                    }
                    visited.add(candidate);

                    float dist = getDistance(query, candidate);
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = candidate;
                        changed = true;
                    }
                }
            }
        } while (changed);

        return nearest;
    }

    private PriorityQueue<Candidate> searchLayerPriorityQueue(
            float[] query, String entryPoint, int ef, int level, Set<String> visited) {
        PriorityQueue<Candidate> W = new PriorityQueue<>(); // min-heap
        PriorityQueue<Candidate> C = new PriorityQueue<>(Collections.reverseOrder()); // max-heap

        float entryDist = getDistance(query, entryPoint);
        visited.add(entryPoint);
        W.add(new Candidate(entryPoint, entryDist));
        C.add(new Candidate(entryPoint, entryDist));

        while (!C.isEmpty()) {
            Candidate curr = C.poll();

            // Check if we can stop
            if (W.size() >= ef && curr.distance > W.peek().distance) {
                break;
            }

            Set<String> neighbors = getNeighbors(level, curr.nodeId);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    if (visited.contains(neighbor)) {
                        continue;
                    }
                    visited.add(neighbor);

                    float dist = getDistance(query, neighbor);

                    if (W.size() < ef || dist < W.peek().distance) {
                        C.add(new Candidate(neighbor, dist));
                        W.add(new Candidate(neighbor, dist));

                        if (W.size() > ef) {
                            W.poll();
                        }
                    }
                }
            }
        }

        return W;
    }

    private Set<String> selectNeighborsHeuristic(Set<String> candidates, float[] vector, int level) {
        PriorityQueue<Candidate> queue = new PriorityQueue<>(candidates.size());

        for (String candidate : candidates) {
            float dist = getDistance(vector, candidate);
            queue.add(new Candidate(candidate, dist));
        }

        int maxConnections = (level == 0) ? maxM0 : maxM;
        Set<String> selected = new HashSet<>();

        while (!queue.isEmpty() && selected.size() < maxConnections) {
            selected.add(queue.poll().nodeId);
        }

        return selected;
    }

    private void addLink(int level, String src, String dst) {
        if (level >= graphs.size()) {
            return;
        }

        Map<String, Node> graph = graphs.get(level);
        Node srcNode = graph.computeIfAbsent(src, k -> new Node(src));
        srcNode.addNeighbor(dst);
    }

    private Set<String> getNeighbors(int level, String nodeId) {
        if (level >= graphs.size()) {
            return Collections.emptySet();
        }

        Map<String, Node> graph = graphs.get(level);
        Node node = graph.get(nodeId);
        return node != null ? node.getNeighbors() : Collections.emptySet();
    }

    private void setNeighbors(int level, String nodeId, Set<String> neighbors) {
        if (level >= graphs.size()) {
            return;
        }

        Map<String, Node> graph = graphs.get(level);
        Node node = graph.get(nodeId);
        if (node != null) {
            node.setNeighbors(neighbors);
        }
    }

    private float[] getVectorData(String nodeId) {
        Vector vector = vectors.get(nodeId);
        return vector != null ? vector.getData() : null;
    }

    private float getDistance(float[] query, String nodeId) {
        return getDistanceCached(query, nodeId);
    }

    private float getDistanceCached(float[] query, String nodeId) {
        Map<String, Float> cache = distanceCache.get();
        return cache.computeIfAbsent(nodeId, id -> {
            float[] data = getVectorData(id);
            if (data == null) {
                return Float.MAX_VALUE;
            }
            // Use the configured distance type
            return DistanceUtil.distance(query, data, distanceType);
        });
    }

    // ========== Inner classes ==========

    private static class Candidate implements Comparable<Candidate> {
        final String nodeId;
        final float distance;

        Candidate(String nodeId, float distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        @Override
        public int compareTo(Candidate other) {
            return Float.compare(this.distance, other.distance);
        }
    }

    private static class Node {
        private final String id;
        private final Set<String> neighbors;

        Node(String id) {
            this.id = id;
            this.neighbors = new HashSet<>();
        }

        void addNeighbor(String neighborId) {
            neighbors.add(neighborId);
        }

        Set<String> getNeighbors() {
            return neighbors;
        }

        void setNeighbors(Set<String> newNeighbors) {
            neighbors.clear();
            neighbors.addAll(newNeighbors);
        }

        void save(DataOutput out) throws IOException {
            out.writeInt(neighbors.size());
            for (String neighbor : neighbors) {
                byte[] neighborBytes = neighbor.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.writeInt(neighborBytes.length);
                out.write(neighborBytes);
            }
        }

        void load(DataInput in) throws IOException {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int neighborLength = in.readInt();
                byte[] neighborBytes = new byte[neighborLength];
                in.readFully(neighborBytes);
                neighbors.add(new String(neighborBytes, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }
}
