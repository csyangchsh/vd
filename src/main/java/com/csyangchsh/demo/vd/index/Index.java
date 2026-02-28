package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.DistanceType;
import com.csyangchsh.demo.vd.model.Filter;
import com.csyangchsh.demo.vd.model.SearchResult;
import com.csyangchsh.demo.vd.model.Vector;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Interface for vector index implementations
 * Uses UUID v7 as vector identifiers
 * Supports metadata filtering during search
 */
public interface Index {

    /**
     * Insert a vector into the index
     * Vector must already have a UUID v7 ID assigned
     * @return The vector ID (UUID v7)
     */
    String insert(Vector vector);

    /**
     * Delete a vector by ID
     * @param vectorId UUID v7 of the vector to delete
     */
    void delete(String vectorId);

    /**
     * Search for k nearest neighbors
     */
    SearchResult[] search(float[] query, int k, DistanceType distanceType);

    /**
     * Search for k nearest neighbors with metadata filter
     * @param query Query vector
     * @param k Number of results to return
     * @param distanceType Distance metric
     * @param filter Metadata filter (null means no filtering)
     * @return Search results matching the filter, sorted by distance
     */
    default SearchResult[] search(float[] query, int k, DistanceType distanceType, Filter filter) {
        SearchResult[] results = search(query, k, distanceType);
        if (filter == null) {
            return results;
        }
        return filterResults(results, filter);
    }

    /**
     * Search with custom efSearch parameter (for HNSW)
     */
    default SearchResult[] search(float[] query, int k, DistanceType distanceType, int efSearch) {
        return search(query, k, distanceType);
    }

    /**
     * Search with custom efSearch and filter
     */
    default SearchResult[] search(float[] query, int k, DistanceType distanceType, Filter filter, int efSearch) {
        return search(query, k, distanceType, filter);
    }

    /**
     * Batch search - process multiple queries in parallel
     * @param queries Array of query vectors
     * @param k Number of results per query
     * @param distanceType Distance metric
     * @return Array of search results, one per query
     */
    default SearchResult[][] searchBatch(float[][] queries, int k, DistanceType distanceType) {
        SearchResult[][] results = new SearchResult[queries.length][];
        for (int i = 0; i < queries.length; i++) {
            results[i] = search(queries[i], k, distanceType);
        }
        return results;
    }

    /**
     * Batch search with filter
     */
    default SearchResult[][] searchBatch(float[][] queries, int k, DistanceType distanceType, Filter filter) {
        SearchResult[][] results = new SearchResult[queries.length][];
        for (int i = 0; i < queries.length; i++) {
            results[i] = search(queries[i], k, distanceType, filter);
        }
        return results;
    }

    /**
     * Range search - find all vectors within a radius
     * @param query Query vector
     * @param radius Maximum distance threshold
     * @param distanceType Distance metric
     * @return All vectors within the radius, sorted by distance
     */
    default SearchResult[] searchRange(float[] query, float radius, DistanceType distanceType) {
        // Default implementation using top-k with large k
        // Subclasses may override for more efficient implementation
        SearchResult[] allResults = search(query, getActiveCount(), distanceType);

        // Filter by radius
        int count = 0;
        for (SearchResult result : allResults) {
            if (result.getScore() <= radius) {
                count++;
            } else {
                break; // Results are sorted by distance
            }
        }

        SearchResult[] filteredResults = new SearchResult[count];
        System.arraycopy(allResults, 0, filteredResults, 0, count);
        return filteredResults;
    }

    /**
     * Range search with filter
     */
    default SearchResult[] searchRange(float[] query, float radius, DistanceType distanceType, Filter filter) {
        SearchResult[] results = searchRange(query, radius, distanceType);
        if (filter == null) {
            return results;
        }
        return filterResults(results, filter);
    }

    /**
     * Filter search results by metadata filter
     * Helper method for implementing filter support in subclasses
     */
    default SearchResult[] filterResults(SearchResult[] results, Filter filter) {
        if (filter == null) {
            return results;
        }

        // Count matching results
        int count = 0;
        for (SearchResult result : results) {
            Vector v = get(result.getVectorId());
            if (v != null && !v.isDeleted() && v.getMetadata() != null) {
                if (filter.matches(v.getMetadata())) {
                    count++;
                }
            } else if (filter == Filter.all()) {
                count++;
            }
        }

        // Build filtered results array
        SearchResult[] filtered = new SearchResult[count];
        int idx = 0;
        for (SearchResult result : results) {
            Vector v = get(result.getVectorId());
            if (v != null && !v.isDeleted()) {
                if (v.getMetadata() == null) {
                    // No metadata - only include if filter accepts null
                    if (filter == Filter.all()) {
                        filtered[idx++] = result;
                    }
                } else if (filter.matches(v.getMetadata())) {
                    filtered[idx++] = result;
                }
            }
        }

        return filtered;
    }

    /**
     * Get vector by ID
     * @param vectorId UUID v7 of the vector
     * @return Vector or null if not found or deleted
     */
    Vector get(String vectorId);

    /**
     * Get all vector IDs in this index
     * @return Iterable of all vector IDs
     */
    default Iterable<String> getAllIds() {
        // Default implementation for indices that don't support efficient ID iteration
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < size(); i++) {
            // This is inefficient, subclasses should override
            // For FlatIndex, we can return the keySet directly
        }
        return ids;
    }

    /**
     * Get all vectors in this index
     * @return Iterable of all vectors
     */
    default java.util.List<Vector> getAllVectors() {
        java.util.List<Vector> vectors = new java.util.ArrayList<>();
        for (String id : getAllIds()) {
            Vector v = get(id);
            if (v != null) {
                vectors.add(v);
            }
        }
        return vectors;
    }

    /**
     * Get total number of vectors (including deleted)
     */
    int size();

    /**
     * Get number of active (non-deleted) vectors
     */
    int getActiveCount();

    /**
     * Clear all vectors
     */
    void clear();

    /**
     * Save index to output stream
     */
    void save(DataOutput out) throws IOException;

    /**
     * Load index from input stream
     */
    void load(DataInput in) throws IOException;

    /**
     * Get dimension of vectors in this index
     */
    int getDimension();
}
