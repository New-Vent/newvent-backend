package com.newvent.participation.service;

import java.math.BigDecimal;
import java.util.function.Supplier;

import com.newvent.participation.exception.ParticipationException;

public final class ParticipationConfigReader {

    private ParticipationConfigReader() {
    }

    public static int readInteger(
        Object value,
        int min,
        int max,
        Supplier<ParticipationException> invalidConfig
    ) {
        if (!(value instanceof Number number)) {
            throw invalidConfig.get();
        }

        int result;

        try {
            result = new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalidConfig.get();
        }

        if (result < min || result > max) {
            throw invalidConfig.get();
        }

        return result;
    }

    public static String readPrizeName(
        Object value,
        Supplier<ParticipationException> invalidConfig
    ) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidConfig.get();
        }

        return text.trim();
    }
}
