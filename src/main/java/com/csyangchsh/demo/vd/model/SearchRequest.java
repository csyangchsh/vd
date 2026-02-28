package com.csyangchsh.demo.vd.model;

/**
 * Search request configuration with metadata filtering support
 * Enhanced with Java 25 features
 *
 * Example usage:
 * <pre>
 * // Simple search
 * SearchRequest request = new SearchRequest(queryVector, 10, DistanceType.L2);
 *
 * // Search with metadata filter
 * Filter filter = Filter.and(
 *     Filter.eq("category", "news"),
 *     Filter.gte("timestamp", startTime)
 * );
 * SearchRequest request = new SearchRequest(queryVector, 10, DistanceType.L2, filter);
 *
 * // Builder pattern
 * SearchRequest request = SearchRequest.builder(queryVector)
 *     .topK(10)
 *     .distanceType(DistanceType.L2)
 *     .filter(Filter.eq("category", "news"))
 *     .efSearch(100)
 *     .build();
 * </pre>
 */
public class SearchRequest {
    private final float[] queryVector;
    private final int topK;
    private final DistanceType distanceType;
    private final Filter filter;
    private int efSearch;  // HNSW specific parameter

    public SearchRequest(float[] queryVector, int topK, DistanceType distanceType) {
        this(queryVector, topK, distanceType, null);
    }

    public SearchRequest(float[] queryVector, int topK, DistanceType distanceType, Filter filter) {
        this.queryVector = queryVector;
        this.topK = topK;
        this.distanceType = distanceType;
        this.filter = filter;
        this.efSearch = 50;  // default efSearch for HNSW
    }

    public float[] getQueryVector() {
        return queryVector;
    }

    public int getTopK() {
        return topK;
    }

    public DistanceType getDistanceType() {
        return distanceType;
    }

    /**
     * Get metadata filter (null means no filtering)
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Check if this request has a metadata filter
     */
    public boolean hasFilter() {
        return filter != null;
    }

    public int getEfSearch() {
        return efSearch;
    }

    public void setEfSearch(int efSearch) {
        this.efSearch = efSearch;
    }

    /**
     * Create a builder for this search request
     */
    public static Builder builder(float[] queryVector) {
        return new Builder(queryVector);
    }

    @Override
    public String toString() {
        // Using Java 25 formatted string
        return "SearchRequest{topK=%d, distanceType=%s, filter=%s, efSearch=%d}".formatted(
            topK, distanceType, filter, efSearch);
    }

    /**
     * Builder for SearchRequest
     */
    public static class Builder {
        private final float[] queryVector;
        private Integer topK;
        private DistanceType distanceType;
        private Filter filter;
        private Integer efSearch;

        private Builder(float[] queryVector) {
            this.queryVector = queryVector;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder distanceType(DistanceType distanceType) {
            this.distanceType = distanceType;
            return this;
        }

        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder efSearch(int efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        public SearchRequest build() {
            SearchRequest request = new SearchRequest(
                queryVector,
                topK != null ? topK : 10,
                distanceType != null ? distanceType : DistanceType.L2,
                filter
            );
            if (efSearch != null) {
                request.setEfSearch(efSearch);
            }
            return request;
        }
    }
}
