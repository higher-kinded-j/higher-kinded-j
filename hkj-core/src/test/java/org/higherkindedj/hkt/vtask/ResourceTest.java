// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Test suite for Resource - the bracket pattern for safe resource management. */
@DisplayName("Resource<A> Test Suite")
class ResourceTest {

  /** A Resource derived from a base one, named for the parameterised test's display name. */
  record Derivation(String name, Function<Resource<String>, Resource<?>> derive) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Derivation> derivations() {
    return Stream.of(
        new Derivation("map", r -> r.map(String::toUpperCase)),
        new Derivation("flatMap", r -> r.flatMap(Resource::pure)),
        new Derivation("and", r -> r.and(Resource.pure("other"))),
        new Derivation("withFinalizer", r -> r.withFinalizer(() -> {})),
        new Derivation("onFailure", r -> r.onFailure(_ -> {})));
  }

  /** A use of a held value that fails with the message "failed", named for the display name. */
  record FailureWhileHeld(String name, Function<Resource<String>, VTask<?>> use) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<FailureWhileHeld> failuresWhileHeld() {
    return Stream.of(
        new FailureWhileHeld(
            "the use function throws",
            r ->
                r.use(
                    _ -> {
                      throw new IllegalStateException("failed");
                    })),
        new FailureWhileHeld(
            "the task fails", r -> r.use(_ -> VTask.fail(new IllegalStateException("failed")))),
        new FailureWhileHeld(
            "map's function throws",
            r ->
                r.map(
                        _ -> {
                          throw new IllegalStateException("failed");
                        })
                    .useSync(v -> v)),
        new FailureWhileHeld(
            "flatMap's inner acquire fails",
            r ->
                r.flatMap(
                        _ ->
                            Resource.make(
                                () -> {
                                  throw new IllegalStateException("failed");
                                },
                                _ -> {}))
                    .useSync(v -> v)),
        new FailureWhileHeld(
            "and's second acquire fails",
            r ->
                r.and(
                        Resource.make(
                            () -> {
                              throw new IllegalStateException("failed");
                            },
                            _ -> {}))
                    .useSync(v -> v)));
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("make() creates resource with acquire and release")
    void makeCreatesResource() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThat(resource).isNotNull();
    }

    @Test
    @DisplayName("make() validates non-null acquire")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void makeValidatesNonNullAcquire() {
      assertThatNullPointerException()
          .isThrownBy(() -> Resource.make(null, _ -> {}))
          .withMessageContaining("acquire for");
    }

    @Test
    @DisplayName("make() validates non-null release")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void makeValidatesNonNullRelease() {
      assertThatNullPointerException()
          .isThrownBy(() -> Resource.make(() -> "test", null))
          .withMessageContaining("release for");
    }

    @Test
    @DisplayName("fromAutoCloseable() creates resource that auto-closes")
    void fromAutoCloseableCreatesResource() {
      AutoCloseable closeable = () -> {};

      Resource<AutoCloseable> resource = Resource.fromAutoCloseable(() -> closeable);

      assertThat(resource).isNotNull();
    }

    @Test
    @DisplayName("fromAutoCloseable() validates non-null acquire")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void fromAutoCloseableValidatesNonNullAcquire() {
      assertThatNullPointerException()
          .isThrownBy(() -> Resource.fromAutoCloseable(null))
          .withMessageContaining("acquire for");
    }

    @Test
    @DisplayName("fromAutoCloseable() silently handles close exception")
    void fromAutoCloseableHandlesCloseException() {
      AtomicBoolean closeCalled = new AtomicBoolean(false);

      AutoCloseable failingCloseable =
          () -> {
            closeCalled.set(true);
            throw new RuntimeException("close failed");
          };

      Resource<AutoCloseable> resource = Resource.fromAutoCloseable(() -> failingCloseable);

      // Should not throw even though close fails
      String result = resource.useSync(_ -> "success").run();

      assertThat(result).isEqualTo("success");
      assertThat(closeCalled).isTrue();
    }

