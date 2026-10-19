// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The field names {@code @GenerateFocus} lists are checked against the record's components, and a
 * list the record's other settings leave unread says so. Each shape is compiled as written, where
 * its message must land on the value it is about, and again as its fix line says, where it must
 * compile with nothing more to report. One compilation holds every shape as written, and one more
 * holds each way of following the fix lines, so the rows cost a few compilations between them.
 */
@DisplayName("@GenerateFocus field lists - checked names, with fix lines that can be followed")
class FocusFieldListChecksTest {

  /**
   * One shape: a record as written, the message it draws, where the message points, and the
   * annotations its fix line leads to.
   *
   * @param record the record's name, unique among the rows
   * @param components the record's components
   * @param annotation the annotation as written, on one line
   * @param kind the message's severity
   * @param at the text in the annotation the message points at, which it writes once; null for a
   *     message about the component rather than the annotation
   * @param message the message, or the part of it this row is about
   * @param followed the annotation the fix line's first remedy leads to
   * @param alternative the annotation its second remedy leads to, or null where it offers one
   */
  record Row(
      String record,
      String components,
      String annotation,
      Diagnostic.Kind kind,
      String at,
      String message,
      String followed,
      String alternative) {

    @Override
    public String toString() {
      return annotation + " on " + record + "(" + components + ")";
    }
  }

