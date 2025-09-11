package io.github.kengirie.JBlossom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Configuration
public class SQLiteConfig {
    
    @Value("${blossom.database.path}")
    private String databasePath;
    
    @PostConstruct
    public void initializeDatabase() throws SQLException, IOException {
        // データディレクトリを作成
        File dbFile = new File(databasePath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // 一時的なコネクション作成でテーブル初期化
        String url = "jdbc:sqlite:" + databasePath + "?journal_mode=DELETE";
        try (Connection conn = DriverManager.getConnection(url)) {
            // 自動コミットを確実にする
            conn.setAutoCommit(true);
            
            // blobsテーブル作成
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS blobs (
                        hash TEXT(64) PRIMARY KEY,
                        size INTEGER NOT NULL,
                        type TEXT,
                        uploaded INTEGER NOT NULL,
                        pubkey TEXT(64)
                    )
                """);
            }
            
            // accessedテーブル作成（アクセスログ）
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS accessed (
                        blob TEXT(64) PRIMARY KEY,
                        timestamp INTEGER NOT NULL
                    )
                """);
            }
            
            // インデックス作成
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_blobs_pubkey ON blobs (pubkey)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_blobs_uploaded ON blobs (uploaded)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_accessed_timestamp ON accessed (timestamp)");
            }
        }
    }
    
    @Bean
    public String databasePath() {
        return databasePath;
    }
}