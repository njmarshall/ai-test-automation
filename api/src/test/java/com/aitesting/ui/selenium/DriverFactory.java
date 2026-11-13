package com.aitesting.ui.selenium;

import com.aitesting.ui.UIConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * DriverFactory: manages the Selenium ChromeDriver lifecycle.
 *
 * Selenium counterpart of PlaywrightFactory. Differences worth knowing:
 *   - Selenium has no Browser/Context split, so each test gets a whole new
 *     ChromeDriver (a fresh browser process) instead of a fresh context.
 *   - The driver is held in a ThreadLocal so TestNG parallel runs never
 *     share a browser between threads.
 *   - Driver binaries are resolved by Selenium Manager (built into 4.6+),
 *     so no WebDriverManager dependency is needed.
 *
 * Headless when CI=true or HEADLESS=true. Timeouts and viewport come from
 * UIConfig so both frameworks run with the same settings.
 *
 * Usage:
 *   @BeforeMethod  WebDriver driver = DriverFactory.create();
 *   @AfterMethod   DriverFactory.quit();
 */
public final class DriverFactory {

    private static final Logger log =
        LoggerFactory.getLogger(DriverFactory.class);

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    /**
     * Creates a ChromeDriver for the current thread and returns it.
     * Quits any driver this thread already holds first.
     */
    public static WebDriver create() {
        quit();

        ChromeOptions options = new ChromeOptions();
        if (isHeadless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
            "--window-size=" + UIConfig.getViewportWidth()
                + "," + UIConfig.getViewportHeight(),
            "--no-sandbox",
            "--disable-dev-shm-usage"
        );

        WebDriver driver = new ChromeDriver(options);
        // Explicit waits only: implicit wait stays at its default of 0.
        driver.manage().timeouts().pageLoadTimeout(
            Duration.ofMillis((long) UIConfig.getPageLoadTimeoutMs()));

        DRIVER.set(driver);
        log.info("ChromeDriver started, headless: {}", isHeadless());
        return driver;
    }

    /**
     * Returns the current thread's driver.
     *
     * @throws IllegalStateException if create() was not called on this thread
     */
    public static WebDriver get() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                "No WebDriver for this thread. Call DriverFactory.create() first.");
        }
        return driver;
    }

    /**
     * Quits the current thread's driver, if any, and clears the ThreadLocal.
     */
    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
                log.debug("ChromeDriver quit");
            } finally {
                DRIVER.remove();
            }
        }
    }

    /**
     * Headless when CI=true or HEADLESS=true.
     */
    public static boolean isHeadless() {
        return Boolean.parseBoolean(System.getenv("CI"))
            || Boolean.parseBoolean(System.getenv("HEADLESS"));
    }

    private DriverFactory() {}
}
