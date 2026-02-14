// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.service.AsyncUserService;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating async operations using CompletableFuturePath.
 *
 * <p>All endpoints return {@code CompletableFuturePath<T>} which:
 *
 * <ul>
 *   <li>Executes async on the configured thread pool
 *   <li>Frees up request threads for other work
 *   <li>Automatically maps to HTTP responses via CompletableFuturePathReturnValueHandler
 * </ul>
 *
 * <p>Response mapping:
 *
 * <ul>
 *   <li>Successful completion → HTTP 200 with value as JSON
 *   <li>UserNotFoundException → HTTP 500 with error JSON (configurable)
 *   <li>ValidationException → HTTP 500 with error JSON
 *   <li>Other exceptions → HTTP 500 with exception details
 * </ul>
 *
 * <p>Example curl commands:
 *
 * <pre>
 * # Successful async request (returns after ~100ms delay)
 * curl http://localhost:8080/api/async/users/1
 *
 * # Async error response (returns 500 after delay)
 * curl http://localhost:8080/api/async/users/999
 *
 * # Composed async operations (multiple async calls chained)
 * curl http://localhost:8080/api/async/users/1/enriched
 *
 * # Async email update
 * curl -X PUT http://localhost:8080/api/async/users/1/email?newEmail=newemail@example.com
 * </pre>
 */
@RestController
@RequestMapping("/api/async")
public class AsyncController {

  private final AsyncUserService asyncUserService;

  public AsyncController(AsyncUserService asyncUserService) {
    this.asyncUserService = asyncUserService;
  }

  /**
   * Get user by ID asynchronously.
   *
   * <p>Demonstrates basic async operation with CompletableFuturePath. The operation runs on the
   * async thread pool and returns immediately, freeing the request thread.
   *
   * @param id the user ID to find
   * @return CompletableFuturePath wrapping async User
   */
  @GetMapping("/users/{id}")
  public CompletableFuturePath<User> getUserAsync(@PathVariable String id) {
    return asyncUserService.findByIdAsync(id);
  }

  /**
   * Find user by email asynchronously.
   *
   * @param email the email to search for
   * @return CompletableFuturePath wrapping async result
   */
  @GetMapping("/users/by-email")
  public CompletableFuturePath<User> getUserByEmailAsync(@RequestParam String email) {
    return asyncUserService.findByEmailAsync(email);
  }

  /**
   * Get enriched user data with composed async operations.
   *
   * <p>Demonstrates CompletableFuturePath composition:
   *
   * <ol>
   *   <li>Async find user by ID
   *   <li>Async load profile data
   *   <li>Combine into enriched result
   * </ol>
   *
   * @param id the user ID
   * @return CompletableFuturePath with enriched user data
   */
  @GetMapping("/users/{id}/enriched")
  public CompletableFuturePath<AsyncUserService.EnrichedUser> getEnrichedUserAsync(
      @PathVariable String id) {
    return asyncUserService.getEnrichedUserAsync(id);
  }

  /**
   * Update user email asynchronously with validation.
   *
   * @param id the user ID to update
   * @param newEmail the new email address
   * @return CompletableFuturePath with updated user
   */
  @PutMapping("/users/{id}/email")
  public CompletableFuturePath<User> updateEmailAsync(
      @PathVariable String id, @RequestParam String newEmail) {
    return asyncUserService.updateEmailAsync(id, newEmail);
  }

  /**
   * Health check endpoint (async).
   *
   * @return CompletableFuturePath with health status
   */
  @GetMapping("/health")
  public CompletableFuturePath<HealthStatus> getAsyncHealth() {
    return asyncUserService.getHealthAsync();
  }

  /** Health status response. */
  public record HealthStatus(String status, String message) {}
}
