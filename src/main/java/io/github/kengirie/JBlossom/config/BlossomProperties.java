package io.github.kengirie.JBlossom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "blossom")
public class BlossomProperties {
    
    private Database database = new Database();
    private Storage storage = new Storage();
    private Cors cors = new Cors();
    
    // Getters and Setters
    public Database getDatabase() {
        return database;
    }
    
    public void setDatabase(Database database) {
        this.database = database;
    }
    
    public Storage getStorage() {
        return storage;
    }
    
    public void setStorage(Storage storage) {
        this.storage = storage;
    }
    
    public Cors getCors() {
        return cors;
    }
    
    public void setCors(Cors cors) {
        this.cors = cors;
    }
    
    // Inner Classes
    public static class Database {
        private String path = "./data/sqlite.db";
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
    }
    
    public static class Storage {
        private String path = "./data/blobs";
        private String maxFileSize = "100MB";
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getMaxFileSize() {
            return maxFileSize;
        }
        
        public void setMaxFileSize(String maxFileSize) {
            this.maxFileSize = maxFileSize;
        }
    }
    
    public static class Cors {
        private String allowedOrigins = "*";
        private String allowedMethods = "GET,HEAD,POST,PUT,DELETE,OPTIONS";
        private String allowedHeaders = "Authorization,Content-Type,X-SHA-256";
        
        public String getAllowedOrigins() {
            return allowedOrigins;
        }
        
        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
        
        public String getAllowedMethods() {
            return allowedMethods;
        }
        
        public void setAllowedMethods(String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }
        
        public String getAllowedHeaders() {
            return allowedHeaders;
        }
        
        public void setAllowedHeaders(String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }
    }
}