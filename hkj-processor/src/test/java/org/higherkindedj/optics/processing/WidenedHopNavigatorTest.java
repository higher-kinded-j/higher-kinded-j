// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.compilations;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContainsRaw;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A hop from a navigator into a field whose own path is narrower than the composed one hands on the
 * field's navigator in the form of the composed tier, nested in that navigator, so a chain keeps
 * navigating after a hop that widens the path.
 *
 * <p>The probe below is compiled alongside the records, so each chain it writes, {@code
 * HistoryFocus.orders().customer().email().value()} among them, is checked by javac as well as run,
 * against the same chain composed with {@code .via()} from the paths the navigators wrap.
 */
@DisplayName("Navigating on after a hop that widens the path")
class WidenedHopNavigatorTest {

  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import java.util.List;
        import java.util.Optional;
        import org.higherkindedj.hkt.Kind;
        import org.higherkindedj.hkt.list.ListKind;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.jspecify.annotations.Nullable;

        %s
        """
            .formatted(body));
  }

  private static final JavaFileObject EMAIL =
      source("Email", "@GenerateFocus public record Email(String value) {}");

  private static final JavaFileObject CUSTOMER =
      source(
          "Customer",
          "@GenerateFocus public record Customer(String name, Email email, @Nullable Email backup)"
              + " {}");

  private static final JavaFileObject BOX =
      source("Box", "@GenerateFocus public record Box<T>(T value) {}");

  private static final JavaFileObject ORDER =
      source(
          "Order",
          """
          @GenerateFocus
          public record Order(
              Customer customer,
              Optional<Customer> referrer,
              @Nullable Customer cosigner,
              Box<Customer> boxed) {}
          """);

  private static final JavaFileObject HISTORY =
      source(
          "History",
          """
          @GenerateFocus
          public record History(
              List<Order> orders, Optional<Order> latest, Kind<ListKind.Witness, Order> archived) {}
          """);

  /**
   * Each chain read and written by navigating, and by composing with {@code .via()} the paths the
   * navigators wrap, keyed by chain: the read, then the record as an upper-casing write left it.
   */
  private static final JavaFileObject PROBE =
      JavaFileObjects.forSourceString(
          "com.example.Probe",
          """
          package com.example;

          import java.util.List;
          import java.util.Map;
          import java.util.Optional;
          import org.higherkindedj.hkt.list.ListKindHelper;
          import org.higherkindedj.optics.focus.AffinePath;
          import org.higherkindedj.optics.focus.TraversalPath;

          public final class Probe {
            private Probe() {}

            private static Customer customer(String name, String backup) {
              return new Customer(
                  name, new Email(name + "@shop"), backup == null ? null : new Email(backup));
            }

            private static Order order(String name, String referrer, String cosigner) {
              Customer customer = customer(name, null);
              return new Order(
                  customer,
                  Optional.ofNullable(referrer).map(r -> customer(r, null)),
                  cosigner == null ? null : customer(cosigner, cosigner + "@home"),
                  new Box<>(customer));
            }

            public static History history() {
              List<Order> orders = List.of(order("ann", "bob", null), order("cy", null, "di"));
              return new History(
                  orders,
                  Optional.of(order("ed", null, "fay")),
                  ListKindHelper.LIST.widen(orders));
            }

            // The hops widen the path, and each hands on a navigator whose type says so. A
            // navigator is a path, so each one can also be held as the plain path it wraps.
            static void types() {
              OrderFocus.CustomerNavigator.TraversalForm<History> customers =
                  HistoryFocus.orders().customer();
              OrderFocus.CustomerNavigator.AffineForm<History> latest = HistoryFocus.latest().customer();
              OrderFocus.ReferrerNavigator.TraversalForm<History> referrers =
                  HistoryFocus.orders().referrer();
              OrderFocus.BoxedNavigator.TraversalForm<History, Customer> boxes =
                  HistoryFocus.orders().boxed();
              CustomerFocus.EmailNavigator.TraversalForm<History> emails = customers.email();
              CustomerFocus.EmailNavigator.AffineForm<History> latestEmail = latest.email();
              TraversalPath<History, Customer> plain = HistoryFocus.orders().customer();
              AffinePath<History, Customer> plainLatest = HistoryFocus.latest().customer();
            }

