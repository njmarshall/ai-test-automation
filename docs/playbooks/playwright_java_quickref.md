# Playwright — Java Quick Reference

> Fast-recall cheatsheet for Senior SDET / Automation Architects  
> Stack: `com.microsoft.playwright` · Java 11+ · JUnit 5 / TestNG · Maven/Gradle

---

## Table of Contents
- [Maven / Gradle Setup](#maven--gradle-setup)
- [Core API](#core-api)
- [Locators](#locators)
- [Assertions](#assertions)
- [Waiting Strategies](#waiting-strategies)
- [Page Object Model](#page-object-model)
- [Base Test Class](#base-test-class)
- [Auth & storageState](#auth--storagestate)
- [API Testing](#api-testing)
- [Network Mocking](#network-mocking)
- [Advanced Patterns](#advanced-patterns)
- [JUnit 5 Integration](#junit-5-integration)
- [TestNG Integration](#testng-integration)
- [Gotchas](#gotchas)
- [CLI Commands](#cli-commands)

---

## Maven / Gradle Setup

### Maven `pom.xml`

```xml
<dependencies>
  <dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.43.0</version>
  </dependency>
  <!-- JUnit 5 -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.2.5</version>
    </plugin>
  </plugins>
</build>
```

### Gradle `build.gradle`

```groovy
dependencies {
  implementation 'com.microsoft.playwright:playwright:1.43.0'
  testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}
```

### Install browsers

```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
# or
gradle playwright install
```

---

## Core API

```java
import com.microsoft.playwright.*;

try (Playwright playwright = Playwright.create()) {
    Browser browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions().setHeadless(true)
    );
    BrowserContext context = browser.newContext(
        new Browser.NewContextOptions().setBaseURL("https://example.com")
    );
    Page page = context.newPage();

    // Navigation
    page.navigate("/login");
    page.navigate("https://example.com",
        new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    page.goBack();
    page.reload();

    // Actions
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit")).click();
    page.getByLabel("Email").fill("user@example.com");
    page.getByLabel("Password").fill("s3cret");
    page.getByRole(AriaRole.COMBOBOX).selectOption("CA");
    page.getByLabel("Remember me").check();
    page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("Products")).hover();
    page.locator("#source").dragTo(page.locator("#target"));
}
```

---

## Locators

```java
// Preferred — semantic / a11y-based
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit"))
page.getByLabel("Email address")
page.getByPlaceholder("Search...")
page.getByText("Welcome back")
page.getByTestId("submit-btn")                    // data-testid="submit-btn"

// CSS / XPath fallback
page.locator("css=.btn-primary")
page.locator("xpath=//button[@type='submit']")

// Chaining (scope to parent)
page.getByRole(AriaRole.DIALOG)
    .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Confirm"))

// nth (avoid if possible)
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Edit")).nth(0)

// Filter by text
page.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText("Jane Doe"))
```

---

## Assertions

Uses `com.microsoft.playwright.assertions.PlaywrightAssertions`. All assertions auto-wait/retry.

```java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

// Page assertions
assertThat(page).hasURL("/dashboard");
assertThat(page).hasTitle(Pattern.compile("Dashboard"));

// Locator assertions
Locator btn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"));
assertThat(btn).isVisible();
assertThat(btn).isEnabled();
assertThat(btn).isDisabled();
assertThat(btn).hasText("Save changes");
assertThat(btn).hasAttribute("aria-label", "Save");

// Negative
assertThat(page.getByText("Error")).not().isVisible();

// Count
assertThat(page.getByRole(AriaRole.LISTITEM)).hasCount(5);
```

---

## Waiting Strategies

> ⚠️ Avoid `page.waitForTimeout()` — flakiness trap.

```java
// Auto-waiting — just use locator actions
page.getByRole(AriaRole.BUTTON).click();              // waits for actionable

// Wait for element state
page.getByText("Loading...").waitFor(
    new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
page.locator(".toast").waitFor(
    new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

// Wait for URL after action
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
page.waitForURL("**/dashboard");

// Wait for specific response
Response response = page.waitForResponse(
    r -> r.url().contains("/api/user") && r.status() == 200,
    () -> page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Load")).click()
);
```

---

## Page Object Model

```java
// pages/LoginPage.java
import com.microsoft.playwright.*;

public class LoginPage {
    private final Page page;
    private final Locator emailInput;
    private final Locator passwordInput;
    private final Locator submitBtn;

    public LoginPage(Page page) {
        this.page = page;
        this.emailInput    = page.getByLabel("Email");
        this.passwordInput = page.getByLabel("Password");
        this.submitBtn     = page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Log in"));
    }

    public void goto() {
        page.navigate("/login");
    }

    public void login(String email, String password) {
        emailInput.fill(email);
        passwordInput.fill(password);
        submitBtn.click();
    }
}
```

---

## Base Test Class

A clean JUnit 5 base wires up Playwright lifecycle and browser config in one place.

```java
// base/BaseTest.java
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

public abstract class BaseTest {
    protected static Playwright playwright;
    protected static Browser     browser;
    protected BrowserContext      context;
    protected Page                page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser    = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true)
        );
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext(new Browser.NewContextOptions()
            .setBaseURL(System.getenv().getOrDefault("BASE_URL", "http://localhost:3000"))
            .setRecordVideoDir(Paths.get("build/videos/"))
        );
        context.tracing().start(new Tracing.StartOptions()
            .setScreenshots(true).setSnapshots(true));
        page = context.newPage();
    }

    @AfterEach
    void closeContext(TestInfo testInfo) {
        boolean failed = testInfo.getTags().contains("failed");
        context.tracing().stop(new Tracing.StopOptions()
            .setPath(Paths.get("build/traces/" + testInfo.getDisplayName() + ".zip")));
        context.close();
    }
}
```

---

## Auth & storageState

```java
// One-time setup: run this standalone to produce auth.json
Playwright playwright = Playwright.create();
Browser browser = playwright.chromium().launch();
BrowserContext context = browser.newContext();
Page page = context.newPage();

page.navigate(System.getenv("BASE_URL") + "/login");
page.getByLabel("Email").fill(System.getenv("TEST_USER"));
page.getByLabel("Password").fill(System.getenv("TEST_PASS"));
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
page.waitForURL("**/dashboard");

context.storageState(new BrowserContext.StorageStateOptions()
    .setPath(Paths.get("auth.json")));
browser.close();
playwright.close();
```

```java
// Reuse in tests via newContext
BrowserContext context = browser.newContext(new Browser.NewContextOptions()
    .setStorageStatePath(Paths.get("auth.json"))
);
```

---

## API Testing

```java
import com.microsoft.playwright.*;

Playwright playwright = Playwright.create();
APIRequestContext request = playwright.request().newContext(
    new APIRequest.NewContextOptions()
        .setBaseURL("https://api.example.com")
        .setExtraHTTPHeaders(Map.of("Authorization", "Bearer " + token))
);

// GET
APIResponse response = request.get("/patients");
assertEquals(200, response.status());
JsonObject body = new Gson().fromJson(response.text(), JsonObject.class);

// POST
APIResponse created = request.post("/patients",
    RequestOptions.create().setData(Map.of(
        "name", "Jane Doe",
        "dob",  "1990-01-15"
    ))
);
assertEquals(201, created.status());

// DELETE
request.delete("/patients/" + id);
request.dispose();
```

---

## Network Mocking

```java
// Mock a 500 error
page.route("**/api/patients", route -> route.fulfill(
    new Route.FulfillOptions()
        .setStatus(500)
        .setContentType("application/json")
        .setBody("{\"error\":\"Internal Server Error\"}")
));

// Abort (block analytics, ads)
page.route("**/*.analytics.js", route -> route.abort());

// Intercept + modify real response
page.route("**/api/config", route -> {
    APIResponse response = route.fetch();
    String body = response.text().replace("\"featureFlag\":false",
                                          "\"featureFlag\":true");
    route.fulfill(new Route.FulfillOptions()
        .setResponse(response).setBody(body));
});

// Assert payload sent by UI
Request[] captured = {null};
page.onRequest(req -> {
    if (req.url().contains("/api/save") && req.method().equals("POST"))
        captured[0] = req;
});
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
assertNotNull(captured[0]);
```

---

## Advanced Patterns

### Data-driven with JUnit 5 `@ParameterizedTest`

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ParameterizedTest
@CsvSource({
    "admin@test.com,  admin123,  /admin",
    "user@test.com,   user123,   /dashboard",
    "viewer@test.com, viewer123, /view"
})
void userLandsOnCorrectPage(String email, String pass, String expectedPath) {
    page.navigate("/login");
    page.getByLabel("Email").fill(email);
    page.getByLabel("Password").fill(pass);
    page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Log in")).click();
    assertThat(page).hasURL(expectedPath);
}
```

### Visual regression

```java
// Capture screenshot
page.navigate("/dashboard");
byte[] screenshot = page.screenshot(
    new Page.ScreenshotOptions().setFullPage(true));

// Compare against stored baseline (use AssertJ / custom comparator)
byte[] baseline = Files.readAllBytes(Paths.get("src/test/resources/snapshots/dashboard.png"));
assertArrayEquals(baseline, screenshot);
// Or use a library like aShot / Shutterbug for threshold-based diff
```

### iFrames

```java
FrameLocator iframe = page.frameLocator("#sandbox-iframe");
iframe.getByRole(AriaRole.BUTTON, new FrameLocator.GetByRoleOptions().setName("Agree")).click();
assertThat(iframe.getByText("Accepted")).isVisible();
```

### File upload / download

```java
// Upload
FileChooser fileChooser = page.waitForFileChooser(
    () -> page.getByLabel("Upload file").click()
);
fileChooser.setFiles(Paths.get("src/test/resources/sample.pdf"));

// Download
Download download = page.waitForDownload(
    () -> page.getByRole(AriaRole.LINK,
        new Page.GetByRoleOptions().setName("Export CSV")).click()
);
download.saveAs(Paths.get("build/downloads/" + download.suggestedFilename()));
```

### Mobile emulation

```java
BrowserContext mobileCtx = browser.newContext(
    new Browser.NewContextOptions()
        .setViewportSize(390, 844)
        .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)...")
        .setIsMobile(true)
        .setHasTouch(true)
);
```

---

## JUnit 5 Integration

```java
// Full test example extending BaseTest
import org.junit.jupiter.api.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Login Tests")
class LoginTest extends BaseTest {

    @Test
    @DisplayName("Valid credentials redirect to dashboard")
    void validLoginRedirectsToDashboard() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.goto();
        loginPage.login(
            System.getenv("TEST_USER"),
            System.getenv("TEST_PASS")
        );
        assertThat(page).hasURL("/dashboard");
    }

    @Test
    @DisplayName("Invalid password shows error")
    void invalidPasswordShowsError() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.goto();
        loginPage.login("user@test.com", "wrongpass");
        assertThat(page.getByText("Invalid credentials")).isVisible();
    }
}
```

---

## TestNG Integration

```java
import org.testng.annotations.*;
import com.microsoft.playwright.*;

public class LoginTestNG {
    Playwright playwright;
    Browser     browser;
    Page        page;

    @BeforeClass
    public void setup() {
        playwright = Playwright.create();
        browser    = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true));
    }

    @BeforeMethod
    public void openPage() {
        page = browser.newPage();
    }

    @AfterMethod
    public void closePage() { page.close(); }

    @AfterClass
    public void teardown() { playwright.close(); }

    @Test(dataProvider = "loginData")
    public void testLogin(String email, String pass, String expectedUrl) {
        page.navigate("/login");
        page.getByLabel("Email").fill(email);
        page.getByLabel("Password").fill(pass);
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Log in")).click();
        assertThat(page).hasURL(expectedUrl);
    }

    @DataProvider(name = "loginData")
    public Object[][] loginData() {
        return new Object[][] {
            { "admin@test.com", "admin123", "/admin"     },
            { "user@test.com",  "user123",  "/dashboard" },
        };
    }
}
```

---

## Gotchas

### Strict mode — multiple matches

```java
// Throws if 2+ buttons named 'Edit' exist
page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Edit")).click(); // ERROR

// Fix: scope to parent row
page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName("Jane Doe"))
    .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Edit"))
    .click();
```

### Thread safety — one Page per thread

```java
// Playwright objects are NOT thread-safe. In parallel tests:
// Use @BeforeEach (JUnit 5) or @BeforeMethod (TestNG) to create fresh Page per test.
// Never share a Page across tests or threads.
```

### try-with-resources for safe cleanup

```java
try (Playwright playwright = Playwright.create()) {
    // Playwright, Browser, Context, Page all auto-close
}
```

### Debugging

```bash
# Run with headed browser + slow-mo
HEADED=true SLOWMO=500 mvn test

# Codegen (record a test by clicking)
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI \
  -D exec.args="codegen https://example.com"

# Open trace viewer
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI \
  -D exec.args="show-trace build/traces/mytest.zip"
```

---

## CLI Commands

| Command | Purpose |
|---|---|
| `mvn test` | Run all tests |
| `mvn test -Dtest=LoginTest` | Run a single test class |
| `mvn test -Dgroups=smoke` | Run tagged group |
| `playwright install` | Install/update browsers |
| `playwright codegen <url>` | Record test via browser |
| `playwright show-trace <file>` | Open trace viewer |
| `playwright screenshot <url> out.png` | Quick screenshot |

---

*Part of the [ai-test-automation](https://github.com/njmarshall/ai-test-automation) Java portfolio*
