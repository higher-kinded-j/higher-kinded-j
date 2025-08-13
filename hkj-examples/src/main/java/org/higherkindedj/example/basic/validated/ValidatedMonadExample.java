// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;

/**
 * Demonstrates basic usage of {@link ValidatedMonad}, now including MonadError capabilities.
 * Validated is typically used to represent computations that can either succeed with a value or
 * fail with an error. This example uses {@code List<String>} as the error type.
 *
 * <p>See also: - {@link Validated} - {@link ValidatedMonad} - {@link
 * org.higherkindedj.hkt.validated.Valid} - {@link org.higherkindedj.hkt.validated.Invalid} - {@link
 * org.higherkindedj.hkt.validated.ValidatedKindHelper} - {@link org.higherkindedj.hkt.MonadError}
 */
public class ValidatedMonadExample {

  // Define a Semigroup for combining List<String> errors by concatenation.
  private static final Semigroup<List<String>> listSemigroup = Semigroups.list();

  private static final ValidatedMonad<List<String>> validatedMonad =
      ValidatedMonad.instance(listSemigroup);

  public static void main(String[] args) {
    System.out.println("--- ValidatedMonad Usage Example (with MonadError) ---");
    validatedMonadOperations();
  }

  private static void validatedMonadOperations() {
    // --- 1. Creating Instances ---
    System.out.println("\n--- 1. Creating Instances ---");

    // Using monad.of() to create a Valid instance (Kind-wrapped)
    // 'of' is inherited from Applicative
    Kind<ValidatedKind.Witness<List<String>>, Integer> validKindFromOf = validatedMonad.of(42);
    System.out.println("From monad.of(42): " + VALIDATED.narrow(validKindFromOf));

    // Using Validated.valid() and VALIDATED.widen()
    Validated<List<String>, String> rawValid = Validated.valid("Success!");
    Kind<ValidatedKind.Witness<List<String>>, String> validKind = VALIDATED.widen(rawValid);
    System.out.println("From Validated.valid() & widen(): " + VALIDATED.narrow(validKind));

    // Using Validated.invalid() and VALIDATED.widen()
    Validated<List<String>, String> rawInvalid =
        Validated.invalid(Collections.singletonList("Error: Something went wrong."));
    Kind<ValidatedKind.Witness<List<String>>, String> invalidKindFromHelper = // Renamed for clarity
        VALIDATED.widen(rawInvalid);
    System.out.println(
        "From Validated.invalid() & widen(): " + VALIDATED.narrow(invalidKindFromHelper));

    // Using VALIDATED.valid() for
    Kind<ValidatedKind.Witness<List<String>>, String> vKind = VALIDATED.valid("Success!");
    System.out.println("From ValidatedHelper.valid(): " + VALIDATED.narrow(vKind));

    // VALIDATED.invalid()
    Kind<ValidatedKind.Witness<List<String>>, String> idKind =
        VALIDATED.invalid(Collections.singletonList("Error: Something went wrong."));
    System.out.println("From ValidatedHelper.invalid(): " + VALIDATED.narrow(idKind));

    // --- 2. Map Operation (Functor's map) ---
    // 'map' is inherited from Functor
    System.out.println("\n--- 2. Map Operation ---");
    Function<Integer, String> intToString = i -> "Value: " + i;

    Kind<ValidatedKind.Witness<List<String>>, String> mappedValid =
        validatedMonad.map(intToString, validKindFromOf);
    System.out.println("Map (Valid input): " + VALIDATED.narrow(mappedValid));

    Validated<List<String>, Integer> rawInvalidInt =
        Validated.invalid(Collections.singletonList("Initial error for map"));
    Kind<ValidatedKind.Witness<List<String>>, Integer>
        invalidIntKind = // Used later in flatMap & MonadError
        VALIDATED.widen(rawInvalidInt);
    Kind<ValidatedKind.Witness<List<String>>, String> mappedInvalid =
        validatedMonad.map(intToString, invalidIntKind);
    System.out.println("Map (Invalid input): " + VALIDATED.narrow(mappedInvalid));

    // --- 3. FlatMap Operation (Monad's flatMap) ---
    // 'flatMap' is from Monad
    System.out.println("\n--- 3. FlatMap Operation ---");
    Function<Integer, Kind<ValidatedKind.Witness<List<String>>, String>> intToValidatedStringKind =
        i -> {
          if (i > 0) {
            return VALIDATED.widen(Validated.valid("Positive: " + i));
          } else {
            // For flatMap, the function itself returns a Kind
            return VALIDATED.invalid(Collections.singletonList("Number not positive: " + i));
          }
        };

    Kind<ValidatedKind.Witness<List<String>>, Integer> positiveNumKind = validatedMonad.of(10);
    Kind<ValidatedKind.Witness<List<String>>, Integer> nonPositiveNumKind = validatedMonad.of(-5);

    Kind<ValidatedKind.Witness<List<String>>, String> flatMappedToValid =
        validatedMonad.flatMap(intToValidatedStringKind, positiveNumKind);
    System.out.println("FlatMap (Valid to Valid): " + VALIDATED.narrow(flatMappedToValid));

    Kind<ValidatedKind.Witness<List<String>>, String> flatMappedToInvalid =
        validatedMonad.flatMap(intToValidatedStringKind, nonPositiveNumKind);
    System.out.println("FlatMap (Valid to Invalid): " + VALIDATED.narrow(flatMappedToInvalid));

    Kind<ValidatedKind.Witness<List<String>>, String> flatMappedFromInvalid =
        validatedMonad.flatMap(
            intToValidatedStringKind, invalidIntKind); // invalidIntKind has "Initial error for map"
    System.out.println("FlatMap (Invalid input): " + VALIDATED.narrow(flatMappedFromInvalid));

    // --- 4. Ap Operation (Applicative's ap) ---
    // 'ap' is from Applicative
    System.out.println("\n--- 4. Ap Operation ---");
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> validFnKind =
        VALIDATED.widen(Validated.valid(i -> "Applied: " + (i * 2)));
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> invalidFnKind =
        VALIDATED.widen(Validated.invalid(Collections.singletonList("Function is invalid")));

    Kind<ValidatedKind.Witness<List<String>>, Integer> validValueForAp = validatedMonad.of(25);
    Kind<ValidatedKind.Witness<List<String>>, Integer> invalidValueForAp =
        VALIDATED.invalid(Collections.singletonList("Value is invalid"));

    // Valid function, Valid value
    Kind<ValidatedKind.Witness<List<String>>, String> apValidFnValidVal =
        validatedMonad.ap(validFnKind, validValueForAp);
    System.out.println("Ap (ValidFn, ValidVal): " + VALIDATED.narrow(apValidFnValidVal));

    // Invalid function, Valid value
    Kind<ValidatedKind.Witness<List<String>>, String> apInvalidFnValidVal =
        validatedMonad.ap(invalidFnKind, validValueForAp);
    System.out.println("Ap (InvalidFn, ValidVal): " + VALIDATED.narrow(apInvalidFnValidVal));

    // Valid function, Invalid value
    Kind<ValidatedKind.Witness<List<String>>, String> apValidFnInvalidVal =
        validatedMonad.ap(validFnKind, invalidValueForAp);
    System.out.println("Ap (ValidFn, InvalidVal): " + VALIDATED.narrow(apValidFnInvalidVal));

    // Invalid function, Invalid value - errors are now accumulated
    Kind<ValidatedKind.Witness<List<String>>, String> apInvalidFnInvalidVal =
        validatedMonad.ap(invalidFnKind, invalidValueForAp);
    System.out.println(
        "Ap (InvalidFn, InvalidVal - errors accumulated): "
            + VALIDATED.narrow(apInvalidFnInvalidVal));

    // --- 5. Folding Validated (using Validated.fold()) ---
    System.out.println("\n--- 5. Folding Validated ---");
    Validated<List<String>, String> unwrappedValidFold = VALIDATED.narrow(mappedValid); // Renamed
    String foldResult1 =
        unwrappedValidFold.fold(
            errors -> "Folded Invalid: " + errors.toString(), value -> "Folded Valid: " + value);
    System.out.println("Fold on " + unwrappedValidFold + ": " + foldResult1);

    Validated<List<String>, String> unwrappedInvalidFold = VALIDATED.narrow(mappedInvalid);
    String foldResult2 =
        unwrappedInvalidFold.fold(
            errors -> "Folded Invalid: " + errors.toString(), value -> "Folded Valid: " + value);
    System.out.println("Fold on " + unwrappedInvalidFold + ": " + foldResult2);

    // --- 6. MonadError Operations ---
    System.out.println("\n--- 6. MonadError Operations ---");

    // Using monad.raiseError() to create an Invalid instance
    List<String> errorPayload = Collections.singletonList("Raised error condition");
    Kind<ValidatedKind.Witness<List<String>>, String> raisedError =
        validatedMonad.raiseError(errorPayload);
    System.out.println("From monad.raiseError(): " + VALIDATED.narrow(raisedError));

    // handleErrorWith: Invalid input, handler returns Valid
    Function<List<String>, Kind<ValidatedKind.Witness<List<String>>, String>>
        recoveryHandlerToValid =
            errors -> {
              System.out.println("handleErrorWith: Recovery handler called with errors: " + errors);
              return VALIDATED.valid("Recovered from " + errors.toString());
            };
    Kind<ValidatedKind.Witness<List<String>>, String> recoveredToValid =
        validatedMonad.handleErrorWith(raisedError, recoveryHandlerToValid);
    System.out.println("handleErrorWith (Invalid -> Valid): " + VALIDATED.narrow(recoveredToValid));

    // handleErrorWith: Invalid input, handler returns Invalid
    List<String> newErrorPayload = Collections.singletonList("New error after handling");
    Function<List<String>, Kind<ValidatedKind.Witness<List<String>>, String>>
        recoveryHandlerToInvalid =
            errors -> {
              System.out.println(
                  "handleErrorWith: New error handler called with errors: " + errors);
              return VALIDATED.invalid(newErrorPayload);
            };
    Kind<ValidatedKind.Witness<List<String>>, String> recoveredToInvalid =
        validatedMonad.handleErrorWith(raisedError, recoveryHandlerToInvalid);
    System.out.println(
        "handleErrorWith (Invalid -> Invalid): " + VALIDATED.narrow(recoveredToInvalid));

    // handleErrorWith: Valid input, handler not called
    Kind<ValidatedKind.Witness<List<String>>, String> initiallyValidForErrorHandling =
        validatedMonad.of("Already Valid");
    Kind<ValidatedKind.Witness<List<String>>, String> notRecovered =
        validatedMonad.handleErrorWith(initiallyValidForErrorHandling, recoveryHandlerToValid);
    System.out.println(
        "handleErrorWith (Valid input): "
            + VALIDATED.narrow(notRecovered)
            + " (handler should not have been called)");

    // handleError (default MonadError method): Invalid input, handler returns a plain value
    Kind<ValidatedKind.Witness<List<String>>, String> anotherRaisedError =
        validatedMonad.raiseError(Collections.singletonList("Error for handleError"));
    Function<List<String>, String> plainValueRecoveryHandler =
        errors -> "Plain value recovery: " + errors.toString();
    Kind<ValidatedKind.Witness<List<String>>, String> recoveredWithHandleError =
        validatedMonad.handleError(anotherRaisedError, plainValueRecoveryHandler);
    System.out.println(
        "handleError (Invalid -> plain value -> Valid): "
            + VALIDATED.narrow(recoveredWithHandleError));

    // --- Example: Combining operations for a simple validation scenario ---
    System.out.println("\n--- Combined Validation Scenario (Original) ---");
    Kind<ValidatedKind.Witness<List<String>>, String> userInput1 = validatedMonad.of("123");
    Kind<ValidatedKind.Witness<List<String>>, String> userInput2 =
        validatedMonad.of("abc"); // This will be Invalid

    Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseToIntKindMonadError =
        s -> {
          try {
            return validatedMonad.of(Integer.parseInt(s)); // Lifts to Valid
          } catch (NumberFormatException e) {
            // Using raiseError for semantic clarity
            return validatedMonad.raiseError(
                Collections.singletonList("'" + s + "' is not a number (via raiseError)."));
          }
        };

    Kind<ValidatedKind.Witness<List<String>>, Integer> parsed1 =
        validatedMonad.flatMap(parseToIntKindMonadError, userInput1);
    Kind<ValidatedKind.Witness<List<String>>, Integer> parsed2 =
        validatedMonad.flatMap(parseToIntKindMonadError, userInput2);

    System.out.println("Parsed Input 1 (Combined): " + VALIDATED.narrow(parsed1));
    System.out.println("Parsed Input 2 (Combined): " + VALIDATED.narrow(parsed2));

    // Example of recovering the parse of userInput2
    Kind<ValidatedKind.Witness<List<String>>, Integer> parsed2Recovered =
        validatedMonad.handleErrorWith(
            parsed2,
            errors -> {
              System.out.println("Combined scenario recovery: " + errors);
              return validatedMonad.of(0); // Default to 0 if parsing failed
            });
    System.out.println("Parsed Input 2 (Recovered to 0): " + VALIDATED.narrow(parsed2Recovered));

    Validated<List<String>, Integer> finalResult1 = VALIDATED.narrow(parsed1);
    finalResult1.ifValid(val -> System.out.println("Final valid number 1 (Combined): " + val));
    finalResult1.ifInvalid(err -> System.out.println("Final invalid number 1 (Combined): " + err));

    Validated<List<String>, Integer> finalResult2Recovered = VALIDATED.narrow(parsed2Recovered);
    finalResult2Recovered.ifValid(
        val -> System.out.println("Final valid number 2 (Combined/Recovered): " + val));
    // finalResult2Recovered.ifInvalid(...) will not be called here as it's recovered
  }
}
