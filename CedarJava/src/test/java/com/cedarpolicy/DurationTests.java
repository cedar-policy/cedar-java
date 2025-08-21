/*
 * Copyright Cedar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cedarpolicy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.cedarpolicy.value.Duration;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

public class DurationTests {

    @Test
    public void testValidDurations() {
        // Test basic valid formats
        assertDoesNotThrow(() -> new Duration("1d2h3m4s5ms"));
        assertDoesNotThrow(() -> new Duration("2h5ms"));
        assertDoesNotThrow(() -> new Duration("2h"));
        assertDoesNotThrow(() -> new Duration("1d2ms"));
        assertDoesNotThrow(() -> new Duration("3h5m"));
        assertDoesNotThrow(() -> new Duration("-10h"));
        assertDoesNotThrow(() -> new Duration("1h"));
        assertDoesNotThrow(() -> new Duration("5d3ms"));

        // Test edge cases
        assertDoesNotThrow(() -> new Duration("0d"));
        assertDoesNotThrow(() -> new Duration("1ms"));
        assertDoesNotThrow(() -> new Duration("999s"));
    }

    @Test
    public void testInvalidDurations() {
        // Test null input
        assertThrows(NullPointerException.class, () -> new Duration(null));

        // Test empty string
        assertThrows(IllegalArgumentException.class, () -> new Duration(""));
        assertThrows(IllegalArgumentException.class, () -> new Duration("   "));

        // Test wrong order
        assertThrows(IllegalArgumentException.class, () -> new Duration("2h1d"));

        // Test duplicate units
        assertThrows(IllegalArgumentException.class, () -> new Duration("2d2d"));

        // Test invalid units
        assertThrows(IllegalArgumentException.class, () -> new Duration("2x"));

        // Test missing quantity
        assertThrows(IllegalArgumentException.class, () -> new Duration("h"));
        assertThrows(IllegalArgumentException.class, () -> new Duration("d2h"));

        // Test invalid format
        assertThrows(IllegalArgumentException.class, () -> new Duration("2h3m1h"));
        assertThrows(IllegalArgumentException.class, () -> new Duration("abc"));

        // Test mixed signs (should be invalid - only negative at beginning allowed)
        assertThrows(IllegalArgumentException.class, () -> new Duration("1d-5h"));
        assertThrows(IllegalArgumentException.class, () -> new Duration("-1d+5h"));
        assertThrows(IllegalArgumentException.class, () -> new Duration("1d+5h"));
        assertThrows(IllegalArgumentException.class, () -> new Duration("+1d5h"));

        // Test signs on individual components (should be invalid)
        assertThrows(IllegalArgumentException.class, () -> new Duration("+2h"));
        assertThrows(IllegalArgumentException.class, () -> new Duration("2d-3m"));
    }

    @Test
    public void testSemanticEquality() {
        // Same duration, different representations should be equal
        Duration duration1 = new Duration("60s");
        Duration duration2 = new Duration("1m");
        assertEquals(duration1, duration2);
        assertEquals(duration1.hashCode(), duration2.hashCode());

        // Different durations should not be equal
        Duration duration3 = new Duration("1h");
        Duration duration4 = new Duration("1m");
        assertNotEquals(duration3, duration4);
    }

    @Test
    public void testToString() {
        Duration duration = new Duration("1d2h3m");
        assertEquals("1d2h3m", duration.toString());
    }

    @Test
    public void testToCedarExpr() {
        Duration duration = new Duration("1h30m");
        assertEquals("duration(\"1h30m\")", duration.toCedarExpr());
    }

    @Test
    public void testCompareTo() {
        Duration oneHour = new Duration("1h");
        Duration twoHours = new Duration("2h");
        Duration oneHourAlternative = new Duration("60m");

        // Test less than
        assertTrue(oneHour.compareTo(twoHours) < 0);

        // Test greater than
        assertTrue(twoHours.compareTo(oneHour) > 0);

        // Test equal (same duration, different format)
        assertEquals(0, oneHour.compareTo(oneHourAlternative));

        // Test with null - should throw exception
        assertThrows(NullPointerException.class, () -> oneHour.compareTo(null));
    }

    @Test
    public void testValidJsonSerialization() throws JsonProcessingException {
        String durationString = "1h30m45s";
        Duration duration = new Duration(durationString);

        String json = CedarJson.objectWriter().writeValueAsString(duration);
        String expectedJson = "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"1h30m45s\"}}";

        assertEquals(expectedJson, json);
    }

    @Test
    public void testValidJsonDeserialization() throws IOException {
        String json = "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"2d3h15m\"}}";

        Duration duration = CedarJson.objectReader().readValue(json, Duration.class);

        assertEquals("2d3h15m", duration.toString());
        assertEquals("duration(\"2d3h15m\")", duration.toCedarExpr());
    }

    @Test
    public void testInvalidJsonDeserialization() {
        // Test that invalid JSON throws appropriate exceptions
        String invalidJson = "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"invalid-duration\"}}";

        assertThrows(Exception.class, () -> {
            CedarJson.objectReader().readValue(invalidJson, Duration.class);
        });
    }

    @Test
    public void testJsonRoundTrip() throws IOException {
        String[] testDurations = {"1d", "2h30m", "5m15s", "1d2h3m4s5ms", "30s", "999ms", "-2h", "0d"};

        for (String durationString : testDurations) {
            Duration original = new Duration(durationString);

            // Serialize to JSON
            String json = CedarJson.objectWriter().writeValueAsString(original);

            // Deserialize back from JSON
            Duration deserialized = CedarJson.objectReader().readValue(json, Duration.class);

            // Verify they are equal
            assertEquals(original, deserialized);
            assertEquals(original.toString(), deserialized.toString());
            assertEquals(original.toCedarExpr(), deserialized.toCedarExpr());
        }
    }
}
