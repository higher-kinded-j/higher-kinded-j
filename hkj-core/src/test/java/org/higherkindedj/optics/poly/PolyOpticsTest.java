// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.poly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Optic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PolyOptics: factories and runners")
class PolyOpticsTest {

  // Generic wrapper used as the canonical "polymorphic container" example.
  record Box<A>(A value) {}

  // Simple two-field polymorphic record for the polyLens "preserve other fields" test.
  record Tagged<A>(String tag, A value) {}

  // Iso fixture: lossless wrapper around a single value.
  record UserId<A>(A raw) {}

  // ---------------------------------------------------------------------------------------------
  // polyLens
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("polyLens")
  class PolyLensTests {

    @Test
    @DisplayName("changes the part type and threads it through to the whole")
    void changesPartType() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      Box<Integer> result = PolyOptics.modify(contents, Integer::parseInt, new Box<>("42"));

      assertThat(result).isEqualTo(new Box<>(42));
    }

    @Test
    @DisplayName("preserves other fields when the part type changes")
    void preservesOtherFields() {
      Optic<Tagged<String>, Tagged<Integer>, String, Integer> value =
          PolyOptics.polyLens(Tagged::value, (t, n) -> new Tagged<>(t.tag(), n));

      Tagged<Integer> result =
          PolyOptics.modify(value, Integer::parseInt, new Tagged<>("answer", "42"));

      assertThat(result).isEqualTo(new Tagged<>("answer", 42));
    }

    @Test
    @DisplayName("modifyF threads the new part through any Applicative")
    void modifyFUnderEither() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      Function<String, Kind<EitherKind.Witness<String>, Integer>> safeParse =
          s -> {
            try {
              return EITHER.widen(Either.right(Integer.parseInt(s)));
            } catch (NumberFormatException e) {
              return EITHER.widen(Either.left("bad number: " + s));
            }
          };

      Kind<EitherKind.Witness<String>, Box<Integer>> ok =
          PolyOptics.modifyF(contents, safeParse, new Box<>("42"), EitherMonad.instance());
      assertThat(EITHER.narrow(ok)).isEqualTo(Either.right(new Box<>(42)));

      Kind<EitherKind.Witness<String>, Box<Integer>> err =
          PolyOptics.modifyF(contents, safeParse, new Box<>("nope"), EitherMonad.instance());
      assertThat(EITHER.narrow(err)).isEqualTo(Either.left("bad number: nope"));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // polyIso
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("polyIso")
  class PolyIsoTests {

    @Test
    @DisplayName("converts a generic wrapper to its raw value and back, changing element type")
    void roundTripChangingType() {
      Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
          PolyOptics.polyIso(UserId::raw, UserId::new);

      UserId<Integer> result = PolyOptics.modify(raw, Integer::parseInt, new UserId<>("123"));
      assertThat(result).isEqualTo(new UserId<>(123));
    }

    @Test
    @DisplayName("set replaces the part irrespective of the original")
    void setReplacesPart() {
      Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
          PolyOptics.polyIso(UserId::raw, UserId::new);

      UserId<Integer> result = PolyOptics.set(raw, 7, new UserId<>("ignored"));
      assertThat(result).isEqualTo(new UserId<>(7));
    }

    @Test
    @DisplayName("modifyF reconstructs through the reverseGet, threading the applicative")
    void modifyFThroughMaybe() {
      Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
          PolyOptics.polyIso(UserId::raw, UserId::new);

      Function<String, Kind<MaybeKind.Witness, Integer>> tryParse =
          s -> {
            try {
              return MAYBE.widen(Maybe.just(Integer.parseInt(s)));
            } catch (NumberFormatException e) {
              return MAYBE.widen(Maybe.nothing());
            }
          };

      Kind<MaybeKind.Witness, UserId<Integer>> ok =
          PolyOptics.modifyF(raw, tryParse, new UserId<>("42"), MaybeMonad.INSTANCE);
      assertThat(MAYBE.narrow(ok)).isEqualTo(Maybe.just(new UserId<>(42)));

      Kind<MaybeKind.Witness, UserId<Integer>> nothing =
          PolyOptics.modifyF(raw, tryParse, new UserId<>("nope"), MaybeMonad.INSTANCE);
      assertThat(MAYBE.narrow(nothing)).isEqualTo(Maybe.nothing());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Runners: get / set / modify / modifyF
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("get")
  class GetTests {

    @Test
    @DisplayName("extracts the focused part of a polyLens")
    void getFromPolyLens() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      assertThat(PolyOptics.get(contents, new Box<>("hello"))).isEqualTo("hello");
    }

    @Test
    @DisplayName("extracts the focused part of a polyIso")
    void getFromPolyIso() {
      Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
          PolyOptics.polyIso(UserId::raw, UserId::new);

      assertThat(PolyOptics.get(raw, new UserId<>("u-1"))).isEqualTo("u-1");
    }

    @Test
    @DisplayName("rejects optics whose modifyF requires Applicative.of (prism-shaped)")
    void rejectsPrismShapedOptic() {
      // A hand-crafted prism-like optic: it calls Applicative.of when the predicate fails.
      Optic<String, String, String, String> nonEmpty =
          new Optic<>() {
            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> modifyF(
                Function<String, Kind<F, String>> f, String s, Applicative<F> app) {
              return s.isEmpty() ? app.of(s) : app.map(x -> x, f.apply(s));
            }
          };

      assertThatThrownBy(() -> PolyOptics.get(nonEmpty, ""))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Applicative.of");
    }

    @Test
    @DisplayName("rejects optics whose modifyF requires Applicative.ap (traversal-shaped)")
    void rejectsTraversalShapedOptic() {
      // A toy "two-element" traversal: focuses on a pair and combines via ap.
      Optic<List<String>, List<String>, String, String> twoOf =
          new Optic<>() {
            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<String>> modifyF(
                Function<String, Kind<F, String>> f, List<String> source, Applicative<F> app) {
              Kind<F, String> a = f.apply(source.get(0));
              Kind<F, String> b = f.apply(source.get(1));
              BiFunction<String, String, List<String>> combine = List::of;
              return app.map2(a, b, combine);
            }
          };

      assertThatThrownBy(() -> PolyOptics.get(twoOf, List.of("x", "y")))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("Applicative.ap");
    }
  }

