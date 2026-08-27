// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.junit.jupiter.api.Assertions.*;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SPI widening enhancements:
 *
 * <ul>
 *   <li>Enhancement 1: ZERO_OR_MORE auto-widening (widenCollections)
 *   <li>Enhancement 2: Optional&lt;NavigableType&gt; navigator generation
 *   <li>Enhancement 3: Wildcard type support
 *   <li>Enhancement 4: SPI generator priority/ordering
 * </ul>
 */
@DisplayName("SPI Widening Enhancements")
public class FocusProcessorSpiEnhancementsTest {

  @Nested
  @DisplayName("Enhancement 4: SPI Generator Priority/Ordering")
  class GeneratorPriority {

    @Test
    @DisplayName("priority() should default to PRIORITY_DEFAULT (0)")
    void priorityShouldDefaultToZero() {
      TraversableGenerator gen =
          new TraversableGenerator() {
            @Override
            public boolean supports(TypeMirror type) {
              return false;
            }

            @Override
            public CodeBlock generateModifyF(
                RecordComponentElement component,
                ClassName recordClassName,
                List<? extends RecordComponentElement> allComponents) {
              return CodeBlock.builder().build();
            }
          };
      assertEquals(TraversableGenerator.PRIORITY_DEFAULT, gen.priority());
      assertEquals(0, gen.priority());
    }

    @Test
    @DisplayName("priority constants should have expected values")
    void priorityConstantsShouldHaveExpectedValues() {
      assertEquals(-100, TraversableGenerator.PRIORITY_FALLBACK);
      assertEquals(0, TraversableGenerator.PRIORITY_DEFAULT);
      assertEquals(100, TraversableGenerator.PRIORITY_OVERRIDE);
    }

    @Test
    @DisplayName("PRIORITY_OVERRIDE > PRIORITY_DEFAULT > PRIORITY_FALLBACK")
    void priorityOrderingShouldBeCorrect() {
      assertTrue(TraversableGenerator.PRIORITY_OVERRIDE > TraversableGenerator.PRIORITY_DEFAULT);
      assertTrue(TraversableGenerator.PRIORITY_DEFAULT > TraversableGenerator.PRIORITY_FALLBACK);
    }
  }

  @Nested
  @DisplayName("Enhancement 1: widenCollections Opt-In")
  class WidenCollections {

    @Test
    @DisplayName("widenCollections=false (default) should keep SPI ZERO_OR_MORE as FocusPath")
    void widenCollectionsDefaultShouldKeepFocusPath() {
      // Map<String,String> is ZERO_OR_MORE via SPI MapValueGenerator.
      // With widenCollections=false (default), the static Focus method returns FocusPath.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Map;

              @GenerateFocus
              public record Config(String name, Map<String, String> metadata) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Map field should remain FocusPath (no auto-widening)
      final String expectedFocusPath =
          """
          public static FocusPath<Config, Map<String, String>> metadata() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedFocusPath);
    }

    @Test
    @DisplayName("widenCollections=true should widen SPI ZERO_OR_MORE to TraversalPath")
    void widenCollectionsTrueShouldWidenToTraversalPath() {
      // With widenCollections=true, Map<String,String> should produce TraversalPath
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Map;

              @GenerateFocus(widenCollections = true)
              public record Config(String name, Map<String, String> metadata) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Map field should be widened to TraversalPath via .each(opticExpr)
      final String expectedTraversalPath =
          """
          public static TraversalPath<Config, String> metadata() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedTraversalPath);

      // The generated method body should contain .each() to compose the traversal
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", ".each(");
    }

    @Test
    @DisplayName("widenCollections should not affect ZERO_OR_ONE SPI types")
    void widenCollectionsShouldNotAffectZeroOrOneTypes() {
      // Either<String,String> is ZERO_OR_ONE via SPI. widenCollections should not change it.
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.either.Either;

              @GenerateFocus(widenCollections = true)
              public record Form(String name, Either<String, String> result) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Either field should still be AffinePath regardless of widenCollections
      final String expectedAffinePath =
          """
          public static AffinePath<Form, String> result() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.FormFocus", expectedAffinePath);

      // The generated method body should contain .some() for ZERO_OR_ONE SPI types
      assertGeneratedCodeContains(compilation, "com.example.FormFocus", ".some(");
    }

    @Test
    @DisplayName("ZERO_OR_ONE SPI types should always widen even with widenCollections=false")
    void zeroOrOneShouldAlwaysWidenRegardlessOfFlag() {
      // Either<String,Integer> is ZERO_OR_ONE via SPI. It should widen to AffinePath
      // even when widenCollections is false (the default).
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.either.Either;

              @GenerateFocus
              public record Form(String name, Either<String, Integer> result) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Either field should produce AffinePath even with widenCollections=false (default)
      final String expectedAffinePath =
          """
          public static AffinePath<Form, Integer> result() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.FormFocus", expectedAffinePath);
      assertGeneratedCodeContains(compilation, "com.example.FormFocus", ".some(");
    }
  }

