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
