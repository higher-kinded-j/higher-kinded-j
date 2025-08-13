// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.traversal.array;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Arrays;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** A runnable example demonstrating how to use a generated Traversal for a field of type Array. */
public class ArrayTraversalExample {

  /**
   * An immutable Survey record containing an array of answers. {@code @GenerateTraversals} will
   * generate a {@code SurveyTraversals} class.
   */
  @GenerateTraversals
  public record Survey(String id, Integer[] answers) {}

  /** An "effectful" function to validate a single survey answer. It must be between 1 and 5. */
  public static Kind<ValidatedKind.Witness<String>, Integer> validateAnswer(Integer answer) {
    if (answer >= 1 && answer <= 5) {
      return VALIDATED.widen(Validated.valid(answer));
    } else {
      return VALIDATED.widen(Validated.invalid("Answer '" + answer + "' is out of range (1-5)"));
    }
  }

  public static void main(String[] args) {
    // 1. Setup: Define a Semigroup for combining String errors and get the Applicative.
    final Semigroup<String> stringSemigroup = Semigroups.string("; ");
    Applicative<ValidatedKind.Witness<String>> validatedApplicative =
        ValidatedMonad.instance(stringSemigroup);

    // 2. Get the generated traversal for the 'answers' field.
    var answersTraversal = SurveyTraversals.answers();

    System.out.println("--- Running Traversal Scenarios for Array ---");

    // --- Scenario 1: A survey with all valid answers ---
    var surveyAllValid = new Survey("S-001", new Integer[] {1, 5, 3, 4, 2});
    System.out.println(
        "\nInput: " + surveyAllValid.id() + " " + Arrays.toString(surveyAllValid.answers()));

    // Use the traversal to validate every answer in the array.
    var result1 =
        answersTraversal.modifyF(
            ArrayTraversalExample::validateAnswer, surveyAllValid, validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result1));
    // Expected: Valid(Survey[id=S-001, answers=[1, 5, 3, 4, 2]])

    // --- Scenario 2: A survey with one invalid answer ---
    var surveyInvalid = new Survey("S-002", new Integer[] {2, 4, 6, 3});
    System.out.println(
        "\nInput: " + surveyInvalid.id() + " " + Arrays.toString(surveyInvalid.answers()));

    // The traversal will fail on the invalid answer (6).
    var result2 =
        answersTraversal.modifyF(
            ArrayTraversalExample::validateAnswer, surveyInvalid, validatedApplicative);

    System.out.println("Result: " + VALIDATED.narrow(result2));
    // Expected: Invalid(Answer '6' is out of range (1-5))

    // --- Scenario 3: A survey with multiple invalid answers ---
    var surveyMultipleInvalid = new Survey("S-003", new Integer[] {0, 4, 9, 2});
    System.out.println(
        "\nInput: "
            + surveyMultipleInvalid.id()
            + " "
            + Arrays.toString(surveyMultipleInvalid.answers()));

    // The traversal will accumulate all errors.
    var result3 =
        answersTraversal.modifyF(
            ArrayTraversalExample::validateAnswer, surveyMultipleInvalid, validatedApplicative);

    System.out.println("Result (errors accumulated): " + VALIDATED.narrow(result3));
    // Expected: Invalid(Answer '0' is out of range (1-5); Answer '9' is out of range (1-5))
  }
}
