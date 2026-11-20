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
