// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the Quick Reference to {@code hkj-api}. Every type class has one row, and every method a
 * row names is a method that type class has. The factory tables list exactly the public factories
 * of {@link Semigroups} and {@link Monoids}, each once, and each {@code Monoid}'s empty value is
 * the one the page states. A new type class or factory fails the test until the page lists it, and
 * a renamed method fails it until the page follows.
 */
@DisplayName("the Quick Reference is read against hkj-api")
class BookQuickReferenceTest {

  private static final Path PAGE =
      Path.of(required("hkj.book.dir")).resolve("hkts/quick_reference.md");

  /** A code span, such as {@code `map2`}. */
  private static final Pattern CODE = Pattern.compile("`([^`]+)`");

  /** A factory cell, such as {@code `Semigroups.string(delimiter)`}. */
  private static final Pattern FACTORY =
      Pattern.compile("`((Semigroups|Monoids)\\.(\\w+))\\(([^)]*)\\)`");

  /** The empty values the page writes as code, each as the value it stands for. */
  private static final Map<String, Object> EMPTY_VALUES =
      Map.of(
          "\"\"",
          "",
          "0",
          0,
          "1",
          1,
          "0L",
          0L,
          "1L",
          1L,
          "0.0",
          0.0,
          "1.0",
          1.0,
          "true",
          true,
          "false",
          false,
          "BigDecimal.ZERO",
          BigDecimal.ZERO);

  @Test
  @DisplayName("every type class has one row, naming only methods it has")
  void everyTypeClassHasOneRowOfRealMethods() {
    List<List<String>> rows = rowsUnder("Type class", 3);
    List<String> named = rows.stream().map(row -> code(row.getFirst())).toList();

    assertThat(named)
        .as("type classes with a row on %s", PAGE)
        .containsExactlyInAnyOrderElementsOf(
            BookTypeClassHierarchyTest.typeClasses().stream().map(Class::getSimpleName).toList());

    Map<String, Class<?>> byName =
        BookTypeClassHierarchyTest.typeClasses().stream()
            .collect(Collectors.toMap(Class::getSimpleName, type -> type));
    for (List<String> row : rows) {
      Class<?> type = byName.get(code(row.getFirst()));
      Set<String> methods =
          Arrays.stream(type.getMethods()).map(Method::getName).collect(Collectors.toSet());
      assertThat(codes(row.get(1)))
          .as("the methods the %s row names", type.getSimpleName())
          .isNotEmpty()
          .allSatisfy(
              name ->
                  assertThat(methods)
                      .as("%s has a method %s", type.getSimpleName(), name)
                      .contains(name));
    }
  }

  @Test
  @DisplayName("Which type class? names a type class on every row")
  void whichTypeClassNamesTypeClasses() {
    List<String> typeClasses =
        BookTypeClassHierarchyTest.typeClasses().stream().map(Class::getSimpleName).toList();

    for (List<String> row : rowsUnder("You want to", 1)) {
      assertThat(codes(row.get(1)))
          .as("the type classes the row '%s' names", row.getFirst())
          .isNotEmpty()
          .isSubsetOf(typeClasses);
    }
  }

  @Test
  @DisplayName("the factory tables list each of Semigroups' and Monoids' factories once")
  void factoryTablesMatchTheApi() {
    List<String> listed = new ArrayList<>();
    for (List<String> row : rowsUnder("Factory", 2)) {
      Matcher factory = factory(row.getFirst());
      listed.add(factory.group(1) + "/" + arguments(factory).size());
    }

    assertThat(listed)
        .as("factories on %s, as Class.method/arguments", PAGE)
        .containsExactlyInAnyOrderElementsOf(
            Stream.of(Semigroups.class, Monoids.class)
                .flatMap(BookQuickReferenceTest::factories)
                .toList());
  }

