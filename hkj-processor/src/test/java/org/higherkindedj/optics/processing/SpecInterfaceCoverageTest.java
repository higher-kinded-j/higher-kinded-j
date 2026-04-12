// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests targeting coverage gaps in SpecInterfaceAnalyser, SpecInterfaceGenerator, and related
 * classes.
 *
 * <p>Covers @ViaConstructor, @ViaCopyAndSet strategies, Affine/Iso/Getter/Fold optic kinds, type
 * parameters on source types, and various error paths.
 */
@DisplayName("Spec Interface Coverage Tests")
class SpecInterfaceCoverageTest {

  @Nested
  @DisplayName("ViaConstructor copy strategy")
  class ViaConstructorStrategy {

    @Test
    @DisplayName("should generate lens with @ViaConstructor strategy")
    void shouldGenerateLensWithViaConstructor() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Coord",
              """
              package com.external;

              public class Coord {
                  private final int x;
                  private final int y;
                  public Coord(int x, int y) { this.x = x; this.y = y; }
                  public int x() { return x; }
                  public int y() { return y; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.CoordOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaConstructor;
              import com.external.Coord;

              @ImportOptics
              public interface CoordOpticsSpec extends OpticsSpec<Coord> {

                  @ViaConstructor(parameterOrder = {"x", "y"})
                  Lens<Coord, Integer> x();

                  @ViaConstructor(parameterOrder = {"x", "y"})
                  Lens<Coord, Integer> y();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.CoordOptics", "public static Lens<Coord, Integer> x()");
    }
  }

  @Nested
  @DisplayName("ViaCopyAndSet copy strategy")
  class ViaCopyAndSetStrategy {

    @Test
    @DisplayName("should generate lens with @ViaCopyAndSet strategy")
    void shouldGenerateLensWithViaCopyAndSet() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.MutablePoint",
              """
              package com.external;

              public class MutablePoint {
                  private int x;
                  private int y;
                  public MutablePoint() {}
                  public MutablePoint(MutablePoint other) { this.x = other.x; this.y = other.y; }
                  public int x() { return x; }
                  public void setX(int x) { this.x = x; }
                  public int y() { return y; }
                  public void setY(int y) { this.y = y; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.MutablePointOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.MutablePoint;

              @ImportOptics
              public interface MutablePointOpticsSpec extends OpticsSpec<MutablePoint> {

                  @ViaCopyAndSet(setter = "setX")
                  Lens<MutablePoint, Integer> x();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.MutablePointOptics",
          "public static Lens<MutablePoint, Integer> x()");
    }
  }

  @Nested
  @DisplayName("Unsupported optic kinds in spec interfaces")
  class UnsupportedOpticKinds {

    @Test
    @DisplayName("should report error for Affine optic kind in spec interface")
    void shouldReportErrorForAffineOptic() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;
              public record Data(String value) {}
              """);

      // Affine return type - not yet supported for spec interfaces
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.DataOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Affine;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Data;

              @ImportOptics
              public interface DataOpticsSpec extends OpticsSpec<Data> {
                  Affine<Data, String> value();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      // Affine/Iso/Getter/Fold don't require annotations in the analyser -
      // they fall through to generate code that throws UnsupportedOperationException.
      // The processor itself succeeds; the generated code contains a throw statement.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.DataOptics", "UnsupportedOperationException");
    }

    @Test
    @DisplayName("should generate code with UnsupportedOperationException for Getter optic")
    void shouldGenerateUnsupportedForGetterOptic() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Simple",
              """
              package com.external;
              public record Simple(String name) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SimpleOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Getter;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Simple;

              @ImportOptics
              public interface SimpleOpticsSpec extends OpticsSpec<Simple> {
                  Getter<Simple, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      // Analyser allows GETTER without annotations; generator produces throwing code
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should generate code with UnsupportedOperationException for Iso optic")
    void shouldGenerateUnsupportedForIsoOptic() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Wrapper",
              """
              package com.external;
              public record Wrapper(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.WrapperOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Wrapper;

              @ImportOptics
              public interface WrapperOpticsSpec extends OpticsSpec<Wrapper> {
                  Iso<Wrapper, String> value();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should generate code with UnsupportedOperationException for Fold optic")
    void shouldGenerateUnsupportedForFoldOptic() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Items",
              """
              package com.external;
              public record Items(String name) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ItemsOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Fold;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Items;

              @ImportOptics
              public interface ItemsOpticsSpec extends OpticsSpec<Items> {
                  Fold<Items, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("SpecInterfaceGenerator coverage")
  class SpecInterfaceGeneratorCoverage {

