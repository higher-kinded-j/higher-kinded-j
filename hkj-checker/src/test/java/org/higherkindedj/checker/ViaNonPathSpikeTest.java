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
 * Reachability spike (verify-before-implement): is {@code via} given a function that returns a
 * plain value (effect §3) a real, reachable javac error a companion can annotate, and does a
 * Chainable-returning function compile (no-FP baseline)?
 */
@DisplayName("Spike: via with a non-Path-returning function")
class ViaNonPathSpikeTest {

  private Compilation javacOnly(String name, String body) {
    return javac()
        .withOptions("--enable-preview", "--release", "25")
        .compile(JavaFileObjects.forSourceString(name, body));
  }

  @Test
  @DisplayName("via with a lambda returning a plain value -> compile error")
  void viaPlainValue_fails() {
    Compilation c =
        javacOnly(
            "test.V1",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.EitherPath;
            public class V1 {
                EitherPath<String, Integer> run(EitherPath<String, Integer> p) {
                    return p.via(x -> x.toString());
                }
            }
            """);
    assertThat(c.status())
        .as("via requires a Chainable-returning function; a plain value must not type-check")
        .isEqualTo(Compilation.Status.FAILURE);
  }

  @Test
  @DisplayName("via with a Chainable-returning lambda -> compiles (no-FP baseline)")
  void viaChainable_compiles() {
    Compilation c =
        javacOnly(
            "test.V2",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.EitherPath;
            public class V2 {
                EitherPath<String, Integer> run(EitherPath<String, Integer> p) {
                    return p.via(x -> Path.<String, Integer>right(x + 1));
                }
            }
            """);
    assertThat(c.status()).isEqualTo(Compilation.Status.SUCCESS);
  }
}
