// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CompanionAnnotationProcessor} claims the hkj annotations no generating processor names, so
 * that {@code -Xlint:processing} does not report them, in a user's sources or in generated ones.
 */
@DisplayName("CompanionAnnotationProcessor")
class CompanionAnnotationProcessorTest {

  /** Carries one companion written onto generated code and one read from a spec. */
  private static final JavaFileObject COMPANIONS =
      JavaFileObjects.forSourceString(
          "com.example.Companions",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.Generated;
          import org.higherkindedj.optics.annotations.MapField;

          @Generated
          public interface Companions {
            @MapField(to = "fullName")
            String name();
          }
          """);

  /** The message javac gives an annotation no processor claims. */
  private static final String UNCLAIMED = "No processor claimed any of these annotations";

  @Nested
  @DisplayName("Claiming")
  class Claiming {

    @Test
    @DisplayName("claims a companion annotation, so -Xlint:processing reports none")
    void claimsACompanionAnnotation() {
      final Compilation compilation =
          javac()
              .withProcessors(new CompanionAnnotationProcessor())
              .withOptions("-Xlint:processing", "-Werror")
              .compile(COMPANIONS);

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("without it, the processors that read companion annotations leave them unclaimed")
    void withoutItTheCompanionsAreUnclaimed() {
      final Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor(), new LensProcessor())
              .withOptions("-Xlint:processing")
              .compile(COMPANIONS);

      assertThat(compilation).hadWarningContaining(UNCLAIMED);
      assertThat(compilation.warnings().stream().map(warning -> warning.getMessage(null)))
          .anySatisfy(
              message ->
                  assertThat(message)
                      .contains(
                          "org.higherkindedj.optics.annotations.Generated",
                          "org.higherkindedj.optics.annotations.MapField"));
    }

    @Test
    @DisplayName("a generated file and the spec it came from compile under -Xlint:all -Werror")
    void generatedSourcesCompileUnderEveryLint() {
      final JavaFileObject person =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateLenses;

              @GenerateLenses
              public record Person(String name, int age) {}
              """);
      final JavaFileObject personDto =
          JavaFileObjects.forSourceString(
              "com.example.PersonDto",
              """
              package com.example;

              public record PersonDto(String fullName, int age) {}
              """);
      final JavaFileObject mapping =
          JavaFileObjects.forSourceString(
              "com.example.PersonMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PersonMapping extends MappingSpec<Person, PersonDto> {
                @MapField(to = "fullName")
                String name();
              }
              """);

      final Compilation compilation =
          javac()
              .withProcessors(
                  new LensProcessor(), new MappingProcessor(), new CompanionAnnotationProcessor())
              .withOptions("-Xlint:all", "-Werror")
              .compile(person, personDto, mapping);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation.generatedSourceFile("com.example.PersonLenses")).isPresent();
      assertThat(compilation.generatedSourceFile("com.example.PersonMappingImpl")).isPresent();
    }
  }

  @Nested
  @DisplayName("Coverage of hkj-annotations")
  class Coverage {

    /** The one annotation no processor reads, so none claims it: it has no effect to report. */
    private static final Set<String> READ_BY_NONE =
        Set.of("org.higherkindedj.hkt.effect.annotation.PathConfig");

    @Test
    @DisplayName("every annotation hkj-annotations declares is claimed by an hkj processor")
    void everyAnnotationIsClaimed() {
      final Set<String> claimed =
          hkjProcessors()
              .flatMap(processor -> processor.getSupportedAnnotationTypes().stream())
              .collect(Collectors.toUnmodifiableSet());

      final List<String> declared =
          new ClassFileImporter()
                  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                  .importPackages(
                      "org.higherkindedj.optics.annotations",
                      "org.higherkindedj.hkt.effect.annotation",
                      "org.higherkindedj.annotation")
                  .stream()
                  .filter(JavaClass::isAnnotation)
                  .map(JavaClass::getName)
                  .filter(name -> !READ_BY_NONE.contains(name))
                  .toList();

      assertThat(declared).isNotEmpty();
      assertThat(declared)
          .as(
              "an annotation no processor names is reported under -Xlint:processing; name it in"
                  + " the processor that generates for it, or in CompanionAnnotationProcessor")
          .allSatisfy(name -> assertThat(claimed).contains(name));
    }

    @Test
    @DisplayName("claims none of the annotations a generating processor names")
    void claimsNoGeneratingAnnotation() {
      // A generating processor after this one would never be offered an annotation claimed here.
      final Set<String> companions =
          new CompanionAnnotationProcessor().getSupportedAnnotationTypes();

      assertThat(
              hkjProcessors()
                  .filter(processor -> !(processor instanceof CompanionAnnotationProcessor)))
          .allSatisfy(
              processor ->
                  assertThat(processor.getSupportedAnnotationTypes())
                      .as(processor.getClass().getSimpleName())
                      .doesNotContainAnyElementsOf(companions));
    }

    @Test
    @DisplayName("is registered with Gradle as isolating, and every hkj processor is registered")
    void isRegisteredWithGradleAsIsolating() throws IOException {
      final Map<String, String> registered = gradleRegistrations();

      assertThat(registered)
          .containsEntry(CompanionAnnotationProcessor.class.getName(), "isolating");
      assertThat(hkjProcessors().map(processor -> processor.getClass().getName()))
          .as("a processor Gradle does not know of turns incremental compilation off")
          .allSatisfy(name -> assertThat(registered).containsKey(name));
    }
  }

  /** Every processor this module registers with the service loader, loaded as javac loads it. */
  private static Stream<Processor> hkjProcessors() {
    return ServiceLoader.load(Processor.class, CompanionAnnotationProcessor.class.getClassLoader())
        .stream()
        .filter(provider -> provider.type().getName().startsWith("org.higherkindedj."))
        .map(ServiceLoader.Provider::get);
  }

  /** The processors named in Gradle's incremental registration, with their categories. */
  private static Map<String, String> gradleRegistrations() throws IOException {
    final List<URL> registrations =
        Collections.list(
            CompanionAnnotationProcessor.class
                .getClassLoader()
                .getResources("META-INF/gradle/incremental.annotation.processors"));
    assertThat(registrations).isNotEmpty();
    final StringBuilder lines = new StringBuilder();
    for (URL registration : registrations) {
      try (InputStream in = registration.openStream()) {
        lines.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
      }
    }
    return lines
        .toString()
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        // The registration can sit on the classpath more than once; a processor named twice with
        // different categories still fails to collect.
        .distinct()
        .map(line -> line.split(","))
        .collect(
            Collectors.toUnmodifiableMap(entry -> entry[0].strip(), entry -> entry[1].strip()));
  }
}
