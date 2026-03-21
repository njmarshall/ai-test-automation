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
 * ApiClient is the single entry point for all HTTP calls in the framework.
 *
 * Every test project calls ApiClient.get(), .post(), .put(), .patch(), .delete()
 * instead of configuring RestAssured from scratch. This guarantees:
 *   - Consistent base URL, timeouts, and content-type across all tests
 *   - Automatic Allure request/response attachment
 *   - Centralised auth injection (API key or Bearer token)
 *   - Optional full request/response logging controlled by BaseConfig
 *
 * Usage:
 *   Response r = ApiClient.get("/pet/1");
 *   Response r = ApiClient.post("/pet", petBody);
 *   Response r = ApiClient.get("/pet/findByStatus", Map.of("status", "available"));
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    /** Shared base spec — built once at class load, reused for every request. */
    private static final RequestSpecification BASE_SPEC;

    static {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(BaseConfig.BASE_URL)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(
                        io.restassured.config.RestAssuredConfig.config()
                                .httpClient(
                                        io.restassured.config.HttpClientConfig.httpClientConfig()
                                                .setParam("http.connection.timeout", BaseConfig.REQUEST_TIMEOUT_MS)
                                                .setParam("http.socket.timeout",     BaseConfig.REQUEST_TIMEOUT_MS)
                                )
                )
                .addFilter(new AllureRestAssured());   // attaches req/resp to Allure

        // Inject auth if configured
        if (!BaseConfig.BEARER_TOKEN.isBlank()) {
            builder.addHeader("Authorization", "Bearer " + BaseConfig.BEARER_TOKEN);
        } else if (!BaseConfig.API_KEY.isBlank()) {
            builder.addHeader("api_key", BaseConfig.API_KEY);
        }

        if (BaseConfig.LOG_ALL_REQUESTS) {
            builder.log(LogDetail.ALL);
        }

        BASE_SPEC = builder.build();
        log.info("ApiClient initialised — baseUri: {}", BaseConfig.BASE_URL);
    }

    // ── HTTP Methods ──────────────────────────────────────────────────────────

    /** GET with no query params. */
    public static Response get(String path) {
        log.debug("GET {}", path);
        return given().get(path);
    }

    /** GET with query parameters. */
    public static Response get(String path, Map<String, ?> queryParams) {
        log.debug("GET {} params={}", path, queryParams);
        return given().queryParams(queryParams).get(path);
    }

    /** POST with a request body. */
    public static Response post(String path, Object body) {
        log.debug("POST {}", path);
        return given().body(body).post(path);
    }

    /** PUT with a request body. */
    public static Response put(String path, Object body) {
        log.debug("PUT {}", path);
        return given().body(body).put(path);
    }

    /** PATCH with a request body. */
    public static Response patch(String path, Object body) {
        log.debug("PATCH {}", path);
        return given().body(body).patch(path);
    }

    /** DELETE — no body. */
    public static Response delete(String path) {
        log.debug("DELETE {}", path);
        return given().delete(path);
    }

    /** DELETE with path params pre-baked (e.g. /pet/{id}). */
    public static Response delete(String path, Map<String, ?> pathParams) {
        log.debug("DELETE {} pathParams={}", path, pathParams);
        return given().pathParams(pathParams).delete(path);
    }

    /**
     * Returns a RequestSpecification pre-loaded with the base spec.
     * Use this when you need advanced customisation (e.g. multipart, custom headers).
     *
     * Example:
     *   Response r = ApiClient.given()
     *       .header("X-Custom-Header", "value")
     *       .queryParam("format", "xml")
     *       .get("/pet/1");
     */
    public static RequestSpecification given() {
        return RestAssured.given().spec(BASE_SPEC);
    }

    /** Prevent instantiation — this is a static utility class. */
    private ApiClient() {}
}