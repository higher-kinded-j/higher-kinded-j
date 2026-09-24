// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards the book's {@code {{#include}}} directives.
 *
 * <p>Where a snippet has a runnable counterpart in this module, the page includes it rather than
 * copying it, so the code on the page is the code the build compiles and runs. That is a stronger
 * guarantee than compiling a copy - but it introduces one new way for the page to lie: <b>mdbook
 * does not fail on a missing anchor.</b> It renders an empty code block and says nothing, so a
 * typo, or a rename of an {@code ANCHOR} in the Java source, silently deletes the code from the
 * page.
 *
 * <p>This test closes that hole: every include must resolve to a file that exists, and to an anchor
 * that exists inside it, and the anchor must not be empty.
 */
@DisplayName("every book {{#include}} resolves to a real anchor")
class BookIncludeTest {

  private static final Path BOOK = Path.of(required("hkj.book.dir"));

  /**
   * The number of includes must never fall below this. Deleting an include line, or replacing it
   * with a stale hand-copied fence, would otherwise be invisible: the remaining includes would
   * still all resolve, and the snippet gate no longer covers those pages.
   *
   * <p>It counts the anchored includes and the whole-file golden ones together, so dropping either
   * kind fails.
   */
  private static final int MINIMUM_INCLUDES = 160;

  /** Any include, in any form, so an unanchored one cannot slip past unchecked. */
  private static final Pattern ANY_INCLUDE = Pattern.compile("\\{\\{#include\\s+([^}]+)}}");

  /** {{#include <path>:<anchor>}}, the anchored form, which is the only one the book uses. */
  private static final Pattern INCLUDE =
      Pattern.compile("\\{\\{#include\\s+([^:}]+):([\\w-]+)\\s*}}");

  /** One include: which page asked for it, and what it asked for. */
  record Include(Path page, String rawPath, String anchor) {
    @Override
    public String toString() {
      return "%s -> %s:%s".formatted(page.getFileName(), Path.of(rawPath).getFileName(), anchor);
    }
  }

