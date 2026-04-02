package com.aitesting.api.insurance;

import com.aitesting.api.models.InsuranceModels.*;
import com.aitesting.shared.dataprovider.TestDataFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * InsuranceTestDataFactory — Insurance-specific test data factory.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: CRTP Subclass of TestDataFactory
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Demonstrates CRTP scaling benefit:
 *   When TestDataFactory adds withZipCode(), withRandomAddress(), or
 *   any new generic method — InsuranceTestDataFactory inherits it
 *   automatically. Zero code changes required here.
 *
 *   Compare to Delegation: every new generic method in TestDataFactory
 *   required adding a delegate in EVERY subclass file.
 *   With 4 capstones that meant 4 file edits per generic addition.
 *   CRTP reduces that to 0 file edits.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   import com.aitesting.api.insurance.InsuranceTestDataFactory;
 *
 *   // Builder chain:
 *   QuoteRequest quote = InsuranceTestDataFactory.createQuote()
 *       .withRandomId()                  // inherited from TestDataFactory<T>
 *       .withCoverageType("comprehensive") // Insurance native
 *       .withDeductible(500)              // Insurance native
 *       .buildQuoteRequest();
 *
 *   // Static convenience:
 *   QuoteRequest standard = InsuranceTestDataFactory.standardQuoteRequest();
 *   QuoteRequest teen     = InsuranceTestDataFactory.teenDriverQuoteRequest();
 */
