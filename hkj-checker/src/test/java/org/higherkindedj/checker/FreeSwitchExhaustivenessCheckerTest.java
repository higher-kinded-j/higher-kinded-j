// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FreeSwitchExhaustivenessChecker")
class FreeSwitchExhaustivenessCheckerTest {

  private Compilation compile(JavaFileObject source) {
    return javac()
        .withOptions("-Xplugin:HKJChecker", "--enable-preview", "--release", "25")
        .compile(source);
  }

  private static JavaFileObject src(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  @Test
  @DisplayName(
      "Free switch matching Pure/Suspend/FlatMapped but missing HandleError+Ap -> companion")
  void freeSwitch_missingNewCases_emitsCompanion() {
    Compilation c =
        compile(
            src(
                "test.PartialFreeInterpreter",
                """
                package test;
                import org.higherkindedj.hkt.free.Free;
                import org.higherkindedj.hkt.optional.OptionalKind;
                public class PartialFreeInterpreter {
                    String interpret(Free<OptionalKind.Witness, Integer> f) {
                        return switch (f) {
                            case Free.Pure<OptionalKind.Witness, Integer> p -> "pure";
                            case Free.Suspend<OptionalKind.Witness, Integer> s -> "suspend";
                            case Free.FlatMapped<OptionalKind.Witness, ?, Integer> fm -> "fm";
                            default -> "other";
                        };
                    }
                }
                """));
    assertThat(c).failed(); // our ERROR-severity companion fails the build
    assertThat(c)
        .hadErrorContaining("switch over Free is missing the Free.HandleError and Free.Ap");
  }

  @Test
  @DisplayName("exhaustive Free switch covering all five variants -> no companion, compiles")
  void freeSwitch_allVariants_noCompanion() {
    Compilation c =
        compile(
            src(
                "test.FullFreeInterpreter",
                """
                package test;
                import org.higherkindedj.hkt.free.Free;
                import org.higherkindedj.hkt.optional.OptionalKind;
                public class FullFreeInterpreter {
                    String interpret(Free<OptionalKind.Witness, Integer> f) {
                        return switch (f) {
                            case Free.Pure<OptionalKind.Witness, Integer> p -> "pure";
                            case Free.Suspend<OptionalKind.Witness, Integer> s -> "suspend";
                            case Free.FlatMapped<OptionalKind.Witness, ?, Integer> fm -> "fm";
                            case Free.HandleError<OptionalKind.Witness, ?, Integer> he -> "he";
                            case Free.Ap<OptionalKind.Witness, Integer> ap -> "ap";
                        };
                    }
                }
                """));
    assertThat(c).succeeded();
  }

  @Test
  @DisplayName("switch over a non-Free selector -> no false positive")
  void nonFreeSwitch_noCompanion() {
    Compilation c =
        compile(
            src(
                "test.NotFree",
                """
                package test;
                public class NotFree {
                    String classify(String s) {
                        return switch (s) {
                            case "Pure" -> "p";
                            case "Suspend" -> "s";
                            default -> "other";
                        };
                    }
                }
                """));
    assertThat(c).succeeded();
  }
}
