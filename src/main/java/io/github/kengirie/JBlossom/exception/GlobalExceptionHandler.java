package io.github.kengirie.JBlossom.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // Helper method to create BUD01 compliant error response with X-Reason header
    private ResponseEntity<Map<String, String>> createErrorResponse(
            HttpStatus status, String error, String message, String xReason) {
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        
        HttpHeaders headers = new HttpHeaders();
        if (xReason != null && !xReason.isBlank()) {
            headers.set("X-Reason", xReason);
        }
        
        // BUD01 CORS compliance
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Expose-Headers", "X-Reason");
        
        return ResponseEntity.status(status).headers(headers).body(errorResponse);
    }
    
    // BUD01 Range Request Error Handling
    @ExceptionHandler(RangeNotSatisfiableException.class)
    public ResponseEntity<Map<String, String>> handleRangeNotSatisfiable(
            RangeNotSatisfiableException ex, WebRequest request) {
        
        logger.warn("Range not satisfiable: {} for range: {}, file size: {}", 
                   ex.getMessage(), ex.getRequestedRange(), ex.getFileSize());
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Reason", ex.getMessage());
        headers.set("Content-Range", ex.getContentRangeHeader());
        headers.set("Accept-Ranges", "bytes");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Expose-Headers", "X-Reason, Content-Range");
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Range Not Satisfiable");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("requestedRange", ex.getRequestedRange());
        errorResponse.put("fileSize", String.valueOf(ex.getFileSize()));
        
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .headers(headers)
                .body(errorResponse);
    }
    
    // BUD01 Authentication Error Handling
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthentication(
            AuthenticationException ex, WebRequest request) {
        
        logger.warn("Authentication failed: {} (type: {})", ex.getMessage(), ex.getErrorType());
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Reason", ex.getXReasonHeader());
        headers.set("WWW-Authenticate", "Nostr");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Expose-Headers", "X-Reason, WWW-Authenticate");
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("authErrorType", ex.getErrorType().name());
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .headers(headers)
                .body(errorResponse);
    }
    
    // BUD01 Storage Error Handling
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorage(
            StorageException ex, WebRequest request) {
        
        logger.error("Storage error: {} (type: {}, hash: {})", 
                    ex.getMessage(), ex.getErrorType(), ex.getSha256Hash());
        
        HttpStatus status = switch (ex.getErrorType()) {
            case BLOB_NOT_FOUND, INVALID_HASH_FORMAT -> HttpStatus.NOT_FOUND;
            case FILE_CORRUPTED, HASH_MISMATCH -> HttpStatus.UNPROCESSABLE_ENTITY;
            case STORAGE_UNAVAILABLE, DATABASE_ERROR, FILE_READ_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        
        return createErrorResponse(status, "Storage Error", ex.getMessage(), ex.getXReasonHeader());
    }
    
    @ExceptionHandler(BlobNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBlobNotFoundException(
            BlobNotFoundException ex, WebRequest request) {
        
        logger.debug("Blob not found: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.NOT_FOUND, "Blob not found", ex.getMessage(), ex.getMessage());
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedException(
            UnauthorizedException ex, WebRequest request) {
        
        logger.warn("Legacy unauthorized exception: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), ex.getMessage());
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        logger.debug("Bad request: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), 
                                 "Invalid request parameters: " + ex.getMessage());
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        logger.error("Unexpected runtime exception", ex);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", 
                                 "An unexpected error occurred", "Runtime exception: " + ex.getClass().getSimpleName());
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGlobalException(
            Exception ex, WebRequest request) {
        
        logger.error("Unexpected exception", ex);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", 
                                 "An unexpected error occurred", "Unexpected error: " + ex.getClass().getSimpleName());
    }
}