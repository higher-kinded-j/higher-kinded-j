// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end integration tests for the complete higher-kinded-j Spring Boot integration.
 *
 * <p>Tests the full system including:
 *
 * <ul>
 *   <li>Both Either and Validated return value handlers
 *   <li>Jackson serialization for nested values
 *   <li>Configuration properties application
 *   <li>Multiple controllers working together
 *   <li>Complete request/response cycle
 * </ul>
 */
@SpringBootTest(classes = HkjSpringExampleApplication.class)
@AutoConfigureMockMvc
@DisplayName("End-to-End Integration Tests")
class EndToEndIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JsonMapper objectMapper;

  @Nested
  @DisplayName("Complete User Workflow Tests")
  class CompleteUserWorkflowTests {

    @Test
    @DisplayName("Should handle complete user lookup workflow")
    void shouldHandleCompleteUserLookupWorkflow() throws Exception {
      // 1. Get existing user - should succeed
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("alice@example.com"));

      // 2. Get non-existent user - should fail with 404
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false));

      // 3. Get another existing user - should succeed
      mockMvc
          .perform(get("/api/users/{id}", "2"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    @DisplayName("Should handle batch user retrieval")
    void shouldHandleBatchUserRetrieval() throws Exception {
      MvcResult result =
          mockMvc
              .perform(get("/api/users/batch"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.results", hasSize(3)))
              .andReturn();

      String response = result.getResponse().getContentAsString();
      var json = objectMapper.readTree(response);

      // Verify mixed success and failure in batch
      assertThat(json.get("results").get(0).get("isRight").asBoolean()).isTrue();
      assertThat(json.get("results").get(1).get("isRight").asBoolean()).isFalse();
      assertThat(json.get("results").get(2).get("isRight").asBoolean()).isTrue();
    }
  }

  @Nested
  @DisplayName("Complete Validation Workflow Tests")
  class CompleteValidationWorkflowTests {

    @Test
    @DisplayName("Should handle complete validation workflow")
    void shouldHandleCompleteValidationWorkflow() throws Exception {
      // 1. Create valid user - should succeed
      String validRequest =
          """
          {
              "email": "newuser@example.com",
              "firstName": "New",
              "lastName": "User"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validRequest))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("newuser@example.com"));

      // 2. Create invalid user - should fail with accumulated errors
      String invalidRequest =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors", hasSize(3)));

      // 3. Create partially invalid user - should fail with specific errors
      String partiallyInvalidRequest =
          """
          {
              "email": "good@example.com",
              "firstName": "",
              "lastName": "Good"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(partiallyInvalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(jsonPath("$.errors[0].field").value("firstName"));
    }

    @Test
    @DisplayName("Should handle batch validation")
    void shouldHandleBatchValidation() throws Exception {
      String batchRequest =
          """
          [
              {
                  "email": "valid1@example.com",
                  "firstName": "Valid",
                  "lastName": "One"
              },
              {
                  "email": "invalid",
                  "firstName": "",
                  "lastName": ""
              },
              {
                  "email": "valid2@example.com",
                  "firstName": "Valid",
                  "lastName": "Two"
              }
          ]
          """;

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/validation/batch")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(batchRequest))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.results", hasSize(3)))
              .andReturn();

      String response = result.getResponse().getContentAsString();
      var json = objectMapper.readTree(response);

      // Verify mixed valid and invalid in batch
      assertThat(json.get("results").get(0).get("valid").asBoolean()).isTrue();
      assertThat(json.get("results").get(1).get("valid").asBoolean()).isFalse();
      assertThat(json.get("results").get(2).get("valid").asBoolean()).isTrue();
    }
  }

  @Nested
  @DisplayName("Either vs Validated Behavior Tests")
  class EitherVsValidatedBehaviorTests {

    @Test
    @DisplayName("Should demonstrate fail-fast (Either) vs error accumulation (Validated)")
    void shouldDemonstrateFailFastVsAccumulation() throws Exception {
      // Either: Returns first error immediately (fail-fast)
      // In UserController, if a user is not found, we get single UserNotFoundError
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.userId").value("999"));

      // Validated: Accumulates all errors
      // In ValidationController, if validation fails, we get ALL validation errors
      String invalidRequest =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    @DisplayName("Should show different response structures for Either and Validated")
    void shouldShowDifferentResponseStructures() throws Exception {
      // Either error response: {success: false, error: {...}}
      MvcResult eitherResult =
          mockMvc
              .perform(get("/api/users/{id}", "999"))
              .andExpect(status().isNotFound())
              .andReturn();

      var eitherJson = objectMapper.readTree(eitherResult.getResponse().getContentAsString());
      assertThat(eitherJson.has("success")).isTrue();
      assertThat(eitherJson.has("error")).isTrue();

      // Validated error response: {valid: false, errors: [...]}
      String invalidRequest =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      MvcResult validatedResult =
          mockMvc
              .perform(
                  post("/api/validation/users")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(invalidRequest))
              .andExpect(status().isBadRequest())
              .andReturn();

      var validatedJson = objectMapper.readTree(validatedResult.getResponse().getContentAsString());
      assertThat(validatedJson.has("valid")).isTrue();
      assertThat(validatedJson.has("errors")).isTrue();
    }
  }

  @Nested
  @DisplayName("Jackson Serialization Integration Tests")
  class JacksonSerializationIntegrationTests {

    @Test
    @DisplayName("Should verify HkjJacksonModule is registered")
    void shouldVerifyHkjModuleRegistered() throws Exception {
      mockMvc
          .perform(get("/api/users/debug/jackson-modules"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hkjModulePresent").value(true))
          .andExpect(jsonPath("$.registeredModules", hasItem("HkjJacksonModule")));
    }

    @Test
    @DisplayName("Should serialize nested Either values with Jackson")
    void shouldSerializeNestedEitherWithJackson() throws Exception {
      MvcResult result =
          mockMvc.perform(get("/api/users/batch")).andExpect(status().isOk()).andReturn();

      var json = objectMapper.readTree(result.getResponse().getContentAsString());
      var results = json.get("results");

      // Verify TAGGED format (default configuration)
      assertThat(results.get(0).has("isRight")).isTrue();
      assertThat(results.get(0).has("right")).isTrue();
      assertThat(results.get(1).has("isRight")).isTrue();
      assertThat(results.get(1).has("left")).isTrue();
    }

    @Test
    @DisplayName("Should serialize nested Validated values with Jackson")
    void shouldSerializeNestedValidatedWithJackson() throws Exception {
      String batchRequest =
          """
          [
              {
                  "email": "valid@example.com",
                  "firstName": "Valid",
                  "lastName": "User"
              },
              {
                  "email": "invalid",
                  "firstName": "",
                  "lastName": ""
              }
          ]
          """;

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/validation/batch")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(batchRequest))
              .andExpect(status().isOk())
              .andReturn();

      var json = objectMapper.readTree(result.getResponse().getContentAsString());
      var results = json.get("results");

      // Verify TAGGED format
      assertThat(results.get(0).has("valid")).isTrue();
      assertThat(results.get(0).has("value")).isTrue();
      assertThat(results.get(1).has("valid")).isTrue();
      assertThat(results.get(1).has("errors")).isTrue();
    }

    @Test
    @DisplayName("Should handle top-level vs nested serialization differently")
    void shouldHandleTopLevelVsNestedSerializationDifferently() throws Exception {
      // Top-level Either: unwrapped by EitherReturnValueHandler
      MvcResult topLevelResult =
          mockMvc.perform(get("/api/users/{id}", "1")).andExpect(status().isOk()).andReturn();

      var topLevelJson = objectMapper.readTree(topLevelResult.getResponse().getContentAsString());
      // Should NOT have wrapper fields
      assertThat(topLevelJson.has("isRight")).isFalse();
      assertThat(topLevelJson.has("right")).isFalse();
      // Should have unwrapped user fields
      assertThat(topLevelJson.has("id")).isTrue();
      assertThat(topLevelJson.has("email")).isTrue();

      // Nested Either: serialized by Jackson with wrappers
      MvcResult nestedResult =
          mockMvc.perform(get("/api/users/batch")).andExpect(status().isOk()).andReturn();

      var nestedJson = objectMapper.readTree(nestedResult.getResponse().getContentAsString());
      var firstResult = nestedJson.get("results").get(0);
      // Should have wrapper fields
      assertThat(firstResult.has("isRight")).isTrue();
      assertThat(firstResult.has("right")).isTrue();
    }
  }

  @Nested
  @DisplayName("HTTP Status Code Mapping Tests")
  class HttpStatusCodeMappingTests {

    @Test
    @DisplayName("Should map error types to correct HTTP status codes")
    void shouldMapErrorTypesToCorrectStatusCodes() throws Exception {
      // UserNotFoundError → 404 (configured in application.yml)
      mockMvc.perform(get("/api/users/{id}", "999")).andExpect(status().isNotFound());

      // Validation errors → 400 (default for Validated)
      String invalidRequest =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should use configured default error status")
    void shouldUseConfiguredDefaultErrorStatus() throws Exception {
      // Default error status is 400 (configured in application.yml)
      // This is tested implicitly through validation errors
      String invalidRequest =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest()); // 400
    }
  }

  @Nested
  @DisplayName("Configuration Properties Tests")
  class ConfigurationPropertiesTests {

    @Test
    @DisplayName("Should verify configuration is applied from application.yml")
    void shouldVerifyConfigurationApplied() throws Exception {
      // Verify that handlers are enabled (default: true)
      // This is implicit - if handlers weren't enabled, these requests would fail

      // Either handler enabled
      mockMvc.perform(get("/api/users/{id}", "1")).andExpect(status().isOk());

      // Validated handler enabled
      String validRequest =
          """
          {
              "email": "test@example.com",
              "firstName": "Test",
              "lastName": "User"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validRequest))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should verify Jackson module configuration is applied")
    void shouldVerifyJacksonModuleConfigurationApplied() throws Exception {
      // Verify custom-serializers-enabled=true (default in application.yml)
      mockMvc
          .perform(get("/api/users/debug/jackson-modules"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hkjModulePresent").value(true));

      // Verify either-format=TAGGED (default in application.yml)
      MvcResult result =
          mockMvc.perform(get("/api/users/batch")).andExpect(status().isOk()).andReturn();

      var json = objectMapper.readTree(result.getResponse().getContentAsString());
      // TAGGED format should have "isRight" and "right"/"left" fields
      assertThat(json.get("results").get(0).has("isRight")).isTrue();
    }
  }

  @Nested
  @DisplayName("CompletableFuturePath Async Integration Tests")
  class CompletableFuturePathAsyncIntegrationTests {

    @Test
    @DisplayName("Should handle async user lookup workflow")
    void shouldHandleAsyncUserLookupWorkflow() throws Exception {
      // 1. Async get existing user - should succeed
      MvcResult result1 =
          mockMvc
              .perform(get("/api/async/users/{id}", "1"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(result1))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("alice@example.com"));

      // 2. Async get non-existent user - should fail with 500
      MvcResult result2 =
          mockMvc
              .perform(get("/api/async/users/{id}", "999"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(result2))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.success").value(false));

      // 3. Async get by email - should succeed
      MvcResult result3 =
          mockMvc
              .perform(get("/api/async/users/by-email").param("email", "bob@example.com"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(result3))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    @DisplayName("Should handle async composed operations with flatMap")
    void shouldHandleAsyncComposedOperations() throws Exception {
      // Enriched user endpoint uses flatMap composition:
      // findByIdAsync() -> loadProfileAsync() -> map to EnrichedUser
      MvcResult result =
          mockMvc
              .perform(get("/api/async/users/{id}/enriched", "1"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(result))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user.id").value("1"))
          .andExpect(jsonPath("$.user.email").value("alice@example.com"))
          .andExpect(jsonPath("$.profile.userId").value("1"))
          .andExpect(jsonPath("$.profile.tier").value("Premium"));
    }

    @Test
    @DisplayName("Should handle async update operations")
    void shouldHandleAsyncUpdateOperations() throws Exception {
      // Async update with valid email
      MvcResult result1 =
          mockMvc
              .perform(
                  put("/api/async/users/{id}/email", "1")
                      .param("newEmail", "alice.new@example.com"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(result1))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("alice.new@example.com"));

      // Async update with invalid email
      MvcResult result2 =
          mockMvc
              .perform(put("/api/async/users/{id}/email", "1").param("newEmail", "invalid"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc.perform(asyncDispatch(result2)).andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should demonstrate async error propagation")
    void shouldDemonstrateAsyncErrorPropagation() throws Exception {
      // When first async operation fails, subsequent operations are skipped
      MvcResult result =
          mockMvc
              .perform(get("/api/async/users/{id}/enriched", "999"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(result))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should verify async health check")
    void shouldVerifyAsyncHealthCheck() throws Exception {
      MvcResult result =
          mockMvc.perform(get("/api/async/health")).andExpect(request().asyncStarted()).andReturn();

      mockMvc
          .perform(asyncDispatch(result))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("healthy"))
          .andExpect(jsonPath("$.message").value("Async operations are working"));
    }
  }

  @Nested
  @DisplayName("Sync vs Async Comparison Tests")
  class SyncVsAsyncComparisonTests {

    @Test
    @DisplayName("Should demonstrate sync Either vs async CompletableFuturePath")
    void shouldDemonstrateSyncVsAsync() throws Exception {
      // Sync Either: Blocks request thread during operation
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("alice@example.com"));

      // Async CompletableFuturePath: Non-blocking, uses thread pool
      MvcResult asyncResult =
          mockMvc
              .perform(get("/api/async/users/{id}", "1"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(asyncResult))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("alice@example.com"));

      // Both return same data structure, but async is non-blocking
    }

    @Test
    @DisplayName("Should show error handling for sync and async endpoints")
    void shouldShowErrorHandlingForSyncAndAsync() throws Exception {
      // Sync Either error - returns 404 with structured error
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false));

      // Async CompletableFuturePath error - returns 500 with error wrapper
      MvcResult asyncMvcResult =
          mockMvc
              .perform(get("/api/async/users/{id}", "999"))
              .andExpect(request().asyncStarted())
              .andReturn();

      mockMvc
          .perform(asyncDispatch(asyncMvcResult))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.success").value(false));

      // Sync uses EitherPath with custom status mapping (404)
      // Async uses CompletableFuturePath with exception handling (500)
    }
  }

  @Nested
  @DisplayName("Complete System Integration Tests")
  class CompleteSystemIntegrationTests {

    @Test
    @DisplayName("Should handle complex workflow with multiple endpoints")
    void shouldHandleComplexWorkflow() throws Exception {
      // 1. Verify Jackson module is loaded
      mockMvc
          .perform(get("/api/users/debug/jackson-modules"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hkjModulePresent").value(true));

      // 2. Get user (Either success)
      mockMvc
          .perform(get("/api/users/{id}", "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").exists());

      // 3. Get non-existent user (Either failure)
      mockMvc
          .perform(get("/api/users/{id}", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false));

      // 4. Get batch (nested Either serialization)
      mockMvc
          .perform(get("/api/users/batch"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[0].isRight").exists());

      // 5. Validate valid user (Validated success)
      String validRequest =
          """
          {
              "email": "workflow@example.com",
              "firstName": "Workflow",
              "lastName": "Test"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validRequest))
          .andExpect(status().isOk());

      // 6. Validate invalid user (Validated failure with error accumulation)
      String invalidRequest =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors", hasSize(3)));

      // 7. Batch validation (nested Validated serialization)
      String batchRequest =
          """
          [
              {
                  "email": "batch1@example.com",
                  "firstName": "Batch",
                  "lastName": "One"
              },
              {
                  "email": "invalid",
                  "firstName": "",
                  "lastName": ""
              }
          ]
          """;

      mockMvc
          .perform(
              post("/api/validation/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(batchRequest))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results", hasSize(2)))
          .andExpect(jsonPath("$.results[0].valid").value(true))
          .andExpect(jsonPath("$.results[1].valid").value(false));
    }

    @Test
    @DisplayName("Should verify all components work together")
    void shouldVerifyAllComponentsWorkTogether() throws Exception {
      // This test verifies:
      // 1. Auto-configuration loaded properties
      // 2. Return value handlers registered
      // 3. Jackson module registered
      // 4. Controllers working
      // 5. Services working
      // 6. Serialization working
      // 7. Status code mapping working

      // Multiple successful operations prove all components are integrated
      mockMvc.perform(get("/api/users/{id}", "1")).andExpect(status().isOk());

      mockMvc.perform(get("/api/users/{id}", "999")).andExpect(status().isNotFound());

      mockMvc.perform(get("/api/users/batch")).andExpect(status().isOk());

      String validRequest =
          """
          {
              "email": "integration@example.com",
              "firstName": "Integration",
              "lastName": "Test"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validRequest))
          .andExpect(status().isOk());

      mockMvc
          .perform(get("/api/users/debug/jackson-modules"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hkjModulePresent").value(true));
    }
  }
}
