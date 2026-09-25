// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletException;
import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.exc.InvalidDefinitionException;

/**
 * What a client receives when Jackson cannot bind a request body, before any mapping runs. The
 * mapping chapter cites both cases: a malformed value in a typed field is Jackson's own 400, while
 * a sealed wire interface with no type information fails in a way Spring does not map to any client
 * error, so it escapes the controller and the server answers 500.
 */
@WebMvcTest(JacksonBindingSliceTest.BindingController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@Import(JacksonBindingSliceTest.BindingController.class)
@DisplayName("Jackson binding failures reach the client as the mapping chapter says")
class JacksonBindingSliceTest {

  /** A sealed wire interface with no type information, which Jackson cannot construct. */
  sealed interface BarePaymentDto permits BareCardDto {}

  record BareCardDto(String pan) implements BarePaymentDto {}

  /** A wire that types its quantity as a number. */
  record TypedLineDto(int quantity) {}

  @RestController
  static class BindingController {
    @PostMapping("/binding/payment")
    String payment(@RequestBody BarePaymentDto payment) {
      return "bound";
    }

    @PostMapping("/binding/line")
    String line(@RequestBody TypedLineDto line) {
      return "bound";
    }
  }

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("a malformed value in a typed field is Jackson's own 400")
  void malformedTypedFieldIsJacksonsOwn400() throws Exception {
    mockMvc
        .perform(
            post("/binding/line")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": \"two\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .isInstanceOf(HttpMessageNotReadableException.class));
  }

  @Test
  @DisplayName("a sealed wire without type information escapes as a server error, not a 400")
  void sealedWireWithoutTypeInformationIsAServerError() {
    // No resolver maps the failure to a status, so it leaves the controller unhandled, which a
    // servlet container answers with 500. Spring maps only HttpMessageNotReadableException to 400.
    assertThatThrownBy(
            () ->
                mockMvc.perform(
                    post("/binding/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pan\": \"4111\"}")))
        .isInstanceOf(ServletException.class)
        .cause()
        .isInstanceOf(HttpMessageConversionException.class)
        .isNotInstanceOf(HttpMessageNotReadableException.class)
        .rootCause()
        .isInstanceOf(InvalidDefinitionException.class);
  }
}
