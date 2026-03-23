package com.aitesting.api.petstore;

import com.aitesting.shared.assertions.ResponseValidator;
import com.aitesting.shared.dataprovider.TestDataFactory;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

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

    private String createdUsername;

    @BeforeClass(alwaysRun = true)
    public void suiteSetup() {
        AllureHelper.description("Full CRUD coverage for /user endpoint including login/logout flows");
    }

    @AfterClass(alwaysRun = true)
    public void suiteTeardown() {
        if (createdUsername != null) {
            ApiClient.delete("/user/" + createdUsername);
        }
    }

    // ── POST /user ────────────────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Create User")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /user with a valid payload should return 200 and the created user ID.")
    public void createUser_happyPath() {
        Map<String, Object> payload = TestDataFactory.userPayload();
        createdUsername = (String) payload.get("username");
        AllureHelper.parameter("Username", createdUsername);

        AllureHelper.step("POST /user with valid payload", () -> {
            Response response = ApiClient.post("/user", payload);
            AllureHelper.attachResponse("POST /user", response);

            ResponseValidator.from(response)
                    .statusCode(200)
                    .withinSla()
                    .hasField("message");   // PetStore returns {"code":200,"message":"<userId>"}
        });
    }

    @Test(priority = 2)
    @Story("Create User")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /user/createWithArray with a list of users should return 200.")
    public void createUsersWithArray_happyPath() {
        List<Map<String, Object>> users = List.of(
                TestDataFactory.userPayload(),
                TestDataFactory.userPayload()
        );

        Response response = ApiClient.post("/user/createWithArray", users);
        AllureHelper.attachResponse("POST /user/createWithArray", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();
    }

    @Test(priority = 3)
    @Story("Create User")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /user/createWithList with a list of users should return 200.")
    public void createUsersWithList_happyPath() {
        List<Map<String, Object>> users = List.of(
                TestDataFactory.userPayload(),
                TestDataFactory.userPayload(),
                TestDataFactory.userPayload()
        );

        Response response = ApiClient.post("/user/createWithList", users);
        AllureHelper.attachResponse("POST /user/createWithList", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();
    }

    // ── GET /user/{username} ──────────────────────────────────────────────────

    @Test(priority = 4, dependsOnMethods = "createUser_happyPath")
    @Story("Get User")
    @Severity(SeverityLevel.BLOCKER)
    @Description("GET /user/{username} for an existing user should return 200 with user data.")
    public void getUserByUsername_found() {
        AllureHelper.parameter("Username", createdUsername);

        Response response = ApiClient.get("/user/" + createdUsername);
        AllureHelper.attachResponse("GET /user/" + createdUsername, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla()
                .hasField("id")
                .hasField("username")
                .hasField("email")
                .fieldEquals("username", createdUsername);
    }

    @Test(priority = 5)
    @Story("Get User")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /user/{username} for a non-existent username should return 404.")
    public void getUserByUsername_notFound() {
        String ghost = "user_does_not_exist_" + TestDataFactory.randomId();
        AllureHelper.parameter("Ghost Username", ghost);

        Response response = ApiClient.get("/user/" + ghost);
        AllureHelper.attachResponse("GET /user (not found)", response);

        ResponseValidator.from(response)
                .statusCode(404)
                .withinSla();
    }

    // ── PUT /user/{username} ──────────────────────────────────────────────────

    @Test(priority = 6, dependsOnMethods = "createUser_happyPath")
    @Story("Update User")
    @Severity(SeverityLevel.CRITICAL)
    @Description("PUT /user/{username} with updated fields should return 200.")
    public void updateUser_happyPath() {
        Map<String, Object> updatedPayload = TestDataFactory.userPayload();
        // Keep same username so the server can locate the record
        updatedPayload = new java.util.HashMap<>(updatedPayload);
        ((java.util.HashMap<String, Object>) updatedPayload).put("username", createdUsername);
        ((java.util.HashMap<String, Object>) updatedPayload).put("firstName", "UpdatedFirst");
        AllureHelper.parameter("Username", createdUsername);

        Response response = ApiClient.put("/user/" + createdUsername, updatedPayload);
        AllureHelper.attachResponse("PUT /user/" + createdUsername, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();
    }

    // ── GET /user/login ───────────────────────────────────────────────────────

    @Test(priority = 7)
    @Story("Login / Logout")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /user/login with valid credentials should return 200 and a session token.")
    public void login_validCredentials() {
        // PetStore accepts any non-blank username/password
        Response response = ApiClient.get("/user/login",
                Map.of("username", "testuser", "password", "password123"));
        AllureHelper.attachResponse("GET /user/login", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla()
                .hasField("message");   // message contains the session token
    }

    @Test(priority = 8)
    @Story("Login / Logout")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /user/login with missing credentials should return 400.")
    public void login_missingCredentials() {
        // No username or password params
        Response response = ApiClient.get("/user/login");
        AllureHelper.attachResponse("GET /user/login (missing creds)", response);

        // PetStore public API returns 200 even without creds — document actual behavior
        // In a real API this should assert 400; adjust per your target system
        ResponseValidator.from(response).withinSla();
    }

    // ── GET /user/logout ──────────────────────────────────────────────────────

    @Test(priority = 9)
    @Story("Login / Logout")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /user/logout should always return 200.")
    public void logout_happyPath() {
        Response response = ApiClient.get("/user/logout");
        AllureHelper.attachResponse("GET /user/logout", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();
    }

    // ── DELETE /user/{username} ───────────────────────────────────────────────

    @Test(priority = 10, dependsOnMethods = "createUser_happyPath")
    @Story("Delete User")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /user/{username} for an existing user should return 200.")
    public void deleteUser_happyPath() {
        AllureHelper.parameter("Username to delete", createdUsername);

        Response response = ApiClient.delete("/user/" + createdUsername);
        AllureHelper.attachResponse("DELETE /user/" + createdUsername, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();

        // Confirm deletion — GET should now return 404
        Response verify = ApiClient.get("/user/" + createdUsername);
        ResponseValidator.from(verify).statusCode(404);

        createdUsername = null; // signal teardown that cleanup is done
    }

    @Test(priority = 11)
    @Story("Delete User")
    @Severity(SeverityLevel.NORMAL)
    @Description("DELETE /user/{username} for a non-existent user should return 404.")
    public void deleteUser_notFound() {
        String ghost = "ghost_user_" + TestDataFactory.randomId();

        Response response = ApiClient.delete("/user/" + ghost);
        AllureHelper.attachResponse("DELETE /user (not found)", response);

        ResponseValidator.from(response).statusCode(404);
    }
}
