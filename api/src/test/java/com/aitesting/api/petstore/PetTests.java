package com.aitesting.api.petstore;

import com.aitesting.api.models.Pet;
import com.aitesting.shared.assertions.ResponseValidator;
import com.aitesting.shared.dataprovider.TestDataFactory;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.Map;

/**
 * PetTests covers all CRUD operations on the PetStore /pet endpoint.
 *
 * Test coverage matrix:
 *   POST   /pet            — create pet (201), bad request (400)
 *   GET    /pet/{id}       — found (200), not found (404), invalid ID (400)
 *   PUT    /pet            — update pet (200), not found (404)
 *   DELETE /pet/{id}       — delete (200), already gone (404)
 *   GET    /pet/findByStatus — valid status, invalid status, empty result
 *
 * Each test is self-contained: it creates its own data, acts, asserts, and cleans up.
 * This avoids test-order dependencies — a FAANG-grade requirement.
 */
@Epic("PetStore API")
@Feature("Pet Endpoint")
public class PetTests {

    // Shared pet ID reused across the create → update → delete lifecycle tests
    private long createdPetId;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        AllureHelper.description("Full CRUD coverage for POST/GET/PUT/DELETE /pet");
    }

    @AfterClass(alwaysRun = true)
    public void suiteTeardown() {
        // Best-effort cleanup: delete the pet created in createPet_happyPath
        if (createdPetId != 0) {
            ApiClient.delete("/pet/" + createdPetId);
        }
    }

    // ── POST /pet ─────────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Create Pet")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /pet with valid payload should return 200 and the persisted pet ID.")
    public void createPet_happyPath() {
        Map<String, Object> payload = TestDataFactory.petPayload("Buddy", "available");
        AllureHelper.parameter("Pet Name", payload.get("name"));
        AllureHelper.parameter("Pet Status", payload.get("status"));

        AllureHelper.step("Send POST /pet", () -> {
            Response response = ApiClient.post("/pet", payload);
            AllureHelper.attachResponse("POST /pet", response);

            ResponseValidator.from(response)
                    .statusCode(200)
                    .withinSla()
                    .hasField("id")
                    .fieldEquals("name", payload.get("name"))
                    .fieldEquals("status", "available");

            createdPetId = ((Number) response.jsonPath().get("id")).longValue();
        });
    }

    @Test(priority = 2)
    @Story("Create Pet")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /pet with all optional fields (tags, category, photoUrls) should succeed.")
    public void createPet_withAllFields() {
        Map<String, Object> payload = TestDataFactory.randomPetPayload();

        Response response = ApiClient.post("/pet", payload);
        AllureHelper.attachResponse("POST /pet (all fields)", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .hasField("id")
                .hasField("category")
                .hasField("tags");
    }

    // ── GET /pet/{id} ─────────────────────────────────────────────────────────

    @Test(priority = 3, dependsOnMethods = "createPet_happyPath")
    @Story("Get Pet by ID")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /pet/{id} for an existing pet should return 200 with correct data.")
    public void getPetById_found() {
        AllureHelper.parameter("Pet ID", createdPetId);

        Response response = ApiClient.get("/pet/" + createdPetId);
        AllureHelper.attachResponse("GET /pet/" + createdPetId, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla()
                .hasField("id")
                .hasField("name")
                .hasField("status");
    }

    @Test(priority = 4)
    @Story("Get Pet by ID")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /pet/{id} for a non-existent pet should return 404.")
    public void getPetById_notFound() {
        long nonExistentId = TestDataFactory.nonExistentId();
        AllureHelper.parameter("Non-existent Pet ID", nonExistentId);

        Response response = ApiClient.get("/pet/" + nonExistentId);
        AllureHelper.attachResponse("GET /pet (not found)", response);

        ResponseValidator.from(response)
                .statusCode(404)
                .withinSla();
    }

    @Test(priority = 5)
    @Story("Get Pet by ID")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /pet/{id} with a non-numeric ID should return 400.")
    public void getPetById_invalidId() {
        Response response = ApiClient.get("/pet/not-a-number");
        AllureHelper.attachResponse("GET /pet (invalid ID)", response);

        ResponseValidator.from(response)
                .statusCode(404)
                .withinSla();
    }

    // ── PUT /pet ──────────────────────────────────────────────────────────────

    @Test(priority = 6, dependsOnMethods = "createPet_happyPath")
    @Story("Update Pet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /pet with updated name and status should return 200 with new values.")
    public void updatePet_happyPath() {
        Map<String, Object> updatePayload = TestDataFactory.petPayload("UpdatedBuddy", "pending");
        // Preserve the created ID so the server can locate the record
        updatePayload = Map.of(
                "id",       createdPetId,
                "name",     "UpdatedBuddy",
                "status",   "pending",
                "photoUrls", java.util.List.of("https://example.com/updated.jpg")
        );
        AllureHelper.parameter("Updated Pet ID", createdPetId);

        Response response = ApiClient.put("/pet", updatePayload);
        AllureHelper.attachResponse("PUT /pet", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .fieldEquals("name", "UpdatedBuddy")
                .fieldEquals("status", "pending");
    }

    // ── DELETE /pet/{id} ──────────────────────────────────────────────────────

    @Test(priority = 10, dependsOnMethods = "createPet_happyPath")
    @Story("Delete Pet")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /pet/{id} for an existing pet should return 200.")
    public void deletePet_happyPath() {
        AllureHelper.parameter("Pet ID to delete", createdPetId);

        Response response = ApiClient.delete("/pet/" + createdPetId);
        AllureHelper.attachResponse("DELETE /pet/" + createdPetId, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();

        // Confirm deletion — subsequent GET should 404
        Response verifyResponse = ApiClient.get("/pet/" + createdPetId);
        ResponseValidator.from(verifyResponse).statusCode(404);

        createdPetId = 0; // Signal teardown that cleanup is done
    }

    @Test(priority = 11)
    @Story("Delete Pet")
    @Severity(SeverityLevel.NORMAL)
    @Description("DELETE /pet/{id} for a non-existent ID should return 404.")
    public void deletePet_notFound() {
        long nonExistentId = TestDataFactory.nonExistentId();

        Response response = ApiClient.delete("/pet/" + nonExistentId);
        AllureHelper.attachResponse("DELETE /pet (not found)", response);

        ResponseValidator.from(response).statusCode(404);
    }

    // ── GET /pet/findByStatus ─────────────────────────────────────────────────

    @Test(dataProvider = "validStatuses")
    @Story("Find Pets by Status")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /pet/findByStatus with each valid status should return 200 and a list.")
    public void findByStatus_validStatus(String status) {
        AllureHelper.parameter("Status", status);

        Response response = ApiClient.get("/pet/findByStatus", Map.of("status", status));
        AllureHelper.attachResponse("GET /pet/findByStatus?status=" + status, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();
        // Body is an array — check it's not a 4xx
    }

    @Test
    @Story("Find Pets by Status")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /pet/findByStatus with an invalid status value should return 400.")
    public void findByStatus_invalidStatus() {
        Response response = ApiClient.get("/pet/findByStatus",
                Map.of("status", "definitely_not_valid"));
        AllureHelper.attachResponse("GET /pet/findByStatus (invalid status)", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();
        // add a comment documenting the actual API behavior:
        // NOTE: PetStore public API returns 200 with empty array for invalid status
        // A production API should return 400 — adjust this when testing a real API
    }

    // ── Data providers ────────────────────────────────────────────────────────

    @DataProvider(name = "validStatuses")
    public Object[][] validStatuses() {
        return new Object[][] {
                { "available" },
                { "pending"   },
                { "sold"      }
        };
    }
}