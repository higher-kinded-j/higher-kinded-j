// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the FAQ's Rosetta table to the library: every type and member its Higher-Kinded-J column
 * names exists in {@code hkj-api} or {@code hkj-core}.
 *
 * <p>The column names each member with its owner, such as {@code `Monad.flatMap`}, so a renamed or
 * invented method fails the test. In a code span, a capitalised name of two letters or more is a
 * type; a single capital, such as {@code F}, is a type variable; and a name after a type and a dot
 * is a method, field or nested type of it. A span holding only a lowercase name, as in {@code
 * `Applicative`: `of`}, is a method of the type the cell named last. A cell must name at least one
 * type, unless it reads "None". Rows split on every pipe that is not escaped, code spans included,
 * as mdBook splits them.
 */
@DisplayName("the FAQ's Rosetta table names only types and members the library has")
class BookRosettaTest {

  private static final Path PAGE = BookFiles.book().resolve("hkts/faq.md");

  /** The heading the table sits under. */
  private static final String HEADING =
      "## I know Scala, Haskell or Vavr: what are the names here?"
          + " {#i-know-scala-haskell-or-vavr-what-are-the-names-here}";

  /** The table's header row, whose last column is Higher-Kinded-J's. */
  private static final String HEADER = "| Scala (Cats) | Haskell | Vavr | Higher-Kinded-J |";

  /** The table's rows today. A row lost to a stray line fails this floor; raise it with a row. */
  private static final int MINIMUM_ROWS = 33;

  /** A code span, such as {@code `Monad.flatMap`}. */
  private static final Pattern CODE = Pattern.compile("`([^`]+)`");

  /** A method named on its own, as in {@code `Applicative`: `of`}, of the type named before it. */
  private static final Pattern METHOD = Pattern.compile("[a-z]\\w*");

  /** A type, with the member named after it, as in {@code Monad.flatMap}. */
  private static final Pattern NAME =
      Pattern.compile("\\b([A-Z][A-Za-z0-9]+)(?:\\.([A-Za-z]\\w*))?");

  @Test
  @DisplayName("every type and member in the Higher-Kinded-J column exists")
  void everyNameExists() {
    Map<String, List<String>> types = libraryTypeNames();
    List<String> problems = new ArrayList<>();
    List<String> cells = libraryCells();
    assertThat(cells).as("rows of the Rosetta table").hasSizeGreaterThanOrEqualTo(MINIMUM_ROWS);
    for (String cell : cells) {
      if (cell.equals("None")) {
        continue;
      }
      int checked = 0;
      String lastType = null;
      Matcher code = CODE.matcher(cell);
      while (code.find()) {
        String span = code.group(1);
        Matcher name = NAME.matcher(span);
        boolean typed = false;
        while (name.find()) {
          checked++;
          typed = true;
          lastType = name.group(1);
          problem(span, lastType, name.group(2), types).ifPresent(problems::add);
        }
        if (!typed && METHOD.matcher(span).matches()) {
          if (lastType == null) {
            problems.add("`%s`: a method named before any type, in: %s".formatted(span, cell));
          } else {
            checked++;
            problem(span, lastType, span, types).ifPresent(problems::add);
          }
        }
      }
      if (checked == 0) {
        problems.add("a Higher-Kinded-J cell that names no type in code: " + cell);
      }
    }
    assertThat(problems).as("names in the FAQ's Rosetta table").isEmpty();
  }

  /** What is wrong with one name in a code span: no such public type, or no such member of it. */
  private static Optional<String> problem(
      String span, String type, String member, Map<String, List<String>> types) {
    List<Class<?>> candidates = new ArrayList<>();
    for (String className : types.getOrDefault(type, List.of())) {
      try {
        Class<?> loaded = Class.forName(className, false, BookRosettaTest.class.getClassLoader());
        if (Modifier.isPublic(loaded.getModifiers())) {
          candidates.add(loaded);
        }
      } catch (ClassNotFoundException | LinkageError e) {
        return Optional.of("`%s`: %s did not load: %s".formatted(span, className, e));
      }
    }
    if (candidates.isEmpty()) {
      return Optional.of("`%s`: no public type %s in hkj-api or hkj-core".formatted(span, type));
    }
    if (member != null && candidates.stream().noneMatch(c -> hasMember(c, member))) {
      return Optional.of("`%s`: %s has no member %s".formatted(span, type, member));
    }
    return Optional.empty();
  }

  /** The Higher-Kinded-J cell of each row of the table under {@link #HEADING}. */
  private static List<String> libraryCells() {
    List<String> lines = BookFiles.read(PAGE).lines().toList();
    int heading = lines.indexOf(HEADING);
    assertThat(heading).as("the heading %s", HEADING).isNotNegative();
    int header = lines.subList(heading, lines.size()).indexOf(HEADER);
    assertThat(header).as("the table header %s under %s", HEADER, HEADING).isNotNegative();
    List<String> cells = new ArrayList<>();
    for (String line : lines.subList(heading + header + 2, lines.size())) {
      if (!line.startsWith("|")) {
        break;
      }
      String[] columns = line.substring(1, line.lastIndexOf('|')).split("(?<!\\\\)\\|", -1);
      assertThat(columns).as("the four columns of: %s", line).hasSize(4);
      cells.add(columns[3].strip());
    }
    return cells;
  }

  private static boolean hasMember(Class<?> type, String member) {
    return Arrays.stream(type.getMethods()).anyMatch(m -> m.getName().equals(member))
        || Arrays.stream(type.getFields()).anyMatch(f -> f.getName().equals(member))
        || Arrays.stream(type.getClasses()).anyMatch(c -> c.getSimpleName().equals(member));
  }

  /**
   * The class names of the top-level types of {@code hkj-api} and {@code hkj-core} under {@code
   * org.higherkindedj}, by simple name, read from their class files without loading any. A name two
   * packages share keeps both.
   */
  private static Map<String, List<String>> libraryTypeNames() {
    Map<String, List<String>> types = new HashMap<>();
    for (Class<?> anchor : List.of(Functor.class, Either.class)) {
      try {
        Path root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (FileSystem jar = Files.isDirectory(root) ? null : FileSystems.newFileSystem(root)) {
          Path classes = jar == null ? root : jar.getPath("/");
          try (Stream<Path> files = Files.walk(classes.resolve("org/higherkindedj"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
              List<String> parts = new ArrayList<>();
              classes.relativize(file).forEach(part -> parts.add(part.toString()));
              String className = String.join(".", parts);
              className = className.substring(0, className.length() - ".class".length());
              if (!className.contains("$") && !className.endsWith("package-info")) {
                String simpleName = className.substring(className.lastIndexOf('.') + 1);
                types.computeIfAbsent(simpleName, k -> new ArrayList<>()).add(className);
              }
            }
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
    }
    assertThat(types).as("types found in hkj-api and hkj-core").containsKeys("Kind", "Either");
    return types;
  }
}
