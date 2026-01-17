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

/**
 * Test suite for Resource - the bracket pattern for safe resource management.
 */
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
          Resource.make(
              () -> "first",
              s -> releases.add(releaseOrder.incrementAndGet()));

      Resource<String> second =
          Resource.make(
              () -> "second",
              s -> releases.add(releaseOrder.incrementAndGet()));

      Resource<String> third =
          Resource.make(
              () -> "third",
              s -> releases.add(releaseOrder.incrementAndGet()));

      Resource<Par.Tuple3<String, String, String>> combined = first.and(second, third);

      VTask<String> task =
          combined.useSync(
              tuple -> tuple.first() + "-" + tuple.second() + "-" + tuple.third());

      String result = task.run();

      assertThat(result).isEqualTo("first-second-third");
      // Releases should be in reverse order (3, 2, 1)
      assertThat(releases).containsExactly(1, 2, 3);
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
          Resource.make(
                  () -> "test",
                  s -> events.add("release"))
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

      assertThatThrownBy(() -> resource.useSync(s -> s).run())
          .isInstanceOf(RuntimeException.class);
      assertThat(finalizerRan).isTrue();
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
          Resource.make(
              () -> new Connection("conn-1"),
              conn -> closed.set(true));

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

      VTask<String> task =
          outer.use(
              o ->
                  inner.use(
                      i ->
                          VTask.succeed(o + "+" + i)));

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
