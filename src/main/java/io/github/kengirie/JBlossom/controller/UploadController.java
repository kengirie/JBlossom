package io.github.kengirie.JBlossom.controller;

import io.github.kengirie.JBlossom.exception.AuthenticationException;
import io.github.kengirie.JBlossom.exception.StorageException;
import io.github.kengirie.JBlossom.model.AuthResult;
import io.github.kengirie.JBlossom.model.BlobDescriptor;
import io.github.kengirie.JBlossom.model.BlobMetadata;
import io.github.kengirie.JBlossom.service.NostrAuthService;
import io.github.kengirie.JBlossom.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

@RestController
@CrossOrigin
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    @Autowired
    private StorageService storageService;

    @Autowired
    private NostrAuthService nostrAuthService;

    @Value("${blossom.upload.max-file-size:104857600}") // 100MB default
    private long maxFileSize;

    @Value("${blossom.upload.require-auth:true}")
    private boolean requireAuth;

    @PutMapping("/upload")
    public ResponseEntity<BlobDescriptor> uploadBlob(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-SHA-256", required = false) String expectedSha256,
            HttpServletRequest request) throws IOException {

        logger.debug("Upload request received - Content-Type: {}, Auth: {}, Expected SHA256: {}", 
                    contentType, authHeader != null ? "present" : "none", expectedSha256);

        // 認証チェック
        AuthResult authResult = null;
        if (requireAuth || authHeader != null) {
            if (authHeader == null || !authHeader.startsWith("Nostr ")) {
                throw new AuthenticationException(
                    AuthenticationException.AuthErrorType.MISSING_AUTH,
                    "Authorization header required for upload"
                );
            }

            authResult = nostrAuthService.validateAuthEvent(authHeader, "upload");
            
            if (!authResult.isValid()) {
                throw new AuthenticationException(
                    authResult.getErrorType(),
                    authResult.getReason()
                );
            }
            
            logger.debug("Authentication successful for pubkey: {}", authResult.getPubkey());
        }

        // ファイルデータの取得
        InputStream inputStream;
        long fileSize;
        String detectedContentType;

        if (file != null && !file.isEmpty()) {
            // MultipartFile からの取得
            inputStream = file.getInputStream();
            fileSize = file.getSize();
            detectedContentType = file.getContentType();
        } else {
            // Raw binary data からの取得
            inputStream = request.getInputStream();
            fileSize = request.getContentLengthLong();
            detectedContentType = contentType;
        }

        // ファイルサイズチェック
        if (fileSize > maxFileSize) {
            throw new StorageException(
                StorageException.StorageErrorType.FILE_TOO_LARGE,
                null,
                String.format("File size %d exceeds maximum allowed size %d", fileSize, maxFileSize)
            );
        }

        if (fileSize <= 0) {
            throw new StorageException(
                StorageException.StorageErrorType.INVALID_FILE,
                null,
                "File is empty or size could not be determined"
            );
        }

        // SHA256計算とファイル保存
        String calculatedSha256;
        try {
            BlobMetadata metadata = storageService.storeBlob(
                inputStream,
                detectedContentType,
                authResult != null ? authResult.getPubkey() : null,
                expectedSha256
            );
            calculatedSha256 = metadata.getHash();
            
            logger.debug("Blob stored successfully: {}", calculatedSha256);
            
        } catch (StorageException e) {
            logger.error("Failed to store blob", e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during blob storage", e);
            throw new StorageException(
                StorageException.StorageErrorType.STORAGE_ERROR,
                null,
                "Failed to store blob: " + e.getMessage()
            );
        }

        // 期待されたSHA256との整合性チェック
        if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(calculatedSha256)) {
            // 保存したファイルを削除
            storageService.deleteBlob(calculatedSha256);
            throw new StorageException(
                StorageException.StorageErrorType.HASH_MISMATCH,
                calculatedSha256,
                String.format("SHA256 mismatch: expected %s, calculated %s", expectedSha256, calculatedSha256)
            );
        }

        // 認証イベントのxタグとSHA256の整合性チェック
        if (authResult != null && authResult.hasTag("x")) {
            String authSha256 = authResult.getTagValue("x");
            if (!calculatedSha256.equalsIgnoreCase(authSha256)) {
                // 保存したファイルを削除
                storageService.deleteBlob(calculatedSha256);
                throw new AuthenticationException(
                    AuthenticationException.AuthErrorType.HASH_MISMATCH,
                    String.format("Auth event x tag mismatch: expected %s, calculated %s", 
                                authSha256, calculatedSha256)
                );
            }
        }

        // BlobDescriptorを作成
        String baseUrl = getBaseUrl(request);
        BlobDescriptor descriptor = createBlobDescriptor(
            calculatedSha256,
            fileSize,
            detectedContentType,
            baseUrl
        );

        logger.info("Blob upload completed: {} ({} bytes)", calculatedSha256, fileSize);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(descriptor);
    }

    private BlobDescriptor createBlobDescriptor(String sha256, long size, String contentType, String baseUrl) {
        BlobDescriptor descriptor = new BlobDescriptor();
        descriptor.setUrl(baseUrl + "/" + sha256);
        descriptor.setSha256(sha256);
        descriptor.setSize(size);
        descriptor.setType(contentType);
        descriptor.setUploaded(Instant.now().getEpochSecond());
        return descriptor;
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        return url.toString();
    }

}