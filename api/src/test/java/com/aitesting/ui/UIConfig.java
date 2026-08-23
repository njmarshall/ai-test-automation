package com.aitesting.ui;

import com.aitesting.shared.config.BaseConfig;

/**
 * UIConfig — Playwright browser configuration.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Centralizes all browser settings in one place.
 * Reads from environment variables with sensible defaults.
 * Shared across all UI capstones (PetStore, Payment, etc.)
 *
 * Environment variables:
 *   HEADLESS      → true (CI) or false (local debugging)
 *   BROWSER_TYPE  → chromium | firefox | webkit
 *   SLOW_MO_MS    → milliseconds between actions (debug mode)
 *   UI_BASE_URL   → base URL for UI tests
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // Check if headless:
 *   boolean headless = UIConfig.isHeadless();
 *
 *   // Get browser type:
 *   String browser = UIConfig.getBrowserType();
 *
 *   // Get page load timeout:
 *   double timeout = UIConfig.getPageLoadTimeoutMs();
 */
public final class UIConfig {

    // ── Defaults ──────────────────────────────────────────────────────────────

    private static final boolean DEFAULT_HEADLESS       = true;
    private static final String  DEFAULT_BROWSER        = "chromium";
    private static final int     DEFAULT_SLOW_MO        = 0;
    private static final int     DEFAULT_PAGE_TIMEOUT   = 30_000;
    private static final int     DEFAULT_ACTION_TIMEOUT = 10_000;
    private static final int     DEFAULT_VIEWPORT_W     = 1280;
    private static final int     DEFAULT_VIEWPORT_H     = 720;

    // ── PetStore UI URL ───────────────────────────────────────────────────────

    public static final String PETSTORE_UI_URL =
        System.getenv().getOrDefault(
            "PETSTORE_UI_URL",
            "https://petstore.swagger.io"
        );

    // ── Browser settings ──────────────────────────────────────────────────────

    /**
     * Run browser in headless mode (no visible window).
     * Set HEADLESS=false for local debugging.
     * Always true in CI/CD.
     */
    public static boolean isHeadless() {
        String val = System.getenv("HEADLESS");
        if (val == null) return DEFAULT_HEADLESS;
        return Boolean.parseBoolean(val);
    }

    /**
     * Browser type: chromium | firefox | webkit
     * Default: chromium (Chrome/Edge compatible)
     */
    public static String getBrowserType() {
        return System.getenv().getOrDefault(
            "BROWSER_TYPE", DEFAULT_BROWSER);
    }

    /**
     * Slow motion delay between actions (milliseconds).
     * Set SLOW_MO_MS=500 for visible debugging.
     * Default: 0 (full speed)
     */
    public static int getSlowMoMs() {
        String val = System.getenv("SLOW_MO_MS");
        if (val == null) return DEFAULT_SLOW_MO;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return DEFAULT_SLOW_MO; }
    }

    /**
     * Page load timeout in milliseconds.
     * Default: 30,000ms (30 seconds)
     */
    public static double getPageLoadTimeoutMs() {
        return DEFAULT_PAGE_TIMEOUT;
    }

    /**
     * Individual action timeout in milliseconds.
     * Applied to clicks, fills, waits.
     * Default: 10,000ms (10 seconds)
     */
    public static double getActionTimeoutMs() {
        return DEFAULT_ACTION_TIMEOUT;
    }

    /**
     * Browser viewport width in pixels.
     */
    public static int getViewportWidth() {
        return DEFAULT_VIEWPORT_W;
    }

    /**
     * Browser viewport height in pixels.
     */
    public static int getViewportHeight() {
        return DEFAULT_VIEWPORT_H;
    }

    /**
     * Screenshot directory — relative to project root.
     * Screenshots saved here on test failure.
     */
    public static String getScreenshotDir() {
        return "api/target/screenshots";
    }

    private UIConfig() {}
}
