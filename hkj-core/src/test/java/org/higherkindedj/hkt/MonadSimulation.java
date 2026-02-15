// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.list.ListKind; // For ListKind.Witness
import org.higherkindedj.hkt.list.ListMonad;
import org.jspecify.annotations.NonNull; // Assuming usage

/**
 * Demonstrates the usage of the ListMonad with various operations. This class is for demonstration
 * and testing purposes.
 */
public class MonadSimulation {

  // Get the singleton instance of ListMonad
  private static final Monad<ListKind.Witness> listMonad = ListMonad.INSTANCE;

  /**
   * Simulates the 'of' (or 'pure') operation of a Monad. It takes a simple value and lifts it into
   * the monadic context (a List in this case).
   *
   * @param value The value to lift.
   * @param <A> The type of the value.
   * @return A Kind representing a List containing the single value.
   */
  public static <A> @NonNull Kind<ListKind.Witness, A> simulateOf(A value) {
    // Use the 'of' method from the ListMonad instance
    return listMonad.of(value);
  }

  /**
   * Simulates the 'map' operation of a Functor (which Monad extends). It applies a function to each
   * element within the monadic context.
   *
   * @param func The function to apply.
   * @param kind The Kind (List) to map over.
   * @param <A> The original element type.
   * @param <B> The new element type after applying the function.
   * @return A new Kind (List) containing the transformed elements.
   */
  public static <A, B> @NonNull Kind<ListKind.Witness, B> simulateMap(
      @NonNull Function<A, B> func, @NonNull Kind<ListKind.Witness, A> kind) {
    // Use the 'map' method from the ListMonad instance
    return listMonad.map(func, kind);
  }

  /**
   * Simulates the 'flatMap' operation of a Monad. It applies a function that returns a monadic
   * value to each element and flattens the result.
   *
   * @param func The function that takes an element and returns a Kind (List).
   * @param kind The Kind (List) to flatMap over.
   * @param <A> The original element type.
   * @param <B> The new element type (contained within the Kind returned by the function).
   * @return A new Kind (List) that is the result of applying the function and flattening.
   */
  public static <A, B> @NonNull Kind<ListKind.Witness, B> simulateFlatMap(
      @NonNull Function<A, Kind<ListKind.Witness, B>> func,
      @NonNull Kind<ListKind.Witness, A> kind) {
    // Use the 'flatMap' method from the ListMonad instance
    return listMonad.flatMap(func, kind);
  }

  public static void main(String[] args) {
    System.out.println("Starting Monad Simulation for List...");

    // 1. Simulate 'of'
    System.out.println("\n--- Simulating 'of' ---");
    Kind<ListKind.Witness, Integer> ofList = simulateOf(10); // Correct type
    System.out.println("listMonad.of(10): " + LIST.narrow(ofList));

    // 2. Simulate 'map'
    System.out.println("\n--- Simulating 'map' ---");
    List<Integer> numbers = Arrays.asList(1, 2, 3, 4);
    Kind<ListKind.Witness, Integer> numberKind = LIST.widen(numbers); // Correct type
    Kind<ListKind.Witness, String> stringsKind =
        simulateMap(Object::toString, numberKind); // Correct type
    System.out.println("map(toString, [1,2,3,4]): " + LIST.narrow(stringsKind));

    Kind<ListKind.Witness, Integer> doubledKind = simulateMap(x -> x * 2, numberKind);
    System.out.println("map(x -> x*2, [1,2,3,4]): " + LIST.narrow(doubledKind));

    // 3. Simulate 'flatMap'
    System.out.println("\n--- Simulating 'flatMap' ---");
    // Function for flatMap: takes an Integer, returns a ListKind<Integer>
    Function<Integer, Kind<ListKind.Witness, Integer>> duplicateAndMultiply =
        x -> LIST.widen(Arrays.asList(x, x * 10)); // Correct return type

    // 'numberKind' is already Kind<ListKind.Witness, Integer>
    Kind<ListKind.Witness, Integer> flatMappedList =
        simulateFlatMap(duplicateAndMultiply, numberKind); // Correct type
    System.out.println("flatMap(x -> [x, x*10], [1,2,3,4]): " + LIST.narrow(flatMappedList));
    // Expected: [1, 10, 2, 20, 3, 30, 4, 40]

    // Another flatMap example: Integer -> ListKind<String>
    Function<Integer, Kind<ListKind.Witness, String>> intToWords =
        x -> {
          if (x == 1) return LIST.widen(Arrays.asList("One", "Uno"));
          if (x == 2) return LIST.widen(Collections.singletonList("Two"));
          return LIST.widen(Collections.emptyList());
        };
    Kind<ListKind.Witness, String> wordsList = simulateFlatMap(intToWords, numberKind);
    System.out.println("flatMap(intToWords, [1,2,3,4]): " + LIST.narrow(wordsList));
    // Expected: ["One", "Uno", "Two"]

    // Chaining operations
    System.out.println("\n--- Chaining Operations ---");
    Kind<ListKind.Witness, Integer> initialData = LIST.widen(Arrays.asList(5, 6));
    Kind<ListKind.Witness, String> chainedResult =
        listMonad.flatMap(
            (Integer x) ->
                listMonad.map(
                    (Integer y) -> "Res: " + (x + y),
                    listMonad.of(x * 2) // Lifts x*2 into List(x*2)
                    ),
            initialData);
    System.out.println(
        "Chained (flatMap(x -> map(y -> Res:x+y, of(x*2)))): " + LIST.narrow(chainedResult));
    // For 5: flatMap(x=5 -> map(y -> "Res:"+(5+y), List(10) )) -> map(y=10 -> "Res:"+(5+10),
    // List(10)) -> List("Res:15")
    // For 6: flatMap(x=6 -> map(y -> "Res:"+(6+y), List(12) )) -> map(y=12 -> "Res:"+(6+12),
    // List(12)) -> List("Res:18")
    // Expected: ["Res:15", "Res:18"]

    System.out.println("\nMonad Simulation Complete.");
  }
}
