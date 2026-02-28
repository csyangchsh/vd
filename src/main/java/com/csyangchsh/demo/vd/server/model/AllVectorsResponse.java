package com.csyangchsh.demo.vd.server.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Response for listing all vectors with details
 */
public record AllVectorsResponse(
        @SerializedName("total") int total,
        @SerializedName("active") int active,
        @SerializedName("dimension") int dimension,
        @SerializedName("vectors") VectorDetail[] vectors
) {
    public record VectorDetail(
            @SerializedName("id") String id,
            @SerializedName("dimension") int dimension,
            @SerializedName("deleted") boolean deleted,
            @SerializedName("text") String text,
            @SerializedName("vector") float[] vector,
            @SerializedName("metadata") JsonObject metadata
    ) {}
}
