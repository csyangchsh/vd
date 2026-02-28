package com.csyangchsh.demo.vd.server.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Request and response models for search operations
 */
public class SearchModels {

    /**
     * Request for vector search
     */
    public record SearchRequest(
            @SerializedName("query") float[] query,
            @SerializedName("k") Integer k,
            @SerializedName("distanceType") String distanceType,
            @SerializedName("filter") JsonObject filter  // Optional metadata filter
    ) {
        public SearchRequest {
            if (k == null) k = 10;
            if (distanceType == null) distanceType = "L2";
        }
    }

    /**
     * Individual search result
     */
    public record SearchResultItem(
            @SerializedName("id") String id,  // UUID v7 string
            @SerializedName("score") float score,
            @SerializedName("text") String text,
            @SerializedName("metadata") JsonObject metadata
    ) {}

    /**
     * Response for vector search
     */
    public record SearchResponse(
            @SerializedName("query_dimension") int queryDimension,
            @SerializedName("k") int k,
            @SerializedName("result_count") int resultCount,
            @SerializedName("results") SearchResultItem[] results
    ) {}
}
