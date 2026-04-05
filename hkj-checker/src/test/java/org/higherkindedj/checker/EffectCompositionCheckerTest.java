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

/** Behavioral tests for {@link EffectCompositionChecker}. */
@DisplayName("EffectCompositionChecker")
class EffectCompositionCheckerTest {

  private Compilation compileWithChecker(JavaFileObject... sources) {
    return javac()
        .withOptions("-Xplugin:HKJChecker", "--enable-preview", "--release", "25")
        .compile(sources);
  }

  @Nested
  @DisplayName("correct usage")
  class CorrectUsageTests {

    @Test
    @DisplayName("compiles without errors when no effect composition is used")
    void noEffectComposition_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.SimpleCode",
              """
              package test;

              public class SimpleCode {
                  public void doSomething() {
                      int x = 1 + 2;
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("compiles without errors when combine-like method is on non-Interpreters class")
    void nonInterpretersCombine_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.NotInterpreters",
              """
              package test;

              public class NotInterpreters {
                  public static String combine(String a) { return a; }

                  public static void test() {
                      combine("hello");
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("compiles without errors when boundTo-like method is on non-Ops class")
    void nonOpsBoundTo_compiles() {
      JavaFileObject source =
          JavaFileObjects.forSourceString(
              "test.NotOps",
              """
              package test;

              public class NotOps {
                  public static Object boundTo(String a) { return a; }

                  public static void test() {
                      boundTo("hello");
                  }
              }
              """);

      Compilation compilation = compileWithChecker(source);
      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("class hierarchy")
  class ClassHierarchyTests {

    @Test
    @DisplayName("Checker should extend TreeScanner")
    void extendsTreeScanner() {
      org.assertj.core.api.Assertions.assertThat(
              EffectCompositionChecker.class.getSuperclass().getSimpleName())
          .isEqualTo("TreeScanner");
    }

    @Test
    @DisplayName("Checker should accept Trees in constructor")
    void acceptsTreesInConstructor() {
      org.assertj.core.api.Assertions.assertThat(
              EffectCompositionChecker.class.getConstructors())
          .hasSize(1);
      org.assertj.core.api.Assertions.assertThat(
              EffectCompositionChecker.class.getConstructors()[0].getParameterTypes()[0]
                  .getSimpleName())
          .isEqualTo("Trees");
    }
  }
}
