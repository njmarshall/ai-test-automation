package com.aitesting.shared.assertions;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ResponseValidatorTest — unit tests for the withinSla(long) overload.
 *
 * ResponseValidator reads elapsed time from Response.getTime(), so a
 * mocked Response with a stubbed getTime() is all that is needed.
 */
@DisplayName("ResponseValidator.withinSla(long)")
class ResponseValidatorTest {

    @Test
    @DisplayName("passes when under the limit, throws with the limit value when over")
    void withinSla_explicitLimit() {
        Response fast = mock(Response.class);
        when(fast.getTime()).thenReturn(7_999L);
        assertDoesNotThrow(() -> ResponseValidator.from(fast).withinSla(8_000L));

        Response slow = mock(Response.class);
        when(slow.getTime()).thenReturn(8_001L);
        AssertionError error = assertThrows(AssertionError.class,
                () -> ResponseValidator.from(slow).withinSla(8_000L));
        assertTrue(error.getMessage().contains("Response time 8001ms exceeded SLA 8000ms"),
                "Unexpected message: " + error.getMessage());
    }
}
