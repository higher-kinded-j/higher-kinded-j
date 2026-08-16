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

@DisplayName("HKJCheckerPlugin")
class HKJCheckerPluginTest {

  /** Compiles the given sources with the checker enabled at its default severities. */
  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withOptions("-Xplugin:HKJChecker", "--enable-preview", "--release", "25")
        .compile(sources);
  }

  /**
   * Counts diagnostics whose message contains {@code fragment}. The fixture statement trips two
   * checks at once — {@code path-type-mismatch} and {@code discarded-effect} — so counting one of
   * them shows a gate is per check, not a blanket kill of the statement.
   */
  private static long diagnosticsMatching(Compilation compilation, String fragment) {
    return compilation.diagnostics().stream()
        .filter(d -> String.valueOf(d.getMessage(null)).contains(fragment))
        .count();
  }

  @Nested
  @DisplayName("getName")
  class GetName {

    @Test
    @DisplayName("returns 'HKJChecker'")
    void getName_returnsPluginName() {
      var plugin = new HKJCheckerPlugin();
      Assertions.assertThat(plugin.getName()).isEqualTo("HKJChecker");
    }

    @Test
    @DisplayName("matches the PLUGIN_NAME constant")
    void getName_matchesConstant() {
      var plugin = new HKJCheckerPlugin();
      Assertions.assertThat(plugin.getName()).isEqualTo(HKJCheckerPlugin.PLUGIN_NAME);
    }
  }

  @Nested
  @DisplayName("PLUGIN_NAME")
  class PluginName {

    @Test
    @DisplayName("is a non-empty string")
    void pluginName_isNonEmpty() {
      Assertions.assertThat(HKJCheckerPlugin.PLUGIN_NAME).isNotEmpty();
    }

    @Test
    @DisplayName("does not contain spaces")
    void pluginName_noSpaces() {
      Assertions.assertThat(HKJCheckerPlugin.PLUGIN_NAME).doesNotContain(" ");
    }
  }

  /**
   * The gate that keeps every check off annotation-processor output.
   *
   * <p>The fixture is a path-type mismatch, the checker's most severe diagnostic (a build-failing
   * error), so a generated unit escaping the gate would be impossible to miss.
   */
  @Nested
  @DisplayName("generated-source gate")
  class GeneratedSourceGate {

    /** A mismatched {@code via()} — MaybePath receiver, IOPath in the lambda. */
    private JavaFileObject mismatchSource(String annotation, String imports) {
      return JavaFileObjects.forSourceString(
          "test.MismatchVia",
          """
          package test;

          import org.higherkindedj.hkt.effect.Path;
          %s

          %s
          public class MismatchVia {
              public void mismatchedVia() {
                  Path.just(1).via(_ -> Path.io(() -> 2));
              }
          }
          """
              .formatted(imports, annotation));
    }

    @Test
    @DisplayName("hand-written sources are checked: the mismatch fails the build")
    void handWritten_isChecked() {
      Compilation compilation = compile(mismatchSource("", ""));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in via()");
    }

    @Test
    @DisplayName("the HKJ @Generated marker skips the unit")
    void hkjMarker_skipsUnit() {
      Compilation compilation =
          compile(
              mismatchSource(
                  "@Generated", "import org.higherkindedj.optics.annotations.Generated;"));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("javax.annotation.processing.Generated does NOT skip: it is hand-applied too")
    void javaxMarker_stillChecked() {
      // JaCoCo excludes any type carrying an annotation named Generated, so this marker
      // appears on hand-written code; honouring it would silently disable every check.
      Compilation compilation =
          compile(
              mismatchSource(
                  "@Generated(\"other-processor\")",
                  "import javax.annotation.processing.Generated;"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in via()");
    }

    @Test
    @DisplayName("a generated type is skipped wherever it sits in a multi-type file")
    void generatedSiblingIsSkipped() {
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "test.HandWritten",
                  """
                  package test;

                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.optics.annotations.Generated;

                  public class HandWritten {
                      public void mine() {
                          Path.just(1).via(_ -> Path.io(() -> 2));
                      }
                  }

                  @Generated
                  class GeneratedSibling {
                      public void theirs() {
                          Path.io(() -> 1).via(_ -> Path.just(2));
                      }
                  }
                  """));

      // The hand-written type is diagnosed; the generated sibling is not, and the
      // hand-written one is reported once rather than once per top-level type.
      Assertions.assertThat(diagnosticsMatching(compilation, "expected MaybePath")).isOne();
      Assertions.assertThat(diagnosticsMatching(compilation, "expected IOPath")).isZero();
    }

    @Test
    @DisplayName("a same-named marker from another package does not skip the unit (FQN gate)")
    void unrelatedMarker_stillChecked() {
      JavaFileObject lookalike =
          JavaFileObjects.forSourceString(
              "test.marker.Generated",
              """
              package test.marker;

              public @interface Generated {}
              """);
      Compilation compilation =
          compile(mismatchSource("@Generated", "import test.marker.Generated;"), lookalike);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in via()");
    }
  }

  /**
   * Per-declaration opt-out, the only way to accept a single deliberate call site: {@code disable=}
   * is project-wide.
   */
  @Nested
  @DisplayName("@SuppressWarnings gate")
  class SuppressWarningsGate {

    /** The same mismatched {@code via()}, with annotations placed on class and method. */
    private JavaFileObject mismatchSource(String onClass, String onMethod) {
      return JavaFileObjects.forSourceString(
          "test.MismatchVia",
          """
          package test;

          import org.higherkindedj.hkt.effect.Path;

          %s
          public class MismatchVia {
              %s
              public void mismatchedVia() {
                  Path.io(() -> 1).via(_ -> Path.just(2));
              }
          }
          """
              .formatted(onClass, onMethod));
    }

    private static final String MISMATCH = "Path type mismatch in via()";
    private static final String DISCARDED = "built but never used";

    @Test
    @DisplayName("the check id on the method silences that check and no other")
    void checkIdOnMethod() {
      Compilation compilation =
          compile(mismatchSource("", "@SuppressWarnings(\"path-type-mismatch\")"));

      Assertions.assertThat(diagnosticsMatching(compilation, MISMATCH)).isZero();
      Assertions.assertThat(diagnosticsMatching(compilation, DISCARDED)).isOne();
    }

    @Test
    @DisplayName("the check id on the enclosing class reaches the method inside it")
    void checkIdOnClass() {
      Compilation compilation =
          compile(mismatchSource("@SuppressWarnings(\"path-type-mismatch\")", ""));

      Assertions.assertThat(diagnosticsMatching(compilation, MISMATCH)).isZero();
      Assertions.assertThat(diagnosticsMatching(compilation, DISCARDED)).isOne();
    }

    @Test
    @DisplayName("'hkj-checker' silences every check at once")
    void blanketToken() {
      Compilation compilation = compile(mismatchSource("", "@SuppressWarnings(\"hkj-checker\")"));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("javac's blanket \"all\" is deliberately not honoured")
    void javacAllToken_stillChecked() {
      // "all" is common on legacy and generated code; honouring it would silently disable
      // every HKJ check there. Opting out of an HKJ check is spelled explicitly.
      Compilation compilation = compile(mismatchSource("", "@SuppressWarnings(\"all\")"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining(MISMATCH);
    }

    @Test
    @DisplayName("suppression does not carry from one compilation unit to the next")
    void doesNotLeakAcrossFiles() {
      JavaFileObject suppressed =
          JavaFileObjects.forSourceString(
              "test.Suppressed",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              @SuppressWarnings("hkj-checker")
              public class Suppressed {
                  public void mismatched() {
                      Path.just(1).via(_ -> Path.io(() -> 2));
                  }
              }
              """);
      JavaFileObject plain =
          JavaFileObjects.forSourceString(
              "test.Plain",
              """
              package test;

              import org.higherkindedj.hkt.effect.Path;

              public class Plain {
                  public void mismatched() {
                      Path.just(1).via(_ -> Path.io(() -> 2));
                  }
              }
              """);
      Compilation compilation = compile(suppressed, plain);

      // Exactly one file is suppressed; the scanner is reused across both.
      Assertions.assertThat(diagnosticsMatching(compilation, MISMATCH)).isOne();
    }

    @Test
    @DisplayName("an unrelated token leaves the check running")
    void unrelatedToken() {
      Compilation compilation = compile(mismatchSource("", "@SuppressWarnings(\"unchecked\")"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining(MISMATCH);
    }

    @Test
    @DisplayName("suppression ends with the declaration that carried it")
    void doesNotLeakToSiblings() {
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "test.MismatchVia",
                  """
                  package test;

                  import org.higherkindedj.hkt.effect.Path;

                  public class MismatchVia {
                      @SuppressWarnings("hkj-checker")
                      public void suppressed() {
                          Path.io(() -> 1).via(_ -> Path.just(2));
                      }

                      public void notSuppressed() {
                          Path.io(() -> 1).via(_ -> Path.just(2));
                      }
                  }
                  """));

      Assertions.assertThat(diagnosticsMatching(compilation, MISMATCH)).isOne();
      Assertions.assertThat(diagnosticsMatching(compilation, DISCARDED)).isOne();
    }
  }
}
