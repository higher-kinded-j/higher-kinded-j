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
      @GenerateFocus(includeFields = "box") | Map<String, Box<?>> box | NONE
      @GenerateFocus(includeFields = "clash") | Clash clash | has no navigator into
      """)
  @DisplayName("existing target notes gain no extra includeFields warning")
  void existingNotes(String annotation, String components, String note) {
    Compilation written = compile(source("Subject", annotation, components));
    assertThat(written).succeededWithoutWarnings();
    if (!note.equals("NONE")) {
      assertThat(written).hadNoteContaining(note);
    }
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

  @Test
  @DisplayName("annotation is not offered when the navigator would name a hidden type")
  void unannotatedHiddenTarget(@TempDir Path temp) throws IOException {
    JavaFileObject upstreamSource =
        JavaFileObjects.forSourceString(
            "com.model.Plain",
            """
        package com.model;
        record Secret(String value) {}
        public record Plain(Secret secret) {}
        """);
    Compilation upstream = javac().withOptions("-proc:none").compile(upstreamSource);
    Path dependency = classDirectory(upstream, temp.resolve("dependency"));
    Compilation downstream =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(dependency))
            .compile(
                source(
                    "Subject",
                    "@GenerateFocus(includeFields = \"plain\")",
                    "com.model.Plain plain"));
    assertThat(downstream).succeeded();
    assertThat(downstream).hadWarningCount(1);
    org.assertj.core.api.Assertions.assertThat(downstream.warnings())
        .allSatisfy(
            warning ->
                org.assertj.core.api.Assertions.assertThat(warning.getMessage(null))
                    .doesNotContain("Alternatively"));
  }
}
