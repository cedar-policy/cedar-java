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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.cedarpolicy.value.DateTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

public class DateTimeTests {

    @Test
    public void testValidDateOnlyFormat() {
        String validDate = "2023-12-25";
        DateTime dateTime = new DateTime(validDate);

        assertEquals(validDate, dateTime.toString());
    }

    @Test
    public void testValidDateTimeWithZFormat() {
        String validDateTime = "2023-12-25T10:30:45Z";
        DateTime dateTime = new DateTime(validDateTime);

        assertEquals(validDateTime, dateTime.toString());
    }

    @Test
    public void testValidDateTimeWithMillisecondsZFormat() {
        String validDateTime = "2023-12-25T10:30:45.123Z";
        DateTime dateTime = new DateTime(validDateTime);

        assertEquals(validDateTime, dateTime.toString());
    }

    @Test
    public void testValidDateTimeWithTimezoneOffsetFormat() {
        String validDateTime = "2023-12-25T10:30:45+0500";
        DateTime dateTime = new DateTime(validDateTime);

        assertEquals(validDateTime, dateTime.toString());
    }

    @Test
    public void testValidDateTimeWithMillisecondsAndTimezoneOffsetFormat() {
        String validDateTime = "2023-12-25T10:30:45.123-0300";
        DateTime dateTime = new DateTime(validDateTime);

        assertEquals(validDateTime, dateTime.toString());
    }

    @Test
    public void testValidDateTimeEdgeCases() {
        // Test leap year
        String leapYear = "2024-02-29";
        DateTime dateTime1 = new DateTime(leapYear);
        assertEquals(leapYear, dateTime1.toString());

        // Test end of year
        String endOfYear = "2023-12-31T23:59:59Z";
        DateTime dateTime2 = new DateTime(endOfYear);
        assertEquals(endOfYear, dateTime2.toString());

        // Test beginning of year
        String beginningOfYear = "2023-01-01T00:00:00Z";
        DateTime dateTime3 = new DateTime(beginningOfYear);
        assertEquals(beginningOfYear, dateTime3.toString());
    }

