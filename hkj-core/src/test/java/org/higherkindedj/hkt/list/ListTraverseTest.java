// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ListTraverse Tests")
class ListTraverseTest extends ListTestBase {

  private final Traverse<ListKind.Witness> listTraverse = ListTraverse.INSTANCE;
  private final Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;

  // The applicative effect threaded through traverse is the real Maybe (its just/nothing
  // short-circuit semantics are exactly what traverse must respect), matching every other
  // *TraverseTest in the codebase.
  private final Applicative<MaybeKind.Witness> maybeApplicative = Instances.monadError(maybe());

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.list.ListLawFixtures#kinds")
    void identity(String label, Kind<ListKind.Witness, Integer> fa) {
      TraverseLaws.assertIdentity(listTraverse, fa, equalityChecker);
    }
  }

  @Test
  @DisplayName("Traverse contract — operations, validations & exceptions (laws verified above)")
  void traverseContract() {
    TypeClassContract.<ListKind.Witness>traverse(ListTraverse.class)
        .<Integer>instance(listTraverse)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .withApplicative(maybeApplicative, i -> MAYBE.widen(Maybe.just("v" + i)))
        .withFoldable(Monoids.string(), Object::toString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("map method")
  class MapTests {
    @Test
    void map_emptyList_shouldReturnEmptyListKind() {
      Kind<ListKind.Witness, Integer> input = LIST.widen(Collections.emptyList());
      Kind<ListKind.Witness, String> result = listTraverse.map(Object::toString, input);
      assertThat(LIST.narrow(result)).isEmpty();
    }

    @Test
    void map_nonEmptyList_shouldApplyFunction() {
      Kind<ListKind.Witness, Integer> input = LIST.widen(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = listTraverse.map(x -> x * 2, input);
      assertThat(LIST.narrow(result)).containsExactly(2, 4, 6);
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {

    private final Function<Integer, Kind<MaybeKind.Witness, String>> intToJustString =
        i -> MAYBE.widen(Maybe.just("v" + i));

    private final Function<Integer, Kind<MaybeKind.Witness, Integer>> intToMaybeSometimesNothing =
        i -> (i % 2 == 0) ? MAYBE.widen(Maybe.nothing()) : MAYBE.widen(Maybe.just(i * 3));

    @Test
    void traverse_emptyList_shouldReturnApplicativeOfEmptyListKind() {
      Kind<ListKind.Witness, Integer> emptyListKind = LIST.widen(Collections.emptyList());

      Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>> resultKind =
          listTraverse.traverse(maybeApplicative, intToJustString, emptyListKind);

      Maybe<Kind<ListKind.Witness, String>> maybe = MAYBE.narrow(resultKind);
      assertThat(maybe.isJust()).isTrue();
      assertThat(LIST.narrow(maybe.get())).isEmpty();
    }

    @Test
    void traverse_allEffectsSucceed_shouldReturnApplicativeOfListOfResults() {
      Kind<ListKind.Witness, Integer> inputList = LIST.widen(Arrays.asList(1, 2, 3));

      Kind<MaybeKind.Witness, Kind<ListKind.Witness, String>> resultKind =
          listTraverse.traverse(maybeApplicative, intToJustString, inputList);

      Maybe<Kind<ListKind.Witness, String>> maybe = MAYBE.narrow(resultKind);
      assertThat(maybe.isJust()).isTrue();
      assertThat(LIST.narrow(maybe.get())).containsExactly("v1", "v2", "v3");
    }

    @Test
    void traverse_oneEffectFails_shouldReturnApplicativeOfNothing() {
      Kind<ListKind.Witness, Integer> inputList = LIST.widen(Arrays.asList(1, 2, 3));

      Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.traverse(maybeApplicative, intToMaybeSometimesNothing, inputList);

      assertThat(MAYBE.narrow(resultKind).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {
    @Test
    void foldMap_emptyList_shouldReturnMonoidEmpty() {
      Kind<ListKind.Witness, Integer> emptyList = LIST.widen(Collections.emptyList());
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = listFoldable.foldMap(sumMonoid, Function.identity(), emptyList);

      assertThat(result).isEqualTo(sumMonoid.empty());
      assertThat(result).isZero();
    }

    @Test
    void foldMap_withIntegerAddition_shouldSumElements() {
      Kind<ListKind.Witness, Integer> numbers = LIST.widen(Arrays.asList(1, 2, 3, 4));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = listFoldable.foldMap(sumMonoid, Function.identity(), numbers);

      assertThat(result).isEqualTo(10);
    }

    @Test
    void foldMap_withStringConcat_shouldConcatenateMappedElements() {
      Kind<ListKind.Witness, Integer> numbers = LIST.widen(Arrays.asList(1, 2, 3));
      Monoid<String> stringMonoid = Monoids.string();

      String result = listFoldable.foldMap(stringMonoid, i -> "i" + i, numbers);

      assertThat(result).isEqualTo("i1i2i3");
    }
  }
}
