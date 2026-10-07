// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds every statement of the optic composition table to the {@code andThen} overloads the optics
 * declare.
 *
 * <p>For each pair of optic types the table answers one question: what does {@code
 * first.andThen(second)} return? This test reads the answer from the interfaces themselves. For
 * each pair it finds the {@code andThen} overload whose parameter is the second optic type and
 * records its return type; a pair with no such overload falls back to {@code Optic.andThen} and is
 * rendered as {@code Optic}.
 *
 * <p>Three things are held to that reading: the golden table the Composition Rules page includes,
 * the copy of it in the {@code hkj-optics} skill, which is installed into users' projects, and
 * every {@code X.andThen(Y) = Z} statement on either page, whether in a summary row, a heading or a
 * code comment. So a new or removed overload fails here until the book and the skill say the same.
 * The chapter and the skill spell a composition as Java does, so the Haskell {@code >>>} notation
 * is refused on every Optics page and every {@code hkj-optics} skill page.
 */
@DisplayName("the book's composition table is read from the andThen overloads")
class BookCompositionTableTest {

  /** The optic types the table covers, in the order the page lists them. */
  private static final List<Class<?>> OPTICS =
      List.of(Iso.class, Lens.class, Prism.class, Affine.class, Traversal.class);

  /** The golden file composition_rules.md includes. */
  private static final String GOLDEN = "/golden/optics-composition-table.md.golden";

  private static final Path BOOK_PAGE =
      Path.of(required("hkj.book.dir")).resolve("optics/composition_rules.md");

  private static final Path SKILL_PAGE =
      Path.of(required("hkj.skills.dir")).resolve("hkj-optics/reference/composition-rules.md");

  /**
   * One composition claim written in prose: {@code Lens.andThen(Prism) = Affine} in a heading or
   * comment, or {@code | Lens.andThen(Prism) | Affine |} in a table row. {@code Any} stands for
   * every optic type, and "Same as second" for the second operand.
   */
  private static final Pattern CLAIM =
      Pattern.compile(
          "\\b(Iso|Lens|Prism|Affine|Traversal|Any|any)\\.andThen\\((Iso|Lens|Prism|Affine|Traversal|Any|any)\\)"
              + "\\s*(?:=|\\|)\\s*\\**(Iso|Lens|Prism|Affine|Traversal|Same as second|Same as 2nd)\\b");

  /** A composition written in the Haskell notation, {@code Lens >>> Prism}. */
  private static final Pattern ARROWS =
      Pattern.compile(
          "\\b(Iso|Lens|Prism|Affine|Traversal|Any|any)\\s*>>>\\s*(Iso|Lens|Prism|Affine|Traversal|Any|any)\\b");

