package com.vcs.collector.quota.exception;

/**
 * Exception thrown when token bucket operations fail
 */
public class TokenBucketException extends RuntimeException {
    
    public TokenBucketException(String message) {
        super(message);
    }
    
    public TokenBucketException(String message, Throwable cause) {
        super(message, cause);
    }
}
