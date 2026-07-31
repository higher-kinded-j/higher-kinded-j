// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The substitution seam, end to end: the controller depends on {@code ValidatedPrism<UserDto,
 * User>}, so a test slice swaps the generated codec for a fake built with {@link
 * ValidatedPrism#of}. The interface is sealed, so a fake is constructed as a value - two lines, no
 * mocking framework (and no mocking framework could: sealed types cannot be mocked).
 */
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@Import({UserService.class, UserParseFakeCodecSliceTest.RejectEverythingCodec.class})
@DisplayName("UserController parse slice with a fake codec bean substituted")
class UserParseFakeCodecSliceTest {

  /** A stub codec: every parse fails with one located error; build renders a fixed DTO. */
  @TestConfiguration
  static class RejectEverythingCodec {
    @Bean
    ValidatedPrism<UserDto, User> userCodec() {
      return ValidatedPrism.of(
          dto -> Validated.invalidNel(FieldError.of("rejected by the fake codec").at("email")),
          user -> new UserDto());
    }
  }

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("the endpoint renders whatever the substituted codec decides - a located 422")
  void fakeCodecDrivesTheResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/users/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\":\"1\",\"email\":\"ada@example.org\","
                        + "\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].path").value("email"))
        .andExpect(jsonPath("$.errors[0].message").value("rejected by the fake codec"));
  }
}
