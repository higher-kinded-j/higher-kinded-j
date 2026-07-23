// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.higherkindedj.spring.example.controller.ErrorStatusFixtureController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end slice test that exercises every error-status mapping rule the starter supports:
 * built-in heuristics, explicit {@code hkj.web.error-status-mappings} entries, and {@link
 * org.higherkindedj.spring.web.returnvalue.HttpHeaderCarrier} header injection.
 *
 * <p>This is the canonical fixture project adopters can copy when they add new {@code DomainError}
 * variants — every variant gets one assertion and one HTTP round-trip.
 */
@WebMvcTest(ErrorStatusFixtureController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@TestPropertySource(
    properties = {
      "hkj.web.either.default-error-status=500",
      "hkj.web.error-status-mappings.MfaAlreadyEnrolledError=409",
      "hkj.web.error-status-mappings.PaymentDeclinedError=422",
      "hkj.web.error-status-mappings.MfaThrottledError=429"
    })
@DisplayName("Error status mapping fixture (heuristics + overrides + headers)")
class ErrorStatusFixtureSliceTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("not-found → 404 via heuristic")
  void notFound() throws Exception {
    mockMvc.perform(get("/api/error-status-fixture/not-found")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("validation → 400 via heuristic")
  void validation() throws Exception {
    mockMvc.perform(get("/api/error-status-fixture/validation")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("invalid → 400 via heuristic (token match)")
  void invalid() throws Exception {
    mockMvc.perform(get("/api/error-status-fixture/invalid")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("forbidden → 403 via heuristic")
  void forbidden() throws Exception {
    mockMvc.perform(get("/api/error-status-fixture/forbidden")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("authorization → 403 via heuristic")
  void authorization() throws Exception {
    mockMvc
        .perform(get("/api/error-status-fixture/authorization"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("authentication → 401 via heuristic")
  void authentication() throws Exception {
    mockMvc
        .perform(get("/api/error-status-fixture/authentication"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("unauthorized → 401 via heuristic")
  void unauthorized() throws Exception {
    mockMvc
        .perform(get("/api/error-status-fixture/unauthorized"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("conflict → 409 via explicit mapping")
  void conflict() throws Exception {
    mockMvc.perform(get("/api/error-status-fixture/conflict")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("unprocessable → 422 via explicit mapping")
  void unprocessable() throws Exception {
    mockMvc
        .perform(get("/api/error-status-fixture/unprocessable"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  @DisplayName("throttled → 429 + Retry-After header via HttpHeaderCarrier")
  void throttled() throws Exception {
    mockMvc
        .perform(get("/api/error-status-fixture/throttled"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "30"));
  }

  @Test
  @DisplayName("unmapped → falls back to configured default 500")
  void unmapped() throws Exception {
    mockMvc
        .perform(get("/api/error-status-fixture/unmapped"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false));
  }
}
