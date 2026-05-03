package com.stellarideas.grooves.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class YearParserTest {

    @Test
    void parsesBareFourDigitYear() {
        assertEquals(1987, YearParser.parse("1987"));
    }

    @Test
    void parsesIso8601DatePrefix() {
        assertEquals(1987, YearParser.parse("1987-05-12"));
    }

    @Test
    void parsesYearWithTrailingTime() {
        assertEquals(1990, YearParser.parse("1990/01/01T00:00:00"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(2020, YearParser.parse("  2020  "));
    }

    @Test
    void returnsNullForNull() {
        assertNull(YearParser.parse(null));
    }

    @Test
    void returnsNullForEmpty() {
        assertNull(YearParser.parse(""));
        assertNull(YearParser.parse("   "));
    }

    @Test
    void returnsNullForGarbage() {
        assertNull(YearParser.parse("unknown"));
        assertNull(YearParser.parse("xx"));
    }

    @Test
    void returnsNullForTooFewDigits() {
        assertNull(YearParser.parse("80"));
        assertNull(YearParser.parse("198"));
    }

    @Test
    void returnsNullForOutOfRangeYears() {
        assertNull(YearParser.parse("0500"));
        assertNull(YearParser.parse("3500"));
    }

    @Test
    void prefersFirstFourDigitsWhenMultiplePresent() {
        assertEquals(1987, YearParser.parse("1987 (remastered 2009)"));
    }
}
