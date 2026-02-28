package com.csyangchsh.demo.vd.model;

/**
 * Distance metric type for vector similarity calculation
 */
public enum DistanceType {
    /**
     * Euclidean distance (L2)
     * Lower value means more similar
     */
    L2,

    /**
     * Cosine similarity
     * Higher value means more similar (converted to distance internally)
     */
    COSINE,

    /**
     * Inner product
     * Higher value means more similar (converted to distance internally)
     */
    INNER_PRODUCT
}
