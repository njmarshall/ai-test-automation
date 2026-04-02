package com.aitesting.api.petstore;

import com.aitesting.shared.dataprovider.TestDataFactory;

import java.util.List;
import java.util.Map;

/**
 * PetTestDataFactory — PetStore-specific test data factory.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: CRTP Subclass of TestDataFactory
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Extends TestDataFactory<PetTestDataFactory> which means:
 *   → T = PetTestDataFactory throughout the hierarchy
 *   → All generic methods (withRandomId, withNonExistentId, etc.)
 *     return PetTestDataFactory — chain never loses subclass type
 *   → All PetStore methods also return PetTestDataFactory
 *   → Result: fully type-safe fluent chain — ONE import in tests
 *
 * Inheritance benefit over Delegation:
 *   When TestDataFactory adds a new generic method (e.g. withZipCode),
 *   PetTestDataFactory gets it automatically — zero code changes here.
 *   Delegation required updating ALL N subclass files for every new
 *   generic method — a fatal scaling weakness at 10+ capstones.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE — test classes import ONLY this class
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   import com.aitesting.api.petstore.PetTestDataFactory;
 *
 *   // Builder chain — generic + domain, type-safe:
 *   Map<String, Object> pet = PetTestDataFactory.create()
 *       .withRandomId()           // inherited from TestDataFactory<T>
 *       .withName("Buddy")        // PetStore native
 *       .withStatus("available")  // PetStore native
 *       .build();
 *
 *   // Not-found scenario:
 *   Map<String, Object> ghost = PetTestDataFactory.create()
 *       .withNonExistentId()      // inherited from TestDataFactory<T>
 *       .withName("Ghost")
 *       .build();
 *
 *   // Backward-compatible static methods:
 *   Map<String, Object> pet   = PetTestDataFactory.petPayload("Buddy", "available");
 *   Map<String, Object> order = PetTestDataFactory.orderPayload(petId);
 *   Map<String, Object> user  = PetTestDataFactory.userPayload();
 *
 *   // Static utilities inherited — no TestDataFactory import needed:
 *   long id    = PetTestDataFactory.randomId();
 *   long ghost = PetTestDataFactory.nonExistentId();
 */
public final class PetTestDataFactory
        extends TestDataFactory<PetTestDataFactory> {

    // ── Domain constants ──────────────────────────────────────────────────────

    private static final String[] PET_NAMES = {
        "Buddy", "Max", "Bella", "Charlie", "Luna",
        "Cooper", "Daisy", "Milo", "Lola", "Rocky",
        "Molly", "Bear", "Sophie", "Duke", "Chloe"
    };

    public static final String[] PET_STATUSES = {
        "available", "pending", "sold"
    };

    private static final String[] CATEGORIES = {
        "Dog", "Cat", "Bird", "Fish", "Reptile", "Small Animal"
    };

    private static final String[] ORDER_STATUSES = {
        "placed", "approved", "delivered"
    };

    // ── Builder state ─────────────────────────────────────────────────────────

    private String       name;
    private String       status;
    private String       categoryName;
    private List<String> photoUrls;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Static factory method — entry point for the builder chain.
     * Pre-initialises with sensible random defaults so .build()
     * always produces a valid payload without requiring all fields.
     */
    public static PetTestDataFactory create() {
        return new PetTestDataFactory()
                .withRandomId()
                .withRandomName()
                .withRandomStatus();
    }

    private PetTestDataFactory() {}

    // ── Builder methods ───────────────────────────────────────────────────────

    public PetTestDataFactory withName(String name) {
        this.name = name;
        return this;
    }

    public PetTestDataFactory withRandomName() {
        this.name = PET_NAMES[random.nextInt(PET_NAMES.length)];
        return this;
    }

    public PetTestDataFactory withStatus(String status) {
        this.status = status;
        return this;
    }

    public PetTestDataFactory withRandomStatus() {
        this.status = PET_STATUSES[random.nextInt(PET_STATUSES.length)];
        return this;
    }

    public PetTestDataFactory withCategory(String categoryName) {
        this.categoryName = categoryName;
        return this;
    }

    public PetTestDataFactory withPhotoUrls(List<String> urls) {
        this.photoUrls = urls;
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Assembles builder state into the API request payload.
     * Applies random defaults for any unset fields.
     */
    @Override
    public Map<String, Object> build() {
        return Map.of(
            "id",        this.id,
            "name",      this.name != null ? this.name : randomPetName(),
            "status",    this.status != null ? this.status : randomPetStatus(),
            "category",  Map.of(
                "id",    randomId(),
                "name",  this.categoryName != null
                             ? this.categoryName : randomCategory()
            ),
            "tags",      List.of(Map.of(
                "id",    randomId(),
                "name",  faker.lorem().word()
            )),
            "photoUrls", this.photoUrls != null
                             ? this.photoUrls
                             : List.of("https://example.com/photos/"
                                       + randomUuid())
        );
    }

    // ── Static convenience methods (backward compatible) ──────────────────────

    /** Quick static payload — backward compatible with existing tests. */
    public static Map<String, Object> petPayload(
            String name, String status) {
        return create().withName(name).withStatus(status).build();
    }

    /** Quick random payload. */
    public static Map<String, Object> randomPetPayload() {
        return create().build();
    }

    /** Order payload for POST /store/order. */
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

    /** User payload for POST /user. */
    public static Map<String, Object> userPayload() {
        String first = faker.name().firstName();
        String last  = faker.name().lastName();
        return Map.of(
            "id",         randomId(),
            "username",   (first + last).toLowerCase(),
            "firstName",  first,
            "lastName",   last,
            "email",      randomEmail(),
            "password",   faker.internet().password(8, 16),
            "phone",      randomPhone(),
            "userStatus", 1
        );
    }

    // ── Static domain generators ──────────────────────────────────────────────

    public static String randomPetName() {
        return PET_NAMES[random.nextInt(PET_NAMES.length)];
    }

    public static String randomPetStatus() {
        return PET_STATUSES[random.nextInt(PET_STATUSES.length)];
    }

    public static String randomCategory() {
        return CATEGORIES[random.nextInt(CATEGORIES.length)];
    }

    public static String randomOrderStatus() {
        return ORDER_STATUSES[random.nextInt(ORDER_STATUSES.length)];
    }
}
