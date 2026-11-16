// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The book's files, for the tests that write the tables Supported Types includes: the book's source
 * directory, its Core Types pages as {@code SUMMARY.md} lists them, and the golden files the tests
 * hold those tables in.
 */
final class BookFiles {

  /** Where an include of a golden file starts, from a page in {@code monads/}. */
  private static final String GOLDEN_INCLUDE =
      "{{#include ../../../hkj-examples/src/test/resources/golden/";

  private BookFiles() {}

  /** The book's source directory, which the Gradle task supplies. */
  static Path book() {
    String property = "hkj.book.dir";
    String value = System.getProperty(property);
    if (value == null) {
      throw new IllegalStateException(
          "System property '%s' is not set. Run this via Gradle: `gradle :hkj-examples:bookVerify`."
              .formatted(property));
    }
    return Path.of(value);
  }

  /**
   * The Core Types pages {@code SUMMARY.md} lists one level under the group, in its order, as file
   * names in {@code monads/}. A sub-page, such as Accumulating Assembly, is left out.
   */
  static List<String> coreTypePages(Path book) {
    List<String> lines = read(book.resolve("SUMMARY.md")).lines().toList();
    String group = "- [Core Types](monads/ch_intro.md)";
    int start = -1;
    for (int i = 0; i < lines.size() && start < 0; i++) {
      if (lines.get(i).strip().equals(group)) {
        start = i;
      }
    }
    assertThat(start).as("the Core Types entry in SUMMARY.md").isNotNegative();
    int indent = lines.get(start).indexOf('-');
    List<String> pages = new ArrayList<>();
    for (String line : lines.subList(start + 1, lines.size())) {
      int depth = line.indexOf('-');
      if (line.isBlank() || depth <= indent) {
        break;
      }
      if (depth == indent + 2) {
        int from = line.indexOf("(monads/");
        assertThat(from).as("a Core Types page in monads/, in SUMMARY.md: %s", line).isPositive();
        from += "(monads/".length();
        pages.add(line.substring(from, line.indexOf(')', from)));
      }
    }
    return pages;
  }

  /** The include a page in {@code monads/} carries for one anchor of a golden file. */
  static String include(String golden, String anchor) {
    return GOLDEN_INCLUDE + golden + ":" + anchor + "}}";
  }

  /** A golden file under the test resources, such as {@code foundations-triage.md.golden}. */
  static String golden(String name) {
    String resource = "/golden/" + name;
    try (InputStream in = BookFiles.class.getResourceAsStream(resource)) {
      assertThat(in).as("the golden file %s", resource).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A file's text, with Windows line endings made Unix ones. */
  static String read(Path file) {
    try {
      return Files.readString(file).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
