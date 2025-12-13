// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link PathProcessor}.
 *
 * <p>Tests verify that the processor correctly generates Path bridge classes for service interfaces
 * annotated with {@code @GeneratePathBridge} and {@code @PathVia}.
 */
@DisplayName("PathProcessor Integration Tests")
class PathProcessorIntegrationTest {

  @Nested
  @DisplayName("Basic Code Generation")
  class BasicCodeGeneration {

    @Test
    @DisplayName("generates bridge class for interface with Optional method")
    void shouldGenerateBridgeForOptionalMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.UserService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface UserService {

                  @PathVia
                  Optional<String> findById(Long id);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.UserServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public final class UserServicePaths");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "private final UserService delegate");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public UserServicePaths(UserService delegate)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public OptionalPath<String> findById(Long id)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.optional(delegate.findById(id))");
    }

    @Test
    @DisplayName("generates bridge class for interface with Either method")
    void shouldGenerateBridgeForEitherMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.OrderService",
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface OrderService {

                  @PathVia
                  Either<String, Integer> processOrder(String orderId);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.OrderServicePaths";
      assertGeneratedCodeContains(
          compilation,
          generatedClassName,
          "public EitherPath<String, Integer> processOrder(String orderId)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.either(delegate.processOrder(orderId))");
    }

    @Test
    @DisplayName("generates bridge class for interface with Maybe method")
    void shouldGenerateBridgeForMaybeMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.CacheService",
              """
              package com.example;

              import org.higherkindedj.hkt.maybe.Maybe;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface CacheService {

                  @PathVia
                  Maybe<String> get(String key);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CacheServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public MaybePath<String> get(String key)");
      assertGeneratedCodeContains(compilation, generatedClassName, "Path.maybe(delegate.get(key))");
    }

    @Test
    @DisplayName("generates bridge class for interface with Try method")
    void shouldGenerateBridgeForTryMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.FileService",
              """
              package com.example;

              import org.higherkindedj.hkt.trymonad.Try;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface FileService {

                  @PathVia
                  Try<String> readFile(String path);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.FileServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public TryPath<String> readFile(String path)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.tryPath(delegate.readFile(path))");
    }

    @Test
    @DisplayName(
        "generates bridge class for interface with Validated method (adds Semigroup param)")
    void shouldGenerateBridgeForValidatedMethod() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ValidationService",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;
              import java.util.List;

              @GeneratePathBridge
              public interface ValidationService {

                  @PathVia
                  Validated<List<String>, String> validate(String input);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.ValidationServicePaths";
      // Validated methods should get an additional Semigroup parameter
      assertGeneratedCodeContains(
          compilation,
          generatedClassName,
          "ValidationPath<List<String>, String> validate(String input, Semigroup<List<String>> semigroup)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "Path.validated(delegate.validate(input), semigroup)");
    }
  }

  @Nested
  @DisplayName("Annotation Options")
  class AnnotationOptions {

    @Test
    @DisplayName("uses custom method name from @PathVia")
    void shouldUseCustomMethodName() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DataService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface DataService {

                  @PathVia(name = "fetchDataPath")
                  Optional<String> fetchData(String id);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.DataServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "public OptionalPath<String> fetchDataPath(String id)");
    }

    @Test
    @DisplayName("uses custom suffix from @GeneratePathBridge")
    void shouldUseCustomSuffix() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.ApiService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge(suffix = "PathBridge")
              public interface ApiService {

                  @PathVia
                  Optional<String> getData();
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Should use custom suffix
      assertTrue(
          compilation.generatedSourceFile("com.example.ApiServicePathBridge").isPresent(),
          "Expected generated file ApiServicePathBridge");
    }