  @Test
  @DisplayName("each Monoid's empty value is the one the page states")
  void emptyValuesMatchTheApi() {
    List<List<String>> monoids =
        rowsUnder("Factory", 2).stream().filter(row -> row.size() == 3).toList();

    assertThat(monoids).as("rows with an Empty value column on %s", PAGE).isNotEmpty();
    for (List<String> row : monoids) {
      Object empty = monoid(factory(row.getFirst())).empty();
      String stated = row.get(2);
      switch (stated) {
        case "An empty list" -> assertThat(empty).as(row.getFirst()).isEqualTo(List.of());
        case "An empty set" -> assertThat(empty).as(row.getFirst()).isEqualTo(Set.of());
        case "`Optional.empty()`" ->
            assertThat(empty).as(row.getFirst()).isEqualTo(Optional.empty());
        case "`Update.identity()`" -> {
          assertThat(empty).as(row.getFirst()).isInstanceOf(Update.class);
          Object value = new Object();
          @SuppressWarnings("unchecked") // the identity update applies to any state
          Update<Object> identity = (Update<Object>) empty;
          assertThat(identity.apply(value)).as(row.getFirst()).isSameAs(value);
        }
        default -> {
          String code = code(stated);
          assertThat(EMPTY_VALUES)
              .as("an empty value the test knows: %s", stated)
              .containsKey(code);
          assertThat(empty).as(row.getFirst()).isEqualTo(EMPTY_VALUES.get(code));
        }
      }
    }
  }

  /** The public static methods of {@code holder}, as {@code Class.method/arguments}. */
  private static Stream<String> factories(Class<?> holder) {
    return Arrays.stream(holder.getDeclaredMethods())
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .filter(method -> Modifier.isStatic(method.getModifiers()))
        .filter(method -> !method.isSynthetic())
        .map(
            method ->
                holder.getSimpleName() + "." + method.getName() + "/" + method.getParameterCount());
  }

  private static Matcher factory(String cell) {
    Matcher factory = FACTORY.matcher(cell);
    assertThat(factory.matches()).as("a factory cell: %s", cell).isTrue();
    return factory;
  }

  private static List<String> arguments(Matcher factory) {
    String arguments = factory.group(4);
    return arguments.isBlank() ? List.of() : Arrays.asList(arguments.split(","));
  }

  /**
   * The {@code Monoid} a factory cell names, called with the natural order for a comparator, the
   * only argument a {@code Monoids} factory takes.
   */
  private static Monoid<?> monoid(Matcher factory) {
    assertThat(factory.group(2)).as("a Monoids factory: %s", factory.group()).isEqualTo("Monoids");
    try {
      return arguments(factory).isEmpty()
          ? (Monoid<?>) Monoids.class.getMethod(factory.group(3)).invoke(null)
          : (Monoid<?>)
              Monoids.class
                  .getMethod(factory.group(3), Comparator.class)
                  .invoke(null, Comparator.naturalOrder());
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("cannot call " + factory.group(), e);
    }
  }

  /**
   * The body rows of every table whose first header cell is {@code header}, each split into cells
   * and as wide as its header; {@code tables} is how many such tables the page has.
   */
  private static List<List<String>> rowsUnder(String header, int tables) {
    List<String> lines = read(PAGE).lines().toList();
    List<List<String>> rows = new ArrayList<>();
    int found = 0;
    for (int i = 0; i + 1 < lines.size(); i++) {
      if (!lines.get(i).startsWith("|") || !cells(lines.get(i)).getFirst().equals(header)) {
        continue;
      }
      found++;
      int width = cells(lines.get(i)).size();
      for (int j = i + 2; j < lines.size() && lines.get(j).startsWith("|"); j++) {
        List<String> row = cells(lines.get(j));
        assertThat(row).as("a row as wide as its header: %s", lines.get(j)).hasSize(width);
        rows.add(row);
      }
    }
    assertThat(found).as("tables headed '%s' on %s", header, PAGE).isEqualTo(tables);
    return rows;
  }

  /** The cells of a table row. A pipe escaped as {@code \|} stays inside its cell. */
  private static List<String> cells(String row) {
    String inner = row.substring(1, row.length() - (row.endsWith("|") ? 1 : 0));
    return Arrays.stream(inner.split("(?<!\\\\)\\|", -1)).map(String::strip).toList();
  }

  /** The cell's one code span, such as {@code Functor} for {@code `Functor`}. */
  private static String code(String cell) {
    List<String> codes = codes(cell);
    assertThat(codes).as("one code span in the cell %s", cell).hasSize(1);
    return codes.getFirst();
  }

  /** Every code span in the cell, in order, each once. */
  private static List<String> codes(String cell) {
    Set<String> found = new LinkedHashSet<>();
    Matcher code = CODE.matcher(cell);
    while (code.find()) {
      found.add(code.group(1));
    }
    return List.copyOf(found);
  }

  private static String read(Path page) {
    try {
      return Files.readString(page).replace("\r\n", "\n");
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
