// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Optic;
import org.higherkindedj.optics.poly.Optics;
import org.higherkindedj.optics.poly.PolyOptics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 21: Polymorphic Optics.
 *
 * <p>The solutions are working code; each {@code @Test} carries a teaching block in the <em>Why
 * this is idiomatic / Alternative / Common wrong attempt</em> format established by the Foundations
 * journey. Read the working answer first, then the commentary.
 */
@DisplayName("Tutorial 21: Polymorphic Optics — Solutions")
public class Tutorial21_PolymorphicOptics_Solution {

  public record Box<A>(A value) {}

  public record UserId<A>(A raw) {}

  public record Page<A>(int number, List<A> rows) {}

  /**
   * Why this is idiomatic: {@code polyLens} is the natural lens shape for a generic wrapper — the
   * getter is just the accessor, and the setter rebuilds the wrapper with a value of a <em>possibly
   * different</em> type. {@code PolyOptics.modify} runs the optic under the Identity applicative so
   * the call site reads like a plain {@code with} method.
   *
   * <p>Alternative: write {@code new Box<>(Integer.parseInt(box.value()))} inline. Same answer, but
   * it does not compose into a longer optic chain.
   *
   * <p>Common wrong attempt: reach for a monomorphic {@code Lens<Box<String>, String>}. That lens
   * cannot return a {@code Box<Integer>} because its types are fixed at construction.
   */
  @Test
  @DisplayName("Exercise 1: polyLens turns Box<String> into Box<Integer>")
  void exercise1_polyLens() {
    Box<String> input = new Box<>("42");

    // SOLUTION: build a polymorphic lens with a type-changing setter.
    Optic<Box<String>, Box<Integer>, String, Integer> contents =
        PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

    Box<Integer> output = PolyOptics.modify(contents, Integer::parseInt, input);

    assertThat(output).isEqualTo(new Box<>(42));
    assertThat(input).isEqualTo(new Box<>("42"));
  }

  /**
   * Why this is idiomatic: {@code PolyOptics.get} re-uses the polymorphic optic we already have for
   * {@code modify} and {@code set}. We do not need a separate "getter" data type.
   *
   * <p>Alternative: call {@code Box::value} directly. That is fine for a single field, but it does
   * not compose with deeper polymorphic chains the way an optic does.
   *
   * <p>Common wrong attempt: pass a prism / traversal-shaped optic into {@code get}. {@code get} is
   * documented as lens-like only and raises a clear {@code UnsupportedOperationException} pointing
   * at {@code modifyF} in that case.
   */
  @Test
  @DisplayName("Exercise 2: PolyOptics.get extracts the focused part")
  void exercise2_polyLensGet() {
    Box<String> input = new Box<>("hello");
    Optic<Box<String>, Box<Integer>, String, Integer> contents =
        PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

    String focused = PolyOptics.get(contents, input);

    assertThat(focused).isEqualTo("hello");
  }

  /**
   * Why this is idiomatic: a single-field wrapper has no "other fields" to preserve, so the iso
   * form is more honest than a lens — {@code reverseGet} only needs the new part.
   *
   * <p>Alternative: write a {@code polyLens(UserId::raw, (u, n) -> new UserId<>(n))}. Equivalent
   * for one-field records, but iso is the right shape and signals the lossless symmetry.
   *
   * <p>Common wrong attempt: hand-roll a {@code Iso<UserId<String>, String>} (monomorphic). It
   * cannot widen the underlying type because both type parameters are fixed.
   */
  @Test
  @DisplayName("Exercise 3: polyIso converts UserId<String> to UserId<Integer>")
  void exercise3_polyIso() {
    UserId<String> input = new UserId<>("123");

    Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
        PolyOptics.polyIso(UserId::raw, UserId::new);

    UserId<Integer> output = PolyOptics.modify(raw, Integer::parseInt, input);

    assertThat(output).isEqualTo(new UserId<>(123));
  }

