// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * Cookbook recipes for cross-optic composition patterns.
 *
 * <p>Problem: Navigating complex data structures that mix product types, sum types, and
 * collections.
 *
 * <p>Solution: Compose optics following the composition rules:
 *
 * <ul>
 *   <li>Lens + Lens = Lens
 *   <li>Lens + Prism = Traversal
 *   <li>Prism + Lens = Traversal
 *   <li>Lens + Traversal = Traversal
 *   <li>Prism + Prism = Prism
 * </ul>
 */
public class CompositionRecipes {

  // --- Domain Model ---
  @GenerateLenses
  public record Container(String id, Optional<Content> content) {}

  @GenerateLenses
  public record Content(String data, int priority) {}

  @GeneratePrisms
  public sealed interface Result permits Success, Failure {}

  @GenerateLenses
  public record Success(String value, Metadata meta) implements Result {}

  @GenerateLenses
  public record Metadata(String source, long timestamp) {}

  public record Failure(String error) implements Result {}

  @GenerateLenses
  public record Batch(String batchId, List<Result> results) {}

  public static void main(String[] args) {
    System.out.println("=== Composition Recipes ===\n");

    recipeLensPrismComposition();
    recipePrismLensComposition();
    recipeOptionalFieldAccess();
    recipeComplexComposition();
  }

  /**
   * Recipe: Lens + Prism = Traversal.
   *
   * <p>Pattern: Navigate from a product type through an optional/sum type field.
   */
  private static void recipeLensPrismComposition() {
    System.out.println("--- Recipe: Lens + Prism = Traversal ---");

    // Container has an Optional<Content> field
    Lens<Container, Optional<Content>> contentLens =
        Lens.of(Container::content, (c, opt) -> new Container(c.id(), opt));

    // Prism to unwrap Optional
    Prism<Optional<Content>, Content> somePrism = Prisms.some();

    // Lens + Prism = Traversal
    Traversal<Container, Content> contentTraversal = contentLens.andThen(somePrism);

    Container withContent = new Container("C1", Optional.of(new Content("data", 5)));
    Container empty = new Container("C2", Optional.empty());

    // Get content (may be empty)
    List<Content> found = Traversals.getAll(contentTraversal, withContent);
    List<Content> notFound = Traversals.getAll(contentTraversal, empty);

    System.out.println("Container with content: " + found);
    System.out.println("Empty container: " + notFound);

    // Modify content (no-op on empty)
    Container modified =
        Traversals.modify(
            contentTraversal, c -> new Content(c.data().toUpperCase(), c.priority()), withContent);

    System.out.println("Modified: " + modified);
    System.out.println();
  }

  /**
   * Recipe: Prism + Lens = Traversal.
   *
   * <p>Pattern: Focus on a sum type variant, then access its fields.
   */
  private static void recipePrismLensComposition() {
    System.out.println("--- Recipe: Prism + Lens = Traversal ---");

    // Prism for Success variant
    Prism<Result, Success> successPrism =
        Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);

    // Lens to Success's value
    Lens<Success, String> valueLens = Lens.of(Success::value, (s, v) -> new Success(v, s.meta()));

    // Prism + Lens = Traversal
    Traversal<Result, String> successValueTraversal = successPrism.andThen(valueLens);

    Result success = new Success("result", new Metadata("api", System.currentTimeMillis()));
    Result failure = new Failure("error");

    // Get value from Success (empty list from Failure)
    List<String> successValue = Traversals.getAll(successValueTraversal, success);
    List<String> failureValue = Traversals.getAll(successValueTraversal, failure);

    System.out.println("From Success: " + successValue);
    System.out.println("From Failure: " + failureValue);

    // Uppercase only Success values
    Result modifiedSuccess = Traversals.modify(successValueTraversal, String::toUpperCase, success);
    Result unchangedFailure =
        Traversals.modify(successValueTraversal, String::toUpperCase, failure);

    System.out.println("Modified Success: " + modifiedSuccess);
    System.out.println("Unchanged Failure: " + unchangedFailure);
    System.out.println();
  }

  /**
   * Recipe: Access deeply nested optional fields.
   *
   * <p>Pattern: Chain Lens + Prism + Lens (use .asTraversal() for final Lens).
   */
  private static void recipeOptionalFieldAccess() {
    System.out.println("--- Recipe: Access Nested Optional Fields ---");

    // Build path: Container -> Optional<Content> -> Content -> priority
    Lens<Container, Optional<Content>> contentLens =
        Lens.of(Container::content, (c, opt) -> new Container(c.id(), opt));

    Prism<Optional<Content>, Content> somePrism = Prisms.some();

    Lens<Content, Integer> priorityLens =
        Lens.of(Content::priority, (c, p) -> new Content(c.data(), p));

    // After Lens+Prism=Traversal, chain with Lens.asTraversal()
    Traversal<Container, Integer> priorityTraversal =
        contentLens.andThen(somePrism).andThen(priorityLens.asTraversal());

    Container container = new Container("C1", Optional.of(new Content("important", 5)));

    // Get priority
    List<Integer> priorities = Traversals.getAll(priorityTraversal, container);
    System.out.println("Priorities: " + priorities);

    // Increase priority
    Container updated = Traversals.modify(priorityTraversal, p -> p + 1, container);
    System.out.println("After priority increase: " + updated);
    System.out.println();
  }

  /**
   * Recipe: Complex composition with lists and variants.
   *
   * <p>Pattern: Batch -> List<Result> -> (Success variant) -> value
   */
  private static void recipeComplexComposition() {
    System.out.println("--- Recipe: Complex Composition ---");

    // Lenses
    Lens<Batch, List<Result>> resultsLens =
        Lens.of(Batch::results, (b, results) -> new Batch(b.batchId(), results));

    // Prism for Success
    Prism<Result, Success> successPrism =
        Prism.of(r -> r instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);

    Lens<Success, String> valueLens = Lens.of(Success::value, (s, v) -> new Success(v, s.meta()));

    // Build from inside out: Prism + Lens = Traversal, then compose all Traversals
    // (Prism.andThen(Lens) = Traversal, Traversal.andThen(Traversal) = Traversal)
    Traversal<Result, String> resultToValue = successPrism.andThen(valueLens);
    Traversal<List<Result>, String> listToValues =
        Traversals.<Result>forList().andThen(resultToValue);
    Traversal<Batch, String> allSuccessValues = resultsLens.asTraversal().andThen(listToValues);

    Batch batch =
        new Batch(
            "B1",
            List.of(
                new Success("result1", new Metadata("api", 1L)),
                new Failure("error1"),
                new Success("result2", new Metadata("db", 2L)),
                new Failure("error2"),
                new Success("result3", new Metadata("cache", 3L))));

    // Get all success values
    List<String> values = Traversals.getAll(allSuccessValues, batch);
    System.out.println("All success values: " + values);

    // Uppercase all success values (failures unchanged)
    Batch updated = Traversals.modify(allSuccessValues, String::toUpperCase, batch);
    List<String> newValues = Traversals.getAll(allSuccessValues, updated);
    System.out.println("After uppercase: " + newValues);

    // Verify failures are unchanged
    System.out.println("Updated batch results: " + updated.results());
    System.out.println();
  }
}
