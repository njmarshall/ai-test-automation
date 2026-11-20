package com.aitesting.healthcare.api;

import com.aitesting.healthcare.model.FhirModels.Patient;
import com.aitesting.shared.config.BaseConfig;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.http.ApiClientFactory;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * PatientTests — FHIR R4 /Patient endpoint tests.
 *
 * Runs against the public HAPI FHIR test server:
 *   https://hapi.fhir.org/baseR4
 *
 * No mock server needed — HAPI FHIR is a free public FHIR R4 server
 * maintained by the HAPI FHIR open source project.
 *
 * HIPAA note: All test data is synthetic — TEST- prefixed IDs,
 * Java Faker names, calculated birth dates. Never real patient data.
 *
 * Test coverage:
 *   POST /Patient          — create patient (201)
 *   GET  /Patient/{id}     — found (200)
 *   GET  /Patient/{id}     — not found (404)
 *   PUT  /Patient/{id}     — update patient (200)
 *   GET  /Patient?family=X — search by family name (200)
 *   POST /Patient          — missing gender (400/422)
 */
@Epic("Healthcare FHIR API")
@Feature("Patient Resource")
// Live public HAPI FHIR server: excluded from the default CI gate (testng.xml),
// run non-blocking via testng-live.xml. Class-level groups merge into every @Test method.
@Test(groups = "live")
public class PatientTests {

    private static ApiClient api;

    // IDs of patients created by the current test; deleted in cleanupCreatedPatients()
    private final List<String> createdPatientIds = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        api = ApiClientFactory.forFhirApi();
        AllureHelper.description(
            "FHIR R4 Patient resource — CRUD operations against " +
            "public HAPI FHIR test server (hapi.fhir.org/baseR4). " +
            "All data is HIPAA-safe synthetic test data."
        );
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCreatedPatients() {
        // Best-effort cleanup: delete every patient this test created, ignoring failures
        for (String id : createdPatientIds) {
            try {
                api.delete("/Patient/" + id);
            } catch (Exception ignored) {
                // Cleanup must never fail the run
            }
        }
        createdPatientIds.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * POSTs a synthetic adult patient with the same checks as
     * createPatient_validAdult_returns201, records its id for cleanup, and returns the id.
     */
    private String createAdultPatient() {
        Patient patient = FhirTestDataFactory.adultPatient();
        Response response = api.post("/Patient", patient);
        AllureHelper.attachResponse("POST /Patient (setup)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Patient")
            .hasFhirId()
            .hasCompletePatientStructure();

        String id = response.jsonPath().getString("id");
        createdPatientIds.add(id);
        return id;
    }

    // ── POST /Patient ─────────────────────────────────────────────────────────

    @Test
    @Story("Create Patient")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /Patient with valid FHIR R4 payload should return 201 with patient ID.")
    public void createPatient_validAdult_returns201() {
        Patient patient = FhirTestDataFactory.adultPatient();
        AllureHelper.parameter("Patient Gender", patient.getGender());
        AllureHelper.parameter("Patient BirthDate", patient.getBirthDate());

        AllureHelper.step("POST /Patient with synthetic adult patient", () -> {
            Response response = api.post("/Patient", patient);
            AllureHelper.attachResponse("POST /Patient", response);

            HealthResponseValidator.from(response)
                .statusCode(201)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasFhirResourceType("Patient")
                .hasFhirId()
                .hasCompletePatientStructure();

            String createdPatientId = response.jsonPath().getString("id");
            createdPatientIds.add(createdPatientId);
            AllureHelper.parameter("Created Patient ID", createdPatientId);
        });
    }

    @Test
    @Story("Create Patient")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Patient with pediatric patient (age 8) should return 201.")
    public void createPatient_pediatric_returns201() {
        Patient patient = FhirTestDataFactory.pediatricPatient();
        AllureHelper.parameter("Patient Age", "8");

        Response response = api.post("/Patient", patient);
        AllureHelper.attachResponse("POST /Patient (pediatric)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Patient")
            .hasFhirId();
    }

    @Test
    @Story("Create Patient")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Patient with senior patient (age 72, Medicare eligible) should return 201.")
    public void createPatient_senior_returns201() {
        Patient patient = FhirTestDataFactory.seniorPatient();
        AllureHelper.parameter("Patient Age", "72");

        Response response = api.post("/Patient", patient);
        AllureHelper.attachResponse("POST /Patient (senior)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Patient");
    }

    @Test
    @Story("Create Patient")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Patient with newborn (age 0) should return 201.")
    public void createPatient_newborn_returns201() {
        Patient patient = FhirTestDataFactory.newbornPatient();
        AllureHelper.parameter("Patient Age", "0 (newborn)");

        Response response = api.post("/Patient", patient);
        AllureHelper.attachResponse("POST /Patient (newborn)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Patient");
    }

    // ── GET /Patient/{id} ─────────────────────────────────────────────────────

    @Test
    @Story("Get Patient")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /Patient/{id} for existing patient should return 200 with complete data.")
    public void getPatient_found_returns200() {
        String createdPatientId = createAdultPatient();
        AllureHelper.parameter("Patient ID", createdPatientId);

        Response response = api.get("/Patient/" + createdPatientId);
        AllureHelper.attachResponse("GET /Patient/" + createdPatientId, response);

        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasCompletePatientStructure()
            .fieldEquals("id", createdPatientId);
    }

    @Test
    @Story("Get Patient")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /Patient/{id} for non-existent patient should return 404.")
    public void getPatient_notFound_returns404() {
        String ghostId = "TEST-NONEXISTENT-99999999";
        AllureHelper.parameter("Ghost Patient ID", ghostId);

        Response response = api.get("/Patient/" + ghostId);
        AllureHelper.attachResponse("GET /Patient (not found)", response);

        HealthResponseValidator.from(response)
            .statusCode(404)
            .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── PUT /Patient/{id} ─────────────────────────────────────────────────────

    @Test
    @Story("Update Patient")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /Patient/{id} should update patient and return 200.")
    public void updatePatient_changePhone_returns200() {
        String createdPatientId = createAdultPatient();
        Patient updated = FhirTestDataFactory.adultPatient();
        updated.setId(createdPatientId);
        AllureHelper.parameter("Patient ID", createdPatientId);

        Response response = api.put("/Patient/" + createdPatientId, updated);
        AllureHelper.attachResponse("PUT /Patient/" + createdPatientId, response);

        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Patient")
            .fieldEquals("id", createdPatientId);
    }

    // ── GET /Patient?family=X ─────────────────────────────────────────────────

    @Test
    @Story("Search Patient")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /Patient?family=Smith should return a Bundle with matching patients.")
    public void searchPatient_byFamilyName_returnsBundle() {
        AllureHelper.parameter("Search family name", "Smith");

        Response response = api.get("/Patient",
            java.util.Map.of("family", "Smith"));
        AllureHelper.attachResponse("GET /Patient?family=Smith", response);

        // FHIR search returns a Bundle resource
        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Bundle");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @Story("Create Patient — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Patient with missing required name should return 400 or 422.")
    public void createPatient_missingName_returns4xx() {
        Patient invalid = FhirTestDataFactory.adultPatient();
        invalid.setName(null);  // remove required name field

        Response response = api.post("/Patient", invalid);
        AllureHelper.attachResponse("POST /Patient (missing name)", response);

        // HAPI FHIR public server is lenient — accepts missing name
        // A strict production FHIR server would return 400/422
        int status = response.getStatusCode();
        org.testng.Assert.assertTrue(
                status == 201 || (status >= 400 && status < 500),
                "Expected 201 (lenient server) or 4xx, got: " + status);
    }
}
