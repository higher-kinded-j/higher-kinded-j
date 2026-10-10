// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.IdAssert.assertThatId;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.VStreamPathAssert.assertThatVStreamPath;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.LensExtensions;
import org.higherkindedj.optics.extensions.TraversalExtensions;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.free.OpticInterpreters;
import org.higherkindedj.optics.free.OpticPrograms;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.ixed.IxedInstances;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.ListPrisms;
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A null focus, as an optic over a {@code @Nullable} field or element meets it.
 *
 * <p>Two rules: a read that hands the focus on in an {@code Optional}, a {@code Maybe}, or an
 * Effect Path that says what a null focus becomes reads a null focus as absent, because none of
 * them holds {@code null}; every other read and write carries the null through. Each case pins a
 * read or a write that must not throw on a null focus.
 */
@DisplayName("A null focus")
class NullFocusTest {

  record Config(@Nullable String apiKey) {}

  private static final Lens<Config, @Nullable String> API_KEY =
      Lens.of(Config::apiKey, (config, apiKey) -> new Config(apiKey));

  private static final Config NO_KEY = new Config(null);

  /** {@code [null, value, null]}: a null first, a null last, and one value between. */
  private static List<@Nullable String> nullsAround(String value) {
    return Arrays.asList(null, value, null);
  }

  @Nested
  @DisplayName("read through an Optional or a Maybe")
  class ReadAsAbsent {

    @Test
    @DisplayName("Fold.preview reads a null first focus as absent; find passes over a null match")
    void foldReadsANullFocusAsAbsent() {
      Fold<List<@Nullable String>, @Nullable String> elements =
          Traversals.<@Nullable String>forList().asFold();
      List<@Nullable String> source = nullsAround("b");

      assertThat(elements.preview(source)).isEmpty();
      assertThat(elements.preview(Arrays.asList("a", null))).contains("a");
      assertThat(elements.find(_ -> true, source)).contains("b");
      assertThat(elements.find(Objects::nonNull, source)).contains("b");
      assertThat(elements.find(Objects::isNull, source)).isEmpty();
      assertThat(API_KEY.asFold().preview(NO_KEY)).isEmpty();
    }

    @Test
    @DisplayName(
        "TraversalPath.preview and toMaybePath read a null first focus as absent; find passes over"
            + " a null match")
    void traversalPathReadsANullFocusAsAbsent() {
      TraversalPath<List<@Nullable String>, @Nullable String> elements =
          TraversalPath.of(Traversals.forList());
      List<@Nullable String> source = nullsAround("b");

      assertThat(elements.preview(source)).isEmpty();
      assertThat(elements.preview(Arrays.asList("a", null))).contains("a");
      assertThat(elements.find(_ -> true, source)).contains("b");
      assertThat(elements.find(Objects::isNull, source)).isEmpty();
      assertThatMaybe(elements.toMaybePath(source).run()).isNothing();
      assertThatMaybe(elements.toMaybePath(Arrays.asList("a", null)).run()).hasValue("a");
    }

    @Test
    @DisplayName("FocusPath.toMaybePath gives Nothing for a null focus")
    void focusPathToMaybePath() {
      FocusPath<Config, @Nullable String> apiKey = FocusPath.of(API_KEY);

      assertThatMaybe(apiKey.toMaybePath(NO_KEY).run()).isNothing();
      assertThatMaybe(apiKey.toMaybePath(new Config("k")).run()).hasValue("k");
    }

    @Test
    @DisplayName("FocusPath.asAffine reads a null focus as absent, and still writes one")
    void focusPathAsAffine() {
      var apiKey = FocusPath.of(API_KEY).asAffine();

      assertThat(apiKey.getOptional(NO_KEY)).isEmpty();
      assertThat(apiKey.modify(String::toUpperCase, NO_KEY)).isEqualTo(NO_KEY);
      assertThat(apiKey.set(null, new Config("k"))).isEqualTo(NO_KEY);
    }

