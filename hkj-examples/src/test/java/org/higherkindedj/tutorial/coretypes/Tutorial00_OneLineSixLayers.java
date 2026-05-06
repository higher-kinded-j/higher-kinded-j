// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 00: One Line, Six Layers — The Whole Stack in a Single Expression.
 *
 * <p>This is the chapter anchor and the setup check. By the time we are done with this file we will
 * have:
 *
 * <ul>
 *   <li>Confirmed our build is configured correctly (annotation processing, Java 25, the Effect
 *       Path API on the classpath).
 *   <li>Touched every layer of the library that the rest of the chapter teaches.
 *   <li>Built up the same expression that the {@code One Line, Six Layers} page in the Foundations
 *       chapter unpacks token by token.
 * </ul>
 *
 * <p>Pain → Promise. Here is the kind of imperative Java that motivates this library:
 *
 * <pre>
 *   Item item = repo.find(id);                   // null check
 *   if (item == null) {
 *     throw new NotFoundException(id);
 *   }
 *   Map&lt;String,String&gt; attrs =
 *       new HashMap&lt;&gt;(item.attributes());     // defensive copy
 *   attrs.put(key, spec.validateAndCoerce(...)); // mutation
 *   Item updated = new Item(item.id(), attrs);   // copy constructor
 *   try {
 *     repo.save(updated);
 *   } catch (RepoException e) {
 *     // map back to a domain error somehow
 *   }
 * </pre>
 *
 * <p>The Higher-Kinded-J version of the same workflow is one expression. Each token in that
 * expression is a different layer of the library:
 *
 * <pre>
 *   find(id)                  // 1. Effect type   (MaybePath&lt;Item&gt;)
 *     .toEitherPath(NOT_FOUND) // 2. Natural transformation (MaybePath ~&gt; EitherPath)
 *     .map(item -&gt;             // 3. Optic         (a Lens onto attributes)
 *         attributesLens.modify(  // 4. Functor      (map applied through the optic)
 *             addEntry(key, value),
 *             item))
 *     .via(this::save)          // 5. Monad         (flatMap chains the dependent save)
 *     .run();                   // 6. Dispatch      (run the EitherPath to get the Either out)
 * </pre>
 *
 * <p>Java idiom anchor. Most Java developers have written the imperative form above many times.
 * Each layer here replaces one specific pain point:
 *
 * <ul>
 *   <li>Layer 1 replaces the null check (and {@code Optional<T>} when we want errors typed).
 *   <li>Layer 2 replaces the manual "map this Optional empty to that exception" boilerplate.
 *   <li>Layers 3 and 4 replace the defensive copy + mutation + copy-constructor dance.
 *   <li>Layer 5 replaces the try/catch ladder and the chained CompletableFuture.thenCompose calls.
 *   <li>Layer 6 is the only place a side effect happens; everything before it is pure.
 * </ul>
 *
 * <p>The exercises below ask us to wire up each layer in turn. Replace each {@code
 * answerRequired()} with the working code; the tests will go green one by one.
 */
@DisplayName("Tutorial 00: One Line, Six Layers")
public class Tutorial00_OneLineSixLayers {

  /** Helper for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // ─── Tiny domain ──────────────────────────────────────────────────────────

  /** A node in our pretend repository, identified by id and carrying a bag of string attributes. */
  record Item(String id, Map<String, String> attributes) {}

  /** A domain error type, deliberately small. */
  enum RepoError {
    NOT_FOUND,
    INVALID
  }

  /** Lens onto the attributes map of an Item — manually defined for clarity in this anchor. */
  static final Lens<Item, Map<String, String>> attributesLens =
      Lens.of(Item::attributes, (item, attrs) -> new Item(item.id(), attrs));

  /** Adds an entry to a copy of the given map, preserving immutability. */
  static Function<Map<String, String>, Map<String, String>> addEntry(String key, String value) {
    return original -> {
      Map<String, String> copy = new LinkedHashMap<>(original);
      copy.put(key, value);
      return copy;
    };
  }

  // ─── Pretend repository ────────────────────────────────────────────────────

