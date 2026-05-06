// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial 01: Kind Basics — the chapter's first "teaching solution".
 *
 * <p>Each exercise is annotated with three short blocks:
 *
 * <ol>
 *   <li><b>Why this is idiomatic</b> — what makes the chosen form the standard one.
 *   <li><b>Alternative</b> — at least one other shape that also works, with the trade-off.
 *   <li><b>Common wrong attempt</b> — a typical first stumble and the symptom it produces.
 * </ol>
 *
 * <p>Read the code first, then the commentary. Reading the commentary <em>before</em> the code
 * tells us what to look for; reading it <em>after</em> tells us why we should care.
 */
@DisplayName("Tutorial 01 Solution: Kind Basics")
public class Tutorial01_KindBasics_Solution {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code EITHER.widen(either)} reads as "tell the type system this is now
   * a Kind". It is a no-op at runtime — no allocation, no copy — and a single, well-known method
   * call.
   *
   * <p>Alternative: ad-hoc casts like {@code (Kind<EitherKind.Witness<String>, Integer>) either}
   * happen to work because every library type implements its own {@code Kind} interface, but they
   * tie us to that implementation detail. The {@code EITHER} helper is the public, supported
   * boundary.
   *
   * <p>Common wrong attempt: skipping the widen and passing the bare {@code Either} into a method
   * with signature {@code <F, A> ... f(Kind<F, A> ...)}. The compiler rejects it: an {@code
   * Either<L, R>} is not by itself a {@code Kind<F, A>} from the caller's perspective; the
   * library's generic combinators take {@code Kind}, not the concrete type.
   */
  @Test
  @DisplayName("Exercise 1: widen Either to Kind")
  void exercise1_widenEitherToKind() {
    Either<String, Integer> either = Either.right(42);

    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);

    assertThat(EITHER.narrow(kind).getRight()).isEqualTo(42);
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code EITHER.narrow(kind)} is the single supported way to get back to
   * concrete-type methods (like {@code fold} or {@code swap}). It includes a runtime check that the
   * {@code Kind} really came from an {@code Either}.
   *
   * <p>Alternative: keep working at the {@code Kind} level — never narrow until we want to extract
   * a final value. In a longer pipeline this is preferable; we only narrow at the boundary.
   *
   * <p>Common wrong attempt: calling {@code Either.narrow} (no helper) on a {@code Kind} obtained
   * from a different container, or casting {@code (Either<...>) kind}. Either form throws (or fails
   * to compile) — the witness type is exactly what catches this.
   */
  @Test
  @DisplayName("Exercise 2: narrow Kind back to Either")
  void exercise2_narrowKindToEither() {
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(100));

    Either<String, Integer> either = EITHER.narrow(kind);

    assertThat(either.isRight()).isTrue();
    assertThat(either.getRight()).isEqualTo(100);
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code LIST} mirrors {@code EITHER} exactly. Once we know the pattern
   * for one container we know it for every container — that uniformity is the whole point of the
   * abstraction.
   *
   * <p>Alternative: {@code LIST::widen} and {@code LIST::narrow} as method references when passing
   * to higher-order code. Same call, slightly more concise in functional position.
   *
   * <p>Common wrong attempt: using a {@code List} method directly without widening, then trying to
   * pass the result to a generic combinator. The compiler will complain that the parameter type
   * does not match {@code Kind<F, A>}.
   */
  @Test
  @DisplayName("Exercise 3: round-trip a List through Kind")
  void exercise3_listKindConversion() {
    List<String> list = List.of("apple", "banana", "cherry");

    Kind<ListKind.Witness, String> kind = LIST.widen(list);

    List<String> narrowedList = LIST.narrow(kind);

    assertThat(narrowedList).hasSize(3).containsExactly("apple", "banana", "cherry");
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: writing the witness type out longhand makes the dispatch explicit. The
   * compiler reads {@code EitherKind.Witness<String>} and immediately knows which {@code Functor},
   * {@code Applicative}, or {@code Monad} instance to wire in.
   *
   * <p>Alternative: let local variable type inference do the work — {@code var kind =
   * EITHER.widen(either);} — when the surrounding code makes the intent obvious. Use the longhand
   * form when we are <em>teaching</em> the shape (as here) or when reviewing a diff.
   *
   * <p>Common wrong attempt: writing {@code Kind<EitherKind.Witness, Boolean>} (no inner type
   * argument). {@code EitherKind.Witness} is itself parameterised — that's how the library knows
   * which left type goes with which right type. Drop the parameter and the compiler treats the left
   * side as a raw type and starts emitting unchecked warnings.
   */
  @Test
  @DisplayName("Exercise 4: witness types name the container shape")
  void exercise4_understandingWitnessTypes() {
    Either<String, Boolean> either = Either.right(true);

    Kind<EitherKind.Witness<String>, Boolean> kind = EITHER.widen(either);

    Either<String, Boolean> result = EITHER.narrow(kind);
    assertThat(result.getRight()).isTrue();
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code LIST.widen} / {@code LIST.narrow} match the witness type baked
   * into the {@code Kind}. Mismatching helpers would either fail to compile (different witness) or
   * throw {@code KindUnwrapException} at runtime (same witness, wrong instance).
   *
   * <p>Alternative: {@code LIST::widen} and {@code LIST::narrow} as method references in
   * higher-order positions; functionally identical.
   *
   * <p>Common wrong attempt: cross-helper narrows like {@code EITHER.narrow(listKind)}. The compile
   * error from the witness-type mismatch is the exact safety net this layer was designed to
   * provide; if we see that error, the type system has done its job.
   */
  @Test
  @DisplayName("Diagnostic: narrow with the helper that matches the witness")
  void diagnostic_witnessMustMatchHelper() {
    List<Integer> list = List.of(1, 2, 3);
    Kind<ListKind.Witness, Integer> listKind = LIST.widen(list);

    List<Integer> back = LIST.narrow(listKind);
    assertThat(back).containsExactly(1, 2, 3);
  }
}
