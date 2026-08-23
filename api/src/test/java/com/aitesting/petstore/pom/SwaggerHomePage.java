package com.aitesting.petstore.pom;

import com.aitesting.ui.BasePage;
import com.aitesting.ui.UIConfig;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SwaggerHomePage — Page Object for petstore.swagger.io.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN: Page Object Model (POM)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Encapsulates all interactions with the Swagger UI main page.
 * Test classes use this page object — they never use raw selectors.
 *
 * This mirrors the same pattern as TestDataFactory in the API layer:
 *   TestDataFactory  → encapsulates data construction
 *   SwaggerHomePage  → encapsulates UI interactions
 *
 * Fluent API — methods return this for chaining:
 *   new SwaggerHomePage(page)
 *       .open()
 *       .expandPetSection()
 *       .waitForEndpoints();
 */
public class SwaggerHomePage extends BasePage {

    private static final Logger log =
        LoggerFactory.getLogger(SwaggerHomePage.class);

    // ── Selectors ─────────────────────────────────────────────────────────────

    private static final String SWAGGER_TITLE =
        ".swagger-ui .title";
    private static final String PET_SECTION =
        "#operations-tag-pet";
    // The tag header carries a data-is-open attribute reflecting its real
    // expand state; the "is-open" CSS class lives on the *parent* section
    // div instead, so a compound selector on the header never matches it.
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

    // ── Constructor ───────────────────────────────────────────────────────────

    public SwaggerHomePage(Page page) {
        super(page);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Navigates to PetStore Swagger UI and waits for full load.
     */
    public SwaggerHomePage open() {
        log.info("Opening PetStore Swagger UI: {}",
            UIConfig.PETSTORE_UI_URL);
        navigate(UIConfig.PETSTORE_UI_URL);
        waitForVisible(SCHEME_CONTAINER);
        log.info("PetStore Swagger UI loaded");
        return this;
    }

    /**
     * Expands the Pet section to show all pet endpoints.
     */
    public SwaggerHomePage expandPetSection() {
        log.debug("Expanding Pet section");
        ensureSectionOpen(PET_SECTION, PET_SECTION_EXPANDED);
        return this;
    }

    /**
     * Expands the Store section.
     */
    public SwaggerHomePage expandStoreSection() {
        ensureSectionOpen(STORE_SECTION, STORE_SECTION_EXPANDED);
        return this;
    }

    /**
     * Expands the User section.
     */
    public SwaggerHomePage expandUserSection() {
        ensureSectionOpen(USER_SECTION, USER_SECTION_EXPANDED);
        return this;
    }

    /**
     * Clicks a tag section header only if it isn't already expanded.
     * petstore.swagger.io renders tag sections expanded by default
     * (docExpansion: "list"), so a blind click would collapse it and
     * hide every operation underneath instead of revealing them.
     */
    private void ensureSectionOpen(String sectionSelector, String expandedSelector) {
        scrollIntoView(sectionSelector);
        if (!isVisible(expandedSelector)) {
            click(sectionSelector);
            waitForVisible(expandedSelector);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns the Swagger UI API title text.
     */
    public String getApiTitle() {
        return getText(API_INFO_TITLE);
    }

    /**
     * Returns true if the Swagger UI title element is visible.
     */
    public boolean isSwaggerLoaded() {
        return isVisible(SCHEME_CONTAINER);
    }

    /**
     * Returns true if the Pet section is visible.
     */
    public boolean isPetSectionVisible() {
        return isVisible(PET_SECTION);
    }

    /**
     * Returns true if the Pet section is expanded.
     */
    public boolean isPetSectionExpanded() {
        return isVisible(PET_SECTION_EXPANDED);
    }

    /**
     * Returns true if Store section is visible.
     */
    public boolean isStoreSectionVisible() {
        return isVisible(STORE_SECTION);
    }

    /**
     * Returns true if User section is visible.
     */
    public boolean isUserSectionVisible() {
        return isVisible(USER_SECTION);
    }

    /**
     * Returns page load time in milliseconds.
     */
    public long getPageLoadTimeMs() {
        return getLoadTimeMs();
    }
}
