package org.simulation.hkt;

import org.simulation.hkt.either.Either;
import org.simulation.hkt.either.EitherKind;
import org.simulation.hkt.either.EitherKindHelper;
import org.simulation.hkt.either.EitherMonad;
import org.simulation.hkt.list.ListKind;
import org.simulation.hkt.list.ListKindHelper;
import org.simulation.hkt.list.ListMonad;
import org.simulation.hkt.maybe.Maybe;
import org.simulation.hkt.maybe.MaybeKind;
import org.simulation.hkt.maybe.MaybeKindHelper;
import org.simulation.hkt.maybe.MaybeMonad;
import org.simulation.hkt.optional.OptionalKind;
import org.simulation.hkt.optional.OptionalKindHelper;
import org.simulation.hkt.optional.OptionalMonad;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;


public class MonadSimulation {

  public static void main(String[] args) {
    var monadSimulation = new MonadSimulation();

    monadSimulation.listMonadExample();
    monadSimulation.optionalMonadExample();
    monadSimulation.maybeMonadExample();
    monadSimulation.eitherMonadExample();
  }

  void listMonadExample() {
    // Instantiate the Monad implementations
    ListMonad listMonad = new ListMonad();

    // --- List Monad Example ---
    System.out.println("--- List Monad ---");

    // 1. Using of
    ListKind<Integer> ofList = listMonad.of(10);
    System.out.println("of(10): " + ListKindHelper.unwrap(ofList)); // Output: [10]

    // 2. Using map (from Functor, inherited by Monad)
    List<Integer> numbers = Arrays.asList(1, 2, 3);
    ListKind<Integer> numberKind = ListKindHelper.wrap(numbers);
    ListKind<String> stringsKind = listMonad.map(Object::toString, numberKind);
    System.out.println("map(toString): " + ListKindHelper.unwrap(stringsKind)); // Output: [1, 2, 3]

    // 3. Using flatMap
    // Function that takes an integer and returns a ListKind of integers (e.g., the number and itself * 10)
    Function<Integer, Kind<ListKind<?>, Integer>> duplicateAndMultiply =
        x -> ListKindHelper.wrap(Arrays.asList(x, x * 10));

    ListKind<Integer> flatMappedList = listMonad.flatMap(duplicateAndMultiply, numberKind);
    System.out.println("flatMap(duplicateAndMultiply): " + ListKindHelper.unwrap(flatMappedList)); // Output: [1, 10, 2, 20, 3, 30]

  }

