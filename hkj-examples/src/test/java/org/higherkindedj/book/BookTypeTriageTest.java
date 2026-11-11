// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Writes the Choosing a Type table on Supported Types from the type cards the Core Types pages open
 * with, and holds the golden file the page includes to that reading.
 *
 * <p>Each row takes one card, in the order of the Core Types pages in {@code SUMMARY.md}: the
 * card's Replaces row, a link to the page, and the card's Effect Path row. A row in bold names each
 * family the Core Types introduction lists, before the rows of its pages. A JDK type's card
 * replaces nothing, since the type is Java's own, so its row takes the clause of the card's Choose
 * it when row before "; otherwise" instead. A link in a card to a heading on its own page gains the
 * page's name, so it still lands when Supported Types shows it. When a card changes, this test
 * fails and prints the replacement file.
 */
@DisplayName("Choosing a Type is read from the Core Types pages' type cards")
class BookTypeTriageTest {

  /** The rows of one page's type card that the table shows. */
  record Card(String page, String name, String replaces, String effectPath, String chooseItWhen) {

    /** What the reader writes today: the Replaces row, or for a JDK type when to choose it. */
    String yourCode() {
      int end = chooseItWhen.indexOf(OTHERWISE);
      if (!replaces.equals(JAVAS_OWN)) {
        return fromSupportedTypes(replaces);
      }
      return fromSupportedTypes(end < 0 ? chooseItWhen : chooseItWhen.substring(0, end));
    }

    /** The Effect Path row, as Supported Types shows it. */
    String path() {
      return fromSupportedTypes(effectPath);
    }

    /** A cell with each link to a heading on the card's page made to name the page. */
    private String fromSupportedTypes(String cell) {
      return cell.replace("](#", "](" + page + "#");
    }
  }

  /** The Replaces row of a JDK type's card, such as {@code Optional}'s. */
  private static final String JAVAS_OWN = "Nothing: it is Java's own type";

  /** What ends the first clause of a card's Choose it when row. */
  private static final String OTHERWISE = "; otherwise";

  /** How every card's Choose it when row ends, which the chapter guide's card shape gives. */
  private static final String CHOOSING_LINK =
      "compare every type in [Choosing a Type](supported-types.md#choosing-a-type)";

  /** The golden file, under the test resources. */
  private static final String GOLDEN = "foundations-triage.md.golden";

  /** The heading the table sits under, whose id the type cards link to. */
  private static final String HEADING = "## Choosing a Type {#choosing-a-type}";

  private static final Path BOOK = BookFiles.book();

  private static final Pattern CARD_TITLE =
      Pattern.compile("^~~~admonish abstract title=\"(.+) at a glance\"$");

  private static final Pattern CARD_ROW = Pattern.compile("^\\| \\*\\*(.+?)\\*\\* \\| (.*) \\|$");

  private static final List<String> CARD_ROWS =
      List.of("Replaces", "Effect Path", "Choose it when");

  private static final Pattern FAMILY = Pattern.compile("^- \\*\\*(.+)\\*\\*$");

  private static final Pattern LISTED_PAGE = Pattern.compile("^ {2}- \\[[^]]+]\\(([^)#]+)\\)");

