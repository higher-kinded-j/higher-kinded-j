// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.gradleRegistrations;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.locationOf;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.registeredProcessors;

import com.palantir.javapoet.JavaFile;
import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.annotations.GenerateGetters;
import org.higherkindedj.optics.focus.FocusPath;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * hkj-processor registers its processors three times. {@code @AutoService} writes {@code
 * META-INF/services}, which javac's processor path reads. The {@code provides} clause of {@code
 * module-info}, which {@code --processor-module-path} reads instead, and Gradle's incremental
 * registration are written by hand, so these tests hold both to the services file; a processor
 * missing from {@code provides} generates nothing on the module path, silently.
 */
@DisplayName("Processor registration")
class ProcessorRegistrationTest {

  @Test
  @DisplayName("module-info provides every registered processor, in the services file's order")
  void moduleInfoProvidesEveryRegisteredProcessor() {
    // On the module path, the service loader would read the provides clause itself, and this
    // would compare the clause with itself.
    assertThat(LensProcessor.class.getModule().isNamed()).as("runs on the classpath").isFalse();
    final List<String> registered =
        registeredProcessors().map(processor -> processor.getClass().getName()).toList();

    assertThat(registered).isNotEmpty();
    assertThat(
            ModuleFinder.of(locationOf(LensProcessor.class))
                .find("org.higherkindedj.processor")
                .map(ModuleReference::descriptor)
                .orElseThrow()
                .provides())
        .filteredOn(provides -> provides.service().equals(Processor.class.getName()))
        .flatExtracting(ModuleDescriptor.Provides::providers)
        .as(
            "module-info's provides clause and the services file disagree; a processor missing"
                + " from provides generates nothing from --processor-module-path, so list every"
                + " processor there, sorted by fully qualified name as @AutoService sorts the file")
        .containsExactlyElementsOf(registered);
  }

  @Test
  @DisplayName("Gradle's incremental registration names every registered processor")
  void gradleRegistersEveryRegisteredProcessor() throws IOException {
    final Map<String, String> registered = gradleRegistrations();

    assertThat(registeredProcessors().map(processor -> processor.getClass().getName()))
        .as("a processor Gradle does not know of turns incremental compilation off")
        .allSatisfy(name -> assertThat(registered).containsKey(name));
  }

  @Test
  @DisplayName("javac run with --processor-module-path generates what the user's code calls")
  void javacGeneratesFromTheProcessorModulePath(@TempDir final Path dir)
      throws IOException, InterruptedException {
    final Path src = Files.createDirectories(dir.resolve("src/com/example"));
    final Path out = Files.createDirectories(dir.resolve("out"));
    final Path account =
        Files.writeString(
            src.resolve("Account.java"),
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateGetters;

            @GenerateGetters
            public record Account(String id) {}
            """);
    final Path person =
        Files.writeString(
            src.resolve("Person.java"),
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Person(String name) {}
            """);
    final Path uses =
        Files.writeString(
            src.resolve("Uses.java"),
            """
            package com.example;

            class Uses {
              static String id(Account account) {
                return AccountGetters.id().get(account);
              }

              static String name(Person person) {
                return PersonFocus.name().get(person);
              }
            }
            """);
    final Path log = dir.resolve("javac.log");

    // A javac process of its own, as a build runs one. In this JVM the processors' layer would sit
    // over the test classpath and load the test sources' TraversableGenerator registrations.
    final ProcessBuilder javac =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                // hkj-processor and each module it requires; a module added to its requires fails
                // the compilation until it is named here
                "--processor-module-path",
                pathOf(LensProcessor.class, GenerateGetters.class, Kind.class, JavaFile.class),
                "-classpath",
                pathOf(GenerateGetters.class, Kind.class, FocusPath.class, NullMarked.class),
                "-d",
                out.toString(),
                // An annotation no processor claims is reported only under this lint.
                "-Xlint:processing",
                "-Werror",
                account.toString(),
                person.toString(),
                uses.toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
    // javac reads further options from this variable, which the test JVM's may carry.
    javac.environment().remove("JDK_JAVAC_OPTIONS");
    final Process process = javac.start();
    final boolean finished;
    try {
      finished = process.waitFor(2, TimeUnit.MINUTES);
    } finally {
      process.destroyForcibly();
    }
    final String output = Files.readString(log);

    assertThat(finished).as("javac still running after two minutes:%n%s", output).isTrue();
    assertThat(process.exitValue())
        .as(
            "javac with --processor-module-path, each module hkj-processor requires named in"
                + " this test:%n%s",
            output)
        .isZero();
    assertThat(out.resolve("com/example/AccountGetters.class")).exists();
    assertThat(out.resolve("com/example/PersonFocus.class")).exists();
  }

  /** The directories and jars the given classes were loaded from, as a path option. */
  private static String pathOf(final Class<?>... types) {
    return Stream.of(types)
        .map(GeneratorTestHelper::locationOf)
        .map(Path::toString)
        .collect(Collectors.joining(File.pathSeparator));
  }
}
