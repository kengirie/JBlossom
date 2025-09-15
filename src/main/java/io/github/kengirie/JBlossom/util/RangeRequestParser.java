package io.github.kengirie.JBlossom.util;

import io.github.kengirie.JBlossom.exception.RangeNotSatisfiableException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RangeRequestParser {
    
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");
    
    public static class Range {
        private final long start;
        private final long end;
        
        public Range(long start, long end) {
            this.start = start;
            this.end = end;
        }
        
        public long getStart() {
            return start;
        }
        
        public long getEnd() {
            return end;
        }
        
        public long getLength() {
            return end - start + 1;
        }
        
        @Override
        public String toString() {
            return String.format("Range{%d-%d}", start, end);
        }
    }
    
    public static Range parseRange(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            return null;
        }
        
        try {
            long start = Long.parseLong(matcher.group(1));
            String endStr = matcher.group(2);
            
            // 開始位置がファイルサイズを超えている場合は無効
            if (start >= fileSize) {
                return null;
            }
            
            long end;
            if (endStr.isEmpty()) {
                // "bytes=100-" の形式（最後まで）
                end = fileSize - 1;
            } else {
                end = Long.parseLong(endStr);
                // 終了位置がファイルサイズを超えている場合は調整
                if (end >= fileSize) {
                    end = fileSize - 1;
                }
            }
            
            // 開始位置が終了位置を超えている場合は無効
            if (start > end) {
                return null;
            }
            
            return new Range(start, end);
            
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public static boolean isValidRange(Range range, long fileSize) {
        if (range == null) {
            return false;
        }
        
        return range.getStart() >= 0 && 
               range.getEnd() < fileSize && 
               range.getStart() <= range.getEnd();
    }
    
    public static String buildContentRange(Range range, long fileSize) {
        if (range == null) {
            return null;
        }
        return String.format("bytes %d-%d/%d", range.getStart(), range.getEnd(), fileSize);
    }
}