package com.aitesting.api.petstore;

import com.aitesting.shared.dataprovider.TestDataFactory;
import com.github.javafaker.Faker;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * PetTestDataFactory generates PetStore-specific test data.
 *
 * Design principle — Single Responsibility:
 *   PetStore payload construction belongs here, NOT in the shared
 *   library. The shared TestDataFactory provides generic utilities
 *   (randomId, randomEmail) that this factory uses internally.
 *
 *   This keeps shared/ free of any industry-specific knowledge.
 *   Every industry capstone has its own factory:
 *     → PetStore:   PetTestDataFactory       (here)
 *     → Insurance:  InsuranceTestDataFactory  (api/insurance/)
 *     → Healthcare: FhirTestDataFactory       (api/healthcare/)
 *
 * Usage:
 *   Map<..> pet   = PetTestDataFactory.petPayload("Buddy", "available");
 *   Map<..> pet   = PetTestDataFactory.randomPetPayload();
 *   Map<..> order = PetTestDataFactory.orderPayload(petId);
 *   Map<..> user  = PetTestDataFactory.userPayload();
 */
public final class PetTestDataFactory {

    private static final Faker  faker  = new Faker();
    private static final Random random = new Random();

    // ── Pet domain constants ──────────────────────────────────────────────────

    private static final String[] PET_NAMES = {
        "Buddy", "Max", "Bella", "Charlie", "Luna",
        "Cooper", "Daisy", "Milo", "Lola", "Rocky",
        "Molly", "Bear", "Sophie", "Duke", "Chloe"
    };

    private static final String[] PET_STATUSES = {
        "available", "pending", "sold"
    };

    private static final String[] CATEGORIES = {
        "Dog", "Cat", "Bird", "Fish", "Reptile", "Small Animal"
    };

    private static final String[] ORDER_STATUSES = {
        "placed", "approved", "delivered"
    };

    // ── Pet payloads ──────────────────────────────────────────────────────────

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
     * Builds a full PetStore POST /pet payload.
     * Uses shared TestDataFactory for generic random values.
     *
     * @param name   pet name (e.g. "Buddy")
     * @param status "available" | "pending" | "sold"
     */
    public static Map<String, Object> petPayload(
            String name, String status) {
        return Map.of(
            "id",        TestDataFactory.randomId(),
            "name",      name,
            "status",    status,
            "category",  Map.of(
                "id",    TestDataFactory.randomId(),
                "name",  randomCategory()
            ),
            "tags",      List.of(Map.of(
                "id",    TestDataFactory.randomId(),
                "name",  faker.lorem().word()
            )),
            "photoUrls", List.of(
                "https://example.com/photos/"
                + TestDataFactory.randomUuid()
            )
        );
    }

    /** Convenience overload — fully random name and status. */
    public static Map<String, Object> randomPetPayload() {
        return petPayload(randomPetName(), randomPetStatus());
    }

    // ── Order payloads ────────────────────────────────────────────────────────

    /** Random order status: "placed" | "approved" | "delivered". */
    public static String randomOrderStatus() {
        return ORDER_STATUSES[random.nextInt(ORDER_STATUSES.length)];
    }

    /**
     * Builds a PetStore POST /store/order payload.
     *
     * @param petId ID of the pet being ordered
     */
    public static Map<String, Object> orderPayload(long petId) {
        return Map.of(
            "id",       TestDataFactory.randomId(),
            "petId",    petId,
            "quantity", random.nextInt(5) + 1,
            "shipDate", "2025-01-01T00:00:00.000Z",
            "status",   "placed",
            "complete", false
        );
    }

    // ── User payloads ─────────────────────────────────────────────────────────

    /**
     * Builds a PetStore POST /user payload.
     * Uses shared TestDataFactory for generic random values.
     */
    public static Map<String, Object> userPayload() {
        String first = faker.name().firstName();
        String last  = faker.name().lastName();
        return Map.of(
            "id",         TestDataFactory.randomId(),
            "username",   (first + last).toLowerCase(),
            "firstName",  first,
            "lastName",   last,
            "email",      TestDataFactory.randomEmail(),
            "password",   faker.internet().password(8, 16),
            "phone",      TestDataFactory.randomPhone(),
            "userStatus", 1
        );
    }

    /** Prevent instantiation — static utility class. */
    private PetTestDataFactory() {}
}
