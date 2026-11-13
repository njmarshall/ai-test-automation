package com.aitesting.petstore.selenium.pages;

import com.aitesting.ui.selenium.SeleniumBasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PetEndpointPageSelenium: Selenium page object for Pet endpoint operations.
 *
 * Mirrors com.aitesting.petstore.pom.PetEndpointPage with the same CSS
 * selectors. Playwright chains locators (page.locator(a).locator(b));
 * here the same scoping is a CSS descendant selector, via within().
 *
 * Usage:
 *   new PetEndpointPageSelenium(driver)
 *       .expandGetPetById().tryItOut().fillPetId("1").execute();
 */
public class PetEndpointPageSelenium extends SeleniumBasePage {

    private static final Logger log =
        LoggerFactory.getLogger(PetEndpointPageSelenium.class);

    // ── Selectors (same strings as PetEndpointPage) ───────────────────────────

    private static final String GET_PET_BY_ID =
        "#operations-pet-getPetById";
    private static final String GET_PET_BY_ID_EXPANDED =
        "#operations-pet-getPetById.is-open";
    private static final String FIND_BY_STATUS =
        "#operations-pet-findPetsByStatus";
    private static final String FIND_BY_STATUS_EXPANDED =
        "#operations-pet-findPetsByStatus.is-open";
    private static final String TRY_IT_OUT =
        ".try-out__btn";
    private static final String EXECUTE_BTN =
        ".execute";
    // response-col_status appears 3 times on the page (docs responses table,
    // the live table's thead "Code" cell, and the live tbody row). Only the
    // live tbody cell holds the actual result code.
    private static final String RESPONSE_CODE =
        ".live-responses-table tbody .response-col_status";
    private static final String PARAM_INPUT =
        ".parameters input[placeholder]";
    private static final String RESPONSE_BODY =
        ".live-responses-table tbody .response-col_description .microlight";
    // findByStatus renders as <select multiple>, not checkboxes.
    private static final String STATUS_SELECT =
        "select[multiple]";

    public PetEndpointPageSelenium(WebDriver driver) {
        super(driver);
    }

    private static String within(String parent, String child) {
        return parent + " " + child;
    }

    // ── GET /pet/{petId} ──────────────────────────────────────────────────────

    public PetEndpointPageSelenium expandGetPetById() {
        log.debug("Expanding GET /pet/{{petId}}");
        click(GET_PET_BY_ID);
        waitForVisible(GET_PET_BY_ID_EXPANDED);
        return this;
    }

    public PetEndpointPageSelenium tryItOut() {
        click(within(GET_PET_BY_ID_EXPANDED, TRY_IT_OUT));
        return this;
    }

    public PetEndpointPageSelenium fillPetId(String petId) {
        type(within(GET_PET_BY_ID_EXPANDED, PARAM_INPUT), petId);
        return this;
    }

    public PetEndpointPageSelenium execute() {
        click(within(GET_PET_BY_ID_EXPANDED, EXECUTE_BTN));
        waitForVisible(RESPONSE_CODE);
        return this;
    }

    // ── GET /pet/findByStatus ─────────────────────────────────────────────────

    public PetEndpointPageSelenium expandFindByStatus() {
        click(FIND_BY_STATUS);
        waitForVisible(FIND_BY_STATUS_EXPANDED);
        return this;
    }

    public PetEndpointPageSelenium selectAvailableStatus() {
        click(within(FIND_BY_STATUS, TRY_IT_OUT));
        new Select(waitForVisible(within(FIND_BY_STATUS, STATUS_SELECT)))
            .selectByValue("available");
        return this;
    }

    public PetEndpointPageSelenium executeFindByStatus() {
        click(within(FIND_BY_STATUS, EXECUTE_BTN));
        waitForVisible(RESPONSE_CODE);
        return this;
    }

    // ── Response reading ──────────────────────────────────────────────────────

    public String getResponseCode() {
        try {
            String code = getText(RESPONSE_CODE).trim();
            log.debug("Response code: {}", code);
            return code;
        } catch (Exception e) {
            log.warn("Could not read response code");
            return "";
        }
    }

    public String getResponseBody() {
        try {
            return getText(RESPONSE_BODY);
        } catch (Exception e) {
            log.warn("Could not read response body");
            return "";
        }
    }

    public boolean hasResponse() {
        return isVisible(RESPONSE_CODE);
    }
}
