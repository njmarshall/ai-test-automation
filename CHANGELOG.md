## 2026-11-02
- fix(ci): retry once on transient SLA failures (rerunFailingTestsCount=1)
  - api/pom.xml: added `<rerunFailingTestsCount>1</rerunFailingTestsCount>` inside
    maven-surefire-plugin configuration block
  - Guards against intermittent latency on shared public sandboxes (HAPI FHIR, PetStore)
    without masking real regressions — Surefire marks a second-attempt pass as FLAKY
    in the Allure report rather than silently passing, so slow tests are visible
  - Root cause that prompted this: PatientTests.createPatient_validAdult_returns201
    response time 13537ms exceeded SLA 3000ms — confirmed HAPI sandbox latency,
    not a code regression

## 2026-10-30
- feat(option-e): AsyncApiClient unit test suite added (shared module)
  - New: shared/src/test/java/com/aitesting/shared/http/AsyncApiClientTest.java
  - 15 JUnit 5 unit tests covering all three polling invariants:
      1. Returns immediately when expected status is reached
      2. Throws AssertionError on terminal failure (never polls again after FAILED)
      3. Throws AssertionError on timeout after N attempts
  - Technique: MockedStatic<AllureHelper> + MockedStatic<Thread> to freeze sleep
    without waiting — tests run in <50ms total with zero live network calls
  - Mirrors Python equivalent test_async_poller.py — same three invariants,
    same mock-clock approach, cross-language parity documented in class Javadoc
  - shared/ had zero unit coverage before this commit; AsyncApiClient is the
    polling backbone for all three domains (healthcare prior-auth, insurance MVR,
    fintech ACH)

- feat(option-e): shared/pom.xml — JUnit 5 + Mockito 5 test scope dependencies
  - Added: junit-jupiter, mockito-core, mockito-junit-jupiter (test scope)
  - Note: mockito-inline merged into mockito-core in Mockito 5.x — no separate
    mockito-inline dependency needed; MockedStatic works with mockito-core 5.11.0
  - maven-surefire-plugin configured with `<includes>**/*Test.java</includes>`
    to pick up JUnit 5 tests in the shared module

- ci(option-e): new CI job — shared-unit-tests, runs before build-and-test
  - .github/workflows/ci.yml: added shared-unit-tests job (ubuntu-24.04)
  - build-and-test now has `needs: shared-unit-tests` — shared module must pass
    its unit tests before integration tests run; fast-fails CI on shared regressions
  - All three jobs pinned to ubuntu-24.04 (was ubuntu-latest) — avoids surprises
    on GitHub runner OS upgrades mid-project
  - CI pipeline: shared-unit-tests → build-and-test → generate-tests (on-demand)

## 2026-11-02
- fix(ci): retry once on transient SLA failures (rerunFailingTestsCount=1)
  - api/pom.xml: added `<rerunFailingTestsCount>1</rerunFailingTestsCount>` inside
    maven-surefire-plugin configuration block
  - Guards against intermittent latency on shared public sandboxes (HAPI FHIR, PetStore)
    without masking real regressions — Surefire marks a second-attempt pass as FLAKY
    in the Allure report rather than silently passing, so slow tests are visible
  - Root cause that prompted this: PatientTests.createPatient_validAdult_returns201
    response time 13537ms exceeded SLA 3000ms — confirmed HAPI sandbox latency,
    not a code regression

## 2026-10-30
- feat(option-e): AsyncApiClient unit test suite added (shared module)
  - New: shared/src/test/java/com/aitesting/shared/http/AsyncApiClientTest.java
  - 15 JUnit 5 unit tests covering all three polling invariants:
      1. Returns immediately when expected status is reached
      2. Throws AssertionError on terminal failure (never polls again after FAILED)
      3. Throws AssertionError on timeout after N attempts
  - Technique: MockedStatic<AllureHelper> + MockedStatic<Thread> to freeze sleep
    without waiting — tests run in <50ms total with zero live network calls
  - Mirrors Python equivalent test_async_poller.py — same three invariants,
    same mock-clock approach, cross-language parity documented in class Javadoc
  - shared/ had zero unit coverage before this commit; AsyncApiClient is the
    polling backbone for all three domains (healthcare prior-auth, insurance MVR,
    fintech ACH)

- feat(option-e): shared/pom.xml — JUnit 5 + Mockito 5 test scope dependencies
  - Added: junit-jupiter, mockito-core, mockito-junit-jupiter (test scope)
  - Note: mockito-inline merged into mockito-core in Mockito 5.x — no separate
    mockito-inline dependency needed; MockedStatic works with mockito-core 5.11.0
  - maven-surefire-plugin configured with `<includes>**/*Test.java</includes>`
    to pick up JUnit 5 tests in the shared module

- ci(option-e): new CI job — shared-unit-tests, runs before build-and-test
  - .github/workflows/ci.yml: added shared-unit-tests job (ubuntu-24.04)
  - build-and-test now has `needs: shared-unit-tests` — shared module must pass
    its unit tests before integration tests run; fast-fails CI on shared regressions
  - All three jobs pinned to ubuntu-24.04 (was ubuntu-latest) — avoids surprises
    on GitHub runner OS upgrades mid-project
  - CI pipeline: shared-unit-tests → build-and-test → generate-tests (on-demand)

