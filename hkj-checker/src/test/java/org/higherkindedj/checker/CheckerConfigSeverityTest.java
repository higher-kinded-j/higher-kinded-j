// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CheckerConfig per-check severity")
class CheckerConfigSeverityTest {

  @Nested
  @DisplayName("defaults")
  class Defaults {

    @Test
    @DisplayName("error-default checks resolve to ERROR")
    void errorDefault() {
      CheckerConfig c = CheckerConfig.parse();
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH)).isEqualTo(Diagnostic.Kind.ERROR);
      assertThat(c.severityFor(CheckerConfig.WITNESS_ARITY)).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("warn-default checks resolve to WARNING")
    void warnDefault() {
      CheckerConfig c = CheckerConfig.parse();
      assertThat(c.severityFor(CheckerConfig.ERROR_TYPE_MISMATCH))
          .isEqualTo(Diagnostic.Kind.WARNING);
      assertThat(c.severityFor(CheckerConfig.MAP_NESTS_EFFECT)).isEqualTo(Diagnostic.Kind.WARNING);
      assertThat(c.severityFor(CheckerConfig.RAW_KIND)).isEqualTo(Diagnostic.Kind.WARNING);
    }
  }

  @Nested
  @DisplayName("global severity")
  class Global {

    @Test
    @DisplayName("severity=warn downgrades error-default checks but not warn-default ones")
    void globalWarn() {
      CheckerConfig c = CheckerConfig.parse("severity=warn");
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH))
          .isEqualTo(Diagnostic.Kind.WARNING);
      assertThat(c.severityFor(CheckerConfig.ERROR_TYPE_MISMATCH))
          .isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("global severity does NOT promote a warn-default check")
    void globalDoesNotPromoteWarnDefault() {
      CheckerConfig c = CheckerConfig.parse("severity=error");
      assertThat(c.severityFor(CheckerConfig.ERROR_TYPE_MISMATCH))
          .as("warn-default stays WARNING unless explicitly overridden")
          .isEqualTo(Diagnostic.Kind.WARNING);
    }
  }

  @Nested
  @DisplayName("per-check override")
  class PerCheck {

    @Test
    @DisplayName("severity:<id>=error promotes a warn-default check")
    void promoteWarnDefault() {
      CheckerConfig c = CheckerConfig.parse("severity:error-type-mismatch=error");
      assertThat(c.severityFor(CheckerConfig.ERROR_TYPE_MISMATCH)).isEqualTo(Diagnostic.Kind.ERROR);
      assertThat(c.severityFor(CheckerConfig.MAP_NESTS_EFFECT))
          .as("other checks unaffected")
          .isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("severity:<id>=warn downgrades a single error-default check")
    void downgradeOne() {
      CheckerConfig c = CheckerConfig.parse("severity:path-type-mismatch=warn");
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH))
          .isEqualTo(Diagnostic.Kind.WARNING);
      assertThat(c.severityFor(CheckerConfig.EFFECT_COMPOSITION)).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("per-check override wins over the global severity")
    void overrideBeatsGlobal() {
      CheckerConfig c = CheckerConfig.parse("severity=warn", "severity:witness-arity=error");
      assertThat(c.severityFor(CheckerConfig.WITNESS_ARITY)).isEqualTo(Diagnostic.Kind.ERROR);
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH))
          .isEqualTo(Diagnostic.Kind.WARNING);
    }

    @Test
    @DisplayName("combined ;-separated token parses")
    void combinedToken() {
      CheckerConfig c =
          CheckerConfig.parse("disable=effect-composition;severity:map-nests-effect=error");
      assertThat(c.isEnabled(CheckerConfig.EFFECT_COMPOSITION)).isFalse();
      assertThat(c.severityFor(CheckerConfig.MAP_NESTS_EFFECT)).isEqualTo(Diagnostic.Kind.ERROR);
    }
  }

  @Nested
  @DisplayName("robustness")
  class Robustness {

    @Test
    @DisplayName("invalid per-check value is ignored (keeps the default)")
    void invalidValue() {
      CheckerConfig c = CheckerConfig.parse("severity:path-type-mismatch=banana");
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH)).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("empty check id is ignored")
    void emptyId() {
      CheckerConfig c = CheckerConfig.parse("severity:=warn");
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH)).isEqualTo(Diagnostic.Kind.ERROR);
    }

    @Test
    @DisplayName("unknown check id is accepted but harmless")
    void unknownId() {
      CheckerConfig c = CheckerConfig.parse("severity:not-a-real-check=warn");
      assertThat(c.severityFor(CheckerConfig.PATH_TYPE_MISMATCH)).isEqualTo(Diagnostic.Kind.ERROR);
      assertThat(c.severityFor("not-a-real-check")).isEqualTo(Diagnostic.Kind.WARNING);
    }
  }

  @Nested
  @DisplayName("integration: promoting a warn-default check breaks the build")
  class Integration {

    private static final Compilation compileWith(String pluginArgs) {
      return Compiler.javac()
          .withOptions(
              "-Xplugin:HKJChecker" + (pluginArgs.isEmpty() ? "" : " " + pluginArgs),
              "--enable-preview",
              "--release",
              "25")
          .compile(
              JavaFileObjects.forSourceString(
                  "test.Promote",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.higherkindedj.hkt.effect.Path;
                  public class Promote {
                      EitherPath<String, EitherPath<String, Integer>> run(EitherPath<String, Integer> p) {
                          return p.map(x -> Path.<String, Integer>right(x));
                      }
                  }
                  """));
    }

    @Test
    @DisplayName("default: map-nests-effect is a warning, build passes")
    void defaultWarn() {
      Compilation c = compileWith("");
      CompilationSubject.assertThat(c).succeeded();
      CompilationSubject.assertThat(c).hadWarningContaining("map() here nests the effect");
    }

    @Test
    @DisplayName("severity:map-nests-effect=error promotes it; build now fails")
    void promotedToError() {
      Compilation c = compileWith("severity:map-nests-effect=error");
      CompilationSubject.assertThat(c).failed();
      CompilationSubject.assertThat(c).hadErrorContaining("map() here nests the effect");
    }
  }
}