    @Test
    @DisplayName("a positional affine or prism over a list reads a null element as absent")
    void positionalListOpticsReadANullElementAsAbsent() {
      List<@Nullable String> source = nullsAround("b");

      assertThat(FocusPaths.<@Nullable String>listAt(0).getOptional(source)).isEmpty();
      assertThat(FocusPaths.<@Nullable String>listLast().getOptional(source)).isEmpty();
      assertThat(FocusPaths.<@Nullable String>arrayAt(0).getOptional(new String[] {null}))
          .isEmpty();
      assertThat(Affines.<@Nullable String>listHead().getOptional(source)).isEmpty();
      assertThat(Affines.<@Nullable String>listLast().getOptional(source)).isEmpty();
      assertThat(Affines.<@Nullable String>listAt(0).getOptional(source)).isEmpty();
      assertThat(Prisms.<@Nullable String>listHead().getOptional(source)).isEmpty();
      assertThat(Prisms.<@Nullable String>listLast().getOptional(source)).isEmpty();
      assertThat(Prisms.<@Nullable String>listAt(0).getOptional(source)).isEmpty();
      assertThat(ListPrisms.<@Nullable String>head().getOptional(source)).isEmpty();
      assertThat(ListPrisms.<@Nullable String>last().getOptional(source)).isEmpty();
    }

    @Test
    @DisplayName("IxedInstances.get reads a null element as absent")
    void ixedGet() {
      // An Ixed over ListTraversals.element visits the null element itself, where listIx(), going
      // through At, never reaches it.
      Ixed<List<@Nullable String>, Integer, @Nullable String> elementAt = ListTraversals::element;

      assertThat(IxedInstances.get(elementAt, 0, nullsAround("b"))).isEmpty();
      assertThat(IxedInstances.get(elementAt, 1, nullsAround("b"))).contains("b");
      assertThat(IxedInstances.get(IxedInstances.<@Nullable String>listIx(), 0, nullsAround("b")))
          .isEmpty();
    }

    @Test
    @DisplayName("IxedInstances.contains counts an index holding a null as present")
    void ixedContains() {
      Ixed<List<@Nullable String>, Integer, @Nullable String> elementAt = ListTraversals::element;

      assertThat(IxedInstances.contains(elementAt, 0, nullsAround("b"))).isTrue();
      assertThat(IxedInstances.contains(elementAt, 5, nullsAround("b"))).isFalse();
    }

    @Test
    @DisplayName("TraversalPath.headOption reads a null first element as absent, not the next one")
    void headOptionIsPositional() {
      var head = TraversalPath.of(Traversals.<@Nullable String>forList()).headOption();

      assertThat(head.getOptional(nullsAround("b"))).isEmpty();
      assertThat(head.getOptional(Arrays.asList("a", null))).contains("a");
    }
  }

  @Nested
  @DisplayName("handed to an effect type")
  class HandedToAnEffect {

    @Test
    @DisplayName("the traversal and Each bridges into a stream or list, and fold, leave a null out")
    void traversalBridgesLeaveANullFocusOut() {
      var each = TraversalPath.of(Traversals.<@Nullable String>forList());
      List<@Nullable String> source = Arrays.asList(null, "a", null, "b");

      assertThat(each.toListPath(source).run()).containsExactly("a", "b");
      assertThat(each.toNonDetPath(source).run()).containsExactly("a", "b");
      assertThat(each.toStreamPath(source).run().toList()).containsExactly("a", "b");
      assertThatVStreamPath(each.toVStreamPath(source)).producesElementsInOrder(List.of("a", "b"));
      assertThatVStreamPath(
              VStreamPath.fromEach(source, EachInstances.<@Nullable String>listEach()))
          .producesElementsInOrder(List.of("a", "b"));
      assertThat(each.fold(Monoids.string(), source)).isEqualTo("ab");
    }

    @Test
    @DisplayName(
        "an Effect Path's focus reads a null focus as absent wherever it is typed non-null")
    void pathFocusReadsANullFocusAsAbsent() {
      FocusPath<Config, @Nullable String> apiKey = FocusPath.of(API_KEY);
      AffinePath<Config, @Nullable String> apiKeyAffine = apiKey.asAffine();

      assertThatMaybe(Path.just(NO_KEY).focus(apiKey).run()).isNothing();
      assertThat(Path.present(NO_KEY).focus(apiKey).run()).isEmpty();
      assertThatMaybe(Path.just(NO_KEY).focus(apiKeyAffine).run()).isNothing();
      assertThat(Path.present(NO_KEY).focus(apiKeyAffine).run()).isEmpty();
      assertThatEither(Path.<String, Config>right(NO_KEY).focus(apiKeyAffine, "no key").run())
          .hasLeft("no key");
      assertThatMaybe(Path.id(NO_KEY).focus(apiKeyAffine).run()).isNothing();
      assertThatVStreamPath(Path.vstreamOf(NO_KEY, new Config("k")).focus(apiKeyAffine))
          .producesElementsInOrder(List.of("k"));
    }

