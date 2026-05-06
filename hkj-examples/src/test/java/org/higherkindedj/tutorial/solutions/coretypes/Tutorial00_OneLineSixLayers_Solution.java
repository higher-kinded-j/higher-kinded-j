// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

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
 * Solution for Tutorial 00: One Line, Six Layers.
 *
 * <p>This file is the "teaching solution" template: each exercise gets the working answer plus a
 * short note on <em>why</em> it is idiomatic, one alternative shape that also works, and one common
 * wrong attempt and its symptoms.
 */
@DisplayName("Tutorial 00 Solution: One Line, Six Layers")
public class Tutorial00_OneLineSixLayers_Solution {

  // ─── Domain (mirrors the tutorial) ────────────────────────────────────────

  record Item(String id, Map<String, String> attributes) {}

  enum RepoError {
    NOT_FOUND,
    INVALID
  }

  static final Lens<Item, Map<String, String>> attributesLens =
      Lens.of(Item::attributes, (item, attrs) -> new Item(item.id(), attrs));

  static Function<Map<String, String>, Map<String, String>> addEntry(String key, String value) {
    return original -> {
      Map<String, String> copy = new LinkedHashMap<>(original);
      copy.put(key, value);
      return copy;
    };
  }

  private static Item makeUser1() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put("name", "Alice");
    attrs.put("country", "US");
    return new Item("user-1", Map.copyOf(attrs));
  }

  static MaybePath<Item> find(String id) {
    if ("user-1".equals(id)) {
      return Path.just(makeUser1());
    }
    return Path.nothing();
  }

  static EitherPath<RepoError, Item> save(Item item) {
    if (item.id() == null || item.id().isBlank()) {
      return Path.left(RepoError.INVALID);
    }
    return Path.right(item);
  }

  // ─── Exercise 0 ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Exercise 0: setup check")
  void exercise0_setupCheck() {
    MaybePath<Integer> sanity = Path.just(42);
    assertThat(sanity.getOrElse(-1)).isEqualTo(42);
  }

  // ─── Exercise 1 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code getOrElse} expresses intent — "give me the value, or this default
   * if absent" — without ever exposing a {@code null} or forcing a downstream {@code if}.
   *
   * <p>Alternative: {@code path.run().getOrElse(...)} (calling through the underlying Maybe). Same
   * answer; the {@code MaybePath} method is just sugar.
   *
   * <p>Common wrong attempt: {@code path.run().get()}. Calling {@code get()} on a {@code Nothing}
   * throws — and silently masks the absent case, which is exactly the problem we are trying to fix.
   */
  @Test
  @DisplayName("Exercise 1: Layer 1 (Effect)")
  void exercise1_effectType() {
    MaybePath<Item> path = find("user-1");

    Item item = path.getOrElse(new Item("missing", Map.of()));

    assertThat(item.id()).isEqualTo("user-1");
    assertThat(item.attributes()).containsEntry("name", "Alice");
  }

  // ─── Exercise 2 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code toEitherPath(error)} is the natural transformation between {@link
   * MaybePath} and {@link EitherPath}. Lifting the absence into a typed error at this point means
   * every downstream step has the error type available; we never have to "remember" to convert
   * later.
   *
   * <p>Alternative: pattern-match on {@code path.run()} and rebuild an {@code EitherPath}
   * ourselves. Strictly more code, no benefit.
   *
   * <p>Common wrong attempt: deferring the lift until after the chain is built. Then the downstream
   * {@code map} / {@code via} calls have to operate on {@code Maybe} and we cannot carry a domain
   * error.
   */
  @Test
  @DisplayName("Exercise 2: Layer 2 (Natural transformation)")
  void exercise2_naturalTransformation() {
    MaybePath<Item> presentMaybe = find("user-1");
    MaybePath<Item> absentMaybe = find("user-missing");

    EitherPath<RepoError, Item> presentEither = presentMaybe.toEitherPath(RepoError.NOT_FOUND);
    EitherPath<RepoError, Item> absentEither = absentMaybe.toEitherPath(RepoError.NOT_FOUND);

    assertThat(presentEither.run().isRight()).isTrue();
    assertThat(absentEither.run().isLeft()).isTrue();
    assertThat(absentEither.run().getLeft()).isEqualTo(RepoError.NOT_FOUND);
  }

  // ─── Exercise 3 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code lens.modify(transform, source)} captures "apply this function to
   * exactly that field" without any visible copy-construction. The original is never mutated.
   *
   * <p>Alternative: {@code lens.set(transform.apply(lens.get(source)), source)}. Strictly more
   * code; {@code modify} is the standard composition.
   *
   * <p>Common wrong attempt: mutating the map returned by {@code item.attributes()} in place. If
   * the source map is immutable (here it is, via {@code Map.copyOf}) we get {@code
   * UnsupportedOperationException}; if it is mutable we accidentally break sharing.
   */
  @Test
  @DisplayName("Exercise 3: Layer 3 (Optic)")
  void exercise3_opticModify() {
    Item original = makeUser1();

    Item updated = attributesLens.modify(addEntry("country", "GB"), original);

    assertThat(updated.id()).isEqualTo("user-1");
    assertThat(updated.attributes()).containsEntry("country", "GB");
    assertThat(original.attributes()).containsEntry("country", "US");
  }

  // ─── Exercise 4 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code map} preserves the {@code EitherPath} structure. If the path is
   * already a {@code Left}, the function does not run; if it is a {@code Right}, it runs and the
   * result is re-wrapped in a {@code Right}.
   *
   * <p>Alternative: {@code .via(item -> Path.right(attributesLens.modify(...)))}. Works, but
   * misuses {@code via} (the Monad capability) for a step that does not need a new effect.
   *
   * <p>Common wrong attempt: {@code .via(item -> attributesLens.modify(...))}. Compile error: the
   * function returns an {@code Item}, not an {@code EitherPath}; {@code via} expects the latter.
   * Use {@code map} when the function returns a plain value.
   */
  @Test
  @DisplayName("Exercise 4: Layer 4 (Functor)")
  void exercise4_functorMap() {
    EitherPath<RepoError, Item> wrapped = find("user-1").toEitherPath(RepoError.NOT_FOUND);

    EitherPath<RepoError, Item> updated =
        wrapped.map(item -> attributesLens.modify(addEntry("country", "GB"), item));

    Either<RepoError, Item> result = updated.run();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().attributes()).containsEntry("country", "GB");
  }

  // ─── Exercise 5 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code via} (a.k.a. {@code flatMap}) expresses dependency. The next step
   * ({@code save}) decides whether the chain continues with a new {@code Right} or short-circuits
   * with a new {@code Left}.
   *
   * <p>Alternative: {@code wrapped.via(Tutorial00_OneLineSixLayers_Solution::save)} — a method
   * reference reads cleaner and is the form used in the One Line, Six Layers anchor.
   *
   * <p>Common wrong attempt: {@code wrapped.map(this::save)}. The function returns an {@code
   * EitherPath}, so {@code map} produces an {@code EitherPath<RepoError, EitherPath<...>>} — nested
   * effects we now need to flatten. {@code via} flattens for us; that is its whole job.
   */
  @Test
  @DisplayName("Exercise 5: Layer 5 (Monad)")
  void exercise5_monadVia() {
    EitherPath<RepoError, Item> wrapped =
        find("user-1")
            .toEitherPath(RepoError.NOT_FOUND)
            .map(item -> attributesLens.modify(addEntry("country", "GB"), item));

    EitherPath<RepoError, Item> saved = wrapped.via(Tutorial00_OneLineSixLayers_Solution::save);

    Either<RepoError, Item> result = saved.run();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().attributes()).containsEntry("country", "GB");
  }

  // ─── Exercise 6 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: {@code run()} is the only impure operation in the chain. Up to that
   * point the entire pipeline is data — a description of what to do — and we can pass it around,
   * cache it, log it, or rewrite it.
   *
   * <p>Alternative: in tests, {@code .run().getRight()} or {@code .run().getLeft()} after asserting
   * which side the result is on; in production code, prefer {@code fold} or pattern matching to
   * stay total.
   *
   * <p>Common wrong attempt: calling {@code .run()} after every step. Each {@code run()} unwraps
   * the description into a value; doing it eagerly means every subsequent step has to wrap-and-run
   * again, defeating the point.
   */
  @Test
  @DisplayName("Exercise 6: Layer 6 (Dispatch)")
  void exercise6_dispatch() {
    EitherPath<RepoError, Item> saved =
        find("user-1")
            .toEitherPath(RepoError.NOT_FOUND)
            .map(item -> attributesLens.modify(addEntry("country", "GB"), item))
            .via(Tutorial00_OneLineSixLayers_Solution::save);

    Either<RepoError, Item> either = saved.run();

    assertThat(either.isRight()).isTrue();
    assertThat(either.getRight().attributes()).containsEntry("country", "GB");
  }

  // ─── Exercise 7 ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: the entire workflow reads top-to-bottom, mirrors the imperative
   * pseudocode at the top of the tutorial, and never mentions a single null check, try/catch, or
   * defensive copy. Errors at any step short-circuit cleanly to a typed {@code Left}.
   *
   * <p>Alternative: extract intermediate steps into named local variables for debugging. Equivalent
   * at runtime; sometimes clearer in a code review.
   *
   * <p>Common wrong attempt: building the same chain with raw {@code Either} and {@code Maybe} and
   * a stack of nested {@code flatMap} calls. Works, but loses the unifying Path API and the future
   * ability to swap in {@code VTaskPath} for an async repository.
   */
  @Test
  @DisplayName("Exercise 7: assemble the full expression")
  void exercise7_oneLineSixLayers() {
    String idToUpdate = "user-1";
    String key = "country";
    String newValue = "GB";

    Either<RepoError, Item> result =
        find(idToUpdate)
            .toEitherPath(RepoError.NOT_FOUND)
            .map(item -> attributesLens.modify(addEntry(key, newValue), item))
            .via(Tutorial00_OneLineSixLayers_Solution::save)
            .run();

    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().id()).isEqualTo(idToUpdate);
    assertThat(result.getRight().attributes()).containsEntry(key, newValue);

    String missingId = "user-missing";
    Either<RepoError, Item> missing =
        find(missingId)
            .toEitherPath(RepoError.NOT_FOUND)
            .map(item -> attributesLens.modify(addEntry(key, newValue), item))
            .via(Tutorial00_OneLineSixLayers_Solution::save)
            .run();

    assertThat(missing.isLeft()).isTrue();
    assertThat(missing.getLeft()).isEqualTo(RepoError.NOT_FOUND);
  }

  // ─── Diagnostic ────────────────────────────────────────────────────────────

  /**
   * Why this is idiomatic: when we want to keep the outer record (the {@code Item}) so we can save
   * it later, we apply the optic <em>inside</em> a {@code .map}. {@code lens.modify(...)} returns a
   * new {@code Item}; the {@code EitherPath} stays {@code EitherPath<RepoError, Item>}.
   *
   * <p>Alternative: build a function {@code Item -> Item} via {@code attributesLens::modify} and
   * pass it to {@code .map}. Same shape, slightly more concise.
   *
   * <p>Common wrong attempt: calling {@code .focus(FocusPath.of(attributesLens))} on the {@code
   * EitherPath}. That call is correct in its own right — it returns an {@code EitherPath<RepoError,
   * Map>} narrowed to the focused field — but it loses the surrounding {@code Item}, which means we
   * cannot chain {@code .via(this::save)} afterwards. Use {@code .focus(...)} when narrowing
   * <em>is</em> the goal; use {@code .map} + {@code lens.modify} when we want to update an inner
   * field <em>and</em> keep going.
   */
  @Test
  @DisplayName("Diagnostic: focus narrows; modify inside map preserves the outer record")
  void diagnostic_focusVsModify() {
    EitherPath<RepoError, Item> wrapped = find("user-1").toEitherPath(RepoError.NOT_FOUND);

    EitherPath<RepoError, Item> updated =
        wrapped.map(item -> attributesLens.modify(addEntry("country", "GB"), item));

    Either<RepoError, Item> result = updated.via(Tutorial00_OneLineSixLayers_Solution::save).run();
    assertThat(result.isRight()).isTrue();
    assertThat(result.getRight().attributes()).containsEntry("country", "GB");

    FocusPath<Item, Map<String, String>> attributesPath = FocusPath.of(attributesLens);
    EitherPath<RepoError, Map<String, String>> narrowed =
        wrapped.focus(attributesPath).map(addEntry("country", "GB"));
    assertThat(narrowed.run().isRight()).isTrue();
    assertThat(narrowed.run().getRight()).containsEntry("country", "GB");
  }
}
