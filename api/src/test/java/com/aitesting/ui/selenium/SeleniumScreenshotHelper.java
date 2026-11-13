package com.aitesting.ui.selenium;

import com.aitesting.ui.UIConfig;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SeleniumScreenshotHelper: Selenium counterpart of ScreenshotHelper.
 *
 * Captures a screenshot, attaches it to Allure, and (on failure) saves a
 * PNG to the UIConfig screenshot dir. ChromeDriver only captures the
 * viewport; Playwright's full-page option has no Chrome equivalent here.
 */
public final class SeleniumScreenshotHelper {

    private static final Logger log =
        LoggerFactory.getLogger(SeleniumScreenshotHelper.class);

    /**
     * Captures a screenshot on test failure, saves it, and attaches to Allure.
     */
    public static void captureOnFailure(WebDriver driver, String testName) {
        if (driver == null) {
            log.warn("Cannot screenshot: driver is null");
            return;
        }
        try {
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            Path dir = Paths.get(UIConfig.getScreenshotDir());
            Files.createDirectories(dir);
            Path file = dir.resolve(
                "selenium_" + testName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_FAILED.png");
            Files.write(file, bytes);
            log.info("Failure screenshot saved: {}", file);

            Allure.addAttachment("Screenshot (Selenium): " + testName,
                "image/png", new ByteArrayInputStream(bytes), "png");
        } catch (Exception e) {
            log.error("Failed to capture screenshot for: {}", testName, e);
        }
    }

    /**
     * Captures a step screenshot and attaches it to Allure.
     */
    public static void captureStep(WebDriver driver, String stepName) {
        if (driver == null) return;
        try {
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("Step: " + stepName,
                "image/png", new ByteArrayInputStream(bytes), "png");
        } catch (Exception e) {
            log.warn("Could not capture step screenshot: {}", stepName);
        }
    }

    private SeleniumScreenshotHelper() {}
}
