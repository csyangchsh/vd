package com.csyangchsh.demo.vd.model;

/**
 * Vector data structure with text and metadata support
 * Uses UUID v7 as ID for distributed systems and client-side generation
 *
 * Example usage:
 * <pre>
 * // Simple vector
 * Vector v = new Vector(new float[128]);
 *
 * // Vector with text and metadata
 * Metadata metadata = new Metadata()
 *     .put("category", "news")
 *     .put("timestamp", System.currentTimeMillis())
 *     .put("score", 0.95);
 * Vector v = new Vector(new float[128], "Sample text", metadata);
 *
 * // Using builder
 * Vector v = Vector.builder(new float[128])
 *     .text("Sample text")
 *     .putMeta("category", "news")
 *     .build();
 * </pre>
 */
public class Vector {
    private final String id;  // UUID v7 string
    private final float[] data;
    private final String text;        // Original text content
    private final Metadata metadata;  // Structured metadata for filtering
    private boolean deleted;

    /**
     * Create a vector with auto-generated UUID v7
     */
    public Vector(float[] data) {
        this(data, null, null);
    }

    /**
     * Create a vector with auto-generated UUID v7, text and metadata
     */
    public Vector(float[] data, String text, Metadata metadata) {
        this.id = com.csyangchsh.demo.vd.util.UUIDv7.generate();
        this.data = data;
        this.text = text;
        this.metadata = metadata;
        this.deleted = false;
    }

    /**
     * Create a vector with specific ID (for loading from storage)
     */
    public Vector(String id, float[] data) {
        this(id, data, null, null);
    }

    /**
     * Create a vector with specific ID, text and metadata (for loading from storage)
     */
    public Vector(String id, float[] data, String text, Metadata metadata) {
        this(id, data, false, text, metadata);
    }

    /**
     * Create a vector with specific ID and deleted flag (for WAL recovery)
     */
    public Vector(String id, float[] data, boolean deleted, String text, Metadata metadata) {
        this.id = id;
        this.data = data;
        this.text = text;
        this.metadata = metadata;
        this.deleted = deleted;
    }

    public String getId() {
        return id;
    }

    public float[] getData() {
        return data;
    }

    /**
     * Get original text content
     */
    public String getText() {
        return text;
    }

    /**
     * Get metadata for filtering
     */
    public Metadata getMetadata() {
        return metadata;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public int getDimension() {
        return data.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vector)) return false;
        Vector v = (Vector) o;
        return id.equals(v.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Vector{id=%s, dimension=%d, deleted=%s, hasText=%s, hasMetadata=%s}".formatted(
            id, data.length, deleted, text != null, metadata != null);
    }

    /**
     * Builder pattern for Vector creation
     */
    public static class Builder {
        private String id;
        private float[] data;
        private String text;
        private Metadata metadata;

        public Builder(float[] data) {
            this.data = data;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder metadata(Metadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder putMeta(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new Metadata();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder putMeta(String key, Long value) {
            if (this.metadata == null) {
                this.metadata = new Metadata();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder putMeta(String key, Double value) {
            if (this.metadata == null) {
                this.metadata = new Metadata();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder putMeta(String key, Boolean value) {
            if (this.metadata == null) {
                this.metadata = new Metadata();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Vector build() {
            if (id != null) {
                return new Vector(id, data, text, metadata);
            }
            return new Vector(data, text, metadata);
        }
    }

    /**
     * Create a builder for this vector
     */
    public static Builder builder(float[] data) {
        return new Builder(data);
    }
}
