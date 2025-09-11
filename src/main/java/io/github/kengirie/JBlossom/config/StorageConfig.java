package io.github.kengirie.JBlossom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {
    
    @Value("${blossom.storage.path}")
    private String storagePath;
    
    @Value("${blossom.storage.max-file-size:100MB}")
    private String maxFileSize;
    
    @PostConstruct
    public void initializeStorage() throws IOException {
        Path blobDirectory = Paths.get(storagePath);
        
        // ストレージディレクトリを作成
        if (!Files.exists(blobDirectory)) {
            Files.createDirectories(blobDirectory);
        }
        
        // 書き込み権限をチェック
        if (!Files.isWritable(blobDirectory)) {
            throw new IOException("Storage directory is not writable: " + storagePath);
        }
    }
    
    @Bean
    public String storagePath() {
        return storagePath;
    }
    
    @Bean
    public String maxFileSize() {
        return maxFileSize;
    }
    
    public long getMaxFileSizeInBytes() {
        String size = maxFileSize.toLowerCase();
        if (size.endsWith("mb")) {
            return Long.parseLong(size.replace("mb", "")) * 1024 * 1024;
        } else if (size.endsWith("gb")) {
            return Long.parseLong(size.replace("gb", "")) * 1024 * 1024 * 1024;
        } else if (size.endsWith("kb")) {
            return Long.parseLong(size.replace("kb", "")) * 1024;
        } else {
            // デフォルトはバイト単位
            return Long.parseLong(size.replaceAll("[^0-9]", ""));
        }
    }
}