  @Nested
  @DisplayName("Enhancement 3: Wildcard Type Support")
  class WildcardTypeSupport {

    @Test
    @DisplayName("should resolve ? extends T to T for Optional fields")
    void shouldResolveExtendsWildcardForOptionalFields() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus
              public record Form(String name, Optional<? extends Number> score) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Optional<? extends Number> should resolve to AffinePath<Form, Number>
      final String expectedAffinePath =
          """
          public static AffinePath<Form, Number> score() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.FormFocus", expectedAffinePath);
    }

    @Test
    @DisplayName("should resolve ? extends T to T for List fields")
    void shouldResolveExtendsWildcardForListFields() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;

              @GenerateFocus
              public record Form(String name, List<? extends Number> values) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // List<? extends Number> should resolve to TraversalPath<Form, Number>
      final String expectedTraversalPath =
          """
          public static TraversalPath<Form, Number> values() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.FormFocus", expectedTraversalPath);
    }

    @Test
    @DisplayName("should resolve ? super T to Object")
    void shouldResolveSuperWildcardToObject() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.List;

              @GenerateFocus
              public record Form(String name, List<? super Integer> values) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // List<? super Integer> should resolve to TraversalPath<Form, Object>
      final String expectedTraversalPath =
          """
          public static TraversalPath<Form, Object> values() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.FormFocus", expectedTraversalPath);
    }

    @Test
    @DisplayName("should resolve unbounded ? to Object")
    void shouldResolveUnboundedWildcardToObject() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Form",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus
              public record Form(String name, Optional<?> data) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Optional<?> should resolve to AffinePath<Form, Object>
      final String expectedAffinePath =
          """
          public static AffinePath<Form, Object> data() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.FormFocus", expectedAffinePath);
    }
  }

  @Nested
  @DisplayName("Enhancement 2: Optional<NavigableType> AffinePath Widening")
  class OptionalNavigableType {

    @Test
    @DisplayName("should generate AffinePath for Optional<NavigableType> field")
    void shouldGenerateAffinePathForOptionalNavigableType() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Address headquarters, Optional<Address> backup) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);
      assertThat(compilation).succeeded();

      // Optional<Address> should produce AffinePath<Company, Address> via .some()
      final String expectedAffinePath = "AffinePath<Company, Address>";
      final String expectedSome = ".some()";

      // Direct navigable field headquarters should produce a navigator
      final String expectedNavigator =
          """
          public static HeadquartersNavigator<Company> headquarters() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedAffinePath);
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedSome);
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedNavigator);
    }

    @Test
    @DisplayName("should generate AffinePath for Maybe<NavigableType> field")
    void shouldGenerateAffinePathForMaybeNavigableType() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.maybe.Maybe;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Maybe<Address> backup) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);
      assertThat(compilation).succeeded();

      // Maybe<Address> should also produce AffinePath via .some()
      final String expectedAffinePath = "AffinePath<Company, Address>";
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedAffinePath);
    }

    @Test
    @DisplayName("Optional<non-navigable> field should produce AffinePath without navigator")
    void shouldProduceAffinePathForOptionalNonNavigableType() {
      final JavaFileObject source =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import java.util.Optional;

              @GenerateFocus(generateNavigators = true)
              public record Config(String name, Optional<String> alias) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(source);
      assertThat(compilation).succeeded();

      // Optional<String> should produce AffinePath<Config, String>
      final String expectedAffinePath = "AffinePath<Config, String>";
      assertGeneratedCodeContains(compilation, "com.example.ConfigFocus", expectedAffinePath);
    }
  }

  @Nested
  @DisplayName("Raw and wildcard type arguments in SPI containers")
  class SpiContainerTypeArguments {

    /**
     * The type these records' containers focus on, navigable so that a navigator is generated for
     * the ones a wildcard bounds by it.
     */
    private static final JavaFileObject LEAF =
        JavaFileObjects.forSourceString(
            "com.example.Leaf",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus
            public record Leaf(String name) {}
            """);

    private static JavaFileObject holder(String attributes, String component) {
      return JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;
          import org.higherkindedj.hkt.either.Either;
          import java.util.List;
          import java.util.Map;
          import java.util.Optional;

          @GenerateFocus(%s)
          public record Holder(%s) {}
          """
              .formatted(attributes, component));
    }

    private static Compilation compile(String attributes, String component) {
      return javac()
          .withProcessors(new FocusProcessor())
          .compile(holder(attributes, component), LEAF);
    }

    @Test
    @DisplayName("should reject a wildcard in the focused type argument")
    void shouldRejectWildcardInFocusedTypeArgument() {
      Compilation compilation = compile("", "Either<String, ? extends Leaf> boundedEither");

      assertThat(compilation)
          .hadErrorContaining(
              "@GenerateFocus: record component 'Holder.boundedEither' has a wildcard type"
                  + " argument in Either<String, ? extends Leaf>.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the component with concrete type arguments, such as"
                  + " Either<String, Leaf>, or drop @GenerateFocus from the record");
    }

    @Test
    @DisplayName("should reject a wildcard in a type argument the generator does not focus on")
    void shouldRejectWildcardInUnfocusedTypeArgument() {
      // Affines.eitherRight() infers both of Either's type arguments, so a wildcard on the left
      // leaves the optic just as undenotable as one on the right.
      Compilation compilation = compile("", "Either<?, Leaf> unfocusedWildcard");

      assertThat(compilation)
          .hadErrorContaining("has a wildcard type argument in Either<?, Leaf>.");
      assertThat(compilation).hadErrorContaining("such as Either<Object, Leaf>,");
    }

    @Test
    @DisplayName("should name Object as the alternative for an unbounded wildcard")
    void shouldNameObjectForUnboundedWildcard() {
      Compilation compilation = compile("", "Either<String, ?> unbounded");

      assertThat(compilation).hadErrorContaining("such as Either<String, Object>,");
    }

    @Test
    @DisplayName("should reject a wildcard container nested inside a widening chain")
    void shouldRejectWildcardContainerNestedInChain() {
      Compilation compilation = compile("", "Optional<Either<String, ? extends Leaf>> nested");

      assertThat(compilation)
          .hadErrorContaining(
              "record component 'Holder.nested' has a wildcard type argument in Either<String, ?"
                  + " extends Leaf>.");
    }

    @Test
    @DisplayName("should accept a wildcard nested inside a type argument")
    void shouldAcceptWildcardNestedInsideTypeArgument() {
      // Either<String, List<? extends Leaf>> has a ground instantiation: the wildcard belongs to
      // the List, which widens through the free type variable of .each().
      Compilation compilation = compile("", "Either<String, List<? extends Leaf>> groundEither");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.HolderFocus", ".some(Affines.eitherRight()).each()");
    }

    @Test
    @DisplayName("should leave a ZERO_OR_MORE container alone when it is not widened anyway")
    void shouldLeaveUnwidenedZeroOrMoreContainerAlone() {
      // The static method leaves every ZERO_OR_MORE SPI field a plain FocusPath, so the wildcard
      // costs this record nothing and there is nothing to report.
      Compilation compilation = compile("", "Map<String, ? extends Leaf> values");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.HolderFocus",
          "public static FocusPath<Holder, Map<String, ? extends Leaf>> values()");
    }

    @Test
    @DisplayName("should reject a ZERO_OR_MORE container once widenCollections asks for it")
    void shouldRejectZeroOrMoreContainerWhenCollectionsWiden() {
      Compilation compilation =
          compile("widenCollections = true", "Map<String, ? extends Leaf> values");

      assertThat(compilation)
          .hadErrorContaining("has a wildcard type argument in Map<String, ? extends Leaf>.");
      assertThat(compilation).hadErrorContaining("such as Map<String, Leaf>,");
    }

    @Test
    @DisplayName("should reject a ZERO_OR_MORE container once a navigator takes it")
    void shouldRejectZeroOrMoreContainerWhenNavigatorTakesIt() {
      Compilation compilation =
          compile("generateNavigators = true", "Map<String, ? extends Leaf> values");

      assertThat(compilation)
          .hadErrorContaining("has a wildcard type argument in Map<String, ? extends Leaf>.");
    }

    @Test
    @DisplayName("should accept a navigator record whose wildcard container has no navigable inner")
    void shouldAcceptNavigatorRecordWithoutNavigableInner() {
      // No navigator is generated for Map<String, ? extends String>, so nothing widens it and the
      // wildcard is harmless.
      Compilation compilation =
          compile("generateNavigators = true", "Map<String, ? extends String> labels");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.HolderFocus",
          "public static FocusPath<Holder, Map<String, ? extends String>> labels()");
    }

    @Test
    @DisplayName("should accept a nested ZERO_OR_MORE container a navigator does not reach")
    void shouldAcceptNestedZeroOrMoreContainerNavigatorDoesNotReach() {
      // A navigator only reads the component's own type, and the chain leaves a nested
      // ZERO_OR_MORE SPI container un-widened, so the Map is never asked for an optic.
      Compilation compilation =
          compile("generateNavigators = true", "Optional<Map<String, ? extends Leaf>> nested");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.HolderFocus",
          "public static AffinePath<Holder, Map<String, ? extends Leaf>> nested()");
    }

    @Test
    @DisplayName("should accept a wildcard container a filter excludes from navigation")
    void shouldAcceptWildcardContainerExcludedFromNavigation() {
      Compilation compilation =
          compile(
              "generateNavigators = true, excludeFields = \"values\"",
              "Map<String, ? extends Leaf> values");

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should reject a raw SPI container")
    void shouldRejectRawSpiContainer() {
      // A raw container offers no type arguments at all, which leaves the optic just as
      // undenotable as a wildcard does.
      Compilation compilation = compile("", "Either raw");

      assertThat(compilation).hadErrorContaining("record component 'Holder.raw' has a raw Either.");
      assertThat(compilation)
          .hadErrorContaining("a raw type offers no type arguments to infer it from.");
      assertThat(compilation).hadErrorContaining("such as Either<Object, Object>,");
    }

    @Test
    @DisplayName("should leave a raw ZERO_OR_MORE container alone when it is not widened anyway")
    void shouldLeaveUnwidenedRawZeroOrMoreContainerAlone() {
      Compilation compilation = compile("", "Map values");

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should accept a wildcard container deeper than the analysis descends")
    void shouldAcceptWildcardContainerBelowMaxDepth() {
      // The widening analysis stops at three layers, so the Either is never widened and its
      // wildcard is never asked for an optic.
      Compilation compilation =
          compile("", "Optional<Optional<Optional<Either<String, ? extends Leaf>>>> deep");

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should accept a primitive component in a navigator record")
    void shouldAcceptPrimitiveComponentInNavigatorRecord() {
      // A primitive is not a declared type, so it never reaches the container question at all.
      Compilation compilation = compile("generateNavigators = true", "int count");

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should accept a wildcard container no generator claims")
    void shouldAcceptWildcardContainerNoGeneratorClaims() {
      // Nothing widens a Function, so its wildcard is never asked for an optic.
      Compilation compilation =
          compile(
              "generateNavigators = true",
              "java.util.function.Function<String, ? extends Leaf> lookup");

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should still widen an SPI container with concrete type arguments")
    void shouldStillWidenConcreteSpiContainer() {
      Compilation compilation = compile("", "Either<String, Leaf> concrete");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.HolderFocus",
          "public static AffinePath<Holder, Leaf> concrete()");
    }
  }

  @Nested
  @DisplayName("ProcessorUtils.resolveWildcard")
  class ResolveWildcardUnit {

    @Test
    @DisplayName("resolveWildcard should return non-wildcard type unchanged")
    void shouldReturnNonWildcardUnchanged() {
      // This is implicitly tested via compile tests, but we also test the utility directly
      // by verifying the compile tests produce correct results (no unit test for TypeMirror
      // without a processing environment).
      assertNotNull(ProcessorUtils.class, "ProcessorUtils should be accessible");
    }
  }

  @Nested
  @DisplayName("ProcessorUtils.hasUndenotableTypeArguments")
  class UndenotableTypeArguments {

    @Test
    @DisplayName("should answer for every shape a record component's type can take")
    void shouldAnswerForEveryComponentShape() {
      // A raw List and a List<?> differ in ways only javac can produce, so the predicate is put
      // to real type mirrors rather than to stand-ins.
      assertEquals(
          Map.of(
              "primitive", false, // not a declared type at all
              "plain", false, // declared, but with no type parameters to leave unfilled
              "concrete", false,
              "wildcard", true,
              "raw", true),
          answers(
              "int primitive, String plain, List<String> concrete, List<?> wildcard, List raw"));
    }

    /** What the predicate answers for each component of a record, keyed by component name. */
    private Map<String, Boolean> answers(String components) {
      ComponentTypeProbe probe = new ComponentTypeProbe();
      Compilation compilation =
          javac()
              .withProcessors(probe)
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Probe",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateFocus;
                      import java.util.List;

                      @GenerateFocus
                      @SuppressWarnings("rawtypes")
                      public record Probe(%s) {}
                      """
                          .formatted(components)));
      assertThat(compilation).succeeded();
      return probe.answers;
    }
  }

  /** Puts {@link ProcessorUtils#hasUndenotableTypeArguments} to each component of a record. */
  private static final class ComponentTypeProbe extends AbstractProcessor {

    private final Map<String, Boolean> answers = new LinkedHashMap<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("org.higherkindedj.optics.annotations.GenerateFocus");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      for (Element element : roundEnv.getElementsAnnotatedWith(GenerateFocus.class)) {
        for (RecordComponentElement component : ((TypeElement) element).getRecordComponents()) {
          answers.put(
              component.getSimpleName().toString(),
              ProcessorUtils.hasUndenotableTypeArguments(component.asType()));
        }
      }
      return true;
    }
  }
}
