package com.aitesting.shared.config;

/**
 * BaseConfig exposes typed, named constants for all framework-wide settings.
 * Tests read from BaseConfig; configuration values come from EnvConfig.
 *
 * Adding a new setting:
 *   1. Add a public static final field here.
 *   2. Add the key/value to config/default.properties.
 *   3. Override in staging.properties / CI env vars as needed.
 */
public final class BaseConfig {

    // ── API ──────────────────────────────────────────────────────────────────
    /** Base URL for the API under test. Override via BASE_URL env var. */
    public static final String BASE_URL =
            EnvConfig.get("BASE_URL", "https://petstore.swagger.io/v2");

    /** Default HTTP request timeout in milliseconds. */
    public static final int REQUEST_TIMEOUT_MS =
            EnvConfig.getInt("REQUEST_TIMEOUT_MS", 10_000);

    /** Maximum number of automatic retries for 5xx responses. */
    public static final int MAX_RETRIES =
            EnvConfig.getInt("MAX_RETRIES", 2);

    /** SLA: API responses must arrive within this many milliseconds. */
    public static final long RESPONSE_TIME_SLA_MS =
            EnvConfig.getInt("RESPONSE_TIME_SLA_MS", 3000);

    // ── Auth ─────────────────────────────────────────────────────────────────
    /** API key header value. Set API_KEY env var in CI; leave blank for public APIs. */
    public static final String API_KEY =
            EnvConfig.get("API_KEY", "");

    /** Bearer token for OAuth-protected APIs. */
    public static final String BEARER_TOKEN =
            EnvConfig.get("BEARER_TOKEN", "");

    // ── Logging / Reporting ──────────────────────────────────────────────────
    /** When true, full request/response bodies are logged on every call. */
    public static final boolean LOG_ALL_REQUESTS =
            EnvConfig.getBoolean("LOG_ALL_REQUESTS", false);

    /** When true, request/response bodies are always attached to Allure report. */
    public static final boolean ALLURE_ATTACH_ALWAYS =
            EnvConfig.getBoolean("ALLURE_ATTACH_ALWAYS", true);

    // ── AI Test Generator ────────────────────────────────────────────────────
    /** LLM API endpoint (e.g. OpenAI, Anthropic, local Ollama). */
    public static final String LLM_API_URL =
            EnvConfig.get("LLM_API_URL", "https://api.anthropic.com/v1/messages");

    /** LLM API key. Always set via env var — never hard-code. */
    public static final String LLM_API_KEY =
            EnvConfig.get("LLM_API_KEY", "");

    /** LLM model identifier. */
    public static final String LLM_MODEL =
            EnvConfig.get("LLM_MODEL", "claude-sonnet-4-20250514");

    /** Directory where AI-generated test files are written. */
    public static final String AI_OUTPUT_DIR =
            EnvConfig.get("AI_OUTPUT_DIR", "api/src/test/java/com/aitesting/api/ai-generated");

    /** Prevent instantiation. */
    private BaseConfig() {}
}
