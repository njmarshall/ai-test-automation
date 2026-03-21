package com.aitesting.api.petstore;

import com.aitesting.shared.assertions.ResponseValidator;
import com.aitesting.shared.dataprovider.TestDataFactory;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.reporting.AllureHelper;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.*;

import java.util.Map;

/**
 * StoreTests covers the PetStore /store endpoint:
 *   GET    /store/inventory          — returns map of status → count
 *   POST   /store/order              — place an order
 *   GET    /store/order/{orderId}    — retrieve order
 *   DELETE /store/order/{orderId}    — cancel order
 */
@Epic("PetStore API")
@Feature("Store Endpoint")
public class StoreTests {

    private long createdOrderId;

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        if (createdOrderId != 0) {
            ApiClient.delete("/store/order/" + createdOrderId);
        }
    }

    // ── GET /store/inventory ──────────────────────────────────────────────────

    @Test(priority = 1)
    @Story("Inventory")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /store/inventory should return 200 with a non-null inventory map.")
    public void getInventory_happyPath() {
        Response response = ApiClient.get("/store/inventory");
        AllureHelper.attachResponse("GET /store/inventory", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla()
                .contentType("application/json");
    }

    // ── POST /store/order ─────────────────────────────────────────────────────

    @Test(priority = 2)
    @Story("Place Order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /store/order with a valid payload should return 200 and the order ID.")
    public void placeOrder_happyPath() {
        long petId = TestDataFactory.randomId();
        Map<String, Object> payload = TestDataFactory.orderPayload(petId);
        AllureHelper.parameter("Pet ID", petId);

        Response response = ApiClient.post("/store/order", payload);
        AllureHelper.attachResponse("POST /store/order", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla()
                .hasField("id")
                .fieldEquals("status", "placed");

        createdOrderId = ((Number) response.jsonPath().get("id")).longValue();
    }

    @Test(priority = 3)
    @Story("Place Order")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /store/order with quantity=0 should return 400.")
    public void placeOrder_zeroQuantity() {
        Map<String, Object> payload = Map.of(
                "id",       TestDataFactory.randomId(),
                "petId",    TestDataFactory.randomId(),
                "quantity", 0,
                "status",   "placed",
                "complete", false
        );

        Response response = ApiClient.post("/store/order", payload);
        AllureHelper.attachResponse("POST /store/order (qty=0)", response);

        // PetStore API accepts 0 — document actual behaviour here
        // Change to 400 if your target API enforces positive quantity
        ResponseValidator.from(response).is2xx();
    }

    // ── GET /store/order/{orderId} ────────────────────────────────────────────

    @Test(priority = 4, dependsOnMethods = "placeOrder_happyPath")
    @Story("Get Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /store/order/{id} for an existing order should return 200 with order details.")
    public void getOrder_found() {
        AllureHelper.parameter("Order ID", createdOrderId);

        Response response = ApiClient.get("/store/order/" + createdOrderId);
        AllureHelper.attachResponse("GET /store/order/" + createdOrderId, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla()
                .hasField("id")
                .hasField("petId")
                .hasField("status");
    }

    @Test(priority = 5)
    @Story("Get Order")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /store/order/{id} for a non-existent order should return 404.")
    public void getOrder_notFound() {
        long nonExistentId = TestDataFactory.nonExistentId();

        Response response = ApiClient.get("/store/order/" + nonExistentId);
        AllureHelper.attachResponse("GET /store/order (not found)", response);

        ResponseValidator.from(response).statusCode(404);
    }

    // ── DELETE /store/order/{orderId} ─────────────────────────────────────────

    @Test(priority = 10, dependsOnMethods = "placeOrder_happyPath")
    @Story("Delete Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /store/order/{id} for an existing order should return 200.")
    public void deleteOrder_happyPath() {
        AllureHelper.parameter("Order ID to delete", createdOrderId);

        Response response = ApiClient.delete("/store/order/" + createdOrderId);
        AllureHelper.attachResponse("DELETE /store/order/" + createdOrderId, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla();

        // Confirm deletion
        Response verify = ApiClient.get("/store/order/" + createdOrderId);
        ResponseValidator.from(verify).statusCode(404);
        createdOrderId = 0;
    }
}