  void optionalMonadExample() {
    OptionalMonad optionalMonad = new OptionalMonad();

    // --- Optional Monad Example ---
    System.out.println("\n--- Optional Monad ---");

    // 1. Using of
    OptionalKind<String> ofOpt = optionalMonad.of("hello");
    OptionalKind<Object> ofNullOpt = optionalMonad.of(null); // Handled by Optional.ofNullable
    System.out.println("of(\"hello\"): " + OptionalKindHelper.unwrap(ofOpt)); // Output: Optional[hello]
    System.out.println("of(null): " + OptionalKindHelper.unwrap(ofNullOpt));   // Output: Optional.empty

    // 2. Using map
    OptionalKind<Integer> valueOptKind = OptionalKindHelper.wrap(Optional.of(5));
    OptionalKind<Integer> emptyOptKind = OptionalKindHelper.wrap(Optional.empty());

    OptionalKind<String> mappedValue = optionalMonad.map(i -> "Value: " + i, valueOptKind);
    OptionalKind<String> mappedEmpty = optionalMonad.map(i -> "Value: " + i, emptyOptKind);
    System.out.println("map on value: " + OptionalKindHelper.unwrap(mappedValue)); // Output: Optional[Value: 5]
    System.out.println("map on empty: " + OptionalKindHelper.unwrap(mappedEmpty)); // Output: Optional.empty

    // 3. Using flatMap for safe division
    // Function that performs division but returns OptionalKind<Double> to handle division by zero
    Function<Integer, Kind<OptionalKind<?>, Double>> safeDivideBy =
        divisor -> OptionalKindHelper.wrap(
            (divisor == 0) ? Optional.empty() : Optional.of(100.0 / divisor)
        );

    OptionalKind<Integer> fiveOptKind = OptionalKindHelper.wrap(Optional.of(5));
    OptionalKind<Integer> zeroOptKind = OptionalKindHelper.wrap(Optional.of(0));

    OptionalKind<Double> result1 = optionalMonad.flatMap(safeDivideBy, fiveOptKind);
    OptionalKind<Double> result2 = optionalMonad.flatMap(safeDivideBy, zeroOptKind); // Will be empty
    OptionalKind<Double> result3 = optionalMonad.flatMap(safeDivideBy, emptyOptKind); // Will be empty

    System.out.println("flatMap safeDivideBy(5): " + OptionalKindHelper.unwrap(result1)); // Output: Optional[20.0]
    System.out.println("flatMap safeDivideBy(0): " + OptionalKindHelper.unwrap(result2)); // Output: Optional.empty
    System.out.println("flatMap safeDivideBy(empty): " + OptionalKindHelper.unwrap(result3)); // Output: Optional.empty


    // Chaining flatMap
    // Example: Get a number, add 10 if present, then try safe division
    OptionalKind<Integer> initialOpt = OptionalKindHelper.wrap(Optional.of(10));

    // Step 1: Add 10. Result is OptionalKind<Integer>
    OptionalKind<Integer> addedTenResult = optionalMonad.flatMap(
        x -> OptionalKindHelper.wrap(Optional.of(x + 10)), // Returns OptionalKind<Integer>
        initialOpt
    );
    // addedTenResult now holds OptionalKind containing Optional.of(20)

    // Step 2: Apply safe division using the result of Step 1. Result is OptionalKind<Double>
    // Make sure safeDivideByOptional is defined as in the previous example:
    Function<Integer, Kind<OptionalKind<?>, Double>> safeDivideByOptional =
        divisor -> OptionalKindHelper.wrap(
            (divisor == 0) ? Optional.empty() : Optional.of(100.0 / divisor)
        );

    OptionalKind<Double> finalResult = optionalMonad.flatMap(
        safeDivideByOptional, // Function returns OptionalKind<Double>
        addedTenResult // Input is OptionalKind<Integer> from Step 1
    );

    // Now finalResult correctly holds the OptionalKind<Double>
    System.out.println("Chained Optional flatMap result: " + OptionalKindHelper.unwrap(finalResult)); // Output: Optional[5.0]

  }

