package com.aitesting.api.util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * WireMockHelper — test utility for mock server lifecycle and stubbing.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LOCATION: api/src/test/java — NOT shared/
 * ═══════════════════════════════════════════════════════════════════════
 *
 * WireMock is a test-scoped dependency used only by industry capstones
 * (Insurance, Healthcare, Payment) that don't have public APIs.
 * It belongs in api/ not shared/ because:
 *   → shared/ compiles to a jar used by all projects
 *   → WireMock is test-only — no place in production shared library
 *   → api/pom.xml holds the WireMock dependency (test scope)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * PURPOSE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Industry APIs (Insurance, Healthcare, Payment) are not publicly
 * available like PetStore. WireMock provides a local HTTP server that
 * simulates real API responses, enabling:
 *
 *   → Tests that run without a real API server
 *   → Deterministic responses (no flakiness)
 *   → Testing error scenarios (500s, timeouts, 404s)
 *   → Testing async patterns (polling, webhooks)
 *   → Industry-standard contract testing approach
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // In @BeforeClass:
 *   WireMockHelper.start();
 *
 *   // Stub responses:
 *   WireMockHelper.stubPost("/quotes", 200, responseBody);
 *   WireMockHelper.stubGet("/quotes/QT-123", 200, quoteJson);
 *   WireMockHelper.stubGet("/quotes/MISSING", 404, errorJson);
 *
 *   // Reset between tests:
 *   WireMockHelper.reset();
 *
 *   // In @AfterClass:
 *   WireMockHelper.stop();
 */
public final class WireMockHelper {

    private static final Logger log =
        LoggerFactory.getLogger(WireMockHelper.class);

    /** Default port — matches BASE_URL in insurance.properties */
    public static final int PORT = 8089;

    /** Base URL — point ApiClient here for WireMock tests */
    public static final String BASE_URL =
        "http://localhost:" + PORT;

    private static WireMockServer server;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the WireMock server on the configured port.
     * Call from @BeforeClass in test suites that need a mock API.
     */
    public static void start() {
        if (server != null && server.isRunning()) {
            log.debug("WireMock already running on port {}", PORT);
            return;
        }
        server = new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                .port(PORT)
                .notifier(new WireMockSlf4jNotifier())
        );
        server.start();
        WireMock.configureFor("localhost", PORT);
        log.info("WireMock server started on port {}", PORT);
    }

    /**
     * Stops the WireMock server.
     * Call from @AfterClass.
     */
    public static void stop() {
        if (server != null && server.isRunning()) {
            server.stop();
            log.info("WireMock server stopped");
        }
    }

    /**
     * Resets all stubs and request history.
     * Call from @BeforeMethod to ensure clean state per test.
     */
    public static void reset() {
        if (server != null && server.isRunning()) {
            server.resetAll();
            log.debug("WireMock stubs reset");
        }
    }

    // ── Stubbing helpers ──────────────────────────────────────────────────────

    /** Stub a POST endpoint. */
    public static void stubPost(
            String path, int statusCode, String responseBody) {
        stubFor(post(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
        log.debug("Stubbed POST {} → {}", path, statusCode);
    }

    /** Stub a GET endpoint. */
    public static void stubGet(
            String path, int statusCode, String responseBody) {
        stubFor(get(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
        log.debug("Stubbed GET {} → {}", path, statusCode);
    }

    /** Stub a PATCH endpoint. */
    public static void stubPatch(
            String path, int statusCode, String responseBody) {
        stubFor(patch(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
        log.debug("Stubbed PATCH {} → {}", path, statusCode);
    }

    /** Stub a DELETE endpoint. */
    public static void stubDelete(
            String path, int statusCode, String responseBody) {
        stubFor(delete(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
        log.debug("Stubbed DELETE {} → {}", path, statusCode);
    }

    /**
     * Stub async polling — first N calls return processing body,
     * then final call returns complete body.
     *
     * @param path            polling endpoint path
     * @param processingBody  JSON body while processing
     * @param finalBody       JSON body when complete
     * @param processingCount number of processing responses before final
     */
    public static void stubPolling(
            String path,
            String processingBody,
            String finalBody,
            int processingCount) {

        for (int i = 0; i < processingCount; i++) {
            stubFor(get(urlEqualTo(path))
                .inScenario("polling-" + path)
                .whenScenarioStateIs(i == 0 ? "Started" : "poll-" + i)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(processingBody))
                .willSetStateTo(i == processingCount - 1
                    ? "complete" : "poll-" + (i + 1)));
        }

        stubFor(get(urlEqualTo(path))
            .inScenario("polling-" + path)
            .whenScenarioStateIs("complete")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(finalBody)));

        log.debug("Stubbed polling for {} ({} steps)", path, processingCount);
    }

    /**
     * Stub a slow response to test timeout handling.
     */
    public static void stubWithDelay(
            String path, int statusCode,
            String responseBody, int delayMs) {
        stubFor(get(urlEqualTo(path))
            .willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)
                .withFixedDelay(delayMs)));
        log.debug("Stubbed slow GET {} → {}ms delay", path, delayMs);
    }

    /** Returns true if WireMock server is running. */
    public static boolean isRunning() {
        return server != null && server.isRunning();
    }

    // ── SLF4J notifier ────────────────────────────────────────────────────────

    /**
     * Routes WireMock internal logging through SLF4J
     * so it respects logback-test.xml configuration.
     */
    private static class WireMockSlf4jNotifier
            implements com.github.tomakehurst.wiremock.common.Notifier {

        private static final Logger wmLog =
            LoggerFactory.getLogger("WireMock");

        @Override public void info(String message)  { wmLog.debug(message); }
        @Override public void error(String message) { wmLog.error(message); }
        @Override public void error(String message, Throwable t) {
            wmLog.error(message, t);
        }
    }

    private WireMockHelper() {}
}
