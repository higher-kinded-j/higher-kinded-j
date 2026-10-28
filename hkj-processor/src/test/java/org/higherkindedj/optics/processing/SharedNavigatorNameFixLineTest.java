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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/**
 * A message that offers a field a navigator asks first for every rename that navigator needs: of a
 * field of the record it reaches named like one of its own methods, and of one of two fields whose
 * navigators would share a class name. The change alone would lead to another note or error: at the
 * field, where the other comes first, or at the other, which would lose its navigator, where it
 * comes later. A later field the same change gives a navigator counts where the record writes
 * {@code generateNavigators = true}, since the clash is an error there. A message whose change
 * leaves the class name free stays as it was.
 *
 * <p>Each fix line is accurate followed alone. Two messages that each offer one of two such fields
 * a navigator say nothing of the other, so following both draws the shared-class-name message,
 * which then asks for the rename; {@code Rack} pins that.
 *
 * <p>Every record shares two compilations: one as written, where each message must say what it says
 * here, and one with each fix line followed, under {@code -Xlint:all} and {@code -Werror}, where
 * nothing more may be reported and each field the fix lines name has a navigator. A followed record
 * renames a field to {@code featured}, {@code outlets} or the like where its message asks for a
 * rename. The shared-class-name error fails its build, and a missing companion needs a dependency,
 * so each of those is compiled on its own.
 */
@DisplayName("Fix lines that ask for a rename before they give a navigator")
class SharedNavigatorNameFixLineTest {

  private static final JavaFileObject ITEM = source("Item", "@GenerateFocus", "String sku");
  private static final JavaFileObject BOX =
      source("Box<T>", "@GenerateFocus", "T value", "String label");
  private static final JavaFileObject OTHER = source("Other", "@GenerateFocus", "String head");
  private static final JavaFileObject STALL = source("Stall", "", "String town");

  // Records with a field named like a path method, and the same records with that field renamed.
  private static final JavaFileObject NAME =
      source("Name", "@GenerateFocus", "String first", "String last");
  private static final JavaFileObject RENAMED_NAME =
      source("Name", "@GenerateFocus", "String first", "String surname");
  private static final JavaFileObject TAB =
      source("Tab<T>", "@GenerateFocus", "T value", "String last");
  private static final JavaFileObject RENAMED_TAB =
      source("Tab<T>", "@GenerateFocus", "T value", "String surname");
  private static final JavaFileObject WIDE =
      source("Wide", "@GenerateFocus", "String first", "String last", "String headOption");
  private static final JavaFileObject RENAMED_WIDE =
      source("Wide", "@GenerateFocus", "String first", "String surname", "String headOption");

  // A record without @GenerateFocus as written, which gains it where a fix line offers that.
  private static final JavaFileObject KIOSK = source("Kiosk", "", "String town");
  private static final JavaFileObject ANNOTATED_KIOSK =
      source("Kiosk", "@GenerateFocus", "String town");

  // Records in another package whose navigators name Secret, which that package hides as written.
  private static final JavaFileObject SECRET = library("Secret", "", "String code");
  private static final JavaFileObject PUBLIC_SECRET = library("Secret", "public", "String code");
  private static final JavaFileObject INNER =
      library("Inner", "@GenerateFocus public", "Secret secret", "String code");
  private static final JavaFileObject VAULT =
      library("Vault", "@GenerateFocus public", "Secret secret", "String last");
  private static final JavaFileObject RENAMED_VAULT =
      library("Vault", "@GenerateFocus public", "Secret secret", "String surname");

  private static JavaFileObject source(String name, String annotation, String... components) {
    return JavaFileObjects.forSourceString(
        "com.example." + name.replaceAll("<.*>", ""),
        """
        package com.example;
        import java.util.List;
        import java.util.Map;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        %s
        public record %s(
            %s) {}
        """
            .formatted(annotation, name, String.join(",\n    ", components)));
  }

  private static JavaFileObject library(String name, String modifiers, String... components) {
    return JavaFileObjects.forSourceString(
        "com.lib." + name,
        """
        package com.lib;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        %s record %s(%s) {}
        """
            .formatted(modifiers, name, String.join(", ", components)));
  }

  /**
   * A record's annotation and components.
   *
   * @param record the record's name, unique among the rows
   * @param annotation the annotation, on one line
   * @param components the record's components, one to a line
   */
  record Shape(String record, String annotation, List<String> components) {

    Shape {
      components = List.copyOf(components);
    }

