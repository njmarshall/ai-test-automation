package com.aitesting.shared.dataprovider;

import com.github.javafaker.Faker;

import java.util.Map;
import java.util.Random;

/**
 * TestDataFactory — CRTP base class for all industry test data factories.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: Curiously Recurring Template Pattern (CRTP)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Problem solved:
 *   Delegation pattern required every subclass to mirror ALL generic
 *   methods. Adding one method to the base meant updating N subclass
 *   files — one per industry capstone. At scale (10+ capstones) this
 *   becomes a serious maintenance burden.
 *
 * CRTP solution:
 *   Subclasses inherit all generic methods automatically via true
 *   Java inheritance. Adding a method to this base class instantly
 *   propagates to ALL subclasses with zero code changes.
 *
 * Type parameter T:
 *   T is the concrete subclass type. Every method returns T instead
 *   of TestDataFactory, enabling type-safe fluent chaining across
 *   the inheritance hierarchy.
 *
 * The @SuppressWarnings("unchecked") on self():
 *   Java's type erasure means the compiler cannot verify (T) this
 *   at runtime. However the cast IS safe by mathematical construction
 *   of CRTP — T is always the concrete subclass, and 'this' IS that
 *   subclass. The annotation is a professional signature saying:
 *   "I verified this cast. The compiler cannot, but I can."
 *
 * ═══════════════════════════════════════════════════════════════════════
 * HIERARCHY
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   TestDataFactory<T>                    ← this class (generic)
 *     ├── PetTestDataFactory              ← PetStore capstone
 *     ├── InsuranceTestDataFactory        ← insurance capstone
 *     ├── FhirTestDataFactory             ← Healthcare capstone (planned)
 *     └── PaymentTestDataFactory          ← Payment capstone (planned)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // Test classes import ONLY the domain subclass:
 *   import com.aitesting.api.petstore.PetTestDataFactory;
 *
 *   // Fluent builder chain — generic + domain in one:
 *   Map<String, Object> pet = PetTestDataFactory.create()
 *       .withRandomId()           // ← from TestDataFactory<T>
 *       .withName("Buddy")        // ← from PetTestDataFactory
 *       .withStatus("available")  // ← from PetTestDataFactory
 *       .build();
 *
 *   Map<String, Object> ghost = PetTestDataFactory.create()
 *       .withNonExistentId()      // ← from TestDataFactory<T>
 *       .withName("Ghost Pet")    // ← from PetTestDataFactory
 *       .build();
 */
public abstract class TestDataFactory<T extends TestDataFactory<T>> {

    // ── Shared Faker instance ─────────────────────────────────────────────────

    protected static final Faker  faker  = new Faker();
    protected static final Random random = new Random();

    // ── Builder state — generic fields ────────────────────────────────────────

    protected long   id;
    protected String email;
    protected String phone;
    protected String fullName;
    protected String username;

    // ── CRTP core ─────────────────────────────────────────────────────────────

    /**
     * Returns 'this' cast to T (the concrete subclass).
     *
     * This is the heart of CRTP. Every fluent method calls self()
     * so the return type is always the concrete subclass, not the
     * abstract base. This enables method chaining across the hierarchy
     * without losing the subclass type.
     *
     * @SuppressWarnings("unchecked") — safe by CRTP construction.
     * T is always the concrete subclass. 'this' IS that subclass.
     * The compiler cannot verify this due to Java type erasure,
     * but it is mathematically guaranteed by the type bound:
     * T extends TestDataFactory<T>
     */
    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    // ── Abstract contract ─────────────────────────────────────────────────────

    /**
     * Build the final payload Map for the API request.
     * Every subclass must implement this — it is the
     * domain-specific assembly of state into a request body.
     */
    public abstract Map<String, Object> build();

    // ── Generic builder methods ───────────────────────────────────────────────
    // Each returns T so the chain stays at the subclass type.
    // Adding a method here instantly propagates to ALL subclasses.

    /**
     * Set a random entity ID (1 to 999,999).
     * Use for standard create/update operations.
     */
    public T withRandomId() {
        this.id = faker.number().numberBetween(1L, 999_999L);
        return self();
    }

    /**
     * Set a non-existent ID (999,999,999).
     * Use for GET/DELETE not-found (404) test scenarios.
     */
    public T withNonExistentId() {
        this.id = 999_999_999L;
        return self();
    }

    /**
     * Set an explicit ID value.
     * Use when a specific ID is required (e.g. update tests).
     */
    public T withId(long id) {
        this.id = id;
        return self();
    }

    /**
     * Set a random realistic email address.
     */
    public T withRandomEmail() {
        this.email = faker.internet().emailAddress();
        return self();
    }

    /**
     * Set an explicit email address.
     */
    public T withEmail(String email) {
        this.email = email;
        return self();
    }

    /**
     * Set a random US-style phone number.
     */
    public T withRandomPhone() {
        this.phone = faker.phoneNumber().phoneNumber();
        return self();
    }

    /**
     * Set a random full name (first + last).
     */
    public T withRandomFullName() {
        this.fullName = faker.name().fullName();
        return self();
    }

    /**
     * Set a random username (no spaces, lowercase).
     */
    public T withRandomUsername() {
        this.username = faker.name().username();
        return self();
    }

    // ── Static utility methods ────────────────────────────────────────────────
    // These do NOT need builder state — they are pure generators.
    // Subclasses inherit these directly — no delegation needed.

    /** Random positive long — suitable for any entity ID. */
    public static long randomId() {
        return faker.number().numberBetween(1L, 999_999L);
    }

    /** An ID guaranteed not to exist in the system. */
    public static long nonExistentId() {
        return 999_999_999L;
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

    /** A string exceeding typical field length limits (5000 chars). */
    public static String oversizedString() {
        return faker.lorem().characters(5000);
    }

    /** SQL injection payload for security boundary tests. */
    public static String sqlInjectionPayload() {
        return "' OR '1'='1'; DROP TABLE pets; --";
    }

    /** XSS payload for security boundary tests. */
    public static String xssPayload() {
        return "<script>alert('xss')</script>";
    }
}
