// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.cookbook;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
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
 *   <li>Lens + Prism = Affine
 *   <li>Prism + Lens = Affine
 *   <li>Lens + Traversal = Traversal
 *   <li>Prism + Prism = Prism
 * </ul>
 */
public class CompositionRecipes {

  // --- Domain Model ---
  @GenerateLenses
  public record Drawer(String id, Optional<Content> content) {}

  @GenerateLenses
  public record Content(String data, int priority) {}

  @GeneratePrisms
  public sealed interface FetchResult permits Fetched, Failure {}

  @GenerateLenses
  public record Fetched(String value, Metadata meta) implements FetchResult {}

  @GenerateLenses
  public record Metadata(String source, long timestamp) {}

  public record Failure(String error) implements FetchResult {}

  @GenerateLenses
  public record FetchBatch(String batchId, List<FetchResult> results) {}

  public static void main(String[] args) {
    System.out.println("=== Composition Recipes ===\n");

    recipeLensPrismComposition();
    recipePrismLensComposition();
    recipeOptionalFieldAccess();
    recipeComplexComposition();
    recipeCombiningMultipleExtractionPaths();
    recipeTraversalToFold();
  }

  /**
   * Recipe: Lens + Prism = Affine.
   *
   * <p>Pattern: Navigate from a product type through an optional/sum type field.
   */
  private static void recipeLensPrismComposition() {
    System.out.println("--- Recipe: Lens + Prism = Affine ---");

    // Drawer has an Optional<Content> field
    Lens<Drawer, Optional<Content>> contentLens =
        Lens.of(Drawer::content, (c, opt) -> new Drawer(c.id(), opt));

    // Prism to unwrap Optional
    Prism<Optional<Content>, Content> somePrism = Prisms.some();

    // Lens + Prism = Affine
    Affine<Drawer, Content> contentAffine = contentLens.andThen(somePrism);

    Drawer withContent = new Drawer("C1", Optional.of(new Content("data", 5)));
    Drawer empty = new Drawer("C2", Optional.empty());

    // Get content (may be empty)
    Optional<Content> found = contentAffine.getOptional(withContent);
    Optional<Content> notFound = contentAffine.getOptional(empty);

    System.out.println("Drawer with content: " + found);
    System.out.println("Empty drawer: " + notFound);

    // Modify content (no-op on empty)
    Drawer modified =
        contentAffine.modify(c -> new Content(c.data().toUpperCase(), c.priority()), withContent);

    System.out.println("Modified: " + modified);
    System.out.println();
  }

  /**
   * Recipe: Prism + Lens = Affine.
   *
   * <p>Pattern: Focus on a sum type variant, then access its fields.
   */
  private static void recipePrismLensComposition() {
    System.out.println("--- Recipe: Prism + Lens = Affine ---");

    // Prism for Fetched variant
    Prism<FetchResult, Fetched> successPrism =
        Prism.of(r -> r instanceof Fetched s ? Optional.of(s) : Optional.empty(), s -> s);

    // Lens to Fetched's value
    Lens<Fetched, String> valueLens = Lens.of(Fetched::value, (s, v) -> new Fetched(v, s.meta()));

    // Prism + Lens = Affine
    Affine<FetchResult, String> successValueAffine = successPrism.andThen(valueLens);

    FetchResult success = new Fetched("result", new Metadata("api", System.currentTimeMillis()));
    FetchResult failure = new Failure("error");

    // Get value from Fetched (empty from Failure)
    Optional<String> successValue = successValueAffine.getOptional(success);
    Optional<String> failureValue = successValueAffine.getOptional(failure);

    System.out.println("From Success: " + successValue);
    System.out.println("From Failure: " + failureValue);

    // Uppercase only Fetched values
    FetchResult modifiedSuccess = successValueAffine.modify(String::toUpperCase, success);
    FetchResult unchangedFailure = successValueAffine.modify(String::toUpperCase, failure);

    System.out.println("Modified Success: " + modifiedSuccess);
    System.out.println("Unchanged Failure: " + unchangedFailure);
    System.out.println();
  }

