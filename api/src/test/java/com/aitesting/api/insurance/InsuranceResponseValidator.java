package com.aitesting.api.insurance;

import com.aitesting.shared.assertions.ResponseValidator;
import io.restassured.response.Response;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * InsuranceResponseValidator — Insurance domain assertion validator.
 *
 * Extends ResponseValidator<InsuranceResponseValidator> so T = this class.
 * Every inherited base method returns InsuranceResponseValidator keeping
 * the chain at the subclass type for full compile-time type safety.
 *
 * Mirrors PetResponseValidator — proves CRTP scales across capstones.
 *
 * Usage:
 *   InsuranceResponseValidator.from(response)
 *       .statusCode(200)           // inherited — returns Insurance type
 *       .withinSla()               // inherited — returns Insurance type
 *       .hasQuoteId()              // Insurance native
 *       .hasApprovedStatus()       // Insurance native
 *       .premiumIsPositive()       // Insurance native
 *       .hasCompleteQuoteStructure(); // composite
 */
public class InsuranceResponseValidator
        extends ResponseValidator<InsuranceResponseValidator> {

    // ── Domain constants ──────────────────────────────────────────────────────

    private static final List<String> VALID_QUOTE_STATUSES =
            Arrays.asList("approved", "referred", "declined");

    private static final List<String> VALID_POLICY_STATUSES =
            Arrays.asList("active", "pending", "cancelled", "expired");

    private static final List<String> VALID_CLAIM_STATUSES =
            Arrays.asList("open", "investigating", "approved",
                    "denied", "closed");

    private static final List<String> VALID_COVERAGE_TYPES =
            Arrays.asList("liability", "comprehensive", "collision");

    private static final List<Integer> VALID_DEDUCTIBLES =
            Arrays.asList(250, 500, 1000, 2000);

    // ── Constructor + factory ─────────────────────────────────────────────────

    private InsuranceResponseValidator(Response response) {
        super(response);
    }

    public static InsuranceResponseValidator from(Response response) {
        return new InsuranceResponseValidator(response);
    }

    // ── Quote assertions ──────────────────────────────────────────────────────

    /** Assert response contains a valid quote ID. */
    public InsuranceResponseValidator hasQuoteId() {
        String quoteId = response.jsonPath().getString("quoteId");
        assertThat("Expected quoteId to be present",
                quoteId, notNullValue());
        assertThat("Expected quoteId to be non-blank",
                quoteId.isBlank(), is(false));
        return this;
    }

    /** Assert quote status is one of: approved | referred | declined. */
    public InsuranceResponseValidator hasValidQuoteStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid quote status, got: " + status,
                VALID_QUOTE_STATUSES, hasItem(status));
        return this;
    }

    /** Assert quote was approved. */
    public InsuranceResponseValidator hasApprovedStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected approved status, got: " + status,
                status, equalTo("approved"));
        return this;
    }

    /** Assert quote was NOT approved (referred or declined). */
    public InsuranceResponseValidator isNotApproved() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected non-approved status, got: " + status,
                status, not(equalTo("approved")));
        return this;
    }

    /** Assert monthly premium is a positive number. */
    public InsuranceResponseValidator premiumIsPositive() {
        Object raw = response.jsonPath().get("monthlyPremium");
        assertThat("Expected monthlyPremium to be present",
                raw, notNullValue());
        double premium = ((Number) raw).doubleValue();
        assertThat("Expected monthlyPremium to be positive",
                premium, greaterThan(0.0));
        return this;
    }

    /**
     * Assert monthly premium is greater than a minimum value.
     * Use for teen/senior/high-risk premium comparison tests.
     */
    public InsuranceResponseValidator premiumGreaterThan(double minPremium) {
        double actual = ((Number) response.jsonPath()
                .get("monthlyPremium")).doubleValue();
        assertThat("Expected premium " + actual
                        + " to exceed minimum " + minPremium,
                actual, greaterThan(minPremium));
        return this;
    }

    /**
     * Assert monthly premium is less than a maximum value.
     */
    public InsuranceResponseValidator premiumLessThan(double maxPremium) {
        double actual = ((Number) response.jsonPath()
                .get("monthlyPremium")).doubleValue();
        assertThat("Expected premium " + actual
                        + " to be under maximum " + maxPremium,
                actual, lessThan(maxPremium));
        return this;
    }

    /** Assert quote has a valid deductible value (250/500/1000/2000). */
    public InsuranceResponseValidator hasValidDeductible() {
        Integer deductible = response.jsonPath().getInt("deductible");
        assertThat("Expected valid deductible, got: " + deductible,
                VALID_DEDUCTIBLES, hasItem(deductible));
        return this;
    }

    /** Assert quote has a valid coverage type. */
    public InsuranceResponseValidator hasValidCoverageType() {
        String coverage = response.jsonPath().getString("coverageType");
        assertThat("Expected valid coverage type, got: " + coverage,
                VALID_COVERAGE_TYPES, hasItem(coverage));
        return this;
    }

    /** Assert quote expiry date is present. */
    public InsuranceResponseValidator hasExpiryDate() {
        assertThat("Expected expiresAt to be present",
                response.jsonPath().getString("expiresAt"),
                notNullValue());
        return this;
    }

    /**
     * Composite — verifies complete quote structure.
     */
    public InsuranceResponseValidator hasCompleteQuoteStructure() {
        return hasQuoteId()
                .premiumIsPositive()
                .hasField("annualPremium")
                .hasValidQuoteStatus()
                .hasExpiryDate();
    }

    // ── Policy assertions ─────────────────────────────────────────────────────

    /** Assert response contains a valid policy ID. */
    public InsuranceResponseValidator hasPolicyId() {
        String policyId = response.jsonPath().getString("policyId");
        assertThat("Expected policyId to be present",
                policyId, notNullValue());
        assertThat("Expected policyId to be non-blank",
                policyId.isBlank(), is(false));
        return this;
    }

    /** Assert policy status is one of valid values. */
    public InsuranceResponseValidator hasValidPolicyStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid policy status, got: " + status,
                VALID_POLICY_STATUSES, hasItem(status));
        return this;
    }

    /** Assert policy is active. */
    public InsuranceResponseValidator isPolicyActive() {
        assertThat("Expected active policy status",
                response.jsonPath().getString("status"),
                equalTo("active"));
        return this;
    }

    /** Assert policy is cancelled. */
    public InsuranceResponseValidator isPolicyCancelled() {
        assertThat("Expected cancelled policy status",
                response.jsonPath().getString("status"),
                equalTo("cancelled"));
        return this;
    }

    /**
     * Composite — verifies complete policy structure.
     */
    public InsuranceResponseValidator hasCompletePolicyStructure() {
        return hasPolicyId()
                .hasValidPolicyStatus()
                .hasField("effectiveDate")
                .hasField("expirationDate")
                .hasField("premium");
    }

    // ── Claims assertions ─────────────────────────────────────────────────────

    /** Assert response contains a valid claim ID. */
    public InsuranceResponseValidator hasClaimId() {
        String claimId = response.jsonPath().getString("claimId");
        assertThat("Expected claimId to be present",
                claimId, notNullValue());
        assertThat("Expected claimId to be non-blank",
                claimId.isBlank(), is(false));
        return this;
    }

    /** Assert claim status is one of valid values. */
    public InsuranceResponseValidator hasValidClaimStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid claim status, got: " + status,
                VALID_CLAIM_STATUSES, hasItem(status));
        return this;
    }

    /** Assert claim amount is a positive number. */
    public InsuranceResponseValidator claimAmountIsPositive() {
        Object raw = response.jsonPath().get("claimAmount");
        assertThat("Expected claimAmount to be present",
                raw, notNullValue());
        assertThat("Expected claimAmount to be positive",
                ((Number) raw).doubleValue(), greaterThan(0.0));
        return this;
    }

    /**
     * Composite — verifies complete claim structure.
     */
    public InsuranceResponseValidator hasCompleteClaimStructure() {
        return hasClaimId()
                .hasValidClaimStatus()
                .claimAmountIsPositive()
                .hasField("incidentDate")
                .hasField("policyId");
    }
}
