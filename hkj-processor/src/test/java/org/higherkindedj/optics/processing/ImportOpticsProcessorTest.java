// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link ImportOpticsProcessor}.
 *
 * <p>These tests verify that the processor correctly generates optics for various external types
 * including records, sealed interfaces, enums, and classes with wither methods.
 */
@DisplayName("ImportOpticsProcessor")
class ImportOpticsProcessorTest {

  @Nested
  @DisplayName("Record Processing")
  class RecordProcessing {

    @Test
    @DisplayName("should generate lenses for external record via package-info")
    void shouldGenerateLensesForExternalRecord() {
      // External record to import
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Customer",
              """
              package com.external;

              public record Customer(String name, int age) {}
              """);

      // Package-info with @ImportOptics
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Customer.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedNameLens =
          """
          public static Lens<Customer, String> name() {
              return Lens.of(Customer::name, (source, newValue) -> new Customer(newValue, source.age()));
          }
          """;

      final String expectedAgeLens =
          """
          public static Lens<Customer, Integer> age() {
              return Lens.of(Customer::age, (source, newValue) -> new Customer(source.name(), newValue));
          }
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.CustomerLenses", expectedNameLens);
      assertGeneratedCodeContains(compilation, "com.myapp.optics.CustomerLenses", expectedAgeLens);
    }

    @Test
    @DisplayName("should generate a traversal with type parameters for a generic record")
    void shouldGenerateTraversalWithTypeParametersForGenericRecord() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Bag",
              """
              package com.external;

              import java.util.List;

