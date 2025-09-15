package io.github.kengirie.JBlossom.service;

import io.github.kengirie.JBlossom.model.AuthResult;
import io.github.kengirie.JBlossom.exception.AuthenticationException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import nostr.crypto.schnorr.Schnorr;
import nostr.util.NostrUtil;

import java.util.Base64;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
public class NostrAuthService {

    private static final Logger logger = LoggerFactory.getLogger(NostrAuthService.class);
    private static final int KIND_AUTH = 24242;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern SIGNATURE_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{128}$");

    private final ObjectMapper objectMapper;

    public NostrAuthService() {
        this.objectMapper = new ObjectMapper();
    }

    public AuthResult validateAuthEvent(String authHeader, String requiredAction) {
        return validateAuthEvent(authHeader, requiredAction, null);
    }

    public AuthResult validateAuthEvent(String authHeader, String requiredAction, String requiredHash) {
        if (authHeader == null || authHeader.isBlank()) {
            return AuthResult.invalid("Missing Authorization header", AuthenticationException.AuthErrorType.MISSING_AUTH);
        }

        // Authorization: Nostr <base64_event> の形式をチェック
        if (!authHeader.startsWith("Nostr ")) {
            return AuthResult.invalid("Invalid Authorization scheme, expected 'Nostr'",
                    AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }

        String base64Event = authHeader.substring(6).trim();
        if (base64Event.isBlank()) {
            return AuthResult.invalid("Missing event data in Authorization header", AuthenticationException.AuthErrorType.INVALID_FORMAT);
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
            Map<String, String> tags = extractTagsMap(eventNode);

            logger.info("Valid auth event for pubkey: {}, action: {}", pubkey, requiredAction);
            return AuthResult.valid(pubkey, createdAt, expiration, requiredAction, tags);

        } catch (Exception e) {
            logger.error("Failed to validate auth event", e);
            return AuthResult.invalid("Invalid event format: " + e.getMessage(), AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }
    }

    private AuthResult validateBasicFields(JsonNode eventNode) {
        // kind フィールドチェック
        if (!eventNode.has("kind") || eventNode.get("kind").asInt() != KIND_AUTH) {
            return AuthResult.invalid("Invalid kind, expected " + KIND_AUTH, AuthenticationException.AuthErrorType.INVALID_KIND);
        }

        // pubkey フィールドチェック
        if (!eventNode.has("pubkey")) {
            return AuthResult.invalid("Missing pubkey field", AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }

        String pubkey = eventNode.get("pubkey").asText();
        if (!HEX_PATTERN.matcher(pubkey).matches()) {
            return AuthResult.invalid("Invalid pubkey format, expected 64-char hex", AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }

        // created_at フィールドチェック
        if (!eventNode.has("created_at")) {
            return AuthResult.invalid("Missing created_at field", AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }

        // content フィールドチェック
        if (!eventNode.has("content")) {
            return AuthResult.invalid("Missing content field", AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }

        // signature フィールドチェック
        if (!eventNode.has("sig")) {
            return AuthResult.invalid("Missing signature field", AuthenticationException.AuthErrorType.INVALID_FORMAT);
        }

        return AuthResult.valid(pubkey, 0, 0, null, null);
    }

    private AuthResult validateTimestamps(JsonNode eventNode) {
        long now = Instant.now().getEpochSecond();

        // created_at は過去でなければならない
        long createdAt = eventNode.get("created_at").asLong();
        if (createdAt > now + 60) { // 1分の誤差を許容
            return AuthResult.invalid("created_at must be in the past", AuthenticationException.AuthErrorType.TIMESTAMP_FUTURE);
        }

        // expiration タグから有効期限を取得
        long expiration = getExpirationFromTags(eventNode);
        if (expiration == 0) {
            return AuthResult.invalid("Missing expiration tag", AuthenticationException.AuthErrorType.MISSING_TAGS);
        }

        // expiration は未来でなければならない
        if (expiration <= now) {
            return AuthResult.invalid("Event has expired", AuthenticationException.AuthErrorType.EVENT_EXPIRED);
        }

        return AuthResult.valid(null, createdAt, expiration, null, null);
    }

    private AuthResult validateTags(JsonNode eventNode, String requiredAction, String requiredHash) {
        if (!eventNode.has("tags") || !eventNode.get("tags").isArray()) {
            return AuthResult.invalid("Missing or invalid tags field", AuthenticationException.AuthErrorType.MISSING_TAGS);
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
            return AuthResult.invalid("Missing or invalid 't' tag for action: " + requiredAction, AuthenticationException.AuthErrorType.INVALID_ACTION);
        }

        if (!foundExpirationTag) {
            return AuthResult.invalid("Missing expiration tag", AuthenticationException.AuthErrorType.MISSING_TAGS);
        }

        if (!hashValidation) {
            return AuthResult.invalid("Missing 'x' tag for required hash: " + requiredHash, AuthenticationException.AuthErrorType.MISSING_TAGS);
        }

        return AuthResult.valid(null, 0, 0, requiredAction, null);
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
            String pubkey = eventNode.get("pubkey").asText();
            String signature = eventNode.get("sig").asText();
            String eventId = eventNode.get("id").asText();

            // 署名フォーマット検証
            if (!SIGNATURE_HEX_PATTERN.matcher(signature).matches() || signature.length() != 128) {
                return AuthResult.invalid("Invalid signature format", AuthenticationException.AuthErrorType.INVALID_SIGNATURE);
            }

            // nostr-javaを使って署名検証
            byte[] messageBytes = NostrUtil.hexToBytes(eventId);
            byte[] pubkeyBytes = NostrUtil.hexToBytes(pubkey);
            byte[] signatureBytes = NostrUtil.hex128ToBytes(signature);

            boolean isValid = Schnorr.verify(messageBytes, pubkeyBytes, signatureBytes);

            if (!isValid) {
                return AuthResult.invalid("Invalid signature", AuthenticationException.AuthErrorType.INVALID_SIGNATURE);
            }

            logger.debug("Signature validation successful for pubkey: {}", pubkey);
            return AuthResult.valid(pubkey, 0, 0, null, null);

        } catch (Exception e) {
            logger.error("Signature validation failed", e);
            return AuthResult.invalid("Signature validation error: " + e.getMessage(), AuthenticationException.AuthErrorType.INVALID_SIGNATURE);
        }
    }


    private Map<String, String> extractTagsMap(JsonNode eventNode) {
        Map<String, String> tagsMap = new HashMap<>();

        if (!eventNode.has("tags") || !eventNode.get("tags").isArray()) {
            return tagsMap;
        }

        JsonNode tagsArray = eventNode.get("tags");
        for (JsonNode tag : tagsArray) {
            if (tag.isArray() && tag.size() >= 2) {
                String tagName = tag.get(0).asText();
                String tagValue = tag.get(1).asText();
                tagsMap.put(tagName, tagValue);
            }
        }

        return tagsMap;
    }

    // ユーティリティメソッド: Nostr eventの検証のみ（action不問）
    public boolean isValidNostrEvent(String authHeader) {
        AuthResult result = validateAuthEvent(authHeader, "get", null);
        return result.isValid();
    }
}