    JavaFileObject source() {
      return SharedNavigatorNameFixLineTest.source(
          record, annotation, components.toArray(String[]::new));
    }
  }

  /**
   * What a message says, on the line it is reported at.
   *
   * @param at text on that line: a component, or the annotation for an includeFields warning
   * @param says text the message holds, from the end of the sentence before its fix line on, so
   *     that nothing can come between the two
   * @param ends whether the message ends with that text, so that nothing can follow it either
   */
  record Message(String at, String says, boolean ends) {}

  /** A message holding the text. */
  private static Message says(String at, String says) {
    return new Message(at, says, false);
  }

  /** A message ending with the text. */
  private static Message ends(String at, String says) {
    return new Message(at, says, true);
  }

  /**
   * A record as written, what its messages say, and the record with every fix line followed.
   *
   * @param written the record as written
   * @param messages what its messages say
   * @param followed the record with its fix lines followed
   * @param navigators the navigator methods the followed record's companion declares
   */
  record Row(Shape written, List<Message> messages, Shape followed, List<String> navigators) {

    Row {
      messages = List.copyOf(messages);
      navigators = List.copyOf(navigators);
    }

    @Override
    public String toString() {
      return written.annotation()
          + " on "
          + written.record()
          + "("
          + String.join(", ", written.components())
          + ")";
    }
  }

  private static Row row(
      String record,
      String annotation,
      List<String> components,
      List<Message> messages,
      String followedAnnotation,
      List<String> followedComponents,
      String... navigators) {
    return new Row(
        new Shape(record, annotation, components),
        messages,
        new Shape(record, followedAnnotation, followedComponents),
        List.of(navigators));
  }

  /** The sentence a message gives for asking that one of two fields be renamed first. */
  private static String shares(String field, String record, String other) {
    return sharesWith(field, "the navigator for '" + record + "." + other + "'");
  }

  /** The same sentence, where the other field gains its navigator from the same change. */
  private static String gains(String field, String record, String other) {
    return sharesWith(field, "the one '" + record + "." + other + "' would gain too");
  }

  private static String sharesWith(String field, String other) {
    return "A navigator for '"
        + field
        + "' would share its class name, "
        + ProcessorUtils.capitalise(field)
        + "Navigator, with "
        + other
        + ", since a navigator class takes its field's name with the first letter in upper case.";
  }

  private static final String NAVIGATORS = "@GenerateFocus(generateNavigators = true)";

