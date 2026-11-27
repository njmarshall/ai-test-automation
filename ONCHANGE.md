# ONCHANGE

A running log of checks and changes.

## 2026.10.22

### Checked: JAVA_HOME in ~/.zshrc

* Time: about 11:16 PM Pacific
* Item: `export JAVA_HOME=$(/usr/libexec/java_home -v 17.0.5)`
* Result: Already present in `~/.zshrc`. No change needed.
* Checked by: Neil, on his own machine.
* Why it matters: Without it, Maven can pick up JDK 25 and Lombok fails to compile. JDK 17 is the version this project uses.
* Action taken: None.

## 2026.10.30

### Fixed: AsyncApiClientTest failing in CI

* Failure: All 28 tests in `AsyncApiClientTest` failed with "It is not possible to mock static methods of java.lang.Thread".
* Cause: Mockito does not allow `mockStatic(Thread.class)`, so the `withSuppressedSideEffects` helper and the backoff tests could never run.
* Fix: Added a package-private `Sleeper` seam in `AsyncApiClient` (`static volatile Sleeper sleeper = Thread::sleep`). Tests swap in a recorder in `@BeforeEach`, restore it in `@AfterEach`, and the backoff tests assert on the recorded `sleeps` list. Polling behavior is unchanged.
* Also fixed: Four timeout tests (maxWaitSeconds=0) stubbed `api.get` but never poll, which Mockito strict stubs reported as UnnecessaryStubbing. Removed the unused stubs.
* Result: 28/28 pass in milliseconds. Full `mvn clean test -pl api -am` passes (95 run, 1 known skip).

## 2026.11.01

### Fixed: PatientTests intermittent SLA failure in CI

* Failure: `PatientTests.createPatient_validAdult_returns201` failed intermittently with "Response time 3199ms exceeded SLA 3000ms". The retry added in 3852946 (`rerunFailingTestsCount=1`) did not fix it.
* Cause: The test calls the live public HAPI FHIR server, but it shared the strict 3000ms `RESPONSE_TIME_SLA_MS` used for WireMock-backed tests. We do not control live server latency, so a hard 3000ms limit fails whenever HAPI is a little slow.
* Fix: Added `BaseConfig.LIVE_API_SLA_MS` (default 8000, env var `LIVE_API_SLA_MS`, also set in `ci.yml`) and a `ResponseValidator.withinSla(long maxMillis)` overload. `withinSla()` now delegates to it with `RESPONSE_TIME_SLA_MS`. Live-server test classes (PatientTests, EncounterTests via `forFhirApi()`; PetTests, StoreTests, UserTests via `ApiClient.DEFAULT`, the same live PetStore URL as `forPrimaryApi()`) now use `withinSla(BaseConfig.LIVE_API_SLA_MS)`. WireMock tests keep the strict 3000ms. `rerunFailingTestsCount=1` stays in `api/pom.xml`.
* Test: Added `ResponseValidatorTest` (mocked `Response.getTime()`): under the limit passes, over the limit throws an AssertionError naming the limit.
* Result: `mvn clean test -pl api -am` on JDK 17 passes. Shared 29 run, 0 failures. API 95 run, 0 failures, 1 known skip.
* Next: replace live Patient create with WireMock (Option C) and run live tests in a non-blocking job.

## 2026.11.13

### Added: Selenium (Java) UI tests mirroring the Playwright PetStore UI tests

* Goal: Show the same 8 PetStore Swagger UI scenarios in both Playwright and Selenium. Option A layout: test scope, inside the `api` module.
* Dependency: `org.seleniumhq.selenium:selenium-java:4.52.0` (test scope) in `api/pom.xml`. No WebDriverManager. Selenium Manager resolves chromedriver.
* Utilities (`api/src/test/java/com/aitesting/ui/selenium/`): `DriverFactory` (ThreadLocal ChromeDriver, headless when CI=true or HEADLESS=true, timeouts and viewport from `UIConfig`), `SeleniumBasePage` (WebDriverWait plus ExpectedConditions, no Thread.sleep, no implicit wait), `SeleniumScreenshotHelper` (failure screenshot saved and attached to Allure).
* Page objects (`petstore/selenium/pages/`): `SwaggerHomePageSelenium`, `PetEndpointPageSelenium`. Locators copied unchanged from the Playwright page objects.
* Tests: `PetStoreSeleniumTests` (same scenarios and assertions as `PetStoreUITests`), registered in `testng.xml`.
* CI: `CI: "true"` set explicitly on the api test step so Selenium runs headless. No new secrets.
* Locator changes: None. All selectors passed against the live Swagger DOM on the first run.
* Result: `mvn clean test -pl api -am` on JDK 17 passes. Shared 29 run, 0 failures. API 103 run, 0 failures, 1 known skip. Headless run with CI=true: Selenium 8/8 (22.4s), Playwright 8/8 (20.8s).
* Docs: README (test count, Selenium UI row, structure diagram), `docs/playbooks/selenium_vs_playwright.md`.

### Fixed: Live FHIR tests no longer block CI

* Failure: `PatientTests.createPatient_validAdult_returns201` failed in CI again. The live public HAPI FHIR server took 12569ms (about 12.5s) against the 8000ms (8s) `LIVE_API_SLA_MS`.
* Why raising the limit is the wrong fix: We already raised it once (3s to 8s on 2026.11.01). HAPI is a shared public sandbox and its latency is outside our control, so any limit we pick will eventually be breached. Raising it each time makes the SLA meaningless and would hide real regressions, while the gate still fails on noise we cannot fix.
* Fix: Tagged `PatientTests` and `EncounterTests` (the only `forFhirApi()` users) with class-level `@Test(groups = "live")`. TestNG merges this into every `@Test` method; verified all 15 methods ran in the live suite. WireMock tests (ClaimTests, PriorAuthTests) and PetStore tests are untagged. No other test depends on data they create.
* Suites: `testng.xml` now has `<groups><run><exclude name="live"/></run></groups>` (Surefire ignores `groups`/`excludedGroups` when `suiteXmlFiles` is set, so the exclusion lives in the suite file). New `testng-live.xml` runs only group `live`. `api/pom.xml` selects the suite via the `testng.suite` property (default `testng.xml`).
* CI: `build-and-test` stays the gate and skips live tests. New `live-fhir-tests` job runs `testng-live.xml` with `continue-on-error: true`, same JDK 17 setup, `LIVE_API_SLA_MS: "8000"`. No new secrets.
* Assertions and limits: unchanged.
* Result: `mvn clean test -pl api -am` on JDK 17: Shared 29 run, 0 failures. API gate 88 run, 0 failures, 0 skipped (was 103; the 15 live tests and the known Encounter skip moved out). Live suite: 15 run, 0 failures, 1 known skip (`getEncounter_found_returns200`), 11.7s. No HAPI calls in the gate, including the live classes' `@BeforeClass` setup.
* Next: WireMock for FHIR Patient create (Option C).

