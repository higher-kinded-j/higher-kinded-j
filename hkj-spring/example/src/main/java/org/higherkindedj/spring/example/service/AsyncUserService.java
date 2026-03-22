// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.spring.example.controller.AsyncController;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service demonstrating async operations using CompletableFuturePath.
 *
 * <p>CompletableFuturePath provides a clean API for async operations with the Effect Path API.
 * Error handling uses exceptions which are automatically converted to appropriate HTTP responses by
 * the CompletableFuturePathReturnValueHandler.
 *
 * <p>Example async chain:
 *
 * <pre>{@code
 * findByIdAsync(id)
 *     .map(user -> enrichUser(user))
 *     .flatMap(user -> loadProfileAsync(user))
 * }</pre>
 */
@Service
public class AsyncUserService {

  private final UserService userService;
  private final Executor asyncExecutor;

  /**
   * Constructs an AsyncUserService.
   *
   * @param userService the underlying user service
   * @param asyncExecutor the executor for async operations
   */
  public AsyncUserService(
      UserService userService, @Qualifier("hkjAsyncExecutor") Executor asyncExecutor) {
    this.userService = userService;
    this.asyncExecutor = asyncExecutor;
  }

  /**
   * Find user by ID asynchronously.
   *
   * <p>Simulates an async database call or external service request. The operation runs on the
   * async executor thread pool.
   *
   * @param id the user ID to find
   * @return CompletableFuturePath containing the User, or failing with UserNotFoundException
   */
  public CompletableFuturePath<User> findByIdAsync(String id) {
    CompletableFuture<User> future =
        CompletableFuture.supplyAsync(
            () -> {
              // Simulate async I/O delay
              sleep(100);

              // Delegate to synchronous service and convert Either to exception
              return userService
                  .findById(id)
                  .fold(
                      error -> {
                        if (error instanceof UserNotFoundError notFound) {
                          throw new UserNotFoundException(notFound.userId());
                        }
                        throw new RuntimeException(error.message());
                      },
                      user -> user);
            },
            asyncExecutor);

    return Path.future(future);
  }

  /**
   * Find user by email asynchronously.
   *
   * @param email the email to search for
   * @return CompletableFuturePath containing the User
   */
  public CompletableFuturePath<User> findByEmailAsync(String email) {
    CompletableFuture<User> future =
        CompletableFuture.supplyAsync(
            () -> {
              sleep(150);

              return userService
                  .findByEmail(email)
                  .fold(
                      error -> {
                        throw new UserNotFoundException(email);
                      },
                      user -> user);
            },
            asyncExecutor);

    return Path.future(future);
  }

  /**
   * Get enriched user data by composing multiple async operations.
   *
   * <p>Demonstrates CompletableFuturePath composition with via:
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
  public CompletableFuturePath<EnrichedUser> getEnrichedUserAsync(String id) {
    return findByIdAsync(id)
        .via(
            user -> {
              CompletableFuture<EnrichedUser> profileFuture =
                  CompletableFuture.supplyAsync(
                      () -> {
                        sleep(100);
                        Profile profile = new Profile(user.id(), "Premium", 100);
                        return new EnrichedUser(user, profile);
                      },
                      asyncExecutor);
              return Path.future(profileFuture);
            });
  }

  /**
   * Update user email asynchronously with validation.
   *
   * @param id the user ID to update
   * @param newEmail the new email address
   * @return CompletableFuturePath with updated user
   */
  public CompletableFuturePath<User> updateEmailAsync(String id, String newEmail) {
    return findByIdAsync(id)
        .via(
            user -> {
              CompletableFuture<User> updateFuture =
                  CompletableFuture.supplyAsync(
                      () -> {
                        sleep(100);

                        // Validate new email
                        if (newEmail == null || !newEmail.contains("@")) {
                          throw new ValidationException("Invalid email format");
                        }

                        // Update user
                        return new User(user.id(), newEmail, user.firstName(), user.lastName());
                      },
                      asyncExecutor);
              return Path.future(updateFuture);
            });
  }

  /**
   * Async health check endpoint.
   *
   * @return CompletableFuturePath with health status
   */
  public CompletableFuturePath<AsyncController.HealthStatus> getHealthAsync() {
    CompletableFuture<AsyncController.HealthStatus> future =
        CompletableFuture.supplyAsync(
            () -> {
              sleep(50);
              return new AsyncController.HealthStatus("healthy", "Async operations are working");
            },
            asyncExecutor);

    return Path.future(future);
  }

  /**
   * Simulates I/O delay for demonstration purposes.
   *
   * @param millis the number of milliseconds to sleep
   */
  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Enriched user data combining user and profile information.
   *
   * @param user the user
   * @param profile the user's profile
   */
  public record EnrichedUser(User user, Profile profile) {}

  /**
   * User profile data (simulated).
   *
   * @param userId the user ID
   * @param tier the membership tier
   * @param points the loyalty points
   */
  public record Profile(String userId, String tier, int points) {}

  /** Exception thrown when a user is not found. */
  public static class UserNotFoundException extends RuntimeException {
    /** The ID of the user that was not found. */
    private final String userId;

    /**
     * Constructs a UserNotFoundException.
     *
     * @param userId the ID of the user that was not found
     */
    public UserNotFoundException(String userId) {
      super("User not found: " + userId);
      this.userId = userId;
    }

    /**
     * Returns the user ID that was not found.
     *
     * @return the user ID
     */
    public String getUserId() {
      return userId;
    }
  }

  /** Exception thrown for validation errors. */
  public static class ValidationException extends RuntimeException {
    /**
     * Constructs a ValidationException.
     *
     * @param message the validation error message
     */
    public ValidationException(String message) {
      super(message);
    }
  }
}
