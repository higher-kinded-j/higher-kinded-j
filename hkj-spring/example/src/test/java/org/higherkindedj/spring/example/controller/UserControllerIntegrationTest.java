// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.higherkindedj.spring.example.HkjSpringExampleApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link UserController}.
 *
 * <p>Uses MockMvc to test the full HTTP request/response cycle including:
 *
 * <ul>
 *   <li>EitherReturnValueHandler for Either return types
 *   <li>Jackson serialization for nested Either values
 *   <li>HTTP status code mapping
 * </ul>
 */
@SpringBootTest(classes = HkjSpringExampleApplication.class)
@AutoConfigureMockMvc
@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Nested
  @DisplayName("GET /api/users/{id} Tests")
  class GetUserByIdTests {

    @Test
    @DisplayName("Should return user with HTTP 200 when user exists")
    void shouldReturnUserWith200WhenExists() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value("1"))
          .andExpect(jsonPath("$.email").value("alice@example.com"))
          .andExpect(jsonPath("$.firstName").value("Alice"))
          .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    @DisplayName("Should return another user with HTTP 200")
    void shouldReturnAnotherUserWith200() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "2"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").value("2"))
          .andExpect(jsonPath("$.email").value("bob@example.com"))
          .andExpect(jsonPath("$.firstName").value("Bob"))
          .andExpect(jsonPath("$.lastName").value("Johnson"));
    }

    @Test
    @DisplayName("Should return UserNotFoundError with HTTP 404 when user does not exist")
    void shouldReturn404WhenUserNotFound() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error").exists())
          .andExpect(jsonPath("$.error.userId").value("999"));
    }

    @Test
    @DisplayName("Should return 404 for non-existent user ID")
    void shouldReturn404ForNonExistentId() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "nonexistent"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.userId").value("nonexistent"));
    }
  }

  @Nested
  @DisplayName("GET /api/users/batch Tests")
  class GetUserBatchTests {

    @Test
    @DisplayName("Should return batch result with mixed success and error values")
    void shouldReturnBatchResultWithMixedValues() throws Exception {
      mockMvc
          .perform(get("/api/users/batch"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.batchId").exists())
          .andExpect(jsonPath("$.results").isArray())
          .andExpect(jsonPath("$.results", hasSize(3)));
    }

    @Test
    @DisplayName("Should serialize successful Either values in batch with TAGGED format")
    void shouldSerializeSuccessfulEitherValues() throws Exception {
      mockMvc
          .perform(get("/api/users/batch"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[0].isRight").value(true))
          .andExpect(jsonPath("$.results[0].right").exists())
          .andExpect(jsonPath("$.results[0].right.id").value("1"))
          .andExpect(jsonPath("$.results[0].right.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("Should serialize error Either values in batch with TAGGED format")
    void shouldSerializeErrorEitherValues() throws Exception {
      mockMvc
          .perform(get("/api/users/batch"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[1].isRight").value(false))
          .andExpect(jsonPath("$.results[1].left").exists())
          .andExpect(jsonPath("$.results[1].left.userId").value("999"));
    }

    @Test
    @DisplayName("Should handle multiple successful values in batch")
    void shouldHandleMultipleSuccessfulValues() throws Exception {
      mockMvc
          .perform(get("/api/users/batch"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[2].isRight").value(true))
          .andExpect(jsonPath("$.results[2].right").exists())
          .andExpect(jsonPath("$.results[2].right.id").value("2"))
          .andExpect(jsonPath("$.results[2].right.email").value("bob@example.com"));
    }
  }

  @Nested
  @DisplayName("GET /api/users/debug/jackson-modules Tests")
  class GetJacksonModulesTests {

    @Test
    @DisplayName("Should return Jackson modules information")
    void shouldReturnJacksonModulesInfo() throws Exception {
      mockMvc
          .perform(get("/api/users/debug/jackson-modules"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.registeredModules").isArray())
          .andExpect(jsonPath("$.hkjModulePresent").exists());
    }

    @Test
    @DisplayName("Should confirm HkjJacksonModule is registered")
    void shouldConfirmHkjModuleRegistered() throws Exception {
      mockMvc
          .perform(get("/api/users/debug/jackson-modules"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hkjModulePresent").value(true))
          .andExpect(jsonPath("$.registeredModules", hasItem("HkjJacksonModule")));
    }
  }

  @Nested
  @DisplayName("Content Type Tests")
  class ContentTypeTests {

    @Test
    @DisplayName("Should return application/json for successful responses")
    void shouldReturnJsonContentTypeForSuccess() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    @DisplayName("Should return application/json for error responses")
    void shouldReturnJsonContentTypeForError() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(header().string("Content-Type", containsString("application/json")));
    }
  }

  @Nested
  @DisplayName("Response Structure Tests")
  class ResponseStructureTests {

    @Test
    @DisplayName("Should return unwrapped value for top-level Either (Right)")
    void shouldReturnUnwrappedValueForTopLevelRight() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          // Top-level Either is unwrapped by EitherReturnValueHandler
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.email").exists())
          // Should NOT have Either wrapper fields
          .andExpect(jsonPath("$.isRight").doesNotExist())
          .andExpect(jsonPath("$.right").doesNotExist());
    }

    @Test
    @DisplayName("Should return wrapped error for top-level Either (Left)")
    void shouldReturnWrappedErrorForTopLevelLeft() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          // Top-level Either is unwrapped by EitherReturnValueHandler
          // Error is wrapped in {success: false, error: ...}
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error").exists())
          .andExpect(jsonPath("$.error.userId").value("999"));
    }

    @Test
    @DisplayName("Should return wrapped Either values for nested Either in DTO")
    void shouldReturnWrappedEitherForNestedValues() throws Exception {
      mockMvc
          .perform(get("/api/users/batch"))
          .andExpect(status().isOk())
          // Nested Either values are serialized by Jackson with wrappers
          .andExpect(jsonPath("$.results[0].isRight").exists())
          .andExpect(jsonPath("$.results[0].right").exists())
          .andExpect(jsonPath("$.results[1].isRight").exists())
          .andExpect(jsonPath("$.results[1].left").exists());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should map UserNotFoundError to HTTP 404")
    void shouldMapUserNotFoundErrorTo404() throws Exception {
      mockMvc.perform(get("/api/users/{id}", "999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should include error details in response")
    void shouldIncludeErrorDetailsInResponse() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error").exists())
          .andExpect(jsonPath("$.error.userId").value("999"));
    }

    @Test
    @DisplayName("Should set success flag to false for errors")
    void shouldSetSuccessFlagToFalse() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false));
    }
  }

  @Nested
  @DisplayName("Jackson Integration Tests")
  class JacksonIntegrationTests {

    @Test
    @DisplayName("Should use custom Jackson serializers for nested Either")
    void shouldUseCustomSerializersForNestedEither() throws Exception {
      String response =
          mockMvc
              .perform(get("/api/users/batch"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Verify the response can be parsed
      var result = objectMapper.readTree(response);

      // Verify TAGGED format is used (default)
      var firstResult = result.get("results").get(0);
      assert firstResult.has("isRight");
      assert firstResult.get("isRight").asBoolean();
      assert firstResult.has("right");
    }

    @Test
    @DisplayName("Should correctly serialize User objects")
    void shouldCorrectlySerializeUserObjects() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").isString())
          .andExpect(jsonPath("$.email").isString())
          .andExpect(jsonPath("$.firstName").isString())
          .andExpect(jsonPath("$.lastName").isString());
    }

    @Test
    @DisplayName("Should correctly serialize error objects")
    void shouldCorrectlySerializeErrorObjects() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.userId").isString());
    }
  }
}
