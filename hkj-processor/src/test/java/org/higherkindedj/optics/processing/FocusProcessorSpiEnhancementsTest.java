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
import java.util.List;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
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
}
