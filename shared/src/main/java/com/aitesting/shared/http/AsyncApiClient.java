package com.aitesting.shared.http;

import com.aitesting.shared.reporting.AllureHelper;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AsyncApiClient — handles async API patterns for test automation.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY THIS EXISTS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Many enterprise APIs are asynchronous by design:
 *
 *   Healthcare:
 *     → Prior Authorization decisions (minutes to hours)
 *     → Lab result processing
 *     → Claims adjudication
 *
 *   Insurance:
 *     → MVR (Motor Vehicle Record) checks
 *     → Credit bureau checks
 *     → Fraud screening
 *
 *   Fintech:
 *     → Payment processing
 *     → ACH bank transfers
 *     → KYC verification
 *
 * These APIs follow one of two patterns:
 *   1. Polling  — client repeatedly checks status endpoint
 *   2. Webhook  — server POSTs result to client callback URL
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // Pattern 1 — Polling:
 *   ApiClient api = ApiClientFactory.forWireMock();
 *   Response result = AsyncApiClient.pollUntil(
 *       api,
 *       "/prior-auth/PA-123",
 *       "status",
 *       "approved",
 *       60,   // max wait seconds
 *       2     // poll interval seconds
 *   );
 *
 *   // Pattern 2 — Poll until any terminal state:
 *   Response result = AsyncApiClient.pollUntilTerminal(
 *       api,
 *       "/claims/CLM-123",
 *       "status",
 *       List.of("approved", "denied", "closed"),
 *       300,  // 5 minute timeout for claims
 *       10    // poll every 10 seconds
 *   );
 */
public final class AsyncApiClient {

    private static final Logger log =
        LoggerFactory.getLogger(AsyncApiClient.class);

    // ── Polling ───────────────────────────────────────────────────────────────

    /**
     * Polls an endpoint until the expected status field value is reached.
     *
     * @param api             ApiClient instance (primary or WireMock)
     * @param path            endpoint path to poll
     * @param statusField     JSON path of the status field
     * @param expectedStatus  value to wait for
     * @param maxWaitSeconds  maximum wait time before timeout
     * @param intervalSeconds seconds between polls
     * @return the final Response when expected status is reached
     * @throws AssertionError if timeout expires or terminal failure reached
     */
    public static Response pollUntil(
            ApiClient api,
            String path,
            String statusField,
            String expectedStatus,
            int maxWaitSeconds,
            int intervalSeconds) {

        log.info("Polling {} — waiting for {}={} (max {}s, interval {}s)",
            path, statusField, expectedStatus,
            maxWaitSeconds, intervalSeconds);

        long deadline = System.currentTimeMillis()
                      + (maxWaitSeconds * 1000L);
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            Response response = api.get(path);
            String current = response.jsonPath().getString(statusField);

            log.debug("Poll attempt {} — {}: {}", attempt, statusField, current);

            AllureHelper.step(
                "Poll " + attempt + " — " + statusField + ": " + current,
                () -> {}
            );

            if (expectedStatus.equals(current)) {
                log.info("Expected status '{}' reached after {} attempts",
                    expectedStatus, attempt);
                return response;
            }

            if (isTerminalFailure(current)) {
                throw new AssertionError(
                    "Terminal failure status '" + current
                    + "' reached while waiting for '"
                    + expectedStatus + "' at " + path);
            }

            sleep(intervalSeconds);
        }

        throw new AssertionError(
            "Timeout after " + maxWaitSeconds + "s ("
            + attempt + " attempts) waiting for "
            + statusField + "='" + expectedStatus
            + "' at " + path);
    }

    /**
     * Polls until ANY of the provided terminal states is reached.
     * Use when multiple outcomes are acceptable
     * (e.g. "approved" OR "denied" OR "referred").
     *
     * @param api              ApiClient instance
     * @param path             endpoint path to poll
     * @param statusField      JSON path of the status field
     * @param terminalStatuses list of acceptable terminal values
     * @param maxWaitSeconds   maximum wait time
     * @param intervalSeconds  seconds between polls
     * @return the final Response when any terminal state is reached
     */
    public static Response pollUntilTerminal(
            ApiClient api,
            String path,
            String statusField,
            List<String> terminalStatuses,
            int maxWaitSeconds,
            int intervalSeconds) {

        log.info("Polling {} — waiting for any of {} (max {}s)",
            path, terminalStatuses, maxWaitSeconds);

        long deadline = System.currentTimeMillis()
                      + (maxWaitSeconds * 1000L);
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            Response response = api.get(path);
            String current = response.jsonPath().getString(statusField);

            log.debug("Poll attempt {} — {}: {}", attempt, statusField, current);

            AllureHelper.step(
                "Poll " + attempt + " — " + statusField + ": " + current,
                () -> {}
            );

            if (terminalStatuses.contains(current)) {
                log.info("Terminal status '{}' reached after {} attempts",
                    current, attempt);
                return response;
            }

            sleep(intervalSeconds);
        }

        throw new AssertionError(
            "Timeout after " + maxWaitSeconds + "s ("
            + attempt + " attempts) waiting for any of "
            + terminalStatuses + " at " + path);
    }

    /**
     * Polls with exponential backoff — ideal for long-running async ops.
     * Starts at initialIntervalSeconds, doubles each attempt
     * up to maxIntervalSeconds.
     *
     * Example for prior auth (can take minutes):
     *   pollWithBackoff(api, path, "status", "approved",
     *                   300, 2, 30)
     *   → polls at 2s, 4s, 8s, 16s, 30s, 30s, 30s...
     *
     * @param api                    ApiClient instance
     * @param path                   endpoint path
     * @param statusField            JSON path of status field
     * @param expectedStatus         value to wait for
     * @param maxWaitSeconds         total timeout
     * @param initialIntervalSeconds starting interval
     * @param maxIntervalSeconds     maximum interval cap
     */
    public static Response pollWithBackoff(
            ApiClient api,
            String path,
            String statusField,
            String expectedStatus,
            int maxWaitSeconds,
            int initialIntervalSeconds,
            int maxIntervalSeconds) {

        log.info("Polling {} with backoff — waiting for {}={}",
            path, statusField, expectedStatus);

        long deadline = System.currentTimeMillis()
                      + (maxWaitSeconds * 1000L);
        int interval = initialIntervalSeconds;
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            Response response = api.get(path);
            String current = response.jsonPath().getString(statusField);

            log.debug("Backoff poll {} (interval {}s) — {}: {}",
                attempt, interval, statusField, current);

            AllureHelper.step(
                "Backoff poll " + attempt
                + " (interval " + interval + "s) — "
                + statusField + ": " + current,
                () -> {}
            );

            if (expectedStatus.equals(current)) {
                log.info("Status '{}' reached after {} attempts",
                    expectedStatus, attempt);
                return response;
            }

            if (isTerminalFailure(current)) {
                throw new AssertionError(
                    "Terminal failure '" + current
                    + "' reached at " + path);
            }

            sleep(interval);

            // Exponential backoff — double interval, cap at max
            interval = Math.min(interval * 2, maxIntervalSeconds);
        }

        throw new AssertionError(
            "Timeout after " + maxWaitSeconds + "s waiting for '"
            + expectedStatus + "' at " + path);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Common terminal failure statuses across healthcare and insurance.
     * Override in domain-specific subclasses if needed.
     */
    private static boolean isTerminalFailure(String status) {
        if (status == null) return false;
        return switch (status.toLowerCase()) {
            case "failed", "error", "rejected",
                 "cancelled", "expired",
                 "denied" -> true;
            default -> false;
        };
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Polling interrupted");
        }
    }

    private AsyncApiClient() {}
}
