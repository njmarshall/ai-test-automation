package com.aitesting.ui;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ScreenshotHelper — automatic screenshot capture for UI tests.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * PURPOSE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * When a UI test fails, a screenshot is worth 1000 log lines.
 * ScreenshotHelper:
 *   → Captures full-page screenshot on test failure
 *   → Attaches screenshot to Allure report automatically
 *   → Saves PNG file to target/screenshots/ for local debugging
 *
 * ═══════════════════════════════════════════════════════════════════════
 * USAGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 *   // In @AfterMethod — capture on failure:
 *   @AfterMethod(alwaysRun = true)
 *   public void tearDown(ITestResult result) {
 *       if (!result.isSuccess()) {
 *           ScreenshotHelper.captureOnFailure(
 *               PlaywrightFactory.getPage(),
 *               result.getName()
 *           );
 *       }
 *       PlaywrightFactory.closePage();
 *   }
 */
public final class ScreenshotHelper {

    private static final Logger log =
        LoggerFactory.getLogger(ScreenshotHelper.class);

    /**
     * Captures a screenshot and attaches it to the Allure report.
     * Also saves to target/screenshots/ for local debugging.
     *
     * @param page     Playwright Page to screenshot
     * @param testName name of the failing test (used as filename)
     */
    public static void captureOnFailure(Page page, String testName) {
        if (page == null || page.isClosed()) {
            log.warn("Cannot screenshot — page is null or closed");
            return;
        }

        try {
            // Ensure screenshot directory exists
            Path dir = Paths.get(UIConfig.getScreenshotDir());
            Files.createDirectories(dir);

            // Capture screenshot bytes
            byte[] bytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true));

            // Save to file
            String filename = testName
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                + "_FAILED.png";
            Path filePath = dir.resolve(filename);
            Files.write(filePath, bytes);
            log.info("Failure screenshot saved: {}", filePath);

            // Attach to Allure report
            Allure.addAttachment(
                "Screenshot — " + testName,
                "image/png",
                new ByteArrayInputStream(bytes),
                "png"
            );

        } catch (IOException e) {
            log.error("Failed to capture screenshot for: {}", testName, e);
        }
    }

    /**
     * Captures a screenshot and attaches to Allure (always — not just on failure).
     * Use for key steps in happy path tests.
     *
     * @param page        Playwright Page
     * @param stepName    descriptive name for the step
     */
    public static void captureStep(Page page, String stepName) {
        if (page == null || page.isClosed()) return;

        try {
            byte[] bytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(false));

            Allure.addAttachment(
                "Step: " + stepName,
                "image/png",
                new ByteArrayInputStream(bytes),
                "png"
            );
        } catch (Exception e) {
            log.warn("Could not capture step screenshot: {}", stepName);
        }
    }

    private ScreenshotHelper() {}
}
