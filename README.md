# ai-test-automation

> AI-powered API test automation framework in Java — built to FAANG-grade standards.
> Three industry capstones · 103 tests (88 CI gate + 15 live FHIR) · 0 failures · CRTP · SOLID · AsyncApiClient

[![CI](https://github.com/njmarshall/ai-test-automation/actions/workflows/ci.yml/badge.svg)](https://github.com/njmarshall/ai-test-automation/actions/workflows/ci.yml)

---

## Overview

Production-grade, multi-industry API test automation framework demonstrating
architect-level design patterns used at Google and Meta.

**Key capabilities:**

- AI-powered test generation from OpenAPI specs via `TestGenerator`
- **CRTP** (Curiously Recurring Template Pattern) for type-safe factory and validator hierarchies
- **ApiClientFactory** implementing SOLID Interface Segregation — supports multiple API servers per test run
- **AsyncApiClient** with `pollUntil()`, `pollUntilTerminal()`, and `pollWithBackoff()` — models real healthcare prior authorization and insurance claims adjudication workflows
- **WireMock** mock server for deterministic industry API testing (Insurance, Healthcare)
- Healthcare **FHIR R4** integration against real `hapi.fhir.org` server — HIPAA-safe synthetic data
- Parallel execution via TestNG · Allure HTML reports · GitHub Actions CI/CD

---

## Test Results

| Capstone | Tests | Status | Server |
|---|---|---|---|
| **PetStore** | 29 | ✅ Passing | Live Swagger API |
| **Insurance** | 28 | ✅ Passing | WireMock |
| **Healthcare FHIR R4** | 30 | ✅ Passing | HAPI FHIR + WireMock |
| **PetStore UI (Playwright)** | 8 | ✅ Passing | Playwright + Chromium |
| **PetStore UI (Selenium)** | 8 | ✅ Passing | Selenium WebDriver + Chrome |
| **Total** | **103** | **✅ 0 failures** | | |

| Suite | Suite file | Tests | Blocks the build? |
|---|---|---|---|
| **Default CI gate** | `testng.xml` (excludes group `live`) | 88 | Yes |
| **Live public FHIR** | `testng-live.xml` (only group `live`: PatientTests, EncounterTests) | 15 | No (`continue-on-error`) |

**Why live tests are separate:** PatientTests and EncounterTests call the public
`hapi.fhir.org` server, whose latency we do not control (one CI run saw 12.5s
against an 8s SLA). Raising the SLA each time hides real regressions, so these
tests are tagged `@Test(groups = "live")` and run in their own non-blocking CI job.
The gate stays deterministic. Plan: WireMock for FHIR Patient create (Option C).

---

## Project Structure

```
ai-test-automation/
├── shared/                              # Reusable framework library
│   └── src/main/java/com/aitesting/shared/
│       ├── config/
│       │   ├── EnvConfig.java           # Env var + .properties loader
│       │   └── BaseConfig.java          # Typed constants (URLs, timeouts, keys)
│       ├── http/
│       │   ├── ApiClient.java           # RestAssured wrapper — instantiable
│       │   ├── ApiClientFactory.java    # SOLID ISP — forPrimaryApi(), forWireMock(), forFhirApi()
│       │   ├── AsyncApiClient.java      # Async polling — pollUntil, pollUntilTerminal, pollWithBackoff
│       │   └── AuthHelper.java          # Bearer / API key / Basic auth
│       ├── assertions/
│       │   └── ResponseValidator.java   # CRTP base — fluent chainable assertions
│       ├── dataprovider/
│       │   └── TestDataFactory.java     # CRTP base — Java Faker random data
│       ├── reporting/
│       │   └── AllureHelper.java        # Allure attachment helpers
│       └── ai/
│           └── TestGenerator.java       # LLM-powered test writer
│
├── api/                                 # Industry capstone test suites
│   └── src/
│       ├── main/java/com/aitesting/
│       │   ├── petstore/model/          # PetStore domain models
│       │   │   ├── Pet.java
│       │   │   └── Order.java
│       │   ├── insurance/model/         # Insurance domain models
│       │   │   └── InsuranceModels.java # QuoteRequest, PolicyRequest, Applicant, Vehicle
│       │   └── healthcare/model/        # Healthcare domain models
│       │       └── FhirModels.java      # Patient, Encounter, Claim, PriorAuth (FHIR R4)
│       │
│       └── test/java/com/aitesting/
│           ├── petstore/api/            # PetStore capstone (29 API + 16 UI tests)
│           │   ├── PetTests.java        # CRUD: POST/GET/PUT/DELETE /pet
│           │   ├── StoreTests.java      # Orders: POST/GET/DELETE /store/order
│           │   ├── UserTests.java       # Users: POST/GET/PUT/DELETE /user
│           │   ├── PetTestDataFactory.java    # CRTP subclass
│           │   └── PetResponseValidator.java  # CRTP subclass
│           │
│           ├── petstore/pom/           # Page Objects (Playwright)
│           │   ├── SwaggerHomePage.java
│           │   └── PetEndpointPage.java
│           │
│           ├── petstore/selenium/pages/ # Page Objects (Selenium, same locators)
│           │   ├── SwaggerHomePageSelenium.java
│           │   └── PetEndpointPageSelenium.java
│           │
│           ├── petstore/ui/            # UI tests (8 Playwright + 8 Selenium)
│           │   ├── PetStoreUITests.java
│           │   └── PetStoreSeleniumTests.java
│           │
│           ├── ui/                     # Shared Playwright utilities
│           │   ├── PlaywrightFactory.java
│           │   ├── BasePage.java
│           │   ├── UIConfig.java       # Shared by both frameworks
│           │   ├── ScreenshotHelper.java
│           │   └── selenium/           # Shared Selenium utilities
│           │       ├── DriverFactory.java          # ThreadLocal ChromeDriver
│           │       ├── SeleniumBasePage.java       # Explicit waits only
│           │       └── SeleniumScreenshotHelper.java
│           │
│           ├── insurance/api/           # Insurance capstone (28 tests)
│           │   ├── QuoteTests.java      # POST/GET /quotes — standard, teen, high-risk
│           │   ├── PolicyTests.java     # POST/GET/PATCH /policies — bind, cancel
│           │   ├── ClaimsTests.java     # POST/GET/PATCH /claims — FNOL, async polling
│           │   ├── InsuranceTestDataFactory.java  # CRTP subclass
│           │   └── InsuranceResponseValidator.java # CRTP subclass
│           │
│           ├── healthcare/api/          # Healthcare FHIR R4 capstone (30 tests)
│           │   ├── PatientTests.java    # CRUD /Patient — real HAPI FHIR server
│           │   ├── EncounterTests.java  # CRUD /Encounter — real HAPI FHIR server
│           │   ├── ClaimTests.java      # POST/GET /Claim — WireMock
│           │   ├── PriorAuthTests.java  # Async polling — WireMock scenarios
│           │   ├── FhirTestDataFactory.java   # CRTP subclass — HIPAA-safe synthetic data
│           │   └── HealthResponseValidator.java # CRTP subclass
│           │
│           └── util/api/
│               └── WireMockHelper.java  # Mock server lifecycle + stubbing
│
├── .github/workflows/
│   └── ci.yml                           # GitHub Actions CI/CD pipeline
└── pom.xml                              # Maven multi-module root
```

---

## Quick Start

### Prerequisites

- Java 17+
- Chromium (auto-installed by Playwright on first run)
- Google Chrome for Selenium (chromedriver is resolved by Selenium Manager)
- Maven 3.9+

### Run all tests

```bash
# Clone the repo
git clone https://github.com/njmarshall/ai-test-automation.git
cd ai-test-automation

# Run the default CI gate (88 tests, live FHIR excluded)
mvn clean test -pl api -am

# Run the live public FHIR suite (15 tests, hapi.fhir.org)
mvn clean test -pl api -am -Dtestng.suite=src/test/resources/testng-live.xml

# Generate Allure HTML report
mvn allure:report -pl api
open api/target/site/allure-maven-plugin/index.html
```

### Run a single capstone

```bash
# PetStore only
mvn test -pl api -am -Dtest="com.aitesting.petstore.api.*"

# Insurance only
mvn test -pl api -am -Dtest="com.aitesting.insurance.api.*"

# Healthcare only
mvn test -pl api -am -Dtest="com.aitesting.healthcare.api.*"

# UI only, Playwright and Selenium side by side
# (Selenium is headless when CI=true or HEADLESS=true)
HEADLESS=true mvn test -pl api -am -Dtest="com.aitesting.petstore.ui.*"
```

### Run against a different environment

```bash
BASE_URL=https://staging.myapi.com mvn test -pl api -am
```

---

## Configuration

All settings are controlled by environment variables (highest priority) or
`config/default.properties` (fallback). Never hard-code secrets.

| Variable | Default | Description |
|---|---|---|
| `BASE_URL` | `https://petstore.swagger.io/v2` | Primary API base URL |
| `REQUEST_TIMEOUT_MS` | `10000` | HTTP timeout in ms |
| `RESPONSE_TIME_SLA_MS` | `3000` | Max acceptable response time |
| `API_KEY` | *(blank)* | API key header value |
| `BEARER_TOKEN` | *(blank)* | OAuth bearer token |
| `LOG_ALL_REQUESTS` | `false` | Log full req/resp to console |
| `LLM_API_KEY` | *(blank)* | Key for AI test generator |
| `LLM_MODEL` | `claude-sonnet-4-20250514` | LLM model |
| `FHIR_BASE_URL` | `https://hapi.fhir.org/baseR4` | FHIR R4 server URL |

---

## Architecture — Design Patterns

### CRTP — Curiously Recurring Template Pattern

Both `TestDataFactory` and `ResponseValidator` use CRTP for type-safe
inheritance — the same pattern used at Google and Meta. Every method returns
the concrete subclass type, enabling full fluent chaining across the hierarchy.

Adding a method to the base class instantly propagates to ALL capstone
subclasses with zero code changes — eliminating the N-file delegation
maintenance burden.

```java
// Base — CRTP
public abstract class TestDataFactory<T extends TestDataFactory<T>> {
    @SuppressWarnings("unchecked")
    protected final T self() { return (T) this; }

    public T withNonExistentId() {
        this.id = 999_999_999L;
        return self();   // returns PetTestDataFactory, InsuranceTestDataFactory, etc.
    }
}

// Subclass — type-safe chain preserved
public final class PetTestDataFactory
        extends TestDataFactory<PetTestDataFactory> {

    public PetTestDataFactory withName(String name) {
        this.name = name;
        return this;
    }
}

// Usage — one import, full chain, compile-time safe
Map<String, Object> pet = PetTestDataFactory.create()
    .withNonExistentId()    // inherited from base ← returns PetTestDataFactory
    .withName("Buddy")      // PetStore native
    .withStatus("available")
    .build();
```

---

### ApiClientFactory — SOLID Interface Segregation

Each test class gets exactly the client it needs. No test is forced to
use a client configured for another server.

```java
// PetStore — primary API
ApiClient api = ApiClientFactory.forPrimaryApi();

// Insurance / Healthcare claims — WireMock mock server
ApiClient api = ApiClientFactory.forWireMock();

// Healthcare Patient / Encounter — real FHIR server
ApiClient api = ApiClientFactory.forFhirApi();

// Any custom URL (staging, etc.)
ApiClient api = ApiClientFactory.forUrl("https://staging.myapi.com/v1");

// Usage — identical regardless of server
Response r = api.get("/Patient/TEST-12345678");
Response r = api.post("/claims", claimPayload);
```

---

### AsyncApiClient — Async Polling Patterns

Models real-world async workflows in healthcare and insurance:

```java
// Poll until prior auth decision arrives
Response decision = AsyncApiClient.pollUntilTerminal(
    api,
    "/Claim/" + priorAuthId,
    "status",
    List.of("approved", "denied", "pended"),
    300,   // 5 minute timeout
    10     // poll every 10 seconds
);

// Exponential backoff for long-running underwriting
Response result = AsyncApiClient.pollWithBackoff(
    api, path, "status", "complete",
    300,  // max 5 minutes
    2,    // start at 2 seconds
    30    // cap at 30 seconds
);
// polls at: 2s → 4s → 8s → 16s → 30s → 30s...
```

---

## AI Test Generation

The `TestGenerator` class reads an OpenAPI spec and writes Java test classes automatically.

```bash
# Set your LLM API key
export LLM_API_KEY=your_key_here

# Run the generator against the PetStore spec
mvn exec:java \
  -pl shared \
  -Dexec.mainClass="com.aitesting.shared.ai.TestGeneratorRunner" \
  -Dexec.args="api/src/test/resources/petstore-openapi.json petstore"
```

Generated files land in `api/src/test/java/com/aitesting/api/aigenerated/<project>/` (package `com.aitesting.api.aigenerated.<project>`).

---

## CI/CD

Every push to `main` or `develop`:

1. Builds the `shared` library
2. Runs the 88-test gate in parallel (3 threads); live FHIR tests are excluded
3. Generates an Allure HTML report
4. Publishes the report to GitHub Pages
5. Uploads artifacts for 30 days
6. Runs the 15 live FHIR tests in a separate `live-fhir-tests` job with `continue-on-error: true`, so a slow public server never fails the build

Allure report: `https://njmarshall.github.io/ai-test-automation/allure-report/`

---

## Wiki

Nine pages of architecture documentation:

| Page | Description |
|---|---|
| [Framework Architecture](../../wiki/Framework-Architecture) | Design patterns, CRTP mechanics, debug walkthrough |
| [ApiClientFactory](../../wiki/ApiClientFactory) | SOLID ISP — multi-server support, WireMock integration |
| [AsyncApiClient](../../wiki/AsyncApiClient) | Polling strategies, timeout guidance, industry use cases |
| [Alternative Design Patterns](../../wiki/Alternative-Design-Patterns) | CRTP vs Delegation — full comparison |
| [Healthcare Industry Guide](../../wiki/Healthcare-Industry) | FHIR R4, HIPAA, async prior auth, 30 tests |
| [Insurance Industry Guide](../../wiki/Insurance-Industry) | Quote, policy, claims, async adjudication, 28 tests |
| [How to Add a New Industry](../../wiki/How-To-Add-New-Industry) | Step-by-step playbook for any new capstone |
| [IntelliJ Setup](../../wiki/IntelliJ-Setup-and-Debugging) | Open project, run tests, debug walkthrough |

---

## Author

**NJ Marshall** — Senior Software Engineer in Test | AI-Powered Test Automation

15+ years: Microsoft · Salesforce · TripIt (Concur) · Dalet (Ooyala) · Indeed · Finix

AWS Professional Certified · Healthcare FHIR R4 · Insurance Lifecycle · Async API Patterns

[github.com/njmarshall](https://github.com/njmarshall)
