package io.github.kengirie.JBlossom.service;

import io.github.kengirie.JBlossom.model.BlobContent;
import io.github.kengirie.JBlossom.model.BlobMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class StorageServiceTest {

    private StorageService storageService;
    private Path tempDir;
    private Path tempDbPath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException, SQLException {
        this.tempDir = tempDir;
        this.tempDbPath = tempDir.resolve("test.db");

        storageService = new StorageService();
        ReflectionTestUtils.setField(storageService, "storagePath", tempDir.toString());
        ReflectionTestUtils.setField(storageService, "databasePath", tempDbPath.toString());

        // テスト用データベース初期化
        initTestDatabase();
    }

    private void initTestDatabase() throws SQLException {
        // SQLiteドライバーの明示的な読み込み
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        String url = "jdbc:sqlite:" + tempDbPath.toString();
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(true);

            // blobsテーブル作成
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS blobs (
                    hash TEXT(64) PRIMARY KEY,
                    size INTEGER NOT NULL,
                    type TEXT,
                    uploaded INTEGER NOT NULL,
                    pubkey TEXT(64)
                )
            """);

            // accessedテーブル作成
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS accessed (
                    blob TEXT(64) PRIMARY KEY,
                    timestamp INTEGER NOT NULL
                )
            """);

            // インデックス作成
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_blobs_pubkey ON blobs (pubkey)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_blobs_uploaded ON blobs (uploaded)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_accessed_timestamp ON accessed (timestamp)");
        }
    }

    @Test
    void testFindBlobNotFound() {
        String hash = "1827b95e971ac79f6b79242512d74c010166603c2bd1958679cb5da14f3b11c3";
        Optional<BlobMetadata> result = storageService.findBlob(hash);
        assertFalse(result.isPresent());
    }

    @Test
    void testFindBlobInvalidHash() {
        String invalidHash = "invalid-hash";
        Optional<BlobMetadata> result = storageService.findBlob(invalidHash);
        assertFalse(result.isPresent());
    }

    @Test
    void testFindBlobExistsInDbButFileNotFound() throws SQLException {
        String hash = "1827b95e971ac79f6b79242512d74c010166603c2bd1958679cb5da14f3b11c3";

        // DBにメタデータを追加（ファイルは作成しない）
        String url = "jdbc:sqlite:" + tempDbPath.toString();
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(true);
            String sql = "INSERT INTO blobs (hash, size, type, uploaded, pubkey) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hash);
                stmt.setLong(2, 1024);
                stmt.setString(3, "text/plain");
                stmt.setLong(4, System.currentTimeMillis() / 1000);
                stmt.setString(5, "test-pubkey");
                stmt.executeUpdate();
            }
        }

        Optional<BlobMetadata> result = storageService.findBlob(hash);
        assertFalse(result.isPresent()); // ファイルが存在しないので false
    }

    @Test
    void testFindBlobSuccess() throws IOException, SQLException {
        String hash = "1827b95e971ac79f6b79242512d74c010166603c2bd1958679cb5da14f3b11c3";
        String content = "Test blob content";

        // テストファイル作成
        Path filePath = tempDir.resolve(hash);
        Files.write(filePath, content.getBytes());


        // DBにメタデータを追加
        String url = "jdbc:sqlite:" + tempDbPath.toString();
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(true);
            String sql = "INSERT INTO blobs (hash, size, type, uploaded, pubkey) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hash);
                stmt.setLong(2, content.length());
                stmt.setString(3, "text/plain");
                stmt.setLong(4, System.currentTimeMillis() / 1000);
                stmt.setString(5, "test-pubkey");
                stmt.executeUpdate();
            }
        }

        Optional<BlobMetadata> result = storageService.findBlob(hash);
        assertTrue(result.isPresent());

        BlobMetadata metadata = result.get();
        assertEquals(hash, metadata.getHash());
        assertEquals(content.length(), metadata.getSize());
        assertEquals("text/plain", metadata.getType());
        assertEquals("test-pubkey", metadata.getPubkey());
    }

    @Test
    void testReadBlobWithoutRange() throws IOException, SQLException {
        String hash = "d8346875f65e726689b5b4a4823714333aaf82127007ab6d926b18a2256503fb";
        String content = "Test blob content for reading";

        // テストファイルとメタデータ作成
        createTestBlob(hash, content, "text/plain");

        Optional<BlobContent> result = storageService.readBlob(hash, null);
        assertTrue(result.isPresent());

        BlobContent blobContent = result.get();
        assertEquals(hash, blobContent.getHash());
        assertEquals(content.length(), blobContent.getSize());
        assertEquals(content.length(), blobContent.getContentLength());
        assertEquals("text/plain", blobContent.getMimeType());
        assertFalse(blobContent.isRangeRequest());
        assertNull(blobContent.getContentRange());
    }

    @Test
    void testReadBlobWithValidRange() throws IOException, SQLException {
        String hash = "74e7e5bb9d22d6db26bf76946d40fff3ea9f0346b884fd0694920fccfad15e33";
        String content = "0123456789abcdefghijklmnopqrstuvwxyz"; // 36 bytes

        createTestBlob(hash, content, "text/plain");

        // Range: bytes=5-14 (10 bytes)
        Optional<BlobContent> result = storageService.readBlob(hash, "bytes=5-14");
        assertTrue(result.isPresent());

        BlobContent blobContent = result.get();
        assertTrue(blobContent.isRangeRequest());
        assertEquals(5, blobContent.getRangeStart());
        assertEquals(14, blobContent.getRangeEnd());
        assertEquals(10, blobContent.getContentLength());
        assertEquals("bytes 5-14/36", blobContent.getContentRange());
    }

    @Test
    void testReadBlobWithInvalidRange() throws IOException, SQLException {
        String hash = "9c6e572ed97ab2cfe7c1362bef165bb98b566452c3e15a444dc074ff319e9ba0";
        String content = "Short content"; // 13 bytes

        createTestBlob(hash, content, "text/plain");

        // Range: bytes=20-30 (ファイルサイズを超過)
        Optional<BlobContent> result = storageService.readBlob(hash, "bytes=20-30");
        assertTrue(result.isPresent());

        BlobContent blobContent = result.get();
        assertFalse(blobContent.isRangeRequest()); // 無効なレンジは無視される
        assertEquals(content.length(), blobContent.getContentLength());
    }

    @Test
    void testDetectMimeTypeFromStoredType() {
        String hash = "test-hash";
        String result = storageService.detectMimeType(hash, "image/png", "txt");
        assertEquals("image/png", result); // 保存されているタイプを優先
    }

    @Test
    void testDetectMimeTypeFromExtension() {
        String hash = "test-hash";
        String result = storageService.detectMimeType(hash, null, "png");
        assertEquals("image/png", result);
    }

    @Test
    void testDetectMimeTypeDefault() {
        String hash = "test-hash";
        String result = storageService.detectMimeType(hash, null, "unknown");
        assertEquals("application/octet-stream", result);
    }

    @Test
    void testUpdateAccessTime() throws SQLException, InterruptedException {
        String hash = "ee8c86b6c92696e35fbe5fb95d69fb6121d4e361d13633f725be2bb76137f882";

        // アクセス時刻更新
        storageService.updateAccessTime(hash);

        // データベースから確認
        String url = "jdbc:sqlite:" + tempDbPath.toString();
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(true);
            String sql = "SELECT timestamp FROM accessed WHERE blob = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hash);
                var rs = stmt.executeQuery();
                assertTrue(rs.next());

                long timestamp = rs.getLong("timestamp");
                long now = System.currentTimeMillis() / 1000;
                assertTrue(Math.abs(now - timestamp) < 5); // 5秒以内
            }
        }
    }

    @Test
    void testHasBlob() throws IOException, SQLException {
        String hash = "9d9595c5d94fb65b824f56e9999527dba9542481580d69feb89056aabaa0aa87";
        String content = "Test content";

        assertFalse(storageService.hasBlob(hash));

        createTestBlob(hash, content, "text/plain");

        assertTrue(storageService.hasBlob(hash));
    }

    @Test
    void testGetBlobSize() throws IOException, SQLException {
        String hash = "ee8c86b6c92696e35fbe5fb95d69fb6121d4e361d13633f725be2bb76137f882";
        String content = "Test content for size check";

        assertEquals(0L, storageService.getBlobSize(hash));

        createTestBlob(hash, content, "text/plain");

        assertEquals(content.length(), storageService.getBlobSize(hash));
    }

    @Test
    void testGetStorageStats() throws IOException, SQLException {
        // 初期状態
        var stats = storageService.getStorageStats();
        assertEquals(0, stats.getBlobCount());
        assertEquals(0, stats.getTotalSize());

        // テストBlob追加
        createTestBlob("stats1234567890123456789012345678901234567890123456789012345678",
                     "Content 1", "text/plain");
        createTestBlob("stats2345678901234567890123456789012345678901234567890123456789",
                     "Content 2 longer", "text/plain");

        stats = storageService.getStorageStats();
        assertEquals(2, stats.getBlobCount());
        assertEquals("Content 1".length() + "Content 2 longer".length(), stats.getTotalSize());
    }

    private void createTestBlob(String hash, String content, String mimeType) throws IOException, SQLException {
        // ファイル作成
        Path filePath = tempDir.resolve(hash);
        Files.write(filePath, content.getBytes());

        // DB登録
        String url = "jdbc:sqlite:" + tempDbPath.toString();
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(true);
            String sql = "INSERT INTO blobs (hash, size, type, uploaded, pubkey) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hash);
                stmt.setLong(2, content.length());
                stmt.setString(3, mimeType);
                stmt.setLong(4, System.currentTimeMillis() / 1000);
                stmt.setString(5, "test-pubkey");
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new SQLException("Failed to insert blob: " + hash);
                }
            }
        }
    }
}
