// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial09 Coyoneda — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial09_Coyoneda_Solution {

  /**
   * Why this is idiomatic: {@code Coyoneda.lift(fa)} captures the value with the implicit identity
   * transformation — no functor instance needed at lift time. {@code lower} is the inverse, paid
   * for once when the actual {@code Functor} is available.
   *
   * <p>Alternative: skip Coyoneda entirely and call {@code MaybeFunctor.INSTANCE.map(fa, f)}
   * directly. Equivalent for one map; Coyoneda earns its keep when several maps stack up (Exercise
   * 3) and you want to fuse them.
   *
   * <p>Common wrong attempt: lower with the wrong functor (e.g. {@code ListMonad} for a {@code
   * MaybeKind} value). The widen/narrow boundary catches it at runtime, but the symptom (a {@code
   * ClassCastException} deep in {@code lower}) is harder to read than the type.
   */
  @Test
  void exercise1_lift() {
    Kind<MaybeKind.Witness, Integer> maybeValue = MAYBE.widen(Maybe.just(42));

    // SOLUTION: Use Coyoneda.lift
    Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(maybeValue);

    // Verify by lowering back
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> lowered = coyo.lower(functor);

    assertThat(MAYBE.narrow(lowered)).isEqualTo(Maybe.just(42));
  }

  /**
   * Why this is idiomatic: {@code coyo.map(f)} composes the function into Coyoneda's internal arrow
   * without ever asking for a {@code Functor}. The only place the functor shows up is at the final
   * {@code lower}.
   *
   * <p>Alternative: {@code MaybeFunctor.INSTANCE.map(maybeString, String::toUpperCase)}. Identical
   * result; the Coyoneda form lets you defer the choice of functor (handy when you're writing
   * generic code that should work for any {@code F}).
   *
   * <p>Common wrong attempt: passing a function with the wrong arity ({@code String::length} after
   * the call site already widened to {@code String}). The compiler still infers a {@code
   * Coyoneda<MaybeKind.Witness, Integer>}, but the next call expecting a string fails — trace the
   * type carefully through each {@code map}.
   */
  @Test
  void exercise2_mapWithoutFunctor() {
    Kind<MaybeKind.Witness, String> maybeString = MAYBE.widen(Maybe.just("hello"));
    Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.lift(maybeString);

    // SOLUTION: Map to uppercase
    Coyoneda<MaybeKind.Witness, String> upper = coyo.map(String::toUpperCase);

    // Lower to get result
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, String> result = upper.lower(functor);

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("HELLO"));
  }

  /**
   * Why this is idiomatic: this is the headline win — three {@code map}s but only one traversal at
   * {@code lower}. Coyoneda fuses the chain of arrows into a single composed function before the
   * functor ever runs.
   *
   * <p>Alternative: three calls to {@code listFunctor.map(...)}, each rebuilding the list. Same
   * answer; on a 5-element list the cost is invisible, on a million-element list it triples the
   * work.
   *
   * <p>Common wrong attempt: call {@code lower} between maps (perhaps to "see what's in there").
   * Each lower forces an immediate traversal and discards Coyoneda's fusion benefit; keep the value
   * in Coyoneda until the end of the pipeline.
   */
  @Test
  void exercise3_mapFusion() {
    Kind<ListKind.Witness, Integer> listValue = LIST.widen(List.of(1, 2, 3, 4, 5));
    Coyoneda<ListKind.Witness, Integer> coyo = Coyoneda.lift(listValue);

    // SOLUTION: Chain three maps
    Coyoneda<ListKind.Witness, String> chained =
        coyo.map(x -> x * 2).map(x -> x + 10).map(Object::toString);

    // Lower to execute
    MonadZero<ListKind.Witness> listFunctor = Instances.monadZero(list());
    Kind<ListKind.Witness, String> result = chained.lower(listFunctor);

    assertThat(LIST.narrow(result)).containsExactly("12", "14", "16", "18", "20");
  }

  /**
   * Why this is idiomatic: Coyoneda over {@code Nothing} stays empty — the captured arrow never
   * fires because the underlying functor's {@code map} on {@code Nothing} returns {@code Nothing}.
   * The fusion is free of cost when there is nothing to fuse over.
   *
   * <p>Alternative: skip Coyoneda for empty inputs (a guard that shortcuts the lift). Premature
   * optimisation; Coyoneda over an empty container is already free.
   *
   * <p>Common wrong attempt: assume the {@code map(x -> x * 2)} runs and produces a default — it
   * doesn't. The function is stored, and the underlying functor decides whether to invoke it; for
   * {@code Nothing} it doesn't.
   */
  @Test
  void exercise4_withNothing() {
    Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());

    // SOLUTION: Lift and map over nothing
    Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(nothing).map(x -> x * 2);

    // Lower
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> result = coyo.lower(functor);

    // Should still be Nothing
    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  /**
   * Why this is idiomatic: a real pipeline (trim, uppercase, decorate) lifted into Coyoneda is
   * three composed arrows; one walk over the list at {@code lower} produces the final list. The
   * sequence reads top-to-bottom in the order it actually executes.
   *
   * <p>Alternative: a single {@code .map(s -> "[" + s.trim().toUpperCase() + "]")}. Same fused
   * traversal; lose the named steps. Reach for the composed lambda in hot code; keep the Coyoneda
   * chain when the steps need to be assembled from elsewhere.
   *
   * <p>Common wrong attempt: try to short-circuit the chain partway through with an {@code if/else}
   * on the value. Coyoneda is parametric — the arrows do not see the values until {@code lower};
   * conditional logic belongs inside one of the {@code map} functions, not around them.
   */
  @Test
  void exercise5_pipeline() {
    Kind<ListKind.Witness, String> names = LIST.widen(List.of("  alice  ", " bob ", "charlie"));

    // SOLUTION: Build the full pipeline
    Coyoneda<ListKind.Witness, String> pipeline =
        Coyoneda.lift(names).map(String::trim).map(String::toUpperCase).map(s -> "[" + s + "]");

    // Execute
    MonadZero<ListKind.Witness> listFunctor = Instances.monadZero(list());
    Kind<ListKind.Witness, String> result = pipeline.lower(listFunctor);

    assertThat(LIST.narrow(result)).containsExactly("[ALICE]", "[BOB]", "[CHARLIE]");
  }
}
