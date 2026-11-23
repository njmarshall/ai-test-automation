package com.aitesting.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the strict positive-int rule used by LLM_MAX_TOKENS and LLM_MAX_TESTS_PER_CLASS. */
@DisplayName("EnvConfig.parsePositiveInt")
class EnvConfigTest {

    @Test
    @DisplayName("an empty LLM_MAX_TOKENS uses the default 16000")
    void empty_usesDefault() {
        assertEquals(16_000, EnvConfig.parsePositiveInt("LLM_MAX_TOKENS", "", 16_000));
    }

    @Test
    @DisplayName("a blank or missing value uses the default")
    void blankOrMissing_usesDefault() {
        assertEquals(16_000, EnvConfig.parsePositiveInt("LLM_MAX_TOKENS", "   ", 16_000));
        assertEquals(16_000, EnvConfig.parsePositiveInt("LLM_MAX_TOKENS", null, 16_000));
    }

    @Test
    @DisplayName("a number is used, surrounding spaces ignored")
    void number_isUsed() {
        assertEquals(12_000, EnvConfig.parsePositiveInt("LLM_MAX_TOKENS", " 12000 ", 16_000));
    }

    @Test
    @DisplayName("a value that is not a number fails with a message naming the key and value")
    void notANumber_failsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> EnvConfig.parsePositiveInt("LLM_MAX_TOKENS", "16k", 16_000));
        assertEquals("LLM_MAX_TOKENS must be a positive whole number, but was '16k'", e.getMessage());
    }

    @Test
    @DisplayName("zero or a negative number fails")
    void zeroOrNegative_failsFast() {
        assertThrows(IllegalStateException.class, () -> EnvConfig.parsePositiveInt("LLM_MAX_TESTS_PER_CLASS", "0", 8));
        assertThrows(IllegalStateException.class, () -> EnvConfig.parsePositiveInt("LLM_MAX_TESTS_PER_CLASS", "-3", 8));
    }
}
