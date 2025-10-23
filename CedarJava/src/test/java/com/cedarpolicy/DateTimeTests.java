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
    public void testValidDateTime() {
        // Test leap year
        assertEquals("2024-02-29", new DateTime("2024-02-29").toString());
        assertEquals(1709164800000L, new DateTime("2024-02-29").toEpochMilli());

        // Test end of year
        assertEquals("2023-12-31T23:59:59Z", new DateTime("2023-12-31T23:59:59Z").toString());
        assertEquals(1704067199000L, new DateTime("2023-12-31T23:59:59Z").toEpochMilli());

        // Test beginning of year
        assertEquals("2023-01-01T00:00:00Z", new DateTime("2023-01-01T00:00:00Z").toString());
        assertEquals(1672531200000L, new DateTime("2023-01-01T00:00:00Z").toEpochMilli());

        // Test extreme timezone cases
        assertEquals(1728905942000L, new DateTime("2024-10-15T11:38:02+2359").toEpochMilli());
        assertEquals(1729078622000L, new DateTime("2024-10-15T11:38:02-2359").toEpochMilli());

        // Test extreme year cases
        assertEquals(-62167219200000L, new DateTime("0000-01-01").toEpochMilli());
        assertEquals(-62135596801000L, new DateTime("0000-12-31T23:59:59Z").toEpochMilli());
        assertEquals(-62167305540000L, new DateTime("0000-01-01T00:00:00+2359").toEpochMilli());
        assertEquals(253402214400000L, new DateTime("9999-12-31").toEpochMilli());
        assertEquals(253402300799000L, new DateTime("9999-12-31T23:59:59Z").toEpochMilli());
        assertEquals(253402300799999L, new DateTime("9999-12-31T23:59:59.999Z").toEpochMilli());
        assertEquals(253402387139000L, new DateTime("9999-12-31T23:59:59-2359").toEpochMilli());

        // Additional test cases
        assertEquals(1665360000000L, new DateTime("2022-10-10").toEpochMilli());
        assertEquals(-86400000L, new DateTime("1969-12-31").toEpochMilli());
        assertEquals(-1000L, new DateTime("1969-12-31T23:59:59Z").toEpochMilli());
        assertEquals(-999L, new DateTime("1969-12-31T23:59:59.001Z").toEpochMilli());
        assertEquals(-1L, new DateTime("1969-12-31T23:59:59.999Z").toEpochMilli());
        assertEquals(1728950400000L, new DateTime("2024-10-15").toEpochMilli());
        assertEquals(1728992282000L, new DateTime("2024-10-15T11:38:02Z").toEpochMilli());
        assertEquals(1728992282101L, new DateTime("2024-10-15T11:38:02.101Z").toEpochMilli());
        assertEquals(1729033922101L, new DateTime("2024-10-15T11:38:02.101-1134").toEpochMilli());
        assertEquals(1728950642101L, new DateTime("2024-10-15T11:38:02.101+1134").toEpochMilli());
        assertEquals(1728950642000L, new DateTime("2024-10-15T11:38:02+1134").toEpochMilli());
        assertEquals(1729033922000L, new DateTime("2024-10-15T11:38:02-1134").toEpochMilli());

    }

    @Test
    public void testInvalidDateTimeThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new DateTime(null));
        assertThrows(IllegalArgumentException.class, () -> new DateTime(""));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("a"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("-"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("-1"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime(" 2022-10-10"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2022-10-10 "));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2022-10- 10"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("11-12-13"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("011-12-13"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("00011-12-13"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-2-13"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-012-13"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-02-3"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-02-003"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T1:01:01Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T001:01:01Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:1:01Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:001:01Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:1Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:001Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.01Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.0001Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.001+01"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.001+001"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.001+00001"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.001+00:01"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("0001-01-01T01:01:01.001+00:00:01"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("-0001-01-01"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("1111-1x-20"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("1111-Jul-20"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("1111-July-20"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("1111-J-20"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-10-15Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-10-15T11:38:02ZZ"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01Ta"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T01:"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T01:02"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T01:02:0b"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T01::02:03"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T01::02::03"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T31:02:03Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T01:60:03Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-12-31T23:59:60Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-12-31T23:59:61Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00T"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00ZZ"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00x001Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00.001ZZ"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-12-31T23:59:60.000Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-12-31T23:59:60.000+0200"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00➕0000"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00➖0000"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00.0001Z"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00.001➖0000"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00.001➕0000"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00.001+00000"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2024-01-01T00:00:00.001-00000"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-01-01T00:00:00+2400"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-01-01T00:00:00+0060"));
        assertThrows(IllegalArgumentException.class, () -> new DateTime("2016-01-01T00:00:00+9999"));
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
    public void testSemanticEqualityWithDifferentFormats() {
        DateTime date1 = new DateTime("2023-12-25");
        DateTime date2 = new DateTime("2023-12-25T00:00:00Z");

        // With semantic equality, these should be equal as they represent the same instant
        assertEquals(date1, date2);
        assertEquals(date1.hashCode(), date2.hashCode());

        // But their string representations remain different
        assertNotEquals(date1.toString(), date2.toString());
    }

    @Test
    public void testSemanticEqualityWithTimezones() {
        // These all represent the same instant: noon UTC on Dec 25, 2023
        DateTime utc = new DateTime("2023-12-25T12:00:00Z");
        DateTime eastern = new DateTime("2023-12-25T07:00:00-0500");
        DateTime pacific = new DateTime("2023-12-25T04:00:00-0800");
        DateTime plus5 = new DateTime("2023-12-25T17:00:00+0500");

        // All should be semantically equal
        assertEquals(utc, eastern);
        assertEquals(utc, pacific);
        assertEquals(utc, plus5);
        assertEquals(eastern, pacific);
        assertEquals(eastern, plus5);
        assertEquals(pacific, plus5);

        // Hash codes should match
        assertEquals(utc.hashCode(), eastern.hashCode());
        assertEquals(utc.hashCode(), pacific.hashCode());
        assertEquals(utc.hashCode(), plus5.hashCode());

        // But string representations should be different
        assertNotEquals(utc.toString(), eastern.toString());
        assertNotEquals(utc.toString(), pacific.toString());
        assertNotEquals(utc.toString(), plus5.toString());
    }

    @Test
    public void testSemanticEqualityWithMilliseconds() {
        // Test that millisecond precision affects equality
        DateTime withMillis = new DateTime("2023-12-25T12:00:12.000Z");
        DateTime withoutMillis = new DateTime("2023-12-25T12:00:00Z");
        DateTime differentMillis = new DateTime("2023-12-25T12:00:00.456Z");

        // These should not be equal due to different milliseconds
        assertNotEquals(withMillis, withoutMillis);
        assertNotEquals(withMillis, differentMillis);
        assertNotEquals(withoutMillis, differentMillis);

        // Same milliseconds should be equal
        DateTime sameMillis = new DateTime("2023-12-25T12:00:12Z");
        assertEquals(withMillis, sameMillis);
        assertEquals(withMillis.hashCode(), sameMillis.hashCode());
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
