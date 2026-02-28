package com.csyangchsh.demo.vd.util;

import com.csyangchsh.demo.vd.model.DistanceType;

/**
 * Utility class for distance calculations between vectors
 */
public final class DistanceUtil {

    private DistanceUtil() {
        // Utility class
    }

    /**
     * Calculate distance between two vectors based on distance type
     * Using Java 25 enhanced switch with guards
     */
    public static float distance(float[] a, float[] b, DistanceType type) {
        return switch (type) {
            case L2 -> {
                validateDimensions(a, b);
                yield l2Distance(a, b);
            }
            case COSINE -> {
                validateDimensions(a, b);
                yield cosineDistance(a, b);
            }
            case INNER_PRODUCT -> {
                validateDimensions(a, b);
                yield innerProductDistance(a, b);
            }
        };
    }

    /**
     * Validate vector dimensions - reusable helper
     */
    private static void validateDimensions(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match: " + a.length + " vs " + b.length);
        }
    }

    /**
     * Euclidean distance (L2)
     * sqrt(sum((a[i] - b[i])^2))
     */
    public static float l2Distance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * Squared L2 distance (faster, preserves ordering)
     * sum((a[i] - b[i])^2)
     */
    public static float l2DistanceSquared(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Cosine distance
     * Converted to distance: 1 - cosine_similarity
     * Cosine similarity: dot(a, b) / (||a|| * ||b||)
     */
    public static float cosineDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        float normProduct = (float) Math.sqrt(normA * normB);
        if (normProduct == 0f) {
            // Both vectors are zero vectors
            return 0f;
        }

        float similarity = dotProduct / normProduct;
        // Convert to distance: lower is more similar
        return 1f - similarity;
    }

    /**
     * Inner product distance
     * Converted to distance: -inner_product (lower is better)
     */
    public static float innerProductDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        // Negate so lower is better (consistent with distance semantics)
        return -sum;
    }

    /**
     * Calculate L2 norm (length) of a vector
     */
    public static float norm(float[] vector) {
        return (float) Math.sqrt(normSquared(vector));
    }

    /**
     * Calculate squared L2 norm of a vector
     */
    public static float normSquared(float[] vector) {
        float sum = 0f;
        for (float v : vector) {
            sum += v * v;
        }
        return sum;
    }

    /**
     * Normalize vector to unit length
     */
    public static float[] normalize(float[] vector) {
        float norm = norm(vector);
        if (norm == 0f) {
            throw new IllegalArgumentException("Cannot normalize zero vector");
        }

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }
        return normalized;
    }

    /**
     * Normalize vector in-place
     */
    public static void normalizeInPlace(float[] vector) {
        float norm = norm(vector);
        if (norm == 0f) {
            throw new IllegalArgumentException("Cannot normalize zero vector");
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    /**
     * Calculate inner product (dot product) of two vectors
     */
    public static float innerProduct(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Calculate cosine similarity between two vectors
     * Returns value in range [-1, 1] where 1 means identical direction
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        float normProduct = (float) Math.sqrt(normA * normB);
        if (normProduct == 0f) {
            return 0f; // Handle zero vectors
        }

        return dotProduct / normProduct;
    }
}
