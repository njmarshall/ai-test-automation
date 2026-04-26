package com.aitesting.healthcare.api;

import com.aitesting.healthcare.model.FhirModels.PriorAuth;
import com.aitesting.util.api.WireMockHelper;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.http.ApiClientFactory;
import com.aitesting.shared.http.AsyncApiClient;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.List;

/**
 * PriorAuthTests — FHIR R4 Prior Authorization tests with async polling.
 *
 * Uses WireMock with polling scenario stubs.
 *
 * Prior Authorization is the most important async pattern in healthcare:
 *   1. Provider submits PA request → gets 201 + PA ID
 *   2. Payer reviews (minutes to hours)
 *   3. Test polls status endpoint until decision arrives
 *   4. Final status: approved | denied | pended
 *
 * This pattern is used by:
 *   → Epic, Cerner, Athenahealth for PA workflows
 *   → Anthem, Kaiser, UnitedHealth for payer decisions
 *   → CMS for Medicare/Medicaid prior auth rules
 *
 * Test coverage:
 *   POST /Claim (PA)     — submit prior auth request (201)
 *   GET  /Claim/{id}     — poll until approved (async)
 *   GET  /Claim/{id}     — poll until denied (async)
 *   GET  /Claim/{id}     — poll until pended (async — needs more info)
 *   POST /Claim (PA)     — urgent request (expedited review)
 *   POST /Claim (PA)     — missing required fields (400)
 */
@Epic("Healthcare FHIR API")
@Feature("Prior Authorization — Async")
public class PriorAuthTests {

    private static ApiClient api;

    private static final String PATIENT_ID = "TEST-12345678";
    private static final String PA_ID      = "TEST-PA-00000001";
    private static final String GHOST_ID   = "TEST-PA-NONEXISTENT";

    // ── Stub responses ────────────────────────────────────────────────────────