public final class InsuranceTestDataFactory
        extends TestDataFactory<InsuranceTestDataFactory> {

    // ── Domain constants ──────────────────────────────────────────────────────

    public static final String[] COVERAGE_TYPES = {
        "liability", "comprehensive", "collision"
    };

    public static final int[] VALID_DEDUCTIBLES = {
        250, 500, 1000, 2000
    };

    private static final String[] MAKES = {
        "Toyota", "Honda", "Ford", "Chevrolet", "BMW", "Tesla"
    };

    private static final String[] MODELS = {
        "Camry", "Civic", "F-150", "Malibu", "3 Series", "Model 3"
    };

    private static final String[] ZIPS = {
        "94105", "10001", "60601", "77001", "85001", "30301"
    };

    // ── Builder state ─────────────────────────────────────────────────────────

    private String  coverageType;
    private Integer deductible;
    private Integer driverAge;
    private Integer accidentsLast3Years;
    private String  vehicleMake;
    private String  vehicleModel;
    private Integer vehicleYear;

    // ── Entry points ──────────────────────────────────────────────────────────

    public static InsuranceTestDataFactory createQuote() {
        return new InsuranceTestDataFactory()
                .withRandomId()
                .withRandomCoverageType()
                .withDeductible(500)
                .withDriverAge(35)
                .withAccidents(0);
    }

    private InsuranceTestDataFactory() {}

    // ── Builder methods ───────────────────────────────────────────────────────

    public InsuranceTestDataFactory withCoverageType(String type) {
        this.coverageType = type;
        return this;
    }

    public InsuranceTestDataFactory withRandomCoverageType() {
        this.coverageType =
            COVERAGE_TYPES[random.nextInt(COVERAGE_TYPES.length)];
        return this;
    }

    public InsuranceTestDataFactory withDeductible(int deductible) {
        this.deductible = deductible;
        return this;
    }

    public InsuranceTestDataFactory withDriverAge(int age) {
        this.driverAge = age;
        return this;
    }

    public InsuranceTestDataFactory withAccidents(int count) {
        this.accidentsLast3Years = count;
        return this;
    }

    public InsuranceTestDataFactory withVehicle(
            String make, String model, int year) {
        this.vehicleMake  = make;
        this.vehicleModel = model;
        this.vehicleYear  = year;
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    public java.util.Map<String, Object> build() {
        return java.util.Map.of(
            "id",           this.id,
            "coverageType", this.coverageType,
            "deductible",   this.deductible
        );
    }

    /** Build full QuoteRequest domain object. */
    public QuoteRequest buildQuoteRequest() {
        return QuoteRequest.builder()
                .applicant(buildApplicant())
                .vehicle(buildVehicle())
                .coverageType(this.coverageType)
                .deductible(this.deductible)
                .addOns(List.of("roadside"))
                .build();
    }

    /** Build full PolicyRequest domain object. */
    public PolicyRequest buildPolicyRequest(String quoteId) {
        return PolicyRequest.builder()
                .quoteId(quoteId)
                .paymentMethod("monthly")
                .effectiveDate(LocalDate.now().plusDays(1).toString())
                .build();
    }

    // ── Private builders ──────────────────────────────────────────────────────

    private Applicant buildApplicant() {
        int age = this.driverAge != null ? this.driverAge : 35;
        return Applicant.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(LocalDate.now().minusYears(age).toString())
                .licenseNumber(faker.regexify("[A-Z]{1}[0-9]{7}"))
                .yearsLicensed(Math.max(1, age - 16))
                .accidentsLast3Years(
                    this.accidentsLast3Years != null
                        ? this.accidentsLast3Years : 0)
                .zipCode(ZIPS[random.nextInt(ZIPS.length)])
                .build();
    }

    private Vehicle buildVehicle() {
        int idx = random.nextInt(MAKES.length);
        return Vehicle.builder()
                .year(this.vehicleYear != null
                    ? this.vehicleYear : 2020 + random.nextInt(5))
                .make(this.vehicleMake  != null ? this.vehicleMake  : MAKES[idx])
                .model(this.vehicleModel != null ? this.vehicleModel : MODELS[idx])
                .vin(faker.regexify("[A-HJ-NPR-Z0-9]{17}"))
                .annualMileage(12000)
                .primaryUse("commute")
                .antiTheft(true)
                .safetyFeatures(true)
                .build();
    }

    // ── Static convenience methods ─────────────────────────────────────────────

    /** Standard low-risk applicant — expect approved status. */
    public static QuoteRequest standardQuoteRequest() {
        return createQuote()
                .withCoverageType("comprehensive")
                .withDeductible(500)
                .withDriverAge(35)
                .withAccidents(0)
                .buildQuoteRequest();
    }

    /** Teen driver age 18 — higher premium boundary test. */
    public static QuoteRequest teenDriverQuoteRequest() {
        return createQuote()
                .withCoverageType("liability")
                .withDeductible(1000)
                .withDriverAge(18)
                .withAccidents(0)
                .buildQuoteRequest();
    }

    /** Senior driver age 75 — upper age boundary test. */
    public static QuoteRequest seniorDriverQuoteRequest() {
        return createQuote()
                .withCoverageType("comprehensive")
                .withDeductible(500)
                .withDriverAge(75)
                .withAccidents(0)
                .buildQuoteRequest();
    }

    /** High-risk applicant — expect referred or declined. */
    public static QuoteRequest highRiskQuoteRequest() {
        return createQuote()
                .withCoverageType("collision")
                .withDeductible(2000)
                .withDriverAge(28)
                .withAccidents(3)
                .buildQuoteRequest();
    }

    /** Luxury vehicle — premium uplift test. */
    public static QuoteRequest luxuryVehicleQuoteRequest() {
        return createQuote()
                .withCoverageType("comprehensive")
                .withDeductible(1000)
                .withVehicle("BMW", "M5",
                    LocalDate.now().getYear())
                .buildQuoteRequest();
    }

    /** Missing applicant — expect 400. */
    public static QuoteRequest missingApplicantQuoteRequest() {
        QuoteRequest req = standardQuoteRequest();
        req.setApplicant(null);
        return req;
    }

    /** Invalid coverage type — expect 400. */
    public static QuoteRequest invalidCoverageQuoteRequest() {
        QuoteRequest req = standardQuoteRequest();
        req.setCoverageType("platinum_ultra_plus");
        return req;
    }

    /** Policy bind request from existing quote. */
    public static PolicyRequest bindPolicyRequest(String quoteId) {
        return createQuote().buildPolicyRequest(quoteId);
    }

    /** Backdated policy — expect 400. */
    public static PolicyRequest backdatedPolicyRequest(String quoteId) {
        return PolicyRequest.builder()
                .quoteId(quoteId)
                .paymentMethod("annual")
                .effectiveDate(
                    LocalDate.now().minusDays(30).toString())
                .build();
    }

    /** Standard applicant as domain object. */
    public static Applicant standardApplicant() {
        return createQuote().buildApplicant();
    }

    /** Standard vehicle as domain object. */
    public static Vehicle standardVehicle() {
        return createQuote().buildVehicle();
    }

    // ── Static domain generators ──────────────────────────────────────────────

    public static String randomCoverageType() {
        return COVERAGE_TYPES[random.nextInt(COVERAGE_TYPES.length)];
    }

    public static int randomDeductible() {
        return VALID_DEDUCTIBLES[random.nextInt(VALID_DEDUCTIBLES.length)];
    }
}
