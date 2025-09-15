package io.github.kengirie.JBlossom.controller;

import io.github.kengirie.JBlossom.model.AuthResult;
import io.github.kengirie.JBlossom.model.BlobContent;
import io.github.kengirie.JBlossom.model.BlobMetadata;
import io.github.kengirie.JBlossom.service.NostrAuthService;
import io.github.kengirie.JBlossom.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@CrossOrigin
public class BlobController {

    private static final Logger logger = LoggerFactory.getLogger(BlobController.class);
    private static final Pattern SHA256_PATTERN = Pattern.compile("([0-9a-f]{64})");

    @Autowired
    private StorageService storageService;

    @Autowired
    private NostrAuthService nostrAuthService;

    @GetMapping("/{pathWithPossibleExtension:.*}")
    public ResponseEntity<Resource> getBlob(
            @PathVariable String pathWithPossibleExtension,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {

        return handleBlobRequest(pathWithPossibleExtension, rangeHeader, authHeader, request, false);
    }

    @RequestMapping(value = "/{pathWithPossibleExtension:.*}", method = RequestMethod.HEAD)
    public ResponseEntity<Resource> headBlob(
            @PathVariable String pathWithPossibleExtension,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {

        return handleBlobRequest(pathWithPossibleExtension, rangeHeader, authHeader, request, true);
    }

    private ResponseEntity<Resource> handleBlobRequest(
            String pathWithPossibleExtension,
            String rangeHeader,
            String authHeader,
            HttpServletRequest request,
            boolean isHeadRequest) {

        // Extract SHA256 hash using regex (supports both /{hash} and /{hash}.{ext})
        String sha256Hash = extractSha256Hash(pathWithPossibleExtension);
        if (sha256Hash == null) {
            logger.debug("Invalid SHA256 hash format: {}", pathWithPossibleExtension);
            return ResponseEntity.notFound().build();
        }

        // Extract extension if present
        String extension = extractExtension(pathWithPossibleExtension);

        logger.debug("Serving blob request for hash: {}, extension: {}, range: {}", 
                    sha256Hash, extension, rangeHeader);

        // Optional authentication (non-blocking)
        AuthResult authResult = null;
        if (authHeader != null && authHeader.startsWith("Nostr ")) {
            authResult = nostrAuthService.validateAuthEvent(authHeader, "get");
            if (!authResult.isValid()) {
                logger.debug("Authentication failed for hash {}: {}", sha256Hash, authResult.getReason());
                // Note: Authentication failure is non-blocking for GET requests in BUD01
            } else {
                logger.debug("Authentication successful for hash {} by pubkey: {}", sha256Hash, authResult.getPubkey());
            }
        }

        // Check if blob exists
        Optional<BlobMetadata> metadataOpt = storageService.findBlob(sha256Hash);
        if (metadataOpt.isEmpty()) {
            logger.debug("Blob not found: {}", sha256Hash);
            return ResponseEntity.notFound().build();
        }

        // Update access time
        storageService.updateAccessTime(sha256Hash);

        // Read blob content with range support
        Optional<BlobContent> blobContentOpt = storageService.readBlob(sha256Hash, rangeHeader);
        if (blobContentOpt.isEmpty()) {
            logger.warn("Failed to read blob content for hash: {}", sha256Hash);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        BlobContent blobContent = blobContentOpt.get();
        BlobMetadata metadata = metadataOpt.get();

        // Determine MIME type (prefer extension if provided)
        String mimeType = storageService.detectMimeType(sha256Hash, metadata.getType(), extension);
        
        // Build response headers
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, mimeType);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");

        // Handle range requests
        if (blobContent.isRangeRequest()) {
            headers.set(HttpHeaders.CONTENT_RANGE, blobContent.getContentRange());
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setContentLength(blobContent.getContentLength());
            
            logger.debug("Serving range request for {}: {} bytes", sha256Hash, blobContent.getContentLength());
            
            Resource resource = isHeadRequest ? null : blobContent.getResource();
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .body(resource);
        } else {
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setContentLength(blobContent.getSize());
            
            logger.debug("Serving full blob for {}: {} bytes", sha256Hash, blobContent.getSize());
            
            Resource resource = isHeadRequest ? null : blobContent.getResource();
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        }
    }

    private String extractSha256Hash(String path) {
        if (path == null) {
            return null;
        }
        
        Matcher matcher = SHA256_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    private String extractExtension(String path) {
        if (path == null) {
            return null;
        }
        
        // Check if path has an extension after the hash
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < path.length() - 1) {
            String potentialExtension = path.substring(dotIndex + 1);
            // Only return as extension if it comes after a valid hash
            String hash = extractSha256Hash(path);
            if (hash != null && path.startsWith(hash)) {
                return potentialExtension;
            }
        }
        
        return null;
    }
}