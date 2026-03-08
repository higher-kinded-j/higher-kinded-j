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

@DisplayName("PathTypeMismatchChecker")
class PathTypeMismatchCheckerTest {

  /**
   * Compiles the given source with the HKJChecker plugin enabled.
   *
   * <p>Note: The compile-testing library runs javac in-process. We pass the plugin argument to
   * activate our checker.
   */
  private Compilation compileWithChecker(JavaFileObject... sources) {
    return javac()
        .withOptions("-Xplugin:HKJChecker", "--enable-preview", "--release", "25")
        .compile(sources);
  }

  @Nested
  @DisplayName("correct usage")
  class CorrectUsageTests {

    @Test
    @DisplayName("compiles without errors when no Path types are used")
    void correctUsage_noPathTypes_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.SimpleCode",
              """
              package test;

              public class SimpleCode {
                  public void doSomething() {
                      int x = 1 + 2;
                      String s = String.valueOf(x);
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("compiles without errors when Path-like method names are used on non-Path types")
    void correctUsage_nonPathVia_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.NonPathVia",
              """
              package test;

              public class NonPathVia {
                  public String via(java.util.function.Function<String, String> f) {
                      return f.apply("hello");
                  }

                  public void test() {
                      via(s -> s.toUpperCase());
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("correct usage with real Path types")
  class CorrectPathUsageTests {

    @Test
    @DisplayName("compiles without errors when same Path type is used throughout via chain")
    void correctUsage_sameTypeVia_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.SameTypeVia",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class SameTypeVia {
                  public void sameTypeChaining() {
                      Path.just(1).via(_ -> Path.just(2));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("compiles without errors when same Path type is used in zipWith")
    void correctUsage_sameTypeZipWith_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.SameTypeZipWith",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class SameTypeZipWith {
                  public void sameTypeZipWith() {
                      Path.just(1).zipWith(Path.just(2), Integer::sum);
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("compiles without errors when same Path type is used in then")
    void correctUsage_sameTypeThen_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.SameTypeThen",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class SameTypeThen {
                  public void sameTypeThen() {
                      Path.just(1).then(() -> Path.just(2));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("mismatch detection")
  class MismatchDetectionTests {

    @Test
    @DisplayName("reports error when via lambda returns a different Path type")
    void mismatch_inVia_reportsError() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.MismatchVia",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class MismatchVia {
                  public void mismatchedVia() {
                      Path.just(1).via(_ -> Path.io(() -> 2));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in via()");
      assertThat(compilation).hadErrorContaining("expected MaybePath but received IOPath");
    }

    @Test
    @DisplayName("reports error when zipWith argument is a different Path type")
    void mismatch_inZipWith_reportsError() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.MismatchZipWith",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class MismatchZipWith {
                  public void mismatchedZipWith() {
                      Path.just(1).zipWith(Path.right("x"), (a, b) -> a + b.length());
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in zipWith()");
      assertThat(compilation).hadErrorContaining("expected MaybePath but received EitherPath");
    }

    @Test
    @DisplayName("reports error when then supplier returns a different Path type")
    void mismatch_inThen_reportsError() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.MismatchThen",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class MismatchThen {
                  public void mismatchedThen() {
                      Path.right("a").then(() -> Path.just(1));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in then()");
      assertThat(compilation).hadErrorContaining("expected EitherPath but received MaybePath");
    }

    @Test
    @DisplayName("reports error when recoverWith lambda returns a different Path type")
    void mismatch_inRecoverWith_reportsError() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.MismatchRecoverWith",
              """
              package test;

              import org.higherkindedj.hkt.Semigroup;
              import org.higherkindedj.hkt.effect.Path;

              public class MismatchRecoverWith {
                  private static final Semigroup<String> CONCAT = (a, b) -> a + b;

                  public void mismatchedRecoverWith() {
                      Path.right(1).recoverWith(_ -> Path.valid(42, CONCAT));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in recoverWith()");
      assertThat(compilation).hadErrorContaining("expected EitherPath but received ValidationPath");
    }

    @Test
    @DisplayName("reports error when orElse supplier returns a different Path type")
    void mismatch_inOrElse_reportsError() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.MismatchOrElse",
              """
              package test;

              import org.higherkindedj.hkt.Semigroup;
              import org.higherkindedj.hkt.effect.Path;

              public class MismatchOrElse {
                  private static final Semigroup<String> CONCAT = (a, b) -> a + b;

                  public void mismatchedOrElse() {
                      Path.<String, Integer>right(1).orElse(() -> Path.valid(2, CONCAT));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in orElse()");
      assertThat(compilation).hadErrorContaining("expected EitherPath but received ValidationPath");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositiveTests {

    @Test
    @DisplayName("does not report error for generic Chainable receiver")
    void noFalsePositive_genericReceiver_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.GenericReceiver",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;
              import org.higherkindedj.hkt.effect.capability.Chainable;

              public class GenericReceiver {
                  public <A> void genericReceiver(Chainable<A> path) {
                      path.via(_ -> Path.just(1));
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("plugin registration")
  class PluginRegistration {

    @Test
    @DisplayName("plugin loads and processes source without crashing")
    void plugin_loadsSuccessfully() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.Empty",
              """
              package test;
              public class Empty {}
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }
  }
}
