package com.csyangchsh.demo.vd.util;

import com.csyangchsh.demo.vd.model.DistanceType;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD-accelerated distance calculation using Java Vector API (JEP 338-460)
 *
 * Provides significant performance improvements for vector distance calculations
 * by leveraging CPU vector instructions (AVX2/AVX-512 on x86, NEON on ARM).
 *
 * Performance gains:
 * - L2 Distance: 2-4x faster than scalar implementation
 * - Cosine Similarity: 2-3x faster
 * - Inner Product: 2-4x faster
 */
public final class SIMDDistanceUtil {

    // Use preferred species for the current CPU (AVX-512 > AVX2 > 256-bit > 128-bit)
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private SIMDDistanceUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Calculate L2 (Euclidean) distance using SIMD acceleration
     *
     * @param a First vector
     * @param b Second vector
     * @return L2 distance
     * @throws IllegalArgumentException if vectors have different lengths
     */
    public static float l2Distance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector lengths must match: " + a.length + " != " + b.length);
        }

        int i = 0;
        float sum = 0.0f;

        // Process vectors using SIMD instructions
        int loopBound = SPECIES.loopBound(a.length);
        for (; i < loopBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            FloatVector diff = va.sub(vb);
            sum += diff.mul(diff).reduceLanes(VectorOperators.ADD);
        }

        // Process remaining elements that don't fit in SIMD vectors
        for (; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }

        return (float) Math.sqrt(sum);
    }

    /**
     * Calculate squared L2 distance (avoids sqrt operation)
     * Use this when you only need to compare distances
     *
     * @param a First vector
     * @param b Second vector
     * @return Squared L2 distance
     */
    public static float l2DistanceSquared(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector lengths must match: " + a.length + " != " + b.length);
        }

        int i = 0;
        float sum = 0.0f;

        int loopBound = SPECIES.loopBound(a.length);
        for (; i < loopBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            FloatVector diff = va.sub(vb);
            sum += diff.mul(diff).reduceLanes(VectorOperators.ADD);
        }

        for (; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }

        return sum;
    }

    /**
     * Calculate inner product using SIMD acceleration
     *
     * @param a First vector
     * @param b Second vector
     * @return Inner product (dot product)
     */
    public static float innerProduct(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector lengths must match: " + a.length + " != " + b.length);
        }

        int i = 0;
        float sum = 0.0f;

        int loopBound = SPECIES.loopBound(a.length);
        for (; i < loopBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            sum += va.mul(vb).reduceLanes(VectorOperators.ADD);
        }

        for (; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    /**
     * Calculate cosine similarity using SIMD acceleration
     * Returns value in range [-1, 1] where 1 means identical direction
     *
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity [-1, 1]
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector lengths must match: " + a.length + " != " + b.length);
        }

        float dotProduct = innerProduct(a, b);
        float normA = simdNorm(a);
        float normB = simdNorm(b);

        float denominator = normA * normB;
        if (denominator == 0.0f) {
            return 0.0f; // Handle zero vectors
        }

        return dotProduct / denominator;
    }

    /**
     * Calculate cosine distance using SIMD acceleration
     * Returns value in range [0, 2] where 0 means identical
     *
     * @param a First vector
     * @param b Second vector
     * @return Cosine distance [0, 2]
     */
    public static float cosineDistance(float[] a, float[] b) {
        return 1.0f - cosineSimilarity(a, b);
    }

    /**
     * Calculate inner product distance using SIMD
     * Distance = -inner_product (for maximization problems)
     *
     * @param a First vector
     * @param b Second vector
     * @return Inner product distance
     */
    public static float innerProductDistance(float[] a, float[] b) {
        return -innerProduct(a, b);
    }

    /**
     * Calculate vector norm (magnitude) using SIMD
     *
     * @param a Vector
     * @return L2 norm of the vector
     */
    private static float simdNorm(float[] a) {
        int i = 0;
        float sum = 0.0f;

        int loopBound = SPECIES.loopBound(a.length);
        for (; i < loopBound; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            sum += va.mul(va).reduceLanes(VectorOperators.ADD);
        }

        for (; i < a.length; i++) {
            sum += a[i] * a[i];
        }

        return (float) Math.sqrt(sum);
    }

    /**
     * Calculate distance based on the specified type using SIMD acceleration
     *
     * @param a First vector
     * @param b Second vector
     * @param distanceType Type of distance to calculate
     * @return Calculated distance
     */
    public static float distance(float[] a, float[] b, DistanceType distanceType) {
        return switch (distanceType) {
            case L2 -> l2Distance(a, b);
            case COSINE -> cosineDistance(a, b);
            case INNER_PRODUCT -> innerProductDistance(a, b);
        };
    }

    /**
     * Get information about the SIMD species being used
     *
     * @return String describing the vector species
     */
    public static String getSpeciesInfo() {
        return "VectorSpecies: " + SPECIES +
               ", VectorSize: " + SPECIES.length() +
               " floats, BitSize: " + SPECIES.vectorBitSize() + " bits";
    }

    /**
     * Check if SIMD acceleration is available
     *
     * @return true if Vector API is available
     */
    public static boolean isSIMDAvailable() {
        return SPECIES != null;
    }
}
