// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.KindHelperLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Writes the Laws page's table of the laws {@code hkj-test} checks, from {@code hkj-api}'s type
 * classes and {@code hkj-test}'s law checkers, and holds the golden file the page includes to it.
 *
 * <p>Every type class {@link BookTypeClassHierarchyTest#typeClasses()} lists, and {@link Natural},
 * has a row, linked to the section of the book that states its laws. The row names the checker in
 * {@code org.higherkindedj.hkt.laws} named after the type class, such as {@code FunctorLaws}, and
 * what its public static {@code assert} methods check, in the order the class declares them, each
 * named from its method: {@code assertLeftIdentity} reads "left identity". A type class with no
 * checker reads "Not supported yet". A last row covers {@link KindHelperLaws}, which checks a
 * type's {@code widen} and {@code narrow}. Every checker in the package has a row, so a new checker
 * fails the test until it is placed, and a new type class fails it until it has a section. When a
 * checker gains or loses a law, this test fails and prints the replacement file.
 */
@DisplayName("the Laws page's table of checked laws is read from hkj-api and hkj-test")
class BookLawsTest {

  /** The golden file, under the test resources. */
  private static final String GOLDEN = "foundations-laws.md.golden";

  /** The heading the table sits under. */
  private static final String HEADING =
      "## Which laws `hkj-test` checks {#which-laws-hkj-test-checks}";

  /** The include the Laws page carries for the table. */
  private static final String INCLUDE = BookFiles.include(GOLDEN, "checked");

  private static final Path BOOK = BookFiles.book();

  /** The package the law checkers live in. */
  private static final String LAWS_PACKAGE = FunctorLaws.class.getPackageName();

  /**
   * The section that states each type class's laws, as the Laws page links it, in the order the
   * table lists them: the order the book teaches them in, with the MTL classes last, from the Monad
   * Transformers chapter.
   */
  private static final Map<Class<?>, String> SECTIONS = sections();

  /** The section that shows {@link KindHelperLaws} checking a type's own widen and narrow. */
  private static final String KIND_HELPER_SECTION = "extending-simulation.md#prove-your-instance";

  private static Map<Class<?>, String> sections() {
    Map<Class<?>, String> sections = new LinkedHashMap<>();
    sections.put(Semigroup.class, "../functional/semigroup_and_monoid.md#three-laws");
    sections.put(Monoid.class, "../functional/semigroup_and_monoid.md#three-laws");
    sections.put(Functor.class, "../functional/functor.md#two-laws");
    sections.put(Applicative.class, "../functional/applicative.md#four-laws");
    sections.put(Selective.class, "../functional/selective.md#laws");
    sections.put(Monad.class, "../functional/monad.md#three-laws");
    sections.put(MonadError.class, "../functional/monad_error.md#three-laws");
    sections.put(Alternative.class, "../functional/alternative.md#laws");
    sections.put(MonadZero.class, "../functional/monad_zero.md#two-laws");
    sections.put(Foldable.class, "../functional/foldable_and_traverse.md#the-traverse-laws");
    sections.put(Traverse.class, "../functional/foldable_and_traverse.md#the-traverse-laws");
    sections.put(Natural.class, "../functional/natural_transformation.md#the-naturality-law");
    sections.put(Bifunctor.class, "../functional/bifunctor.md#four-laws");
    sections.put(Profunctor.class, "../functional/profunctor.md#two-laws");
    sections.put(MonadReader.class, "../transformers/mtl_reader.md#the-monadreader-laws");
    sections.put(MonadState.class, "../transformers/mtl_state.md#the-monadstate-laws");
    sections.put(MonadWriter.class, "../transformers/mtl_writer.md#the-monadwriter-laws");
    return sections;
  }

  @Test
  @DisplayName("the golden table is the one hkj-api and hkj-test give")
  void goldenMatchesTheLibrary() {
    String rendered = render();
    assertThat(BookFiles.golden(GOLDEN))
        .as(
            """
            The Laws page's table no longer matches hkj-test's law checkers. If the change is \
            intended, replace hkj-examples/src/test/resources/golden/%s with:

            %s""",
            GOLDEN, rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName("every type class in hkj-api, and Natural, has a section that states its laws")
  void everyTypeClassHasASection() {
    List<Class<?>> expected =
        Stream.concat(BookTypeClassHierarchyTest.typeClasses().stream(), Stream.of(Natural.class))
            .toList();
    assertThat(SECTIONS.keySet())
        .as("the type classes the Laws page's table lists")
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  @DisplayName("every law checker in hkj-test has a row")
  void everyCheckerHasARow() {
    List<Class<?>> placed = new ArrayList<>();
    SECTIONS.keySet().forEach(type -> checker(type).ifPresent(placed::add));
    placed.add(KindHelperLaws.class);
    assertThat(checkers())
        .as("the law checkers in %s, each in one row of the Laws page's table", LAWS_PACKAGE)
        .containsExactlyInAnyOrderElementsOf(placed);
  }

  @Test
  @DisplayName("every row links a heading that exists")
  void everyRowLinksAHeading() {
    List<String> links =
        Stream.concat(SECTIONS.values().stream(), Stream.of(KIND_HELPER_SECTION))
            .distinct()
            .toList();
    Path hkts = BOOK.resolve("hkts");
    for (String link : links) {
      int hash = link.indexOf('#');
      assertThat(hash).as("%s links a heading, with a #", link).isPositive();
      Path page = hkts.resolve(link.substring(0, hash)).normalize();
      assertThat(page).as("the page %s links", link).isRegularFile();
      assertThat(BookFiles.read(page))
          .as("%s pins the heading id it links", link)
          .contains("{#" + link.substring(hash + 1) + "}");
    }
  }

  @Test
  @DisplayName("the Laws page includes the table under its heading")
  void lawsPageIncludesTheTable() {
    String page = BookFiles.read(BOOK.resolve("hkts/laws.md"));
    int heading = page.indexOf(HEADING);
    assertThat(heading).as("the heading %s", HEADING).isNotNegative();
    int nextHeading = page.indexOf("\n## ", heading + HEADING.length());
    String section = page.substring(heading, nextHeading < 0 ? page.length() : nextHeading);
    assertThat(section).as("the section under %s", HEADING).contains(INCLUDE);
  }

  /** The golden file as this test would write it. */
  private static String render() {
    StringBuilder out = new StringBuilder();
    out.append(
        "<!-- Written by BookLawsTest from hkj-api's type classes and hkj-test's law checkers."
            + " Do not edit: the test prints the replacement. -->\n");
    out.append("<!-- ANCHOR: checked -->\n");
    out.append("| Laws of | `hkj-test` checks |\n");
    out.append("|---|---|\n");
    SECTIONS.forEach(
        (type, section) ->
            out.append(
                "| [`%s`](%s) | %s |\n"
                    .formatted(
                        type.getSimpleName(),
                        section,
                        checker(type).map(BookLawsTest::checks).orElse("Not supported yet"))));
    out.append(
        "| [A type's `widen` and `narrow`](%s) | %s |\n"
            .formatted(KIND_HELPER_SECTION, checks(KindHelperLaws.class)));
    out.append("<!-- ANCHOR_END: checked -->\n");
    return out.toString();
  }

  /** The checker named after the type class, such as {@code FunctorLaws}, when there is one. */
  private static Optional<Class<?>> checker(Class<?> typeClass) {
    try {
      return Optional.of(Class.forName(LAWS_PACKAGE + "." + typeClass.getSimpleName() + "Laws"));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  /** The checker's name and the laws its {@code assert} methods check, such as "identity". */
  private static String checks(Class<?> checker) {
    List<String> laws = assertMethods(checker).stream().map(BookLawsTest::lawName).toList();
    assertThat(laws).as("the assert methods of %s", checker.getSimpleName()).isNotEmpty();
    return "`%s`: %s".formatted(checker.getSimpleName(), String.join(", ", laws));
  }

  /** {@code assertLeftIdentity} as "left identity". */
  private static String lawName(String method) {
    return method
        .substring("assert".length())
        .replaceAll("(?<=[a-z])(?=[A-Z])", " ")
        .toLowerCase(Locale.ROOT);
  }

  /** The public static {@code assert} methods the checker declares, in declaration order. */
  private static List<String> assertMethods(Class<?> checker) {
    try (InputStream in = checker.getResourceAsStream(checker.getSimpleName() + ".class")) {
      assertThat(in).as("%s's class file on the test classpath", checker.getName()).isNotNull();
      return ClassFile.of().parse(in.readAllBytes()).methods().stream()
          .filter(method -> method.flags().has(AccessFlag.PUBLIC))
          .filter(method -> method.flags().has(AccessFlag.STATIC))
          .map(method -> method.methodName().stringValue())
          .filter(name -> name.startsWith("assert"))
          .distinct()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The public top-level classes named {@code ...Laws} in the checkers' package. */
  private static List<Class<?>> checkers() {
    try {
      Path root =
          Path.of(FunctorLaws.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      try (FileSystem jar = Files.isDirectory(root) ? null : FileSystems.newFileSystem(root);
          Stream<Path> files =
              Files.list(
                  (jar == null ? root : jar.getPath("/"))
                      .resolve(LAWS_PACKAGE.replace('.', '/')))) {
        List<String> names =
            files
                .map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith("Laws.class") && !name.contains("$"))
                .map(name -> name.substring(0, name.length() - ".class".length()))
                .sorted()
                .toList();
        List<Class<?>> checkers = new ArrayList<>();
        for (String name : names) {
          Class<?> type = Class.forName(LAWS_PACKAGE + "." + name);
          if (Modifier.isPublic(type.getModifiers())) {
            checkers.add(type);
          }
        }
        assertThat(checkers).as("checkers found in %s", root).contains(FunctorLaws.class);
        return checkers;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException | ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }
}