  /**
   * Why this is idiomatic: {@code Optics.mapped} surfaces the existing {@code Functor} instance as
   * an optic that composes with the rest of the optics ecosystem via {@code Optic#andThen}. Use it
   * when the operation is a pure type-changing map.
   *
   * <p>Alternative: {@code list.stream().map(Integer::parseInt).toList()}. Same outcome for one
   * step; the optic shines when the map is one leaf in a longer chain over a record.
   *
   * <p>Common wrong attempt: pass an effectful function (returning {@code Maybe}, {@code Either},
   * ...) into {@code mapped}. {@code mapped} is a polymorphic Setter under Identity; for effectful
   * traversal use {@code traversed} (Exercise 5).
   */
  @Test
  @DisplayName("Exercise 4: Optics.mapped changes the elements of a List under modify")
  void exercise4_mappedOverList() {
    List<String> input = List.of("1", "2", "3");

    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> elems =
        Optics.mapped(ListMonad.INSTANCE);

    Kind<ListKind.Witness, Integer> result =
        PolyOptics.modify(elems, Integer::parseInt, LIST.widen(input));

    assertThat(LIST.narrow(result)).containsExactly(1, 2, 3);
  }

  /**
   * Why this is idiomatic: a {@code Traverse} instance plus {@code Validated} together give us
   * "parse every element, accumulate every error" with no manual error book-keeping. Threading an
   * {@link Optic} through it means the same step composes with monomorphic optics on either side.
   *
   * <p>Alternative: a hand-written loop that builds a {@code List<String>} of errors and a {@code
   * List<Integer>} of values. Verbose, and does not interoperate with the Effect Path API.
   *
   * <p>Common wrong attempt: use {@code Optics.mapped} (the Setter) here. It only supports the
   * Identity applicative, so {@code Validated} cannot accumulate.
   */
  @Test
  @DisplayName("Exercise 5: Optics.traversed accumulates errors under Validated")
  void exercise5_traversedUnderValidated() {
    List<String> input = List.of("1", "x", "2", "y");

    Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseValidated =
        s -> {
          try {
            return VALIDATED.widen(Validated.valid(Integer.parseInt(s)));
          } catch (NumberFormatException e) {
            return VALIDATED.widen(Validated.invalid(List.of("bad: " + s)));
          }
        };

    ValidatedMonad<List<String>> validatedApp = ValidatedMonad.instance(Semigroups.list());

    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
        Optics.traversed(ListTraverse.INSTANCE);

    Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> result =
        PolyOptics.modifyF(trav, parseValidated, LIST.widen(input), validatedApp);

    Validated<List<String>, Kind<ListKind.Witness, Integer>> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();
    assertThat(narrowed.getError()).containsExactly("bad: x", "bad: y");
  }

  /**
   * Why this is idiomatic: composing the three pieces with {@code Optic#andThen} keeps every step
   * type-safe end-to-end. The middle "iso" is just a bridge between the plain {@code List} surface
   * and the HKT shape that {@code Optics.traversed} expects — it is not magic, it is a lossless
   * reinterpretation.
   *
   * <p>Alternative: skip the polymorphic head and operate on rows directly with a stream. The optic
   * version composes with anything else in your record ({@code Page::number}, etc.) and supports
   * {@code modifyF} under any applicative for free.
   *
   * <p>Common wrong attempt: drop the {@code listAsKind} bridge and try to call {@code
   * Optics.traversed(...)} on a plain {@code List}. The traversed optic is parameterised by {@code
   * Kind<F, A>}; the bridge is what allows the plain {@code List} produced by the lens to flow into
   * it.
   */
  @Test
  @DisplayName("Exercise 6: monomorphic head + polymorphic leaf transforms a whole record")
  void exercise6_compose() {
    Page<String> input = new Page<>(1, List.of("1", "2", "3"));

    Optic<Page<String>, Page<Integer>, List<String>, List<Integer>> rowsLens =
        PolyOptics.polyLens(Page::rows, (p, rs) -> new Page<>(p.number(), rs));

    Optic<
            List<String>,
            List<Integer>,
            Kind<ListKind.Witness, String>,
            Kind<ListKind.Witness, Integer>>
        listAsKind = PolyOptics.polyIso(LIST::widen, LIST::narrow);

    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
        allRows = Optics.traversed(ListTraverse.INSTANCE);

    Optic<Page<String>, Page<Integer>, String, Integer> pageRows =
        rowsLens.andThen(listAsKind).andThen(allRows);

    Page<Integer> output = PolyOptics.modify(pageRows, Integer::parseInt, input);

    assertThat(output).isEqualTo(new Page<>(1, List.of(1, 2, 3)));
  }

