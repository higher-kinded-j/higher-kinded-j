// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.higherkindedj.spring.example.controller.EitherDefaultErrorStatusController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end slice test reproducing the exact scenario from issue #490.
 *
 * <p>The {@link EitherDefaultErrorStatusController} returns {@code Either<DomainError, ?>} from
 * three endpoints. With {@code hkj.web.either.default-error-status=500} configured:
 *
 * <ul>
 *   <li>{@code /api/either-status/not-found} hits the {@code NotFound} heuristic → 404
 *   <li>{@code /api/either-status/duplicate} matches no heuristic → must fall back to the
 *       configured 500 (not the hardcoded 400 observed before the fix)
 *   <li>{@code /api/either-status/persistence} matches no heuristic → must fall back to the
 *       configured 500
 * </ul>
 *
 * <p>Lives in the example module because {@code @WebMvcTest} requires a {@code
 * @SpringBootApplication} for context bootstrapping, and {@link HkjSpringExampleApplication}
 * provides one.
 *
 * <p>Property-level binding coverage for both the nested {@code hkj.web.either.default-error-
 * status} and the legacy flat {@code hkj.web.default-error-status} lives in {@code
 * HkjWebMvcAutoConfigurationTest} / {@code HkjPropertiesEitherTest} in the autoconfigure module;
 * this class focuses on the full MVC round-trip for the specific bug reproduction.
 */
@WebMvcTest(EitherDefaultErrorStatusController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@TestPropertySource(properties = "hkj.web.either.default-error-status=500")
@DisplayName("Either default-error-status end-to-end (issue #490)")
class EitherDefaultErrorStatusSliceTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("/api/either-status/not-found → 404 via NotFound heuristic")
  void notFoundHitsHeuristic() throws Exception {
    mockMvc
        .perform(get("/api/either-status/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.id").value("x"));
  }

  @Test
  @DisplayName("/api/either-status/duplicate → 500 via configured default (no heuristic match)")
  void duplicateFallsBackToConfiguredDefault() throws Exception {
    mockMvc
        .perform(get("/api/either-status/duplicate"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.id").value("x"));
  }

  @Test
  @DisplayName(
      "/api/either-status/persistence → 500 via configured default (no heuristic match)")
  void persistenceFallsBackToConfiguredDefault() throws Exception {
    mockMvc
        .perform(get("/api/either-status/persistence"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.op").value("save"));
  }
}
