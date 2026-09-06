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
import org.higherkindedj.book.SnippetExtractor.Expectation;
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
 *
 * <p>A page documenting what the processor <em>refuses</em> cannot be gated by compiling its
 * snippets, and those are the pages a processor change is most likely to invalidate. Two further
 * markers cover them: {@code <!-- verify:rejects "fragment" -->} asserts the snippet does not
 * compile and that an error quotes the fragment, and {@code <!-- verify:reports "fragment" -->}
 * asserts it compiles and a note or warning quotes it. The fragment is the half that rots when a
 * diagnostic is reworded.
 */
@DisplayName("hkj-book snippets are held to the real library")
class BookSnippetVerificationTest {

  /**
   * The number of marked snippets must never fall below this, whichever marker they carry. Raise it
   * as pages are brought under the gate; a drop means a marker was removed, which is exactly the
   * move this gate exists to catch.
   *
   * <p>It went 49 -> 38 when record_mapping's eleven snippets moved to {@code {{#include}}}: the
   * page now renders the compiled example directly, which is a stronger guarantee than compiling a
   * copy of it, so those snippets no longer need a marker. That is the only reason this number may
   * fall.
   */
  private static final int MINIMUM_VERIFIED_SNIPPETS = 1948;

  /**
   * How many of those snippets must quote a diagnostic, under {@code verify:rejects} or {@code
   * verify:reports}. It has its own floor because the total cannot protect it: swapping a rejection
   * check for an easy positive snippet elsewhere leaves the total untouched, and those checks are
   * the only thing holding the pages that document refusals to what the processor actually says.
   */
  private static final int MINIMUM_DIAGNOSTIC_SNIPPETS = 49;

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

  /** One snippet whose marker quotes a diagnostic, beside the fragment it quotes. */
  record Quoted(Case testCase, String fragment) {
    @Override
    public String toString() {
      return testCase.toString();
    }
  }

  static Stream<Case> verifiedSnippets() throws IOException {
    return verifiedPages()
        .flatMap(page -> page.snippets().stream().map(s -> new Case(page, s)))
        .toList()
        .stream();
  }

  static Stream<Case> compilingSnippets() throws IOException {
    return verifiedSnippets()
        .filter(c -> c.snippet().expectation() instanceof Expectation.Compiles)
        .toList()
        .stream();
  }

  static Stream<Quoted> rejectedSnippets() throws IOException {
    return quoting(Expectation.Rejects.class);
  }

  static Stream<Quoted> reportedSnippets() throws IOException {
    return quoting(Expectation.Reports.class);
  }

  private static Stream<Quoted> quoting(Class<? extends Expectation> marker) throws IOException {
    return verifiedSnippets()
        .filter(c -> marker.isInstance(c.snippet().expectation()))
        .map(c -> new Quoted(c, fragmentOf(c.snippet().expectation())))
        .toList()
        .stream();
  }

