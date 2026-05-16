// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spike result, frozen as a characterization test.
 *
 * <p><b>Question:</b> does the documented effect §5 / transformers §2 "error type {@code E}
 * mismatch in an Either chain" produce a javac error on JDK 25?
 *
 * <p><b>Finding:</b> <em>no</em>. {@code EitherPath.via} is {@code <B> EitherPath<E,B>
 * via(Function<? super A, ? extends Chainable<B>>)}: the lambda return is bound to {@code
 * Chainable<B>}, which does not carry the error type. A lambda returning {@code
 * EitherPath<String,User>} on an {@code EitherPath<AppError,String>} receiver compiles cleanly —
 * the inner {@code String} Left is erased through the {@code Chainable<B>} bound and the result is
 * typed {@code EitherPath<AppError,User>}. The documentation's "incompatible types: EitherPath
 * &lt;String,User&gt; cannot be converted to EitherPath&lt;AppError,User&gt;" does not occur; like
 * §1, that section is stale.
 *
 * <p><b>Consequence:</b> this is not a javac-erroring case the checker would merely annotate — it
 * is a <em>silent correctness hole</em> (wrong runtime Left type, latent ClassCastException). A
 * detector is therefore the sole build-affecting signal for code javac accepts, which raises the
 * false-positive bar and argues for a warn-default — which is how the {@code error-type-mismatch}
 * check ultimately shipped.
 *
 * <p>This test pins the observed javac behaviour and will fail if a future JDK or a library
 * signature change turns the mismatch back into a compile error, at which point the re-scoping
 * should be revisited.
 */
@DisplayName("Spike: error-type mismatch in an Either chain is silently erased")
class ErrorTypeMismatchSpikeTest {

  private Compilation javacOnly(JavaFileObject src) {
    return javac().withOptions("--enable-preview", "--release", "25").compile(src);
  }

  private static JavaFileObject src(String name, String body) {
    return JavaFileObjects.forSourceString(name, body);
  }

  @Test
  @DisplayName("via with a lambda returning a different-E EitherPath compiles (silent erasure)")
  void viaDifferentErrorType_compilesSilently() {
    Compilation c =
        javacOnly(
            src(
                "test.Effect5",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                import org.higherkindedj.hkt.effect.EitherPath;
                public class Effect5 {
                    static final class AppError {}
                    static final class User {}
                    EitherPath<String, User> lookupUser(String id) {
                        return Path.<String, User>right(new User());
                    }
                    EitherPath<AppError, User> run(EitherPath<AppError, String> validated) {
                        return validated.via(id -> lookupUser(id));
                    }
                }
                """));
    assertThat(c.status())
        .as("doc §5 claims a compile error here; modern javac erases the inner E instead")
        .isEqualTo(Compilation.Status.SUCCESS);
  }

  @Test
  @DisplayName("explicit EitherPath<AppError,User> target also compiles (E still erased)")
  void assignedTarget_compilesSilently() {
    Compilation c =
        javacOnly(
            src(
                "test.Effect5b",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                import org.higherkindedj.hkt.effect.EitherPath;
                public class Effect5b {
                    static final class AppError {}
                    static final class User {}
                    EitherPath<String, User> lookupUser(String id) {
                        return Path.<String, User>right(new User());
                    }
                    void run(EitherPath<AppError, String> validated) {
                        EitherPath<AppError, User> r = validated.via(id -> lookupUser(id));
                    }
                }
                """));
    assertThat(c.status()).isEqualTo(Compilation.Status.SUCCESS);
  }
}
