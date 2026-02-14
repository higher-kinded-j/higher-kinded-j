// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.OpticKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.PrismHintKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.TraversalHintKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SpecInterfaceAnalyser}.
 *
 * <p>These tests verify that the analyser correctly parses spec interfaces extending {@code
 * OpticsSpec<S>} and extracts method information for optics generation.
 */
@DisplayName("SpecInterfaceAnalyser")
class SpecInterfaceAnalyserTest {

  /** Common source files needed for tests. */
  private static final JavaFileObject OPTICS_SPEC =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.annotations.OpticsSpec",
          """
          package org.higherkindedj.optics.annotations;
          public interface OpticsSpec<S> {}
          """);

  private static final JavaFileObject LENS =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.Lens",
          """
          package org.higherkindedj.optics;
          public interface Lens<S, A> {}
          """);

  private static final JavaFileObject PRISM =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.Prism",
          """
          package org.higherkindedj.optics;
          public interface Prism<S, A> {}
          """);

  private static final JavaFileObject TRAVERSAL =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.Traversal",
          """
          package org.higherkindedj.optics;
          public interface Traversal<S, A> {}
          """);

  private static final JavaFileObject VIA_BUILDER =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.annotations.ViaBuilder",
          """
          package org.higherkindedj.optics.annotations;
          import java.lang.annotation.*;
          @Target(ElementType.METHOD)
          @Retention(RetentionPolicy.CLASS)
          public @interface ViaBuilder {
              String getter() default "";
              String toBuilder() default "toBuilder";
              String setter() default "";
              String build() default "build";
          }
          """);

  private static final JavaFileObject WITHER =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.annotations.Wither",
          """
          package org.higherkindedj.optics.annotations;
          import java.lang.annotation.*;
          @Target(ElementType.METHOD)
          @Retention(RetentionPolicy.CLASS)
          public @interface Wither {
              String value();
              String getter() default "";
          }
          """);

  private static final JavaFileObject INSTANCE_OF =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.annotations.InstanceOf",
          """
          package org.higherkindedj.optics.annotations;
          import java.lang.annotation.*;
          @Target(ElementType.METHOD)
          @Retention(RetentionPolicy.CLASS)
          public @interface InstanceOf {
              Class<?> value();
          }
          """);

  private static final JavaFileObject TRAVERSE_WITH =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.optics.annotations.TraverseWith",
          """
          package org.higherkindedj.optics.annotations;
          import java.lang.annotation.*;
          @Target(ElementType.METHOD)
          @Retention(RetentionPolicy.CLASS)
          public @interface TraverseWith {
              String value();
          }
          """);

  /**
   * A helper processor that runs the SpecInterfaceAnalyser and captures the result. This allows us
   * to test the analyser within the annotation processing environment.
   */
  private static class AnalyserTestProcessor extends AbstractProcessor {
    private final String targetTypeName;
    private Optional<SpecAnalysis> result = Optional.empty();
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
        SpecInterfaceAnalyser analyser =
            new SpecInterfaceAnalyser(
                processingEnv.getTypeUtils(),
                processingEnv.getElementUtils(),
                processingEnv.getMessager());
        result = analyser.analyse(typeElement);
      }

      return false;
    }

    Optional<SpecAnalysis> getResult() {
      return result;
    }
  }

  private Optional<SpecAnalysis> analyseSpec(String typeName, JavaFileObject... additionalSources) {
    JavaFileObject[] allSources = new JavaFileObject[additionalSources.length + 4];
    allSources[0] = OPTICS_SPEC;
    allSources[1] = LENS;
    allSources[2] = PRISM;
    allSources[3] = TRAVERSAL;
    System.arraycopy(additionalSources, 0, allSources, 4, additionalSources.length);

    AnalyserTestProcessor processor = new AnalyserTestProcessor(typeName);
    Compilation compilation = javac().withProcessors(processor).compile(allSources);
    assertThat(compilation).succeeded();
    return processor.getResult();
  }

  @Nested
  @DisplayName("Source Type Extraction")
  class SourceTypeExtraction {

    @Test
    @DisplayName("should extract source type from OpticsSpec<S>")
    void shouldExtractSourceType() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name, int age) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              public interface PersonOptics extends OpticsSpec<Person> {}
              """);

      Optional<SpecAnalysis> result = analyseSpec("com.test.PersonOptics", person, spec);

      assertThat(result).isPresent();
      assertThat(result.get().sourceType().toString()).isEqualTo("com.test.Person");
      assertThat(result.get().sourceTypeElement().getSimpleName().toString()).isEqualTo("Person");
    }
  }

  @Nested
  @DisplayName("Method Categorisation")
  class MethodCategorisation {

    @Test
    @DisplayName("should identify abstract methods requiring generation")
    void shouldIdentifyAbstractMethods() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name, int age) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              public interface PersonOptics extends OpticsSpec<Person> {
                  @ViaBuilder
                  Lens<Person, String> name();

                  @ViaBuilder
                  Lens<Person, Integer> age();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PersonOptics", person, spec, VIA_BUILDER);

      assertThat(result).isPresent();
      assertThat(result.get().opticMethods()).hasSize(2);
      assertThat(result.get().opticMethods().get(0).methodName()).isEqualTo("name");
      assertThat(result.get().opticMethods().get(1).methodName()).isEqualTo("age");
    }

    @Test
    @DisplayName("should identify default methods to copy")
    void shouldIdentifyDefaultMethods() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name, int age) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              public interface PersonOptics extends OpticsSpec<Person> {
                  @ViaBuilder
                  Lens<Person, String> name();

                  default Lens<Person, String> firstName() {
                      return null; // Placeholder
                  }
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PersonOptics", person, spec, VIA_BUILDER);

      assertThat(result).isPresent();
      assertThat(result.get().opticMethods()).hasSize(1);
      assertThat(result.get().defaultMethods()).hasSize(1);
      assertThat(result.get().defaultMethods().get(0).getSimpleName().toString())
          .isEqualTo("firstName");
    }
  }

  @Nested
  @DisplayName("Optic Kind Detection")
  class OpticKindDetection {

    @Test
    @DisplayName("should detect Lens return type")
    void shouldDetectLens() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              public interface PersonOptics extends OpticsSpec<Person> {
                  @ViaBuilder
                  Lens<Person, String> name();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PersonOptics", person, spec, VIA_BUILDER);

      assertThat(result).isPresent();
      assertThat(result.get().opticMethods().get(0).opticKind()).isEqualTo(OpticKind.LENS);
    }

    @Test
    @DisplayName("should detect Prism return type")
    void shouldDetectPrism() {
      var payment =
          JavaFileObjects.forSourceString(
              "com.test.PaymentMethod",
              """
              package com.test;
              public sealed interface PaymentMethod permits CreditCard {}
              """);

      var creditCard =
          JavaFileObjects.forSourceString(
              "com.test.CreditCard",
              """
              package com.test;
              public record CreditCard(String number) implements PaymentMethod {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PaymentOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;

              public interface PaymentOptics extends OpticsSpec<PaymentMethod> {
                  @InstanceOf(CreditCard.class)
                  Prism<PaymentMethod, CreditCard> creditCard();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PaymentOptics", payment, creditCard, spec, INSTANCE_OF);

      assertThat(result).isPresent();
      assertThat(result.get().opticMethods().get(0).opticKind()).isEqualTo(OpticKind.PRISM);
    }

    @Test
    @DisplayName("should detect Traversal return type")
    void shouldDetectTraversal() {
      var team =
          JavaFileObjects.forSourceString(
              "com.test.Team",
              """
              package com.test;
              import java.util.List;
              public record Team(String name, List<String> members) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TeamOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.TraverseWith;

              public interface TeamOptics extends OpticsSpec<Team> {
                  @TraverseWith("com.test.Traversals.list()")
                  Traversal<Team, String> members();
              }
              """);

      Optional<SpecAnalysis> result = analyseSpec("com.test.TeamOptics", team, spec, TRAVERSE_WITH);

      assertThat(result).isPresent();
      assertThat(result.get().opticMethods().get(0).opticKind()).isEqualTo(OpticKind.TRAVERSAL);
    }
  }

  @Nested
  @DisplayName("Copy Strategy Parsing")
  class CopyStrategyParsing {

    @Test
    @DisplayName("should parse @ViaBuilder with defaults")
    void shouldParseViaBuilderDefaults() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              public interface PersonOptics extends OpticsSpec<Person> {
                  @ViaBuilder
                  Lens<Person, String> name();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PersonOptics", person, spec, VIA_BUILDER);

      assertThat(result).isPresent();
      var method = result.get().opticMethods().get(0);
      assertThat(method.copyStrategy()).isEqualTo(CopyStrategyKind.VIA_BUILDER);
      assertThat(method.copyStrategyInfo().toBuilder()).isEqualTo("toBuilder");
      assertThat(method.copyStrategyInfo().build()).isEqualTo("build");
    }

    @Test
    @DisplayName("should parse @ViaBuilder with custom values")
    void shouldParseViaBuilderCustom() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              public interface PersonOptics extends OpticsSpec<Person> {
                  @ViaBuilder(getter = "getName", toBuilder = "newBuilder", setter = "withName", build = "create")
                  Lens<Person, String> name();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PersonOptics", person, spec, VIA_BUILDER);

      assertThat(result).isPresent();
      var info = result.get().opticMethods().get(0).copyStrategyInfo();
      assertThat(info.getter()).isEqualTo("getName");
      assertThat(info.toBuilder()).isEqualTo("newBuilder");
      assertThat(info.setter()).isEqualTo("withName");
      assertThat(info.build()).isEqualTo("create");
    }

    @Test
    @DisplayName("should parse @Wither annotation")
    void shouldParseWither() {
      var localDate =
          JavaFileObjects.forSourceString(
              "com.test.LocalDate",
              """
              package com.test;
              public class LocalDate {
                  private int year;
                  public int getYear() { return year; }
                  public LocalDate withYear(int year) { return this; }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.LocalDateOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              public interface LocalDateOptics extends OpticsSpec<LocalDate> {
                  @Wither(value = "withYear", getter = "getYear")
                  Lens<LocalDate, Integer> year();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.LocalDateOptics", localDate, spec, WITHER);

      assertThat(result).isPresent();
      var method = result.get().opticMethods().get(0);
      assertThat(method.copyStrategy()).isEqualTo(CopyStrategyKind.WITHER);
      assertThat(method.copyStrategyInfo().witherMethod()).isEqualTo("withYear");
      assertThat(method.copyStrategyInfo().getter()).isEqualTo("getYear");
    }
  }

  @Nested
  @DisplayName("Prism Hint Parsing")
  class PrismHintParsing {

    @Test
    @DisplayName("should parse @InstanceOf annotation")
    void shouldParseInstanceOf() {
      var payment =
          JavaFileObjects.forSourceString(
              "com.test.PaymentMethod",
              """
              package com.test;
              public sealed interface PaymentMethod permits CreditCard {}
              """);

      var creditCard =
          JavaFileObjects.forSourceString(
              "com.test.CreditCard",
              """
              package com.test;
              public record CreditCard(String number) implements PaymentMethod {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PaymentOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;

              public interface PaymentOptics extends OpticsSpec<PaymentMethod> {
                  @InstanceOf(CreditCard.class)
                  Prism<PaymentMethod, CreditCard> creditCard();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PaymentOptics", payment, creditCard, spec, INSTANCE_OF);

      assertThat(result).isPresent();
      var method = result.get().opticMethods().get(0);
      assertThat(method.prismHint()).isEqualTo(PrismHintKind.INSTANCE_OF);
      assertThat(method.prismHintInfo().targetType()).isNotNull();
      assertThat(method.prismHintInfo().targetType().toString()).isEqualTo("com.test.CreditCard");
    }
  }

  @Nested
  @DisplayName("Traversal Hint Parsing")
  class TraversalHintParsing {

    @Test
    @DisplayName("should parse @TraverseWith annotation")
    void shouldParseTraverseWith() {
      var team =
          JavaFileObjects.forSourceString(
              "com.test.Team",
              """
              package com.test;
              import java.util.List;
              public record Team(String name, List<String> members) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TeamOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.TraverseWith;

              public interface TeamOptics extends OpticsSpec<Team> {
                  @TraverseWith("org.higherkindedj.optics.Traversals.list()")
                  Traversal<Team, String> members();
              }
              """);

      Optional<SpecAnalysis> result = analyseSpec("com.test.TeamOptics", team, spec, TRAVERSE_WITH);

      assertThat(result).isPresent();
      var method = result.get().opticMethods().get(0);
      assertThat(method.traversalHint()).isEqualTo(TraversalHintKind.TRAVERSE_WITH);
      assertThat(method.traversalHintInfo().traversalReference())
          .isEqualTo("org.higherkindedj.optics.Traversals.list()");
    }
  }

  @Nested
  @DisplayName("Focus Type Extraction")
  class FocusTypeExtraction {

    @Test
    @DisplayName("should extract focus type from Lens<S, A>")
    void shouldExtractFocusType() {
      var person =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;
              public record Person(String name, int age) {}
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.PersonOptics",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;

              public interface PersonOptics extends OpticsSpec<Person> {
                  @ViaBuilder
                  Lens<Person, String> name();

                  @ViaBuilder
                  Lens<Person, Integer> age();
              }
              """);

      Optional<SpecAnalysis> result =
          analyseSpec("com.test.PersonOptics", person, spec, VIA_BUILDER);

      assertThat(result).isPresent();
      assertThat(result.get().opticMethods().get(0).focusType().toString())
          .isEqualTo("java.lang.String");
      assertThat(result.get().opticMethods().get(1).focusType().toString())
          .isEqualTo("java.lang.Integer");
    }
  }
}