  void maybeMonadExample() {
    MaybeMonad maybeMonad = new MaybeMonad();

    // --- Maybe Monad Example ---
    System.out.println("\n--- Maybe Monad ---");

    // 1. Using of
    MaybeKind<String> ofMaybe = maybeMonad.of("world");
    MaybeKind<Object> ofNullMaybe = maybeMonad.of(null); // Becomes Nothing
    System.out.println("of(\"world\"): " + MaybeKindHelper.unwrap(ofMaybe)); // Output: Just(world)
    System.out.println("of(null): " + MaybeKindHelper.unwrap(ofNullMaybe));   // Output: Nothing

    // 2. Using map
    MaybeKind<Integer> justKind = MaybeKindHelper.wrap(Maybe.just(36));
    MaybeKind<Integer> nothingKind = MaybeKindHelper.wrap(Maybe.nothing());

    MaybeKind<Double> mappedJust = maybeMonad.map(i -> Math.sqrt(i), justKind);
    MaybeKind<Double> mappedNothing = maybeMonad.map(i -> Math.sqrt(i), nothingKind);
    System.out.println("map sqrt on Just(36): " + MaybeKindHelper.unwrap(mappedJust)); // Output: Just(6.0)
    System.out.println("map sqrt on Nothing: " + MaybeKindHelper.unwrap(mappedNothing)); // Output: Nothing

    // 3. Using flatMap for safe inversion
    Function<Integer, Kind<MaybeKind<?>, Double>> safeInvertMaybe =
        num -> MaybeKindHelper.wrap(
            (num == 0) ? Maybe.nothing() : Maybe.just(1.0 / num)
        );

    MaybeKind<Integer> tenMaybeKind = MaybeKindHelper.wrap(Maybe.just(10));
    MaybeKind<Integer> zeroMaybeKind = MaybeKindHelper.wrap(Maybe.just(0));

    MaybeKind<Double> resultMaybe1 = maybeMonad.flatMap(safeInvertMaybe, tenMaybeKind);
    MaybeKind<Double> resultMaybe2 = maybeMonad.flatMap(safeInvertMaybe, zeroMaybeKind); // Becomes Nothing
    MaybeKind<Double> resultMaybe3 = maybeMonad.flatMap(safeInvertMaybe, nothingKind); // Stays Nothing

    System.out.println("flatMap safeInvertMaybe(10): " + MaybeKindHelper.unwrap(resultMaybe1)); // Output: Just(0.1)
    System.out.println("flatMap safeInvertMaybe(0): " + MaybeKindHelper.unwrap(resultMaybe2)); // Output: Nothing
    System.out.println("flatMap safeInvertMaybe(Nothing): " + MaybeKindHelper.unwrap(resultMaybe3)); // Output: Nothing

    // Chaining flatMap (Maybe)
    MaybeKind<Integer> initialMaybe = MaybeKindHelper.wrap(Maybe.just(4));

    // Step 1: Multiply by 5
    MaybeKind<Integer> multipliedResultMaybe = maybeMonad.flatMap(
        x -> MaybeKindHelper.wrap(Maybe.just(x * 5)), // Returns MaybeKind<Integer>
        initialMaybe
    );
    // multipliedResultMaybe holds MaybeKind containing Just(20)

    // Step 2: Safely invert the result of Step 1
    MaybeKind<Double> finalResultMaybe = maybeMonad.flatMap(
        safeInvertMaybe, // Function returns MaybeKind<Double>
        multipliedResultMaybe // Input is MaybeKind<Integer> from Step 1
    );

    System.out.println("Chained Maybe flatMap result: " + MaybeKindHelper.unwrap(finalResultMaybe)); // Output: Just(0.05) (1.0 / (4*5))

    // Example showing Nothing propagation in chaining
    MaybeKind<Integer> initialNothingMaybe = MaybeKindHelper.wrap(Maybe.nothing());
    MaybeKind<Integer> chainedResultMaybeNothing1 = maybeMonad.flatMap(
        x -> MaybeKindHelper.wrap(Maybe.just(x * 5)),
        initialNothingMaybe // Start with Nothing
    );
    MaybeKind<Double> finalResultMaybeNothing = maybeMonad.flatMap(
        safeInvertMaybe,
        chainedResultMaybeNothing1 // This is already NothingKind
    );
    System.out.println("Chained Maybe flatMap starting with Nothing: " + MaybeKindHelper.unwrap(finalResultMaybeNothing)); // Output: Nothing

  }

