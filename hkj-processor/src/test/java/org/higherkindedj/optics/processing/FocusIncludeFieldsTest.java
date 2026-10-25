// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * An includeFields name whose field has no navigator warns at the name, with a fix line that can be
 * followed. Most shapes share two compilations: one holds every record as written, where each
 * warning must land on the name it is about, and one holds every record with its fix line followed,
 * under {@code -Xlint:all} and {@code -Werror}, where nothing more may be reported. A shape that
 * compiles against a dependency, runs what it compiles, fails to build, or changes a record the
 * other shapes share is compiled on its own.
 */
@DisplayName("includeFields entries without navigators")
class FocusIncludeFieldsTest {

  private static final JavaFileObject ADDRESS =
      source("Address", "@GenerateFocus", "String street");
  private static final JavaFileObject PLAIN = source("Plain", "", "String street");
  private static final JavaFileObject GENERIC = source("Box<T>", "@GenerateFocus", "T value");
  private static final JavaFileObject CLASH = source("Clash", "@GenerateFocus", "String last");

  // A record without @GenerateFocus as written, which gains it where a fix line offers that.
  private static final JavaFileObject STALL = source("Stall", "", "String street");
  private static final JavaFileObject ANNOTATED_STALL =
      source("Stall", "@GenerateFocus", "String street");

  // Records without @GenerateFocus that the annotation alone would not let navigate: a wildcard
  // instantiation has no navigator, and a field named last clashes with a path method.
  private static final JavaFileObject POUCH = source("Pouch<T>", "", "T value");
  private static final JavaFileObject DIARY = source("Diary", "", "String last");

