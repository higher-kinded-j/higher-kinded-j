// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.kind;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Coverage tests for {@link KindFieldAnalyser}.
 *
 * <p>These tests exercise edge-case branches in KindFieldAnalyser that are not covered by
 * higher-level integration tests, including non-Kind field types and malformed Kind type arguments.
 */
@DisplayName("KindFieldAnalyser Coverage Tests")
class KindFieldAnalyserCoverageTest {

  /** Stub Kind interface with the standard two type parameters. */
  private static final JavaFileObject KIND_INTERFACE =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.hkt.Kind",
          """
          package org.higherkindedj.hkt;
          public interface Kind<W, A> {}
          """);

  /**
   * A stub Kind interface with only one type parameter, used to test the malformed Kind type
   * argument branch (L86).
   */
  private static final JavaFileObject KIND_SINGLE_ARG =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.hkt.Kind1",
          """
          package org.higherkindedj.hkt;
          public interface Kind1<W> {}
          """);

  /**
   * A helper processor that invokes {@link KindFieldAnalyser} within the annotation processing
   * environment, capturing the analysis results for each record component.
   */
  private static class AnalyserTestProcessor extends AbstractProcessor {
    private final String targetTypeName;
    private final List<Optional<KindFieldInfo>> results = new ArrayList<>();
    private boolean analysisAttempted = false;

    AnalyserTestProcessor(String targetTypeName) {
      this.targetTypeName = targetTypeName;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.RELEASE_25;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver() || analysisAttempted) {
        return false;
      }

      TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(targetTypeName);
      if (typeElement != null) {
        analysisAttempted = true;
        KindFieldAnalyser analyser = new KindFieldAnalyser(processingEnv);

        for (RecordComponentElement component : typeElement.getRecordComponents()) {
          results.add(analyser.analyse(component));
        }
      }

      return false;
    }

    List<Optional<KindFieldInfo>> getResults() {
      return results;
    }
  }

  /**
   * Compiles the given sources with the helper processor and returns the analysis results.
   *
   * @param typeName the fully qualified name of the record to analyse
   * @param sources the source files to compile
   * @return the list of analysis results, one per record component
   */
  private List<Optional<KindFieldInfo>> analyseRecord(String typeName, JavaFileObject... sources) {
    AnalyserTestProcessor processor = new AnalyserTestProcessor(typeName);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getResults();
  }

  @Nested
  @DisplayName("Non-Kind field types (L112 - isKindType returns false for non-declared types)")
  class NonKindFieldTypes {

    @Test
    @DisplayName("should return empty for fields that are not Kind types")
    void shouldReturnEmptyForNonKindFields() {
      var record =
          JavaFileObjects.forSourceString(
              "com.test.SimpleRecord",
              """
              package com.test;
              public record SimpleRecord(String name, int age) {}
              """);

      List<Optional<KindFieldInfo>> results =
          analyseRecord("com.test.SimpleRecord", KIND_INTERFACE, record);

      assertThat(results).hasSize(2);
      assertThat(results.get(0)).isEmpty();
      assertThat(results.get(1)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Malformed Kind type arguments (L86 - Kind without 2 type args)")
  class MalformedKindTypeArgs {

    @Test
    @DisplayName("should return empty for Kind type with only 1 type argument")
    void shouldReturnEmptyForKindWithOneTypeArg() {
      // Use a separate Kind1<W> interface to simulate Kind with only 1 type arg.
      // We register it under the same qualified name so isKindType returns true,
      // but it only has 1 type argument, triggering the L86 branch.
      var kindSingleParam =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.Kind",
              """
              package org.higherkindedj.hkt;
              public interface Kind<W> {}
              """);

      var witnessType =
          JavaFileObjects.forSourceString(
              "com.test.MyWitness",
              """
              package com.test;
              public final class MyWitness {}
              """);

      var record =
          JavaFileObjects.forSourceString(
              "com.test.KindRecord",
              """
              package com.test;
              import org.higherkindedj.hkt.Kind;
              public record KindRecord(Kind<com.test.MyWitness> singleArgField) {}
              """);

      List<Optional<KindFieldInfo>> results =
          analyseRecord("com.test.KindRecord", kindSingleParam, witnessType, record);

      assertThat(results).hasSize(1);
      assertThat(results.get(0)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Kind field with explicit @TraverseField annotation")
  class ExplicitTraverseField {

    @Test
    @DisplayName("should create KindFieldInfo from @TraverseField annotation")
    void shouldCreateFromAnnotation() {
      var witnessType =
          JavaFileObjects.forSourceString(
              "com.test.MyWitness",
              """
              package com.test;
              public final class MyWitness {}
              """);

      // Use the real @TraverseField annotation from hkj-annotations on the classpath
      var record =
          JavaFileObjects.forSourceString(
              "com.test.AnnotatedKindRecord",
              """
              package com.test;
              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.optics.annotations.TraverseField;
              import org.higherkindedj.optics.annotations.KindSemantics;
              public record AnnotatedKindRecord(
                  @TraverseField(
                      traverse = "com.test.MyTraverse.INSTANCE",
                      semantics = KindSemantics.ZERO_OR_MORE
                  )
                  Kind<com.test.MyWitness, String> value
              ) {}
              """);

      List<Optional<KindFieldInfo>> results =
          analyseRecord("com.test.AnnotatedKindRecord", KIND_INTERFACE, witnessType, record);

      assertThat(results).hasSize(1);
      assertThat(results.get(0)).isPresent();
      assertThat(results.get(0).get().witnessType()).isEqualTo("com.test.MyWitness");
    }
  }

  @Nested
  @DisplayName("Kind field with non-parameterised witness type")
  class NonParameterisedWitness {

    @Test
    @DisplayName("should handle Kind field where witness has no type arguments")
    void shouldHandleSimpleWitness() {
      var witnessType =
          JavaFileObjects.forSourceString(
              "com.test.SimpleW",
              """
              package com.test;
              public final class SimpleW {}
              """);

      var record =
          JavaFileObjects.forSourceString(
              "com.test.SimpleWRecord",
              """
              package com.test;
              import org.higherkindedj.hkt.Kind;
              public record SimpleWRecord(Kind<com.test.SimpleW, String> field) {}
              """);

      List<Optional<KindFieldInfo>> results =
          analyseRecord("com.test.SimpleWRecord", KIND_INTERFACE, witnessType, record);

      // The witness com.test.SimpleW is not registered in KindRegistry and is not
      // a library witness, so analyse returns empty
      assertThat(results).hasSize(1);
      assertThat(results.get(0)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Library witness not in KindRegistry")
  class LibraryWitnessNotInRegistry {

    @Test
    @DisplayName("should emit note when unregistered witness is in org.higherkindedj.hkt.* package")
    void shouldEmitNoteForUnregisteredLibraryWitness() {
      // Witness under org.higherkindedj.hkt.* matches isLibraryWitness() === true,
      // but is not in the KindRegistry known kinds map.
      // This covers L166-167 (isLibraryWitness true-branch + note() call)
      // and L234-236 (the note() method body).
      var fakeKind =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.hkt.fake.FakeKind",
              """
              package org.higherkindedj.hkt.fake;
              public final class FakeKind {
                  public static final class Witness {}
              }
              """);

      var record =
          JavaFileObjects.forSourceString(
              "com.test.FakeKindRecord",
              """
              package com.test;
              import org.higherkindedj.hkt.Kind;
              public record FakeKindRecord(
                  Kind<org.higherkindedj.hkt.fake.FakeKind.Witness, String> value
              ) {}
              """);

      List<Optional<KindFieldInfo>> results =
          analyseRecord("com.test.FakeKindRecord", KIND_INTERFACE, fakeKind, record);

      // Library-looking but unregistered witness returns empty and emits a NOTE.
      assertThat(results).hasSize(1);
      assertThat(results.get(0)).isEmpty();
    }
  }

  @Nested
  @DisplayName("@TraverseField with parameterised witness")
  class TraverseFieldParameterisedWitness {

    @Test
    @DisplayName("should detect isParameterised=true when witness has type arguments")
    void shouldDetectParameterisedWitness() {
      // A witness type with its own type parameter (e.g. GenericKind.Witness<E>)
      // causes extractWitnessTypeArgs to return a non-empty string, triggering
      // the isParameterised=true branch on L136 of createFromAnnotation.
      var genericKind =
          JavaFileObjects.forSourceString(
              "com.test.GenericKind",
              """
              package com.test;
              public final class GenericKind {
                  public static final class Witness<E> {}
              }
              """);

      var record =
          JavaFileObjects.forSourceString(
              "com.test.GenericKindRecord",
              """
              package com.test;
              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.optics.annotations.TraverseField;
              import org.higherkindedj.optics.annotations.KindSemantics;
              public record GenericKindRecord(
                  @TraverseField(
                      traverse = "com.test.GenericTraverse.instance()",
                      semantics = KindSemantics.ZERO_OR_ONE
                  )
                  Kind<com.test.GenericKind.Witness<String>, Integer> value
              ) {}
              """);

      List<Optional<KindFieldInfo>> results =
          analyseRecord("com.test.GenericKindRecord", KIND_INTERFACE, genericKind, record);

      assertThat(results).hasSize(1);
      assertThat(results.get(0)).isPresent();
      assertThat(results.get(0).get().isParameterised()).isTrue();
      assertThat(results.get(0).get().witnessTypeArgs()).isEqualTo("java.lang.String");
    }
  }
}
