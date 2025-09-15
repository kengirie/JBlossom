package io.github.kengirie.JBlossom.service;

import io.github.kengirie.JBlossom.model.AuthResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NostrAuthServiceTest {

    private NostrAuthService nostrAuthService;

    @BeforeEach
    void setUp() {
        nostrAuthService = new NostrAuthService();
    }

    @Test
    void testValidAuthEvent() {
        // 有効な認証イベントのテストデータ
        long now = Instant.now().getEpochSecond();
        long createdAt = now - 60; // 1分前
        long expiration = now + 3600; // 1時間後

        String eventJson = String.format(
            """
    {
  "created_at": %d,
  "kind": 24242,
  "content": "blossom stuff",
  "tags": [
    [
      "expiration",
      "%d"
    ],
    [
      "t",
      "get"
    ],
    [
      "x",
      "d2958e6e31a4562ddcb99da838e12a002221c296f1901d82d9196900d2f89b47"
    ]
  ],
  "pubkey": "83279ad28eec4785e2139dc529a9650fdbb424366d4645e5c2824f7cbd49240d",
  "id": "3478cdb9c576e09605cf154105221333ac571071abf4c9630104936a9bd305e9",
  "sig": "fd4c35229e749fdf06120269963578f3806f2aad341f57840a6bf0a5edae39e5fd06d3816a4e050444d032e0062da89875868be1928e99cfb84ce7c62da123e0"
            }"""
            , createdAt, expiration);

        String authHeader = "Nostr " + Base64.getEncoder().encodeToString(eventJson.getBytes());

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        System.out.println(result.getReason());

        assertTrue(result.isValid());
        assertEquals("83279ad28eec4785e2139dc529a9650fdbb424366d4645e5c2824f7cbd49240d", result.getPubkey());
        assertEquals("get", result.getAction());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(expiration, result.getExpiration());
    }

    @Test
    void testMissingAuthorizationHeader() {
        AuthResult result = nostrAuthService.validateAuthEvent(null, "get");

        assertFalse(result.isValid());
        assertEquals("Missing Authorization header", result.getReason());
    }

    @Test
    void testInvalidAuthorizationScheme() {
        String authHeader = "Bearer invalid-token";

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        assertFalse(result.isValid());
        assertEquals("Invalid Authorization scheme, expected 'Nostr'", result.getReason());
    }

    @Test
    void testInvalidKind() {
        String eventJson = """
            {
                "id": "test",
                "pubkey": "b53185b9f27962ebdf76b8a9b0a84cd8b27f9f3d4abd59f715788a3bf9e7f75e",
                "kind": 1,
                "content": "Invalid kind",
                "created_at": %d,
                "tags": [],
                "sig": "d0d58c92afb3f4f1925120b99c39cffe77d93e82f488c5f8f482e8f97df75c5357175b5098c338661c37d1074b0a18ab5e75a9df08967bfb200930ec6a76562f"
            }""".formatted(Instant.now().getEpochSecond() - 60);

        String authHeader = "Nostr " + Base64.getEncoder().encodeToString(eventJson.getBytes());

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        assertFalse(result.isValid());
        assertEquals("Invalid kind, expected 24242", result.getReason());
    }

    @Test
    void testExpiredEvent() {
        long now = Instant.now().getEpochSecond();
        long createdAt = now - 3600; // 1時間前
        long expiration = now - 60;   // 1分前（期限切れ）

        String eventJson = String.format("""
            {
                "id": "bb653c815da18c089f3124b41c4b5ec072a40b87ca0f50bbbc6ecde9aca442eb",
                "pubkey": "b53185b9f27962ebdf76b8a9b0a84cd8b27f9f3d4abd59f715788a3bf9e7f75e",
                "kind": 24242,
                "content": "Expired event",
                "created_at": %d,
                "tags": [
                    ["t", "get"],
                    ["expiration", "%d"]
                ],
                "sig": "d0d58c92afb3f4f1925120b99c39cffe77d93e82f488c5f8f482e8f97df75c5357175b5098c338661c37d1074b0a18ab5e75a9df08967bfb200930ec6a76562f"
            }""", createdAt, expiration);

        String authHeader = "Nostr " + Base64.getEncoder().encodeToString(eventJson.getBytes());

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        assertFalse(result.isValid());
        assertEquals("Event has expired", result.getReason());
    }

    @Test
    void testMissingTTag() {
        long now = Instant.now().getEpochSecond();
        long createdAt = now - 60;
        long expiration = now + 3600;

        String eventJson = String.format("""
            {
                "id": "bb653c815da18c089f3124b41c4b5ec072a40b87ca0f50bbbc6ecde9aca442eb",
                "pubkey": "b53185b9f27962ebdf76b8a9b0a84cd8b27f9f3d4abd59f715788a3bf9e7f75e",
                "kind": 24242,
                "content": "Missing t tag",
                "created_at": %d,
                "tags": [
                    ["expiration", "%d"]
                ],
                "sig": "d0d58c92afb3f4f1925120b99c39cffe77d93e82f488c5f8f482e8f97df75c5357175b5098c338661c37d1074b0a18ab5e75a9df08967bfb200930ec6a76562f"
            }""", createdAt, expiration);

        String authHeader = "Nostr " + Base64.getEncoder().encodeToString(eventJson.getBytes());

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        assertFalse(result.isValid());
        assertEquals("Missing or invalid 't' tag for action: get", result.getReason());
    }

    @Test
    void testRequiredHashValidation() {
        long now = Instant.now().getEpochSecond();
        long createdAt = now - 60;
        long expiration = now + 3600;
        String requiredHash = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553";

        // x タグありのイベント
        String eventJsonWithX = String.format("""
            {
                "id": "bb653c815da18c089f3124b41c4b5ec072a40b87ca0f50bbbc6ecde9aca442eb",
                "pubkey": "b53185b9f27962ebdf76b8a9b0a84cd8b27f9f3d4abd59f715788a3bf9e7f75e",
                "kind": 24242,
                "content": "Get specific blob",
                "created_at": %d,
                "tags": [
                    ["t", "get"],
                    ["x", "%s"],
                    ["expiration", "%d"]
                ],
                "sig": "d0d58c92afb3f4f1925120b99c39cffe77d93e82f488c5f8f482e8f97df75c5357175b5098c338661c37d1074b0a18ab5e75a9df08967bfb200930ec6a76562f"
            }""", createdAt, requiredHash, expiration);

        String authHeaderWithX = "Nostr " + Base64.getEncoder().encodeToString(eventJsonWithX.getBytes());

        AuthResult resultWithX = nostrAuthService.validateAuthEvent(authHeaderWithX, "get", requiredHash);
        assertTrue(resultWithX.isValid());

        // x タグなしのイベント（必須ハッシュが指定されている場合）
        String eventJsonWithoutX = String.format("""
            {
                "id": "bb653c815da18c089f3124b41c4b5ec072a40b87ca0f50bbbc6ecde9aca442eb",
                "pubkey": "b53185b9f27962ebdf76b8a9b0a84cd8b27f9f3d4abd59f715788a3bf9e7f75e",
                "kind": 24242,
                "content": "Get specific blob",
                "created_at": %d,
                "tags": [
                    ["t", "get"],
                    ["expiration", "%d"]
                ],
                "sig": "d0d58c92afb3f4f1925120b99c39cffe77d93e82f488c5f8f482e8f97df75c5357175b5098c338661c37d1074b0a18ab5e75a9df08967bfb200930ec6a76562f"
            }""", createdAt, expiration);

        String authHeaderWithoutX = "Nostr " + Base64.getEncoder().encodeToString(eventJsonWithoutX.getBytes());

        AuthResult resultWithoutX = nostrAuthService.validateAuthEvent(authHeaderWithoutX, "get", requiredHash);
        assertFalse(resultWithoutX.isValid());
        assertTrue(resultWithoutX.getReason().contains("Missing 'x' tag"));
    }

    @Test
    void testInvalidBase64() {
        String authHeader = "Nostr invalid-base64!";

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Invalid event format"));
    }

    @Test
    void testInvalidJson() {
        String invalidJson = "{ invalid json }";
        String authHeader = "Nostr " + Base64.getEncoder().encodeToString(invalidJson.getBytes());

        AuthResult result = nostrAuthService.validateAuthEvent(authHeader, "get");

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Invalid event format"));
    }
}
