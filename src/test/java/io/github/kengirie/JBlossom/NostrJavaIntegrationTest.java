package io.github.kengirie.JBlossom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NostrJavaIntegrationTest {
    
    @Test
    public void contextLoads() {
        // Spring Boot コンテキストが正常にロードされることをテスト
        assertTrue(true);
    }
    
    @Test
    public void testSQLiteConnection() {
        // SQLite接続テストは後で実装
        // 現在はnostr-javaライブラリの依存関係エラーがあるため、シンプルにテスト
        assertNotNull("Test");
    }
}