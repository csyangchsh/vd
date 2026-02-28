package com.csyangchsh.demo.vd.server.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Request and response models for vector operations
 */
public class VectorModels {

    /**
     * Request for inserting a single vector
     */
    public record InsertVectorRequest(
            @SerializedName("vector") float[] vector,
            @SerializedName("text") String text,
            @SerializedName("metadata") Map<String, Object> metadata
    ) {}

    /**
     * Request for batch inserting vectors
     */
    public record BatchInsertRequest(
            @SerializedName("vectors") VectorItem[] vectors
    ) {
        public record VectorItem(
                @SerializedName("data") float[] data,
                @SerializedName("text") String text,
                @SerializedName("metadata") Map<String, Object> metadata
        ) {}
    }

    /**
     * Request for deleting vectors
     */
    public record DeleteVectorsRequest(
            @SerializedName("ids") String[] ids  // UUID v7 strings
    ) {}

    /**
     * Response for successful insertion
     */
    public record InsertResponse(
            @SerializedName("id") String id,  // UUID v7 string
            @SerializedName("dimension") int dimension
    ) {}

    /**
     * Response for batch insertion
     */
    public record BatchInsertResponse(
            @SerializedName("count") int count,
            @SerializedName("ids") String[] ids  // UUID v7 strings
    ) {}

    /**
     * Response for delete operation
     */
    public record DeleteResponse(
            @SerializedName("deleted") int deleted
    ) {}

    /**
     * Response for getting vector info
     */
    public record VectorInfoResponse(
            @SerializedName("id") String id,
            @SerializedName("dimension") int dimension,
            @SerializedName("deleted") boolean deleted,
            @SerializedName("text") String text,
            @SerializedName("metadata") JsonObject metadata
    ) {}

    /**
     * Response for listing vectors
     */
    public record VectorsListResponse(
            @SerializedName("total") int total,
            @SerializedName("active") int active,
            @SerializedName("dimension") int dimension
    ) {}
}
