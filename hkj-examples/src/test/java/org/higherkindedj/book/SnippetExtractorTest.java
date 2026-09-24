// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.higherkindedj.book.SnippetExtractor.Expectation;
import org.higherkindedj.book.SnippetExtractor.Page;
import org.higherkindedj.book.SnippetExtractor.Snippet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Holds the gate to compiling the declaration a page wrote. The pages cover most of the extractor,
 * but not every shape a page could take, and a shape it rewrites wrongly passes silently.
 */
@DisplayName("SnippetExtractor")
class SnippetExtractorTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          public class Shop { private record Sku(String value) {} }  | class Shop { private record Sku(String value) {} }
          class Shop { private record Sku(String value) {} }         | class Shop { private record Sku(String value) {} }
          @Deprecated public final class Shop { public enum Kind {} } | @Deprecated final class Shop { public enum Kind {} }
          """)
  @DisplayName("A top-level type loses its own access modifier, a nested one keeps it")
  void onlyTheDeclarationsOwnAccessGoes(String written, String compiled) {
    Snippet snippet = new Snippet(0, 1, written + "\n", new Expectation.Compiles());
    Page page = new Page(Path.of("shop.md"), "shop", List.of(snippet));

    assertThat(SnippetExtractor.toCompilationUnit(page, snippet, "")).contains(compiled);
  }
}
