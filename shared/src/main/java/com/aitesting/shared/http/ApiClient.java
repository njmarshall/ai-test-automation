package com.aitesting.shared.http;

import com.aitesting.shared.config.BaseConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * ApiClient — HTTP client wrapper over RestAssured.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: Supports both static usage (PetStore) and instance usage
 *         (insurance/Healthcare via ApiClientFactory).
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Java cannot have instance and static methods with the same name.
 * Solution: ALL methods are instance methods. Static convenience methods
 * are provided with a "static" prefix for backward compatibility, OR
 * test classes use a static DEFAULT instance directly.
 *
 * Migration path:
 *   Phase 1 (PetStore — existing):
 *     Uses ApiClient.DEFAULT.get() or static helper methods.
 *
 *   Phase 2 (insurance — new):
 *     Uses ApiClientFactory.forWireMock() → instance methods.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // Existing static style (PetStore — unchanged):
 *   Response r = ApiClient.DEFAULT.get("/pet/1");
 *   Response r = ApiClient.DEFAULT.post("/pet", payload);
 *
 *   // New instance style (insurance via factory):
 *   ApiClient api = ApiClientFactory.forWireMock();
 *   Response r = api.get("/claims/123");
 *   Response r = api.post("/claims", payload);
 */
public class ApiClient {

    private static final Logger log =
        LoggerFactory.getLogger(ApiClient.class);

    // ── DEFAULT static instance — backward compatible ─────────────────────────

    /**
     * Shared default instance pointing at BaseConfig.BASE_URL.
     * PetStore tests use ApiClient.DEFAULT.get() etc.
     * Replaces the old static method approach.
     */
    public static final ApiClient DEFAULT =
        new ApiClient(BaseConfig.BASE_URL);

    // ── Instance spec ─────────────────────────────────────────────────────────

    private final RequestSpecification spec;
    private final String baseUrl;

    /**
     * Package-private constructor — use ApiClientFactory to create instances.
     * Direct construction only for DEFAULT instance above.
     *
     * @param baseUrl the API server base URL for this client instance
     */
    ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;

        RequestSpecBuilder builder = new RequestSpecBuilder()
            .setBaseUri(baseUrl)
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(
                io.restassured.config.RestAssuredConfig.config()
                    .httpClient(
                        io.restassured.config.HttpClientConfig
                            .httpClientConfig()
                            .setParam("http.connection.timeout",
                                BaseConfig.REQUEST_TIMEOUT_MS)
                            .setParam("http.socket.timeout",
                                BaseConfig.REQUEST_TIMEOUT_MS)
                    )
            )
            .addFilter(new AllureRestAssured());

        if (!BaseConfig.BEARER_TOKEN.isBlank()) {
            builder.addHeader("Authorization",
                "Bearer " + BaseConfig.BEARER_TOKEN);
        } else if (!BaseConfig.API_KEY.isBlank()) {
            builder.addHeader("api_key", BaseConfig.API_KEY);
        }

        if (BaseConfig.LOG_ALL_REQUESTS) {
            builder.log(LogDetail.ALL);
        }

        this.spec = builder.build();
        log.info("ApiClient created — baseUri: {}", baseUrl);
    }

    // ── Instance HTTP methods ─────────────────────────────────────────────────

    /** GET with no query params. */
    public Response get(String path) {
        log.debug("GET {}", path);
        return RestAssured.given().spec(spec).get(path);
    }

    /** GET with query parameters. */
    public Response get(String path, Map<String, ?> queryParams) {
        log.debug("GET {} params={}", path, queryParams);
        return RestAssured.given().spec(spec)
            .queryParams(queryParams).get(path);
    }

    /** POST with a request body. */
    public Response post(String path, Object body) {
        log.debug("POST {}", path);
        return RestAssured.given().spec(spec).body(body).post(path);
    }

    /** PUT with a request body. */
    public Response put(String path, Object body) {
        log.debug("PUT {}", path);
        return RestAssured.given().spec(spec).body(body).put(path);
    }

    /** PATCH with a request body. */
    public Response patch(String path, Object body) {
        log.debug("PATCH {}", path);
        return RestAssured.given().spec(spec).body(body).patch(path);
    }

    /** DELETE — no body. */
    public Response delete(String path) {
        log.debug("DELETE {}", path);
        return RestAssured.given().spec(spec).delete(path);
    }

    /**
     * Returns the underlying RequestSpecification for advanced use cases
     * such as multipart uploads or custom headers.
     */
    public RequestSpecification given() {
        return RestAssured.given().spec(spec);
    }

    /** Returns the base URL this client is configured for. */
    public String getBaseUrl() {
        return baseUrl;
    }
}