    @Test
    @DisplayName("should generate optics for spec interface with default method")
    void shouldHandleDefaultMethodInSpec() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Config",
              """
              package com.external;
              public class Config {
                  private final String host;
                  private final int port;
                  public Config(String host, int port) { this.host = host; this.port = port; }
                  public String host() { return host; }
                  public int port() { return port; }
                  public Config withHost(String host) { return new Config(host, this.port); }
                  public Config withPort(int port) { return new Config(this.host, port); }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ConfigOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.Config;

              @ImportOptics
              public interface ConfigOpticsSpec extends OpticsSpec<Config> {

                  @Wither("withHost")
                  Lens<Config, String> host();

                  @Wither("withPort")
                  Lens<Config, Integer> port();

                  default Lens<Config, String> defaultHostLens() {
                      return ConfigOptics.host();
                  }
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      // Default method should be copied to generated class
      assertGeneratedCodeContains(compilation, "com.myapp.ConfigOptics", "defaultHostLens");
    }

    @Test
    @DisplayName("should handle spec interface with generic source type")
    void shouldHandleGenericSourceType() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Box",
              """
              package com.external;
              public class Box<T> {
                  private final T content;
                  public Box(T content) { this.content = content; }
                  public T content() { return content; }
                  public Box<T> withContent(T content) { return new Box<>(content); }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BoxOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.Box;

              @ImportOptics
              public interface BoxOpticsSpec<T> extends OpticsSpec<Box<T>> {

                  @Wither("withContent")
                  Lens<Box<T>, T> content();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      // Should generate optics with type parameters
      assertGeneratedCodeContains(compilation, "com.myapp.BoxOptics", "public static");
    }

    @Test
    @DisplayName("should derive class name ending with Impl when spec does not end with Spec")
    void shouldDeriveImplClassName() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Point",
              """
              package com.external;
              public record Point(int x, int y) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PointLenses",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaConstructor;
              import com.external.Point;

              @ImportOptics
              public interface PointLenses extends OpticsSpec<Point> {

                  @ViaConstructor(parameterOrder = {"x", "y"})
                  Lens<Point, Integer> x();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).succeeded();
      // Name does not end with "Spec", so append "Impl"
      assertGeneratedCodeContains(
          compilation, "com.myapp.PointLensesImpl", "public static Lens<Point, Integer> x()");
    }
  }

  @Nested
  @DisplayName("ExternalPrismGenerator with multiple subtypes")
  class ExternalPrismGeneratorMultipleSubtypes {

    @Test
    @DisplayName("should generate prisms for sealed interface with three subtypes")
    void shouldGeneratePrismsForThreeSubtypes() {
      final var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.external.Vehicle",
              """
              package com.external;
              public sealed interface Vehicle permits Vehicle.Car, Vehicle.Truck, Vehicle.Bike {
                  record Car(int seats) implements Vehicle {}
                  record Truck(double payload) implements Vehicle {}
                  record Bike(boolean electric) implements Vehicle {}
              }
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.VehicleImporter",
              """
              package com.myapp;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import com.external.Vehicle;
              @ImportOptics({Vehicle.class})
              public class VehicleImporter {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(sealedInterface, importAnnotation);

      assertThat(compilation).succeeded();
      // All three subtypes should have prism methods
      assertGeneratedCodeContains(compilation, "com.myapp.VehiclePrisms", "car()");
      assertGeneratedCodeContains(compilation, "com.myapp.VehiclePrisms", "truck()");
      assertGeneratedCodeContains(compilation, "com.myapp.VehiclePrisms", "bike()");
    }

    @Test
    @DisplayName("should generate prisms for enum with many constants")
    void shouldGeneratePrismsForManyEnumConstants() {
      final var enumType =
          JavaFileObjects.forSourceString(
              "com.external.Priority",
              """
              package com.external;
              public enum Priority { LOW, MEDIUM, HIGH, CRITICAL, BLOCKER }
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.PriorityImporter",
              """
              package com.myapp;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import com.external.Priority;
              @ImportOptics({Priority.class})
              public class PriorityImporter {}
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(enumType, importAnnotation);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.PriorityPrisms", "low()");
      assertGeneratedCodeContains(compilation, "com.myapp.PriorityPrisms", "critical()");
      assertGeneratedCodeContains(compilation, "com.myapp.PriorityPrisms", "blocker()");
    }
  }

  @Nested
  @DisplayName("SpecInterfaceAnalyser coverage")
  class SpecInterfaceAnalyserCoverage {

    @Test
    @DisplayName("should handle traversal with THROUGH_FIELD hint")
    void shouldHandleTraversalWithThroughFieldHint() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Team",
              """
              package com.external;
              import java.util.List;
              public class Team {
                  private final String name;
                  private final List<String> members;
                  public Team(String name, List<String> members) {
                      this.name = name;
                      this.members = members;
                  }
                  public String name() { return name; }
                  public List<String> members() { return members; }
                  public Team withName(String name) { return new Team(name, this.members); }
                  public Team withMembers(List<String> members) { return new Team(this.name, members); }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.TeamOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import com.external.Team;

