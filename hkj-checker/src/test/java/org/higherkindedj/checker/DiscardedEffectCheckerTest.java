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

@DisplayName("DiscardedEffectChecker")
class DiscardedEffectCheckerTest {

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

  @Nested
  @DisplayName("flags a discarded lazy effect")
  class TruePositives {

    @Test
    @DisplayName("bare Path chain statement")
    void bareChain() {
      Compilation c =
          compile(
              src(
                  "test.BareChain",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  public class BareChain {
                      void m() {
                          Path.<String, Integer>right(1).map(x -> x + 1);
                      }
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadErrorContaining("EitherPath is built but never used");
      assertThat(c).hadErrorContaining("effect/capabilities.html");
    }

    @Test
    @DisplayName("bare Path.io statement (classic silent no-op)")
    void bareIo() {
      Compilation c =
          compile(
              src(
                  "test.BareIo",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  public class BareIo {
                      void m() {
                          Path.io(() -> { System.out.println("fx"); return 1; });
                      }
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadErrorContaining("IOPath is built but never used");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("effect that is actually run (unsafeRun) compiles")
    void runEffect() {
      Compilation c =
          compile(
              src(
                  "test.RunEffect",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  public class RunEffect {
                      void m() {
                          Path.io(() -> 1).unsafeRun();
                      }
                  }
                  """));
      assertThat(c).succeeded();
    }

    @Test
    @DisplayName("effect assigned to a variable is consumed, not discarded")
    void assignedEffect() {
      Compilation c =
          compile(
              src(
                  "test.AssignedEffect",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class AssignedEffect {
                      private EitherPath<String, Integer> field;
                      void m() {
                          field = Path.<String, Integer>right(1);
                      }
                  }
                  """));
      assertThat(c).succeeded();
    }

    @Test
    @DisplayName("returned effect is consumed, not a statement")
    void returnedEffect() {
      Compilation c =
          compile(
              src(
                  "test.ReturnedEffect",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class ReturnedEffect {
                      EitherPath<String, Integer> m() {
                          return Path.<String, Integer>right(1);
                      }
                  }
                  """));
      assertThat(c).succeeded();
    }

    @Test
    @DisplayName("local declared (not run) is the accepted scope boundary, not flagged")
    void declaredLocal() {
      Compilation c =
          compile(
              src(
                  "test.DeclaredLocal",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class DeclaredLocal {
                      @SuppressWarnings("unused")
                      void m() {
                          EitherPath<String, Integer> x = Path.<String, Integer>right(1);
                      }
                  }
                  """));
      assertThat(c).succeeded();
    }

    @Test
    @DisplayName("discarded non-HKJ fluent API is not flagged")
    void nonHkjFluent() {
      Compilation c =
          compile(
              src(
                  "test.NonHkjFluent",
                  """
                  package test;
                  public class NonHkjFluent {
                      void m() {
                          new StringBuilder().append("a").append("b");
                      }
                  }
                  """));
      assertThat(c).succeeded();
    }

    @Test
    @DisplayName("void method call that internally runs effects is not flagged")
    void voidCall() {
      Compilation c =
          compile(
              src(
                  "test.VoidCall",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  public class VoidCall {
                      void helper() { Path.io(() -> 1).unsafeRun(); }
                      void m() { helper(); }
                  }
                  """));
      assertThat(c).succeeded();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    private static final JavaFileObject DISCARD =
        src(
            "test.Discard",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            public class Discard {
                void m() { Path.<String, Integer>right(1).map(x -> x); }
            }
            """);

    @Test
    @DisplayName("disable=discarded-effect suppresses the diagnostic entirely")
    void disabled() {
      Compilation c = compile("disable=discarded-effect", DISCARD);
      assertThat(c).succeeded();
    }

    @Test
    @DisplayName("severity=warn downgrades to a warning, build still succeeds")
    void warn() {
      Compilation c = compile("severity=warn", DISCARD);
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("EitherPath is built but never used");
    }
  }
}