  @Test
  @DisplayName("the golden table is the one the type cards give")
  void goldenMatchesTheCards() {
    String rendered = render();
    assertThat(BookFiles.golden(GOLDEN))
        .as(
            """
            Choosing a Type no longer matches the Core Types pages' type cards. If the change is \
            intended, replace hkj-examples/src/test/resources/golden/%s with:

            %s""",
            GOLDEN, rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName("every Core Types page opens with a type card holding the rows the table reads")
  void everyPageHasACard() {
    List<String> problems = new ArrayList<>();
    for (String page : BookFiles.coreTypePages(BOOK)) {
      Optional<Map<String, String>> rows = cardRows(page);
      if (rows.isEmpty()) {
        problems.add(page + ": no type card before its first ## heading");
        continue;
      }
      CARD_ROWS.stream()
          .filter(row -> !rows.get().containsKey(row))
          .forEach(row -> problems.add(page + ": its card has no " + row + " row"));
    }
    assertThat(problems).as("Core Types pages whose type card the table cannot read").isEmpty();
  }

  @Test
  @DisplayName("every card's Choose it when row names one neighbour, then links Choosing a Type")
  void everyCardFollowsTheShape() {
    for (String page : BookFiles.coreTypePages(BOOK)) {
      card(page)
          .ifPresent(
              card -> {
                assertThat(card.chooseItWhen())
                    .as("%s's Choose it when row", page)
                    .containsOnlyOnce(OTHERWISE)
                    .endsWith(CHOOSING_LINK);
                if (card.replaces().startsWith("Nothing")) {
                  assertThat(card.replaces()).as("%s's Replaces row", page).isEqualTo(JAVAS_OWN);
                }
              });
    }
  }

  @Test
  @DisplayName("the Core Types introduction lists every Core Types page under a family")
  void everyPageHasAFamily() {
    assertThat(families().keySet())
        .as("the pages listed under a family on monads/ch_intro.md")
        .containsExactlyElementsOf(BookFiles.coreTypePages(BOOK));
  }

  @Test
  @DisplayName("Supported Types includes the table under Choosing a Type")
  void supportedTypesIncludesTheTable() {
    String page = BookFiles.read(BOOK.resolve("monads/supported-types.md"));
    String include = BookFiles.include(GOLDEN, "triage");
    int heading = page.indexOf(HEADING);
    assertThat(heading).as("the heading %s", HEADING).isNotNegative();
    int nextHeading = page.indexOf("\n## ", heading + HEADING.length());
    String section = page.substring(heading, nextHeading < 0 ? page.length() : nextHeading);
    assertThat(section).as("the Choosing a Type section").contains(include);
  }

  /** The golden file as this test would write it, from the pages whose card it can read. */
  private static String render() {
    Map<String, String> families = families();
    StringBuilder out = new StringBuilder();
    out.append(
        "<!-- Written by BookTypeTriageTest from the Core Types pages' type cards. Do not edit:"
            + " the test prints the replacement. -->\n");
    out.append("<!-- ANCHOR: triage -->\n");
    out.append("| Your code today | Type | Effect Path |\n");
    out.append("|---|---|---|\n");
    String family = null;
    for (String page : BookFiles.coreTypePages(BOOK)) {
      Optional<Card> found = card(page);
      if (found.isEmpty()) {
        continue;
      }
      Card card = found.get();
      String pageFamily = families.getOrDefault(page, "");
      if (!pageFamily.equals(family)) {
        out.append("| **%s** | | |\n".formatted(pageFamily));
        family = pageFamily;
      }
      out.append(
          "| %s | [%s](%s) | %s |\n".formatted(card.yourCode(), card.name(), page, card.path()));
    }
    out.append("<!-- ANCHOR_END: triage -->\n");
    return out.toString();
  }

  /** The page's type card, when it has one holding every row the table reads. */
  private static Optional<Card> card(String page) {
    return cardRows(page)
        .filter(rows -> rows.keySet().containsAll(CARD_ROWS))
        .map(
            rows ->
                new Card(
                    page,
                    rows.get("name"),
                    rows.get("Replaces"),
                    rows.get("Effect Path"),
                    rows.get("Choose it when")));
  }

  /**
   * The rows of the page's type card, the {@code abstract} admonition titled "<i>Type</i> at a
   * glance" that opens the page before its first {@code ##} heading, with the type's name under
   * {@code name}.
   */
  private static Optional<Map<String, String>> cardRows(String page) {
    List<String> lines = BookFiles.read(BOOK.resolve("monads").resolve(page)).lines().toList();
    for (int i = 0; i < lines.size() && !lines.get(i).startsWith("## "); i++) {
      Matcher title = CARD_TITLE.matcher(lines.get(i));
      if (!title.matches()) {
        continue;
      }
      Map<String, String> rows = new LinkedHashMap<>();
      rows.put("name", title.group(1));
      for (String line : lines.subList(i + 1, lines.size())) {
        if (line.startsWith("~~~")) {
          break;
        }
        Matcher row = CARD_ROW.matcher(line);
        if (row.matches()) {
          assertThat(rows.put(row.group(1), row.group(2)))
              .as("%s's card has one %s row", page, row.group(1))
              .isNull();
        }
      }
      return Optional.of(rows);
    }
    return Optional.empty();
  }

  /**
   * Each Core Types page's family, from the Core Types introduction's list: a family's name in
   * bold, with its pages one level under it. The first link in an item is the page it lists, and
   * any other line in the list fails, so a mistyped family cannot move its pages into the one
   * before it.
   */
  private static Map<String, String> families() {
    List<String> lines = BookFiles.read(BOOK.resolve("monads/ch_intro.md")).lines().toList();
    int start = lines.indexOf("## Pages in this group {#pages-in-this-group}");
    assertThat(start).as("the Core Types introduction's list").isNotNegative();
    Map<String, String> families = new LinkedHashMap<>();
    List<String> seen = new ArrayList<>();
    String family = null;
    for (String line : lines.subList(start + 1, lines.size())) {
      if (line.startsWith("#") || line.equals("---")) {
        break;
      }
      Matcher heading = FAMILY.matcher(line);
      Matcher listed = LISTED_PAGE.matcher(line);
      if (heading.matches()) {
        family = heading.group(1);
        seen.add(family);
      } else if (listed.find()) {
        assertThat(family).as("a family above %s", line).isNotNull();
        assertThat(families.put(listed.group(1), family))
            .as("one family for %s", listed.group(1))
            .isNull();
      } else {
        assertThat(line.isBlank() || line.startsWith("    "))
            .as("a line of the Core Types list that is neither a family nor a page: %s", line)
            .isTrue();
      }
    }
    assertThat(seen).as("the families on the Core Types introduction").doesNotHaveDuplicates();
    return families;
  }
}
