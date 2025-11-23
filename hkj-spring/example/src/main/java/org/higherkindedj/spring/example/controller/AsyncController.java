// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.service.AsyncUserService;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating async operations using EitherT with CompletableFuture.
 *
 * <p>All endpoints return {@code EitherT<CompletableFuture.Witness, DomainError, T>} which:
 *
 * <ul>
 *   <li>Executes async on the configured thread pool
 *   <li>Frees up request threads for other work
 *   <li>Maintains type-safe error handling
 *   <li>Automatically maps to HTTP responses via EitherTReturnValueHandler
 * </ul>
 *
 * <p>Response mapping:
 *
 * <ul>
 *   <li>Right(value) → HTTP 200 with value as JSON (after async completion)
 *   <li>Left(UserNotFoundError) → HTTP 404 with error JSON (after async completion)
 *   <li>Left(other errors) → HTTP 400/403/401 based on error type
 *   <li>Exception during async → HTTP 500 with exception details
 * </ul>
 *
 * <p>Example curl commands:
 *
 * <pre>
 * # Successful async request (returns after ~100ms delay)
 * curl http://localhost:8080/api/async/users/1
 *
 * # Async error response (returns 404 after delay)
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
   * <p>Demonstrates basic async operation with EitherT. The operation runs on the async thread pool
   * and returns immediately, freeing the request thread. Spring handles the async completion.
   *
   * @param id the user ID to find
   * @return EitherT wrapping async Either<DomainError, User>
   */
  @GetMapping("/users/{id}")
  public EitherT<CompletableFutureKind.Witness, DomainError, User> getUserAsync(
      @PathVariable String id) {
    return asyncUserService.findByIdAsync(id);
    // EitherTReturnValueHandler automatically:
    // 1. Unwraps the CompletableFuture
    // 2. Waits for async completion (non-blocking)
    // 3. Folds the Either to determine HTTP response
    // 4. Returns 200 + user JSON or 404 + error JSON
  }

  /**
   * Find user by email asynchronously.
   *
   * <p>Demonstrates async query with different success criteria.
   *
   * @param email the email to search for
   * @return EitherT wrapping async result
   */
  @GetMapping("/users/by-email")
  public EitherT<CompletableFutureKind.Witness, DomainError, User> getUserByEmailAsync(
      @RequestParam String email) {
    return asyncUserService.findByEmailAsync(email);
    // Async operation with email lookup
    // Left(error) if not found or invalid email
    // Right(user) if found
  }

  /**
   * Get enriched user data with composed async operations.
   *
   * <p>Demonstrates EitherT composition:
   *
   * <ol>
   *   <li>Async find user by ID
   *   <li>Async load profile data
   *   <li>Combine into enriched result
   * </ol>
   *
   * <p>All operations run asynchronously, and if any step fails (Left), the entire chain
   * short-circuits and returns the error.
   *
   * @param id the user ID
   * @return EitherT with enriched user data
   */
  @GetMapping("/users/{id}/enriched")
  public EitherT<CompletableFutureKind.Witness, DomainError, AsyncUserService.EnrichedUser>
      getEnrichedUserAsync(@PathVariable String id) {
    return asyncUserService.getEnrichedUserAsync(id);
    // Multiple async operations chained with flatMap
    // If findByIdAsync returns Left, the profile loading is skipped
    // If profile loading fails, error is propagated
    // Only if both succeed do we get Right(EnrichedUser)
  }

  /**
   * Update user email asynchronously with validation.
   *
   * <p>Demonstrates async update operation with validation.
   *
   * @param id the user ID to update
   * @param newEmail the new email address
   * @return EitherT with updated user
   */
  @PutMapping("/users/{id}/email")
  public EitherT<CompletableFutureKind.Witness, DomainError, User> updateEmailAsync(
      @PathVariable String id, @RequestParam String newEmail) {
    return asyncUserService.updateEmailAsync(id, newEmail);
    // Async validation + update
    // Left if user not found or email invalid
    // Right with updated user if successful
  }

  /**
   * Health check endpoint (async).
   *
   * <p>Simple async health check that returns status asynchronously.
   *
   * @return EitherT with health status
   */
  @GetMapping("/health")
  public EitherT<CompletableFutureKind.Witness, DomainError, HealthStatus> getAsyncHealth() {
    return asyncUserService.getHealthAsync();
  }

  /** Health status response. */
  public record HealthStatus(String status, String message) {}
}