            public static Map<String, List<Object>> navigated(History h) {
              return Map.of(
                  "orders.customer.email", List.of(
                      HistoryFocus.orders().customer().email().value().getAll(h),
                      HistoryFocus.orders().customer().email().value()
                          .modifyAll(String::toUpperCase, h)),
                  "latest.customer.email", List.of(
                      HistoryFocus.latest().customer().email().value().getOptional(h),
                      HistoryFocus.latest().customer().email().value()
                          .modify(String::toUpperCase, h)),
                  "orders.referrer.email", List.of(
                      HistoryFocus.orders().referrer().email().value().getAll(h),
                      HistoryFocus.orders().referrer().email().value()
                          .modifyAll(String::toUpperCase, h)),
                  "orders.cosigner.backup", List.of(
                      HistoryFocus.orders().cosigner().backup().value().getAll(h),
                      HistoryFocus.orders().cosigner().backup().value()
                          .modifyAll(String::toUpperCase, h)),
                  "latest.cosigner.email", List.of(
                      HistoryFocus.latest().cosigner().email().value().getOptional(h),
                      HistoryFocus.latest().cosigner().email().value()
                          .modify(String::toUpperCase, h)),
                  "archived.customer.email", List.of(
                      HistoryFocus.archived().customer().email().value().getAll(h),
                      HistoryFocus.archived().customer().email().value()
                          .modifyAll(String::toUpperCase, h)),
                  "orders.boxed.value", List.of(
                      HistoryFocus.orders().boxed().value().via(CustomerFocus.name()).getAll(h),
                      HistoryFocus.orders().boxed().value().via(CustomerFocus.name())
                          .modifyAll(String::toUpperCase, h)));
            }

