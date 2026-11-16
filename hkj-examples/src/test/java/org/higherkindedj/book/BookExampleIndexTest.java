// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the Example Index to {@code org.higherkindedj.example.basic}: every class there that the
 * {@code java} launcher can run has one row, and every row links a source file that exists, under
 * the name of the class it holds. A new runnable example fails the test until the page lists it.
 */
@DisplayName("the Example Index lists every runnable basic example")
class BookExampleIndexTest {

  private static final Path PAGE =
      Path.of(required("hkj.book.dir")).resolve("hkts/hkt_basic_examples.md");

  private static final Path BASIC = Path.of(required("hkj.basic.examples.dir"));

  private static final String SOURCE =
      "https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/"
          + "org/higherkindedj/example/basic/";

  /** A row's first cell: the class name as code, linking its source. */
  private static final Pattern ROW =
      Pattern.compile(
          "^\\| \\[`(\\w+)`\\]\\(" + Pattern.quote(SOURCE) + "((?:\\w+/)*(\\w+)\\.java)\\) \\|.*");

  private static final String PACKAGE = "org.higherkindedj.example.basic.";

  @Test
  @DisplayName("every class the launcher can run has one row")
  void everyRunnableClassHasOneRow() {
    assertThat(rows().map(row -> row.group(2)).toList())
        .as("sources linked from %s, relative to %s", PAGE, BASIC)
        .containsExactlyInAnyOrderElementsOf(runnable());
  }

  @Test
  @DisplayName("every row names the class whose source it links")
  void everyRowNamesItsClass() {
    rows()
        .forEach(
            row ->
                assertThat(row.group(1))
                    .as("the link text of the row for %s", row.group(2))
                    .isEqualTo(row.group(3)));
  }

  @Test
  @DisplayName("every row that links an example is in the shape the test reads")
  void everyExampleRowIsRead() {
    List<String> exampleRows = read(PAGE).lines().filter(line -> line.startsWith("| [`")).toList();

    assertThat(exampleRows).as("example rows on %s", PAGE).isNotEmpty();
    assertThat(exampleRows).allSatisfy(line -> assertThat(line).matches(ROW));
  }

  /** The page's example rows, matched against {@link #ROW}. */
  private static Stream<Matcher> rows() {
    return read(PAGE).lines().map(ROW::matcher).filter(Matcher::matches);
  }

  /**
   * The sources under {@link #BASIC} whose top-level class the {@code java} launcher can run,
   * relative to it. Each is read from its compiled class rather than its text, so a {@code main} in
   * a comment or a nested class does not count, and every modifier order does.
   */
  private static List<String> runnable() {
    try (Stream<Path> files = Files.walk(BASIC)) {
      List<String> sources =
          files
              .map(file -> BASIC.relativize(file).toString().replace('\\', '/'))
              .filter(source -> source.endsWith(".java"))
              .filter(source -> !source.endsWith("package-info.java"))
              .filter(BookExampleIndexTest::launchable)
              .sorted()
              .toList();
      assertThat(sources).as("runnable examples under %s", BASIC).isNotEmpty();
      return sources;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Whether the class in {@code source} declares a {@code main} the launcher accepts: not private,
   * returning {@code void}, taking a {@code String[]} or nothing, and static or, as Java 25 allows,
   * an instance method.
   */
  private static boolean launchable(String source) {
    String name =
        PACKAGE + source.substring(0, source.length() - ".java".length()).replace('/', '.');
    try {
      Class<?> type = Class.forName(name, false, BookExampleIndexTest.class.getClassLoader());
      return Arrays.stream(type.getDeclaredMethods()).anyMatch(BookExampleIndexTest::isMain);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("no compiled class for " + source, e);
    }
  }

  private static boolean isMain(Method method) {
    return method.getName().equals("main")
        && !Modifier.isPrivate(method.getModifiers())
        && method.getReturnType() == void.class
        && (method.getParameterCount() == 0
            || Arrays.equals(method.getParameterTypes(), new Class<?>[] {String[].class}));
  }

  private static String read(Path file) {
    try {
      return Files.readString(file).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The directory is supplied by the Gradle task. Run straight from an IDE the property is unset;
   * this turns the opaque failure into a message that says how to run the test.
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
