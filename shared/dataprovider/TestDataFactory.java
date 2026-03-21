package com.aitesting.shared.dataprovider;

import com.github.javafaker.Faker;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TestDataFactory generates realistic, randomised test data for all projects.
 *
 * Using random-but-valid data (rather than hard-coded strings) means tests
 * don't pollute each other's state and are more likely to catch edge cases.
 *
 * Usage:
 *   String  name   = TestDataFactory.randomPetName();
 *   String  email  = TestDataFactory.randomEmail();
 *   long    id     = TestDataFactory.randomId();
 *   Map<..> petMap = TestDataFactory.petPayload("Buddy", "available");
 */
public final class TestDataFactory {

    private static final Faker faker = new Faker();
    private static final Random random = new Random();

    // ── Generic ───────────────────────────────────────────────────────────────

    /** Random positive long — suitable for entity IDs. */
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

    /** Random full name. */
    public static String randomFullName() {
        return faker.name().fullName();
    }

    /** Random username (no spaces, lowercase). */
    public static String randomUsername() {
        return faker.name().username();
    }

    /** Random alphanumeric string of given length. */
    public static String randomString(int length) {
        return faker.lorem().characters(length, true, false);
    }

    // ── PetStore domain ───────────────────────────────────────────────────────

    private static final String[] PET_NAMES = {
            "Buddy", "Max", "Bella", "Charlie", "Luna", "Cooper", "Daisy", "Milo",
            "Lola", "Rocky", "Molly", "Bear", "Sophie", "Duke", "Chloe"
    };

    private static final String[] PET_STATUSES = {"available", "pending", "sold"};

    private static final String[] CATEGORIES = {
            "Dog", "Cat", "Bird", "Fish", "Reptile", "Small Animal"
    };

    /** Random pet name from a realistic list. */
    public static String randomPetName() {
        return PET_NAMES[random.nextInt(PET_NAMES.length)];
    }

    /** Random pet status: "available" | "pending" | "sold". */
    public static String randomPetStatus() {
        return PET_STATUSES[random.nextInt(PET_STATUSES.length)];
    }

    /** Random pet category name. */
    public static String randomCategory() {
        return CATEGORIES[random.nextInt(CATEGORIES.length)];
    }

    /**
     * Builds a full PetStore /pet POST payload as a Map (serialised to JSON by RestAssured).
     *
     * @param name    pet name
     * @param status  "available" | "pending" | "sold"
     */
    public static Map<String, Object> petPayload(String name, String status) {
        return Map.of(
                "id",       randomId(),
                "name",     name,
                "status",   status,
                "category", Map.of("id", randomId(), "name", randomCategory()),
                "tags",     List.of(Map.of("id", randomId(), "name", faker.lorem().word())),
                "photoUrls", List.of("https://example.com/photos/" + faker.internet().uuid())
        );
    }

    /** Convenience overload — random name and status. */
    public static Map<String, Object> randomPetPayload() {
        return petPayload(randomPetName(), randomPetStatus());
    }

    // ── PetStore Order ────────────────────────────────────────────────────────

    private static final String[] ORDER_STATUSES = {"placed", "approved", "delivered"};

    public static String randomOrderStatus() {
        return ORDER_STATUSES[random.nextInt(ORDER_STATUSES.length)];
    }

    /**
     * Builds a PetStore /store/order POST payload.
     *
     * @param petId  ID of the pet to order
     */
    public static Map<String, Object> orderPayload(long petId) {
        return Map.of(
                "id",       randomId(),
                "petId",    petId,
                "quantity", random.nextInt(5) + 1,
                "shipDate", "2025-01-01T00:00:00.000Z",
                "status",   "placed",
                "complete", false
        );
    }

    // ── PetStore User ─────────────────────────────────────────────────────────

    /**
     * Builds a PetStore /user POST payload.
     */
    public static Map<String, Object> userPayload() {
        String first = faker.name().firstName();
        String last  = faker.name().lastName();
        return Map.of(
                "id",         randomId(),
                "username",   (first + last).toLowerCase(),
                "firstName",  first,
                "lastName",   last,
                "email",      faker.internet().emailAddress(),
                "password",   faker.internet().password(8, 16),
                "phone",      faker.phoneNumber().phoneNumber(),
                "userStatus", 1
        );
    }

    // ── Boundary / negative data ──────────────────────────────────────────────

    /** An ID that should not exist in the system. */
    public static long nonExistentId() {
        return 999_999_999L;
    }

    /** A string exceeding typical field length limits (for negative tests). */
    public static String oversizedString() {
        return faker.lorem().characters(5000);
    }

    /** Common SQL injection string for security boundary tests. */
    public static String sqlInjectionPayload() {
        return "' OR '1'='1'; DROP TABLE pets; --";
    }

    /** Prevent instantiation. */
    private TestDataFactory() {}
}
