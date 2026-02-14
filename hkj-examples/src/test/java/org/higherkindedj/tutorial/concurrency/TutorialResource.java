// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tutorial: Resource Management with Bracket Pattern
 *
 * <p>Learn to manage resources safely using Resource, Higher-Kinded-J's implementation of the
 * bracket pattern. Resource guarantees that acquired resources are always released, even when
 * exceptions occur or tasks are cancelled.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Resource wraps acquire-use-release into a single abstraction
 *   <li>Release is guaranteed even on exceptions or cancellation
 *   <li>Resources compose with flatMap (sequential) and and() (parallel)
 *   <li>LIFO release order ensures dependent resources are released correctly
 * </ul>
 *
 * <p>Prerequisites: Complete TutorialVTask and TutorialScope first
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial: Resource Management with Bracket Pattern")
public class TutorialResource {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ===========================================================================
  // Part 1: Creating Resources
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: Creating Resources")
  class CreatingResources {

    /**
     * Exercise 1: Create a Resource from AutoCloseable
     *
     * <p>Resource.fromAutoCloseable() wraps any AutoCloseable, automatically calling close() when
     * the resource is released. This is the most common pattern for database connections, file
     * handles, and streams.
     *
     * <p>Task: Create a Resource that wraps a TestConnection (an AutoCloseable)
     */
    @Test
    @DisplayName("Exercise 1: Create Resource from AutoCloseable")
    void exercise1_fromAutoCloseable() {
      AtomicBoolean closed = new AtomicBoolean(false);

      // A simple AutoCloseable for testing
      class TestConnection implements AutoCloseable {
        @Override
        public void close() {
          closed.set(true);
        }

        String query() {
          return "result";
        }
      }

      // TODO: Replace answerRequired() with:
      // Resource.fromAutoCloseable(TestConnection::new)
      Resource<TestConnection> connResource = answerRequired();

      // Use the resource and verify it gets closed
      VTask<String> task = connResource.useSync(TestConnection::query);

      String result = task.run();
      assertThat(result).isEqualTo("result");
      assertThat(closed.get()).as("Resource should be closed after use").isTrue();
    }

    /**
     * Exercise 2: Create a Resource with explicit acquire and release
     *
     * <p>Resource.make() allows you to specify custom acquire and release functions. This is useful
     * for resources that don't implement AutoCloseable or need special cleanup logic.
     *
     * <p>Task: Create a Resource with explicit acquire and release functions
     */
    @Test
    @DisplayName("Exercise 2: Create Resource with make()")
    void exercise2_makeResource() {
      List<String> events = new ArrayList<>();

      // TODO: Replace answerRequired() with:
      // Resource.make(
      //     () -> { events.add("acquired"); return "handle"; },
      //     handle -> events.add("released")
      // )
      Resource<String> resource = answerRequired();

      VTask<String> task = resource.useSync(h -> h.toUpperCase());

      String result = task.run();
      assertThat(result).isEqualTo("HANDLE");
      assertThat(events).containsExactly("acquired", "released");
    }
  }

  // ===========================================================================
  // Part 2: Using Resources
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: Using Resources")
  class UsingResources {

    /**
     * Exercise 3: Use a resource to execute a computation
     *
     * <p>The use() method takes a function from the resource to a VTask. The resource is acquired,
     * the function is executed, and the resource is released - guaranteed.
     *
     * <p>Task: Use a resource to execute a VTask computation
     */
    @Test
    @DisplayName("Exercise 3: Use resource with VTask")
    void exercise3_useWithVTask() {
      AtomicInteger counter = new AtomicInteger(0);

      Resource<Integer> resource =
          Resource.make(
              () -> {
                counter.incrementAndGet();
                return 21;
              },
              value -> counter.decrementAndGet());

      // TODO: Replace answerRequired() with:
      // resource.use(value -> VTask.of(() -> value * 2))
      VTask<Integer> task = answerRequired();

      Integer result = task.run();
      assertThat(result).isEqualTo(42);
      assertThat(counter.get()).as("Resource should be released").isZero();
    }

