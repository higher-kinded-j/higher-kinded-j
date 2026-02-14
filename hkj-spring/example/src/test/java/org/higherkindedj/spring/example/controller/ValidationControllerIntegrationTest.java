// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.higherkindedj.spring.example.HkjSpringExampleApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for {@link ValidationController}.
 *
 * <p>Uses MockMvc to test the full HTTP request/response cycle including:
 *
 * <ul>
 *   <li>ValidatedReturnValueHandler for Validated return types
 *   <li>Jackson serialization for nested Validated values
 *   <li>Error accumulation behavior
 *   <li>HTTP status code mapping (400 for invalid)
 * </ul>
 */
@SpringBootTest(classes = HkjSpringExampleApplication.class)
@AutoConfigureMockMvc
@DisplayName("ValidationController Integration Tests")
class ValidationControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JsonMapper objectMapper;

  @Nested
  @DisplayName("POST /api/validation/users Tests")
  class CreateUserTests {

    @Test
    @DisplayName("Should return valid user with HTTP 200 when all validations pass")
    void shouldReturnValidUserWith200() throws Exception {
      String requestBody =
          """
          {
              "email": "test@example.com",
              "firstName": "John",
              "lastName": "Doe"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.email").value("test@example.com"))
          .andExpect(jsonPath("$.firstName").value("John"))
          .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @DisplayName("Should accumulate all validation errors and return HTTP 400")
    void shouldAccumulateAllValidationErrors() throws Exception {
      String requestBody =
          """
          {
              "email": "invalid-email",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors").isArray())
          .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    @DisplayName("Should return single validation error for invalid email")
    void shouldReturnSingleErrorForInvalidEmail() throws Exception {
      String requestBody =
          """
          {
              "email": "invalid-email",
              "firstName": "John",
              "lastName": "Doe"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors").isArray())
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(jsonPath("$.errors[0].field").value("email"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value("Validation error on field 'email': Invalid email format"));
    }

    @Test
    @DisplayName("Should return validation error for empty first name")
    void shouldReturnErrorForEmptyFirstName() throws Exception {
      String requestBody =
          """
          {
              "email": "test@example.com",
              "firstName": "",
              "lastName": "Doe"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(jsonPath("$.errors[0].field").value("firstName"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value("Validation error on field 'firstName': First name cannot be empty"));
    }

    @Test
    @DisplayName("Should return validation error for empty last name")
    void shouldReturnErrorForEmptyLastName() throws Exception {
      String requestBody =
          """
          {
              "email": "test@example.com",
              "firstName": "John",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors", hasSize(1)))
          .andExpect(jsonPath("$.errors[0].field").value("lastName"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value("Validation error on field 'lastName': Last name cannot be empty"));
    }

    @Test
    @DisplayName("Should accumulate multiple errors (demonstrates Validated vs Either)")
    void shouldAccumulateMultipleErrors() throws Exception {
      String requestBody =
          """
          {
              "email": "not-an-email",
              "firstName": "",
              "lastName": ""
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors", hasSize(3)))
          // Verify all three errors are present
          .andExpect(
              jsonPath("$.errors[*].field", containsInAnyOrder("email", "firstName", "lastName")));
    }
  }

  @Nested
  @DisplayName("POST /api/validation/batch Tests")
  class BatchValidationTests {

    @Test
    @DisplayName("Should return batch result with mixed valid and invalid values")
    void shouldReturnBatchResultWithMixedValues() throws Exception {
      String requestBody =
          """
          [
              {
                  "email": "valid@example.com",
                  "firstName": "Valid",
                  "lastName": "User"
              },
              {
                  "email": "invalid-email",
                  "firstName": "",
                  "lastName": ""
              },
              {
                  "email": "another@example.com",
                  "firstName": "Another",
                  "lastName": "User"
              }
          ]
          """;

      mockMvc
          .perform(
              post("/api/validation/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.batchId").exists())
          .andExpect(jsonPath("$.results").isArray())
          .andExpect(jsonPath("$.results", hasSize(3)));
    }

    @Test
    @DisplayName("Should serialize valid Validated values in batch with TAGGED format")
    void shouldSerializeValidValidatedValues() throws Exception {
      String requestBody =
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

      mockMvc
          .perform(
              post("/api/validation/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[0].valid").value(true))
          .andExpect(jsonPath("$.results[0].value").exists())
          .andExpect(jsonPath("$.results[0].value.email").value("valid@example.com"));
    }

    @Test
    @DisplayName("Should serialize invalid Validated values in batch with accumulated errors")
    void shouldSerializeInvalidValidatedValues() throws Exception {
      String requestBody =
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

      mockMvc
          .perform(
              post("/api/validation/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[1].valid").value(false))
          .andExpect(jsonPath("$.results[1].errors").exists())
          .andExpect(jsonPath("$.results[1].errors").isArray())
          .andExpect(jsonPath("$.results[1].errors", hasSize(3)));
    }

    @Test
    @DisplayName("Should handle all valid submissions")
    void shouldHandleAllValidSubmissions() throws Exception {
      String requestBody =
          """
          [
              {
                  "email": "user1@example.com",
                  "firstName": "User",
                  "lastName": "One"
              },
              {
                  "email": "user2@example.com",
                  "firstName": "User",
                  "lastName": "Two"
              }
          ]
          """;

      mockMvc
          .perform(
              post("/api/validation/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[0].valid").value(true))
          .andExpect(jsonPath("$.results[1].valid").value(true));
    }

    @Test
    @DisplayName("Should handle all invalid submissions")
    void shouldHandleAllInvalidSubmissions() throws Exception {
      String requestBody =
          """
          [
              {
                  "email": "invalid1",
                  "firstName": "",
                  "lastName": ""
              },
              {
                  "email": "invalid2",
                  "firstName": "",
                  "lastName": ""
              }
          ]
          """;

      mockMvc
          .perform(
              post("/api/validation/batch")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.results[0].valid").value(false))
          .andExpect(jsonPath("$.results[1].valid").value(false));
    }
  }

  @Nested
  @DisplayName("Error Accumulation Tests")
  class ErrorAccumulationTests {

    @Test
    @DisplayName("Should demonstrate error accumulation vs fail-fast")
    void shouldDemonstrateErrorAccumulation() throws Exception {
      String requestBody =
          """
          {
              "email": "bad-email",
              "firstName": "",
              "lastName": ""
          }
          """;

      // With Validated, we get ALL three errors at once
      // With Either, we would get only the first error (fail-fast)
      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors", hasSize(3)))
          .andExpect(jsonPath("$.errors[*].field", hasItems("email", "firstName", "lastName")));
    }

    @Test
    @DisplayName("Should accumulate errors in order of validation")
    void shouldAccumulateErrorsInOrder() throws Exception {
      String requestBody =
          """
          {
              "email": "invalid",
              "firstName": "",
              "lastName": ""
          }
          """;

      String response =
          mockMvc
              .perform(
                  post("/api/validation/users")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      var result = objectMapper.readTree(response);
      var errors = result.get("errors");

      // Verify errors are accumulated (order matches map3 application)
      assert errors.size() == 3;
      assert errors.get(0).get("field").asText().equals("email");
      assert errors.get(1).get("field").asText().equals("firstName");
      assert errors.get(2).get("field").asText().equals("lastName");
    }
  }

  @Nested
  @DisplayName("Response Structure Tests")
  class ResponseStructureTests {

    @Test
    @DisplayName("Should return unwrapped value for top-level Validated (Valid)")
    void shouldReturnUnwrappedValueForTopLevelValid() throws Exception {
      String requestBody =
          """
          {
              "email": "test@example.com",
              "firstName": "John",
              "lastName": "Doe"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          // Top-level Validated is unwrapped by ValidatedReturnValueHandler
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.email").exists())
          // Should NOT have Validated wrapper fields
          .andExpect(jsonPath("$.valid").doesNotExist())
          .andExpect(jsonPath("$.value").doesNotExist());
    }

    @Test
    @DisplayName("Should return wrapped error for top-level Validated (Invalid)")
    void shouldReturnWrappedErrorForTopLevelInvalid() throws Exception {
      String requestBody =
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
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          // Top-level Validated is unwrapped by ValidatedReturnValueHandler
          // Errors are wrapped in {valid: false, errors: [...]}
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.errors").exists())
          .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("Should return wrapped Validated values for nested Validated in DTO")
    void shouldReturnWrappedValidatedForNestedValues() throws Exception {
      String requestBody =
          """
          [
              {
                  "email": "test@example.com",
                  "firstName": "John",
                  "lastName": "Doe"
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
                  .content(requestBody))
          .andExpect(status().isOk())
          // Nested Validated values are serialized by Jackson with wrappers
          .andExpect(jsonPath("$.results[0].valid").exists())
          .andExpect(jsonPath("$.results[0].value").exists())
          .andExpect(jsonPath("$.results[1].valid").exists())
          .andExpect(jsonPath("$.results[1].errors").exists());
    }
  }

  @Nested
  @DisplayName("Content Type Tests")
  class ContentTypeTests {

    @Test
    @DisplayName("Should return application/json for successful responses")
    void shouldReturnJsonContentTypeForSuccess() throws Exception {
      String requestBody =
          """
          {
              "email": "test@example.com",
              "firstName": "John",
              "lastName": "Doe"
          }
          """;

      mockMvc
          .perform(
              post("/api/validation/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk())
          .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    @DisplayName("Should return application/json for validation error responses")
    void shouldReturnJsonContentTypeForValidationErrors() throws Exception {
      String requestBody =
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
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(header().string("Content-Type", containsString("application/json")));
    }
  }

  @Nested
  @DisplayName("Jackson Integration Tests")
  class JacksonIntegrationTests {

    @Test
    @DisplayName("Should use custom Jackson serializers for nested Validated")
    void shouldUseCustomSerializersForNestedValidated() throws Exception {
      String requestBody =
          """
          [
              {
                  "email": "test@example.com",
                  "firstName": "John",
                  "lastName": "Doe"
              }
          ]
          """;

      String response =
          mockMvc
              .perform(
                  post("/api/validation/batch")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(requestBody))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Verify the response can be parsed
      var result = objectMapper.readTree(response);

      // Verify TAGGED format is used (default)
      var firstResult = result.get("results").get(0);
      assert firstResult.has("valid");
      assert firstResult.get("valid").asBoolean();
      assert firstResult.has("value");
    }

    @Test
    @DisplayName("Should correctly serialize validation error objects")
    void shouldCorrectlySerializeValidationErrors() throws Exception {
      String requestBody =
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
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").isString())
          .andExpect(jsonPath("$.errors[0].message").isString());
    }
  }
}
