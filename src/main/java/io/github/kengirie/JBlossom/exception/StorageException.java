package io.github.kengirie.JBlossom.exception;

public class StorageException extends RuntimeException {
    
    public enum StorageErrorType {
        BLOB_NOT_FOUND("Blob not found"),
        FILE_READ_ERROR("Failed to read blob file"),
        FILE_CORRUPTED("Blob file corrupted or unreadable"),
        HASH_MISMATCH("File hash does not match expected SHA256"),
        DATABASE_ERROR("Database operation failed"),
        INVALID_HASH_FORMAT("Invalid SHA256 hash format"),
        STORAGE_UNAVAILABLE("Storage system unavailable");
        
        private final String defaultMessage;
        
        StorageErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
        
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }
    
    private final StorageErrorType errorType;
    private final String sha256Hash;
    
    public StorageException(StorageErrorType errorType, String sha256Hash) {
        super(errorType.getDefaultMessage());
        this.errorType = errorType;
        this.sha256Hash = sha256Hash;
    }
    
    public StorageException(StorageErrorType errorType, String sha256Hash, String customMessage) {
        super(customMessage);
        this.errorType = errorType;
        this.sha256Hash = sha256Hash;
    }
    
    public StorageException(StorageErrorType errorType, String sha256Hash, Throwable cause) {
        super(errorType.getDefaultMessage(), cause);
        this.errorType = errorType;
        this.sha256Hash = sha256Hash;
    }
    
    public StorageException(StorageErrorType errorType, String sha256Hash, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorType = errorType;
        this.sha256Hash = sha256Hash;
    }
    
    public StorageErrorType getErrorType() {
        return errorType;
    }
    
    public String getSha256Hash() {
        return sha256Hash;
    }
    
    public String getXReasonHeader() {
        if (sha256Hash != null) {
            return getMessage() + " (hash: " + sha256Hash + ")";
        }
        return getMessage();
    }
}