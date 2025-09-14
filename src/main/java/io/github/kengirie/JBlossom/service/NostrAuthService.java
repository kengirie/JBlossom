package io.github.kengirie.JBlossom.service;

import io.github.kengirie.JBlossom.model.AuthResult;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Base64;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class NostrAuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(NostrAuthService.class);
    private static final int KIND_AUTH = 24242;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    
    private final ObjectMapper objectMapper;
    
    public NostrAuthService() {
        this.objectMapper = new ObjectMapper();
    }
    
    public AuthResult validateAuthEvent(String authHeader, String requiredAction) {
        return validateAuthEvent(authHeader, requiredAction, null);
    }
    
    public AuthResult validateAuthEvent(String authHeader, String requiredAction, String requiredHash) {
        if (authHeader == null || authHeader.isBlank()) {
            return AuthResult.invalid("Missing Authorization header");
        }
        
        // Authorization: Nostr <base64_event> の形式をチェック
        if (!authHeader.startsWith("Nostr ")) {
            return AuthResult.invalid("Invalid Authorization scheme, expected 'Nostr'");
        }
        
        String base64Event = authHeader.substring(6).trim();
        if (base64Event.isBlank()) {
            return AuthResult.invalid("Missing event data in Authorization header");
        }
        
        try {
            // Base64デコード
            byte[] decodedBytes = Base64.getDecoder().decode(base64Event);
            String eventJson = new String(decodedBytes);
            
            logger.debug("Decoded Nostr event: {}", eventJson);
            
            // JSONパース
            JsonNode eventNode = objectMapper.readTree(eventJson);
            
            // 基本フィールド検証
            AuthResult basicValidation = validateBasicFields(eventNode);
            if (!basicValidation.isValid()) {
                return basicValidation;
            }
            
            // タイムスタンプ検証
            AuthResult timeValidation = validateTimestamps(eventNode);
            if (!timeValidation.isValid()) {
                return timeValidation;
            }
            
            // タグ検証
            AuthResult tagValidation = validateTags(eventNode, requiredAction, requiredHash);
            if (!tagValidation.isValid()) {
                return tagValidation;
            }
            
            // 署名検証
            AuthResult signatureValidation = validateSignature(eventNode);
            if (!signatureValidation.isValid()) {
                return signatureValidation;
            }
            
            // 全ての検証に成功
            String pubkey = eventNode.get("pubkey").asText();
            long createdAt = eventNode.get("created_at").asLong();
            long expiration = getExpirationFromTags(eventNode);
            
            logger.info("Valid auth event for pubkey: {}, action: {}", pubkey, requiredAction);
            return AuthResult.valid(pubkey, createdAt, expiration, requiredAction);
            
        } catch (Exception e) {
            logger.error("Failed to validate auth event", e);
            return AuthResult.invalid("Invalid event format: " + e.getMessage());
        }
    }
    
    private AuthResult validateBasicFields(JsonNode eventNode) {
        // kind フィールドチェック
        if (!eventNode.has("kind") || eventNode.get("kind").asInt() != KIND_AUTH) {
            return AuthResult.invalid("Invalid kind, expected " + KIND_AUTH);
        }
        
        // pubkey フィールドチェック
        if (!eventNode.has("pubkey")) {
            return AuthResult.invalid("Missing pubkey field");
        }
        
        String pubkey = eventNode.get("pubkey").asText();
        if (!HEX_PATTERN.matcher(pubkey).matches()) {
            return AuthResult.invalid("Invalid pubkey format, expected 64-char hex");
        }
        
        // created_at フィールドチェック
        if (!eventNode.has("created_at")) {
            return AuthResult.invalid("Missing created_at field");
        }
        
        // content フィールドチェック
        if (!eventNode.has("content")) {
            return AuthResult.invalid("Missing content field");
        }
        
        // signature フィールドチェック
        if (!eventNode.has("sig")) {
            return AuthResult.invalid("Missing signature field");
        }
        
        return AuthResult.valid(pubkey, 0, 0, null);
    }
    
    private AuthResult validateTimestamps(JsonNode eventNode) {
        long now = Instant.now().getEpochSecond();
        
        // created_at は過去でなければならない
        long createdAt = eventNode.get("created_at").asLong();
        if (createdAt > now + 60) { // 1分の誤差を許容
            return AuthResult.invalid("created_at must be in the past");
        }
        
        // expiration タグから有効期限を取得
        long expiration = getExpirationFromTags(eventNode);
        if (expiration == 0) {
            return AuthResult.invalid("Missing expiration tag");
        }
        
        // expiration は未来でなければならない
        if (expiration <= now) {
            return AuthResult.invalid("Event has expired");
        }
        
        return AuthResult.valid(null, createdAt, expiration, null);
    }
    
    private AuthResult validateTags(JsonNode eventNode, String requiredAction, String requiredHash) {
        if (!eventNode.has("tags") || !eventNode.get("tags").isArray()) {
            return AuthResult.invalid("Missing or invalid tags field");
        }
        
        JsonNode tagsArray = eventNode.get("tags");
        
        // t タグ（action）検証
        boolean foundTTag = false;
        boolean foundExpirationTag = false;
        boolean hashValidation = requiredHash == null; // hash不要な場合は true
        
        for (JsonNode tag : tagsArray) {
            if (!tag.isArray() || tag.size() < 2) {
                continue;
            }
            
            String tagName = tag.get(0).asText();
            String tagValue = tag.get(1).asText();
            
            switch (tagName) {
                case "t":
                    if (requiredAction != null && requiredAction.equals(tagValue)) {
                        foundTTag = true;
                    }
                    break;
                case "expiration":
                    foundExpirationTag = true;
                    break;
                case "x":
                    if (requiredHash != null && requiredHash.equals(tagValue)) {
                        hashValidation = true;
                    }
                    break;
            }
        }
        
        if (!foundTTag) {
            return AuthResult.invalid("Missing or invalid 't' tag for action: " + requiredAction);
        }
        
        if (!foundExpirationTag) {
            return AuthResult.invalid("Missing expiration tag");
        }
        
        if (!hashValidation) {
            return AuthResult.invalid("Missing 'x' tag for required hash: " + requiredHash);
        }
        
        return AuthResult.valid(null, 0, 0, requiredAction);
    }
    
    private long getExpirationFromTags(JsonNode eventNode) {
        if (!eventNode.has("tags")) {
            return 0;
        }
        
        JsonNode tagsArray = eventNode.get("tags");
        for (JsonNode tag : tagsArray) {
            if (tag.isArray() && tag.size() >= 2) {
                if ("expiration".equals(tag.get(0).asText())) {
                    try {
                        return Long.parseLong(tag.get(1).asText());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }
    
    private AuthResult validateSignature(JsonNode eventNode) {
        try {
            // TODO: nostr-java を使った署名検証を実装
            // 現在は基本的な構造チェックのみ
            
            String pubkey = eventNode.get("pubkey").asText();
            String signature = eventNode.get("sig").asText();
            
            if (!HEX_PATTERN.matcher(signature).matches() && signature.length() != 128) {
                return AuthResult.invalid("Invalid signature format");
            }
            
            // 署名検証の詳細実装は後のコミットで追加
            logger.warn("Signature validation not yet fully implemented");
            
            return AuthResult.valid(pubkey, 0, 0, null);
            
        } catch (Exception e) {
            logger.error("Signature validation failed", e);
            return AuthResult.invalid("Signature validation error: " + e.getMessage());
        }
    }
    
    // ユーティリティメソッド: Nostr eventの検証のみ（action不問）
    public boolean isValidNostrEvent(String authHeader) {
        AuthResult result = validateAuthEvent(authHeader, "get", null);
        return result.isValid();
    }
}