// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.effect.advanced;

import java.io.IOException;
import java.util.List;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * The code shown in the Resource Management section of the book's <a
 * href="https://higher-kinded-j.github.io/effect/advanced_topics.html">Advanced Effect Topics</a>
 * page. The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API, and
 * the build runs {@code main} to check the output each region claims.
 */
public final class ResourceSafetyBook {

  private ResourceSafetyBook() {}

  public static void main(String[] args) {
    // ANCHOR: cleanup_fails
    IOPath<String> report =
        IOPath.bracket(
            () -> "connection",
            _ -> {
              throw new IllegalStateException("query failed");
            },
            _ -> {
              throw new IllegalArgumentException("close failed");
            });

    Try<String> outcome = report.runSafe();
    // Failure(java.lang.IllegalStateException: query failed)
    List<Throwable> suppressed =
        outcome.foldFailureFirst(failure -> List.of(failure.getSuppressed()), _ -> List.of());
    // [java.lang.IllegalArgumentException: close failed]
    // ANCHOR_END: cleanup_fails
    System.out.println(outcome);
    System.out.println(suppressed);

    // ANCHOR: close_fails
    AutoCloseable writer =
        () -> {
          throw new IOException("final flush failed");
        };

    Try<String> saved = IOPath.withResource(() -> writer, _ -> "saved").runSafe();
    // Failure(java.io.UncheckedIOException: java.io.IOException: final flush failed)
    // ANCHOR_END: close_fails
    System.out.println(saved);
  }
}
