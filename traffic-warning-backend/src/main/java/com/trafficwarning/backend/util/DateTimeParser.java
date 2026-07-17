package com.trafficwarning.backend.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeParser {

    private static final DateTimeFormatter SPACE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeParser() {
    }

    public static LocalDateTime parseOrNow(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        String normalized = value.trim();
        if (normalized.contains("T")) {
            return LocalDateTime.parse(normalized);
        }
        return LocalDateTime.parse(normalized, SPACE_FORMATTER);
    }
}
