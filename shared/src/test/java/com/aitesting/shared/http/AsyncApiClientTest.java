package com.aitesting.shared.http;

import com.aitesting.shared.reporting.AllureHelper;
import io.restassured.response.Response;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AsyncApiClientTest — unit tests for AsyncApiClient polling utilities.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY THESE TESTS EXIST
 * ═══════════════════════════════════════════════════════════════════════
 *
 * AsyncApiClient is the shared polling backbone used by every domain
 * in this repo — healthcare prior-auth, insurance MVR, fintech ACH.
 * Before these tests, shared/http had zero unit coverage.
 *
 * Directly mirrors the Python equivalent: test_async_poller.py
 * Both suites verify the same three invariants:
 *   1. Returns immediately when expected status is reached
 *   2. Throws AssertionError on terminal failure (never polls again)
 *   3. Throws AssertionError on timeout after N attempts
 *
 * ═══════════════════════════════════════════════════════════════════════
 * TECHNIQUE: MockedStatic for Thread.sleep + AllureHelper
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Thread.sleep() and AllureHelper.step() are patched as no-ops so:
 *   - Tests run in milliseconds (no real sleeping)
 *   - No Allure context is required on the classpath at test time
 *   - Backoff doubling is verified via sleep call argument capture
 *
 * ApiClient.get() is mocked via @Mock — each test configures the
 * exact sequence of status values the mock returns.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncApiClient")
class AsyncApiClientTest {

    @Mock
    private ApiClient api;

    @Mock
    private Response response;

    @Mock
    private JsonPath jsonPath;

    // ─── pollUntil ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pollUntil")
    class PollUntilTests {

        @Test
        @DisplayName("returns response immediately when first poll returns expected status")
        void returnsOnFirstAttempt() throws Exception {
            when(api.get("/status/123")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("approved");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntil(api, "/status/123", "status", "approved", 30, 1)
            );

            assertSame(response, result);
            // Only one GET issued — no retry needed
            verify(api, times(1)).get("/status/123");
        }

        @Test
        @DisplayName("retries until expected status reached, then returns")
        void retriesAndSucceeds() throws Exception {
            when(api.get("/status/123")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            // First two polls return "processing", third returns "approved"
            when(jsonPath.getString("status"))
                .thenReturn("processing")
                .thenReturn("processing")
                .thenReturn("approved");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntil(api, "/status/123", "status", "approved", 30, 1)
            );

            assertSame(response, result);
            verify(api, times(3)).get("/status/123");
        }

        @Test
        @DisplayName("throws AssertionError immediately on 'failed' terminal status")
        void throwsOnTerminalFailed() {
            when(api.get("/status/PA-001")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("failed");

            AssertionError err = assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/status/PA-001", "status", "approved", 30, 1)
                )
            );

            assertTrue(err.getMessage().contains("failed"),
                "Error message should name the terminal status");
            assertTrue(err.getMessage().contains("approved"),
                "Error message should name the expected status");
            // Stops after one poll — does not retry past terminal status
            verify(api, times(1)).get("/status/PA-001");
        }

