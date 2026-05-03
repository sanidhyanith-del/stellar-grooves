package com.stellarideas.grooves.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses release-year strings from audio metadata into a normalized 4-digit Integer.
 * Tolerates the common shapes JAudioTagger surfaces: bare year ({@code "1987"}),
 * date-time ({@code "1987-05-12"}, {@code "1987/05/12T00:00:00"}), and whitespace.
 * Returns {@code null} for anything not matching a plausible year (1000–2999).
 */
public final class YearParser {

    private static final Pattern FIRST_FOUR_DIGITS = Pattern.compile("(\\d{4})");

    private YearParser() {}

    public static Integer parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        Matcher m = FIRST_FOUR_DIGITS.matcher(trimmed);
        if (!m.find()) return null;
        int year;
        try {
            year = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (year < 1000 || year > 2999) return null;
        return year;
    }
}
