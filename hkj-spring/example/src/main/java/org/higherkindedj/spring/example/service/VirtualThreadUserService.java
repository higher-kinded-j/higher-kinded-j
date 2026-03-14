// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.service;

import java.time.Duration;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.springframework.stereotype.Service;

/**
 * Service demonstrating virtual thread operations using VTaskPath and VStreamPath.
 *
 * <p>Unlike {@link AsyncUserService} which requires a fixed thread pool ({@code Executor}), this
 * service uses virtual threads via VTask. No pool sizing, no thread tuning — virtual threads scale
 * to millions of concurrent tasks with minimal memory overhead.
 *
 * <p>Key differences from CompletableFuture-based async:
 *
 * <ul>
 *   <li>No Executor injection needed — virtual threads are managed by the JVM
 *   <li>Lazy: computations are not started until the handler runs them
 *   <li>Composable resilience: retry, circuit breaker, bulkhead, timeout built-in
 *   <li>Structured concurrency via {@link Scope} for fan-out patterns
 * </ul>
 */
@Service
public class VirtualThreadUserService {

  private final UserService userService;

  public VirtualThreadUserService(UserService userService) {
    this.userService = userService;
  }

  /**
   * Find user by ID on a virtual thread.
   *
   * <p>The computation is deferred — nothing executes until the Spring handler invokes the VTask.
   * When executed, it runs on a virtual thread automatically.
   *
   * @param id the user ID to find
   * @return VTaskPath containing the User, or failing with an exception
   */
  public VTaskPath<User> findById(String id) {
    return Path.vtask(
            () -> {
              // Simulate I/O delay (database call, external service)
              Thread.sleep(100);

              return userService
                  .findById(id)
                  .fold(
                      error -> {
                        if (error instanceof UserNotFoundError notFound) {
                          throw new RuntimeException("User not found: " + notFound.userId());
                        }
                        throw new RuntimeException(error.message());
                      },
                      user -> user);
            })
        .timeout(Duration.ofSeconds(5));
  }

  /**
   * Get enriched user data using structured concurrency.
   *
   * <p>Demonstrates {@link Scope#allSucceed()} to fan out to multiple services in parallel on
   * virtual threads, then combine results. If any subtask fails, all others are cancelled
   * automatically.
   *
   * @param id the user ID
   * @return VTaskPath with enriched user data
   */
  @SuppressWarnings("preview")
  public VTaskPath<EnrichedUser> getEnrichedUser(String id) {
    VTask<User> userTask =
        VTask.of(
            () -> {
              Thread.sleep(100);
              return userService
                  .findById(id)
                  .fold(
                      error -> {
                        throw new RuntimeException(error.message());
                      },
                      user -> user);
            });

    VTask<Profile> profileTask =
        VTask.of(
            () -> {
              Thread.sleep(80);
              return new Profile(id, "Premium", 150);
            });

    VTask<OrderSummary> orderTask =
        VTask.of(
            () -> {
              Thread.sleep(60);
              return new OrderSummary(id, 42, 1299.99);
            });

    // Use structured concurrency: all three tasks run in parallel on virtual threads
    // If any fails, the others are cancelled automatically
    return Path.vtaskPath(
            Scope.<Object>allSucceed().fork(userTask).fork(profileTask).fork(orderTask).join())
        .map(
            results -> {
              User user = (User) results.get(0);
              Profile profile = (Profile) results.get(1);
              OrderSummary orders = (OrderSummary) results.get(2);
              return new EnrichedUser(user, profile, orders);
            });
  }

  /**
   * Stream all users as Server-Sent Events on virtual threads.
   *
   * <p>Demonstrates VStreamPath for streaming HTTP responses. Each element is produced lazily on a
   * virtual thread and sent as an SSE event. No WebFlux/Reactor needed.
   *
   * @return VStreamPath that emits users one by one
   */
  public VStreamPath<User> streamAllUsers() {
    return Path.vstream(
        VStream.fromList(userService.findAll().fold(err -> java.util.List.of(), users -> users))
            .map(
                user -> {
                  // Simulate per-element processing delay
                  try {
                    Thread.sleep(200);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  return user;
                }));
  }

  /**
   * Stream a countdown of tick events.
   *
   * <p>Demonstrates VStreamPath with an infinite stream source that is limited via take().
   *
   * @param count the number of ticks to emit
   * @return VStreamPath that emits tick events
   */
  public VStreamPath<TickEvent> streamTicks(int count) {
    return Path.vstreamIterate(1, n -> n + 1)
        .map(
            n -> {
              try {
                Thread.sleep(500);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return new TickEvent(n, System.currentTimeMillis());
            })
        .take(count);
  }

  /** Enriched user combining user, profile, and order data. */
  public record EnrichedUser(User user, Profile profile, OrderSummary orders) {}

  /** User profile data. */
  public record Profile(String userId, String tier, int points) {}

  /** Order summary data. */
  public record OrderSummary(String userId, int totalOrders, double totalAmount) {}

  /** A tick event for streaming demonstration. */
  public record TickEvent(int sequence, long timestamp) {}
}
