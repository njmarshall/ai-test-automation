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
 * ResponseValidator provides a fluent, chainable assertion API for REST responses.
 *
 * Every method returns `this` so assertions can be chained naturally:
 *
 *   ResponseValidator.from(response)
 *       .statusCode(200)
 *       .withinSla()
 *       .hasField("id")
 *       .fieldEquals("name", "Buddy")
 *       .listIsNotEmpty("tags")
 *       .matchesSchema("schemas/pet-schema.json");
 *
 * Design goals:
 *   - One import, zero RestAssured boilerplate in test classes
 *   - SLA check built in via BaseConfig.RESPONSE_TIME_SLA_MS
 *   - JSON schema validation in one call
 *   - Readable failure messages for Allure reports
 */
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);

    private final Response response;

    private ResponseValidator(Response response) {
        this.response = response;
    }

    /** Factory — start a validator chain from a RestAssured Response. */
    public static ResponseValidator from(Response response) {
        return new ResponseValidator(response);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    /** Assert exact HTTP status code. */
    public ResponseValidator statusCode(int expected) {
        assertThat("Expected HTTP status " + expected,
                response.getStatusCode(), equalTo(expected));
        return this;
    }

    /** Assert status code is in the 2xx range. */
    public ResponseValidator is2xx() {
        int code = response.getStatusCode();
        assertThat("Expected 2xx status, got " + code, code, allOf(greaterThanOrEqualTo(200), lessThan(300)));
        return this;
    }

    /** Assert status code is in the 4xx range. */
    public ResponseValidator is4xx() {
        int code = response.getStatusCode();
        assertThat("Expected 4xx status, got " + code, code, allOf(greaterThanOrEqualTo(400), lessThan(500)));
        return this;
    }

    // ── Performance ───────────────────────────────────────────────────────────

    /** Assert response arrived within the SLA defined in BaseConfig. */
    public ResponseValidator withinSla() {
        long elapsed = response.getTime();
        assertThat("Response time " + elapsed + "ms exceeded SLA of "
                        + BaseConfig.RESPONSE_TIME_SLA_MS + "ms",
                elapsed, lessThanOrEqualTo(BaseConfig.RESPONSE_TIME_SLA_MS));
        return this;
    }

    /** Assert response arrived within a custom millisecond threshold. */
    public ResponseValidator withinMs(long maxMs) {
        long elapsed = response.getTime();
        assertThat("Response time " + elapsed + "ms exceeded " + maxMs + "ms",
                elapsed, lessThanOrEqualTo(maxMs));
        return this;
    }

    // ── Content type ──────────────────────────────────────────────────────────

    /** Assert Content-Type header contains the given value (e.g. "application/json"). */
    public ResponseValidator contentType(String expected) {
        assertThat("Content-Type mismatch",
                response.getContentType(), containsString(expected));
        return this;
    }

    // ── JSON field checks ─────────────────────────────────────────────────────

    /** Assert a JSON path exists and is not null. */
    public ResponseValidator hasField(String jsonPath) {
        Object value = response.jsonPath().get(jsonPath);
        assertThat("Expected JSON field '" + jsonPath + "' to exist", value, notNullValue());
        return this;
    }

    /** Assert a JSON path equals the expected value. */
    public ResponseValidator fieldEquals(String jsonPath, Object expected) {
        Object actual = response.jsonPath().get(jsonPath);
        assertThat("Field '" + jsonPath + "'", actual, equalTo(expected));
        return this;
    }

    /** Assert a JSON path string contains the given substring. */
    public ResponseValidator fieldContains(String jsonPath, String substring) {
        String actual = response.jsonPath().getString(jsonPath);
        assertThat("Field '" + jsonPath + "' should contain '" + substring + "'",
                actual, containsString(substring));
        return this;
    }

    /** Assert a JSON array path is not empty. */
    public ResponseValidator listIsNotEmpty(String jsonPath) {
        List<?> list = response.jsonPath().getList(jsonPath);
        assertThat("Expected non-empty list at '" + jsonPath + "'", list, not(empty()));
        return this;
    }

    /** Assert a JSON array path has exactly the expected size. */
    public ResponseValidator listSize(String jsonPath, int expectedSize) {
        List<?> list = response.jsonPath().getList(jsonPath);
        assertThat("List size at '" + jsonPath + "'", list, hasSize(expectedSize));
        return this;
    }

    // ── Schema validation ─────────────────────────────────────────────────────

    /**
     * Validate response body against a JSON Schema file on the classpath.
     * Place schema files under src/test/resources/schemas/.
     *
     * Example: .matchesSchema("schemas/pet-schema.json")
     */
    public ResponseValidator matchesSchema(String schemaClasspathPath) {
        response.then().assertThat()
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath(schemaClasspathPath));
        log.debug("Schema validation passed: {}", schemaClasspathPath);
        return this;
    }

    // ── Raw access ────────────────────────────────────────────────────────────

    /** Extract a typed value from a JSON path for use in later assertions. */
    public <T> T extract(String jsonPath) {
        return response.jsonPath().get(jsonPath);
    }

    /** Returns the raw RestAssured Response for cases not covered by this API. */
    public Response raw() {
        return response;
    }
}
