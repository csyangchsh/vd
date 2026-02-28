package com.csyangchsh.demo.vd.util;

import com.csyangchsh.demo.vd.model.DistanceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SIMD-accelerated distance calculations.
 *
 * These tests verify:
 * 1. Correctness - SIMD results match scalar implementation
 * 2. Edge cases - zero vectors, identical vectors, opposite vectors
 * 3. Boundary conditions - vectors of various sizes
 * 4. Error handling - dimension mismatches
 * 5. Performance - SIMD is faster than scalar (when performance tests enabled)
 */
@DisplayName("SIMD Distance Utility Tests")
class SIMDDistanceUtilTest {

    private static final float FLOAT_DELTA = 0.0001f;

    // ========== Correctness Tests ==========

    @Test
    @DisplayName("SIMD L2 distance should match scalar implementation")
    void testSIMDL2DistanceMatchesScalar() {
        float[] a = {1, 2, 3, 4, 5};
        float[] b = {2, 3, 4, 5, 6};

        float simdResult = SIMDDistanceUtil.l2Distance(a, b);
        float scalarResult = DistanceUtil.l2Distance(a, b);

        assertEquals(scalarResult, simdResult, FLOAT_DELTA,
            "SIMD L2 distance should match scalar implementation");
    }

    @Test
    @DisplayName("SIMD squared L2 distance should match scalar implementation")
    void testSIMDL2DistanceSquaredMatchesScalar() {
        float[] a = {1, 2, 3, 4, 5};
        float[] b = {2, 3, 4, 5, 6};

        float simdResult = SIMDDistanceUtil.l2DistanceSquared(a, b);
        float scalarResult = DistanceUtil.l2DistanceSquared(a, b);

        assertEquals(scalarResult, simdResult, FLOAT_DELTA,
            "SIMD squared L2 distance should match scalar implementation");
    }

    @Test
    @DisplayName("SIMD cosine distance should match scalar implementation")
    void testSIMDCosineDistanceMatchesScalar() {
        float[] a = {1, 2, 3, 4, 5};
        float[] b = {2, 3, 4, 5, 6};

        float simdResult = SIMDDistanceUtil.cosineDistance(a, b);
        float scalarResult = DistanceUtil.cosineDistance(a, b);

        assertEquals(scalarResult, simdResult, FLOAT_DELTA,
            "SIMD cosine distance should match scalar implementation");
    }

    @Test
    @DisplayName("SIMD inner product should match scalar calculation")
    void testSIMDInnerProduct() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        // dot product = 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32
        float result = SIMDDistanceUtil.innerProduct(a, b);

        assertEquals(32, result, FLOAT_DELTA, "Inner product should be 32");
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("L2 distance of identical vectors should be zero")
    void testL2DistanceIdenticalVectors() {
        float[] a = {1, 2, 3, 4, 5};
        float[] b = {1, 2, 3, 4, 5};

        float distance = SIMDDistanceUtil.l2Distance(a, b);

        assertEquals(0, distance, FLOAT_DELTA, "Distance of identical vectors should be 0");
    }

    @Test
    @DisplayName("L2 distance of zero vectors should be zero")
    void testL2DistanceZeroVectors() {
        float[] a = {0, 0, 0};
        float[] b = {0, 0, 0};

        float distance = SIMDDistanceUtil.l2Distance(a, b);

        assertEquals(0, distance, FLOAT_DELTA, "Distance of zero vectors should be 0");
    }

    @Test
    @DisplayName("Cosine similarity of identical vectors should be 1")
    void testCosineSimilarityIdenticalVectors() {
        float[] a = {1, 2, 3, 4, 5};
        float[] b = {1, 2, 3, 4, 5};

        float similarity = SIMDDistanceUtil.cosineSimilarity(a, b);

        assertEquals(1, similarity, FLOAT_DELTA, "Cosine similarity of identical vectors should be 1");
    }

    @Test
    @DisplayName("Cosine distance of identical vectors should be 0")
    void testCosineDistanceIdenticalVectors() {
        float[] a = {1, 2, 3, 4, 5};
        float[] b = {1, 2, 3, 4, 5};

        float distance = SIMDDistanceUtil.cosineDistance(a, b);

        assertEquals(0, distance, FLOAT_DELTA, "Cosine distance of identical vectors should be 0");
    }

    @Test
    @DisplayName("Cosine similarity of orthogonal vectors should be 0")
    void testCosineSimilarityOrthogonalVectors() {
        float[] a = {1, 0, 0};
        float[] b = {0, 1, 0};

        float similarity = SIMDDistanceUtil.cosineSimilarity(a, b);

        assertEquals(0, similarity, FLOAT_DELTA, "Cosine similarity of orthogonal vectors should be 0");
    }

    @Test
    @DisplayName("Cosine distance of orthogonal vectors should be 1")
    void testCosineDistanceOrthogonalVectors() {
        float[] a = {1, 0, 0};
        float[] b = {0, 1, 0};

        float distance = SIMDDistanceUtil.cosineDistance(a, b);

        assertEquals(1, distance, FLOAT_DELTA, "Cosine distance of orthogonal vectors should be 1");
    }

