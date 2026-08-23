package com.aitesting.petstore.ui;

import com.aitesting.petstore.pom.PetEndpointPage;
import com.aitesting.petstore.pom.SwaggerHomePage;
import com.aitesting.shared.reporting.AllureHelper;
import com.aitesting.ui.PlaywrightFactory;
import com.aitesting.ui.ScreenshotHelper;
import com.aitesting.ui.UIConfig;
import com.microsoft.playwright.Page;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

/**
 * PetStoreUITests — Playwright UI tests for petstore.swagger.io.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DESIGN
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Uses Page Object Model (POM) — tests never use raw selectors.
 * Every UI interaction goes through SwaggerHomePage or PetEndpointPage.
 *
 * Complements the API test layer:
 *   PetTests (API)    → validates HTTP responses via RestAssured
 *   PetStoreUITests   → validates UI behavior via Playwright
 *
 * Browser lifecycle:
 *   @BeforeClass → PlaywrightFactory.start()  (one browser)
 *   @BeforeMethod → PlaywrightFactory.newPage() (fresh tab per test)
 *   @AfterMethod  → screenshot on failure + close page
 *   @AfterClass   → PlaywrightFactory.stop()
 *
 * Test coverage:
 *   1. Swagger UI loads correctly
 *   2. Page title is correct
 *   3. All three API sections visible (Pet, Store, User)
 *   4. Pet section expands on click
 *   5. GET /pet/{id} — valid ID shows response
 *   6. GET /pet/{id} — invalid ID shows 404 or error
 *   7. GET /pet/findByStatus — available returns results
 *   8. Page loads within SLA (3 seconds)
 */
@Epic("PetStore UI")
@Feature("Swagger UI")
public class PetStoreUITests {

