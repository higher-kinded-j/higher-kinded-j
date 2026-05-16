// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <em>Detector</em> spike: can the receiver-E vs lambda-return-E comparison be done with zero false
 * positives, given it would be the sole build-affecting signal?
 *
 * <p>Each case compiles a representative shape with {@link ErrorTypeMismatchDetectorSpikeProbe}
 * (compilation succeeds — javac does not error here, per {@link ErrorTypeMismatchSpikeTest}) and
 * asserts the verdict the candidate rule reaches. The collected matrix is the evidence for whether
 * the check gets a green light, and on what scope.
 */
@DisplayName("Detector spike: receiver-E vs lambda-return-E false-positive surface")
class ErrorTypeMismatchDetectorSpikeTest {

  @BeforeEach
  void reset() {
    ErrorTypeMismatchDetectorSpikeProbe.reset();
  }

  private List<String> observe(String name, String body) {
    Compilation c =
        javac()
            .withOptions(
                "-Xplugin:HKJErrorTypeMismatchDetectorSpike", "--enable-preview", "--release", "25")
            .compile(JavaFileObjects.forSourceString(name, body));
    assertThat(c.status())
        .as("spike sources must compile so attribution is available at ANALYZE")
        .isEqualTo(Compilation.Status.SUCCESS);
    System.out.println("=== " + name + " ===");
    ErrorTypeMismatchDetectorSpikeProbe.OBSERVATIONS.forEach(o -> System.out.println("  " + o));
    return List.copyOf(ErrorTypeMismatchDetectorSpikeProbe.OBSERVATIONS);
  }

  @Test
  @DisplayName("TRUE POSITIVE: via lambda returns a different-E EitherPath")
  void truePositive_differentE() {
    assertThat(
            observe(
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
                    EitherPath<AppError, User> run(EitherPath<AppError, String> validated) {
                        return validated.via(id -> lookupUser(id));
                    }
                }
                """))
        .anyMatch(o -> o.contains("verdict=FLAG:different-E"));
  }

  @Test
  @DisplayName("CORRECT: via lambda returns the same-E EitherPath -> no flag")
  void correct_sameE() {
    List<String> obs =
        observe(
            "test.OkVia",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.EitherPath;
            public class OkVia {
                static final class AppError {}
                static final class User {}
                EitherPath<AppError, User> lookupUser(String id) {
                    return Path.<AppError, User>right(new User());
                }
                EitherPath<AppError, User> run(EitherPath<AppError, String> validated) {
                    return validated.via(id -> lookupUser(id));
                }
            }
            """);
    assertThat(obs).anyMatch(o -> o.contains("verdict=OK:same-E"));
    assertThat(obs).noneMatch(o -> o.contains("FLAG"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: generic E (type variable) -> skipped, never flagged")
  void noFp_genericTypeVariable() {
    List<String> obs =
        observe(
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
            """);
    assertThat(obs).noneMatch(o -> o.contains("FLAG"));
    assertThat(obs).anyMatch(o -> o.contains("SKIP:non-concrete-E"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: method reference -> unresolved, never flagged")
  void noFp_methodReference() {
    List<String> obs =
        observe(
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
                EitherPath<AppError, User> run(EitherPath<AppError, String> validated) {
                    return validated.via(this::lookupUser);
                }
            }
            """);
    assertThat(obs).noneMatch(o -> o.contains("FLAG"));
    assertThat(obs).anyMatch(o -> o.contains("SKIP:unresolved-lambda-return"));
  }

  @Test
  @DisplayName("OUT OF SCOPE: lambda returns a different category -> skipped, not flagged")
  void outOfScope_differentCategory() {
    List<String> obs =
        observe(
            "test.DiffCat",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.MaybePath;
            public class DiffCat {
                static final class AppError {}
                MaybePath<Integer> toMaybe(String s) {
                    return Path.just(1);
                }
                void run(EitherPath<AppError, String> validated) {
                    var r = validated.via(id -> toMaybe(id));
                }
            }
            """);
    assertThat(obs).noneMatch(o -> o.contains("FLAG"));
    assertThat(obs).anyMatch(o -> o.contains("SKIP:return-not-same-error-typed-category"));
  }

  @Test
  @DisplayName("EDGE (recorded for the verdict): Object receiver-E vs concrete return-E")
  void edge_objectReceiverE() {
    List<String> obs =
        observe(
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
                EitherPath<Object, User> run(EitherPath<Object, String> validated) {
                    return validated.via(id -> lookupUser(id));
                }
            }
            """);
    // No assertion on flag/no-flag: this edge is the judgement call the verdict must resolve
    // (Object-as-E is plausibly an intentional widening, yet still a latent Left-type confusion).
    System.out.println("  EDGE-OBJECT verdicts: " + obs);
    assertThat(obs).isNotEmpty();
  }

  @Test
  @DisplayName("TRUE POSITIVE: then-supplier returns a different-E EitherPath")
  void truePositive_thenSupplier() {
    assertThat(
            observe(
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
                    EitherPath<AppError, Integer> run(EitherPath<AppError, String> validated) {
                        return validated.then(() -> other());
                    }
                }
                """))
        .anyMatch(o -> o.contains("verdict=FLAG:different-E"));
  }
}
