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
 * element gets one only where the record widens the container, a field named like a path method is
 * refused, and of two fields whose navigator classes would share a name the first keeps its
 * navigator. Each fix line the processor offers is followed here and compiles.
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
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
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
  @DisplayName("Two fields whose navigator classes would share a name")
  class SharedClassName {

    @ParameterizedTest(name = "{0} beside {1}")
    @CsvSource(
        delimiterString = " | ",
        textBlock =
            """
            List<Item> items | Item Items | ItemsNavigator<Dup> items() | FocusPath<Dup, Item> Items()
            List<Item> items | Optional<Item> Items | ItemsNavigator<Dup> items() | AffinePath<Dup, Item> Items()
            Item item | Item Item | ItemNavigator<Dup> item() | FocusPath<Dup, Item> Item()
            Item Items | List<Item> items | ItemsNavigator<Dup> Items() | TraversalPath<Dup, Item> items()
            """)
    @DisplayName(
        "leaves the later field a plain path with a note, where navigators are the default")
    void isDeclinedWithANoteOnTheDefault(
        String first, String later, String navigator, String plainPath) {
      String earlierName = first.substring(first.indexOf(' ') + 1);
      String laterName = later.substring(later.indexOf(' ') + 1);
      JavaFileObject dup = dup("@GenerateFocus", first, later);
      Compilation noted = javac().withProcessors(new FocusProcessor()).compile(ITEM, dup);

      assertThat(noted).succeededWithoutWarnings();
      assertThat(noted)
          .hadNoteContaining(
              "record component 'Dup."
                  + laterName
                  + "' has no navigator, because 'Dup."
                  + earlierName
                  + "' has its class name. 'Dup."
                  + earlierName
                  + "' already has the navigator class "
                  + navigator.substring(0, navigator.indexOf('<'))
                  + ", and a navigator for '"
                  + laterName
                  + "' would take the same name, since a navigator class takes its field's name"
                  + " with the first letter in upper case. DupFocus."
                  + laterName
                  + "() keeps the path it has without one. Rename 'Dup."
                  + laterName
                  + "' or 'Dup."
                  + earlierName
                  + "' to give both a navigator, or add excludeFields = \""
                  + laterName
                  + "\" to @GenerateFocus on Dup to keep the path without this note.")
          .inFile(dup)
          .onLineContaining(later);
      Assertions.assertThat(noted.notes())
          .filteredOn(note -> note.getMessage(null).contains("has its class name"))
          .hasSize(1);
      assertGeneratedCodeContains(noted, "com.example.DupFocus", "public static " + navigator);
      assertGeneratedCodeContains(noted, "com.example.DupFocus", "public static " + plainPath);

      Compilation followed =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  ITEM, dup("@GenerateFocus(excludeFields = \"" + laterName + "\")", first, later));
      assertThat(followed).succeededWithoutWarnings();
      Assertions.assertThat(followed.notes())
          .noneMatch(note -> note.getMessage(null).contains("already has the navigator class"));
      assertGeneratedCodeContains(followed, "com.example.DupFocus", "public static " + navigator);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " | ",
        textBlock =
            """
            @GenerateFocus(generateNavigators = true) | add excludeFields = "Items" to @GenerateFocus on Dup | @GenerateFocus(generateNavigators = true, excludeFields = "Items")
            @GenerateFocus(generateNavigators = true, includeFields = {"items", "Items"}) | remove "Items" from includeFields on Dup | @GenerateFocus(generateNavigators = true, includeFields = "items")
            """)
    @DisplayName("is refused, where the record asks for navigators, with a fix line that compiles")
    void isRefusedWithAFollowableFixLine(String annotation, String fix, String followed) {
      JavaFileObject dup = dup(annotation, "List<Item> items", "Item Items");
      Compilation refused = javac().withProcessors(new FocusProcessor()).compile(ITEM, dup);

      assertThat(refused).failed();
      assertThat(refused).hadErrorCount(1);
      assertThat(refused)
          .hadErrorContaining(
              "the navigator for 'Dup.Items' cannot share its class name with 'Dup.items'."
                  + " 'Dup.items' already has the navigator class ItemsNavigator, and a navigator"
                  + " for 'Items' would take the same name, since a navigator class takes its"
                  + " field's name with the first letter in upper case. Rename 'Dup.Items' or"
                  + " 'Dup.items', or "
                  + fix
                  + " to keep 'Items' a plain path.")
          .inFile(dup)
          .onLineContaining("Item Items");

      Compilation fixed =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(ITEM, dup(followed, "List<Item> items", "Item Items"));
      assertThat(fixed).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          fixed, "com.example.DupFocus", "public static ItemsNavigator<Dup> items()");
      assertGeneratedCodeContains(
          fixed, "com.example.DupFocus", "public static FocusPath<Dup, Item> Items()");
    }

    @Test
    @DisplayName("composes the earlier navigator and the later plain path from another record")
    void composesFromAnotherRecord() {
      JavaFileObject holder =
          record(
              "Holder",
              """
              @GenerateFocus
              public record Holder(Dup dup) {}
              """);
      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(ITEM, dup("@GenerateFocus", "List<Item> items", "Item Items"), holder);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", "DupFocus.ItemsNavigator<S> items()");
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", "public FocusPath<S, Item> Items()");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " | ",
        textBlock =
            """
            String items | not navigable
            Name items | declined for a field named like a path method
            """)
    @DisplayName("gives the later field the navigator where the earlier one has none")
    void anEarlierFieldWithoutANavigatorLeavesTheName(String first, String reason) {
      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(NAME, ITEM, dup("@GenerateFocus", first, "Item Items"));

      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(compilation.notes())
          .as(reason)
          .noneMatch(note -> note.getMessage(null).contains("already has the navigator class"));
      assertGeneratedCodeContains(
          compilation, "com.example.DupFocus", "public static ItemsNavigator<Dup> Items()");
    }

    private static JavaFileObject dup(String annotation, String first, String later) {
      return record(
          "Dup", annotation + "\npublic record Dup(\n    " + first + ",\n    " + later + ") {}\n");
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

    @Test
    @DisplayName("a map left whole inside a list draws the note, and widening it navigates")
    void aMapLeftWholeInsideAListDrawsTheNote() {
      JavaFileObject shelf = shelf("@GenerateFocus(generateNavigators = true)");
      Compilation noted = javac().withProcessors(new FocusProcessor()).compile(ITEM, shelf);

      assertThat(noted).succeededWithoutWarnings();
      assertThat(noted)
          .hadNoteContaining(
              "record component 'Shelf.bins' has no navigator over its Item elements. A navigator"
                  + " steps into a container only where the record widens it, and Shelf does not"
                  + " widen 'bins'.")
          .inFile(shelf)
          .onLineContaining("bins");

      Compilation widened =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  ITEM, shelf("@GenerateFocus(generateNavigators = true, widenFields = \"bins\")"));
      assertThat(widened).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          widened, "com.example.ShelfFocus", "public static BinsNavigator<Shelf> bins()");
    }

    @Test
    @DisplayName(
        "an element annotation the companion's package cannot name is left off the navigator")
    void anUnwritableElementAnnotationIsLeftOff() {
      JavaFileObject tag =
          JavaFileObjects.forSourceString(
              "a.Tag",
              """
              package a;
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              @Target(ElementType.TYPE_USE)
              @interface Tag {}
              """);
      JavaFileObject leaf =
          JavaFileObjects.forSourceString(
              "a.Leaf",
              """
              package a;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              public record Leaf(String name) {}
              """);
      JavaFileObject outer =
          JavaFileObjects.forSourceString(
              "a.Outer",
              """
              package a;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;
              @GenerateFocus(targetPackage = "b")
              public record Outer(List<@Nullable @Tag Leaf> list) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(tag, leaf, outer);

      // The companion is written into b, which cannot name a's package-private @Tag, so the
      // navigator keeps only the @Nullable its method writes too.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "b.OuterFocus",
          "ListNavigator<S> extends NavigatorTraversalPath<S, @Nullable Leaf>");
    }

    @ParameterizedTest(name = "{0} over ({1})")
    @CsvSource(
        delimiterString = " | ",
        quoteCharacter = '"',
        textBlock =
            """
            Map<String, Name> | String first, String last | String first, String surname | 'Name.last' | last()
            List<Map<String, Name>> | String first, String last | String first, String surname | 'Name.last' | last()
            Map<String, Name> | String head, String last | String top, String surname | 'Name.head' and 'Name.last' | head() and last()
            Map<String, Name> | String first, String headOption | String first, String firstOption | 'Name.headOption' | headOption()
            """)
    @DisplayName(
        "left whole, over elements with a field named like a path method, draws a note to rename"
            + " the field first")
    void aClashingElementDrawsANoteToRenameIt(
        String container, String fields, String renamed, String named, String methods) {
      Compilation noted =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(name(fields), roster(NAVIGATORS, container));

      // Widening groups alone would reach Name, whose clashing fields its navigator, a
      // TraversalPath, cannot navigate to, so the note asks for them to be renamed first. The
      // headOption row tells the widened path from the whole container's, which has no such method.
      assertThat(noted).succeededWithoutWarnings();
      assertThat(noted)
          .hadNoteContaining(
              "record component 'Roster.groups' has no navigator over its Name elements. A"
                  + " navigator steps into a container only where the record widens it, and Roster"
                  + " does not widen 'groups'. Widened, its navigator would be a TraversalPath,"
                  + " which has its own "
                  + methods
                  + ", so it could not navigate to "
                  + named
                  + ". Rename "
                  + named
                  + ", then add widenFields = \"groups\" to @GenerateFocus on Roster to navigate"
                  + " into its elements, or add excludeFields = \"groups\" to @GenerateFocus on"
                  + " Roster to go without this note.")
          .inFile(roster(NAVIGATORS, container))
          .onLineContaining("groups");

      Compilation followed =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  name(renamed),
                  roster(
                      "@GenerateFocus(generateNavigators = true, widenFields = \"groups\")",
                      container));
      assertThat(followed).succeededWithoutWarnings();
      Assertions.assertThat(followed.notes())
          .noneMatch(note -> note.getMessage(null).contains("has no navigator over"));
      assertGeneratedCodeContains(
          followed, "com.example.RosterFocus", "public static GroupsNavigator<Roster> groups()");
    }

    @Test
    @DisplayName(
        "left whole, over elements with a clashing field, can be kept whole without a note")
    void aClashingElementCanBeKeptWhole() {
      Compilation kept =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  name("String first, String last"),
                  roster(
                      "@GenerateFocus(generateNavigators = true, excludeFields = \"groups\")",
                      "Map<String, Name>"));

      assertThat(kept).succeededWithoutWarnings();
      Assertions.assertThat(kept.notes())
          .noneMatch(note -> note.getMessage(null).contains("has no navigator over"));
      assertGeneratedCodeContains(
          kept,
          "com.example.RosterFocus",
          "public static FocusPath<Roster, Map<String, Name>> groups()");
    }

    private static final String NAVIGATORS = "@GenerateFocus(generateNavigators = true)";

    private static JavaFileObject name(String fields) {
      return record(
          "Name",
          """
          @GenerateFocus
          public record Name(%s) {}
          """
              .formatted(fields));
    }

    /** A roster of groups, held in the given container, under the given annotation. */
    private static JavaFileObject roster(String annotation, String container) {
      return record(
          "Roster",
          annotation
              + """

              public record Roster(%s groups) {}
              """
                  .formatted(container));
    }

    private static JavaFileObject holder(String annotation) {
      return record(
          "Holder",
          annotation
              + """

              public record Holder(Map<String, Item> many, Map<String, Item> kept) {}
              """);
    }

    /** A shelf of bins, each a map of items, under the given annotation. */
    private static JavaFileObject shelf(String annotation) {
      return record(
          "Shelf",
          annotation
              + """

              public record Shelf(List<Map<String, Item>> bins) {}
              """);
    }
  }
}