## 2026-11-02
- fix(ci): retry once on transient SLA failures (rerunFailingTestsCount=1)
  - api/pom.xml: added `<rerunFailingTestsCount>1</rerunFailingTestsCount>` inside
    maven-surefire-plugin configuration block
  - Guards against intermittent latency on shared public sandboxes (HAPI FHIR, PetStore)
    without masking real regressions — Surefire marks a second-attempt pass as FLAKY
    in the Allure report rather than silently passing, so slow tests are visible
  - Root cause that prompted this: PatientTests.createPatient_validAdult_returns201
    response time 13537ms exceeded SLA 3000ms — confirmed HAPI sandbox latency,
    not a code regression

## 2026-10-30
- feat(option-e): AsyncApiClient unit test suite added (shared module)
  - New: shared/src/test/java/com/aitesting/shared/http/AsyncApiClientTest.java
  - 15 JUnit 5 unit tests covering all three polling invariants:
      1. Returns immediately when expected status is reached
      2. Throws AssertionError on terminal failure (never polls again after FAILED)
      3. Throws AssertionError on timeout after N attempts
  - Technique: MockedStatic<AllureHelper> + MockedStatic<Thread> to freeze sleep
    without waiting — tests run in <50ms total with zero live network calls
  - Mirrors Python equivalent test_async_poller.py — same three invariants,
    same mock-clock approach, cross-language parity documented in class Javadoc
  - shared/ had zero unit coverage before this commit; AsyncApiClient is the
    polling backbone for all three domains (healthcare prior-auth, insurance MVR,
    fintech ACH)

- feat(option-e): shared/pom.xml — JUnit 5 + Mockito 5 test scope dependencies
  - Added: junit-jupiter, mockito-core, mockito-junit-jupiter (test scope)
  - Note: mockito-inline merged into mockito-core in Mockito 5.x — no separate
    mockito-inline dependency needed; MockedStatic works with mockito-core 5.11.0
  - maven-surefire-plugin configured with `<includes>**/*Test.java</includes>`
    to pick up JUnit 5 tests in the shared module

- ci(option-e): new CI job — shared-unit-tests, runs before build-and-test
  - .github/workflows/ci.yml: added shared-unit-tests job (ubuntu-24.04)
  - build-and-test now has `needs: shared-unit-tests` — shared module must pass
    its unit tests before integration tests run; fast-fails CI on shared regressions
  - All three jobs pinned to ubuntu-24.04 (was ubuntu-latest) — avoids surprises
    on GitHub runner OS upgrades mid-project
  - CI pipeline: shared-unit-tests → build-and-test → generate-tests (on-demand)

## 2026-11-02
- fix(ci): retry once on transient SLA failures (rerunFailingTestsCount=1)
  - api/pom.xml: added `<rerunFailingTestsCount>1</rerunFailingTestsCount>` inside
    maven-surefire-plugin configuration block
  - Guards against intermittent latency on shared public sandboxes (HAPI FHIR, PetStore)
    without masking real regressions — Surefire marks a second-attempt pass as FLAKY
    in the Allure report rather than silently passing, so slow tests are visible
  - Root cause that prompted this: PatientTests.createPatient_validAdult_returns201
    response time 13537ms exceeded SLA 3000ms — confirmed HAPI sandbox latency,
    not a code regression

## 2026-10-30
- feat(option-e): AsyncApiClient unit test suite added (shared module)
  - New: shared/src/test/java/com/aitesting/shared/http/AsyncApiClientTest.java
  - 15 JUnit 5 unit tests covering all three polling invariants:
      1. Returns immediately when expected status is reached
      2. Throws AssertionError on terminal failure (never polls again after FAILED)
      3. Throws AssertionError on timeout after N attempts
  - Technique: MockedStatic<AllureHelper> + MockedStatic<Thread> to freeze sleep
    without waiting — tests run in <50ms total with zero live network calls
  - Mirrors Python equivalent test_async_poller.py — same three invariants,
    same mock-clock approach, cross-language parity documented in class Javadoc
  - shared/ had zero unit coverage before this commit; AsyncApiClient is the
    polling backbone for all three domains (healthcare prior-auth, insurance MVR,
    fintech ACH)

- feat(option-e): shared/pom.xml — JUnit 5 + Mockito 5 test scope dependencies
  - Added: junit-jupiter, mockito-core, mockito-junit-jupiter (test scope)
  - Note: mockito-inline merged into mockito-core in Mockito 5.x — no separate
    mockito-inline dependency needed; MockedStatic works with mockito-core 5.11.0
  - maven-surefire-plugin configured with `<includes>**/*Test.java</includes>`
    to pick up JUnit 5 tests in the shared module

- ci(option-e): new CI job — shared-unit-tests, runs before build-and-test
  - .github/workflows/ci.yml: added shared-unit-tests job (ubuntu-24.04)
  - build-and-test now has `needs: shared-unit-tests` — shared module must pass
    its unit tests before integration tests run; fast-fails CI on shared regressions
  - All three jobs pinned to ubuntu-24.04 (was ubuntu-latest) — avoids surprises
    on GitHub runner OS upgrades mid-project
  - CI pipeline: shared-unit-tests → build-and-test → generate-tests (on-demand)

