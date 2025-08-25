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
import com.cedarpolicy.value.functions.Offset;
import com.cedarpolicy.value.DateTime;
import com.cedarpolicy.value.Duration;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

public class OffsetTests {

    @Test
    public void testValidJsonSerialization() throws JsonProcessingException {
        DateTime dateTime = new DateTime("2023-12-25T10:30:45Z");
        Duration duration = new Duration("2h30m");
        Offset offset = new Offset(dateTime, duration);

        String json = CedarJson.objectWriter().writeValueAsString(offset);
        String expectedJson = "{\"__extn\":{\"fn\":\"offset\",\"args\":["
                + "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"2023-12-25T10:30:45Z\"}},"
                + "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"2h30m\"}}]}}";

        assertEquals(expectedJson, json);
    }

    @Test
    public void testValidJsonDeserialization() throws IOException {
        String json = "{\"__extn\":{\"fn\":\"offset\",\"args\":["
                + "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"2023-01-01T00:00:00Z\"}},"
                + "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"1d5h\"}}]}}";

        Offset offset = CedarJson.objectReader().readValue(json, Offset.class);

        assertEquals("2023-01-01T00:00:00Z", offset.getDateTime().toString());
        assertEquals("1d5h", offset.getOffsetDuration().toString());
        assertEquals("datetime(\"2023-01-01T00:00:00Z\").offset(duration(\"1d5h\"))", offset.toCedarExpr());
    }

    @Test
    public void testInvalidJsonDeserialization() {
        // Test that invalid JSON throws appropriate exceptions
        String invalidJson = "{\"__extn\":{\"fn\":\"offset\",\"args\":\"invalid-offset\"}}";

        assertThrows(Exception.class, () -> {
            CedarJson.objectReader().readValue(invalidJson, Offset.class);
        });

        // Test with invalid datetime
        String invalidDateTimeJson = "{\"__extn\":{\"fn\":\"offset\",\"args\":["
                + "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"invalid-date\"}},"
                + "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"1h\"}}]}}";

        assertThrows(Exception.class, () -> {
            CedarJson.objectReader().readValue(invalidDateTimeJson, Offset.class);
        });

        // Test with invalid duration
        String invalidDurationJson = "{\"__extn\":{\"fn\":\"offset\",\"args\":["
                + "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"2023-01-01T00:00:00Z\"}},"
                + "{\"__extn\":{\"fn\":\"duration\",\"arg\":\"invalid-duration\"}}]}}";

        assertThrows(Exception.class, () -> {
            CedarJson.objectReader().readValue(invalidDurationJson, Offset.class);
        });
    }

    @Test
    public void testJsonRoundTrip() throws IOException {
        // Test data: array of [dateTimeString, durationString] pairs
        String[][] testOffsets = {
            {"2023-12-25T10:30:45Z", "2h30m"},
            {"2023-01-01T00:00:00Z", "1d"},
            {"2023-06-15T14:22:33.123Z", "5m30s"},
            {"2023-03-10T08:45:12+0500", "1h15m45s"},
            {"2023-11-30T23:59:59-0800", "-30m"},
            {"2023-02-28", "24h"},
            {"2023-07-04T12:00:00.999Z", "1ms"}
        };

        for (String[] testOffset : testOffsets) {
            DateTime dateTime = new DateTime(testOffset[0]);
            Duration duration = new Duration(testOffset[1]);
            Offset original = new Offset(dateTime, duration);

            // Serialize to JSON
            String json = CedarJson.objectWriter().writeValueAsString(original);

            // Deserialize back from JSON
            Offset deserialized = CedarJson.objectReader().readValue(json, Offset.class);

            // Verify they are equal
            assertEquals(original.getDateTime().toString(), deserialized.getDateTime().toString());
            assertEquals(original.getOffsetDuration().toString(), deserialized.getOffsetDuration().toString());
            assertEquals(original.toCedarExpr(), deserialized.toCedarExpr());
        }
    }
}
