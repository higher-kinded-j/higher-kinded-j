// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherFunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: Functor — transforming values inside a container.
 *
 * <p>Pain → Promise. Without a uniform abstraction, "transform the inside of a container" is one
 * method per container:
 *
 * <pre>
 *   List&lt;String&gt; names = users.stream().map(User::name).toList();
 *   Optional&lt;String&gt; nm  = maybeUser.map(User::name);
 *   CompletableFuture&lt;String&gt; fn = futureUser.thenApply(User::name);
 *   Either&lt;Err, String&gt;        en = result.map(User::name);
 *   // Same idea, four different APIs.
 * </pre>
 *
 * <p>{@link org.higherkindedj.hkt.Functor Functor}{@code <F>} is the typeclass that captures the
 * idea once. It says: "container F supports {@code map(Function<A, B>, Kind<F, A>) -> Kind<F, B>}."
 * Every type the library supports has a {@code Functor} instance, so the same generic code works
 * for {@link List}, {@link Either}, {@link java.util.Optional Optional}, and so on.
 *
 * <p>Java idiom anchor. Functor is the abstraction behind {@link java.util.stream.Stream#map},
 * {@link java.util.Optional#map}, and {@link java.util.concurrent.CompletableFuture#thenApply}.
 * Each is a special case of {@code Functor.map} for one specific {@code F}.
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Use the concrete-type {@code map} on {@link Either} (success path) and observe that the
 *       failure path is unaffected.
 *   <li>Use {@code map} on {@link List} via the {@code ListMonad} instance.
 *   <li>Chain multiple {@code map} calls (Functor composition law in action).
 *   <li>Call {@code map} via the {@link EitherFunctor} typeclass instance — the form generic code
 *       uses.
 *   <li>Spot the most common Functor stumble: confusing {@code map} with {@code flatMap}.
 * </ol>
 *
 * <p>Functor laws (for completeness):
 *
 * <ul>
 *   <li>Identity: {@code map(x -> x) ≡ identity}
 *   <li>Composition: {@code map(g).map(f) ≡ map(x -> f(g(x)))}
 * </ul>
 *
 * <p>For the typeclass deep-dive see <a
 * href="../../../../../../../../../hkj-book/src/functional/functor.md">Functor</a> in the
 * Foundations chapter, including its "Things People Get Wrong" panel.
 */
@DisplayName("Tutorial 02: Functor Mapping")
public class Tutorial02_FunctorMapping {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: Map on the success side of Either
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Basic {@code map} on {@link Either}.
   *
   * <p>{@code map} transforms the value <em>inside</em> a {@code Right}; the structure (Left vs
   * Right) is preserved. This is the same shape as {@link java.util.Optional#map}.
   *
   * <p>Task: turn the integer into its string form.
   *
   * <pre>
   *   // Nudge:    Either has a map method that takes a Function&lt;A, B&gt;.
   *   // Strategy: Integer has a toString method (or use String.valueOf).
   *   // Spoiler:  either.map(String::valueOf)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: map transforms the Right value")
  void exercise1_mapEither() {
    Either<String, Integer> either = Either.right(42);

    Either<String, String> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight()).isEqualTo("42");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Map on the error side does nothing
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: {@code map} preserves structure.
   *
   * <p>When the {@code Either} is a {@code Left}, the function passed to {@code map} is never
   * invoked. Structure preservation is one of the two Functor laws and is what makes refactors safe
   * — replacing one {@code map} with another never changes whether we are still on the success
   * path.
   *
   * <p>Task: provide any function we like for the body of {@code map}; the test passes either way
   * because the function never runs on a {@code Left}.
   *
   * <pre>
   *   // Nudge:    The function inside map() is never invoked when the Either is Left.
   *   // Strategy: Anything that returns a String is fine - the test passes either way.
   *   // Spoiler:  i -&gt; "anything"
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: map on Left is a no-op")
  void exercise2_mapDoesNotAffectLeft() {
    Either<String, Integer> error = Either.left("Error occurred");

    Either<String, String> result = error.map(i -> answerRequired());

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Error occurred");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: Map on List via the ListMonad instance
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Generic {@code map} via the typeclass instance.
   *
   * <p>{@link ListMonad} carries a {@code Functor} instance for {@link List}. When we call {@code
   * monad.map(fn, kind)} we are using that instance directly, instead of the concrete-type
   * shortcut. This is the form library code uses to stay polymorphic.
   *
   * <p>Task: double every number.
   *
   * <pre>
   *   // Nudge:    monad.map takes the function first, then the Kind.
   *   // Strategy: Multiply by 2 with a lambda.
   *   // Spoiler:  monad.map(n -&gt; n * 2, numbers)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: ListMonad.map doubles each element")
  void exercise3_mapList() {
    ListMonad monad = ListMonad.INSTANCE;
    Kind<ListKind.Witness, Integer> numbers = LIST.widen(List.of(1, 2, 3, 4, 5));

    Kind<ListKind.Witness, Integer> doubled = answerRequired();

    assertThat(LIST.narrow(doubled)).containsExactly(2, 4, 6, 8, 10);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: Chaining maps (Functor composition law)
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Chaining transformations.
   *
   * <p>Multiple {@code map} calls compose left-to-right; each step transforms the result of the
   * previous one. The Functor composition law guarantees this is equivalent to a single {@code map}
   * of the composed function — important when reasoning about performance, since the library is
   * allowed to fuse adjacent maps (see {@link org.higherkindedj.hkt.coyoneda.Coyoneda} in the
   * advanced journey).
   *
   * <p>Task: chain three maps to do (×2, +5, then to String).
   *
   * <pre>
   *   // Nudge:    Each map() produces a new Either; chain them.
   *   // Strategy: value.map(...).map(...).map(...).
   *   // Spoiler:  value.map(n -&gt; n * 2).map(n -&gt; n + 5).map(String::valueOf)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: chain map operations")
  void exercise4_chainingMaps() {
    Either<String, Integer> value = Either.right(10);

    Either<String, String> result = answerRequired();

    assertThat(result.getRight()).isEqualTo("25");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: The Functor typeclass directly
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: Calling {@code map} via the {@link EitherFunctor} typeclass.
   *
   * <p>This is the same operation as {@code either.map(...)} on the concrete type, but expressed
   * generically. Code that takes a {@code Functor<F>} parameter can transform any container
   * uniformly without naming it.
   *
   * <p>Task: produce {@code "Value: 100"} from the wrapped {@code 100}. (The placeholder is inside
   * the lambda, not the call site.)
   *
   * <pre>
   *   // Nudge:    The lambda's input is the Integer inside the Right.
   *   // Strategy: Concatenate with "Value: ".
   *   // Spoiler:  "Value: " + i
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5: Functor typeclass instance for Either")
  void exercise5_functorTypeclass() {
    EitherFunctor<String> functor = EitherFunctor.instance();
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(100));

    Kind<EitherKind.Witness<String>, String> mapped = functor.map(i -> answerRequired(), kind);

    Either<String, String> result = EITHER.narrow(mapped);
    assertThat(result.getRight()).isEqualTo("Value: 100");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Method references work everywhere
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Method references with {@code map}.
   *
   * <p>Functor.map takes any {@code Function<A, B>}, including method references like {@code
   * String::toUpperCase}. The same code we would write with {@link java.util.stream.Stream#map}
   * works here.
   *
   * <p>Task: uppercase every word.
   *
   * <pre>
   *   // Nudge:    String has a toUpperCase method; we want a method reference.
   *   // Strategy: monad.map(METHOD_REFERENCE, words).
   *   // Spoiler:  monad.map(String::toUpperCase, words)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6: method references work with Functor.map")
  void exercise6_methodReferences() {
    ListMonad monad = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> words = LIST.widen(List.of("hello", "world", "java"));

    Kind<ListKind.Witness, String> uppercase = answerRequired();

    assertThat(LIST.narrow(uppercase)).containsExactly("HELLO", "WORLD", "JAVA");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: {@code map} vs {@code flatMap} when the function returns a wrapped value.
   *
   * <p>If the function we pass returns a plain {@code B}, we use {@code map} and get {@code F<B>}.
   * If the function returns an already-wrapped {@code F<B>}, we use {@code map} and get {@code
   * F<F<B>>} — nested containers we now have to flatten. {@code flatMap} (the Monad capability,
   * coming next tutorial) does the flatten for us.
   *
   * <p>The exercise below produces an {@code Either<String, Either<String, Integer>>} on purpose.
   * That nesting is the symptom of using {@code map} where {@code flatMap} was needed. Replace the
   * placeholder so we observe the nesting; the next tutorial teaches the fix.
   *
   * <pre>
   *   // Nudge:    parse() already returns Either; what does map do with that?
   *   // Strategy: It puts an Either inside an Either, leaving a nested shape.
   *   // Spoiler:  outer.map(parse) returns Either&lt;String, Either&lt;String, Integer&gt;&gt;.
   * </pre>
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

    // TODO: replace answerRequired() with outer.map(parse).
    // The result is nested: Either&lt;String, Either&lt;String, Integer&gt;&gt;.
    Either<String, Either<String, Integer>> nested = answerRequired();

    assertThat(nested.isRight()).isTrue();
    assertThat(nested.getRight().isRight()).isTrue();
    assertThat(nested.getRight().getRight()).isEqualTo(42);

    // The next tutorial will show how flatMap collapses this to a single Either.
  }

  /*
   * Where to next?
   *   • Tutorial 03 — Applicative combining. When two values do not depend on each other, we want
   *     to combine them in parallel; that is what map2 / map3 are for.
   *   • Foundations chapter — Functor. Includes the Things People Get Wrong panel that the
   *     diagnostic exercise above is drawn from.
   */
}
