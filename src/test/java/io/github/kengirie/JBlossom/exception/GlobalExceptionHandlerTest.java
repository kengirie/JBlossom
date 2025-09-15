package io.github.kengirie.JBlossom.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.kengirie.JBlossom.service.StorageService;
import io.github.kengirie.JBlossom.service.NostrAuthService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
public class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @MockBean
    private NostrAuthService nostrAuthService;

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/range-error")
        public void testRangeError() {
            throw new RangeNotSatisfiableException(
                "Requested range exceeds file size",
                "bytes=2000-3000",
                1000L
            );
        }

        @GetMapping("/auth-expired")
        public void testAuthExpired() {
            throw new AuthenticationException(
                AuthenticationException.AuthErrorType.EVENT_EXPIRED,
                "Auth event expired at timestamp 1234567890"
            );
        }

        @GetMapping("/auth-invalid-signature")
        public void testAuthInvalidSignature() {
            throw new AuthenticationException(
                AuthenticationException.AuthErrorType.INVALID_SIGNATURE
            );
        }

        @GetMapping("/storage-not-found")
        public void testStorageNotFound() {
            throw new StorageException(
                StorageException.StorageErrorType.BLOB_NOT_FOUND,
                "abc123def456",
                "Blob not found in storage"
            );
        }

        @GetMapping("/storage-corrupted")
        public void testStorageCorrupted() {
            throw new StorageException(
                StorageException.StorageErrorType.FILE_CORRUPTED,
                "abc123def456",
                "File checksum mismatch"
            );
        }

        @GetMapping("/blob-not-found")
        public void testBlobNotFound() {
            throw new BlobNotFoundException("Blob with hash abc123 not found");
        }

        @GetMapping("/illegal-argument")
        public void testIllegalArgument() {
            throw new IllegalArgumentException("Invalid SHA256 hash format");
        }

        @GetMapping("/runtime-exception")
        public void testRuntimeException() {
            throw new RuntimeException("Unexpected runtime error");
        }

        @GetMapping("/general-exception")
        public void testGeneralException() throws Exception {
            throw new Exception("General checked exception");
        }
    }

    @Test
    void testRangeNotSatisfiableException() throws Exception {
        mockMvc.perform(get("/test/range-error"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("X-Reason", "Requested range exceeds file size"))
                .andExpect(header().string("Content-Range", "bytes */1000"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Reason, Content-Range"))
                .andExpect(jsonPath("$.error").value("Range Not Satisfiable"))
                .andExpect(jsonPath("$.message").value("Requested range exceeds file size"))
                .andExpect(jsonPath("$.requestedRange").value("bytes=2000-3000"))
                .andExpect(jsonPath("$.fileSize").value("1000"));
    }

    @Test
    void testAuthenticationExceptionExpired() throws Exception {
        mockMvc.perform(get("/test/auth-expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Reason", "Auth event expired at timestamp 1234567890"))
                .andExpect(header().string("WWW-Authenticate", "Nostr"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Reason, WWW-Authenticate"))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Auth event expired at timestamp 1234567890"))
                .andExpect(jsonPath("$.authErrorType").value("EVENT_EXPIRED"));
    }

    @Test
    void testAuthenticationExceptionInvalidSignature() throws Exception {
        mockMvc.perform(get("/test/auth-invalid-signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Reason", "Invalid Nostr event signature"))
                .andExpect(header().string("WWW-Authenticate", "Nostr"))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.authErrorType").value("INVALID_SIGNATURE"));
    }

    @Test
    void testStorageExceptionNotFound() throws Exception {
        mockMvc.perform(get("/test/storage-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Reason", "Blob not found in storage (hash: abc123def456)"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.error").value("Storage Error"))
                .andExpect(jsonPath("$.message").value("Blob not found in storage"));
    }

    @Test
    void testStorageExceptionCorrupted() throws Exception {
        mockMvc.perform(get("/test/storage-corrupted"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Reason", "File checksum mismatch (hash: abc123def456)"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.error").value("Storage Error"))
                .andExpect(jsonPath("$.message").value("File checksum mismatch"));
    }

    @Test
    void testBlobNotFoundException() throws Exception {
        mockMvc.perform(get("/test/blob-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Reason", "Blob not found: Blob with hash abc123 not found"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.error").value("Blob not found"))
                .andExpect(jsonPath("$.message").value("Blob not found: Blob with hash abc123 not found"));
    }

    @Test
    void testIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Reason", "Invalid request parameters: Invalid SHA256 hash format"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid SHA256 hash format"));
    }

    @Test
    void testRuntimeException() throws Exception {
        mockMvc.perform(get("/test/runtime-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Reason", "Runtime exception: RuntimeException"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void testGeneralException() throws Exception {
        mockMvc.perform(get("/test/general-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Reason", "Unexpected error: Exception"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void testCorsHeadersPresent() throws Exception {
        // Test that CORS headers are present in all error responses
        mockMvc.perform(get("/test/auth-expired")
                        .header("Origin", "https://example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().exists("Access-Control-Expose-Headers"));
    }

    @Test
    void testXReasonHeaderExposure() throws Exception {
        // Test that X-Reason header is properly exposed for CORS
        mockMvc.perform(get("/test/range-error")
                        .header("Origin", "https://example.com"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Reason, Content-Range"));
    }

    @Test
    void testErrorResponseStructure() throws Exception {
        // Test that all error responses have consistent structure
        mockMvc.perform(get("/test/auth-expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
