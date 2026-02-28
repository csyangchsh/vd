package com.csyangchsh.demo.vd.util;

/**
 * Utility class for vector operations
 */
public final class VectorUtil {

    private VectorUtil() {
        // Utility class
    }

    /**
     * Create a copy of the vector
     */
    public static float[] copy(float[] vector) {
        float[] copy = new float[vector.length];
        System.arraycopy(vector, 0, copy, 0, vector.length);
        return copy;
    }

    /**
     * Add two vectors
     */
    public static float[] add(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float[] result = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
        return result;
    }

    /**
     * Subtract two vectors (a - b)
     */
    public static float[] subtract(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        float[] result = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] - b[i];
        }
        return result;
    }

    /**
     * Scale vector by scalar
     */
    public static float[] scale(float[] vector, float scalar) {
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i] * scalar;
        }
        return result;
    }

    /**
     * Calculate dot product
     */
    public static float dot(float[] a, float[] b) {
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
     * Generate random vector with values in [-1, 1]
     */
    public static float[] random(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) (Math.random() * 2 - 1);
        }
        return vector;
    }

    /**
     * Generate random normalized vector
     */
    public static float[] randomNormalized(int dimension) {
        float[] vector = random(dimension);
        DistanceUtil.normalizeInPlace(vector);
        return vector;
    }

    /**
     * Check if vector is zero vector
     */
    public static boolean isZero(float[] vector) {
        for (float v : vector) {
            if (v != 0f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculate average value of vector
     */
    public static float mean(float[] vector) {
        float sum = 0f;
        for (float v : vector) {
            sum += v;
        }
        return sum / vector.length;
    }

    /**
     * Find minimum value in vector
     */
    public static float min(float[] vector) {
        float min = vector[0];
        for (int i = 1; i < vector.length; i++) {
            if (vector[i] < min) {
                min = vector[i];
            }
        }
        return min;
    }

    /**
     * Find maximum value in vector
     */
    public static float max(float[] vector) {
        float max = vector[0];
        for (int i = 1; i < vector.length; i++) {
            if (vector[i] > max) {
                max = vector[i];
            }
        }
        return max;
    }
}