              public record Bag<T>(String name, List<T> items) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Bag.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      // The lens methods beside it already declared the record's parameters; the traversal named
      // the record raw and declared none, so the container's element type had nothing to bind to.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.BagLenses",
          "public static <T> Traversal<Bag<T>, T> itemsTraversal()");
    }

    @Test
    @DisplayName("should generate lenses with type parameters for generic record")
    void shouldGenerateLensesForGenericRecord() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Pair",
              """
              package com.external;

              public record Pair<A, B>(A first, B second) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Pair.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedFirstLens =
          """
          public static <A, B> Lens<Pair<A, B>, A> first()
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.PairLenses", expectedFirstLens);
    }

    @Test
    @DisplayName("should generate with methods for record")
    void shouldGenerateWithMethodsForRecord() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Point",
              """
              package com.external;

              public record Point(int x, int y) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Point.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedWithX =
          """
          public static Point withX(Point source, int newX) {
              return x().set(newX, source);
          }
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.PointLenses", expectedWithX);
    }

    @Test
    @DisplayName("a raw component and a raw type-parameter bound compile under -Werror")
    void rawComponentAndRawBoundCompileUnderWerror() {
      // The lens, with and traversal members restate the component type and redeclare the record's
      // type parameters with their bounds, in a file the author's own suppression does not reach.
      final var raw =
          JavaFileObjects.forSourceString(
              "com.external.RawBag",
              """
              package com.external;

              import java.util.List;
              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public record RawBag(List tags, Optional<List> contacts, List<List> rows) {}
              """);
      final var bounded =
          JavaFileObjects.forSourceString(
              "com.external.BoundedBag",
              """
              package com.external;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public record BoundedBag<T extends List>(T value, List<String> ids) {}
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.RawBag.class, com.external.BoundedBag.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(raw, bounded, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.RawBagLenses",
          "@SuppressWarnings(\"rawtypes\") public static Lens<RawBag, List> tags()");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.RawBagLenses",
          "@SuppressWarnings(\"rawtypes\") private static final class RowsTraversal"
              + " implements Traversal<RawBag, List>");
      // The element is clean, so only the redeclared bound can call for it here.
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.BoundedBagLenses",
          "@SuppressWarnings(\"rawtypes\") private static final class IdsTraversal<T extends List>");
    }

    @Test
    @DisplayName("a type parameter named like the generated class does not hide it")
    void typeParameterNamedLikeTheGeneratedClassDoesNotHideIt() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Box",
              """
              package com.external;

              public record Box<BoxLenses>(BoxLenses value) {}
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Box.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(externalRecord, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.BoxLenses",
          "return com.myapp.optics.BoxLenses.<BoxLenses>value().set(newValue, source);");
    }

    @Test
    @DisplayName("a wildcard element is traversed at the type it stands for")
    void wildcardElementIsTraversedAtTheTypeItStandsFor() {
      // No class can implement a traversal type that names a wildcard, so each one is written as
      // the type it stands for: the bound of an extends wildcard, and Object for any other.
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Bounds",
              """
              package com.external;

              import java.util.List;
              import java.util.Set;

              @SuppressWarnings("rawtypes")
              public record Bounds(
                  List<? extends Number> numbers,
                  List<?> anything,
                  Set<? super Integer> sinks,
                  List<? extends List> lists) {}
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Bounds.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(externalRecord, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      final String generated = "com.myapp.optics.BoundsLenses";
      assertGeneratedCodeContains(
          compilation, generated, "public static Traversal<Bounds, Number> numbersTraversal()");
      assertGeneratedCodeContains(
          compilation, generated, "public static Traversal<Bounds, Object> anythingTraversal()");
      assertGeneratedCodeContains(
          compilation, generated, "public static Traversal<Bounds, Object> sinksTraversal()");
      // The raw type the bound resolves to is answered for like any other.
      assertGeneratedCodeContains(
          compilation,
          generated,
          "@SuppressWarnings(\"rawtypes\") public static Traversal<Bounds, List> listsTraversal()");
    }

    @Test
    @DisplayName("an Optional component's traversal draws no lint warning")
    void optionalTraversalDrawsNoLintWarning() {
      // The effect f returns is already in the focus type, the wildcard's bound included, so the
      // traversal maps it as it stands.
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Doc",
              """
              package com.external;

              import java.util.Optional;

              public record Doc(String id, Optional<String> more, Optional<? extends Number> amount) {}
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Doc.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      // Beside the companion processor, as a build that finds its processors on the processor path
      // runs it, which claims the @Generated marker on the generated file.
      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor(), new CompanionAnnotationProcessor())
              .withOptions("-Xlint:all", "-Werror")
              .compile(externalRecord, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      final String generated = "com.myapp.optics.DocLenses";
      assertGeneratedCodeContains(
          compilation, generated, "public static Traversal<Doc, String> moreTraversal()");
      assertGeneratedCodeContains(
          compilation, generated, "public static Traversal<Doc, Number> amountTraversal()");
    }
  }

  @Nested
  @DisplayName("Sealed Interface Processing")
  class SealedInterfaceProcessing {

    @Test
    @DisplayName("should generate prisms for sealed interface subtypes")
    void shouldGeneratePrismsForSealedInterface() {
      final var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.external.Shape",
              """
              package com.external;

              public sealed interface Shape permits Circle, Rectangle {}
              """);

      final var circleSubtype =
          JavaFileObjects.forSourceString(
              "com.external.Circle",
              """
              package com.external;

              public record Circle(double radius) implements Shape {}
              """);

      final var rectangleSubtype =
          JavaFileObjects.forSourceString(
              "com.external.Rectangle",
              """
              package com.external;

              public record Rectangle(double width, double height) implements Shape {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Shape.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(sealedInterface, circleSubtype, rectangleSubtype, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedCirclePrism =
          """
          public static Prism<Shape, Circle> circle() {
              return Prism.of(source -> source instanceof Circle ? Optional.of((Circle) source) : Optional.empty(), value -> value);
          }
          """;

      final String expectedRectanglePrism =
          """
          public static Prism<Shape, Rectangle> rectangle() {
              return Prism.of(source -> source instanceof Rectangle ? Optional.of((Rectangle) source) : Optional.empty(), value -> value);
          }
          """;

      assertGeneratedCodeContains(compilation, "com.myapp.optics.ShapePrisms", expectedCirclePrism);
      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.ShapePrisms", expectedRectanglePrism);
    }
  }

  @Nested
  @DisplayName("Enum Processing")
  class EnumProcessing {

    @Test
    @DisplayName("should generate prisms for enum constants")
    void shouldGeneratePrismsForEnum() {
      final var enumType =
          JavaFileObjects.forSourceString(
              "com.external.Status",
              """
              package com.external;

              public enum Status { PENDING, ACTIVE, COMPLETED }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Status.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(enumType, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedPendingPrism =
          """
          public static Prism<Status, Status> pending() {
              return Prism.of(source -> source == Status.PENDING ? Optional.of(source) : Optional.empty(), value -> value);
          }
          """;

      final String expectedActivePrism =
          """
          public static Prism<Status, Status> active() {
              return Prism.of(source -> source == Status.ACTIVE ? Optional.of(source) : Optional.empty(), value -> value);
          }
          """;

      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.StatusPrisms", expectedPendingPrism);
      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.StatusPrisms", expectedActivePrism);
    }

    @Test
    @DisplayName("should convert SNAKE_CASE enum constants to camelCase method names")
    void shouldConvertSnakeCaseToCamelCase() {
      final var enumType =
          JavaFileObjects.forSourceString(
              "com.external.HttpStatus",
              """
              package com.external;

              public enum HttpStatus { OK, NOT_FOUND, INTERNAL_SERVER_ERROR }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.HttpStatus.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(enumType, packageInfo);

      assertThat(compilation).succeeded();

      // NOT_FOUND -> notFound
      final String expectedNotFoundPrism = "public static Prism<HttpStatus, HttpStatus> notFound()";

      // INTERNAL_SERVER_ERROR -> internalServerError
      final String expectedInternalServerErrorPrism =
          "public static Prism<HttpStatus, HttpStatus> internalServerError()";

      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.HttpStatusPrisms", expectedNotFoundPrism);
      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.HttpStatusPrisms", expectedInternalServerErrorPrism);
    }
  }

  @Nested
  @DisplayName("Wither Class Processing")
  class WitherClassProcessing {

    @Test
    @DisplayName("should generate lenses for class with wither methods")
    void shouldGenerateLensesForWitherClass() {
      final var witherClass =
          JavaFileObjects.forSourceString(
              "com.external.ImmutableDate",
              """
              package com.external;

              public final class ImmutableDate {
                  private final int year;
                  private final int month;
                  private final int day;

                  public ImmutableDate(int year, int month, int day) {
                      this.year = year;
                      this.month = month;
                      this.day = day;
                  }

                  public int getYear() { return year; }
                  public int getMonth() { return month; }
                  public int getDay() { return day; }

                  public ImmutableDate withYear(int year) {
                      return new ImmutableDate(year, this.month, this.day);
                  }

                  public ImmutableDate withMonth(int month) {
                      return new ImmutableDate(this.year, month, this.day);
                  }

                  public ImmutableDate withDay(int day) {
                      return new ImmutableDate(this.year, this.month, day);
                  }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.ImmutableDate.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(witherClass, packageInfo);

      assertThat(compilation).succeeded();

      final String expectedYearLens =
          """
          public static Lens<ImmutableDate, Integer> year() {
              return Lens.of(ImmutableDate::getYear, (source, newValue) -> source.withYear(newValue));
          }
          """;

      assertGeneratedCodeContains(
          compilation, "com.myapp.optics.ImmutableDateLenses", expectedYearLens);
    }

    @Test
    @DisplayName("a raw field and a raw type-parameter bound compile under -Werror")
    void rawFieldAndRawBoundCompileUnderWerror() {
      // The wither lens and with members restate the field type and redeclare the class's type
      // parameters with their bounds, in a file the author's own suppression does not reach.
      final var raw =
          JavaFileObjects.forSourceString(
              "com.external.RawBox",
              """
              package com.external;

              import java.util.List;
              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public final class RawBox {
                  private final List tags;
                  private final Optional<List> contacts;

                  public RawBox(List tags, Optional<List> contacts) {
                      this.tags = tags;
                      this.contacts = contacts;
                  }

                  public List tags() { return tags; }
                  public Optional<List> contacts() { return contacts; }
                  public RawBox withTags(List tags) { return new RawBox(tags, contacts); }
                  public RawBox withContacts(Optional<List> contacts) {
                      return new RawBox(tags, contacts);
                  }
              }
              """);
      final var bounded =
          JavaFileObjects.forSourceString(
              "com.external.BoundedBox",
              """
              package com.external;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public final class BoundedBox<T extends List> {
                  private final String id;

                  public BoundedBox(String id) { this.id = id; }

                  public String id() { return id; }
                  public BoundedBox<T> withId(String id) { return new BoundedBox<>(id); }
              }
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.RawBox.class, com.external.BoundedBox.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(raw, bounded, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.RawBoxLenses",
          "@SuppressWarnings(\"rawtypes\") public static Lens<RawBox, List> tags()");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.BoundedBoxLenses",
          "@SuppressWarnings(\"rawtypes\") public static <T extends List> Lens<BoundedBox<T>,"
              + " String> id()");
    }

    @Test
    @DisplayName("an inner class of a generic class is named under its enclosing class's arguments")
    void innerClassOfAGenericClassIsNamedUnderItsEnclosingClassArguments() {
      // Written without the enclosing class's arguments an inner class is raw, and a field typed by
      // the enclosing class's parameter names a variable nothing declares.
      final var outer =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public class Outer<X extends List> {
                  public final class Val {
                      private final X value;
                      public Val(X value) { this.value = value; }
                      public X value() { return value; }
                      public Val withValue(X value) { return new Val(value); }
                  }

                  public final class In<Y> {
                      private final Y item;
                      public In(Y item) { this.item = item; }
                      public Y item() { return item; }
                      public In<Y> withItem(Y item) { return new In<>(item); }
                  }

                  public final class Tag<P> {
                      private final String label;
                      public Tag(String label) { this.label = label; }
                      public String label() { return label; }
                      public <U> Tag<U> withLabel(String label) { return new Tag<>(label); }
                  }

                  public class Mid {
                      public final class Deep {
                          private final String id;
                          public Deep(String id) { this.id = id; }
                          public String id() { return id; }
                          public Deep withId(String id) { return new Deep(id); }
                      }
                  }
              }
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({
                  com.external.Outer.Val.class,
                  com.external.Outer.In.class,
                  com.external.Outer.Tag.class,
                  com.external.Outer.Mid.Deep.class
              })
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(outer, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.ValLenses",
          "public static <X extends List> Lens<Outer<X>.Val, X> value()");
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.InLenses",
          "public static <X extends List, Y> Outer<X>.In<Y> withItem(Outer<X>.In<Y> source,"
              + " Y newItem) { return InLenses.<X, Y>item().set(newItem, source); }");
      // The retag is inferred back to the class under the enclosing class's arguments too.
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.TagLenses",
          "public static <X extends List, P> Lens<Outer<X>.Tag<P>, String> label()");
      // Deep's own field is clean, so only the bound it redeclares from Outer can call for this.
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.DeepLenses",
          "@SuppressWarnings(\"rawtypes\") public static <X extends List>"
              + " Lens<Outer<X>.Mid.Deep, String> id()");
    }

    @Test
    @DisplayName("a wither is paired only when it returns the class under its own arguments")
    void witherIsPairedOnlyWhenItReturnsTheClassUnderItsOwnArguments() {
      // The lens hands the wither's result back as the class itself: a raw return would be an
      // unchecked conversion, and one under other arguments no conversion at all.
      final var tagged =
          JavaFileObjects.forSourceString(
              "com.external.Tagged",
              """
              package com.external;

              @SuppressWarnings("rawtypes")
              public class Tagged<T> {
                  private final String id;
                  private final String name;
                  private final String tag;
                  private final int count;

                  public Tagged(String id, String name, String tag, int count) {
                      this.id = id;
                      this.name = name;
                      this.tag = tag;
                      this.count = count;
                  }

                  public String id() { return id; }
                  public String name() { return name; }
                  public String tag() { return tag; }
                  public int count() { return count; }

                  public Tagged withId(String id) { return new Tagged<>(id, name, tag, count); }
                  public Tagged<String> withName(String name) {
                      return new Tagged<>(id, name, tag, count);
                  }
                  public Tagged<?> withTag(String tag) { return new Tagged<>(id, name, tag, count); }
                  public Special<T> withCount(int count) {
                      return new Special<>(id, name, tag, count);
                  }

                  public static final class Special<T> extends Tagged<T> {
                      public Special(String id, String name, String tag, int count) {
                          super(id, name, tag, count);
                      }
                  }
              }
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Tagged.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(tagged, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      final String generated = "com.myapp.optics.TaggedLenses";
      // A subtype is still the class, so its wither pairs.
      assertGeneratedCodeContains(
          compilation, generated, "public static <T> Lens<Tagged<T>, Integer> count()");
      // id, name and tag are all String fields, so no String lens means none of the three paired.
      assertGeneratedCodeDoesNotContain(compilation, generated, "Lens<Tagged<T>, String>");
    }

    @Test
    @DisplayName("a retag wither pairs when the call infers its own parameter back")
    void retagWitherPairsWhenTheCallInfersItsOwnParameterBack() {
      // withId's U is inferred to A at the generated call. withName's U cannot be A, which its
      // bound does not admit, and withCode's cannot be both A and B.
      final var phantom =
          JavaFileObjects.forSourceString(
              "com.external.Phantom",
              """
              package com.external;

              public final class Phantom<A, B> {
                  private final String id;
                  private final Integer name;
                  private final Long code;

                  public Phantom(String id, Integer name, Long code) {
                      this.id = id;
                      this.name = name;
                      this.code = code;
                  }

                  public String id() { return id; }
                  public Integer name() { return name; }
                  public Long code() { return code; }

                  public <U> Phantom<U, B> withId(String id) {
                      return new Phantom<>(id, name, code);
                  }
                  public <U extends Number> Phantom<U, B> withName(Integer name) {
                      return new Phantom<>(id, name, code);
                  }
                  public <U> Phantom<U, U> withCode(Long code) {
                      return new Phantom<>(id, name, code);
                  }
              }
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Phantom.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(phantom, packageInfo);

      assertThat(compilation).succeededWithoutWarnings();
      final String generated = "com.myapp.optics.PhantomLenses";
      assertGeneratedCodeContains(
          compilation, generated, "public static <A, B> Lens<Phantom<A, B>, String> id()");
      assertGeneratedCodeDoesNotContain(compilation, generated, "Lens<Phantom<A, B>, Integer>");
      assertGeneratedCodeDoesNotContain(compilation, generated, "Lens<Phantom<A, B>, Long>");
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("an inner class under a type parameter hiding an enclosing one is refused")
    void innerClassUnderATypeParameterHidingAnEnclosingOneIsRefused() {
      // Mid's T hides Outer's, so the message names Mid, not the imported class that sits in it.
      final var outer =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer<T> {
                  public class Mid<T> {
                      public final class In {
                          private final String id;
                          public In(String id) { this.id = id; }
                          public String id() { return id; }
                          public In withId(String id) { return new In(id); }
                      }
                  }
              }
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Outer.Mid.In.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(outer, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "type 'com.external.Outer.Mid.In' names the type parameter 'T' of 'Mid', which"
                  + " hides the 'T' of its enclosing class 'Outer'")
          .inFile(packageInfo);
      assertThat(compilation).hadErrorContaining("'Outer<T>.Mid<T>.In'");
    }

    @Test
    @DisplayName("a hidden type parameter is refused on a class read from a jar too")
    void hiddenTypeParameterIsRefusedOnAClassReadFromAJar(@TempDir Path tmp) throws IOException {
      // In a class file both names read back as the inner parameter, so no wither pairs there: the
      // refusal is asked before the pairing is, or this class would meet the generic one.
      final var library =
          javac()
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.external.Outer",
                      """
                      package com.external;

                      public class Outer<T> {
                          public final class In<T> {
                              private final T item;
                              public In(T item) { this.item = item; }
                              public T item() { return item; }
                              public In<T> withItem(T item) { return new In<>(item); }
                          }
                      }
                      """));
      assertThat(library).succeeded();
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Outer.In.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withClasspath(classpathWith(classDirectory(library, tmp)))
              .withProcessors(new ImportOpticsProcessor())
              .compile(packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "names the type parameter 'T' of 'In', which hides the 'T' of its enclosing class"
                  + " 'Outer'")
          .inFile(packageInfo);
    }

    @Test
    @DisplayName(
        "a class whose only wither returns another type is told what pairs, mutable or not")
    void classWhoseOnlyWitherReturnsAnotherTypeIsToldWhatPairs() {
      final var renamed =
          JavaFileObjects.forSourceString(
              "com.external.Renamed",
              """
              package com.external;

              public final class Renamed<T> {
                  private final String id;
                  public Renamed(String id) { this.id = id; }
                  public String id() { return id; }
                  public Renamed<String> withId(String id) { return new Renamed<>(id); }
              }
              """);
      final var settable =
          JavaFileObjects.forSourceString(
              "com.external.Settable",
              """
              package com.external;

              public final class Settable<T> {
                  private String id;
                  public Settable(String id) { this.id = id; }
                  public String id() { return id; }
                  public void setId(String id) { this.id = id; }
                  public Settable<String> withId(String id) { return new Settable<>(id); }
              }
              """);
      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics(
                  value = {com.external.Renamed.class, com.external.Settable.class},
                  allowMutable = true)
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(renamed, settable, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "type 'com.external.Renamed' is not a record, sealed interface, enum, or class with"
                  + " wither methods");
      assertThat(compilation)
          .hadErrorContaining("type 'com.external.Settable' is a mutable class without wither");
      // Both refusals say what would pair, since each class has a withX that does not.
      Assertions.assertThat(compilation.errors())
          .filteredOn(
              error ->
                  error
                      .getMessage(null)
                      .contains("A class has wither methods when a public 'withX' hands back"))
          .hasSize(2);
    }

    @Test
    @DisplayName("should reject mutable class without allowMutable flag")
    void shouldRejectMutableClassWithoutFlag() {
      final var mutableClass =
          JavaFileObjects.forSourceString(
              "com.external.MutablePerson",
              """
              package com.external;

              public class MutablePerson {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }

                  public MutablePerson withName(String name) {
                      MutablePerson copy = new MutablePerson();
                      copy.name = name;
                      return copy;
                  }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.MutablePerson.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(mutableClass, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has mutable fields").inFile(packageInfo);
    }

    @Test
    @DisplayName("should allow mutable class with allowMutable = true")
    void shouldAllowMutableClassWithFlag() {
      final var mutableClass =
          JavaFileObjects.forSourceString(
              "com.external.MutablePerson",
              """
              package com.external;

              public class MutablePerson {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }

                  public MutablePerson withName(String name) {
                      MutablePerson copy = new MutablePerson();
                      copy.name = name;
                      return copy;
                  }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics(value = {com.external.MutablePerson.class}, allowMutable = true)
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(mutableClass, packageInfo);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should reject unsupported type without withers")
    void shouldRejectUnsupportedType() {
      final var plainClass =
          JavaFileObjects.forSourceString(
              "com.external.PlainClass",
              """
              package com.external;

              public class PlainClass {
                  private String value;

                  public String getValue() { return value; }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.PlainClass.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(plainClass, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("not a record, sealed interface, enum, or class with wither methods")
          .inFile(packageInfo);
    }
  }

  @Nested
  @DisplayName("What/Why/Fix Diagnostics")
  class WhatWhyFixDiagnostics {

    @Test
    @DisplayName("should reject @ImportOptics on an enum with the what/why/fix message")
    void shouldRejectEnumPlacement() {
      final var enumSource =
          JavaFileObjects.forSourceString(
              "com.myapp.Colour",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({java.lang.String.class})
              public enum Colour { RED, GREEN }
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(enumSource);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@ImportOptics: cannot be applied to 'Colour'");
      assertThat(compilation)
          .hadErrorContaining("Move the annotation to a package-info.java or a type declaration");
    }

    @Test
    @DisplayName("should reject a mutable class without withers, prescribing the fix")
    void shouldRejectMutableClassWithoutWithers() {
      final var mutableClass =
          JavaFileObjects.forSourceString(
              "com.external.SetterOnly",
              """
              package com.external;

              public class SetterOnly {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.SetterOnly.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(mutableClass, packageInfo);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("is a mutable class without wither methods")
          .inFile(packageInfo);
      assertThat(compilation).hadErrorContaining("Define an OpticsSpec interface");
    }
  }

  @Nested
  @DisplayName("Target Package Configuration")
  class TargetPackageConfiguration {

    @Test
    @DisplayName("should use custom target package when specified")
    void shouldUseCustomTargetPackage() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Order",
              """
              package com.external;

              public record Order(String id, int quantity) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics(value = {com.external.Order.class}, targetPackage = "com.myapp.generated")
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.generated.OrderLenses").isNotNull();
    }

    @Test
    @DisplayName("should use annotated package when targetPackage is empty")
    void shouldUseAnnotatedPackageByDefault() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Item",
              """
              package com.external;

              public record Item(String name) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.custom.package-info",
              """
              @ImportOptics({com.external.Item.class})
              package com.myapp.custom;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.custom.ItemLenses").isNotNull();
    }
  }

  @Nested
  @DisplayName("Type-Level Annotation")
  class TypeLevelAnnotation {

    @Test
    @DisplayName("should support @ImportOptics on a class")
    void shouldSupportAnnotationOnClass() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Product",
              """
              package com.external;

              public record Product(String sku, double price) {}
              """);

      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.ProductOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({com.external.Product.class})
              public class ProductOptics {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerClass);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.ProductLenses").isNotNull();
    }
  }

  @Nested
  @DisplayName("Coverage Hardening")
  class CoverageHardening {

    @Test
    @DisplayName("should generate array traversal for record with array component")
    void shouldGenerateArrayTraversalForRecord() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Squad",
              """
              package com.external;

              public record Squad(String name, String[] tags) {}
              """);

      final var packageInfo =
          JavaFileObjects.forSourceString(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.external.Squad.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalRecord, packageInfo);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.optics.SquadLenses", "tagsTraversal");
    }

    @Test
    @DisplayName(
        "should treat interface with non-OpticsSpec super-interface as a class-list importer")
    void shouldTreatNonSpecInterfaceAsClassListImporter() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Widget",
              """
              package com.external;

              public record Widget(String label) {}
              """);

      final var importerInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.WidgetImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({com.external.Widget.class})
              public interface WidgetImporter extends java.io.Serializable {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerInterface);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.WidgetLenses").isNotNull();
    }

    @Test
    @DisplayName("should honour explicit targetPackage on a spec interface")
    void shouldHonourExplicitTargetPackageOnSpecInterface() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Temp",
              """
              package com.external;

              public class Temp {
                  private final int celsius;
                  public Temp(int celsius) { this.celsius = celsius; }
                  public int celsius() { return celsius; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.TempOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaConstructor;
              import com.external.Temp;

              @ImportOptics(targetPackage = "com.custom.gen")
              public interface TempOpticsSpec extends OpticsSpec<Temp> {

                  @ViaConstructor(parameterOrder = {"celsius"})
                  Lens<Temp, Integer> celsius();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.custom.gen.TempOptics").isNotNull();
    }

    @Test
    @DisplayName("should honour explicit targetPackage on a type-level importer")
    void shouldHonourExplicitTargetPackageOnTypeImporter() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Gadget",
              """
              package com.external;

              public record Gadget(String id) {}
              """);

      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.GadgetImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics(value = {com.external.Gadget.class}, targetPackage = "com.gen")
              public class GadgetImporter {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerClass);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.gen.GadgetLenses").isNotNull();
    }

    @Test
    @DisplayName("should skip non-@ImportOptics annotation mirrors when reading the class list")
    void shouldSkipOtherAnnotationMirrors() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Gizmo",
              """
              package com.external;

              public record Gizmo(String id) {}
              """);

      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.GizmoImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @Deprecated
              @ImportOptics({com.external.Gizmo.class})
              public class GizmoImporter {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerClass);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.GizmoLenses").isNotNull();
    }

    @Test
    @DisplayName("should skip non-value annotation elements when reading the class list")
    void shouldSkipNonValueAnnotationElements() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Doohickey",
              """
              package com.external;

              public record Doohickey(String id) {}
              """);

      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.DoohickeyImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics(allowMutable = true, value = {com.external.Doohickey.class})
              public class DoohickeyImporter {}
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importerClass);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.myapp.DoohickeyLenses").isNotNull();
    }

    @Test
    @DisplayName("should generate nothing when the class list is absent")
    void shouldGenerateNothingWhenClassListAbsent() {
      // Only targetPackage is written, so the element-values loop never finds "value"
      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.NoValueImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics(targetPackage = "com.gen2")
              public class NoValueImporter {}
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(importerClass);

      assertThat(compilation).succeeded();
      Assertions.assertThat(compilation.generatedSourceFiles()).isEmpty();
    }

    @Test
    @DisplayName("should ignore class-list entries whose type has no element (primitives)")
    void shouldIgnorePrimitiveClassListEntries() {
      final var importerClass =
          JavaFileObjects.forSourceString(
              "com.myapp.PrimitiveImporter",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({int.class})
              public class PrimitiveImporter {}
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(importerClass);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should return empty class list for element without @ImportOptics")
    void shouldReturnEmptyClassListWithoutImportOptics() {
      final var plainClass =
          JavaFileObjects.forSourceString(
              "com.myapp.Plain",
              """
              package com.myapp;

              public class Plain {}
              """);

      final class HarnessProcessor extends AbstractProcessor {
        private java.util.List<javax.lang.model.element.TypeElement> classList;

        @Override
        public java.util.Set<String> getSupportedAnnotationTypes() {
          return java.util.Set.of("*");
        }

        @Override
        public javax.lang.model.SourceVersion getSupportedSourceVersion() {
          return javax.lang.model.SourceVersion.RELEASE_25;
        }

        @Override
        public boolean process(
            java.util.Set<? extends javax.lang.model.element.TypeElement> annotations,
            RoundEnvironment roundEnv) {
          if (roundEnv.processingOver() || classList != null) {
            return false;
          }

          var plain = processingEnv.getElementUtils().getTypeElement("com.myapp.Plain");
          if (plain != null) {
            ImportOpticsProcessor target = new ImportOpticsProcessor();
            target.init(processingEnv);
            classList = target.getClassArrayFromAnnotation(plain);
          }

          return false;
        }
      }

      HarnessProcessor harness = new HarnessProcessor();
      var compilation = javac().withProcessors(harness).compile(plainClass);

      assertThat(compilation).succeeded();
      Assertions.assertThat(harness.classList).isEmpty();
    }
  }
}
