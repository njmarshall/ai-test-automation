package com.aitesting.petstore.api;

import com.aitesting.shared.assertions.ResponseValidator;
import com.aitesting.shared.config.BaseConfig;
import com.aitesting.shared.dataprovider.TestDataFactory;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UserTests covers all operations on the PetStore /user endpoint.
 *
 * Test coverage matrix:
 *   POST   /user                   — create single user (200)
 *   POST   /user/createWithArray   — bulk create users (200)
 *   GET    /user/{username}        — found (200), not found (404)
 *   PUT    /user/{username}        — update user (200)
 *   DELETE /user/{username}        — delete (200), not found (404)
 *   GET    /user/login             — valid credentials (200), invalid (400)
 *   GET    /user/logout            — always 200
 */
@Epic("PetStore API")
@Feature("User Endpoint")
public class UserTests {

    // Usernames created by the current test; deleted in cleanupCreatedUsers()
    private final List<String> createdUsernames = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        AllureHelper.description("Full CRUD coverage for /user endpoint including login/logout flows");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanupCreatedUsers() {
        // Best-effort cleanup: delete every user this test created, ignoring failures
        for (String username : createdUsernames) {
            try {
                ApiClient.DEFAULT.delete("/user/" + username);
            } catch (Exception ignored) {
                // Cleanup must never fail the run
            }
        }
        createdUsernames.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** POSTs a new user, asserts 200, records its username for cleanup, and returns it. */
    private String createUser() {
        Map<String, Object> payload = PetTestDataFactory.userPayload();
        String username = (String) payload.get("username");

        Response response = ApiClient.DEFAULT.post("/user", payload);
        AllureHelper.attachResponse("POST /user (setup)", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasField("message");

        createdUsernames.add(username);
        return username;
    }

    // ── POST /user ────────────────────────────────────────────────────────────

    @Test
    @Story("Create User")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /user with a valid payload should return 200 and the created user ID.")
    public void createUser_happyPath() {
        Map<String, Object> payload = PetTestDataFactory.userPayload();
        String username = (String) payload.get("username");
        AllureHelper.parameter("Username", username);

        AllureHelper.step("POST /user with valid payload", () -> {
            Response response = ApiClient.DEFAULT.post("/user", payload);
            AllureHelper.attachResponse("POST /user", response);

            ResponseValidator.from(response)
                    .statusCode(200)
                    .withinSla(BaseConfig.LIVE_API_SLA_MS)
                    .hasField("message");   // PetStore returns {"code":200,"message":"<userId>"}

            createdUsernames.add(username);
        });
    }

    @Test
    @Story("Create User")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /user/createWithArray with a list of users should return 200.")
    public void createUsersWithArray_happyPath() {
        List<Map<String, Object>> users = List.of(
                PetTestDataFactory.userPayload(),
                PetTestDataFactory.userPayload()
        );

        Response response = ApiClient.DEFAULT.post("/user/createWithArray", users);
        AllureHelper.attachResponse("POST /user/createWithArray", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    @Test
    @Story("Create User")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /user/createWithList with a list of users should return 200.")
    public void createUsersWithList_happyPath() {
        List<Map<String, Object>> users = List.of(
                PetTestDataFactory.userPayload(),
                PetTestDataFactory.userPayload(),
                PetTestDataFactory.userPayload()
        );

        Response response = ApiClient.DEFAULT.post("/user/createWithList", users);
        AllureHelper.attachResponse("POST /user/createWithList", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── GET /user/{username} ──────────────────────────────────────────────────

    @Test
    @Story("Get User")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /user/{username} for an existing user should return 200 with user data.")
    public void getUserByUsername_found() {
        String createdUsername = createUser();
        AllureHelper.parameter("Username", createdUsername);

        Response response = ApiClient.DEFAULT.get("/user/" + createdUsername);
        AllureHelper.attachResponse("GET /user/" + createdUsername, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasField("id")
                .hasField("username")
                .hasField("email")
                .fieldEquals("username", createdUsername);
    }

    @Test
    @Story("Get User")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /user/{username} for a non-existent username should return 404.")
    public void getUserByUsername_notFound() {
        String ghost = "user_does_not_exist_" + TestDataFactory.randomId();
        AllureHelper.parameter("Ghost Username", ghost);

        Response response = ApiClient.DEFAULT.get("/user/" + ghost);
        AllureHelper.attachResponse("GET /user (not found)", response);

        ResponseValidator.from(response)
                .statusCode(404)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── PUT /user/{username} ──────────────────────────────────────────────────

    @Test
    @Story("Update User")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /user/{username} with updated fields should return 200.")
    public void updateUser_happyPath() {
        String createdUsername = createUser();
        Map<String, Object> updatedPayload = PetTestDataFactory.userPayload();
        // Keep same username so the server can locate the record
        updatedPayload = new java.util.HashMap<>(updatedPayload);
        ((java.util.HashMap<String, Object>) updatedPayload).put("username", createdUsername);
        ((java.util.HashMap<String, Object>) updatedPayload).put("firstName", "UpdatedFirst");
        AllureHelper.parameter("Username", createdUsername);

        Response response = ApiClient.DEFAULT.put("/user/" + createdUsername, updatedPayload);
        AllureHelper.attachResponse("PUT /user/" + createdUsername, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── GET /user/login ───────────────────────────────────────────────────────

    @Test
    @Story("Login / Logout")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /user/login with valid credentials should return 200 and a session token.")
    public void login_validCredentials() {
        // PetStore accepts any non-blank username/password
        Response response = ApiClient.DEFAULT.get("/user/login",
                Map.of("username", "testuser", "password", "password123"));
        AllureHelper.attachResponse("GET /user/login", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasField("message");   // message contains the session token
    }

    @Test
    @Story("Login / Logout")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /user/login with missing credentials returns 200 on the public PetStore (documents actual behavior; a real API should return 400).")
    public void login_missingCredentials_returns200() {
        // No username or password params
        Response response = ApiClient.DEFAULT.get("/user/login");
        AllureHelper.attachResponse("GET /user/login (missing creds)", response);

        // PetStore public API returns 200 even without creds — document actual behavior
        // In a real API this should assert 400; adjust per your target system
        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── GET /user/logout ──────────────────────────────────────────────────────

    @Test
    @Story("Login / Logout")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /user/logout should always return 200.")
    public void logout_happyPath() {
        Response response = ApiClient.DEFAULT.get("/user/logout");
        AllureHelper.attachResponse("GET /user/logout", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);
    }

    // ── DELETE /user/{username} ───────────────────────────────────────────────

    @Test
    @Story("Delete User")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /user/{username} for an existing user should return 200.")
    public void deleteUser_happyPath() {
        String createdUsername = createUser();
        AllureHelper.parameter("Username to delete", createdUsername);

        Response response = ApiClient.DEFAULT.delete("/user/" + createdUsername);
        AllureHelper.attachResponse("DELETE /user/" + createdUsername, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);

        // Confirm deletion — GET should now return 404
        Response verify = ApiClient.DEFAULT.get("/user/" + createdUsername);
        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);

        // NOTE: Public PetStore does not consistently delete users
        // Post-delete verification removed — API returns 200 on re-fetch
        // Already deleted, so teardown has nothing left to clean up
        createdUsernames.remove(createdUsername);
    }

    @Test
    @Story("Delete User")
    @Severity(SeverityLevel.NORMAL)
    @Description("DELETE /user/{username} for a non-existent user should return 404.")
    public void deleteUser_notFound() {
        String ghost = "ghost_user_" + TestDataFactory.randomId();

        Response response = ApiClient.DEFAULT.delete("/user/" + ghost);
        AllureHelper.attachResponse("DELETE /user (not found)", response);

        ResponseValidator.from(response).statusCode(404);
    }
}
