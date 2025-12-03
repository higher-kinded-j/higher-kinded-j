// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FreeFactory} demonstrating improved type inference for Free monad construction.
 */
@DisplayName("FreeFactory Tests")
class FreeFactoryTest {

  private FreeFactory<IdKind.Witness> factory;
  private IdMonad idMonad;

  @BeforeEach
  void setUp() {
    factory = FreeFactory.of();
    idMonad = IdMonad.instance();
  }

  @Nested
  @DisplayName("Factory Creation")
  class FactoryCreation {

    @Test
    @DisplayName("of() creates a factory instance")
    void ofCreatesFactory() {
      FreeFactory<IdKind.Witness> f = FreeFactory.of();
      assertThat(f).isNotNull();
      assertThat(f.toString()).isEqualTo("FreeFactory");
    }

    @Test
    @DisplayName("withMonad() creates a factory instance with type inference from monad")
    void withMonadCreatesFactory() {
      FreeFactory<IdKind.Witness> f = FreeFactory.withMonad(IdMonad.instance());
      assertThat(f).isNotNull();
    }
  }

  @Nested
  @DisplayName("Type Inference Improvements")
  class TypeInferenceImprovements {

    @Test
    @DisplayName("pure() followed by map() infers types correctly")
    void pureMapInfersTypes() {
      // This would fail to compile without explicit type parameters if using Free.pure() directly
      Free<IdKind.Witness, Integer> result = factory.pure(2).map(x -> x * 2);

      assertThat(result).isNotNull();
      Integer value = interpret(result);
      assertThat(value).isEqualTo(4);
    }

    @Test
    @DisplayName("pure() followed by flatMap() infers types correctly")
    void pureFlatMapInfersTypes() {
      // This would fail to compile without explicit type parameters if using Free.pure() directly
      Free<IdKind.Witness, Integer> result = factory.pure(3).flatMap(x -> factory.pure(x + 1));

      assertThat(result).isNotNull();
      Integer value = interpret(result);
      assertThat(value).isEqualTo(4);
    }

    @Test
    @DisplayName("chained operations infer types correctly")
    void chainedOperationsInferTypes() {
      Free<IdKind.Witness, Integer> result =
          factory
              .pure(10)
              .map(x -> x + 1)
              .flatMap(x -> factory.pure(x * 2))
              .map(x -> x - 4)
              .flatMap(x -> factory.pure(x / 3));

      Integer value = interpret(result);
      assertThat(value).isEqualTo(6); // ((10 + 1) * 2 - 4) / 3 = (22 - 4) / 3 = 18 / 3 = 6
    }
  }

  @Nested
  @DisplayName("Pure Operations")
  class PureOperations {

    @Test
    @DisplayName("pure() creates a Pure Free monad")
    void pureCreatesPureFree() {
      Free<IdKind.Witness, Integer> result = factory.pure(42);

      assertThat(result).isInstanceOf(Free.Pure.class);
      Integer value = interpret(result);
      assertThat(value).isEqualTo(42);
    }

    @Test
    @DisplayName("pure() with null value")
    void pureWithNullValue() {
      Free<IdKind.Witness, String> result = factory.pure(null);

      assertThat(result).isInstanceOf(Free.Pure.class);
      String value = interpret(result);
      assertThat(value).isNull();
    }

    @Test
    @DisplayName("pure() with different types")
    void pureWithDifferentTypes() {
      Free<IdKind.Witness, String> stringFree = factory.pure("hello");
      Free<IdKind.Witness, Double> doubleFree = factory.pure(3.14);
      Free<IdKind.Witness, Boolean> boolFree = factory.pure(true);

      assertThat(interpret(stringFree)).isEqualTo("hello");
      assertThat(interpret(doubleFree)).isEqualTo(3.14);
      assertThat(interpret(boolFree)).isTrue();
    }
  }

  @Nested
  @DisplayName("Suspend Operations")
  class SuspendOperations {

