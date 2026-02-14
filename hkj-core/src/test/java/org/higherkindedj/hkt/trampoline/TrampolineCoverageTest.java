// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trampoline;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Additional tests to ensure 100% code coverage for {@link Trampoline}.
 *
 * <p>These tests focus on edge cases, branch coverage, and ensuring all code paths are executed.
 */
@DisplayName("Trampoline Coverage Tests")
class TrampolineCoverageTest extends TrampolineTestBase {

  @Nested
  @DisplayName("run() Branch Coverage")
  class RunBranchCoverage {

    @Test
    @DisplayName("Done case with empty stack returns value directly")
    void doneWithEmptyStackReturnsValue() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      // This hits the Done case with stack.isEmpty() == true
      Integer result = trampoline.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("Done case with non-empty stack applies continuation")
    void doneWithNonEmptyStackAppliesContinuation() {
      // Create a flatMap chain which puts continuations on the stack
      Trampoline<Integer> trampoline =
          Trampoline.done(10)
              .flatMap(
                  x -> Trampoline.done(x * 2)) // FlatMap pushes to stack, Done pops and applies
              .flatMap(x -> Trampoline.done(x + 5)); // Another FlatMap

      // This hits the Done case with stack.isEmpty() == false (line 271)
      Integer result = trampoline.run();

      assertThat(result).isEqualTo(25);
    }

