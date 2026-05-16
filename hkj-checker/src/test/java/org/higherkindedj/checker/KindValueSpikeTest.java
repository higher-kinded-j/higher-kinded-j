// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reachability spike (verify-before-implement): is {@code .value()} on a {@code Kind}-typed
 * receiver (transformers §5) a real, reachable javac error a companion can annotate?
 *
 * <p>{@code org.higherkindedj.hkt.Kind} is an empty marker interface, so {@code kind.value()} is
 * structurally a "cannot find symbol" error (not the inference family). This pins that behaviour
 * and confirms the receiver type still resolves so a detector can key on it.
 */
@DisplayName("Spike: .value() on a Kind is a real cannot-find-symbol error")
class KindValueSpikeTest {

  @Test
  @DisplayName("kind.value() does not compile (cannot find symbol)")
  void valueOnKind_isCompileError() {
    Compilation c =
        javac()
            .withOptions("--enable-preview", "--release", "25")
            .compile(
                JavaFileObjects.forSourceString(
                    "test.K1",
                    """
                    package test;
                    import org.higherkindedj.hkt.Kind;
                    import org.higherkindedj.hkt.optional.OptionalKind;
                    public class K1 {
                        void m(Kind<OptionalKind.Witness, String> workflow) {
                            var v = workflow.value();
                        }
                    }
                    """));
    assertThat(c.status())
        .as("Kind is an empty marker interface; .value() is a genuine cannot-find-symbol error")
        .isEqualTo(Compilation.Status.FAILURE);
  }
}
