// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

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
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CompanionAnnotationProcessor} claims the hkj annotations {@link HkjHttpClientProcessor}
 * reads or writes without naming them, so that {@code -Xlint:processing} reports only the Spring
 * annotations a client carries.
 */
@DisplayName("CompanionAnnotationProcessor")
class CompanionAnnotationProcessorTest {

  /** The hkj annotations a client build carries: on the interface's methods and on its output. */
  private static final List<String> HKJ_ANNOTATIONS =
      List.of(
          "org.higherkindedj.optics.annotations.Generated",
          "org.higherkindedj.spring.client.OnStatus",
          "org.higherkindedj.spring.client.OnStatuses");

  /** One method overrides a status once, and one twice, which javac holds as the container. */
  private static final JavaFileObject USER_API =
      JavaFileObjects.forSourceString(
          "com.example.UserApi",
          """
          package com.example;

          import org.higherkindedj.hkt.effect.EitherPath;
          import org.higherkindedj.spring.client.HkjHttpClient;
          import org.higherkindedj.spring.client.OnStatus;
          import org.springframework.web.bind.annotation.PathVariable;
          import org.springframework.web.service.annotation.GetExchange;
          import org.springframework.web.service.annotation.HttpExchange;

          @HttpExchange("/users")
          @HkjHttpClient
          public interface UserApi {
            @GetExchange("/{id}")
            @OnStatus(value = 404, error = NotFound.class)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);

            @GetExchange("/{id}/owner")
            @OnStatus(value = 404, error = NotFound.class)
            @OnStatus(value = 409, error = Conflict.class)
            EitherPath<ApiErr, UserDto> getOwner(@PathVariable String id);
          }
          """);

  private static final JavaFileObject[] SOURCES = {
    USER_API,
    JavaFileObjects.forSourceString(
        "com.example.UserDto", "package com.example; public record UserDto(String id) {}"),
    JavaFileObjects.forSourceString(
        "com.example.ApiErr",
        "package com.example; public sealed interface ApiErr permits NotFound, Conflict {}"),
    JavaFileObjects.forSourceString(
        "com.example.NotFound",
        "package com.example; public record NotFound(String message) implements ApiErr {}"),
    JavaFileObjects.forSourceString(
        "com.example.Conflict",
        "package com.example; public record Conflict(String message) implements ApiErr {}")
  };

  @Nested
  @DisplayName("Claiming")
  class Claiming {

    @Test
    @DisplayName("-Xlint:processing reports the client's Spring annotations and none of hkj's")
    void reportsOnlySpringAnnotations() {
      final Compilation compilation =
          javac()
              .withProcessors(new HkjHttpClientProcessor(), new CompanionAnnotationProcessor())
              .withOptions("-Xlint:processing")
              .compile(SOURCES);

      assertThat(compilation).succeeded();
      assertThat(unclaimed(compilation))
          .contains("org.springframework.web.service.annotation.HttpExchange")
          .doesNotContainAnyElementsOf(HKJ_ANNOTATIONS);
    }

    @Test
    @DisplayName("without it, -Xlint:processing reports hkj's annotations too")
    void withoutItHkjAnnotationsAreReported() {
      final Compilation compilation =
          javac()
              .withProcessors(new HkjHttpClientProcessor())
              .withOptions("-Xlint:processing")
              .compile(SOURCES);

      assertThat(unclaimed(compilation)).containsAll(HKJ_ANNOTATIONS);
    }
  }

  @Nested
  @DisplayName("Coverage of hkj-spring-boot-client")
  class Coverage {

    @Test
    @DisplayName("every annotation the client module declares is claimed by a client processor")
    void everyAnnotationIsClaimed() {
      final Set<String> claimed =
          clientProcessors()
              .flatMap(processor -> processor.getSupportedAnnotationTypes().stream())
              .collect(Collectors.toUnmodifiableSet());

      final List<String> declared =
          new ClassFileImporter()
                  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                  .importPackages("org.higherkindedj.spring.client")
                  .stream()
                  .filter(JavaClass::isAnnotation)
                  .map(JavaClass::getName)
                  .toList();

      assertThat(declared).isNotEmpty();
      assertThat(declared)
          .as(
              "an annotation no processor names is reported under -Xlint:processing; name it in"
                  + " HkjHttpClientProcessor if it generates for it, or in"
                  + " CompanionAnnotationProcessor")
          .allSatisfy(name -> assertThat(claimed).contains(name));
    }

    @Test
    @DisplayName("claims none of the annotations the client processor generates for")
    void claimsNoGeneratingAnnotation() {
      // A generating processor after this one would never be offered an annotation claimed here.
      assertThat(new HkjHttpClientProcessor().getSupportedAnnotationTypes())
          .doesNotContainAnyElementsOf(
              new CompanionAnnotationProcessor().getSupportedAnnotationTypes());
    }

    @Test
    @DisplayName("is registered with Gradle as isolating, and every client processor is registered")
    void isRegisteredWithGradleAsIsolating() throws IOException {
      final Map<String, String> registered = gradleRegistrations();

      assertThat(registered)
          .containsEntry(CompanionAnnotationProcessor.class.getName(), "isolating");
      assertThat(clientProcessors().map(processor -> processor.getClass().getName()))
          .as("a processor Gradle does not know of turns incremental compilation off")
          .allSatisfy(name -> assertThat(registered).containsKey(name));
    }

    @Test
    @DisplayName("every client processor reports the latest supported source version")
    void everyProcessorReportsTheLatestSourceVersion() {
      assertThat(clientProcessors())
          .isNotEmpty()
          .allSatisfy(
              processor ->
                  assertThat(processor.getSupportedSourceVersion())
                      .as(processor.getClass().getSimpleName())
                      .isEqualTo(SourceVersion.latestSupported()));
    }
  }

  /** The annotations javac reported no processor claimed, without their module prefixes. */
  private static List<String> unclaimed(final Compilation compilation) {
    final String prefix = "No processor claimed any of these annotations: ";
    return compilation.warnings().stream()
        .map(warning -> warning.getMessage(null))
        .filter(message -> message.startsWith(prefix))
        .flatMap(message -> Stream.of(message.substring(prefix.length()).split(",")))
        .map(name -> name.strip().substring(name.strip().indexOf('/') + 1))
        .toList();
  }

  /** Every processor this module registers with the service loader, loaded as javac loads it. */
  private static Stream<Processor> clientProcessors() {
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
