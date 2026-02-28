package com.csyangchsh.demo.vd.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata for vectors - supports structured key-value pairs for filtering.
 *
 * Supported value types:
 * - String
 * - Long (for timestamps, integers)
 * - Double (for scores, floats)
 * - Boolean (for flags)
 *
 * Example usage:
 * <pre>
 * Metadata metadata = new Metadata()
 *     .put("category", "news")
 *     .put("timestamp", 1234567890L)
 *     .put("score", 0.95)
 *     .put("published", true);
 * </pre>
 */
public class Metadata {

    public enum ValueType {
        STRING(0),
        LONG(1),
        DOUBLE(2),
        BOOLEAN(3),
        NULL(4);

        private final int code;
        ValueType(int code) { this.code = code; }
        public int code() { return code; }

        public static ValueType fromCode(int code) {
            return values()[code];
        }
    }

    public static class Value {
        private final ValueType type;
        private final String stringValue;
        private final Long longValue;
        private final Double doubleValue;
        private final Boolean booleanValue;

        // Package-private constructors for use by Metadata class
        Value(String value) {
            this.type = ValueType.STRING;
            this.stringValue = value;
            this.longValue = null;
            this.doubleValue = null;
            this.booleanValue = null;
        }

        Value(Long value) {
            this.type = ValueType.LONG;
            this.stringValue = null;
            this.longValue = value;
            this.doubleValue = null;
            this.booleanValue = null;
        }

        Value(Double value) {
            this.type = ValueType.DOUBLE;
            this.stringValue = null;
            this.longValue = null;
            this.doubleValue = value;
            this.booleanValue = null;
        }

        Value(Boolean value) {
            this.type = ValueType.BOOLEAN;
            this.stringValue = null;
            this.longValue = null;
            this.doubleValue = null;
            this.booleanValue = value;
        }

        Value() {
            this.type = ValueType.NULL;
            this.stringValue = null;
            this.longValue = null;
            this.doubleValue = null;
            this.booleanValue = null;
        }

        public ValueType getType() { return type; }
        public String asString() { return stringValue; }
        public Long asLong() { return longValue; }
        public Double asDouble() { return doubleValue; }
        public Boolean asBoolean() { return booleanValue; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value that)) return false;
            return type == that.type &&
                   Objects.equals(stringValue, that.stringValue) &&
                   Objects.equals(longValue, that.longValue) &&
                   Objects.equals(doubleValue, that.doubleValue) &&
                   Objects.equals(booleanValue, that.booleanValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, stringValue, longValue, doubleValue, booleanValue);
        }

        @Override
        public String toString() {
            return switch (type) {
                case STRING -> stringValue;
                case LONG -> String.valueOf(longValue);
                case DOUBLE -> String.valueOf(doubleValue);
                case BOOLEAN -> String.valueOf(booleanValue);
                case NULL -> "null";
            };
        }

        void save(DataOutput out) throws IOException {
            out.writeInt(type.code());
            switch (type) {
                case STRING -> {
                    byte[] bytes = stringValue.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                }
                case LONG -> out.writeLong(longValue);
                case DOUBLE -> out.writeDouble(doubleValue);
                case BOOLEAN -> out.writeBoolean(booleanValue);
                case NULL -> {}
            }
        }

        static Value load(DataInput in) throws IOException {
            ValueType type = ValueType.fromCode(in.readInt());
            return switch (type) {
                case STRING -> {
                    int len = in.readInt();
                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    yield new Value(new String(bytes, StandardCharsets.UTF_8));
                }
                case LONG -> new Value(in.readLong());
                case DOUBLE -> new Value(in.readDouble());
                case BOOLEAN -> new Value(in.readBoolean());
                case NULL -> new Value();
            };
        }
    }

    private final Map<String, Value> data;

    public Metadata() {
        this.data = new HashMap<>();
    }

    private Metadata(Map<String, Value> data) {
        this.data = new HashMap<>(data);
    }

    /**
     * Put a string value
     */
    public Metadata put(String key, String value) {
        data.put(key, new Value(value));
        return this;
    }

    /**
     * Put a long value (for timestamps, integers)
     */
    public Metadata put(String key, Long value) {
        data.put(key, new Value(value));
        return this;
    }

    /**
     * Put an integer value (stored as long)
     */
    public Metadata put(String key, Integer value) {
        data.put(key, new Value(value.longValue()));
        return this;
    }

    /**
     * Put a double value (for scores, floats)
     */
    public Metadata put(String key, Double value) {
        data.put(key, new Value(value));
        return this;
    }

    /**
     * Put a float value (stored as double)
     */
    public Metadata put(String key, Float value) {
        data.put(key, new Value(value.doubleValue()));
        return this;
    }

    /**
     * Put a boolean value
     */
    public Metadata put(String key, Boolean value) {
        data.put(key, new Value(value));
        return this;
    }

    /**
     * Get a value by key
     */
    public Value get(String key) {
        return data.get(key);
    }

    /**
     * Get string value by key
     */
    public String getString(String key) {
        Value v = data.get(key);
        return v != null ? v.asString() : null;
    }

    /**
     * Get long value by key
     */
    public Long getLong(String key) {
        Value v = data.get(key);
        return v != null ? v.asLong() : null;
    }

    /**
     * Get double value by key
     */
    public Double getDouble(String key) {
        Value v = data.get(key);
        return v != null ? v.asDouble() : null;
    }

    /**
     * Get boolean value by key
     */
    public Boolean getBoolean(String key) {
        Value v = data.get(key);
        return v != null ? v.asBoolean() : null;
    }

    /**
     * Check if key exists
     */
    public boolean contains(String key) {
        return data.containsKey(key);
    }

    /**
     * Get all keys
     */
    public java.util.Set<String> keySet() {
        return data.keySet();
    }

    /**
     * Get size
     */
    public int size() {
        return data.size();
    }

    /**
     * Check if empty
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Create a copy of this metadata
     */
    public Metadata copy() {
        return new Metadata(this.data);
    }

    /**
     * Convert metadata to JSON object for API responses
     */
    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, Value> entry : data.entrySet()) {
            Value value = entry.getValue();
            switch (value.getType()) {
                case STRING -> jsonObject.add(entry.getKey(), new JsonPrimitive(value.asString()));
                case LONG -> jsonObject.add(entry.getKey(), new JsonPrimitive(value.asLong()));
                case DOUBLE -> jsonObject.add(entry.getKey(), new JsonPrimitive(value.asDouble()));
                case BOOLEAN -> jsonObject.add(entry.getKey(), new JsonPrimitive(value.asBoolean()));
                case NULL -> jsonObject.add(entry.getKey(), null);
            }
        }
        return jsonObject;
    }

    /**
     * Save to DataOutput
     */
    public void save(DataOutput out) throws IOException {
        out.writeInt(data.size());
        for (Map.Entry<String, Value> entry : data.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            out.writeInt(keyBytes.length);
            out.write(keyBytes);
            entry.getValue().save(out);
        }
    }

    /**
     * Load from DataInput
     */
    public static Metadata load(DataInput in) throws IOException {
        int size = in.readInt();
        Map<String, Value> data = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            int keyLen = in.readInt();
            byte[] keyBytes = new byte[keyLen];
            in.readFully(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            Value value = Value.load(in);
            data.put(key, value);
        }
        return new Metadata(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Metadata that)) return false;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public String toString() {
        return "Metadata" + data;
    }
}
