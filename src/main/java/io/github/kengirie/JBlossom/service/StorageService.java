package io.github.kengirie.JBlossom.service;

import io.github.kengirie.JBlossom.model.BlobContent;
import io.github.kengirie.JBlossom.model.BlobMetadata;
import io.github.kengirie.JBlossom.util.RangeRequestParser;
import io.github.kengirie.JBlossom.util.RangeRequestParser.Range;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Service
public class StorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageService.class);

    @Value("${blossom.storage.path}")
    private String storagePath;

    @Value("${blossom.database.path}")
    private String databasePath;

    private final Tika tika;

    public StorageService() {
        this.tika = new Tika();
    }

    public Optional<BlobMetadata> findBlob(String sha256) {
        if (!isValidSha256(sha256)) {
            return Optional.empty();
        }

        try (Connection conn = getConnection()) {
            String sql = "SELECT hash, size, type, uploaded, pubkey FROM blobs WHERE hash = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sha256);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        BlobMetadata metadata = new BlobMetadata(
                            rs.getString("hash"),
                            rs.getLong("size"),
                            rs.getString("type"),
                            rs.getLong("uploaded"),
                            rs.getString("pubkey")
                        );

                        // ファイルの物理的存在確認
                        Path filePath = getFilePath(sha256);
                        if (Files.exists(filePath) && Files.isReadable(filePath)) {
                            return Optional.of(metadata);
                        } else {
                            logger.warn("Blob metadata exists but file not found: {}", sha256);
                            return Optional.empty();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error while finding blob: {}", sha256, e);
        }

        return Optional.empty();
    }

    public Optional<BlobContent> readBlob(String sha256, String rangeHeader) {
        Optional<BlobMetadata> metadataOpt = findBlob(sha256);
        if (metadataOpt.isEmpty()) {
            return Optional.empty();
        }

        BlobMetadata metadata = metadataOpt.get();
        Path filePath = getFilePath(sha256);

        try {
            Resource resource = new FileSystemResource(filePath);
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("File not readable: {}", filePath);
                return Optional.empty();
            }

            String mimeType = detectMimeType(sha256, metadata.getType(), filePath);
            BlobContent.Builder builder = BlobContent.builder()
                .hash(sha256)
                .resource(resource)
                .mimeType(mimeType)
                .size(metadata.getSize());

            // Range Request処理
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                Range range = RangeRequestParser.parseRange(rangeHeader, metadata.getSize());
                if (range != null && RangeRequestParser.isValidRange(range, metadata.getSize())) {
                    builder.range(range.getStart(), range.getEnd());
                    logger.debug("Range request for {}: {}", sha256, range);
                } else {
                    logger.debug("Invalid range request for {}: {}", sha256, rangeHeader);
                }
            }

            return Optional.of(builder.build());

        } catch (Exception e) {
            logger.error("Error reading blob: {}", sha256, e);
            return Optional.empty();
        }
    }

    public String detectMimeType(String sha256, String storedType, String extension) {
        // 1. DBに保存されているタイプを優先
        if (storedType != null && !storedType.isBlank() && !storedType.equals("application/octet-stream")) {
            return storedType;
        }

        // 2. 拡張子から推測
        if (extension != null && !extension.isBlank()) {
            try {
                String detected = tika.detect("." + extension.toLowerCase());
                if (detected != null && !detected.equals("application/octet-stream")) {
                    logger.debug("MIME type detected from extension for {}: {}", sha256, detected);
                    return detected;
                }
            } catch (Exception e) {
                logger.debug("Failed to detect MIME type from extension: {}", extension, e);
            }
        }

        // 3. ファイル内容から推測
        Path filePath = getFilePath(sha256);
        if (Files.exists(filePath)) {
            try {
                String detected = tika.detect(filePath.toFile());
                if (detected != null && !detected.equals("application/octet-stream")) {
                    logger.debug("MIME type detected from file content for {}: {}", sha256, detected);
                    return detected;
                }
            } catch (Exception e) {
                logger.debug("Failed to detect MIME type from file content for {}: {}", sha256, e);
            }
        }

        // 4. デフォルト
        return "application/octet-stream";
    }

    private String detectMimeType(String sha256, String storedType, Path filePath) {
        // 1. DBに保存されているタイプを優先
        if (storedType != null && !storedType.isBlank() && !storedType.equals("application/octet-stream")) {
            return storedType;
        }

        // 2. ファイル内容から推測
        try {
            String detected = tika.detect(filePath.toFile());
            if (detected != null && !detected.equals("application/octet-stream")) {
                logger.debug("MIME type detected from file content for {}: {}", sha256, detected);
                return detected;
            }
        } catch (Exception e) {
            logger.debug("Failed to detect MIME type from file content for {}: {}", sha256, e);
        }

        // 3. デフォルト
        return "application/octet-stream";
    }

    public void updateAccessTime(String sha256) {
        if (!isValidSha256(sha256)) {
            return;
        }

        long timestamp = Instant.now().getEpochSecond();

        try (Connection conn = getConnection()) {
            String sql = "INSERT OR REPLACE INTO accessed (blob, timestamp) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sha256);
                stmt.setLong(2, timestamp);
                stmt.executeUpdate();

                logger.debug("Updated access time for blob: {}", sha256);
            }
        } catch (SQLException e) {
            logger.error("Failed to update access time for blob: {}", sha256, e);
        }
    }

    public boolean hasBlob(String sha256) {
        return findBlob(sha256).isPresent();
    }

    public long getBlobSize(String sha256) {
        return findBlob(sha256)
            .map(BlobMetadata::getSize)
            .orElse(0L);
    }

    private Path getFilePath(String sha256) {
        return Paths.get(storagePath, sha256);
    }

    private Connection getConnection() throws SQLException {
        // SQLiteドライバーの明示的な読み込み
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        String url = "jdbc:sqlite:" + databasePath;
        Connection conn = DriverManager.getConnection(url);
        conn.setAutoCommit(true);
        return conn;
    }

    private boolean isValidSha256(String hash) {
        return hash != null &&
               hash.matches("^[a-fA-F0-9]{64}$");
    }

    // ストレージ統計情報
    public StorageStats getStorageStats() {
        try (Connection conn = getConnection()) {
            String sql = "SELECT COUNT(*) as count, SUM(size) as total_size FROM blobs";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return new StorageStats(
                        rs.getLong("count"),
                        rs.getLong("total_size")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get storage stats", e);
        }

        return new StorageStats(0, 0);
    }

    public static class StorageStats {
        private final long blobCount;
        private final long totalSize;

        public StorageStats(long blobCount, long totalSize) {
            this.blobCount = blobCount;
            this.totalSize = totalSize;
        }

        public long getBlobCount() {
            return blobCount;
        }

        public long getTotalSize() {
            return totalSize;
        }

        @Override
        public String toString() {
            return String.format("StorageStats{count=%d, totalSize=%d bytes}", blobCount, totalSize);
        }
    }
}
