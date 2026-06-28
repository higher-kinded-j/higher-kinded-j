// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Understanding {@code Kind<F, A>} — the foundation of HKTs in Java.
 *
 * <p>Pain → Promise. Suppose we want to write a method that takes "any container F holding an A"
 * and returns the same container holding a B. In imperative Java we cannot:
 *
 * <pre>
 *   // We want this signature, but Java cannot express it:
 *   //
 *   //   &lt;F&lt;?&gt;, A, B&gt; F&lt;B&gt; map(F&lt;A&gt; container, Function&lt;A, B&gt; fn);
 *   //
 *   // Java has no way to spell "the same shape F" once we have erased to F&lt;?&gt;.
 *
 *   public &lt;A, B&gt; List&lt;B&gt; mapList(List&lt;A&gt; xs, Function&lt;A, B&gt; fn)        { ... }
 *   public &lt;A, B&gt; Optional&lt;B&gt; mapOpt(Optional&lt;A&gt; o, Function&lt;A, B&gt; fn)     { ... }
 *   public &lt;L, A, B&gt; Either&lt;L, B&gt; mapEither(Either&lt;L, A&gt; e, Function&lt;A, B&gt; fn) { ... }
 *   // ... and so on, one method per container, forever.
 * </pre>
 *
 * <p>{@link Kind Kind&lt;F, A&gt;} is the workaround. It moves the "shape" into a regular type
 * parameter {@code F} (called the <em>witness</em>) so a single generic method can talk about a
 * container <em>without committing to which one it is</em>:
 *
 * <pre>
 *   &lt;F, A, B&gt; Kind&lt;F, B&gt; map(Kind&lt;F, A&gt; container, Function&lt;A, B&gt; fn);
 * </pre>
 *
 * <p>That signature is legal Java. It is also the entire reason {@link Kind} exists.
 *
 * <p>Java idiom anchor. The shape {@code Kind<F, A>} is a generalisation of patterns we have
 * already used in plain Java:
 *
 * <ul>
 *   <li>A {@link java.util.stream.Stream Stream}{@code <Order>} is a shape (Stream) holding an
 *       element type (Order). Stream is fixed, Order is free.
 *   <li>A {@link java.util.concurrent.CompletableFuture CompletableFuture}{@code <Response>} is a
 *       shape (Future) holding a result type (Response).
 *   <li>A {@link java.util.Optional Optional}{@code <User>} is a shape (Optional) holding a user
 *       type.
 * </ul>
 *
 * <p>{@code Kind<F, A>} is the same idea, except we get to write {@code F} as a type parameter
 * rather than baking it into a method name.
 *
 * <p>What we will do here:
 *
 * <ol>
 *   <li>Take a concrete {@link Either} and <em>widen</em> it to {@code Kind<EitherKind.Witness<L>,
 *       R>}.
 *   <li>Take a {@code Kind} and <em>narrow</em> it back to the concrete type when we want
 *       container-specific methods.
 *   <li>Repeat for {@link List}.
 *   <li>See what the <em>witness</em> type contributes (and why we need it at all).
 * </ol>
 *
 * <p>Tiered hints. Every exercise carries three lines of help, in order. Read top-to-bottom and
 * stop as soon as we have what we need:
 *
 * <ul>
 *   <li><b>Nudge</b> — restates the concept that should fire.
 *   <li><b>Strategy</b> — names the method or value involved.
 *   <li><b>Spoiler</b> — the literal answer.
 * </ul>
 *
 * <p>Reading the Spoiler is fine; copying it before reading the Strategy is the line we should try
 * not to cross.
 *
 * <p>For the underlying machinery, see <a
 * href="../../../../../../../../../hkj-book/src/hkts/lifting_the_hood.md">Lifting the Hood</a> in
 * the Foundations chapter, which traces a single {@code flatMap} call all the way through {@code
 * widen} and {@code narrow}.
 */
