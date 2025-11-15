// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.foldable;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;

/**
 * An example demonstrating the power of the {@link org.higherkindedj.hkt.Foldable} typeclass and
 * the {@link Monoid} structure.
 *
 * <p>A Monoid allows us to combine elements in a collection and provides an "empty" or "identity"
 * element, which is crucial for handling empty collections gracefully. This example showcases a
 * common functional pattern: {@code foldMap}.
 *
 * <p><b>foldMap:</b> This operation, provided by the {@code Foldable} typeclass, maps each element
 * of a collection to a Monoidal type and then combines all the results using the Monoid's {@code
 * combine} operation. It's incredibly powerful because by simply changing the Monoid, we can get
 * completely different aggregations from the same data.
 */
public class FoldableExample {

  // We use ListTraverse as our Foldable instance for List.
  private static final ListTraverse listFoldable = ListTraverse.INSTANCE;

  public static void main(String[] args) {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);
    Kind<ListKind.Witness, Integer> numbersKind = LIST.widen(numbers);
    System.out.println("Operating on the list: " + numbers);

    // --- Scenario 1: Summing all numbers ---
    // We map each Integer to itself and use the integerAddition Monoid.
    Integer sum = listFoldable.foldMap(Monoids.integerAddition(), Function.identity(), numbersKind);
    System.out.println("\n1. Summing with Monoids.integerAddition(): " + sum); // Expected: 15

    // --- Scenario 2: Multiplying all numbers ---
    // We use the integerMultiplication Monoid.
    Integer product =
        listFoldable.foldMap(Monoids.integerMultiplication(), Function.identity(), numbersKind);
    System.out.println(
        "2. Multiplying with Monoids.integerMultiplication(): " + product); // Expected: 120

    // --- Scenario 3: Converting all numbers to strings and concatenating ---
    // We map each Integer to a String and use the string Monoid.
    String asString = listFoldable.foldMap(Monoids.string(), String::valueOf, numbersKind);
    System.out.println(
        "3. Concatenating as strings with Monoids.string(): " + asString); // Expected: "12345"

    // --- Scenario 4: Checking if all numbers are positive ---
    // We map each Integer to a Boolean (true if > 0) and use the booleanAnd Monoid.
    Boolean allPositive = listFoldable.foldMap(Monoids.booleanAnd(), i -> i > 0, numbersKind);
    System.out.println(
        "4. Checking if all are positive with Monoids.booleanAnd(): "
            + allPositive); // Expected: true

    // --- Scenario 5: Checking if any number is even ---
    // We map each Integer to a Boolean (true if even) and use the booleanOr Monoid.
    Boolean anyEven = listFoldable.foldMap(Monoids.booleanOr(), i -> i % 2 == 0, numbersKind);
    System.out.println(
        "5. Checking if any are even with Monoids.booleanOr(): " + anyEven); // Expected: true

    // --- Scenario 6: Folding an empty list ---
    // The `empty()` value of the monoid ensures this works correctly.
    List<Integer> emptyList = List.of();
    Kind<ListKind.Witness, Integer> emptyKind = LIST.widen(emptyList);
    Integer sumOfEmpty =
        listFoldable.foldMap(Monoids.integerAddition(), Function.identity(), emptyKind);
    System.out.println("\n6. Summing an empty list: " + sumOfEmpty); // Expected: 0

    // --- Scenario 7: Using Long monoids for large number operations ---
    // The Long monoids handle large numeric values that exceed Integer's range.
    List<Long> userCounts = List.of(1_500_000L, 2_300_000L, 890_000L);
    Kind<ListKind.Witness, Long> userCountsKind = LIST.widen(userCounts);
    Long totalUsers =
        listFoldable.foldMap(Monoids.longAddition(), Function.identity(), userCountsKind);
    System.out.println("\n7. Total users with Long monoid: " + totalUsers); // Expected: 4,690,000

    // --- Scenario 8: Using Double monoids for financial calculations ---
    // The Double monoids provide floating-point arithmetic for precise calculations.
    List<Double> expenses = List.of(49.99, 129.50, 89.99);
    Kind<ListKind.Witness, Double> expensesKind = LIST.widen(expenses);
    Double totalExpenses =
        listFoldable.foldMap(Monoids.doubleAddition(), Function.identity(), expensesKind);
    System.out.println(
        "8. Total expenses with Double monoid: $" + totalExpenses); // Expected: $269.48

    // --- Scenario 9: Finding maximum score from optional values ---
    // The maximum monoid elegantly handles sparse data, ignoring empty optionals.
    List<Optional<Integer>> scores =
        List.of(Optional.of(85), Optional.empty(), Optional.of(92), Optional.of(78));
    Kind<ListKind.Witness, Optional<Integer>> scoresKind = LIST.widen(scores);
    Optional<Integer> highestScore =
        listFoldable.foldMap(Monoids.maximum(), Function.identity(), scoresKind);
    System.out.println("9. Highest score: " + highestScore); // Expected: Optional[92]

    // --- Advanced: Using combineN for repeated application ---
    demonstrateCombineN();
  }

  /**
   * Demonstrates the {@code combineN} method for repeated application of a value.
   *
   * <p>{@code combineN(value, n)} combines a value with itself {@code n} times using the monoid's
   * operation. This is useful for scenarios like compound interest calculations, text formatting,
   * and other patterns requiring repeated application.
   */
  private static void demonstrateCombineN() {
    System.out.println("\n=== Using combineN for Repeated Application ===");

    // Calculate compound interest: initial * (1.05)^10
    Monoid<Double> growth = Monoids.doubleMultiplication();
    Double rate = 1.05; // 5% annual growth
    Double compoundFactor = growth.combineN(rate, 10);

    Double initialInvestment = 1000.0;
    Double finalValue = initialInvestment * compoundFactor;

    System.out.println("Initial investment: $" + initialInvestment);
    System.out.println("After 10 years at 5%: $" + String.format("%.2f", finalValue));

    // Build repeated string pattern
    Monoid<String> stringConcat = Monoids.string();
    String separator = "=";
    String border = stringConcat.combineN(separator, 50);

    System.out.println("\n" + border);
    System.out.println("Repeated border created with combineN");
    System.out.println(border);
  }
}