    @Test
    @DisplayName("OpticPrograms.preview reads a null first focus as absent")
    void opticProgramsPreviewReadsANullFocusAsAbsent() {
      assertThat(OpticInterpreters.direct().run(OpticPrograms.preview(NO_KEY, API_KEY.asFold())))
          .isEmpty();
    }

    @Test
    @DisplayName("a function sees a null focus, and writes a value or leaves the null")
    void aFunctionSeesANullFocus() {
      Either<String, Config> keyed =
          LensExtensions.modifyEither(
              API_KEY, k -> Either.right(k == null ? "default" : k), NO_KEY);
      Either<String, List<@Nullable String>> filled =
          OpticOps.modifyAllEither(
              nullsAround("b"),
              Traversals.<@Nullable String>forList(),
              s -> Either.right(s == null ? "-" : s));
      Either<String, List<@Nullable String>> extended =
          TraversalExtensions.modifyAllEither(
              Traversals.<@Nullable String>forList(),
              s -> Either.right(s == null ? "-" : s),
              nullsAround("b"));
      EitherPath<String, List<String>> each =
          PathOps.traverseEachEither(
              nullsAround("b"),
              EachInstances.<@Nullable String>listEach(),
              s -> Path.right(s == null ? "-" : s));

      List<@Nullable String> kept =
          TraversalExtensions.modifyWherePossible(
              Traversals.<@Nullable String>forList(),
              s -> s == null ? Maybe.nothing() : Maybe.just(s.toUpperCase()),
              nullsAround("b"));

      assertThatEither(keyed).hasRight(new Config("default"));
      assertThatEither(filled).hasRight(List.of("-", "b", "-"));
      assertThatEither(extended).hasRight(List.of("-", "b", "-"));
      assertThatEither(each.run()).hasRight(List.of("-", "b", "-"));
      assertThat(kept).containsExactly(null, "B", null);
      assertThatVStreamPath(
              PathOps.traverseVStream(nullsAround("b"), s -> Path.vstreamPure(s == null ? "-" : s)))
          .producesElementsInOrder(List.of("-", "b", "-"));
    }

    @Test
    @DisplayName(
        "the helpers keep a null element their traversal passes over, though a Right or a Valid"
            + " cannot hold it")
    void theHelpersKeepANullElementTheTraversalPassesOver() {
      Traversal<List<@Nullable String>, @Nullable String> present =
          Traversals.<@Nullable String>forList().filtered(Objects::nonNull);
      Traversal<List<@Nullable String>, String> viaPrism =
          Traversals.<@Nullable String>forList().andThen(Prisms.<String>notNull().asTraversal());

      assertThatEither(
              TraversalExtensions.modifyAllEither(
                  present, s -> Either.<String, String>right(s + "!"), nullsAround("b")))
          .hasRight(Arrays.asList(null, "b!", null));
      assertThatEither(
              OpticOps.modifyAllEither(
                  nullsAround("b"), viaPrism, s -> Either.<String, String>right(s + "!")))
          .hasRight(Arrays.asList(null, "b!", null));
      assertThatEither(
              TraversalExtensions.modifyAllEither(
                  viaPrism, s -> Either.<String, String>left("bad " + s), Arrays.asList("x", "y")))
          .hasLeft("bad x");
      assertThatValidated(
              OpticOps.modifyAllValidated(
                  nullsAround("b"), viaPrism, s -> Validated.<String, String>valid(s + "!")))
          .hasValue(Arrays.asList(null, "b!", null));
      assertThatValidated(
              TraversalExtensions.modifyAllValidated(
                  viaPrism,
                  s -> Validated.<String, String>invalid("bad " + s),
                  Arrays.asList("x", null, "y")))
          .hasError(List.of("bad x", "bad y"));
      assertThatMaybe(
              TraversalExtensions.modifyAllMaybe(
                  viaPrism, s -> Maybe.just(s + "!"), nullsAround("b")))
          .hasValue(Arrays.asList(null, "b!", null));
      assertThatMaybe(
              TraversalExtensions.modifyAllMaybe(
                  viaPrism, s -> Maybe.<String>nothing(), nullsAround("b")))
          .isNothing();
    }
  }