    @Test
    @DisplayName("pure() creates resource without acquire/release")
    void pureCreatesNoOpResource() {
      Resource<String> resource = Resource.pure("test");

      String result = resource.useSync(s -> s).run();

      assertThat(result).isEqualTo("test");
    }
  }

  @Nested
  @DisplayName("Use Operations")
  class UseOperationsTests {

    @Test
    @DisplayName("use() acquires, uses, and releases resource")
    void useAcquiresUsesAndReleases() {
      List<String> events = new ArrayList<>();

      Resource<String> resource =
          Resource.make(
              () -> {
                events.add("acquire");
                return "resource";
              },
              _ -> events.add("release"));

      VTask<String> task =
          resource.use(
              r -> {
                events.add("use");
                return VTask.succeed(r.toUpperCase());
              });

      String result = task.run();

      assertThat(result).isEqualTo("RESOURCE");
      assertThat(events).containsExactly("acquire", "use", "release");
    }

    @Test
    @DisplayName("use() releases resource even on exception")
    void useReleasesOnException() {
      AtomicBoolean released = new AtomicBoolean(false);

      Resource<String> resource = Resource.make(() -> "test", _ -> released.set(true));

      VTask<String> task =
          resource.use(
              _ ->
                  VTask.of(
                      () -> {
                        throw new RuntimeException("intentional error");
                      }));

      assertThatThrownBy(task::run).isInstanceOf(RuntimeException.class);
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("use() validates non-null function")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void useValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.use(null))
          .withMessageContaining("f for");
    }

    @Test
    @DisplayName("use() validates function does not return null")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void useValidatesFunctionReturnsNonNull() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "test", _ -> released.set(true));

      VTask<String> task = resource.use(_ -> null);

      assertThatNullPointerException().isThrownBy(task::run).withMessageContaining("returned null");
      assertThat(released).isTrue(); // Resource should still be released
    }

    @Test
    @DisplayName("useSync() validates non-null function")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void useSyncValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.useSync(null))
          .withMessageContaining("f for");
    }

    @Test
    @DisplayName("useSync() works with non-effectful functions")
    void useSyncWorksWithSimpleFunctions() {
      AtomicBoolean released = new AtomicBoolean(false);

      Resource<String> resource = Resource.make(() -> "hello", _ -> released.set(true));

      VTask<Integer> task = resource.useSync(String::length);

      Integer result = task.run();

      assertThat(result).isEqualTo(5);
      assertThat(released).isTrue();
    }
  }

  @Nested
  @DisplayName("Composition")
  class CompositionTests {

    @Test
    @DisplayName("map() transforms resource value and releases")
    void mapTransformsResourceValue() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "hello", _ -> released.set(true));

      Resource<Integer> mapped = resource.map(String::length);

      Integer result = mapped.useSync(i -> i).run();

      assertThat(result).isEqualTo(5);
      assertThat(released).as("Resource should be released after map and use").isTrue();
    }

    @Test
    @DisplayName("map() releases what acquire returned, as use() on the unmapped resource does")
    @SuppressWarnings("DataFlowIssue") // the acquire deliberately returns null
    void mapReleasesWhatAcquireReturned() {
      AtomicInteger releases = new AtomicInteger();
      Resource<String> nullResource = Resource.make(() -> null, _ -> releases.incrementAndGet());

      Resource<Integer> mapped = nullResource.map(_ -> 42);

      Integer result = mapped.useSync(i -> i).run();

      assertThat(result).isEqualTo(42);
      assertThat(releases).hasValue(1);
    }

    @Test
    @DisplayName("map() validates non-null function")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.map(null))
          .withMessageContaining("f for");
    }

    @Test
    @DisplayName("map() releases resource even when use throws exception")
    void mapReleasesOnUseException() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "hello", _ -> released.set(true));

      Resource<Integer> mapped = resource.map(String::length);

      VTask<Integer> failingTask =
          mapped.use(
              _ ->
                  VTask.of(
                      () -> {
                        throw new RuntimeException("use failed");
                      }));

      assertThatThrownBy(failingTask::run).hasMessageContaining("use failed");
      assertThat(released).as("Resource should be released even when use fails").isTrue();
    }

    @Test
    @DisplayName("map() releases resource when the map function throws")
    void mapReleasesWhenMapFunctionThrows() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "hello", _ -> released.set(true));

      Resource<Integer> mapped =
          resource.map(
              _ -> {
                throw new RuntimeException("map function failed");
              });

      assertThatThrownBy(() -> mapped.useSync(i -> i).run())
          .hasMessageContaining("map function failed");
      assertThat(released).as("Resource should be released when map's function throws").isTrue();
    }

    @Test
    @DisplayName("map() suppresses release exception when the map function throws")
    void mapSuppressesReleaseExceptionWhenMapFunctionThrows() {
      Resource<String> resource =
          Resource.make(
              () -> "hello",
              _ -> {
                throw new RuntimeException("release failed");
              });

      Resource<Integer> mapped =
          resource.map(
              _ -> {
                throw new RuntimeException("map function failed");
              });

      assertThatThrownBy(() -> mapped.useSync(i -> i).run())
          .hasMessage("map function failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .singleElement()
                      .satisfies(s -> assertThat(s).hasMessage("release failed")));
    }

    @Test
    @DisplayName("flatMap() chains resource acquisition and releases in LIFO order")
    void flatMapChainsResources() {
      List<String> events = new ArrayList<>();

      Resource<String> first =
          Resource.make(
              () -> {
                events.add("acquire-first");
                return "first";
              },
              _ -> events.add("release-first"));

      Resource<String> chained =
          first.flatMap(
              f ->
                  Resource.make(
                      () -> {
                        events.add("acquire-second");
                        return f + "-second";
                      },
                      _ -> events.add("release-second")));

      String result = chained.useSync(s -> s).run();

      assertThat(result).isEqualTo("first-second");
      assertThat(events)
          .as("Acquire in order, release in LIFO order")
          .containsExactly("acquire-first", "acquire-second", "release-second", "release-first");
    }

    @Test
    @DisplayName("flatMap() releases what the outer acquire returned, as use() on it does")
    @SuppressWarnings("DataFlowIssue") // the acquire deliberately returns null
    void flatMapReleasesWhatOuterAcquireReturned() {
      AtomicInteger outerReleases = new AtomicInteger();
      AtomicBoolean innerReleased = new AtomicBoolean(false);

      Resource<String> nullResource =
          Resource.make(() -> null, _ -> outerReleases.incrementAndGet());

      Resource<Integer> chained =
          nullResource.flatMap(_ -> Resource.make(() -> 42, _ -> innerReleased.set(true)));

      Integer result = chained.useSync(i -> i).run();

      assertThat(result).isEqualTo(42);
      assertThat(innerReleased).as("Inner resource should be released").isTrue();
      assertThat(outerReleases).hasValue(1);
    }

    @Test
    @DisplayName(
        "flatMap() releases each inner value with the release of the Resource that made it")
    void flatMapReleasesEachInnerWithItsOwnRelease() {
      List<String> events = new ArrayList<>();
      AtomicInteger counter = new AtomicInteger();

      Resource<String> chained =
          Resource.make(() -> "conn" + counter.incrementAndGet(), _ -> {})
              .flatMap(
                  conn ->
                      Resource.make(
                          () -> conn + "-stmt", stmt -> events.add(conn + " closes " + stmt)));

      chained.use(outer -> chained.use(inner -> VTask.succeed(outer + inner))).run();

      assertThat(events).containsExactly("conn2 closes conn2-stmt", "conn1 closes conn1-stmt");
    }

    @Test
    @DisplayName("flatMap() validates non-null function")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void flatMapValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.flatMap(null))
          .withMessageContaining("f for");
    }

    @Test
    @DisplayName("flatMap() releases first if second acquire fails")
    void flatMapReleasesFirstIfSecondFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", _ -> firstReleased.set(true));

      Resource<String> chained =
          first.flatMap(
              _ ->
                  Resource.make(
                      () -> {
                        throw new RuntimeException("second acquire failed");
                      },
                      _ -> {}));

      assertThatThrownBy(() -> chained.useSync(s -> s).run())
          .hasMessageContaining("second acquire failed");
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("flatMap() suppresses release exception when second acquire fails")
    void flatMapSuppressesReleaseExceptionWhenSecondAcquireFails() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("release failed");
              });

      Resource<String> chained =
          first.flatMap(
              _ ->
                  Resource.make(
                      () -> {
                        throw new RuntimeException("second acquire failed");
                      },
                      _ -> {}));

      assertThatThrownBy(() -> chained.useSync(s -> s).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("second acquire failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .hasSize(1)
                      .allMatch(s -> s.getMessage().contains("release failed")));
    }

    @Test
    @DisplayName("flatMap() validates function does not return null")
    @SuppressWarnings("DataFlowIssue") // the mapper deliberately returns null
    void flatMapValidatesFunctionReturnsNonNull() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "test", _ -> released.set(true));

      Resource<String> chained = resource.flatMap(_ -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> chained.useSync(s -> s).run())
          .withMessageContaining("returned null");
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("flatMap() handles inner release exception")
    void flatMapHandlesInnerReleaseException() {
      AtomicBoolean outerReleased = new AtomicBoolean(false);

      Resource<String> outer = Resource.make(() -> "outer", _ -> outerReleased.set(true));

      Resource<String> chained =
          outer.flatMap(
              _ ->
                  Resource.make(
                      () -> "inner",
                      _ -> {
                        throw new RuntimeException("inner release failed");
                      }));

      assertThatThrownBy(() -> chained.useSync(s -> s).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release inner resource")
          .hasCauseInstanceOf(RuntimeException.class)
          .satisfies(e -> assertThat(e.getCause().getMessage()).contains("inner release failed"));
      assertThat(outerReleased).as("Outer resource should still be released").isTrue();
    }

    @Test
    @DisplayName("flatMap() handles outer release exception")
    void flatMapHandlesOuterReleaseException() {
      AtomicBoolean innerReleased = new AtomicBoolean(false);

      Resource<String> outer =
          Resource.make(
              () -> "outer",
              _ -> {
                throw new RuntimeException("outer release failed");
              });

      Resource<String> chained =
          outer.flatMap(_ -> Resource.make(() -> "inner", _ -> innerReleased.set(true)));

      assertThatThrownBy(() -> chained.useSync(s -> s).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release outer resource")
          .hasCauseInstanceOf(RuntimeException.class)
          .satisfies(e -> assertThat(e.getCause().getMessage()).contains("outer release failed"));
      assertThat(innerReleased).as("Inner resource should be released first").isTrue();
    }

    @Test
    @DisplayName("flatMap() handles both releases throwing exceptions")
    void flatMapHandlesBothReleasesThrowingExceptions() {
      Resource<String> outer =
          Resource.make(
              () -> "outer",
              _ -> {
                throw new RuntimeException("outer release failed");
              });

      Resource<String> chained =
          outer.flatMap(
              _ ->
                  Resource.make(
                      () -> "inner",
                      _ -> {
                        throw new RuntimeException("inner release failed");
                      }));

      // When both releases throw:
      // - Inner release exception is suppressed by outer
      // - Outer release exception is wrapped as cause
      assertThatThrownBy(() -> chained.useSync(s -> s).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release outer resource")
          .satisfies(
              e ->
                  assertThat(e.getCause().getSuppressed())
                      .hasSize(1)
                      .allMatch(s -> s.getMessage().contains("inner release failed")));
    }

    @Test
    @DisplayName("flatMap() releases both resources even when use throws exception")
    void flatMapReleasesBothResourcesOnUseException() {
      AtomicBoolean outerReleased = new AtomicBoolean(false);
      AtomicBoolean innerReleased = new AtomicBoolean(false);

      Resource<String> outer = Resource.make(() -> "outer", _ -> outerReleased.set(true));

      Resource<String> chained =
          outer.flatMap(_ -> Resource.make(() -> "inner", _ -> innerReleased.set(true)));

      VTask<String> failingTask =
          chained.use(
              _ ->
                  VTask.of(
                      () -> {
                        throw new RuntimeException("use failed");
                      }));

      assertThatThrownBy(failingTask::run).hasMessageContaining("use failed");
      assertThat(innerReleased).as("Inner resource should be released").isTrue();
      assertThat(outerReleased).as("Outer resource should be released").isTrue();
    }

    @Test
    @DisplayName("and() validates non-null other resource")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void andValidatesNonNullOther() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.and(null))
          .withMessageContaining("other for");
    }

    @Test
    @DisplayName("and() combines two resources")
    void andCombinesTwoResources() {
      List<String> events = new ArrayList<>();

      Resource<String> first =
          Resource.make(
              () -> {
                events.add("acquire-first");
                return "first";
              },
              _ -> events.add("release-first"));

      Resource<String> second =
          Resource.make(
              () -> {
                events.add("acquire-second");
                return "second";
              },
              _ -> events.add("release-second"));

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      VTask<String> task = combined.useSync(tuple -> tuple.first() + "-" + tuple.second());

      String result = task.run();

      assertThat(result).isEqualTo("first-second");
      // Resources should be released in reverse order
      assertThat(events)
          .containsExactly("acquire-first", "acquire-second", "release-second", "release-first");
    }

    @Test
    @DisplayName("and() suppresses release exception when second acquire fails")
    void andSuppressesReleaseExceptionWhenSecondAcquireFails() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });

      Resource<String> second =
          Resource.make(
              () -> {
                throw new RuntimeException("second acquire failed");
              },
              _ -> {});

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      assertThatThrownBy(() -> combined.useSync(_ -> "result").run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("second acquire failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .hasSize(1)
                      .allMatch(s -> s.getMessage().contains("first release failed")));
    }

    @Test
    @DisplayName("and() handles second release exception")
    void andHandlesSecondReleaseException() {
      Resource<String> first = Resource.make(() -> "first", _ -> {});
      Resource<String> second =
          Resource.make(
              () -> "second",
              _ -> {
                throw new RuntimeException("second release failed");
              });

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      assertThatThrownBy(() -> combined.useSync(Par.Tuple2::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource")
          .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("and() handles first release exception only")
    void andHandlesFirstReleaseExceptionOnly() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });
      Resource<String> second = Resource.make(() -> "second", _ -> {});

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      // First release throws, second doesn't - covers the false branch of if (firstException !=
      // null)
      assertThatThrownBy(() -> combined.useSync(Par.Tuple2::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource")
          .satisfies(e -> assertThat(e.getCause().getSuppressed()).isEmpty());
    }

    @Test
    @DisplayName("and() handles both releases throwing exceptions")
    void andHandlesBothReleasesThrowingExceptions() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });
      Resource<String> second =
          Resource.make(
              () -> "second",
              _ -> {
                throw new RuntimeException("second release failed");
              });

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      // Second release exception is suppressed by first release exception
      // First release exception is wrapped in RuntimeException as cause
      assertThatThrownBy(() -> combined.useSync(Par.Tuple2::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource")
          .satisfies(e -> assertThat(e.getCause().getSuppressed()).hasSize(1));
    }

    @Test
    @DisplayName("and() releases first resource if second acquire fails")
    void andReleasesFirstIfSecondFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", _ -> firstReleased.set(true));

      Resource<String> second =
          Resource.make(
              () -> {
                throw new RuntimeException("acquire failed");
              },
              _ -> {});

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      VTask<String> task = combined.useSync(_ -> "should not reach");

      assertThatThrownBy(task::run).hasMessageContaining("acquire failed");
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("and() with three resources")
    void andWithThreeResources() {
      AtomicInteger releaseOrder = new AtomicInteger(0);
      List<Integer> releases = new ArrayList<>();

      Resource<String> first =
          Resource.make(() -> "first", _ -> releases.add(releaseOrder.incrementAndGet()));

      Resource<String> second =
          Resource.make(() -> "second", _ -> releases.add(releaseOrder.incrementAndGet()));

      Resource<String> third =
          Resource.make(() -> "third", _ -> releases.add(releaseOrder.incrementAndGet()));

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      VTask<String> task =
          combined.useSync(tuple -> tuple.first() + "-" + tuple.second() + "-" + tuple.third());

      String result = task.run();

      assertThat(result).isEqualTo("first-second-third");
      // Releases should be in reverse order (3, 2, 1)
      assertThat(releases).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("and(second, third) validates non-null second")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void andThreeValidatesNonNullSecond() {
      Resource<String> first = Resource.make(() -> "first", _ -> {});
      Resource<String> third = Resource.make(() -> "third", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> first.and(null, third))
          .withMessageContaining("second for");
    }

    @Test
    @DisplayName("and(second, third) validates non-null third")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void andThreeValidatesNonNullThird() {
      Resource<String> first = Resource.make(() -> "first", _ -> {});
      Resource<String> second = Resource.make(() -> "second", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> first.and(second, null))
          .withMessageContaining("third for");
    }

    @Test
    @DisplayName("and(second, third) releases first if second acquire fails")
    void andThreeReleasesFirstIfSecondFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", _ -> firstReleased.set(true));
      Resource<String> second =
          Resource.make(
              () -> {
                throw new RuntimeException("second acquire failed");
              },
              _ -> {});
      Resource<String> third = Resource.make(() -> "third", _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(_ -> "result").run())
          .hasMessageContaining("second acquire failed");
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("and(second, third) releases first and second if third acquire fails")
    void andThreeReleasesFirstAndSecondIfThirdFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);
      AtomicBoolean secondReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", _ -> firstReleased.set(true));
      Resource<String> second = Resource.make(() -> "second", _ -> secondReleased.set(true));
      Resource<String> third =
          Resource.make(
              () -> {
                throw new RuntimeException("third acquire failed");
              },
              _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(_ -> "result").run())
          .hasMessageContaining("third acquire failed");
      assertThat(firstReleased).isTrue();
      assertThat(secondReleased).isTrue();
    }

    @Test
    @DisplayName("and(second, third) suppresses second release exception when third acquire fails")
    void andThreeSuppressesSecondReleaseExceptionWhenThirdFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", _ -> firstReleased.set(true));
      Resource<String> second =
          Resource.make(
              () -> "second",
              _ -> {
                throw new RuntimeException("second release failed");
              });
      Resource<String> third =
          Resource.make(
              () -> {
                throw new RuntimeException("third acquire failed");
              },
              _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(_ -> "result").run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("third acquire failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .hasSize(1)
                      .allMatch(s -> s.getMessage().contains("second release failed")));
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("and(second, third) suppresses first release exception when second acquire fails")
    void andThreeSuppressesFirstReleaseExceptionWhenSecondFails() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });
      Resource<String> second =
          Resource.make(
              () -> {
                throw new RuntimeException("second acquire failed");
              },
              _ -> {});
      Resource<String> third = Resource.make(() -> "third", _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(_ -> "result").run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("second acquire failed")
          .satisfies(
              e ->
                  assertThat(e.getSuppressed())
                      .hasSize(1)
                      .allMatch(s -> s.getMessage().contains("first release failed")));
    }

    @Test
    @DisplayName("and(second, third) handles third release exception")
    void andThreeHandlesThirdReleaseException() {
      Resource<String> first = Resource.make(() -> "first", _ -> {});
      Resource<String> second = Resource.make(() -> "second", _ -> {});
      Resource<String> third =
          Resource.make(
              () -> "third",
              _ -> {
                throw new RuntimeException("third release failed");
              });

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(Par.Tuple3::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource");
    }

    @Test
    @DisplayName("and(second, third) handles second release exception")
    void andThreeHandlesSecondReleaseException() {
      Resource<String> first = Resource.make(() -> "first", _ -> {});
      Resource<String> second =
          Resource.make(
              () -> "second",
              _ -> {
                throw new RuntimeException("second release failed");
              });
      Resource<String> third = Resource.make(() -> "third", _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(Par.Tuple3::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource");
    }

    @Test
    @DisplayName("and(second, third) handles first release exception only")
    void andThreeHandlesFirstReleaseExceptionOnly() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });
      Resource<String> second = Resource.make(() -> "second", _ -> {});
      Resource<String> third = Resource.make(() -> "third", _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      // First release throws, second and third don't - covers false branch of if (firstException !=
      // null)
      assertThatThrownBy(() -> combined.useSync(Par.Tuple3::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource")
          .satisfies(e -> assertThat(e.getCause().getSuppressed()).isEmpty());
    }

    @Test
    @DisplayName("and(second, third) handles first release exception with suppressed")
    void andThreeHandlesFirstReleaseExceptionWithSuppressed() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });
      Resource<String> second =
          Resource.make(
              () -> "second",
              _ -> {
                throw new RuntimeException("second release failed");
              });
      Resource<String> third = Resource.make(() -> "third", _ -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      // Second release exception is suppressed by first release exception
      // First release exception is wrapped in RuntimeException as cause
      assertThatThrownBy(() -> combined.useSync(Par.Tuple3::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource")
          .satisfies(e -> assertThat(e.getCause().getSuppressed()).hasSize(1));
    }

    @Test
    @DisplayName("and(second, third) handles all three releases throwing exceptions")
    void andThreeHandlesAllThreeReleasesThrowingExceptions() {
      Resource<String> first =
          Resource.make(
              () -> "first",
              _ -> {
                throw new RuntimeException("first release failed");
              });
      Resource<String> second =
          Resource.make(
              () -> "second",
              _ -> {
                throw new RuntimeException("second release failed");
              });
      Resource<String> third =
          Resource.make(
              () -> "third",
              _ -> {
                throw new RuntimeException("third release failed");
              });

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      // When all three releases throw:
      // - Third release exception is suppressed by second
      // - Second release exception (with third suppressed) is suppressed by first
      // - First release exception is wrapped in RuntimeException as cause
      assertThatThrownBy(() -> combined.useSync(Par.Tuple3::first).run())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to release resource")
          .satisfies(e -> assertThat(e.getCause().getSuppressed()).hasSize(1));
    }
  }

  @Nested
  @DisplayName("Finaliser Support")
  class FinalizerSupportTests {

    @Test
    @DisplayName("withFinalizer() runs after release")
    void withFinalizerRunsAfterRelease() {
      List<String> events = new ArrayList<>();

      Resource<String> resource =
          Resource.make(() -> "test", _ -> events.add("release"))
              .withFinalizer(() -> events.add("finalizer"));

      resource.useSync(s -> s).run();

      assertThat(events).containsExactly("release", "finalizer");
    }

    @Test
    @DisplayName("withFinalizer() runs even if release throws")
    void withFinalizerRunsEvenIfReleaseThrows() {
      AtomicBoolean finalizerRan = new AtomicBoolean(false);

      Resource<String> resource =
          Resource.make(
                  () -> "test",
                  _ -> {
                    throw new RuntimeException("release failed");
                  })
              .withFinalizer(() -> finalizerRan.set(true));

      assertThatThrownBy(() -> resource.useSync(s -> s).run()).isInstanceOf(RuntimeException.class);
      assertThat(finalizerRan).isTrue();
    }

    @Test
    @DisplayName("withFinalizer() runs finalisers in the order they were added")
    void withFinalizerRunsFinalisersInOrderAdded() {
      List<String> events = new ArrayList<>();

      Resource<String> resource =
          Resource.make(() -> "test", _ -> events.add("release"))
              .withFinalizer(() -> events.add("first added"))
              .withFinalizer(() -> events.add("second added"));

      resource.useSync(s -> s).run();

      assertThat(events).containsExactly("release", "first added", "second added");
    }

    @Test
    @DisplayName("withFinalizer() fails with the finaliser's exception when the release throws too")
    void withFinalizerFailsWithLastExceptionThrown() {
      Resource<String> resource =
          Resource.make(
                  () -> "test",
                  _ -> {
                    throw new IllegalStateException("release failed");
                  })
              .withFinalizer(
                  () -> {
                    throw new IllegalStateException("finaliser failed");
                  });

      assertThatThrownBy(() -> resource.useSync(s -> s).run()).hasMessage("finaliser failed");
    }

    @Test
    @DisplayName("withFinalizer() validates non-null finaliser")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void withFinalizerValidatesNonNull() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.withFinalizer(null))
          .withMessageContaining("finalizer for");
    }

    @Test
    @DisplayName("onFailure() validates non-null handler")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void onFailureValidatesNonNull() {
      Resource<String> resource = Resource.make(() -> "test", _ -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.onFailure(null))
          .withMessageContaining("onFailure for");
    }

    @Test
    @DisplayName("onFailure() does not run its action when the use succeeds")
    void onFailureSkipsActionWhenUseSucceeds() {
      List<String> events = new ArrayList<>();
      Resource<String> resource =
          Resource.make(() -> "conn", c -> events.add("release " + c))
              .onFailure(c -> events.add("rollback " + c));

      String result = resource.useSync(String::toUpperCase).run();

      assertThat(result).isEqualTo("CONN");
      assertThat(events).containsExactly("release conn");
    }

    @ParameterizedTest(name = "when {0}")
    @MethodSource("org.higherkindedj.hkt.vtask.ResourceTest#failuresWhileHeld")
    @DisplayName(
        "onFailure() runs its action, then the release, on a failure while the value is held")
    void onFailureRunsActionBeforeRelease(FailureWhileHeld failure) {
      List<String> events = new ArrayList<>();
      Resource<String> resource =
          Resource.make(() -> "conn", c -> events.add("release " + c))
              .onFailure(c -> events.add("rollback " + c));

      VTask<?> task = failure.use().apply(resource);

      assertThatThrownBy(task::run).hasMessage("failed");
      assertThat(events).containsExactly("rollback conn", "release conn");
    }

    @Test
    @DisplayName("onFailure() on both layers of a flatMap runs innermost first")
    void onFailureOnBothLayersRunsInnermostFirst() {
      List<String> events = new ArrayList<>();
      Resource<String> chained =
          Resource.make(() -> "conn", c -> events.add("release " + c))
              .onFailure(c -> events.add("rollback " + c))
              .flatMap(
                  conn ->
                      Resource.make(() -> conn + "-stmt", s -> events.add("release " + s))
                          .onFailure(s -> events.add("discard " + s)));

      VTask<String> task = chained.use(_ -> VTask.fail(new IllegalStateException("failed")));

      assertThatThrownBy(task::run).hasMessage("failed");
      assertThat(events)
          .containsExactly(
              "discard conn-stmt", "release conn-stmt", "rollback conn", "release conn");
    }

    @Test
    @DisplayName("onFailure() still releases when its action throws")
    void onFailureStillReleasesWhenActionThrows() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource =
          Resource.make(() -> "conn", _ -> released.set(true))
              .onFailure(
                  _ -> {
                    throw new IllegalStateException("rollback failed");
                  });

      VTask<String> task = resource.use(_ -> VTask.fail(new IllegalStateException("failed")));

      assertThatThrownBy(task::run).isInstanceOf(IllegalStateException.class);
      assertThat(released).isTrue();
    }
  }

  @Nested
  @DisplayName("Each Use Owns Its Acquisition")
  class EachUseOwnsItsAcquisitionTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vtask.ResourceTest#derivations")
    @DisplayName("a nested use releases each acquired value once")
    void nestedUseReleasesEachAcquisitionOnce(Derivation derivation) {
      List<String> released = new ArrayList<>();
      AtomicInteger counter = new AtomicInteger();
      Resource<?> conn =
          derivation
              .derive()
              .apply(Resource.make(() -> "conn" + counter.incrementAndGet(), released::add));

      conn.use(outer -> conn.use(inner -> VTask.succeed(outer + "+" + inner))).run();

      assertThat(released).containsExactly("conn2", "conn1");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.vtask.ResourceTest#derivations")
    @DisplayName("two concurrent uses that both acquire before either releases release each once")
    void concurrentUsesReleaseEachAcquisitionOnce(Derivation derivation) throws Exception {
      List<String> released = new CopyOnWriteArrayList<>();
      AtomicInteger counter = new AtomicInteger();
      Resource<?> conn =
          derivation
              .derive()
              .apply(Resource.make(() -> "conn" + counter.incrementAndGet(), released::add));
      CyclicBarrier bothAcquired = new CyclicBarrier(2);
      Callable<String> use =
          () ->
              conn.use(
                      c ->
                          VTask.of(
                              () -> {
                                bothAcquired.await(10, TimeUnit.SECONDS);
                                return String.valueOf(c);
                              }))
                  .run();

      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<String> first = executor.submit(use);
        Future<String> second = executor.submit(use);
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
      }

      assertThat(released).containsExactlyInAnyOrder("conn1", "conn2");
    }
  }

  @Nested
  @DisplayName("Real-World Patterns")
  class RealWorldPatternsTests {

    @Test
    @DisplayName("database connection pattern")
    void databaseConnectionPattern() {
      // Simulated connection
      record Connection(String id) implements AutoCloseable {
        @Override
        public void close() {
          // Cleanup
        }
      }

      AtomicBoolean closed = new AtomicBoolean(false);

      Resource<Connection> connResource =
          Resource.make(() -> new Connection("conn-1"), _ -> closed.set(true));

      VTask<String> query =
          connResource.use(
              conn ->
                  VTask.of(
                      () -> {
                        // Simulate query
                        return "Result from " + conn.id();
                      }));

      String result = query.run();

      assertThat(result).isEqualTo("Result from conn-1");
      assertThat(closed).isTrue();
    }

    @Test
    @DisplayName("nested resources pattern")
    void nestedResourcesPattern() {
      List<String> events = new ArrayList<>();

      Resource<String> outer =
          Resource.make(
              () -> {
                events.add("acquire-outer");
                return "outer";
              },
              _ -> events.add("release-outer"));

      Resource<String> inner =
          Resource.make(
              () -> {
                events.add("acquire-inner");
                return "inner";
              },
              _ -> events.add("release-inner"));

      VTask<String> task = outer.use(o -> inner.use(i -> VTask.succeed(o + "+" + i)));

      String result = task.run();

      assertThat(result).isEqualTo("outer+inner");
      assertThat(events)
          .containsExactly("acquire-outer", "acquire-inner", "release-inner", "release-outer");
    }

    @Test
    @DisplayName("file handling pattern")
    void fileHandlingPattern() {
      // Simulated file handle
      AtomicBoolean fileClosed = new AtomicBoolean(false);

      AutoCloseable fakeFile =
          new AutoCloseable() {
            @Override
            public void close() {
              fileClosed.set(true);
            }

            @Override
            public String toString() {
              return "FakeFile";
            }
          };

      Resource<AutoCloseable> fileResource = Resource.fromAutoCloseable(() -> fakeFile);

      VTask<String> readTask = fileResource.useSync(f -> "content from " + f);

      String result = readTask.run();

      assertThat(result).isEqualTo("content from FakeFile");
      assertThat(fileClosed).isTrue();
    }
  }
}
