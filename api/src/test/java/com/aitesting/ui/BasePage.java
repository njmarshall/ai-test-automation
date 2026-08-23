package com.aitesting.ui;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * BasePage — common Playwright page actions shared across all Page Objects.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: Page Object Model (POM) base class
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Every page object extends BasePage and inherits:
 *   → navigate()     — go to a URL and wait for load
 *   → getTitle()     — get current page title
 *   → isVisible()    — check if element is visible
 *   → click()        — click an element safely
 *   → fill()         — type into an input field
 *   → getText()      — get element text content
 *   → screenshot()   — capture screenshot
 *   → waitForLoad()  — wait for network idle
 *
 * Mirrors the same pattern as ResponseValidator in the API layer:
 *   ResponseValidator → base assertions for all API tests
 *   BasePage          → base actions for all UI tests
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   public class SwaggerHomePage extends BasePage {
 *       public SwaggerHomePage(Page page) { super(page); }
 *
 *       public SwaggerHomePage open() {
 *           navigate(UIConfig.PETSTORE_UI_URL);
 *           return this;
 *       }
 *   }
 */
public abstract class BasePage {

    private static final Logger log =
        LoggerFactory.getLogger(BasePage.class);

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Navigate to a URL and wait for the page to fully load.
     *
     * @param url full URL to navigate to
     */
    protected void navigate(String url) {
        log.debug("Navigating to: {}", url);
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        log.debug("Page loaded: {}", url);
    }

    /**
     * Returns the current page title.
     */
    protected String getTitle() {
        return page.title();
    }

    /**
     * Returns the current page URL.
     */
    protected String getUrl() {
        return page.url();
    }

    // ── Element interactions ──────────────────────────────────────────────────

    /**
     * Returns true if the element matching selector is visible.
     *
     * @param selector CSS or text selector
     */
    protected boolean isVisible(String selector) {
        try {
            return page.locator(selector).isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clicks an element and waits for any navigation to complete.
     *
     * @param selector CSS or text selector
     */
    protected void click(String selector) {
        log.debug("Clicking: {}", selector);
        page.locator(selector).click();
    }

    /**
     * Types text into an input field (clears first).
     *
     * @param selector CSS or text selector
     * @param text     text to type
     */
    protected void fill(String selector, String text) {
        log.debug("Filling {} with: {}", selector, text);
        page.locator(selector).fill(text);
    }

    /**
     * Returns the text content of an element.
     *
     * @param selector CSS or text selector
     */
    protected String getText(String selector) {
        return page.locator(selector).textContent();
    }

    /**
     * Returns inner text of an element.
     */
    protected String getInnerText(String selector) {
        return page.locator(selector).innerText();
    }

    /**
     * Waits for an element to be visible on the page.
     *
     * @param selector CSS or text selector
     */
    protected void waitForVisible(String selector) {
        page.locator(selector).waitFor();
    }

    /**
     * Waits for network to be idle — useful after AJAX calls.
     */
    protected void waitForNetworkIdle() {
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /**
     * Scrolls element into view.
     */
    protected void scrollIntoView(String selector) {
        page.locator(selector).scrollIntoViewIfNeeded();
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /**
     * Returns the page load time in milliseconds.
     * Use for SLA assertions.
     */
    protected long getLoadTimeMs() {
        Object timing = page.evaluate(
            "() => performance.timing.loadEventEnd " +
            "- performance.timing.navigationStart"
        );
        return ((Number) timing).longValue();
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    /**
     * Takes a full-page screenshot and saves to target/screenshots/.
     *
     * @param filename filename without extension
     * @return path to saved screenshot
     */
    public String screenshot(String filename) {
        String path = UIConfig.getScreenshotDir()
                    + "/" + filename + ".png";
        page.screenshot(new Page.ScreenshotOptions()
            .setPath(Paths.get(path))
            .setFullPage(true));
        log.info("Screenshot saved: {}", path);
        return path;
    }

    /**
     * Takes a screenshot of a specific element.
     *
     * @param selector CSS selector of element to capture
     * @param filename filename without extension
     */
    public String screenshotElement(String selector, String filename) {
        String path = UIConfig.getScreenshotDir()
                    + "/" + filename + ".png";
        page.locator(selector).screenshot(
            new com.microsoft.playwright.Locator.ScreenshotOptions()
                .setPath(Paths.get(path)));
        log.info("Element screenshot saved: {}", path);
        return path;
    }
}
