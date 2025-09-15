package io.github.kengirie.JBlossom.model;

import io.github.kengirie.JBlossom.exception.AuthenticationException;
import java.util.Map;
import java.util.HashMap;

public class AuthResult {
    private final boolean valid;
    private final String pubkey;
    private final String reason;
    private final long createdAt;
    private final long expiration;
    private final String action;
    private final Map<String, String> tags;
    private final AuthenticationException.AuthErrorType errorType;
    
    private AuthResult(boolean valid, String pubkey, String reason, long createdAt, long expiration, String action, Map<String, String> tags, AuthenticationException.AuthErrorType errorType) {
        this.valid = valid;
        this.pubkey = pubkey;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiration = expiration;
        this.action = action;
        this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
        this.errorType = errorType;
    }
    
    public static AuthResult valid(String pubkey, long createdAt, long expiration, String action, Map<String, String> tags) {
        return new AuthResult(true, pubkey, null, createdAt, expiration, action, tags, null);
    }
    
    public static AuthResult invalid(String reason, AuthenticationException.AuthErrorType errorType) {
        return new AuthResult(false, null, reason, 0, 0, null, null, errorType);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public String getPubkey() {
        return pubkey;
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getExpiration() {
        return expiration;
    }
    
    public String getAction() {
        return action;
    }
    
    public Map<String, String> getTags() {
        return new HashMap<>(tags);
    }
    
    public boolean hasTag(String tagName) {
        return tags.containsKey(tagName);
    }
    
    public String getTagValue(String tagName) {
        return tags.get(tagName);
    }
    
    public AuthenticationException.AuthErrorType getErrorType() {
        return errorType;
    }
    
    @Override
    public String toString() {
        if (valid) {
            return String.format("AuthResult{valid=true, pubkey='%s', action='%s'}", pubkey, action);
        } else {
            return String.format("AuthResult{valid=false, reason='%s'}", reason);
        }
    }
}