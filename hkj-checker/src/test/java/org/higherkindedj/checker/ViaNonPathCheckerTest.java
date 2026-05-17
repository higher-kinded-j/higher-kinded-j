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

@DisplayName("ViaNonPathChecker")
class ViaNonPathCheckerTest {

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
            d -> String.valueOf(d.getMessage(null)).contains("needs a function that returns"));
  }

  private static final JavaFileObject VIA_PLAIN =
      src(
          "test.ViaPlain",
          """
          package test;
          import org.higherkindedj.hkt.effect.EitherPath;
          public class ViaPlain {
              EitherPath<String, Integer> run(EitherPath<String, Integer> p) {
                  return p.via(x -> x.toString());
              }
          }
          """);

  @Nested
  @DisplayName("flags via/then given a non-Path-returning function")
  class TruePositives {

    @Test
    @DisplayName("via lambda returning a plain value")
    void viaPlain() {
      Compilation c = compile(VIA_PLAIN);
      assertThat(c).failed(); // javac's own "method via cannot be applied" error
      assertThat(c).hadErrorContaining("via() needs a function that returns a Path");
      assertThat(c).hadErrorContaining("returns String");
      assertThat(c).hadErrorContaining("effect/compiler_errors.html");
    }

    @Test
    @DisplayName("then supplier returning a plain value")
    void thenPlain() {
      Compilation c =
          compile(
              src(
                  "test.ThenPlain",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class ThenPlain {
                      EitherPath<String, Integer> run(EitherPath<String, Integer> p) {
                          return p.then(() -> "plain");
                      }
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadErrorContaining("then() needs a function that returns a Path");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("via with a Path-returning lambda compiles, no companion")
    void viaPath() {
      Compilation c =
          compile(
              src(
                  "test.ViaPath",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class ViaPath {
                      EitherPath<String, Integer> run(EitherPath<String, Integer> p) {
                          return p.via(x -> Path.<String, Integer>right(x + 1));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      org.assertj.core.api.Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("method reference is skipped (no false positive, javac error stands alone)")
    void methodReference() {
      Compilation c =
          compile(
              src(
                  "test.MRef",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class MRef {
                      EitherPath<String, Integer> run(EitherPath<String, Integer> p) {
                          return p.via(Object::toString);
                      }
                  }
                  """));
      assertThat(c).failed(); // javac still rejects it
      org.assertj.core.api.Assertions.assertThat(mentionsCompanion(c))
          .as("method refs are deliberately skipped to stay false-positive-free")
          .isFalse();
    }

    @Test
    @DisplayName("flatMap on a non-Chainable receiver (java Optional) is not flagged")
    void nonChainableReceiver() {
      Compilation c =
          compile(
              src(
                  "test.NotChain",
                  """
                  package test;
                  import java.util.Optional;
                  public class NotChain {
                      Optional<Integer> run(Optional<Integer> o) {
                          return o.flatMap(x -> Optional.of(x + 1));
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
    @DisplayName("disable=via-non-path suppresses the companion (javac error remains)")
    void disabled() {
      Compilation c = compile("disable=via-non-path", VIA_PLAIN);
      assertThat(c).failed();
      org.assertj.core.api.Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("severity=warn downgrades the companion to a warning")
    void warn() {
      Compilation c = compile("severity=warn", VIA_PLAIN);
      assertThat(c).hadWarningContaining("via() needs a function that returns a Path");
    }
  }
}
