package com.aitesting.insurance.api;

import com.aitesting.util.api.WireMockHelper;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.http.ApiClientFactory;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.Map;

/**
 * ClaimsTests — Insurance /claims endpoint test suite.
 *
 * Uses WireMock to simulate the Insurance API server.
 * This is the industry-standard approach for testing against
 * APIs that are not publicly available.
 *
 * Test coverage matrix:
 *   POST /claims           — FNOL happy path (201)
 *   POST /claims           — missing required field (400)
 *   POST /claims           — invalid policy ID (404)
 *   GET  /claims/{id}      — found (200)
 *   GET  /claims/{id}      — not found (404)
 *   GET  /claims/{id}      — async status polling (200 → processing → complete)
 *   PATCH /claims/{id}/close — close resolved claim (200)
 *   PATCH /claims/{id}/close — close already-closed claim (409)
 */
@Epic("Insurance API")
@Feature("Claims Endpoint")
public class ClaimsTests {

    // ── Test data ─────────────────────────────────────────────────────────────

    private static final String CLAIM_ID      = "CLM-2025-001234";
    private static final String POLICY_ID     = "POL-2025-056789";
    private static final String GHOST_ID      = "CLM-DOES-NOT-EXIST";

    // ── Stub responses ────────────────────────────────────────────────────────

    private static final String CLAIM_CREATED = """
        {
          "claimId":      "%s",
          "policyId":     "%s",
          "status":       "open",
          "incidentDate": "2025-03-20",
          "claimAmount":  8500.00,
          "description":  "Vehicle damage from collision"
        }
        """.formatted(CLAIM_ID, POLICY_ID);

    private static final String CLAIM_FOUND = """
        {
          "claimId":      "%s",
          "policyId":     "%s",
          "status":       "investigating",
          "incidentDate": "2025-03-20",
          "claimAmount":  8500.00,
          "paidAmount":   0.00
        }
        """.formatted(CLAIM_ID, POLICY_ID);

    private static final String CLAIM_PROCESSING = """
        {
          "claimId": "%s",
          "status":  "investigating"
        }
        """.formatted(CLAIM_ID);

    private static final String CLAIM_APPROVED = """
        {
          "claimId":    "%s",
          "policyId":   "%s",
          "status":     "approved",
          "claimAmount": 8500.00,
          "paidAmount":  7650.00
        }
        """.formatted(CLAIM_ID, POLICY_ID);

    private static final String CLAIM_CLOSED = """
        {
          "claimId": "%s",
          "status":  "closed"
        }
        """.formatted(CLAIM_ID);

    private static final String NOT_FOUND = """
        {
          "code":    404,
          "message": "Claim not found"
        }
        """;

    private static final String BAD_REQUEST = """
        {
          "code":    400,
          "message": "Missing required field: policyId"
        }
        """;

    private static final String CONFLICT = """
        {
          "code":    409,
          "message": "Claim is already closed"
        }
        """;

    private static final String POLICY_NOT_FOUND = """
        {
          "code":    404,
          "message": "Policy not found"
        }
        """;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    private static ApiClient api;
    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        WireMockHelper.start();
        api = ApiClientFactory.forWireMock();

