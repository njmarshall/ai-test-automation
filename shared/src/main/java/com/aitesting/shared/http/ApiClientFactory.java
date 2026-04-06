package com.aitesting.shared.http;

import com.aitesting.shared.config.BaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ApiClientFactory — creates ApiClient instances for different API servers.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: Interface Segregation Principle (SOLID — I)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Problem solved:
 *   When a test run involves multiple API servers (primary API, WireMock
 *   mock server, credit bureau, fraud detection, payment gateway),
 *   a single static ApiClient cannot serve all of them. Each server
 *   needs its own configured client.
 *
 *   Before (violation):
 *     ApiClient was static — ONE URL for ALL tests.
 *     Insurance tests created their own wireMockSpec — duplication.
 *     Three Insurance test classes each duplicated the same 6-line spec.
 *
 *   After (ISP compliant):
 *     Each test class requests exactly the client it needs.
 *     No test is forced to use a client configured for another server.
 *     Shared infrastructure — no duplication.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // PetStore tests — primary API:
 *   private ApiClient api = ApiClientFactory.forPrimaryApi();
 *
 *   // Insurance tests — WireMock mock server:
 *   private ApiClient api = ApiClientFactory.forWireMock();
 *
 *   // Staging environment:
 *   private ApiClient api = ApiClientFactory.forUrl(
 *       "https://staging.insurance.com/api");
 *
 *   // Future — credit bureau, fraud detection, payment:
 *   private ApiClient creditApi  = ApiClientFactory.forCreditBureau();
 *   private ApiClient fraudApi   = ApiClientFactory.forFraudDetection();
 *   private ApiClient paymentApi = ApiClientFactory.forPaymentGateway();
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ENTERPRISE SCENARIO (multi-server test)
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   public class InsuranceIntegrationTests {
 *
 *       // Each server gets its own correctly configured client
 *       private ApiClient claimsApi   = ApiClientFactory.forPrimaryApi();
 *       private ApiClient creditApi   = ApiClientFactory.forCreditBureau();
 *       private ApiClient fraudApi    = ApiClientFactory.forFraudDetection();
 *
 *       @Test
 *       public void submitClaim_triggersAllChecks() {
 *           // Each call goes to the right server automatically
 *           Response claim   = claimsApi.post("/claims", payload);
 *           Response credit  = creditApi.get("/check/" + applicantId);
 *           Response fraud   = fraudApi.post("/screen", screenPayload);
 *       }
 *   }
 */
public final class ApiClientFactory {

    private static final Logger log =
        LoggerFactory.getLogger(ApiClientFactory.class);

    // ── Named factory methods ─────────────────────────────────────────────────

    /**
     * Creates a client pointing at the primary API under test.
     * URL comes from BaseConfig.BASE_URL (env var or default.properties).
     *
     * Use for: PetStore tests, any test against the main configured API.
     */
    public static ApiClient forPrimaryApi() {
        log.debug("Creating ApiClient for primary API: {}",
            BaseConfig.BASE_URL);
        return new ApiClient(BaseConfig.BASE_URL);
    }

    /**
     * Creates a client pointing at the local WireMock mock server.
     * Always uses http://localhost:8089.
     *
     * Use for: Insurance, Healthcare, Payment tests that need
     * a mock server because no public API exists.
     *
     * Call WireMockHelper.start() in @BeforeClass before using this.
     */
    public static ApiClient forWireMock() {
        String url = "http://localhost:8089";
        log.debug("Creating ApiClient for WireMock: {}", url);
        return new ApiClient(url);
    }

    /**
     * Creates a client pointing at an explicit URL.
     * Use for staging environments, specific microservices,
     * or any server not covered by the named factory methods.
     *
     * @param baseUrl full base URL including scheme and port
     *                e.g. "https://staging.insurance.com/api/v1"
     */
    public static ApiClient forUrl(String baseUrl) {
        log.debug("Creating ApiClient for URL: {}", baseUrl);
        return new ApiClient(baseUrl);
    }

    // ── Future named methods (add as new capstones are built) ─────────────────

    /**
     * Creates a client for the Healthcare FHIR API.
     * URL configured via FHIR_BASE_URL environment variable.
     * Planned for Healthcare capstone.
     */
    public static ApiClient forFhirApi() {
        String url = System.getenv().getOrDefault(
            "FHIR_BASE_URL", "https://hapi.fhir.org/baseR4");
        log.debug("Creating ApiClient for FHIR API: {}", url);
        return new ApiClient(url);
    }

    /**
     * Creates a client for a credit bureau third-party API.
     * URL configured via CREDIT_API_URL environment variable.
     * Planned for async credit check tests.
     */
    public static ApiClient forCreditBureau() {
        String url = System.getenv().getOrDefault(
                "CREDIT_API_URL", "http://localhost:8089");
        log.debug("Creating ApiClient for Credit Bureau: {}", url);
        return new ApiClient(url);
    }

    /**
     * Creates a client for a payment gateway API.
     * URL configured via PAYMENT_API_URL environment variable.
     * Planned for Payment capstone.
     */
    public static ApiClient forPaymentGateway() {
        String url = System.getenv().getOrDefault(
                "PAYMENT_API_URL", "http://localhost:8089");
        log.debug("Creating ApiClient for Payment Gateway: {}", url);
        return new ApiClient(url);
    }

    /** Prevent instantiation — this is a static factory class. */
    private ApiClientFactory() {}
}
