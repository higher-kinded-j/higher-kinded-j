// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.coyoneda;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Demonstrates Coyoneda (the "free functor") in Higher-Kinded-J.
 *
 * <p>Coyoneda provides two key benefits:
 *
 * <ol>
 *   <li><b>Automatic Functor instances</b>: Any type constructor can be mapped over without
 *       implementing Functor
 *   <li><b>Map fusion</b>: Multiple consecutive map operations are combined into one function
 *       composition
 * </ol>
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Lifting values into Coyoneda
 *   <li>Mapping without a Functor instance
 *   <li>Map fusion in action
 *   <li>Lowering back to the original type
 * </ul>
 *
 * <p>Run with: ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.coyoneda.CoyonedaExample
 */
public class CoyonedaExample {

  public static void main(String[] args) {
    System.out.println("=== Coyoneda Examples ===\n");

    basicLiftAndLowerExample();
    mapFusionExample();
    mapWithoutFunctorExample();
    chainedTransformationsExample();

    System.out.println("\n=== All examples completed ===");
  }

  /** Example 1: Basic lift and lower operations. */
  private static void basicLiftAndLowerExample() {
    System.out.println("--- Example 1: Basic Lift and Lower ---");

    // Lift a Maybe into Coyoneda
    Kind<MaybeKind.Witness, Integer> maybeValue = MAYBE.widen(Maybe.just(42));
    Coyoneda<MaybeKind.Witness, Integer> coyo = Coyoneda.lift(maybeValue);

    System.out.println("Original Maybe: " + MAYBE.narrow(maybeValue));
    System.out.println("Lifted into Coyoneda: " + coyo);

    // Lower back to Maybe (requires Functor)
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, Integer> lowered = coyo.lower(functor);
    System.out.println("Lowered back to Maybe: " + MAYBE.narrow(lowered));

    System.out.println();
  }

  /**
   * Example 2: Map fusion demonstration.
   *
   * <p>Multiple map operations are composed into a single function, applied once during lower().
   */
  private static void mapFusionExample() {
    System.out.println("--- Example 2: Map Fusion ---");

    // Start with a list
    Kind<ListKind.Witness, Integer> listValue = LIST.widen(List.of(1, 2, 3, 4, 5));
    System.out.println("Original list: " + LIST.narrow(listValue));

    // Lift into Coyoneda and chain multiple maps
    Coyoneda<ListKind.Witness, Integer> coyo = Coyoneda.lift(listValue);

    // These maps are NOT applied yet - just function composition!
    Coyoneda<ListKind.Witness, Integer> step1 =
        coyo.map(
            x -> {
              System.out.println("  Step 1 (multiply by 2): processing " + x);
              return x * 2;
            });

    Coyoneda<ListKind.Witness, Integer> step2 =
        step1.map(
            x -> {
              System.out.println("  Step 2 (add 10): processing " + x);
              return x + 10;
            });

    Coyoneda<ListKind.Witness, String> step3 =
        step2.map(
            x -> {
              System.out.println("  Step 3 (to string): processing " + x);
              return "Value: " + x;
            });

    System.out.println("\nNo processing has happened yet! Now lowering...\n");

    // NOW the composed function is applied - in ONE traversal
    ListMonad listFunctor = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> result = step3.lower(listFunctor);

    System.out.println("\nResult: " + LIST.narrow(result));
    System.out.println("\nNotice: Each element was processed through all 3 steps in sequence,");
    System.out.println("but the list was only traversed ONCE (during lower).");

    System.out.println();
  }

  /**
   * Example 3: Mapping without a Functor instance.
   *
   * <p>Coyoneda allows mapping over any type constructor, deferring the need for a Functor until
   * lower().
   */
  private static void mapWithoutFunctorExample() {
    System.out.println("--- Example 3: Map Without Functor ---");

    // Lift a value
    Kind<MaybeKind.Witness, String> maybeString = MAYBE.widen(Maybe.just("hello"));
    Coyoneda<MaybeKind.Witness, String> coyo = Coyoneda.lift(maybeString);

    // Map multiple times - no Functor needed!
    Coyoneda<MaybeKind.Witness, String> upper = coyo.map(String::toUpperCase);
    Coyoneda<MaybeKind.Witness, Integer> length = upper.map(String::length);
    Coyoneda<MaybeKind.Witness, String> formatted = length.map(n -> "Length: " + n);

    System.out.println("Chained maps without Functor: coyo.map(upper).map(length).map(format)");

    // Only need Functor at the end
    MaybeFunctor functor = MaybeFunctor.INSTANCE;
    Kind<MaybeKind.Witness, String> result = formatted.lower(functor);
    System.out.println("Result after lower: " + MAYBE.narrow(result));

    System.out.println();
  }

  /**
   * Example 4: Complex chained transformations.
   *
   * <p>Shows a more complex transformation pipeline with Coyoneda.
   */
  private static void chainedTransformationsExample() {
    System.out.println("--- Example 4: Chained Transformations ---");

    // Data processing pipeline
    Kind<ListKind.Witness, String> names = LIST.widen(List.of("alice", "bob", "charlie", "david"));
    System.out.println("Input names: " + LIST.narrow(names));

    // Build transformation pipeline
    Coyoneda<ListKind.Witness, String> pipeline =
        Coyoneda.lift(names)
            .map(String::trim) // Step 1: Trim whitespace
            .map(String::toUpperCase) // Step 2: Uppercase
            .map(s -> s + "!") // Step 3: Add exclamation
            .map(s -> "[" + s + "]"); // Step 4: Wrap in brackets

    System.out.println("Pipeline created (no execution yet)");

    // Execute the pipeline
    ListMonad listFunctor = ListMonad.INSTANCE;
    Kind<ListKind.Witness, String> result = pipeline.lower(listFunctor);
    System.out.println("Result: " + LIST.narrow(result));

    // Demonstrate with Nothing
    System.out.println("\nWith Maybe.nothing():");
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Coyoneda<MaybeKind.Witness, String> nothingPipeline =
        Coyoneda.lift(nothing).map(String::toUpperCase).map(s -> s + "!");

    Kind<MaybeKind.Witness, String> nothingResult = nothingPipeline.lower(MaybeFunctor.INSTANCE);
    System.out.println("Result: " + MAYBE.narrow(nothingResult));
  }
}