    private static final String PA_SUBMITTED = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "active",
          "use":          "preauthorization",
          "outcome":      "queued",
          "patient":      {"reference": "Patient/%s"},
          "created":      "2025-04-06T10:00:00Z"
        }
        """.formatted(PA_ID, PATIENT_ID);

    private static final String PA_PROCESSING = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "active",
          "use":          "preauthorization",
          "outcome":      "queued"
        }
        """.formatted(PA_ID);

    private static final String PA_APPROVED = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "approved",
          "use":          "preauthorization",
          "outcome":      "complete",
          "patient":      {"reference": "Patient/%s"}
        }
        """.formatted(PA_ID, PATIENT_ID);

    private static final String PA_DENIED = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "denied",
          "use":          "preauthorization",
          "outcome":      "complete",
          "patient":      {"reference": "Patient/%s"}
        }
        """.formatted(PA_ID, PATIENT_ID);

    private static final String PA_PENDED = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "pended",
          "use":          "preauthorization",
          "outcome":      "partial",
          "patient":      {"reference": "Patient/%s"}
        }
        """.formatted(PA_ID, PATIENT_ID);

    private static final String NOT_FOUND = """
        {
          "resourceType": "OperationOutcome",
          "issue": [{"severity": "error", "code": "not-found"}]
        }
        """;

    private static final String BAD_REQUEST = """
        {
          "resourceType": "OperationOutcome",
          "issue": [{"severity": "error", "code": "required",
                     "diagnostics": "Patient reference required"}]
        }
        """;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        WireMockHelper.start();
        api = ApiClientFactory.forWireMock();
        AllureHelper.description(
            "FHIR R4 Prior Authorization — async polling pattern. " +
            "Simulates real payer review workflows using WireMock scenarios."
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

    // ── POST /Claim (prior auth) ──────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Submit Prior Auth")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Submit PA request for elective MRI — returns 201 with queued status.")
    public void submitPriorAuth_electiveMri_returnsQueued() {
        WireMockHelper.stubPost("/Claim", 201, PA_SUBMITTED);

        PriorAuth request = FhirTestDataFactory
            .electiveMriPriorAuth(PATIENT_ID);
        AllureHelper.parameter("Patient ID",  PATIENT_ID);
        AllureHelper.parameter("Procedure",   FhirTestDataFactory.CPT_MRI_BRAIN);
        AllureHelper.parameter("Request Use", "preauthorization");

        Response response = api.post("/Claim", request);
        AllureHelper.attachResponse("POST /Claim (PA)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla()
            .hasCompletePriorAuthStructure()
            .isQueued()
            .fieldEquals("id", PA_ID);
    }

    @Test(priority = 2)
    @Story("Submit Prior Auth")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submit PA for routine office visit — typically auto-approved.")
    public void submitPriorAuth_routineVisit_returnsQueued() {
        WireMockHelper.stubPost("/Claim", 201, PA_SUBMITTED);

        PriorAuth request = FhirTestDataFactory
            .routineVisitPriorAuth(PATIENT_ID);
        AllureHelper.parameter("Procedure", FhirTestDataFactory.CPT_OFFICE_VISIT);

        Response response = api.post("/Claim", request);
        AllureHelper.attachResponse("POST /Claim (PA routine)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla()
            .hasFhirResourceType("Claim")
            .isQueued();
    }

    // ── Async polling — approved ──────────────────────────────────────────────

    @Test(priority = 3)
    @Story("Prior Auth Decision — Async Polling")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Poll PA status until approved — 2 processing steps then approved. " +
                 "Demonstrates async polling pattern used in all FHIR payer integrations.")
    public void priorAuth_pollUntilApproved() {
        // Stub: 2 "queued" responses then "approved"
        WireMockHelper.stubPolling(
            "/Claim/" + PA_ID,
            PA_PROCESSING,
            PA_APPROVED,
            2
        );

        AllureHelper.parameter("PA ID",    PA_ID);
        AllureHelper.parameter("Pattern",  "Poll: queued → queued → approved");
        AllureHelper.parameter("Max wait", "30s, interval 1s");

        Response finalResponse = AsyncApiClient.pollUntil(
            api,
            "/Claim/" + PA_ID,
            "status",
            "approved",
            30,  // 30 second timeout
            1    // poll every 1 second
        );
        AllureHelper.attachResponse("Final PA decision", finalResponse);

        HealthResponseValidator.from(finalResponse)
            .statusCode(200)
            .isApproved()
            .hasValidOutcome();
    }

    // ── Async polling — denied ────────────────────────────────────────────────

    @Test(priority = 4)
    @Story("Prior Auth Decision — Async Polling")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Poll PA status until denied — payer rejects elective procedure.")
    public void priorAuth_pollUntilDenied() {
        WireMockHelper.stubPolling(
            "/Claim/" + PA_ID,
            PA_PROCESSING,
            PA_DENIED,
            1
        );

        AllureHelper.parameter("PA ID",   PA_ID);
        AllureHelper.parameter("Pattern", "Poll: queued → denied");

        // Use pollUntilTerminal — both approved and denied are terminal
        Response finalResponse = AsyncApiClient.pollUntilTerminal(
            api,
            "/Claim/" + PA_ID,
            "status",
            List.of("approved", "denied", "pended"),
            30,
            1
        );
        AllureHelper.attachResponse("Final PA decision (denied)", finalResponse);

        HealthResponseValidator.from(finalResponse)
            .statusCode(200)
            .isDenied()
            .hasValidOutcome();
    }

    // ── Async polling — pended ────────────────────────────────────────────────

    @Test(priority = 5)
    @Story("Prior Auth Decision — Async Polling")
    @Severity(SeverityLevel.NORMAL)
    @Description("Poll PA until pended — payer needs additional clinical information.")
    public void priorAuth_pollUntilPended() {
        WireMockHelper.stubPolling(
            "/Claim/" + PA_ID,
            PA_PROCESSING,
            PA_PENDED,
            1
        );

        AllureHelper.parameter("PA ID",   PA_ID);
        AllureHelper.parameter("Pattern", "Poll: queued → pended");

        Response finalResponse = AsyncApiClient.pollUntilTerminal(
            api,
            "/Claim/" + PA_ID,
            "status",
            List.of("approved", "denied", "pended"),
            30,
            1
        );
        AllureHelper.attachResponse("Final PA decision (pended)", finalResponse);

        HealthResponseValidator.from(finalResponse)
            .statusCode(200)
            .hasValidPriorAuthStatus()
            .hasValidOutcome();

        // Pended means more info needed — not a terminal failure
        String status = finalResponse.jsonPath().getString("status");
        org.testng.Assert.assertEquals(status, "pended",
            "Expected pended status for additional info request");
    }

    // ── Async polling — exponential backoff ──────────────────────────────────

    @Test(priority = 6)
    @Story("Prior Auth Decision — Async Polling")
    @Severity(SeverityLevel.NORMAL)
    @Description("Complex PA uses exponential backoff polling — realistic for long-running reviews.")
    public void priorAuth_pollWithExponentialBackoff() {
        WireMockHelper.stubPolling(
            "/Claim/" + PA_ID,
            PA_PROCESSING,
            PA_APPROVED,
            3   // 3 processing steps before approval
        );

        AllureHelper.parameter("PA ID",    PA_ID);
        AllureHelper.parameter("Pattern",  "Exponential backoff: 1s→2s→4s");

        Response finalResponse = AsyncApiClient.pollWithBackoff(
            api,
            "/Claim/" + PA_ID,
            "status",
            "approved",
            30,  // max 30 seconds
            1,   // start at 1 second
            4    // cap at 4 seconds
        );
        AllureHelper.attachResponse("Final PA (backoff)", finalResponse);

        HealthResponseValidator.from(finalResponse)
            .statusCode(200)
            .isApproved();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test(priority = 10)
    @Story("Submit Prior Auth — Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /Claim (PA) missing patient reference should return 400.")
    public void submitPriorAuth_missingPatient_returns400() {
        WireMockHelper.stubPost("/Claim", 400, BAD_REQUEST);

        PriorAuth request = FhirTestDataFactory
            .electiveMriPriorAuth(PATIENT_ID);
        request.setPatient(null);  // remove required patient ref

        Response response = api.post("/Claim", request);
        AllureHelper.attachResponse("POST /Claim PA (missing patient)", response);

        HealthResponseValidator.from(response)
            .statusCode(400)
            .withinSla();
    }

    @Test(priority = 11)
    @Story("Submit Prior Auth — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /Claim/{id} for non-existent PA should return 404.")
    public void getPriorAuth_notFound_returns404() {
        WireMockHelper.stubGet("/Claim/" + GHOST_ID, 404, NOT_FOUND);

        Response response = api.get("/Claim/" + GHOST_ID);
        AllureHelper.attachResponse("GET /Claim PA (not found)", response);

        HealthResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }
}
