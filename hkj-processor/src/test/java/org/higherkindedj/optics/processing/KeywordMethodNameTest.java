// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.processing.effect.EffectAlgebraProcessor;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A generated method named after an enum constant or a type takes its camelCase form, and a form
 * that is a Java keyword or literal takes a trailing underscore, so {@code NEW} names {@code
 * new_()} rather than crashing the processor.
 */
@DisplayName("A generated method named after a keyword takes a trailing underscore")
class KeywordMethodNameTest {

  private static final JavaFileObject STATUS =
      JavaFileObjects.forSourceString(
          "com.test.Status",
          """
          package com.test;

          import org.higherkindedj.optics.annotations.GeneratePrisms;

          @GeneratePrisms
          public enum Status { NEW, CLASS, DEFAULT, PAID }
          """);

  @Test
  @DisplayName("toMethodName escapes keywords and literals, and leaves other names as camelCase")
  void toMethodName() {
    Assertions.assertThat(ProcessorUtils.toMethodName("NEW")).isEqualTo("new_");
    Assertions.assertThat(ProcessorUtils.toMethodName("CLASS")).isEqualTo("class_");
    Assertions.assertThat(ProcessorUtils.toMethodName("Default")).isEqualTo("default_");
    Assertions.assertThat(ProcessorUtils.toMethodName("TRUE")).isEqualTo("true_");
    Assertions.assertThat(ProcessorUtils.toMethodName("NULL")).isEqualTo("null_");
    Assertions.assertThat(ProcessorUtils.toMethodName("PAID")).isEqualTo("paid");
    Assertions.assertThat(ProcessorUtils.toMethodName("OUT_OF_STOCK")).isEqualTo("outOfStock");
    Assertions.assertThat(ProcessorUtils.escapeKeyword("default")).isEqualTo("default_");
    Assertions.assertThat(ProcessorUtils.escapeKeyword("yield")).isEqualTo("yield");
    Assertions.assertThat(ProcessorUtils.escapeKeyword("name")).isEqualTo("name");
  }

  @Test
  @DisplayName("@GeneratePrisms on an enum names a keyword constant's prism with an underscore")
  void enumConstantPrisms() throws Exception {
    var compiled = RuntimeCompilationHelper.compileWith(new PrismProcessor(), STATUS);
    Class<?> status = compiled.loadClass("com.test.Status");
    Object newStatus = status.getEnumConstants()[0];
    Object paid = status.getEnumConstants()[3];

    Prism<Object, Object> prism = prism(compiled, "com.test.StatusPrisms", "new_");

    Assertions.assertThat(prism.getOptional(newStatus)).contains(newStatus);
    Assertions.assertThat(prism.getOptional(paid)).isEmpty();
    Assertions.assertThat(generated(compiled.compilation(), "com.test.StatusPrisms"))
        .contains("class_()", "default_()", "paid()");
  }

  @Test
  @DisplayName(
      "@GeneratePrisms on a sealed type names a keyword subtype's prism with an underscore")
  void sealedSubtypePrisms() throws Exception {
    JavaFileObject mode =
        JavaFileObjects.forSourceString(
            "com.test.Mode",
            """
            package com.test;

            import org.higherkindedj.optics.annotations.GeneratePrisms;

            @GeneratePrisms
            public sealed interface Mode permits Mode.Default, Mode.Custom {
              record Default() implements Mode {}

              record Custom(String name) implements Mode {}
            }
            """);
    var compiled = RuntimeCompilationHelper.compileWith(new PrismProcessor(), mode);
    Object defaultMode =
        compiled.loadClass("com.test.Mode$Default").getDeclaredConstructor().newInstance();
    Object customMode =
        compiled
            .loadClass("com.test.Mode$Custom")
            .getDeclaredConstructor(String.class)
            .newInstance("dark");

    Prism<Object, Object> prism = prism(compiled, "com.test.ModePrisms", "default_");

    Assertions.assertThat(prism.getOptional(defaultMode)).contains(defaultMode);
    Assertions.assertThat(prism.getOptional(customMode)).isEmpty();
  }

