// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Keeps a verified diagnostic where the reader can see it.
 *
 * <p>A {@code verify:rejects} or {@code verify:reports} marker holds the book to the compiler's
 * exact words, which is the whole point of it. But the marker is an HTML comment: it renders to
 * nothing. A page can therefore prove a message and still never show it, leaving the reader with a
 * paraphrase when what they will one day do is paste the real thing into a search box. Two pages of
 * the mapping chapter did exactly that.
 *
 * <p>So the fragment a marker quotes must also appear as visible text on the same page. Whitespace
 * is ignored, because a long message is wrapped to fit the page.
 *
 * <p>The book has pages that predate the rule, mostly the optics error catalogue, where the entry
 * headings shorten the message rather than quote it. Those are listed rather than excused, page by
 * page and exactly: a new marker that hides its message fails, and so does a page rework that
 * quotes one without lowering the list, which is what keeps the backlog honest as it is paid down.
 */
@DisplayName("a verified diagnostic is visible on its page")
class BookDiagnosticVisibilityTest {

  private static final Path BOOK = Path.of(required("hkj.book.dir"));

  /**
   * The backlog, page by page: pages whose markers predate the rule, and how many each still hides.
   * Lower a count, or drop a page, as the pages are reworked; never add to it. An exact comparison
   * is what makes this a ratchet. A total alone would let a reworked page pay for a new hidden
   * marker elsewhere, and leave the count unchanged.
   */
  private static final List<String> KNOWN_BACKLOG =
      List.of(
          "monads/free_monad.md: 1",
          "optics/compiler_errors.md: 19",
          "optics/focus_containers.md: 1",
          "optics/optics_spec_interfaces.md: 3",
          "transformers/common_errors.md: 2");

  /**
   * Markers the book must not fall below, so deleting the gated snippets cannot pass for
   * compliance.
   */
  private static final int MINIMUM_MARKERS = 123;

  private static final Pattern MARKER =
      Pattern.compile("<!--\\s*verify:(?:rejects|reports)\\s+\"([^\"]+)\"\\s*-->");

  /** One marker whose message the page never shows. */
  record Unquoted(Path page, String fragment) {
    @Override
    public String toString() {
      return page.getFileName() + ": \"" + fragment + "\"";
    }
  }

  @Test
  @DisplayName("every quoted diagnostic appears as visible text, bar a shrinking backlog")
  void diagnosticsAreVisible() {
    List<Unquoted> hidden = new ArrayList<>();
    int markers = 0;

    try (Stream<Path> pages = Files.walk(BOOK)) {
      for (Path page : pages.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
        String text = read(page);
        String visible = collapse(MARKER.matcher(text).replaceAll(""));
        Matcher marker = MARKER.matcher(text);
        while (marker.find()) {
          markers++;
          String fragment = collapse(marker.group(1));
          if (!visible.contains(fragment))
            hidden.add(new Unquoted(BOOK.relativize(page), fragment));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    assertThat(markers)
        .as("diagnostic markers found under " + BOOK)
        .isGreaterThanOrEqualTo(MINIMUM_MARKERS);

    List<String> backlog =
        hidden.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    u -> u.page().toString().replace('\\', '/'),
                    java.util.TreeMap::new,
                    java.util.stream.Collectors.counting()))
            .entrySet()
            .stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .toList();

    assertThat(backlog)
        .as(
            """
            A verified diagnostic must also appear as visible text on its page: the marker is an \
            HTML comment, so the reader cannot see or search what it proves. Quote the message \
            beside the block. If you have reworked a page, lower its count in KNOWN_BACKLOG, or \
            drop the page from the list.""")
        .isEqualTo(KNOWN_BACKLOG);
  }

  /** A message is wrapped to fit the page, so whitespace plays no part in the comparison. */
  private static String collapse(String text) {
    return text.replaceAll("\\s+", " ");
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          property + " is not set: run this through `gradle :hkj-examples:bookVerify`");
    }
    return value;
  }
}
