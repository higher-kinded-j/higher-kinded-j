// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;

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
 * source that compiles, or it refuses at the declaration and says why. A processor that does
 * neither accepts the declaration and emits source naming a type variable nothing brings into
 * scope, which the author meets as {@code cannot find symbol} in a file they never wrote.
 *
 * <p>The accepted rows assert the generated signature, compiled under the consuming build's own
 * {@code -Werror} flags, because that is the whole claim: what comes out has to build where it
 * lands, and on javac's defaults an annotation that drops its type parameters altogether looks
 * exactly like success. The refusing rows assert the remedy as well as the complaint, because a
 * refusal that cannot be acted on is the other half of the same fault.
 *
 * <p>The rest are answered elsewhere, or the question does not arise. {@code @ImportOptics} has an
 * axis of its own in {@link GenericSpecInterfaceAxisTest}, one case per copy strategy and optic
 * hint against a spec that declares its own parameter. {@code @GenerateAccumulators} and
 * {@code @GenerateForComprehensions} target a package rather than a declaration, so nothing they
 * see can be generic; {@code @EffectAlgebra} and {@code @PathSource} require a parameter, so
 * generic is the only shape they take, and their arity rules are tested with them.
 */
@DisplayName("What every generating annotation does with a generic declaration")
class GenericsAcceptanceAxisTest {

  private static Compilation compile(Processor processor, JavaFileObject... sources) {
    // The consuming build's flags, not javac's defaults, as SpecInterfaceProcessingTest does.
    // Plain succeeded() passes on raw generated output, which is how an annotation that drops its
    // type parameters altogether looks from the outside - accepted, and a -Werror failure in the
    // build that consumes it.
    return javac()
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .withProcessors(processor)
        .compile(sources);
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
      assertGeneratedCodeContains(
          compilation, "com.example.BoxLenses", "public static <T> Lens<Box<T>, T> value()");
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
      assertGeneratedCodeContains(
          compilation,
          "com.example.BagTraversals",
          "public static <T> Traversal<Bag<T>, T> items()");
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
      assertGeneratedCodeContains(
          compilation, "com.example.CellFocus", "public static <T> FocusPath<Cell<T>, T> value()");
    }

