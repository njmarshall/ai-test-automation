package com.aitesting.shared.assertions;

import com.aitesting.shared.config.BaseConfig;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * ResponseValidator — CRTP base class for all industry assertion validators.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: Curiously Recurring Template Pattern (CRTP)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Problem solved:
 *   Without CRTP, base class methods return ResponseValidator (base).
 *   After calling a base method, subclass methods are invisible:
 *
 *   BROKEN (without CRTP):
 *     PetResponseValidator.from(response)
 *         .statusCode(200)        // returns ResponseValidator ← base!
 *         .hasValidPetStatus()    // compile error — not on base type
 *
 *   FIXED (with CRTP):
 *     PetResponseValidator.from(response)
 *         .statusCode(200)        // returns PetResponseValidator ← T
 *         .hasValidPetStatus()    // compiles — chain stayed at subtype
 *
 * Hierarchy:
 *   ResponseValidator<T>
 *     PetResponseValidator
 *     InsuranceResponseValidator  (planned)
 *     HealthResponseValidator     (planned)
 *     PaymentResponseValidator    (planned)
 */
public class ResponseValidator<T extends ResponseValidator<T>> {

    private static final Logger log =
        LoggerFactory.getLogger(ResponseValidator.class);

    protected final Response response;

    protected ResponseValidator(Response response) {
        this.response = response;
    }

    /**
     * Returns 'this' cast to T.
     * Safe by CRTP construction — T is always the concrete subclass.
     * @SuppressWarnings: compiler cannot verify due to type erasure.
     */
    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    /** Generic factory — use subclass from() for domain assertions. */
    public static ResponseValidator<?> from(Response response) {
        return new ResponseValidator<>(response);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public T statusCode(int expected) {
        assertThat("Expected HTTP status " + expected,
                response.getStatusCode(), equalTo(expected));
        return self();
    }

    public T is2xx() {
        int code = response.getStatusCode();
        assertThat("Expected 2xx, got " + code,
                code, allOf(greaterThanOrEqualTo(200), lessThan(300)));
        return self();
    }

    public T is4xx() {
        int code = response.getStatusCode();
        assertThat("Expected 4xx, got " + code,
                code, allOf(greaterThanOrEqualTo(400), lessThan(500)));
        return self();
    }

    // ── Performance ───────────────────────────────────────────────────────────

    public T withinSla() {
        long elapsed = response.getTime();
        assertThat("Response time " + elapsed + "ms exceeded SLA "
                + BaseConfig.RESPONSE_TIME_SLA_MS + "ms",
                elapsed,
                lessThanOrEqualTo(BaseConfig.RESPONSE_TIME_SLA_MS));
        return self();
    }

    public T withinMs(long maxMs) {
        long elapsed = response.getTime();
        assertThat("Response time " + elapsed + "ms exceeded " + maxMs + "ms",
                elapsed, lessThanOrEqualTo(maxMs));
        return self();
    }

    // ── Content type ──────────────────────────────────────────────────────────

    public T contentType(String expected) {
        assertThat("Content-Type mismatch",
                response.getContentType(), containsString(expected));
        return self();
    }

    // ── JSON field checks ─────────────────────────────────────────────────────

    public T hasField(String jsonPath) {
        assertThat("Expected field '" + jsonPath + "' to exist",
                response.jsonPath().get(jsonPath), notNullValue());
        return self();
    }

    public T fieldEquals(String jsonPath, Object expected) {
        assertThat("Field '" + jsonPath + "'",
                response.jsonPath().get(jsonPath), equalTo(expected));
        return self();
    }

    public T fieldContains(String jsonPath, String substring) {
        assertThat("Field '" + jsonPath + "' should contain '" + substring + "'",
                response.jsonPath().getString(jsonPath),
                containsString(substring));
        return self();
    }

    public T listIsNotEmpty(String jsonPath) {
        assertThat("Expected non-empty list at '" + jsonPath + "'",
                response.jsonPath().getList(jsonPath), not(empty()));
        return self();
    }

    public T listSize(String jsonPath, int expectedSize) {
        assertThat("List size at '" + jsonPath + "'",
                response.jsonPath().getList(jsonPath), hasSize(expectedSize));
        return self();
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    public T matchesSchema(String schemaClasspathPath) {
        response.then().assertThat()
                .body(JsonSchemaValidator
                    .matchesJsonSchemaInClasspath(schemaClasspathPath));
        log.debug("Schema validation passed: {}", schemaClasspathPath);
        return self();
    }

    // ── Raw access ────────────────────────────────────────────────────────────

    public <V> V extract(String jsonPath) {
        return response.jsonPath().get(jsonPath);
    }

    public Response raw() {
        return response;
    }
}