  /**
   * Why this is idiomatic: the diagnostic shows the exact symptom of choosing the wrong tool — a
   * clear runtime exception that names the right tool. The follow-up uses {@code traversed} under
   * {@code MaybeMonad} and works.
   *
   * <p>Alternative: simply remember the rule: {@code mapped(Functor)} for pure Setter, {@code
   * traversed(Traverse)} for full Traversal under any applicative.
   *
   * <p>Common wrong attempt: assume {@code mapped} is "more general" because Functor is a weaker
   * abstraction. In our optic surface, the more powerful applicative-compatible factory needs the
   * stronger {@code Traverse} instance. The error message points the way.
   */
  @Test
  @DisplayName("Diagnostic: mapped(Functor) is a Setter; use traversed(Traverse) for effects")
  void diagnostic_mappedVsTraversed() {
    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
        wrongChoice = Optics.mapped(ListMonad.INSTANCE);

    Function<String, Kind<MaybeKind.Witness, Integer>> tryParse =
        s -> {
          try {
            return MAYBE.widen(Maybe.just(Integer.parseInt(s)));
          } catch (NumberFormatException e) {
            return MAYBE.widen(Maybe.nothing());
          }
        };

    Assertions.assertThatThrownBy(
            () ->
                PolyOptics.modifyF(
                    wrongChoice, tryParse, LIST.widen(List.of("1")), MaybeMonad.INSTANCE))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Optics.traversed(Traverse)");

    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
        rightChoice = Optics.traversed(ListTraverse.INSTANCE);

    Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> result =
        PolyOptics.modifyF(
            rightChoice, tryParse, LIST.widen(List.of("1", "2")), MaybeMonad.INSTANCE);

    assertThat(MAYBE.narrow(result).isJust()).isTrue();
    assertThat(LIST.narrow(MAYBE.narrow(result).get())).containsExactly(1, 2);
  }

  /**
   * Why this is idiomatic: switching from {@code Validated} to {@code Either} is a one-line change
   * to the applicative; the optic itself is unchanged. That is the payoff of building traversals on
   * top of {@code Applicative}.
   *
   * <p>Alternative: hand-write a loop that parses elements and bails on the first failure. Same
   * outcome, more boilerplate, no composition.
   *
   * <p>Common wrong attempt: expect {@code Either} to accumulate errors. {@code Either} is a
   * fail-fast applicative; for accumulation use {@code Validated}.
   */
  @Test
  @DisplayName("Exercise 7: traversed short-circuits under Either")
  void exercise7_traversedShortCircuits() {
    List<String> input = List.of("1", "nope", "2");

    Function<String, Kind<EitherKind.Witness<String>, Integer>> safeParse =
        s -> {
          try {
            return EITHER.widen(Either.right(Integer.parseInt(s)));
          } catch (NumberFormatException e) {
            return EITHER.widen(Either.left("bad: " + s));
          }
        };

    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
        Optics.traversed(ListTraverse.INSTANCE);

    Kind<EitherKind.Witness<String>, Kind<ListKind.Witness, Integer>> result =
        PolyOptics.modifyF(trav, safeParse, LIST.widen(input), EitherMonad.instance());

    assertThat(EITHER.narrow(result)).isEqualTo(Either.left("bad: nope"));
  }
}
