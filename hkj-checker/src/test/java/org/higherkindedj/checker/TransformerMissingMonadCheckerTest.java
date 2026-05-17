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

@DisplayName("TransformerMissingMonadChecker")
class TransformerMissingMonadCheckerTest {

  private Compilation compile(JavaFileObject source) {
    return javac()
        .withOptions("-Xplugin:HKJChecker", "--enable-preview", "--release", "25")
        .compile(source);
  }

  private static JavaFileObject src(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  @Test
  @DisplayName("zero-arg EitherTMonad construction -> companion diagnostic + doc link")
  void eitherT_zeroArg_emitsCompanion() {
    Compilation c =
        compile(
            src(
                "test.MissingEitherT",
                """
                package test;
                import org.higherkindedj.hkt.either_t.EitherTMonad;
                import org.higherkindedj.hkt.optional.OptionalKind;
                public class MissingEitherT {
                    void m() {
                        var x = new EitherTMonad<OptionalKind.Witness, String>();
                    }
                }
                """));
    assertThat(c).failed(); // javac's own "constructor cannot be applied" error
    assertThat(c).hadErrorContaining("EitherTMonad needs the outer Monad<F>");
    assertThat(c).hadErrorContaining("transformers/common_errors.html");
  }

  @Test
  @DisplayName("zero-arg WriterTMonad construction -> companion also mentions Monoid<W>")
  void writerT_zeroArg_mentionsMonoid() {
    Compilation c =
        compile(
            src(
                "test.MissingWriterT",
                """
                package test;
                import org.higherkindedj.hkt.writer_t.WriterTMonad;
                import org.higherkindedj.hkt.optional.OptionalKind;
                public class MissingWriterT {
                    void m() {
                        var x = new WriterTMonad<OptionalKind.Witness, String>();
                    }
                }
                """));
    assertThat(c).failed();
    assertThat(c).hadErrorContaining("WriterTMonad needs the outer Monad<F>");
    assertThat(c).hadErrorContaining("Monoid<W>");
  }

  @Test
  @DisplayName("correctly constructed transformer monad -> no companion, compiles")
  void eitherT_withOuterMonad_noCompanion() {
    Compilation c =
        compile(
            src(
                "test.GoodEitherT",
                """
                package test;
                import org.higherkindedj.hkt.either_t.EitherTMonad;
                import org.higherkindedj.hkt.optional.OptionalKind;
                import org.higherkindedj.hkt.optional.OptionalMonad;
                public class GoodEitherT {
                    void m() {
                        var x =
                            new EitherTMonad<OptionalKind.Witness, String>(OptionalMonad.INSTANCE);
                    }
                }
                """));
    assertThat(c).succeeded();
  }

  @Test
  @DisplayName("same-named user type with a no-arg constructor -> no false positive")
  void unrelatedSameName_noCompanion() {
    Compilation c =
        compile(
            src(
                "test.UnrelatedEitherTMonad",
                """
                package test;
                public class UnrelatedEitherTMonad {
                    static class EitherTMonad<F, L> {}
                    void m() {
                        var x = new EitherTMonad<String, Integer>();
                    }
                }
                """));
    assertThat(c).succeeded();
  }
}
