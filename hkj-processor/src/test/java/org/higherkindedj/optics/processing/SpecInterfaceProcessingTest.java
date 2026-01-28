// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for spec interface processing in {@link ImportOpticsProcessor}.
 *
 * <p>These tests verify that the processor correctly generates optics from spec interfaces
 * extending {@code OpticsSpec<S>} with various copy strategies and hints.
 */
@DisplayName("Spec Interface Processing")
class SpecInterfaceProcessingTest {

  /** Helper to print compilation diagnostics for debugging. */
  private void printDiagnostics(Compilation compilation) {
    for (var diagnostic : compilation.diagnostics()) {
      System.err.println(diagnostic.getKind() + ": " + diagnostic.getMessage(null));
    }
  }

  /** Helper to check compilation and print errors if it fails. */
  private void assertCompilationSucceeded(Compilation compilation) {
    if (compilation.status() != Compilation.Status.SUCCESS) {
      printDiagnostics(compilation);
      fail("Compilation failed - see diagnostics above");
    }
  }

  /** Helper to check generated file exists and print diagnostics if not. */
  private void assertGeneratedFileExists(Compilation compilation, String fileName) {
    if (compilation.generatedSourceFile(fileName).isEmpty()) {
      System.err.println("Generated file " + fileName + " not found.");
      System.err.println("Generated files:");
      compilation.generatedSourceFiles().forEach(f -> System.err.println("  - " + f.getName()));
      printDiagnostics(compilation);
      fail("Generated file not found: " + fileName);
    }
  }

  @Nested
  @DisplayName("Basic Spec Interface")
  class BasicSpecInterface {

    @Test
    @DisplayName("should recognize spec interface and generate file")
    void shouldRecognizeSpecInterfaceAndGenerateFile() {
      // Minimal test to verify spec interface detection works
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.SimpleRecord",
              """
              package com.external;

              public record SimpleRecord(String value) {}
              """);

      // Very simple spec interface - just extends OpticsSpec, no methods
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SimpleRecordOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.SimpleRecord;

              @ImportOptics
              public interface SimpleRecordOptics extends OpticsSpec<SimpleRecord> {
                  // No methods - just test that the file is generated
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      // Print all diagnostics for debugging FIRST
      System.out.println("=== Compilation Status: " + compilation.status() + " ===");
      System.out.println("=== Compilation Diagnostics ===");
      for (var diagnostic : compilation.diagnostics()) {
        System.out.println(diagnostic.getKind() + ": " + diagnostic.getMessage(null));
      }

      // Only try to access generated files if compilation succeeded
      if (compilation.status() == Compilation.Status.SUCCESS) {
        System.out.println("\n=== Generated Files ===");
        compilation.generatedSourceFiles().forEach(f -> System.out.println("  " + f.getName()));
      }

      assertCompilationSucceeded(compilation);
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      assertGeneratedFileExists(compilation, "com.myapp.SimpleRecordOpticsImpl");
    }

    @Test
    @DisplayName("should generate optics from spec interface extending OpticsSpec")
    void shouldGenerateOpticsFromSpecInterface() {
      // External class with builder pattern
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Person",
              """
              package com.external;

              public final class Person {
                  private final String name;
                  private final int age;

                  private Person(Builder builder) {
                      this.name = builder.name;
                      this.age = builder.age;
                  }

                  public String name() { return name; }
                  public int age() { return age; }

                  public Builder toBuilder() {
                      return new Builder().name(name).age(age);
                  }

                  public static Builder builder() { return new Builder(); }

                  public static class Builder {
                      private String name;
                      private int age;

                      public Builder name(String name) { this.name = name; return this; }
                      public Builder age(int age) { this.age = age; return this; }
                      public Person build() { return new Person(this); }
                  }
              }
              """);

      // Spec interface
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PersonOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Person;

              @ImportOptics
              public interface PersonOptics extends OpticsSpec<Person> {

                  @ViaBuilder
                  Lens<Person, String> name();

                  @ViaBuilder
                  Lens<Person, Integer> age();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertCompilationSucceeded(compilation);
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      assertGeneratedFileExists(compilation, "com.myapp.PersonOpticsImpl");

