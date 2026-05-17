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
 * Detector spike: does "flag {@code Chainable.map} whose function returns a {@code Chainable}"
 * catch the silent-nesting bug while staying silent on correct code, plain-Java {@code map}, and
 * the collection-of-effects question?
 */
@DisplayName("Detector spike: map-returns-Path false-positive surface")
class MapReturnsPathDetectorSpikeTest {

  @BeforeEach
  void reset() {
    MapReturnsPathDetectorSpikeProbe.reset();
  }

  private List<String> observe(String name, String body) {
    Compilation c =
        javac()
            .withOptions(
                "-Xplugin:HKJMapReturnsPathDetectorSpike", "--enable-preview", "--release", "25")
            .compile(JavaFileObjects.forSourceString(name, body));
    System.out.println("=== " + name + " (" + c.status() + ") ===");
    MapReturnsPathDetectorSpikeProbe.OBSERVATIONS.forEach(o -> System.out.println("  " + o));
    return List.copyOf(MapReturnsPathDetectorSpikeProbe.OBSERVATIONS);
  }

  @Test
  @DisplayName("TRUE POSITIVE: EitherPath.map returning a Path (silent nesting)")
  void singleValueEffect() {
    assertThat(
            observe(
                "test.Tp1",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                import org.higherkindedj.hkt.effect.EitherPath;
                public class Tp1 {
                    Object run(EitherPath<String, Integer> p) {
                        return p.map(x -> Path.<String, Integer>right(x + 1));
                    }
                }
                """))
        .anyMatch(o -> o.contains("FLAG:returns-Chainable"));
  }

  @Test
  @DisplayName("CORRECT: map returning a plain value -> OK")
  void plainValue() {
    List<String> obs =
        observe(
            "test.Ok1",
            """
            package test;
            import org.higherkindedj.hkt.effect.EitherPath;
            public class Ok1 {
                EitherPath<String, String> run(EitherPath<String, Integer> p) {
                    return p.map(x -> "v" + x);
                }
            }
            """);
    assertThat(obs).noneMatch(o -> o.contains("FLAG"));
    assertThat(obs).anyMatch(o -> o.contains("OK:returns-plain"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: plain-Java Optional.map returning Optional")
  void plainJavaMap() {
    assertThat(
            observe(
                "test.Jdk",
                """
                package test;
                import java.util.Optional;
                public class Jdk {
                    Optional<Optional<Integer>> run(Optional<Integer> o) {
                        return o.map(x -> Optional.of(x + 1));
                    }
                }
                """))
        .noneMatch(o -> o.contains("FLAG"));
  }

  @Test
  @DisplayName("NO FALSE POSITIVE: method reference is skipped")
  void methodReference() {
    assertThat(
            observe(
                "test.MRef",
                """
                package test;
                import org.higherkindedj.hkt.effect.Path;
                import org.higherkindedj.hkt.effect.EitherPath;
                public class MRef {
                    EitherPath<String, Integer> toPath(Integer x) {
                        return Path.<String, Integer>right(x);
                    }
                    Object run(EitherPath<String, Integer> p) {
                        return p.map(this::toPath);
                    }
                }
                """))
        .noneMatch(o -> o.contains("FLAG"));
  }

  @Test
  @DisplayName("EDGE (recorded): collection-shaped receiver (ListPath) mapping to a Path")
  void collectionReceiver() {
    List<String> obs =
        observe(
            "test.Coll",
            """
            package test;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.ListPath;
            import org.higherkindedj.hkt.effect.EitherPath;
            import java.util.List;
            public class Coll {
                Object run(ListPath<Integer> xs) {
                    return xs.map(x -> Path.<String, Integer>right(x));
                }
            }
            """);
    // No assertion: this is the judgement edge the verdict must resolve (is a
    // collection-of-effects map a legitimate pre-traverse pattern, or still a via mistake?).
    System.out.println("  EDGE-COLLECTION: " + obs);
    assertThat(obs).isNotNull();
  }
}
