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

@DisplayName("MapReturnsPathChecker")
class MapReturnsPathCheckerTest {

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
        .anyMatch(d -> String.valueOf(d.getMessage(null)).contains("map() here nests the effect"));
  }

  private static final JavaFileObject MAP_NESTS =
      src(
          "test.MapNests",
          """
          package test;
          import org.higherkindedj.hkt.effect.Path;
          import org.higherkindedj.hkt.effect.EitherPath;
          public class MapNests {
              Object run(EitherPath<String, Integer> p) {
                  return p.map(x -> Path.<String, Integer>right(x + 1));
              }
          }
          """);

  @Test
  @DisplayName("map returning the same Path type -> warning (build still passes)")
  void sameCategoryNesting_warns() {
    Compilation c = compile(MAP_NESTS);
    assertThat(c).succeeded(); // warn-default: never breaks the build
    assertThat(c).hadWarningContaining("map() here nests the effect");
    assertThat(c).hadWarningContaining("use");
    assertThat(c).hadWarningContaining("via()");
    assertThat(c).hadWarningContaining("effect/compiler_errors.html");
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("map returning a plain value")
    void plainValue() {
      Compilation c =
          compile(
              src(
                  "test.Plain",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class Plain {
                      EitherPath<String, String> run(EitherPath<String, Integer> p) {
                          return p.map(x -> "v" + x);
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("plain-Java Optional.map returning Optional is not flagged")
    void plainJavaMap() {
      Compilation c =
          compile(
              src(
                  "test.Jdk",
                  """
                  package test;
                  import java.util.Optional;
                  public class Jdk {
                      Optional<Optional<Integer>> run(Optional<Integer> o) {
                          return o.map(x -> Optional.of(x + 1));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("method reference is skipped")
    void methodReference() {
      Compilation c =
          compile(
              src(
                  "test.MRef",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class MRef {
                      EitherPath<String, Integer> toPath(Integer x) {
                          return Path.<String, Integer>right(x);
                      }
                      Object run(EitherPath<String, Integer> p) {
                          return p.map(this::toPath);
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("cross-category map (ListPath -> EitherPath) is not flagged")
    void crossCategory() {
      Compilation c =
          compile(
              src(
                  "test.Cross",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.ListPath;
                  public class Cross {
                      Object run(ListPath<Integer> xs) {
                          return xs.map(x -> Path.<String, Integer>right(x));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c))
          .as("cross-category is the ambiguous case the same-category rule excludes")
          .isFalse();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("warn-default: never breaks the build")
    void warnDefault() {
      Compilation c = compile(MAP_NESTS);
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("map() here nests the effect");
    }

    @Test
    @DisplayName("disable=map-nests-effect suppresses it entirely")
    void disabled() {
      Compilation c = compile("disable=map-nests-effect", MAP_NESTS);
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }
  }
}
