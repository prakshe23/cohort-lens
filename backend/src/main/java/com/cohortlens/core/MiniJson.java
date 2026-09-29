package com.cohortlens.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Tiny JSON writer for records, lists, maps, enums, strings and numbers. It exists so the
 * dependency free dev server can return exactly the JSON shape Spring's Jackson produces for
 * the same records. The Spring application uses Jackson, not this class.
 */
public final class MiniJson {
    private MiniJson() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object v, StringBuilder out) {
        if (v == null) {
            out.append("null");
        } else if (v instanceof String s) {
            quote(s, out);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            out.append(v);
        } else if (v instanceof Double d) {
            out.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : d.toString());
        } else if (v instanceof Enum<?> e) {
            quote(e.name(), out);
        } else if (v instanceof Map<?, ?> m) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
        } else if (v instanceof Iterable<?> it) {
            out.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(o, out);
            }
            out.append(']');
        } else if (v.getClass().isRecord()) {
            out.append('{');
            boolean first = true;
            for (RecordComponent c : v.getClass().getRecordComponents()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(c.getName(), out);
                out.append(':');
                try {
                    write(c.getAccessor().invoke(v), out);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            }
            out.append('}');
        } else {
            quote(v.toString(), out);
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
