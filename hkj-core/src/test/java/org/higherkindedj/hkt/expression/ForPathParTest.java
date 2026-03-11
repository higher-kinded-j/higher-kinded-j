// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code par()} parallel/applicative entry points on {@link ForPath}.
 *
 * <p>Verifies that each path type correctly combines independent computations using applicative
 * semantics, preserving the path type in the result.
 */
@DisplayName("ForPath par() Tests")
class ForPathParTest {

  @Nested
  @DisplayName("MaybePath par()")
  class MaybePathParTests {

    @Test
    @DisplayName("par(2): should combine two Just values")
    void par2_bothJust() {
      MaybePath<String> result = ForPath.par(Path.just(1), Path.just("x")).yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Maybe.just("1x"));
    }

    @Test
    @DisplayName("par(2): should short-circuit on Nothing")
    void par2_oneNothing() {
      MaybePath<String> result =
          ForPath.par(Path.just(1), Path.<String>nothing()).yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("par(3): should combine three Just values")
    void par3_allJust() {
      MaybePath<Integer> result =
          ForPath.par(Path.just(1), Path.just(2), Path.just(3)).yield((a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(Maybe.just(6));
    }

    @Test
    @DisplayName("par(3): should short-circuit when any is Nothing")
    void par3_oneNothing() {
      MaybePath<Integer> result =
          ForPath.par(Path.just(1), Path.<Integer>nothing(), Path.just(3))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("OptionalPath par()")
  class OptionalPathParTests {

    @Test
    @DisplayName("par(2): should combine two present values")
    void par2_bothPresent() {
      OptionalPath<String> result =
          ForPath.par(Path.optional(Optional.of(1)), Path.optional(Optional.of("x")))
              .yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Optional.of("1x"));
    }

    @Test
    @DisplayName("par(2): should short-circuit on empty")
    void par2_oneEmpty() {
      OptionalPath<String> result =
          ForPath.par(Path.optional(Optional.of(1)), Path.optional(Optional.<String>empty()))
              .yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("par(3): should combine three present values")
    void par3_allPresent() {
      OptionalPath<Integer> result =
          ForPath.par(
                  Path.optional(Optional.of(1)),
                  Path.optional(Optional.of(2)),
                  Path.optional(Optional.of(3)))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(Optional.of(6));
    }
  }

  @Nested
  @DisplayName("EitherPath par()")
  class EitherPathParTests {

    @Test
    @DisplayName("par(2): should combine two Right values")
    void par2_bothRight() {
      EitherPath<String, Integer> result =
          ForPath.par(Path.<String, Integer>right(1), Path.<String, Integer>right(2))
              .yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Either.right(3));
    }

    @Test
    @DisplayName("par(2): should short-circuit on Left")
    void par2_oneLeft() {
      EitherPath<String, Integer> result =
          ForPath.par(Path.<String, Integer>left("error"), Path.<String, Integer>right(2))
              .yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Either.left("error"));
    }

    @Test
    @DisplayName("par(3): should combine three Right values")
    void par3_allRight() {
      EitherPath<String, Integer> result =
          ForPath.par(
                  Path.<String, Integer>right(1),
                  Path.<String, Integer>right(2),
                  Path.<String, Integer>right(3))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(Either.right(6));
    }
  }

  @Nested
  @DisplayName("TryPath par()")
  class TryPathParTests {

    @Test
    @DisplayName("par(2): should combine two successful values")
    void par2_bothSuccess() {
      TryPath<String> result =
          ForPath.par(Path.tryPath(Try.success(1)), Path.tryPath(Try.success("x")))
              .yield((a, b) -> a + b);
      assertThat(result.run()).isEqualTo(Try.success("1x"));
    }

    @Test
    @DisplayName("par(2): should propagate failure")
    void par2_oneFailure() {
      RuntimeException error = new RuntimeException("boom");
      TryPath<String> result =
          ForPath.par(Path.tryPath(Try.failure(error)), Path.tryPath(Try.success("x")))
              .yield((a, b) -> a + b);
      assertThat(result.run().isFailure()).isTrue();
    }

    @Test
    @DisplayName("par(3): should combine three successful values")
    void par3_allSuccess() {
      TryPath<Integer> result =
          ForPath.par(
                  Path.tryPath(Try.success(1)),
                  Path.tryPath(Try.success(2)),
                  Path.tryPath(Try.success(3)))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(Try.success(6));
    }
  }

  @Nested
  @DisplayName("IOPath par()")
  class IOPathParTests {

    @Test
    @DisplayName("par(2): should combine two IO computations")
    void par2_bothSucceed() {
      IOPath<String> result =
          ForPath.par(Path.ioPath(() -> 1), Path.ioPath(() -> "x")).yield((a, b) -> a + b);
      assertThat(result.run().unsafeRunSync()).isEqualTo("1x");
    }

    @Test
    @DisplayName("par(3): should combine three IO computations")
    void par3_allSucceed() {
      IOPath<Integer> result =
          ForPath.par(Path.ioPath(() -> 1), Path.ioPath(() -> 2), Path.ioPath(() -> 3))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run().unsafeRunSync()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("VTaskPath par() - True Parallel Execution")
  class VTaskPathParTests {

    @Test
    @DisplayName("par(2): should combine two VTask computations in parallel")
    void par2_parallel() {
      VTaskPath<String> result =
          ForPath.par(Path.vtaskPath(() -> 1), Path.vtaskPath(() -> "x")).yield((a, b) -> a + b);
      assertThat(result.run().run()).isEqualTo("1x");
    }

    @Test
    @DisplayName("par(3): should combine three VTask computations in parallel")
    void par3_parallel() {
      VTaskPath<Integer> result =
          ForPath.par(Path.vtaskPath(() -> 1), Path.vtaskPath(() -> 2), Path.vtaskPath(() -> 3))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run().run()).isEqualTo(6);
    }

    @Test
    @DisplayName("par(2): should truly execute in parallel (timing verification)")
    void par2_actuallyParallel() {
      VTaskPath<Long> result =
          ForPath.par(
                  Path.vtaskPath(
                      () -> {
                        Thread.sleep(100);
                        return System.nanoTime();
                      }),
                  Path.vtaskPath(
                      () -> {
                        Thread.sleep(100);
                        return System.nanoTime();
                      }))
              .yield((a, b) -> Math.abs(a - b));

      long timeDiff = result.run().run();
      // If truly parallel, both complete around the same time (within ~50ms)
      // If sequential, there would be ~100ms between them
      // Using 80ms threshold to be safe
      assertThat(timeDiff).isLessThan(80_000_000L); // 80ms in nanos
    }
  }

  @Nested
  @DisplayName("IdPath par()")
  class IdPathParTests {

    @Test
    @DisplayName("par(2): should combine two Id values")
    void par2_combine() {
      IdPath<String> result =
          ForPath.par(Path.idPath(Id.of(1)), Path.idPath(Id.of("x"))).yield((a, b) -> a + b);
      assertThat(result.run().value()).isEqualTo("1x");
    }

    @Test
    @DisplayName("par(3): should combine three Id values")
    void par3_combine() {
      IdPath<Integer> result =
          ForPath.par(Path.idPath(Id.of(1)), Path.idPath(Id.of(2)), Path.idPath(Id.of(3)))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run().value()).isEqualTo(6);
    }
  }

  @Nested
  @DisplayName("NonDetPath par()")
  class NonDetPathParTests {

    @Test
    @DisplayName("par(2): should produce Cartesian product")
    void par2_cartesianProduct() {
      NonDetPath<String> result =
          ForPath.par(NonDetPath.of(List.of(1, 2)), NonDetPath.of(List.of("a", "b")))
              .yield((a, b) -> a + b);
      assertThat(result.run()).containsExactly("1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("par(3): should produce Cartesian product of three lists")
    void par3_cartesianProduct() {
      NonDetPath<String> result =
          ForPath.par(
                  NonDetPath.of(List.of(1, 2)),
                  NonDetPath.of(List.of("a")),
                  NonDetPath.of(List.of(true)))
              .yield((a, b, c) -> a + b + c);
      assertThat(result.run()).containsExactly("1atrue", "2atrue");
    }
  }

  @Nested
  @DisplayName("GenericPath par()")
  class GenericPathParTests {

    @Test
    @DisplayName("par(2): should combine two GenericPath values")
    void par2_combine() {
      IdMonad idMonad = IdMonad.instance();
      GenericPath<IdKind.Witness, String> result =
          ForPath.par(Path.genericPure(1, idMonad), Path.genericPure("x", idMonad))
              .yield((a, b) -> a + b);
      assertThat(IdKindHelper.ID.unwrap(result.runKind())).isEqualTo("1x");
    }

    @Test
    @DisplayName("par(3): should combine three GenericPath values")
    void par3_combine() {
      IdMonad idMonad = IdMonad.instance();
      GenericPath<IdKind.Witness, Integer> result =
          ForPath.par(
                  Path.genericPure(1, idMonad),
                  Path.genericPure(2, idMonad),
                  Path.genericPure(3, idMonad))
              .yield((a, b, c) -> a + b + c);
      assertThat(IdKindHelper.ID.unwrap(result.runKind())).isEqualTo(6);
    }
  }
}
