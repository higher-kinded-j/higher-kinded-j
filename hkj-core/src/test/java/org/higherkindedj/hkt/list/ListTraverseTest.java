// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.unwrap;
import static org.higherkindedj.hkt.list.ListKindHelper.wrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
// import java.util.stream.Collectors; // Not used
import org.higherkindedj.hkt.Applicative;
// Not directly used by tests, but Traverse extends it
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListTraverse Tests")
class ListTraverseTest {

  // Changed from ListKind.Witness to ListKind.Witness
  private final Traverse<ListKind.Witness> listTraverse = ListTraverse.INSTANCE;

  // --- Mock/Simple Optional HKT for Testing Traverse ---

  /** HKT marker for TestOptional. Made non-generic. */
  static final class TestOptionalKindWitness {
    private TestOptionalKindWitness() {}
  }

  /**
   * HKT interface for our test Optional. Now uses the non-generic TestOptionalKindWitness marker.
   */
  interface TestOptional<A> extends Kind<TestOptionalKindWitness, A> {
    Optional<A> getOptional();

    @SuppressWarnings("unchecked")
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
        @NonNull Function<A, B> f, @NonNull Kind<TestOptionalKindWitness, A> fa) {
      return TestOptional.narrow(fa)
          .getOptional()
          .map(f)
          .map(TestOptional::some)
          .orElseGet(TestOptional::none);
    }

    @Override
    public <A, B> @NonNull Kind<TestOptionalKindWitness, B> ap(
        @NonNull Kind<TestOptionalKindWitness, Function<A, B>> ff,
        @NonNull Kind<TestOptionalKindWitness, A> fa) {
      Optional<Function<A, B>> optFunc = TestOptional.narrow(ff).getOptional();
      Optional<A> optVal = TestOptional.narrow(fa).getOptional();

      return optFunc
          .flatMap(f -> optVal.map(f))
          .map(TestOptional::some)
          .orElseGet(TestOptional::none);
    }
  }

  // --- End of Mock Optional HKT ---

  @Nested
  @DisplayName("map method (delegates to ListFunctor)")
  class MapTests {
    @Test
    void map_emptyList_shouldReturnEmptyListKind() {
      // Changed from ListKind.Witness to ListKind.Witness
      Kind<ListKind.Witness, Integer> input = wrap(Collections.emptyList());
      Kind<ListKind.Witness, String> result = listTraverse.map(Object::toString, input);
      assertThat(unwrap(result)).isEmpty();
    }

    @Test
    void map_nonEmptyList_shouldApplyFunction() {
      Kind<ListKind.Witness, Integer> input = wrap(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = listTraverse.map(x -> x * 2, input);
      assertThat(unwrap(result)).containsExactly(2, 4, 6);
    }

    @Test
    void map_functionChangesType() {
      Kind<ListKind.Witness, Integer> input = wrap(Arrays.asList(1, 2));
      Kind<ListKind.Witness, String> result = listTraverse.map(x -> "v" + x, input);
      assertThat(unwrap(result)).containsExactly("v1", "v2");
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
      Kind<ListKind.Witness, Integer> emptyListKind = wrap(Collections.emptyList());

      // G = TestOptionalKindWitness
      // A = Integer
      // B = String
      // T = ListKind.Witness
      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, String>> resultKind =
          listTraverse.traverse(optionalApplicative, emptyListKind, intToOptionalStringKind);

      Optional<Kind<ListKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(unwrap(resultOptional.get())).isEmpty();
    }

    @Test
    void traverse_allEffectsSucceed_shouldReturnApplicativeOfListOfResults() {
      Kind<ListKind.Witness, Integer> inputList = wrap(Arrays.asList(1, 2, 3));

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, String>> resultKind =
          listTraverse.traverse(optionalApplicative, inputList, intToOptionalStringKind);

      Optional<Kind<ListKind.Witness, String>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(unwrap(resultOptional.get())).containsExactly("v1", "v2", "v3");
    }

    @Test
    void traverse_oneEffectFails_shouldReturnApplicativeOfNone() {
      Kind<ListKind.Witness, Integer> inputList = wrap(Arrays.asList(1, 2, 3));

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.traverse(optionalApplicative, inputList, intToOptionalIntSometimesNoneKind);

      Optional<Kind<ListKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isEmpty();
    }

    @Test
    void traverse_allEffectsSucceed_noFailure() {
      Kind<ListKind.Witness, Integer> inputList = wrap(Arrays.asList(1, 3, 5));

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.traverse(optionalApplicative, inputList, intToOptionalIntSometimesNoneKind);

      Optional<Kind<ListKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(unwrap(resultOptional.get())).containsExactly(3, 9, 15);
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {
    private final Applicative<TestOptionalKindWitness> optionalApplicative =
        TestOptionalApplicative.INSTANCE;

    @Test
    void sequenceA_emptyListOfEffects_shouldReturnApplicativeOfEmptyList() {
      List<Kind<TestOptionalKindWitness, Integer>> actualEmptyList = Collections.emptyList();
      Kind<ListKind.Witness, Kind<TestOptionalKindWitness, Integer>> emptyListOfEffects =
          wrap(actualEmptyList);

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.<TestOptionalKindWitness, Integer>sequenceA(
              optionalApplicative, emptyListOfEffects);

      Optional<Kind<ListKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(unwrap(resultOptional.get())).isEmpty();
    }

    @Test
    void sequenceA_listOfSuccessfulEffects_shouldReturnApplicativeOfListOfValues() {
      List<Kind<TestOptionalKindWitness, Integer>> listOfActualEffects =
          Arrays.asList(TestOptional.some(10), TestOptional.some(20), TestOptional.some(30));
      Kind<ListKind.Witness, Kind<TestOptionalKindWitness, Integer>> listOfEffects =
          wrap(listOfActualEffects);

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.<TestOptionalKindWitness, Integer>sequenceA(
              optionalApplicative, listOfEffects);

      Optional<Kind<ListKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isPresent();
      assertThat(unwrap(resultOptional.get())).containsExactly(10, 20, 30);
    }

    @Test
    void sequenceA_listContainsFailingEffect_shouldReturnApplicativeOfNone() {
      List<Kind<TestOptionalKindWitness, Integer>> listOfActualEffects =
          Arrays.asList(TestOptional.some(10), TestOptional.none(), TestOptional.some(30));
      Kind<ListKind.Witness, Kind<TestOptionalKindWitness, Integer>> listOfEffects =
          wrap(listOfActualEffects);

      Kind<TestOptionalKindWitness, Kind<ListKind.Witness, Integer>> resultKind =
          listTraverse.<TestOptionalKindWitness, Integer>sequenceA(
              optionalApplicative, listOfEffects);

      Optional<Kind<ListKind.Witness, Integer>> resultOptional =
          TestOptional.narrow(resultKind).getOptional();
      assertThat(resultOptional).isEmpty();
    }
  }
}
