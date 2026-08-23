package com.aitesting.petstore.pom;

import com.aitesting.ui.BasePage;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PetEndpointPage — Page Object for Pet API endpoint interactions.
 *
 * Encapsulates interactions with individual Pet endpoints
 * in the Swagger UI — expanding, filling parameters, executing,
 * and reading responses.
 *
 * Usage:
 *   PetEndpointPage petPage = new PetEndpointPage(page);
 *   petPage.expandGetPetById()
 *          .tryItOut()
 *          .fillPetId("1")
 *          .execute();
 *   String response = petPage.getResponseCode();
 */
public class PetEndpointPage extends BasePage {

    private static final Logger log =
        LoggerFactory.getLogger(PetEndpointPage.class);

    // ── Selectors ─────────────────────────────────────────────────────────────

    // GET /pet/{petId}
    private static final String GET_PET_BY_ID =
        "#operations-pet-getPetById";
    private static final String GET_PET_BY_ID_EXPANDED =
        "#operations-pet-getPetById.is-open";

    // GET /pet/findByStatus
    private static final String FIND_BY_STATUS =
        "#operations-pet-findPetsByStatus";
    private static final String FIND_BY_STATUS_EXPANDED =
        "#operations-pet-findPetsByStatus.is-open";

    // POST /pet
    private static final String POST_PET =
        "#operations-pet-addPet";

    // Try it out button (inside expanded operation)
    private static final String TRY_IT_OUT =
        ".try-out__btn";

    // Execute button
    private static final String EXECUTE_BTN =
        ".execute";

    // Response code — scoped to the LIVE result table's body row. The docs
    // section also has a table with class "responses-table" listing possible
    // codes (200, 404, ...), and the live table's own <thead> repeats the
    // "response-col_status" class on its "Code" header cell — so both the
    // table and the tbody scoping are required to land on exactly one match.
    private static final String RESPONSE_CODE =
        ".live-responses-table tbody .response-col_status";

    // Parameter input
    private static final String PARAM_INPUT =
        ".parameters input[placeholder]";

    // Response body
    private static final String RESPONSE_BODY =
        ".live-responses-table tbody .response-col_description .microlight";

    // findByStatus renders as a multi-select <select>, not checkboxes.
    private static final String STATUS_SELECT =
        "select[multiple]";

    // ── Constructor ───────────────────────────────────────────────────────────

    public PetEndpointPage(Page page) {
        super(page);
    }

    // ── GET /pet/{petId} ──────────────────────────────────────────────────────

    /**
     * Expands the GET /pet/{petId} endpoint section.
     */
    public PetEndpointPage expandGetPetById() {
        log.debug("Expanding GET /pet/{{petId}}");
        scrollIntoView(GET_PET_BY_ID);
        click(GET_PET_BY_ID);
        waitForVisible(GET_PET_BY_ID_EXPANDED);
        return this;
    }

    /**
     * Clicks the Try It Out button to enable parameter input.
     */
    public PetEndpointPage tryItOut() {
        log.debug("Clicking Try it out");
        page.locator(GET_PET_BY_ID_EXPANDED)
            .locator(TRY_IT_OUT).click();
        return this;
    }

    /**
     * Fills in the petId parameter field.
     *
     * @param petId the pet ID to look up
     */
    public PetEndpointPage fillPetId(String petId) {
        log.debug("Filling petId: {}", petId);
        page.locator(GET_PET_BY_ID_EXPANDED)
            .locator(PARAM_INPUT).fill(petId);
        return this;
    }

    /**
     * Clicks the Execute button to send the request.
     */
    public PetEndpointPage execute() {
        log.debug("Clicking Execute");
        page.locator(GET_PET_BY_ID_EXPANDED)
            .locator(EXECUTE_BTN).click();
        waitForVisible(RESPONSE_CODE);
        return this;
    }

    // ── GET /pet/findByStatus ─────────────────────────────────────────────────

    /**
     * Expands the GET /pet/findByStatus endpoint.
     */
    public PetEndpointPage expandFindByStatus() {
        scrollIntoView(FIND_BY_STATUS);
        click(FIND_BY_STATUS);
        waitForVisible(FIND_BY_STATUS_EXPANDED);
        return this;
    }

    /**
     * Selects "available" in the status multi-select.
     */
    public PetEndpointPage selectAvailableStatus() {
        page.locator(FIND_BY_STATUS)
            .locator(TRY_IT_OUT).click();
        page.locator(FIND_BY_STATUS)
            .locator(STATUS_SELECT).waitFor();
        page.locator(FIND_BY_STATUS)
            .locator(STATUS_SELECT).selectOption("available");
        return this;
    }

    /**
     * Executes findByStatus request.
     */
    public PetEndpointPage executeFindByStatus() {
        page.locator(FIND_BY_STATUS)
            .locator(EXECUTE_BTN).click();
        waitForVisible(RESPONSE_CODE);
        return this;
    }

    // ── Response reading ──────────────────────────────────────────────────────

    /**
     * Returns the HTTP response code shown in the UI after Execute.
     * e.g. "200", "404"
     */
    public String getResponseCode() {
        try {
            waitForVisible(RESPONSE_CODE);
            String code = getText(RESPONSE_CODE).trim();
            log.debug("Response code: {}", code);
            return code;
        } catch (Exception e) {
            log.warn("Could not read response code");
            return "";
        }
    }

    /**
     * Returns the response body text shown in the UI.
     */
    public String getResponseBody() {
        try {
            waitForVisible(RESPONSE_BODY);
            return getText(RESPONSE_BODY);
        } catch (Exception e) {
            log.warn("Could not read response body");
            return "";
        }
    }

    /**
     * Returns true if a response is shown in the UI.
     */
    public boolean hasResponse() {
        return isVisible(RESPONSE_CODE);
    }
}
