package io.github.kengirie.JBlossom.exception;

public class AuthenticationException extends RuntimeException {
    
    public enum AuthErrorType {
        INVALID_SIGNATURE("Invalid Nostr event signature"),
        EVENT_EXPIRED("Auth event expired"),
        INVALID_KIND("Invalid event kind, expected 24242"),
        MISSING_TAGS("Required tags missing"),
        INVALID_ACTION("Invalid action in t tag"),
        INVALID_FORMAT("Invalid auth event format"),
        MALFORMED_BASE64("Malformed base64 encoding"),
        TIMESTAMP_FUTURE("Event timestamp is in the future"),
        MISSING_AUTHORIZATION("Missing Authorization header"),
        MISSING_AUTH("Authorization required for this operation"),
        HASH_MISMATCH("SHA256 hash mismatch in auth event");
        
        private final String defaultMessage;
        
        AuthErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
        
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }
    
    private final AuthErrorType errorType;
    
    public AuthenticationException(AuthErrorType errorType) {
        super(errorType.getDefaultMessage());
        this.errorType = errorType;
    }
    
    public AuthenticationException(AuthErrorType errorType, String customMessage) {
        super(customMessage);
        this.errorType = errorType;
    }
    
    public AuthenticationException(AuthErrorType errorType, Throwable cause) {
        super(errorType.getDefaultMessage(), cause);
        this.errorType = errorType;
    }
    
    public AuthenticationException(AuthErrorType errorType, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorType = errorType;
    }
    
    public AuthErrorType getErrorType() {
        return errorType;
    }
    
    public String getXReasonHeader() {
        return getMessage();
    }
}