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

@DisplayName("KindValueNarrowChecker")
class KindValueNarrowCheckerTest {

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

  private static boolean mentionsCompanion(Compilation c) {
    return c.diagnostics().stream()
        .anyMatch(
            d -> String.valueOf(d.getMessage(null)).contains("value() is defined on the concrete"));
  }

  private static final JavaFileObject BARE_KIND_VALUE =
      src(
          "test.BareKind",
          """
          package test;
          import org.higherkindedj.hkt.Kind;
          import org.higherkindedj.hkt.optional.OptionalKind;
          public class BareKind {
              void m(Kind<OptionalKind.Witness, String> workflow) {
                  var v = workflow.value();
              }
          }
          """);

  @Test
  @DisplayName("value() on a bare Kind -> companion + doc link")
  void valueOnKind_emitsCompanion() {
    Compilation c = compile(BARE_KIND_VALUE);
    assertThat(c).failed(); // javac's own "cannot find symbol" error
    assertThat(c).hadErrorContaining("value() is defined on the concrete transformer");
    assertThat(c).hadErrorContaining("transformers/common_errors.html");
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("value() on a concrete transformer compiles, no companion")
    void concreteTransformer() {
      Compilation c =
          compile(
              src(
                  "test.Concrete",
                  """
                  package test;
                  import org.higherkindedj.hkt.either_t.EitherT;
                  import org.higherkindedj.hkt.optional.OptionalKind;
                  public class Concrete {
                      void m(EitherT<OptionalKind.Witness, String, Integer> et) {
                          var v = et.value();
                      }
                  }
                  """));
      assertThat(c).succeeded();
      org.assertj.core.api.Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("value() on an unrelated user type is not flagged")
    void unrelatedType() {
      Compilation c =
          compile(
              src(
                  "test.Unrelated",
                  """
                  package test;
                  public class Unrelated {
                      static final class Box { int value() { return 1; } }
                      void m(Box b) {
                          var v = b.value();
                      }
                  }
                  """));
      assertThat(c).succeeded();
      org.assertj.core.api.Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("disable=kind-value-narrow suppresses the companion (javac error remains)")
    void disabled() {
      Compilation c = compile("disable=kind-value-narrow", BARE_KIND_VALUE);
      assertThat(c).failed(); // javac's own error is unaffected by our config
      org.assertj.core.api.Assertions.assertThat(mentionsCompanion(c))
          .as("companion suppressed when the check is disabled")
          .isFalse();
    }

    @Test
    @DisplayName("severity=warn downgrades the companion to a warning")
    void warn() {
      Compilation c = compile("severity=warn", BARE_KIND_VALUE);
      assertThat(c).hadWarningContaining("value() is defined on the concrete transformer");
    }
  }
}
