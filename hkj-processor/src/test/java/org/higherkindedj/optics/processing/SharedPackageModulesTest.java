// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Modules compiled together that declare the same package. javac writes a generated class into the
 * module declaring its package and cannot choose between two, so it refuses every file a processor
 * writes into that package, as it refuses a name already taken; each processor reports the shared
 * package rather than a collision.
 */
@DisplayName("Generated files in a package more than one module declares")
class SharedPackageModulesTest {

  private static final String PKG = "com.example.shared";

  private static final String SHARED =
      ": its package '"
          + PKG
          + "' is declared by more than one module being compiled ('billing', 'shipping').";

  /** One annotated type for each processor that writes into the annotated type's package. */
  private static final List<JavaFileObject> SOURCES =
      List.of(
          source("Contact", "public record Contact(String name) {}"),
          source("ContactDto", "public record ContactDto(String name) {}"),
          source(
              "ContactMapping",
              """
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ContactMapping extends MappingSpec<Contact, ContactDto> {}
              """),
          source("Named", "public record Named(String id) {}"),
          source("Titled", "public record Titled(String title) {}"),
          source("Card", "public record Card(String id, String title) {}"),
          source(
              "CardMerge",
              """
              import org.higherkindedj.optics.annotations.GenerateMerge;

              @GenerateMerge
              public interface CardMerge {
                Card merge(Named named, Titled titled);
              }
              """),
          source("FooErrorContext", "public record FooErrorContext(String orderId) {}"),
          source(
              "FooError",
              """
              import org.higherkindedj.hkt.error.ErrorEnvelope;
              import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

              @GenerateErrorEnvelope
              public sealed interface FooError {
                ErrorEnvelope<FooErrorContext> envelope();

                record SystemHalted(ErrorEnvelope<FooErrorContext> envelope) implements FooError {}
              }
              """),
          source(
              "Solo",
              """
              import org.higherkindedj.optics.annotations.GenerateAssembly;

              @GenerateAssembly
              public record Solo(String a) {}
              """));

  @TempDir Path tmp;

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + body);
  }

  @Test
  @DisplayName("each processor names the shared package, never a name already taken")
  void eachProcessorNamesTheSharedPackage() throws IOException {
    assertEachProcessorNamesTheSharedPackage(Map.of("billing", SOURCES, "shipping", SOURCES));
  }

  @Test
  @DisplayName("a module declaring the package by its package-info alone still shares it")
  void aPackageInfoAloneDeclaresThePackage() throws IOException {
    assertEachProcessorNamesTheSharedPackage(
        Map.of(
            "billing",
            List.of(JavaFileObjects.forSourceString(PKG + ".package-info", "package " + PKG + ";")),
            "shipping",
            SOURCES));
  }

  private void assertEachProcessorNamesTheSharedPackage(Map<String, List<JavaFileObject>> modules)
      throws IOException {
    List<String> errors =
        GeneratorTestHelper.compileModules(
                tmp,
                List.of(
                    new MappingProcessor(),
                    new MergeProcessor(),
                    new ErrorEnvelopeProcessor(),
                    new AssemblyProcessor()),
                modules)
            .stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> diagnostic.getMessage(null))
            .toList();
    Assertions.assertThat(errors)
        .anyMatch(
            error ->
                error.startsWith(
                    "@GenerateMapping: could not write the generated mapping for 'ContactMapping'"
                        + SHARED))
        .anyMatch(
            error ->
                error.startsWith(
                    "@GenerateMerge: could not write the generated merge for 'CardMerge'" + SHARED))
        .anyMatch(
            error ->
                error.startsWith(
                    "@GenerateErrorEnvelope: could not write the generated companion for 'FooError'"
                        + SHARED))
        .anyMatch(
            error ->
                error.startsWith(
                    "@GenerateAssembly: could not write the companion 'SoloAssembly' for 'Solo'"
                        + SHARED))
        .allSatisfy(
            error ->
                Assertions.assertThat(error)
                    .contains("Declare the package in only one of those modules")
                    .doesNotContain("already exists"));
  }
}
