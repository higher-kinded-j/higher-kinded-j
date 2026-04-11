// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the effect-example application.
 *
 * <p>Verifies end-to-end HTTP request/response behaviour with the EffectBoundary interpreting Free
 * programs.
 */
@SpringBootTest(classes = EffectExampleApplication.class)
@AutoConfigureMockMvc
@DisplayName("Effect Example Integration Tests")
class EffectExampleIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Nested
  @DisplayName("POST /api/orders - Place Order")
  class PlaceOrderTests {

    @Test
    @DisplayName("Should place order and return confirmed result")
    void shouldPlaceOrder() throws Exception {
      mockMvc
          .perform(
              post("/api/orders")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"customerId":"C001","itemId":"ITEM-42","quantity":2}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.orderId").exists())
          .andExpect(jsonPath("$.status").value("CONFIRMED"))
          .andExpect(jsonPath("$.message").value("Order confirmed"));
    }
  }

  @Nested
  @DisplayName("GET /api/orders/{id}/status - Get Status")
  class GetStatusTests {

    @Test
    @DisplayName("Should return PENDING for unknown order")
    void shouldReturnPendingForUnknownOrder() throws Exception {
      mockMvc
          .perform(get("/api/orders/UNKNOWN-123/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").value("PENDING"));
    }
  }
}
