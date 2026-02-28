package com.csyangchsh.demo.vd.index;

import com.csyangchsh.demo.vd.model.Metadata;
import com.csyangchsh.demo.vd.model.Vector;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for Vector serialization and deserialization.
 * Format version 2: ID, deleted, text, metadata, vector data
 */
public class VectorIO {

    // Format version constants
    private static final int FORMAT_V2 = 2;  // Current format: ID, deleted, text, metadata, data

    /**
     * Save a vector to DataOutput.
     * Format:
     * - Version (int)
     * - ID (string length + bytes)
     * - Deleted flag (boolean)
     * - Text flag + text (if present)
     * - Metadata flag + metadata (if present)
     * - Vector data (float[])
     */
    public static void saveVector(Vector vector, DataOutput out) throws IOException {
        // Write version
        out.writeInt(FORMAT_V2);

        // Write ID (as string)
        String id = vector.getId();
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        out.writeInt(idBytes.length);
        out.write(idBytes);

        // Write deleted flag
        out.writeBoolean(vector.isDeleted());

        // Write text (optional)
        String text = vector.getText();
        if (text == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            out.writeInt(textBytes.length);
            out.write(textBytes);
        }

        // Write metadata (optional)
        Metadata metadata = vector.getMetadata();
        if (metadata == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            metadata.save(out);
        }

        // Write vector data
        float[] data = vector.getData();
        for (float v : data) {
            out.writeFloat(v);
        }
    }

    /**
     * Load a vector from DataInput.
     * Supports format v2 (current).
     */
    public static Vector loadVector(DataInput in, int dimension) throws IOException {
        // Read version
        int version = in.readInt();

        return switch (version) {
            case FORMAT_V2 -> loadVectorV2(in, dimension);
            default -> throw new IOException("Unknown vector format version: " + version);
        };
    }

    /**
     * Load vector in v2 format (current: text and metadata, no payload).
     */
    private static Vector loadVectorV2(DataInput in, int dimension) throws IOException {
        // Read ID
        int idLength = in.readInt();
        byte[] idBytes = new byte[idLength];
        in.readFully(idBytes);
        String id = new String(idBytes, StandardCharsets.UTF_8);

        // Read deleted flag
        boolean deleted = in.readBoolean();

        // Read text (optional)
        String text = null;
        boolean hasText = in.readBoolean();
        if (hasText) {
            int textLength = in.readInt();
            byte[] textBytes = new byte[textLength];
            in.readFully(textBytes);
            text = new String(textBytes, StandardCharsets.UTF_8);
        }

        // Read metadata (optional)
        Metadata metadata = null;
        boolean hasMetadata = in.readBoolean();
        if (hasMetadata) {
            metadata = Metadata.load(in);
        }

        // Read vector data
        float[] data = new float[dimension];
        for (int j = 0; j < dimension; j++) {
            data[j] = in.readFloat();
        }

        Vector vector = new Vector(id, data, text, metadata);
        vector.setDeleted(deleted);
        return vector;
    }
}
