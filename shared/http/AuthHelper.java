package com.aitesting.shared.http;

import com.aitesting.shared.config.BaseConfig;
import io.restassured.specification.RequestSpecification;

/**
 * AuthHelper injects authentication headers into a RequestSpecification.
 *
 * Supports: Bearer token, API key header, Basic auth.
 * Designed to be chained onto ApiClient.given() for auth overrides per-test.
 *
 * Usage:
 *   Response r = AuthHelper.withBearer("myToken", ApiClient.given()).get("/pet/1");
 *   Response r = AuthHelper.withApiKey("myKey",   ApiClient.given()).post("/pet", body);
 *   Response r = AuthHelper.noAuth(ApiClient.given()).get("/pet/1");  // test 401 flows
 */
public final class AuthHelper {

    /**
     * Injects a Bearer token from BaseConfig (default auth for the test run).
     */
    public static RequestSpecification withDefaultAuth(RequestSpecification spec) {
        if (!BaseConfig.BEARER_TOKEN.isBlank()) {
            return spec.header("Authorization", "Bearer " + BaseConfig.BEARER_TOKEN);
        }
        if (!BaseConfig.API_KEY.isBlank()) {
            return spec.header("api_key", BaseConfig.API_KEY);
        }
        return spec;
    }

    /**
     * Injects an explicit Bearer token — useful for multi-user or role-based tests.
     */
    public static RequestSpecification withBearer(String token, RequestSpecification spec) {
        return spec.header("Authorization", "Bearer " + token);
    }

    /**
     * Injects an explicit API key.
     */
    public static RequestSpecification withApiKey(String key, RequestSpecification spec) {
        return spec.header("api_key", key);
    }

    /**
     * Injects HTTP Basic auth credentials.
     */
    public static RequestSpecification withBasic(String username, String password,
                                                 RequestSpecification spec) {
        return spec.auth().basic(username, password);
    }

    /**
     * Strips all auth headers — use when testing 401/403 responses.
     */
    public static RequestSpecification noAuth(RequestSpecification spec) {
        return spec.auth().none();
    }

    private AuthHelper() {}
}