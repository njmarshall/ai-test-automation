package com.aitesting.insurance.api;

import com.aitesting.util.api.WireMockHelper;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.http.ApiClientFactory;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.*;

/**
 * PolicyTests — Insurance /policies endpoint test suite.
 *
 * Uses WireMock to simulate the Insurance Policy API.
 *
 * Test coverage:
 *   POST /policies               — bind approved quote (201 active)
 *   POST /policies               — invalid quote ID (404)
 *   POST /policies               — backdated effective date (400)
 *   POST /policies               — annual vs monthly premium comparison
 *   GET  /policies/{id}          — found (200)
 *   GET  /policies/{id}          — not found (404)
 *   PATCH /policies/{id}/cancel  — cancel active policy (200)
 *   PATCH /policies/{id}/cancel  — cancel already-cancelled (409)
 */
@Epic("Insurance API")
@Feature("Policy Endpoint")
public class PolicyTests {


    // ── Test data ─────────────────────────────────────────────────────────────

    private static final String POLICY_ID    = "POL-2025-056789";
    private static final String QUOTE_ID     = "QT-2025-001234";
    private static final String GHOST_ID     = "POL-DOES-NOT-EXIST";

    // ── Stub responses ────────────────────────────────────────────────────────

    private static final String POLICY_ACTIVE = """
        {
          "policyId":        "%s",
          "status":          "active",
          "effectiveDate":   "2025-04-03",
          "expirationDate":  "2026-04-03",
          "premium":         1402.50,
          "paymentMethod":   "monthly"
        }
        """.formatted(POLICY_ID);

    private static final String POLICY_ANNUAL = """
        {
          "policyId":        "POL-2025-056790",
          "status":          "active",
          "effectiveDate":   "2025-04-03",
          "expirationDate":  "2026-04-03",
          "premium":         1300.00,
          "paymentMethod":   "annual"
        }
        """;

    private static final String POLICY_MONTHLY = """
        {
          "policyId":        "POL-2025-056791",
          "status":          "active",
          "effectiveDate":   "2025-04-03",
          "expirationDate":  "2026-04-03",
          "premium":         127.50,
          "paymentMethod":   "monthly"
        }
        """;

    private static final String POLICY_FOUND = """
        {
          "policyId":        "%s",
          "status":          "active",
          "effectiveDate":   "2025-04-03",
          "expirationDate":  "2026-04-03",
          "premium":         1402.50
        }
        """.formatted(POLICY_ID);

    private static final String POLICY_CANCELLED = """
        {
          "policyId": "%s",
          "status":   "cancelled"
        }
        """.formatted(POLICY_ID);

    private static final String NOT_FOUND = """
        {
          "code":    404,
          "message": "Policy not found"
        }
        """;

    private static final String QUOTE_NOT_FOUND = """
        {
          "code":    404,
          "message": "Quote not found or expired"
        }
        """;

    private static final String BAD_REQUEST = """
        {
          "code":    400,
          "message": "effectiveDate cannot be in the past"
        }
        """;

    private static final String CONFLICT = """
        {
          "code":    409,
          "message": "Policy is already cancelled"
        }
        """;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    private static ApiClient api;

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        WireMockHelper.start();
        api = ApiClientFactory.forWireMock();