      final String expectedNameLens = "public static Lens<Person, String> name()";
      final String expectedAgeLens = "public static Lens<Person, Integer> age()";

      assertGeneratedCodeContains(compilation, "com.myapp.PersonOpticsImpl", expectedNameLens);
      assertGeneratedCodeContains(compilation, "com.myapp.PersonOpticsImpl", expectedAgeLens);
    }
  }

  @Nested
  @DisplayName("@ViaBuilder Copy Strategy")
  class ViaBuilderCopyStrategy {

    @Test
    @DisplayName("should generate lens using builder pattern with defaults")
    void shouldGenerateLensWithBuilderDefaults() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Config",
              """
              package com.external;

              public final class Config {
                  private final String host;
                  private final int port;

                  private Config(Builder builder) {
                      this.host = builder.host;
                      this.port = builder.port;
                  }

                  public String host() { return host; }
                  public int port() { return port; }

                  public Builder toBuilder() {
                      return new Builder().host(host).port(port);
                  }

                  public static class Builder {
                      private String host;
                      private int port;

                      public Builder host(String host) { this.host = host; return this; }
                      public Builder port(int port) { this.port = port; return this; }
                      public Config build() { return new Config(this); }
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ConfigOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Config;

              @ImportOptics
              public interface ConfigOptics extends OpticsSpec<Config> {

                  @ViaBuilder
                  Lens<Config, String> host();

                  @ViaBuilder
                  Lens<Config, Integer> port();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();

      // Verify builder pattern code is generated
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedBuilderUsage = "source.toBuilder().host(newValue).build()";
      assertGeneratedCodeContains(compilation, "com.myapp.ConfigOpticsImpl", expectedBuilderUsage);
    }

    @Test
    @DisplayName("should generate lens with custom builder method names")
    void shouldGenerateLensWithCustomBuilderMethods() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Request",
              """
              package com.external;

              public final class Request {
                  private final String url;

                  private Request(Builder builder) { this.url = builder.url; }

                  public String getUrl() { return url; }

                  public Builder newBuilder() { return new Builder().withUrl(url); }

                  public static class Builder {
                      private String url;

                      public Builder withUrl(String url) { this.url = url; return this; }
                      public Request create() { return new Request(this); }
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.RequestOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Request;

              @ImportOptics
              public interface RequestOptics extends OpticsSpec<Request> {

                  @ViaBuilder(getter = "getUrl", toBuilder = "newBuilder", setter = "withUrl", build = "create")
                  Lens<Request, String> url();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();

      // Verify custom method names are used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedCustomBuilder = "source.newBuilder().withUrl(newValue).create()";
      assertGeneratedCodeContains(
          compilation, "com.myapp.RequestOpticsImpl", expectedCustomBuilder);
    }
  }

  @Nested
  @DisplayName("@Wither Copy Strategy")
  class WitherCopyStrategy {

    @Test
    @DisplayName("should generate lens using wither method")
    void shouldGenerateLensWithWither() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.LocalDate",
              """
              package com.external;

              public final class LocalDate {
                  private final int year;
                  private final int month;
                  private final int day;

                  public LocalDate(int year, int month, int day) {
                      this.year = year;
                      this.month = month;
                      this.day = day;
                  }

                  public int getYear() { return year; }
                  public int getMonthValue() { return month; }
                  public int getDayOfMonth() { return day; }

                  public LocalDate withYear(int year) {
                      return new LocalDate(year, month, day);
                  }

                  public LocalDate withMonth(int month) {
                      return new LocalDate(year, month, day);
                  }

                  public LocalDate withDayOfMonth(int day) {
                      return new LocalDate(year, month, day);
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.LocalDateOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.LocalDate;

              @ImportOptics
              public interface LocalDateOptics extends OpticsSpec<LocalDate> {

                  @Wither(value = "withYear", getter = "getYear")
                  Lens<LocalDate, Integer> year();

                  @Wither(value = "withMonth", getter = "getMonthValue")
                  Lens<LocalDate, Integer> monthValue();

                  @Wither(value = "withDayOfMonth", getter = "getDayOfMonth")
                  Lens<LocalDate, Integer> dayOfMonth();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();

      // Verify wither method is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedWitherUsage = "source.withYear(newValue)";
      assertGeneratedCodeContains(
          compilation, "com.myapp.LocalDateOpticsImpl", expectedWitherUsage);
    }
  }

  @Nested
  @DisplayName("@InstanceOf Prism Hint")
  class InstanceOfPrismHint {

    @Test
    @DisplayName("should generate prism using instanceof pattern matching")
    void shouldGeneratePrismWithInstanceOf() {
      final var sealedHierarchy =
          JavaFileObjects.forSourceString(
              "com.external.PaymentMethod",
              """
              package com.external;

