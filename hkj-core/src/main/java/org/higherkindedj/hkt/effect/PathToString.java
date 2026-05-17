// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Collection;

/**
 * Internal helper that defines the shared {@code toString()} convention for Effect Path types.
 *
 * <p>The convention is deliberately narrow so that path output stays greppable in logs and stable
 * to assert on in tests:
 *
 * <ul>
 *   <li><b>Wrapper form:</b> every path renders as {@code TypeName(inner)} using round parentheses.
 *       The {@code inner} portion delegates to the wrapped value's own {@code toString()} (e.g.
 *       {@code MaybePath(Just(42))}, {@code EitherPath(Left(error))}).
 *   <li><b>Sentinels:</b> states with no eagerly-available value use a single, angle-bracketed
 *       vocabulary ({@link #DEFERRED}, {@link #STREAM}, {@link #EMPTY}, {@link #PENDING}, {@link
 *       #FAILED}) so that, for example, every unevaluated computation reads {@code <deferred>}.
 *   <li><b>Bounded collections:</b> collection-backed paths cap the number of rendered elements via
 *       {@link #elements(Collection)} and append an explicit {@code …(+k more)} marker, so a large
 *       backing collection never produces an unbounded log line.
 * </ul>
 *
 * <p>Rendering a path's {@code toString()} never forces a deferred computation or consumes a
 * stream.
 */
final class PathToString {

  private PathToString() {}

  /** An unevaluated deferred computation (e.g. {@code IOPath}, {@code VTaskPath}). */
  static final String DEFERRED = "<deferred>";

  /** A lazy stream that has not been consumed (e.g. {@code StreamPath}, {@code VStreamPath}). */
  static final String STREAM = "<stream>";

  /** An empty optional-like path with no value present. */
  static final String EMPTY = "<empty>";

  /** An asynchronous computation that has not yet completed. */
  static final String PENDING = "<pending>";

  /** An asynchronous or lazy computation that completed exceptionally. */
  static final String FAILED = "<failed>";

  /** Default maximum number of collection elements rendered before truncation. */
  static final int DEFAULT_LIMIT = 10;

  /**
   * Renders a collection with at most {@link #DEFAULT_LIMIT} elements, appending {@code …(+k more)}
   * when the collection is larger.
   *
   * @param collection the collection to render; must not be {@code null}
   * @return a bounded bracketed representation, identical to {@code List.toString()} when the
   *     collection size is within the limit
   */
  static String elements(Collection<?> collection) {
    return elements(collection, DEFAULT_LIMIT);
  }

  /**
   * Renders a collection with at most {@code limit} elements, appending {@code …(+k more)} when the
   * collection is larger.
   *
   * @param collection the collection to render; must not be {@code null}
   * @param limit the maximum number of elements to render
   * @return a bounded bracketed representation
   */
  static String elements(Collection<?> collection, int limit) {
    int size = collection.size();
    StringBuilder sb = new StringBuilder("[");
    int i = 0;
    for (Object element : collection) {
      if (i == limit) {
        break;
      }
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(element);
      i++;
    }
    if (size > limit) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("…(+").append(size - limit).append(" more)");
    }
    return sb.append(']').toString();
  }
}
