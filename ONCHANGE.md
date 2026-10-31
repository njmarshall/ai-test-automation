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
