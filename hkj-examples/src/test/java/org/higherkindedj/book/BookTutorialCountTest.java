// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Holds every tutorial exercise count in the book, and in README, to the tutorial code.
 *
 * <p>An exercise is a {@code @Test} method named {@code exercise…} or {@code diagnostic…}, as the
 * tutorial style guide's naming rule has it; a worked example under another name is not one. A
 * journey page lists its tutorial files in an {@code <!-- exercises: ... -->} comment, and its
 * {@code **Exercises**:} figure is the sum over them. Every table row and every "(N exercises)"
 * that links to a journey page, a track's introduction or a tutorial file gives the same number, so
 * a count cannot drift from the code it describes.
 */
@DisplayName("every tutorial exercise count matches the tutorial code")
class BookTutorialCountTest {

  private static final Path BOOK = Path.of(required("hkj.book.dir"));
  private static final Path TUTORIALS = Path.of(required("hkj.tutorials.dir"));
  private static final Path README = Path.of(required("hkj.readme"));

  /** Removing a journey's marker would otherwise drop its counts from the gate unnoticed. */
  private static final int MINIMUM_JOURNEYS = 19;

  private static final String SITE = "https://higher-kinded-j.github.io/latest/";
  private static final String SOURCE =
      "https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/"
          + "org/higherkindedj/tutorial/";

  private static final Pattern MARKER = Pattern.compile("<!--\\s*exercises:\\s*(.*?)\\s*-->");
  private static final Pattern HEADER = Pattern.compile("\\*\\*Exercises\\*\\*:\\s*(\\d+)");
  private static final Pattern FILE_LINE =
      Pattern.compile(
          "\\*\\*File\\*\\*:\\s*`(\\w+)\\.java`\\s*\\|\\s*\\*\\*Exercises\\*\\*:\\s*(\\d+)");

  /** A {@code @Test} annotation at the start of a line, and the method it marks. */
  private static final Pattern TEST_METHOD =
      Pattern.compile("(?m)^\\s*@Test\\b[\\s\\S]*?\\bvoid\\s+(\\w+)\\s*\\(");

  private static final Pattern EXERCISE_NAME = Pattern.compile("(exercise|diagnostic)\\w*");
  private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
  private static final Pattern LINK_THEN_COUNT =
      Pattern.compile("\\]\\(([^)\\s]+)\\)\\**\\s*\\(([^()]*?)(~?)(\\d+) exercises");

  /** A journey page, the tutorial files it lists, and the exercises they hold. */
  record Journey(Path page, Map<String, Integer> files) {
    int total() {
      return files.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public String toString() {
      return BOOK.relativize(page).toString();
    }
  }

  static Stream<Journey> journeys() throws IOException {
    List<Journey> found = new ArrayList<>();
    for (Path page : bookPages()) {
      Matcher marker = MARKER.matcher(read(page));
      if (marker.find()) {
        Map<String, Integer> files = new LinkedHashMap<>();
        for (String entry : marker.group(1).split("\\s+")) {
          files.put(entry, exercises(TUTORIALS.resolve(entry + ".java")));
        }
        found.add(new Journey(page, files));
      }
    }
    return found.stream();
  }

