package com.aitesting.api.healthcare;

import com.aitesting.api.models.FhirModels.Claim;
import com.aitesting.api.util.WireMockHelper;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.http.ApiClientFactory;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

/**
 * ClaimTests — FHIR R4 /Claim resource tests.
 *
 * Uses WireMock — insurance claims involve complex billing rules,
 * real payer integrations and sensitive financial data.
 * WireMock gives deterministic, repeatable test responses.
 *
 * Test coverage:
 *   POST /Claim  — professional claim (201)
 *   POST /Claim  — institutional claim (201)
 *   POST /Claim  — invalid diagnosis code (422)
 *   POST /Claim  — missing patient reference (400)
 *   GET  /Claim/{id} — found (200)
 *   GET  /Claim/{id} — not found (404)
 *   GET  /ClaimResponse/{id} — adjudication result (200)
 */
@Epic("Healthcare FHIR API")
@Feature("Claim Resource")
public class ClaimTests {

    private static ApiClient api;

    private static final String PATIENT_ID = "TEST-12345678";
    private static final String CLAIM_ID   = "TEST-CLM-00000001";
    private static final String GHOST_ID   = "TEST-CLM-NONEXISTENT";

    // ── Stub responses ────────────────────────────────────────────────────────

    private static final String CLAIM_CREATED = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "active",
          "use":          "claim",
          "patient":      {"reference": "Patient/%s"},
          "total":        {"value": 150.00, "currency": "USD"}
        }
        """.formatted(CLAIM_ID, PATIENT_ID);

    private static final String CLAIM_FOUND = """
        {
          "resourceType": "Claim",
          "id":           "%s",
          "status":       "active",
          "use":          "claim",
          "patient":      {"reference": "Patient/%s"},
          "total":        {"value": 150.00, "currency": "USD"}
        }
        """.formatted(CLAIM_ID, PATIENT_ID);

    private static final String CLAIM_RESPONSE = """
        {
          "resourceType": "ClaimResponse",
          "id":           "TEST-CR-00000001",
          "status":       "active",
          "outcome":      "complete",
          "request":      {"reference": "Claim/%s"},
          "payment":      {"amount": {"value": 120.00, "currency": "USD"}}
        }
        """.formatted(CLAIM_ID);

    private static final String NOT_FOUND = """
        {
          "resourceType": "OperationOutcome",
          "issue": [{"severity": "error",
                     "code": "not-found",
                     "diagnostics": "Claim not found"}]
        }
        """;

    private static final String INVALID_CODE = """
        {
          "resourceType": "OperationOutcome",
          "issue": [{"severity": "error",
                     "code": "invalid",
                     "diagnostics": "Invalid ICD-10 diagnosis code"}]
        }
        """;

    private static final String BAD_REQUEST = """
        {
          "resourceType": "OperationOutcome",
          "issue": [{"severity": "error",
                     "code": "required",
                     "diagnostics": "Patient reference is required"}]
        }
        """;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        WireMockHelper.start();
        api = ApiClientFactory.forWireMock();
        AllureHelper.description(
            "FHIR R4 Claim resource — insurance billing submissions. " +
            "Uses WireMock for complex billing rule simulation."
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

    // ── POST /Claim ───────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Submit Claim")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /Claim with valid professional claim should return 201.")
    public void submitClaim_professional_returns201() {
        WireMockHelper.stubPost("/Claim", 201, CLAIM_CREATED);

        Claim claim = FhirTestDataFactory.professionalClaim(PATIENT_ID);
        AllureHelper.parameter("Patient ID", PATIENT_ID);
        AllureHelper.parameter("Claim Type", "professional");
        AllureHelper.parameter("ICD-10", FhirTestDataFactory.ICD10_PNEUMONIA);
        AllureHelper.parameter("CPT",    FhirTestDataFactory.CPT_OFFICE_VISIT);

        Response response = api.post("/Claim", claim);
        AllureHelper.attachResponse("POST /Claim (professional)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla()
            .hasCompleteClaimStructure()
            .fieldEquals("id", CLAIM_ID);
    }

    @Test(priority = 2)
    @Story("Submit Claim")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Claim for institutional claim (hospital) should return 201.")
    public void submitClaim_institutional_returns201() {
        String institutionalBody = CLAIM_CREATED
            .replace("\"professional\"", "\"institutional\"");
        WireMockHelper.stubPost("/Claim", 201, institutionalBody);

        Claim claim = FhirTestDataFactory.professionalClaim(PATIENT_ID);
        AllureHelper.parameter("Claim Type", "institutional");

        Response response = api.post("/Claim", claim);
        AllureHelper.attachResponse("POST /Claim (institutional)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla()
            .hasFhirResourceType("Claim");
    }

    // ── POST /Claim — validation ──────────────────────────────────────────────

    @Test(priority = 3)
    @Story("Submit Claim — Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /Claim with invalid ICD-10 code should return 422.")
    public void submitClaim_invalidDiagnosisCode_returns422() {
        WireMockHelper.stubPost("/Claim", 422, INVALID_CODE);

        Claim claim = FhirTestDataFactory.professionalClaim(PATIENT_ID);
        // Override with invalid code
        claim.getDiagnosis().get(0)
            .getDiagnosisCodeableConcept()
            .getCoding().get(0)
            .setCode("INVALID-CODE");
        AllureHelper.parameter("Invalid ICD-10", "INVALID-CODE");

        Response response = api.post("/Claim", claim);
        AllureHelper.attachResponse("POST /Claim (invalid ICD-10)", response);

        HealthResponseValidator.from(response)
            .statusCode(422)
            .withinSla();
    }

    @Test(priority = 4)
    @Story("Submit Claim — Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("POST /Claim without patient reference should return 400.")
    public void submitClaim_missingPatient_returns400() {
        WireMockHelper.stubPost("/Claim", 400, BAD_REQUEST);

        Claim claim = FhirTestDataFactory.professionalClaim(PATIENT_ID);
        claim.setPatient(null);  // remove required patient reference

        Response response = api.post("/Claim", claim);
        AllureHelper.attachResponse("POST /Claim (missing patient)", response);

        HealthResponseValidator.from(response)
            .statusCode(400)
            .withinSla();
    }

    // ── GET /Claim/{id} ───────────────────────────────────────────────────────

    @Test(priority = 5)
    @Story("Get Claim")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /Claim/{id} for existing claim should return 200 with full details.")
    public void getClaim_found_returns200() {
        WireMockHelper.stubGet("/Claim/" + CLAIM_ID, 200, CLAIM_FOUND);
        AllureHelper.parameter("Claim ID", CLAIM_ID);

        Response response = api.get("/Claim/" + CLAIM_ID);
        AllureHelper.attachResponse("GET /Claim/" + CLAIM_ID, response);

        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasCompleteClaimStructure()
            .fieldEquals("id", CLAIM_ID);
    }

    @Test(priority = 6)
    @Story("Get Claim")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /Claim/{id} for non-existent claim should return 404.")
    public void getClaim_notFound_returns404() {
        WireMockHelper.stubGet("/Claim/" + GHOST_ID, 404, NOT_FOUND);
        AllureHelper.parameter("Ghost Claim ID", GHOST_ID);

        Response response = api.get("/Claim/" + GHOST_ID);
        AllureHelper.attachResponse("GET /Claim (not found)", response);

        HealthResponseValidator.from(response)
            .statusCode(404)
            .withinSla();
    }

    // ── GET /ClaimResponse/{id} ───────────────────────────────────────────────

    @Test(priority = 7)
    @Story("Claim Adjudication")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /ClaimResponse/{id} returns adjudication result with payment amount.")
    public void getClaimResponse_adjudicated_returns200() {
        WireMockHelper.stubGet("/ClaimResponse/TEST-CR-00000001",
            200, CLAIM_RESPONSE);

        Response response = api.get("/ClaimResponse/TEST-CR-00000001");
        AllureHelper.attachResponse("GET /ClaimResponse", response);

        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla()
            .hasFhirResourceType("ClaimResponse")
            .hasFhirId()
            .hasField("payment.amount.value");
    }
}
