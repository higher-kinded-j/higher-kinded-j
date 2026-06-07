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

@DisplayName("WitnessArityChecker")
class WitnessArityCheckerTest {

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
            d -> String.valueOf(d.getMessage(null)).contains("used as a higher-kinded witness"));
  }

  private static final JavaFileObject UNBOUNDED_PARAM =
      src(
          "test.Unbounded",
          """
          package test;
          import org.higherkindedj.hkt.Monad;
          public class Unbounded {
              <F> void use(Monad<F> m) {}
          }
          """);

  @Nested
  @DisplayName("flags a witness that is not WitnessArity")
  class TruePositives {

    @Test
    @DisplayName("unbounded type parameter used as Monad<F>")
    void unboundedTypeParam() {
      Compilation c = compile(UNBOUNDED_PARAM);
      assertThat(c).failed(); // javac's own not.within.bounds error
      assertThat(c)
          .hadErrorContaining(
              "Type parameter F is used as a higher-kinded witness but is not "
                  + "WitnessArity-bounded");
    }

    @Test
    @DisplayName("plain Witness class used as Kind<W,A>")
    void witnessWithoutArity() {
      Compilation c =
          compile(
              src(
                  "test.PlainW",
                  """
                  package test;
                  import org.higherkindedj.hkt.Kind;
                  public class PlainW {
                      static final class W {}
                      Kind<W, String> k;
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadErrorContaining(
              "W is used as a higher-kinded witness but does not implement WitnessArity");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("properly bounded <F extends WitnessArity<Unary>>")
    void boundedParam() {
      Compilation c =
          compile(
              src(
                  "test.Bounded",
                  """
                  package test;
                  import org.higherkindedj.hkt.Monad;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.TypeArity;
                  public class Bounded {
                      <F extends WitnessArity<TypeArity.Unary>> void use(Monad<F> m) {}
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("intersection-bounded <F extends Serializable & WitnessArity<Unary>>")
    void intersectionBoundedParam() {
      // Regression: erasing the upper bound collapses the intersection to its first member
      // (Serializable) and would false-positive on this valid code.
      Compilation c =
          compile(
              src(
                  "test.Intersection",
                  """
                  package test;
                  import java.io.Serializable;
                  import org.higherkindedj.hkt.Monad;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.TypeArity;
                  public class Intersection {
                      <F extends Serializable & WitnessArity<TypeArity.Unary>> void use(
                          Monad<F> m) {}
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("correct HKJ-style witness")
    void correctHkjWitness() {
      Compilation c =
          compile(
              src(
                  "test.Good",
                  """
                  package test;
                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.TypeArity;
                  public interface Good<A> extends Kind<Good.Witness, A> {
                      final class Witness implements WitnessArity<TypeArity.Unary> {}
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("real library Kind usage (OptionalKind.Witness)")
    void realLibraryWitness() {
      Compilation c =
          compile(
              src(
                  "test.Lib",
                  """
                  package test;
                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.optional.OptionalKind;
                  public class Lib {
                      Kind<OptionalKind.Witness, String> k;
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    @Test
    @DisplayName("disable=witness-arity suppresses the companion (javac error remains)")
    void disabled() {
      Compilation c = compile("disable=witness-arity", UNBOUNDED_PARAM);
      assertThat(c).failed();
      Assertions.assertThat(mentionsCompanion(c)).isFalse();
    }

    @Test
    @DisplayName("severity=warn downgrades the companion to a warning")
    void warn() {
      Compilation c = compile("severity=warn", UNBOUNDED_PARAM);
      assertThat(c).hadWarningContaining("not WitnessArity-bounded");
    }
  }
}
