// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.io.Serial;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runs a {@link Fold} only until a query has its answer, for {@link Fold#preview}, {@link
 * Fold#find}, {@link Fold#exists}, {@link Fold#all} and {@link Fold#isEmpty}.
 *
 * <p>{@code foldMap} has no way to stop, so a search ends it by throwing itself from inside the
 * fold's function at the first focus its test accepts. It carries no stack trace, and each search
 * catches only itself, so a search run inside another search's fold ends only its own. A fold that
 * catches the throw and goes on is thrown again at its next focus, and its answer still stands.
 */
@NullMarked
final class FoldSearch extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Combines nothing: a search keeps its answer in itself, not in the fold's result. */
  private static final Monoid<Void> NOTHING =
      new Monoid<>() {
        @Override
        public Void empty() {
          return null;
        }

        @Override
        public Void combine(Void a, Void b) {
          return null;
        }
      };

  private transient boolean found;
  private transient @Nullable Object focus;

  private FoldSearch() {
    super(null, null, false, false);
  }

  /**
   * Folds {@code source} until {@code test} accepts a focus, in the order {@code foldMap} visits
   * them. No focus after the accepted one is visited, so {@code test} runs only up to it.
   *
   * @param fold the fold to search
   * @param test accepts the focus that answers the query
   * @param source the structure to fold
   * @return the finished search: whether a focus was accepted, and which
   */
  static <S extends @Nullable Object, A extends @Nullable Object> FoldSearch first(
      Fold<S, A> fold, Predicate<? super A> test, S source) {
    FoldSearch search = new FoldSearch();
    try {
      fold.foldMap(
          NOTHING,
          a -> {
            if (search.found) {
              throw search;
            }
            if (test.test(a)) {
              search.found = true;
              search.focus = a;
              throw search;
            }
            return null;
          },
          source);
    } catch (FoldSearch thrown) {
      if (thrown != search) {
        throw thrown;
      }
    }
    return search;
  }

  /**
   * Whether the test accepted a focus.
   *
   * @return {@code true} when a focus answered the query
   */
  boolean found() {
    return found;
  }

  /**
   * The accepted focus, or {@code null} when none was accepted.
   *
   * @param <A> the fold's focus type
   * @return the focus the test accepted
   */
  @SuppressWarnings("unchecked") // the focus was stored from the searched fold's own A
  <A extends @Nullable Object> @Nullable A focus() {
    return (A) focus;
  }
}
