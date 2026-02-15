// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListTraverse Tests")
class ListTraverseTest {

  private final Traverse<ListKind.Witness> listTraverse = ListTraverse.INSTANCE;
  private final Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;

  // --- Mock/Simple Optional HKT for Testing Traverse ---

  /** HKT marker for TestOptional. Made non-generic. */
  static final class TestOptionalKindWitness implements WitnessArity<TypeArity.Unary> {
    private TestOptionalKindWitness() {}
  }

  /**
   * HKT interface for our test Optional. Now uses the non-generic TestOptionalKindWitness marker.
   */
  interface TestOptional<A> extends Kind<TestOptionalKindWitness, A> {
    Optional<A> getOptional();

    static <A> TestOptional<A> narrow(Kind<TestOptionalKindWitness, A> kind) {
      return (TestOptional<A>) kind;
    }

    static <A> TestOptional<A> some(A value) {
      return new Some<>(value);
    }

    static <A> TestOptional<A> none() {
      return None.instance();
    }
  }

  record Some<A>(A value) implements TestOptional<A> {
    @Override
    public Optional<A> getOptional() {
      return Optional.ofNullable(value);
    }
  }

  static final class None<A> implements TestOptional<A> {
    private static final None<?> INSTANCE = new None<>();

    private None() {}

    @SuppressWarnings("unchecked")
    public static <A> None<A> instance() {
      return (None<A>) INSTANCE;
    }

    @Override
    public Optional<A> getOptional() {
      return Optional.empty();
    }
  }

  /**
   * Applicative instance for TestOptional. Now uses the non-generic TestOptionalKindWitness marker.
   */
  static class TestOptionalApplicative implements Applicative<TestOptionalKindWitness> {
    public static final TestOptionalApplicative INSTANCE = new TestOptionalApplicative();

    private TestOptionalApplicative() {}

    @Override
    public <A> @NonNull Kind<TestOptionalKindWitness, A> of(A value) {
      return TestOptional.some(value);
    }

    @Override
    public <A, B> @NonNull Kind<TestOptionalKindWitness, B> map(
        @NonNull Function<? super A, ? extends B> f, @NonNull Kind<TestOptionalKindWitness, A> fa) {
      return TestOptional.narrow(fa)
          .getOptional()
          .map(f)
          .map(TestOptional::some)
          .orElseGet(TestOptional::none);
    }

    @Override
    public <A, B> @NonNull Kind<TestOptionalKindWitness, B> ap(
        @NonNull Kind<TestOptionalKindWitness, ? extends Function<A, B>> ff,
        @NonNull Kind<TestOptionalKindWitness, A> fa) {
      Optional<? extends Function<A, B>> optFunc = TestOptional.narrow(ff).getOptional();
      Optional<A> optVal = TestOptional.narrow(fa).getOptional();

      return optFunc.flatMap(optVal::map).map(TestOptional::some).orElseGet(TestOptional::none);
    }
  }

  // --- End of Mock Optional HKT ---

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
    private final Applicative<TestOptionalKindWitness> optionalApplicative =
        TestOptionalApplicative.INSTANCE;

    Function<Integer, Kind<TestOptionalKindWitness, String>> intToOptionalStringKind =
        i -> TestOptional.some("v" + i);

    Function<Integer, Kind<TestOptionalKindWitness, Integer>> intToOptionalIntSometimesNoneKind =
        i -> (i % 2 == 0) ? TestOptional.none() : TestOptional.some(i * 3);

    @Test
    void traverse_emptyList_shouldReturnApplicativeOfEmptyListKind() {
      Kind<ListKind.Witness, Integer> emptyListKind = LIST.widen(Collections.emptyList());

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, String>> resultKind =
          listTraverse.traverse(optionalApplicative, intToOptionalStringKind, emptyListKind);

      Optional<Kind<ListKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(LIST.narrow(resultOptional.get())).isEmpty();
    }

    @Test
    void traverse_allEffectsSucceed_shouldReturnApplicativeOfListOfResults() {
      Kind<ListKind.Witness, Integer> inputList = LIST.widen(Arrays.asList(1, 2, 3));

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, String>> resultKind =
          listTraverse.traverse(optionalApplicative, intToOptionalStringKind, inputList);

      Optional<Kind<ListKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(LIST.narrow(resultOptional.get())).containsExactly("v1", "v2", "v3");
    }

    @Test
    void traverse_oneEffectFails_shouldReturnApplicativeOfNone() {
      Kind<ListKind.Witness, Integer> inputList = LIST.widen(Arrays.asList(1, 2, 3));

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.traverse(optionalApplicative, intToOptionalIntSometimesNoneKind, inputList);

      Optional<Kind<ListKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isEmpty();
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
