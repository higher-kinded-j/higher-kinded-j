// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code @ImportOptics} over types another annotation processor writes in the same build: an
 * importer naming one waits until it resolves, and is then processed as it would be had the type
 * been written by hand.
 */
@DisplayName("ImportOptics across rounds")
class ImportOpticsRoundsTest {

  /** A class with a wither, as Immutables writes one. */
  private static final String IMMUTABLE_FOO =
      """
      package com.gen;

      public final class ImmutableFoo {
          private final int x;

          public ImmutableFoo(int x) { this.x = x; }

          public int getX() { return x; }

          public ImmutableFoo withX(int x) { return new ImmutableFoo(x); }
      }
      """;

  private static final String WIDGET =
      """
      package com.gen;

      public record Widget(String name) {}
      """;

  /**
   * Writes the given sources in its first round, standing in for any processor whose output an
   * importer names.
   */
  private static final class TypeWriter extends AbstractProcessor {

    private final Map<String, String> sources;
    private boolean written;

    TypeWriter(Map<String, String> sources) {
      this.sources = sources;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!written) {
        written = true;
        new TreeMap<>(sources).forEach(this::write);
      }
      return false;
    }

    private void write(String type, String source) {
      try (Writer out = processingEnv.getFiler().createSourceFile(type).openWriter()) {
        out.write(source);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * The writer goes first: javac does not offer a round to a processor supporting every annotation
   * once an earlier processor has claimed that round's annotations.
   */
  private static Compilation compile(Map<String, String> written, JavaFileObject... sources) {
    return javac()
        .withProcessors(new TypeWriter(written), new ImportOpticsProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(sources);
  }

  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  @Nested
  @DisplayName("An importer waits for a type another processor writes")
  class Waits {

    @Test
    @DisplayName("a package listing a written class imports it once it resolves")
    void packageListingAWrittenClassImportsIt() {
      var packageInfo =
          source(
              "com.myapp.optics.package-info",
              """
              @ImportOptics({com.gen.Widget.class})
              package com.myapp.optics;

              import org.higherkindedj.optics.annotations.ImportOptics;
              """);
      var use =
          source(
              "com.myapp.Use",
              """
              package com.myapp;

              import com.gen.Widget;
              import org.higherkindedj.optics.Lens;

              class Use {
                  Lens<Widget, String> name = com.myapp.optics.WidgetLenses.name();
              }
              """);

      var compilation = compile(Map.of("com.gen.Widget", WIDGET), packageInfo, use);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.optics.WidgetLenses").isNotNull();
    }

    @Test
    @DisplayName("a listed class declaring a written type waits for it")
    void listedClassDeclaringAWrittenTypeWaits() {
      // The companion writes the component's type out, so it has to resolve first.
      var order =
          source(
              "com.myapp.Order",
              """
              package com.myapp;

              import com.gen.Widget;

              public record Order(Widget widget, int count) {}
              """);
      var importer =
          source(
              "com.myapp.OrderImports",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Order.class})
              class OrderImports {}
              """);

      var compilation = compile(Map.of("com.gen.Widget", WIDGET), order, importer);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.OrderLenses").isNotNull();
    }

    @Test
    @DisplayName("a spec over a written source type generates once it resolves")
    void specOverAWrittenSourceTypeGenerates() {
      var spec =
          source(
              "com.myapp.FooOpticsSpec",
              """
              package com.myapp;

              import com.gen.ImmutableFoo;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              interface FooOpticsSpec extends OpticsSpec<ImmutableFoo> {
                  @Wither(value = "withX", getter = "getX")
                  Lens<ImmutableFoo, Integer> x();
              }
              """);
      var use =
          source(
              "com.myapp.Use",
              """
              package com.myapp;

              import com.gen.ImmutableFoo;
              import org.higherkindedj.optics.Lens;

              class Use {
                  Lens<ImmutableFoo, Integer> x = FooOptics.x();
              }
              """);

      var compilation = compile(Map.of("com.gen.ImmutableFoo", IMMUTABLE_FOO), spec, use);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.FooOptics").isNotNull();
    }

    @Test
    @DisplayName("a spec over a written source type is checked against it once it resolves")
    void specOverAWrittenSourceTypeIsChecked() {
      // Read in the first round, the wither could not be checked, and the generated class would
      // name a type it cannot see.
      var spec =
          source(
              "com.myapp.FooOpticsSpec",
              """
              package com.myapp;

              import com.gen.ImmutableFoo;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              interface FooOpticsSpec extends OpticsSpec<ImmutableFoo> {
                  @Wither(value = "withY", getter = "getX")
                  Lens<ImmutableFoo, Integer> x();
              }
              """);

      var compilation = compile(Map.of("com.gen.ImmutableFoo", IMMUTABLE_FOO), spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("has no method 'withY' for the generated lens to call")
          .inFile(spec);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an @InstanceOf naming a written subtype waits for it")
    void instanceOfNamingAWrittenSubtypeWaits() {
      var shapes =
          source(
              "com.myapp.Shape",
              """
              package com.myapp;

              public abstract class Shape {
                  public abstract static class Circle extends Shape {}
              }
              """);
      var spec =
          source(
              "com.myapp.ShapeOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              interface ShapeOpticsSpec extends OpticsSpec<Shape> {
                  @InstanceOf(com.gen.ImmutableCircle.class)
                  Prism<Shape, Shape.Circle> circle();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.ImmutableCircle",
                  """
                  package com.gen;

                  public final class ImmutableCircle extends com.myapp.Shape.Circle {}
                  """),
              shapes,
              spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.ShapeOptics").isNotNull();
    }

    @Test
    @DisplayName("a spec's source type implementing a written interface is read once it resolves")
    void sourceTypeImplementingAWrittenInterfaceIsRead() {
      // The wither comes from the written interface, as a generated 'With' interface supplies it;
      // read in the first round, the spec would be refused for lacking it.
      var person =
          source(
              "com.myapp.Person",
              """
              package com.myapp;

              public record Person(String name) implements com.gen.PersonWith {}
              """);
      var spec =
          source(
              "com.myapp.PersonOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              interface PersonOpticsSpec extends OpticsSpec<Person> {
                  @Wither(value = "withName", getter = "name")
                  Lens<Person, String> name();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.PersonWith",
                  """
                  package com.gen;

                  import com.myapp.Person;

                  public interface PersonWith {
                      default Person withName(String name) { return new Person(name); }
                  }
                  """),
              person,
              spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PersonOptics").isNotNull();
    }

    @Test
    @DisplayName("a spec's source type is read through its source superclass once that resolves")
    void sourceTypeIsReadThroughItsSourceSuperclass() {
      // The wither reaches the source type through a superclass declared in source, which
      // implements the written interface supplying it.
      var person =
          source(
              "com.myapp.Person",
              """
              package com.myapp;

              abstract class PersonBase implements com.gen.PersonWith {}

              public final class Person extends PersonBase {
                  private final String name;

                  public Person(String name) { this.name = name; }

                  public String name() { return name; }
              }
              """);
      var spec =
          source(
              "com.myapp.PersonOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              interface PersonOpticsSpec extends OpticsSpec<Person> {
                  @Wither(value = "withName", getter = "name")
                  Lens<Person, String> name();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.PersonWith",
                  """
                  package com.gen;

                  import com.myapp.Person;

                  public interface PersonWith {
                      default Person withName(String name) { return new Person(name); }
                  }
                  """),
              person,
              spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PersonOptics").isNotNull();
    }

    @Test
    @DisplayName("a spec over a source type with a written builder is checked against it")
    void specOverASourceTypeWithAWrittenBuilderIsChecked() {
      // Read in the first round, the builder would not resolve, the setter could not be checked,
      // and the misspelling would surface inside the generated file.
      var person =
          source(
              "com.myapp.Person",
              """
              package com.myapp;

              public final class Person {
                  private final String name;

                  public Person(String name) { this.name = name; }

                  public String name() { return name; }

                  public com.gen.PersonBuilder toBuilder() { return new com.gen.PersonBuilder(name); }
              }
              """);
      var spec =
          source(
              "com.myapp.PersonOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              @ImportOptics
              interface PersonOpticsSpec extends OpticsSpec<Person> {
                  @ViaBuilder(setter = "nmae")
                  Lens<Person, String> name();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.PersonBuilder",
                  """
                  package com.gen;

                  import com.myapp.Person;

                  public final class PersonBuilder {
                      private String name;

                      public PersonBuilder(String name) { this.name = name; }

                      public PersonBuilder name(String name) { this.name = name; return this; }

                      public Person build() { return new Person(name); }
                  }
                  """),
              person,
              spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'nmae'").inFile(spec);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an inner class under an enclosing parameter bounded by a written type waits")
    void innerClassUnderABoundByAWrittenTypeWaits() {
      // The companion declares every type parameter in scope, the enclosing class's with its bound.
      var outer =
          source(
              "com.myapp.Outer",
              """
              package com.myapp;

              import com.gen.Widget;

              public class Outer<T extends Widget> {
                  public final class Inner {
                      private final int x;

                      public Inner(int x) { this.x = x; }

                      public int x() { return x; }

                      public Inner withX(int x) { return new Inner(x); }
                  }
              }
              """);
      var importer =
          source(
              "com.myapp.InnerImports",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Outer.Inner.class})
              class InnerImports {}
              """);

      var compilation = compile(Map.of("com.gen.Widget", WIDGET), outer, importer);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.InnerLenses").isNotNull();
    }

    @Test
    @DisplayName("a listed sealed interface permitting a written subtype waits for it")
    void sealedInterfacePermittingAWrittenSubtypeWaits() {
      // In one package the companion names the subtype correctly either way; across the packages
      // of a module it would not, which a single-module compilation cannot show. This holds the
      // read of the permits clause to generating once the subtype exists.
      var shape =
          source(
              "com.myapp.Shape",
              """
              package com.myapp;

              public sealed interface Shape permits Circle {}
              """);
      var importer =
          source(
              "com.myapp.ShapeImports",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Shape.class})
              class ShapeImports {}
              """);

      var compilation =
          compile(
              Map.of(
                  "com.myapp.Circle",
                  """
                  package com.myapp;

                  public record Circle(double radius) implements Shape {}
                  """),
              shape,
              importer);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.ShapePrisms").isNotNull();
    }

    @Test
    @DisplayName("an @InstanceOf target extending a written class waits for it")
    void instanceOfTargetExtendingAWrittenClassWaits() {
      // Read in the first round, the target would not be a subtype of the source type yet.
      var shapes =
          source(
              "com.myapp.Figure",
              """
              package com.myapp;

              public abstract class Figure {}

              final class Round extends com.gen.GenRound {}
              """);
      var spec =
          source(
              "com.myapp.FigureOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              interface FigureOpticsSpec extends OpticsSpec<Figure> {
                  @InstanceOf(Round.class)
                  Prism<Figure, Figure> round();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.GenRound",
                  """
                  package com.gen;

                  public abstract class GenRound extends com.myapp.Figure {}
                  """),
              shapes,
              spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.FigureOptics").isNotNull();
    }

    @Test
    @DisplayName("an interface extending a written spec base is refused once it resolves")
    void interfaceExtendingAWrittenSpecBaseIsRefused() {
      // Read in the first round, its supertype would not resolve, and nothing would be said.
      var importer =
          source(
              "com.myapp.BarOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics
              interface BarOptics extends com.gen.GeneratedBase {}
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.ImmutableFoo",
                  IMMUTABLE_FOO,
                  "com.gen.GeneratedBase",
                  """
                  package com.gen;

                  public interface GeneratedBase
                      extends org.higherkindedj.optics.annotations.OpticsSpec<ImmutableFoo> {}
                  """),
              importer);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'BarOptics' extends OpticsSpec only through 'GeneratedBase'. A spec interface is"
                  + " read from the OpticsSpec<S> it declares itself, and reaching it through"
                  + " another interface is not supported yet, so it is not read as a spec. Declare"
                  + " OpticsSpec<ImmutableFoo> on 'BarOptics' itself, beside 'GeneratedBase'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a builder bounded by several types waits for a base one of them extends")
    void builderBoundedBySeveralTypesWaitsForAWrittenBase() {
      // toBuilder() hands back a type bounded by a class and an interface, and the class's base is
      // written in the first round. The importer waits a round for it, so the misspelt setter is
      // refused at the spec with the setter the base declares offered, rather than written out to
      // fail in the generated file.
      var memo =
          source(
              "com.myapp.Memo",
              """
              package com.myapp;

              public final class Memo {
                  private final String text;

                  public Memo(String text) { this.text = text; }

                  public String text() { return text; }

                  @SuppressWarnings("unchecked")
                  public <B extends Base & Finishes> B toBuilder() { return (B) new Impl(text); }

                  public interface Finishes { Memo build(); }

                  public abstract static class Base extends com.gen.MemoBase {}

                  static final class Impl extends Base implements Finishes {
                      private String text;

                      Impl(String text) { this.text = text; }

                      @Override
                      public Impl note(String note) {
                          this.text = note;
                          return this;
                      }

                      @Override
                      public Memo build() { return new Memo(text); }
                  }
              }
              """);
      var spec =
          source(
              "com.myapp.MemoOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              @ImportOptics
              interface MemoOpticsSpec extends OpticsSpec<Memo> {
                  @ViaBuilder(setter = "nott")
                  Lens<Memo, String> text();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.MemoBase",
                  """
                  package com.gen;

                  public abstract class MemoBase {
                      public abstract MemoBase note(String note);
                  }
                  """),
              memo,
              spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "which have no method 'nott' the generated class in 'com.myapp' can call to set"
                  + " through.");
      assertThat(compilation).hadErrorContaining("Did you mean 'note'?");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a builder an inherited generic toBuilder() hands back waits for its written base")
    void builderAnInheritedGenericToBuilderHandsBackWaitsForItsWrittenBase() {
      // Memo inherits B toBuilder() from Parent<B> and fills B with MemoBuilder, whose base is
      // written in the first round. Read as Parent declares it, toBuilder() hands back B, bounded
      // by Object; read on Memo, it hands back MemoBuilder, so the importer waits a round for its
      // base, and the misspelt setter is refused at the spec with the setter the base declares
      // offered, rather than written out to fail in the generated file.
      var parent =
          source(
              "com.myapp.Parent",
              """
              package com.myapp;

              public abstract class Parent<B> {
                  public B toBuilder() { return builder(); }

                  protected abstract B builder();
              }
              """);
      var builder =
          source(
              "com.myapp.MemoBuilder",
              """
              package com.myapp;

              public final class MemoBuilder extends com.gen.MemoBase {
                  private String text;

                  MemoBuilder(String text) { this.text = text; }

                  @Override
                  public MemoBuilder note(String note) {
                      this.text = note;
                      return this;
                  }

                  public Memo build() { return new Memo(text); }
              }
              """);
      var memo =
          source(
              "com.myapp.Memo",
              """
              package com.myapp;

              public final class Memo extends Parent<MemoBuilder> {
                  private final String text;

                  public Memo(String text) { this.text = text; }

                  public String text() { return text; }

                  @Override
                  protected MemoBuilder builder() { return new MemoBuilder(text); }
              }
              """);
      var spec =
          source(
              "com.myapp.MemoOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              @ImportOptics
              interface MemoOpticsSpec extends OpticsSpec<Memo> {
                  @ViaBuilder(setter = "nott")
                  Lens<Memo, String> text();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.MemoBase",
                  """
                  package com.gen;

                  public abstract class MemoBase {
                      public abstract MemoBase note(String note);
                  }
                  """),
              parent,
              builder,
              memo,
              spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'MemoBuilder' has no method 'nott'").inFile(spec);
      assertThat(compilation).hadErrorContaining("Did you mean 'note'?");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("What an importer does not wait for")
  class DoesNotWait {

    @Test
    @DisplayName("a spec's static method naming the class generated from it does not hold it back")
    void staticMethodNamingTheGeneratedClassDoesNotHoldTheSpecBack() {
      // Waiting on it would wait for a class only this spec generates.
      var point =
          source(
              "com.external.Point",
              """
              package com.external;

              public final class Point {
                  private final int x;

                  public Point(int x) { this.x = x; }

                  public int getX() { return x; }

                  public Point withX(int x) { return new Point(x); }
              }
              """);
      var spec =
          source(
              "com.myapp.PointOpticsSpec",
              """
              package com.myapp;

              import com.external.Point;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              interface PointOpticsSpec extends OpticsSpec<Point> {
                  @Wither(value = "withX", getter = "getX")
                  Lens<Point, Integer> x();

                  static PointOptics generated() {
                      return null;
                  }
              }
              """);

      var compilation = compile(Map.of(), point, spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PointOptics").isNotNull();
    }

    @Test
    @DisplayName("a listed class's static method naming its companion does not hold it back")
    void listedClassStaticMethodNamingItsCompanionDoesNotHoldItBack() {
      var point =
          source(
              "com.myapp.Point",
              """
              package com.myapp;

              public record Point(int x) {
                  public static PointLenses lenses() {
                      return null;
                  }
              }
              """);
      var importer =
          source(
              "com.myapp.PointImports",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Point.class})
              class PointImports {}
              """);

      var compilation = compile(Map.of(), point, importer);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PointLenses").isNotNull();
    }