  private static final List<Row> ROWS =
      List.of(
          new Row(
              "AssetClass",
              "String name, Map<String, Integer> positions",
              "@GenerateFocus(widenFields = \"postions\")",
              Diagnostic.Kind.ERROR,
              "\"postions\"",
              "@GenerateFocus: widenFields on AssetClass names \"postions\", which is not a"
                  + " component of AssetClass. widenFields widens the containers of the components"
                  + " it names, so a name that matches none widens nothing. Did you mean"
                  + " 'positions'? Found on AssetClass: [name, positions]. Replace \"postions\""
                  + " with \"positions\" in widenFields on AssetClass.",
              "@GenerateFocus(widenFields = \"positions\")",
              null),
          new Row(
              "Holding",
              "String name, Map<String, Integer> positions",
              "@GenerateFocus(widenFields = \"Positions\")",
              Diagnostic.Kind.ERROR,
              "\"Positions\"",
              "Did you mean 'positions'? Found on Holding: [name, positions]. Replace"
                  + " \"Positions\" with \"positions\" in widenFields on Holding.",
              "@GenerateFocus(widenFields = \"positions\")",
              null),
          new Row(
              "Person",
              "Addr home, Addr work",
              "@GenerateFocus(excludeFields = \"hmoe\")",
              Diagnostic.Kind.ERROR,
              "\"hmoe\"",
              "@GenerateFocus: excludeFields on Person names \"hmoe\", which is not a component of"
                  + " Person. excludeFields leaves the components it names without navigators, so"
                  + " a name that matches none leaves out nothing. Did you mean 'home'? Found on"
                  + " Person: [home, work]. Replace \"hmoe\" with \"home\" in excludeFields on"
                  + " Person.",
              "@GenerateFocus(excludeFields = \"home\")",
              null),
          new Row(
              "Customer",
              "String id, Addr address",
              "@GenerateFocus(includeFields = \"adress\")",
              Diagnostic.Kind.ERROR,
              "\"adress\"",
              "@GenerateFocus: includeFields on Customer names \"adress\", which is not a"
                  + " component of Customer. includeFields gives navigators only to the components"
                  + " it names, so a name that matches none gives no component a navigator. Did you"
                  + " mean 'address'? Found on Customer: [id, address]. Replace \"adress\" with"
                  + " \"address\" in includeFields on Customer.",
              "@GenerateFocus(includeFields = \"address\")",
              null),
          new Row(
              "Supplier",
              "String id, Addr address",
              "@GenerateFocus(includeFields = \"getAddress\")",
              Diagnostic.Kind.ERROR,
              "\"getAddress\"",
              "Did you mean 'address'? Found on Supplier: [id, address]. Replace \"getAddress\""
                  + " with \"address\" in includeFields on Supplier.",
              "@GenerateFocus(includeFields = \"address\")",
              null),
          new Row(
              "Shipment",
              "String id, Addr origin",
              "@GenerateFocus(excludeFields = {\"origin\", \"zzz\"})",
              Diagnostic.Kind.ERROR,
              "\"zzz\"",
              "@GenerateFocus: excludeFields on Shipment names \"zzz\", which is not a component of"
                  + " Shipment. excludeFields leaves the components it names without navigators, so"
                  + " a name that matches none leaves out nothing. Found on Shipment: [id, origin]."
                  + " Write a component of Shipment in place of \"zzz\", or remove \"zzz\" from"
                  + " excludeFields on Shipment.",
              "@GenerateFocus(excludeFields = {\"origin\"})",
              null),
          new Row(
              "Courier",
              "String id, Addr depot",
              "@GenerateFocus(includeFields = \"everything\")",
              Diagnostic.Kind.ERROR,
              "\"everything\"",
              "Found on Courier: [id, depot]. Write a component of Courier in place of"
                  + " \"everything\", or remove includeFields from @GenerateFocus on Courier.",
              "@GenerateFocus",
              null),
          new Row(
              "Commuter",
              "Addr home, Addr work",
              "@GenerateFocus(includeFields = \"home\", excludeFields = \"work\")",
              Diagnostic.Kind.WARNING,
              "\"work\"",
              "@GenerateFocus: excludeFields on Commuter changes nothing, because includeFields is"
                  + " set beside it. A non-empty includeFields gives navigators to the components it"
                  + " names and to no others, so excludeFields is never read. Remove excludeFields"
                  + " from @GenerateFocus on Commuter, or remove includeFields to give navigators to"
                  + " every component but those excludeFields names.",
              "@GenerateFocus(includeFields = \"home\")",
              "@GenerateFocus(excludeFields = \"work\")"),
          new Row(
              "Visitor",
              "Addr home, Addr work",
              "@GenerateFocus(generateNavigators = false, includeFields = \"home\")",
              Diagnostic.Kind.WARNING,
              "\"home\"",
              "@GenerateFocus: includeFields on Visitor changes nothing, because generateNavigators"
                  + " = false turns navigators off. includeFields and excludeFields choose which"
                  + " components get navigators, and with navigators off none does. Remove"
                  + " includeFields from @GenerateFocus on Visitor, or remove generateNavigators ="
                  + " false to give navigators to the components includeFields names.",
              "@GenerateFocus(generateNavigators = false)",
              "@GenerateFocus(includeFields = \"home\")"),
          new Row(
              "Resident",
              "Addr home, Addr work",
              "@GenerateFocus(generateNavigators = false, excludeFields = {\"work\"})",
              Diagnostic.Kind.WARNING,
              "{\"work\"}",
              "Remove excludeFields from @GenerateFocus on Resident, or remove generateNavigators ="
                  + " false to give navigators to every component but those excludeFields names.",
              "@GenerateFocus(generateNavigators = false)",
              "@GenerateFocus(excludeFields = {\"work\"})"),
          new Row(
              "Warehouse",
              "Map<String, Integer> stock",
              "@GenerateFocus(widenCollections = true, widenFields = \"stock\")",
              Diagnostic.Kind.WARNING,
              "\"stock\"",
              "@GenerateFocus: widenFields on Warehouse changes nothing, because widenCollections ="
                  + " true widens every field widenFields could name. widenCollections = true widens"
                  + " every Map, array of objects and third-party collection, which is all"
                  + " widenFields can widen. Remove widenFields from @GenerateFocus on Warehouse, or"
                  + " remove widenCollections = true to widen only the fields widenFields names.",
              "@GenerateFocus(widenCollections = true)",
              "@GenerateFocus(widenFields = \"stock\")"),
          new Row(
              "Shelf",
              "Map<String, Integer> stock, List<String> tags",
              "@GenerateFocus(widenFields = {\"stock\", \"tags\"})",
              Diagnostic.Kind.WARNING,
              "\"tags\"",
              "@GenerateFocus: widenFields on Shelf names \"tags\", whose List<String> is widened"
                  + " without it. ShelfFocus.tags() returns a TraversalPath whether widenFields names"
                  + " it or not. Remove \"tags\" from widenFields on Shelf.",
              "@GenerateFocus(widenFields = {\"stock\"})",
              null),
          new Row(
              "Profile",
              "Optional<String> nickname",
              "@GenerateFocus(widenFields = \"nickname\")",
              Diagnostic.Kind.WARNING,
              "\"nickname\"",
              "whose Optional<String> is widened without it. ProfileFocus.nickname() returns an"
                  + " AffinePath whether widenFields names it or not. Remove widenFields from"
                  + " @GenerateFocus on Profile.",
              "@GenerateFocus",
              null),
          new Row(
              "Basket",
              "@Nullable List<String> items",
              "@GenerateFocus(widenFields = \"items\")",
              Diagnostic.Kind.WARNING,
              "\"items\"",
              "whose List<String> is widened without it. BasketFocus.items() returns a"
                  + " TraversalPath whether widenFields names it or not.",
              "@GenerateFocus",
              null),
          new Row(
              "Quote",
              "Either<String, Integer> price",
              "@GenerateFocus(widenFields = \"price\")",
              Diagnostic.Kind.WARNING,
              "\"price\"",
              "whose Either<String, Integer> is widened without it. QuoteFocus.price() returns an"
                  + " AffinePath whether widenFields names it or not.",
              "@GenerateFocus",
              null),
          new Row(
              "Badge",
              "String label",
              "@GenerateFocus(widenFields = \"label\")",
              Diagnostic.Kind.WARNING,
              "\"label\"",
              "@GenerateFocus: widenFields on Badge names \"label\", which holds no container"
                  + " widenFields widens. widenFields widens a Map, an array of objects, or another"
                  + " collection that a TraversableGenerator on the processor path can widen, and"
                  + " none can widen String. Remove widenFields from @GenerateFocus on Badge.",
              "@GenerateFocus",
              null),
          new Row(
              "Scorecard",
              "int[] scores",
              "@GenerateFocus(widenFields = \"scores\")",
              Diagnostic.Kind.WARNING,
              "\"scores\"",
              "and none can widen int[]. Remove widenFields from @GenerateFocus on Scorecard.",
              "@GenerateFocus",
              null),
          // A type with type arguments may be a collection whose generator is not on the path.
          new Row(
              "Backlog",
              "java.util.Deque<String> queue",
              "@GenerateFocus(widenFields = \"queue\")",
              Diagnostic.Kind.WARNING,
              "\"queue\"",
              "and none can widen Deque<String>. Remove widenFields from @GenerateFocus on Backlog,"
                  + " or, where Deque is a collection, add a TraversableGenerator that supports it"
                  + " to the annotation processor path, as hkj-processor-plugins does for Eclipse"
                  + " Collections, Guava, Vavr, Apache Commons and PCollections.",
              "@GenerateFocus",
              null),
          new Row(
              "Blank",
              "",
              "@GenerateFocus(widenFields = \"x\")",
              Diagnostic.Kind.ERROR,
              "\"x\"",
              "so a name that matches none widens nothing. Blank has no components. Remove"
                  + " widenFields from @GenerateFocus on Blank.",
              "@GenerateFocus",
              null),
          new Row(
              "Memo",
              "@Nullable String text",
              "@GenerateFocus(widenFields = \"text\")",
              Diagnostic.Kind.WARNING,
              "\"text\"",
              "widenFields on Memo names \"text\", which holds no container widenFields widens.",
              "@GenerateFocus",
              null),
          // A container refused for its wildcard is refused at the component, and the name that
          // widens it draws nothing more.
          new Row(
              "Crate",
              "Map<String, ? extends Number> values",
              "@GenerateFocus(widenFields = \"values\")",
              Diagnostic.Kind.ERROR,
              null,
              "record component 'Crate.values' has a wildcard type argument in Map<String, ?"
                  + " extends Number>.",
              "@GenerateFocus",
              null));

