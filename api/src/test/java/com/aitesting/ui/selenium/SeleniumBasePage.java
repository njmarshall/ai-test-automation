package com.aitesting.ui.selenium;

import com.aitesting.ui.UIConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * SeleniumBasePage: common Selenium actions shared by Selenium page objects.
 *
 * Selenium counterpart of BasePage. Playwright auto-waits on every action;
 * Selenium does not, so every helper here waits explicitly through a
 * WebDriverWait plus ExpectedConditions. There is no Thread.sleep and no
 * implicit wait anywhere.
 *
 * Selectors are CSS strings, the same strings the Playwright page objects
 * use, so a locator fix in one framework can be copied to the other.
 */
public abstract class SeleniumBasePage {

    private static final Logger log =
        LoggerFactory.getLogger(SeleniumBasePage.class);

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected SeleniumBasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(
            driver, Duration.ofMillis((long) UIConfig.getActionTimeoutMs()));
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    protected void navigate(String url) {
        log.debug("Navigating to: {}", url);
        driver.get(url);   // blocks until document.readyState is complete
    }

    public String getTitle() {
        return driver.getTitle();
    }

    // ── Element interactions ──────────────────────────────────────────────────

    /**
     * Waits for the element to be clickable, scrolls it to the viewport
     * center (avoids sticky headers intercepting the click), then clicks.
     */
    protected void click(String css) {
        log.debug("Clicking: {}", css);
        WebElement el = wait.until(
            ExpectedConditions.elementToBeClickable(By.cssSelector(css)));
        scrollIntoView(el);
        el.click();
    }

    /**
     * Waits for the input to be visible, clears it, and types text.
     */
    protected void type(String css, String text) {
        log.debug("Typing into {}: {}", css, text);
        WebElement el = waitForVisible(css);
        scrollIntoView(el);
        el.clear();
        el.sendKeys(text);
    }

    /**
     * Waits for the element to be visible and returns its visible text.
     */
    protected String getText(String css) {
        return waitForVisible(css).getText();
    }

    /**
     * Immediate (non-waiting) visibility check, matching Playwright's
     * Locator.isVisible() semantics.
     */
    protected boolean isVisible(String css) {
        try {
            List<WebElement> els = driver.findElements(By.cssSelector(css));
            return !els.isEmpty() && els.get(0).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Waits up to the UIConfig action timeout for the element to be visible.
     */
    protected WebElement waitForVisible(String css) {
        return wait.until(
            ExpectedConditions.visibilityOfElementLocated(By.cssSelector(css)));
    }

    protected void scrollIntoView(String css) {
        scrollIntoView(waitForVisible(css));
    }

    protected void scrollIntoView(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].scrollIntoView({block: 'center'});", el);
    }
}
