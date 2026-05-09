// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
 * Tutorial 21: Polymorphic Optics — Type-Changing Updates Without Boilerplate.
 *
 * <p>Pain → Promise. Most of the time we update a record field with a value of the <em>same</em>
 * type ({@code Lens<User, String>}, {@code Prism<Result, Failure>}). Occasionally we want to change
 * the focused type along the way: parse {@code Box<String>} into {@code Box<Integer>}, or map every
 * element of {@code List<String>} to {@code List<Integer>} as a leaf step in a longer optic chain.
 * Hand-rolling that is messy:
 *
 * <pre>
 *   Box&lt;Integer&gt; result = new Box&lt;&gt;(Integer.parseInt(box.value()));   // tedious for every wrapper
 *   List&lt;Integer&gt; ints   = strings.stream().map(Integer::parseInt).toList(); // can't compose
 * </pre>
 *
 * <p>Higher-Kinded-J solves this with a small <b>polymorphic optics</b> surface in {@link
 * org.higherkindedj.optics.poly}:
 *
 * <pre>
 *   Optic&lt;Box&lt;String&gt;, Box&lt;Integer&gt;, String, Integer&gt; contents =
 *       PolyOptics.polyLens(Box::value, (b, n) -&gt; new Box&lt;&gt;(n));
 *   Box&lt;Integer&gt; result = PolyOptics.modify(contents, Integer::parseInt, new Box&lt;&gt;("42"));
 * </pre>
 *
 * <p>Java idiom anchors:
 *
 * <ul>
 *   <li>{@code PolyOptics.polyLens} ↔ a {@code with*} method whose return type is allowed to differ
 *       from its input type.
 *   <li>{@code PolyOptics.polyIso} ↔ a pair of conversion functions ({@code S -> A} and {@code B ->
 *       T}) that change types lossless-ly.
 *   <li>{@code Optics.mapped(Functor)} ↔ {@code Stream.map} for any unary HKT, lifted into an optic
 *       that composes with {@code andThen}.
 *   <li>{@code Optics.traversed(Traverse)} ↔ {@code List.stream().map(...).collect(...)} but under
 *       any {@link org.higherkindedj.hkt.Applicative} (so you can accumulate validation errors,
 *       short-circuit on Either, run async, ...).
 * </ul>
 *
 * <p>Key concepts:
 *
 * <ul>
 *   <li>The base {@link Optic} interface already carries four type parameters ({@code S, T, A, B});
 *       polymorphic optics simply <em>use</em> them.
 *   <li>The everyday monomorphic {@code Lens}, {@code Prism}, {@code Iso}, {@code Affine}, {@code
 *       Traversal}, and {@code Setter} stay unchanged. Polymorphic optics live in their own package
 *       as a deliberate escape hatch.
 *   <li>For most "type-changing" needs (DTO/V1↔V2 adapters, wrapper integration), the existing
 *       {@code dimap} / {@code map} / {@code contramap} <em>profunctor</em> adapters are the right
 *       tool. Polymorphic optics are for the smaller set of cases where the type change <em>is</em>
 *       the operation, not an adapter around it.
 * </ul>
 *
 * <p>Estimated time: 12-15 minutes.
 *
 * <p>This tutorial uses tiered hints (Nudge / Strategy / Spoiler) — read only as far as you need.
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 21: Polymorphic Optics")
public class Tutorial21_PolymorphicOptics {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Domain model ───────────────────────────────────────────────────────

  /** A generic single-field wrapper. The whole point of this tutorial is to change its A. */
  public record Box<A>(A value) {}

  /** A generic ID wrapper used to demonstrate polyIso. */
  public record UserId<A>(A raw) {}

