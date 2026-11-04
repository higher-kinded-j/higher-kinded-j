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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The field names {@code @GenerateFocus} lists are checked against the record's components, and a
 * list the record's other settings leave unread says so. A name that matches none is offered only a
 * component the list can use and does not name already, with any rename its navigator needs first,
 * and a name written again is told once, with a warning at each later copy. Each shape is compiled
 * as written, where its message must land on the value it is about, and again as its fix line says,
 * where it must compile with nothing more to report. One compilation holds every shape as written,
 * and one more holds each way of following the fix lines, so the rows cost a few compilations
 * between them. A shape that draws more than one message is compiled on its own, and the shapes
 * whose offer may ask for a rename share a compilation as written and one for each version of the
 * record they compile beside once followed.
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
          // A name the list holds already is not offered, so following the fix line cannot name
          // one field twice and leave the field the entry meant without a navigator.
          new Row(
              "Showcase",
              "Addr items, Addr featured",
              "@GenerateFocus(includeFields = {\"items\", \"Items\"})",
              Diagnostic.Kind.ERROR,
              "\"Items\"",
              "a name that matches none gives no component a navigator. Found on Showcase: [items,"
                  + " featured]. Write a component of Showcase in place of \"Items\", or remove"
                  + " \"Items\" from includeFields on Showcase.",
              "@GenerateFocus(includeFields = {\"items\", \"featured\"})",
              null),
          // Past a nearer name the list holds, the next nearest component is offered.
          new Row(
              "Tote",
              "Addr items, Addr itemz",
              "@GenerateFocus(includeFields = {\"items\", \"itens\"})",
              Diagnostic.Kind.ERROR,
              "\"itens\"",
              "Did you mean 'itemz'? Found on Tote: [items, itemz]. Replace \"itens\" with"
                  + " \"itemz\" in includeFields on Tote.",
              "@GenerateFocus(includeFields = {\"items\", \"itemz\"})",
              null),
          // A name includeFields could give no navigator is not offered either.
          new Row(
              "Parcel",
              "String label, Addr address",
              "@GenerateFocus(includeFields = \"labl\")",
              Diagnostic.Kind.ERROR,
              "\"labl\"",
              "a name that matches none gives no component a navigator. Found on Parcel: [label,"
                  + " address]. Write a component of Parcel in place of \"labl\", or remove"
                  + " includeFields from @GenerateFocus on Parcel.",
              "@GenerateFocus(includeFields = \"address\")",
              null),
          // An earlier namesake the list leaves without a navigator keeps its plain path, so the
          // fix line asks for no rename.
          new Row(
              "Locker",
              "Addr Items, Addr items",
              "@GenerateFocus(includeFields = \"itemz\")",
              Diagnostic.Kind.ERROR,
              "\"itemz\"",
              "Did you mean 'items'? Found on Locker: [Items, items]. Replace \"itemz\" with"
                  + " \"items\" in includeFields on Locker.",
              "@GenerateFocus(includeFields = \"items\")",
              null),
          // widenFields is offered the nearest name it can widen, past a nearer one it cannot.
          new Row(
              "Stockroom",
              "String stocks, Map<String, Integer> stock",
              "@GenerateFocus(widenFields = \"stoks\")",
              Diagnostic.Kind.ERROR,
              "\"stoks\"",
              "a name that matches none widens nothing. Did you mean 'stock'? Found on Stockroom:"
                  + " [stocks, stock]. Replace \"stoks\" with \"stock\" in widenFields on"
                  + " Stockroom.",
              "@GenerateFocus(widenFields = \"stock\")",
              null),
          // A container whose widening is refused for its wildcard is not offered.
          new Row(
              "Gauge",
              "Map<String, ? extends Number> readings",
              "@GenerateFocus(widenFields = \"readngs\")",
              Diagnostic.Kind.ERROR,
              "\"readngs\"",
              "Found on Gauge: [readings]. Write a component of Gauge in place of \"readngs\", or"
                  + " remove widenFields from @GenerateFocus on Gauge.",
              "@GenerateFocus",
              null),
          // Nor is one that widens a container only to refuse the one inside it.
          new Row(
              "Barometer",
              "Map<String, Map<String, ? extends Number>> readings",
              "@GenerateFocus(widenFields = \"readngs\")",
              Diagnostic.Kind.ERROR,
              "\"readngs\"",
              "Found on Barometer: [readings]. Write a component of Barometer in place of"
                  + " \"readngs\", or remove widenFields from @GenerateFocus on Barometer.",
              "@GenerateFocus",
              null),
          // Widening gives no navigator with navigators off, or to a field excludeFields names,
          // so a namesake asks for no rename.
          new Row(
              "Pantry",
              "Map<String, Addr> stock, Addr Stock",
              "@GenerateFocus(generateNavigators = false, widenFields = \"stck\")",
              Diagnostic.Kind.ERROR,
              "\"stck\"",
              "Did you mean 'stock'? Found on Pantry: [stock, Stock]. Replace \"stck\" with"
                  + " \"stock\" in widenFields on Pantry.",
              "@GenerateFocus(generateNavigators = false, widenFields = \"stock\")",
              null),
          new Row(
              "Cellar",
              "Map<String, Addr> stock, Addr Stock",
              "@GenerateFocus(excludeFields = \"stock\", widenFields = \"stck\")",
              Diagnostic.Kind.ERROR,
              "\"stck\"",
              "Did you mean 'stock'? Found on Cellar: [stock, Stock]. Replace \"stck\" with"
                  + " \"stock\" in widenFields on Cellar.",
              "@GenerateFocus(excludeFields = \"stock\", widenFields = \"stock\")",
              null),
          new Row(
              "Tenant",
              "Addr home, Addr work",
              "@GenerateFocus(includeFields = {\"home\", \"home\"})",
              Diagnostic.Kind.WARNING,
              "\"home\"}",
              "@GenerateFocus: includeFields on Tenant names \"home\" again. includeFields reads"
                  + " each name once, so a name written again changes nothing. Remove the repeated"
                  + " \"home\" from includeFields on Tenant.",
              "@GenerateFocus(includeFields = {\"home\"})",
              null),
          new Row(
              "Lodge",
              "Addr home, Addr work",
              "@GenerateFocus(excludeFields = {\"work\", \"work\"})",
              Diagnostic.Kind.WARNING,
              "\"work\"}",
              "excludeFields on Lodge names \"work\" again. excludeFields reads each name once, so"
                  + " a name written again changes nothing. Remove the repeated \"work\" from"
                  + " excludeFields on Lodge.",
              "@GenerateFocus(excludeFields = {\"work\"})",
              null),
          new Row(
              "Silo",
              "Map<String, Integer> stock",
              "@GenerateFocus(widenFields = {\"stock\", \"stock\"})",
              Diagnostic.Kind.WARNING,
              "\"stock\"}",
              "widenFields on Silo names \"stock\" again. widenFields reads each name once, so a"
                  + " name written again changes nothing. Remove the repeated \"stock\" from"
                  + " widenFields on Silo.",
              "@GenerateFocus(widenFields = {\"stock\"})",
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
    return compile(rows.map(row -> source(row, annotation.apply(row))));
  }

  private static Compilation compile(Stream<JavaFileObject> sources) {
    return javac()
        .withProcessors(new FocusProcessor())
        .compile(Stream.concat(Stream.of(ADDR), sources).toList());
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

  /** A row that follows its fix lines to {@code followed}, for a shape that draws more than one. */
  private static Row shape(String record, String components, String annotation, String followed) {
    return new Row(record, components, annotation, null, null, null, followed, null);
  }

  /**
   * The column of the {@code nth} copy, counting from one, of {@code text} in a row's annotation.
   */
  private static long column(Row row, String text, int nth) {
    int at = row.annotation().indexOf(text);
    for (int copy = 1; copy < nth; copy++) {
      at = row.annotation().indexOf(text, at + 1);
    }
    return at + 1;
  }

  /** The first name an annotation writes, with its quotes. */
  private static String firstName(String annotation) {
    int open = annotation.indexOf('"');
    return annotation.substring(open, annotation.indexOf('"', open + 1) + 1);
  }

  /** A record with a field named like a method a TraversalPath has, and with that field renamed. */
  private static final JavaFileObject NAME = focusRecord("Name", "String first, String last");

  private static final JavaFileObject RENAMED_NAME =
      focusRecord("Name", "String first, String surname");

  private static JavaFileObject focusRecord(String name, String components) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        @GenerateFocus
        public record %s(%s) {}
        """
            .formatted(name, components));
  }

  @Test
  @DisplayName("a name written again is told once, at its first copy, and warned at each later one")
  void aRepeatedNameIsToldOnce() {
    Row unknown =
        shape(
            "Roster",
            "Addr items",
            "@GenerateFocus(includeFields = {\"itemz\", \"itemz\"})",
            "@GenerateFocus(includeFields = {\"items\"})");
    Row unwidenable =
        shape(
            "Banner",
            "String label",
            "@GenerateFocus(widenFields = {\"label\", \"label\"})",
            "@GenerateFocus");
    Row noNavigator =
        shape(
            "Sticker",
            "String label, Addr address",
            "@GenerateFocus(includeFields = {\"label\", \"label\"})",
            "@GenerateFocus(includeFields = {\"address\"})");
    Row declined =
        shape(
            "Signature",
            "Name name",
            "@GenerateFocus(includeFields = {\"name\", \"name\"})",
            "@GenerateFocus(generateNavigators = false)");
    List<Row> rows = List.of(unknown, unwidenable, noNavigator, declined);
    Compilation written =
        compile(
            Stream.concat(
                Stream.of(NAME), rows.stream().map(row -> source(row, row.annotation()))));
    Compilation followed =
        compile(
            Stream.concat(Stream.of(NAME), rows.stream().map(row -> source(row, row.followed()))));

    assertThat(written).hadErrorCount(1);
    assertThat(written)
        .hadErrorContaining(
            "Did you mean 'items'? Found on Roster: [items]. Replace \"itemz\" with \"items\" in"
                + " includeFields on Roster.")
        .inFile(source(unknown, unknown.annotation()))
        .onLine(ANNOTATION_LINE)
        .atColumn(column(unknown, "\"itemz\"", 1));
    assertThat(written)
        .hadWarningContaining(
            "and none can widen String. Remove widenFields from @GenerateFocus on Banner.")
        .inFile(source(unwidenable, unwidenable.annotation()))
        .onLine(ANNOTATION_LINE)
        .atColumn(column(unwidenable, "\"label\"", 1));
    assertThat(written)
        .hadWarningContaining(
            "includeFields on Sticker names \"label\", whose field has no navigator. Navigator"
                + " generation cannot reach a @GenerateFocus record through String. It needs a"
                + " @GenerateFocus record or a supported container of one. Replace \"label\" in"
                + " includeFields on Sticker with a navigator field from [address].")
        .inFile(source(noNavigator, noNavigator.annotation()))
        .onLine(ANNOTATION_LINE)
        .atColumn(column(noNavigator, "\"label\"", 1));
    // Taking the one name out would leave an empty includeFields, which filters nothing.
    assertThat(written)
        .hadNoteContaining(
            "remove includeFields and add generateNavigators = false to @GenerateFocus on"
                + " Signature");
    for (Row row : rows) {
      String name = firstName(row.annotation());
      assertThat(written)
          .hadWarningContaining("Remove the repeated " + name)
          .inFile(source(row, row.annotation()))
          .onLine(ANNOTATION_LINE)
          .atColumn(column(row, name, 2));
    }
    // One warning for each repeat, and one for each of the two names that draw a warning anyway.
    assertThat(written).hadWarningCount(rows.size() + 2);
    assertThat(followed).succeededWithoutWarnings();
  }

  /** Asserts the error a row draws at the first copy of {@code at}. */
  private static void assertErrorAt(Compilation compilation, Row row, String at, String message) {
    assertThat(compilation)
        .hadErrorContaining(message)
        .inFile(source(row, row.annotation()))
        .onLine(ANNOTATION_LINE)
        .atColumn(column(row, at, 1));
  }

  @Test
  @DisplayName(
      "two names that match none are not both offered one component, and a spelling of the"
          + " component's own name is offered it first")
  void twoUnknownNamesAreNotOfferedOneComponent() {
    Row typos =
        shape(
            "Shelving",
            "Addr items, Addr featured",
            "@GenerateFocus(includeFields = {\"itemz\", \"itens\"})",
            "@GenerateFocus(includeFields = {\"items\", \"featured\"})");
    // The accessor spelling is offered items, though the misspelling is written before it.
    Row spellings =
        shape(
            "Rack",
            "Addr items, Addr featured",
            "@GenerateFocus(includeFields = {\"itms\", \"getItems\"})",
            "@GenerateFocus(includeFields = {\"featured\", \"items\"})");
    List<Row> rows = List.of(typos, spellings);
    Compilation written = compile(Row::annotation, rows.stream());

    assertErrorAt(
        written,
        typos,
        "\"itemz\"",
        "Found on Shelving: [items, featured]. Replace \"itemz\" with \"items\" in"
            + " includeFields on Shelving.");
    assertErrorAt(
        written,
        typos,
        "\"itens\"",
        "Found on Shelving: [items, featured]. Write a component of Shelving in place of"
            + " \"itens\", or remove \"itens\" from includeFields on Shelving.");
    assertErrorAt(
        written,
        spellings,
        "\"itms\"",
        "Found on Rack: [items, featured]. Write a component of Rack in place of \"itms\", or"
            + " remove \"itms\" from includeFields on Rack.");
    assertErrorAt(
        written,
        spellings,
        "\"getItems\"",
        "Did you mean 'items'? Found on Rack: [items, featured]. Replace \"getItems\" with"
            + " \"items\" in includeFields on Rack.");
    assertThat(written).hadErrorCount(4);
    assertThat(written).hadWarningCount(0);
    assertThat(compile(Row::followed, rows.stream())).succeededWithoutWarnings();
  }

  /**
   * A shape whose offer would give a navigator that may need a rename first: the row as written,
   * the text its message points at, the message from the components it found on, the shape once its
   * fix line is followed, renames included, and the {@code Name} record that shape compiles beside.
   */
  record Rename(Row row, String at, String message, Row followed, JavaFileObject beside) {

    @Override
    public String toString() {
      return row.toString();
    }
  }

  /**
   * Offers whose navigator would share a class name with another field's, in each list that gives
   * one, and widenings whose navigator would reach a field named like a method of its path, with
   * navigators on by default and written on, and the widening written off too.
   */
  private static final List<Rename> RENAMES =
      Stream.concat(
              Stream.of("", "On")
                  .flatMap(
                      on -> {
                        String navigators = on.isEmpty() ? "" : "generateNavigators = true, ";
                        String display = "Display" + on;
                        String larder = "Larder" + on;
                        return Stream.of(
                            new Rename(
                                quiet(
                                    display,
                                    "Addr items, Addr Items",
                                    "@GenerateFocus("
                                        + navigators
                                        + "includeFields = {\"Items\", \"itemz\"})"),
                                "\"itemz\"",
                                ("Found on %1$s: [items, Items]. A navigator for 'items' would"
                                        + " share its class name, ItemsNavigator, with the"
                                        + " navigator for '%1$s.Items', since a navigator class"
                                        + " takes its field's name with the first letter in"
                                        + " upper case. Rename '%1$s.items' or '%1$s.Items',"
                                        + " then replace \"itemz\" with \"items\", or its new"
                                        + " name, in includeFields on %1$s.")
                                    .formatted(display),
                                quiet(
                                    display,
                                    "Addr items, Addr featured",
                                    "@GenerateFocus("
                                        + navigators
                                        + "includeFields = {\"featured\", \"items\"})"),
                                NAME),
                            new Rename(
                                quiet(
                                    larder,
                                    "Map<String, Addr> stock, Addr Stock",
                                    "@GenerateFocus(" + navigators + "widenFields = \"stck\")"),
                                "\"stck\"",
                                ("Found on %1$s: [stock, Stock]. A navigator for 'stock' would"
                                        + " share its class name, StockNavigator, with the"
                                        + " navigator for '%1$s.Stock', since a navigator class"
                                        + " takes its field's name with the first letter in"
                                        + " upper case. Rename '%1$s.stock' or '%1$s.Stock',"
                                        + " then replace \"stck\" with \"stock\", or its new"
                                        + " name, in widenFields on %1$s.")
                                    .formatted(larder),
                                quiet(
                                    larder,
                                    "Map<String, Addr> stock, Addr featured",
                                    "@GenerateFocus(" + navigators + "widenFields = \"stock\")"),
                                NAME));
                      }),
              Stream.of(
                      new String[] {"Directory", ""},
                      new String[] {"DirectoryOn", "generateNavigators = true, "},
                      new String[] {"DirectoryOff", "generateNavigators = false, "})
                  .map(
                      directory -> {
                        String record = directory[0];
                        String navigators = directory[1];
                        // Only where navigators are written on does the navigator reach Name and
                        // draw an error; on the default it is declined with a note, and off there
                        // is none.
                        boolean renames = navigators.contains("true");
                        return new Rename(
                            quiet(
                                record,
                                "Map<String, Name> byName",
                                "@GenerateFocus(" + navigators + "widenFields = \"byNam\")"),
                            "\"byNam\"",
                            "Found on "
                                + record
                                + ": [byName]."
                                + (renames
                                    ? " Widened, its navigator would be a TraversalPath, which"
                                        + " has its own last(), so it could not navigate to"
                                        + " 'Name.last'. Rename 'Name.last', then replace"
                                    : " Replace")
                                + " \"byNam\" with \"byName\" in widenFields on "
                                + record
                                + ".",
                            quiet(
                                record,
                                "Map<String, Name> byName",
                                "@GenerateFocus(" + navigators + "widenFields = \"byName\")"),
                            renames ? RENAMED_NAME : NAME);
                      }))
          .toList();

  private static final Compilation RENAMES_WRITTEN =
      compile(
          Stream.concat(
              Stream.of(NAME), RENAMES.stream().map(r -> source(r.row(), r.row().annotation()))));

  @ParameterizedTest(name = "{0}")
  @FieldSource("RENAMES")
  @DisplayName("a name whose navigator would need a rename asks for the rename first")
  void aNameWhoseNavigatorNeedsARenameAsksForItFirst(Rename rename) {
    assertErrorAt(RENAMES_WRITTEN, rename.row(), rename.at(), rename.message());
  }

  @Test
  @DisplayName("each shape that may ask for a rename draws one error, and compiles once followed")
  void eachRenameShapeCompilesOnceFollowed() {
    assertThat(RENAMES_WRITTEN).hadErrorCount(RENAMES.size());
    assertThat(RENAMES_WRITTEN).hadWarningCount(0);
    RENAMES.stream()
        .collect(Collectors.groupingBy(Rename::beside))
        .forEach(
            (name, renames) ->
                assertThat(
                        compile(
                            Stream.concat(
                                Stream.of(name),
                                renames.stream()
                                    .map(r -> source(r.followed(), r.followed().annotation())))))
                    .succeededWithoutWarnings());
  }

  @Test
  @DisplayName("beside widenCollections = true, widenFields is offered no name, since none widens")
  void besideWidenCollectionsNoNameIsOffered() {
    Row row =
        shape(
            "Granary",
            "Map<String, Integer> stock",
            "@GenerateFocus(widenCollections = true, widenFields = \"stck\")",
            "@GenerateFocus(widenCollections = true)");
    Compilation written = compile(Row::annotation, Stream.of(row));

    assertThat(written).hadErrorCount(1);
    assertThat(written)
        .hadErrorContaining(
            "Found on Granary: [stock]. Write a component of Granary in place of \"stck\", or"
                + " remove widenFields from @GenerateFocus on Granary.")
        .inFile(source(row, row.annotation()))
        .onLine(ANNOTATION_LINE)
        .atColumn(column(row, "\"stck\"", 1));
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadWarningContaining("widenFields on Granary changes nothing");
    assertThat(compile(Row::followed, Stream.of(row))).succeededWithoutWarnings();
  }
}