    /**
     * Exercise 4: Verify resource release on failure
     *
     * <p>Resources are released even when the use function throws an exception. This is the core
     * guarantee of the bracket pattern.
     *
     * <p>Task: Verify that resources are released even when computations fail
     */
    @Test
    @DisplayName("Exercise 4: Resource released on failure")
    void exercise4_releasedOnFailure() {
      AtomicBoolean released = new AtomicBoolean(false);

      Resource<String> resource = Resource.make(() -> "resource", value -> released.set(true));

      VTask<String> failingTask =
          resource.use(
              r ->
                  VTask.of(
                      () -> {
                        throw new RuntimeException("Computation failed");
                      }));

      // TODO: Replace answerRequired() with: failingTask.runSafe()
      Try<String> result = answerRequired();

      assertThat(result.isFailure()).isTrue();
      assertThat(released.get()).as("Resource must be released even on failure").isTrue();
    }
  }

  // ===========================================================================
  // Part 3: Composing Resources
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: Composing Resources")
  class ComposingResources {

    /**
     * Exercise 5: Chain dependent resources with flatMap
     *
     * <p>flatMap() chains resource acquisition where the second resource depends on the first.
     * Resources are released in reverse order (LIFO): inner resource first, then outer.
     *
     * <p>Task: Chain two resources where the second depends on the first
     */
    @Test
    @DisplayName("Exercise 5: Chain resources with flatMap")
    void exercise5_flatMapResources() {
      List<String> events = new ArrayList<>();

      Resource<String> outer =
          Resource.make(
              () -> {
                events.add("acquire-outer");
                return "OUTER";
              },
              v -> events.add("release-outer"));

      // TODO: Replace answerRequired() with:
      // outer.flatMap(o -> Resource.make(
      //     () -> { events.add("acquire-inner"); return o + "-INNER"; },
      //     v -> events.add("release-inner")
      // ))
      Resource<String> chained = answerRequired();

      String result = chained.useSync(v -> v).run();

      assertThat(result).isEqualTo("OUTER-INNER");
      // LIFO order: inner released before outer
      assertThat(events)
          .containsExactly("acquire-outer", "acquire-inner", "release-inner", "release-outer");
    }

    /**
     * Exercise 6: Combine independent resources with and()
     *
     * <p>The and() method combines two independent resources into a tuple. Both are acquired and
     * released independently, with LIFO release order.
     *
     * <p>Task: Combine two resources and use them together
     */
    @Test
    @DisplayName("Exercise 6: Combine resources with and()")
    void exercise6_andResources() {
      List<String> events = new ArrayList<>();

      Resource<String> first =
          Resource.make(
              () -> {
                events.add("acquire-first");
                return "first";
              },
              v -> events.add("release-first"));

      Resource<String> second =
          Resource.make(
              () -> {
                events.add("acquire-second");
                return "second";
              },
              v -> events.add("release-second"));

      // TODO: Replace answerRequired() with:
      // first.and(second)
      Resource<Par.Tuple2<String, String>> combined = answerRequired();

      String result = combined.useSync(tuple -> tuple.first() + " + " + tuple.second()).run();

      assertThat(result).isEqualTo("first + second");
      // LIFO: second released before first
      assertThat(events)
          .containsExactly("acquire-first", "acquire-second", "release-second", "release-first");
    }
  }

  // ===========================================================================
  // Part 4: Advanced Patterns
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Advanced Patterns")
  class AdvancedPatterns {

    /**
     * Exercise 7: Transform resource values with map
     *
     * <p>map() transforms the resource value without changing the acquire/release behaviour. The
     * transformation is applied when the resource is used.
     *
     * <p>Task: Transform a resource value using map
     */
    @Test
    @DisplayName("Exercise 7: Transform with map")
    void exercise7_mapResource() {
      Resource<Integer> numberResource = Resource.pure(21);

      // TODO: Replace answerRequired() with:
      // numberResource.map(n -> n * 2)
      Resource<Integer> doubled = answerRequired();

      Integer result = doubled.useSync(n -> n).run();
      assertThat(result).isEqualTo(42);
    }