  /** A non-empty initial state so {@code find("user-1")} returns a present value. */
  private static Item makeUser1() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put("name", "Alice");
    attrs.put("country", "US");
    return new Item("user-1", Map.copyOf(attrs));
  }

  /** Simulated repository read. {@code "user-1"} is present; everything else is absent. */
  static MaybePath<Item> find(String id) {
    if ("user-1".equals(id)) {
      return Path.just(makeUser1());
    }
    return Path.nothing();
  }

  /** Simulated repository write. Rejects items whose id is blank, otherwise echoes them back. */
  static EitherPath<RepoError, Item> save(Item item) {
    if (item.id() == null || item.id().isBlank()) {
      return Path.left(RepoError.INVALID);
    }
    return Path.right(item);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 0: Setup check
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 0: Setup check.
   *
   * <p>This test does not call {@code answerRequired()}. It simply asserts that the Effect Path
   * API, the Optics API, and the JUnit + AssertJ runtime are all on the classpath and reachable.
   *
   * <p>If this test goes green, the rest of the chapter will work. If it fails to compile, see <a
   * href="../../../../../../../../../hkj-book/src/tutorials/troubleshooting.md">Troubleshooting</a>.
   */
  @Test
  @DisplayName("Exercise 0: setup check (no code to write)")
  void exercise0_setupCheck() {
    MaybePath<Integer> sanity = Path.just(42);
    assertThat(sanity.getOrElse(-1)).isEqualTo(42);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 1 — Layer 1: Effect type
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 1: Layer 1 — Effect type.
   *
   * <p>The repository read returns a {@link MaybePath}, which encodes "a value that may or may not
   * be present" without using {@code null}. Find {@code "user-1"} and confirm the returned path
   * carries a value.
   *
   * <pre>
   *   // Nudge:    What does {@link #find(String)} return for a known id?
   *   // Strategy: MaybePath has a getOrElse method.
   *   // Spoiler:  find("user-1").getOrElse(...)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 1 — Layer 1 (Effect): find returns MaybePath")
  void exercise1_effectType() {
    MaybePath<Item> path = find("user-1");

    // TODO: replace answerRequired() with code that extracts the Item from path,
    //       or returns a sentinel Item if the path is empty.
    Item item = answerRequired();

    assertThat(item.id()).isEqualTo("user-1");
    assertThat(item.attributes()).containsEntry("name", "Alice");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 2 — Layer 2: Natural transformation
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 2: Layer 2 — Natural transformation.
   *
   * <p>{@link MaybePath} encodes absence with {@code Nothing}; {@link EitherPath} encodes a typed
   * error. The bridge between them is a natural transformation: every {@code Nothing} becomes a
   * {@code Left(error)} of our choosing, and every {@code Just} becomes a {@code Right}.
   *
   * <pre>
   *   // Nudge:    MaybePath has a method that adds the missing error type.
   *   // Strategy: It takes the value to use as the Left when the path is empty.
   *   // Spoiler:  maybePath.toEitherPath(RepoError.NOT_FOUND)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 2 — Layer 2 (Natural transformation): MaybePath → EitherPath")
  void exercise2_naturalTransformation() {
    MaybePath<Item> presentMaybe = find("user-1");
    MaybePath<Item> absentMaybe = find("user-missing");

    // TODO: replace both answerRequired() calls with the same toEitherPath(...) call.
    EitherPath<RepoError, Item> presentEither = answerRequired();
    EitherPath<RepoError, Item> absentEither = answerRequired();

    assertThat(presentEither.run().isRight()).isTrue();
    assertThat(absentEither.run().isLeft()).isTrue();
    assertThat(absentEither.run().getLeft()).isEqualTo(RepoError.NOT_FOUND);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 3 — Layer 3: Optic
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 3: Layer 3 — Optic.
   *
   * <p>An optic is a first-class getter/setter we can pass around. Use the pre-defined {@link
   * #attributesLens} to update the {@code attributes} map of an {@link Item} without touching the
   * rest of the record.
   *
   * <pre>
   *   // Nudge:    Lens has a modify method.
   *   // Strategy: modify(transform, source) returns a new source with the focused field updated.
   *   // Spoiler:  attributesLens.modify(addEntry("country", "GB"), item)
   * </pre>
   */
  @Test
  @DisplayName("Exercise 3 — Layer 3 (Optic): a Lens onto attributes")
  void exercise3_opticModify() {
    Item original = makeUser1();

    // TODO: replace answerRequired() with attributesLens.modify(...)
    //       Use addEntry("country", "GB") as the modifier.
    Item updated = answerRequired();

    assertThat(updated.id()).isEqualTo("user-1");
    assertThat(updated.attributes()).containsEntry("country", "GB");
    assertThat(original.attributes()).containsEntry("country", "US"); // immutability
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 4 — Layer 4: Functor (map through the effect)
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 4: Layer 4 — Functor.
   *
   * <p>{@code map} is the Functor capability: transform the success value of an {@link EitherPath}
   * while leaving the effect structure (and any error) untouched. Combine with the optic from the
   * previous exercise.
   *
   * <pre>
   *   // Nudge:    EitherPath has a map method that takes a Function.
   *   // Strategy: The Function should turn an Item into a new Item.
   *   // Spoiler:  either.map(item -> attributesLens.modify(addEntry("country", "GB"), item))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 4 — Layer 4 (Functor): map a transformation through the EitherPath")
  void exercise4_functorMap() {
    EitherPath<RepoError, Item> wrapped = find("user-1").toEitherPath(RepoError.NOT_FOUND);

    // TODO: replace answerRequired() with wrapped.map(...) using attributesLens.modify
    EitherPath<RepoError, Item> updated = answerRequired();

    Either<RepoError, Item> result = updated.run();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().attributes()).containsEntry("country", "GB");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 5 — Layer 5: Monad (flatMap / via)
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 5: Layer 5 — Monad.
   *
   * <p>{@code via} is the Monad capability: chain a step that itself returns an effect. Here the
   * dependent step is {@link #save(Item)}, which itself returns an {@link EitherPath} and may fail.
   * If save fails, the chain short-circuits with that error.
   *
   * <pre>
   *   // Nudge:    EitherPath has a via method (flatMap by another name).
   *   // Strategy: The function we pass returns the next EitherPath in the chain.
   *   // Spoiler:  either.via(item -> save(item))
   * </pre>
   */
  @Test
  @DisplayName("Exercise 5 — Layer 5 (Monad): chain save via flatMap")
  void exercise5_monadVia() {
    EitherPath<RepoError, Item> wrapped =
        find("user-1")
            .toEitherPath(RepoError.NOT_FOUND)
            .map(item -> attributesLens.modify(addEntry("country", "GB"), item));

    // TODO: replace answerRequired() with wrapped.via(item -> save(item)) (or method reference)
    EitherPath<RepoError, Item> saved = answerRequired();

    Either<RepoError, Item> result = saved.run();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().attributes()).containsEntry("country", "GB");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 6 — Layer 6: Dispatch (run)
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 6: Layer 6 — Dispatch.
   *
   * <p>An {@link EitherPath} is a description of a computation. Until we call {@code run()},
   * nothing has been "extracted". This is where the typeclass instance for {@code Either} is
   * dispatched and the underlying {@link Either} value is returned.
   *
   * <pre>
   *   // Nudge:    EitherPath has a run() method.
   *   // Strategy: run() returns Either&lt;E, A&gt;.
   *   // Spoiler:  saved.run()
   * </pre>
   */
  @Test
  @DisplayName("Exercise 6 — Layer 6 (Dispatch): run the EitherPath to get the Either")
  void exercise6_dispatch() {
    EitherPath<RepoError, Item> saved =
        find("user-1")
            .toEitherPath(RepoError.NOT_FOUND)
            .map(item -> attributesLens.modify(addEntry("country", "GB"), item))
            .via(Tutorial00_OneLineSixLayers::save);

    // TODO: replace answerRequired() with saved.run()
    Either<RepoError, Item> either = answerRequired();

    assertThat(either.isRight()).isTrue();
    assertThat(either.getRight().attributes()).containsEntry("country", "GB");
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Exercise 7 — Putting it all together
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Exercise 7: Assemble the whole expression.
   *
   * <p>One expression. Six layers. The shape we will see again and again across the rest of this
   * chapter. The success path returns the updated item; the missing-id path returns {@link
   * RepoError#NOT_FOUND}.
   *
   * <pre>
   *   // Nudge:    Combine exercises 1 through 6 into one fluent chain.
   *   // Strategy: find(...).toEitherPath(...).map(item -&gt; ...).via(...).run()
   *   // Spoiler:  see exercise 5 + 6 above; assemble both into a single .run() expression.
   * </pre>
   */
  @Test
  @DisplayName("Exercise 7: One line, six layers — assemble the full expression")
  void exercise7_oneLineSixLayers() {
    String idToUpdate = "user-1";
    String key = "country";
    String newValue = "GB";

    // TODO: replace answerRequired() with the full one-line expression.
    Either<RepoError, Item> result = answerRequired();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().id()).isEqualTo(idToUpdate);
    assertThat(result.getRight().attributes()).containsEntry(key, newValue);

    // Same shape, missing id — the whole pipeline collapses to NOT_FOUND.
    String missingId = "user-missing";
    // TODO: replace answerRequired() with the same expression but using missingId.
    Either<RepoError, Item> missing = answerRequired();

    assertThat(missing.isLeft()).isTrue();
    assertThat(missing.getLeft()).isEqualTo(RepoError.NOT_FOUND);
  }

  // ═════════════════════════════════════════════════════════════════════════
  // Diagnostic Exercise — Things People Get Wrong
  // ═════════════════════════════════════════════════════════════════════════

  /**
   * Diagnostic exercise: a wrong attempt and its fix.
   *
   * <p>The code below tries to call {@code .focus(FocusPath.of(attributesLens))} on an {@link
   * EitherPath} hoping to "modify the inner map" — but {@code EitherPath.focus(FocusPath)} narrows
   * the path to the focused value, so the outer {@link Item} is no longer in scope. The result of a
   * subsequent {@code .map} is an {@code EitherPath<RepoError, Map>}, not {@code
   * EitherPath<RepoError, Item>}, and we cannot save it.
   *
   * <p>The lesson: when we want to update an inner field while keeping the outer record, we use
   * {@code lens.modify(...)} inside a {@code .map}. We use {@code .focus(...)} on the path when we
   * actually want to narrow.
   *
   * <p>Replace the broken expression so the test passes; both the original and the fixed expression
   * are valid for different goals.
   *
   * <pre>
   *   // Nudge:    The fix is to keep the Item in scope.
   *   // Strategy: Move the lens.modify call into a map.
   *   // Spoiler:  see exercise 4 above.
   * </pre>
   */
  @Test
  @DisplayName("Diagnostic: focus narrows; modify inside map preserves the outer record")
  void diagnostic_focusVsModify() {
    EitherPath<RepoError, Item> wrapped = find("user-1").toEitherPath(RepoError.NOT_FOUND);

    // The intent below is wrong: we want to keep an Item, not a Map.
    // FocusPath<Item, Map<String,String>> attributesPath = FocusPath.of(attributesLens);
    // EitherPath<RepoError, Map<String,String>> narrowed =
    //     wrapped.focus(attributesPath).map(addEntry("country", "GB"));
    //
    // Calling .via(this::save) on `narrowed` would not even compile,
    // because save expects an Item, not a Map.

    // TODO: replace answerRequired() with the correct shape:
    //       use lens.modify inside .map so the EitherPath continues to carry an Item.
    EitherPath<RepoError, Item> updated = answerRequired();

    Either<RepoError, Item> result = updated.via(Tutorial00_OneLineSixLayers::save).run();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().attributes()).containsEntry("country", "GB");

    // Demonstrate that focus() *is* the right tool when narrowing is the goal.
    FocusPath<Item, Map<String, String>> attributesPath = FocusPath.of(attributesLens);
    EitherPath<RepoError, Map<String, String>> narrowed =
        wrapped.focus(attributesPath).map(addEntry("country", "GB"));
    assertThat(narrowed.run().isRight()).isTrue();
    assertThat(narrowed.run().getRight()).containsEntry("country", "GB");
  }

  // -------------------------------------------------------------------------
  // Congratulations! We have just touched every layer of Higher-Kinded-J.
  //
  // Where to next?
  //   • Tutorial 01_KindBasics       — what Kind<F, A> is and why it exists
  //   • Effect API journey           — for productive day-to-day use of Path types
  //   • Foundations / One Line Six   — the book chapter that unpacks the same anchor
  // -------------------------------------------------------------------------
}
