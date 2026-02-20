// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Operational tests for each VStream type class instance. These tests verify concrete behaviour
 * beyond the algebraic laws tested in VStreamLawsTest.
 */
@DisplayName("VStream Type Class Operational Tests")
class VStreamTypeClassTest {

  // ==================== VStreamFunctor ====================

  @Nested
  @DisplayName("VStreamFunctor")
  class VStreamFunctorTests {

    private final VStreamFunctor functor = VStreamFunctor.INSTANCE;

    @Test
    @DisplayName("map transforms each element")
    void mapTransformsEachElement() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      Kind<VStreamKind.Witness, String> result = functor.map(i -> "v" + i, stream);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly("v1", "v2", "v3");
    }

    @Test
    @DisplayName("map on empty stream returns empty")
    void mapOnEmptyStreamReturnsEmpty() {
      Kind<VStreamKind.Witness, Integer> empty = VSTREAM.widen(VStream.empty());

      Kind<VStreamKind.Witness, String> result = functor.map(Object::toString, empty);

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("map preserves stream length")
    void mapPreservesStreamLength() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3, 4, 5));

      Kind<VStreamKind.Witness, Integer> result = functor.map(x -> x * 2, stream);

      assertThat(VSTREAM.narrow(result).count().run()).isEqualTo(5);
    }
  }

  // ==================== VStreamApplicative ====================

  @Nested
  @DisplayName("VStreamApplicative")
  class VStreamApplicativeTests {

    private final VStreamApplicative applicative = VStreamApplicative.INSTANCE;

    @Test
    @DisplayName("of creates single-element stream")
    void ofCreatesSingleElementStream() {
      Kind<VStreamKind.Witness, Integer> result = applicative.of(42);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(42);
    }

    @Test
    @DisplayName("ap applies function stream to value stream (Cartesian product)")
    void apAppliesFunctionStreamToValueStream() {
      Kind<VStreamKind.Witness, Function<Integer, String>> fns =
          VSTREAM.widen(VStream.of(i -> "a" + i, i -> "b" + i));
      Kind<VStreamKind.Witness, Integer> values = VSTREAM.widen(VStream.of(1, 2));

      Kind<VStreamKind.Witness, String> result = applicative.ap(fns, values);

      // Cartesian product: [a1, a2, b1, b2]
      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly("a1", "a2", "b1", "b2");
    }

    @Test
    @DisplayName("ap with empty function stream returns empty")
    void apWithEmptyFunctionStreamReturnsEmpty() {
      Kind<VStreamKind.Witness, Function<Integer, String>> fns = VSTREAM.widen(VStream.empty());
      Kind<VStreamKind.Witness, Integer> values = VSTREAM.widen(VStream.of(1, 2, 3));

      Kind<VStreamKind.Witness, String> result = applicative.ap(fns, values);

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("ap with empty value stream returns empty")
    void apWithEmptyValueStreamReturnsEmpty() {
      Kind<VStreamKind.Witness, Function<Integer, String>> fns =
          VSTREAM.widen(VStream.of(i -> "v" + i));
      Kind<VStreamKind.Witness, Integer> values = VSTREAM.widen(VStream.empty());

      Kind<VStreamKind.Witness, String> result = applicative.ap(fns, values);

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("map2 combines two streams")
    void map2CombinesTwoStreams() {
      Kind<VStreamKind.Witness, Integer> s1 = VSTREAM.widen(VStream.of(1, 2));
      Kind<VStreamKind.Witness, String> s2 = VSTREAM.widen(VStream.of("a", "b"));

      Kind<VStreamKind.Witness, String> result = applicative.map2(s1, s2, (i, s) -> i + s);

      // Cartesian product via Applicative default map2
      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly("1a", "1b", "2a", "2b");
    }
  }

  // ==================== VStreamMonad ====================

  @Nested
  @DisplayName("VStreamMonad")
  class VStreamMonadTests {

    private final VStreamMonad monad = VStreamMonad.INSTANCE;

    @Test
    @DisplayName("flatMap with expansion (each element produces multiple)")
    void flatMapWithExpansion() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      Kind<VStreamKind.Witness, Integer> result =
          monad.flatMap(i -> VSTREAM.widen(VStream.of(i, i * 10)), stream);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 10, 2, 20, 3, 30);
    }

    @Test
    @DisplayName("flatMap with contraction (some elements produce empty)")
    void flatMapWithContraction() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3, 4, 5));

      Kind<VStreamKind.Witness, Integer> result =
          monad.flatMap(
              i -> i % 2 == 0 ? VSTREAM.widen(VStream.of(i)) : VSTREAM.widen(VStream.empty()),
              stream);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(2, 4);
    }

    @Test
    @DisplayName("flatMap preserves order")
    void flatMapPreservesOrder() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(3, 1, 2));

      Kind<VStreamKind.Witness, String> result =
          monad.flatMap(i -> VSTREAM.widen(VStream.of(i + "a", i + "b")), stream);

      assertThat(VSTREAM.narrow(result).toList().run())
          .containsExactly("3a", "3b", "1a", "1b", "2a", "2b");
    }

    @Test
    @DisplayName("flatMap on empty stream returns empty")
    void flatMapOnEmptyStreamReturnsEmpty() {
      Kind<VStreamKind.Witness, Integer> empty = VSTREAM.widen(VStream.empty());

      Kind<VStreamKind.Witness, String> result =
          monad.flatMap(i -> VSTREAM.widen(VStream.of("v" + i)), empty);

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }
  }

  // ==================== VStreamTraverse (Foldable) ====================

  @Nested
  @DisplayName("VStreamFoldable (via VStreamTraverse)")
  class VStreamFoldableTests {

    private final VStreamTraverse foldable = VStreamTraverse.INSTANCE;

    private final Monoid<Integer> sumMonoid =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };

    private final Monoid<String> stringMonoid =
        new Monoid<>() {
          @Override
          public String empty() {
            return "";
          }

          @Override
          public String combine(String a, String b) {
            return a + b;
          }
        };

    @Test
    @DisplayName("foldMap with integer sum")
    void foldMapWithIntegerSum() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3, 4, 5));

      Integer result = foldable.foldMap(sumMonoid, Function.identity(), stream);

      assertThat(result).isEqualTo(15);
    }

    @Test
    @DisplayName("foldMap with string concatenation")
    void foldMapWithStringConcatenation() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      String result = foldable.foldMap(stringMonoid, i -> "[" + i + "]", stream);

      assertThat(result).isEqualTo("[1][2][3]");
    }

    @Test
    @DisplayName("foldMap on empty stream returns monoid identity")
    void foldMapOnEmptyStreamReturnsMonoidIdentity() {
      Kind<VStreamKind.Witness, Integer> empty = VSTREAM.widen(VStream.empty());

      Integer result = foldable.foldMap(sumMonoid, Function.identity(), empty);

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("foldMap is consistent with manual fold")
    void foldMapIsConsistentWithManualFold() {
      List<Integer> values = List.of(10, 20, 30, 40);
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.fromList(values));

      Integer foldMapResult = foldable.foldMap(sumMonoid, Function.identity(), stream);
      Integer manualResult = values.stream().reduce(0, Integer::sum);

      assertThat(foldMapResult).isEqualTo(manualResult);
    }
  }

  // ==================== VStreamTraverse (Traverse) ====================

  @Nested
  @DisplayName("VStreamTraverse")
  class VStreamTraverseTests {

    private final VStreamTraverse traverse = VStreamTraverse.INSTANCE;
    private final OptionalMonad optionalApplicative = OptionalMonad.INSTANCE;

    @Test
    @DisplayName("map transforms each element")
    void mapTransformsEachElement() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      Kind<VStreamKind.Witness, String> result = traverse.map(i -> "v" + i, stream);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly("v1", "v2", "v3");
    }

    @Test
    @DisplayName("map on empty stream returns empty")
    void mapOnEmptyStreamReturnsEmpty() {
      Kind<VStreamKind.Witness, Integer> empty = VSTREAM.widen(VStream.empty());

      Kind<VStreamKind.Witness, String> result = traverse.map(Object::toString, empty);

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("map validates mapper is non-null")
    void mapValidatesMapperIsNonNull() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1));

      assertThatThrownBy(() -> traverse.map(null, stream))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("VStreamTraverse")
          .hasMessageContaining("map");
    }

    @Test
    @DisplayName("map validates Kind is non-null")
    void mapValidatesKindIsNonNull() {
      assertThatThrownBy(() -> traverse.map(Object::toString, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("VStreamTraverse")
          .hasMessageContaining("map");
    }

    @Test
    @DisplayName("traverse with Optional applicative (all present)")
    void traverseWithOptionalAllPresent() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> OptionalKindHelper.OPTIONAL.widen(Optional.of("v" + i));

      Kind<OptionalKind.Witness, Kind<VStreamKind.Witness, String>> result =
          traverse.traverse(optionalApplicative, f, stream);

      Optional<Kind<VStreamKind.Witness, String>> optResult =
          OptionalKindHelper.OPTIONAL.narrow(result);

      assertThat(optResult).isPresent();
      assertThat(VSTREAM.narrow(optResult.get()).toList().run()).containsExactly("v1", "v2", "v3");
    }

    @Test
    @DisplayName("traverse with Optional applicative (one empty, entire result empty)")
    void traverseWithOptionalOneEmpty() {
      Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

      Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i ->
              i == 2
                  ? OptionalKindHelper.OPTIONAL.widen(Optional.empty())
                  : OptionalKindHelper.OPTIONAL.widen(Optional.of("v" + i));

      Kind<OptionalKind.Witness, Kind<VStreamKind.Witness, String>> result =
          traverse.traverse(optionalApplicative, f, stream);

      Optional<Kind<VStreamKind.Witness, String>> optResult =
          OptionalKindHelper.OPTIONAL.narrow(result);

      assertThat(optResult).isEmpty();
    }

    @Test
    @DisplayName("traverse on empty stream returns applicative of empty stream")
    void traverseOnEmptyStreamReturnsApplicativeOfEmptyStream() {
      Kind<VStreamKind.Witness, Integer> empty = VSTREAM.widen(VStream.empty());

      Function<Integer, Kind<OptionalKind.Witness, String>> f =
          i -> OptionalKindHelper.OPTIONAL.widen(Optional.of("v" + i));

      Kind<OptionalKind.Witness, Kind<VStreamKind.Witness, String>> result =
          traverse.traverse(optionalApplicative, f, empty);

      Optional<Kind<VStreamKind.Witness, String>> optResult =
          OptionalKindHelper.OPTIONAL.narrow(result);

      assertThat(optResult).isPresent();
      assertThat(VSTREAM.narrow(optResult.get()).toList().run()).isEmpty();
    }
  }

  // ==================== VStreamAlternative ====================

  @Nested
  @DisplayName("VStreamAlternative")
  class VStreamAlternativeTests {

    private final VStreamAlternative alternative = VStreamAlternative.INSTANCE;

    @Test
    @DisplayName("empty produces empty stream")
    void emptyProducesEmptyStream() {
      Kind<VStreamKind.Witness, Integer> result = alternative.empty();

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("orElse concatenates streams")
    void orElseConcatenatesStreams() {
      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(VStream.of(1, 2));
      Kind<VStreamKind.Witness, Integer> fb = VSTREAM.widen(VStream.of(3, 4));

      Kind<VStreamKind.Witness, Integer> result = alternative.orElse(fa, () -> fb);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("orElse with empty first returns second")
    void orElseWithEmptyFirstReturnsSecond() {
      Kind<VStreamKind.Witness, Integer> fa = alternative.empty();
      Kind<VStreamKind.Witness, Integer> fb = VSTREAM.widen(VStream.of(1, 2, 3));

      Kind<VStreamKind.Witness, Integer> result = alternative.orElse(fa, () -> fb);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("orElse with empty second returns first")
    void orElseWithEmptySecondReturnsFirst() {
      Kind<VStreamKind.Witness, Integer> fa = VSTREAM.widen(VStream.of(1, 2, 3));

      Kind<VStreamKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("guard(true) produces Unit")
    void guardTrueProducesUnit() {
      Kind<VStreamKind.Witness, Unit> result = alternative.guard(true);

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) produces empty stream")
    void guardFalseProducesEmptyStream() {
      Kind<VStreamKind.Witness, Unit> result = alternative.guard(false);

      assertThat(VSTREAM.narrow(result).toList().run()).isEmpty();
    }

    @Test
    @DisplayName("orElse chains multiple streams")
    void orElseChainsMultipleStreams() {
      Kind<VStreamKind.Witness, Integer> a = VSTREAM.widen(VStream.of(1));
      Kind<VStreamKind.Witness, Integer> b = VSTREAM.widen(VStream.of(2));
      Kind<VStreamKind.Witness, Integer> c = VSTREAM.widen(VStream.of(3));

      Kind<VStreamKind.Witness, Integer> result =
          alternative.orElse(a, () -> alternative.orElse(b, () -> c));

      assertThat(VSTREAM.narrow(result).toList().run()).containsExactly(1, 2, 3);
    }
  }
}
