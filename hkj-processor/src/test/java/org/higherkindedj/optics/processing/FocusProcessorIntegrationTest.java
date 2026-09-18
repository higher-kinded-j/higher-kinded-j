// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FocusProcessor Integration Tests")
public class FocusProcessorIntegrationTest {

  @Nested
  @DisplayName("Basic Code Generation")
  class BasicCodeGeneration {

    @Test
    @DisplayName("a raw type in a component or a bound compiles under -Werror")
    void rawTypesCompileUnderWerror() {
      // The path type, the setter lambda's inferred value and the record's type-parameter bounds
      // all land in the generated method.
      var nested =
          JavaFileObjects.forSourceString(
              "com.example.RawBag",
              """
              package com.example;
              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              @SuppressWarnings("rawtypes")
              public record RawBag(String id, Optional<List> contacts) {}
              """);
      var bounded =
          JavaFileObjects.forSourceString(
              "com.example.BoundedBag",
              """
              package com.example;
              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              @GenerateFocus
              @SuppressWarnings("rawtypes")
              public record BoundedBag<T extends List>(T value, String id) {}
              """);
      var compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(nested, bounded);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoundedBagFocus",
          "@SuppressWarnings(\"rawtypes\") public static <T extends List> FocusPath<BoundedBag<T>, T> value()");
    }

    @Test
    @DisplayName("should generate FocusPath methods for each record component")
    void shouldGenerateFocusPathMethodsForRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.User",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record User(String name, int age) {}
              """);

      final String expectedNameFocusPath =
          """
          public static FocusPath<User, String> name() {
              return FocusPath.of(Lens.of(User::name, (source, newValue) -> new User(newValue, source.age())), "name");
          }
          """;
      final String expectedAgeFocusPath =
          """
          public static FocusPath<User, Integer> age() {
              return FocusPath.of(Lens.of(User::age, (source, newValue) -> new User(source.name(), newValue)), "age");
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.UserFocus";
      assertGeneratedCodeContains(compilation, generatedClassName, expectedNameFocusPath);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedAgeFocusPath);
    }

    @Test
    @DisplayName("should generate Focus class with correct name suffix")
    void shouldGenerateFocusClassWithCorrectSuffix() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Address(String street, String city, String postcode) {}
              """);

      final String expectedStreetFocusPath =
          """
          public static FocusPath<Address, String> street() {
              return FocusPath.of(Lens.of(Address::street, (source, newValue) -> new Address(newValue, source.city(), source.postcode())), "street");
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.AddressFocus", expectedStreetFocusPath);
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("should report error when applied to non-record type")
    void shouldReportErrorForNonRecordType() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NotARecord",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public class NotARecord {
                  private String name;
              }
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@GenerateFocus: can only be applied to records");
    }
  }

  @Nested
  @DisplayName("Package Configuration")
  class PackageConfiguration {

    @Test
    @DisplayName("should generate in custom target package when specified")
    void shouldGenerateInCustomTargetPackage() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.model.Person",
              """
              package com.example.model;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(targetPackage = "com.example.optics")
              public record Person(String firstName, String lastName) {}
              """);

      final String expectedFirstNameFocusPath =
          """
          public static FocusPath<Person, String> firstName() {
              return FocusPath.of(Lens.of(Person::firstName, (source, newValue) -> new Person(newValue, source.lastName())), "firstName");
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.optics.PersonFocus", expectedFirstNameFocusPath);
    }
  }

  @Nested
  @DisplayName("Generic Record Support")
  class GenericRecordSupport {

    @Test
    @DisplayName("should generate FocusPath methods for generic record")
    void shouldGenerateFocusPathMethodsForGenericRecord() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Wrapper<T>(T value, String label) {}
              """);

      final String expectedValueFocusPath =
          """
          public static <T> FocusPath<Wrapper<T>, T> value() {
              return FocusPath.of(Lens.of(Wrapper<T>::value, (source, newValue) -> new Wrapper<T>(newValue, source.label())), "value");
          }
          """;
      final String expectedLabelFocusPath =
          """
          public static <T> FocusPath<Wrapper<T>, String> label() {
              return FocusPath.of(Lens.of(Wrapper<T>::label, (source, newValue) -> new Wrapper<T>(source.value(), newValue)), "label");
          }
          """;

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.WrapperFocus";
      assertGeneratedCodeContains(compilation, generatedClassName, expectedValueFocusPath);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedLabelFocusPath);
    }
  }
}
