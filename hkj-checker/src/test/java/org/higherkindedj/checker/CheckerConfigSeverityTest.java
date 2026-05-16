// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final com.google.testing.compile.Compilation compileWith(String pluginArgs) {
      return com.google.testing.compile.Compiler.javac()
          .withOptions(
              "-Xplugin:HKJChecker" + (pluginArgs.isEmpty() ? "" : " " + pluginArgs),
              "--enable-preview",
              "--release",
              "25")
          .compile(
              com.google.testing.compile.JavaFileObjects.forSourceString(
                  "test.Promote",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class Promote {
                      static final class AppError {}
                      static final class User {}
                      EitherPath<String, User> lookupUser(String id) {
                          return Path.<String, User>right(new User());
                      }
                      EitherPath<AppError, User> run(EitherPath<AppError, String> v) {
                          return v.via(id -> lookupUser(id));
                      }
                  }
                  """));
    }

    @Test
    @DisplayName("default: error-type-mismatch is a warning, build passes")
    void defaultWarn() {
      com.google.testing.compile.Compilation c = compileWith("");
      com.google.testing.compile.CompilationSubject.assertThat(c).succeeded();
      com.google.testing.compile.CompilationSubject.assertThat(c)
          .hadWarningContaining("Error type is silently mismatched");
    }

    @Test
    @DisplayName("severity:error-type-mismatch=error promotes it; build now fails")
    void promotedToError() {
      com.google.testing.compile.Compilation c = compileWith("severity:error-type-mismatch=error");
      com.google.testing.compile.CompilationSubject.assertThat(c).failed();
      com.google.testing.compile.CompilationSubject.assertThat(c)
          .hadErrorContaining("Error type is silently mismatched");
    }
  }
}
