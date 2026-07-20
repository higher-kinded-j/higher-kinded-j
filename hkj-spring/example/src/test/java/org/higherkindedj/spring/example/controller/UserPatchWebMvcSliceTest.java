// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Slice test for the sparse PATCH endpoint (issue #645). Runs against the <b>real</b> {@link
 * UserService} (its pre-populated store), so a single PATCH exercises the whole path end to end:
 * the generated {@code updateFrom} mapping and persistence. (The service's atomicity is a
 * by-construction property of {@code ConcurrentHashMap.compute}, not something these sequential
 * requests exercise.) Each test targets a distinct pre-populated user, so they do not contend on
 * the shared store; a follow-up {@code GET} proves the write landed — or, for a validation failure
 * or an unknown id, that nothing was written.
 */
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@Import(UserService.class)
@DisplayName("UserController sparse PATCH @WebMvcTest slice (#645)")
class UserPatchWebMvcSliceTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("a present valid field is applied and persisted; absent fields keep their value")
  void presentFieldAppliedAndPersisted() throws Exception {
    // user "2" is pre-populated as bob@example.com / Bob / Johnson.
    mockMvc
        .perform(
            patch("/api/users/{id}", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grace@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("2")) // id was never mapped, so it survives
        .andExpect(jsonPath("$.email").value("grace@example.com")) // present -> changed
        .andExpect(jsonPath("$.firstName").value("Bob")); // absent -> unchanged

    // The change was actually written: a fresh read sees it.
    mockMvc
        .perform(get("/api/users/{id}", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("grace@example.com"))
        .andExpect(jsonPath("$.firstName").value("Bob"));
  }

  @Test
  @DisplayName("a different present field is applied and persisted on its own")
  void differentPresentFieldAppliedAndPersisted() throws Exception {
    // user "3" is pre-populated as charlie@example.com / Charlie / Brown.
    mockMvc
        .perform(
            patch("/api/users/{id}", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ada\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.email").value("charlie@example.com")); // unchanged

    mockMvc
        .perform(get("/api/users/{id}", "3"))
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.email").value("charlie@example.com"));
  }

  @Test
  @DisplayName("a present invalid field fails with HTTP 400 and writes nothing")
  void presentInvalidFieldMapsTo400AndWritesNothing() throws Exception {
    // user "1" is pre-populated as alice@example.com / Alice / Smith.
    mockMvc
        .perform(
            patch("/api/users/{id}", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").exists());

    // Nothing was written: the stored user is untouched.
    mockMvc
        .perform(get("/api/users/{id}", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alice@example.com"));
  }

  @Test
  @DisplayName("an unknown id maps to HTTP 404 and creates no user")
  void unknownIdMapsTo404() throws Exception {
    mockMvc
        .perform(
            patch("/api/users/{id}", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"grace@example.com\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.userId").value("999"));

    mockMvc.perform(get("/api/users/{id}", "999")).andExpect(status().isNotFound());
  }
}
