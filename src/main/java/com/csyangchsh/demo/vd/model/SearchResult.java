package com.csyangchsh.demo.vd.model;

/**
 * Search result containing vector id (UUID v7), score, and payload
 * Using Java 25 record with custom compareTo and getter methods
 */
public record SearchResult(String vectorId, float score, byte[] payload) implements Comparable<SearchResult> {

    // Getter methods for API consistency
    public String getVectorId() {
        return vectorId();
    }

    public float getScore() {
        return score();
    }

    public byte[] getPayload() {
        return payload();
    }

    @Override
    public int compareTo(SearchResult other) {
        return Float.compare(this.score, other.score);
    }

    @Override
    public String toString() {
        return "SearchResult{vectorId=%s, score=%.4f}".formatted(vectorId, score);
    }
}
