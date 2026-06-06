// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link Tuple2Bifunctor}.
 *
 * <p>{@code Tuple2} is an eager product: {@code bimap}/{@code first}/{@code second} apply their
 * mappers to the held components immediately, so a thrown mapper propagates synchronously and the
 * contract includes {@link Category#EXCEPTIONS}. The laws are driven by the shipped {@link
 * BifunctorLaws} over {@link Tuple2LawFixtures}.
 */
@DisplayName("Tuple2Bifunctor Tests")
class Tuple2BifunctorTest {

  private Tuple2Bifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = Tuple2Bifunctor.INSTANCE;
  }

  /**
   * {@code Tuple2} applies both mappers eagerly, so a thrown mapper surfaces synchronously — {@link
   * Category#EXCEPTIONS} is included. The Bifunctor laws are verified in the {@code Laws} block
   * below.
   */
  @Test
  @DisplayName("Bifunctor contract — operations, validations & exceptions")
  void bifunctorContract() {
    Kind2<Tuple2Kind2.Witness, String, Integer> validTuple =
        TUPLE2.widen2(new Tuple2<>("hello", 42));

    TypeClassContract.<Tuple2Kind2.Witness>bifunctor(Tuple2Bifunctor.class)
        .<String, Integer>instance(bifunctor)
        .withKind2(validTuple)
        .withFirstMapper(String::length)
        .withSecondMapper(n -> "Value:" + n)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Bifunctor Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.tuple.Tuple2LawFixtures#kind2s")
    void identity(String label, Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
      BifunctorLaws.assertIdentity(bifunctor, fab, Tuple2LawFixtures.BIFUNCTOR_EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.tuple.Tuple2LawFixtures#kind2s")
    void composition(String label, Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";
      BifunctorLaws.assertComposition(
          bifunctor, fab, f1, f2, g1, g2, Tuple2LawFixtures.BIFUNCTOR_EQ);
    }

    @ParameterizedTest(name = "first-consistency holds on {0}")
    @MethodSource("org.higherkindedj.hkt.tuple.Tuple2LawFixtures#kind2s")
    void firstConsistency(String label, Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
      Function<String, Integer> f = String::length;
      BifunctorLaws.assertFirstConsistency(bifunctor, fab, f, Tuple2LawFixtures.BIFUNCTOR_EQ);
    }

    @ParameterizedTest(name = "second-consistency holds on {0}")
    @MethodSource("org.higherkindedj.hkt.tuple.Tuple2LawFixtures#kind2s")
    void secondConsistency(String label, Kind2<Tuple2Kind2.Witness, String, Integer> fab) {
      Function<Integer, String> g = n -> "Value:" + n;
      BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, Tuple2LawFixtures.BIFUNCTOR_EQ);
    }
  }

  @Nested
  @DisplayName("Bifunctor Operation Tests")
  class BifunctorOperationTests {

    @Test
    @DisplayName("bimap() transforms both elements")
    void bimapTransformsBothElements() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Tuple2<Integer, String> result =
          TUPLE2.narrow2(bifunctor.bimap(String::length, n -> "Value:" + n, tuple));

      assertThat(result).isEqualTo(new Tuple2<>(5, "Value:42"));
    }

    @Test
    @DisplayName("first() transforms only first element")
    void firstTransformsOnlyFirstElement() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Tuple2<Integer, Integer> result = TUPLE2.narrow2(bifunctor.first(String::length, tuple));

      assertThat(result).isEqualTo(new Tuple2<>(5, 42));
    }

    @Test
    @DisplayName("second() transforms only second element")
    void secondTransformsOnlySecondElement() {
      Kind2<Tuple2Kind2.Witness, String, Integer> tuple = TUPLE2.widen2(new Tuple2<>("hello", 42));

      Tuple2<String, String> result = TUPLE2.narrow2(bifunctor.second(n -> "Value:" + n, tuple));

      assertThat(result).isEqualTo(new Tuple2<>("hello", "Value:42"));
    }
  }
}
