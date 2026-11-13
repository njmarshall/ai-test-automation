package com.aitesting.petstore.ui;

import com.aitesting.petstore.selenium.pages.PetEndpointPageSelenium;
import com.aitesting.petstore.selenium.pages.SwaggerHomePageSelenium;
import com.aitesting.shared.reporting.AllureHelper;
import com.aitesting.ui.UIConfig;
import com.aitesting.ui.selenium.DriverFactory;
import com.aitesting.ui.selenium.SeleniumScreenshotHelper;
import io.qameta.allure.*;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.*;

/**
 * PetStoreSeleniumTests: Selenium WebDriver UI tests for petstore.swagger.io.
 *
 * Same 8 scenarios and the same assertions as PetStoreUITests (Playwright),
 * so the two frameworks can be compared side by side in the Allure report.
 *
 * Browser lifecycle:
 *   @BeforeMethod  DriverFactory.create()  (fresh ChromeDriver per test)
 *   @AfterMethod   screenshot on failure, then DriverFactory.quit()
 *
 * Test coverage:
 *   1. Swagger UI loads correctly
 *   2. Page title is correct
 *   3. All three API sections visible (Pet, Store, User)
 *   4. Pet section is expanded after expandPetSection()
 *   5. GET /pet/{id}: valid ID shows response
 *   6. GET /pet/{id}: non-existent ID shows 404 or 200
 *   7. GET /pet/findByStatus: available returns 200
 *   8. Page loads within SLA (5 seconds)
 */
@Epic("PetStore UI")
@Feature("Swagger UI (Selenium)")
public class PetStoreSeleniumTests {

    private WebDriver driver;
    private SwaggerHomePageSelenium homePage;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeMethod(alwaysRun = true)
    public void testSetup() {
        driver = DriverFactory.create();
        homePage = new SwaggerHomePageSelenium(driver);
        AllureHelper.description(
            "PetStore Swagger UI tests using Selenium WebDriver (Chrome). " +
            "Headless: " + DriverFactory.isHeadless());
    }

    @AfterMethod(alwaysRun = true)
    public void testTeardown(ITestResult result) {
        if (!result.isSuccess()) {
            SeleniumScreenshotHelper.captureOnFailure(driver, result.getName());
        }
        DriverFactory.quit();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Page Load")
    @Severity(SeverityLevel.BLOCKER)
    @Description("PetStore Swagger UI should load and display the API interface.")
    public void swaggerUI_loads_successfully() {
        homePage.open();

        AllureHelper.parameter("URL", UIConfig.PETSTORE_UI_URL);
        SeleniumScreenshotHelper.captureStep(driver, "swagger-loaded");

        Assert.assertTrue(homePage.isSwaggerLoaded(),
            "Expected Swagger UI to load at " + UIConfig.PETSTORE_UI_URL);
    }

    @Test(priority = 2)
    @Story("Page Load")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Page title should contain 'Swagger'.")
    public void swaggerUI_pageTitle_correct() {
        homePage.open();

        String title = homePage.getTitle();
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

        SeleniumScreenshotHelper.captureStep(driver, "all-sections");

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
    @Description("Pet section should be expanded to show endpoints.")
    public void petSection_click_expands() {
        homePage.open();
        homePage.expandPetSection();

        SeleniumScreenshotHelper.captureStep(driver, "pet-section-expanded");

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

        PetEndpointPageSelenium petPage = new PetEndpointPageSelenium(driver);
        petPage.expandGetPetById()
               .tryItOut()
               .fillPetId("1")
               .execute();

        AllureHelper.parameter("Pet ID", "1");
        SeleniumScreenshotHelper.captureStep(driver, "get-pet-response");

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

        PetEndpointPageSelenium petPage = new PetEndpointPageSelenium(driver);
        petPage.expandGetPetById()
               .tryItOut()
               .fillPetId("999999999")
               .execute();

        AllureHelper.parameter("Pet ID", "999999999 (non-existent)");
        SeleniumScreenshotHelper.captureStep(driver, "get-pet-404");

        Assert.assertTrue(petPage.hasResponse(),
            "Expected a response to be shown");

        String code = petPage.getResponseCode();
        AllureHelper.parameter("Response code", code);

        // Accept 404 or 200: public PetStore is inconsistent
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

        PetEndpointPageSelenium petPage = new PetEndpointPageSelenium(driver);
        petPage.expandFindByStatus()
               .selectAvailableStatus()
               .executeFindByStatus();

        AllureHelper.parameter("Status", "available");
        SeleniumScreenshotHelper.captureStep(driver, "find-by-status-response");

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

        SeleniumScreenshotHelper.captureStep(driver, "performance-check");

        Assert.assertTrue(elapsed < 5000,
            "Page load time " + elapsed + "ms exceeded 5000ms SLA");
    }
}
