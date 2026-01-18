// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Test suite for Resource - the bracket pattern for safe resource management. */
@DisplayName("Resource<A> Test Suite")
class ResourceTest {

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodsTests {

    @Test
    @DisplayName("make() creates resource with acquire and release")
    void makeCreatesResource() {
      AtomicBoolean released = new AtomicBoolean(false);

      Resource<String> resource = Resource.make(() -> "test", s -> released.set(true));

      assertThat(resource).isNotNull();
    }

    @Test
    @DisplayName("make() validates non-null acquire")
    void makeValidatesNonNullAcquire() {
      assertThatNullPointerException()
          .isThrownBy(() -> Resource.make(null, s -> {}))
          .withMessageContaining("acquire must not be null");
    }

    @Test
    @DisplayName("make() validates non-null release")
    void makeValidatesNonNullRelease() {
      assertThatNullPointerException()
          .isThrownBy(() -> Resource.make(() -> "test", null))
          .withMessageContaining("release must not be null");
    }

    @Test
    @DisplayName("fromAutoCloseable() creates resource that auto-closes")
    void fromAutoCloseableCreatesResource() {
      AtomicBoolean closed = new AtomicBoolean(false);

      AutoCloseable closeable =
          () -> {
            closed.set(true);
          };

      Resource<AutoCloseable> resource = Resource.fromAutoCloseable(() -> closeable);

      assertThat(resource).isNotNull();
    }

    @Test
    @DisplayName("fromAutoCloseable() validates non-null acquire")
    void fromAutoCloseableValidatesNonNullAcquire() {
      assertThatNullPointerException()
          .isThrownBy(() -> Resource.fromAutoCloseable(null))
          .withMessageContaining("acquire must not be null");
    }

    @Test
    @DisplayName("fromAutoCloseable() silently handles close exception")
    void fromAutoCloseableHandlesCloseException() throws Throwable {
      AtomicBoolean closeCalled = new AtomicBoolean(false);

      AutoCloseable failingCloseable =
          () -> {
            closeCalled.set(true);
            throw new RuntimeException("close failed");
          };

      Resource<AutoCloseable> resource = Resource.fromAutoCloseable(() -> failingCloseable);

      // Should not throw even though close fails
      String result = resource.useSync(c -> "success").run();

      assertThat(result).isEqualTo("success");
      assertThat(closeCalled).isTrue();
    }

    @Test
    @DisplayName("pure() creates resource without acquire/release")
    void pureCreatesNoOpResource() throws Throwable {
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
    void useAcquiresUsesAndReleases() throws Throwable {
      List<String> events = new ArrayList<>();

      Resource<String> resource =
          Resource.make(
              () -> {
                events.add("acquire");
                return "resource";
              },
              s -> events.add("release"));

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

      Resource<String> resource = Resource.make(() -> "test", s -> released.set(true));

      VTask<String> task =
          resource.use(
              r ->
                  VTask.of(
                      () -> {
                        throw new RuntimeException("intentional error");
                      }));

      assertThatThrownBy(task::run).isInstanceOf(RuntimeException.class);
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("use() validates non-null function")
    void useValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.use(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("use() validates function does not return null")
    void useValidatesFunctionReturnsNonNull() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "test", s -> released.set(true));

      VTask<String> task = resource.use(r -> null);

      assertThatNullPointerException().isThrownBy(task::run).withMessageContaining("must not return null");
      assertThat(released).isTrue(); // Resource should still be released
    }

    @Test
    @DisplayName("useSync() validates non-null function")
    void useSyncValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.useSync(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("useSync() works with non-effectful functions")
    void useSyncWorksWithSimpleFunctions() throws Throwable {
      AtomicBoolean released = new AtomicBoolean(false);

      Resource<String> resource = Resource.make(() -> "hello", s -> released.set(true));

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
    @DisplayName("map() transforms resource value")
    void mapTransformsResourceValue() throws Throwable {
      Resource<String> resource = Resource.make(() -> "hello", s -> {});

      Resource<Integer> mapped = resource.map(String::length);

      Integer result = mapped.useSync(i -> i).run();

      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("map() validates non-null function")
    void mapValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.map(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("flatMap() chains resource acquisition")
    void flatMapChainsResources() throws Throwable {
      List<String> events = new ArrayList<>();

      Resource<String> first =
          Resource.make(
              () -> {
                events.add("acquire-first");
                return "first";
              },
              s -> events.add("release-first"));

      Resource<String> chained =
          first.flatMap(
              f ->
                  Resource.make(
                      () -> {
                        events.add("acquire-second");
                        return f + "-second";
                      },
                      s -> events.add("release-second")));

      String result = chained.useSync(s -> s).run();

      assertThat(result).isEqualTo("first-second");
      assertThat(events).contains("acquire-first", "acquire-second");
    }

    @Test
    @DisplayName("flatMap() validates non-null function")
    void flatMapValidatesNonNullFunction() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.flatMap(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("flatMap() releases first if second acquire fails")
    void flatMapReleasesFirstIfSecondFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", s -> firstReleased.set(true));

      Resource<String> chained =
          first.flatMap(
              f ->
                  Resource.make(
                      () -> {
                        throw new RuntimeException("second acquire failed");
                      },
                      s -> {}));

      assertThatThrownBy(() -> chained.useSync(s -> s).run())
          .hasMessageContaining("second acquire failed");
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("flatMap() validates function does not return null")
    void flatMapValidatesFunctionReturnsNonNull() {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource = Resource.make(() -> "test", s -> released.set(true));

      Resource<String> chained = resource.flatMap(r -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> chained.useSync(s -> s).run())
          .withMessageContaining("must not return null");
      assertThat(released).isTrue();
    }

    @Test
    @DisplayName("and() validates non-null other resource")
    void andValidatesNonNullOther() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.and(null))
          .withMessageContaining("other must not be null");
    }

    @Test
    @DisplayName("and() combines two resources")
    void andCombinesTwoResources() throws Throwable {
      List<String> events = new ArrayList<>();

      Resource<String> first =
          Resource.make(
              () -> {
                events.add("acquire-first");
                return "first";
              },
              s -> events.add("release-first"));

      Resource<String> second =
          Resource.make(
              () -> {
                events.add("acquire-second");
                return "second";
              },
              s -> events.add("release-second"));

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      VTask<String> task = combined.useSync(tuple -> tuple.first() + "-" + tuple.second());

      String result = task.run();

      assertThat(result).isEqualTo("first-second");
      // Resources should be released in reverse order
      assertThat(events)
          .containsExactly("acquire-first", "acquire-second", "release-second", "release-first");
    }

    @Test
    @DisplayName("and() releases first resource if second acquire fails")
    void andReleasesFirstIfSecondFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", s -> firstReleased.set(true));

      Resource<String> second =
          Resource.make(
              () -> {
                throw new RuntimeException("acquire failed");
              },
              s -> {});

      Resource<Par.Tuple2<String, String>> combined = first.and(second);

      VTask<String> task = combined.useSync(tuple -> "should not reach");

      assertThatThrownBy(task::run).hasMessageContaining("acquire failed");
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("and() with three resources")
    void andWithThreeResources() throws Throwable {
      AtomicInteger releaseOrder = new AtomicInteger(0);
      List<Integer> releases = new ArrayList<>();

      Resource<String> first =
          Resource.make(() -> "first", s -> releases.add(releaseOrder.incrementAndGet()));

      Resource<String> second =
          Resource.make(() -> "second", s -> releases.add(releaseOrder.incrementAndGet()));

      Resource<String> third =
          Resource.make(() -> "third", s -> releases.add(releaseOrder.incrementAndGet()));

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
    void andThreeValidatesNonNullSecond() {
      Resource<String> first = Resource.make(() -> "first", s -> {});
      Resource<String> third = Resource.make(() -> "third", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> first.and(null, third))
          .withMessageContaining("second must not be null");
    }

    @Test
    @DisplayName("and(second, third) validates non-null third")
    void andThreeValidatesNonNullThird() {
      Resource<String> first = Resource.make(() -> "first", s -> {});
      Resource<String> second = Resource.make(() -> "second", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> first.and(second, null))
          .withMessageContaining("third must not be null");
    }

    @Test
    @DisplayName("and(second, third) releases first if second acquire fails")
    void andThreeReleasesFirstIfSecondFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", s -> firstReleased.set(true));
      Resource<String> second =
          Resource.make(
              () -> {
                throw new RuntimeException("second acquire failed");
              },
              s -> {});
      Resource<String> third = Resource.make(() -> "third", s -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(t -> "result").run())
          .hasMessageContaining("second acquire failed");
      assertThat(firstReleased).isTrue();
    }

    @Test
    @DisplayName("and(second, third) releases first and second if third acquire fails")
    void andThreeReleasesFirstAndSecondIfThirdFails() {
      AtomicBoolean firstReleased = new AtomicBoolean(false);
      AtomicBoolean secondReleased = new AtomicBoolean(false);

      Resource<String> first = Resource.make(() -> "first", s -> firstReleased.set(true));
      Resource<String> second = Resource.make(() -> "second", s -> secondReleased.set(true));
      Resource<String> third =
          Resource.make(
              () -> {
                throw new RuntimeException("third acquire failed");
              },
              s -> {});

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      assertThatThrownBy(() -> combined.useSync(t -> "result").run())
          .hasMessageContaining("third acquire failed");
      assertThat(firstReleased).isTrue();
      assertThat(secondReleased).isTrue();
    }
  }

  @Nested
  @DisplayName("Finalizer Support")
  class FinalizerSupportTests {

    @Test
    @DisplayName("withFinalizer() runs after release")
    void withFinalizerRunsAfterRelease() throws Throwable {
      List<String> events = new ArrayList<>();

      Resource<String> resource =
          Resource.make(() -> "test", s -> events.add("release"))
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
                  s -> {
                    throw new RuntimeException("release failed");
                  })
              .withFinalizer(() -> finalizerRan.set(true));

      assertThatThrownBy(() -> resource.useSync(s -> s).run()).isInstanceOf(RuntimeException.class);
      assertThat(finalizerRan).isTrue();
    }

    @Test
    @DisplayName("withFinalizer() validates non-null finalizer")
    void withFinalizerValidatesNonNull() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.withFinalizer(null))
          .withMessageContaining("finalizer must not be null");
    }

    @Test
    @DisplayName("onFailure() validates non-null handler")
    void onFailureValidatesNonNull() {
      Resource<String> resource = Resource.make(() -> "test", s -> {});

      assertThatNullPointerException()
          .isThrownBy(() -> resource.onFailure(null))
          .withMessageContaining("onFailure must not be null");
    }

    @Test
    @DisplayName("onFailure() returns resource that can be used")
    void onFailureReturnsUsableResource() throws Throwable {
      AtomicBoolean released = new AtomicBoolean(false);
      Resource<String> resource =
          Resource.make(() -> "test", s -> released.set(true)).onFailure(s -> {});

      String result = resource.useSync(s -> s.toUpperCase()).run();

      assertThat(result).isEqualTo("TEST");
      assertThat(released).isTrue();
    }
  }

  @Nested
  @DisplayName("Real-World Patterns")
  class RealWorldPatternsTests {

    @Test
    @DisplayName("database connection pattern")
    void databaseConnectionPattern() throws Throwable {
      // Simulated connection
      record Connection(String id) implements AutoCloseable {
        @Override
        public void close() {
          // Cleanup
        }
      }

      AtomicBoolean closed = new AtomicBoolean(false);

      Resource<Connection> connResource =
          Resource.make(() -> new Connection("conn-1"), conn -> closed.set(true));

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
    void nestedResourcesPattern() throws Throwable {
      List<String> events = new ArrayList<>();

      Resource<String> outer =
          Resource.make(
              () -> {
                events.add("acquire-outer");
                return "outer";
              },
              s -> events.add("release-outer"));

      Resource<String> inner =
          Resource.make(
              () -> {
                events.add("acquire-inner");
                return "inner";
              },
              s -> events.add("release-inner"));

      VTask<String> task = outer.use(o -> inner.use(i -> VTask.succeed(o + "+" + i)));

      String result = task.run();

      assertThat(result).isEqualTo("outer+inner");
      assertThat(events)
          .containsExactly("acquire-outer", "acquire-inner", "release-inner", "release-outer");
    }

    @Test
    @DisplayName("file handling pattern")
    void fileHandlingPattern() throws Throwable {
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