## 2026.11.17

### Chore: Dependabot, unused MAX_RETRIES removed, pattern comments fixed

* Added `.github/dependabot.yml`: weekly (Monday) updates for Maven (limit 5 open PRs, minor and patch grouped as `maven-minor-patch`) and GitHub Actions. Why: so dependencies stop going stale silently.
* Removed `MAX_RETRIES`: the field in `BaseConfig`, the line (and its comment) in `api/src/test/resources/config/default.properties`, and the README config row. Why: it was documented but never implemented. Grep confirmed no Java code read it.
* Corrected Javadoc only, no code changes. `ApiClientFactory` now says "Static Factory Methods" instead of "Interface Segregation Principle" / "ISP compliant". `AuthHelper` examples now use an `ApiClient` instance (`api.given()`) instead of the removed static `ApiClient.given()`. Why: so pattern names match the code.
* Result: `mvn -q clean verify -Dtestng.suite=src/test/resources/testng.xml` on JDK 17 passes. Shared 29 run, 0 failures. API 88 run, 0 failures, 0 skipped. (On JDK 25 the shared `AsyncApiClientTest` fails because Mockito cannot mock `ApiClient`. That is the known JDK issue from 2026.10.22, not caused by this change.)

### Build and CI: enforce JDK 17 to 21; AI job uploads tests for review

* Added `maven-enforcer-plugin` 3.7.0 to the root `pom.xml` `<build><plugins>` so it runs for every module. Execution `enforce-java` (goal `enforce`, default `validate` phase) with `requireJavaVersion` `[17,22)` and `requireMavenVersion` `[3.8,)`. Why: running on the wrong JDK (for example JDK 25) used to fail later with a confusing Mockito error. Now it fails right away with a clear message saying how to set JAVA_HOME.
* `ci.yml` job `generate-tests`: the generator now reads the spec from `https://petstore.swagger.io/v2/swagger.json`, because the local `petstore-openapi.json` does not exist in the repo. The "Commit generated tests" step (`git push`) is replaced with `actions/upload-artifact@v4` (`ai-generated-tests`, `if-no-files-found: warn`, 14-day retention). Permissions are now `contents: read`. Why: AI-generated tests should get a human review and never be pushed straight to main.
* Test code: unchanged.
* Result: on JDK 17.0.5, `mvn -q clean verify` passes. Shared 29 run, 0 failures. API 88 run, 0 failures, 0 skipped. On JDK 25.0.2, `mvn -q validate` fails right away (exit 1) with the enforcer message.

## 2026.11.18

### Build: Maven wrapper added

* Ran `mvn -N wrapper:wrapper` on JDK 17. Committed `mvnw` (executable, mode 100755 in git), `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties` (wrapper 3.3.4, Maven 3.9.14). Why: everyone, including CI later, builds with the same Maven version. Use `./mvnw` instead of `mvn`.
* CI: unchanged in this commit.

### Test: PetTests no longer depends on test order

* Problem: `getPetById_found`, `updatePet_happyPath` and `deletePet_happyPath` used `dependsOnMethods = "createPet_happyPath"` and a shared `createdPetId` field, so they could not run alone or in any other order. If the create test failed, the others were skipped.
* Fix: Added a private helper `createPet(name, status)` that POSTs `PetTestDataFactory.petPayload(name, status)`, asserts 200, records the id in `createdPetIds` and returns it. Each of the three tests now creates its own pet. `createPet_happyPath` keeps its own POST and checks, then records the id. `deletePet_happyPath` removes its id from the list after a successful delete. `@AfterMethod(alwaysRun = true)` deletes every recorded id (best effort, failures ignored) and clears the list. It replaces the old `@AfterClass` cleanup. All `priority` and `dependsOnMethods` attributes are gone from the class.
* Assertions: unchanged.
* This is the pattern for the other test classes that still rely on test order.
* Known gap, left for a later fix: `getPetById_notFound` accepts 404 or 200, so it can never fail. Not changed in this commit.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 29 run, 0 failures. API 88 run, 0 failures, 0 skipped (same as before). Each test passes alone, for example `./mvnw -q -pl api -am test -Dtest=PetTests#getPetById_found -Dsurefire.failIfNoSpecifiedTests=false` (also `updatePet_happyPath`, `deletePet_happyPath`).
* Note: `-am` is required. Without it, Maven uses an old `shared` jar from `~/.m2` and the test fails with `NoSuchFieldError: DEFAULT`.

## 2026.11.19

### Test: PetTests.getPetById_notFound now asserts 404 on a deleted pet

* Problem: The test used the fixed id from `TestDataFactory.nonExistentId()` (999999999) and accepted 404 or 200. Anyone can create that id on the public PetStore, so the test could never fail. This was the known gap noted on 2026.11.18.
* Fix: The test creates a pet with the `createPet(...)` helper, DELETEs it, asserts the delete returned 200, and removes the id from `createdPetIds`. It then GETs `/pet/{thatId}` and asserts 404 only, with `ResponseValidator`. The `@Description` now says it checks a pet that was just deleted.
* Unchanged: `deletePet_notFound` (it still accepts 404 or 200).

### Test: PatientTests no longer depends on test order

