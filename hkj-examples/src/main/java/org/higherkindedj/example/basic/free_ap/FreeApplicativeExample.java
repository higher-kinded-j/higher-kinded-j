// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.free_ap;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;

/**
 * Demonstrates Free Applicative in Higher-Kinded-J.
 *
 * <p>Free Applicative captures independent computations that can potentially run in parallel. This
 * contrasts with Free Monad, which captures sequential, dependent computations.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Creating FreeAp values with pure and lift
 *   <li>Combining independent computations with map2
 *   <li>Interpreting with foldMap and natural transformations
 *   <li>The key distinction from Free Monad
 * </ul>
 *
 * <p>Run with: ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.free_ap.FreeApplicativeExample
 */
public class FreeApplicativeExample {

  public static void main(String[] args) {
    System.out.println("=== Free Applicative Examples ===\n");

    basicPureAndMapExample();
    combineIndependentExample();
    interpretationExample();
    comparisonWithFreeMonadExample();

    System.out.println("\n=== All examples completed ===");
  }

  /** Example 1: Basic pure and map operations. */
  private static void basicPureAndMapExample() {
    System.out.println("--- Example 1: Basic Pure and Map ---");

    // Create a pure value
    FreeAp<IdKind.Witness, Integer> pureValue = FreeAp.pure(42);
    System.out.println("Created pure value: FreeAp.pure(42)");

    // Map over it
    FreeAp<IdKind.Witness, String> mapped = pureValue.map(x -> "Value is: " + x);
    System.out.println("After map: FreeAp containing transformed value");

    // Interpret to get the result
    IdMonad idApp = IdMonad.instance();
    Natural<IdKind.Witness, IdKind.Witness> identity = Natural.identity();

    Kind<IdKind.Witness, String> result = mapped.foldMap(identity, idApp);
    System.out.println("Result: " + ID.narrow(result).value());

    System.out.println();
  }

  /**
   * Example 2: Combining independent computations.
   *
   * <p>The key feature of Free Applicative: combining computations that don't depend on each
   * other's results.
   */
  private static void combineIndependentExample() {
    System.out.println("--- Example 2: Combining Independent Computations ---");

    // Two independent computations
    FreeAp<IdKind.Witness, String> getName = FreeAp.pure("Alice");
    FreeAp<IdKind.Witness, Integer> getAge = FreeAp.pure(30);

    System.out.println("getName: FreeAp.pure(\"Alice\")");
    System.out.println("getAge: FreeAp.pure(30)");

    // Combine them - neither depends on the other!
    FreeAp<IdKind.Witness, String> combined =
        getName.map2(getAge, (name, age) -> name + " is " + age + " years old");

    System.out.println("Combined with map2: name + \" is \" + age + \" years old\"");

    // Interpret
    IdMonad idApp = IdMonad.instance();
    Natural<IdKind.Witness, IdKind.Witness> identity = Natural.identity();

    Kind<IdKind.Witness, String> result = combined.foldMap(identity, idApp);
    System.out.println("Result: " + ID.narrow(result).value());

    System.out.println();
  }

  /**
   * Example 3: Interpretation with natural transformation.
   *
   * <p>Shows how FreeAp programs are interpreted using foldMap.
   */
  private static void interpretationExample() {
    System.out.println("--- Example 3: Interpretation ---");

    // Build a more complex program
    FreeAp<IdKind.Witness, Integer> a = FreeAp.pure(10);
    FreeAp<IdKind.Witness, Integer> b = FreeAp.pure(20);
    FreeAp<IdKind.Witness, Integer> c = FreeAp.pure(30);

    System.out.println("Three independent values: 10, 20, 30");

    // Combine all three using the applicative instance
    FreeApApplicative<IdKind.Witness> freeApApp = FreeApApplicative.instance();

    // map2 chained to combine three values
    FreeAp<IdKind.Witness, Integer> sum =
        a.map2(b.map2(c, (x, y) -> x + y), (x, partial) -> x + partial);

    System.out.println("Program: sum all three values");

    // Interpret
    IdMonad idApp = IdMonad.instance();
    Natural<IdKind.Witness, IdKind.Witness> identity = Natural.identity();

    Kind<IdKind.Witness, Integer> result = sum.foldMap(identity, idApp);
    System.out.println("Result (10 + 20 + 30): " + ID.narrow(result).value());

    System.out.println();
  }

  /**
   * Example 4: Comparison with Free Monad.
   *
   * <p>Illustrates the key conceptual difference between Free Applicative and Free Monad.
   */
  private static void comparisonWithFreeMonadExample() {
    System.out.println("--- Example 4: Free Applicative vs Free Monad ---");

    System.out.println("\nFree Monad (sequential, dependent):");
    System.out.println("  step1.flatMap(result1 ->");
    System.out.println("    step2(result1).flatMap(result2 ->");
    System.out.println("      step3(result1, result2)))");
    System.out.println("  Each step depends on previous results.");
    System.out.println("  Cannot run in parallel.");

    System.out.println("\nFree Applicative (independent, parallel):");
    System.out.println("  step1.map2(step2, (result1, result2) -> combine(result1, result2))");
    System.out.println("  Steps are independent - neither needs the other's result.");
    System.out.println("  CAN run in parallel with a smart interpreter.");

    System.out.println("\nExample with data fetching:");

    // Simulate two independent data fetches
    FreeAp<IdKind.Witness, String> fetchUser = FreeAp.pure("User{id=1, name='Alice'}");
    FreeAp<IdKind.Witness, String> fetchPosts = FreeAp.pure("Posts{userId=1, count=5}");

    System.out.println("\n  fetchUser = FreeAp.pure(\"User{...}\")");
    System.out.println("  fetchPosts = FreeAp.pure(\"Posts{...}\")");

    // These are INDEPENDENT - a smart interpreter could run them in parallel
    FreeAp<IdKind.Witness, String> dashboard =
        fetchUser.map2(fetchPosts, (user, posts) -> "Dashboard: " + user + " with " + posts);

    System.out.println("\n  dashboard = fetchUser.map2(fetchPosts, combine)");
    System.out.println("  Notice: fetchPosts does NOT use the result of fetchUser!");
    System.out.println("  A parallel interpreter could fetch both simultaneously.");

    // Interpret
    IdMonad idApp = IdMonad.instance();
    Natural<IdKind.Witness, IdKind.Witness> identity = Natural.identity();

    Kind<IdKind.Witness, String> result = dashboard.foldMap(identity, idApp);
    System.out.println("\n  Result: " + ID.narrow(result).value());
  }
}
