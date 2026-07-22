// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.higherkindedj.spring.example.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for the 422 leg (issue #627): the {@code POST /api/users/parse} endpoint returns the
 * generated {@code UserMappingImpl.parse} result directly, so the whole path is exercised end to
 * end — Jackson binds the wire DTO, the generated mapping accumulates located {@code FieldError}s,
 * and the {@code ValidationPathReturnValueHandler} renders them as one 422 with every bad field by
 * path.
 */
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@Import(UserService.class)
@DisplayName("UserController parse @WebMvcTest slice - the 422 leg (#627)")
class UserParseWebMvcSliceTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("a fully valid wire user parses to 200 with the domain user")
  void validWireUserParsesTo200() throws Exception {
    mockMvc
        .perform(
            post("/api/users/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id":"7","email":"grace@example.com","firstName":"Grace","lastName":"Hopper"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("7"))
        .andExpect(jsonPath("$.email").value("grace@example.com"))
        .andExpect(jsonPath("$.firstName").value("Grace"))
        .andExpect(jsonPath("$.lastName").value("Hopper"));
  }

  @Test
  @DisplayName("two bad fields come back as ONE 422 listing both by path")
  void twoBadFieldsAccumulateInOne422() throws Exception {
    mockMvc
        .perform(
            post("/api/users/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id":"7","email":"not-an-email","firstName":"","lastName":"Hopper"}
                    """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.errorCount").value(2))
        .andExpect(jsonPath("$.errors[*].path").value(containsInAnyOrder("email", "firstName")))
        .andExpect(
            jsonPath("$.errors[?(@.path=='email')].message").value("not a valid email address"))
        .andExpect(jsonPath("$.errors[?(@.path=='email')].segments[0]").value("email"))
        .andExpect(jsonPath("$.errors[?(@.path=='firstName')].message").value("must not be blank"));
  }

  @Test
  @DisplayName("absent fields become located 'must not be null' errors in the same 422, not a 500")
  void absentFieldsAreLocatedErrorsNotExceptions() throws Exception {
    // id and lastName are omitted: Jackson leaves the bean properties null, and the bean-shaped
    // parse null-guards every read - no NPE, both absences located in one response.
    mockMvc
        .perform(
            post("/api/users/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"grace@example.com","firstName":"Grace"}
                    """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.errorCount").value(2))
        .andExpect(jsonPath("$.errors[*].path").value(containsInAnyOrder("id", "lastName")))
        .andExpect(jsonPath("$.errors[?(@.path=='id')].message").value("must not be null"));
  }

  @Test
  @DisplayName("a single bad field is a 422 with exactly one located error")
  void singleBadFieldIsA422WithOneError() throws Exception {
    mockMvc
        .perform(
            post("/api/users/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id":"7","email":"not-an-email","firstName":"Grace","lastName":"Hopper"}
                    """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCount").value(1))
        .andExpect(jsonPath("$.errors[0].path").value("email"))
        .andExpect(jsonPath("$.errors[0].segments[0]").value("email"))
        .andExpect(jsonPath("$.errors[0].message").value("not a valid email address"));
  }
}
