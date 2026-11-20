package com.aitesting.healthcare.api;

import com.aitesting.healthcare.model.FhirModels.Encounter;
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
 * EncounterTests — FHIR R4 /Encounter endpoint tests.
 *
 * Runs against public HAPI FHIR test server.
 *
 * An Encounter represents a clinical visit, admission, or appointment.
 * Key fields: status, class (AMB/IMP/EMER), subject (Patient ref), period.
 *
 * Each test creates its own patient (and encounter where needed);
 * cleanup runs after every method, so tests can run alone and in any order.
 *
 * Test coverage:
 *   POST /Encounter        — create ambulatory encounter (201)
 *   POST /Encounter        — create inpatient encounter (201)
 *   GET  /Encounter/{id}   — found (200)
 *   GET  /Encounter/{id}   — not found (404)
 *   GET  /Encounter?patient=X — search by patient (200 Bundle)
 *   POST /Encounter        — invalid status code (400/422)
 */
@Epic("Healthcare FHIR API")
@Feature("Encounter Resource")
// Live public HAPI FHIR server: excluded from the default CI gate (testng.xml),
// run non-blocking via testng-live.xml. Class-level groups merge into every @Test method.
@Test(groups = "live")
public class EncounterTests {

    private static ApiClient api;

    // IDs created by the current test; deleted in cleanupCreatedResources()
    private final List<String> createdEncounterIds = new ArrayList<>();
    private final List<String> createdPatientIds   = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        api = ApiClientFactory.forFhirApi();
        AllureHelper.description(
            "FHIR R4 Encounter resource — clinical visits and admissions. " +
            "Tests run against public HAPI FHIR test server."
        );
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCreatedResources() {
        // Best-effort cleanup. Encounters first: they reference the patients.
        for (String id : createdEncounterIds) {
            try {
                api.delete("/Encounter/" + id);
            } catch (Exception ignored) {
                // Cleanup must never fail the run
            }
        }
        createdEncounterIds.clear();

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

    /** POSTs a synthetic adult patient, asserts 201, records its id for cleanup, returns the id. */
    private String createPatient() {
        Patient patient = FhirTestDataFactory.adultPatient();
        Response response = api.post("/Patient", patient);
        AllureHelper.attachResponse("POST /Patient (setup)", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Patient")
            .hasFhirId();

        String id = response.jsonPath().getString("id");
        createdPatientIds.add(id);
        AllureHelper.parameter("Patient ID", id);
        return id;
    }

    /** POSTs an encounter, asserts 201, records its id for cleanup, returns the id. */
    private String postEncounter(String label, Encounter encounter) {
        Response response = api.post("/Encounter", encounter);
        AllureHelper.attachResponse("POST /Encounter (" + label + ")", response);

        HealthResponseValidator.from(response)
            .statusCode(201)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasCompleteEncounterStructure();

        String id = response.jsonPath().getString("id");
        createdEncounterIds.add(id);
        AllureHelper.parameter("Created Encounter ID", id);
        return id;
    }

    // ── POST /Encounter ───────────────────────────────────────────────────────

    @Test
    @Story("Create Encounter")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /Encounter with ambulatory visit should return 201.")
    public void createEncounter_ambulatory_returns201() {
        String patientId = createPatient();
        Encounter encounter = FhirTestDataFactory.ambulatoryEncounter(patientId);
        AllureHelper.parameter("Encounter Class", "AMB (ambulatory)");

        postEncounter("AMB", encounter);
    }

    @Test
    @Story("Create Encounter")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Encounter with inpatient admission should return 201.")
    public void createEncounter_inpatient_returns201() {
        String patientId = createPatient();
        Encounter encounter = FhirTestDataFactory.ambulatoryEncounter(patientId);

        // Override class to inpatient
        encounter.getClassCode().setCode("IMP");
        encounter.getClassCode().setDisplay("inpatient encounter");
        encounter.setStatus("in-progress");
        AllureHelper.parameter("Encounter Class", "IMP (inpatient)");

        postEncounter("IMP", encounter);
    }

    // ── GET /Encounter/{id} ───────────────────────────────────────────────────

    @Test
    @Story("Get Encounter")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /Encounter/{id} for existing encounter should return 200.")
    public void getEncounter_found_returns200() {
        String patientId = createPatient();
        String encounterId = postEncounter("setup",
            FhirTestDataFactory.ambulatoryEncounter(patientId));
        AllureHelper.parameter("Encounter ID", encounterId);

        Response response = api.get("/Encounter/" + encounterId);
        AllureHelper.attachResponse("GET /Encounter/" + encounterId, response);

        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasCompleteEncounterStructure()
            .fieldEquals("id", encounterId);
    }

    @Test
    @Story("Get Encounter")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /Encounter/{id} for non-existent encounter should return 404.")
    public void getEncounter_notFound_returns404() {
        String ghostId = "TEST-ENC-NONEXISTENT-99999";
        AllureHelper.parameter("Ghost Encounter ID", ghostId);

        Response response = api.get("/Encounter/" + ghostId);
        AllureHelper.attachResponse("GET /Encounter (not found)", response);

        HealthResponseValidator.from(response)
            .statusCode(404)
            .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── GET /Encounter?patient=X ──────────────────────────────────────────────

    @Test
    @Story("Search Encounter")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /Encounter?patient={id} should return Bundle with patient's encounters.")
    public void searchEncounter_byPatient_returnsBundle() {
        String patientId = createPatient();
        postEncounter("setup", FhirTestDataFactory.ambulatoryEncounter(patientId));

        Response response = api.get("/Encounter",
            java.util.Map.of("patient", patientId));
        AllureHelper.attachResponse("GET /Encounter?patient=" + patientId, response);

        HealthResponseValidator.from(response)
            .statusCode(200)
            .withinSla(BaseConfig.LIVE_API_SLA_MS)
            .hasFhirResourceType("Bundle");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @Story("Create Encounter — Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /Encounter with a status outside the EncounterStatus value set should return 400/422.")
    public void createEncounter_invalidStatus_returns4xx() {
        String patientId = createPatient();
        Encounter invalid = FhirTestDataFactory.ambulatoryEncounter(patientId);
        // Encounter.status is a required binding to EncounterStatus. HAPI rejects unknown
        // codes when parsing (HAPI-1821). It does not enforce cardinality on create, so a
        // missing status (or a missing subject, which is 0..1 in R4) would be accepted.
        invalid.setStatus("not-a-status");

        Response response = api.post("/Encounter", invalid);
        AllureHelper.attachResponse("POST /Encounter (invalid status)", response);

        // If the server wrongly accepts it, record the id so cleanup still deletes it
        if (response.getStatusCode() == 201) {
            createdEncounterIds.add(response.jsonPath().getString("id"));
        }

        HealthResponseValidator.from(response)
            .is4xx()
            .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }
}