    @Test
    @DisplayName("More case unwraps deferred computation")
    void moreCaseUnwrapsDeferred() {
      // Create a More (via defer) that contains a Done
      Trampoline<Integer> trampoline = Trampoline.defer(() -> Trampoline.done(42));

      // This hits the More case (line 275)
      Integer result = trampoline.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("More case with multiple defers")
    void moreCaseWithMultipleDefers() {
      // Nested defers
      Trampoline<Integer> trampoline =
          Trampoline.defer(
              () -> Trampoline.defer(() -> Trampoline.defer(() -> Trampoline.done(42))));

      // This hits the More case multiple times
      Integer result = trampoline.run();

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("FlatMap case pushes continuation and processes sub")
    void flatMapCasePushesContinuation() {
      // Create a FlatMap
      Trampoline<Integer> trampoline = Trampoline.done(10).flatMap(x -> Trampoline.done(x * 2));

      // This hits the FlatMap case (lines 280-284)
      Integer result = trampoline.run();

      assertThat(result).isEqualTo(20);
    }

    @Test
    @DisplayName("Complex mix of Done, More, and FlatMap")
    void complexMixOfConstructors() {
      // Mix all three constructors
      Trampoline<Integer> trampoline =
          Trampoline.defer(() -> Trampoline.done(5)) // More -> Done
              .flatMap(
                  x -> Trampoline.defer(() -> Trampoline.done(x * 2))) // FlatMap -> More -> Done
              .map(x -> x + 10) // Uses flatMap internally
              .flatMap(
                  x ->
                      Trampoline.defer(
                          () -> Trampoline.defer(() -> Trampoline.done(x * 3)))); // Nested More

      Integer result = trampoline.run();

      assertThat(result).isEqualTo(60); // (5 * 2 + 10) * 3 = 60
    }

    @Test
    @DisplayName("Stack operations with multiple continuations")
    void stackOperationsWithMultipleContinuations() {
      // Create a chain that will have multiple items on the continuation stack
      Trampoline<Integer> trampoline =
          Trampoline.done(1)
              .flatMap(x -> Trampoline.done(x + 1)) // 2
              .flatMap(x -> Trampoline.done(x + 1)) // 3
              .flatMap(x -> Trampoline.done(x + 1)) // 4
              .flatMap(x -> Trampoline.done(x + 1)); // 5

      // This ensures stack.addLast() and stack.removeLast() are both called multiple times
      Integer result = trampoline.run();

      assertThat(result).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("Constructor Coverage")
  class ConstructorCoverage {

    @Test
    @DisplayName("Done constructor with various value types")
    void doneConstructorWithVariousTypes() {
      // Test with different types
      Trampoline<Integer> intTrampoline = Trampoline.done(42);
      Trampoline<String> stringTrampoline = Trampoline.done("hello");
      Trampoline<Boolean> boolTrampoline = Trampoline.done(true);
      Trampoline<Double> doubleTrampoline = Trampoline.done(3.14);

      assertThat(intTrampoline.run()).isEqualTo(42);
      assertThat(stringTrampoline.run()).isEqualTo("hello");
      assertThat(boolTrampoline.run()).isTrue();
      assertThat(doubleTrampoline.run()).isEqualTo(3.14);
    }

    @Test
    @DisplayName("More constructor with null supplier throws")
    void moreConstructorWithNullThrows() {
      // This tests the More compact constructor validation
      assertThatThrownBy(() -> Trampoline.defer(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("FlatMap created through flatMap with valid inputs")
    void flatMapConstructorWithValidInputs() {
      // Create a FlatMap by calling flatMap
      Trampoline<Integer> base = Trampoline.done(10);
      Trampoline<Integer> flatMapped = base.flatMap(x -> Trampoline.done(x * 2));

      // Verify it's a FlatMap
      assertThat(flatMapped).isInstanceOf(Trampoline.FlatMap.class);
      assertThat(flatMapped.run()).isEqualTo(20);
    }

    @Test
    @DisplayName("All three constructor types can be created")
    void allThreeConstructorTypes() {
      Trampoline<Integer> done = Trampoline.done(1);
      Trampoline<Integer> more = Trampoline.defer(() -> Trampoline.done(2));
      Trampoline<Integer> flatMap = Trampoline.done(3).flatMap(x -> Trampoline.done(x * 2));

      assertThat(done).isInstanceOf(Trampoline.Done.class);
      assertThat(more).isInstanceOf(Trampoline.More.class);
      assertThat(flatMap).isInstanceOf(Trampoline.FlatMap.class);
    }
  }

  @Nested
  @DisplayName("Edge Case Coverage")
  class EdgeCaseCoverage {

    @Test
    @DisplayName("Continuation applies to null value")
    void continuationAppliesToNullValue() {
      // Create a flatMap where the first result is null
      Trampoline<String> trampoline =
          Trampoline.<String>done(null)
              .flatMap(x -> Trampoline.done(x == null ? "was null" : "not null"));

      // This tests applying continuation with null value
      String result = trampoline.run();

      assertThat(result).isEqualTo("was null");
    }

    @Test
    @DisplayName("Multiple flatMaps with null intermediate values")
    void multipleFlatMapsWithNullValues() {
      Trampoline<String> trampoline =
          Trampoline.<String>done(null)
              .flatMap(x -> Trampoline.done(x)) // Pass null through
              .flatMap(x -> Trampoline.done(x == null ? "final" : "unexpected"));

      String result = trampoline.run();

      assertThat(result).isEqualTo("final");
    }

    @Test
    @DisplayName("defer returning defer returning done")
    void deferReturningDeferReturningDone() {
      // Multiple levels of deferred computations
      Trampoline<Integer> trampoline =
          Trampoline.defer(
              () -> Trampoline.defer(() -> Trampoline.defer(() -> Trampoline.done(42))));

      assertThat(trampoline.run()).isEqualTo(42);
    }

    @Test
    @DisplayName("flatMap after defer")
    void flatMapAfterDefer() {
      Trampoline<Integer> trampoline =
          Trampoline.defer(() -> Trampoline.done(10)).flatMap(x -> Trampoline.done(x * 2));

      assertThat(trampoline.run()).isEqualTo(20);
    }

    @Test
    @DisplayName("defer after flatMap")
    void deferAfterFlatMap() {
      Trampoline<Integer> trampoline =
          Trampoline.done(10).flatMap(x -> Trampoline.defer(() -> Trampoline.done(x * 2)));

      assertThat(trampoline.run()).isEqualTo(20);
    }

    @Test
    @DisplayName("Long chain of operations hitting all constructors")
    void longChainHittingAllConstructors() {
      Trampoline<Integer> trampoline =
          Trampoline.done(1) // Done
              .map(x -> x + 1) // FlatMap (map uses flatMap)
              .flatMap(
                  x -> Trampoline.defer(() -> Trampoline.done(x + 1))) // FlatMap -> More -> Done
              .map(x -> x + 1) // FlatMap
              .flatMap(x -> Trampoline.done(x + 1)) // FlatMap -> Done
              .map(x -> x + 1); // FlatMap

      assertThat(trampoline.run()).isEqualTo(6);
    }

    @Test
    @DisplayName("runT alias works correctly")
    void runTAliasWorks() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThat(trampoline.runT()).isEqualTo(42);
      assertThat(trampoline.runT()).isEqualTo(trampoline.run());
    }
  }

  @Nested
  @DisplayName("Type Coverage")
  class TypeCoverage {

    @Test
    @DisplayName("Works with different value types")
    void worksWithDifferentValueTypes() {
      // Integer
      assertThat(Trampoline.done(42).run()).isEqualTo(42);

      // String
      assertThat(Trampoline.done("test").run()).isEqualTo("test");

      // Boolean
      assertThat(Trampoline.done(true).run()).isTrue();

      // Long
      assertThat(Trampoline.done(123L).run()).isEqualTo(123L);

      // Double
      assertThat(Trampoline.done(3.14).run()).isEqualTo(3.14);
    }

    @Test
    @DisplayName("Works with complex types")
    void worksWithComplexTypes() {
      record Person(String name, int age) {}

      Person person = new Person("Alice", 30);
      Trampoline<Person> trampoline = Trampoline.done(person);

      assertThat(trampoline.run()).isEqualTo(person);
    }

    @Test
    @DisplayName("Handles type transformations")
    void handlesTypeTransformations() {
      Trampoline<String> trampoline =
          Trampoline.done(42).map(Object::toString).flatMap(s -> Trampoline.done("Number: " + s));

      assertThat(trampoline.run()).isEqualTo("Number: 42");
    }
  }

  @Nested
  @DisplayName("Validation Coverage")
  class ValidationCoverage {

    @Test
    @DisplayName("map with null mapper throws")
    void mapWithNullMapperThrows() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThatThrownBy(() -> trampoline.map(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap with null function throws")
    void flatMapWithNullFunctionThrows() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThatThrownBy(() -> trampoline.flatMap(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("flatMap with function returning null throws during run")
    void flatMapWithNullReturningFunctionThrows() {
      Trampoline<Integer> trampoline = Trampoline.done(42).flatMap(x -> null);

      assertThatThrownBy(trampoline::run).isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("defer with null supplier throws")
    void deferWithNullSupplierThrows() {
      assertThatThrownBy(() -> Trampoline.defer(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Deep Nesting Coverage")
  class DeepNestingCoverage {

    @Test
    @DisplayName("Deeply nested FlatMaps")
    void deeplyNestedFlatMaps() {
      Trampoline<Integer> trampoline = Trampoline.done(0);

      for (int i = 0; i < 1000; i++) {
        trampoline = trampoline.flatMap(x -> Trampoline.done(x + 1));
      }

      assertThat(trampoline.run()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Deeply nested More constructors")
    void deeplyNestedMore() {
      Trampoline<Integer> trampoline = buildNestedDefer(1000, 0);

      assertThat(trampoline.run()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Mix of deep nesting with all constructors")
    void mixOfDeepNesting() {
      Trampoline<Integer> trampoline = Trampoline.done(0);

      for (int i = 0; i < 100; i++) {
        int currentI = i;
        trampoline =
            trampoline
                .flatMap(x -> Trampoline.defer(() -> Trampoline.done(x + 1)))
                .map(x -> x)
                .flatMap(x -> Trampoline.done(x));
      }

      assertThat(trampoline.run()).isEqualTo(100);
    }

    private Trampoline<Integer> buildNestedDefer(int depth, int current) {
      if (depth <= 0) {
        return Trampoline.done(current);
      }
      return Trampoline.defer(() -> buildNestedDefer(depth - 1, current + 1));
    }
  }

  @Nested
  @DisplayName("Instance and Static Coverage")
  class InstanceCoverage {

    @Test
    @DisplayName("Static done factory creates Done")
    void staticDoneFactory() {
      Trampoline<Integer> trampoline = Trampoline.done(42);

      assertThat(trampoline).isInstanceOf(Trampoline.Done.class);
      assertThat(((Trampoline.Done<Integer>) trampoline).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("Static defer factory creates More")
    void staticDeferFactory() {
      Trampoline<Integer> trampoline = Trampoline.defer(() -> Trampoline.done(42));

      assertThat(trampoline).isInstanceOf(Trampoline.More.class);
    }

    @Test
    @DisplayName("TRAMPOLINE_CLASS constant is accessible")
    void trampolineClassConstant() {
      // Ensure the constant is defined and accessible
      assertThat(Trampoline.TRAMPOLINE_CLASS).isEqualTo(Trampoline.class);
    }
  }
}