    /**
     * Exercise 8: Add a finaliser for cleanup actions
     *
     * <p>withFinalizer() adds a cleanup action that runs after the primary release. Finalisers run
     * even if the primary release throws, and multiple finalisers run in reverse order.
     *
     * <p>Task: Add a finaliser to log resource release
     */
    @Test
    @DisplayName("Exercise 8: Add finaliser")
    void exercise8_withFinalizer() {
      List<String> events = new ArrayList<>();

      Resource<String> base =
          Resource.make(
              () -> {
                events.add("acquired");
                return "resource";
              },
              v -> events.add("released"));

      // TODO: Replace answerRequired() with:
      // base.withFinalizer(() -> events.add("finalised"))
      Resource<String> withCleanup = answerRequired();

      withCleanup.useSync(r -> r).run();

      assertThat(events).containsExactly("acquired", "released", "finalised");
    }

    /**
     * Exercise 9: Use pure for values that don't need cleanup
     *
     * <p>Resource.pure() wraps a value that doesn't need resource management. This is useful when
     * composing with other resources but you have a simple value.
     *
     * <p>Task: Create a pure resource and combine it with a managed resource
     */
    @Test
    @DisplayName("Exercise 9: Use pure for simple values")
    void exercise9_pureResource() {
      AtomicBoolean released = new AtomicBoolean(false);

      // TODO: Replace answerRequired() with: Resource.pure(10)
      Resource<Integer> pureResource = answerRequired();

      Resource<String> managedResource = Resource.make(() -> "managed", v -> released.set(true));

      Resource<Par.Tuple2<Integer, String>> combined = pureResource.and(managedResource);

      String result = combined.useSync(t -> t.first() + ": " + t.second()).run();

      assertThat(result).isEqualTo("10: managed");
      assertThat(released.get()).isTrue();
    }
  }

  // ===========================================================================
  // Bonus: Complete Example
  // ===========================================================================

  @Nested
  @DisplayName("Bonus: Complete Example")
  class CompleteExample {

    /**
     * This test demonstrates a complete workflow using Resource:
     *
     * <ol>
     *   <li>Acquire a "database connection"
     *   <li>Acquire a "prepared statement" that depends on the connection
     *   <li>Execute a query
     *   <li>Resources released in reverse order
     * </ol>
     *
     * <p>This is provided as a reference - no exercise to complete.
     */
    @Test
    @DisplayName("Complete workflow example")
    void completeWorkflowExample() {
      List<String> events = new ArrayList<>();

      // Simulated database resources
      Resource<String> connection =
          Resource.make(
              () -> {
                events.add("connect");
                return "conn-1";
              },
              conn -> events.add("disconnect"));

      Resource<String> statement =
          connection.flatMap(
              conn ->
                  Resource.make(
                      () -> {
                        events.add("prepare");
                        return "stmt-for-" + conn;
                      },
                      stmt -> events.add("close-stmt")));

      // Execute query
      VTask<String> query =
          statement.use(
              stmt ->
                  VTask.of(
                      () -> {
                        events.add("execute");
                        return "result-from-" + stmt;
                      }));

      String result = query.run();

      assertThat(result).isEqualTo("result-from-stmt-for-conn-1");
      assertThat(events)
          .containsExactly("connect", "prepare", "execute", "close-stmt", "disconnect");
    }
  }

  /**
   * Congratulations! You've completed Tutorial: Resource Management with Bracket Pattern
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to create resources with fromAutoCloseable() and make()
   *   <li>That release is guaranteed even on exceptions or cancellation
   *   <li>How to chain dependent resources with flatMap (LIFO release)
   *   <li>How to combine independent resources with and()
   *   <li>How to add cleanup actions with withFinalizer()
   *   <li>How to use pure() for values that don't need cleanup
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>Resource implements the bracket pattern: acquire-use-release
   *   <li>Composition maintains LIFO release order automatically
   *   <li>Use fromAutoCloseable for standard Java resources
   *   <li>Combine Resource with Scope for concurrent resource management
   * </ul>
   *
   * <p>Next: Explore the VTask documentation for advanced patterns combining Scope and Resource.
   */
}