  /** A page of rows. The "head" of our composition will be a polymorphic lens onto rows. */
  public record Page<A>(int number, List<A> rows) {}

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1: polyLens over a generic wrapper
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Build a polymorphic lens.
   *
   * <p>{@link PolyOptics#polyLens} takes a getter and a type-changing setter and returns an {@link
   * Optic} with four type parameters. We then use {@link PolyOptics#modify} to apply a pure
   * type-changing function.
   *
   * <p>Task: produce a {@code Box<Integer>} by parsing the inner value of a {@code Box<String>}.
   *
   * <pre>
   *   // Nudge:    polyLens needs a getter (Box -&gt; A) and a type-changing setter (Box, B) -&gt; new Box.
   *   // Strategy: PolyOptics.polyLens(Box::value, (b, n) -&gt; new Box&lt;&gt;(n)).
   *   // Spoiler:  PolyOptics.modify(contents, Integer::parseInt, input)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1: polyLens turns Box<String> into Box<Integer>")
  void exercise1_polyLens() {
    Box<String> input = new Box<>("42");

    // TODO: build a polymorphic lens that focuses on Box::value and rebuilds with a new value.
    Optic<Box<String>, Box<Integer>, String, Integer> contents = answerRequired();

    // TODO: use PolyOptics.modify with Integer::parseInt to produce a Box<Integer>.
    Box<Integer> output = answerRequired();

    assertThat(output).isEqualTo(new Box<>(42));
    assertThat(input).isEqualTo(new Box<>("42")); // original unchanged
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2: PolyOptics.get over a polyLens
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Extracting the focus of a polymorphic lens.
   *
   * <p>{@link PolyOptics#get} works for any optic built from {@code polyLens} or {@code polyIso}
   * (and their compositions). It internally uses a Const applicative; if you point it at a prism /
   * traversal-shaped optic it will throw a clear error.
   *
   * <p>Task: extract the focused {@code String} from a {@code Box<String>} via your polymorphic
   * lens.
   *
   * <pre>
   *   // Nudge:    The same polymorphic lens you built in Exercise 1 supports get.
   *   // Strategy: PolyOptics.get(contents, input).
   *   // Spoiler:  PolyOptics.get(contents, input)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2: PolyOptics.get extracts the focused part")
  void exercise2_polyLensGet() {
    Box<String> input = new Box<>("hello");
    Optic<Box<String>, Box<Integer>, String, Integer> contents =
        PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

    // TODO: extract the focused String using PolyOptics.get.
    String focused = answerRequired();

    assertThat(focused).isEqualTo("hello");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3: polyIso for a single-field wrapper
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Build a polymorphic iso.
   *
   * <p>An iso is a lossless two-way conversion. Because the wrapper has only one field, we can
   * reconstruct the whole from the new part directly without referring to the original source.
   * That's exactly what {@link PolyOptics#polyIso} expects.
   *
   * <p>Task: build a polymorphic iso between {@code UserId<String>} and {@code UserId<Integer>}.
   *
   * <pre>
   *   // Nudge:    polyIso takes a forward getter and a reverseGet that builds a new whole from a part.
   *   // Strategy: PolyOptics.polyIso(UserId::raw, UserId::new).
   *   // Spoiler:  PolyOptics.modify(raw, Integer::parseInt, input)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3: polyIso converts UserId<String> to UserId<Integer>")
  void exercise3_polyIso() {
    UserId<String> input = new UserId<>("123");

    // TODO: build a polymorphic iso for UserId.
    Optic<UserId<String>, UserId<Integer>, String, Integer> raw = answerRequired();

    // TODO: produce a UserId<Integer> by parsing the raw String.
    UserId<Integer> output = answerRequired();

    assertThat(output).isEqualTo(new UserId<>(123));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4: Optics.mapped over a Functor
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: A polymorphic Setter into the contents of any Functor.
   *
   * <p>{@link Optics#mapped(org.higherkindedj.hkt.Functor)} returns a polymorphic optic into the
   * elements of any unary {@link org.higherkindedj.hkt.Functor}. It is a polymorphic <b>Setter</b>:
   * it supports pure modifications via {@link PolyOptics#modify}. For effectful traversal use
   * {@code Optics.traversed} (Exercise 5).
   *
   * <p>Task: change the element type of a {@code List<String>} into {@code List<Integer>} using
   * {@code Optics.mapped} and {@code PolyOptics.modify}.
   *
   * <pre>
   *   // Nudge:    Optics.mapped wants a Functor instance; ListMonad.INSTANCE is a Functor.
   *   // Strategy: Optics.mapped(ListMonad.INSTANCE).
   *   // Spoiler:  PolyOptics.modify(elems, Integer::parseInt, LIST.widen(input))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4: Optics.mapped changes the elements of a List under modify")
  void exercise4_mappedOverList() {
    List<String> input = List.of("1", "2", "3");

    // TODO: build a polymorphic Setter for the elements of any List Functor.
    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> elems =
        answerRequired();

    // TODO: apply PolyOptics.modify with Integer::parseInt to obtain a Kind<ListKind.Witness,
    // Integer>.
    Kind<ListKind.Witness, Integer> result = answerRequired();

    assertThat(LIST.narrow(result)).containsExactly(1, 2, 3);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5: Optics.traversed under Validated
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: A polymorphic Traversal that accumulates errors.
   *
   * <p>{@link Optics#traversed(org.higherkindedj.hkt.Traverse)} returns a polymorphic optic that
   * runs under <em>any</em> {@link org.higherkindedj.hkt.Applicative}, including {@code Validated}
   * (which accumulates errors via the supplied {@code Semigroup}).
   *
   * <p>Task: parse a {@code List<String>} into a {@code Kind<ValidatedKind.Witness<List<String>>,
   * Kind<ListKind.Witness, Integer>>} that accumulates "bad: x" / "bad: y" errors.
   *
   * <pre>
   *   // Nudge:    Use Optics.traversed(ListTraverse.INSTANCE) and PolyOptics.modifyF.
   *   // Strategy: ValidatedMonad.instance(Semigroups.list()) is the Applicative you want.
   *   // Spoiler:  PolyOptics.modifyF(trav, parseValidated, LIST.widen(input), validatedApp)
   * </pre>
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

    // TODO: build a polymorphic traversal over List elements.
    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
        answerRequired();

    // TODO: run modifyF with the validated parser and assert that errors accumulate.
    Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> result =
        answerRequired();

    Validated<List<String>, Kind<ListKind.Witness, Integer>> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();
    assertThat(narrowed.getError()).containsExactly("bad: x", "bad: y");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6: Composition — monomorphic head + polymorphic leaf
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Compose a polyLens head with an Optics.traversed leaf.
   *
   * <p>The shipped optics compose via {@link Optic#andThen}. We focus a {@code Page<String>} onto
   * its rows with a {@code polyLens}, bridge the plain {@code List} to its HKT shape with a {@code
   * polyIso}, and traverse with {@code Optics.traversed} to change element type.
   *
   * <p>Task: complete the composition so that calling {@code PolyOptics.modify} on {@code
   * Page<String>} returns a {@code Page<Integer>} with each row parsed.
   *
   * <pre>
   *   // Nudge:    Three pieces compose with andThen: rowsLens, listAsKind, allRows.
   *   // Strategy: rowsLens.andThen(listAsKind).andThen(allRows).
   *   // Spoiler:  PolyOptics.modify(pageRows, Integer::parseInt, input)
   * </pre>
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

    // TODO: compose the three optics into a single optic Page<String> -> Page<Integer>.
    Optic<Page<String>, Page<Integer>, String, Integer> pageRows = answerRequired();

    // TODO: use PolyOptics.modify with Integer::parseInt to obtain the new page.
    Page<Integer> output = answerRequired();

    assertThat(output).isEqualTo(new Page<>(1, List.of(1, 2, 3)));
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic: when NOT to use polymorphic optics
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic exercise: when NOT to reach for {@link PolyOptics}.
   *
   * <p>Most "I want to change types" needs are <em>not</em> calls for polymorphic optics. If you
   * are mapping between an external DTO and your internal record, the right tool is the monomorphic
   * optic plus its {@code dimap} / {@code map} / {@code contramap} profunctor adapters. Polymorphic
   * optics are for the smaller case where the type change <em>is</em> the operation (typically over
   * a generic container the user authored).
   *
   * <p>The code below tries to use {@link Optics#mapped} under {@link MaybeMonad} as if it were a
   * full traversal — but {@code mapped} is a polymorphic <b>Setter</b> and only supports pure
   * modification under the Identity applicative. The right tool for the failable case is {@link
   * Optics#traversed} with a {@code Traverse} instance.
   *
   * <p>Lesson: <em>different tools for different jobs</em>. {@code Optics.mapped(Functor)} is the
   * Setter; {@code Optics.traversed(Traverse)} is the Traversal.
   */
  @Test
  @DisplayName("Diagnostic: mapped(Functor) is a Setter; use traversed(Traverse) for effects")
  void diagnostic_mappedVsTraversed() {
    // The wrong attempt: pass a Maybe-returning function into Optics.mapped.
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

    // The right shape: traversed(Traverse) under any Applicative.
    Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
        rightChoice = Optics.traversed(ListTraverse.INSTANCE);

    Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> result =
        PolyOptics.modifyF(
            rightChoice, tryParse, LIST.widen(List.of("1", "2")), MaybeMonad.INSTANCE);

    assertThat(MAYBE.narrow(result).isJust()).isTrue();
    assertThat(LIST.narrow(MAYBE.narrow(result).get())).containsExactly(1, 2);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Bonus: short-circuit with Either via Optics.traversed
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Optics.traversed short-circuits over an Either Applicative.
   *
   * <p>Same {@code traversed} optic as Exercise 5, but under {@link EitherMonad} the first error
   * stops the traversal.
   *
   * <p>Task: parse a list that contains a bad token, observing the short-circuit.
   *
   * <pre>
   *   // Nudge:    Use the same Optics.traversed(ListTraverse.INSTANCE) leaf.
   *   // Strategy: EitherMonad.&lt;String&gt;instance() is the Applicative.
   *   // Spoiler:  PolyOptics.modifyF(trav, safeParse, LIST.widen(input), EitherMonad.instance())
   * </pre>
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

    // TODO: run modifyF under EitherMonad and observe the short-circuit on the first failure.
    Kind<EitherKind.Witness<String>, Kind<ListKind.Witness, Integer>> result = answerRequired();

    assertThat(EITHER.narrow(result)).isEqualTo(Either.left("bad: nope"));
  }

  /**
   * Congratulations! You have completed Tutorial 21: Polymorphic Optics.
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to build a type-changing lens with {@link PolyOptics#polyLens}.
   *   <li>How to build a type-changing iso with {@link PolyOptics#polyIso}.
   *   <li>The difference between {@link Optics#mapped} (Setter under Identity) and {@link
   *       Optics#traversed} (Traversal under any Applicative).
   *   <li>How to compose monomorphic heads with polymorphic leaves via {@link Optic#andThen}.
   *   <li>When <em>not</em> to use polymorphic optics: profunctor adapters ({@code dimap} / {@code
   *       map} / {@code contramap}) cover the common DTO/wrapper cases.
   * </ul>
   *
   * <p>Next steps:
   *
   * <ul>
   *   <li>Read the Profunctor Optics chapter to see when monomorphic optics + adapters are the
   *       right tool.
   *   <li>Combine polymorphic optics with the Effect Path API for failable type-changing pipelines.
   * </ul>
   */
}