  private static String fragmentOf(Expectation expectation) {
    return switch (expectation) {
      case Expectation.Rejects(String fragment) -> fragment;
      case Expectation.Reports(String fragment) -> fragment;
      case Expectation.Compiles _ -> "";
    };
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
  @MethodSource("compilingSnippets")
  @DisplayName("snippet compiles")
  void snippetCompiles(Case testCase) throws IOException {
    Compiled compiled = compile(testCase);
    List<Diagnostic<? extends JavaFileObject>> errors =
        compiled
            // The real build is -Werror on these, so a warning here is a page defect.
            .ofKind(
                Diagnostic.Kind.ERROR, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING)
            .stream()
            .filter(d -> !message(d).contains("preview"))
            .toList();

    if (!errors.isEmpty()) {
      fail(
          header(testCase, "does not compile against the library")
              + "Fix the page (or the code it documents); do not remove the marker.\n\n"
              + listing(errors)
              + assembled(compiled.unit()));
    }
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("rejectedSnippets")
  @DisplayName("snippet is refused, in the words the page quotes")
  void snippetIsRejected(Quoted quoted) throws IOException {
    Compiled compiled = compile(quoted.testCase());
    List<Diagnostic<? extends JavaFileObject>> errors = compiled.ofKind(Diagnostic.Kind.ERROR);

    if (errors.isEmpty()) {
      fail(
          header(quoted.testCase(), "compiles, but the page documents it as refused")
              + """
              Either the rule was relaxed and the page is now wrong, or the snippet no longer \
              shows the shape it is meant to. Do not weaken the marker to make this pass.

              """
              + assembled(compiled.unit()));
    }
    if (errors.stream().noneMatch(e -> message(e).contains(quoted.fragment()))) {
      fail(
          header(quoted.testCase(), "is refused, but not in the words the page quotes")
              + "The page quotes: \"%s\"%n%nThe processor said:%n%n".formatted(quoted.fragment())
              + listing(errors)
              + assembled(compiled.unit()));
    }
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("reportedSnippets")
  @DisplayName("snippet compiles, and is reported in the words the page quotes")
  void snippetIsReported(Quoted quoted) throws IOException {
    Compiled compiled = compile(quoted.testCase());
    List<Diagnostic<? extends JavaFileObject>> errors = compiled.ofKind(Diagnostic.Kind.ERROR);

    if (!errors.isEmpty()) {
      fail(
          header(quoted.testCase(), "does not compile, and the page documents it as compiling")
              + "A note or a warning is raised about code that builds; this does not build.\n\n"
              + listing(errors)
              + assembled(compiled.unit()));
    }
    List<Diagnostic<? extends JavaFileObject>> reports =
        compiled.ofKind(
            Diagnostic.Kind.NOTE, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING);
    if (reports.stream().noneMatch(r -> message(r).contains(quoted.fragment()))) {
      fail(
          header(quoted.testCase(), "draws no diagnostic quoting what the page says it draws")
              + "The page quotes: \"%s\"%n%nThe compiler said:%n%n".formatted(quoted.fragment())
              + listing(reports)
              + assembled(compiled.unit()));
    }
  }

  @Test
  @DisplayName("the gate does not shrink")
  void theGateDoesNotShrink() throws IOException {
    int verified = (int) verifiedSnippets().count();
    assertThat(verified)
        .as(
            """
            The number of marked snippets dropped below the floor.

            A snippet was un-marked rather than fixed, which defeats the gate. If a \
            snippet genuinely cannot be verified any more, lower MINIMUM_VERIFIED_SNIPPETS \
            deliberately and say why in the commit message.""")
        .isGreaterThanOrEqualTo(MINIMUM_VERIFIED_SNIPPETS);
  }

  @Test
  @DisplayName("the diagnostic gate does not shrink")
  void theDiagnosticGateDoesNotShrink() throws IOException {
    long quotingSnippets =
        verifiedSnippets()
            .filter(c -> !(c.snippet().expectation() instanceof Expectation.Compiles))
            .count();
    assertThat(quotingSnippets)
        .as(
            """
            The number of snippets quoting a diagnostic dropped below the floor.

            A `verify:rejects` or `verify:reports` marker was removed rather than the page \
            being brought back into line with the processor. These are the only checks holding \
            the pages that document refusals to what the processor actually says.""")
        .isGreaterThanOrEqualTo(MINIMUM_DIAGNOSTIC_SNIPPETS);
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

  /** The assembled unit, and everything javac and the processor said about it. */
  private record Compiled(String unit, List<Diagnostic<? extends JavaFileObject>> diagnostics) {

    List<Diagnostic<? extends JavaFileObject>> ofKind(Diagnostic.Kind... kinds) {
      List<Diagnostic.Kind> wanted = List.of(kinds);
      return diagnostics.stream().filter(d -> wanted.contains(d.getKind())).toList();
    }
  }

  private static Compiled compile(Case testCase) throws IOException {
    SnippetExtractor.Page page = testCase.page();
    String unit = SnippetExtractor.toCompilationUnit(page, testCase.snippet(), fixtureFor(page));
    return new Compiled(
        unit, compile(page.slug() + "_" + testCase.snippet().index(), unit).getDiagnostics());
  }

  private static String message(Diagnostic<? extends JavaFileObject> diagnostic) {
    return String.valueOf(diagnostic.getMessage(Locale.ROOT));
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

  private static String header(Case testCase, String verdict) {
    return "%nThe code in %s %s.%n%n".formatted(testCase, verdict);
  }

  private static String listing(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    StringBuilder message = new StringBuilder();
    diagnostics.forEach(
        d ->
            message
                .append("  ")
                .append(d.getKind().toString().toLowerCase(Locale.ROOT).replace('_', ' '))
                .append(": ")
                .append(message(d))
                .append("%n    at assembled line %d%n".formatted(d.getLineNumber())));
    return message.toString();
  }

  /** A javac error tells you nothing without the line it fired on, so print the assembled unit. */
  private static String assembled(String unit) {
    StringBuilder message = new StringBuilder("\n--- assembled from the page's snippet ---\n");
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