  @Test
  @DisplayName("@ImportOptics on a wither class names a keyword property's lens with an underscore")
  void importedWitherLens() throws Exception {
    // Lombok's @With on a 'boolean isDefault' field writes this pair: a lens named 'default'.
    JavaFileObject external =
        JavaFileObjects.forSourceString(
            "com.external.Setting",
            """
            package com.external;

            public final class Setting {
              private final boolean isDefault;

              public Setting(boolean isDefault) {
                this.isDefault = isDefault;
              }

              public boolean isDefault() {
                return isDefault;
              }

              public Setting withDefault(boolean isDefault) {
                return new Setting(isDefault);
              }
            }
            """);
    var compiled =
        RuntimeCompilationHelper.compile(
            external, RuntimeCompilationHelper.packageInfo("com.test", "com.external.Setting"));
    Object setting =
        compiled.loadClass("com.external.Setting").getConstructor(boolean.class).newInstance(false);

    @SuppressWarnings("unchecked") // the generated lens is over a type this test cannot name
    Lens<Object, Object> lens =
        (Lens<Object, Object>) compiled.invokeStatic("com.test.SettingLenses", "default_");

    Assertions.assertThat(lens.get(setting)).isEqualTo(false);
    Assertions.assertThat(lens.get(lens.set(true, setting))).isEqualTo(true);
  }

  @Test
  @DisplayName("@EffectAlgebra names a keyword permit's operation with an underscore")
  void effectAlgebraOperations() {
    JavaFileObject algebra =
        JavaFileObjects.forSourceString(
            "test.pkg.FailOp",
            """
            package test.pkg;

            import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

            @EffectAlgebra
            public sealed interface FailOp<A> permits FailOp.Throw, FailOp.Recover {
                record Throw<A>(String reason) implements FailOp<A> {}
                record Recover<A>() implements FailOp<A> {}
            }
            """);

    Compilation compilation = javac().withProcessors(new EffectAlgebraProcessor()).compile(algebra);

    assertThat(compilation).succeeded();
    Assertions.assertThat(generated(compilation, "test.pkg.FailOpOps"))
        .contains(" throw_(", " recover(");
  }

  @Test
  @DisplayName("@ImportOptics on an external enum names a keyword constant's prism the same way")
  void importedEnumPrisms() throws Exception {
    JavaFileObject external =
        JavaFileObjects.forSourceString(
            "com.external.Level",
            """
            package com.external;

            public enum Level { NEW, HIGH }
            """);
    var compiled =
        RuntimeCompilationHelper.compile(
            external, RuntimeCompilationHelper.packageInfo("com.test", "com.external.Level"));
    Object newLevel = compiled.loadClass("com.external.Level").getEnumConstants()[0];

    Prism<Object, Object> prism = prism(compiled, "com.test.LevelPrisms", "new_");

    Assertions.assertThat(prism.getOptional(newLevel)).contains(newLevel);
  }

  @Test
  @DisplayName("@GenerateErrorEnvelope names a keyword variant's factory with an underscore")
  void errorEnvelopeFactories() {
    JavaFileObject context =
        JavaFileObjects.forSourceString(
            "com.example.FooErrorContext",
            """
            package com.example;

            public record FooErrorContext(String orderId) {}
            """);
    JavaFileObject error =
        JavaFileObjects.forSourceString(
            "com.example.FooError",
            """
            package com.example;

            import org.higherkindedj.hkt.error.ErrorEnvelope;
            import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

            @GenerateErrorEnvelope
            public sealed interface FooError {
              ErrorEnvelope<FooErrorContext> envelope();

              record New(ErrorEnvelope<FooErrorContext> envelope) implements FooError {}

              record Default(ErrorEnvelope<FooErrorContext> envelope) implements FooError {}

              record Yield(ErrorEnvelope<FooErrorContext> envelope) implements FooError {}
            }
            """);

    Compilation compilation =
        javac().withProcessors(new ErrorEnvelopeProcessor()).compile(context, error);

    assertThat(compilation).succeededWithoutWarnings();
    // 'yield' is a legal method name, but an unqualified call to it is not, so the convenience
    // factory calls the timed one through the companion.
    Assertions.assertThat(generated(compilation, "com.example.FooErrors"))
        .contains(" new_(", " default_(", " yield(", "FooErrors.yield(");
  }

  @SuppressWarnings("unchecked") // the generated prism is over types this test cannot name
  private static Prism<Object, Object> prism(
      RuntimeCompilationHelper.CompiledResult compiled, String prismsClass, String method)
      throws ReflectiveOperationException {
    return (Prism<Object, Object>) compiled.invokeStatic(prismsClass, method);
  }

  private static String generated(Compilation compilation, String qualifiedName) {
    Optional<JavaFileObject> file =
        compilation.generatedFile(
            StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    Assertions.assertThat(file).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
