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
 * QuoteTests — insurance /quotes endpoint test suite.
 *
 * Uses WireMock to simulate the insurance Quote API.
 *
 * NOTE: ApiClient is intentionally NOT used here because ApiClient
 * initialises BASE_URL once at JVM startup (static block). WireMock
 * runs on a different port (8089) requiring its own RequestSpecification.
 *
 * Future refactor: ApiClientFactory (SOLID — Interface Segregation)
 * will eliminate this wireMockSpec duplication across insurance tests.
 * See Wiki: Alternative-Design-Patterns.md
 *
 * Test coverage:
 *   POST /quotes — standard applicant (200 approved)
 *   POST /quotes — teen driver (200, higher premium)
 *   POST /quotes — high risk (200, referred/declined)
 *   POST /quotes — missing applicant (400)
 *   POST /quotes — invalid coverage type (400)
 *   POST /quotes — underage driver (422)
 *   POST /quotes — valid deductibles data-driven (200)
 *   GET  /quotes/{id} — found (200)
 *   GET  /quotes/{id} — not found (404)
 */
@Epic("insurance API")
@Feature("Quote Endpoint")
public class QuoteTests {

    // ── Test data ─────────────────────────────────────────────────────────────

    private static final String QUOTE_ID     = "QT-2025-001234";
    private static final String GHOST_ID     = "QT-DOES-NOT-EXIST";

    // ── Stub responses ────────────────────────────────────────────────────────

    private static final String QUOTE_APPROVED = """
        {
          "quoteId":        "%s",
          "status":         "approved",
          "monthlyPremium": 127.50,
          "annualPremium":  1402.50,
          "coverageType":   "comprehensive",
          "deductible":     500,
          "expiresAt":      "2025-04-22T00:00:00Z"
        }
        """.formatted(QUOTE_ID);

    private static final String QUOTE_TEEN = """
        {
          "quoteId":        "QT-2025-001235",
          "status":         "approved",
          "monthlyPremium": 287.50,
          "annualPremium":  3307.50,
          "coverageType":   "liability",
          "deductible":     1000,
          "expiresAt":      "2025-04-22T00:00:00Z"
        }
        """;

    private static final String QUOTE_HIGH_RISK = """
        {
          "quoteId":        "QT-2025-001236",
          "status":         "referred",
          "monthlyPremium": 485.00,
          "annualPremium":  5565.00,
          "coverageType":   "collision",
          "deductible":     2000,
          "expiresAt":      "2025-04-22T00:00:00Z"
        }
        """;

    private static final String QUOTE_FOUND = """
        {
          "quoteId":        "%s",
          "status":         "approved",
          "monthlyPremium": 127.50,
          "annualPremium":  1402.50,
          "coverageType":   "comprehensive",
          "deductible":     500,
          "expiresAt":      "2025-04-22T00:00:00Z"
        }
        """.formatted(QUOTE_ID);

    private static final String NOT_FOUND = """
        {
          "code":    404,
          "message": "Quote not found"
        }
        """;

    private static final String BAD_REQUEST = """
        {
          "code":    400,
          "message": "Missing required field: applicant"
        }
        """;

    private static final String INVALID_COVERAGE = """
        {
          "code":    400,
          "message": "Invalid coverage type: platinum_ultra_plus"
        }
        """;

    private static final String UNDERAGE = """
        {
          "code":    422,
          "message": "Applicant must be at least 16 years old"
        }
        """;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    private ApiClient api;

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        WireMockHelper.start();
        api = ApiClientFactory.forWireMock();