  static Stream<Include> includes() throws IOException {
    List<Include> found = new ArrayList<>();
    try (Stream<Path> pages = Files.walk(BOOK)) {
      pages
          .filter(p -> p.toString().endsWith(".md"))
          .sorted()
          .forEach(
              page -> {
                Matcher m = INCLUDE.matcher(read(page));
                while (m.find()) {
                  found.add(new Include(page, m.group(1).strip(), m.group(2)));
                }
              });
    }
    return found.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("includes")
  @DisplayName("include resolves")
  void includeResolves(Include include) {
    // mdbook resolves an include relative to the page that contains it.
    Path target = include.page().getParent().resolve(include.rawPath()).normalize();

    if (!Files.exists(target)) {
      fail(
          """

          %s includes a file that does not exist:
            %s

          Fix the path, or restore the file. mdbook would render an empty code block and say nothing.
          """
              .formatted(BOOK.relativize(include.page()), target));
    }

    String source = read(target);

    // The name must match EXACTLY. mdbook does, and substring matching does not: `total` is a
    // prefix
    // of `total_ops`, so `contains("ANCHOR: total")` happily finds `ANCHOR: total_ops`, passes, and
    // lets the page render an empty block. That is the precise failure this test exists to catch.
    Matcher open = anchorAt("ANCHOR", include.anchor()).matcher(source);
    Matcher close = anchorAt("ANCHOR_END", include.anchor()).matcher(source);

    if (!open.find() || !close.find()) {
      fail(
          """

          %s includes the anchor `%s`, which does not exist in:
            %s

          mdbook does NOT fail on a missing anchor. It renders an EMPTY code block, so the page
          would silently lose its code. Add `// ANCHOR: %s` / `// ANCHOR_END: %s` to the source, or
          fix the name on the page.
          """
              .formatted(
                  BOOK.relativize(include.page()),
                  include.anchor(),
                  target,
                  include.anchor(),
                  include.anchor()));
    }

    String body =
        source
            .substring(open.end(), close.start())
            .lines()
            .filter(line -> !line.isBlank())
            .filter(line -> !line.strip().startsWith("// ANCHOR"))
            .reduce("", (a, b) -> a + b + "\n");

    assertThat(body.strip())
        .as(
            "the anchor `%s` in %s is empty, so the page would render a blank code block",
            include.anchor(), target)
        .isNotEmpty();
  }

  /** `ANCHOR: total` must not match `ANCHOR: total_ops`, so the name may not run on. */
  private static Pattern anchorAt(String keyword, String anchor) {
    return Pattern.compile(keyword + ":\\s*" + Pattern.quote(anchor) + "(?![\\w-])");
  }

  @Test
  @DisplayName("every include is the anchored form, or a whole golden file")
  void everyIncludeIsAnchored() throws IOException {
    // The resolve test only understands `file:anchor`. A line-range include would be silently
    // unchecked, and would quietly show the wrong lines the moment the file it points into grows.
    //
    // A golden file is the one whole-file include worth allowing. It is generated output, pinned
    // byte for byte by the processor's own golden-file test, so it cannot carry an ANCHOR comment
    // (that would change the very bytes under test) and it cannot drift without failing there
    // first. Showing a reader exactly what the processor writes is worth the exception.
    try (Stream<Path> pages = Files.walk(BOOK)) {
      pages
          .filter(p -> p.toString().endsWith(".md"))
          .forEach(
              page -> {
                Matcher m = ANY_INCLUDE.matcher(read(page));
                while (m.find()) {
                  String target = m.group(1).trim();
                  if (isWholeGoldenFile(page, target)) continue;
                  assertThat(target)
                      .as(
                          "%s uses an unanchored include (%s), which this test cannot verify",
                          BOOK.relativize(page), m.group())
                      .contains(":");
                }
              });
    }
  }

  /** How many whole-file golden includes the book carries, which the floor counts as well. */
  private static long goldenIncludes() throws IOException {
    try (Stream<Path> pages = Files.walk(BOOK)) {
      return pages
          .filter(p -> p.toString().endsWith(".md"))
          .mapToLong(
              page -> {
                Matcher m = ANY_INCLUDE.matcher(read(page));
                long found = 0;
                while (m.find()) {
                  if (m.group(1).trim().endsWith(".golden")) found++;
                }
                return found;
              })
          .sum();
    }
  }

  /** A whole-file include of a golden file that exists, which is the one allowed exception. */
  private static boolean isWholeGoldenFile(Path page, String target) {
    if (!target.endsWith(".golden")) return false;
    Path resolved = page.getParent().resolve(target).normalize();
    assertThat(resolved)
        .as("%s includes a golden file that does not exist (%s)", BOOK.relativize(page), target)
        .exists();
    return true;
  }

  @Test
  @DisplayName("the include coverage does not shrink")
  void theIncludeCoverageDoesNotShrink() throws IOException {
    // A guard on the guard. Every include resolving is worthless if the includes were deleted: the
    // remaining ones still pass, and the pages quietly revert to unverified hand-copied code.
    assertThat(includes().count() + goldenIncludes())
        .as(
            """
            The number of {{#include}} directives dropped below the floor.

            An include was deleted, or replaced with a hand-copied fence, so those pages are no \
            longer sourced from compiled code. If a page legitimately stopped using includes, lower \
            MINIMUM_INCLUDES deliberately and say why in the commit message.""")
        .isGreaterThanOrEqualTo(MINIMUM_INCLUDES);
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The book directory is supplied by the Gradle task. Run straight from an IDE the property is
   * unset, and {@code Path.of(null)} throws an opaque NPE during class initialisation; this turns
   * that into a message that says how to run the test.
   */
  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null) {
      throw new IllegalStateException(
          "System property '%s' is not set. Run this via Gradle: `gradle :hkj-examples:bookVerify`."
              .formatted(property));
    }
    return value;
  }
}
