package com.cedarpolicy;

import com.cedarpolicy.model.schema.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SchemaTests {
    @Test
    public void parseSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse("{\"ns1\": {\"entityTypes\": {}, \"actions\":  {}}}");
            Schema.parse("{}");
        });
        assertThrows(Exception.class, () -> {
            Schema.parse("{\"foo\": \"bar\"}");
        });
    }
}