  /** An SPI container whose key bound depends on itself, so no concrete alternative is invented. */
  private static final JavaFileObject KEYED_BOX =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Box",
          """
          package com.example.hkjtest;
          public record Box<K extends Comparable<K>, V>(V value) {}
          """);

  /** The line every record's annotation is written on, after the package and the five imports. */
  private static final long ANNOTATION_LINE = 7;

  private static JavaFileObject source(String name, String annotation, String components) {
    return JavaFileObjects.forSourceString(
        "com.example." + name.replaceAll("<.*>", ""),
        """
        package com.example;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;
        %s
        public record %s(%s) {}
        """
            .formatted(annotation, name, components));
  }

  /**
   * A record compiled beside the others.
   *
   * @param record the record's name, unique among the shapes
   * @param annotation the annotation, on one line
   * @param components the record's components
   */
  record Shape(String record, String annotation, String components) {

    JavaFileObject source() {
      return FocusIncludeFieldsTest.source(record, annotation, components);
    }

    @Override
    public String toString() {
      return annotation + " on " + record + "(" + components + ")";
    }
  }

  /** A record as written, and as its fix line leads to. */
  record Row(Shape written, Shape followed) {

    @Override
    public String toString() {
      return written.toString();
    }
  }

  private static Row row(String record, String annotation, String components, String followed) {
    return row(record, annotation, components, followed, components);
  }

  private static Row row(
      String record, String annotation, String components, String followed, String fixed) {
    return new Row(new Shape(record, annotation, components), new Shape(record, followed, fixed));
  }

  /**
   * The two compilations most tests read: every record as written, and every record with its fix
   * line followed. Each shape is declared beside the test that reads it, and listed in {@code ROWS}
   * if it has a followed form, in {@code AS_WRITTEN} if it is checked only as written, or in {@code
   * QUIET} if it is checked only followed; a shape listed in none fails its test. The holder reads
   * the test class's lists, so the class's methods may read it, but its static fields must not.
   */
  private static final class Shared {

    static final List<Row> ROWS =
        Stream.of(
                OFFERS.stream().map(Offer::row),
                BESIDE_NAVIGATOR.stream().map(Beside::row),
                BESIDE_ANNOTATION.stream(),
                UNCORRECTED.stream(),
                WIDENINGS.stream().map(Widening::row),
                WILDCARD_CONTAINERS.stream(),
                REPLACEMENTS.stream().map(Replacement::row),
                REFUSED.stream(),
                UNANNOTATABLE.stream(),
                Stream.of(
                    MULTIPLE, ANNOTATING, SHELF, DEPENDENT_BOUND, REMOVAL, ELIGIBLE, CLASHING))
            .flatMap(Function.identity())
            .toList();

    /** Every record as written: the rows, and the shapes whose message another fix line owns. */
    static final List<Shape> AS_WRITTEN =
        Stream.of(
                ROWS.stream().map(Row::written),
                WILDCARD_ELEMENTS.stream(),
                NOTED.stream().map(Noted::shape),
                Stream.of(RACK, RAW, NAVIGATORS_OFF))
            .flatMap(Function.identity())
            .toList();

    static final Compilation WRITTEN =
        compile(support(STALL), AS_WRITTEN.stream().map(Shape::source));

    /** Every row followed, beside the shapes that draw nothing and the navigations they offer. */
    static final Compilation FOLLOWED =
        compile(
            support(ANNOTATED_STALL),
            Stream.of(
                    ROWS.stream().map(Row::followed).map(Shape::source),
                    QUIET.stream().map(Shape::source),
                    BESIDE_NAVIGATOR.stream().map(Beside::use))
                .flatMap(Function.identity()),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror");

    /** The records the shapes refer to, with Stall annotated or not. */
    private static Stream<JavaFileObject> support(JavaFileObject stall) {
      return Stream.of(ADDRESS, PLAIN, GENERIC, CLASH, stall, POUCH, DIARY, KEYED_BOX);
    }

    private static Compilation compile(
        Stream<JavaFileObject> records, Stream<JavaFileObject> shapes, String... options) {
      return javac()
          .withProcessors(new FocusProcessor())
          .withOptions(List.of(options))
          .compile(Stream.concat(records, shapes).toList());
    }
  }

  /**
   * The diagnostics a compilation reports in a file. It first checks that the compilation holds the
   * file, so that a shape left out of it fails rather than passing for one that draws nothing.
   */
  private static List<Diagnostic<? extends JavaFileObject>> in(
      Compilation compilation, JavaFileObject file) {
    String path = file.toUri().getPath();
    assertThat(compilation.sourceFiles()).extracting(each -> each.toUri().getPath()).contains(path);
    return compilation.diagnostics().stream()
        .filter(
            diagnostic ->
                diagnostic.getSource() != null
                    && diagnostic.getSource().toUri().getPath().equals(path))
        .toList();
  }

  /** The warnings the records as written draw in a shape's file. */
  private static List<String> warningsIn(Shape shape) {
    return messagesIn(shape, Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING);
  }

  /** The notes the records as written draw in a shape's file. */
  private static List<String> notesIn(Shape shape) {
    return messagesIn(shape, Diagnostic.Kind.NOTE);
  }

  private static List<String> messagesIn(Shape shape, Diagnostic.Kind... kinds) {
    return in(Shared.WRITTEN, shape.source()).stream()
        .filter(diagnostic -> List.of(kinds).contains(diagnostic.getKind()))
        .map(diagnostic -> diagnostic.getMessage(null))
        .toList();
  }

  /** Checks that a shape as written draws a warning containing the message. */
  private static void warns(Shape shape, String message) {
    assertThat(Shared.WRITTEN).hadWarningContaining(message).inFile(shape.source());
  }

  /**
   * Checks that a shape as written draws a warning containing the message, pointing at the text
   * {@code at} in its annotation.
   */
  private static void warnsAt(Shape shape, String at, String message) {
    assertThat(Shared.WRITTEN)
        .hadWarningContaining(message)
        .inFile(shape.source())
        .onLine(ANNOTATION_LINE)
        .atColumn(shape.annotation().indexOf(at) + 1);
  }

  /** Checks that a shape as written draws a note containing the message. */
  private static void notes(Shape shape, String message) {
    assertThat(Shared.WRITTEN).hadNoteContaining(message).inFile(shape.source());
  }

  /** Checks that the followed compilation holds a file and reports nothing in it. */
  private static void followedCleanly(JavaFileObject file) {
    assertThat(in(Shared.FOLLOWED, file)).isEmpty();
  }

  private static Compilation compile(JavaFileObject subject, String... options) {
    return javac()
        .withProcessors(new FocusProcessor())
        .withOptions(List.of(options))
        .compile(ADDRESS, PLAIN, GENERIC, CLASH, subject);
  }

  private static void clean(Compilation compilation) {
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation.notes()).isEmpty();
  }

  @Test
  @DisplayName("nothing as written is reported outside the shapes' own files")
  void reportsOnlyInTheRecords() {
    List<String> files =
        Shared.AS_WRITTEN.stream().map(shape -> shape.source().toUri().getPath()).toList();
    assertThat(Shared.WRITTEN).succeeded();
    assertThat(Stream.concat(Shared.WRITTEN.warnings().stream(), Shared.WRITTEN.notes().stream()))
        .extracting(
            diagnostic ->
                diagnostic.getSource() == null ? "" : diagnostic.getSource().toUri().getPath())
        .isSubsetOf(files);
  }

  @Test
  @DisplayName("each fix line, followed as written, compiles with nothing more to report")
  void eachFixLineCompiles() {
    clean(Shared.FOLLOWED);
  }

  /** A field without a navigator, with the path its method still returns. */
  record Offer(Row row, String name, String type, String method) {

    @Override
    public String toString() {
      return row.toString();
    }
  }

  private static final List<Offer> OFFERS =
      List.of(
          offer("Passport", "String", "id", "FocusPath<Passport, String> id()"),
          offer("Article", "List<String>", "tags", "TraversalPath<Article, String> tags()"),
          offer("Parcel", "Plain", "plain", "FocusPath<Parcel, Plain> plain()"),
          offer("Counter", "int", "number", "FocusPath<Counter, Integer> number()"),
          offer("Catalogue", "List<Plain>", "plains", "TraversalPath<Catalogue, Plain> plains()"));

  private static Offer offer(String record, String type, String name, String method) {
    return new Offer(
        row(
            record,
            "@GenerateFocus(includeFields = \"" + name + "\")",
            type + " " + name + ", Address address",
            "@GenerateFocus(includeFields = \"address\")"),
        name,
        type,
        method);
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("OFFERS")
  @DisplayName("a non-navigator field warns at its name and offers an eligible field")
  void offersANavigatorField(Offer offer) {
    Shape written = offer.row().written();
    String record = written.record();
    assertThat(warningsIn(written)).hasSize(1);
    warnsAt(
        written,
        "\"",
        "includeFields on "
            + record
            + " names \""
            + offer.name()
            + "\", whose field has no navigator.");
    warns(
        written,
        "Navigator generation cannot reach a @GenerateFocus record through " + offer.type());
    warns(
        written,
        "Replace \""
            + offer.name()
            + "\" in includeFields on "
            + record
            + " with a navigator field from [address].");
    assertGeneratedCodeContains(Shared.WRITTEN, "com.example." + record + "Focus", offer.method());
    assertGeneratedCodeContains(
        Shared.WRITTEN,
        "com.example." + record + "Focus",
        "FocusPath<" + record + ", Address> address()");

    followedCleanly(offer.row().followed().source());
    assertGeneratedCodeContains(
        Shared.FOLLOWED,
        "com.example." + record + "Focus",
        "AddressNavigator<" + record + "> address()");
  }

  private static final Row MULTIPLE =
      row(
          "Survey",
          "@GenerateFocus(includeFields = {\"id\", \"tags\"})",
          "String id, List<String> tags",
          "@GenerateFocus(generateNavigators = false)");

  @Test
  @DisplayName("each entry in an includeFields array warns at that entry")
  void multipleEntries() {
    Shape written = MULTIPLE.written();
    assertThat(warningsIn(written)).hasSize(2);
    for (String name : List.of("id", "tags")) {
      warnsAt(written, '"' + name + '"', "names \"" + name + "\", whose field has no navigator");
    }
    warns(
        written,
        "Remove includeFields and set generateNavigators = false in @GenerateFocus on Survey.");
    followedCleanly(MULTIPLE.followed().source());
  }

  /**
   * A name without a navigator beside a field that navigates, or would once its own fix line is
   * followed: the followed record's method, a navigation through it, and the field's own warnings
   * and notes as written.
   */
  record Beside(
      Row row,
      String field,
      String method,
      String navigation,
      List<String> warnings,
      List<String> notes) {

    JavaFileObject use() {
      String record = row.written().record();
      return JavaFileObjects.forSourceString(
          "com.example.Use" + record,
          """
          package com.example;
          class Use%s {
            Object path() { return %sFocus.%s; }
          }
          """
              .formatted(record, record, navigation));
    }

    @Override
    public String toString() {
      return row.toString();
    }
  }

  private static final List<Beside> BESIDE_NAVIGATOR =
      List.of(
          beside(
              "C05",
              "Address address",
              "address",
              "@GenerateFocus(includeFields = \"address\")",
              "Address address",
              "AddressNavigator<C05> address()",
              "address().street()",
              List.of(),
              List.of()),
          beside(
              "C10",
              "Map<String, Address> sites",
              "sites",
              "@GenerateFocus(includeFields = \"sites\", widenFields = \"sites\")",
              "Map<String, Address> sites",
              "SitesNavigator<C10> sites()",
              "sites().street()",
              List.of("Add widenFields = \"sites\" to @GenerateFocus on C10."),
              List.of()),
          beside(
              "C11",
              "Box<?> boxed",
              "boxed",
              "@GenerateFocus(includeFields = \"boxed\")",
              "Box<Object> boxed",
              "BoxedNavigator<C11, Object> boxed()",
              "boxed().value()",
              List.of(),
              List.of("Write Box<Object>, or another concrete instantiation, in place of Box<?>")),
          beside(
              "C12",
              "Map<String, Box<?>> boxed",
              "boxed",
              "@GenerateFocus(includeFields = \"boxed\", widenFields = \"boxed\")",
              "Map<String, Box<Object>> boxed",
              "BoxedNavigator<C12, Object> boxed()",
              "boxed().value()",
              List.of(
                  "Add widenFields = \"boxed\" to @GenerateFocus on C12 to step into its Box"
                      + " elements."),
              List.of()));

  private static Beside beside(
      String record,
      String component,
      String field,
      String correction,
      String corrected,
      String method,
      String navigation,
      List<String> warnings,
      List<String> notes) {
    return new Beside(
        row(
            record,
            "@GenerateFocus(includeFields = {\"id\", \"" + field + "\"})",
            "String id, " + component,
            correction,
            "String id, " + corrected),
        field,
        method,
        navigation,
        warnings,
        notes);
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("BESIDE_NAVIGATOR")
  @DisplayName("another included navigator or its correction makes removal the followable fix")
  void removesNameBesideNavigator(Beside beside) {
    Shape written = beside.row().written();
    String record = written.record();
    assertThat(warningsIn(written))
        .hasSize(1 + beside.warnings().size())
        .allSatisfy(
            warning ->
                assertThat(warning).doesNotContain("Replace \"id\"", "generateNavigators = false"));
    warnsAt(written, "\"", "Remove \"id\" from includeFields on " + record + ".");
    beside.warnings().forEach(warning -> warnsAt(written, '"' + beside.field() + '"', warning));
    assertThat(notesIn(written)).hasSize(beside.notes().size());
    beside.notes().forEach(note -> notes(written, note));

    followedCleanly(beside.row().followed().source());
    followedCleanly(beside.use());
    assertGeneratedCodeContains(
        Shared.FOLLOWED, "com.example." + record + "Focus", beside.method());
  }

  private static final List<Row> BESIDE_ANNOTATION =
      List.of(
          row(
              "Market",
              "@GenerateFocus(includeFields = {\"id\", \"stall\"})",
              "String id, Stall stall",
              "@GenerateFocus(includeFields = \"stall\")"),
          row(
              "Bazaar",
              "@GenerateFocus(includeFields = {\"id\", \"stall\"})",
              "String id, List<Stall> stall",
              "@GenerateFocus(includeFields = \"stall\")"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("BESIDE_ANNOTATION")
  @DisplayName("another included source record can keep navigation through its annotation fix")
  void removesNameBesideAnnotationSuggestion(Row row) {
    Shape written = row.written();
    String record = written.record();
    assertThat(warningsIn(written)).hasSize(2);
    warnsAt(written, "\"", "Remove \"id\" from includeFields on " + record + ".");
    warnsAt(written, "\"stall\"", "Alternatively, annotate record Stall with @GenerateFocus.");
    followedCleanly(row.followed().source());
    assertGeneratedCodeContains(
        Shared.FOLLOWED,
        "com.example." + record + "Focus",
        "StallNavigator<" + record + "> stall()");
  }

  private static final List<Row> UNCORRECTED =
      List.of(
          row(
              "Meter",
              "@GenerateFocus(includeFields = {\"id\", \"other\"})",
              "String id, int other",
              "@GenerateFocus(generateNavigators = false)"),
          row(
              "League",
              "@GenerateFocus(includeFields = {\"id\", \"other\"})",
              "String id, Map<String, Clash> other",
              "@GenerateFocus(generateNavigators = false)"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("UNCORRECTED")
  @DisplayName("an included field without a usable correction does not keep navigation enabled")
  void noOtherNavigatorCorrection(Row row) {
    Shape written = row.written();
    assertThat(warningsIn(written)).hasSize(2);
    warnsAt(
        written,
        "\"",
        "Remove includeFields and set generateNavigators = false in @GenerateFocus on "
            + written.record()
            + ".");
    followedCleanly(row.followed().source());
  }

  @Test
  @DisplayName("a classpath record cannot be annotated by the consumer")
  void externalRecordHasNoAnnotationSuggestion(@TempDir Path temp) throws IOException {
    Compilation upstream = javac().withOptions("-proc:none").compile(PLAIN);
    clean(upstream);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    String annotation = "@GenerateFocus(includeFields = \"plain\")";
    JavaFileObject subject = source("Subject", annotation, "Plain plain, Address address");
    Compilation written =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .compile(ADDRESS, subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written)
        .hadWarningContaining("Plain is a record without @GenerateFocus.")
        .inFile(subject)
        .onLine(ANNOTATION_LINE)
        .atColumn(annotation.indexOf('"') + 1);
    assertThat(written)
        .hadWarningContaining(
            "Replace \"plain\" in includeFields on Subject with a navigator field from [address].");
    assertThat(written.warnings().getFirst().getMessage(null))
        .doesNotContain("Alternatively", "annotate record Plain");
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                ADDRESS,
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"address\")",
                    "Plain plain, Address address"));
    clean(followed);
    assertGeneratedCodeContains(
        followed, "com.example.SubjectFocus", "AddressNavigator<Subject> address()");
  }

  // The fix annotates Stall, which the followed compilation holds annotated; the record itself
  // stays as written.
  private static final Row ANNOTATING =
      row(
          "Fair",
          "@GenerateFocus(includeFields = \"stall\")",
          "Stall stall",
          "@GenerateFocus(includeFields = \"stall\")");

  @Test
  @DisplayName("annotating an unannotated record is a followable alternative")
  void annotatingTheRecord() {
    Shape written = ANNOTATING.written();
    assertThat(warningsIn(written)).hasSize(1);
    warns(written, "Stall is a record without @GenerateFocus.");
    warns(written, "Alternatively, annotate record Stall with @GenerateFocus.");
    followedCleanly(ANNOTATING.followed().source());
    assertGeneratedCodeContains(
        Shared.FOLLOWED, "com.example.FairFocus", "StallNavigator<Fair> stall()");
  }

  /** An unwidened container of records, with the words its fix line widens it in. */
  record Widening(Row row, String fix) {

    @Override
    public String toString() {
      return row.toString();
    }
  }

  private static final List<Widening> WIDENINGS =
      List.of(
          widening(
              "Atlas",
              "Map<String, Address> sites",
              "@GenerateFocus(includeFields = \"sites\")",
              "Add widenFields = \"sites\"",
              "@GenerateFocus(includeFields = \"sites\", widenFields = \"sites\")"),
          widening(
              "Gazette",
              "Address[] sites",
              "@GenerateFocus(generateNavigators = true, includeFields = \"sites\")",
              "Add widenFields = \"sites\"",
              "@GenerateFocus(generateNavigators = true, includeFields = \"sites\","
                  + " widenFields = \"sites\")"),
          widening(
              "Registry",
              "@Nullable Map<String, Address> sites",
              "@GenerateFocus(includeFields = \"sites\", widenFields = \"other\")",
              "Add \"sites\" to widenFields",
              "@GenerateFocus(includeFields = \"sites\", widenFields = {\"other\", \"sites\"})"));

  private static Widening widening(
      String record, String component, String annotation, String fix, String followed) {
    return new Widening(
        row(record, annotation, component + ", Map<String, Address> other", followed), fix);
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("WIDENINGS")
  @DisplayName(
      "an unwidened container warns once, and the offered widening generates its navigator")
  void wideningIsFollowable(Widening widening) {
    Shape written = widening.row().written();
    String record = written.record();
    assertThat(warningsIn(written)).hasSize(1);
    warnsAt(written, "\"sites\"", record + " does not widen \"sites\". " + widening.fix());
    assertThat(notesIn(written)).noneMatch(note -> note.contains("has no navigator over"));
    followedCleanly(widening.row().followed().source());
    assertGeneratedCodeContains(
        Shared.FOLLOWED,
        "com.example." + record + "Focus",
        "SitesNavigator<" + record + "> sites()");
  }

  private static final Row SHELF =
      row(
          "Shelf",
          "@GenerateFocus(includeFields = \"boxes\")",
          "Map<String, Box<?>> boxes, Address address",
          "@GenerateFocus(includeFields = \"boxes\", widenFields = \"boxes\")",
          "Map<String, Box<Object>> boxes, Address address");

  /** Shelf with only its widening followed, which leaves the wildcard target's note to follow. */
  private static final Shape RACK =
      new Shape(
          "Rack",
          "@GenerateFocus(includeFields = \"boxes\", widenFields = \"boxes\")",
          "Map<String, Box<?>> boxes, Address address");

  @Test
  @DisplayName("an unwidened wildcard target warns before its existing wildcard note can apply")
  void unwidenedWildcardTarget() {
    Shape written = SHELF.written();
    assertThat(warningsIn(written)).hasSize(1);
    assertThat(notesIn(written)).isEmpty();
    warnsAt(
        written,
        "\"",
        "Add widenFields = \"boxes\" to @GenerateFocus on Shelf to step into its Box elements.");
    assertThat(warningsIn(RACK)).isEmpty();
    assertThat(notesIn(RACK)).hasSize(1);
    notes(RACK, "Write Box<Object>");
    followedCleanly(SHELF.followed().source());
    assertGeneratedCodeContains(
        Shared.FOLLOWED, "com.example.ShelfFocus", "BoxesNavigator<Shelf, Object> boxes()");
  }

  private static final List<Row> WILDCARD_CONTAINERS =
      List.of(
          wildcardContainer("Branches", "\"sites\""),
          wildcardContainer("Outlets", "\"id\", \"sites\""),
          wildcardContainer("Franchise", "\"sites\", \"id\""));

  private static Row wildcardContainer(String record, String included) {
    return row(
        record,
        "@GenerateFocus(includeFields = {" + included + "})",
        "String id, Map<String, ? extends Address> sites, Address address",
        "@GenerateFocus(includeFields = \"sites\", widenFields = \"sites\")",
        "String id, Map<String, Address> sites, Address address");
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("WILDCARD_CONTAINERS")
  @DisplayName("a refused wildcard container offers its concrete type and widening together")
  void wildcardContainerCorrection(Row row) {
    Shape written = row.written();
    String record = written.record();
    boolean withId = written.annotation().contains("\"id\"");
    assertThat(warningsIn(written)).hasSize(withId ? 2 : 1);
    assertThat(notesIn(written)).isEmpty();
    warnsAt(
        written,
        "\"sites\"",
        "holds Address records, but its wildcard type arguments prevent widening");
    warns(written, "Write Map<String, Address> in place of Map<String, ? extends Address>");
    warns(written, "add widenFields = \"sites\" to @GenerateFocus on " + record);
    if (withId) {
      warns(written, "Remove \"id\" from includeFields on " + record + ".");
    }
    followedCleanly(row.followed().source());
    assertGeneratedCodeContains(
        Shared.FOLLOWED,
        "com.example." + record + "Focus",
        "SitesNavigator<" + record + "> sites()");
  }

  /** Two names without navigators, in list order. */
  record Replacement(Row row, String first, String remaining) {

    @Override
    public String toString() {
      return row.toString();
    }
  }

  private static final List<Replacement> REPLACEMENTS =
      List.of(replacement("Ticket", "id", "tags"), replacement("Voucher", "tags", "id"));

  private static Replacement replacement(String record, String first, String remaining) {
    return new Replacement(
        row(
            record,
            "@GenerateFocus(includeFields = {\"" + first + "\", \"" + remaining + "\"})",
            "String id, List<String> tags, Address address",
            "@GenerateFocus(includeFields = \"address\")"),
        first,
        remaining);
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("REPLACEMENTS")
  @DisplayName("only the first invalid entry in list order receives the navigator replacement")
  void oneReplacementPerList(Replacement replacement) {
    Shape written = replacement.row().written();
    String record = written.record();
    String first = replacement.first();
    String remaining = replacement.remaining();
    assertThat(warningsIn(written)).hasSize(2);
    assertThat(notesIn(written)).isEmpty();
    warnsAt(
        written,
        '"' + first + '"',
        "Replace \""
            + first
            + "\" in includeFields on "
            + record
            + " with a navigator field from [address].");
    warnsAt(
        written,
        '"' + remaining + '"',
        "Remove \"" + remaining + "\" from includeFields on " + record + ".");
    followedCleanly(replacement.row().followed().source());
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      List<Address> | java.util.List.of(new Address("High Street")) | getAll
      Optional<Address> | Optional.of(new Address("High Street")) | getOptional
      List<? extends Address> | java.util.List.of(new Address("High Street")) | getAll
      List<Optional<Address>> | java.util.List.of(Optional.of(new Address("High Street"))) | getAll
      org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.list.ListKind.Witness, Address> | org.higherkindedj.hkt.list.ListKindHelper.LIST.widen(java.util.List.of(new Address("High Street"))) | getAll
      List<@Nullable Address> | java.util.Arrays.asList(null, new Address("High Street")) | getAll
      Optional<@Nullable Address> | Optional.of(new Address("High Street")) | getOptional
      org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.list.ListKind.Witness, @Nullable Address> | org.higherkindedj.hkt.list.ListKindHelper.LIST.widen(java.util.Arrays.asList(null, new Address("High Street"))) | getAll
      """)
  @DisplayName("a container of records draws no warning, and navigating it passes over nulls")
  void containerOfRecordsNavigates(String container, String value, String getter)
      throws ReflectiveOperationException {
    String read = "SubjectFocus.members().street()." + getter + "(subject)";
    if (getter.equals("getOptional")) {
      read += ".stream().toList()";
    }
    JavaFileObject use =
        JavaFileObjects.forSourceString(
            "com.example.Use",
            """
        package com.example;
        import java.util.Optional;
        public class Use {
          public static Object run() {
            var subject = new Subject(%s, new Address("Other Street"));
            return %s;
          }
        }
        """
                .formatted(value, read));
    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                ADDRESS,
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"members\")",
                    container + " members, Address address"),
                use);

    clean(compilation);
    assertThat(
            new RuntimeCompilationHelper.CompiledResult(compilation)
                .invokeStatic("com.example.Use", "run"))
        .isEqualTo(List.of("High Street"));
  }

  private static final List<Shape> WILDCARD_ELEMENTS =
      List.of(
          members("Crate", "List<Box<?>>"),
          members("Hamper", "List<Box>"),
          members("Basket", "Optional<Box<?>>"),
          members(
              "Bundle",
              "org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.list.ListKind.Witness, Box<?>>"));

  private static Shape members(String record, String container) {
    return new Shape(record, "@GenerateFocus(includeFields = \"members\")", container + " members");
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("WILDCARD_ELEMENTS")
  @DisplayName("a container of raw or wildcard records draws the element's note, not this warning")
  void wildcardElementDrawsItsNote(Shape shape) {
    assertThat(warningsIn(shape)).isEmpty();
    notes(shape, "Navigator for field 'members' is not generated");
  }

  private static final Shape RAW = members("Heap", "List");

  @Test
  @DisplayName("a raw container warns, since it reaches no record")
  void rawContainerWarns() {
    assertThat(warningsIn(RAW)).hasSize(1);
    warns(RAW, "Navigator generation cannot reach a @GenerateFocus record through");
  }

  @Test
  @DisplayName("a list of records from a dependency without a companion draws that note instead")
  void unpublishedElementDrawsItsNote(@TempDir Path temp) throws IOException {
    Compilation upstream = javac().withOptions("-proc:none").compile(ADDRESS);
    clean(upstream);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    Compilation written =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .compile(
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"members\")",
                    "List<Address> members"));

    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(0);
    assertThat(written).hadNoteContaining("carries @GenerateFocus, but no public");
  }

  @Test
  @DisplayName("a list of a generic record navigates through its instantiation")
  void genericElementNavigates() throws ReflectiveOperationException {
    JavaFileObject use =
        JavaFileObjects.forSourceString(
            "com.example.Use",
            """
        package com.example;
        public class Use {
          public static Object run() {
            var subject = new Subject(java.util.List.of(new Box<String>("High Street")));
            return SubjectFocus.members().value().getAll(subject);
          }
        }
        """);
    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                GENERIC,
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"members\")",
                    "List<Box<String>> members"),
                use);

    clean(compilation);
    assertThat(
            new RuntimeCompilationHelper.CompiledResult(compilation)
                .invokeStatic("com.example.Use", "run"))
        .isEqualTo(List.of("High Street"));
  }

  @Test
  @DisplayName("a list of records navigates through a companion published to another package")
  void redirectedPublishedCompanion(@TempDir Path temp) throws IOException {
    Compilation upstream =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(
                source(
                    "Address", "@GenerateFocus(targetPackage = \"com.paths\")", "String street"));
    clean(upstream);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"members\")",
                    "List<Address> members"),
                JavaFileObjects.forSourceString(
                    "com.example.Use",
                    """
                package com.example;
                class Use {
                  Object path() { return SubjectFocus.members().street(); }
                }
                """));

    clean(compilation);
    assertGeneratedCodeContains(
        compilation, "com.example.SubjectFocus", "MembersNavigator<Subject> members()");
  }

  private static final List<Row> REFUSED =
      List.of(
          refused("Inventory", "Map<String, ?>", ""),
          // -Xlint:all reports the raw Map in the record itself, which no fix line changes.
          refused("Manifest", "Map", "@SuppressWarnings(\"rawtypes\") "),
          refused("Lookup", "Map<String, ? extends String>", ""),
          refused("Roster", "Map<?, Box<?>>", ""),
          refused("Census", "Map<?, Clash>", ""));

  private static Row refused(String record, String container, String suppression) {
    return row(
        record,
        "@GenerateFocus(includeFields = \"items\")",
        container + " items",
        suppression + "@GenerateFocus(generateNavigators = false)");
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("REFUSED")
  @DisplayName(
      "a refused container without a usable record correction retains a followable plain path")
  void refusedContainerWithoutCorrection(Row row) {
    assertThat(warningsIn(row.written()))
        .singleElement()
        .asString()
        .contains("Remove includeFields and set generateNavigators = false")
        .doesNotContain("Write Map", "add widenFields", "Add widenFields", "which composes");
    followedCleanly(row.followed().source());
  }

  private static final Row DEPENDENT_BOUND =
      row(
          "Vault",
          "@GenerateFocus(includeFields = \"items\")",
          "com.example.hkjtest.Box<?, Address> items",
          "@GenerateFocus(generateNavigators = false)");

  @Test
  @DisplayName("an SPI container with a dependent bound receives no invented concrete alternative")
  void refusedContainerWithDependentBound() {
    assertThat(warningsIn(DEPENDENT_BOUND.written()))
        .singleElement()
        .asString()
        .contains("Remove includeFields and set generateNavigators = false")
        .doesNotContain("Write Box", "widenFields");
    followedCleanly(DEPENDENT_BOUND.followed().source());
  }

  private static final Row REMOVAL =
      row(
          "Dossier",
          "@GenerateFocus(includeFields = {\"sites\", \"id\", \"tags\"})",
          "String id, List<String> tags, Map<String, Clash> sites, Address address",
          "@GenerateFocus(includeFields = \"address\")");

  @Test
  @DisplayName("removing an unwidened clashing field leaves the first replacement for another name")
  void removalDoesNotConsumeReplacement() {
    Shape written = REMOVAL.written();
    assertThat(warningsIn(written)).hasSize(3);
    warns(
        written,
        "Rename 'Clash.last', then add widenFields = \"sites\" to @GenerateFocus on Dossier, or"
            + " remove \"sites\" from includeFields on Dossier to keep its plain path.");
    warns(
        written,
        "Replace \"id\" in includeFields on Dossier with a navigator field from [address].");
    warns(written, "Remove \"tags\" from includeFields on Dossier.");
    followedCleanly(REMOVAL.followed().source());
  }

  @Test
  @DisplayName("the warning stops a -Werror build")
  void warningStopsWerror() {
    Compilation written =
        compile(
            source("Subject", "@GenerateFocus(includeFields = \"id\")", "String id"), "-Werror");
    assertThat(written).failed();
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadErrorContaining("warnings found and -Werror specified");
  }

  private static final Row ELIGIBLE =
      row(
          "Profile",
          "@GenerateFocus(generateNavigators = true, includeFields = \"id\")",
          "String id, Address address, Map<String, Address> sites, Clash clash",
          "@GenerateFocus(generateNavigators = true, includeFields = \"address\")");

  @Test
  @DisplayName("candidate fields must widen to a navigator without inherited name clashes")
  void onlyOffersEligibleFields() {
    Shape written = ELIGIBLE.written();
    assertThat(warningsIn(written)).hasSize(1);
    warns(written, "with a navigator field from [address].");
    followedCleanly(ELIGIBLE.followed().source());
  }

  // Valid requests, which draw nothing as written, so they are compiled only beside the followed
  // rows.
  private static final List<Shape> QUIET =
      List.of(
          new Shape("Residence", "@GenerateFocus(includeFields = \"address\")", "Address address"),
          new Shape(
              "Agency",
              "@GenerateFocus(includeFields = \"sites\", widenCollections = true)",
              "Map<String, Address> sites"),
          new Shape("Notice", "@GenerateFocus(excludeFields = \"id\")", "String id"),
          new Shape("Memo", "@GenerateFocus(includeFields = {})", "String id"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("QUIET")
  @DisplayName("valid navigator requests and excludeFields draw nothing")
  void quiet(Shape shape) {
    followedCleanly(shape.source());
  }

  /** A target another note explains, with part of that note. */
  record Noted(Shape shape, String note) {

    @Override
    public String toString() {
      return shape.toString();
    }
  }

  private static final List<Noted> NOTED =
      List.of(
          new Noted(
              new Shape("Locker", "@GenerateFocus(includeFields = \"box\")", "Box<?> box"),
              "is not generated"),
          new Noted(
              new Shape("Cabinet", "@GenerateFocus(includeFields = \"box\")", "Box box"),
              "is not generated"),
          new Noted(
              new Shape("Duel", "@GenerateFocus(includeFields = \"clash\")", "Clash clash"),
              "has no navigator into"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("NOTED")
  @DisplayName("existing target notes gain no extra includeFields warning")
  void existingNotes(Noted noted) {
    assertThat(warningsIn(noted.shape())).isEmpty();
    notes(noted.shape(), noted.note());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      @GenerateFocus(includeFields = "sites", widenFields = "sites") | Map<String, ? extends Address> sites | has a wildcard type argument
      @GenerateFocus(includeFields = "sites") | org.higherkindedj.hkt.either.Either<String, ? extends Address> sites | has a wildcard type argument
      @GenerateFocus(generateNavigators = true, includeFields = "clash") | Clash clash | cannot navigate to
      @GenerateFocus(includeFields = "missing") | String id | which is not a component
      """)
  @DisplayName("existing errors gain no extra includeFields warning")
  void existingErrors(String annotation, String components, String error) {
    Compilation written = compile(source("Subject", annotation, components));
    assertThat(written).failed();
    assertThat(written).hadErrorCount(1);
    assertThat(written).hadWarningCount(0);
    assertThat(written).hadErrorContaining(error);
  }

  private static final Shape NAVIGATORS_OFF =
      new Shape(
          "Archive",
          "@GenerateFocus(generateNavigators = false, includeFields = \"id\")",
          "String id");

  @Test
  @DisplayName("a list with navigators off draws only the existing unread-list warning")
  void navigatorsOff() {
    assertThat(warningsIn(NAVIGATORS_OFF)).hasSize(1);
    warns(NAVIGATORS_OFF, "generateNavigators = false turns navigators off");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Invalid invalid", "Map<String, Invalid> invalid", "Invalid[] invalid"})
  @DisplayName("a target whose annotation javac cannot read is left to javac's error")
  void unreadableTarget(String component) {
    Compilation written =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(
                source("Invalid", "@GenerateFocus(includeFields = Missing.NAME)", "String id"),
                source("Subject", "@GenerateFocus(includeFields = \"invalid\")", component));
    assertThat(written).failed();
    assertThat(written).hadErrorCount(1);
    assertThat(written).hadErrorContaining("cannot find symbol");
    assertThat(written).hadWarningCount(0);
  }

  @Test
  @DisplayName("an unresolved component type is left to javac's error")
  void unresolvedType() {
    Compilation written =
        compile(
            source("Subject", "@GenerateFocus(includeFields = \"missing\")", "Missing missing"));
    assertThat(written).failed();
    assertThat(written).hadErrorContaining("cannot find symbol");
    assertThat(written).hadWarningCount(0);
  }

  private static final List<Row> UNANNOTATABLE =
      List.of(
          row(
              "Wallet",
              "@GenerateFocus(includeFields = \"pouch\")",
              "Pouch<?> pouch",
              "@GenerateFocus(generateNavigators = false)"),
          row(
              "Journal",
              "@GenerateFocus(includeFields = \"diary\")",
              "Diary diary",
              "@GenerateFocus(generateNavigators = false)"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("UNANNOTATABLE")
  @DisplayName("annotation is not offered alone when a target still could not navigate")
  void annotationWouldNotSuffice(Row row) {
    assertThat(warningsIn(row.written()))
        .singleElement()
        .asString()
        .doesNotContain("Alternatively");
    followedCleanly(row.followed().source());
  }

  private static final Row CLASHING =
      row(
          "Contest",
          "@GenerateFocus(generateNavigators = true, includeFields = \"sites\")",
          "Map<String, Clash> sites",
          "@GenerateFocus(generateNavigators = false)");

  @Test
  @DisplayName("widening is not offered alone where the navigator would clash")
  void wideningWouldExposeAClash() {
    assertThat(warningsIn(CLASHING.written())).hasSize(1);
    warns(
        CLASHING.written(),
        "Widened, its navigator would be a TraversalPath, which has its own last(), so it could not"
            + " navigate to 'Clash.last'. Rename 'Clash.last', then add widenFields = \"sites\" to"
            + " @GenerateFocus on Contest, or remove includeFields and replace generateNavigators ="
            + " true with generateNavigators = false on Contest to keep its plain path.");
    followedCleanly(CLASHING.followed().source());
    // Following the rename instead gives the field its navigator, and the warning goes. The rename
    // changes Clash itself, so it is compiled on its own.
    clean(
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Werror")
            .compile(
                source("Clash", "@GenerateFocus", "String surname"),
                source(
                    "Contest",
                    "@GenerateFocus(generateNavigators = true, includeFields = \"sites\","
                        + " widenFields = \"sites\")",
                    "Map<String, Clash> sites")));
  }

  @Test
  @DisplayName("an unpublished target keeps only its existing note")
  void unpublishedTarget(@TempDir Path temp) throws IOException {
    Compilation upstream = javac().withOptions("-proc:none").compile(ADDRESS);
    clean(upstream);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    Compilation downstream =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .withOptions("-Werror")
            .compile(
                source(
                    "Subject", "@GenerateFocus(includeFields = \"address\")", "Address address"));
    assertThat(downstream).succeededWithoutWarnings();
    assertThat(downstream).hadNoteCount(1);
    assertThat(downstream).hadNoteContaining("but no public com.example.AddressFocus");
  }

  @Test
  @DisplayName("a target naming a hidden type keeps only its existing note")
  void hiddenTarget(@TempDir Path temp) throws IOException {
    JavaFileObject upstreamSource =
        JavaFileObjects.forSourceString(
            "com.model.Address",
            """
        package com.model;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        record Secret(String value) {}
        @GenerateFocus
        public record Address(Secret secret) {}
        """);
    Compilation upstream = javac().withProcessors(new FocusProcessor()).compile(upstreamSource);
    clean(upstream);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    Compilation downstream =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .withOptions("-Werror")
            .compile(
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"address\")",
                    "com.model.Address address"));
    assertThat(downstream).succeededWithoutWarnings();
    assertThat(downstream).hadNoteCount(1);
    assertThat(downstream).hadNoteContaining("cannot be reached from");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("annotation is not offered when the navigator would name a hidden type")
  void unannotatedHiddenTarget(boolean fromClasspath, @TempDir Path temp) throws IOException {
    JavaFileObject upstreamSource =
        JavaFileObjects.forSourceString(
            "com.model.Plain",
            """
        package com.model;
        record Secret(String value) {}
        public record Plain(Secret secret) {}
        """);
    JavaFileObject subject =
        source("Subject", "@GenerateFocus(includeFields = \"plain\")", "com.model.Plain plain");
    Compilation downstream;
    if (fromClasspath) {
      Compilation upstream = javac().withOptions("-proc:none").compile(upstreamSource);
      clean(upstream);
      Path dependency = classDirectory(upstream, temp.resolve("dependency"));
      downstream =
          javac()
              .withProcessors(new FocusProcessor())
              .withClasspath(classpathWith(dependency))
              .compile(subject);
    } else {
      downstream = javac().withProcessors(new FocusProcessor()).compile(upstreamSource, subject);
    }
    assertThat(downstream).succeeded();
    assertThat(downstream).hadWarningCount(1);
    assertThat(downstream.warnings())
        .allSatisfy(
            warning -> assertThat(warning.getMessage(null)).doesNotContain("Alternatively"));
  }
}