  /**
   * Recipe: Access deeply nested optional fields.
   *
   * <p>Pattern: Chain Lens + Prism + Lens (Affine + Lens = Affine).
   */
  private static void recipeOptionalFieldAccess() {
    System.out.println("--- Recipe: Access Nested Optional Fields ---");

    // Build path: Drawer -> Optional<Content> -> Content -> priority
    Lens<Drawer, Optional<Content>> contentLens =
        Lens.of(Drawer::content, (c, opt) -> new Drawer(c.id(), opt));

    Prism<Optional<Content>, Content> somePrism = Prisms.some();

    Lens<Content, Integer> priorityLens =
        Lens.of(Content::priority, (c, p) -> new Content(c.data(), p));

    // After Lens+Prism=Affine, chain with Lens to get another Affine
    Affine<Drawer, Integer> priorityAffine = contentLens.andThen(somePrism).andThen(priorityLens);

    Drawer drawer = new Drawer("C1", Optional.of(new Content("important", 5)));

    // Get priority
    Optional<Integer> priority = priorityAffine.getOptional(drawer);
    System.out.println("Priority: " + priority);

    // Increase priority
    Drawer updated = priorityAffine.modify(p -> p + 1, drawer);
    System.out.println("After priority increase: " + updated);
    System.out.println();
  }

  /**
   * Recipe: Complex composition with lists and variants.
   *
   * <p>Pattern: FetchBatch -> List<FetchResult> -> (Fetched variant) -> value
   */
  private static void recipeComplexComposition() {
    System.out.println("--- Recipe: Complex Composition ---");

    // Lenses
    Lens<FetchBatch, List<FetchResult>> resultsLens =
        Lens.of(FetchBatch::results, (b, results) -> new FetchBatch(b.batchId(), results));

    // Prism for Fetched
    Prism<FetchResult, Fetched> successPrism =
        Prism.of(r -> r instanceof Fetched s ? Optional.of(s) : Optional.empty(), s -> s);

    Lens<Fetched, String> valueLens = Lens.of(Fetched::value, (s, v) -> new Fetched(v, s.meta()));

    // Build from inside out: Prism + Lens = Affine, then Traversal + Affine = Traversal
    Affine<FetchResult, String> resultToValueAffine = successPrism.andThen(valueLens);
    Traversal<List<FetchResult>, String> listToValues =
        Traversals.<FetchResult>forList().andThen(resultToValueAffine);
    Traversal<FetchBatch, String> allSuccessValues = resultsLens.andThen(listToValues);

    FetchBatch batch =
        new FetchBatch(
            "B1",
            List.of(
                new Fetched("result1", new Metadata("api", 1L)),
                new Failure("error1"),
                new Fetched("result2", new Metadata("db", 2L)),
                new Failure("error2"),
                new Fetched("result3", new Metadata("cache", 3L))));

    // Get all success values
    List<String> values = Traversals.getAll(allSuccessValues, batch);
    System.out.println("All success values: " + values);

    // Uppercase all success values (failures unchanged)
    FetchBatch updated = Traversals.modify(allSuccessValues, String::toUpperCase, batch);
    List<String> newValues = Traversals.getAll(allSuccessValues, updated);
    System.out.println("After uppercase: " + newValues);

    // Verify failures are unchanged
    System.out.println("Updated batch results: " + updated.results());
    System.out.println();
  }

