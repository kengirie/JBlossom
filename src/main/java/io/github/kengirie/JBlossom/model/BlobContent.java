package io.github.kengirie.JBlossom.model;

import org.springframework.core.io.Resource;

public class BlobContent {
    private final String hash;
    private final Resource resource;
    private final String mimeType;
    private final long size;
    private final long rangeStart;
    private final long rangeEnd;
    private final boolean isRangeRequest;
    
    private BlobContent(Builder builder) {
        this.hash = builder.hash;
        this.resource = builder.resource;
        this.mimeType = builder.mimeType;
        this.size = builder.size;
        this.rangeStart = builder.rangeStart;
        this.rangeEnd = builder.rangeEnd;
        this.isRangeRequest = builder.isRangeRequest;
    }
    
    public String getHash() {
        return hash;
    }
    
    public Resource getResource() {
        return resource;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public long getSize() {
        return size;
    }
    
    public long getRangeStart() {
        return rangeStart;
    }
    
    public long getRangeEnd() {
        return rangeEnd;
    }
    
    public boolean isRangeRequest() {
        return isRangeRequest;
    }
    
    public long getContentLength() {
        if (isRangeRequest) {
            return rangeEnd - rangeStart + 1;
        }
        return size;
    }
    
    public String getContentRange() {
        if (isRangeRequest) {
            return String.format("bytes %d-%d/%d", rangeStart, rangeEnd, size);
        }
        return null;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String hash;
        private Resource resource;
        private String mimeType;
        private long size;
        private long rangeStart = 0;
        private long rangeEnd;
        private boolean isRangeRequest = false;
        
        public Builder hash(String hash) {
            this.hash = hash;
            return this;
        }
        
        public Builder resource(Resource resource) {
            this.resource = resource;
            return this;
        }
        
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }
        
        public Builder size(long size) {
            this.size = size;
            this.rangeEnd = size - 1; // デフォルトは全体
            return this;
        }
        
        public Builder range(long start, long end) {
            this.rangeStart = start;
            this.rangeEnd = end;
            this.isRangeRequest = true;
            return this;
        }
        
        public BlobContent build() {
            if (hash == null || resource == null || mimeType == null) {
                throw new IllegalArgumentException("hash, resource, and mimeType are required");
            }
            return new BlobContent(this);
        }
    }
    
    @Override
    public String toString() {
        if (isRangeRequest) {
            return String.format("BlobContent{hash='%s', size=%d, range=%d-%d, mimeType='%s'}", 
                    hash, size, rangeStart, rangeEnd, mimeType);
        } else {
            return String.format("BlobContent{hash='%s', size=%d, mimeType='%s'}", 
                    hash, size, mimeType);
        }
    }
}