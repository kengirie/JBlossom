package io.github.kengirie.JBlossom.controller;

import io.github.kengirie.JBlossom.exception.AuthenticationException;
import io.github.kengirie.JBlossom.exception.StorageException;
import io.github.kengirie.JBlossom.model.AuthResult;
import io.github.kengirie.JBlossom.model.BlobMetadata;
import io.github.kengirie.JBlossom.service.NostrAuthService;
import io.github.kengirie.JBlossom.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.startsWith;

@WebMvcTest(UploadController.class)
public class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @MockBean
    private NostrAuthService nostrAuthService;

    private static final String TEST_HASH = "1827b95e971ac79f6b79242512d74c010166603c2bd1958679cb5da14f3b11c3";
    private static final String TEST_CONTENT = "Test blob content";
    private static final String TEST_PUBKEY = "83279ad28eec4785e2139dc529a9650fdbb424366d4645e5c2824f7cbd49240d";
    private static final String eventJson = """
    {
  "created_at": 1757936981,
  "kind": 24242,
  "content": "blossom stuff",
  "tags": [
    [
      "expiration",
      "1757937041"
    ],
    [
      "t",
      "upload"
    ],
    [
      "x",
      "d2958e6e31a4562ddcb99da838e12a002221c296f1901d82d9196900d2f89b47"
    ]
  ],
  "pubkey": "83279ad28eec4785e2139dc529a9650fdbb424366d4645e5c2824f7cbd49240d",
  "id": "3478cdb9c576e09605cf154105221333ac571071abf4c9630104936a9bd305e9",
  "sig": "fd4c35229e749fdf06120269963578f3806f2aad341f57840a6bf0a5edae39e5fd06d3816a4e050444d032e0062da89875868be1928e99cfb84ce7c62da123e0"
            }""";

    private static final String base64Encoded = Base64.getEncoder().encodeToString(eventJson.getBytes());
    private static final String TEST_AUTH_HEADER = "Nostr " + base64Encoded;

    private BlobMetadata testMetadata;
    private AuthResult validAuthResult;
    private AuthResult invalidAuthResult;

    @BeforeEach
    void setUp() {
        long currentTime = Instant.now().getEpochSecond();

        testMetadata = new BlobMetadata(
            TEST_HASH,
            TEST_CONTENT.length(),
            "text/plain",
            currentTime,
            TEST_PUBKEY
        );

        Map<String, String> validTags = new HashMap<>();
        validTags.put("t", "upload");
        validTags.put("expiration", String.valueOf(currentTime + 3600));

        validAuthResult = AuthResult.valid(
            TEST_PUBKEY,
            currentTime,
            currentTime + 3600,
            "upload",
            validTags
        );

        invalidAuthResult = AuthResult.invalid(
            "Invalid signature",
            AuthenticationException.AuthErrorType.INVALID_SIGNATURE
        );
    }

    @Test
    void testUploadMultipartFileSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenReturn(testMetadata);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").value(TEST_HASH))
                .andExpect(jsonPath("$.size").value(TEST_CONTENT.length()))
                .andExpect(jsonPath("$.type").value("text/plain"))
                .andExpect(jsonPath("$.url").value("http://localhost/" + TEST_HASH))
                .andExpect(jsonPath("$.uploaded").exists());

        verify(nostrAuthService).validateAuthEvent(TEST_AUTH_HEADER, "upload");
        verify(storageService).storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull());
    }

    @Test
    void testUploadRawBinarySuccess() throws Exception {
        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), anyString(), eq(TEST_PUBKEY), isNull()))
            .thenReturn(testMetadata);

        mockMvc.perform(put("/upload")
                        .header("Authorization", TEST_AUTH_HEADER)
                        .header("Content-Type", "application/octet-stream")
                        .content(TEST_CONTENT.getBytes()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").value(TEST_HASH))
                .andExpect(jsonPath("$.size").value(TEST_CONTENT.length()));

        verify(storageService).storeBlob(any(), anyString(), eq(TEST_PUBKEY), isNull());
    }

    @Test
    void testUploadWithExpectedSha256() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), eq(TEST_HASH)))
            .thenReturn(testMetadata);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER)
                        .header("X-SHA-256", TEST_HASH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").value(TEST_HASH));

        verify(storageService).storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), eq(TEST_HASH));
    }

    @Test
    void testUploadWithAuthHashTag() throws Exception {
        Map<String, String> tagsWithHash = new HashMap<>();
        tagsWithHash.put("t", "upload");
        tagsWithHash.put("x", TEST_HASH);
        tagsWithHash.put("expiration", String.valueOf(Instant.now().getEpochSecond() + 3600));

        AuthResult authWithHash = AuthResult.valid(
            TEST_PUBKEY,
            Instant.now().getEpochSecond(),
            Instant.now().getEpochSecond() + 3600,
            "upload",
            tagsWithHash
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(authWithHash);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenReturn(testMetadata);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isCreated());

        verify(storageService).storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull());
    }

    @Test
    void testUploadWithoutAuth() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(header().string("WWW-Authenticate", "Nostr"));

        verify(nostrAuthService, never()).validateAuthEvent(any(), any());
        verify(storageService, never()).storeBlob(any(), any(), any(), any());
    }

    @Test
    void testUploadWithInvalidAuth() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent("Nostr invalid", "upload"))
            .thenReturn(invalidAuthResult);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", "Nostr invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authErrorType").value("INVALID_SIGNATURE"));

        verify(storageService, never()).storeBlob(any(), any(), any(), any());
    }

    @Test
    void testUploadFileTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenThrow(new StorageException(
                StorageException.StorageErrorType.FILE_TOO_LARGE,
                null,
                "File size exceeds maximum allowed size"
            ));

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("Storage Error"));
    }

    @Test
    void testUploadHashMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), eq("wrong_hash")))
            .thenThrow(new StorageException(
                StorageException.StorageErrorType.HASH_MISMATCH,
                TEST_HASH,
                "SHA256 mismatch"
            ));

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER)
                        .header("X-SHA-256", "wrong_hash"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Storage Error"));
    }

    @Test
    void testUploadEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.txt",
            "text/plain",
            new byte[0]
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isBadRequest()) // File size <= 0 triggers INVALID_FILE
                .andExpect(jsonPath("$.error").value("Storage Error"))
                .andExpect(jsonPath("$.message").value("File is empty or size could not be determined"));
    }

    @Test
    void testUploadAuthHashMismatch() throws Exception {
        String wrongHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        Map<String, String> tagsWithWrongHash = new HashMap<>();
        tagsWithWrongHash.put("t", "upload");
        tagsWithWrongHash.put("x", wrongHash);
        tagsWithWrongHash.put("expiration", String.valueOf(Instant.now().getEpochSecond() + 3600));

        AuthResult authWithWrongHash = AuthResult.valid(
            TEST_PUBKEY,
            Instant.now().getEpochSecond(),
            Instant.now().getEpochSecond() + 3600,
            "upload",
            tagsWithWrongHash
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(authWithWrongHash);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenReturn(testMetadata);
        when(storageService.deleteBlob(TEST_HASH)).thenReturn(true);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authErrorType").value("HASH_MISMATCH"));

        verify(storageService).deleteBlob(TEST_HASH); // Cleanup after hash mismatch
    }

    @Test
    void testUploadDuplicateFile() throws Exception {
        // Test that existing files are returned without re-upload
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenReturn(testMetadata); // StorageService handles duplicate detection

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").value(TEST_HASH));
    }

    @Test
    void testUploadStorageError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenThrow(new StorageException(
                StorageException.StorageErrorType.STORAGE_ERROR,
                null,
                "Disk full"
            ));

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Storage Error"));
    }

    @Test
    void testUploadWithCustomContentType() throws Exception {
        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), anyString(), eq(TEST_PUBKEY), isNull()))
            .thenReturn(new BlobMetadata(TEST_HASH, TEST_CONTENT.length(), "image/jpeg",
                                       Instant.now().getEpochSecond(), TEST_PUBKEY));

        mockMvc.perform(put("/upload")
                        .header("Authorization", TEST_AUTH_HEADER)
                        .header("Content-Type", "image/jpeg")
                        .content(TEST_CONTENT.getBytes()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value(startsWith("image/jpeg")));

        verify(storageService).storeBlob(any(), anyString(), eq(TEST_PUBKEY), isNull());
    }

    @Test
    void testUploadUrlGeneration() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            TEST_CONTENT.getBytes()
        );

        when(nostrAuthService.validateAuthEvent(TEST_AUTH_HEADER, "upload"))
            .thenReturn(validAuthResult);
        when(storageService.storeBlob(any(), eq("text/plain"), eq(TEST_PUBKEY), isNull()))
            .thenReturn(testMetadata);

        mockMvc.perform(multipart(HttpMethod.PUT, "/upload")
                        .file(file)
                        .header("Authorization", TEST_AUTH_HEADER)
                        .header("Host", "example.com:8080"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("http://example.com:8080/" + TEST_HASH));
    }
}
