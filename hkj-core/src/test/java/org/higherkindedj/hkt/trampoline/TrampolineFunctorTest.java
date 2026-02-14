// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link TrampolineFunctor}.
 *
 * <p>Ensures 100% coverage of the Functor instance including all validation paths and edge cases.
 */
@DisplayName("TrampolineFunctor Tests")
class TrampolineFunctorTest extends TrampolineTestBase {

  private final TrampolineFunctor functor = TrampolineFunctor.INSTANCE;

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
    void mapWithNullFunctionThrows() {
      Kind<TrampolineKind.Witness, Integer> kind = TRAMPOLINE.widen(Trampoline.done(42));

      assertThatThrownBy(() -> functor.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map with null kind throws NullPointerException")
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
    void mapWithNullResultValue() {
      Kind<TrampolineKind.Witness, String> kind = TRAMPOLINE.widen(Trampoline.done("test"));
      Kind<TrampolineKind.Witness, String> mapped = functor.map(x -> null, kind);

      Trampoline<String> result = TRAMPOLINE.narrow(mapped);
      assertThat(result.run()).isNull();
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {

    @Test
    @DisplayName("Identity law: map(id, fa) == fa")
    void identityLaw() {
      Kind<TrampolineKind.Witness, Integer> fa = TRAMPOLINE.widen(Trampoline.done(42));
      Kind<TrampolineKind.Witness, Integer> mapped = functor.map(x -> x, fa);

      assertThat(TRAMPOLINE.narrow(mapped).run()).isEqualTo(TRAMPOLINE.narrow(fa).run());
    }

    @Test
    @DisplayName("Composition law: map(f . g, fa) == map(f, map(g, fa))")
    void compositionLaw() {
      Kind<TrampolineKind.Witness, Integer> fa = TRAMPOLINE.widen(Trampoline.done(10));
      Function<Integer, Integer> f = x -> x * 2;
      Function<Integer, Integer> g = x -> x + 5;

      // Left side: map(f . g, fa)
      Kind<TrampolineKind.Witness, Integer> left = functor.map(x -> f.apply(g.apply(x)), fa);

      // Right side: map(f, map(g, fa))
      Kind<TrampolineKind.Witness, Integer> right = functor.map(f, functor.map(g, fa));

      assertThat(TRAMPOLINE.narrow(left).run()).isEqualTo(TRAMPOLINE.narrow(right).run());
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