        AllureHelper.description(
            "insurance Quote API — standard, boundary, and " +
            "negative test scenarios using WireMock."
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

    // ── POST /quotes — happy paths ────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Create Quote")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Standard low-risk applicant should receive approved quote with positive premium.")
    public void createQuote_standardApplicant_approved() {
        WireMockHelper.stubPost("/quotes", 200, QUOTE_APPROVED);

        AllureHelper.parameter("Coverage Type", "comprehensive");
        AllureHelper.parameter("Deductible", 500);

        Response response = api.post("/quotes", InsuranceTestDataFactory.standardQuoteRequest());
        AllureHelper.attachResponse("POST /quotes (standard)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasCompleteQuoteStructure()
            .hasApprovedStatus()
            .premiumIsPositive();
    }

    @Test(priority = 2)
    @Story("Create Quote")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Teen driver (age 18) should receive higher premium than standard applicant.")
    public void createQuote_teenDriver_higherPremium() {
        WireMockHelper.stubPost("/quotes", 200, QUOTE_APPROVED);
        Response standardResp = api.post("/quotes", InsuranceTestDataFactory.standardQuoteRequest());

        WireMockHelper.reset();
        WireMockHelper.stubPost("/quotes", 200, QUOTE_TEEN);
        Response teenResp = api.post("/quotes", InsuranceTestDataFactory.teenDriverQuoteRequest());

        AllureHelper.attachResponse("POST /quotes (standard)", standardResp);
        AllureHelper.attachResponse("POST /quotes (teen)", teenResp);

        InsuranceResponseValidator.from(standardResp)
            .statusCode(200).premiumIsPositive();
        InsuranceResponseValidator.from(teenResp)
            .statusCode(200).premiumIsPositive();

        double standardPremium = ((Number) standardResp.jsonPath()
            .get("monthlyPremium")).doubleValue();
        double teenPremium = ((Number) teenResp.jsonPath()
            .get("monthlyPremium")).doubleValue();

        AllureHelper.parameter("Standard premium", standardPremium);
        AllureHelper.parameter("Teen premium", teenPremium);

        Assert.assertTrue(teenPremium > standardPremium,
            "Teen premium (" + teenPremium
            + ") should exceed standard (" + standardPremium + ")");
    }

    @Test(priority = 3)
    @Story("Create Quote")
    @Severity(SeverityLevel.NORMAL)
    @Description("High-risk applicant (3 accidents) should receive referred or declined status.")
    public void createQuote_highRisk_referredOrDeclined() {
        WireMockHelper.stubPost("/quotes", 200, QUOTE_HIGH_RISK);

        Response response = api.post("/quotes", InsuranceTestDataFactory.highRiskQuoteRequest());
        AllureHelper.attachResponse("POST /quotes (high risk)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasValidQuoteStatus()
            .isNotApproved();
    }

    // ── POST /quotes — deductible data-driven ─────────────────────────────────

    @Test(dataProvider = "validDeductibles")
    @Story("Create Quote")
    @Severity(SeverityLevel.NORMAL)
    @Description("All valid deductible values (250/500/1000/2000) should return 200.")
    public void createQuote_validDeductibles(int deductible) {
        String body = """
            {
              "quoteId":        "QT-2025-00%s",
              "status":         "approved",
              "monthlyPremium": 150.00,
              "annualPremium":  1700.00,
              "coverageType":   "comprehensive",
              "deductible":     %s,
              "expiresAt":      "2025-04-22T00:00:00Z"
            }
            """.formatted(deductible, deductible);

        WireMockHelper.stubPost("/quotes", 200, body);
        AllureHelper.parameter("Deductible", deductible);

        Response response = api.post("/quotes", InsuranceTestDataFactory.createQuote()
                    .withDeductible(deductible)
                    .buildQuoteRequest());
        AllureHelper.attachResponse("POST /quotes deductible=" + deductible, response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .hasValidDeductible();
    }

    @DataProvider(name = "validDeductibles")
    public Object[][] validDeductibles() {
        return new Object[][] {{ 250 }, { 500 }, { 1000 }, { 2000 }};
    }

    // ── POST /quotes — negative cases ─────────────────────────────────────────

    @Test(priority = 10)
    @Story("Create Quote — Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /quotes with null applicant should return 400.")
    public void createQuote_missingApplicant_returns400() {
        WireMockHelper.stubPost("/quotes", 400, BAD_REQUEST);

        Response response = api.post("/quotes", InsuranceTestDataFactory.missingApplicantQuoteRequest());
        AllureHelper.attachResponse("POST /quotes (missing applicant)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(400)
            .withinSla();
    }

    @Test(priority = 11)
    @Story("Create Quote — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /quotes with invalid coverage type should return 400.")
    public void createQuote_invalidCoverage_returns400() {
        WireMockHelper.stubPost("/quotes", 400, INVALID_COVERAGE);

        Response response = api.post("/quotes", InsuranceTestDataFactory.invalidCoverageQuoteRequest());
        AllureHelper.attachResponse("POST /quotes (invalid coverage)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(400)
            .withinSla();
    }

    @Test(priority = 12)
    @Story("Create Quote — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /quotes with driver age below 16 should return 422.")
    public void createQuote_underageDriver_returns422() {
        WireMockHelper.stubPost("/quotes", 422, UNDERAGE);
        AllureHelper.parameter("Driver Age", "15");

        Response response = api.post("/quotes", InsuranceTestDataFactory.createQuote()
                    .withDriverAge(15)
                    .buildQuoteRequest());
        AllureHelper.attachResponse("POST /quotes (underage)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(422)
            .withinSla();
    }

    // ── GET /quotes/{id} ──────────────────────────────────────────────────────

    @Test(priority = 20)
    @Story("Get Quote")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /quotes/{id} for existing quote should return 200 with full details.")
    public void getQuoteById_found_returns200() {
        WireMockHelper.stubGet("/quotes/" + QUOTE_ID, 200, QUOTE_FOUND);
        AllureHelper.parameter("Quote ID", QUOTE_ID);

        Response response = api.get("/quotes/" + QUOTE_ID);
        AllureHelper.attachResponse("GET /quotes/" + QUOTE_ID, response);

        InsuranceResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasCompleteQuoteStructure()
            .fieldEquals("quoteId", QUOTE_ID);
    }

    @Test(priority = 21)
    @Story("Get Quote")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /quotes/{id} for non-existent quote should return 404.")
    public void getQuoteById_notFound_returns404() {
        WireMockHelper.stubGet("/quotes/" + GHOST_ID, 404, NOT_FOUND);
        AllureHelper.parameter("Ghost Quote ID", GHOST_ID);

        Response response = api.get("/quotes/" + GHOST_ID);
        AllureHelper.attachResponse("GET /quotes (not found)", response);

        InsuranceResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }
}
