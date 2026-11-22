package com.aitesting.shared.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** FrameworkApiSummaryTest — the reflection summary lists real methods only. */
@DisplayName("FrameworkApiSummary")
class FrameworkApiSummaryTest {

    private final String summary = FrameworkApiSummary.build();

    @Test
    @DisplayName("lists real methods of all four framework classes with parameter and return types")
    void containsKnownRealMethods() {
        assertAll(
                () -> assertTrue(summary.contains("class com.aitesting.shared.http.ApiClient"), summary),
                () -> assertTrue(summary.contains("static ApiClient DEFAULT"), summary),
                () -> assertTrue(summary.contains("get(String) -> Response"), summary),
                () -> assertTrue(summary.contains("post(String, Object) -> Response"), summary),
                () -> assertTrue(summary.contains("from(Response) -> static ResponseValidator<?>"), summary),
                () -> assertTrue(summary.contains("statusCode(int) -> ResponseValidator"), summary),
                () -> assertTrue(summary.contains("hasField(String) -> ResponseValidator"), summary),
                () -> assertTrue(summary.contains("randomId() -> static long"), summary),
                () -> assertTrue(summary.contains("step(String, Runnable) -> static void"), summary));
    }

    @Test
    @DisplayName("does not contain the methods the model invented in the first real run")
    void doesNotContainInventedMethods() {
        assertFalse(summary.contains("assertStatusCode"), summary);
        assertFalse(summary.contains("logStep"), summary);
    }
}