    @Test
    @DisplayName("@GenerateIsos on a generic method whose parameter the iso never names")
    void generateIsosWhoseParameterTheIsoNeverNames() {
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
                      public record Box(String value) {}

                      @GenerateIsos
                      public static <T> Iso<Box, String> boxIso() {
                          return Iso.of(Box::value, Box::new);
                      }
                  }
                  """));

      // T is inferred at the call and never reaches the field's type, so the field writes down
      // exactly as it would without it. Refusing on the declaration alone turned this away.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ConvIsos",
          "public static final Iso<Conv.Box, String> boxIso = Conv.boxIso();");
    }

    @Test
    @DisplayName("@GeneratePrisms on a generic sealed hierarchy")
    void generatePrisms() {
      var compilation =
          compile(
              new PrismProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Shape",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GeneratePrisms;

                  @GeneratePrisms
                  public sealed interface Shape<T> {
                      record Circle<T>(T radius) implements Shape<T> {}
                      record Square<T>(T side) implements Shape<T> {}
                  }
                  """));

      // Both sides of the prism are written in the subtype's own vocabulary (#742). Emitting the
      // pair raw also compiles - just not under the flags the consuming build uses.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ShapePrisms",
          "public static <T> Prism<Shape<T>, Shape.Circle<T>> circle()");
    }

    @Test
    @DisplayName("@GenerateGetters on a generic record")
    void generateGetters() {
      var compilation =
          compile(
              new GetterProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.GBox",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateGetters;

                  @GenerateGetters
                  public record GBox<T>(T value, String label) {}
                  """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.GBoxGetters", "public static <T> Getter<GBox<T>, T> value()");
    }

    @Test
    @DisplayName("@GenerateSetters on a generic record")
    void generateSetters() {
      var compilation =
          compile(
              new SetterProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.SBox",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateSetters;

                  @GenerateSetters
                  public record SBox<T>(T value, String label) {}
                  """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.SBoxSetters", "public static <T> Setter<SBox<T>, T> value()");
    }

    @Test
    @DisplayName("@GenerateFolds on a generic record")
    void generateFolds() {
      var compilation =
          compile(
              new FoldProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.FBox",
                  """
                  package com.example;

                  import java.util.List;
                  import org.higherkindedj.optics.annotations.GenerateFolds;

                  @GenerateFolds
                  public record FBox<T>(List<T> items, String label) {}
                  """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.FBoxFolds", "public static <T> Fold<FBox<T>, T> items()");
    }

    @Test
    @DisplayName("@GenerateMapping on a spec declaring its own parameter")
    void generateMapping() {
      var compilation =
          compile(
              new MappingProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Page",
                  """
                  package com.example;

                  import java.util.List;

                  public record Page<T>(List<T> items, String cursor) {}
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.PageDto",
                  """
                  package com.example;

                  import java.util.List;

                  public record PageDto<T>(List<T> items, String cursor) {}
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.PageMapping",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  public interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {}
                  """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.PageMappingImpl", "class PageMappingImpl<T>");
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
      // The delegate field is asserted too: a raw one still compiles, just not under -Werror.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.example.RepoPaths", "class RepoPaths<T>");
      assertGeneratedCodeContains(
          compilation, "com.example.RepoPaths", "private final Repo<T> delegate;");
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
      assertGeneratedCodeContains(
          compilation, "com.example.SvcPaths", "public <T> OptionalPath<T> find(Class<T> type)");
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
      assertGeneratedCodeContains(
          compilation, "com.example.BothRepoPaths", "class BothRepoPaths<T extends Comparable<T>>");
      assertGeneratedCodeContains(
          compilation,
          "com.example.BothRepoPaths",
          "public <R> OptionalPath<R> convert(T from, Class<R> to)");
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
      assertThat(compilation)
          .hadErrorContaining("the iso returned by 'boxIso' names a type variable");
      assertThat(compilation)
          .hadErrorContaining("Give the iso concrete type arguments at the declaration");
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
      assertThat(compilation).hadErrorContaining("Make 'boxIso' static.");
    }

    @Test
    @DisplayName("@GenerateIsos on an instance method naming its enclosing class's parameter")
    void generateIsosNamingAnEnclosingParameter() {
      var compilation =
          compile(
              new IsoProcessor(),
              JavaFileObjects.forSourceString(
                  "com.example.Holder",
                  """
                  package com.example;

                  import org.higherkindedj.optics.Iso;
                  import org.higherkindedj.optics.annotations.GenerateIsos;

                  public class Holder<X> {
                      public record Box<T>(T value) {}

                      @GenerateIsos
                      public Iso<Box<X>, X> boxIso() { return Iso.of(Box::value, Box::new); }
                  }
                  """));

      // The method declares nothing; X comes from the class. Reported as the variable it is,
      // because "make it static" is a remedy this method cannot follow - a static method of
      // Holder<X> cannot name X either.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("names a type variable");
    }

    @Test
    @DisplayName("@GenerateIsos: both remedies it names actually work")
    void generateIsosRemediesWork() {
      // The refusals are only half an answer if what they tell the author to write does not
      // compile. Both messages name this shape: concrete arguments, on a static method.
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
                      public static Iso<Box<String>, String> boxIso() {
                          return Iso.of(Box::value, Box::new);
                      }
                  }
                  """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ConvIsos",
          "public static final Iso<Conv.Box<String>, String> boxIso = Conv.boxIso();");
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
      assertThat(compilation).hadErrorContaining("Merge concrete record types.");
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
      assertThat(compilation).hadErrorContaining("remove the type parameters");
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
      assertThat(compilation).hadErrorContaining("Declare the hierarchy without type parameters.");
    }
  }
}
