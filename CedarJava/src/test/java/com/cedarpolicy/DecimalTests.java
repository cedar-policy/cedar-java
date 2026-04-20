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
import com.cedarpolicy.value.Decimal;

public class DecimalTests {

    @Test
    public void testValidDecimals() {
        assertDoesNotThrow(() -> new Decimal("1.0"));
        assertDoesNotThrow(() -> new Decimal("1.0000"));
        assertDoesNotThrow(() -> new Decimal("-1.0"));
        assertDoesNotThrow(() -> new Decimal("0.0"));
        assertDoesNotThrow(() -> new Decimal("0.0000"));
        assertDoesNotThrow(() -> new Decimal("123.456"));
        assertDoesNotThrow(() -> new Decimal("-123.456"));
        // Negative zero is valid
        assertDoesNotThrow(() -> new Decimal("-0.0"));
        assertDoesNotThrow(() -> new Decimal("-0.0000"));
    }

    @Test
    public void testLeadingZeros() {
        // Leading zeros should be accepted - Cedar trims them
        assertDoesNotThrow(() -> new Decimal("001.0"));
        assertDoesNotThrow(() -> new Decimal("0001.0000"));
        assertDoesNotThrow(() -> new Decimal("-001.0"));
        assertDoesNotThrow(() -> new Decimal("00000000000000000001.0"));
    }

    @Test
    public void testBoundaryValues() {
        // Max positive: 922337203685477.5807
        assertDoesNotThrow(() -> new Decimal("922337203685477.5807"));
        // Min negative: -922337203685477.5808
        assertDoesNotThrow(() -> new Decimal("-922337203685477.5808"));
        // With leading zeros
        assertDoesNotThrow(() -> new Decimal("-000000922337203685477.5808"));
        assertDoesNotThrow(() -> new Decimal("000000922337203685477.5807"));
    }

    @Test
    public void testOutOfRange() {
        // One above max positive
        assertThrows(IllegalArgumentException.class, () -> new Decimal("922337203685477.5808"));
        // One below min negative
        assertThrows(IllegalArgumentException.class, () -> new Decimal("-922337203685477.5809"));
        // Way out of range
        assertThrows(IllegalArgumentException.class, () -> new Decimal("9999999999999999.0"));
        assertThrows(IllegalArgumentException.class, () -> new Decimal("-9999999999999999.0"));
        // Out of range even with leading zeros
        assertThrows(IllegalArgumentException.class, () -> new Decimal("000922337203685477.5808"));
        assertThrows(IllegalArgumentException.class, () -> new Decimal("-000922337203685477.5809"));
    }

    @Test
    public void testInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> new Decimal(""));
        assertThrows(IllegalArgumentException.class, () -> new Decimal("abc"));
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1"));
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1.00000"));
        assertThrows(IllegalArgumentException.class, () -> new Decimal(null));
    }

    @Test
    public void testEdgeCaseFormats() {
        // Missing integer part before dot
        assertThrows(IllegalArgumentException.class, () -> new Decimal("."));
        assertThrows(IllegalArgumentException.class, () -> new Decimal(".1"));
        // Missing fractional part after dot
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1."));
        // Negative zero with no fractional digits
        assertThrows(IllegalArgumentException.class, () -> new Decimal("-.0"));
        // Multiple dots
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1.2.3"));
        // Consecutive dots
        assertThrows(IllegalArgumentException.class, () -> new Decimal("-.."));
        // Explicit plus sign
        assertThrows(IllegalArgumentException.class, () -> new Decimal("+1.0"));
        // Double negative
        assertThrows(IllegalArgumentException.class, () -> new Decimal("--1.0"));
        // Scientific notation
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1.0e2"));
        // Whitespace in the middle
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1 .0"));
        // Leading/trailing whitespace
        assertThrows(IllegalArgumentException.class, () -> new Decimal(" 1.0"));
        assertThrows(IllegalArgumentException.class, () -> new Decimal("1.0 "));
        assertThrows(IllegalArgumentException.class, () -> new Decimal(" 1.0 "));
    }

    @Test
    public void testToCedarExpr() {
        Decimal d = new Decimal("1.0000");
        assertEquals("decimal(\"1.0000\")", d.toCedarExpr());
    }

    @Test
    public void testEquality() {
        Decimal d1 = new Decimal("1.0000");
        Decimal d2 = new Decimal("1.0000");
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }
}
