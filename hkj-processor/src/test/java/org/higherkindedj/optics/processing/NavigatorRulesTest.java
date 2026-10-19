// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A generated navigator extends the hkj-core base for its tier, so it is a path. A container's
 * element gets one only where the record widens the container, and a field named like a path method
 * is refused. Each fix line the processor offers is followed here and compiles.
 */
@DisplayName("Navigator rules and their fix lines")
class NavigatorRulesTest {

  /** A navigable record with a field named like a no-argument path method. */
  private static final JavaFileObject NAME =
      JavaFileObjects.forSourceString(
          "com.example.Name",
          """
          package com.example;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          @GenerateFocus
          public record Name(String first, String last) {}
          """);

  /** A navigable record whose field names clash with nothing. */
  private static final JavaFileObject ITEM =
      JavaFileObjects.forSourceString(
          "com.example.Item",
          """
          package com.example;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          @GenerateFocus
          public record Item(String sku, String title) {}
          """);

  private static JavaFileObject record(String name, String source) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;
        import java.util.Map;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;
        """
            + source);
  }

  @Nested
  @DisplayName("A field named like a path method")
  class InheritedName {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " | ",
        textBlock =
            """
            @GenerateFocus(generateNavigators = true) | add excludeFields = "name" to @GenerateFocus on Person | @GenerateFocus(generateNavigators = true, excludeFields = "name")
            @GenerateFocus(generateNavigators = true, excludeFields = "other") | add "name" to excludeFields on Person | @GenerateFocus(generateNavigators = true, excludeFields = {"other", "name"})
            @GenerateFocus(generateNavigators = true, includeFields = {"name", "other"}) | remove "name" from includeFields on Person | @GenerateFocus(generateNavigators = true, includeFields = "other")
            @GenerateFocus(generateNavigators = true, includeFields = "name") | remove includeFields and replace generateNavigators = true with generateNavigators = false on Person | @GenerateFocus(generateNavigators = false)
            """)
    @DisplayName("is refused, where the record asks for navigators, with a fix line that compiles")
    void isRefusedWithAFollowableFixLine(String annotation, String fix, String followed) {
      Compilation refused =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NAME, person(annotation), NAME_OTHER);

      assertThat(refused).failed();
      assertThat(refused).hadErrorCount(1);
      assertThat(refused)
          .hadErrorContaining("Rename 'Name.last', or " + fix + " to keep 'name' a plain path.")
          .inFile(person(annotation))
          .onLineContaining("Name name");

      Compilation fixed =
          javac().withProcessors(new FocusProcessor()).compile(NAME, person(followed), NAME_OTHER);
      assertThat(fixed).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          fixed, "com.example.PersonFocus", "public static FocusPath<Person, Name> name()");
    }

    @Test
    @DisplayName("leaves the field a plain path with a note, where navigators are the default")
    void isDeclinedWithANoteOnTheDefault() {
      JavaFileObject person = person("@GenerateFocus");
      Compilation noted =
          javac().withProcessors(new FocusProcessor()).compile(NAME, person, NAME_OTHER);

      assertThat(noted).succeededWithoutWarnings();
      assertThat(noted)
          .hadNoteContaining(
              "record component 'Person.name' has no navigator into Name. A navigator is a"
                  + " FocusPath, which has its own last(), so it cannot navigate to 'Name.last',"
                  + " and PersonFocus.name() keeps the path it has without one. Rename 'Name.last'"
                  + " to navigate into Name, or add excludeFields = \"name\" to @GenerateFocus on"
                  + " Person to keep the path without this note.")
          .inFile(person)
          .onLineContaining("Name name");
      assertGeneratedCodeContains(
          noted, "com.example.PersonFocus", "public static FocusPath<Person, Name> name()");
      assertGeneratedCodeContains(
          noted, "com.example.PersonFocus", "public static OtherNavigator<Person> other()");

      Compilation followed =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NAME, person("@GenerateFocus(excludeFields = \"name\")"), NAME_OTHER);
      assertThat(followed).succeededWithoutWarnings();
      Assertions.assertThat(followed.notes())
          .noneMatch(note -> note.getMessage(null).contains("has no navigator into"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " | ",
        textBlock =
            """
            @GenerateFocus(excludeFields = "other") | add "name" to excludeFields on Person | @GenerateFocus(excludeFields = {"other", "name"})
            @GenerateFocus(includeFields = {"name", "other"}) | remove "name" from includeFields on Person | @GenerateFocus(includeFields = "other")
            @GenerateFocus(includeFields = "name") | remove includeFields and add generateNavigators = false to @GenerateFocus on Person | @GenerateFocus(generateNavigators = false)
            """)
    @DisplayName("draws a note on the default whose fix line compiles with nothing more to say")
    void isNotedWithAFollowableFixLine(String annotation, String fix, String followed) {
      Compilation noted =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NAME, person(annotation), NAME_OTHER);

      assertThat(noted).succeededWithoutWarnings();
      assertThat(noted)
          .hadNoteContaining(
              "Rename 'Name.last' to navigate into Name, or "
                  + fix
                  + " to keep the path without this note.")
          .inFile(person(annotation))
          .onLineContaining("Name name");

      Compilation fixed =
          javac().withProcessors(new FocusProcessor()).compile(NAME, person(followed), NAME_OTHER);
      assertThat(fixed).succeededWithoutWarnings();
      Assertions.assertThat(fixed.notes())
          .noneMatch(note -> note.getMessage(null).contains("has no navigator into"));
    }

    @Test
    @DisplayName("lets a self-referencing record named like a list compile on the default")
    void aSelfReferencingRecordCompiles() {
      JavaFileObject cons =
          record(
              "Cons",
              """
              @GenerateFocus
              public record Cons(int head, @Nullable Cons tail) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(cons);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "A navigator is an AffinePath, which has its own head() and tail(), so it cannot"
                  + " navigate to 'Cons.head' and 'Cons.tail'");
      assertGeneratedCodeContains(
          compilation, "com.example.ConsFocus", "public static AffinePath<Cons, Cons> tail()");
    }

    /** A second navigable field, so that a list naming another field is not empty. */
    private static final JavaFileObject NAME_OTHER =
        JavaFileObjects.forSourceString(
            "com.example.Other",
            """
            package com.example;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus
            public record Other(String note) {}
            """);

    private static JavaFileObject person(String annotation) {
      return record(
          "Person",
          annotation
              + """

              public record Person(Name name, Other other) {}
              """);
    }
  }

  @Nested
  @DisplayName("A container of navigable records")
  class Containers {

    @Test
    @DisplayName("widenFields widens one container to a navigator and leaves another whole")
    void widenFieldsWidensOneContainer() {
      JavaFileObject holder =
          record(
              "Holder",
              """
              @GenerateFocus(widenFields = "many")
              public record Holder(Map<String, Item> many, Map<String, Item> kept) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(ITEM, holder);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", "public static ManyNavigator<Holder> many()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.HolderFocus",
          "public static FocusPath<Holder, Map<String, Item>> kept()");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " | ",
        textBlock =
            """
            @GenerateFocus(generateNavigators = true) | Add widenFields = "kept" to @GenerateFocus on Holder | @GenerateFocus(generateNavigators = true, widenFields = "kept")
            @GenerateFocus(generateNavigators = true, widenFields = "many") | Add "kept" to widenFields on Holder | @GenerateFocus(generateNavigators = true, widenFields = {"many", "kept"})
            """)
    @DisplayName("left whole by a record that writes generateNavigators draws a note that compiles")
    void drawsAFollowableNote(String annotation, String fix, String followed) {
      Compilation noted =
          javac().withProcessors(new FocusProcessor()).compile(ITEM, holder(annotation));

      assertThat(noted).succeededWithoutWarnings();
      assertThat(noted)
          .hadNoteContaining(
              "record component 'Holder.kept' has no navigator over its Item elements. A"
                  + " navigator steps into a container only where the record widens it, and Holder"
                  + " does not widen 'kept'. "
                  + fix
                  + " to navigate into its elements.")
          .inFile(holder(annotation))
          .onLineContaining("Map<String, Item> kept");

      Compilation fixed =
          javac().withProcessors(new FocusProcessor()).compile(ITEM, holder(followed));
      assertThat(fixed).succeededWithoutWarnings();
      Assertions.assertThat(fixed.notes())
          .noneMatch(note -> note.getMessage(null).contains("'Holder.kept' has no navigator over"));
      assertGeneratedCodeContains(
          fixed, "com.example.HolderFocus", "public static KeptNavigator<Holder> kept()");
    }

    @Test
    @DisplayName("left whole by a record that leaves navigators to the default draws no note")
    void defaultNavigatorsDrawNoNote() {
      JavaFileObject holder =
          record(
              "Holder",
              """
              @GenerateFocus
              public record Holder(Item one, Map<String, Item> kept) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(ITEM, holder);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", "public static OneNavigator<Holder> one()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.HolderFocus",
          "public static FocusPath<Holder, Map<String, Item>> kept()");
      Assertions.assertThat(compilation.notes())
          .noneMatch(note -> note.getMessage(null).contains("has no navigator over"));
    }

    private static JavaFileObject holder(String annotation) {
      return record(
          "Holder",
          annotation
              + """

              public record Holder(Map<String, Item> many, Map<String, Item> kept) {}
              """);
    }
  }
}