  @Test
  @DisplayName("every journey page lists its tutorial files")
  void journeysAreDeclared() throws IOException {
    List<Journey> all = journeys().toList();

    assertThat(all).hasSizeGreaterThanOrEqualTo(MINIMUM_JOURNEYS);
    List<String> listed = all.stream().flatMap(j -> j.files().keySet().stream()).toList();
    assertThat(listed).as("a tutorial file belongs to one journey").doesNotHaveDuplicates();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("journeys")
  @DisplayName("a journey page's counts match its files")
  void journeyPageMatchesItsFiles(Journey journey) {
    String page = read(journey.page());

    Matcher header = HEADER.matcher(page);
    assertThat(header.find()).as("%s has an **Exercises**: line", journey).isTrue();
    assertThat(Integer.parseInt(header.group(1)))
        .as("%s total", journey)
        .isEqualTo(journey.total());

    Matcher fileLine = FILE_LINE.matcher(page);
    while (fileLine.find()) {
      String stem = fileLine.group(1);
      Optional<String> listed =
          journey.files().keySet().stream().filter(f -> f.endsWith("/" + stem)).findFirst();
      assertThat(listed).as("%s lists %s.java", journey, stem).isPresent();
      assertThat(Integer.parseInt(fileLine.group(2)))
          .as("%s: %s.java", journey, stem)
          .isEqualTo(journey.files().get(listed.get()));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("journeys")
  @DisplayName("every placeholder sits in a method named as an exercise")
  void placeholdersAreInExercises(Journey journey) {
    for (String file : journey.files().keySet()) {
      String source = read(TUTORIALS.resolve(file + ".java"));
      Matcher method = TEST_METHOD.matcher(source);
      while (method.find()) {
        String body = body(source, source.indexOf('{', method.end()));
        if (body.contains("answerRequired(")) {
          assertThat(method.group(1)).as("%s.%s", file, method.group(1)).matches(EXERCISE_NAME);
        }
      }
    }
  }

  @Test
  @DisplayName("every count that links to a journey, a track or a tutorial file matches")
  void everyLinkedCountMatches() throws IOException {
    Map<Path, Integer> journeyTotals = new LinkedHashMap<>();
    journeys().forEach(j -> journeyTotals.put(j.page().normalize(), j.total()));

    List<String> mismatches = new ArrayList<>();
    List<Path> pages = new ArrayList<>(bookPages());
    pages.add(README);
    for (Path page : pages) {
      checkTables(page, journeyTotals, mismatches);
      checkInlineCounts(page, journeyTotals, mismatches);
    }
    assertThat(mismatches).isEmpty();
  }

  private static void checkTables(Path page, Map<Path, Integer> totals, List<String> mismatches) {
    List<String> lines = Arrays.asList(read(page).split("\n", -1));
    int column = -1;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (!line.startsWith("|")) {
        column = -1;
        continue;
      }
      List<String> cells = cells(line);
      if (column < 0) {
        column = cells.indexOf("Exercises");
        continue;
      }
      if (line.matches("\\|[\\s:|-]+\\|") || column >= cells.size()) {
        continue;
      }
      Optional<Integer> expected = expectedForLinks(page, line, totals);
      String cell = cells.get(column);
      if (expected.isPresent() && !cell.equals(String.valueOf(expected.get()))) {
        mismatches.add(
            "%s:%d says %s exercises, expected %d"
                .formatted(where(page), i + 1, cell, expected.get()));
      }
    }
  }

  private static void checkInlineCounts(
      Path page, Map<Path, Integer> totals, List<String> mismatches) {
    String text = read(page);
    Matcher m = LINK_THEN_COUNT.matcher(text);
    while (m.find()) {
      Optional<Integer> expected = expectedFor(page, m.group(1), totals);
      int line = (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
      if (!m.group(3).isEmpty()) {
        mismatches.add("%s:%d gives an approximate count".formatted(where(page), line));
      }
      int said = Integer.parseInt(m.group(4));
      if (expected.isPresent() && said != expected.get()) {
        mismatches.add(
            "%s:%d says %d exercises, expected %d"
                .formatted(where(page), line, said, expected.get()));
      }
    }
  }

  private static Optional<Integer> expectedForLinks(
      Path page, String row, Map<Path, Integer> totals) {
    Matcher link = LINK.matcher(row);
    while (link.find()) {
      Optional<Integer> expected = expectedFor(page, link.group(1), totals);
      if (expected.isPresent()) {
        return expected;
      }
    }
    return Optional.empty();
  }

  /** What a link's "N exercises" must say: a journey's total, a track's sum, or a file's count. */
  private static Optional<Integer> expectedFor(
      Path page, String target, Map<Path, Integer> totals) {
    String path = target.split("#", 2)[0];
    if (path.startsWith(SOURCE)) {
      return Optional.of(exercises(TUTORIALS.resolve(path.substring(SOURCE.length()))));
    }
    if (path.startsWith(SITE)) {
      String relative = path.substring(SITE.length()).replaceAll("\\.html$", ".md");
      return expectedForPage(BOOK.resolve(relative).normalize(), totals);
    }
    if (path.isEmpty() || path.contains("://")) {
      return Optional.empty();
    }
    return expectedForPage(page.getParent().resolve(path).normalize(), totals);
  }

  private static Optional<Integer> expectedForPage(Path target, Map<Path, Integer> totals) {
    if (totals.containsKey(target)) {
      return Optional.of(totals.get(target));
    }
    // A track's introduction stands for the journeys in its directory.
    if (target.getFileName().toString().equals("ch_intro.md")
        && target.startsWith(BOOK.resolve("tutorials"))
        && !target.getParent().equals(BOOK.resolve("tutorials"))) {
      int sum =
          totals.entrySet().stream()
              .filter(e -> e.getKey().getParent().equals(target.getParent()))
              .mapToInt(Map.Entry::getValue)
              .sum();
      return sum > 0 ? Optional.of(sum) : Optional.empty();
    }
    return Optional.empty();
  }

  /** The exercises in one tutorial file: its {@code @Test} methods named as exercises. */
  private static int exercises(Path tutorial) {
    Matcher method = TEST_METHOD.matcher(read(tutorial));
    int count = 0;
    while (method.find()) {
      if (EXERCISE_NAME.matcher(method.group(1)).matches()) {
        count++;
      }
    }
    return count;
  }

  private static String body(String source, int open) {
    int depth = 0;
    for (int i = open; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(open, i + 1);
      }
    }
    return source.substring(open);
  }

  private static List<String> cells(String row) {
    if (row.length() < 2) {
      return List.of();
    }
    String inner = row.substring(1, row.endsWith("|") ? row.length() - 1 : row.length());
    return Arrays.stream(inner.split("\\|", -1)).map(String::strip).toList();
  }

  private static List<Path> bookPages() throws IOException {
    try (Stream<Path> pages = Files.walk(BOOK)) {
      return pages
          .filter(p -> p.toString().endsWith(".md"))
          .filter(p -> !p.startsWith(BOOK.resolve("release-history")))
          .map(Path::normalize)
          .sorted()
          .toList();
    }
  }

  private static String where(Path page) {
    return page.equals(README) ? "README.md" : BOOK.relativize(page).toString();
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
    if (value == null) {
      throw new IllegalStateException(
          "System property '%s' is not set. Run this via Gradle: `gradle :hkj-examples:bookVerify`."
              .formatted(property));
    }
    return value;
  }
}