        @Test
        @DisplayName("throws AssertionError on 'rejected' terminal status")
        void throwsOnTerminalRejected() {
            when(api.get("/claims/C-99")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("rejected");

            assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/claims/C-99", "status", "approved", 30, 1)
                )
            );
        }

        @Test
        @DisplayName("throws AssertionError on 'cancelled' terminal status")
        void throwsOnTerminalCancelled() {
            when(api.get("/plans/P-42")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("cancelled");

            assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/plans/P-42", "status", "completed", 30, 1)
                )
            );
        }

        @Test
        @DisplayName("throws AssertionError on 'error' terminal status")
        void throwsOnTerminalError() {
            when(api.get("/kyc/K-01")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("error");

            assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/kyc/K-01", "status", "verified", 30, 1)
                )
            );
        }

        @Test
        @DisplayName("throws AssertionError on 'denied' — does NOT block disbursement domains")
        void throwsOnTerminalDenied() {
            // 'denied' is a terminal failure — e.g. prior auth denied
            when(api.get("/auth/A-7")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("denied");

            assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/auth/A-7", "status", "approved", 30, 1)
                )
            );
        }

        @Test
        @DisplayName("null status is not treated as terminal — polling continues")
        void nullStatusContinuesPolling() throws Exception {
            when(api.get("/status/null-case")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            // null first (transient), then success
            when(jsonPath.getString("status"))
                .thenReturn(null)
                .thenReturn("approved");

            // Should not throw — null is not a terminal state
            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntil(api, "/status/null-case", "status", "approved", 30, 1)
            );
            assertSame(response, result);
        }

        @Test
        @DisplayName("throws AssertionError with timeout message when deadline expires")
        void throwsOnTimeout() {
            when(api.get("/slow/S-1")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            // Always returns "processing" — never reaches "approved"
            when(jsonPath.getString("status")).thenReturn("processing");

            // maxWaitSeconds=0 → deadline is in the past immediately
            AssertionError err = assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/slow/S-1", "status", "approved", 0, 1)
                )
            );

            assertTrue(err.getMessage().toLowerCase().contains("timeout")
                || err.getMessage().contains("0s"),
                "Timeout message should mention timeout or 0s: " + err.getMessage());
        }

        @Test
        @DisplayName("error message names the path that timed out")
        void timeoutMessageIncludesPath() {
            when(api.get("/slow/S-2")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("pending");

            AssertionError err = assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/slow/S-2", "status", "done", 0, 1)
                )
            );

            assertTrue(err.getMessage().contains("/slow/S-2"),
                "Timeout error should include the path: " + err.getMessage());
        }
    }

    // ─── pollUntilTerminal ────────────────────────────────────────────────────

    @Nested
    @DisplayName("pollUntilTerminal")
    class PollUntilTerminalTests {

        @Test
        @DisplayName("returns on first terminal status in list")
        void returnsOnFirstTerminalStatus() throws Exception {
            when(api.get("/claims/CLM-1")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("approved");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntilTerminal(
                    api, "/claims/CLM-1", "status",
                    List.of("approved", "denied", "closed"),
                    30, 1)
            );

            assertSame(response, result);
            verify(api, times(1)).get("/claims/CLM-1");
        }

        @Test
        @DisplayName("returns on any status in the terminal list — second option")
        void returnsOnSecondTerminalOption() throws Exception {
            when(api.get("/claims/CLM-2")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("denied");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntilTerminal(
                    api, "/claims/CLM-2", "status",
                    List.of("approved", "denied", "closed"),
                    30, 1)
            );

            assertSame(response, result);
        }

        @Test
        @DisplayName("retries while status is not in terminal list")
        void retriesUntilTerminalReached() throws Exception {
            when(api.get("/claims/CLM-3")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status"))
                .thenReturn("pending")
                .thenReturn("in-review")
                .thenReturn("closed");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntilTerminal(
                    api, "/claims/CLM-3", "status",
                    List.of("approved", "denied", "closed"),
                    30, 1)
            );

            assertSame(response, result);
            verify(api, times(3)).get("/claims/CLM-3");
        }

        @Test
        @DisplayName("throws AssertionError on timeout with list of expected terminals")
        void throwsOnTimeout() {
            when(api.get("/mvr/M-1")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("running");

            AssertionError err = assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntilTerminal(
                        api, "/mvr/M-1", "status",
                        List.of("clean", "flagged"),
                        0, 1)
                )
            );

            // Message should reference one of the expected statuses
            String msg = err.getMessage();
            assertTrue(msg.contains("clean") || msg.contains("flagged"),
                "Timeout message should mention terminal statuses: " + msg);
        }

        @Test
        @DisplayName("handles single-element terminal list")
        void singleElementTerminalList() throws Exception {
            when(api.get("/ach/ACH-1")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("settled");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollUntilTerminal(
                    api, "/ach/ACH-1", "status",
                    List.of("settled"),
                    30, 1)
            );

            assertSame(response, result);
        }
    }

    // ─── pollWithBackoff ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("pollWithBackoff")
    class PollWithBackoffTests {

        @Test
        @DisplayName("returns response when expected status reached on first attempt")
        void returnsImmediatelyOnFirstAttempt() throws Exception {
            when(api.get("/auth/PA-1")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("approved");

            Response result = withSuppressedSideEffects(() ->
                AsyncApiClient.pollWithBackoff(
                    api, "/auth/PA-1", "status", "approved",
                    300, 2, 30)
            );

            assertSame(response, result);
            verify(api, times(1)).get("/auth/PA-1");
        }

        @Test
        @DisplayName("doubles sleep interval on each retry up to max")
        void backoffIntervalDoublesUpToMax() throws Exception {
            when(api.get("/auth/PA-2")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            // pending × 4, then approved
            when(jsonPath.getString("status"))
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("approved");

            // Capture sleep durations to verify backoff progression
            AtomicInteger callCount = new AtomicInteger(0);
            long[] sleepArgs = new long[5];

            try (MockedStatic<Thread> threadMock = mockStatic(Thread.class);
                 MockedStatic<AllureHelper> alluMock = mockStatic(AllureHelper.class)) {

                threadMock.when(() -> Thread.sleep(anyLong()))
                    .thenAnswer(inv -> {
                        long ms = inv.getArgument(0);
                        int i = callCount.getAndIncrement();
                        if (i < sleepArgs.length) sleepArgs[i] = ms;
                        return null;
                    });

                alluMock.when(() -> AllureHelper.step(anyString(), any()))
                    .thenAnswer(inv -> null);

                AsyncApiClient.pollWithBackoff(
                    api, "/auth/PA-2", "status", "approved",
                    300, 2, 30);
            }

            // Intervals: 2s, 4s, 8s, 16s (before hitting max of 30s)
            // Thread.sleep receives milliseconds: 2000, 4000, 8000, 16000
            assertEquals(2_000L, sleepArgs[0], "First interval: 2s");
            assertEquals(4_000L, sleepArgs[1], "Second interval: 4s (doubled)");
            assertEquals(8_000L, sleepArgs[2], "Third interval: 8s (doubled)");
            assertEquals(16_000L, sleepArgs[3], "Fourth interval: 16s (doubled)");
        }

        @Test
        @DisplayName("caps backoff interval at maxIntervalSeconds")
        void backoffCapsAtMaxInterval() throws Exception {
            when(api.get("/auth/PA-3")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            // 6 pendings, then success — enough to hit and stay at cap
            when(jsonPath.getString("status"))
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("pending")
                .thenReturn("approved");

            AtomicInteger callCount = new AtomicInteger(0);
            long[] sleepArgs = new long[7];

            try (MockedStatic<Thread> threadMock = mockStatic(Thread.class);
                 MockedStatic<AllureHelper> alluMock = mockStatic(AllureHelper.class)) {

                threadMock.when(() -> Thread.sleep(anyLong()))
                    .thenAnswer(inv -> {
                        long ms = inv.getArgument(0);
                        int i = callCount.getAndIncrement();
                        if (i < sleepArgs.length) sleepArgs[i] = ms;
                        return null;
                    });

                alluMock.when(() -> AllureHelper.step(anyString(), any()))
                    .thenAnswer(inv -> null);

                AsyncApiClient.pollWithBackoff(
                    api, "/auth/PA-3", "status", "approved",
                    300, 2, 5);  // max cap = 5s
            }

            // 2s, 4s, then capped at 5s, 5s, 5s, 5s
            assertEquals(2_000L, sleepArgs[0], "First: 2s");
            assertEquals(4_000L, sleepArgs[1], "Second: 4s");
            assertEquals(5_000L, sleepArgs[2], "Third: capped at 5s");
            assertEquals(5_000L, sleepArgs[3], "Fourth: stays at 5s cap");
            assertEquals(5_000L, sleepArgs[4], "Fifth: stays at 5s cap");
            assertEquals(5_000L, sleepArgs[5], "Sixth: stays at 5s cap");
        }

        @Test
        @DisplayName("throws AssertionError on terminal failure — stops backoff")
        void throwsOnTerminalFailureStopsBackoff() {
            when(api.get("/ach/A-fail")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("failed");

            AssertionError err = assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollWithBackoff(
                        api, "/ach/A-fail", "status", "settled",
                        300, 2, 30)
                )
            );

            assertTrue(err.getMessage().contains("failed"),
                "Error should name the terminal status");
            // Never sleeps — stops immediately on first terminal poll
            verify(api, times(1)).get("/ach/A-fail");
        }

        @Test
        @DisplayName("throws AssertionError with path on timeout")
        void throwsOnTimeoutIncludesPath() {
            when(api.get("/auth/PA-timeout")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("pending");

            AssertionError err = assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollWithBackoff(
                        api, "/auth/PA-timeout", "status", "approved",
                        0, 2, 30)
                )
            );

            assertTrue(err.getMessage().contains("/auth/PA-timeout")
                || err.getMessage().contains("approved"),
                "Timeout message should reference path or expected status: " + err.getMessage());
        }
    }

    // ─── Terminal status completeness ─────────────────────────────────────────

    @Nested
    @DisplayName("isTerminalFailure — all recognised terminal statuses")
    class TerminalStatusTests {

        /**
         * These six statuses represent the hardcoded terminal set in AsyncApiClient.
         * If any is removed or renamed, these tests surface the regression immediately.
         *
         * Domain mapping:
         *   "failed"    → payment/ACH failure, fintech
         *   "error"     → system error, any domain
         *   "rejected"  → insurance underwriting
         *   "cancelled" → appointment cancellation, plan cancellation
         *   "expired"   → prior auth expiry, token expiry
         *   "denied"    → prior authorization denied, insurance claim denied
         */
        private void assertIsTerminal(String status) {
            when(api.get("/probe")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn(status);

            assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/probe", "status", "approved", 30, 1)
                ),
                "'" + status + "' should be treated as a terminal failure"
            );
        }

        @Test @DisplayName("'failed' is terminal")   void failedIsTerminal()    { assertIsTerminal("failed"); }
        @Test @DisplayName("'error' is terminal")    void errorIsTerminal()     { assertIsTerminal("error"); }
        @Test @DisplayName("'rejected' is terminal") void rejectedIsTerminal()  { assertIsTerminal("rejected"); }
        @Test @DisplayName("'cancelled' is terminal") void cancelledIsTerminal() { assertIsTerminal("cancelled"); }
        @Test @DisplayName("'expired' is terminal")  void expiredIsTerminal()   { assertIsTerminal("expired"); }
        @Test @DisplayName("'denied' is terminal")   void deniedIsTerminal()    { assertIsTerminal("denied"); }

        @Test
        @DisplayName("'FAILED' (uppercase) is also terminal — case-insensitive check")
        void uppercaseTerminalIsHandled() {
            when(api.get("/probe")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status")).thenReturn("FAILED");

            assertThrows(AssertionError.class, () ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/probe", "status", "approved", 30, 1)
                )
            );
        }

        @Test
        @DisplayName("'processing', 'pending', 'in-review' are NOT terminal")
        void nonTerminalStatusesContinuePolling() {
            // For this test, the second call returns success — verifying
            // the non-terminal status does not throw
            when(api.get("/probe")).thenReturn(response);
            when(response.jsonPath()).thenReturn(jsonPath);
            when(jsonPath.getString("status"))
                .thenReturn("processing")
                .thenReturn("pending")
                .thenReturn("in-review")
                .thenReturn("approved");

            assertDoesNotThrow(() ->
                withSuppressedSideEffects(() ->
                    AsyncApiClient.pollUntil(api, "/probe", "status", "approved", 30, 1)
                )
            );
        }
    }

    // ─── Shared helper ────────────────────────────────────────────────────────

    /**
     * Runs the given supplier with Thread.sleep and AllureHelper.step
     * both mocked as no-ops. This prevents actual sleeping (tests run in ms)
     * and avoids requiring an Allure test context.
     *
     * For backoff tests that need to inspect the sleep arguments, use
     * MockedStatic directly instead of this helper.
     */
    @FunctionalInterface
    interface PollingSupplier<T> {
        T get() throws Exception;
    }

    private <T> T withSuppressedSideEffects(PollingSupplier<T> supplier) throws Exception {
        try (MockedStatic<Thread> threadMock = mockStatic(Thread.class);
             MockedStatic<AllureHelper> alluMock = mockStatic(AllureHelper.class)) {

            // Thread.sleep() → no-op (no actual sleeping)
            threadMock.when(() -> Thread.sleep(anyLong()))
                .thenAnswer(inv -> null);

            // AllureHelper.step() → no-op (no Allure context needed)
            alluMock.when(() -> AllureHelper.step(anyString(), any()))
                .thenAnswer(inv -> null);

            return supplier.get();
        }
    }
}
