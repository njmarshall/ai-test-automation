# AI Test Automation Framework

> **AI-powered API test automation built to FAANG-grade standards.**
> Designed for scalability, maintainability, and real-world industry coverage.

[![CI](https://github.com/njmarshall/ai-test-automation/actions/workflows/ci.yml/badge.svg)](https://github.com/njmarshall/ai-test-automation/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Maven](https://img.shields.io/badge/Maven-3.9-blue.svg)](https://maven.apache.org/)
[![TestNG](https://img.shields.io/badge/TestNG-7.9-green.svg)](https://testng.org/)
[![RestAssured](https://img.shields.io/badge/RestAssured-5.4-yellow.svg)](https://rest-assured.io/)
[![Allure](https://img.shields.io/badge/Allure-2.27-red.svg)](https://allurereport.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

---

## What This Framework Does

This is not just a test framework — it is an **AI-powered test engineering platform**
that automatically scans API specifications, generates comprehensive Java test suites
using large language models, and executes them with full observability and CI/CD integration.

**Four core capabilities:**

- **AI Test Generation** — reads OpenAPI specs and writes Java test classes automatically
  using Claude/GPT, covering happy paths, boundary values, and negative cases
- **Shared Library Architecture** — one reusable framework serves all API capstone projects
  with zero duplication across industries
- **Self-Healing Tests** — detects API changes and regenerates affected tests automatically
- **Full Observability** — Allure HTML reports with request/response attachments published
  to GitHub Pages on every push to main

---

## Project Highlights

| Metric | Value |
|---|---|
| **Tests passing** | 82 / 82 ✅ |
| **Test execution time** | ~15 seconds |
| **Parallel threads** | 3 (classes level) |
| **Industries covered** | PetStore · Insurance · Healthcare FHIR |
| **CI/CD** | GitHub Actions — build, test, report on every push |
| **Reporting** | Allure HTML published to GitHub Pages |
| **AI generation** | Claude API — generates tests from OpenAPI spec |

---

## Framework Architecture

```
ai-test-automation/
│
├── shared/                          ← Reusable framework library
│   └── src/main/java/com/aitesting/shared/
│       ├── config/
│       │   ├── EnvConfig.java       ← Environment variable + .properties loader
│       │   └── BaseConfig.java      ← Typed constants (URLs, timeouts, keys)
│       ├── http/
│       │   ├── ApiClient.java       ← Central RestAssured wrapper (Facade pattern)
│       │   └── AuthHelper.java      ← Bearer / API key / Basic / OAuth auth
│       ├── assertions/
│       │   └── ResponseValidator.java ← Fluent chainable assertions
│       ├── dataprovider/
│       │   └── TestDataFactory.java   ← Java Faker random realistic data
│       ├── reporting/
│       │   └── AllureHelper.java      ← Allure attachment + step helpers
│       └── ai/
│           ├── TestGenerator.java     ← LLM-powered test writer
│           └── TestGeneratorRunner.java ← CLI entry point
│
├── api/                             ← Industry capstone test suites
│   └── src/
│       ├── main/java/com/aitesting/api/
│       │   └── models/              ← Domain POJOs (Pet, Order, InsuranceModels, FhirModels)
│       └── test/java/com/aitesting/api/
│           ├── petstore/            ← PetTests · StoreTests · UserTests
│           │                           PetTestDataFactory · PetResponseValidator
│           ├── insurance/           ← QuoteTests · PolicyTests · ClaimsTests
│           │                           InsuranceTestDataFactory · InsuranceResponseValidator
│           ├── healthcare/          ← PatientTests · ClaimTests · EncounterTests · PriorAuthTests
│           │                           FhirTestDataFactory · HealthResponseValidator
│           └── util/
│               └── WireMockHelper.java ← WireMock stub helpers
│
├── .github/workflows/
│   └── ci.yml                       ← GitHub Actions CI/CD pipeline
└── pom.xml                          ← Maven multi-module root
```

---

## Design Patterns

| Pattern | Where Used | Why |
|---|---|---|
| **Facade** | `ApiClient.java` | Hides RestAssured complexity behind 4 simple methods |
| **Fluent Interface** | `ResponseValidator.java` | Chainable assertions read like English |
| **Factory** | `TestDataFactory.java` | Centralised test data creation |
| **Singleton** | `EnvConfig`, `BaseConfig` | Config loaded once, shared everywhere |
| **Template Method** | `@BeforeClass` / `@AfterClass` | Consistent setup/teardown contract |
| **Strategy** | `TestGenerator.java` | Swappable LLM backend via config |
| **API Object Model** | `shared/` vs `api/` | Separates HTTP layer from test logic |

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Build | Maven (multi-module) | 3.9 |
| HTTP / API testing | RestAssured | 5.4 |
| Test framework | TestNG | 7.9 |
| Mocking | Mockito | 5.11 |
| Reporting | Allure | 2.27 |
| Test data | Java Faker | 1.0.2 |
| AI generation | Anthropic Claude API | claude-sonnet-4 |
| HTTP client (AI) | OkHttp | 4.12 |
| JSON | Jackson | 2.17 |
| Logging | SLF4J + Logback | 2.0 |
| CI/CD | GitHub Actions | — |

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Allure CLI (`brew install allure`)

### Run PetStore Tests
```bash
# Clone
git clone https://github.com/njmarshall/ai-test-automation.git
cd ai-test-automation

# Build shared library
mvn install -pl shared -am -DskipTests

# Run all tests
mvn test -pl api -am

# View Allure report
allure serve api/target/allure-results
```

### Run AI Test Generator
```bash
export LLM_API_KEY=your_anthropic_key

# Auto-generate tests from live PetStore spec
mvn exec:java -pl shared \
  -Dexec.mainClass="com.aitesting.shared.ai.TestGeneratorRunner"

# Generate from any OpenAPI spec URL
mvn exec:java -pl shared \
  -Dexec.mainClass="com.aitesting.shared.ai.TestGeneratorRunner" \
  -Dexec.args="https://your-api.com/openapi.json projectname"
```

### Run Against Different Environment
```bash
BASE_URL=https://staging.yourapi.com mvn test -pl api -am
```

---

## Industry Capstone Projects

| # | Project | Status | Industry | Key Test Scenarios |
|---|---|---|---|---|
| 1 | **PetStore API** | ✅ Complete | Reference / Demo | Full CRUD, boundary values, parallel execution |
| 2 | **Insurance Quote** | ✅ Complete | Insurance | Quote generation, risk profiles, policy binding |
| 3 | **Healthcare FHIR** | ✅ Complete | Medical / Health | Patient records, claims, prior authorization |
| 4 | **Payment API** | 🔜 Planned | Fintech | Charges, refunds, disputes, async callbacks |

> Each capstone reuses the shared library with zero framework code — only domain models and test logic.

---

## Adding a New Industry Project

Adding a new capstone takes **less than a day** following the playbook:

```
1. Add domain models    → api/src/main/java/.../models/
2. Add test data factory → api/src/test/java/.../projectname/
3. Add test classes     → api/src/test/java/.../projectname/
4. Register in testng.xml
5. (Optional) Run AI generator to auto-write tests
```

See the [Wiki](../../wiki) for industry-specific guides.

---

## Configuration

All settings controlled by environment variables — no hard-coded values:

| Variable | Default | Description |
|---|---|---|
| `BASE_URL` | `https://petstore.swagger.io/v2` | API under test |
| `REQUEST_TIMEOUT_MS` | `5000` | HTTP timeout |
| `RESPONSE_TIME_SLA_MS` | `5000` | Max response time SLA |
| `MAX_RETRIES` | `2` | Retries on 5xx |
| `API_KEY` | _(blank)_ | API key header |
| `BEARER_TOKEN` | _(blank)_ | OAuth bearer token |
| `LLM_API_KEY` | _(blank)_ | AI generator key |
| `LLM_MODEL` | `claude-sonnet-4-20250514` | LLM model |
| `ENV_NAME` | `default` | Environment profile |

---

## CI/CD Pipeline

Every push to `main` or `develop` automatically:

```
1. Builds shared library
2. Compiles all test modules
3. Runs full test suite in parallel (3 threads)
4. Generates Allure HTML report
5. Publishes report to GitHub Pages
6. Uploads artifacts for 30 days
```

**Live Allure Report:**
`https://njmarshall.github.io/ai-test-automation/allure-report`

---

## Roadmap

### Near Term
- [x] Insurance Quote + Policy capstone (complete)
- [x] Healthcare FHIR capstone (complete)
- [x] `AsyncApiClient` — polling + webhook patterns
- [x] WireMock integration for third-party API mocking

### Medium Term
- [ ] Payment API capstone
- [ ] Schema registry validation
- [ ] Contract testing with Pact
- [ ] Performance testing with `PerformanceHelper`

### Long Term
- [ ] Self-healing tests via spec diff detection
- [ ] AI-powered root cause analysis on failures
- [ ] Natural language test authoring

---

## About the Author

**NJ Marshall** — Senior SET / AI Test Automation Engineer

15 years of test engineering expertise across industry leaders:

| Company | Role | Highlights |
|---|---|---|
| **Microsoft** | SDET | Enterprise-scale test automation |
| **Salesforce** | Senior SET | API automation at cloud scale |
| **Indeed** | Senior SET | High-volume job platform testing |
| **Ooyala** | SET | Video platform, ex-Googler founding team |
| **TripIt** | SET | Travel API automation |
| **Finix** | SET | Payment processing, async API patterns |

**Certifications:** AWS Professional · Algorithms & System Design (Scaler)

**Philosophy:**
> *"AI doesn't replace the engineer — it amplifies them.
> The best test framework is one that lets engineers focus
> on domain knowledge while AI handles the boilerplate."*

---

[![GitHub](https://img.shields.io/badge/GitHub-njmarshall-black.svg)](https://github.com/njmarshall)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-blue.svg)](https://linkedin.com/in/njmarshall)
