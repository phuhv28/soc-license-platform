package com.vcs.collector.quota.exception;

/**
 * Exception thrown when quota is not found or invalid
 */
public class QuotaException extends RuntimeException {
    
    public QuotaException(String message) {
        super(message);
    }
    
    public QuotaException(String message, Throwable cause) {
        super(message, cause);
    }
}
