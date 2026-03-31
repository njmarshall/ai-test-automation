package com.aitesting.shared.dataprovider;

import com.github.javafaker.Faker;

import java.util.Random;

/**
 * TestDataFactory generates generic, reusable test data for ALL projects.
 *
 * Design principle — Single Responsibility + Common Reuse:
 *   This class contains ONLY data that is genuinely useful to every
 *   industry capstone (PetStore, Insurance, Healthcare, Payment, etc.).
 *   Industry-specific payloads belong in their own factories:
 *     → PetStore:   PetTestDataFactory (api/petstore/)
 *     → Insurance:  InsuranceTestDataFactory (api/insurance/)
 *     → Healthcare: FhirTestDataFactory (api/healthcare/)
 *
 * Usage:
 *   long   id    = TestDataFactory.randomId();
 *   String email = TestDataFactory.randomEmail();
 *   String name  = TestDataFactory.randomFullName();
 *   long   ghost = TestDataFactory.nonExistentId();
 */
public final class TestDataFactory {

    private static final Faker  faker  = new Faker();
    private static final Random random = new Random();

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Random positive long — suitable for any entity ID. */
    public static long randomId() {
        return faker.number().numberBetween(1L, 999_999L);
    }

    /** Random realistic email address. */
    public static String randomEmail() {
        return faker.internet().emailAddress();
    }

    /** Random US-style phone number. */
    public static String randomPhone() {
        return faker.phoneNumber().phoneNumber();
    }

    /** Random full name (first + last). */
    public static String randomFullName() {
        return faker.name().fullName();
    }

    /** Random username — no spaces, lowercase. */
    public static String randomUsername() {
        return faker.name().username();
    }

    /** Random alphanumeric string of given length. */
    public static String randomString(int length) {
        return faker.lorem().characters(length, true, false);
    }

    /** Random UUID string. */
    public static String randomUuid() {
        return faker.internet().uuid();
    }

    // ── Boundary / Negative ───────────────────────────────────────────────────

    /**
     * An ID guaranteed not to exist in any system.
     * Use for GET/DELETE not-found (404) test scenarios.
     */
    public static long nonExistentId() {
        return 999_999_999L;
    }

    /**
     * A string exceeding typical field length limits.
     * Use for negative / boundary tests on string fields.
     */
    public static String oversizedString() {
        return faker.lorem().characters(5000);
    }

    /**
     * Standard SQL injection payload.
     * Use for security boundary tests on string input fields.
     */
    public static String sqlInjectionPayload() {
        return "' OR '1'='1'; DROP TABLE pets; --";
    }

    /**
     * Standard XSS payload.
     * Use for security boundary tests on string input fields.
     */
    public static String xssPayload() {
        return "<script>alert('xss')</script>";
    }

    /** Prevent instantiation — static utility class. */
    private TestDataFactory() {}
}