    @Test
    @DisplayName("Cosine similarity of opposite vectors should be -1")
    void testCosineSimilarityOppositeVectors() {
        float[] a = {1, 1, 1};
        float[] b = {-1, -1, -1};

        float similarity = SIMDDistanceUtil.cosineSimilarity(a, b);

        assertEquals(-1, similarity, FLOAT_DELTA, "Cosine similarity of opposite vectors should be -1");
    }

    @Test
    @DisplayName("Cosine distance of opposite vectors should be 2")
    void testCosineDistanceOppositeVectors() {
        float[] a = {1, 1, 1};
        float[] b = {-1, -1, -1};

        float distance = SIMDDistanceUtil.cosineDistance(a, b);

        assertEquals(2, distance, FLOAT_DELTA, "Cosine distance of opposite vectors should be 2");
    }

    @Test
    @DisplayName("Cosine similarity with zero vector should be 0")
    void testCosineSimilarityWithZeroVector() {
        float[] a = {1, 2, 3};
        float[] b = {0, 0, 0};

        float similarity = SIMDDistanceUtil.cosineSimilarity(a, b);

        assertEquals(0, similarity, FLOAT_DELTA, "Cosine similarity with zero vector should be 0");
    }

    @Test
    @DisplayName("Inner product with zero vector should be 0")
    void testInnerProductWithZeroVector() {
        float[] a = {1, 2, 3};
        float[] b = {0, 0, 0};

        float result = SIMDDistanceUtil.innerProduct(a, b);

        assertEquals(0, result, FLOAT_DELTA, "Inner product with zero vector should be 0");
    }

    // ========== Boundary Conditions ==========

    @Test
    @DisplayName("L2 distance should handle single-element vectors")
    void testL2DistanceSingleElement() {
        float[] a = {5};
        float[] b = {3};

        // sqrt((5-3)^2) = sqrt(4) = 2
        float distance = SIMDDistanceUtil.l2Distance(a, b);

        assertEquals(2, distance, FLOAT_DELTA);
    }

    @Test
    @DisplayName("L2 distance should handle two-element vectors")
    void testL2DistanceTwoElements() {
        float[] a = {3, 4};
        float[] b = {0, 0};

        // sqrt(3^2 + 4^2) = sqrt(9 + 16) = sqrt(25) = 5
        float distance = SIMDDistanceUtil.l2Distance(a, b);

        assertEquals(5, distance, FLOAT_DELTA);
    }

    @Test
    @DisplayName("L2 distance should handle small non-SIMD-sized vectors")
    void testL2DistanceSmallNonSIMDSize() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        // sqrt((4-1)^2 + (5-2)^2 + (6-3)^2) = sqrt(9+9+9) = sqrt(27) ≈ 5.196
        float distance = SIMDDistanceUtil.l2Distance(a, b);

