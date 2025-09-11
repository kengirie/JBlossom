package io.github.kengirie.JBlossom.model;

import java.time.Instant;

public class BlobMetadata {
    private String hash;
    private long size;
    private String type;
    private long uploaded;
    private String pubkey;
    private Long lastAccessed;
    
    public BlobMetadata() {}
    
    public BlobMetadata(String hash, long size, String type, long uploaded, String pubkey) {
        this.hash = hash;
        this.size = size;
        this.type = type;
        this.uploaded = uploaded;
        this.pubkey = pubkey;
    }
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
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
    
    public String getPubkey() {
        return pubkey;
    }
    
    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }
    
    public Long getLastAccessed() {
        return lastAccessed;
    }
    
    public void setLastAccessed(Long lastAccessed) {
        this.lastAccessed = lastAccessed;
    }
    
    // アクセス時刻を現在時刻で更新
    public void updateLastAccessed() {
        this.lastAccessed = Instant.now().getEpochSecond();
    }
    
    // BlobDescriptorに変換
    public BlobDescriptor toBlobDescriptor(String baseUrl) {
        String url = baseUrl + "/" + hash;
        return new BlobDescriptor(url, hash, size, type, uploaded);
    }
}