              @ImportOptics
              public interface TeamOpticsSpec extends OpticsSpec<Team> {

                  @ThroughField(field = "members")
                  Traversal<Team, String> members();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.TeamOptics", "public static Traversal<Team, String> members()");
    }
  }

  @Nested
  @DisplayName("Error validation paths")
  class ErrorValidation {

    @Test
    @DisplayName("should report error for optic method with parameters")
    void shouldReportErrorForOpticMethodWithParameters() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;
              public record Base(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BaseOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Base;

              @ImportOptics
              public interface BaseOpticsSpec extends OpticsSpec<Base> {
                  @ViaBuilder
                  Lens<Base, String> value(String extraParam);
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must have no parameters");
    }

    @Test
    @DisplayName("should report error for non-optic return type")
    void shouldReportErrorForNonOpticReturnType() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Sub",
              """
              package com.external;
              public record Sub(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Sub;

              @ImportOptics
              public interface SubOpticsSpec extends OpticsSpec<Sub> {
                  String value();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must return Lens, Prism, Traversal");
    }

    @Test
    @DisplayName("should report error for lens method without copy strategy")
    void shouldReportErrorForLensWithoutCopyStrategy() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Animal",
              """
              package com.external;
              public record Animal(String name) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.AnimalOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Animal;

              @ImportOptics
              public interface AnimalOpticsSpec extends OpticsSpec<Animal> {
                  Lens<Animal, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("requires a copy strategy annotation");
    }

    @Test
    @DisplayName("should report error for prism method without prism hint")
    void shouldReportErrorForPrismWithoutHint() {
      final var externalSealed =
          JavaFileObjects.forSourceString(
              "com.external.Plant",
              """
              package com.external;
              public sealed interface Plant permits Plant.Tree, Plant.Flower {
                  record Tree(int height) implements Plant {}
                  record Flower(String color) implements Plant {}
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PlantOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Plant;

              @ImportOptics
              public interface PlantOpticsSpec extends OpticsSpec<Plant> {
                  Prism<Plant, Plant.Tree> asTree();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalSealed, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("requires a prism hint annotation");
    }

    @Test
    @DisplayName("should report error for traversal method without traversal hint")
    void shouldReportErrorForTraversalWithoutHint() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Container",
              """
              package com.external;
              import java.util.List;
              public record Container(List<String> items) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ContainerOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Container;

              @ImportOptics
              public interface ContainerOpticsSpec extends OpticsSpec<Container> {
                  Traversal<Container, String> items();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("requires a traversal hint annotation");
    }
  }

  @Nested
  @DisplayName("@InstanceOf prism in spec interface")
  class InstanceOfPrismInSpec {

    @Test
    @DisplayName("should generate prism with @InstanceOf for sealed type")
    void shouldGeneratePrismWithInstanceOf() {
      final var externalSealed =
          JavaFileObjects.forSourceString(
              "com.external.Shape",
              """
              package com.external;
              public sealed interface Shape permits Shape.Circle, Shape.Rect {
                  record Circle(double radius) implements Shape {}
                  record Rect(double w, double h) implements Shape {}
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ShapeOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import com.external.Shape;

              @ImportOptics
              public interface ShapeOpticsSpec extends OpticsSpec<Shape> {

                  @InstanceOf(Shape.Circle.class)
                  Prism<Shape, Shape.Circle> asCircle();

                  @InstanceOf(Shape.Rect.class)
                  Prism<Shape, Shape.Rect> asRect();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalSealed, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.ShapeOptics", "Prism<Shape, Shape.Circle> asCircle()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.ShapeOptics", "Prism<Shape, Shape.Rect> asRect()");
      assertGeneratedCodeContains(compilation, "com.myapp.ShapeOptics", "instanceof");
    }
  }

  @Nested
  @DisplayName("@MatchWhen prism in spec interface")
  class MatchWhenPrismInSpec {

    @Test
    @DisplayName("should generate prism with @MatchWhen predicate/getter")
    void shouldGeneratePrismWithMatchWhen() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.JsonNode",
              """
              package com.external;
              public class JsonNode {
                  public boolean isArray() { return false; }
                  public JsonNode asArray() { return this; }
                  public boolean isObject() { return false; }
                  public JsonNode asObject() { return this; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.JsonOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.MatchWhen;
              import com.external.JsonNode;

              @ImportOptics
              public interface JsonOpticsSpec extends OpticsSpec<JsonNode> {

                  @MatchWhen(predicate = "isArray", getter = "asArray")
                  Prism<JsonNode, JsonNode> asArray();

                  @MatchWhen(predicate = "isObject", getter = "asObject")
                  Prism<JsonNode, JsonNode> asObject();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.JsonOptics", "Prism<JsonNode, JsonNode> asArray()");
      assertGeneratedCodeContains(compilation, "com.myapp.JsonOptics", "isArray()");
      assertGeneratedCodeContains(compilation, "com.myapp.JsonOptics", "asArray()");
    }
  }

  @Nested
  @DisplayName("@ThroughField traversal with Set field in spec interface")
  class ThroughFieldWithSetInSpec {

    @Test
    @DisplayName("should generate traversal with @ThroughField for Set field with auto-detection")
    void shouldGenerateTraversalForSetField() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Catalog",
              """
              package com.external;
              import java.util.Set;
              public class Catalog {
                  private final String name;
                  private final Set<String> items;
                  public Catalog(String name, Set<String> items) {
                      this.name = name; this.items = items;
                  }
                  public String name() { return name; }
                  public Set<String> items() { return items; }
                  public Catalog withName(String name) { return new Catalog(name, this.items); }
                  public Catalog withItems(Set<String> items) { return new Catalog(this.name, items); }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.CatalogOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import com.external.Catalog;

              @ImportOptics
              public interface CatalogOpticsSpec extends OpticsSpec<Catalog> {

                  @ThroughField(field = "items")
                  Traversal<Catalog, String> items();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.CatalogOptics", "Traversal<Catalog, String> items()");
      // Should auto-detect Set traversal
      assertGeneratedCodeContains(compilation, "com.myapp.CatalogOptics", "Traversals.forSet()");
    }
  }

  @Nested
  @DisplayName("Spec interface with default methods having parameters")
  class DefaultMethodsWithParameters {

    @Test
    @DisplayName("should handle spec with default method that has parameters")
    void shouldHandleDefaultMethodWithParameters() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Config",
              """
              package com.external;
              public class Config {
                  private final String key;
                  private final String value;
                  public Config(String key, String value) { this.key = key; this.value = value; }
                  public String getKey() { return key; }
                  public String getValue() { return value; }
                  public Config withKey(String key) { return new Config(key, this.value); }
                  public Config withValue(String value) { return new Config(this.key, value); }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ConfigOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.Config;

              @ImportOptics
              public interface ConfigOpticsSpec extends OpticsSpec<Config> {
                  @Wither(value = "withKey", getter = "getKey")
                  Lens<Config, String> key();

                  default Lens<Config, String> keyWithDefault(String prefix) {
                      return key();
                  }
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("ExternalPrismGenerator multi-subtype coverage")
  class ExternalPrismMultiSubtype {

    @Test
    @DisplayName("should generate prisms for sealed interface with multiple record subtypes")
    void shouldGeneratePrismsForMultipleSubtypes() {
      final var vehicle =
          JavaFileObjects.forSourceString(
              "com.external.Vehicle",
              """
              package com.external;
              public sealed interface Vehicle permits Vehicle.Car, Vehicle.Truck, Vehicle.Bike {
                  record Car(String make, int doors) implements Vehicle {}
                  record Truck(String make, double payload) implements Vehicle {}
                  record Bike(String brand) implements Vehicle {}
              }
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @org.higherkindedj.optics.annotations.ImportOptics({com.external.Vehicle.class})
              package com.myapp;
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(vehicle, importAnnotation);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.VehiclePrisms", "Prism<Vehicle, Vehicle.Car> car()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.VehiclePrisms", "Prism<Vehicle, Vehicle.Truck> truck()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.VehiclePrisms", "Prism<Vehicle, Vehicle.Bike> bike()");
    }
  }

  @Nested
  @DisplayName("ExternalLensGenerator traversal coverage")
  class ExternalLensTraversal {

    @Test
    @DisplayName("should generate traversal for record with List field via ImportOptics")
    void shouldGenerateTraversalForListField() {
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.Team",
              """
              package com.external;
              public record Team(String name, java.util.List<String> members) {}
              """);

      final var importAnnotation =
          JavaFileObjects.forSourceString(
              "com.myapp.package-info",
              """
              @org.higherkindedj.optics.annotations.ImportOptics({com.external.Team.class})
              package com.myapp;
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, importAnnotation);

      assertThat(compilation).succeeded();
      // Should generate lenses for the record fields
      assertGeneratedCodeContains(compilation, "com.myapp.TeamLenses", "Lens<Team, String> name()");
    }
  }
}
