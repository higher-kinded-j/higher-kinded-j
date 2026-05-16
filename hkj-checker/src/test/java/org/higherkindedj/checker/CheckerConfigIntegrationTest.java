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

/**
 * Integration tests proving plugin-argument configuration takes effect through a real compilation.
 *
 * <p>The {@code -Xplugin} value must be a single option string; javac splits it on whitespace into
 * the plugin name followed by the checker arguments.
 */
@DisplayName("CheckerConfig (integration)")
class CheckerConfigIntegrationTest {

  /** A source that triggers the path-type-mismatch check (MaybePath receiver, IOPath in via). */
  private static JavaFileObject mismatchSource() {
    return JavaFileObjects.forSourceString(
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
  }

  private static Compilation compileWith(String pluginOption) {
    return javac()
        .withOptions(pluginOption, "--enable-preview", "--release", "25")
        .compile(mismatchSource());
  }

  @Nested
  @DisplayName("baseline")
  class Baseline {

    @Test
    @DisplayName("without config the mismatch is a build-failing error")
    void noConfig_isError() {
      Compilation compilation = compileWith("-Xplugin:HKJChecker");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Path type mismatch in via()");
    }
  }

  @Nested
  @DisplayName("disable")
  class Disable {

    @Test
    @DisplayName("disable=path-type-mismatch suppresses the diagnostic entirely")
    void disable_suppressesDiagnostic() {
      // The fixture is both a type mismatch and a discarded effect; disable both so
      // this asserts only that disabling path-type-mismatch removes its diagnostic.
      Compilation compilation =
          compileWith("-Xplugin:HKJChecker disable=path-type-mismatch,discarded-effect");

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("severity")
  class Severity {

    @Test
    @DisplayName("severity=warn downgrades the error to a non-fatal warning")
    void severityWarn_downgradesToWarning() {
      Compilation compilation = compileWith("-Xplugin:HKJChecker severity=warn");

      assertThat(compilation).succeeded();
      assertThat(compilation).hadWarningContaining("Path type mismatch in via()");
    }

    @Test
    @DisplayName("the downgraded warning still carries the documentation link")
    void severityWarn_keepsDocLink() {
      Compilation compilation = compileWith("-Xplugin:HKJChecker severity=warn");

      assertThat(compilation)
          .hadWarningContaining(
              "https://higher-kinded-j.github.io/latest/effect/compiler_errors.html");
    }
  }
}
