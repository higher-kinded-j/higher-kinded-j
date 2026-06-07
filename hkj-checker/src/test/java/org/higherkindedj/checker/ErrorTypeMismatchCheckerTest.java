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

@DisplayName("ErrorTypeMismatchChecker")
class ErrorTypeMismatchCheckerTest {

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

  private static boolean mentionsMismatch(Compilation c) {
    return c.diagnostics().stream()
        .anyMatch(d -> String.valueOf(d.getMessage(null)).contains("Error type is silently"));
  }

  @Nested
  @DisplayName("flags a silently-erased error-type mismatch (warning, build still passes)")
  class TruePositives {

    @Test
    @DisplayName("via lambda returning a different-E EitherPath")
    void via() {
      Compilation c =
          compile(
              src(
                  "test.TpVia",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class TpVia {
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
      assertThat(c).succeeded(); // warn-default: does not break the build
      assertThat(c).hadWarningContaining("Error type is silently mismatched in via()");
      assertThat(c).hadWarningContaining("carries E=AppError but this step yields E=String");
      assertThat(c).hadWarningContaining("effect/compiler_errors.html");
    }

    @Test
    @DisplayName("then supplier returning a different-E EitherPath")
    void then() {
      Compilation c =
          compile(
              src(
                  "test.TpThen",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class TpThen {
                      static final class AppError {}
                      EitherPath<String, Integer> other() {
                          return Path.<String, Integer>right(1);
                      }
                      EitherPath<AppError, Integer> run(EitherPath<AppError, String> v) {
                          return v.then(() -> other());
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Error type is silently mismatched in then()");
    }

    @Test
    @DisplayName("zipWith argument of a different-E EitherPath")
    void zipWith() {
      Compilation c =
          compile(
              src(
                  "test.TpZip",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class TpZip {
                      static final class AppError {}
                      EitherPath<AppError, Integer> run(EitherPath<AppError, String> v) {
                          return v.zipWith(
                              Path.<String, Integer>right(1), (s, n) -> n);
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Error type is silently mismatched in zipWith()");
    }

    @Test
    @DisplayName("ValidationPath is covered too (same error-typed category)")
    void validationPath() {
      Compilation c =
          compile(
              src(
                  "test.TpValidation",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.ValidationPath;
                  public class TpValidation {
                      static final class AppError {}
                      static final class User {}
                      ValidationPath<String, User> lookup(String id) { return null; }
                      ValidationPath<AppError, User> run(ValidationPath<AppError, String> v) {
                          return v.via(id -> lookup(id));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Error type is silently mismatched in via()");
    }

    @Test
    @DisplayName("nested lambda's return is ignored — the outer lambda's return drives the check")
    void nestedScopeReturnIsIgnored() {
      // Regression: a nested lambda whose `return` precedes the outer lambda's must NOT be
      // mistaken for the outer return. Old scanner would resolve String (the nested supplier's
      // return) and skip; the scope-correct scanner resolves lookupUser(id) and flags.
      Compilation c =
          compile(
              src(
                  "test.NestedScope",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  import java.util.function.Supplier;
                  public class NestedScope {
                      static final class AppError {}
                      static final class User {}
                      EitherPath<String, User> lookupUser(String id) {
                          return Path.<String, User>right(new User());
                      }
                      EitherPath<AppError, User> run(EitherPath<AppError, String> v) {
                          return v.via(id -> {
                              Supplier<String> nested = () -> { return "nested"; };
                              return lookupUser(id);
                          });
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Error type is silently mismatched in via()");
      assertThat(c).hadWarningContaining("carries E=AppError but this step yields E=String");
    }

    @Test
    @DisplayName("block lambda whose first return is correct but a later return mismatches")
    void mixedReturnsLaterMismatch() {
      // Regression: the lambda's first return has the correct E; a later, conditional return
      // yields the wrong E. Checking only the first return would miss this silent mismatch —
      // every in-scope return must be inspected.
      Compilation c =
          compile(
              src(
                  "test.MixedReturns",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class MixedReturns {
                      static final class AppError {}
                      static final class User {}
                      EitherPath<AppError, User> ok(String id) {
                          return Path.<AppError, User>right(new User());
                      }
                      EitherPath<String, User> wrong(String id) {
                          return Path.<String, User>right(new User());
                      }
                      EitherPath<AppError, User> run(EitherPath<AppError, String> v) {
                          return v.via(id -> {
                              if (id.isEmpty()) {
                                  return ok(id);
                              }
                              return wrong(id);
                          });
                      }
                  }
                  """));
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Error type is silently mismatched in via()");
      assertThat(c).hadWarningContaining("carries E=AppError but this step yields E=String");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("same error type across the chain")
    void sameE() {
      Compilation c =
          compile(
              src(
                  "test.SameE",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class SameE {
                      static final class AppError {}
                      static final class User {}
                      EitherPath<AppError, User> lookupUser(String id) {
                          return Path.<AppError, User>right(new User());
                      }
                      EitherPath<AppError, User> run(EitherPath<AppError, String> v) {
                          return v.via(id -> lookupUser(id));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }

    @Test
    @DisplayName("Object receiver-E with a String step-E (assignable -> safe, not flagged)")
    void objectAssignableIsSafe() {
      Compilation c =
          compile(
              src(
                  "test.ObjEdge",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class ObjEdge {
                      static final class User {}
                      EitherPath<String, User> lookupUser(String id) {
                          return Path.<String, User>right(new User());
                      }
                      EitherPath<Object, User> run(EitherPath<Object, String> v) {
                          return v.via(id -> lookupUser(id));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c))
          .as("a String Left consumed as Object never ClassCastExceptions")
          .isFalse();
    }

    @Test
    @DisplayName("generic E (type variable)")
    void genericE() {
      Compilation c =
          compile(
              src(
                  "test.GenericE",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class GenericE {
                      <E> EitherPath<E, Integer> g(String s) {
                          return Path.<E, Integer>right(1);
                      }
                      <E> EitherPath<E, Integer> f(EitherPath<E, String> p) {
                          return p.via(s -> g(s));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }

    @Test
    @DisplayName("method reference (unresolved step return)")
    void methodReference() {
      Compilation c =
          compile(
              src(
                  "test.MethodRef",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  public class MethodRef {
                      static final class AppError {}
                      static final class User {}
                      EitherPath<String, User> lookupUser(String id) {
                          return Path.<String, User>right(new User());
                      }
                      EitherPath<AppError, User> run(EitherPath<AppError, String> v) {
                          return v.via(this::lookupUser);
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }

    @Test
    @DisplayName("different Path category (separate concern, out of scope)")
    void differentCategory() {
      // The fixture is itself a category mismatch that path-type-mismatch rightly errors on;
      // disable it to isolate the checker under test (the point: error-type-mismatch must NOT
      // fire on a different-category step).
      Compilation c =
          compile(
              "disable=path-type-mismatch",
              src(
                  "test.DiffCat",
                  """
                  package test;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.higherkindedj.hkt.effect.MaybePath;
                  public class DiffCat {
                      static final class AppError {}
                      MaybePath<Integer> toMaybe(String s) { return Path.just(1); }
                      void run(EitherPath<AppError, String> v) {
                          var r = v.via(id -> toMaybe(id));
                      }
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    private static final JavaFileObject MISMATCH =
        src(
            "test.Cfg",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.EitherPath;
            public class Cfg {
                static final class AppError {}
                static final class User {}
                EitherPath<String, User> lookupUser(String id) {
                    return Path.<String, User>right(new User());
                }
                EitherPath<AppError, User> run(EitherPath<AppError, String> v) {
                    return v.via(id -> lookupUser(id));
                }
            }
            """);

    @Test
    @DisplayName("warn-default: a mismatch is a warning, never breaks the build")
    void warnDefault() {
      Compilation c = compile(MISMATCH);
      assertThat(c).succeeded();
      assertThat(c).hadWarningContaining("Error type is silently mismatched");
    }

    @Test
    @DisplayName("disable=error-type-mismatch suppresses it entirely")
    void disabled() {
      Compilation c = compile("disable=error-type-mismatch", MISMATCH);
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }
  }
}
