// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link TrampolineFunctor}.
 *
 * <p>Verifies the Functor operations and laws; the laws are driven by the shipped {@link
 * FunctorLaws} over {@link TrampolineLawFixtures}.
 */
@DisplayName("TrampolineFunctor Tests")
class TrampolineFunctorTest extends TrampolineTestBase {

  private final TrampolineFunctor functor = TrampolineFunctor.INSTANCE;

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}
   * <em>propagates</em> a thrown mapper exception immediately, but {@code Trampoline.map} defers —
   * it builds a {@code FlatMap} structure, so the mapper only runs at {@link Trampoline#run()}
   * time. The Functor laws are verified in the {@code Laws} block below.
   */
  @Test
  @DisplayName("Functor contract — operations & validations")
  void functorContract() {
    TypeClassContract.<TrampolineKind.Witness>functor(TrampolineFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(TRAMPOLINE.widen(Trampoline.done(DEFAULT_VALUE)))
        .withMapper(Object::toString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")
    void identity(String label, Kind<TrampolineKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, TrampolineLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.trampoline.TrampolineLawFixtures#kinds")
    void composition(String label, Kind<TrampolineKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, x -> x + 5, x -> x * 2, TrampolineLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Instance Tests")
  class InstanceTests {

    @Test
    @DisplayName("INSTANCE singleton is accessible")
    void instanceSingletonAccessible() {
      assertThat(TrampolineFunctor.INSTANCE).isNotNull();
      assertThat(TrampolineFunctor.INSTANCE).isSameAs(functor);
    }

    @Test
    @DisplayName("Multiple references to INSTANCE return same object")
    void multipleReferencesReturnSame() {
      TrampolineFunctor instance1 = TrampolineFunctor.INSTANCE;
      TrampolineFunctor instance2 = TrampolineFunctor.INSTANCE;

      assertThat(instance1).isSameAs(instance2);
    }
  }

  @Nested
  @DisplayName("map() Tests")
  class MapTests {

    @Test
    @DisplayName("map transforms value correctly")
    void mapTransformsValue() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(42));
      Kind<TrampolineKind.Witness, Integer> mapped = functor.map(x -> x * 2, kind);

      Trampoline<Integer> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("map with identity function returns equivalent value")
    void mapWithIdentity() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(42));
      Kind<TrampolineKind.Witness, Integer> mapped = functor.map(x -> x, kind);

      Trampoline<Integer> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("map with null function throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapWithNullFunctionThrows() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(42));

      assertThatThrownBy(() -> functor.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map with null kind throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void mapWithNullKindThrows() {
      assertThatThrownBy(() -> functor.map((Integer x) -> x * 2, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map chains correctly")
    void mapChainsCorrectly() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(10));
      Kind<TrampolineKind.Witness, Integer> mapped =
          functor.map(x -> x * 2, functor.map(x -> x + 5, kind));

      Trampoline<Integer> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isEqualTo(30); // (10 + 5) * 2
    }

    @Test
    @DisplayName("map with type transformation")
    void mapWithTypeTransformation() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(42));
      Kind<TrampolineKind.Witness, String> mapped = functor.map(Object::toString, kind);

      Trampoline<String> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isEqualTo("42");
    }

    @Test
    @DisplayName("map preserves stack safety")
    void mapPreservesStackSafety() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(0));

      // Chain many maps
      for (int i = 0; i < 10_000; i++) {
        kind = functor.map(x -> x + 1, kind);
      }

      Trampoline<Integer> result = TRAMPOLINE.narrow(kind);
      assertThat(result.run()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("map on deferred trampoline")
    void mapOnDeferredTrampoline() {
      Kind<TrampolineKind.Witness, Integer> kind =
          TRAMPOLINE.widen(Trampoline.defer(() -> Trampoline.done(42)));
      Kind<TrampolineKind.Witness, Integer> mapped = functor.map(x -> x * 2, kind);

      Trampoline<Integer> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isEqualTo(84);
    }

    @Test
    @DisplayName("map with null result value")
    @SuppressWarnings("DataFlowIssue") // null-returning mapper exercises Trampoline.map's null path
    void mapWithNullResultValue() {
      Kind<TrampolineKind.Witness, String> kind = TRAMPOLINE.widen(Trampoline.done("test"));
      Function<String, String> nullMapper = _ -> null;
      Kind<TrampolineKind.Witness, String> mapped = functor.map(nullMapper, kind);

      Trampoline<String> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isNull();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("map on already mapped trampoline")
    void mapOnAlreadyMapped() {
      Trampoline<Integer> base = Trampoline.done(10);
      Trampoline<Integer> mapped1 = base.map(x -> x * 2);
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(mapped1);
      Kind<TrampolineKind.Witness, Integer> mapped2 = functor.map(x -> x + 5, kind);

      Trampoline<Integer> result = TRAMPOLINE.narrow(mapped2);
      assertThat(result.run()).isEqualTo(25); // (10 * 2) + 5
    }

    @Test
    @DisplayName("map with complex function")
    void mapWithComplexFunction() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(5));
      Kind<TrampolineKind.Witness, String> mapped = functor.map(x -> "Value: " + (x * x), kind);

      Trampoline<String> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isEqualTo("Value: 25");
    }
  }
}
