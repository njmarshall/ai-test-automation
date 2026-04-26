package com.aitesting.healthcare.api;

import com.aitesting.shared.assertions.ResponseValidator;
import io.restassured.response.Response;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * HealthResponseValidator — FHIR R4 domain assertion validator.
 *
 * Extends ResponseValidator<HealthResponseValidator> so T = this class.
 * Every inherited base method returns HealthResponseValidator keeping
 * the chain at the subclass type for full compile-time type safety.
 *
 * Mirrors PetResponseValidator and InsuranceResponseValidator —
 * proves CRTP scales across ALL industry capstones.
 *
 * Usage:
 *   HealthResponseValidator.from(response)
 *       .statusCode(200)              // inherited
 *       .withinSla()                  // inherited
 *       .hasFhirResourceType("Patient") // FHIR native
 *       .hasPatientId()               // FHIR native
 *       .hasValidGender();            // FHIR native
 */
public class HealthResponseValidator
        extends ResponseValidator<HealthResponseValidator> {

    // ── FHIR domain constants ─────────────────────────────────────────────────

    private static final List<String> VALID_GENDERS =
        Arrays.asList("male", "female", "other", "unknown");

    private static final List<String> VALID_PATIENT_STATUSES =
        Arrays.asList("active", "inactive", "entered-in-error");

    private static final List<String> VALID_ENCOUNTER_STATUSES =
        Arrays.asList("planned", "arrived", "triaged",
                      "in-progress", "onleave", "finished",
                      "cancelled", "entered-in-error", "unknown");

    private static final List<String> VALID_CLAIM_STATUSES =
        Arrays.asList("active", "cancelled", "draft", "entered-in-error");

    private static final List<String> VALID_PRIOR_AUTH_STATUSES =
        Arrays.asList("active", "cancelled", "draft",
                      "entered-in-error", "approved",
                      "denied", "pended");

    private static final List<String> VALID_OUTCOMES =
        Arrays.asList("queued", "complete", "error", "partial");

    // ── Constructor + factory ─────────────────────────────────────────────────

    private HealthResponseValidator(Response response) {
        super(response);
    }

    public static HealthResponseValidator from(Response response) {
        return new HealthResponseValidator(response);
    }

    // ── FHIR structural assertions ────────────────────────────────────────────

    /**
     * Assert response has the expected FHIR resourceType.
     * Every FHIR resource must declare its type.
     */
    public HealthResponseValidator hasFhirResourceType(String expectedType) {
        String actual = response.jsonPath().getString("resourceType");
        assertThat("Expected FHIR resourceType '" + expectedType + "'",
                actual, equalTo(expectedType));
        return this;
    }

    /**
     * Assert response has a non-null FHIR resource ID.
     */
    public HealthResponseValidator hasFhirId() {
        assertThat("Expected FHIR id to be present",
                response.jsonPath().getString("id"), notNullValue());
        return this;
    }

    /**
     * Assert FHIR resource ID starts with "TEST-" prefix.
     * Confirms synthetic test data — never real patient records.
     */
    public HealthResponseValidator hasTestPrefix() {
        String id = response.jsonPath().getString("id");
        assertThat("Expected TEST- prefix on ID (HIPAA — no real PHI)",
                id, startsWith("TEST-"));
        return this;
    }

    // ── Patient assertions ────────────────────────────────────────────────────

    /**
     * Assert response contains a valid patient ID.
     */
    public HealthResponseValidator hasPatientId() {
        hasFhirId();
        hasFhirResourceType("Patient");
        return this;
    }

    /**
     * Assert patient has a valid FHIR gender value.
     */
    public HealthResponseValidator hasValidGender() {
        String gender = response.jsonPath().getString("gender");
        assertThat("Expected valid FHIR gender, got: " + gender,
                VALID_GENDERS, hasItem(gender));
        return this;
    }

    /**
     * Assert patient has a birthDate in YYYY-MM-DD format.
     */
    public HealthResponseValidator hasBirthDate() {
        String birthDate = response.jsonPath().getString("birthDate");
        assertThat("Expected birthDate to be present",
                birthDate, notNullValue());
        assertThat("Expected birthDate in YYYY-MM-DD format",
                birthDate,
                matchesPattern("\\d{4}-\\d{2}-\\d{2}"));
        return this;
    }

    /**
     * Assert patient has at least one name.
     */
    public HealthResponseValidator hasPatientName() {
        assertThat("Expected patient name array to be present",
                response.jsonPath().getList("name"), not(empty()));
        return this;
    }

    /**
     * Assert patient has at least one identifier (e.g. MRN).
     */
    public HealthResponseValidator hasIdentifier() {
        assertThat("Expected patient identifier to be present",
                response.jsonPath().getList("identifier"), not(empty()));
        return this;
    }

    /**
     * Composite — verifies complete Patient structure.
     */
    public HealthResponseValidator hasCompletePatientStructure() {
        return hasFhirResourceType("Patient")
              .hasFhirId()
              .hasPatientName()
              .hasValidGender()
              .hasBirthDate()
              .hasIdentifier();
    }

    // ── Encounter assertions ──────────────────────────────────────────────────

    /**
     * Assert encounter has a valid FHIR status.
     */
    public HealthResponseValidator hasValidEncounterStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid encounter status, got: " + status,
                VALID_ENCOUNTER_STATUSES, hasItem(status));
        return this;
    }

    /**
     * Assert encounter references a patient.
     */
    public HealthResponseValidator hasPatientReference() {
        // Encounters use subject.reference
        // Claims and PriorAuth use patient.reference
        String subjectRef = response.jsonPath().getString("subject.reference");
        String patientRef = response.jsonPath().getString("patient.reference");
        assertThat("Expected subject.reference or patient.reference",
                subjectRef != null || patientRef != null, is(true));
        return this;

    }

    /**
     * Composite — verifies complete Encounter structure.
     */
    public HealthResponseValidator hasCompleteEncounterStructure() {
        return hasFhirResourceType("Encounter")
              .hasFhirId()
              .hasValidEncounterStatus()
              .hasPatientReference();
    }

    // ── Claim assertions ──────────────────────────────────────────────────────

    /**
     * Assert claim has a valid FHIR status.
     */
    public HealthResponseValidator hasValidClaimStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid claim status, got: " + status,
                VALID_CLAIM_STATUSES, hasItem(status));
        return this;
    }

    /**
     * Assert claim has a total amount greater than zero.
     */
    public HealthResponseValidator claimTotalIsPositive() {
        Object total = response.jsonPath().get("total.value");
        assertThat("Expected claim total to be present", total, notNullValue());
        assertThat("Expected claim total to be positive",
                ((Number) total).doubleValue(), greaterThan(0.0));
        return this;
    }

    /**
     * Composite — verifies complete Claim structure.
     */
    public HealthResponseValidator hasCompleteClaimStructure() {
        return hasFhirResourceType("Claim")
              .hasFhirId()
              .hasValidClaimStatus()
              .claimTotalIsPositive()
              .hasPatientReference();
    }

    // ── Prior Authorization assertions ────────────────────────────────────────

    /**
     * Assert prior auth has a valid status.
     */
    public HealthResponseValidator hasValidPriorAuthStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid prior auth status, got: " + status,
                VALID_PRIOR_AUTH_STATUSES, hasItem(status));
        return this;
    }

    /**
     * Assert prior auth outcome is queued (just submitted).
     */
    public HealthResponseValidator isQueued() {
        assertThat("Expected queued outcome",
                response.jsonPath().getString("outcome"),
                equalTo("queued"));
        return this;
    }

    /**
     * Assert prior auth was approved.
     */
    public HealthResponseValidator isApproved() {
        assertThat("Expected approved status",
                response.jsonPath().getString("status"),
                equalTo("approved"));
        return this;
    }

    /**
     * Assert prior auth was denied.
     */
    public HealthResponseValidator isDenied() {
        assertThat("Expected denied status",
                response.jsonPath().getString("status"),
                equalTo("denied"));
        return this;
    }

    /**
     * Assert prior auth has a valid outcome value.
     */
    public HealthResponseValidator hasValidOutcome() {
        String outcome = response.jsonPath().getString("outcome");
        assertThat("Expected valid outcome, got: " + outcome,
                VALID_OUTCOMES, hasItem(outcome));
        return this;
    }

    /**
     * Composite — verifies complete Prior Auth structure.
     */
    public HealthResponseValidator hasCompletePriorAuthStructure() {
        return hasFhirResourceType("Claim")
              .hasFhirId()
              .hasValidPriorAuthStatus()
              .hasPatientReference();
    }
}
