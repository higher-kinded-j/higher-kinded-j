// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTMapTArityChecker")
class StateTMapTArityCheckerTest {

  private Compilation compile(String pluginArgs, JavaFileObject source) {
    return javac()
        .withOptions(
            "-Xplugin:HKJChecker" + (pluginArgs.isEmpty() ? "" : " " + pluginArgs),
            "--enable-preview",
            "--release",
            "25")
        .compile(source);
  }

  private Compilation compile(JavaFileObject source) {
    return compile("", source);
  }

  private static JavaFileObject src(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  /** {@code stateT.mapT(f)} — the function supplied, the leading Monad forgotten. */
  private static final JavaFileObject BAD_STATE_T =
      src(
          "test.BadStateTMapT",
          """
          package test;
          import org.higherkindedj.hkt.state_t.StateT;
          import org.higherkindedj.hkt.optional.OptionalKind;
          import org.higherkindedj.hkt.Kind;
          import org.higherkindedj.hkt.state.StateTuple;
          import java.util.function.Function;
          public class BadStateTMapT {
              void m(
                  StateT<Integer, OptionalKind.Witness, String> st,
                  Function<
                          Kind<OptionalKind.Witness, StateTuple<Integer, String>>,
                          Kind<OptionalKind.Witness, StateTuple<Integer, String>>>
                      f) {
                  st.mapT(f);
              }
          }
          """);

  @Test
  @DisplayName("StateT.mapT(f) without the leading Monad -> companion + doc link")
  void stateTMapT_missingMonad_emitsCompanion() {
    Compilation c = compile(BAD_STATE_T);
    assertThat(c).failed(); // javac's own "mapT cannot be applied" error
    assertThat(c).hadErrorContaining("StateT.mapT(...) needs the target Monad<G>");
    assertThat(c).hadErrorContaining("transformers/common_errors.html");
  }

  @Test
  @DisplayName("StateT.mapT(monad, f) correctly -> no companion, compiles")
  void stateTMapT_withMonad_noCompanion() {
    Compilation c =
        compile(
            src(
                "test.GoodStateTMapT",
                """
                package test;
                import org.higherkindedj.hkt.state_t.StateT;
                import org.higherkindedj.hkt.optional.OptionalKind;
                import org.higherkindedj.hkt.optional.OptionalMonad;
                import org.higherkindedj.hkt.Kind;
                import org.higherkindedj.hkt.state.StateTuple;
                import java.util.function.Function;
                public class GoodStateTMapT {
                    void m(
                        StateT<Integer, OptionalKind.Witness, String> st,
                        Function<
                                Kind<OptionalKind.Witness, StateTuple<Integer, String>>,
                                Kind<OptionalKind.Witness, StateTuple<Integer, String>>>
                            f) {
                        var r = st.mapT(OptionalMonad.INSTANCE, f);
                    }
                }
                """));
    assertThat(c).succeeded();
  }

  @Test
  @DisplayName("EitherT.mapT(f) (one-arg, correct for non-StateT) -> no false positive")
  void otherTransformerMapT_noCompanion() {
    Compilation c =
        compile(
            src(
                "test.GoodEitherTMapT",
                """
                package test;
                import org.higherkindedj.hkt.either_t.EitherT;
                import org.higherkindedj.hkt.either.Either;
                import org.higherkindedj.hkt.optional.OptionalKind;
                import org.higherkindedj.hkt.Kind;
                import java.util.function.Function;
                public class GoodEitherTMapT {
                    void m(
                        EitherT<OptionalKind.Witness, String, Integer> et,
                        Function<
                                Kind<OptionalKind.Witness, Either<String, Integer>>,
                                Kind<OptionalKind.Witness, Either<String, Integer>>>
                            f) {
                        var r = et.mapT(f);
                    }
                }
                """));
    assertThat(c).succeeded();
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("disable=state-t-mapt-arity suppresses the companion (javac error remains)")
    void disabled() {
      Compilation c = compile("disable=state-t-mapt-arity", BAD_STATE_T);
      assertThat(c).failed(); // javac's own mapT error is unaffected by our config
      org.assertj.core.api.Assertions.assertThat(
              c.diagnostics().stream()
                  .anyMatch(
                      d ->
                          String.valueOf(d.getMessage(null)).contains("needs the target Monad<G>")))
          .as("the companion must be suppressed when the check is disabled")
          .isFalse();
    }

    @Test
    @DisplayName("severity=warn downgrades the companion to a warning")
    void warn() {
      Compilation c = compile("severity=warn", BAD_STATE_T);
      assertThat(c).hadWarningContaining("StateT.mapT(...) needs the target Monad<G>");
    }
  }
}