    @Test
    @DisplayName("suspend() creates a suspended computation")
    void suspendCreatesComputation() {
      Kind<IdKind.Witness, Free<IdKind.Witness, Integer>> suspended =
          IdKindHelper.ID.widen(Id.of(factory.pure(99)));

      Free<IdKind.Witness, Integer> result = factory.suspend(suspended);

      assertThat(result).isInstanceOf(Free.Suspend.class);
      Integer value = interpret(result);
      assertThat(value).isEqualTo(99);
    }

    @Test
    @DisplayName("suspend() with chained operations")
    void suspendWithChainedOperations() {
      Kind<IdKind.Witness, Free<IdKind.Witness, Integer>> suspended =
          IdKindHelper.ID.widen(Id.of(factory.pure(10)));

      Free<IdKind.Witness, Integer> result =
          factory.suspend(suspended).map(x -> x + 5).flatMap(x -> factory.pure(x * 2));

      Integer value = interpret(result);
      assertThat(value).isEqualTo(30); // (10 + 5) * 2 = 30
    }
  }

  @Nested
  @DisplayName("LiftF Operations")
  class LiftFOperations {

    @Test
    @DisplayName("liftF() lifts a computation into Free")
    void liftFLiftsComputation() {
      Kind<IdKind.Witness, Integer> computation = IdKindHelper.ID.widen(Id.of(77));

      Free<IdKind.Witness, Integer> result = factory.liftF(computation, idMonad);

      Integer value = interpret(result);
      assertThat(value).isEqualTo(77);
    }

    @Test
    @DisplayName("liftF() followed by operations infers types correctly")
    void liftFWithChainedOperations() {
      Kind<IdKind.Witness, Integer> computation = IdKindHelper.ID.widen(Id.of(5));

      Free<IdKind.Witness, Integer> result =
          factory.liftF(computation, idMonad).map(x -> x * 3).flatMap(x -> factory.pure(x + 1));

      Integer value = interpret(result);
      assertThat(value).isEqualTo(16); // 5 * 3 + 1 = 16
    }
  }

  @Nested
  @DisplayName("Real-World Usage Patterns")
  class RealWorldUsagePatterns {

    @Test
    @DisplayName("building a DSL program with factory")
    void buildingDslProgramWithFactory() {
      // Simulates building a simple calculator DSL
      Free<IdKind.Witness, Integer> program =
          factory
              .pure(100)
              .flatMap(x -> factory.pure(x / 2)) // 50
              .map(x -> x + 10) // 60
              .flatMap(x -> factory.pure(x * 3)) // 180
              .map(x -> x - 30); // 150

      Integer result = interpret(program);
      assertThat(result).isEqualTo(150);
    }

    @Test
    @DisplayName("multiple independent programs with shared factory")
    void multipleIndependentPrograms() {
      Free<IdKind.Witness, Integer> program1 = factory.pure(1).map(x -> x + 1);
      Free<IdKind.Witness, Integer> program2 = factory.pure(2).flatMap(x -> factory.pure(x * 2));
      Free<IdKind.Witness, Integer> program3 = factory.pure(3).map(x -> x + 3);

      assertThat(interpret(program1)).isEqualTo(2);
      assertThat(interpret(program2)).isEqualTo(4);
      assertThat(interpret(program3)).isEqualTo(6);
    }

    @Test
    @DisplayName("factory created with withMonad for clarity")
    void factoryWithMonadForClarity() {
      // Using withMonad makes it clear which monad will be used for interpretation
      FreeFactory<IdKind.Witness> typedFactory = FreeFactory.withMonad(IdMonad.instance());

      Free<IdKind.Witness, String> program =
          typedFactory.pure("Hello").map(s -> s + ", World!").map(String::toUpperCase);

      String result = interpret(program);
      assertThat(result).isEqualTo("HELLO, WORLD!");
    }
  }

  /** Helper method to interpret a Free program using the identity transformation. */
  private <A> A interpret(Free<IdKind.Witness, A> free) {
    Kind<IdKind.Witness, A> result = free.foldMap(Natural.identity(), idMonad);
    return IdKindHelper.ID.narrow(result).value();
  }
}
