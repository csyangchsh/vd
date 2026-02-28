package com.csyangchsh.demo.vd.server.model;

import com.google.gson.annotations.SerializedName;

/**
 * Standard error response
 */
public record ErrorResponse(
        @SerializedName("error") String error,
        @SerializedName("code") String code,
        @SerializedName("details") String details
) {
    public static ErrorResponse badRequest(String message) {
        return new ErrorResponse(message, "BAD_REQUEST", null);
    }

    public static ErrorResponse notFound(String message) {
        return new ErrorResponse(message, "NOT_FOUND", null);
    }

    public static ErrorResponse methodNotAllowed(String message) {
        return new ErrorResponse(message, "METHOD_NOT_ALLOWED", null);
    }

    public static ErrorResponse internalError(String message, String details) {
        return new ErrorResponse(message, "INTERNAL_ERROR", details);
    }

    public static ErrorResponse validationError(String message, String details) {
        return new ErrorResponse(message, "VALIDATION_ERROR", details);
    }
}
