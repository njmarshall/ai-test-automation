package com.aitesting.petstore.api;

import com.aitesting.shared.assertions.ResponseValidator;
import io.restassured.response.Response;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * PetResponseValidator — PetStore domain assertion validator.
 *
 * Extends ResponseValidator<PetResponseValidator> so T = this class.
 * Every inherited base method returns PetResponseValidator keeping
 * the chain at the subclass type for full compile-time type safety.
 *
 * Usage:
 *   PetResponseValidator.from(response)
 *       .statusCode(200)        // inherited — returns PetResponseValidator
 *       .withinSla()            // inherited — returns PetResponseValidator
 *       .hasPetId()             // PetStore native
 *       .hasValidPetStatus()    // PetStore native
 *       .nameEquals("Buddy");   // PetStore native
 */
public class PetResponseValidator
        extends ResponseValidator<PetResponseValidator> {

    private static final List<String> VALID_STATUSES =
        Arrays.asList("available", "pending", "sold");

    private PetResponseValidator(Response response) {
        super(response);
    }

    public static PetResponseValidator from(Response response) {
        return new PetResponseValidator(response);
    }

    // ── PetStore domain assertions ────────────────────────────────────────────

    public PetResponseValidator hasPetId() {
        Object id = response.jsonPath().get("id");
        assertThat("Expected pet ID to be present", id, notNullValue());
        assertThat("Expected pet ID to be positive",
                ((Number) id).longValue(), greaterThan(0L));
        return this;
    }

    public PetResponseValidator hasValidPetStatus() {
        String status = response.jsonPath().getString("status");
        assertThat("Expected valid pet status, got: " + status,
                VALID_STATUSES, hasItem(status));
        return this;
    }

    public PetResponseValidator hasStatus(String expectedStatus) {
        assertThat("'" + expectedStatus + "' is not a valid PetStore status",
                VALID_STATUSES, hasItem(expectedStatus));
        assertThat("Pet status mismatch",
                response.jsonPath().getString("status"),
                equalTo(expectedStatus));
        return this;
    }

    public PetResponseValidator nameEquals(String expectedName) {
        assertThat("Pet name mismatch",
                response.jsonPath().getString("name"),
                equalTo(expectedName));
        return this;
    }

    public PetResponseValidator hasCategory() {
        assertThat("Expected category to be present",
                response.jsonPath().get("category"), notNullValue());
        assertThat("Expected category.name to be present",
                response.jsonPath().getString("category.name"),
                notNullValue());
        return this;
    }

    public PetResponseValidator hasPhotoUrls() {
        return (PetResponseValidator) listIsNotEmpty("photoUrls");
    }

    public PetResponseValidator hasTags() {
        return (PetResponseValidator) listIsNotEmpty("tags");
    }

    /** Composite — verifies complete pet structure in one call. */
    public PetResponseValidator hasCompleteStructure() {
        return hasPetId()
              .hasField("name")
              .hasValidPetStatus()
              .hasCategory()
              .hasPhotoUrls();
    }
}