  @Test
  @DisplayName("the golden table matches what each andThen overload returns")
  void goldenTableMatchesTheOverloads() {
    String rendered = render();
    assertThat(normalised(golden()))
        .as(
            """
            The composition table on hkj-book/src/optics/composition_rules.md no longer matches \
            the andThen overloads. If the API change is intended, replace \
            hkj-examples/src/test/resources%s with:

            %s""",
            GOLDEN, rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName("every pair of optics composes to an optic type, not a bare Optic")
  void everyPairHasAnOverload() {
    assertThat(render()).doesNotContain("`Optic`");
  }

  @Test
  @DisplayName("the hkj-optics skill carries the same table")
  void skillCarriesTheTable() {
    assertThat(normalised(read(SKILL_PAGE)))
        .as("%s must contain the composition table exactly as the overloads give it", SKILL_PAGE)
        .contains(render());
  }

  @Test
  @DisplayName(
      "every X.andThen(Y) claim on the book page and in the skill agrees with the overloads")
  void everyWrittenClaimAgrees() {
    List<String> wrong = new ArrayList<>();
    int checked = 0;
    for (Path page : List.of(BOOK_PAGE, SKILL_PAGE)) {
      List<String> lines = normalised(read(page)).lines().toList();
      for (int i = 0; i < lines.size(); i++) {
        Matcher m = CLAIM.matcher(lines.get(i));
        while (m.find()) {
          for (Class<?> first : operands(m.group(1))) {
            for (Class<?> second : operands(m.group(2))) {
              String claimed =
                  m.group(3).startsWith("Same as") ? second.getSimpleName() : m.group(3);
              String actual = resultOf(first, second);
              checked++;
              if (!claimed.equals(actual)) {
                wrong.add(
                    "%s:%d claims %s.andThen(%s) = %s, but andThen returns %s"
                        .formatted(
                            page.getFileName(),
                            i + 1,
                            first.getSimpleName(),
                            second.getSimpleName(),
                            claimed,
                            actual));
              }
            }
          }
        }
      }
    }
    assertThat(checked).as("composition claims read from the two pages").isGreaterThan(20);
    assertThat(wrong).as("composition claims the overloads contradict").isEmpty();
  }

  @Test
  @DisplayName("no Optics page and no hkj-optics skill page writes a composition as X >>> Y")
  void noHaskellArrows() {
    List<Path> pages = new ArrayList<>(markdownUnder(BOOK_PAGE.getParent()));
    pages.addAll(markdownUnder(SKILL_PAGE.getParent().getParent()));
    List<String> arrows = new ArrayList<>();
    for (Path page : pages) {
      List<String> lines = normalised(read(page)).lines().toList();
      for (int i = 0; i < lines.size(); i++) {
        if (ARROWS.matcher(lines.get(i)).find()) {
          arrows.add("%s:%d: %s".formatted(page.getFileName(), i + 1, lines.get(i).strip()));
        }
      }
    }
    assertThat(pages).as("pages scanned for X >>> Y").hasSizeGreaterThan(60);
    assertThat(arrows).as("compositions to spell as X.andThen(Y)").isEmpty();
  }

  /** Every markdown file at or below {@code dir}. */
  private static List<Path> markdownUnder(Path dir) {
    try (var files = Files.walk(dir)) {
      return files.filter(file -> file.toString().endsWith(".md")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The table as markdown: one row per first optic, one column per second. */
  private static String render() {
    String header =
        "| `first.andThen(second)` | "
            + OPTICS.stream().map(Class::getSimpleName).collect(Collectors.joining(" | "))
            + " |";
    String rule = "|---" + "|---".repeat(OPTICS.size()) + "|";
    String rows =
        OPTICS.stream()
            .map(
                first ->
                    "| **"
                        + first.getSimpleName()
                        + "** | "
                        + OPTICS.stream()
                            .map(second -> resultOf(first, second))
                            .collect(Collectors.joining(" | "))
                        + " |")
            .collect(Collectors.joining("\n"));
    return header + "\n" + rule + "\n" + rows + "\n";
  }

  /** What {@code first.andThen(second)} returns, or {@code Optic} when no overload takes it. */
  private static String resultOf(Class<?> first, Class<?> second) {
    return Arrays.stream(first.getMethods())
        .filter(m -> m.getName().equals("andThen") && !m.isBridge() && !m.isSynthetic())
        .filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == second)
        .map(Method::getReturnType)
        .map(Class::getSimpleName)
        .findFirst()
        .orElse("`Optic`");
  }

  /** The optic types an operand names: one, or all of them for {@code Any}. */
  private static List<Class<?>> operands(String name) {
    return name.equalsIgnoreCase("any")
        ? OPTICS
        : OPTICS.stream().filter(c -> c.getSimpleName().equals(name)).toList();
  }

  private static String golden() {
    try (InputStream in = BookCompositionTableTest.class.getResourceAsStream(GOLDEN)) {
      assertThat(in).as("the golden table %s is on the test classpath", GOLDEN).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path page) {
    try {
      return Files.readString(page);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A checkout with Windows line endings reads the same as one with Unix ones. */
  private static String normalised(String text) {
    return text.replace("\r\n", "\n");
  }

  /**
   * The directories are supplied by the Gradle task. Run straight from an IDE the property is
   * unset; this turns the opaque failure into a message that says how to run the test.
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
