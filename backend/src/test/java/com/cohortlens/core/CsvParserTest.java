package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void parsesQuotedFieldsWithCommasAndEscapedQuotes() {
        List<String[]> rows = CsvParser.parse("a,\"b,c\",\"d \"\"q\"\"\"\n1,2,3\n");
        assertEquals(2, rows.size());
        assertArrayEquals(new String[] {"a", "b,c", "d \"q\""}, rows.get(0));
        assertArrayEquals(new String[] {"1", "2", "3"}, rows.get(1));
    }

    @Test
    void handlesWindowsLineEndingsAndSkipsBlankLines() {
        List<String[]> rows = CsvParser.parse("a,b\r\n\r\n1,2\r\n");
        assertEquals(2, rows.size());
        assertArrayEquals(new String[] {"1", "2"}, rows.get(1));
    }

    @Test
    void keepsTrailingEmptyField() {
        List<String[]> rows = CsvParser.parse("a,b,\n");
        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).length);
        assertEquals("", rows.get(0)[2]);
    }

    @Test
    void lastRowWithoutNewlineIsKept() {
        List<String[]> rows = CsvParser.parse("a,b\n1,2");
        assertEquals(2, rows.size());
        assertArrayEquals(new String[] {"1", "2"}, rows.get(1));
    }
}
