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
        .withOptions("-Xplugin:HKJChecker")
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
      assertThat(compilation).succeededWithoutWarnings();
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
      assertThat(compilation).succeededWithoutWarnings();
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
      assertThat(compilation).succeededWithoutWarnings();
    }
  }
}