@DisplayName("Tutorial 01: Kind Basics")
public class Tutorial01_KindBasics {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: Widening a concrete type to Kind
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Widening to Kind.
   *
   * <p>Every higher-kinded type in this library can be <em>widened</em> to its {@code Kind}
   * representation. Widening is what lets generic combinators like {@code map} and {@code flatMap}
   * accept any container without knowing the exact type.
   *
   * <p>Task: widen an {@code Either<String, Integer>} to {@code Kind<EitherKind.Witness<String>,
   * Integer>}.
   *
   * <pre>
   *   // Nudge:    There is a single helper called EITHER that knows how to widen.
   *   // Strategy: EITHER exposes a widen() method.
   *   // Spoiler:  EITHER.widen(either)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: widen Either to Kind")
  void exercise1_widenEitherToKind() {
    Either<String, Integer> either = Either.right(42);

    Kind<EitherKind.Witness<String>, Integer> kind = answerRequired();

    // Sanity: narrow back and check the value survived the round-trip.
    assertThatEither(EITHER.narrow(kind)).hasRight(42);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: Narrowing back to the concrete type
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Narrowing from Kind.
   *
   * <p>Once we have a {@code Kind}, we can narrow it back to the concrete type whenever we need a
   * container-specific method (like {@code Either.fold} or {@code Either.swap}).
   *
   * <p>Task: narrow {@code kind} to {@link Either} and confirm the wrapped value is intact.
   *
   * <pre>
   *   // Nudge:    Narrow is the inverse of widen.
   *   // Strategy: EITHER exposes a narrow() method.
   *   // Spoiler:  EITHER.narrow(kind)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: narrow Kind back to Either")
  void exercise2_narrowKindToEither() {
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(100));

    Either<String, Integer> either = answerRequired();

    assertThatEither(either).isRight().hasRight(100);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: The same trick works for List
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: List as a higher-kinded type.
   *
   * <p>{@link List} is a higher-kinded type just like {@link Either}. The widen/narrow pattern is
   * identical; only the helper changes ({@code LIST} instead of {@code EITHER}).
   *
   * <p>Task: widen the list to {@code Kind}, then narrow it back.
   *
   * <pre>
   *   // Nudge:    LIST is to List what EITHER is to Either.
   *   // Strategy: LIST.widen(list) and LIST.narrow(kind).
   *   // Spoiler:  Kind&lt;ListKind.Witness, String&gt; kind = LIST.widen(list);
   *   //           List&lt;String&gt; back               = LIST.narrow(kind);
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: round-trip a List through Kind")
  void exercise3_listKindConversion() {
    List<String> list = List.of("apple", "banana", "cherry");

    Kind<ListKind.Witness, String> kind = answerRequired();

    List<String> narrowedList = answerRequired();

    assertThat(narrowedList).hasSize(3).containsExactly("apple", "banana", "cherry");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: What the witness type contributes
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Witness types.
   *
   * <p>The witness {@code F} in {@code Kind<F, A>} is what tells the compiler <em>which</em>
   * container we are working with. It is a phantom type — never instantiated, never allocated —
   * whose only job is to give the type system a name to dispatch on.
   *
   * <ul>
   *   <li>{@code EitherKind.Witness<L>} for {@code Either<L, R>}
   *   <li>{@code ListKind.Witness} for {@code List<A>}
   *   <li>{@code MaybeKind.Witness} for {@code Maybe<A>}
   * </ul>
   *
   * <p>The witness is exactly what lets us write a single {@code map} method that works for {@link
   * List}, {@link Either}, {@link java.util.Optional Optional}, and every other container the
   * library supports — without losing the precise return type.
   *
   * <p>Task: spell out the witness type for {@code Either<String, Boolean>}, then round-trip
   * through {@code Kind} and back.
   *
   * <pre>
   *   // Nudge:    Either has two type parameters; the left one is fixed in the witness.
   *   // Strategy: Witness type is EitherKind.Witness&lt;LEFT_TYPE&gt;.
   *   // Spoiler:  Kind&lt;EitherKind.Witness&lt;String&gt;, Boolean&gt;
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: witness types name the container shape")
  void exercise4_understandingWitnessTypes() {
    Either<String, Boolean> either = Either.right(true);

    // The right-hand side is already correct; the lesson is in the type
    // declaration on the left. Read it: "a Kind whose shape is Either with
    // a String left, and whose value type is Boolean".
    Kind<EitherKind.Witness<String>, Boolean> kind = EITHER.widen(either);

    Either<String, Boolean> result = EITHER.narrow(kind);
    assertThatEither(result).hasRight(true);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic: a wrong attempt and its symptom.
   *
   * <p>A common stumble: trying to narrow a {@code Kind} with the <em>wrong</em> helper. The helper
   * baked into the {@code Kind}'s witness type is the only one that knows how to read it.
   *
   * <p>Below, {@code listKind} is a {@code Kind<ListKind.Witness, Integer>}. Calling {@code
   * EITHER.narrow(listKind)} does not even compile because the witness types do not match — the
   * type system has caught the mistake at compile time, exactly as designed.
   *
   * <p>That is the entire reason for the witness type. Try uncommenting the broken line below and
   * watch the compiler reject it before the JVM ever runs:
   *
   * <pre>
   *   // Either&lt;String, Integer&gt; broken = EITHER.narrow(listKind);
   *   //                                  ^^^^^^^^^^^^^^^^^^^^^^^^^^
   *   // error: incompatible types: Kind&lt;ListKind.Witness, Integer&gt;
   *   //         cannot be converted to Kind&lt;EitherKind.Witness&lt;capture#1 of ?&gt;, Integer&gt;
   * </pre>
   *
   * <p>Task: complete the test by widening with the matching helper and narrowing back to the
   * matching concrete type.
   *
   * <pre>
   *   // Nudge:    Pick the helper that goes with the container we are using.
   *   // Strategy: List ↔ LIST, Either ↔ EITHER, Maybe ↔ MAYBE, ...
   *   // Spoiler:  LIST.widen(list) and LIST.narrow(kind) for a List.
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: narrow with the helper that matches the witness")
  void diagnostic_witnessMustMatchHelper() {
    List<Integer> list = List.of(1, 2, 3);
    Kind<ListKind.Witness, Integer> listKind = answerRequired();

    // Verify the narrow is also matched.
    List<Integer> back = answerRequired();
    assertThat(back).containsExactly(1, 2, 3);

    // The compile-time reason this is safe: ListKind.Witness and EitherKind.Witness<L> are
    // distinct types, so the type system will reject any cross-helper narrow attempt before
    // the JVM ever runs.
  }

  /*
   * Where to next?
   *   • Tutorial 02 — Functor mapping. Now that we can move concrete types in and out of Kind,
   *     the next step is the first thing we typically *do* with one: transform the value inside.
   *   • Foundations chapter — "Lifting the Hood". A token-by-token trace of one flatMap call
   *     through widen, dispatch, and narrow, with the JIT cost called out.
   */
}
