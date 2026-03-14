// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.service.VirtualThreadUserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating virtual thread operations using VTaskPath and VStreamPath.
 *
 * <p>This controller showcases the key advantages of HKJ's virtual thread integration with Spring
 * Boot:
 *
 * <ul>
 *   <li><b>VTaskPath endpoints</b>: Deferred computations on virtual threads with automatic async
 *       response handling. No thread pool configuration needed — virtual threads scale
 *       automatically.
 *   <li><b>VStreamPath endpoints</b>: Pull-based streaming on virtual threads delivered as
 *       Server-Sent Events (SSE). No WebFlux/Reactor complexity — write imperative code that
 *       streams.
 *   <li><b>Structured concurrency</b>: Fan-out to multiple services with automatic cancellation on
 *       failure via {@link org.higherkindedj.hkt.vtask.Scope}.
 * </ul>
 *
 * <p>Compare with {@link AsyncController} which uses CompletableFuturePath with a fixed thread
 * pool. This controller achieves the same (and more) with zero thread pool configuration.
 *
 * <p>Example curl commands:
 *
 * <pre>
 * # VTask: Get user on virtual thread (returns after ~100ms)
 * curl http://localhost:8080/api/vt/users/1
 *
 * # VTask: Structured concurrency — fan-out to 3 services in parallel
 * curl http://localhost:8080/api/vt/users/1/enriched
 *
 * # VStream: Stream all users as SSE events
 * curl -N http://localhost:8080/api/vt/users/stream
 *
 * # VStream: Stream tick events (5 ticks, one every 500ms)
 * curl -N http://localhost:8080/api/vt/ticks?count=5
 * </pre>
 */
@RestController
@RequestMapping("/api/vt")
public class VirtualThreadController {

  private final VirtualThreadUserService vtUserService;

  public VirtualThreadController(VirtualThreadUserService vtUserService) {
    this.vtUserService = vtUserService;
  }

  /**
   * Get user by ID on a virtual thread.
   *
   * <p>Demonstrates basic VTaskPath return type. The VTask computation is deferred — nothing
   * executes until the VTaskPathReturnValueHandler processes it. When executed, it runs on a
   * virtual thread via {@code VTask.runAsync()}.
   *
   * <p>Compared to CompletableFuturePath, this requires no Executor or thread pool — virtual
   * threads are managed by the JVM.
   *
   * @param id the user ID to find
   * @return VTaskPath wrapping the deferred virtual thread computation
   */
  @GetMapping("/users/{id}")
  public VTaskPath<User> getUser(@PathVariable String id) {
    return vtUserService.findById(id);
  }

  /**
   * Get enriched user data using structured concurrency.
   *
   * <p>Demonstrates HKJ's {@link org.higherkindedj.hkt.vtask.Scope} for parallel fan-out. Three
   * virtual threads are forked simultaneously to fetch user, profile, and order data. If any fails,
   * the others are automatically cancelled.
   *
   * <p>This pattern replaces the common {@code CompletableFuture.allOf()} approach with structured,
   * cancellation-aware concurrency.
   *
   * @param id the user ID
   * @return VTaskPath with combined enriched data
   */
  @GetMapping("/users/{id}/enriched")
  public VTaskPath<VirtualThreadUserService.EnrichedUser> getEnrichedUser(@PathVariable String id) {
    return vtUserService.getEnrichedUser(id);
  }

  /**
   * Stream all users as Server-Sent Events on virtual threads.
   *
   * <p>Demonstrates VStreamPath return type for streaming HTTP responses. Each user is emitted as
   * an SSE {@code data:} event with JSON payload. The stream is pull-based — the handler drives
   * element production, providing natural backpressure via virtual thread blocking.
   *
   * <p>No WebFlux, no Reactor, no Flux — just imperative code that streams.
   *
   * @return VStreamPath that emits users as SSE events
   */
  @GetMapping(value = "/users/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public VStreamPath<User> streamUsers() {
    return vtUserService.streamAllUsers();
  }

  /**
   * Stream tick events as Server-Sent Events.
   *
   * <p>Demonstrates VStreamPath with a parameterised infinite stream. The stream uses {@code
   * Path.vstreamIterate()} to generate an infinite sequence, then {@code take(count)} to limit it.
   *
   * @param count the number of tick events to emit (default 10)
   * @return VStreamPath that emits tick events
   */
  @GetMapping(value = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public VStreamPath<VirtualThreadUserService.TickEvent> streamTicks(
      @RequestParam(defaultValue = "10") int count) {
    return vtUserService.streamTicks(count);
  }
}
