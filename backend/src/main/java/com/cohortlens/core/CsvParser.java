package com.cohortlens.core;

import java.util.ArrayList;
import java.util.List;

/** Small RFC 4180 style CSV reader: quoted fields, escaped quotes, commas and newlines inside quotes. */
public final class CsvParser {
    private CsvParser() {
    }

    public static List<String[]> parse(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (fieldStarted || field.length() > 0 || !fields.isEmpty()) {
                    fields.add(field.toString());
                    rows.add(fields.toArray(new String[0]));
                }
                fields = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (fieldStarted || field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }
}
