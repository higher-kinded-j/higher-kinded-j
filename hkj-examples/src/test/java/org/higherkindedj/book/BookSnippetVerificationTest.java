// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The documentation gate: every snippet in hkj-book marked {@code <!-- verify -->} must compile
 * against the real library.
 *
 * <p>hkj-book is not a Gradle module, so until this existed nothing compiled its code. Snippets
 * were hand-maintained, and drifted: two shipped examples did not compile, and a third documented a
 * method as taking a type it does not take. A reader copying those examples got a compiler error; a
 * coding assistant reading them generated code that could not build.
 *
 * <p>Snippets opt in individually, so pages can be brought under the gate one at a time. {@link
 * #theGateDoesNotShrink()} stops a marker being quietly deleted to silence a failure.
 */
@DisplayName("hkj-book snippets compile against the real library")
class BookSnippetVerificationTest {

  /**
   * The number of verified snippets must never fall below this. Raise it as pages are brought under
   * the gate; a drop means a marker was removed, which is exactly the move this gate exists to
   * catch.
   *
   * <p>It went 49 -> 38 when record_mapping's eleven snippets moved to {@code {{#include}}}: the
   * page now renders the compiled example directly, which is a stronger guarantee than compiling a
   * copy of it, so those snippets no longer need a marker. That is the only reason this number may
   * fall.
   */
  private static final int MINIMUM_VERIFIED_SNIPPETS = 215;

  /**
   * Every documentation root whose code is verified. The book was the first; the skills are the
   * other documentation this repo ships, and until they were added here nothing compiled them, so a
   * code review found four undefined identifiers in a single pass.
   */
  private static final List<Path> ROOTS =
      List.of(Path.of(required("hkj.book.dir")), Path.of(required("hkj.skills.dir")));

  private static final String PROCESSOR_PATH = System.getProperty("hkj.book.processorPath", "");

  /** Domain types a page elides for readability. Kept out of the book so the pages stay clean. */
  private static final Path FIXTURES =
      Path.of("src", "test", "resources", "fixtures").toAbsolutePath();

  /** One row per marked snippet, so a failure names the snippet rather than the whole page. */
  record Case(SnippetExtractor.Page page, SnippetExtractor.Snippet snippet) {
    @Override
    public String toString() {
      return "%s (snippet %d, line %d)"
          .formatted(shortName(page.file()), snippet.index() + 1, snippet.lineNumber());
    }
  }

  static Stream<Case> verifiedSnippets() throws IOException {
    return verifiedPages()
        .flatMap(page -> page.snippets().stream().map(s -> new Case(page, s)))
        .toList()
        .stream();
  }

  private static Stream<SnippetExtractor.Page> verifiedPages() throws IOException {
    List<SnippetExtractor.Page> found = new ArrayList<>();
    for (Path root : ROOTS) {
      try (Stream<Path> pages = Files.walk(root)) {
        pages
            .filter(p -> p.toString().endsWith(".md"))
            .sorted()
            .map(page -> extract(page, root))
            .filter(page -> !page.isEmpty())
            .forEach(found::add);
      }
    }
    return found.stream();
  }

  private static SnippetExtractor.Page extract(Path page, Path root) {
    try {
      return SnippetExtractor.extract(page, root);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A path relative to whichever root it came from, so a failure names the file the way you do. */
  private static String shortName(Path file) {
    return ROOTS.stream()
        .filter(file::startsWith)
        .findFirst()
        .map(root -> root.relativize(file).toString())
        .orElseGet(file::toString);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("verifiedSnippets")
  @DisplayName("snippet compiles")
  void snippetCompiles(Case testCase) throws IOException {
    SnippetExtractor.Page page = testCase.page();
    String unit = SnippetExtractor.toCompilationUnit(page, testCase.snippet(), fixtureFor(page));

    DiagnosticCollector<JavaFileObject> diagnostics =
        compile(page.slug() + "_" + testCase.snippet().index(), unit);
    List<Diagnostic<? extends JavaFileObject>> errors =
        diagnostics.getDiagnostics().stream()
            .filter(
                d ->
                    d.getKind() == Diagnostic.Kind.ERROR
                        // The real build is -Werror on these, so a warning here is a page defect.
                        || d.getKind() == Diagnostic.Kind.WARNING
                        || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
            .filter(d -> !String.valueOf(d.getMessage(Locale.ROOT)).contains("preview"))
            .toList();

    if (!errors.isEmpty()) {
      fail(report(testCase, unit, errors));
    }
  }

  @Test
  @DisplayName("the gate does not shrink")
  void theGateDoesNotShrink() throws IOException {
    int verified = (int) verifiedSnippets().count();
    assertThat(verified)
        .as(
            """
            The number of `<!-- verify -->` snippets dropped below the floor.

            A snippet was un-marked rather than fixed, which defeats the gate. If a \
            snippet genuinely cannot be verified any more, lower MINIMUM_VERIFIED_SNIPPETS \
            deliberately and say why in the commit message.""")
        .isGreaterThanOrEqualTo(MINIMUM_VERIFIED_SNIPPETS);
  }

  /** A bare NPE from a static initialiser is a miserable way to learn you skipped Gradle. */
  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null) {
      throw new IllegalStateException(
          "System property '%s' is not set. Run this via Gradle: `gradle :hkj-examples:bookVerify`."
              .formatted(property));
    }
    return value;
  }

  private static String fixtureFor(SnippetExtractor.Page page) throws IOException {
    Path fixture = FIXTURES.resolve(page.slug() + ".java");
    return Files.exists(fixture) ? Files.readString(fixture) : "";
  }

  private static DiagnosticCollector<JavaFileObject> compile(String slug, String source) {
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    try (StandardJavaFileManager files =
        javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {

      Path out = Files.createTempDirectory("book-verify-" + slug);
      List<String> options =
          new ArrayList<>(
              List.of(
                  // Follow the toolchain rather than pinning a release: when it moves to 26, a
                  // hardcoded `--release 25 --enable-preview` becomes a confusing javac error.
                  "--release",
                  String.valueOf(Runtime.version().feature()),
                  "--enable-preview",
                  "-parameters",
                  // Lint exactly as the real build does, so the gate cannot pass code a reader's
                  // own `-Werror` build would reject.
                  "-Xlint:unchecked,rawtypes",
                  "-classpath",
                  System.getProperty("java.class.path"),
                  "-d",
                  out.toString(),
                  "-s",
                  out.toString(),
                  "-Xlint:-preview"));
      if (!PROCESSOR_PATH.isBlank()) {
        options.add("-processorpath");
        options.add(PROCESSOR_PATH);
      }

      try {
        javac
            .getTask(
                null,
                files,
                diagnostics,
                options,
                null,
                List.of(new InMemorySource("bookverify." + slug, source)))
            .call();
      } finally {
        deleteRecursively(out);
      }
      return diagnostics;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Each snippet compiles to its own temp directory. Left behind, they accumulate: one per verified
   * snippet, every run. The classes are never read back, so the directory exists only for the
   * compile and is removed the moment it is done.
   */
  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(BookSnippetVerificationTest::deleteQuietly);
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.delete(path);
    } catch (IOException ignored) {
      // A best-effort clean-up: a file we cannot delete is not worth failing the gate over.
    }
  }

  /** A javac error tells you nothing without the line it fired on, so print the assembled unit. */
  private static String report(
      Case testCase, String unit, List<Diagnostic<? extends JavaFileObject>> errors) {

    StringBuilder message = new StringBuilder();
    message
        .append("\nThe code in ")
        .append(testCase)
        .append(" does not compile against the library.\n")
        .append("Fix the page (or the code it documents); do not remove the marker.\n\n");

    errors.forEach(
        e ->
            message
                .append("  error: ")
                .append(e.getMessage(Locale.ROOT))
                .append("\n    at assembled line ")
                .append(e.getLineNumber())
                .append('\n'));

    message.append("\n--- assembled from the page's `<!-- verify -->` snippets ---\n");
    String[] lines = unit.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      message.append("%4d | %s%n".formatted(i + 1, lines[i]));
    }
    return message.toString();
  }

  /** javac reads sources through the file manager, so hand it the assembled unit from memory. */
  private static final class InMemorySource extends SimpleJavaFileObject {
    private final String code;

    InMemorySource(String fullyQualifiedName, String code) {
      super(
          URI.create("string:///" + fullyQualifiedName.replace('.', '/') + Kind.SOURCE.extension),
          Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }
}