  /** Shapes that read a list and draw nothing. */
  private static final List<Row> QUIET =
      List.of(
          // The container inside the List is one only widenFields widens.
          quiet(
              "Grid", "List<Map<String, Integer>> rows", "@GenerateFocus(widenFields = \"rows\")"),
          // An empty includeFields filters nothing, so excludeFields is read.
          quiet(
              "Office",
              "Addr home, Addr work",
              "@GenerateFocus(includeFields = {}, excludeFields = \"work\")"),
          // widenFields decides what the static method returns, navigators or not.
          quiet(
              "Depot",
              "Map<String, Integer> stock",
              "@GenerateFocus(generateNavigators = false, widenFields = \"stock\")"));

  private static Row quiet(String record, String components, String annotation) {
    return new Row(record, components, annotation, null, null, null, annotation, null);
  }

  /** A navigable record the rows' navigators reach. */
  private static final JavaFileObject ADDR =
      JavaFileObjects.forSourceString(
          "com.example.Addr",
          """
          package com.example;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          @GenerateFocus
          public record Addr(String street, String city) {}
          """);

  private static final Compilation WRITTEN = compile(Row::annotation, ROWS.stream());

  private static final Compilation FOLLOWED =
      compile(Row::followed, Stream.concat(ROWS.stream(), QUIET.stream()));

  private static final Compilation ALTERNATIVES =
      compile(Row::alternative, ROWS.stream().filter(row -> row.alternative() != null));

  private static JavaFileObject source(Row row, String annotation) {
    return JavaFileObjects.forSourceString(
        "com.example." + row.record(),
        """
        package com.example;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;
        """
            + annotation
            + "\npublic record "
            + row.record()
            + "("
            + row.components()
            + ") {}\n");
  }

