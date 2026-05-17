// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monoid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies the standardised Effect Path {@code toString()} convention (issue #530):
 *
 * <ul>
 *   <li>round-parenthesis wrapper form delegating to the inner value
 *   <li>uniform angle-bracketed sentinel vocabulary
 *   <li>bounded rendering for collection-backed paths
 *   <li>{@code IdPath} null-safety and no forcing of deferred computations
 * </ul>
 */
@DisplayName("Effect Path toString() format")
class PathToStringFormatTest {

  @Nested
  @DisplayName("Wrapper convention: TypeName(inner)")
  class WrapperConvention {

    @Test
    @DisplayName("MaybePath delegates to the wrapped Maybe")
    void maybePath() {
      assertThat(Path.just(42).toString()).isEqualTo("MaybePath(Just(42))");
      assertThat(Path.nothing().toString()).isEqualTo("MaybePath(Nothing)");
    }

    @Test
    @DisplayName("EitherPath delegates to the wrapped Either")
    void eitherPath() {
      assertThat(Path.right(1).toString()).isEqualTo("EitherPath(Right(1))");
      assertThat(Path.left("error").toString()).isEqualTo("EitherPath(Left(error))");
    }

    @Test
    @DisplayName("IdPath is null-safe")
    void idPathNullSafe() {
      assertThat(Path.id(9).toString()).isEqualTo("IdPath(9)");
      assertThat(Path.id(null).toString()).isEqualTo("IdPath(null)");
    }

    @Test
    @DisplayName("OptionalPath shows the present value")
    void optionalPresent() {
      assertThat(Path.optional(Optional.of(7)).toString()).isEqualTo("OptionalPath(7)");
    }
  }

  @Nested
  @DisplayName("Sentinel vocabulary is uniform and angle-bracketed")
  class SentinelVocabulary {

    @Test
    @DisplayName("deferred computations all read <deferred>")
    void deferred() {
      assertThat(Path.io(() -> 1).toString()).isEqualTo("IOPath(<deferred>)");
      assertThat(Path.vtask(() -> 1).toString()).isEqualTo("VTaskPath(<deferred>)");
      assertThat(Path.lazyDefer(() -> 1).toString()).isEqualTo("LazyPath(<deferred>)");
      assertThat(TrampolinePath.done(5).toString()).isEqualTo("TrampolinePath(<deferred>)");
    }

    @Test
    @DisplayName("unconsumed streams read <stream>")
    void stream() {
      assertThat(Path.stream(Stream.of(1, 2, 3)).toString()).isEqualTo("StreamPath(<stream>)");
    }

    @Test
    @DisplayName("empty optional-like reads <empty>")
    void empty() {
      assertThat(Path.optional(Optional.empty()).toString()).isEqualTo("OptionalPath(<empty>)");
    }

    @Test
    @DisplayName("async pending / failed read <pending> / <failed>")
    void asyncStates() {
      assertThat(Path.future(new CompletableFuture<>()).toString())
          .isEqualTo("CompletableFuturePath(<pending>)");
      assertThat(Path.futureFailed(new RuntimeException("boom")).toString())
          .isEqualTo("CompletableFuturePath(<failed>)");
      assertThat(Path.futureCompleted(42).toString()).isEqualTo("CompletableFuturePath(42)");
    }

    @Test
    @DisplayName("evaluated LazyPath shows its memoised value")
    void lazyEvaluated() {
      assertThat(Path.lazyNow("hi").toString()).isEqualTo("LazyPath(hi)");
    }

    @Test
    @DisplayName("evaluated-but-failed LazyPath reads <failed>")
    void lazyFailed() {
      var path =
          Path.lazyDefer(
              () -> {
                throw new IllegalStateException("boom");
              });
      assertThatThrownBy(path::get).isInstanceOf(IllegalStateException.class);

      assertThat(path.toString()).isEqualTo("LazyPath(<failed>)");
    }

    @Test
    @DisplayName("rendering a deferred LazyPath does not force the computation")
    void lazyToStringDoesNotForce() {
      AtomicBoolean ran = new AtomicBoolean(false);
      var path =
          Path.lazyDefer(
              () -> {
                ran.set(true);
                return 1;
              });

      String rendered = path.toString();

      assertThat(ran).isFalse();
      assertThat(rendered).isEqualTo("LazyPath(<deferred>)");
    }
  }

  @Nested
  @DisplayName("Collection-backed paths are bounded")
  class BoundedRendering {

    @Test
    @DisplayName("small collections render in full, identical to List.toString()")
    void smallUnchanged() {
      assertThat(ListPath.of(1, 2, 3).toString()).isEqualTo("ListPath([1, 2, 3])");
      assertThat(NonDetPath.of(1, 2, 3).toString()).isEqualTo("NonDetPath([1, 2, 3])");
    }

    @Test
    @DisplayName("large collections are truncated with an explicit (+k more) marker")
    void largeTruncated() {
      List<Integer> fifteen = IntStream.rangeClosed(1, 15).boxed().toList();

      assertThat(ListPath.of(fifteen).toString())
          .isEqualTo("ListPath([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, …(+5 more)])");
    }

    @Test
    @DisplayName("PathToString.elements respects the default and custom limits")
    void helperLimits() {
      List<Integer> five = List.of(1, 2, 3, 4, 5);

      assertThat(PathToString.elements(five)).isEqualTo("[1, 2, 3, 4, 5]");
      assertThat(PathToString.elements(five, 2)).isEqualTo("[1, 2, …(+3 more)]");
      assertThat(PathToString.elements(List.of(), 10)).isEqualTo("[]");
      // limit 0 must not emit a leading comma before the marker
      assertThat(PathToString.elements(five, 0)).isEqualTo("[…(+5 more)]");
    }

    @Test
    @DisplayName("WriterPath bounds a collection log and renders a scalar log verbatim")
    void writerLog() {
      Monoid<List<String>> listMonoid =
          new Monoid<>() {
            @Override
            public List<String> empty() {
              return List.of();
            }

            @Override
            public List<String> combine(List<String> a, List<String> b) {
              return Stream.concat(a.stream(), b.stream()).toList();
            }
          };
      Monoid<String> stringMonoid =
          new Monoid<>() {
            @Override
            public String empty() {
              return "";
            }

            @Override
            public String combine(String a, String b) {
              return a + b;
            }
          };

      List<String> twelve = IntStream.rangeClosed(1, 12).mapToObj(i -> "e" + i).toList();
      assertThat(WriterPath.writer(1, twelve, listMonoid).toString())
          .isEqualTo(
              "WriterPath(log=[e1, e2, e3, e4, e5, e6, e7, e8, e9, e10, …(+2 more)], value=1)");
      assertThat(WriterPath.writer(7, "audit|", stringMonoid).toString())
          .isEqualTo("WriterPath(log=audit|, value=7)");
    }
  }
}
