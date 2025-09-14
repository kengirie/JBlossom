package io.github.kengirie.JBlossom.util;

import io.github.kengirie.JBlossom.util.RangeRequestParser.Range;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RangeRequestParserTest {
    
    @Test
    void testParseValidRange() {
        Range range = RangeRequestParser.parseRange("bytes=0-9", 100);
        
        assertNotNull(range);
        assertEquals(0, range.getStart());
        assertEquals(9, range.getEnd());
        assertEquals(10, range.getLength());
    }
    
    @Test
    void testParseRangeWithoutEnd() {
        Range range = RangeRequestParser.parseRange("bytes=50-", 100);
        
        assertNotNull(range);
        assertEquals(50, range.getStart());
        assertEquals(99, range.getEnd()); // ファイルサイズ - 1
        assertEquals(50, range.getLength());
    }
    
    @Test
    void testParseRangeEndExceedsFileSize() {
        Range range = RangeRequestParser.parseRange("bytes=0-150", 100);
        
        assertNotNull(range);
        assertEquals(0, range.getStart());
        assertEquals(99, range.getEnd()); // ファイルサイズに調整される
        assertEquals(100, range.getLength());
    }
    
    @Test
    void testParseRangeStartExceedsFileSize() {
        Range range = RangeRequestParser.parseRange("bytes=200-250", 100);
        
        assertNull(range); // 開始位置がファイルサイズを超えている
    }
    
    @Test
    void testParseRangeStartGreaterThanEnd() {
        Range range = RangeRequestParser.parseRange("bytes=50-25", 100);
        
        assertNull(range); // 開始位置が終了位置より大きい
    }
    
    @Test
    void testParseInvalidFormat() {
        assertNull(RangeRequestParser.parseRange("invalid-format", 100));
        assertNull(RangeRequestParser.parseRange("bytes=", 100));
        assertNull(RangeRequestParser.parseRange("bytes=abc-def", 100));
        assertNull(RangeRequestParser.parseRange("", 100));
        assertNull(RangeRequestParser.parseRange(null, 100));
    }
    
    @Test
    void testParseRangeSingleByte() {
        Range range = RangeRequestParser.parseRange("bytes=42-42", 100);
        
        assertNotNull(range);
        assertEquals(42, range.getStart());
        assertEquals(42, range.getEnd());
        assertEquals(1, range.getLength());
    }
    
    @Test
    void testParseRangeZeroStart() {
        Range range = RangeRequestParser.parseRange("bytes=0-0", 100);
        
        assertNotNull(range);
        assertEquals(0, range.getStart());
        assertEquals(0, range.getEnd());
        assertEquals(1, range.getLength());
    }
    
    @Test
    void testIsValidRange() {
        Range validRange = new Range(10, 19);
        Range invalidRangeNegativeStart = new Range(-1, 19);
        Range invalidRangeEndExceedsFile = new Range(10, 150);
        Range invalidRangeStartGreaterThanEnd = new Range(20, 10);
        
        assertTrue(RangeRequestParser.isValidRange(validRange, 100));
        assertFalse(RangeRequestParser.isValidRange(invalidRangeNegativeStart, 100));
        assertFalse(RangeRequestParser.isValidRange(invalidRangeEndExceedsFile, 100));
        assertFalse(RangeRequestParser.isValidRange(invalidRangeStartGreaterThanEnd, 100));
        assertFalse(RangeRequestParser.isValidRange(null, 100));
    }
    
    @Test
    void testBuildContentRange() {
        Range range = new Range(100, 199);
        
        String contentRange = RangeRequestParser.buildContentRange(range, 1000);
        assertEquals("bytes 100-199/1000", contentRange);
        
        assertNull(RangeRequestParser.buildContentRange(null, 1000));
    }
    
    @Test
    void testRangeToString() {
        Range range = new Range(50, 99);
        assertEquals("Range{50-99}", range.toString());
    }
    
    @Test
    void testParseComplexRanges() {
        // エッジケース: ファイルサイズ1バイト
        Range range1 = RangeRequestParser.parseRange("bytes=0-0", 1);
        assertNotNull(range1);
        assertEquals(0, range1.getStart());
        assertEquals(0, range1.getEnd());
        
        // エッジケース: 最後のバイトのみ
        Range range2 = RangeRequestParser.parseRange("bytes=99-", 100);
        assertNotNull(range2);
        assertEquals(99, range2.getStart());
        assertEquals(99, range2.getEnd());
        
        // エッジケース: 全体
        Range range3 = RangeRequestParser.parseRange("bytes=0-", 1000);
        assertNotNull(range3);
        assertEquals(0, range3.getStart());
        assertEquals(999, range3.getEnd());
    }
}