  private static Compilation compile(Function<Row, String> annotation, Stream<Row> rows) {
    return javac()
        .withProcessors(new FocusProcessor())
        .compile(
            Stream.concat(Stream.of(ADDR), rows.map(row -> source(row, annotation.apply(row))))
                .toList());
  }

  static Stream<Row> rows() {
    return ROWS.stream();
  }

  /** The line every row's annotation is written on, after the package and the six imports. */
  private static final long ANNOTATION_LINE = 8;

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("each shape draws its message on the value it is about")
  void drawsItsMessageOnTheValue(Row row) {
    JavaFileObject written = source(row, row.annotation());
    if (row.at() == null) {
      assertThat(WRITTEN).hadErrorContaining(row.message()).inFile(written);
      return;
    }
    long column = row.annotation().indexOf(row.at()) + 1;
    if (row.kind() == Diagnostic.Kind.ERROR) {
      assertThat(WRITTEN)
          .hadErrorContaining(row.message())
          .inFile(written)
          .onLine(ANNOTATION_LINE)
          .atColumn(column);
    } else {
      assertThat(WRITTEN)
          .hadWarningContaining(row.message())
          .inFile(written)
          .onLine(ANNOTATION_LINE)
          .atColumn(column);
    }
  }

  @Test
  @DisplayName("each shape draws one message and no other")
  void drawsNothingElse() {
    assertThat(WRITTEN)
        .hadErrorCount(
            (int) ROWS.stream().filter(row -> row.kind() == Diagnostic.Kind.ERROR).count());
    assertThat(WRITTEN)
        .hadWarningCount(
            (int) ROWS.stream().filter(row -> row.kind() == Diagnostic.Kind.WARNING).count());
  }

  @Test
  @DisplayName("each fix line, followed as written, compiles with nothing more to report")
  void eachFixLineCompiles() {
    assertThat(FOLLOWED).succeededWithoutWarnings();
    assertThat(ALTERNATIVES).succeededWithoutWarnings();
    assertGeneratedCodeContains(
        FOLLOWED,
        "com.example.AssetClassFocus",
        "public static TraversalPath<AssetClass, Integer> positions()");
    assertGeneratedCodeContains(
        FOLLOWED,
        "com.example.CustomerFocus",
        "public static AddressNavigator<Customer> address()");
    assertGeneratedCodeContains(
        FOLLOWED, "com.example.PersonFocus", "public static FocusPath<Person, Addr> home()");
    assertGeneratedCodeContains(
        FOLLOWED, "com.example.PersonFocus", "public static WorkNavigator<Person> work()");
  }

  @Test
  @DisplayName("both navigator lists with navigators off are each offered only their removal")
  void bothListsWithNavigatorsOff() {
    Row lodger =
        quiet(
            "Lodger",
            "Addr home, Addr work",
            "@GenerateFocus(generateNavigators = false, includeFields = \"home\","
                + " excludeFields = \"work\")");
    Compilation written = compile(Row::annotation, Stream.of(lodger));
    Compilation followed =
        compile(row -> "@GenerateFocus(generateNavigators = false)", Stream.of(lodger));

    assertThat(written).hadWarningCount(2);
    assertThat(written)
        .hadWarningContaining(
            "includeFields on Lodger changes nothing, because generateNavigators = false turns"
                + " navigators off. includeFields and excludeFields choose which components get"
                + " navigators, and with navigators off none does. Remove includeFields from"
                + " @GenerateFocus on Lodger.");
    assertThat(written).hadWarningContaining("Remove excludeFields from @GenerateFocus on Lodger.");
    assertThat(followed).succeededWithoutWarnings();
  }

  @Test
  @DisplayName("a list naming a constant javac cannot resolve is left to javac's own error")
  void anUnresolvedConstantIsLeftToJavac() {
    JavaFileObject unresolved =
        JavaFileObjects.forSourceString(
            "com.example.Ledger",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(includeFields = Names.MISSING)
            public record Ledger(String id, Addr address) {}
            """);
    // A record that would navigate into it treats it as not navigable, since its settings cannot
    // be read and it has no companion to compose.
    JavaFileObject owner =
        JavaFileObjects.forSourceString(
            "com.example.Owner",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus(generateNavigators = true)
            public record Owner(Ledger ledger) {}
            """);

    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(ADDR, unresolved, owner);

    // One error: a navigator into Ledger would name a LedgerFocus that is never written, and javac
    // would report that too.
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("cannot find symbol");
  }
}
