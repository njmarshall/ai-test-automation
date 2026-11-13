# Selenium vs Playwright (PetStore Swagger UI)

Observed while building the same 8 scenarios in both (`PetStoreUITests`, `PetStoreSeleniumTests`), JDK 17, Chrome, 2026.11.13.

- **Waits:** Playwright auto-waits on every locator action, so its `BasePage` helpers are one-liners. Selenium waits for nothing, so every `SeleniumBasePage` helper wraps a `WebDriverWait` plus `ExpectedConditions` (clickable, visible). `isVisible()` had to stay a non-waiting `findElements` check to match Playwright's semantics.
- **Locators:** All CSS strings ported unchanged and passed on the first run, because the Playwright pass had already learned the DOM quirks (`data-is-open`, three `response-col_status` cells, `select[multiple]`). Playwright's chained `locator(a).locator(b)` becomes a descendant CSS selector `a b`.
- **Setup:** Playwright downloads its own browser builds. Selenium uses the installed Chrome, and Selenium Manager resolves chromedriver with no WebDriverManager. Each framework needs one Maven dependency.
- **Lifecycle:** Playwright starts one browser per class and isolates tests with a fresh context. Selenium has no context concept, so `DriverFactory` launches a new Chrome per test and holds it in a `ThreadLocal` for TestNG parallel safety.
- **Speed:** Headless class totals were Playwright 20.8s and Selenium 22.4s. Page-load-only tests ran slightly faster in Selenium (1.4 to 1.8s vs 1.7 to 1.8s per method). The Try it out and Execute flows ran slower (2.8 to 2.9s vs 2.1 to 2.4s) because of the explicit wait round trips.
- **Debugging:** Playwright gives full-page screenshots, `SLOW_MO_MS`, and trace tooling. ChromeDriver screenshots cover only the viewport. A Selenium `TimeoutException` names both the condition and the locator, which makes a bad selector just as easy to spot.
