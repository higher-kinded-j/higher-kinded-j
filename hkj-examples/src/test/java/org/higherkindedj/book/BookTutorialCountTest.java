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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Holds every tutorial exercise count in the book, and in the root README, to the tutorial code.
 *
 * <p>An exercise is a {@code @Test} method named {@code exercise…} or {@code diagnostic…}, as the
 * tutorial style guide's Exercise Counts rule has it; a worked example under another name is not
 * one. A journey page lists its tutorial files in an {@code <!-- exercises: ... -->} comment under
 * its {@code **Tutorials**:} line, and every other tutorial file is on {@link #STANDALONE}.
 *
 * <p>The checks: a journey's {@code **Tutorials**:} and {@code **Exercises**:} figures match its
 * files, as does each {@code **File**:} line; every {@code answerRequired()} placeholder sits in a
 * method named as an exercise; and every "N exercises" in the book, outside code, either sits
 * beside the link whose count it gives, matching that journey, track or file, or fails as an
 * unchecked count. Released pages under {@code release-history/} are left as they were published.
 */
@DisplayName("every tutorial exercise count matches the tutorial code")
class BookTutorialCountTest {

  private static final Path BOOK = Path.of(required("hkj.book.dir"));
  private static final Path TUTORIALS = Path.of(required("hkj.tutorials.dir"));
  private static final Path README = Path.of(required("hkj.readme"));

  /** Removing a journey's marker would otherwise drop its counts from the gate unnoticed. */
  private static final int MINIMUM_JOURNEYS = 19;

  /** Tutorial files that belong to no journey: a new file joins a journey's marker or this list. */
  private static final Set<String> STANDALONE =
      Set.of(
          "concurrency/TutorialVStream",
          "concurrency/TutorialVStreamAdvanced",
          "concurrency/TutorialVStreamHKT",
          "concurrency/TutorialVStreamParallel",
          "concurrency/TutorialVStreamPath",
          "expression/Tutorial03_ForTraverseComprehension",
          "expression/Tutorial04_EnhancedOpticsIntegration",
          "expression/Tutorial05_ZoomAndMagnify");

  private static final Pattern SITE =
      Pattern.compile("https://higher-kinded-j\\.github\\.io/[^/]+/(.*)");
  private static final String SOURCE =
      "https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/"
          + "org/higherkindedj/tutorial/";

  private static final Pattern MARKER = Pattern.compile("<!--\\s*exercises:\\s*(.*?)\\s*-->");
  private static final Pattern TUTORIALS_LINE = Pattern.compile("(?m)^\\*\\*Tutorials\\*\\*:.*$");
  private static final Pattern TUTORIALS_FIGURE =
      Pattern.compile("\\*\\*Tutorials\\*\\*:\\s*(\\d+)");
  private static final Pattern EXERCISES_FIGURE =
      Pattern.compile("\\*\\*Exercises\\*\\*:\\s*([^|(\\n]*)");
  private static final Pattern FILE_LINE =
      Pattern.compile(
          "\\*\\*File\\*\\*:\\s*`(\\w+)\\.java`\\s*\\|\\s*\\*\\*Exercises\\*\\*:\\s*(\\d+)");

  /** A {@code @Test} annotation that begins a line, perhaps after others, and its method. */
  private static final Pattern TEST_METHOD =
      Pattern.compile(
          "(?m)^[\\t ]*(?:@\\w+(?:\\([^()\\n]*\\))?[\\t ]+)*@Test\\b[\\s\\S]*?\\bvoid\\s+(\\w+)\\s*\\(");

  private static final Pattern EXERCISE_NAME = Pattern.compile("(exercise|diagnostic)\\w*");
  private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
  private static final Pattern LINKED_COUNT =
      Pattern.compile("\\]\\(([^)\\s]+)\\)\\**\\s*(?:\\(([^()]*?)|:\\s*)(\\d+)\\s+exercises?\\b");
  private static final Pattern ANY_COUNT = Pattern.compile("(\\d+)\\s+exercises?\\b");

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
          Path file = TUTORIALS.resolve(entry + ".java");
          if (!Files.isRegularFile(file)) {
            throw new AssertionError(
                "%s lists %s, which is not a tutorial file"
                    .formatted(BOOK.relativize(page), entry));
          }
          files.put(entry, exercises(file));
        }
        found.add(new Journey(page.normalize(), files));
      }
    }
    return found.stream();
  }

  @Test
  @DisplayName("at least nineteen journeys list their files, and every tutorial file has one home")
  void everyTutorialFileHasOneHome() throws IOException {
    List<Journey> all = journeys().toList();
    assertThat(all)
        .as("journey pages with an <!-- exercises: --> marker; one may have lost its marker")
        .hasSizeGreaterThanOrEqualTo(MINIMUM_JOURNEYS);

    List<String> homes = new ArrayList<>(STANDALONE);
    all.forEach(journey -> homes.addAll(journey.files().keySet()));
    assertThat(homes)
        .as("a tutorial file is in one journey, or standalone")
        .doesNotHaveDuplicates();
    assertThat(new HashSet<>(homes))
        .as("every tutorial file is in a journey's marker or on STANDALONE")
        .containsExactlyInAnyOrderElementsOf(tutorialFiles());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("journeys")
  @DisplayName("a journey page's figures match its files")
  void journeyPageMatchesItsFiles(Journey journey) {
    String page = read(journey.page());
    Matcher line = TUTORIALS_LINE.matcher(page);
    assertThat(line.find()).as("%s has a **Tutorials**: line", journey).isTrue();

    Matcher tutorials = TUTORIALS_FIGURE.matcher(line.group());
    assertThat(tutorials.find()).as("%s gives its tutorials as a number", journey).isTrue();
    assertThat(Integer.parseInt(tutorials.group(1)))
        .as("%s: **Tutorials**: against the files its marker lists", journey)
        .isEqualTo(journey.files().size());

    Matcher exercises = EXERCISES_FIGURE.matcher(line.group());
    assertThat(exercises.find()).as("%s gives **Exercises**: on that line", journey).isTrue();
    assertThat(exercises.group(1).strip())
        .as("%s: **Exercises**: is the exact sum over its files", journey)
        .isEqualTo(String.valueOf(journey.total()));

    Matcher fileLine = FILE_LINE.matcher(page);
    while (fileLine.find()) {
      String stem = fileLine.group(1);
      Optional<String> listed =
          journey.files().keySet().stream().filter(f -> f.endsWith("/" + stem)).findFirst();
      assertThat(listed).as("%s lists %s.java in its marker", journey, stem).isPresent();
      assertThat(Integer.parseInt(fileLine.group(2)))
          .as("%s: **File**: %s.java", journey, stem)
          .isEqualTo(journey.files().get(listed.get()));
    }
  }

  @Test
  @DisplayName("every answerRequired() placeholder sits in a method named as an exercise")
  void placeholdersAreInExercises() throws IOException {
    List<String> misnamed = new ArrayList<>();
    for (String file : tutorialFiles()) {
      String code = code(read(TUTORIALS.resolve(file + ".java")));
      Matcher method = TEST_METHOD.matcher(code);
      while (method.find()) {
        String body = body(code, code.indexOf('{', method.end()));
        if (body.contains("answerRequired(") && !EXERCISE_NAME.matcher(method.group(1)).matches()) {
          misnamed.add(file + "." + method.group(1));
        }
      }
    }
    assertThat(misnamed)
        .as("methods holding a placeholder; name each exerciseN_… or diagnostic_…")
        .isEmpty();
  }

  @Test
  @DisplayName("every exercise count in the book and README is beside its link, and matches")
  void everyCountMatches() throws IOException {
    Map<Path, Integer> journeyTotals = new LinkedHashMap<>();
    journeys().forEach(j -> journeyTotals.put(j.page(), j.total()));

    List<String> problems = new ArrayList<>();
    List<Path> pages = new ArrayList<>(bookPages());
    pages.add(README);
    for (Path page : pages) {
      String text = prose(read(page));
      checkTables(page, text, journeyTotals, problems);
      checkCounts(page, text, journeyTotals, problems);
    }
    assertThat(problems).as("exercise counts the tutorial code does not bear out").isEmpty();
  }

  private static void checkTables(
      Path page, String text, Map<Path, Integer> totals, List<String> problems) {
    String[] lines = text.split("\n", -1);
    int column = -1;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].strip();
      if (!line.startsWith("|")) {
        column = -1;
        continue;
      }
      List<String> cells = cells(line);
      if (column < 0) {
        column = indexOfExercises(cells);
        continue;
      }
      if (line.matches("\\|[\\s:|-]+\\|") || column >= cells.size()) {
        continue;
      }
      Set<Integer> expected = new HashSet<>();
      Matcher link = LINK.matcher(line);
      while (link.find()) {
        expectedFor(page, link.group(1), totals).ifPresent(expected::add);
      }
      String cell = cells.get(column);
      if (expected.size() > 1) {
        problems.add(
            "%s:%d links pages with different counts in one row".formatted(where(page), i + 1));
      } else if (expected.size() == 1 && !cell.equals(String.valueOf(expected.iterator().next()))) {
        problems.add(
            "%s:%d says %s exercises, expected %d"
                .formatted(where(page), i + 1, cell, expected.iterator().next()));
      }
    }
  }

  private static void checkCounts(
      Path page, String text, Map<Path, Integer> totals, List<String> problems) {
    Set<Integer> checked = new HashSet<>();
    Matcher linked = LINKED_COUNT.matcher(text);
    while (linked.find()) {
      checked.add(linked.start(3));
      int line = lineOf(text, linked.start());
      Optional<Integer> expected = expectedFor(page, linked.group(1), totals);
      if (linked.group(2) != null && linked.group(2).contains("~")) {
        problems.add("%s:%d gives an approximate count".formatted(where(page), line));
      } else if (expected.isEmpty()) {
        problems.add(
            "%s:%d gives a count beside a link to nothing counted".formatted(where(page), line));
      } else if (Integer.parseInt(linked.group(3)) != expected.get()) {
        problems.add(
            "%s:%d says %s exercises, expected %d"
                .formatted(where(page), line, linked.group(3), expected.get()));
      }
    }
    Matcher any = ANY_COUNT.matcher(text);
    while (any.find()) {
      String line = lineAt(text, any.start());
      boolean journeyFigure = line.contains("**File**:") || line.startsWith("**Tutorials**:");
      if (!checked.contains(any.start(1)) && !journeyFigure) {
        problems.add(
            "%s:%d gives an unchecked count: put it beside the link it counts"
                .formatted(where(page), lineOf(text, any.start())));
      }
    }
  }

  private static int indexOfExercises(List<String> header) {
    for (int i = 0; i < header.size(); i++) {
      if (header.get(i).contains("Exercise")) {
        return i;
      }
    }
    return -1;
  }

  /** What a link's count must say: a journey's total, a track's sum, or a file's count. */
  private static Optional<Integer> expectedFor(
      Path page, String target, Map<Path, Integer> totals) {
    String path = target.split("#", 2)[0];
    if (path.startsWith(SOURCE)) {
      Path file = TUTORIALS.resolve(path.substring(SOURCE.length()));
      return Files.isRegularFile(file) ? Optional.of(exercises(file)) : Optional.empty();
    }
    Matcher site = SITE.matcher(path);
    if (site.matches()) {
      String relative = site.group(1).replaceAll("\\.html$", ".md");
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
    Matcher method = TEST_METHOD.matcher(code(read(tutorial)));
    int count = 0;
    while (method.find()) {
      if (EXERCISE_NAME.matcher(method.group(1)).matches()) {
        count++;
      }
    }
    return count;
  }

  /** Every tutorial source, as a path under the tutorial package without {@code .java}. */
  private static List<String> tutorialFiles() throws IOException {
    try (Stream<Path> files = Files.walk(TUTORIALS)) {
      return files
          .filter(f -> f.toString().endsWith(".java"))
          .filter(f -> !TUTORIALS.relativize(f).startsWith("solutions"))
          .map(f -> TUTORIALS.relativize(f).toString().replace('\\', '/'))
          .map(f -> f.substring(0, f.length() - ".java".length()))
          .sorted()
          .toList();
    }
  }

  /**
   * Java source with its comments and its string, text-block and character literals blanked, so
   * that a test commented out, or a word in a display name, is not read as code. Line breaks are
   * kept.
   */
  static String code(String source) {
    StringBuilder out = new StringBuilder(source.length());
    int i = 0;
    while (i < source.length()) {
      char c = source.charAt(i);
      if (source.startsWith("//", i)) {
        int end = source.indexOf('\n', i);
        i = blank(source, out, i, end < 0 ? source.length() : end);
      } else if (source.startsWith("/*", i)) {
        int end = source.indexOf("*/", i + 2);
        i = blank(source, out, i, end < 0 ? source.length() : end + 2);
      } else if (source.startsWith("\"\"\"", i)) {
        int end = source.indexOf("\"\"\"", i + 3);
        i = blank(source, out, i, end < 0 ? source.length() : end + 3);
      } else if (c == '"' || c == '\'') {
        int end = i + 1;
        while (end < source.length() && source.charAt(end) != c && source.charAt(end) != '\n') {
          end += source.charAt(end) == '\\' ? 2 : 1;
        }
        out.append(c);
        i = blank(source, out, i + 1, Math.min(end, source.length()));
        if (i < source.length() && source.charAt(i) == c) {
          out.append(c);
          i++;
        }
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  private static int blank(String source, StringBuilder out, int from, int to) {
    for (int k = from; k < to; k++) {
      out.append(source.charAt(k) == '\n' ? '\n' : ' ');
    }
    return to;
  }

  /** Markdown with its code blocks blanked; an admonition's body is prose, and stays. */
  private static String prose(String markdown) {
    StringBuilder out = new StringBuilder();
    String fence = null;
    int admonitions = 0;
    for (String line : markdown.split("\n", -1)) {
      String trimmed = line.strip();
      if (fence != null) {
        if (trimmed.startsWith(fence)) {
          fence = null;
        }
        out.append('\n');
        continue;
      }
      if (trimmed.startsWith("~~~admonish")) {
        admonitions++;
      } else if (trimmed.equals("~~~") && admonitions > 0) {
        admonitions--;
      } else if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
        fence = trimmed.startsWith("```") ? "```" : "~~~";
        out.append('\n');
        continue;
      }
      out.append(line).append('\n');
    }
    return out.toString();
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

  private static int lineOf(String text, int index) {
    return (int) text.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
  }

  private static String lineAt(String text, int index) {
    int start = text.lastIndexOf('\n', index) + 1;
    int end = text.indexOf('\n', index);
    return text.substring(start, end < 0 ? text.length() : end).strip();
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

  /** A system property the Gradle task sets, or a message saying to run through Gradle. */
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