        AllureHelper.description(
            "Insurance Policy API — bind, retrieve and cancel " +
            "policies. Uses WireMock mock server."
        );
    }

    @AfterClass(alwaysRun = true)
    public void suiteTeardown() {
        WireMockHelper.stop();
    }

    @BeforeMethod(alwaysRun = true)
    public void resetStubs() {
        WireMockHelper.reset();
    }

    // ── POST /policies ────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Bind Policy")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Binding an approved quote should create an active policy.")
    public void bindPolicy_approvedQuote_returnsActive() {
        WireMockHelper.stubPost("/policies", 201, POLICY_ACTIVE);
        AllureHelper.parameter("Quote ID", QUOTE_ID);

        Response response = api.post("/policies", InsuranceTestDataFactory.bindPolicyRequest(QUOTE_ID));
        AllureHelper.attachResponse("POST /policies", response);

        InsuranceResponseValidator.from(response)
            .statusCode(201)
            .withinSla()
            .hasCompletePolicyStructure()
            .isPolicyActive();
    }

    @Test(priority = 2)
    @Story("Bind Policy")
    @Severity(SeverityLevel.NORMAL)
    @Description("Annual payment should produce lower total cost than 12x monthly.")
    public void bindPolicy_annualVsMonthly_annualIsCheaper() {
        // Annual bind
        WireMockHelper.stubPost("/policies", 201, POLICY_ANNUAL);
        Response annualResp = api.post("/policies", InsuranceTestDataFactory.bindPolicyRequest(QUOTE_ID));

        WireMockHelper.reset();

        // Monthly bind
        WireMockHelper.stubPost("/policies", 201, POLICY_MONTHLY);
        Response monthlyResp = api.post("/policies", InsuranceTestDataFactory.bindPolicyRequest(QUOTE_ID));

        AllureHelper.attachResponse("POST /policies (annual)",  annualResp);
        AllureHelper.attachResponse("POST /policies (monthly)", monthlyResp);

        InsuranceResponseValidator.from(annualResp)
            .statusCode(201).hasField("premium");
        InsuranceResponseValidator.from(monthlyResp)
            .statusCode(201).hasField("premium");

        double annualPremium   = ((Number) annualResp.jsonPath()
            .get("premium")).doubleValue();
        double monthlyPremium  = ((Number) monthlyResp.jsonPath()
            .get("premium")).doubleValue();
        double monthlyAnnualized = monthlyPremium * 12;

        AllureHelper.parameter("Annual premium",      annualPremium);
        AllureHelper.parameter("Monthly x12 premium", monthlyAnnualized);

        Assert.assertTrue(annualPremium < monthlyAnnualized,
            "Annual (" + annualPremium
            + ") should cost less than monthly x12 ("
            + monthlyAnnualized + ")");
    }

    // ── POST /policies — negative cases ──────────────────────────────────────

    @Test(priority = 10)
    @Story("Bind Policy — Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Binding with non-existent quote ID should return 404.")
    public void bindPolicy_invalidQuoteId_returns404() {
        WireMockHelper.stubPost("/policies", 404, QUOTE_NOT_FOUND);

        Response response = api.post("/policies", InsuranceTestDataFactory.bindPolicyRequest(
                    "QT-DOES-NOT-EXIST"));
        AllureHelper.attachResponse("POST /policies (invalid quote)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }

    @Test(priority = 11)
    @Story("Bind Policy — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("Binding with backdated effective date should return 400.")
    public void bindPolicy_backdatedDate_returns400() {
        WireMockHelper.stubPost("/policies", 400, BAD_REQUEST);

        Response response = api.post("/policies", InsuranceTestDataFactory.backdatedPolicyRequest(QUOTE_ID));
        AllureHelper.attachResponse("POST /policies (backdated)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(400)
            .withinSla();
    }

    // ── GET /policies/{id} ────────────────────────────────────────────────────

    @Test(priority = 20)
    @Story("Get Policy")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /policies/{id} for active policy should return 200 with full details.")
    public void getPolicyById_found_returns200() {
        WireMockHelper.stubGet("/policies/" + POLICY_ID, 200, POLICY_FOUND);
        AllureHelper.parameter("Policy ID", POLICY_ID);

        Response response = api.get("/policies/" + POLICY_ID);
        AllureHelper.attachResponse("GET /policies/" + POLICY_ID, response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasCompletePolicyStructure()
            .isPolicyActive()
            .fieldEquals("policyId", POLICY_ID);
    }

    @Test(priority = 21)
    @Story("Get Policy")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /policies/{id} for non-existent policy should return 404.")
    public void getPolicyById_notFound_returns404() {
        WireMockHelper.stubGet("/policies/" + GHOST_ID, 404, NOT_FOUND);
        AllureHelper.parameter("Ghost Policy ID", GHOST_ID);

        Response response = api.get("/policies/" + GHOST_ID);
        AllureHelper.attachResponse("GET /policies (not found)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }

    // ── PATCH /policies/{id}/cancel ───────────────────────────────────────────

    @Test(priority = 30)
    @Story("Cancel Policy")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Cancelling an active policy should return 200 and status cancelled.")
    public void cancelPolicy_active_returns200() {
        WireMockHelper.stubPatch(
            "/policies/" + POLICY_ID + "/cancel", 200, POLICY_CANCELLED);
        AllureHelper.parameter("Policy ID", POLICY_ID);

        Response response = api.patch("/policies/" + POLICY_ID + "/cancel", "{}");
        AllureHelper.attachResponse("PATCH /policies/cancel", response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .isPolicyCancelled();
    }

    @Test(priority = 31)
    @Story("Cancel Policy")
    @Severity(SeverityLevel.NORMAL)
    @Description("Cancelling an already-cancelled policy should return 409 Conflict.")
    public void cancelPolicy_alreadyCancelled_returns409() {
        WireMockHelper.stubPatch(
            "/policies/" + POLICY_ID + "/cancel", 409, CONFLICT);
        AllureHelper.parameter("Policy ID", POLICY_ID);

        Response response = api.patch("/policies/" + POLICY_ID + "/cancel", "{}");
        AllureHelper.attachResponse("PATCH /policies/cancel (conflict)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(409)
            .withinSla();
    }
}
