// Copyright (c) 2025 Magnus Smith
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
              return FocusPath.of(Lens.of(User::name, (source, newValue) -> new User(newValue, source.age())));
          }
          """;
      final String expectedAgeFocusPath =
          """
          public static FocusPath<User, Integer> age() {
              return FocusPath.of(Lens.of(User::age, (source, newValue) -> new User(source.name(), newValue)));
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
              return FocusPath.of(Lens.of(Address::street, (source, newValue) -> new Address(newValue, source.city(), source.postcode())));
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
      assertThat(compilation)
          .hadErrorContaining("@GenerateFocus annotation can only be applied to records");
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
              return FocusPath.of(Lens.of(Person::firstName, (source, newValue) -> new Person(newValue, source.lastName())));
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
              return FocusPath.of(Lens.of(Wrapper<T>::value, (source, newValue) -> new Wrapper<T>(newValue, source.label())));
          }
          """;
      final String expectedLabelFocusPath =
          """
          public static <T> FocusPath<Wrapper<T>, String> label() {
              return FocusPath.of(Lens.of(Wrapper<T>::label, (source, newValue) -> new Wrapper<T>(source.value(), newValue)));
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
