package com.csyangchsh.demo.vd.model;

/**
 * Index type for vector search
 */
public enum IndexType {
    /**
     * Flat index - brute force exact search
     * 100% recall, slower performance
     */
    FLAT,

    /**
     * Compact Flat index - memory-efficient exact search
     * 100% recall, 30-50% less memory, similar performance
     */
    COMPACT_FLAT,

    /**
     * HNSW index - Hierarchical Navigable Small World
     * Approximate search, high performance, high recall
     */
    HNSW,

    /**
     * PQ index - Product Quantization
     * Approximate search with 8-16x compression, good for large-scale datasets
     */
    PQ
}