  @Nested
  @DisplayName("carried through")
  class CarriedThrough {

    @Test
    @DisplayName("Setter.forList's effectful modify keeps a null element")
    void setterForListModifyF() {
      Kind<IdKind.Witness, List<@Nullable String>> result =
          Setter.<@Nullable String>forList().modifyF(Id::of, nullsAround("b"), IdMonad.instance());

      assertThatId(result).hasValue(nullsAround("b"));
    }

    @Test
    @DisplayName("an element traversal visits a null first element")
    void elementTraversalsVisitANullFirstElement() {
      List<@Nullable String> source = nullsAround("b");

      assertThat(Traversals.modify(FocusPaths.<@Nullable String>listElements(), s -> s, source))
          .containsExactly(null, "b", null);
      assertThat(
              Traversals.modify(
                  FocusPaths.<@Nullable String>arrayElements(), s -> s, new String[] {null, "b"}))
          .containsExactly(null, "b");
    }

    @Test
    @DisplayName("listCons, listSnoc, listTail and listInit keep a null element")
    void structuralListOpticsKeepANullElement() {
      List<@Nullable String> source = nullsAround("b");

      assertThat(FocusPaths.<@Nullable String>listCons().getOptional(source))
          .contains(Pair.of(null, Arrays.asList("b", null)));
      assertThat(FocusPaths.<@Nullable String>listSnoc().getOptional(source))
          .contains(Pair.of(Arrays.asList(null, "b"), null));
      assertThat(FocusPaths.<@Nullable String>listCons().build(Pair.of(null, List.of("b"))))
          .containsExactly(null, "b");
      assertThat(FocusPaths.<@Nullable String>listSnoc().build(Pair.of(List.of("b"), null)))
          .containsExactly("b", null);
      assertThat(FocusPaths.<@Nullable String>listTail().getOptional(source))
          .contains(Arrays.asList("b", null));
      assertThat(FocusPaths.<@Nullable String>listInit().getOptional(source))
          .contains(Arrays.asList(null, "b"));
      assertThat(FocusPaths.<@Nullable String>listTail().set(Arrays.asList(null, "c"), source))
          .containsExactly(null, null, "c");
      assertThat(FocusPaths.<@Nullable String>listInit().set(Arrays.asList("a", null), source))
          .containsExactly("a", null, null);
    }

    @Test
    @DisplayName("FocusPaths.mapValues keeps a null value")
    void mapValuesKeepsANullValue() {
      Map<String, @Nullable String> source = new HashMap<>();
      source.put("k", null);

      assertThat(
              Traversals.modify(FocusPaths.<String, @Nullable String>mapValues(), v -> v, source))
          .containsEntry("k", null)
          .hasSize(1);
    }

    @Test
    @DisplayName("a positional setter writes a null element, onto an empty list as well")
    void positionalSettersWriteANullElement() {
      List<@Nullable String> source = new ArrayList<>(List.of("a", "b"));

      assertThat(Affines.<@Nullable String>listHead().set(null, source)).containsExactly(null, "b");
      assertThat(Affines.<@Nullable String>listLast().set(null, source)).containsExactly("a", null);
      assertThat(Affines.<@Nullable String>listAt(1).set(null, source)).containsExactly("a", null);
      assertThat(Affines.<@Nullable String>listHead().set(null, List.of()))
          .containsExactly((String) null);
      assertThat(Affines.<@Nullable String>listLast().set(null, List.of()))
          .containsExactly((String) null);
      assertThat(Prisms.<@Nullable String>listHead().build(null)).containsExactly((String) null);
      assertThat(Prisms.<@Nullable String>listLast().build(null)).containsExactly((String) null);
      assertThat(ListPrisms.<@Nullable String>head().set(null, List.of()))
          .containsExactly((String) null);
      assertThat(ListPrisms.<@Nullable String>last().set(null, List.of()))
          .containsExactly((String) null);
    }
  }
}
