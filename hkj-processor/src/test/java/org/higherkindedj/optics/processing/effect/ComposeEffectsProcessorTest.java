// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ComposeEffectsProcessor} including @ComposeEffects generation and @Handles
 * validation.
 */
@DisplayName("ComposeEffects Processor Tests")
class ComposeEffectsProcessorTest {

  // ---------------------------------------------------------------------------
  // Test fixtures
  // ---------------------------------------------------------------------------

  private static JavaFileObject twoEffectComposition() {
    return JavaFileObjects.forSourceString(
        "test.pkg.AppEffects",
        """
        package test.pkg;

        import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

        @ComposeEffects
        public record AppEffects(
            Integer console,
            String db
        ) {}
        """);
  }

  private static JavaFileObject threeEffectComposition() {
    return JavaFileObjects.forSourceString(
        "test.pkg.TripleEffects",
        """
        package test.pkg;

        import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

        @ComposeEffects
        public record TripleEffects(
            Integer console,
            String db,
            Long logging
        ) {}
        """);
  }

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ComposeEffectsProcessor()).compile(sources);
  }

  private String getGeneratedSource(Compilation compilation, String className) throws IOException {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(className);
    assertThat(file).as("Generated file should exist: %s", className).isPresent();
    return file.get().getCharContent(true).toString();
  }

  // ===========================================================================
  // @ComposeEffects Generation Tests
  // ===========================================================================

  @Nested
  @DisplayName("ComposeEffects Generation")
  class ComposeEffectsGenerationTests {

    @Test
    @DisplayName("Should generate Support class for 2-effect composition")
    void generatesSupportFor2Effects() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      assertThat(compilation.errors()).isEmpty();

      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("public final class AppEffectsSupport");
      assertThat(source).contains("@Generated");
      assertThat(source).contains("private AppEffectsSupport()");
    }

    @Test
    @DisplayName("Should generate inject methods for 2-effect composition")
    void generatesInjectMethodsFor2Effects() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("injectConsole(");
      assertThat(source).contains("InjectInstances.injectLeft()");
      assertThat(source).contains("injectDb(");
      assertThat(source).contains("InjectInstances.injectRight()");
    }

    @Test
    @DisplayName("Should generate inject methods for 3-effect composition")
    void generatesInjectMethodsFor3Effects() throws IOException {
      Compilation compilation = compile(threeEffectComposition());
      assertThat(compilation.errors()).isEmpty();

      String source = getGeneratedSource(compilation, "test.pkg.TripleEffectsSupport");

      assertThat(source).contains("injectConsole(");
      assertThat(source).contains("InjectInstances.injectLeft()");
      assertThat(source).contains("injectDb(");
      assertThat(source).contains("injectLogging(");
    }

    @Test
    @DisplayName("Should generate functor() method")
    void generatesFunctorMethod() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("functor(");
      assertThat(source).contains("EitherFFunctor");
    }

    @Test
    @DisplayName("Should generate BoundSet record")
    void generatesBoundSetRecord() throws IOException {
      Compilation compilation = compile(twoEffectComposition());
      String source = getGeneratedSource(compilation, "test.pkg.AppEffectsSupport");

      assertThat(source).contains("record BoundSet<F");
      assertThat(source).contains("Object console");
      assertThat(source).contains("Object db");
    }

    @Test
    @DisplayName("Should generate inject methods for 4-effect composition")
    void generatesInjectMethodsFor4Effects() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.QuadEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record QuadEffects(
                  Integer console,
                  String db,
                  Long logging,
                  Double metrics
              ) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isEmpty();

      String generated = getGeneratedSource(compilation, "test.pkg.QuadEffectsSupport");

      assertThat(generated).contains("injectConsole(");
      assertThat(generated).contains("injectDb(");
      assertThat(generated).contains("injectLogging(");
      assertThat(generated).contains("injectMetrics(");
      // 4-effect functor should nest 3 EitherFFunctors
      assertThat(generated).contains("functor(");
    }
  }

  // ===========================================================================
  // @ComposeEffects Validation Tests
  // ===========================================================================

  @Nested
  @DisplayName("ComposeEffects Validation")
  class ComposeEffectsValidationTests {

    @Test
    @DisplayName("Non-record should produce error")
    void nonRecordShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadEffects",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public class BadEffects {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@ComposeEffects can only annotate record types");
    }

    @Test
    @DisplayName("Single field should produce error")
    void singleFieldShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadEffects",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record BadEffects(String only) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@ComposeEffects requires 2-4 effect algebra fields");
    }

    @Test
    @DisplayName("Five fields should produce error")
    void fiveFieldsShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadEffects",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record BadEffects(String a, String b, String c, String d, String e) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@ComposeEffects requires 2-4 effect algebra fields");
    }
  }

  // ===========================================================================
  // @Handles Validation Tests
  // ===========================================================================

  @Nested
  @DisplayName("Handles Validation")
  class HandlesValidationTests {

    @Test
    @DisplayName("@Handles on non-class should produce error")
    void handlesOnNonClassShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.BadHandler",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(String.class)
              public interface BadHandler {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@Handles can only annotate classes");
    }

    @Test
    @DisplayName("Missing handler should produce error")
    void missingHandlerShouldError() {
      // Need sealed interface + interpreter in same compilation
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.TestOp",
              """
              package test.pkg;

              public sealed interface TestOp<A> permits TestOp.Foo, TestOp.Bar {
                  record Foo<A>() implements TestOp<A> {}
                  record Bar<A>() implements TestOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.TestInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(TestOp.class)
              public class TestInterpreter {
                  // Missing handleFoo and handleBar
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      assertThat(compilation.errors()).isNotEmpty();
      // Should report missing handlers for Foo and Bar
      String errors =
          compilation.errors().stream()
              .map(d -> d.getMessage(null))
              .reduce("", (a, b) -> a + " " + b);
      assertThat(errors).contains("No handler for operation: Foo");
      assertThat(errors).contains("No handler for operation: Bar");
    }

    @Test
    @DisplayName("Complete handlers should pass validation")
    void completeHandlersShouldPass() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.TestOp",
              """
              package test.pkg;

              public sealed interface TestOp<A> permits TestOp.Foo {
                  record Foo<A>() implements TestOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.TestInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(TestOp.class)
              public class TestInterpreter {
                  public Object handleFoo(TestOp.Foo<?> op) { return null; }
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      assertThat(compilation.errors()).isEmpty();
    }

    @Test
    @DisplayName("@Handles on non-sealed interface should produce error")
    void handlesOnNonSealedShouldError() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.PlainOp",
              """
              package test.pkg;

              public interface PlainOp<A> {}
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.PlainInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(PlainOp.class)
              public class PlainInterpreter {}
              """);

      Compilation compilation = compile(algebra, interpreter);
      assertThat(compilation.errors()).isNotEmpty();
      assertThat(compilation.errors().get(0).getMessage(null))
          .contains("@Handles references non-sealed interface");
    }

    @Test
    @DisplayName("Extra handler methods should produce warning")
    void extraHandlerShouldWarn() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.SmallOp",
              """
              package test.pkg;

              public sealed interface SmallOp<A> permits SmallOp.Only {
                  record Only<A>() implements SmallOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.SmallInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(SmallOp.class)
              public class SmallInterpreter {
                  public Object handleOnly(SmallOp.Only<?> op) { return null; }
                  public Object handleExtra(Object op) { return null; }
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      // No errors (handleOnly covers the one permit)
      assertThat(compilation.errors()).isEmpty();
      // But should have a warning for handleExtra
      assertThat(compilation.warnings()).isNotEmpty();
      String warnings =
          compilation.warnings().stream()
              .map(d -> d.getMessage(null))
              .reduce("", (a, b) -> a + " " + b);
      assertThat(warnings).contains("handleExtra");
      assertThat(warnings).contains("doesn't match any operation");
    }

    @Test
    @DisplayName("Non-handle methods should be ignored when checking handlers")
    void nonHandleMethodsShouldBeIgnored() {
      var algebra =
          JavaFileObjects.forSourceString(
              "test.pkg.HelperOp",
              """
              package test.pkg;

              public sealed interface HelperOp<A> permits HelperOp.Work {
                  record Work<A>() implements HelperOp<A> {}
              }
              """);
      var interpreter =
          JavaFileObjects.forSourceString(
              "test.pkg.HelperInterpreter",
              """
              package test.pkg;
              import org.higherkindedj.hkt.effect.annotation.Handles;

              @Handles(HelperOp.class)
              public class HelperInterpreter {
                  public Object handleWork(HelperOp.Work<?> op) { return null; }
                  // Non-handle method should be ignored, not flagged as extra
                  public Object processWork(Object data) { return null; }
              }
              """);

      Compilation compilation = compile(algebra, interpreter);
      // No errors — handleWork covers the one permit
      assertThat(compilation.errors()).isEmpty();
      // processWork should NOT produce a warning since it doesn't start with "handle"
    }

    @Test
    @DisplayName("Custom targetPackage should be used for Support class")
    void customTargetPackage() throws IOException {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.MyEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects(targetPackage = "test.gen")
              public record MyEffects(
                  Integer console,
                  String db
              ) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isEmpty();
      assertThat(compilation.generatedSourceFile("test.gen.MyEffectsSupport")).isPresent();
    }

    @Test
    @DisplayName("Duplicate effect types should produce error with field name")
    void duplicateEffectTypesShouldError() {
      var source =
          JavaFileObjects.forSourceString(
              "test.pkg.DupEffects",
              """
              package test.pkg;

              import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

              @ComposeEffects
              public record DupEffects(
                  String first,
                  String second
              ) {}
              """);

      Compilation compilation = compile(source);
      assertThat(compilation.errors()).isNotEmpty();
      String errors =
          compilation.errors().stream()
              .map(d -> d.getMessage(null))
              .reduce("", (a, b) -> a + " " + b);
      assertThat(errors).contains("Duplicate effect algebra type");
      assertThat(errors).contains("second");
    }
  }
}
