package io.github.kengirie.JBlossom.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class BlobDescriptor {
    private String url;
    private String sha256;
    private long size;
    private String type;
    private long uploaded;
    
    public BlobDescriptor() {}
    
    public BlobDescriptor(String url, String sha256, long size, String type, long uploaded) {
        this.url = url;
        this.sha256 = sha256;
        this.size = size;
        this.type = type;
        this.uploaded = uploaded;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getSha256() {
        return sha256;
    }
    
    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public long getUploaded() {
        return uploaded;
    }
    
    public void setUploaded(long uploaded) {
        this.uploaded = uploaded;
    }
    
    // 現在時刻をunixタイムスタンプで設定
    public void setUploadedNow() {
        this.uploaded = Instant.now().getEpochSecond();
    }
}