  /** The note on a container a record that writes generateNavigators leaves whole. */
  static final List<Row> UNWIDENED =
      List.of(
          row(
              "Crate",
              NAVIGATORS,
              List.of("Map<String, Item> items", "Item Items"),
              List.of(
                  says(
                      "Map<String, Item> items",
                      "Crate does not widen 'items'. "
                          + shares("items", "Crate", "Items")
                          + " Rename 'Crate.items' or 'Crate.Items', then add widenFields ="
                          + " \"items\", or its new name, to @GenerateFocus on Crate to navigate"
                          + " into its elements, or add excludeFields = \"items\" to @GenerateFocus"
                          + " on Crate to go without this note.")),
              "@GenerateFocus(generateNavigators = true, widenFields = \"items\")",
              List.of("Map<String, Item> items", "Item featured"),
              "ItemsNavigator<Crate> items()",
              "FeaturedNavigator<Crate> featured()"),
          row(
              "Carton",
              NAVIGATORS,
              List.of("Item Items", "Map<String, Item> items"),
              List.of(
                  says(
                      "Map<String, Item> items",
                      "Carton does not widen 'items'. "
                          + shares("items", "Carton", "Items")
                          + " Rename 'Carton.items' or 'Carton.Items', then add widenFields ="
                          + " \"items\", or its new name, to @GenerateFocus on Carton to navigate"
                          + " into its elements, or add excludeFields = \"items\" to @GenerateFocus"
                          + " on Carton to go without this note.")),
              "@GenerateFocus(generateNavigators = true, widenFields = \"items\")",
              List.of("Item featured", "Map<String, Item> items"),
              "FeaturedNavigator<Carton> featured()",
              "ItemsNavigator<Carton> items()"),
          row(
              "Ledger",
              NAVIGATORS,
              List.of("Map<String, Name> names", "Item Names"),
              List.of(
                  says(
                      "Map<String, Name> names",
                      "so it could not navigate to 'Name.last'. "
                          + shares("names", "Ledger", "Names")
                          + " Rename 'Name.last' and either 'Ledger.names' or 'Ledger.Names', then"
                          + " add widenFields = \"names\", or its new name, to @GenerateFocus on"
                          + " Ledger to navigate into its elements, or add excludeFields ="
                          + " \"names\" to @GenerateFocus on Ledger to go without this note.")),
              "@GenerateFocus(generateNavigators = true, widenFields = \"names\")",
              List.of("Map<String, Name> names", "Item featured"),
              "NamesNavigator<Ledger> names()",
              "FeaturedNavigator<Ledger> featured()"),
          row(
              "Rack",
              NAVIGATORS,
              List.of("Map<String, Item> items", "Map<String, Item> Items"),
              List.of(
                  says(
                      "Map<String, Item> items",
                      "Rack does not widen 'items'. Add widenFields = \"items\" to @GenerateFocus"
                          + " on Rack to navigate into its elements."),
                  says(
                      "Map<String, Item> Items",
                      "Rack does not widen 'Items'. Add widenFields = \"Items\" to @GenerateFocus"
                          + " on Rack to navigate into its elements.")),
              "@GenerateFocus(generateNavigators = true, widenFields = \"items\","
                  + " excludeFields = \"Items\")",
              List.of("Map<String, Item> items", "Map<String, Item> Items"),
              "ItemsNavigator<Rack> items()"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("UNWIDENED")
  @DisplayName("the unwidened-container note asks for a rename before it offers widenFields")
  void theUnwidenedContainerNoteRenamesFirst(Row row) {
    followsThrough(row);
  }

  /** The includeFields warning on a name whose field has no navigator. */
  static final List<Row> INCLUDED =
      List.of(
          row(
              "Bin",
              "@GenerateFocus(generateNavigators = true, includeFields = {\"items\", \"Items\"})",
              List.of("Map<String, Item> items", "Item Items"),
              List.of(
                  says(
                      "includeFields",
                      "Bin does not widen \"items\". "
                          + shares("items", "Bin", "Items")
                          + " Rename 'Bin.items' or 'Bin.Items', then add widenFields = \"items\","
                          + " or its new name, to @GenerateFocus on Bin, or remove \"items\" from"
                          + " includeFields on Bin to keep its plain path.")),
              "@GenerateFocus(generateNavigators = true, includeFields = {\"items\", \"featured\"},"
                  + " widenFields = \"items\")",
              List.of("Map<String, Item> items", "Item featured"),
              "ItemsNavigator<Bin> items()",
              "FeaturedNavigator<Bin> featured()"),
          row(
              "Hold",
              "@GenerateFocus(includeFields = {\"Items\", \"items\"})",
              List.of("Item Items", "Map<String, Item> items"),
              List.of(
                  says(
                      "includeFields",
                      "Hold does not widen \"items\". "
                          + shares("items", "Hold", "Items")
                          + " Rename 'Hold.items' or 'Hold.Items', then add widenFields ="
                          + " \"items\", or its new name, to @GenerateFocus on Hold, or remove"
                          + " \"items\" from includeFields on Hold to keep its plain path.")),
              "@GenerateFocus(includeFields = {\"featured\", \"items\"}, widenFields = \"items\")",
              List.of("Item featured", "Map<String, Item> items"),
              "FeaturedNavigator<Hold> featured()",
              "ItemsNavigator<Hold> items()"),
          row(
              "Register",
              "@GenerateFocus(includeFields = {\"Names\", \"names\"})",
              List.of("Name Names", "Map<String, Name> names"),
              List.of(
                  says(
                      "Name Names",
                      "keeps the path it has without one. Rename 'Name.last' to navigate into"
                          + " Name, or remove \"Names\" from includeFields on Register to keep the"
                          + " path without this note."),
                  says(
                      "includeFields",
                      "so it could not navigate to 'Name.last'. "
                          + gains("names", "Register", "Names")
                          + " Rename 'Name.last' and either 'Register.names' or 'Register.Names',"
                          + " then add widenFields = \"names\", or its new name, to @GenerateFocus"
                          + " on Register, or remove \"names\" from includeFields on Register to"
                          + " keep its plain path.")),
              "@GenerateFocus(includeFields = {\"featured\", \"names\"}, widenFields = \"names\")",
              List.of("Name featured", "Map<String, Name> names"),
              "FeaturedNavigator<Register> featured()",
              "NamesNavigator<Register> names()"),
          row(
              "Depot",
              "@GenerateFocus(includeFields = {\"sites\", \"Sites\"})",
              List.of("Map<String, ? extends Item> sites", "Item Sites"),
              List.of(
                  says(
                      "includeFields",
                      "prevent widening as written. "
                          + shares("sites", "Depot", "Sites")
                          + " Rename 'Depot.sites' or 'Depot.Sites', then write Map<String, Item>"
                          + " in place of Map<String, ? extends Item> and add widenFields ="
                          + " \"sites\", or its new name, to @GenerateFocus on Depot, or remove"
                          + " \"sites\" from includeFields on Depot to keep its plain path.")),
              "@GenerateFocus(includeFields = {\"sites\", \"outlet\"}, widenFields = \"sites\")",
              List.of("Map<String, Item> sites", "Item outlet"),
              "SitesNavigator<Depot> sites()",
              "OutletNavigator<Depot> outlet()"),
          row(
              "Hawker",
              "@GenerateFocus(includeFields = {\"sites\", \"Sites\"})",
              List.of("List<Kiosk> sites", "List<Kiosk> Sites"),
              List.of(
                  says(
                      "includeFields",
                      "container of one. Remove \"sites\" from includeFields on Hawker."
                          + " Alternatively, annotate record Kiosk with @GenerateFocus."),
                  says(
                      "includeFields",
                      "container of one. "
                          + gains("Sites", "Hawker", "sites")
                          + " Remove \"Sites\" from includeFields on Hawker. Alternatively, rename"
                          + " 'Hawker.Sites' or 'Hawker.sites', then annotate record Kiosk with"
                          + " @GenerateFocus.")),
              "@GenerateFocus(includeFields = {\"sites\", \"outlets\"})",
              List.of("List<Kiosk> sites", "List<Kiosk> outlets"),
              "SitesNavigator<Hawker> sites()",
              "OutletsNavigator<Hawker> outlets()"),
          row(
              "Stand",
              "@GenerateFocus(generateNavigators = true, includeFields = {\"sites\", \"Sites\"})",
              List.of("List<Kiosk> sites", "List<Kiosk> Sites"),
              List.of(
                  says(
                      "includeFields",
                      "container of one. "
                          + gains("sites", "Stand", "Sites")
                          + " Remove \"sites\" from includeFields on Stand. Alternatively, rename"
                          + " 'Stand.sites' or 'Stand.Sites', then annotate record Kiosk with"
                          + " @GenerateFocus."),
                  says(
                      "includeFields",
                      "container of one. "
                          + gains("Sites", "Stand", "sites")
                          + " Remove \"Sites\" from includeFields on Stand. Alternatively, rename"
                          + " 'Stand.Sites' or 'Stand.sites', then annotate record Kiosk with"
                          + " @GenerateFocus.")),
              "@GenerateFocus(generateNavigators = true, includeFields = {\"sites\", \"outlets\"})",
              List.of("List<Kiosk> sites", "List<Kiosk> outlets"),
              "SitesNavigator<Stand> sites()",
              "OutletsNavigator<Stand> outlets()"),
          row(
              "Pedlar",
              "@GenerateFocus(includeFields = {\"sites\", \"Sites\"})",
              List.of("List<Kiosk> sites", "Item Sites"),
              List.of(
                  says(
                      "includeFields",
                      "container of one. "
                          + shares("sites", "Pedlar", "Sites")
                          + " Remove \"sites\" from includeFields on Pedlar. Alternatively, rename"
                          + " 'Pedlar.sites' or 'Pedlar.Sites', then annotate record Kiosk with"
                          + " @GenerateFocus.")),
              "@GenerateFocus(includeFields = {\"sites\", \"outlet\"})",
              List.of("List<Kiosk> sites", "Item outlet"),
              "SitesNavigator<Pedlar> sites()",
              "OutletNavigator<Pedlar> outlet()"),
          row(
              "Vendor",
              "@GenerateFocus(includeFields = \"Sites\")",
              List.of("List<Kiosk> sites", "List<Kiosk> Sites"),
              List.of(
                  says(
                      "includeFields",
                      "container of one. Remove includeFields and set generateNavigators = false"
                          + " in @GenerateFocus on Vendor. Alternatively, annotate record Kiosk"
                          + " with @GenerateFocus.")),
              "@GenerateFocus(includeFields = \"Sites\")",
              List.of("List<Kiosk> sites", "List<Kiosk> Sites"),
              "SitesNavigator<Vendor> Sites()"),
          row(
              "Trader",
              "@GenerateFocus(includeFields = {\"sites\", \"Sites\"})",
              List.of("List<Stall> sites", "List<Kiosk> Sites"),
              List.of(
                  says(
                      "includeFields",
                      "container of one. Remove \"Sites\" from includeFields on Trader."
                          + " Alternatively, annotate record Kiosk with @GenerateFocus.")),
              "@GenerateFocus(includeFields = \"Sites\")",
              List.of("List<Stall> sites", "List<Kiosk> Sites"),
              "SitesNavigator<Trader> Sites()"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("INCLUDED")
  @DisplayName(
      "the includeFields warning asks for a rename before a change that gives a navigator, and"
          + " only then")
  void theIncludeFieldsWarningRenamesFirst(Row row) {
    followsThrough(row);
  }

  /** The note on a field that reaches a generic record through a wildcard. */
  static final List<Row> WILDCARDS =
      List.of(
          row(
              "Bundle",
              "@GenerateFocus",
              List.of("Box<?> box", "Box<String> Box"),
              List.of(
                  says(
                      "Box<?> box",
                      "through .via(). "
                          + shares("box", "Bundle", "Box")
                          + " Rename 'Bundle.box' or 'Bundle.Box', then write Box<Object>, or"
                          + " another concrete instantiation, in place of Box<?> to get a"
                          + " navigator.")),
              "@GenerateFocus",
              List.of("Box<Object> box", "Box<String> boxed"),
              "BoxNavigator<Bundle, Object> box()",
              "BoxedNavigator<Bundle, String> boxed()"),
          row(
              "Packet",
              NAVIGATORS,
              List.of("Box<String> Box", "Box<?> box"),
              List.of(
                  says(
                      "Box<?> box",
                      "through .via(). "
                          + shares("box", "Packet", "Box")
                          + " Rename 'Packet.box' or 'Packet.Box', then write Box<Object>, or"
                          + " another concrete instantiation, in place of Box<?> to get a"
                          + " navigator.")),
              NAVIGATORS,
              List.of("Box<String> boxed", "Box<Object> box"),
              "BoxedNavigator<Packet, String> boxed()",
              "BoxNavigator<Packet, Object> box()"),
          row(
              "Ticket",
              "@GenerateFocus",
              List.of("Tab<?> tab"),
              List.of(
                  says(
                      "Tab<?> tab",
                      "through .via(). A navigator into one instantiation of Tab would be a"
                          + " FocusPath, which has its own last(), so it could not navigate to"
                          + " 'Tab.last'. Rename 'Tab.last', then write Tab<Object>, or another"
                          + " concrete instantiation, in place of Tab<?> to get a navigator.")),
              "@GenerateFocus",
              List.of("Tab<Object> tab"),
              "TabNavigator<Ticket, Object> tab()"),
          row(
              "Docket",
              "@GenerateFocus",
              List.of("Tab<String> Tab", "Tab<?> tab"),
              List.of(
                  says(
                      "Tab<String> Tab",
                      "keeps the path it has without one. Rename 'Tab.last' to navigate into Tab,"
                          + " or add excludeFields = \"Tab\" to @GenerateFocus on Docket to keep"
                          + " the path without this note."),
                  says(
                      "Tab<?> tab",
                      "so it could not navigate to 'Tab.last'. "
                          + gains("tab", "Docket", "Tab")
                          + " Rename 'Tab.last' and either 'Docket.tab' or 'Docket.Tab', then"
                          + " write Tab<Object>, or another concrete instantiation, in place of"
                          + " Tab<?> to get a navigator.")),
              "@GenerateFocus",
              List.of("Tab<String> featured", "Tab<Object> tab"),
              "FeaturedNavigator<Docket, String> featured()",
              "TabNavigator<Docket, Object> tab()"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("WILDCARDS")
  @DisplayName("the wildcard note asks for each rename a navigator needs before the concrete type")
  void theWildcardNoteRenamesFirst(Row row) {
    followsThrough(row);
  }

  /** The note on a field whose target names a type this package cannot reach. */
  static final List<Row> HIDDEN =
      List.of(
          row(
              "Atlas",
              "@GenerateFocus",
              List.of("com.lib.Inner inner", "Item Inner"),
              List.of(
                  ends(
                      "com.lib.Inner inner",
                      "Make 'Secret' public. Until then AtlasFocus.inner() returns"
                          + " FocusPath<Atlas, Inner>. "
                          + shares("inner", "Atlas", "Inner")
                          + " Rename 'Atlas.inner' or 'Atlas.Inner' as well.")),
              "@GenerateFocus",
              List.of("com.lib.Inner inner", "Item featured"),
              "InnerNavigator<Atlas> inner()",
              "FeaturedNavigator<Atlas> featured()"),
          row(
              "Gazette",
              "@GenerateFocus",
              List.of("com.lib.Inner inner", "com.lib.Inner Inner"),
              List.of(
                  ends(
                      "com.lib.Inner inner",
                      "Make 'Secret' public. Until then GazetteFocus.inner() returns"
                          + " FocusPath<Gazette, Inner>."),
                  ends(
                      "com.lib.Inner Inner",
                      "Make 'Secret' public. Until then GazetteFocus.Inner() returns"
                          + " FocusPath<Gazette, Inner>. "
                          + gains("Inner", "Gazette", "inner")
                          + " Rename 'Gazette.Inner' or 'Gazette.inner' as well.")),
              "@GenerateFocus",
              List.of("com.lib.Inner inner", "com.lib.Inner featured"),
              "InnerNavigator<Gazette> inner()",
              "FeaturedNavigator<Gazette> featured()"),
          row(
              "Almanac",
              "@GenerateFocus",
              List.of("com.lib.Vault vault"),
              List.of(
                  ends(
                      "com.lib.Vault vault",
                      "Make 'Secret' public. Until then AlmanacFocus.vault() returns"
                          + " FocusPath<Almanac, Vault>. A navigator into Vault would be a"
                          + " FocusPath, which has its own last(), so it could not navigate to"
                          + " 'Vault.last'. Rename 'Vault.last' as well.")),
              "@GenerateFocus",
              List.of("com.lib.Vault vault"),
              "VaultNavigator<Almanac> vault()"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("HIDDEN")
  @DisplayName("the unreachable-type note names each rename a navigator needs as well")
  void theUnreachableTypeNoteRenamesAsWell(Row row) {
    followsThrough(row);
  }

  /** The note on a field whose target has a field named like a path method. */
  static final List<Row> PATH_METHODS =
      List.of(
          row(
              "Couple",
              "@GenerateFocus",
              List.of("Name name", "Name Name"),
              List.of(
                  says(
                      "Name name",
                      "keeps the path it has without one. Rename 'Name.last' to navigate into"
                          + " Name, or add excludeFields = \"name\" to @GenerateFocus on Couple to"
                          + " keep the path without this note."),
                  says(
                      "Name Name",
                      "keeps the path it has without one. "
                          + gains("Name", "Couple", "name")
                          + " Rename 'Name.last' and either 'Couple.Name' or 'Couple.name' to"
                          + " navigate into Name, or add excludeFields = \"Name\" to @GenerateFocus"
                          + " on Couple to keep the path without this note.")),
              "@GenerateFocus",
              List.of("Name name", "Name alias"),
              "NameNavigator<Couple> name()",
              "AliasNavigator<Couple> alias()"),
          row(
              "Badge",
              "@GenerateFocus",
              List.of("Name name", "Item Name"),
              List.of(
                  says(
                      "Name name",
                      "keeps the path it has without one. "
                          + shares("name", "Badge", "Name")
                          + " Rename 'Name.last' and either 'Badge.name' or 'Badge.Name' to"
                          + " navigate into Name, or add excludeFields = \"name\" to @GenerateFocus"
                          + " on Badge to keep the path without this note.")),
              "@GenerateFocus",
              List.of("Name name", "Item featured"),
              "NameNavigator<Badge> name()",
              "FeaturedNavigator<Badge> featured()"),
          row(
              "Label",
              "@GenerateFocus",
              List.of("String name", "Name Name"),
              List.of(
                  says(
                      "Name Name",
                      "keeps the path it has without one. Rename 'Name.last' to navigate into"
                          + " Name, or add excludeFields = \"Name\" to @GenerateFocus on Label to"
                          + " keep the path without this note.")),
              "@GenerateFocus",
              List.of("String name", "Name Name"),
              "NameNavigator<Label> Name()"),
          row(
              "Signer",
              "@GenerateFocus",
              List.of("Other name", "Name Name"),
              List.of(
                  says(
                      "Name Name",
                      "keeps the path it has without one. Rename 'Name.last' to navigate into"
                          + " Name, or add excludeFields = \"Name\" to @GenerateFocus on Signer to"
                          + " keep the path without this note.")),
              "@GenerateFocus(excludeFields = \"name\")",
              List.of("Other name", "Name Name"),
              "NameNavigator<Signer> Name()"),
          row(
              "Census",
              "@GenerateFocus",
              List.of("List<Wide> wide", "Wide Wide"),
              List.of(
                  says(
                      "Wide Wide",
                      "keeps the path it has without one. Rename 'Wide.last' to navigate into"
                          + " Wide, or add excludeFields = \"Wide\" to @GenerateFocus on Census to"
                          + " keep the path without this note.")),
              "@GenerateFocus(excludeFields = \"wide\")",
              List.of("List<Wide> wide", "Wide Wide"),
              "WideNavigator<Census> Wide()"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("PATH_METHODS")
  @DisplayName(
      "the path-method note asks for a field rename where renaming the target's field alone gives"
          + " the class name to another")
  void thePathMethodNoteRenamesFirst(Row row) {
    followsThrough(row);
  }

  /**
   * The two compilations most tests read: each record as written, and each record with its fix
   * lines followed. The holder reads the test class's lists, so the class's methods may read it,
   * but its static fields must not.
   */
  private static final class Shared {

    static final List<Row> ROWS =
        Stream.of(UNWIDENED, INCLUDED, WILDCARDS, HIDDEN, PATH_METHODS)
            .flatMap(List::stream)
            .toList();

    static final Compilation WRITTEN =
        compile(
            Stream.of(ITEM, BOX, OTHER, STALL, NAME, TAB, WIDE, KIOSK, SECRET, INNER, VAULT),
            Row::written);

    static final Compilation FOLLOWED =
        compile(
            Stream.of(
                ITEM,
                BOX,
                OTHER,
                STALL,
                RENAMED_NAME,
                RENAMED_TAB,
                RENAMED_WIDE,
                ANNOTATED_KIOSK,
                PUBLIC_SECRET,
                INNER,
                RENAMED_VAULT),
            Row::followed,
            "-Xlint:all,-processing",
            "-Werror");

    private static Compilation compile(
        Stream<JavaFileObject> records, Function<Row, Shape> shape, String... options) {
      return javac()
          .withProcessors(new FocusProcessor())
          .withOptions(List.of(options))
          .compile(Stream.concat(records, ROWS.stream().map(shape).map(Shape::source)).toList());
    }
  }

  /**
   * Checks that each of a row's messages says what it should on its line as written, and that the
   * row with its fix lines followed reports nothing and declares each navigator.
   */
  private static void followsThrough(Row row) {
    JavaFileObject written = row.written().source();
    for (Message message : row.messages()) {
      assertThat(messagesAt(written, message.at()))
          .anySatisfy(
              said -> {
                if (message.ends()) {
                  assertThat(said).endsWith(message.says());
                } else {
                  assertThat(said).contains(message.says());
                }
              });
    }
    String path = row.followed().source().toUri().getPath();
    assertThat(Shared.FOLLOWED.diagnostics())
        .noneMatch(
            diagnostic ->
                diagnostic.getSource() != null
                    && diagnostic.getSource().toUri().getPath().equals(path));
    row.navigators()
        .forEach(
            navigator ->
                assertGeneratedCodeContains(
                    Shared.FOLLOWED,
                    "com.example." + row.written().record() + "Focus",
                    "public static " + navigator));
  }

  /** The messages the records as written report in a file, on the line holding the text. */
  private static List<String> messagesAt(JavaFileObject file, String text) {
    String path = file.toUri().getPath();
    long line = lineOf(file, text);
    assertThat(Shared.WRITTEN.sourceFiles())
        .extracting(each -> each.toUri().getPath())
        .contains(path);
    return Shared.WRITTEN.diagnostics().stream()
        .filter(
            diagnostic ->
                diagnostic.getSource() != null
                    && diagnostic.getSource().toUri().getPath().equals(path)
                    && diagnostic.getLineNumber() == line)
        .map(diagnostic -> diagnostic.getMessage(null))
        .toList();
  }

  /** The number of the first line of a file that holds the text. */
  private static long lineOf(JavaFileObject file, String text) {
    try {
      List<String> lines = file.getCharContent(true).toString().lines().toList();
      return IntStream.range(0, lines.size())
              .filter(index -> lines.get(index).contains(text))
              .findFirst()
              .orElseThrow(() -> new AssertionError("no line holds " + text + " in " + file))
          + 1L;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  @DisplayName(
      "the shared-class-name error asks for a field named like a path method to be renamed too")
  void theSharedClassNameErrorRenamesThePathMethodFieldToo() {
    JavaFileObject pairing = source("Pairing", NAVIGATORS, "Item Name", "Name name");
    Compilation refused = javac().withProcessors(new FocusProcessor()).compile(ITEM, NAME, pairing);

    assertThat(refused).failed();
    assertThat(refused).hadErrorCount(1);
    assertThat(refused)
        .hadErrorContaining(
            "with the first letter in upper case. A navigator for 'name' would be a FocusPath,"
                + " which has its own last(), so it could not navigate to 'Name.last'. Rename"
                + " 'Name.last' and either 'Pairing.name' or 'Pairing.Name', or add excludeFields"
                + " = \"name\" to @GenerateFocus on Pairing to keep 'name' a plain path.")
        .inFile(pairing)
        .onLineContaining("Name name");

    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(ITEM, RENAMED_NAME, source("Pairing", NAVIGATORS, "Item Name", "Name alias"));
    assertThat(followed).succeededWithoutWarnings();
    assertThat(followed.notes()).isEmpty();
    assertGeneratedCodeContains(
        followed, "com.example.PairingFocus", "public static NameNavigator<Pairing> Name()");
    assertGeneratedCodeContains(
        followed, "com.example.PairingFocus", "public static AliasNavigator<Pairing> alias()");
  }

  @Test
  @DisplayName("the missing-companion note names the rename a navigator needs as well")
  void theMissingCompanionNoteRenamesAsWell(@TempDir Path temp) throws IOException {
    JavaFileObject ext =
        JavaFileObjects.forSourceString(
            "com.dep.Ext",
            """
            package com.dep;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            @GenerateFocus
            public record Ext(String code) {}
            """);
    Compilation unprocessed = javac().withOptions("-proc:none").compile(ext);
    assertThat(unprocessed).succeededWithoutWarnings();
    JavaFileObject outer = source("Outer", "@GenerateFocus", "com.dep.Ext ext", "Item Ext");
    Compilation noted =
        javac()
            .withProcessors(new FocusProcessor())
            .withClasspath(classpathWith(classDirectory(unprocessed, temp.resolve("bare"))))
            .compile(ITEM, outer);

    assertThat(noted).succeeded();
    assertThat(noted)
        .hadNoteContaining(
            "rebuild it; until then OuterFocus.ext() returns FocusPath<Outer, Ext>. "
                + shares("ext", "Outer", "Ext")
                + " Rename 'Outer.ext' or 'Outer.Ext' as well.")
        .inFile(outer)
        .onLineContaining("com.dep.Ext ext");

    Compilation processed = javac().withProcessors(new FocusProcessor()).compile(ext);
    assertThat(processed).succeededWithoutWarnings();
    Compilation followed =
        javac()
            .withProcessors(new FocusProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .withClasspath(classpathWith(classDirectory(processed, temp.resolve("built"))))
            .compile(ITEM, source("Outer", "@GenerateFocus", "com.dep.Ext ext", "Item featured"));
    assertThat(followed).succeededWithoutWarnings();
    assertThat(followed.notes()).isEmpty();
    assertGeneratedCodeContains(
        followed, "com.example.OuterFocus", "public static ExtNavigator<Outer> ext()");
    assertGeneratedCodeContains(
        followed, "com.example.OuterFocus", "public static FeaturedNavigator<Outer> featured()");
  }

  @Test
  @DisplayName("each record as written compiles, and reports only in its own file")
  void writtenCompiles() {
    assertThat(Shared.WRITTEN).succeeded();
    List<String> files =
        Shared.ROWS.stream().map(row -> row.written().source().toUri().getPath()).toList();
    assertThat(Stream.concat(Shared.WRITTEN.warnings().stream(), Shared.WRITTEN.notes().stream()))
        .extracting(
            diagnostic ->
                diagnostic.getSource() == null ? "" : diagnostic.getSource().toUri().getPath())
        .isSubsetOf(files);
  }

  @Test
  @DisplayName("each fix line, followed as written, compiles with nothing more to report")
  void eachFixLineCompiles() {
    assertThat(Shared.FOLLOWED).succeededWithoutWarnings();
    assertThat(Shared.FOLLOWED.notes()).isEmpty();
  }
}