            public static Map<String, List<Object>> composed(History h) {
              TraversalPath<History, Order> orders = HistoryFocus.orders().toPath();
              AffinePath<History, Order> latest = HistoryFocus.latest().toPath();
              TraversalPath<History, Order> archived = HistoryFocus.archived().toPath();
              return Map.of(
                  "orders.customer.email", List.of(
                      orders.via(OrderFocus.customer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value()).getAll(h),
                      orders.via(OrderFocus.customer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .modifyAll(String::toUpperCase, h)),
                  "latest.customer.email", List.of(
                      latest.via(OrderFocus.customer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .getOptional(h),
                      latest.via(OrderFocus.customer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .modify(String::toUpperCase, h)),
                  "orders.referrer.email", List.of(
                      orders.via(OrderFocus.referrer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value()).getAll(h),
                      orders.via(OrderFocus.referrer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .modifyAll(String::toUpperCase, h)),
                  "orders.cosigner.backup", List.of(
                      orders.via(OrderFocus.cosigner().toPath())
                          .via(CustomerFocus.backup().toPath()).via(EmailFocus.value()).getAll(h),
                      orders.via(OrderFocus.cosigner().toPath())
                          .via(CustomerFocus.backup().toPath()).via(EmailFocus.value())
                          .modifyAll(String::toUpperCase, h)),
                  "latest.cosigner.email", List.of(
                      latest.via(OrderFocus.cosigner().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .getOptional(h),
                      latest.via(OrderFocus.cosigner().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .modify(String::toUpperCase, h)),
                  "archived.customer.email", List.of(
                      archived.via(OrderFocus.customer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value()).getAll(h),
                      archived.via(OrderFocus.customer().toPath())
                          .via(CustomerFocus.email().toPath()).via(EmailFocus.value())
                          .modifyAll(String::toUpperCase, h)),
                  "orders.boxed.value", List.of(
                      orders.via(OrderFocus.boxed().toPath()).via(BoxFocus.<Customer>value())
                          .via(CustomerFocus.name()).getAll(h),
                      orders.via(OrderFocus.boxed().toPath()).via(BoxFocus.<Customer>value())
                          .via(CustomerFocus.name()).modifyAll(String::toUpperCase, h)));
            }
          }
          """);

  private static final CompiledResult RESULT =
      RuntimeCompilationHelper.compileWith(
          new FocusProcessor(), EMAIL, CUSTOMER, BOX, ORDER, HISTORY, PROBE);

  private static Object probe(String method, Object... args) {
    try {
      return RESULT.invokeStatic("com.example.Probe", method, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not run com.example.Probe." + method, e);
    }
  }

  /** The text of every note a compilation reported. */
  private static List<String> noteTexts(Compilation compilation) {
    return compilation.notes().stream().map(note -> note.getMessage(null)).toList();
  }

  /** The text of a generated file. */
  private static String generated(Compilation compilation, String generatedFile) {
    try {
      return compilation
          .generatedSourceFile(generatedFile)
          .orElseThrow()
          .getCharContent(true)
          .toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked") // the probe returns a Map<String, List<Object>>
  private static Map<String, List<Object>> chains(String route) {
    return (Map<String, List<Object>>) probe(route, probe("history"));
  }

  @Test
  @DisplayName("navigating on reads and writes what the via() chain does")
  void navigatingOnAgreesWithVia() {
    Map<String, List<Object>> navigated = chains("navigated");

    assertThat(navigated).isEqualTo(chains("composed"));
    // Spot-checks, so that two routes agreeing on nothing at all would fail here.
    assertThat(navigated.get("orders.customer.email").getFirst())
        .isEqualTo(List.of("ann@shop", "cy@shop"));
    assertThat(navigated.get("latest.customer.email").getFirst()).isEqualTo(Optional.of("ed@shop"));
    assertThat(navigated.get("orders.referrer.email").getFirst()).isEqualTo(List.of("bob@shop"));
    assertThat(navigated.get("orders.cosigner.backup").getFirst()).isEqualTo(List.of("di@home"));
    assertThat(navigated.get("latest.cosigner.email").getFirst())
        .isEqualTo(Optional.of("fay@shop"));
    assertThat(navigated.get("archived.customer.email").getFirst())
        .isEqualTo(List.of("ann@shop", "cy@shop"));
    assertThat(navigated.get("orders.boxed.value").getFirst()).isEqualTo(List.of("ann", "cy"));
    assertThat(navigated.get("orders.customer.email").getLast().toString())
        .contains("ANN@SHOP", "CY@SHOP");
  }

  @Nested
  @DisplayName("The forms a navigator has")
  class Forms {

    private static final String ORDER_FOCUS = "com.example.OrderFocus";
    private static final String HISTORY_FOCUS = "com.example.HistoryFocus";

    @Test
    @DisplayName("a navigator has a form for each tier wider than its own, and none for its own")
    void aFormForEachWiderTier() {
      Compilation compilation = RESULT.compilation();

      assertGeneratedCodeContains(
          compilation,
          ORDER_FOCUS,
          "public static final class TraversalForm<S> extends NavigatorTraversalPath<S, Customer> {");
      assertGeneratedCodeContains(
          compilation,
          ORDER_FOCUS,
          "public static final class AffineForm<S> extends NavigatorAffinePath<S, Customer> {");
      // A generic target's form declares the target's type parameters after the source.
      assertGeneratedCodeContains(
          compilation,
          ORDER_FOCUS,
          "public static final class TraversalForm<S, T> extends NavigatorTraversalPath<S, Box<T>>"
              + " {");
      // A traversal navigator has no wider tier, so of History's navigators only the affine one,
      // over 'latest', has a form.
      assertThat(
              Pattern.compile("final class TraversalForm<")
                  .matcher(generated(compilation, HISTORY_FOCUS))
                  .results())
          .hasSize(1);
      assertGeneratedCodeDoesNotContain(compilation, HISTORY_FOCUS, "class AffineForm<");
    }

    @Test
    @DisplayName("a hop that widens the path hands on the form of the tier it lands on")
    void aWideningHopHandsOnTheForm() {
      Compilation compilation = RESULT.compilation();

      assertGeneratedCodeContains(
          compilation,
          HISTORY_FOCUS,
          "public OrderFocus.CustomerNavigator.TraversalForm<S> customer() {"
              + " return new OrderFocus.CustomerNavigator.TraversalForm<>("
              + "toPath().via(OrderFocus.customer().toPath())); }");
      assertGeneratedCodeContains(
          compilation,
          HISTORY_FOCUS,
          "public OrderFocus.BoxedNavigator.TraversalForm<S, Customer> boxed() {");
      // A form's own hops hand on forms in turn, so the chain goes on as far as it goes.
      assertGeneratedCodeContains(
          compilation,
          ORDER_FOCUS,
          "public CustomerFocus.EmailNavigator.TraversalForm<S> email() {");
      assertGeneratedCodeContains(
          compilation,
          ORDER_FOCUS,
          "public CustomerFocus.BackupNavigator.TraversalForm<S> backup() {");
    }

    @Test
    @DisplayName("a form hides no type of its name that the navigator's target names")
    void aFormHidesNoTypeOfItsName() {
      // Inside a navigator, AffineForm and TraversalForm name its forms, so a target field of a
      // type named the same, or a target record named the same, has to be written out in full.
      JavaFileObject traversal =
          source("TraversalForm", "public record TraversalForm(String route) {}");
      JavaFileObject affine =
          source("AffineForm", "@GenerateFocus public record AffineForm(double scale) {}");
      JavaFileObject leg =
          source(
              "Leg",
              "@GenerateFocus public record Leg(TraversalForm crossing, AffineForm transform) {}");
      JavaFileObject trip =
          source("Trip", "@GenerateFocus public record Trip(Leg leg, List<Leg> legs) {}");
      JavaFileObject use =
          JavaFileObjects.forSourceString(
              "com.example.Use",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.optics.focus.FocusPath;
              import org.higherkindedj.optics.focus.TraversalPath;

              final class Use {
                static List<Double> scales(Trip trip) {
                  FocusPath<Trip, TraversalForm> crossing = TripFocus.leg().crossing();
                  TraversalPath<Trip, TraversalForm> crossings = TripFocus.legs().crossing();
                  LegFocus.TransformNavigator.TraversalForm<Trip> transforms =
                      TripFocus.legs().transform();
                  return transforms.scale().getAll(trip);
                }
              }
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(traversal, affine, leg, trip, use);

      assertAbout(compilations()).that(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.LegFocus",
          "public static final class AffineForm<S> extends NavigatorAffinePath<S,"
              + " com.example.AffineForm> {");
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.TripFocus",
          "public FocusPath<S, com.example.TraversalForm> crossing() {");
    }

    @Test
    @DisplayName("a navigator's javadoc names its forms")
    void theJavadocNamesTheForms() {
      Compilation compilation = RESULT.compilation();

      assertGeneratedCodeContainsRaw(
          compilation,
          ORDER_FOCUS,
          "A hop into {@code customer} from a wider path hands on {@code AffineForm} or {@code"
              + " TraversalForm}, nested here:");
      assertGeneratedCodeContainsRaw(
          compilation,
          ORDER_FOCUS,
          "A hop into {@code referrer} from a wider path hands on {@code TraversalForm}, nested"
              + " here:");
      assertGeneratedCodeContainsRaw(
          compilation,
          ORDER_FOCUS,
          "The {@code customer} navigator that a hop from an {@link AffinePath} hands on.");
      // An affine path's own navigator is named with its article too.
      assertGeneratedCodeContainsRaw(
          compilation, ORDER_FOCUS, "This navigator is an {@link AffinePath}, with");
      assertGeneratedCodeContainsRaw(
          compilation, ORDER_FOCUS, "@return an AffinePath focusing on the {@code name} field");
    }
  }

  @Nested
  @DisplayName("Where a hop returns the composed path")
  class ComposedPath {

    @Test
    @DisplayName("a target field named like a wider path's own method leaves out that form only")
    void aClashingFieldLeavesOutThatForm() {
      // TraversalPath has a headOption() that FocusPath and AffinePath do not, so a traversal form
      // of a navigator into Feed could not navigate to its field.
      JavaFileObject feed =
          source("Feed", "@GenerateFocus public record Feed(String name, String headOption) {}");
      JavaFileObject channel =
          source("Channel", "@GenerateFocus public record Channel(Feed feed) {}");
      JavaFileObject station =
          source(
              "Station",
              """
              @GenerateFocus
              public record Station(List<Channel> channels, Optional<Channel> current) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(feed, channel, station);

      assertAbout(compilations()).that(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ChannelFocus",
          "public static final class AffineForm<S> extends NavigatorAffinePath<S, Feed> {");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.example.ChannelFocus", "TraversalForm<S>");
      assertGeneratedCodeContains(
          compilation,
          "com.example.StationFocus",
          "public TraversalPath<S, Feed> feed() { return"
              + " toPath().via(ChannelFocus.feed().toPath()); }");
      assertGeneratedCodeContains(
          compilation,
          "com.example.StationFocus",
          "public ChannelFocus.FeedNavigator.AffineForm<S> feed() {");
      // Channel leaves navigators to the default, so it is told nothing.
      assertThat(noteTexts(compilation)).noneMatch(note -> note.contains("TraversalForm"));
    }

    @Test
    @DisplayName("a record that asks for navigators is told which form is missing and why")
    void aRecordThatAsksIsToldWhichFormIsMissing() {
      JavaFileObject feed =
          source(
              "Feed",
              "@GenerateFocus public record Feed(String name, String headOption, String toAffine)"
                  + " {}");
      JavaFileObject channel =
          source(
              "Channel",
              "@GenerateFocus(generateNavigators = true) public record Channel(Feed feed) {}");

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(feed, channel);

      assertAbout(compilations()).that(compilation).succeededWithoutWarnings();
      assertThat(noteTexts(compilation))
          .filteredOn(note -> note.contains("'Channel.feed'"))
          .containsExactly(
              "@GenerateFocus: the navigator for 'Channel.feed' has no AffineForm or TraversalForm."
                  + " An AffineForm would be an AffinePath, which has its own toAffine(), so it could"
                  + " not navigate to 'Feed.toAffine', and a hop into 'feed' from an AffinePath"
                  + " returns a plain AffinePath. A TraversalForm would be a TraversalPath, which"
                  + " has its own headOption(), so it could not navigate to 'Feed.headOption', and a"
                  + " hop into 'feed' from a TraversalPath returns a plain TraversalPath. Rename"
                  + " 'Feed.toAffine' and 'Feed.headOption' to give the navigator for 'feed' an"
                  + " AffineForm and a TraversalForm, or remove generateNavigators = true from"
                  + " @GenerateFocus on Channel to go without this note.");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.ChannelFocus", "Form<");
    }

    @Test
    @DisplayName("renaming the field, as the note says, gives the navigator its form")
    void followingTheNoteGivesTheForm() {
      // The note's other way out, leaving navigators to the default, is the record that
      // aClashingFieldLeavesOutThatForm compiles without a note.
      JavaFileObject feed =
          source("Feed", "@GenerateFocus public record Feed(String name, String firstItem) {}");
      JavaFileObject channel =
          source(
              "Channel",
              "@GenerateFocus(generateNavigators = true) public record Channel(Feed feed) {}");

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(feed, channel);

      assertAbout(compilations()).that(compilation).succeededWithoutWarnings();
      assertThat(noteTexts(compilation)).noneMatch(note -> note.contains("'Channel.feed'"));
      assertGeneratedCodeContains(
          compilation,
          "com.example.ChannelFocus",
          "public static final class TraversalForm<S> extends NavigatorTraversalPath<S, Feed> {");
    }

    @Test
    @DisplayName("a target type parameter named like a form leaves out that form only")
    void aTypeParameterNamedLikeAFormLeavesOutThatForm() {
      // Inside the navigator, a nested class named TraversalForm would hide the type variable of
      // that name that the navigator declares after the target.
      JavaFileObject crate =
          source(
              "Crate",
              "@GenerateFocus public record Crate<TraversalForm>(TraversalForm content) {}");
      JavaFileObject shelf =
          source("Shelf", "@GenerateFocus public record Shelf(Crate<String> crate) {}");
      JavaFileObject depot =
          source("Depot", "@GenerateFocus public record Depot(List<Shelf> shelves) {}");

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:rawtypes", "-Werror")
              .compile(crate, shelf, depot);

      assertAbout(compilations()).that(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ShelfFocus",
          "public static final class AffineForm<S, TraversalForm> extends NavigatorAffinePath<S,"
              + " Crate<TraversalForm>> {");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.example.ShelfFocus", "final class TraversalForm<");
      assertGeneratedCodeContains(
          compilation,
          "com.example.DepotFocus",
          "public TraversalPath<S, Crate<String>> crate() {");
    }

    @Test
    @DisplayName("a type of the unnamed package named like a form leaves out that form only")
    void anUnnamedPackageTypeNamedLikeAFormLeavesOutThatForm() {
      // Generated code cannot qualify a type of the unnamed package, so inside the navigator a
      // nested TraversalForm would hide the one the target's field names.
      JavaFileObject traversal =
          JavaFileObjects.forSourceString(
              "TraversalForm", "public record TraversalForm(String route) {}");
      JavaFileObject leg =
          JavaFileObjects.forSourceString(
              "Leg",
              """
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Leg(TraversalForm crossing, String name) {}
              """);
      JavaFileObject trip =
          JavaFileObjects.forSourceString(
              "Trip",
              """
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Trip(Leg leg, List<Leg> legs) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:rawtypes", "-Werror")
              .compile(traversal, leg, trip);

      assertAbout(compilations()).that(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "TripFocus",
          "public static final class AffineForm<S> extends NavigatorAffinePath<S, Leg> {");
      assertGeneratedCodeDoesNotContain(compilation, "TripFocus", "final class TraversalForm<");
    }

    @Test
    @DisplayName("a form a rename would not restore is not offered by the note")
    void theNoteOffersNoFormARenameWouldNotRestore() {
      // The type parameter alone leaves the TraversalForm out, so renaming headOption would not
      // give the navigator one.
      JavaFileObject feed =
          source(
              "Feed",
              "@GenerateFocus public record Feed<TraversalForm>(TraversalForm name, String"
                  + " headOption) {}");
      JavaFileObject channel =
          source(
              "Channel",
              "@GenerateFocus(generateNavigators = true) public record Channel(Feed<String> feed)"
                  + " {}");

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(feed, channel);

      assertAbout(compilations()).that(compilation).succeededWithoutWarnings();
      assertThat(noteTexts(compilation)).noneMatch(note -> note.contains("'Channel.feed'"));
    }

    @Test
    @DisplayName("a navigator at the depth limit returns the composed path though a form exists")
    void theDepthLimitEndsTheChain() {
      JavaFileObject station =
          source(
              "Station",
              """
              @GenerateFocus(maxNavigatorDepth = 1)
              public record Station(List<Order> orders) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(EMAIL, CUSTOMER, BOX, ORDER, station);

      assertAbout(compilations()).that(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.StationFocus",
          "public TraversalPath<S, Customer> customer() {");
    }
  }
}
