package com.aitesting.ui;

import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * PlaywrightFactory — manages Playwright browser lifecycle.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Centralizes browser creation and teardown.
 * Each test suite gets ONE browser instance shared across tests.
 * Each test gets its own BrowserContext (isolated session).
 * Each test gets its own Page (isolated tab).
 *
 * This mirrors real-world browser test architecture:
 *   Playwright  → process (created once)
 *   Browser     → browser instance (created once per suite)
 *   Context     → isolated session (created per test)
 *   Page        → tab (created per test)
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // In @BeforeClass:
 *   PlaywrightFactory.start();
 *
 *   // In @BeforeMethod:
 *   Page page = PlaywrightFactory.newPage();
 *
 *   // In @AfterMethod:
 *   PlaywrightFactory.closePage();
 *
 *   // In @AfterClass:
 *   PlaywrightFactory.stop();
 */
public final class PlaywrightFactory {

    private static final Logger log =
        LoggerFactory.getLogger(PlaywrightFactory.class);

    private static Playwright     playwright;
    private static Browser        browser;
    private static BrowserContext context;
    private static Page           page;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts Playwright and launches the browser.
     * Call from @BeforeClass.
     */
    public static void start() {
        playwright = Playwright.create();

        BrowserType.LaunchOptions options =
            new BrowserType.LaunchOptions()
                .setHeadless(UIConfig.isHeadless())
                .setSlowMo(UIConfig.getSlowMoMs());

        browser = switch (UIConfig.getBrowserType()) {
            case "firefox" -> playwright.firefox().launch(options);
            case "webkit"  -> playwright.webkit().launch(options);
            default        -> playwright.chromium().launch(options);
        };

        log.info("Playwright started — browser: {} headless: {}",
            UIConfig.getBrowserType(), UIConfig.isHeadless());
    }

    /**
     * Creates a new isolated BrowserContext and Page.
     * Call from @BeforeMethod — gives each test a clean session.
     *
     * @return fresh Page ready for navigation
     */
    public static Page newPage() {
        context = browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(
                    UIConfig.getViewportWidth(),
                    UIConfig.getViewportHeight()
                )
        );

        context.setDefaultTimeout(UIConfig.getActionTimeoutMs());
        context.setDefaultNavigationTimeout(UIConfig.getPageLoadTimeoutMs());

        page = context.newPage();
        log.debug("New page created");
        return page;
    }

    /**
     * Closes the current page and context.
     * Call from @AfterMethod.
     */
    public static void closePage() {
        if (page != null && !page.isClosed()) {
            page.close();
        }
        if (context != null) {
            context.close();
        }
        log.debug("Page and context closed");
    }

    /**
     * Stops the browser and Playwright process.
     * Call from @AfterClass.
     */
    public static void stop() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("Playwright stopped");
    }

    /**
     * Returns the current active Page.
     * Use when page reference is needed outside of newPage() caller.
     */
    public static Page getPage() {
        return page;
    }

    /**
     * Returns true if browser is running.
     */
    public static boolean isRunning() {
        return browser != null && browser.isConnected();
    }
}