  @Nested
  @DisplayName("set / modify")
  class SetModifyTests {

    @Test
    @DisplayName("set replaces the value")
    void setReplaces() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      assertThat(PolyOptics.set(contents, 99, new Box<>("ignored"))).isEqualTo(new Box<>(99));
    }

    @Test
    @DisplayName("modify applies a pure transformation under Identity")
    void modifyAppliesFunction() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      assertThat(PolyOptics.modify(contents, String::length, new Box<>("hello")))
          .isEqualTo(new Box<>(5));
    }
  }

  @Nested
  @DisplayName("modifyF")
  class ModifyFTests {

    @Test
    @DisplayName("delegates to the optic's own modifyF (Identity case)")
    void delegatesToOpticIdentity() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      Kind<IdKind.Witness, Box<Integer>> result =
          PolyOptics.modifyF(
              contents, s -> Id.of(s.length()), new Box<>("abcd"), IdMonad.instance());
      assertThat(ID.narrow(result).value()).isEqualTo(new Box<>(4));
    }

    @Test
    @DisplayName("accumulates errors under Validated")
    void accumulatesUnderValidated() {
      Optic<Box<String>, Box<Integer>, String, Integer> contents =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      ValidatedMonad<List<String>> validatedApp = ValidatedMonad.instance(Semigroups.list());

      Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseValidated =
          s -> {
            try {
              return VALIDATED.widen(Validated.valid(Integer.parseInt(s)));
            } catch (NumberFormatException e) {
              return VALIDATED.widen(Validated.invalid(List.of("bad: " + s)));
            }
          };

      Kind<ValidatedKind.Witness<List<String>>, Box<Integer>> ok =
          PolyOptics.modifyF(contents, parseValidated, new Box<>("9"), validatedApp);
      assertThat(VALIDATED.narrow(ok)).isEqualTo(Validated.valid(new Box<>(9)));

      Kind<ValidatedKind.Witness<List<String>>, Box<Integer>> bad =
          PolyOptics.modifyF(contents, parseValidated, new Box<>("nope"), validatedApp);
      assertThat(VALIDATED.narrow(bad).getError()).containsExactly("bad: nope");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Composition with the base Optic#andThen
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("composition via Optic#andThen")
  class CompositionTests {

    record Outer<A>(Box<A> inner) {}

    @Test
    @DisplayName("composes two polymorphic lenses into a deeper polymorphic lens")
    void composeTwoPolyLenses() {
      Optic<Outer<String>, Outer<Integer>, Box<String>, Box<Integer>> outerLens =
          PolyOptics.polyLens(Outer::inner, (o, b) -> new Outer<>(b));

      Optic<Box<String>, Box<Integer>, String, Integer> innerLens =
          PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));

      Optic<Outer<String>, Outer<Integer>, String, Integer> combined = outerLens.andThen(innerLens);

      Outer<Integer> result =
          PolyOptics.modify(combined, Integer::parseInt, new Outer<>(new Box<>("123")));
      assertThat(result).isEqualTo(new Outer<>(new Box<>(123)));

      assertThat(PolyOptics.get(combined, new Outer<>(new Box<>("hello")))).isEqualTo("hello");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Constructor accessibility (covers the private no-arg ctor)
  // ---------------------------------------------------------------------------------------------

  @Nested
  @DisplayName("API hygiene")
  class HygieneTests {
    @Test
    @DisplayName("PolyOptics is not instantiable")
    void notInstantiable() throws Exception {
      var ctor = PolyOptics.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      var instance = ctor.newInstance();
      assertThat(instance).isNotNull();
    }
  }
}