    @Test
    public void testInvalidDateTimeFormatsThrowException() {
        // Test null input
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime(null);
        });

        // Test empty string
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("");
        });

        // Test whitespace-only string
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("   ");
        });

        // Test invalid date format
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023/12/25");
        });

        // Test invalid month
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-15-25");
        });

        // Test invalid day
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-32");
        });

        // Test invalid hour
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T25:30:45Z");
        });

        // Test invalid minute
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:60:45Z");
        });

        // Test invalid second
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:60Z");
        });

        // Test invalid leap year date
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-02-29"); // 2023 is not a leap year
        });

        // Test invalid timezone format
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45+ABC");
        });

        // Test millisecond precision beyond the pattern (more than 3 digits)
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45.1234Z");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45.12345+0500");
        });

        // Test half-formed timezone offset (missing minutes)
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45+09");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45-05");
        });

        // Test incorrect timezone offset format (with colon separator)
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45+08:30");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45-09:45");
        });

        // Test timezone offset with invalid hour values
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45+2600");
        });

        // Test timezone offset with invalid minute values
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45-0875");
        });

        // Test timezone offset with only one digit
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45+5");
        });

        // Test timezone offset with too many digits
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:45+05000");
        });

        // Test timezone offset without sign
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10:30:450800");
        });

        // Test malformed string
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("not-a-date");
        });

        // Test incomplete datetime
        assertThrows(IllegalArgumentException.class, () -> {
            new DateTime("2023-12-25T10");
        });
    }

    @Test
    public void testEqualsAndHashCode() {
        String dateTimeString = "2023-12-25T10:30:45Z";
        DateTime dateTime1 = new DateTime(dateTimeString);
        DateTime dateTime2 = new DateTime(dateTimeString);
        DateTime dateTime3 = new DateTime("2023-12-25T10:30:46Z"); // Different second

        // Test equals
        assertEquals(dateTime1, dateTime2);
        assertEquals(dateTime1, dateTime1); // Self equality
        assertNotEquals(dateTime1, dateTime3);
        assertNotEquals(dateTime1, null);
        assertNotEquals(dateTime1, "not a DateTime");

        // Test hashCode consistency
        assertEquals(dateTime1.hashCode(), dateTime2.hashCode());
    }

    @Test
    public void testToString() {
        String dateTimeString = "2023-12-25T10:30:45.123Z";
        DateTime dateTime = new DateTime(dateTimeString);

        assertEquals(dateTimeString, dateTime.toString());
    }

    @Test
    public void testToCedarExpr() {
        String dateTimeString = "2023-12-25";
        DateTime dateTime = new DateTime(dateTimeString);

        assertEquals("datetime(\"2023-12-25\")", dateTime.toCedarExpr());
    }

    @Test
    public void testToCedarExprWithComplexDateTime() {
        String dateTimeString = "2023-12-25T10:30:45.123+0500";
        DateTime dateTime = new DateTime(dateTimeString);

        assertEquals("datetime(\"2023-12-25T10:30:45.123+0500\")", dateTime.toCedarExpr());
    }

    @Test
    public void testDateTimeValidatorBoundaryConditions() {
        // Test minimum CE valid value
        DateTime minDate = new DateTime("0001-01-01");
        assertEquals("0001-01-01", minDate.toString());

        // Test maximum month and day values
        DateTime maxMonthDay = new DateTime("2023-12-31");
        assertEquals("2023-12-31", maxMonthDay.toString());

        // Test maximum time values
        DateTime maxTime = new DateTime("2023-12-25T23:59:59Z");
        assertEquals("2023-12-25T23:59:59Z", maxTime.toString());

        // Test minimum time values
        DateTime minTime = new DateTime("2023-12-25T00:00:00Z");
        assertEquals("2023-12-25T00:00:00Z", minTime.toString());

        // Test maximum milliseconds
        DateTime maxMillis = new DateTime("2023-12-25T10:30:45.999Z");
        assertEquals("2023-12-25T10:30:45.999Z", maxMillis.toString());
    }

    @Test
    public void testEqualityWithDifferentFormats() {
        DateTime date1 = new DateTime("2023-12-25");
        DateTime date2 = new DateTime("2023-12-25T00:00:00Z");

        // Since we store the original string, these should not be equal
        assertNotEquals(date1, date2);
        assertNotEquals(date1.toString(), date2.toString());
    }

    @Test
    public void testValidJsonSerialization() throws JsonProcessingException {
        String dateTimeString = "2023-12-25T10:30:45Z";
        DateTime dateTime = new DateTime(dateTimeString);

        String json = CedarJson.objectWriter().writeValueAsString(dateTime);
        String expectedJson = "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"2023-12-25T10:30:45Z\"}}";

        assertEquals(expectedJson, json);
    }

    @Test
    public void testValidJsonDeserialization() throws IOException {
        String json = "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"2023-12-25T10:30:45.123+0500\"}}";

        DateTime dateTime = CedarJson.objectReader().readValue(json, DateTime.class);

        assertEquals("2023-12-25T10:30:45.123+0500", dateTime.toString());
        assertEquals("datetime(\"2023-12-25T10:30:45.123+0500\")", dateTime.toCedarExpr());
    }

    @Test
    public void testInvalidJsonDeserialization() {
        // Test that invalid JSON throws appropriate exceptions
        String invalidJson = "{\"__extn\":{\"fn\":\"datetime\",\"arg\":\"invalid-date\"}}";

        assertThrows(Exception.class, () -> {
            CedarJson.objectReader().readValue(invalidJson, DateTime.class);
        });
    }

    @Test
    public void testJsonRoundTrip() throws IOException {
        String[] testDates = {"2023-12-25", "2023-12-25T10:30:45Z", "2023-12-25T10:30:45.123Z",
                "2023-12-25T10:30:45+0500", "2023-12-25T10:30:45.999-0800"};

        for (String dateTimeString : testDates) {
            DateTime original = new DateTime(dateTimeString);

            // Serialize to JSON
            String json = CedarJson.objectWriter().writeValueAsString(original);

            // Deserialize back from JSON
            DateTime deserialized = CedarJson.objectReader().readValue(json, DateTime.class);

            // Verify they are equal
            assertEquals(original, deserialized);
            assertEquals(original.toString(), deserialized.toString());
            assertEquals(original.toCedarExpr(), deserialized.toCedarExpr());
        }
    }

}