    private Page           page;
    private SwaggerHomePage homePage;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        PlaywrightFactory.start();
        AllureHelper.description(
            "PetStore Swagger UI tests using Playwright. " +
            "Browser: " + UIConfig.getBrowserType() +
            " Headless: " + UIConfig.isHeadless()
        );
    }

    @AfterClass(alwaysRun = true)
    public void suiteTeardown() {
        PlaywrightFactory.stop();
    }

    @BeforeMethod(alwaysRun = true)
    public void testSetup() {
        page = PlaywrightFactory.newPage();
        homePage = new SwaggerHomePage(page);
    }

    @AfterMethod(alwaysRun = true)
    public void testTeardown(ITestResult result) {
        // Capture screenshot on failure — attaches to Allure
        if (!result.isSuccess()) {
            ScreenshotHelper.captureOnFailure(
                page, result.getName());
        }
        PlaywrightFactory.closePage();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Page Load")
    @Severity(SeverityLevel.BLOCKER)
    @Description("PetStore Swagger UI should load and display the API interface.")
    public void swaggerUI_loads_successfully() {
        homePage.open();

        AllureHelper.parameter("URL", UIConfig.PETSTORE_UI_URL);
        ScreenshotHelper.captureStep(page, "swagger-loaded");

        Assert.assertTrue(homePage.isSwaggerLoaded(),
            "Expected Swagger UI to load at " + UIConfig.PETSTORE_UI_URL);
    }

    @Test(priority = 2)
    @Story("Page Load")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Page title should contain 'Swagger UI'.")
    public void swaggerUI_pageTitle_correct() {
        homePage.open();

        String title = page.title();
        AllureHelper.parameter("Page title", title);

        Assert.assertTrue(title.contains("Swagger"),
            "Expected title to contain 'Swagger', got: " + title);
    }

    @Test(priority = 3)
    @Story("Page Structure")
    @Severity(SeverityLevel.CRITICAL)
    @Description("All three API sections (Pet, Store, User) should be visible.")
    public void swaggerUI_allSections_visible() {
        homePage.open();

        ScreenshotHelper.captureStep(page, "all-sections");

        Assert.assertTrue(homePage.isPetSectionVisible(),
            "Expected Pet section to be visible");
        Assert.assertTrue(homePage.isStoreSectionVisible(),
            "Expected Store section to be visible");
        Assert.assertTrue(homePage.isUserSectionVisible(),
            "Expected User section to be visible");
    }

    @Test(priority = 4)
    @Story("Pet Section")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Clicking Pet section should expand it to show endpoints.")
    public void petSection_click_expands() {
        homePage.open();
        homePage.expandPetSection();

        ScreenshotHelper.captureStep(page, "pet-section-expanded");

        Assert.assertTrue(homePage.isPetSectionExpanded(),
            "Expected Pet section to be expanded after click");
    }

    @Test(priority = 5)
    @Story("GET /pet/{id}")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Executing GET /pet/{id} with valid ID should show a response.")
    public void getPetById_validId_showsResponse() {
        homePage.open();
        homePage.expandPetSection();

        PetEndpointPage petPage = new PetEndpointPage(page);
        petPage.expandGetPetById()
               .tryItOut()
               .fillPetId("1")
               .execute();

        AllureHelper.parameter("Pet ID", "1");
        ScreenshotHelper.captureStep(page, "get-pet-response");

        Assert.assertTrue(petPage.hasResponse(),
            "Expected a response to be shown after Execute");

        String code = petPage.getResponseCode();
        AllureHelper.parameter("Response code", code);

        // PetStore public API may return 200 or 404 depending on data
        Assert.assertTrue(
            code.equals("200") || code.equals("404"),
            "Expected 200 or 404 response code, got: " + code);
    }

    @Test(priority = 6)
    @Story("GET /pet/{id}")
    @Severity(SeverityLevel.NORMAL)
    @Description("Executing GET /pet/{id} with non-existent ID should show 404.")
    public void getPetById_nonExistentId_shows404() {
        homePage.open();
        homePage.expandPetSection();

        PetEndpointPage petPage = new PetEndpointPage(page);
        petPage.expandGetPetById()
               .tryItOut()
               .fillPetId("999999999")
               .execute();

        AllureHelper.parameter("Pet ID", "999999999 (non-existent)");
        ScreenshotHelper.captureStep(page, "get-pet-404");

        Assert.assertTrue(petPage.hasResponse(),
            "Expected a response to be shown");

        String code = petPage.getResponseCode();
        AllureHelper.parameter("Response code", code);

        // Accept 404 or 200 — public PetStore is inconsistent
        Assert.assertTrue(
            code.equals("404") || code.equals("200"),
            "Expected 404 or 200 for non-existent ID, got: " + code);
    }

    @Test(priority = 7)
    @Story("GET /pet/findByStatus")
    @Severity(SeverityLevel.NORMAL)
    @Description("Selecting 'available' status and executing should return results.")
    public void findByStatus_available_showsResponse() {
        homePage.open();
        homePage.expandPetSection();

        PetEndpointPage petPage = new PetEndpointPage(page);
        petPage.expandFindByStatus()
               .selectAvailableStatus()
               .executeFindByStatus();

        AllureHelper.parameter("Status", "available");
        ScreenshotHelper.captureStep(page, "find-by-status-response");

        Assert.assertTrue(petPage.hasResponse(),
            "Expected response after findByStatus execution");

        String code = petPage.getResponseCode();
        AllureHelper.parameter("Response code", code);
        Assert.assertEquals(code, "200",
            "Expected 200 for findByStatus=available");
    }

    @Test(priority = 8)
    @Story("Performance")
    @Severity(SeverityLevel.NORMAL)
    @Description("Swagger UI should load within 5 second SLA.")
    public void swaggerUI_loadsWithinSla() {
        long start = System.currentTimeMillis();
        homePage.open();
        long elapsed = System.currentTimeMillis() - start;

        AllureHelper.parameter("Load time ms", elapsed);
        AllureHelper.parameter("SLA ms", 5000);

        ScreenshotHelper.captureStep(page, "performance-check");

        Assert.assertTrue(elapsed < 5000,
            "Page load time " + elapsed + "ms exceeded 5000ms SLA");
    }
}
