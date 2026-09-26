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

              public record Order(com.gen.Widget widget, int count) {}
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
      // Read in the first round, the wither could not be checked, and the generated class named
      // a type it could not see.
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
    @DisplayName("an interface extending a written spec base is refused once it resolves")
    void interfaceExtendingAWrittenSpecBaseIsRefused() {
      // Read in the first round, its supertype did not resolve, and nothing was said.
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