  void eitherMonadExample() {
    // Instantiate Monad for Either<String, ?> (Left type is String)
    EitherMonad<String> eitherMonad = new EitherMonad<>();


    System.out.println("\n--- Either Monad (Left = String) ---");

    // 1. Using of (creates a Right)
    Kind<EitherKind<String, ?>, Integer> ofEither = eitherMonad.of(123);
    System.out.println("of(123): " + EitherKindHelper.unwrap(ofEither)); // Output: Right(123)

    // 2. Using map
    Kind<EitherKind<String, ?>, Integer> rightKind = EitherKindHelper.wrap(Either.right(100));
    Kind<EitherKind<String, ?>, Integer> leftKind = EitherKindHelper.wrap(Either.left("Error occurred"));

    // Map on Right: function is applied
    Kind<EitherKind<String, ?>, Integer> mappedRight = eitherMonad.map(x -> x * 2, rightKind);
    System.out.println("map (*2) on Right(100): " + EitherKindHelper.unwrap(mappedRight)); // Output: Right(200)

    // Map on Left: function is ignored, Left propagates
    Kind<EitherKind<String, ?>, Integer> mappedLeft = eitherMonad.map(x -> x * 2, leftKind);
    System.out.println("map (*2) on Left(\"Error...\"): " + EitherKindHelper.unwrap(mappedLeft)); // Output: Left(Error occurred)

    // 3. Using flatMap
    // Function: parse string to int, return Left(errorMsg) or Right(intValue)
    Function<String, Kind<EitherKind<String, ?>, Integer>> parseIntEither = s -> {
      try {
        return EitherKindHelper.wrap(Either.right(Integer.parseInt(s)));
      } catch (NumberFormatException e) {
        return EitherKindHelper.wrap(Either.left("Invalid integer format: '" + s + "'"));
      }
    };

    Kind<EitherKind<String, ?>, String> rightStringKind = EitherKindHelper.wrap(Either.right("456"));
    Kind<EitherKind<String, ?>, String> rightInvalidStringKind = EitherKindHelper.wrap(Either.right("abc"));
    Kind<EitherKind<String, ?>, String> leftStringKind = EitherKindHelper.wrap(Either.left("Initial error"));

    // flatMap on Right(valid string) -> Right(parsed int)
    Kind<EitherKind<String, ?>, Integer> fmResult1 = eitherMonad.flatMap(parseIntEither, rightStringKind);
    System.out.println("flatMap parseIntEither on Right(\"456\"): " + EitherKindHelper.unwrap(fmResult1)); // Output: Right(456)

    // flatMap on Right(invalid string) -> Left(error message)
    Kind<EitherKind<String, ?>, Integer> fmResult2 = eitherMonad.flatMap(parseIntEither, rightInvalidStringKind);
    System.out.println("flatMap parseIntEither on Right(\"abc\"): " + EitherKindHelper.unwrap(fmResult2)); // Output: Left(Invalid integer format: 'abc')

    // flatMap on Left -> Left propagates
    Kind<EitherKind<String, ?>, Integer> fmResult3 = eitherMonad.flatMap(parseIntEither, leftStringKind);
    System.out.println("flatMap parseIntEither on Left(\"Initial error\"): " + EitherKindHelper.unwrap(fmResult3)); // Output: Left(Initial error)

    // 4. Chaining flatMap
    Kind<EitherKind<String, ?>, String> initialEitherStr = EitherKindHelper.wrap(Either.right(" 20 ")); // Start with a string

    // Step 1: Parse the string
    Kind<EitherKind<String, ?>, Integer> parsedResult = eitherMonad.flatMap(parseIntEither, initialEitherStr);
    // parsedResult holds Right(20)

    // Step 2: Define another function, e.g., safe division returning Either
    Function<Integer, Kind<EitherKind<String, ?>, Double>> safeDivideEither =
        num -> (num == 0)
            ? EitherKindHelper.wrap(Either.left("Division by zero"))
            : EitherKindHelper.wrap(Either.right(100.0 / num));

    // Step 3: Apply safe division to the parsed integer
    Kind<EitherKind<String, ?>, Double> finalResultEither = eitherMonad.flatMap(safeDivideEither, parsedResult);
    System.out.println("Chained Either: parseInt -> safeDivide: " + EitherKindHelper.unwrap(finalResultEither)); // Output: Right(5.0)

    // Chaining example with failure propagation
    Kind<EitherKind<String, ?>, String> initialEitherBadStr = EitherKindHelper.wrap(Either.right("xyz"));
    Kind<EitherKind<String, ?>, Integer> parsedResultFail = eitherMonad.flatMap(parseIntEither, initialEitherBadStr);
    // parsedResultFail holds Left("Invalid integer format: 'xyz'")
    Kind<EitherKind<String, ?>, Double> finalResultEitherFail = eitherMonad.flatMap(safeDivideEither, parsedResultFail);
    // safeDivideEither is never called because parsedResultFail is Left
    System.out.println("Chained Either starting with bad string: " + EitherKindHelper.unwrap(finalResultEitherFail)); // Output: Left(Invalid integer format: 'xyz')

  }
}

