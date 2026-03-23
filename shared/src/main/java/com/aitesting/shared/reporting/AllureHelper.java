package com.aitesting.shared.reporting;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * AllureHelper provides convenience wrappers around the Allure Java API.
 *
 * Every test class can call AllureHelper to:
 *   - Attach request/response bodies to the Allure HTML report
 *   - Add named steps to the test timeline
 *   - Log structured test parameters for parametrised tests
 *   - Mark tests with dynamic severity, issue links, or descriptions
 *
 * Usage:
 *   AllureHelper.attachResponse("Create Pet", response);
 *   AllureHelper.step("Verify pet ID is returned", () -> {
 *       // assertions here
 *   });
 */
public final class AllureHelper {

    private static final Logger log = LoggerFactory.getLogger(AllureHelper.class);

    // ── Response attachment ───────────────────────────────────────────────────

    /**
     * Attaches the full HTTP response (status, headers, body) to the Allure report.
     * Call this on every API response so failures are self-documenting.
     */
    public static void attachResponse(String stepName, Response response) {
        String content = String.format(
                "=== %s ===\nStatus: %d\nTime: %dms\nContent-Type: %s\n\nBody:\n%s",
                stepName,
                response.getStatusCode(),
                response.getTime(),
                response.getContentType(),
                response.asPrettyString()
        );
        Allure.addAttachment(stepName + " — response",
                "text/plain",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                ".txt");
        log.debug("Allure attachment added: {}", stepName);
    }

    /**
     * Attaches a request body string to the Allure report.
     */
    public static void attachRequest(String stepName, String requestBody) {
        Allure.addAttachment(stepName + " — request",
                "application/json",
                new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8)),
                ".json");
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    /**
     * Wraps logic in a named Allure step.
     * The step appears as a collapsible node in the Allure timeline.
     *
     * Usage:
     *   AllureHelper.step("Create a pet via POST /pet", () -> {
     *       response = ApiClient.post("/pet", payload);
     *   });
     */
    public static void step(String name, Runnable action) {
        Allure.step(name, action::run);
    }

    // ── Parameters ────────────────────────────────────────────────────────────

    /**
     * Logs a named parameter in the Allure report — great for data-driven tests.
     *
     * Usage:
     *   AllureHelper.parameter("Pet Status", "available");
     *   AllureHelper.parameter("Pet ID", petId);
     */
    public static void parameter(String name, Object value) {
        Allure.parameter(name, String.valueOf(value));
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    /**
     * Adds a description to the current test in the Allure report.
     */
    public static void description(String text) {
        Allure.description(text);
    }

    /**
     * Links the current test to an issue tracker URL.
     */
    public static void issue(String url) {
        Allure.issue("Issue", url);
    }

    /**
     * Adds a plain text note to the current test.
     */
    public static void note(String message) {
        Allure.addAttachment("Note", "text/plain",
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)), ".txt");
    }

    /** Prevent instantiation. */
    private AllureHelper() {}
}
