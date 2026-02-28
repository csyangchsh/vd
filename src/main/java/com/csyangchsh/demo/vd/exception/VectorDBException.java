package com.csyangchsh.demo.vd.exception;

/**
 * Vector Database Exception
 */
public class VectorDBException extends RuntimeException {

    public VectorDBException(String message) {
        super(message);
    }

    public VectorDBException(String message, Throwable cause) {
        super(message, cause);
    }

    public VectorDBException(Throwable cause) {
        super(cause);
    }
}
