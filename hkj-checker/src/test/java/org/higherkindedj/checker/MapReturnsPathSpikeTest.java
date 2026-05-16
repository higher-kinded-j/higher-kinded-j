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
 * Final-review evidence: the silent dual of {@code via-non-path}. {@code map} given a
 * Path-returning function nests the effect ({@code EitherPath<E, EitherPath<E, X>>}) and
 * <em>compiles silently</em> — usually a bug; the user meant {@code via}. Pins that this is a
 * silent footgun (no javac error), justifying it as a backlog opportunity.
 */
@DisplayName("Review spike: map with a Path-returning function nests silently")
class MapReturnsPathSpikeTest {

  @Test
  @DisplayName("map(x -> Path.right(x)) compiles (nested effect, no javac error)")
  void mapReturningPath_compilesSilently() {
    Compilation c =
        javac()
            .withOptions("--enable-preview", "--release", "25")
            .compile(
                JavaFileObjects.forSourceString(
                    "test.M1",
                    """
                    package test;
                    import org.higherkindedj.hkt.effect.Path;
                    import org.higherkindedj.hkt.effect.EitherPath;
                    public class M1 {
                        Object run(EitherPath<String, Integer> p) {
                            return p.map(x -> Path.<String, Integer>right(x + 1));
                        }
                    }
                    """));
    assertThat(c.status())
        .as("map accepts any B; a Path-returning function nests silently — no compile error")
        .isEqualTo(Compilation.Status.SUCCESS);
  }
}