* Problem: `getPatient_found_returns200` and `updatePatient_changePhone_returns200` used `dependsOnMethods = "createPatient_validAdult_returns201"` and a shared static `createdPatientId`, so they could not run alone. If the create test failed, both were skipped.
* Fix: Same pattern as PetTests. Added a private helper `createAdultPatient()` that POSTs `FhirTestDataFactory.adultPatient()` with the same checks as the create test (201, `LIVE_API_SLA_MS`, resource type Patient, has id, complete patient structure), records the id in `createdPatientIds` and returns it. Both tests create their own patient first. `createPatient_validAdult_returns201` also records its id. `@AfterMethod(alwaysRun = true)` deletes every recorded id (best effort, failures ignored) and clears the list. It replaces the old `@AfterClass` cleanup. All `priority` and `dependsOnMethods` attributes are gone from the class. The class-level `@Test(groups = "live")` is unchanged.
* Assertions: unchanged.
* Not changed: `EncounterTests`.

### Result

* On JDK 17, `./mvnw -q clean verify` passes. Shared 29 run, 0 failures. API 88 run, 0 failures, 0 skipped.
* Each test passes alone, run with `./mvnw -q -pl api -am test -Dtest=<Class>#<method> -Dsurefire.failIfNoSpecifiedTests=false`: `PetTests#getPetById_notFound`, `PatientTests#getPatient_found_returns200`, `PatientTests#updatePatient_changePhone_returns200`. The two PatientTests runs call the live HAPI FHIR server. Both passed on the first try, so no rerun was needed.
* Live suite (`-Dtestng.suite=src/test/resources/testng-live.xml`): 15 run, 0 failures, 1 known skip (`getEncounter_found_returns200`).

### Fixed: EncounterTests create returned 400; class now independent

* Problem: Both `createEncounter_*` tests got HTTP 400 from HAPI FHIR but accepted 201 or 400, so they could never fail. `getEncounter_found_returns200` always skipped.
* Cause (from the OperationOutcome): `HAPI-1094: Resource Condition/TEST-COND-001 not found, specified in path: Encounter.diagnosis.condition`. The factory put a reference to a Condition that does not exist into every Encounter. There was also a second, silent bug: `Encounter.classCode` was serialized as `"classCode"`, which HAPI drops as an unknown element. FHIR R4 names it `class`.
* Fix: `@JsonProperty("class")` on `FhirModels.Encounter.classCode`. Removed the dangling `diagnosis` from `FhirTestDataFactory.buildEncounter`. The tests also passed `"Patient/" + id` to a factory that adds `Patient/` itself, which made the reference `Patient/Patient/{id}`. They now pass the bare id.
* Independence: same pattern as PatientTests. Each test creates its own patient (and its own encounter where needed) with the helpers `createPatient()` and `postEncounter(...)`, which assert 201. Ids go into `createdEncounterIds` and `createdPatientIds`. `@AfterMethod(alwaysRun = true)` deletes encounters first, then patients. Removed the static ids, the `"example"` patient fallback, `@AfterClass`, every `priority`, every `dependsOnMethods`, and the `SkipException` in `getEncounter_found_returns200`. The class-level `@Test(groups = "live")` is unchanged.
* Assertions: both `createEncounter_*` tests now assert 201 only.
* Negative test: the old missing-subject test passed only because of the bad Condition reference, since subject is optional (0..1) in FHIR R4. HAPI also accepts a missing `status` (it does not enforce cardinality on create). The test is now `createEncounter_invalidStatus_returns4xx`. It sends `status: "not-a-status"`, which the HAPI parser rejects with 400 (HAPI-1821), and still asserts 4xx. If the server ever returns 201, the id is recorded so cleanup deletes it.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 29 run, 0 failures. API 88 run, 0 failures, 0 skipped. Live suite (`testng-live.xml`): 15 run, 0 failures, 0 skipped. `createEncounter_ambulatory_returns201`, `getEncounter_found_returns200` and `createEncounter_invalidStatus_returns4xx` each pass alone.

## 2026.11.20

### Test: UserTests no longer depends on test order

* Problem: `getUserByUsername_found`, `updateUser_happyPath` and `deleteUser_happyPath` used `dependsOnMethods = "createUser_happyPath"` and a shared `createdUsername` field, so they could not run alone. If the create test failed, the others were skipped.
* Fix: Same pattern as PetTests. Added a private helper `createUser()` that POSTs `PetTestDataFactory.userPayload()` with the same checks as `createUser_happyPath` (200, `LIVE_API_SLA_MS`, has `message`), records the username in `createdUsernames` and returns it. The three tests each create their own user first. `createUser_happyPath` also records its username. `deleteUser_happyPath` removes its username from the list after a successful delete. `@AfterMethod(alwaysRun = true)` deletes every recorded username (best effort, failures ignored) and clears the list. It replaces the old `@AfterClass` cleanup. All `priority` and `dependsOnMethods` attributes are gone from the class.
* Missing-credentials login: `login_missingCredentials` asserted no status, so it could never fail. Renamed to `login_missingCredentials_returns200`, it now asserts `statusCode(200)`, and the `@Description` says this documents public PetStore behavior (a real API should return 400).
* Other assertions: unchanged.

### Test: StoreTests no longer depends on test order

* Problem: `getOrder_found` and `deleteOrder_happyPath` used `dependsOnMethods = "placeOrder_happyPath"` and a shared `createdOrderId` field, so they could not run alone.
* Fix: Same pattern as PetTests. Added a private helper `placeOrder()` with the same payload and checks as `placeOrder_happyPath` (200, `LIVE_API_SLA_MS`, has `id`, status `placed`), which records the id in `createdOrderIds` and returns it. Both tests place their own order first. `placeOrder_happyPath` also records its id. `deleteOrder_happyPath` removes its id after a successful delete. `@AfterMethod(alwaysRun = true)` deletes every recorded order id (best effort, failures ignored) and clears the list. It replaces the old `@AfterClass` cleanup. All `priority` and `dependsOnMethods` attributes are gone from the class.
* `getOrder_notFound`: used the fixed id from `TestDataFactory.nonExistentId()` (999999999), which anyone can create on the public PetStore. It now places an order, DELETEs it (asserts 200), removes the id from the list, then GETs it and asserts 404 only. Same fix as `PetTests.getPetById_notFound`.
* Zero quantity: `placeOrder_zeroQuantity` asserted 2xx while its `@Description` said 400. Renamed to `placeOrder_zeroQuantity_isAccepted`. The `@Description` now says the public PetStore accepts quantity 0. Still asserts `is2xx()`.
* Other assertions: unchanged.

