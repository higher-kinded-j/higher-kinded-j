// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RawKindChecker")
class RawKindCheckerTest {

  private Compilation compile(String pluginArgs, JavaFileObject source) {
    return com.google.testing.compile.Compiler.javac()
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

  private static long rawKindDiagnostics(Compilation c, Diagnostic.Kind kind) {
    return c.diagnostics().stream()
        .filter(d -> d.getKind() == kind)
        .filter(d -> String.valueOf(d.getMessage(null)).contains("drops its witness type argument"))
        .count();
  }

  private static boolean mentionsRawKind(Compilation c) {
    return c.diagnostics().stream()
        .anyMatch(
            d -> String.valueOf(d.getMessage(null)).contains("drops its witness type argument"));
  }

  @Nested
  @DisplayName("flags raw Kind / Kind2")
  class TruePositives {

    @Test
    @DisplayName("raw Kind local variable")
    void rawLocalVariable() {
      JavaFileObject source =
          src(
              "test.RawVar",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              public class RawVar {
                void use() {
                  Kind raw = null;
                }
              }
              """);
      Assertions.assertThat(rawKindDiagnostics(compile(source), Diagnostic.Kind.WARNING))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("raw Kind method parameter")
    void rawParameter() {
      JavaFileObject source =
          src(
              "test.RawParam",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              public class RawParam {
                void use(Kind raw) {}
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }

    @Test
    @DisplayName("cast to raw Kind")
    void rawCast() {
      JavaFileObject source =
          src(
              "test.RawCast",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              public class RawCast {
                Object use(Object x) {
                  return (Kind) x;
                }
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }

    @Test
    @DisplayName("raw Kind2 field")
    void rawKind2Field() {
      JavaFileObject source =
          src(
              "test.RawKind2",
              """
              package test;
              import org.higherkindedj.hkt.Kind2;
              public class RawKind2 {
                Kind2 field;
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }

    @Test
    @DisplayName("raw Kind in a method return type")
    void rawMethodReturnType() {
      JavaFileObject source =
          src(
              "test.RawReturn",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              public class RawReturn {
                Kind use() {
                  return null;
                }
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }

    @Test
    @DisplayName("raw Kind nested inside a parameterised type")
    void rawNestedInParameterised() {
      JavaFileObject source =
          src(
              "test.RawNested",
              """
              package test;
              import java.util.List;
              import org.higherkindedj.hkt.Kind;
              public class RawNested {
                List<Kind> rawList;
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }

    @Test
    @DisplayName("raw Kind array")
    void rawArray() {
      JavaFileObject source =
          src(
              "test.RawArray",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              public class RawArray {
                Kind[] arr;
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }

    @Test
    @DisplayName("raw Kind in a wildcard bound")
    void rawWildcardBound() {
      JavaFileObject source =
          src(
              "test.RawWildcard",
              """
              package test;
              import java.util.List;
              import org.higherkindedj.hkt.Kind;
              public class RawWildcard {
                List<? extends Kind> xs;
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isTrue();
    }
  }

  @Nested
  @DisplayName("does not flag parameterised Kind")
  class TrueNegatives {

    @Test
    @DisplayName("Kind<Witness, A> is fine")
    void parameterisedKind() {
      JavaFileObject source =
          src(
              "test.GoodKind",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.maybe.MaybeKind;
              public class GoodKind {
                void use(Kind<MaybeKind.Witness, String> k) {}
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isFalse();
    }

    @Test
    @DisplayName("Kind<Witness, ?> wildcard is fine")
    void wildcardKind() {
      JavaFileObject source =
          src(
              "test.WildcardKind",
              """
              package test;
              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.maybe.MaybeKind;
              public class WildcardKind {
                void use(Kind<MaybeKind.Witness, ?> k) {}
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isFalse();
    }

    @Test
    @DisplayName("an unrelated raw type is not flagged")
    void unrelatedRawType() {
      JavaFileObject source =
          src(
              "test.RawList",
              """
              package test;
              import java.util.List;
              public class RawList {
                List raw;
              }
              """);
      Assertions.assertThat(mentionsRawKind(compile(source))).isFalse();
    }
  }

  @Nested
  @DisplayName("honours configuration")
  class Configuration {

    private static final JavaFileObject RAW_VAR =
        src(
            "test.RawVar2",
            """
            package test;
            import org.higherkindedj.hkt.Kind;
            public class RawVar2 {
              void use() {
                Kind raw = null;
              }
            }
            """);

    @Test
    @DisplayName("disable=raw-kind suppresses the diagnostic")
    void disabled() {
      Assertions.assertThat(mentionsRawKind(compile("disable=raw-kind", RAW_VAR))).isFalse();
    }

    @Test
    @DisplayName("severity:raw-kind=error promotes it to an error")
    void promotedToError() {
      Compilation c = compile("severity:raw-kind=error", RAW_VAR);
      Assertions.assertThat(rawKindDiagnostics(c, Diagnostic.Kind.ERROR)).isEqualTo(1);
    }
  }
}
