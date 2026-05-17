// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spike result, frozen as a characterization test (the risk gate for the inference-failure family).
 *
 * <p><b>Question the spike had to answer:</b> can the {@code Path.right(...)} / {@code
 * Path.left(...)} "cannot infer type-variable(s) E" failure be detected structurally, given that it
 * fires <em>because</em> attribution failed?
 *
 * <p><b>Empirical finding (JDK 25, the supported toolchain):</b> that javac error is unreachable
 * for every shape the documentation claims triggers it. javac either binds {@code E} from the
 * target type, or, when nothing constrains it, silently defaults {@code E} to {@code Object}. The
 * compilation <em>succeeds</em>; the call node is fully attributed to a concrete, non-erroneous
 * {@code EitherPath<…>}. There is therefore no "nearby javac inference error" to couple a
 * structural detector to (the proposed attribution-failure signal never occurs).
 *
 * <p><b>Verdict:</b> the inference-failure family is dropped (not shipped), not because structural
 * detection is brittle but because the targeted error does not occur on the supported compiler. The
 * structurally-trivial cases proceed instead.
 *
 * <p>This test is the durable evidence for that decision: it pins the observed javac behaviour and
 * will fail loudly if a future JDK reintroduces the inference error (at which point the deferral
 * should be revisited).
 */
@DisplayName("Spike: Path.right/left inference behaviour on the supported javac")
class PathRightInferenceSpikeTest {

  @BeforeEach
  void resetProbe() {
    PathRightInferenceSpikeProbe.reset();
  }

  private Compilation compileWithProbe(JavaFileObject source) {
    return javac()
        .withOptions("-Xplugin:HKJSpikeProbe", "--enable-preview", "--release", "25")
        .compile(source);
  }

  private static JavaFileObject src(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  private String soleObservedType(JavaFileObject source) {
    Compilation c = compileWithProbe(source);
    assertThat(c.status())
        .as("the spike's central finding: this shape must NOT produce an inference error")
        .isEqualTo(Compilation.Status.SUCCESS);
    assertThat(PathRightInferenceSpikeProbe.analyzeFired).isTrue();
    assertThat(PathRightInferenceSpikeProbe.OBSERVATIONS)
        .as("the Path.right/left node is fully attributed at ANALYZE")
        .allMatch(o -> o.contains("type RESOLVED"))
        .noneMatch(o -> o.contains("ERRONEOUS") || o.contains("type==null"));
    return String.join(" | ", PathRightInferenceSpikeProbe.OBSERVATIONS);
  }

  @Test
  @DisplayName("no target type: E silently defaults to Object (no inference error)")
  void noTarget_defaultsEToObject() {
    String observed =
        soleObservedType(
            src(
                "test.NoTarget",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                public class NoTarget {
                    public void m() { var x = Path.right(1); }
                }
                """));
    assertThat(observed).contains("EitherPath<java.lang.Object,java.lang.Integer>");
  }

  @Test
  @DisplayName("bare expression statement: still resolves, E = Object")
  void bareStatement_resolves() {
    String observed =
        soleObservedType(
            src(
                "test.BareStmt",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                public class BareStmt {
                    public void m() { Path.right(1); }
                }
                """));
    assertThat(observed).contains("EitherPath<java.lang.Object,java.lang.Integer>");
  }

  @Test
  @DisplayName(
      "doc's literal trigger (return with EitherPath<AppError,User>): E inferred, compiles")
  void docLiteralReturnTrigger_compiles() {
    String observed =
        soleObservedType(
            src(
                "test.DocTrigger",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                import org.higherkindedj.hkt.effect.EitherPath;
                public class DocTrigger {
                    static final class AppError {}
                    static final class User {}
                    User repoFind(String id) { return new User(); }
                    EitherPath<AppError, User> findUser(String id) {
                        User user = repoFind(id);
                        return Path.right(user);
                    }
                }
                """));
    assertThat(observed).contains("EitherPath<test.DocTrigger.AppError,test.DocTrigger.User>");
  }

  @Test
  @DisplayName("doc's mixed-return lambda (\"may fail\"): compiles, E = Object")
  void mixedReturnLambda_compiles() {
    String observed =
        soleObservedType(
            src(
                "test.MixedLambda",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                public class MixedLambda {
                    public void m() {
                        Path.right(1).via(x -> {
                            if (x > 0) return Path.right(x);
                            return Path.left("neg");
                        });
                    }
                }
                """));
    // All three factory calls attribute cleanly; none raises an inference error.
    assertThat(observed).contains("Path.right(...)").contains("Path.left(...)");
  }

  @Test
  @DisplayName("explicit witness and pinned local type both resolve to the written E")
  void explicitlyPinned_resolvesToWrittenE() {
    assertThat(
            soleObservedType(
                src(
                    "test.Witness",
                    """
                    package test;
                    import org.higherkindedj.hkt.effect.Path;
                    import org.higherkindedj.hkt.effect.EitherPath;
                    public class Witness {
                        public void m() {
                            EitherPath<String, Integer> x = Path.<String, Integer>right(1);
                        }
                    }
                    """)))
        .contains("EitherPath<java.lang.String,java.lang.Integer>");

    PathRightInferenceSpikeProbe.reset();

    assertThat(
            soleObservedType(
                src(
                    "test.PinnedLocal",
                    """
                    package test;
                    import org.higherkindedj.hkt.effect.Path;
                    import org.higherkindedj.hkt.effect.EitherPath;
                    public class PinnedLocal {
                        public void m() {
                            EitherPath<String, Integer> x = Path.right(1);
                        }
                    }
                    """)))
        .contains("EitherPath<java.lang.String,java.lang.Integer>");
  }
}