### Result

* On JDK 17, `./mvnw -q clean verify` passes. Shared 29 run, 0 failures. API 88 run, 0 failures, 0 skipped.
* Each test passes alone, run with `./mvnw -q -pl api -am test -Dtest=<Class>#<method> -Dsurefire.failIfNoSpecifiedTests=false` (one test run each): `UserTests#getUserByUsername_found`, `UserTests#updateUser_happyPath`, `UserTests#deleteUser_happyPath`, `StoreTests#getOrder_found`, `StoreTests#deleteOrder_happyPath`, `StoreTests#getOrder_notFound`. These call the live PetStore. All passed on the first try, so no rerun was needed, and no Surefire retries were reported.
* `grep -rn dependsOnMethods api/src/test/java` finds nothing.
* Known gap, not changed: in `deleteUser_happyPath` the post-delete GET (`verify`) is never asserted; the next check repeats the assertion on the DELETE response.
* Fixed later the same day: `deleteUser_happyPath` now asserts `ResponseValidator.from(verify).statusCode(404)` and the duplicate check on the DELETE response is gone. Before the fix, the GET right after delete was logged in 3 runs on its own: 404 (`User not found`) every time, so no retry was added. Result: `./mvnw -q clean verify` passes (shared 29, api 88, 0 failed, 0 skipped) and the test passes alone 3 of 3 times.

### Refactor and fix: AI TestGenerator is unit tested and rejects bad output

* Seam: Added `LlmClient` (`LlmResponse complete(LlmRequest)`), records `LlmRequest(system, user, model, maxTokens)` and `LlmResponse(text, stopReason, inputTokens, outputTokens)`, and `AnthropicLlmClient`. The OkHttp call and the `LLM_API_KEY` check moved out of `TestGenerator` into `AnthropicLlmClient` unchanged (same URL, headers, timeouts, `max_tokens` 8192, no system prompt sent). It now also reads `stop_reason` and `usage.input_tokens` / `usage.output_tokens`. I/O errors are wrapped in `UncheckedIOException` and `TestGenerator` rethrows the `IOException`, so `TestGeneratorRunner` exit codes are unchanged (2 for a missing key, 1 for I/O).
* Constructors: `TestGenerator()` (uses `new AnthropicLlmClient()` and `BaseConfig.AI_OUTPUT_DIR`, as before), `TestGenerator(LlmClient)`, and `TestGenerator(LlmClient, Path outputBaseDir)` for tests. `TestGeneratorRunner` and the CI `generate-tests` job are unchanged.
* Package fix: The prompt asked for `com.aitesting.api.<project>.aigenerated`, but files are written to `.../com/aitesting/api/aigenerated/<project>/`. The prompt now asks for `com.aitesting.api.aigenerated.<project>`. The folder is unchanged (CI uploads it). README path corrected to match.
* Output checks, before anything is written:
  * `stopReason` `max_tokens`: the whole reply is rejected. Only `llm-raw-response.txt` is written, and the reason is logged at ERROR.
  * Per code block, skipped with a WARN naming the reason: package line not exactly `package com.aitesting.api.aigenerated.<project>;`, no `@Test` annotation, no public class name (the name must also be a valid Java identifier, so it cannot point outside the folder), or no closing fence.
  * A final INFO line logs how many blocks were accepted and rejected.
* Tests: Added `shared/src/test/java/com/aitesting/shared/ai/TestGeneratorTest.java` (JUnit 5, `@TempDir`, a `FakeLlmClient` that returns a scripted reply and records the request). 7 tests: valid reply writes one file with the right name and package; `max_tokens` writes no `.java` file; wrong package skipped; no `@Test` skipped; no code blocks saves `llm-raw-response.txt`; the request contains the spec text and the expected package; two valid blocks write two files. The real Anthropic API is never called.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 36 run (was 29), 0 failures. API 88 run, 0 failures, 0 skipped.

## 2026.11.21

### Added: AI TestGenerator rejects generated tests that do not compile

* New class: `com.aitesting.shared.ai.JavaCompileCheck`. `errors(className, source)` compiles one source string in memory with `ToolProvider.getSystemJavaCompiler()` (`-proc:none`, class files to a temp folder that is deleted afterwards) and returns the ERROR diagnostics. An empty list means it compiles.
* Classpath, in order: `surefire.test.class.path` if set; else the URLs of the thread context class loader if it is a `URLClassLoader` (as under `mvn exec:java`); else `java.class.path`. Surefire and exec:java both hide the real project classpath from `java.class.path`. A DEBUG line names the source used.
* No system compiler (JRE only): the check is skipped with one WARN log and every block is treated as compiling.
* TestGenerator: after the package, `@Test` and class-name checks pass, each block is compiled. On errors the block is skipped with WARN `does not compile: line <n>: <first diagnostic>` (multi-line javac messages folded onto one line) and counted as rejected. New constructor `TestGenerator(LlmClient, Path, JavaCompileCheck)`; the others use a real `JavaCompileCheck`.
* Tests (`TestGeneratorTest`, real compile check, no mock): a block using `org.testng.annotations.Test` and `com.aitesting.shared.http.ApiClient` compiles and is written; a block importing `com.aitesting.shared.http.MagicClient` is skipped; a block calling `ApiClient.DEFAULT.teleport(...)` is skipped. Existing fixtures still compile, so none were changed.
* Mutation check: with `errors()` changed to always return no errors, exactly the two new negative tests failed (`missingImport_isSkipped`, `unknownApiClientMethod_isSkipped`). Restored afterwards.
* CI: the `generate-tests` job now runs `mvn exec:java` with `-Dexec.classpathScope=compile`, so TestNG (provided scope in shared) is on the classpath the compile check sees. Not run here (it needs `LLM_API_KEY`; the real API was not called).
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 41 run (was 38), 0 failures. API 88 run, 0 failures, 0 skipped.

### Changed: AI TestGenerator after the first real run

First real run (`claude-sonnet-4-6`, live PetStore spec): 0 accepted, 1 rejected. The single reply hit `stop_reason=max_tokens` at 8192 output tokens, so nothing was written, yet the runner still exited 0. The cut-off class also used methods that do not exist (static `ResponseValidator.assertStatusCode`, `AllureHelper.logStep`, `ApiClient.builder()`, `TestDataFactory.generateSmallImageBytes`).

