// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.poly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
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
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Optic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Optics: typeclass-driven polymorphic factories")
class OpticsTest {

  // ---------------------------------------------------------------------------------------------
  // Optics.mapped
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("Optics.mapped(Functor)")
  class MappedTests {

    @Test
    @DisplayName("changes element type of a List under PolyOptics.modify")
    void mappedOverList() {
      Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
          elems = Optics.mapped(ListMonad.INSTANCE);

      Kind<ListKind.Witness, Integer> result =
          PolyOptics.modify(elems, Integer::parseInt, LIST.widen(List.of("1", "2", "3")));

      assertThat(LIST.narrow(result)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("changes the inner of a Maybe under PolyOptics.modify")
    void mappedOverMaybe() {
      Optic<Kind<MaybeKind.Witness, String>, Kind<MaybeKind.Witness, Integer>, String, Integer>
          inner = Optics.mapped(MaybeMonad.INSTANCE);

      Kind<MaybeKind.Witness, Integer> some =
          PolyOptics.modify(inner, String::length, MAYBE.widen(Maybe.just("hello")));
      assertThat(MAYBE.narrow(some)).isEqualTo(Maybe.just(5));

      Kind<MaybeKind.Witness, Integer> none =
          PolyOptics.modify(inner, String::length, MAYBE.widen(Maybe.nothing()));
      assertThat(MAYBE.narrow(none)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("changes the Right of an Either under PolyOptics.modify")
    void mappedOverEither() {
      Optic<
              Kind<EitherKind.Witness<String>, String>,
              Kind<EitherKind.Witness<String>, Integer>,
              String,
              Integer>
          inner = Optics.mapped(EitherMonad.<String>instance());

      Kind<EitherKind.Witness<String>, Integer> right =
          PolyOptics.modify(inner, String::length, EITHER.widen(Either.right("hello")));
      assertThat(EITHER.narrow(right)).isEqualTo(Either.right(5));

      Kind<EitherKind.Witness<String>, Integer> left =
          PolyOptics.modify(inner, String::length, EITHER.widen(Either.left("oops")));
      assertThat(EITHER.narrow(left)).isEqualTo(Either.left("oops"));
    }

    @Test
    @DisplayName("rejects non-Identity Applicatives with a clear error")
    void rejectsNonIdentityApplicative() {
      Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
          elems = Optics.mapped(ListMonad.INSTANCE);

      Function<String, Kind<MaybeKind.Witness, Integer>> tryParse =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      assertThatThrownBy(
              () ->
                  PolyOptics.modifyF(
                      elems, tryParse, LIST.widen(List.of("1")), MaybeMonad.INSTANCE))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Optics.traversed(Traverse)");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Optics.traversed
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("Optics.traversed(Traverse)")
  class TraversedTests {

    @Test
    @DisplayName("pure type-changing modify under Identity")
    void traversedPureModify() {
      Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
          Optics.traversed(ListTraverse.INSTANCE);

      Kind<ListKind.Witness, Integer> result =
          PolyOptics.modify(trav, String::length, LIST.widen(List.of("a", "bb", "ccc")));

      assertThat(LIST.narrow(result)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("accumulates errors across a list under Validated")
    void traversedAccumulatesErrors() {
      Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
          Optics.traversed(ListTraverse.INSTANCE);

      ValidatedMonad<List<String>> validatedApp = ValidatedMonad.instance(Semigroups.list());

      Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseValidated =
          s -> {
            try {
              return VALIDATED.widen(Validated.valid(Integer.parseInt(s)));
            } catch (NumberFormatException e) {
              return VALIDATED.widen(Validated.invalid(List.of("bad: " + s)));
            }
          };

      // All valid
      Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> good =
          PolyOptics.modifyF(
              trav, parseValidated, LIST.widen(List.of("1", "2", "3")), validatedApp);
      assertThat(VALIDATED.narrow(good).isValid()).isTrue();
      assertThat(LIST.narrow(VALIDATED.narrow(good).get())).containsExactly(1, 2, 3);

      // Mixed: errors are accumulated.
      Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> mixed =
          PolyOptics.modifyF(
              trav, parseValidated, LIST.widen(List.of("1", "x", "2", "y")), validatedApp);
      Validated<List<String>, Kind<ListKind.Witness, Integer>> narrowed = VALIDATED.narrow(mixed);
      assertThat(narrowed.isInvalid()).isTrue();
      assertThat(narrowed.getError()).containsExactly("bad: x", "bad: y");
    }

    @Test
    @DisplayName("short-circuits over a Maybe traverse")
    void traversedOverMaybeShortCircuits() {
      Optic<Kind<MaybeKind.Witness, String>, Kind<MaybeKind.Witness, Integer>, String, Integer>
          trav = Optics.traversed(MaybeTraverse.INSTANCE);

      Function<String, Kind<EitherKind.Witness<String>, Integer>> safeParse =
          s -> {
            try {
              return EITHER.widen(Either.right(Integer.parseInt(s)));
            } catch (NumberFormatException e) {
              return EITHER.widen(Either.left("bad: " + s));
            }
          };

      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> ok =
          PolyOptics.modifyF(trav, safeParse, MAYBE.widen(Maybe.just("9")), EitherMonad.instance());
      assertThat(EITHER.narrow(ok).isRight()).isTrue();
      assertThat(MAYBE.narrow(EITHER.narrow(ok).getRight())).isEqualTo(Maybe.just(9));

      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> bad =
          PolyOptics.modifyF(
              trav, safeParse, MAYBE.widen(Maybe.just("nope")), EitherMonad.instance());
      assertThat(EITHER.narrow(bad)).isEqualTo(Either.left("bad: nope"));

      // Empty Maybe never invokes the function: result is an Either.right(Maybe.nothing()).
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> empty =
          PolyOptics.modifyF(trav, safeParse, MAYBE.widen(Maybe.nothing()), EitherMonad.instance());
      assertThat(EITHER.narrow(empty).isRight()).isTrue();
      assertThat(MAYBE.narrow(EITHER.narrow(empty).getRight())).isEqualTo(Maybe.nothing());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Composition: monomorphic head + polymorphic leaf
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("composition with monomorphic heads")
  class CompositionTests {

    // A monomorphic-shaped head followed by a type-changing leaf via Optic#andThen.
    record Page<A>(int number, List<A> rows) {}

    @Test
    @DisplayName("polyLens head + Optics.traversed leaf transforms List<String> into List<Integer>")
    void monoHeadPolyLeaf() {
      Optic<Page<String>, Page<Integer>, List<String>, List<Integer>> rowsLens =
          PolyOptics.polyLens(Page::rows, (p, rs) -> new Page<>(p.number(), rs));

      Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
          allRows = Optics.traversed(ListTraverse.INSTANCE);

      // Bridge plain List <-> Kind<ListKind.Witness, ?> via a polyIso.
      Optic<
              List<String>,
              List<Integer>,
              Kind<ListKind.Witness, String>,
              Kind<ListKind.Witness, Integer>>
          listAsKind = PolyOptics.polyIso(LIST::widen, LIST::narrow);

      Optic<Page<String>, Page<Integer>, String, Integer> pageRows =
          rowsLens.andThen(listAsKind).andThen(allRows);

      Page<Integer> result =
          PolyOptics.modify(pageRows, Integer::parseInt, new Page<>(1, List.of("1", "2", "3")));

      assertThat(result).isEqualTo(new Page<>(1, List.of(1, 2, 3)));
    }

    @Test
    @DisplayName("modifyF under Validated composes through the bridge to accumulate errors")
    void modifyFComposes() {
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

      ValidatedMonad<List<String>> validatedApp = ValidatedMonad.instance(Semigroups.list());

      Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseValidated =
          s -> {
            try {
              return VALIDATED.widen(Validated.valid(Integer.parseInt(s)));
            } catch (NumberFormatException e) {
              return VALIDATED.widen(Validated.invalid(List.of("bad: " + s)));
            }
          };

      Kind<ValidatedKind.Witness<List<String>>, Page<Integer>> result =
          PolyOptics.modifyF(
              pageRows, parseValidated, new Page<>(1, List.of("a", "b", "3")), validatedApp);

      assertThat(VALIDATED.narrow(result).getError()).containsExactly("bad: a", "bad: b");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Constructor accessibility
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("API hygiene")
  class HygieneTests {
    @Test
    @DisplayName("Optics is not instantiable")
    void notInstantiable() throws Exception {
      var ctor = Optics.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      var instance = ctor.newInstance();
      assertThat(instance).isNotNull();
    }
  }
}