              public sealed interface PaymentMethod permits CreditCard, BankTransfer {}
              """);

      final var creditCard =
          JavaFileObjects.forSourceString(
              "com.external.CreditCard",
              """
              package com.external;

              public record CreditCard(String number, String expiry) implements PaymentMethod {}
              """);

      final var bankTransfer =
          JavaFileObjects.forSourceString(
              "com.external.BankTransfer",
              """
              package com.external;

              public record BankTransfer(String iban) implements PaymentMethod {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PaymentMethodOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import com.external.PaymentMethod;
              import com.external.CreditCard;
              import com.external.BankTransfer;

              @ImportOptics
              public interface PaymentMethodOptics extends OpticsSpec<PaymentMethod> {

                  @InstanceOf(CreditCard.class)
                  Prism<PaymentMethod, CreditCard> creditCard();

                  @InstanceOf(BankTransfer.class)
                  Prism<PaymentMethod, BankTransfer> bankTransfer();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(sealedHierarchy, creditCard, bankTransfer, specInterface);

      assertThat(compilation).succeeded();

      // Verify instanceof pattern matching is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedInstanceOf = "source instanceof CreditCard";
      assertGeneratedCodeContains(
          compilation, "com.myapp.PaymentMethodOpticsImpl", expectedInstanceOf);
    }

