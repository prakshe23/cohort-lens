package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiniJsonTest {
    record Sample(String name, Integer count, Double ratio, Term term, List<String> tags) {
    }

    @Test
    void writesRecordsInComponentOrder() {
        String json = MiniJson.write(new Sample("a", 3, 0.5, Term.FALL, List.of("x", "y")));
        assertEquals("{\"name\":\"a\",\"count\":3,\"ratio\":0.5,\"term\":\"FALL\",\"tags\":[\"x\",\"y\"]}", json);
    }

    @Test
    void writesNullsAndEscapes() {
        assertEquals("{\"name\":\"say \\\"hi\\\"\\n\",\"count\":null,\"ratio\":null,\"term\":null,\"tags\":[]}",
                MiniJson.write(new Sample("say \"hi\"\n", null, null, null, List.of())));
    }

    @Test
    void writesMaps() {
        assertEquals("{\"a\":1}", MiniJson.write(Map.of("a", 1L)));
    }
}