    @Test
    @DisplayName("includes documentation from @PathVia")
    void shouldIncludeDocumentation() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DocService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface DocService {

                  @PathVia(doc = "Fetches user by ID with Path semantics")
                  Optional<String> fetchUser(Long id);
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.DocServicePaths";
      assertGeneratedCodeContainsRaw(
          compilation, generatedClassName, "Fetches user by ID with Path semantics");
    }
  }

  @Nested
  @DisplayName("Multiple Methods")
  class MultipleMethods {

    @Test
    @DisplayName("generates bridge methods for multiple @PathVia methods")
    void shouldGenerateMultipleBridgeMethods() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.CompleteService",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.maybe.Maybe;
              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface CompleteService {

                  @PathVia
                  Optional<String> findOptional(String id);

                  @PathVia
                  Either<String, Integer> processEither(int value);

                  @PathVia
                  Maybe<Double> getMaybe();

                  // Method without @PathVia should NOT be included
                  void doSomething();
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CompleteServicePaths";
      assertGeneratedCodeContains(
          compilation, generatedClassName, "OptionalPath<String> findOptional(String id)");
      assertGeneratedCodeContains(
          compilation, generatedClassName, "EitherPath<String, Integer> processEither(int value)");
      assertGeneratedCodeContains(compilation, generatedClassName, "MaybePath<Double> getMaybe()");
      // doSomething should NOT be in the generated code
      assertGeneratedCodeDoesNotContain(compilation, generatedClassName, "doSomething");
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("fails when @GeneratePathBridge is applied to a class")
    void shouldFailForClass() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.NotAnInterface",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;

              @GeneratePathBridge
              public class NotAnInterface {}
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@GeneratePathBridge can only be applied to interfaces");
    }

    @Test
    @DisplayName("fails when @PathVia method has unsupported return type")
    void shouldFailForUnsupportedReturnType() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.BadService",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
              import org.higherkindedj.hkt.effect.annotation.PathVia;

              @GeneratePathBridge
              public interface BadService {

                  @PathVia
                  String notAnEffectType();
              }
              """);

      var compilation = javac().withProcessors(new PathProcessor()).compile(sourceFile);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Unsupported return type for @PathVia");
    }
  }

  // Helper methods

  private static void assertGeneratedCodeContains(
      Compilation compilation, String generatedFileName, String expectedCode) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      String normalisedActual = normaliseCode(actualGeneratedCode);
      String normalisedExpected = normaliseCode(expectedCode);

      assertTrue(
          normalisedActual.contains(normalisedExpected),
          String.format(
              "Expected generated code to contain:%n---%n%s%n---%nBut was:%n---%n%s%n---",
              normalisedExpected, normalisedActual));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static void assertGeneratedCodeContainsRaw(
      Compilation compilation, String generatedFileName, String expectedText) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();

      assertTrue(
          actualGeneratedCode.contains(expectedText),
          String.format(
              "Expected generated code to contain (raw):%n---%n%s%n---%nBut was:%n---%n%s%n---",
              expectedText, actualGeneratedCode));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static void assertGeneratedCodeDoesNotContain(
      Compilation compilation, String generatedFileName, String unexpectedCode) {
    Optional<JavaFileObject> generatedSourceFile =
        compilation.generatedSourceFile(generatedFileName);

    if (generatedSourceFile.isEmpty()) {
      fail("Generated source file not found: " + generatedFileName);
      return;
    }

    try {
      String actualGeneratedCode = generatedSourceFile.get().getCharContent(true).toString();
      String normalisedActual = normaliseCode(actualGeneratedCode);
      String normalisedUnexpected = normaliseCode(unexpectedCode);

      assertTrue(
          !normalisedActual.contains(normalisedUnexpected),
          String.format(
              "Expected generated code NOT to contain:%n---%n%s%n---%nBut it did:%n---%n%s%n---",
              normalisedUnexpected, normalisedActual));
    } catch (IOException e) {
      fail("Could not read content from generated file: " + generatedFileName, e);
    }
  }

  private static String normaliseCode(String code) {
    String normalised =
        code.replaceAll("package [\\w.]+;\\s*", "")
            .replaceAll("import [\\w.]+;\\s*", "")
            .replaceAll("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", "")
            .replaceAll("//.*", "");

    // Normalise fully qualified class names to simple names
    normalised =
        normalised
            .replaceAll("java\\.util\\.Optional", "Optional")
            .replaceAll("java\\.util\\.List", "List")
            .replaceAll("java\\.util\\.Objects", "Objects")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.Path", "Path")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.MaybePath", "MaybePath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.EitherPath", "EitherPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.TryPath", "TryPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.ValidationPath", "ValidationPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.OptionalPath", "OptionalPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.effect\\.IOPath", "IOPath")
            .replaceAll("org\\.higherkindedj\\.hkt\\.Semigroup", "Semigroup")
            .replaceAll("org\\.higherkindedj\\.hkt\\.either\\.Either", "Either")
            .replaceAll("org\\.higherkindedj\\.hkt\\.maybe\\.Maybe", "Maybe")
            .replaceAll("org\\.higherkindedj\\.hkt\\.trymonad\\.Try", "Try")
            .replaceAll("org\\.higherkindedj\\.hkt\\.validated\\.Validated", "Validated")
            // Test-specific types
            .replaceAll("com\\.example\\.UserService", "UserService")
            .replaceAll("com\\.example\\.OrderService", "OrderService")
            .replaceAll("com\\.example\\.CacheService", "CacheService")
            .replaceAll("com\\.example\\.FileService", "FileService")
            .replaceAll("com\\.example\\.ValidationService", "ValidationService")
            .replaceAll("com\\.example\\.DataService", "DataService")
            .replaceAll("com\\.example\\.ApiService", "ApiService")
            .replaceAll("com\\.example\\.DocService", "DocService")
            .replaceAll("com\\.example\\.CompleteService", "CompleteService");

    return normalised.replaceAll("\\s+", "").trim();
  }
}