    @Test
    @DisplayName("a listed class's instance method naming its companion does not hold it back")
    void listedClassInstanceMethodNamingItsCompanionDoesNotHoldItBack() {
      var point =
          source(
              "com.myapp.Point",
              """
              package com.myapp;

              public record Point(int x) {
                  public PointLenses lenses() {
                      return null;
                  }
              }
              """);
      var importer =
          source(
              "com.myapp.PointImports",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Point.class})
              class PointImports {}
              """);

      var compilation = compile(Map.of(), point, importer);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PointLenses").isNotNull();
    }

    @Test
    @DisplayName("a class in the unnamed package naming its companion does not hold it back")
    void unnamedPackageClassNamingItsCompanionDoesNotHoldItBack() {
      // In the unnamed package a companion's qualified name is its simple name.
      var point =
          source(
              "Point",
              """
              public record Point(int x) {
                  public PointLenses lenses() {
                      return null;
                  }
              }
              """);
      var importer =
          source(
              "PointImports",
              """
              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Point.class})
              class PointImports {}
              """);

      var compilation = compile(Map.of(), point, importer);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("PointLenses").isNotNull();
    }

    @Test
    @DisplayName("a source type naming the class generated from its spec does not hold it back")
    void sourceTypeNamingItsSpecsClassDoesNotHoldItBack() {
      var point =
          source(
              "com.myapp.Point",
              """
              package com.myapp;

              public final class Point {
                  private final int x;

                  public Point(int x) { this.x = x; }

                  public int getX() { return x; }

                  public Point withX(int x) { return new Point(x); }

                  public com.myapp.optics.PointOptics optics() { return null; }
              }
              """);
      var spec =
          source(
              "com.myapp.PointOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics(targetPackage = "com.myapp.optics")
              public interface PointOpticsSpec extends OpticsSpec<Point> {
                  @Wither(value = "withX", getter = "getX")
                  Lens<Point, Integer> x();
              }
              """);

      var compilation = compile(Map.of(), point, spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.optics.PointOptics").isNotNull();
    }

    @Test
    @DisplayName("two importers whose classes name each other's companions do not hold each back")
    void importersNamingEachOthersCompanionsDoNotHoldEachOtherBack() {
      var records =
          source(
              "com.myapp.Pair",
              """
              package com.myapp;

              public record Pair(int a) {
                  public OtherLenses other() { return null; }
              }

              record Other(int b) {
                  public PairLenses pair() { return null; }
              }
              """);
      var importers =
          source(
              "com.myapp.Imports",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;

              @ImportOptics({Pair.class})
              class PairImports {}

              @ImportOptics({Other.class})
              class OtherImports {}
              """);

      var compilation = compile(Map.of(), records, importers);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PairLenses").isNotNull();
      assertThat(compilation).generatedSourceFile("com.myapp.OtherLenses").isNotNull();
    }

    @Test
    @DisplayName("a builder extending a written base is not refused for what it inherits")
    void builderExtendingAWrittenBaseIsNotRefused() {
      // The builder's base is written in the first round, so the importer waits a round for it,
      // and then reads the chain through the setter and build() the base declares.
      var person =
          source(
              "com.myapp.Person",
              """
              package com.myapp;

              public final class Person {
                  private final String name;

                  public Person(String name) { this.name = name; }

                  public String name() { return name; }

                  public Builder toBuilder() { return new Builder(name); }

                  public static final class Builder extends com.gen.PersonBuilderBase {
                      Builder(String name) { super(name); }
                  }
              }
              """);
      var spec =
          source(
              "com.myapp.PersonOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              @ImportOptics
              interface PersonOpticsSpec extends OpticsSpec<Person> {
                  @ViaBuilder
                  Lens<Person, String> name();
              }
              """);

      var compilation =
          compile(
              Map.of(
                  "com.gen.PersonBuilderBase",
                  """
                  package com.gen;

                  import com.myapp.Person;

                  public abstract class PersonBuilderBase {
                      private String name;

                      protected PersonBuilderBase(String name) { this.name = name; }

                      public Person.Builder name(String name) {
                          this.name = name;
                          return (Person.Builder) this;
                      }

                      public Person build() { return new Person(name); }
                  }
                  """),
              person,
              spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.myapp.PersonOptics").isNotNull();
    }

    @Test
    @DisplayName("a spec over a type that never resolves is javac's error, and generates nothing")
    void specOverATypeThatNeverResolvesIsJavacsError() {
      var spec =
          source(
              "com.myapp.GhostOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              interface GhostOpticsSpec extends OpticsSpec<Ghost> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Ghost, String> id();
              }
              """);

      var compilation = compile(Map.of(), spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("cannot find symbol")
          .inFile(spec)
          .onLineContaining("OpticsSpec<Ghost>");
      // Waiting to the end generates nothing in the last round, which javac would warn about.
      assertThat(compilation).hadWarningCount(0);
    }
  }
}
