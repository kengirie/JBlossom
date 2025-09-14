package io.github.kengirie.JBlossom.model;

public class AuthResult {
    private final boolean valid;
    private final String pubkey;
    private final String reason;
    private final long createdAt;
    private final long expiration;
    private final String action;
    
    private AuthResult(boolean valid, String pubkey, String reason, long createdAt, long expiration, String action) {
        this.valid = valid;
        this.pubkey = pubkey;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiration = expiration;
        this.action = action;
    }
    
    public static AuthResult valid(String pubkey, long createdAt, long expiration, String action) {
        return new AuthResult(true, pubkey, null, createdAt, expiration, action);
    }
    
    public static AuthResult invalid(String reason) {
        return new AuthResult(false, null, reason, 0, 0, null);
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
    
    @Override
    public String toString() {
        if (valid) {
            return String.format("AuthResult{valid=true, pubkey='%s', action='%s'}", pubkey, action);
        } else {
            return String.format("AuthResult{valid=false, reason='%s'}", reason);
        }
    }
}