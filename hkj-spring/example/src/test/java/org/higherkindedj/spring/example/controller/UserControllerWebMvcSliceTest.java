// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.higherkindedj.spring.example.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Canonical slice-test recipe for controllers that return {@code Either} / {@code Validated} /
 * {@code *Path} types from higher-kinded-j.
 *
 * <p>{@code @WebMvcTest} only loads the MVC slice — third-party auto-configurations registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} are
 * <b>not</b> picked up. Without importing them explicitly, MockMvc sees a raw POJO serialization of
 * the {@code Either} and no status-code mapping, because:
 *
 * <ul>
 *   <li>{@link HkjJacksonAutoConfiguration} (registers {@code HkjJacksonModule}) isn't loaded, so
 *       {@code Either} / {@code Validated} serialize as their raw fields rather than the tagged
 *       {@code {success, value}} / {@code {success, error}} shape.
 *   <li>{@link HkjWebMvcAutoConfiguration} (registers the {@code *ReturnValueHandler}s and the
 *       {@code ErrorStatusCodeMapper}) isn't loaded, so top-level {@code Either} values are not
 *       unwrapped and left errors don't map to status codes like 404/400.
 * </ul>
 *
 * <p>Importing all three auto-configurations below restores production behaviour inside the slice:
 *
 * <ul>
 *   <li>{@link HkjAutoConfiguration} — binds {@code HkjProperties} (required by the Web MVC
 *       auto-config).
 *   <li>{@link HkjJacksonAutoConfiguration} — registers the Jackson module for JSON shape.
 *   <li>{@link HkjWebMvcAutoConfiguration} — registers the return-value handlers and status-code
 *       mapping.
 * </ul>
 *
 * <p>For full-fidelity integration testing, prefer {@code @SpringBootTest + @AutoConfigureMockMvc}
 * (see {@link UserControllerIntegrationTest}). Use this slice recipe when you want fast, focused
 * controller tests without loading the full application context.
 */
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
  HkjAutoConfiguration.class,
  HkjJacksonAutoConfiguration.class,
  HkjWebMvcAutoConfiguration.class
})
@DisplayName("UserController @WebMvcTest slice (with hkj-spring auto-config imported)")
class UserControllerWebMvcSliceTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @Test
  @DisplayName("Right(User) is unwrapped to HTTP 200 with the user JSON")
  void rightEitherUnwrappedTo200() throws Exception {
    when(userService.findById("1"))
        .thenReturn(Either.right(new User("1", "alice@example.com", "Alice", "Smith")));

    mockMvc
        .perform(get("/api/users/{id}", "1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        // Top-level Either is unwrapped — the Either wrapper fields must NOT leak.
        .andExpect(jsonPath("$.isRight").doesNotExist())
        .andExpect(jsonPath("$.right").doesNotExist());
  }

  @Test
  @DisplayName("Left(UserNotFoundError) maps to HTTP 404 with tagged error JSON")
  void leftUserNotFoundMapsTo404() throws Exception {
    when(userService.findById("999"))
        .thenReturn(Either.<DomainError, User>left(new UserNotFoundError("999")));

    mockMvc
        .perform(get("/api/users/{id}", "999"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.userId").value("999"));
  }
}
