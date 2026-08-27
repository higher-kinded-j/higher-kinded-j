// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.effect.PathProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One row per generating annotation: what it does when the thing it is put on is generic.
 *
 * <p>Every annotation here answers a generic declaration one of exactly two ways — it generates
 * source that compiles, or it refuses at the declaration and says why. There is no third answer,
 * and the two that had no row took it: both accepted the declaration and emitted source naming a
 * type variable nothing brought into scope, which the author met as {@code cannot find symbol} in a
 * file they never wrote.
 *
 * <p>The accepted rows assert compilation, not a signature, because that is the whole claim: what
 * comes out has to build. The refusing rows assert the wording, because a refusal whose remedy
 * cannot be followed is the other half of the same fault.
 *
 * <p>{@code @GeneratePrisms} belongs in this matrix and is verified in {@link
 * PrismProcessorIntegrationTest} instead, where the hierarchy fixtures it needs already live.
 */
@DisplayName("What every generating annotation does with a generic declaration")
class GenericsAcceptanceAxisTest {

  private static Compilation compile(Processor processor, JavaFileObject... sources) {
    return javac().withProcessors(processor).compile(sources);
  }

  @Nested
  @DisplayName("generates source that compiles")
  class Accepted {

    @Test
    @DisplayName("@GenerateLenses on a generic record")
    void generateLenses() {
      var compilation =
          compile(
              new LensProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Box",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateLenses;

                  @GenerateLenses
                  public record Box<T>(T value, String label) {}
                  """));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.BoxLenses");
    }

    @Test
    @DisplayName("@GenerateTraversals on a generic record")
    void generateTraversals() {
      var compilation =
          compile(
              new TraversalProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Bag",
                  """
                  package com.example;

                  import java.util.List;
                  import org.higherkindedj.optics.annotations.GenerateTraversals;

                  @GenerateTraversals
                  public record Bag<T>(List<T> items, String label) {}
                  """));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.BagTraversals");
    }

    @Test
    @DisplayName("@GenerateFocus on a generic record")
    void generateFocus() {
      var compilation =
          compile(
              new FocusProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Cell",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateFocus;

                  @GenerateFocus
                  public record Cell<T>(T value, String label) {}
                  """));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.CellFocus");
    }

    @Test
    @DisplayName("@GeneratePathBridge on a generic interface")
    void pathBridgeOnAGenericInterface() {
      var compilation =
          compile(
              new PathProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Repo",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Repo<T> {
                      @PathVia
                      Optional<String> byId(T id);
                  }
                  """));

      // The bridge holds one delegate of the interface, so it declares what the interface does.
      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.RepoPaths");
    }

    @Test
    @DisplayName("@GeneratePathBridge on a generic method")
    void pathBridgeOnAGenericMethod() {
      var compilation =
          compile(
              new PathProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Svc",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface Svc {
                      @PathVia
                      <T> Optional<T> find(Class<T> type);
                  }
                  """));

      // The bridge method copies the delegate's signature, so it declares the method's own too.
      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.SvcPaths");
    }

    @Test
    @DisplayName("@GeneratePathBridge on both at once, each parameter in its own place")
    void pathBridgeOnBoth() {
      var compilation =
          compile(
              new PathProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.BothRepo",
                  """
                  package com.example;

                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                  import org.higherkindedj.hkt.effect.annotation.PathVia;

                  @GeneratePathBridge
                  public interface BothRepo<T extends Comparable<T>> {
                      @PathVia
                      <R> Optional<R> convert(T from, Class<R> to);
                  }
                  """));

      // T on the class with its bound, R on the method: putting either in the other's place names
      // a variable out of scope at one of the two use sites.
      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.BothRepoPaths");
    }
  }

  @Nested
  @DisplayName("refuses at the declaration, with a remedy that can be followed")
  class Refused {

    @Test
    @DisplayName("@GenerateIsos on a generic method")
    void generateIsosOnAGenericMethod() {
      var compilation =
          compile(
              new IsoProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Conv",
                  """
                  package com.example;

                  import org.higherkindedj.optics.Iso;
                  import org.higherkindedj.optics.annotations.GenerateIsos;

                  public class Conv {
                      public record Box<T>(T value) {}

                      @GenerateIsos
                      public static <T> Iso<Box<T>, T> boxIso() {
                          return Iso.of(Box::value, Box::new);
                      }
                  }
                  """));

      // A static final field has nowhere to declare <T>, so there is no correct source to emit.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'boxIso' declares type parameters");
    }

    @Test
    @DisplayName("@GenerateIsos on an instance method")
    void generateIsosOnAnInstanceMethod() {
      var compilation =
          compile(
              new IsoProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Conv",
                  """
                  package com.example;

                  import org.higherkindedj.optics.Iso;
                  import org.higherkindedj.optics.annotations.GenerateIsos;

                  public class Conv {
                      public record Box<T>(T value) {}

                      @GenerateIsos
                      public Iso<Box<String>, String> boxIso() {
                          return Iso.of(Box::value, Box::new);
                      }
                  }
                  """));

      // The field initialises itself with a static call; an instance method has no receiver for it.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'boxIso' is not static");
    }

    @Test
    @DisplayName("@GenerateMerge on a generic spec")
    void generateMerge() {
      var compilation =
          compile(
              new MergeProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.PairMerge",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateMerge;

                  @GenerateMerge
                  public interface PairMerge<T> {
                      String assemble(String a, Integer b);
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'PairMerge' is generic");
    }

    @Test
    @DisplayName("@GenerateAssembly on a generic record")
    void generateAssembly() {
      var compilation =
          compile(
              new AssemblyProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Pair",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateAssembly;

                  @GenerateAssembly
                  public record Pair<T>(T left, String right) {}
                  """));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("the record is generic");
    }

    @Test
    @DisplayName("@GenerateErrorEnvelope on a generic hierarchy")
    void generateErrorEnvelope() {
      var compilation =
          compile(
              new ErrorEnvelopeProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Failure",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

                  @GenerateErrorEnvelope
                  public sealed interface Failure<T> permits Failure.NotFound {
                      record NotFound(String id) implements Failure<String> {}
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'Failure' is generic");
    }
  }
}
