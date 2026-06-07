// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationNudgeChecker")
class MigrationNudgeCheckerTest {

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

  private static boolean nudged(Compilation c) {
    return c.diagnostics().stream()
        .anyMatch(d -> String.valueOf(d.getMessage(null)).contains(", but"));
  }

  private static final JavaFileObject RAW_FREE =
      src(
          "test.RawFree",
          """
          package test;
          import org.higherkindedj.hkt.free.Free;
          import org.higherkindedj.hkt.Kind;
          import org.higherkindedj.hkt.Functor;
          import org.higherkindedj.hkt.WitnessArity;
          import org.higherkindedj.hkt.TypeArity;
          public class RawFree {
              <F extends WitnessArity<TypeArity.Unary>, A> void m(
                  Kind<F, A> fa, Functor<F> fn, Kind<F, Free<F, A>> comp) {
                  var a = Free.liftF(fa, fn);
                  var b = Free.suspend(comp);
              }
          }
          """);

  @Nested
  @DisplayName("nudges (advisory; build still passes)")
  class TruePositives {

    @Test
    @DisplayName("Free.liftF / Free.suspend -> FreePath nudge")
    void rawFree() {
      Compilation c = compile(RAW_FREE);
      assertThat(c).succeeded(); // advisory: never breaks the build
      assertThat(c).hadWarningContaining("Free.liftF(...) works, but the FreePath");
      assertThat(c).hadWarningContaining("Free.suspend(...) works, but the FreePath");
    }

    @Test
    @DisplayName("InjectInstances factories -> @ComposeEffects nudge")
    void injectBoilerplate() {
      Compilation c =
          compile(
              src(
                  "test.RawInject",
                  """
                  package test;
                  import org.higherkindedj.hkt.inject.InjectInstances;
                  import org.higherkindedj.hkt.WitnessArity;
                  public class RawInject {
                      <F extends WitnessArity<?>, G extends WitnessArity<?>> void m() {
                          var i = InjectInstances.<F, G>injectLeft();
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c)
          .hadWarningContaining("InjectInstances.injectLeft(...) works, but @ComposeEffects");
    }

    @Test
    @DisplayName("static-import call site is still detected (element-based)")
    void staticImport() {
      Compilation c =
          compile(
              src(
                  "test.StaticImp",
                  """
                  package test;
                  import static org.higherkindedj.hkt.free.Free.suspend;
                  import org.higherkindedj.hkt.free.Free;
                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.TypeArity;
                  public class StaticImp {
                      <F extends WitnessArity<TypeArity.Unary>, A> void m(
                          Kind<F, Free<F, A>> comp) {
                          var b = suspend(comp);
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Free.suspend(...) works, but the FreePath");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("unrelated same-named methods are not flagged (FQN gate)")
    void unrelatedMethods() {
      Compilation c =
          compile(
              src(
                  "test.Unrelated",
                  """
                  package test;
                  public class Unrelated {
                      static <T> T liftF(T t) { return t; }
                      static String injectLeft() { return "x"; }
                      void m() {
                          var a = liftF("a");
                          var b = injectLeft();
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(nudged(c)).isFalse();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("warn-default: advisory, never breaks the build")
    void warnDefault() {
      Compilation c = compile(RAW_FREE);
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("works, but the FreePath");
    }

    @Test
    @DisplayName("disable=migration-nudge suppresses it")
    void disabled() {
      Compilation c = compile("disable=migration-nudge", RAW_FREE);
      assertThat(c).succeeded();
      Assertions.assertThat(nudged(c)).isFalse();
    }

    @Test
    @DisplayName("severity:migration-nudge=error promotes it (build fails)")
    void promoted() {
      Compilation c = compile("severity:migration-nudge=error", RAW_FREE);
      assertThat(c).failed();
      assertThat(c).hadErrorContaining("Free.liftF(...) works, but the FreePath");
    }
  }
}