  /**
   * Recipe: Combining Multiple Extraction Paths with Fold.plus().
   *
   * <p>Pattern: Extract values from different branches of a data structure and combine them into a
   * single result set.
   */
  private static void recipeCombiningMultipleExtractionPaths() {
    System.out.println("--- Recipe: Combining Multiple Extraction Paths ---");

    // Two different ways to get a string value from a FetchResult
    Prism<FetchResult, Fetched> successPrism =
        Prism.of(r -> r instanceof Fetched s ? Optional.of(s) : Optional.empty(), s -> s);

    Prism<FetchResult, Failure> failurePrism =
        Prism.of(r -> r instanceof Failure f ? Optional.of(f) : Optional.empty(), f -> f);

    // Extract success values
    Fold<FetchResult, String> successValues =
        successPrism.asFold().andThen(Fold.of(s -> List.of(s.value())));

    // Extract failure messages
    Fold<FetchResult, String> failureMessages =
        failurePrism.asFold().andThen(Fold.of(f -> List.of(f.error())));

    // Combine: get ALL text from a FetchResult regardless of variant
    Fold<FetchResult, String> allText = successValues.plus(failureMessages);

    FetchResult success = new Fetched("ok", new Metadata("api", 1L));
    FetchResult failure = new Failure("not found");

    System.out.println("Success text: " + allText.getAll(success));
    System.out.println("Failure text: " + allText.getAll(failure));

    // Use with a batch via Fold.sum()
    Fold<FetchBatch, String> batchSuccesses =
        Fold.<FetchBatch, FetchResult>of(FetchBatch::results).andThen(successValues);
    Fold<FetchBatch, String> batchFailures =
        Fold.<FetchBatch, FetchResult>of(FetchBatch::results).andThen(failureMessages);

    Fold<FetchBatch, String> batchAllText = Fold.sum(batchSuccesses, batchFailures);

    FetchBatch batch =
        new FetchBatch(
            "B1",
            List.of(
                new Fetched("result1", new Metadata("api", 1L)),
                new Failure("error1"),
                new Fetched("result2", new Metadata("db", 2L))));

    System.out.println("All batch text: " + batchAllText.getAll(batch));
    System.out.println("Total text items: " + batchAllText.length(batch));
    System.out.println();
  }

  /**
   * Recipe: Traversal.asFold() for Read-Only Queries and Combination.
   *
   * <p>Pattern: Use Traversal for modifications, convert to Fold via asFold() for aggregation
   * queries. The resulting Fold can be combined with other Folds via plus()/sum().
   */
  private static void recipeTraversalToFold() {
    System.out.println("--- Recipe: Traversal.asFold() for Queries ---");

    // Prism for Fetched variant
    Prism<FetchResult, Fetched> successPrism =
        Prism.of(r -> r instanceof Fetched s ? Optional.of(s) : Optional.empty(), s -> s);

    Lens<Fetched, String> valueLens = Lens.of(Fetched::value, (s, v) -> new Fetched(v, s.meta()));
    Lens<Fetched, Metadata> metaLens = Lens.of(Fetched::meta, (s, m) -> new Fetched(s.value(), m));
    Lens<Metadata, String> sourceLens =
        Lens.of(Metadata::source, (m, src) -> new Metadata(src, m.timestamp()));

    // Build a Traversal for all success values in a FetchBatch
    Lens<FetchBatch, List<FetchResult>> resultsLens =
        Lens.of(FetchBatch::results, (b, results) -> new FetchBatch(b.batchId(), results));

    Affine<FetchResult, String> resultToValue = successPrism.andThen(valueLens);
    Traversal<FetchBatch, String> allSuccessValues =
        resultsLens.andThen(Traversals.<FetchResult>forList().andThen(resultToValue));

    // Convert to Fold for read-only aggregation
    Fold<FetchBatch, String> successValuesFold = allSuccessValues.asFold();

    FetchBatch batch =
        new FetchBatch(
            "B1",
            List.of(
                new Fetched("alpha", new Metadata("api", 1L)),
                new Failure("error1"),
                new Fetched("beta", new Metadata("db", 2L)),
                new Fetched("gamma", new Metadata("cache", 3L))));

    System.out.println("Success values: " + successValuesFold.getAll(batch));
    System.out.println("Count: " + successValuesFold.length(batch));
    System.out.println("Has 'beta': " + successValuesFold.exists(v -> v.equals("beta"), batch));

    // Combine traversal-derived fold with another fold via plus()
    Affine<FetchResult, String> resultToSource = successPrism.andThen(metaLens).andThen(sourceLens);
    Fold<FetchBatch, String> sourcesFold =
        resultsLens.andThen(Traversals.<FetchResult>forList().andThen(resultToSource)).asFold();

    Fold<FetchBatch, String> allStringsFold = successValuesFold.plus(sourcesFold);
    System.out.println("All strings (values + sources): " + allStringsFold.getAll(batch));

    // foldMap with a monoid on the traversal-derived fold
    String concatenated = successValuesFold.foldMap(Monoids.string(), s -> s + " ", batch);
    System.out.println("Concatenated values: " + concatenated.trim());
    System.out.println();
  }
}
