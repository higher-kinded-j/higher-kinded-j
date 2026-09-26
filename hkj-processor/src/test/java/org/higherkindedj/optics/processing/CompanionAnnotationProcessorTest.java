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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.effect.PathSourceProcessor;
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
  @DisplayName("@PathConfig")
  class PathConfigNote {

    private static final JavaFileObject WITNESS =
        JavaFileObjects.forSourceString(
            "com.example.BoxKind",
            """
            package com.example;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.TypeArity;
            import org.higherkindedj.hkt.WitnessArity;

            public interface BoxKind<A> extends Kind<BoxKind.Witness, A> {
              final class Witness implements WitnessArity<TypeArity.Unary> {}
            }
            """);

    private static final JavaFileObject BOX =
        JavaFileObjects.forSourceString(
            "com.example.Box",
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = BoxKind.Witness.class)
            public interface Box<A> {}
            """);

    /** Sets every setting the generated Path would show, each away from its default. */
    private static final JavaFileObject CONFIGURED =
        packageInfo(
            "com.example",
            """
            @PathConfig(pathSuffix = "Effect", makeFinal = false, generateToString = false,
                includeGeneratedAnnotation = false)
            """);

    private static final String NO_EFFECT =
        "@PathConfig: it has no effect on package 'com.example'.";

    private static JavaFileObject packageInfo(String packageName, String annotation) {
      return JavaFileObjects.forSourceString(
          packageName + ".package-info",
          annotation
              + "package "
              + packageName
              + ";\n\nimport org.higherkindedj.hkt.effect.annotation.PathConfig;\n");
    }

    private static Compilation compile(String lint, JavaFileObject... sources) {
      return javac()
          .withProcessors(new PathSourceProcessor(), new CompanionAnnotationProcessor())
          .withOptions(lint, "-Werror")
          .compile(sources);
    }

    private static String generatedBoxPath(Compilation compilation) throws IOException {
      return compilation
          .generatedSourceFile("com.example.BoxPath")
          .orElseThrow()
          .getCharContent(true)
          .toString();
    }

    @Test
    @DisplayName(
        "notes at the annotation that it has no effect and how to get its suffix, and the Path"
            + " generates as without it")
    void notesThatItHasNoEffect() throws IOException {
      final Compilation configured = compile("-Xlint:all,-removal", CONFIGURED, WITNESS, BOX);
      final Compilation plain = compile("-Xlint:all", WITNESS, BOX);

      assertThat(configured).succeededWithoutWarnings();
      assertThat(configured).hadNoteContaining(NO_EFFECT).inFile(CONFIGURED).onLine(1);
      assertThat(configured)
          .hadNoteContaining(
              "Remove it; nothing generated changes. To name the package's Path classes with"
                  + " \"Effect\" instead, which renames them, add suffix = \"Effect\" to each"
                  + " @PathSource.");
      assertThat(plain).succeededWithoutWarnings();
      assertThat(generatedBoxPath(configured)).isEqualTo(generatedBoxPath(plain));
    }

    @Test
    @DisplayName("offers only its removal for a pathSuffix that is the default or names no class")
    void offersOnlyItsRemoval() {
      final List<JavaFileObject> packageInfos =
          List.of(
              packageInfo("com.example.unset", "@PathConfig(makeFinal = false)\n"),
              packageInfo("com.example.path", "@PathConfig(pathSuffix = \"Path\")\n"),
              packageInfo("com.example.empty", "@PathConfig(pathSuffix = \"\")\n"),
              packageInfo("com.example.invalid", "@PathConfig(pathSuffix = \"-x\")\n"));

      final Compilation compilation =
          javac()
              .withProcessors(new CompanionAnnotationProcessor())
              .withOptions("-Xlint:all,-removal", "-Werror")
              .compile(packageInfos);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining("@PathConfig: it has no effect on package 'com.example.invalid'.")
          .inFile(packageInfos.getLast())
          .onLine(1);
      assertThat(compilation.notes().stream().map(note -> note.getMessage(null)))
          .filteredOn(message -> message.startsWith("@PathConfig"))
          .hasSize(packageInfos.size())
          .allSatisfy(
              message -> assertThat(message).endsWith("Remove it; nothing generated changes."));
    }

    @Test
    @DisplayName("is claimed, so javac reports only that it is deprecated for removal")
    void isReportedOnlyAsDeprecated() {
      final Compilation compilation =
          javac()
              .withProcessors(new PathSourceProcessor(), new CompanionAnnotationProcessor())
              .withOptions("-Xlint:all")
              .compile(CONFIGURED, WITNESS, BOX);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining(
              "PathConfig in org.higherkindedj.hkt.effect.annotation has been"
                  + " deprecated and marked for removal")
          .inFile(CONFIGURED)
          .onLine(1);
      assertThat(compilation.warnings().stream().map(warning -> warning.getMessage(null)))
          .allSatisfy(message -> assertThat(message).contains("marked for removal"));
    }

    @Test
    @DisplayName("without it, the Path processor leaves @PathConfig unclaimed")
    void withoutItPathConfigIsUnclaimed() {
      final Compilation compilation =
          javac()
              .withProcessors(new PathSourceProcessor())
              .withOptions("-Xlint:processing")
              .compile(CONFIGURED, WITNESS, BOX);

      assertThat(compilation).hadWarningContaining(UNCLAIMED);
      assertThat(compilation.warnings().stream().map(warning -> warning.getMessage(null)))
          .anySatisfy(
              message -> assertThat(message).contains(CompanionAnnotationProcessor.PATH_CONFIG));
    }
  }

  @Nested
  @DisplayName("Coverage of hkj-annotations")
  class Coverage {

    @Test
    @DisplayName("every annotation hkj-annotations declares is claimed by an hkj processor")
    void everyAnnotationIsClaimed() {
      final Set<String> claimed =
          GeneratorTestHelper.registeredProcessors()
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
              GeneratorTestHelper.registeredProcessors()
                  .filter(processor -> !(processor instanceof CompanionAnnotationProcessor)))
          .allSatisfy(
              processor ->
                  assertThat(processor.getSupportedAnnotationTypes())
                      .as(processor.getClass().getSimpleName())
                      .doesNotContainAnyElementsOf(companions));
    }

    @Test
    @DisplayName("is registered with Gradle as isolating")
    void isRegisteredWithGradleAsIsolating() throws IOException {
      assertThat(GeneratorTestHelper.gradleRegistrations())
          .containsEntry(CompanionAnnotationProcessor.class.getName(), "isolating");
    }
  }
}
