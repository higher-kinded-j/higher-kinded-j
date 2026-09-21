// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.generator.basejdk.ArrayGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.OptionalGenerator;
import org.higherkindedj.optics.processing.generator.hkj.EitherGenerator;
import org.higherkindedj.optics.processing.generator.hkj.MaybeGenerator;
import org.higherkindedj.optics.processing.generator.hkj.TryGenerator;
import org.higherkindedj.optics.processing.generator.hkj.ValidatedGenerator;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A {@code modifyF} body that names a component's type names it from the package the file is
 * written into.
 *
 * <p>A traversal companion lands in the record's own package by default, and elsewhere under a
 * {@code targetPackage} or for a type reached through {@code @ImportOptics}. An annotation
 * package-private to the record's package cannot be written from such a file, so it is left off
 * there and kept at home.
 */
@DisplayName("modifyF bodies name types from the package they are written into")
class ModifyFTargetPackageTest {

  /** The record whose components each of the generators below claims. */
  private static final JavaFileObject SOURCE =
      JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import java.lang.annotation.ElementType;
          import java.lang.annotation.Target;
          import java.util.Optional;
          import org.higherkindedj.hkt.either.Either;
          import org.higherkindedj.hkt.maybe.Maybe;
          import org.higherkindedj.hkt.trymonad.Try;
          import org.higherkindedj.hkt.validated.Validated;

          public record Holder(
              Optional<@Tag String> maybeName,
              Maybe<@Tag String> just,
              Try<@Tag String> attempt,
              Validated<String, @Tag String> checked,
              Either<String, @Tag String> choice,
              @Tag String[] tags) {}

          @Target(ElementType.TYPE_USE)
          @interface Tag {}
          """);

  private static final Map<String, TraversableGenerator> GENERATORS =
      Map.of(
          "maybeName", new OptionalGenerator(),
          "just", new MaybeGenerator(),
          "attempt", new TryGenerator(),
          "checked", new ValidatedGenerator(),
          "choice", new EitherGenerator(),
          "tags", new ArrayGenerator());

  @Test
  @DisplayName("a body written into the record's own package keeps a package-private annotation")
  void aBodyAtHomeKeepsAPackagePrivateAnnotation() {
    assertThat(bodies(null).values()).allMatch(body -> body.contains("@com.example.Tag"));
  }

  @Test
  @DisplayName("a body written into another package leaves it off")
  void aBodyElsewhereLeavesItOff() {
    assertThat(bodies("com.away").values()).noneMatch(body -> body.contains("Tag"));
  }

  /**
   * Each generator's body for its component, written into {@code targetPackage}, or through the
   * form that names no package, which answers for the record's own.
   */
  private static Map<String, String> bodies(final String targetPackage) {
    final Map<String, String> written = new HashMap<>();

    final class Reader extends AbstractProcessor {
      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(
          final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
        for (TypeElement type : ElementFilter.typesIn(round.getRootElements())) {
          if (!type.getSimpleName().contentEquals("Holder")) {
            continue;
          }
          final ClassName recordClassName = ClassName.get(type);
          final List<? extends RecordComponentElement> components = type.getRecordComponents();
          for (RecordComponentElement component : components) {
            final TraversableGenerator generator =
                GENERATORS.get(component.getSimpleName().toString());
            final CodeBlock body =
                targetPackage == null
                    ? generator.generateModifyF(component, recordClassName, components)
                    : generator.generateModifyF(
                        component, recordClassName, components, targetPackage);
            written.put(component.getSimpleName().toString(), body.toString());
          }
        }
        return false;
      }
    }

    assertThat(javac().withProcessors(new Reader()).compile(SOURCE)).succeeded();
    return Map.copyOf(written);
  }
}