#### 1. Runner exits 3 when nothing is accepted

* `TestGenerator.generateFromSpec` / `generateFromSpecString` now return `GenerationResult(accepted, rejected)`. A whole-reply rejection (`max_tokens`, or no code blocks) counts as 1 rejected.
* `TestGeneratorRunner.main` calls `run(args, Supplier<TestGenerator>)` and exits with its code. When `accepted == 0` it logs ERROR `No generated tests were accepted` and returns 3. Exit codes 1 (I/O or unexpected) and 2 (configuration, e.g. `LLM_API_KEY` missing) are unchanged. The final INFO line now gives the totals.
* Tests: new `TestGeneratorRunnerTest` (local temp spec file, `FakeLlmClient`, logback `ListAppender`): no accepted block gives 3 plus the log line; one accepted block gives 0; a configuration error gives 2; a missing spec gives 1.

#### 2. The prompt lists the real framework API

* New class `FrameworkApiSummary`. `build()` uses reflection to list the public fields and public declared methods (name, parameter types, return type, `static` where it applies) of `ApiClient`, `ResponseValidator`, `TestDataFactory` and `AllureHelper`, sorted by name. The CRTP self type `T` is shown as the class name (`statusCode(int) -> ResponseValidator`), so fluent chains read correctly. It is built from the classes at run time, so it follows code changes without edits.
* Prompt: adds the summary under "Framework API", then the rule `Use only these methods. Do not invent methods. ResponseValidator is fluent: ResponseValidator.from(response).statusCode(200).hasField(...)`. "Use RestAssured 5.x for HTTP calls" became "Make HTTP calls through ApiClient (it wraps RestAssured 5.x)".
* Tests: new `FrameworkApiSummaryTest` (the summary contains `static ApiClient DEFAULT`, `get(String) -> Response`, `from(Response) -> static ResponseValidator<?>`, `hasField(String)`, `randomId()`, `step(String, Runnable)` and others; it does not contain `assertStatusCode` or `logStep`). `TestGeneratorTest` checks that the request contains the summary and the rule.

#### 3. One test class per OpenAPI tag

* New class `OpenApiTagGroups`. `byFirstTag(spec)` parses a JSON spec with Jackson and groups the paths by their first tag (the first tag of the first operation that has one; untagged paths go to `default`). Each group spec keeps every other top-level key (info, basePath, definitions, ...) and narrows `paths` and `tags` to the group. Groups keep the order they first appear in.
* TestGenerator: one request per group, containing only that group's paths, asking for exactly one class named `<Tag>ApiGeneratedTests` (`PetApiGeneratedTests`, `StoreApiGeneratedTests`, `UserApiGeneratedTests`) in one code block. Each group logs `Result for tag '<tag>': <n> block(s) accepted, <m> rejected`, and the returned `GenerationResult` is the sum. A rejected reply for a group is saved as `llm-raw-response-<tag>.txt`, so groups do not overwrite each other. Specs that are not JSON or have no paths are sent whole in one request, as before (`llm-raw-response.txt`).
* Not changed: `MAX_TOKENS` is still 8192 per request, and the class name the prompt asks for is not enforced.
* Tests (`TestGeneratorTest`, `FakeLlmClient` now records every request and can reply per request): a spec with tags pet, store and user leads to three requests, each containing its own two paths and none of the other four, asking for its own class name, and keeping `definitions`; replies of valid, valid, no code give `GenerationResult(2, 1)`, two files, and `llm-raw-response-user.txt`.
* Mutation check: with the group spec keeping all paths, `threeTags_threeRequests_eachWithOnlyItsOwnPaths` failed (`request 0 should not contain "/store/order"`). Restored afterwards.

#### Result

* On JDK 17, `./mvnw -q clean verify` passes. Shared 50 run (was 41), 0 failures. API 88 run, 0 failures, 0 skipped. No test calls the real Anthropic API.

## 2026.11.22

### Changed: AI TestGenerator max tokens is configurable, and tests per class are capped

* `MAX_TOKENS = 8192` is gone. New `BaseConfig.LLM_MAX_TOKENS` (env/property `LLM_MAX_TOKENS`, default 16000) is sent as `max_tokens` on every request. The CI `generate-tests` job passes `${{ vars.LLM_MAX_TOKENS }}` (261493c), which is an empty string when the repo variable is not set, so an empty or blank value counts as unset and gives the default.
* New `EnvConfig.getPositiveIntStrict(key, default)`: blank or missing gives the default; anything that is not a positive whole number throws `IllegalStateException("<KEY> must be a positive whole number, but was '<value>'")` when `BaseConfig` loads, so the generator stops at startup instead of quietly using the default (as `getInt` does).
* `TestGeneratorRunner` logs `Using max_tokens=<n>` and `Max tests per class: <n>` once at startup.
* New `BaseConfig.LLM_MAX_TESTS_PER_CLASS` (default 8, same rules). The prompt gains the requirement `Write at most N @Test methods. Choose the most valuable: one happy path per endpoint first, then the most important negative cases (not found, invalid input). Do not write near-duplicate tests.`
* An accepted class with more than N `@Test` methods is still written, with WARN `<Class> has <count> @Test methods, more than the cap of <N> (LLM_MAX_TESTS_PER_CLASS); written anyway`. The INFO "written" line now gives the `@Test` count.
* New constructor `TestGenerator(LlmClient, Path, JavaCompileCheck, int maxTokens, int maxTestsPerClass)`; the others read `BaseConfig`.
* `LLM_MAX_TOKENS=16000` and `LLM_MAX_TESTS_PER_CLASS=8` added to `config/default.properties` and the README config table.
* Not changed: the prompt still says "Cover: happy path, 404, 400, 401" and "boundary values for all numeric and string parameters", which pulls against the cap.
* Tests: new `EnvConfigTest` (empty `LLM_MAX_TOKENS` gives 16000; blank/missing give the default; `" 12000 "` gives 12000; `16k` throws with the message above; 0 and -3 throw). `TestGeneratorTest`: every request carries the configured max tokens (12345); the prompt contains the cap rule with the configured number (5); a class with 4 tests and a cap of 3 is written and logs exactly that WARN; a class at the cap logs no WARN. No test calls the real API.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 59 run (was 50), 0 failures. API 88 run, 0 failures, 0 skipped.