    @Test
    @DisplayName("should reject @InstanceOf with non-subtype target")
    void shouldRejectNonSubtypeInstanceOf() {
      final var baseClass =
          JavaFileObjects.forSourceString(
              "com.external.Animal",
              """
              package com.external;

              public class Animal {}
              """);

      final var unrelatedClass =
          JavaFileObjects.forSourceString(
              "com.external.Plant",
              """
              package com.external;

              public class Plant {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.AnimalOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import com.external.Animal;
              import com.external.Plant;

              @ImportOptics
              public interface AnimalOptics extends OpticsSpec<Animal> {

                  @InstanceOf(Plant.class)  // Plant is not a subtype of Animal
                  Prism<Animal, Plant> plant();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(baseClass, unrelatedClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("not a subtype");
    }
  }

  @Nested
  @DisplayName("@MatchWhen Prism Hint")
  class MatchWhenPrismHint {

    @Test
    @DisplayName("should generate prism using predicate and getter")
    void shouldGeneratePrismWithMatchWhen() {
      final var jsonNode =
          JavaFileObjects.forSourceString(
              "com.external.JsonNode",
              """
              package com.external;

              public abstract class JsonNode {
                  public abstract boolean isArray();
                  public abstract boolean isObject();
                  public ArrayNode asArray() { throw new IllegalStateException(); }
                  public ObjectNode asObject() { throw new IllegalStateException(); }
              }
              """);

      final var arrayNode =
          JavaFileObjects.forSourceString(
              "com.external.ArrayNode",
              """
              package com.external;

              public class ArrayNode extends JsonNode {
                  @Override public boolean isArray() { return true; }
                  @Override public boolean isObject() { return false; }
                  @Override public ArrayNode asArray() { return this; }
              }
              """);

      final var objectNode =
          JavaFileObjects.forSourceString(
              "com.external.ObjectNode",
              """
              package com.external;

              public class ObjectNode extends JsonNode {
                  @Override public boolean isArray() { return false; }
                  @Override public boolean isObject() { return true; }
                  @Override public ObjectNode asObject() { return this; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.JsonNodeOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.MatchWhen;
              import com.external.JsonNode;
              import com.external.ArrayNode;
              import com.external.ObjectNode;

              @ImportOptics
              public interface JsonNodeOptics extends OpticsSpec<JsonNode> {

                  @MatchWhen(predicate = "isArray", getter = "asArray")
                  Prism<JsonNode, ArrayNode> array();

                  @MatchWhen(predicate = "isObject", getter = "asObject")
                  Prism<JsonNode, ObjectNode> object();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(jsonNode, arrayNode, objectNode, specInterface);

      assertThat(compilation).succeeded();

      // Verify predicate/getter pattern is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedPredicateUsage = "source.isArray()";
      assertGeneratedCodeContains(
          compilation, "com.myapp.JsonNodeOpticsImpl", expectedPredicateUsage);
    }
  }

  @Nested
  @DisplayName("@TraverseWith Traversal Hint")
  class TraverseWithHint {

    @Test
    @DisplayName("should generate traversal using explicit reference")
    void shouldGenerateTraversalWithReference() {
      final var team =
          JavaFileObjects.forSourceString(
              "com.external.Team",
              """
              package com.external;

              import java.util.List;

              public record Team(String name, List<String> members) {}
              """);

      // Stub Traversals class with a method that returns the correct type
      // In real usage, this would be a pre-composed traversal
      final var traversalsStub =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.optics.Traversals",
              """
              package org.higherkindedj.optics;

              public final class Traversals {
                  private Traversals() {}
                  public static <A> Traversal<java.util.List<A>, A> list() { return null; }

                  // Pre-composed traversal for Team.members - realistic usage pattern
                  public static Traversal<com.external.Team, String> teamMembers() { return null; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.TeamOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.TraverseWith;
              import com.external.Team;

              @ImportOptics
              public interface TeamOptics extends OpticsSpec<Team> {

                  @TraverseWith("org.higherkindedj.optics.Traversals.teamMembers()")
                  Traversal<Team, String> eachMember();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(team, traversalsStub, specInterface);

      assertThat(compilation).succeeded();

      // Verify traversal reference is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedTraversalRef = "org.higherkindedj.optics.Traversals.teamMembers()";
      assertGeneratedCodeContains(compilation, "com.myapp.TeamOpticsImpl", expectedTraversalRef);
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("should reject lens method without copy strategy annotation")
    void shouldRejectLensWithoutCopyStrategy() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Simple",
              """
              package com.external;

              public record Simple(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SimpleOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Simple;

              @ImportOptics
              public interface SimpleOptics extends OpticsSpec<Simple> {

                  // Missing copy strategy annotation
                  Lens<Simple, String> value();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("requires a copy strategy annotation");
    }

    @Test
    @DisplayName("should reject prism method without prism hint annotation")
    void shouldRejectPrismWithoutHint() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base {}
              """);

      final var subclass =
          JavaFileObjects.forSourceString(
              "com.external.Sub",
              """
              package com.external;

              public class Sub extends Base {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BaseOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Base;
              import com.external.Sub;

              @ImportOptics
              public interface BaseOptics extends OpticsSpec<Base> {

                  // Missing prism hint annotation
                  Prism<Base, Sub> sub();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalClass, subclass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("requires a prism hint annotation");
    }

    @Test
    @DisplayName("should reject method with invalid return type")
    void shouldRejectInvalidReturnType() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;

              public record Data(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.DataOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Data;

              @ImportOptics
              public interface DataOptics extends OpticsSpec<Data> {

                  // Invalid return type - not an optic
                  @ViaBuilder
                  String value();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must return Lens, Prism, Traversal");
    }

    @Test
    @DisplayName("should reject method with parameters")
    void shouldRejectMethodWithParameters() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Item",
              """
              package com.external;

              public record Item(String name) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ItemOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Item;

              @ImportOptics
              public interface ItemOptics extends OpticsSpec<Item> {

                  // Invalid - optic methods should have no parameters
                  @ViaBuilder
                  Lens<Item, String> name(String unused);
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must have no parameters");
    }
  }
}
