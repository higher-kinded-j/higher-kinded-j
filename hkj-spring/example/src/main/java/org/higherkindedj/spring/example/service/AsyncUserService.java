// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureKindHelper;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service demonstrating async operations using EitherT with CompletableFuture.
 *
 * <p>EitherT allows composing async operations while maintaining type-safe error handling. The key
 * advantage over plain Either is that EitherT enables:
 *
 * <ul>
 *   <li>Non-blocking I/O - async operations don't block request threads
 *   <li>Composable async chains - flatMap over async + error handling
 *   <li>Consistent error propagation across async boundaries
 * </ul>
 *
 * <p>Example async chain:
 *
 * <pre>{@code
 * findByIdAsync(id)
 *     .flatMap(user -> loadProfileAsync(user))
 *     .flatMap(profile -> enrichDataAsync(profile))
 *     // All operations async, all errors propagated as Left
 * }</pre>
 */
@Service
public class AsyncUserService {

  private final UserService userService;
  private final Executor asyncExecutor;
  private final Monad<CompletableFutureKind.Witness> futureMonad;
  private final Monad<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>> eitherTMonad;

  public AsyncUserService(
      UserService userService, @Qualifier("hkjAsyncExecutor") Executor asyncExecutor) {
    this.userService = userService;
    this.asyncExecutor = asyncExecutor;
    this.futureMonad = CompletableFutureMonad.INSTANCE;
    this.eitherTMonad = new EitherTMonad<>(futureMonad);
  }

  /**
   * Find user by ID asynchronously.
   *
   * <p>Simulates an async database call or external service request. The operation runs on the
   * async executor thread pool.
   *
   * @param id the user ID to find
   * @return EitherT wrapping CompletableFuture<Either<DomainError, User>>
   */
  public EitherT<CompletableFutureKind.Witness, DomainError, User> findByIdAsync(String id) {
    // Create async computation that returns Either
    CompletableFuture<Either<DomainError, User>> futureEither =
        CompletableFuture.supplyAsync(
            () -> {
              // Simulate async I/O delay
              sleep(100);

              // Delegate to synchronous service
              // In real app, this would be async database call
              return userService.findById(id);
            },
            asyncExecutor);

    // Wrap in EitherT
    Kind<CompletableFutureKind.Witness, Either<DomainError, User>> kind =
        CompletableFutureKindHelper.FUTURE.widen(futureEither);

    return EitherT.fromKind(kind);
  }

  /**
   * Find user by email asynchronously.
   *
   * <p>Demonstrates async operation that may fail with specific error.
   *
   * @param email the email to search for
   * @return EitherT wrapping async Either
   */
  public EitherT<CompletableFutureKind.Witness, DomainError, User> findByEmailAsync(String email) {
    CompletableFuture<Either<DomainError, User>> futureEither =
        CompletableFuture.supplyAsync(
            () -> {
              sleep(150);

              // Simple search through users (in real app, use async database)
              return userService.findByEmail(email);
            },
            asyncExecutor);

    Kind<CompletableFutureKind.Witness, Either<DomainError, User>> kind =
        CompletableFutureKindHelper.FUTURE.widen(futureEither);

    return EitherT.fromKind(kind);
  }

