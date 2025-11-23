// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for AsyncController - EitherT async return value handling.
 *
 * <p>Tests verify that:
 *
 * <ul>
 *   <li>EitherTReturnValueHandler correctly processes async return values
 *   <li>CompletableFuture operations complete successfully
 *   <li>Either values (Left/Right) map to appropriate HTTP responses
 *   <li>Async operations execute on the configured thread pool
 *   <li>Error handling works correctly in async context
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AsyncControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  /**
   * Test successful async user retrieval.
   *
   * <p>Verifies that EitherT<CompletableFuture, Error, User> with Right value:
   *
   * <ul>
   *   <li>Completes asynchronously on thread pool
   *   <li>Returns HTTP 200 with user JSON
   *   <li>Serializes User record correctly
   * </ul>
   */
  @Test
  void getUserAsync_whenUserExists_returnsUser() throws Exception {
    MvcResult mvcResult =
        mockMvc.perform(get("/api/async/users/1")).andExpect(request().asyncStarted()).andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.firstName").value("Alice"))
        .andExpect(jsonPath("$.lastName").value("Smith"));
  }

  /**
   * Test async user not found error.
   *
   * <p>Verifies that EitherT with Left(UserNotFoundError):
   *
   * <ul>
   *   <li>Returns HTTP 404 (based on *NotFound* class name pattern)
   *   <li>Includes error wrapper with success=false
   *   <li>Serializes error details correctly
   * </ul>
   */
  @Test
  void getUserAsync_whenUserNotFound_returns404() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(get("/api/async/users/999"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.userId").value("999"));
  }

  /**
   * Test async user lookup by email - success case.
   *
   * <p>Verifies async operation with query parameter:
   *
   * <ul>
   *   <li>Async search completes successfully
   *   <li>Returns correct user data
   * </ul>
   */
  @Test
  void getUserByEmailAsync_whenEmailExists_returnsUser() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(get("/api/async/users/by-email").param("email", "bob@example.com"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("2"))
        .andExpect(jsonPath("$.email").value("bob@example.com"))
        .andExpect(jsonPath("$.firstName").value("Bob"))
        .andExpect(jsonPath("$.lastName").value("Johnson"));
  }

  /** Test async email lookup not found. */
  @Test
  void getUserByEmailAsync_whenEmailNotFound_returns404() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(get("/api/async/users/by-email").param("email", "unknown@example.com"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false));
  }

  /**
   * Test async enriched user data with flatMap composition.
   *
   * <p>Verifies that composed async operations:
   *
   * <ul>
   *   <li>First async call: findByIdAsync()
   *   <li>Second async call: load profile data
   *   <li>Combination: map to EnrichedUser
   *   <li>All operations execute asynchronously
   *   <li>Result includes both user and profile data
   * </ul>
   */
  @Test
  void getEnrichedUserAsync_whenUserExists_returnsEnrichedData() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(get("/api/async/users/1/enriched"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.user.id").value("1"))
        .andExpect(jsonPath("$.user.email").value("alice@example.com"))
        .andExpect(jsonPath("$.profile.userId").value("1"))
        .andExpect(jsonPath("$.profile.tier").value("Premium"))
        .andExpect(jsonPath("$.profile.points").value(100));
  }

  /**
   * Test enriched data when user not found.
   *
   * <p>Verifies that flatMap chain short-circuits on first error:
   *
   * <ul>
   *   <li>findByIdAsync() returns Left(error)
   *   <li>Subsequent async operations are skipped
   *   <li>Error propagates to HTTP response
   * </ul>
   */
  @Test
  void getEnrichedUserAsync_whenUserNotFound_returns404() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(get("/api/async/users/999/enriched"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.userId").value("999"));
  }

  /**
   * Test async email update - success case.
   *
   * <p>Verifies async update operation:
   *
   * <ul>
   *   <li>User lookup is async
   *   <li>Validation runs asynchronously
   *   <li>Update completes successfully
   *   <li>Returns updated user data
   * </ul>
   */
  @Test
  void updateEmailAsync_withValidEmail_returnsUpdatedUser() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(put("/api/async/users/1/email").param("newEmail", "alice.updated@example.com"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.email").value("alice.updated@example.com"))
        .andExpect(jsonPath("$.firstName").value("Alice"))
        .andExpect(jsonPath("$.lastName").value("Smith"));
  }

  /**
   * Test async email update with invalid email format.
   *
   * <p>Verifies async validation:
   *
   * <ul>
   *   <li>User lookup succeeds (async)
   *   <li>Validation fails (async)
   *   <li>Returns error via Left path
   * </ul>
   */
  @Test
  void updateEmailAsync_withInvalidEmail_returns404() throws Exception {
    // Note: Current implementation uses UserNotFoundError for invalid email
    // In production, you'd use a proper ValidationError type
    MvcResult mvcResult =
        mockMvc
            .perform(put("/api/async/users/1/email").param("newEmail", "invalid-email"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false));
  }

  /**
   * Test async update when user not found.
   *
   * <p>Verifies that flatMap chain short-circuits:
   *
   * <ul>
   *   <li>findByIdAsync() returns Left(UserNotFoundError)
   *   <li>Update operation is skipped
   *   <li>Error propagates to response
   * </ul>
   */
  @Test
  void updateEmailAsync_whenUserNotFound_returns404() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(put("/api/async/users/999/email").param("newEmail", "test@example.com"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.userId").value("999"));
  }

  /**
   * Test async health check endpoint.
   *
   * <p>Verifies simple async operation that always succeeds:
   *
   * <ul>
   *   <li>Always returns Right(message)
   *   <li>Confirms async infrastructure is working
   * </ul>
   */
  @Test
  void getAsyncHealth_returnsHealthyStatus() throws Exception {
    MvcResult mvcResult =
        mockMvc.perform(get("/api/async/health")).andExpect(request().asyncStarted()).andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("healthy"))
        .andExpect(jsonPath("$.message").value("Async operations are working"));
  }

  /**
   * Test concurrent async requests.
   *
   * <p>While MockMvc doesn't truly test concurrency, this verifies:
   *
   * <ul>
   *   <li>Multiple async operations can be initiated
   *   <li>Each completes independently
   *   <li>No shared state issues
   * </ul>
   *
   * <p>Note: For true concurrency testing, use the curl commands from TESTING.md with multiple
   * simultaneous requests.
   */
  @Test
  void multipleAsyncRequests_completeIndependently() throws Exception {
    // Request 1
    MvcResult mvcResult1 =
        mockMvc.perform(get("/api/async/users/1")).andExpect(request().asyncStarted()).andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("1"));

    // Request 2
    MvcResult mvcResult2 =
        mockMvc.perform(get("/api/async/users/2")).andExpect(request().asyncStarted()).andReturn();

    mockMvc
        .perform(asyncDispatch(mvcResult2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("2"));

    // Request 3 - Not found
    MvcResult mvcResult3 =
        mockMvc
            .perform(get("/api/async/users/999"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult3)).andExpect(status().isNotFound());
  }
}