        assertEquals(5.196f, distance, 0.01f);
    }

    @Test
    @DisplayName("Distance calculation should handle large vectors")
    void testLargeVectors() {
        int dimension = 1024;
        float[] a = new float[dimension];
        float[] b = new float[dimension];

        for (int i = 0; i < dimension; i++) {
            a[i] = i * 0.1f;
            b[i] = i * 0.1f + 1;
        }

        assertDoesNotThrow(() -> SIMDDistanceUtil.l2Distance(a, b),
            "Should handle large vectors without error");
    }

    @Test
    @DisplayName("Distance calculation should handle very large vectors")
    void testVeryLargeVectors() {
        int dimension = 10000;
        float[] a = new float[dimension];
        float[] b = new float[dimension];

        for (int i = 0; i < dimension; i++) {
            a[i] = 0.1f;
            b[i] = 0.2f;
        }

        assertDoesNotThrow(() -> SIMDDistanceUtil.l2Distance(a, b),
            "Should handle very large vectors without error");
    }

    // ========== Error Handling ==========

    @Test
    @DisplayName("L2 distance should throw on dimension mismatch")
    void testL2DistanceDimensionMismatch() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2};

        assertThrows(IllegalArgumentException.class, () -> SIMDDistanceUtil.l2Distance(a, b),
            "Should throw IllegalArgumentException for dimension mismatch");
    }

    @Test
    @DisplayName("Squared L2 distance should throw on dimension mismatch")
    void testL2DistanceSquaredDimensionMismatch() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2};

        assertThrows(IllegalArgumentException.class, () -> SIMDDistanceUtil.l2DistanceSquared(a, b),
            "Should throw IllegalArgumentException for dimension mismatch");
    }

    @Test
    @DisplayName("Cosine distance should throw on dimension mismatch")
    void testCosineDistanceDimensionMismatch() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2};

        assertThrows(IllegalArgumentException.class, () -> SIMDDistanceUtil.cosineDistance(a, b),
            "Should throw IllegalArgumentException for dimension mismatch");
    }

    @Test
    @DisplayName("Inner product should throw on dimension mismatch")
    void testInnerProductDimensionMismatch() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2};

        assertThrows(IllegalArgumentException.class, () -> SIMDDistanceUtil.innerProduct(a, b),
            "Should throw IllegalArgumentException for dimension mismatch");
    }

    // ========== Distance Type Switch Tests ==========

    @Test
    @DisplayName("Distance method should correctly compute L2 distance")
    void testDistanceMethodL2() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        float result = SIMDDistanceUtil.distance(a, b, DistanceType.L2);

        float expected = SIMDDistanceUtil.l2Distance(a, b);
        assertEquals(expected, result, FLOAT_DELTA);
    }

    @Test
    @DisplayName("Distance method should correctly compute cosine distance")
    void testDistanceMethodCosine() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        float result = SIMDDistanceUtil.distance(a, b, DistanceType.COSINE);

        float expected = SIMDDistanceUtil.cosineDistance(a, b);
        assertEquals(expected, result, FLOAT_DELTA);
    }

    @Test
    @DisplayName("Distance method should correctly compute inner product distance")
    void testDistanceMethodInnerProduct() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        float result = SIMDDistanceUtil.distance(a, b, DistanceType.INNER_PRODUCT);

        float expected = SIMDDistanceUtil.innerProductDistance(a, b);
        assertEquals(expected, result, FLOAT_DELTA);
    }

    // ========== SIMD Info Tests ==========

    @Test
    @DisplayName("SIMD should be available")
    void testSIMDAvailable() {
        assertTrue(SIMDDistanceUtil.isSIMDAvailable(),
            "SIMD should be available on platforms that support Java Vector API");
    }

    @Test
    @DisplayName("Species info should be non-null and valid")
    void testGetSpeciesInfo() {
        String info = SIMDDistanceUtil.getSpeciesInfo();

        assertNotNull(info, "Species info should not be null");
        assertTrue(info.contains("VectorSpecies"), "Info should contain species information");
        assertTrue(info.contains("VectorSize"), "Info should contain vector size");
        assertTrue(info.contains("BitSize"), "Info should contain bit size");
    }

    // ========== Negative/Positive Value Tests ==========

    @Test
    @DisplayName("L2 distance should handle negative values correctly")
    void testL2DistanceWithNegativeValues() {
        float[] a = {-1, -2, -3};
        float[] b = {1, 2, 3};

        // Distance = sqrt((1-(-1))^2 + (2-(-2))^2 + (3-(-3))^2) = sqrt(4+16+36) = sqrt(56)
        float distance = SIMDDistanceUtil.l2Distance(a, b);

        float expected = (float) Math.sqrt(56); // sqrt(56) ≈ 7.48
        assertEquals(expected, distance, 0.01f);
    }

    @Test
    @DisplayName("Cosine similarity should handle mixed positive/negative values")
    void testCosineSimilarityMixedValues() {
        float[] a = {-1, 2, -3};
        float[] b = {4, -5, 6};

        assertDoesNotThrow(() -> SIMDDistanceUtil.cosineSimilarity(a, b));

        float similarity = SIMDDistanceUtil.cosineSimilarity(a, b);
        assertTrue(similarity >= -1 && similarity <= 1,
            "Cosine similarity should be in range [-1, 1]");
    }

    // ========== Special Value Tests ==========

    @Test
    @DisplayName("Distance calculations should handle NaN")
    void testNaNValues() {
        float[] a = {1, Float.NaN, 3};
        float[] b = {4, 5, 6};

        // L2 distance with NaN should propagate NaN
        float result = SIMDDistanceUtil.l2Distance(a, b);
        assertTrue(Float.isNaN(result), "Result should be NaN when input contains NaN");
    }

    @Test
    @DisplayName("Distance calculations should handle Infinity")
    void testInfinityValues() {
        float[] a = {1, Float.POSITIVE_INFINITY, 3};
        float[] b = {4, 5, 6};

        float result = SIMDDistanceUtil.l2Distance(a, b);
        assertTrue(Float.isInfinite(result), "Result should be infinite when input contains infinity");
    }

    // ========== Performance Tests (optional, run with system property) ==========

    @Test
    @EnabledIfSystemProperty(named = "runPerformanceTests", matches = "true")
    @DisplayName("SIMD L2 distance should be faster than scalar")
    void testSIMDL2Performance() {
        int dimension = 1024;
        int iterations = 10000;

        float[] a = new float[dimension];
        float[] b = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            a[i] = (float) Math.random();
            b[i] = (float) Math.random();
        }

        // Warm up
        for (int i = 0; i < 1000; i++) {
            SIMDDistanceUtil.l2Distance(a, b);
            DistanceUtil.l2Distance(a, b);
        }

        // Time SIMD
        long simdStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            SIMDDistanceUtil.l2Distance(a, b);
        }
        long simdTime = System.nanoTime() - simdStart;

        // Time scalar
        long scalarStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DistanceUtil.l2Distance(a, b);
        }
        long scalarTime = System.nanoTime() - scalarStart;

        System.out.println("SIMD time: " + simdTime / 1_000_000 + "ms");
        System.out.println("Scalar time: " + scalarTime / 1_000_000 + "ms");
        System.out.println("Speedup: " + (double) scalarTime / simdTime + "x");

        assertTrue(simdTime < scalarTime,
            "SIMD should be faster than scalar implementation");
    }
}