  /**
   * Get enriched user data by composing multiple async operations.
   *
   * <p>Demonstrates EitherT composition with flatMap:
   *
   * <ol>
   *   <li>Find user by ID (async)
   *   <li>Load additional profile data (async)
   *   <li>Combine into enriched result
   * </ol>
   *
   * <p>If any step fails (Left), the entire chain short-circuits and returns the error. All
   * operations run asynchronously on the thread pool.
   *
   * @param id the user ID
   * @return EitherT with enriched user data
   */
  public EitherT<CompletableFutureKind.Witness, DomainError, EnrichedUser> getEnrichedUserAsync(
      String id) {
    // Step 1: Find user by ID
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, User> userKind =
        findByIdAsync(id);

    // Step 2: Use monad's flatMap to compose operations
    // Note: flatMap signature is flatMap(Function, Kind)
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, EnrichedUser> result =
        eitherTMonad.flatMap(
            user -> {
              // Load profile data (simulated async operation)
              CompletableFuture<Either<DomainError, Profile>> profileFuture =
                  CompletableFuture.supplyAsync(
                      () -> {
                        sleep(100);
                        return Either.<DomainError, Profile>right(
                            new Profile(user.id(), "Premium", 100));
                      },
                      asyncExecutor);

              Kind<CompletableFutureKind.Witness, Either<DomainError, Profile>> profileKind =
                  CompletableFutureKindHelper.FUTURE.widen(profileFuture);

              EitherT<CompletableFutureKind.Witness, DomainError, Profile> profileEitherT =
                  EitherT.fromKind(profileKind);

              // Step 3: Combine user + profile into enriched result using monad's map
              return eitherTMonad.map(profile -> new EnrichedUser(user, profile), profileEitherT);
            },
            userKind);

    // Narrow back to EitherT
    return (EitherT<CompletableFutureKind.Witness, DomainError, EnrichedUser>) result;
  }

  /**
   * Validate user data asynchronously and update.
   *
   * <p>Demonstrates async validation with error accumulation potential.
   *
   * @param id the user ID to update
   * @param newEmail the new email
   * @return EitherT with updated user
   */
  public EitherT<CompletableFutureKind.Witness, DomainError, User> updateEmailAsync(
      String id, String newEmail) {

    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, User> userKind =
        findByIdAsync(id);

    // Note: flatMap signature is flatMap(Function, Kind)
    Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, User> result =
        eitherTMonad.flatMap(
            user -> {
              CompletableFuture<Either<DomainError, User>> updateFuture =
                  CompletableFuture.supplyAsync(
                      () -> {
                        sleep(100);

                        // Validate new email
                        if (newEmail == null || !newEmail.contains("@")) {
                          return Either.left(
                              new UserNotFoundError(id)); // Using UserNotFoundError as example
                        }

                        // Update user
                        User updated =
                            new User(user.id(), newEmail, user.firstName(), user.lastName());
                        return Either.right(updated);
                      },
                      asyncExecutor);

              Kind<CompletableFutureKind.Witness, Either<DomainError, User>> updateKind =
                  CompletableFutureKindHelper.FUTURE.widen(updateFuture);

              return EitherT.fromKind(updateKind);
            },
            userKind);

    return (EitherT<CompletableFutureKind.Witness, DomainError, User>) result;
  }

  /**
   * Async health check endpoint.
   *
   * <p>Simple async operation that always succeeds with a health status.
   *
   * @return EitherT with health status
   */
  public EitherT<
          CompletableFutureKind.Witness,
          DomainError,
          org.higherkindedj.spring.example.controller.AsyncController.HealthStatus>
      getHealthAsync() {
    CompletableFuture<
            Either<
                DomainError,
                org.higherkindedj.spring.example.controller.AsyncController.HealthStatus>>
        futureEither =
            CompletableFuture.supplyAsync(
                () -> {
                  sleep(50);
                  return Either
                      .<DomainError,
                          org.higherkindedj.spring.example.controller.AsyncController.HealthStatus>
                          right(
                              new org.higherkindedj.spring.example.controller.AsyncController
                                  .HealthStatus("healthy", "Async operations are working"));
                },
                asyncExecutor);

    Kind<
            CompletableFutureKind.Witness,
            Either<
                DomainError,
                org.higherkindedj.spring.example.controller.AsyncController.HealthStatus>>
        kind = CompletableFutureKindHelper.FUTURE.widen(futureEither);

    return EitherT.fromKind(kind);
  }

  /** Simulates I/O delay for demonstration purposes. */
  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Enriched user data combining user and profile information. */
  public record EnrichedUser(User user, Profile profile) {}

  /** User profile data (simulated). */
  public record Profile(String userId, String tier, int points) {}
}