        AllureHelper.description(
                "Insurance Claims API — FNOL, status tracking, " +
                        "async polling and claim closure. Uses WireMock mock server."
        );
    }

    @AfterClass(alwaysRun = true)
    public void suiteTeardown() {
        WireMockHelper.stop();
        System.clearProperty("BASE_URL");
    }

    @BeforeMethod(alwaysRun = true)
    public void resetStubs() {
        WireMockHelper.reset();
    }

    // ── POST /claims ──────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("File Claim — FNOL")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /claims with valid FNOL payload should return 201 with claim ID.")
    public void fileClaim_validFnol_returns201() {
        WireMockHelper.stubPost("/claims", 201, CLAIM_CREATED);

        Map<String, Object> payload = Map.of(
            "policyId",     POLICY_ID,
            "incidentDate", "2025-03-20",
            "description",  "Vehicle damage from collision",
            "claimAmount",  8500.00
        );
        AllureHelper.parameter("Policy ID", POLICY_ID);

        Response response = api.post("/claims", payload);
        AllureHelper.attachResponse("POST /claims", response);

        InsuranceResponseValidator.from(response)
            .statusCode(201)
            .withinSla()
            .hasClaimId()
            .hasValidClaimStatus()
            .claimAmountIsPositive()
            .fieldEquals("policyId", POLICY_ID);
    }

    @Test(priority = 2)
    @Story("File Claim — Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /claims missing policyId should return 400.")
    public void fileClaim_missingPolicyId_returns400() {
        WireMockHelper.stubPost("/claims", 400, BAD_REQUEST);

        Map<String, Object> payload = Map.of(
            "incidentDate", "2025-03-20",
            "description",  "Damage"
            // policyId intentionally omitted
        );

        Response response = api.post("/claims", payload);
        AllureHelper.attachResponse("POST /claims (missing policyId)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(400)
            .withinSla();
    }

    @Test(priority = 3)
    @Story("File Claim — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /claims with non-existent policy ID should return 404.")
    public void fileClaim_invalidPolicyId_returns404() {
        WireMockHelper.stubPost("/claims", 404, POLICY_NOT_FOUND);

        Map<String, Object> payload = Map.of(
            "policyId",     "POL-DOES-NOT-EXIST",
            "incidentDate", "2025-03-20",
            "description",  "Damage",
            "claimAmount",  5000.00
        );
        AllureHelper.parameter("Invalid Policy ID", "POL-DOES-NOT-EXIST");

        Response response = api.post("/claims", payload);
        AllureHelper.attachResponse("POST /claims (invalid policy)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }

    // ── GET /claims/{id} ──────────────────────────────────────────────────────

    @Test(priority = 4)
    @Story("Get Claim")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /claims/{id} for existing claim should return 200 with full details.")
    public void getClaimById_found_returns200() {
        WireMockHelper.stubGet("/claims/" + CLAIM_ID, 200, CLAIM_FOUND);
        AllureHelper.parameter("Claim ID", CLAIM_ID);

        Response response = api.get("/claims/" + CLAIM_ID);
        AllureHelper.attachResponse("GET /claims/" + CLAIM_ID, response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasCompleteClaimStructure()
            .fieldEquals("claimId", CLAIM_ID)
            .fieldEquals("policyId", POLICY_ID);
    }

    @Test(priority = 5)
    @Story("Get Claim")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /claims/{id} for non-existent claim should return 404.")
    public void getClaimById_notFound_returns404() {
        WireMockHelper.stubGet("/claims/" + GHOST_ID, 404, NOT_FOUND);
        AllureHelper.parameter("Ghost Claim ID", GHOST_ID);

        Response response = api.get("/claims/" + GHOST_ID);
        AllureHelper.attachResponse("GET /claims (not found)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }

    // ── Async polling — claim adjudication ───────────────────────────────────

    @Test(priority = 6)
    @Story("Claim Adjudication — Async")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Claim adjudication uses async polling — status moves " +
                 "from investigating → approved. Tests polling pattern.")
    public void claimAdjudication_pollUntilApproved() {
        // Stub: 2 processing responses then final approved
        WireMockHelper.stubPolling(
            "/claims/" + CLAIM_ID,
            CLAIM_PROCESSING,
            CLAIM_APPROVED,
            2
        );
        AllureHelper.parameter("Claim ID", CLAIM_ID);
        AllureHelper.parameter("Pattern", "Polling — investigating → approved");

        // Poll until approved (max 10s, every 1s)
        Response finalResponse = pollUntilStatus(
            "/claims/" + CLAIM_ID,
            "approved",
            10, 1
        );
        AllureHelper.attachResponse("Final adjudication", finalResponse);

        InsuranceResponseValidator.from(finalResponse)
            .statusCode(200)
            .fieldEquals("status", "approved")
            .claimAmountIsPositive();
    }

    // ── PATCH /claims/{id}/close ──────────────────────────────────────────────

    @Test(priority = 7)
    @Story("Close Claim")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PATCH /claims/{id}/close for open claim should return 200.")
    public void closeClaim_openClaim_returns200() {
        WireMockHelper.stubPatch("/claims/" + CLAIM_ID + "/close",
            200, CLAIM_CLOSED);
        AllureHelper.parameter("Claim ID", CLAIM_ID);

        Response response = api.patch("/claims/" + CLAIM_ID + "/close", "{}");
        AllureHelper.attachResponse("PATCH /claims/close", response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .fieldEquals("status", "closed");
    }

    @Test(priority = 8)
    @Story("Close Claim")
    @Severity(SeverityLevel.NORMAL)
    @Description("PATCH /claims/{id}/close for already-closed claim should return 409 Conflict.")
    public void closeClaim_alreadyClosed_returns409() {
        WireMockHelper.stubPatch("/claims/" + CLAIM_ID + "/close",
            409, CONFLICT);
        AllureHelper.parameter("Claim ID", CLAIM_ID);

        Response response = api.patch("/claims/" + CLAIM_ID + "/close", "{}");
        AllureHelper.attachResponse("PATCH /claims/close (conflict)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(409)
            .withinSla();
    }

    // ── Polling helper ────────────────────────────────────────────────────────

    /**
     * Polls an endpoint until the expected status field value is reached.
     * Insurance-specific polling for claim adjudication workflows.
     */
    private Response pollUntilStatus(
            String path, String expectedStatus,
            int maxWaitSeconds, int intervalSeconds) {

        long deadline = System.currentTimeMillis()
                      + (maxWaitSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            Response response = api.get(path);
            String current = response.jsonPath().getString("status");

            AllureHelper.step("Polling " + path + " — status: " + current,
                () -> {});

            if (expectedStatus.equals(current)) {
                return response;
            }

            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw new AssertionError(
            "Timeout after " + maxWaitSeconds + "s waiting for status="
            + expectedStatus + " at " + path);
    }
}
