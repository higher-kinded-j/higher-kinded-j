// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("includeFields entries without navigators")
class FocusIncludeFieldsTest {

  private static final JavaFileObject ADDRESS =
      source("Address", "@GenerateFocus", "String street");
  private static final JavaFileObject PLAIN = source("Plain", "", "String street");
  private static final JavaFileObject GENERIC = source("Box<T>", "@GenerateFocus", "T value");
  private static final JavaFileObject CLASH = source("Clash", "@GenerateFocus", "String last");

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

  private static Compilation compile(JavaFileObject subject, String... options) {
    return javac()
        .withProcessors(new FocusProcessor())
        .withOptions(List.of(options))
        .compile(ADDRESS, PLAIN, GENERIC, CLASH, subject);
  }

  private static void clean(Compilation compilation) {
    assertThat(compilation).succeededWithoutWarnings();
    org.assertj.core.api.Assertions.assertThat(compilation.notes())
        .noneMatch(note -> note.getMessage(null).contains("@GenerateFocus:"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          String id | id | String | FocusPath<Subject, String> id()
          List<String> tags | tags | List<String> | TraversalPath<Subject, String> tags()
          Plain plain | plain | Plain | FocusPath<Subject, Plain> plain()
          int number | number | int | FocusPath<Subject, Integer> number()
          List<Address> addresses | addresses | List<Address> | TraversalPath<Subject, Address> addresses()
          Optional<Address> optional | optional | Optional<Address> | AffinePath<Subject, Address> optional()
          """)
  @DisplayName("a non-navigator field warns at its name and offers an eligible field")
  void offersANavigatorField(String component, String name, String type, String method) {
    String annotation = "@GenerateFocus(includeFields = \"" + name + "\")";
    JavaFileObject subject = source("Subject", annotation, component + ", Address address");
    Compilation written = compile(subject);

    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written)
        .hadWarningContaining(
            "includeFields on Subject names \"" + name + "\", whose field cannot have a navigator.")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    assertThat(written)
        .hadWarningContaining(
            "Navigator generation cannot reach a @GenerateFocus record through " + type);
    assertThat(written)
        .hadWarningContaining(
            "Replace \""
                + name
                + "\" in includeFields on Subject with a navigator field from [address].");
    assertGeneratedCodeContains(written, "com.example.SubjectFocus", method);
    assertGeneratedCodeContains(
        written, "com.example.SubjectFocus", "FocusPath<Subject, Address> address()");

    Compilation followed =
        compile(
            source(
                "Subject",
                "@GenerateFocus(includeFields = \"address\")",
                component + ", Address address"),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror");
    clean(followed);
    assertGeneratedCodeContains(
        followed, "com.example.SubjectFocus", "AddressNavigator<Subject> address()");
  }

  @Test
  @DisplayName("each entry in an includeFields array warns at that entry")
  void multipleEntries() {
    String annotation = "@GenerateFocus(includeFields = {\"id\", \"tags\"})";
    JavaFileObject subject = source("Subject", annotation, "String id, List<String> tags");
    Compilation written = compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(2);
    for (String name : List.of("id", "tags")) {
      assertThat(written)
          .hadWarningContaining("names \"" + name + "\", whose field cannot have a navigator")
          .inFile(subject)
          .onLine(7)
          .atColumn(annotation.indexOf('"' + name + '"') + 1);
    }
    assertThat(written)
        .hadWarningContaining(
            "Remove includeFields and set generateNavigators = false in @GenerateFocus on Subject.");
    clean(
        compile(
            source(
                "Subject",
                "@GenerateFocus(generateNavigators = false)",
                "String id, List<String> tags"),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      C05 | Address address | address | @GenerateFocus(includeFields = "address") | Address address | AddressNavigator<C05> address() | address().street()
      C10 | Map<String, Address> sites | sites | @GenerateFocus(includeFields = "sites", widenFields = "sites") | Map<String, Address> sites | SitesNavigator<C10> sites() | sites().street()
      C11 | Box<?> boxed | boxed | @GenerateFocus(includeFields = "boxed") | Box<Object> boxed | BoxedNavigator<C11, Object> boxed() | boxed().value()
      C12 | Map<String, Box<?>> boxed | boxed | @GenerateFocus(includeFields = "boxed", widenFields = "boxed") | Map<String, Box<Object>> boxed | BoxedNavigator<C12, Object> boxed() | boxed().value()
      """)
  @DisplayName("another included navigator or its correction makes removal the followable fix")
  void removesNameBesideNavigator(
      String record,
      String component,
      String field,
      String correction,
      String correctedComponent,
      String method,
      String navigation) {
    String annotation = "@GenerateFocus(includeFields = {\"id\", \"" + field + "\"})";
    JavaFileObject subject = source(record, annotation, "String id, " + component);
    Compilation written = compile(subject, "-Werror");
    assertThat(written).failed();
    assertThat(written).hadErrorContaining("warnings found and -Werror specified");
    assertThat(written).hadWarningCount(record.equals("C10") || record.equals("C12") ? 2 : 1);
    assertThat(written)
        .hadWarningContaining("Remove \"id\" from includeFields on " + record + ".")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    org.assertj.core.api.Assertions.assertThat(written.warnings())
        .allSatisfy(
            warning ->
                org.assertj.core.api.Assertions.assertThat(warning.getMessage(null))
                    .doesNotContain("Replace \"id\"", "generateNavigators = false"));
    if (record.equals("C10")) {
      assertThat(written)
          .hadWarningContaining("Add widenFields = \"sites\" to @GenerateFocus on C10.")
          .inFile(subject)
          .onLine(7)
          .atColumn(annotation.indexOf("\"sites\"") + 1);
    }
    if (record.equals("C11")) {
      assertThat(written).hadNoteCount(1);
      assertThat(written)
          .hadNoteContaining(
              "Write Box<Object>, or another concrete instantiation, in place of Box<?>");
    }

    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                ADDRESS,
                GENERIC,
                source(record, correction, "String id, " + correctedComponent),
                JavaFileObjects.forSourceString(
                    "com.example.UseFixed",
                    """
                    package com.example;
                    class UseFixed {
                      Object path() { return %sFocus.%s; }
                    }
                    """
                        .formatted(record, navigation)));
    clean(followed);
    assertGeneratedCodeContains(followed, "com.example." + record + "Focus", method);
  }

  @Test
  @DisplayName("another included source record can keep navigation through its annotation fix")
  void removesNameBesideAnnotationSuggestion() {
    String annotation = "@GenerateFocus(includeFields = {\"id\", \"plain\"})";
    JavaFileObject subject = source("Subject", annotation, "String id, Plain plain");
    Compilation written = compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(2);
    assertThat(written)
        .hadWarningContaining("Remove \"id\" from includeFields on Subject.")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    assertThat(written)
        .hadWarningContaining("Alternatively, annotate record Plain with @GenerateFocus.")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf("\"plain\"") + 1);
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                source("Plain", "@GenerateFocus", "String street"),
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"plain\")",
                    "String id, Plain plain"));
    clean(followed);
    assertGeneratedCodeContains(
        followed, "com.example.SubjectFocus", "PlainNavigator<Subject> plain()");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      int other | 2
      Map<String, Clash> other | 2
      """)
  @DisplayName("an included field without a usable correction does not keep navigation enabled")
  void noOtherNavigatorCorrection(String component, int warnings) {
    String annotation = "@GenerateFocus(includeFields = {\"id\", \"other\"})";
    JavaFileObject subject = source("Subject", annotation, "String id, " + component);
    Compilation written = compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(warnings);
    assertThat(written)
        .hadWarningContaining(
            "Remove includeFields and set generateNavigators = false in @GenerateFocus on Subject.")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    clean(
        compile(
            source(
                "Subject", "@GenerateFocus(generateNavigators = false)", "String id, " + component),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror"));
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
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    assertThat(written)
        .hadWarningContaining(
            "Replace \"plain\" in includeFields on Subject with a navigator field from [address].");
    org.assertj.core.api.Assertions.assertThat(written.warnings().getFirst().getMessage(null))
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

  @Test
  @DisplayName("annotating an unannotated record is a followable alternative")
  void annotatingTheRecord() {
    JavaFileObject subject =
        source("Subject", "@GenerateFocus(includeFields = \"plain\")", "Plain plain");
    Compilation written = compile(subject);
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadWarningContaining("Plain is a record without @GenerateFocus.");
    assertThat(written)
        .hadWarningContaining("Alternatively, annotate record Plain with @GenerateFocus.");
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(source("Plain", "@GenerateFocus", "String street"), subject);
    clean(followed);
    assertGeneratedCodeContains(
        followed, "com.example.SubjectFocus", "PlainNavigator<Subject> plain()");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      Map<String, Address> sites | @GenerateFocus(includeFields = "sites") | Add widenFields = "sites" | @GenerateFocus(includeFields = "sites", widenFields = "sites")
      Address[] sites | @GenerateFocus(generateNavigators = true, includeFields = "sites") | Add widenFields = "sites" | @GenerateFocus(generateNavigators = true, includeFields = "sites", widenFields = "sites")
      @Nullable Map<String, Address> sites | @GenerateFocus(includeFields = "sites", widenFields = "other") | Add "sites" to widenFields | @GenerateFocus(includeFields = "sites", widenFields = {"other", "sites"})
      """)
  @DisplayName(
      "an unwidened container warns once, and the offered widening generates its navigator")
  void wideningIsFollowable(String component, String annotation, String fix, String followed) {
    String components = component + ", Map<String, Address> other";
    JavaFileObject subject = source("Subject", annotation, components);
    Compilation written = compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written)
        .hadWarningContaining("Subject does not widen \"sites\". " + fix)
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf("\"sites\"") + 1);
    org.assertj.core.api.Assertions.assertThat(written.notes())
        .noneMatch(note -> note.getMessage(null).contains("has no navigator over"));
    Compilation fixed =
        compile(
            source("Subject", followed, components),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror");
    clean(fixed);
    assertGeneratedCodeContains(
        fixed, "com.example.SubjectFocus", "SitesNavigator<Subject> sites()");
  }

  @Test
  @DisplayName("an unwidened wildcard target warns before its existing wildcard note can apply")
  void unwidenedWildcardTarget() {
    String annotation = "@GenerateFocus(includeFields = \"boxes\")";
    JavaFileObject shelf =
        source("Shelf", annotation, "Map<String, Box<?>> boxes, Address address");
    Compilation written = compile(shelf);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadNoteCount(0);
    assertThat(written)
        .hadWarningContaining(
            "Add widenFields = \"boxes\" to @GenerateFocus on Shelf to step into its Box elements.")
        .inFile(shelf)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    Compilation widened =
        compile(
            source(
                "Shelf",
                "@GenerateFocus(includeFields = \"boxes\", widenFields = \"boxes\")",
                "Map<String, Box<?>> boxes, Address address"),
            "-Werror");
    assertThat(widened).succeededWithoutWarnings();
    assertThat(widened).hadNoteCount(1);
    assertThat(widened).hadNoteContaining("Write Box<Object>");
    clean(
        compile(
            source(
                "Shelf",
                "@GenerateFocus(includeFields = \"boxes\", widenFields = \"boxes\")",
                "Map<String, Box<Object>> boxes, Address address"),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"sites", "id,sites", "sites,id"})
  @DisplayName("a refused wildcard container offers its concrete type and widening together")
  void wildcardContainerCorrection(String included) {
    String names = included.replace(",", "\", \"");
    String annotation = "@GenerateFocus(includeFields = {\"" + names + "\"})";
    String components = "String id, Map<String, ? extends Address> sites, Address address";
    JavaFileObject branches = source("Branches", annotation, components);
    Compilation written = compile(branches);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(included.contains(",") ? 2 : 1);
    assertThat(written).hadNoteCount(0);
    assertThat(written)
        .hadWarningContaining(
            "holds Address records, but its wildcard type arguments prevent widening")
        .inFile(branches)
        .onLine(7)
        .atColumn(annotation.indexOf("\"sites\"") + 1);
    assertThat(written)
        .hadWarningContaining(
            "Write Map<String, Address> in place of Map<String, ? extends Address>");
    assertThat(written)
        .hadWarningContaining("add widenFields = \"sites\" to @GenerateFocus on Branches");
    if (included.contains(",")) {
      assertThat(written).hadWarningContaining("Remove \"id\" from includeFields on Branches.");
    }
    Compilation followed =
        compile(
            source(
                "Branches",
                "@GenerateFocus(includeFields = \"sites\", widenFields = \"sites\")",
                "String id, Map<String, Address> sites, Address address"),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror");
    clean(followed);
    assertGeneratedCodeContains(
        followed, "com.example.BranchesFocus", "SitesNavigator<Branches> sites()");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      id,tags | id | tags
      tags,id | tags | id
      """)
  @DisplayName("only the first invalid entry in list order receives the navigator replacement")
  void oneReplacementPerList(String included, String first, String remaining) {
    String annotation =
        "@GenerateFocus(includeFields = {\"" + included.replace(",", "\", \"") + "\"})";
    String fields = "String id, List<String> tags, Address address";
    JavaFileObject ticket = source("Ticket", annotation, fields);
    Compilation written = compile(ticket);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(2);
    assertThat(written).hadNoteCount(0);
    assertThat(written)
        .hadWarningContaining(
            "Replace \""
                + first
                + "\" in includeFields on Ticket with a navigator field from [address].")
        .inFile(ticket)
        .onLine(7)
        .atColumn(annotation.indexOf('"' + first + '"') + 1);
    assertThat(written)
        .hadWarningContaining("Remove \"" + remaining + "\" from includeFields on Ticket.")
        .inFile(ticket)
        .onLine(7)
        .atColumn(annotation.indexOf('"' + remaining + '"') + 1);
    clean(
        compile(
            source("Ticket", "@GenerateFocus(includeFields = \"address\")", fields),
            "-Xlint:all,-processing,-auxiliaryclass",
            "-Werror"));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      List<Address> | java.util.List.of(new Address("High Street")) | getAll | .via()
      Optional<Address> | Optional.of(new Address("High Street")) | getOptional | .via()
      org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.list.ListKind.Witness, Address> | org.higherkindedj.hkt.list.ListKindHelper.LIST.widen(java.util.List.of(new Address("High Street"))) | getAll | .via()
      List<@Nullable Address> | java.util.Arrays.asList(null, new Address("High Street")) | getAll | .some(Affines.nullable()).via()
      Optional<@Nullable Address> | Optional.of(new Address("High Street")) | getOptional | .some(Affines.nullable()).via()
      org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.list.ListKind.Witness, @Nullable Address> | org.higherkindedj.hkt.list.ListKindHelper.LIST.widen(java.util.Arrays.asList(null, new Address("High Street"))) | getAll | .some(Affines.nullable()).via()
      """)
  @DisplayName("the offered composition compiles and runs, passing over nullable elements")
  void compositionHintRuns(String container, String value, String getter, String hint)
      throws ReflectiveOperationException {
    String annotation = "@GenerateFocus(includeFields = \"members\")";
    JavaFileObject subject = source("Subject", annotation, container + " members, Address address");
    Compilation written = compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadNoteCount(0);
    assertThat(written)
        .hadWarningContaining(
            "which composes with com.example.AddressFocus's paths through " + hint)
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    String composed =
        "SubjectFocus.members()" + hint.replace("via()", "via(AddressFocus.street())");
    String read = composed + "." + getter + "(subject)";
    if (getter.equals("getOptional")) {
      read += ".stream().toList()";
    }
    JavaFileObject use =
        JavaFileObjects.forSourceString(
            "com.example.Use",
            """
        package com.example;
        import java.util.Optional;
        import org.higherkindedj.optics.util.Affines;
        public class Use {
          public static Object run() {
            var subject = new Subject(%s, new Address("Other Street"));
            return %s;
          }
        }
        """
                .formatted(value, read));
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                ADDRESS,
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"address\")",
                    container + " members, Address address"),
                use);
    clean(followed);
    org.assertj.core.api.Assertions.assertThat(
            new RuntimeCompilationHelper.CompiledResult(followed)
                .invokeStatic("com.example.Use", "run"))
        .isEqualTo(List.of("High Street"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "List<Box<?>>",
        "List<Box>",
        "List",
        "List<? extends Address>",
        "Optional<Box<?>>",
        "org.higherkindedj.hkt.Kind<org.higherkindedj.hkt.list.ListKind.Witness, Box<?>>"
      })
  @DisplayName("raw or wildcard elements receive no companion composition hint")
  void noInvalidCompositionHint(String container) {
    Compilation written =
        compile(
            source(
                "Subject", "@GenerateFocus(includeFields = \"members\")", container + " members"));
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    org.assertj.core.api.Assertions.assertThat(written.warnings().getFirst().getMessage(null))
        .doesNotContain("which composes", ".via()");
  }

  @Test
  @DisplayName("a dependency that published no Focus companion receives no composition hint")
  void noUnpublishedCompositionHint(@TempDir Path temp) throws IOException {
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
    assertThat(written).hadWarningCount(1);
    org.assertj.core.api.Assertions.assertThat(written.warnings().getFirst().getMessage(null))
        .doesNotContain("which composes", ".via()");
  }

  @Test
  @DisplayName("a concrete generic element composes with its inferred companion instantiation")
  void genericCompositionRuns() throws ReflectiveOperationException {
    String annotation = "@GenerateFocus(includeFields = \"members\")";
    JavaFileObject subject = source("Subject", annotation, "List<Box<String>> members");
    Compilation written = compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written)
        .hadWarningContaining("which composes with com.example.BoxFocus's paths through .via()")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    JavaFileObject use =
        JavaFileObjects.forSourceString(
            "com.example.Use",
            """
        package com.example;
        public class Use {
          public static Object run() {
            var subject = new Subject(java.util.List.of(new Box<String>("High Street")));
            return SubjectFocus.members().via(BoxFocus.value()).getAll(subject);
          }
        }
        """);
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                GENERIC,
                source(
                    "Subject",
                    "@GenerateFocus(generateNavigators = false)",
                    "List<Box<String>> members"),
                use);
    clean(followed);
    org.assertj.core.api.Assertions.assertThat(
            new RuntimeCompilationHelper.CompiledResult(followed)
                .invokeStatic("com.example.Use", "run"))
        .isEqualTo(List.of("High Street"));
  }

  @Test
  @DisplayName("a published companion hint honours its redirected package")
  void redirectedPublishedCompanion(@TempDir Path temp) throws IOException {
    Compilation upstream =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(
                source(
                    "Address", "@GenerateFocus(targetPackage = \"com.paths\")", "String street"));
    clean(upstream);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    String annotation = "@GenerateFocus(includeFields = \"members\")";
    JavaFileObject subject = source("Subject", annotation, "List<Address> members");
    Compilation written =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .compile(subject);
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written)
        .hadWarningContaining("which composes with com.paths.AddressFocus's paths through .via()")
        .inFile(subject)
        .onLine(7)
        .atColumn(annotation.indexOf('"') + 1);
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(
                source(
                    "Subject",
                    "@GenerateFocus(generateNavigators = false)",
                    "List<Address> members"),
                JavaFileObjects.forSourceString(
                    "com.example.Use",
                    """
                package com.example;
                class Use {
                  Object path() { return SubjectFocus.members().via(com.paths.AddressFocus.street()); }
                }
                """));
    clean(followed);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Map<String, ?>",
        "Map",
        "Map<String, ? extends String>",
        "Map<?, Box<?>>",
        "Map<?, Clash>"
      })
  @DisplayName(
      "a refused container without a usable record correction retains a followable plain path")
  void refusedContainerWithoutCorrection(String container) {
    Compilation written =
        compile(
            source("Subject", "@GenerateFocus(includeFields = \"items\")", container + " items"));
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    org.assertj.core.api.Assertions.assertThat(written.warnings().getFirst().getMessage(null))
        .contains("Remove includeFields and set generateNavigators = false")
        .doesNotContain("Write Map", "add widenFields", "Add widenFields", "which composes");
    clean(
        compile(
            source("Subject", "@GenerateFocus(generateNavigators = false)", container + " items"),
            "-Werror"));
  }

  @Test
  @DisplayName("an SPI container with a dependent bound receives no invented concrete alternative")
  void refusedContainerWithDependentBound() {
    JavaFileObject container =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Box",
            """
        package com.example.hkjtest;
        public record Box<K extends Comparable<K>, V>(V value) {}
        """);
    String fields = "com.example.hkjtest.Box<?, Address> items";
    Compilation written =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(
                ADDRESS,
                container,
                source("Subject", "@GenerateFocus(includeFields = \"items\")", fields));
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    org.assertj.core.api.Assertions.assertThat(written.warnings().getFirst().getMessage(null))
        .contains("Remove includeFields and set generateNavigators = false")
        .doesNotContain("Write Box", "widenFields");
    clean(
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Werror")
            .compile(
                ADDRESS,
                container,
                source("Subject", "@GenerateFocus(generateNavigators = false)", fields)));
  }

  @Test
  @DisplayName("removing an unwidened clashing field leaves the first replacement for another name")
  void removalDoesNotConsumeReplacement() {
    String fields = "String id, List<String> tags, Map<String, Clash> sites, Address address";
    Compilation written =
        compile(
            source(
                "Subject",
                "@GenerateFocus(includeFields = {\"sites\", \"id\", \"tags\"})",
                fields));
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(3);
    assertThat(written)
        .hadWarningContaining(
            "Remove \"sites\" from includeFields on Subject to keep its plain path.");
    assertThat(written)
        .hadWarningContaining(
            "Replace \"id\" in includeFields on Subject with a navigator field from [address].");
    assertThat(written).hadWarningContaining("Remove \"tags\" from includeFields on Subject.");
    clean(
        compile(
            source("Subject", "@GenerateFocus(includeFields = \"address\")", fields), "-Werror"));
  }

  @Test
  @DisplayName("the new warning stops a -Werror build")
  void warningStopsWerror() {
    Compilation written =
        compile(
            source("Subject", "@GenerateFocus(includeFields = \"id\")", "String id"), "-Werror");
    assertThat(written).failed();
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadErrorContaining("warnings found and -Werror specified");
  }

  @Test
  @DisplayName("candidate fields must widen to a navigator without inherited name clashes")
  void onlyOffersEligibleFields() {
    String components = "String id, Address address, Map<String, Address> sites, Clash clash";
    Compilation written =
        compile(
            source(
                "Subject",
                "@GenerateFocus(generateNavigators = true, includeFields = \"id\")",
                components));
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadWarningContaining("with a navigator field from [address].");
    clean(
        compile(
            source(
                "Subject",
                "@GenerateFocus(generateNavigators = true, includeFields = \"address\")",
                components),
            "-Werror"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      @GenerateFocus(includeFields = "address") | Address address
      @GenerateFocus(includeFields = "sites", widenCollections = true) | Map<String, Address> sites
      @GenerateFocus(excludeFields = "id") | String id
      @GenerateFocus(includeFields = {}) | String id
      """)
  @DisplayName("valid navigator requests and excludeFields keep their existing behaviour")
  void quiet(String annotation, String components) {
    clean(compile(source("Subject", annotation, components), "-Werror"));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      @GenerateFocus(includeFields = "box") | Box<?> box | is not generated
      @GenerateFocus(includeFields = "box") | Box box | is not generated
      @GenerateFocus(includeFields = "clash") | Clash clash | has no navigator into
      """)
  @DisplayName("existing target notes gain no extra includeFields warning")
  void existingNotes(String annotation, String components, String note) {
    Compilation written = compile(source("Subject", annotation, components));
    assertThat(written).succeededWithoutWarnings();
    assertThat(written).hadNoteContaining(note);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      @GenerateFocus(includeFields = "sites", widenFields = "sites") | Map<String, ? extends Address> sites | has a wildcard type argument
      @GenerateFocus(includeFields = "sites") | java.util.Set<? extends Address> sites | has a wildcard type argument
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

  @Test
  @DisplayName("a list with navigators off draws only the existing unread-list warning")
  void navigatorsOff() {
    Compilation written =
        compile(
            source(
                "Subject",
                "@GenerateFocus(generateNavigators = false, includeFields = \"id\")",
                "String id"));
    assertThat(written).hadWarningCount(1);
    assertThat(written).hadWarningContaining("generateNavigators = false turns navigators off");
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

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
      Plain<T> | T value | Plain<?> plain
      Plain | String last | Plain plain
      """)
  @DisplayName("annotation is not offered alone when a target still could not navigate")
  void annotationWouldNotSuffice(String record, String fields, String component) {
    Compilation written =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(
                source(record, "", fields),
                source("Subject", "@GenerateFocus(includeFields = \"plain\")", component));
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    org.assertj.core.api.Assertions.assertThat(written.warnings())
        .allSatisfy(
            warning ->
                org.assertj.core.api.Assertions.assertThat(warning.getMessage(null))
                    .doesNotContain("Alternatively"));
    clean(
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Werror")
            .compile(
                source(record, "", fields),
                source("Subject", "@GenerateFocus(generateNavigators = false)", component)));
  }

  @Test
  @DisplayName("widening is not offered alone where the navigator would clash")
  void wideningWouldExposeAClash() {
    Compilation written =
        compile(
            source(
                "Subject",
                "@GenerateFocus(generateNavigators = true, includeFields = \"sites\")",
                "Map<String, Clash> sites"));
    assertThat(written).succeeded();
    assertThat(written).hadWarningCount(1);
    assertThat(written)
        .hadWarningContaining("Its widened navigator would also clash with target fields [last].");
    assertThat(written)
        .hadWarningContaining(
            "Remove includeFields and replace generateNavigators = true with generateNavigators = false on Subject");
    clean(
        compile(
            source(
                "Subject",
                "@GenerateFocus(generateNavigators = false)",
                "Map<String, Clash> sites"),
            "-Werror"));
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
    org.assertj.core.api.Assertions.assertThat(downstream.warnings())
        .allSatisfy(
            warning ->
                org.assertj.core.api.Assertions.assertThat(warning.getMessage(null))
                    .doesNotContain("Alternatively"));
  }
}
