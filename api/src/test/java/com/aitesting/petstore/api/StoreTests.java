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
 * StoreTests covers the PetStore /store endpoint:
 *   GET    /store/inventory          — returns map of status → count
 *   POST   /store/order              — place an order
 *   GET    /store/order/{orderId}    — retrieve order
 *   DELETE /store/order/{orderId}    — cancel order
 */
@Epic("PetStore API")
@Feature("Store Endpoint")
public class StoreTests {

    // Ids of orders placed by the current test; deleted in cleanupCreatedOrders()
    private final List<Long> createdOrderIds = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @AfterMethod(alwaysRun = true)
    public void cleanupCreatedOrders() {
        // Best-effort cleanup: delete every order this test placed, ignoring failures
        for (Long id : createdOrderIds) {
            try {
                ApiClient.DEFAULT.delete("/store/order/" + id);
            } catch (Exception ignored) {
                // Cleanup must never fail the run
            }
        }
        createdOrderIds.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** POSTs a new order, asserts 200 and status "placed", records its id for cleanup, and returns it. */
    private long placeOrder() {
        long petId = TestDataFactory.randomId();
        Map<String, Object> payload = PetTestDataFactory.orderPayload(petId);

        Response response = ApiClient.DEFAULT.post("/store/order", payload);
        AllureHelper.attachResponse("POST /store/order (setup)", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasField("id")
                .fieldEquals("status", "placed");

        long id = ((Number) response.jsonPath().get("id")).longValue();
        createdOrderIds.add(id);
        return id;
    }

    // ── GET /store/inventory ──────────────────────────────────────────────────

    @Test
    @Story("Inventory")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /store/inventory should return 200 with a non-null inventory map.")
    public void getInventory_happyPath() {
        Response response = ApiClient.DEFAULT.get("/store/inventory");
        AllureHelper.attachResponse("GET /store/inventory", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .contentType("application/json");
    }

    // ── POST /store/order ─────────────────────────────────────────────────────

    @Test
    @Story("Place Order")
    @Severity(SeverityLevel.BLOCKER)
    @Description("POST /store/order with a valid payload should return 200 and the order ID.")
    public void placeOrder_happyPath() {
        long petId = TestDataFactory.randomId();
        Map<String, Object> payload = PetTestDataFactory.orderPayload(petId);
        AllureHelper.parameter("Pet ID", petId);

        Response response = ApiClient.DEFAULT.post("/store/order", payload);
        AllureHelper.attachResponse("POST /store/order", response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasField("id")
                .fieldEquals("status", "placed");

        createdOrderIds.add(((Number) response.jsonPath().get("id")).longValue());
    }

    @Test
    @Story("Place Order")
    @Severity(SeverityLevel.NORMAL)
    @Description("POST /store/order with quantity=0 is accepted (2xx) by the public PetStore (documents actual behavior).")
    public void placeOrder_zeroQuantity_isAccepted() {
        Map<String, Object> payload = Map.of(
                "id",       TestDataFactory.randomId(),
                "petId",    TestDataFactory.randomId(),
                "quantity", 0,
                "status",   "placed",
                "complete", false
        );

        Response response = ApiClient.DEFAULT.post("/store/order", payload);
        AllureHelper.attachResponse("POST /store/order (qty=0)", response);

        // PetStore API accepts 0 — document actual behaviour here
        // Change to 400 if your target API enforces positive quantity
        ResponseValidator.from(response).is2xx();
    }

    // ── GET /store/order/{orderId} ────────────────────────────────────────────

    @Test
    @Story("Get Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("GET /store/order/{id} for an existing order should return 200 with order details.")
    public void getOrder_found() {
        long createdOrderId = placeOrder();
        AllureHelper.parameter("Order ID", createdOrderId);

        Response response = ApiClient.DEFAULT.get("/store/order/" + createdOrderId);
        AllureHelper.attachResponse("GET /store/order/" + createdOrderId, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS)
                .hasField("id")
                .hasField("petId")
                .hasField("status");
    }

    @Test
    @Story("Get Order")
    @Severity(SeverityLevel.NORMAL)
    @Description("GET /store/order/{id} for an order that was just deleted should return 404.")
    public void getOrder_notFound() {
        // A fixed "non-existent" id can be created by anyone on the public PetStore,
        // so place an order and delete it to get an id that is known to be gone.
        long deletedOrderId = placeOrder();

        Response deleteResponse = ApiClient.DEFAULT.delete("/store/order/" + deletedOrderId);
        AllureHelper.attachResponse("DELETE /store/order/" + deletedOrderId + " (setup)", deleteResponse);
        ResponseValidator.from(deleteResponse).statusCode(200);

        // Already deleted, so teardown has nothing left to clean up
        createdOrderIds.remove(Long.valueOf(deletedOrderId));

        Response response = ApiClient.DEFAULT.get("/store/order/" + deletedOrderId);
        AllureHelper.attachResponse("GET /store/order (not found)", response);

        ResponseValidator.from(response).statusCode(404);
    }

    // ── DELETE /store/order/{orderId} ─────────────────────────────────────────

    @Test
    @Story("Delete Order")
    @Severity(SeverityLevel.CRITICAL)
    @Description("DELETE /store/order/{id} for an existing order should return 200.")
    public void deleteOrder_happyPath() {
        long createdOrderId = placeOrder();
        AllureHelper.parameter("Order ID to delete", createdOrderId);

        Response response = ApiClient.DEFAULT.delete("/store/order/" + createdOrderId);
        AllureHelper.attachResponse("DELETE /store/order/" + createdOrderId, response);

        ResponseValidator.from(response)
                .statusCode(200)
                .withinSla(BaseConfig.LIVE_API_SLA_MS);

        // Confirm deletion
        Response verify = ApiClient.DEFAULT.get("/store/order/" + createdOrderId);
        ResponseValidator.from(verify).statusCode(404);

        // Already deleted, so teardown has nothing left to clean up
        createdOrderIds.remove(Long.valueOf(createdOrderId));
    }
}
