// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherFunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Solution for Tutorial 02: Functor Mapping — teaching-solution format. */
@DisplayName("Tutorial 02 Solution: Functor Mapping")
public class Tutorial02_FunctorMapping_Solution {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code String::valueOf} is the standard Java method reference for
   * "render this as a string". Reads as a name, not a lambda.
   *
   * <p>Alternative: {@code i -> Integer.toString(i)} or {@code i -> "" + i}. Same answer; the
   * method reference is shorter and signals intent.
   *
   * <p>Common wrong attempt: {@code either.flatMap(i -> Either.right(String.valueOf(i)))}. Works,
   * but uses the heavier Monad capability for what is purely a Functor job. Reach for {@code
   * flatMap} only when the function itself returns an {@code Either}.
   */
  @Test
  @DisplayName("Exercise 1: map transforms the Right value")
  void exercise1_mapEither() {
    Either<String, Integer> either = Either.right(42);

    Either<String, String> result = either.map(String::valueOf);

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo("42");
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: passing any function we like to {@code map} on a {@code Left}
   * demonstrates that the function is dead code on the failure path. Useful to remember when
   * profiling: {@code map} on a Left is essentially free.
   *
   * <p>Alternative: leave the lambda body as something explicit like {@code i -> "won't run"} — the
   * documentation effect is the same.
   *
   * <p>Common wrong attempt: assuming {@code map} can transform the Left side. It cannot — that's
   * what {@code mapLeft} is for. {@code map} is a Functor on the Right; the Left is fixed by the
   * witness type.
   */
  @Test
  @DisplayName("Exercise 2: map on Left is a no-op")
  void exercise2_mapDoesNotAffectLeft() {
    Either<String, Integer> error = Either.left("Error occurred");

    Either<String, String> result = error.map(i -> "won't run");

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Error occurred");
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code monad.map(fn, kind)} is the typeclass-level form. When code needs
   * to be polymorphic in the container, this is the only form that survives generification.
   *
   * <p>Alternative: {@code LIST.narrow(numbers).stream().map(n -> n * 2).toList()} stays at the
   * concrete level. Fine for one-off code; loses the {@code Kind} when the surrounding pipeline
   * needs it.
   *
   * <p>Common wrong attempt: forgetting the helper exists and trying to get a {@code Functor<List>}
   * from somewhere. {@code Instances.monadZero(list())} is both a {@code Functor} and a {@code
   * Monad}; ask the monad for its {@code map}.
   */
  @Test
  @DisplayName("Exercise 3: ListMonad.map doubles each element")
  void exercise3_mapList() {
    MonadZero<ListKind.Witness> monad = Instances.monadZero(list());
    Kind<ListKind.Witness, Integer> numbers = LIST.widen(List.of(1, 2, 3, 4, 5));

    Kind<ListKind.Witness, Integer> doubled = monad.map(n -> n * 2, numbers);

    assertThat(LIST.narrow(doubled)).containsExactly(2, 4, 6, 8, 10);
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: each step is one transformation; the chain reads top-to-bottom in the
   * order the data flows.
   *
   * <p>Alternative: pre-compose into one function and call {@code map} once: {@code
   * value.map(((Function<Integer, Integer>) n -> n * 2).andThen(n -> n +
   * 5).andThen(String::valueOf))}. Functor composition law says these are equivalent. Use whichever
   * reads better in context.
   *
   * <p>Common wrong attempt: mixing {@code map} and {@code flatMap} inside the chain. If every
   * step's function returns a plain value, every step is {@code map}. Switching to {@code flatMap}
   * only makes sense when a step returns a wrapped value.
   */
  @Test
  @DisplayName("Exercise 4: chain map operations")
  void exercise4_chainingMaps() {
    Either<String, Integer> value = Either.right(10);

    Either<String, String> result = value.map(n -> n * 2).map(n -> n + 5).map(String::valueOf);

    assertThat(result.getRight()).isEqualTo("25");
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code "Value: " + i} uses Java's autoboxing-to-string concatenation,
   * which is exactly what we want for a one-line tag.
   *
   * <p>Alternative: {@code String.format("Value: %d", i)} when we want explicit formatting.
   *
   * <p>Common wrong attempt: returning an {@code Either} from inside the lambda — that would force
   * us into the {@code map} vs {@code flatMap} confusion called out in Tutorial 02's diagnostic.
   * Functor.map's signature is {@code A -> B}; if the function signature is {@code A -> Kind<F,
   * B>}, we want {@code Monad.flatMap}.
   */
  @Test
  @DisplayName("Exercise 5: Functor typeclass instance for Either")
  void exercise5_functorTypeclass() {
    EitherFunctor<String> functor = EitherFunctor.instance();
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(100));

    Kind<EitherKind.Witness<String>, String> mapped = functor.map(i -> "Value: " + i, kind);

    Either<String, String> result = EITHER.narrow(mapped);
    assertThat(result.getRight()).isEqualTo("Value: 100");
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: a method reference is the cleanest possible spelling when one already
   * exists. No anonymous parameter, no body, no risk of subtly different behaviour.
   *
   * <p>Alternative: a lambda {@code s -> s.toUpperCase()}. Identical behaviour, two extra tokens.
   *
   * <p>Common wrong attempt: {@code String::toUpperCase()} (with parentheses). That is a method
   * <em>call</em>, not a reference; the compiler will complain.
   */
  @Test
  @DisplayName("Exercise 6: method references work with Functor.map")
  void exercise6_methodReferences() {
    MonadZero<ListKind.Witness> monad = Instances.monadZero(list());
    Kind<ListKind.Witness, String> words = LIST.widen(List.of("hello", "world", "java"));

    Kind<ListKind.Witness, String> uppercase = monad.map(String::toUpperCase, words);

    assertThat(LIST.narrow(uppercase)).containsExactly("HELLO", "WORLD", "JAVA");
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the example deliberately produces the nested shape so we can
   * <em>see</em> what {@code map} does when the function returns an already-wrapped value. The fix
   * (use {@code flatMap}) is the topic of Tutorial 04.
   *
   * <p>Alternative: there isn't one for the diagnostic itself — the whole point is to see the
   * problem.
   *
   * <p>Common wrong attempt: trying to "unwrap" the inner {@code Either} after the fact with a
   * second {@code map}. That would require a function {@code Either -> Either}, which is not what
   * the data wants. The correct fix is to never produce the nesting in the first place — by using
   * {@code flatMap}.
   */
  @Test
  @DisplayName("Diagnostic: map with a wrapping function gives nested Eithers")
  void diagnostic_mapVsFlatMap() {
    Function<String, Either<String, Integer>> parse =
        s -> {
          try {
            return Either.right(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Either.left("Not a number: " + s);
          }
        };

    Either<String, String> outer = Either.right("42");

    Either<String, Either<String, Integer>> nested = outer.map(parse);

    assertThat(nested.isRight()).isTrue();
    assertThat(nested.getRight().isRight()).isTrue();
    assertThat(nested.getRight().getRight()).isEqualTo(42);
  }
}
