// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Dispatched;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Pending;
import org.higherkindedj.example.book.optics.cast.ConsignmentState.Returned;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.optics.Prism;

/**
 * Cookbook recipes for conditional updates using Prisms.
 *
 * <p>Problem: Updating values only when they match certain criteria or types.
 *
 * <p>Solution: Use Prisms to focus on specific variants or matching values.
 *
 * <p>The sum type is the chapter's {@link ConsignmentState}, imported from its cast package with
 * the prisms generated for it.
 */
public class ConditionalUpdateRecipes {

  private static final Instant MORNING = Instant.parse("2026-10-02T09:00:00Z");

  private static final Instant AFTERNOON = Instant.parse("2026-10-02T15:00:00Z");

  public static void main(String[] args) {
    System.out.println("=== Conditional Update Recipes ===\n");

    recipeUpdateSpecificVariant();
    recipeFilterByType();
    recipeTypeChecking();
    recipeExclusionFiltering();
  }

  /**
   * Recipe: Update only a specific variant of a sum type.
   *
   * <p>Pattern: {@code prism.modify(function, source)} - no-op if doesn't match
   */
  private static void recipeUpdateSpecificVariant() {
    System.out.println("--- Recipe: Update Specific Variant ---");

    // Prism for returned consignments
    Prism<ConsignmentState, Returned> returnedPrism = ConsignmentStatePrisms.returned();

    ConsignmentState pending = new Pending();
    ConsignmentState returned = new Returned("damaged");
    ConsignmentState dispatched = new Dispatched(MORNING);

    // Update only Returned consignments - note the refund against the reason
    ConsignmentState updatedPending =
        returnedPrism.modify(r -> new Returned(r.reason() + ", refunded"), pending);
    ConsignmentState updatedReturned =
        returnedPrism.modify(r -> new Returned(r.reason() + ", refunded"), returned);
    ConsignmentState updatedDispatched =
        returnedPrism.modify(r -> new Returned(r.reason() + ", refunded"), dispatched);

    System.out.println("Pending (unchanged): " + updatedPending);
    System.out.println("Returned (refund noted): " + updatedReturned);
    System.out.println("Dispatched (unchanged): " + updatedDispatched);
    System.out.println();
  }

  /**
   * Recipe: Filter a collection by variant type.
   *
   * <p>Pattern: {@code stream.filter(prism::matches)}
   */
  private static void recipeFilterByType() {
    System.out.println("--- Recipe: Filter by Variant Type ---");

    Prism<ConsignmentState, Dispatched> dispatchedPrism = ConsignmentStatePrisms.dispatched();

    List<ConsignmentState> consignments =
        List.of(
            new Pending(),
            new Dispatched(MORNING),
            new Pending(),
            new Dispatched(AFTERNOON),
            new Returned("Wrong size"));

    // Get only dispatched consignments
    List<ConsignmentState> dispatched =
        consignments.stream().filter(dispatchedPrism::matches).collect(Collectors.toList());

    System.out.println("All consignments: " + consignments.size());
    System.out.println("Dispatched consignments: " + dispatched);
    System.out.println();
  }

  /**
   * Recipe: Type checking with matches().
   *
   * <p>Pattern: {@code if (prism.matches(value))}
   */
  private static void recipeTypeChecking() {
    System.out.println("--- Recipe: Type Checking ---");

    Prism<ConsignmentState, Dispatched> dispatchedPrism = ConsignmentStatePrisms.dispatched();

    ConsignmentState consignment1 = new Pending();
    ConsignmentState consignment2 = new Dispatched(AFTERNOON);

    // Check if consignments are dispatched
    if (dispatchedPrism.matches(consignment1)) {
      System.out.println("Consignment 1 is dispatched");
    } else {
      System.out.println("Consignment 1 is not dispatched yet");
    }

    if (dispatchedPrism.matches(consignment2)) {
      // Safe to extract since we checked
      Dispatched dispatched = dispatchedPrism.getOptional(consignment2).orElseThrow();
      System.out.println("Consignment 2 dispatched at: " + dispatched.at());
    }
    System.out.println();
  }

  /**
   * Recipe: Exclusion filtering with doesNotMatch().
   *
   * <p>Pattern: {@code stream.filter(prism::doesNotMatch)}
   */
  private static void recipeExclusionFiltering() {
    System.out.println("--- Recipe: Exclusion Filtering ---");

    Prism<ConsignmentState, Returned> returnedPrism = ConsignmentStatePrisms.returned();

    List<ConsignmentState> consignments =
        List.of(
            new Pending(),
            new Returned("Customer request"),
            new Dispatched(MORNING),
            new Returned("Wrong size"),
            new Dispatched(AFTERNOON));

    // Get all consignments that have not come back
    List<ConsignmentState> active =
        consignments.stream().filter(returnedPrism::doesNotMatch).collect(Collectors.toList());

    System.out.println("Total consignments: " + consignments.size());
    System.out.println("Active (not returned) consignments: " + active.size());
    active.forEach(c -> System.out.println("  " + c));
    System.out.println();
  }
}
