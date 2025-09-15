package io.github.kengirie.JBlossom.exception;

public class RangeNotSatisfiableException extends RuntimeException {
    
    private final long fileSize;
    private final String requestedRange;
    
    public RangeNotSatisfiableException(String message, String requestedRange, long fileSize) {
        super(message);
        this.requestedRange = requestedRange;
        this.fileSize = fileSize;
    }
    
    public RangeNotSatisfiableException(String message, String requestedRange, long fileSize, Throwable cause) {
        super(message, cause);
        this.requestedRange = requestedRange;
        this.fileSize = fileSize;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public String getRequestedRange() {
        return requestedRange;
    }
    
    public String getContentRangeHeader() {
        return "bytes */" + fileSize;
    }
}