### Ran: first AI-generated PetStore tests against the live server

* Command: the pom pins `suiteXmlFiles` to `testng.xml`, so `-Dtest='com.aitesting.api.aigenerated.petstore.*'` is ignored (it ran the normal 88 tests). Used a throwaway suite listing the three generated classes, passed as `-Dtestng.suite=<path>`.
* Result: 24 tests, 12 passed, 5 failed (one of them Store `setUp`), 9 skipped (8 Store tests plus `tearDown`, skipped because `setUp` failed).
* Pet 6/8: `testGetPetById_InvalidId` expected 400, got 404; `testFindPetsByStatus_InvalidStatus` expected 400, got 200. The spec documents 400 for both operations, but the live PetStore does not return it.
* Store 0/8: `setUp` threw `ClassCastException: Integer cannot be cast to Long`. It assigned `ResponseValidator.extract("id")` (generic `<V> V`) to a `long` field; small JSON numbers come back as Integer. The test was wrong.
* User 6/8: `testLoginUser_InvalidCredentials_BadRequest` (empty password) expected 400, got 200; the spec documents 400, the live server logs anyone in. `testDeleteUser_HappyPath`: DELETE was 2xx, then a GET straight after it expected 404 and got 200, because the live server is not consistent right after a write. The test's assumption was wrong.

### Changed: AI TestGenerator prompt tightened from that run

* Removed `Cover: happy path (200/201), not found (404), bad request (400), unauthorized (401)` and `Cover boundary values for all numeric and string parameters`, which pulled against the tests-per-class cap.
* Added, as `TestGenerator.LIVE_RUN_RULES`: `Never fall back to a fixed id when setup fails. Fail in setup instead.`; `Use BaseConfig.LIVE_API_SLA_MS for live servers` (with the exact `withinSla(BaseConfig.LIVE_API_SLA_MS)` call and import); `Only assert a negative status code that the OpenAPI spec documents for that operation. Do not guess.`; read numeric ids with `response.jsonPath().getLong("id")`, not `extract(...)` into a long; do not assert that a read right after a write already shows the change.
* Known gap: 3 of the 5 failures were status codes the spec does document (400) that the live PetStore does not return, so the documented-status rule does not stop them.
* Tests (`TestGeneratorTest`, fake client): each new rule is in the prompt; the per-tag prompt has every rule; neither prompt has the removed Cover lines.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 65 run (was 59), 0 failures. API 88 run, 0 failures, 0 skipped.

## 2026.11.24

### Ran: AI-generated PetStore tests with the tightened prompt (451ecf1)

* Generate: `LLM_MAX_TOKENS` unset, so the default 16000 was used (logged `Using max_tokens=16000`, `Max tests per class: 8`). Same command as last time: `mvn exec:java -pl shared -Dexec.classpathScope=compile -Dexec.mainClass=com.aitesting.shared.ai.TestGeneratorRunner -Dexec.args="https://petstore.swagger.io/v2/swagger.json petstore"` with `LLM_MODEL=claude-sonnet-4-6`, after `install -pl shared -am -DskipTests`. Result: 3 accepted, 0 rejected, 8 `@Test` methods per class, no cap WARN.
* Run: throwaway suite listing the three classes, `./mvnw -pl api -am test -Dtestng.suite=<path>`, live PetStore, JDK 17. No Surefire reruns were reported.
* Result: 24 tests, 18 passed, 6 failed, 0 skipped. All three `@BeforeClass` setups passed.
* Pet 5/8:
  * `testFindPetsByStatus_invalidStatus`: expected 400, got 200. Category B (spec documents 400).
  * `testGetPetById_notFound`: expected 404, got 200. Category A. It uses the fixed `TestDataFactory.nonExistentId()` (999999999), which exists on the public PetStore (a direct GET returns 200). Same bug fixed by hand on 2026.11.19.
  * `testUpdatePetWithForm_happyPath`: expected 2xx, got 415. Category A. The operation consumes `application/x-www-form-urlencoded`, but the test posts a Map, which `ApiClient` sends as JSON.
* Store 6/8:
  * `placeOrder_withEmptyBody_shouldReturn400`: expected 400, got 200. Category B (spec documents 400 Invalid Order).
  * `getOrderById_withInvalidId_shouldReturn400` (id 0): expected 400, got 404. Category B (spec documents 400 Invalid ID supplied).
* User 7/8:
  * `testLoginUser_invalidCredentials_returns400` (empty username and password): expected 400, got 200. Category B, same as last run.
