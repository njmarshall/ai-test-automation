package com.aitesting.petstore.selenium.pages;

import com.aitesting.ui.UIConfig;
import com.aitesting.ui.selenium.SeleniumBasePage;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SwaggerHomePageSelenium: Selenium page object for petstore.swagger.io.
 *
 * Mirrors com.aitesting.petstore.pom.SwaggerHomePage. The selectors are
 * copied from the Playwright version, including the data-is-open lesson:
 * the tag header carries data-is-open with its real expand state, while
 * the "is-open" class sits on the parent section div. Tag sections are
 * expanded by default (docExpansion: "list"), so a blind click collapses
 * them; ensureSectionOpen() only clicks when the section is closed.
 */
public class SwaggerHomePageSelenium extends SeleniumBasePage {

    private static final Logger log =
        LoggerFactory.getLogger(SwaggerHomePageSelenium.class);

    // ── Selectors (same strings as SwaggerHomePage) ───────────────────────────

    private static final String PET_SECTION =
        "#operations-tag-pet";
    private static final String PET_SECTION_EXPANDED =
        "#operations-tag-pet[data-is-open='true']";
    private static final String STORE_SECTION =
        "#operations-tag-store";
    private static final String STORE_SECTION_EXPANDED =
        "#operations-tag-store[data-is-open='true']";
    private static final String USER_SECTION =
        "#operations-tag-user";
    private static final String USER_SECTION_EXPANDED =
        "#operations-tag-user[data-is-open='true']";
    private static final String API_INFO_TITLE =
        ".swagger-ui .info .title";
    private static final String SCHEME_CONTAINER =
        ".swagger-ui .scheme-container";

    public SwaggerHomePageSelenium(WebDriver driver) {
        super(driver);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Navigates to the Swagger UI and waits until it has rendered.
     */
    public SwaggerHomePageSelenium open() {
        log.info("Opening PetStore Swagger UI: {}", UIConfig.PETSTORE_UI_URL);
        navigate(UIConfig.PETSTORE_UI_URL);
        waitForVisible(SCHEME_CONTAINER);
        return this;
    }

    public SwaggerHomePageSelenium expandPetSection() {
        ensureSectionOpen(PET_SECTION, PET_SECTION_EXPANDED);
        return this;
    }

    public SwaggerHomePageSelenium expandStoreSection() {
        ensureSectionOpen(STORE_SECTION, STORE_SECTION_EXPANDED);
        return this;
    }

    public SwaggerHomePageSelenium expandUserSection() {
        ensureSectionOpen(USER_SECTION, USER_SECTION_EXPANDED);
        return this;
    }

    private void ensureSectionOpen(String sectionSelector, String expandedSelector) {
        scrollIntoView(sectionSelector);
        if (!isVisible(expandedSelector)) {
            click(sectionSelector);
            waitForVisible(expandedSelector);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public String getApiTitle() {
        return getText(API_INFO_TITLE);
    }

    public boolean isSwaggerLoaded() {
        return isVisible(SCHEME_CONTAINER);
    }

    public boolean isPetSectionVisible() {
        return isVisible(PET_SECTION);
    }

    public boolean isPetSectionExpanded() {
        return isVisible(PET_SECTION_EXPANDED);
    }

    public boolean isStoreSectionVisible() {
        return isVisible(STORE_SECTION);
    }

    public boolean isUserSectionVisible() {
        return isVisible(USER_SECTION);
    }
}
