package com.csyangchsh.demo.vd.util;

import com.csyangchsh.demo.vd.model.DistanceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceUtilTest {

    @Test
    void testL2Distance() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        // Distance = sqrt((4-1)^2 + (5-2)^2 + (6-3)^2) = sqrt(9+9+9) = sqrt(27) ≈ 5.196
        float distance = DistanceUtil.l2Distance(a, b);
        assertEquals(5.196f, distance, 0.01f);
    }

    @Test
    void testL2DistanceIdentical() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2, 3};

        float distance = DistanceUtil.l2Distance(a, b);
        assertEquals(0, distance, 0.0001f);
    }

    @Test
    void testL2DistanceSquared() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        // Squared distance = 27
        float distance = DistanceUtil.l2DistanceSquared(a, b);
        assertEquals(27, distance, 0.0001f);
    }

    @Test
    void testCosineDistance() {
        // a = [1, 0], b = [0, 1]
        // Cosine similarity = 0, so cosine distance = 1
        float[] a = {1, 0};
        float[] b = {0, 1};

        float distance = DistanceUtil.cosineDistance(a, b);
        assertEquals(1, distance, 0.0001f);
    }

    @Test
    void testCosineDistanceIdentical() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2, 3};

        // Cosine similarity = 1, so cosine distance = 0
        float distance = DistanceUtil.cosineDistance(a, b);
        assertEquals(0, distance, 0.0001f);
    }

    @Test
    void testCosineDistanceOpposite() {
        float[] a = {1, 1};
        float[] b = {-1, -1};

        // Cosine similarity = -1, so cosine distance = 2
        float distance = DistanceUtil.cosineDistance(a, b);
        assertEquals(2, distance, 0.0001f);
    }

    @Test
    void testNorm() {
        float[] vector = {3, 4};
        float norm = DistanceUtil.norm(vector);
        assertEquals(5, norm, 0.0001f);
    }

    @Test
    void testNormalize() {
        float[] vector = {3, 4};
        float[] normalized = DistanceUtil.normalize(vector);

        // Normalized vector should have norm of 1
        assertEquals(1, DistanceUtil.norm(normalized), 0.0001f);
        assertEquals(0.6f, normalized[0], 0.0001f);
        assertEquals(0.8f, normalized[1], 0.0001f);
    }

    @Test
    void testNormalizeInPlace() {
        float[] vector = {3, 4};
        DistanceUtil.normalizeInPlace(vector);

        // Normalized vector should have norm of 1
        assertEquals(1, DistanceUtil.norm(vector), 0.0001f);
        assertEquals(0.6f, vector[0], 0.0001f);
        assertEquals(0.8f, vector[1], 0.0001f);
    }

    @Test
    void testDistanceMethod() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        float l2 = DistanceUtil.distance(a, b, DistanceType.L2);
        float cosine = DistanceUtil.distance(a, b, DistanceType.COSINE);

        assertTrue(l2 > 0);
        assertTrue(cosine >= 0 && cosine <= 2);
    }

    @Test
    void testDimensionMismatch() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5};

        assertThrows(IllegalArgumentException.class, () -> {
            DistanceUtil.l2Distance(a, b);
        });
    }

    @Test
    void testZeroVectorNorm() {
        float[] zero = {0, 0, 0};
        assertEquals(0, DistanceUtil.norm(zero), 0.0001f);
    }

    @Test
    void testNormalizeZeroVector() {
        float[] zero = {0, 0, 0};
        assertThrows(IllegalArgumentException.class, () -> {
            DistanceUtil.normalize(zero);
        });
    }

    // ========== Boundary and Edge Case Tests ==========

    @Test
    void testL2DistanceWithVerySmallValues() {
        float[] a = {Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};
        float[] b = {0, 0, 0};

        float distance = DistanceUtil.l2Distance(a, b);
        // Distance may be 0 due to floating point underflow
        assertTrue(distance >= 0, "Distance should be non-negative");
    }

    @Test
    void testL2DistanceWithVeryLargeValues() {
        float[] a = {Float.MAX_VALUE / 10, Float.MAX_VALUE / 10};
        float[] b = {0, 0};

        assertDoesNotThrow(() -> DistanceUtil.l2Distance(a, b));
    }

    @Test
    void testL2DistanceWithMaxFloatValues() {
        float[] a = {Float.MAX_VALUE, Float.MAX_VALUE};
        float[] b = {0, 0};

        assertDoesNotThrow(() -> DistanceUtil.l2Distance(a, b));
    }

    @Test
    void testL2DistanceWithMixedSigns() {
        float[] a = {-1, -2, -3};
        float[] b = {1, 2, 3};

        // Distance = sqrt((1-(-1))^2 + (2-(-2))^2 + (3-(-3))^2) = sqrt(4+16+36) = sqrt(56)
        float distance = DistanceUtil.l2Distance(a, b);
        float expected = (float) Math.sqrt(56);
        assertEquals(expected, distance, 0.01f);
    }

    @Test
    void testCosineDistanceWithSmallVector() {
        float[] a = {0.0001f, 0.0001f};
        float[] b = {0.0001f, 0.0001f};

        float distance = DistanceUtil.cosineDistance(a, b);
        assertEquals(0, distance, 0.0001f, "Identical small vectors should have distance 0");
    }

    @Test
    void testCosineDistanceWithLargeVector() {
        float[] a = {10000, 10000};
        float[] b = {10000, 10000};

        float distance = DistanceUtil.cosineDistance(a, b);
        assertEquals(0, distance, 0.0001f, "Identical large vectors should have distance 0");
    }

    @Test
    void testInnerProductWithMixedValues() {
        float[] a = {1, -2, 3};
        float[] b = {-4, 5, -6};

        // dot product = 1*(-4) + (-2)*5 + 3*(-6) = -4 -10 -18 = -32
        float result = DistanceUtil.innerProduct(a, b);
        assertEquals(-32, result, 0.0001f);
    }

    @Test
    void testInnerProductWithZeros() {
        float[] a = {1, 2, 3};
        float[] b = {0, 0, 0};

        float result = DistanceUtil.innerProduct(a, b);
        assertEquals(0, result, 0.0001f);
    }

    @Test
    void testNormWithNegativeValues() {
        float[] vector = {-3, -4};
        float norm = DistanceUtil.norm(vector);
        assertEquals(5, norm, 0.0001f, "Norm should be same for negative values");
    }

    @Test
    void testNormalizePreservesDirection() {
        float[] vector = {3, 4, 0};
        float[] normalized = DistanceUtil.normalize(vector);

        // Check that the direction is preserved by checking ratios
        assertEquals(3.0 / 5.0, normalized[0], 0.0001f);
        assertEquals(4.0 / 5.0, normalized[1], 0.0001f);
        assertEquals(0.0, normalized[2], 0.0001f);
    }

    @Test
    void testNormalizeWithSingleElement() {
        float[] vector = {5};
        float[] normalized = DistanceUtil.normalize(vector);

        assertEquals(1, normalized[0], 0.0001f);
    }

    @Test
    void testL2DistanceWithSingleElement() {
        float[] a = {5};
        float[] b = {3};

        float distance = DistanceUtil.l2Distance(a, b);
        assertEquals(2, distance, 0.0001f);
    }

    @Test
    void testCosineSimilarityWithPerpendicularVectors() {
        float[] a = {1, 0};
        float[] b = {0, 1};

        float similarity = DistanceUtil.cosineSimilarity(a, b);
        assertEquals(0, similarity, 0.0001f);
    }

    @Test
    void testCosineSimilarityWithParallelVectors() {
        float[] a = {1, 2, 3};
        float[] b = {2, 4, 6}; // b = 2*a

        float similarity = DistanceUtil.cosineSimilarity(a, b);
        assertEquals(1, similarity, 0.0001f, "Parallel vectors should have similarity 1");
    }

    @Test
    void testDistanceWithInnerProduct() {
        float[] a = {1, 2, 3};
        float[] b = {4, 5, 6};

        float distance = DistanceUtil.distance(a, b, DistanceType.INNER_PRODUCT);

        // Inner product = 1*4 + 2*5 + 3*6 = 32
        // Distance = -inner_product = -32
        float expected = -(1 * 4 + 2 * 5 + 3 * 6);
        assertEquals(expected, distance, 0.0001f);
    }

    @Test
    void testInnerProductDistanceWithIdenticalVectors() {
        float[] a = {1, 2, 3};
        float[] b = {1, 2, 3};

        float distance = DistanceUtil.distance(a, b, DistanceType.INNER_PRODUCT);

        // Inner product of identical vectors is their squared norm
        // Distance should be negative of that
        assertTrue(distance < 0, "Inner product distance should be negative for same direction");
    }

    @Test
    void testVeryLargeDimension() {
        int dim = 10000;
        float[] a = new float[dim];
        float[] b = new float[dim];

        for (int i = 0; i < dim; i++) {
            a[i] = 0.001f;
            b[i] = 0.002f;
        }

        assertDoesNotThrow(() -> DistanceUtil.l2Distance(a, b));
    }

    @Test
    void testDimensionOneVectors() {
        float[] a = {5};
        float[] b = {3};

        assertEquals(2, DistanceUtil.l2Distance(a, b), 0.0001f);
        assertEquals(4, DistanceUtil.l2DistanceSquared(a, b), 0.0001f);
    }

    @Test
    void testAllNegativeCosineSimilarity() {
        float[] a = {1, 2, 3};
        float[] b = {-1, -2, -3};

        float similarity = DistanceUtil.cosineSimilarity(a, b);
        assertEquals(-1, similarity, 0.0001f, "Opposite vectors should have similarity -1");
    }
}