* Categories: A (test bug) 2, B (live PetStore breaks its own spec) 4, C (timing or flake) 0.
* Pass rate: 18/24 = 75% (last run 12/24 = 50%). Correct rate, (passed + B) / total: 22/24 = 92% (last run (12 + 3) / 24 = 62.5%; last run's 5 failures were 3 B, 1 A (Store `setUp` ClassCastException, which also caused the 9 skips) and 1 C (read right after delete)).

### Checked: the generated files

* Fixed ids: no setup falls back to a fixed id (every `@BeforeClass` asserts and fails instead). Fixed ids are still used as "not found" ids: `PetApiGeneratedTests.testGetPetById_notFound` and `StoreApiGeneratedTests.deleteOrder_withNonExistentId_shouldReturn404` use `TestDataFactory.nonExistentId()` (999999999); `StoreApiGeneratedTests.getOrderById_withNonExistentId_shouldReturn404` hardcodes `9L` (and computes `nonExistentId()` without using it). The Pet one failed this run; the two Store ones passed but can fail whenever someone creates those ids.
* More than one status accepted: `StoreApiGeneratedTests.getOrderById_withNonExistentId_shouldReturn404` asserts `status == 404 || status == 400`, then `is4xx()`. `UserApiGeneratedTests.testLogoutUser_happyPath` asserts only `is2xx()` on a GET. `getInventory_*` and `getOrderById_withValidId_*` call `is2xx()` on a GET after `statusCode(200)`, which is redundant, not wider. Non-GET happy paths (POST, PUT, DELETE in all three classes, plus the Pet delete test's and User `setUpUser`'s `>= 200 && < 300` checks) assert 2xx only.
* Tests with no assertion: none. Every `@Test` method asserts a status. Only the `@AfterClass` cleanups assert nothing, by design.
* Also seen: the classes share state through `@BeforeClass` fields and use `priority` and `dependsOnMethods` (Pet 2, Store 2 plus `priority` on all 8, User `priority` on all 8). The hand-written classes dropped both on 2026.11.18 to 2026.11.20. The prompt does not forbid them.
* Generated files: not edited, not committed (`**/aigenerated/` is gitignored).

## 2026.11.25

### Changed: AI TestGenerator rejects order dependence, fixed ids and multi-status asserts

* Four prompt rules added to `TestGenerator.LIVE_RUN_RULES`, from the 2026.11.24 run:
  * `For a not-found test, create the resource, delete it, then GET it and assert 404. Never use TestDataFactory.nonExistentId() or a hardcoded id.`
  * `Assert exactly one status code per response. Never use || between status codes.`
  * `Do not use priority or dependsOnMethods. Each test creates its own data.`
  * `If an operation consumes application/x-www-form-urlencoded, send it with ApiClient.given().contentType(...).formParams(...).`
* Output checks: after the package, `@Test` and class-name checks and before compiling, `TestGenerator.forbiddenPatternReason` rejects a block that:
  * contains `nonExistentId(`: `uses nonExistentId() (a fixed id that may exist on the server)`
  * matches `\bdependsOnMethods\b` or `\bpriority\s*=`: `uses dependsOnMethods (tests must not depend on order)` / `uses priority = (tests must not depend on order)`
  * compares a status code next to `||`, on either side (`== 404 ||`, `|| status == 400`, also `!=`): `accepts more than one status code ('<match>')`
  * The block is skipped and counted as rejected, with WARN `Skipped code block <n> for <label>: <reason>`, like the other checks.
* Known limits: the checks are plain text. A comment that mentions `nonExistentId(` is rejected too, and a hardcoded not-found id (for example `9L`) is not detected; only the prompt forbids it. Status checks written as `is4xx()` are not counted as multi-status.
* Tests (`TestGeneratorTest`, fake client, no real API): the prompt contains the four rules (and `taggedPrompt_containsAllRules` covers them through `LIVE_RUN_RULES`); one rejection test per check, each asserting no file was written and the exact WARN line; `s == null || n == 2` is still accepted.
* Mutation checks, one at a time and restored afterwards: with the `nonExistentId(` check disabled only `nonExistentId_isRejected` failed; with the order check disabled only `orderDependence_isRejected` failed; with the `||` check disabled only `multiStatusAssert_isRejected` failed; with `FORM_PARAMS_RULE` taken out of `LIVE_RUN_RULES` `prompt_containsSecondRunRules` failed.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 70 run (was 65), 0 failures. API 88 run, 0 failures, 0 skipped.

### Ran: AI-generated PetStore tests with the output checks (3ed8ec7)

* Generate: same command as 2026.11.24 (`install -pl shared -am -DskipTests`, then `mvn exec:java -pl shared -Dexec.classpathScope=compile -Dexec.mainClass=com.aitesting.shared.ai.TestGeneratorRunner -Dexec.args="https://petstore.swagger.io/v2/swagger.json petstore"`, `LLM_MODEL=claude-sonnet-4-6`). One difference: `LLM_MAX_TOKENS=12000` was set in the shell, so it logged `Using max_tokens=12000` (last run 16000). No reply hit `max_tokens`. `Max tests per class: 8`.
* Generation result: 3 accepted, 0 rejected, 8 `@Test` methods per class, no cap WARN. No class was rejected by the new checks.
* Run: throwaway suite listing the three classes, `./mvnw -pl api -am test -Dtestng.suite=<path>`, live PetStore, JDK 17. No Surefire reruns were reported.
* Result: 24 tests, 21 passed, 3 failed, 0 skipped. All `@BeforeClass` setups passed.
* Pet 8/8. `testUpdatePetWithForm_happyPath` now sends `contentType("application/x-www-form-urlencoded").formParams(...)` and passes (was 415). `testGetPetById_notFound_afterDelete` creates, deletes, then asserts 404, and passes (was the fixed-id failure).
* Store 6/8:
  * `testGetOrderByIdInvalidIdReturns400` (id 0): expected 400, got 404. Category B (spec documents 400 Invalid ID supplied). Same as last run.
  * `testPlaceOrderInvalidBodyReturns400` (quantity -999, status `INVALID_STATUS`): expected 400, got 200. Category B (spec documents 400 Invalid Order). Same as last run.
* User 7/8:
  * `testLoginUser_emptyPassword_returns400`: expected 400, got 200. Category B, same as the last two runs.
* Categories: A (test bug) 0, B (live PetStore breaks its own spec) 3, C (timing or flake) 0.
* Pass rate: 21/24 = 87.5% (last run 18/24 = 75%, first run 12/24 = 50%). Correct rate, (passed + B) / total: 24/24 = 100% (last run 22/24 = 92%, first run 62.5%).

| Run | Passed | Failed | Skipped | A | B | C | Pass rate | Correct rate |
|---|---|---|---|---|---|---|---|---|
| 2026.11.22 | 12 | 5 | 9 | 1 | 3 | 1 | 50% | 62.5% |
| 2026.11.24 | 18 | 6 | 0 | 2 | 4 | 0 | 75% | 92% |
| 2026.11.25 | 21 | 3 | 0 | 0 | 3 | 0 | 87.5% | 100% |

### Checked: the generated files

* Fixed ids: none used for not-found. No `nonExistentId()` anywhere. All four not-found tests create their own resource and delete it first: `PetApiGeneratedTests.testGetPetById_notFound_afterDelete` and `UserApiGeneratedTests.testGetUser_afterDelete_returns404` GET after delete; `StoreApiGeneratedTests.testGetOrderByIdNotFoundReturns404` GETs after delete; `testDeleteOrderNotFoundReturns404` deletes twice. The only literal id is `0L` in `testGetOrderByIdInvalidIdReturns400`, which is an invalid-id test, not a not-found test. No setup falls back to a fixed id. Pet's `@BeforeClass` seed pet is used only by its own `@AfterClass` cleanup.
* More than one status accepted: no `||` between status codes. Every GET asserts one exact code. `is2xx()` appears only on writes (Pet DELETE; Store DELETE; User POST, PUT, DELETE, POST createWithList). Setup steps inside tests check `>= 200 && < 300` (Pet and Store deletes before a not-found check) or `< 300` (User creates and deletes), which is a 2xx-only check on a write.
* Tests with no assertion: none. All 24 `@Test` methods assert a status. Only the `@BeforeClass` / `@AfterClass` methods in Store and User assert nothing (they only log notes), and Pet's `@AfterClass` cleanup is best effort.
* Order dependence: no `priority` or `dependsOnMethods` in any class (last run: Pet 2, Store 2 plus `priority` on all 8, User `priority` on all 8). There are no shared test-data fields between tests.
* Generated files: not edited, not committed (`**/aigenerated/` is gitignored).

## 2026.11.26

### Upgraded: RestAssured 5.4.0 to 6.3.0

* Change: in the root `pom.xml`, `restassured.version` and `json-schema-validator.version` both go from 5.4.0 to 6.3.0, in one commit on `deps/rest-assured-6`. This replaces Dependabot #7 (json-schema-validator) and #8 (rest-assured), which each bumped only one of the two.
* Resolved: `dependency:tree` shows `rest-assured`, `json-path`, `xml-path`, `rest-assured-common` and `json-schema-validator` all at 6.3.0.
* Code changes: none. Everything compiled and passed with no fixes, so no RestAssured 6 behavior change affected this project.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 70 run, 0 failures, 0 skipped. API 88 run, 0 failures, 0 skipped.
* Live suite (`-Dtestng.suite=src/test/resources/testng-live.xml`, live HAPI FHIR): 15 run, 0 failures, 0 skipped.

### Upgraded: Dependabot maven-minor-patch group (#10), except Surefire

* Change: applied all 14 updates from Dependabot #10 in #12 (branch `deps/maven-minor-patch`): Maven wrapper 3.9.14 to 3.10.2, Playwright 1.44.0 to 1.65.1, Selenium 4.52.0 to 4.54.0, TestNG 7.9.0 to 7.12.0, Mockito 5.11.0 to 5.24.0, Jackson 2.17.0 to 2.23.0, SLF4J 2.0.13 to 2.0.20, Logback 1.5.6 to 1.6.8, Lombok 1.18.32 to 1.18.48, maven-compiler-plugin 3.13.0 to 3.16.0, exec-maven-plugin 3.3.0 to 3.7.0, surefire-testng 3.2.5 to 3.5.6. The one exception: `maven-surefire-plugin` goes from 3.2.5 to 3.5.6 (the latest 3.5.x), not 3.6.1, in `pom.xml`, `api/pom.xml` and `shared/pom.xml`.
* Why: #10 failed because Surefire 3.6.0+ runs TestNG tests on the JUnit Platform, which can't read TestNG suite XML files. `testng.xml` and `testng-live.xml` depend on `suiteXmlFiles`.
* Dependabot: `.github/dependabot.yml` now ignores `org.apache.maven.plugins:maven-surefire-plugin` versions `[3.6.0,)`.
* Result: on JDK 17, `./mvnw -q clean verify` passes. Shared 70 run, 0 failures, 0 skipped. API 88 run, 0 failures, 0 skipped.
* Live suite (`-Dtestng.suite=src/test/resources/testng-live.xml`): 15 run, 0 failures, 0 skipped.
* UI spot checks: `PetStoreSeleniumTests#swaggerUI_loads_successfully` (Selenium) and `PetStoreUITests#swaggerUI_loads_successfully` (Playwright) both pass.
* Report note: with Surefire 3.5.6, the `@Nested` classes of `AsyncApiClientTest` go into one report file. Its console line says "Tests run: 0" and its XML says `tests="27"`, but all 28 cases run and the module total is still 70.
* CI: the first run of Live FHIR Tests failed to resolve `maven-install-plugin:3.2.0` from Central (no 429 in the log; the artifact is on Central). It passed on one rerun. #12 merged; #10 closed with a comment pointing to #12.
* Future task: migrate `testng.xml` / `testng-live.xml` to TestNG groups so Surefire 3.6+ can be used, then remove the Dependabot ignore.

### Changed: Dependabot no longer opens major-version PRs (Maven)

* Change: `.github/dependabot.yml` (maven ecosystem) now has an ignore rule for `dependency-name: "*"` with `update-types: ["version-update:semver-major"]`, commented "Major upgrades are planned by hand, one per branch." The Surefire `[3.6.0,)` ignore and the `maven-minor-patch` group are unchanged. GitHub Actions updates are not affected.
* Closed Dependabot PRs with "Closing: major upgrades are planned by hand. Tracked in the plan.": #16 (WireMock 3), #15 (OkHttp 5), #14 (allure-maven 3), #13 (JUnit 6), #9 (Allure 3). #6 (allure-maven 3.1.0) was already closed, superseded by #14.

### Planned: major upgrades, one per branch

| Upgrade | From | To | Replaces | Notes |
|---|---|---|---|---|
| WireMock | `com.github.tomakehurst:wiremock-jre8-standalone` 2.35.2 | 3.x | #16 | 3.x moves to `org.wiremock` coordinates; check stub/server API changes. |
| OkHttp | `com.squareup.okhttp3:okhttp` 4.12.0 | 5.x | #15 | Check Kotlin stdlib and API changes. |
| JUnit | `org.junit.jupiter:junit-jupiter` 5.10.2 | 6.x | #13 | Check Surefire 3.5.6 compatibility (see Surefire hold above). |
| Allure (libraries) | `allure.version` 2.27.0 | 3.x | #9 | Do together with the allure-maven upgrade. |
| allure-maven plugin | `io.qameta.allure:allure-maven` 2.12.0 | 3.x | #14, #6 | Do together with the Allure libraries upgrade. |
| Surefire | `maven-surefire-plugin` 3.5.6 | 3.6+ | — | Blocked: migrate `testng.xml` / `testng-live.xml` to TestNG groups first. |
