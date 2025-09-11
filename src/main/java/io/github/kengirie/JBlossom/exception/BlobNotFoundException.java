package io.github.kengirie.JBlossom.exception;

public class BlobNotFoundException extends RuntimeException {
    
    public BlobNotFoundException(String hash) {
        super("Blob not found: " + hash);
    }
    
    public BlobNotFoundException(String hash, Throwable cause) {
        super("Blob not found: " + hash, cause);
    }
}