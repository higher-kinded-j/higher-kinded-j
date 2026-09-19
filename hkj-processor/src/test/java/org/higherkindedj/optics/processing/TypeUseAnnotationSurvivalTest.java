// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.effect.EffectAlgebraProcessor;
import org.higherkindedj.optics.processing.effect.PathProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A case per generator: a {@code @Nullable} the author wrote survives into what the generator
 * emits.
 *
 * <p>Dropping it is not merely a loss of information. Generated source lands in the annotated
 * type's own package, so it inherits whatever {@code @NullMarked} scope the consumer set there -
 * and inside one, an unannotated type <em>means</em> non-null. A dropped {@code @Nullable} does not
 * leave the contract unstated; it states the opposite of what the author wrote.
 *
 * <p>{@code @EffectAlgebra} is the one generator that stamps {@code @NullMarked} on its own output,
 * so it carries the extra check that the emitted file actually compiles as the author's own call
 * would.
 */
@DisplayName("Type-use annotations survive into generated code")
class TypeUseAnnotationSurvivalTest {

  private static JavaFileObject source(String qualifiedName, String body) {
    return JavaFileObjects.forSourceString(qualifiedName, body);
  }

  @Nested
  @DisplayName("record component optics")
  class RecordComponentOptics {

    private static final String BOX =
        """
        package com.example;

        import java.util.List;
        import org.jspecify.annotations.Nullable;
        import org.higherkindedj.optics.annotations.%s;

        @%s
        public record Box<T extends @Nullable Object>(
            T value, @Nullable String label, List<@Nullable String> tags) {}
        """;

    private static Compilation compile(String annotation, Object processor) {
      return javac()
          .withProcessors((javax.annotation.processing.Processor) processor)
          .compile(source("com.example.Box", BOX.formatted(annotation, annotation)));
    }

    @Test
    @DisplayName("@GenerateLenses keeps it in the focus, the wither's parameter and the bound")
    void generateLensesKeepsIt() {
      Compilation compilation = compile("GenerateLenses", new LensProcessor());

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxLenses",
          "public static <T extends @Nullable Object> Lens<Box<T>, @Nullable String> label()");
      // The bound is the half that decides what the generated type admits: stripped to `<T>`, the
      // lens no longer accepts the `Box<@Nullable String>` the record itself permits.
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxLenses",
          "public static <T extends @Nullable Object> Box<T> withLabel(Box<T> source,"
              + " @Nullable String newLabel)");
    }

    @Test
    @DisplayName("@GenerateSetters keeps it in the setter's focus")
    void generateSettersKeepsIt() {
      Compilation compilation = compile("GenerateSetters", new SetterProcessor());

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxSetters",
          "public static <T extends @Nullable Object> Setter<Box<T>, @Nullable String> label()");
    }

    @Test
    @DisplayName("@GenerateGetters keeps it in the getter's focus and its convenience return")
    void generateGettersKeepsIt() {
      Compilation compilation = compile("GenerateGetters", new GetterProcessor());

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxGetters",
          "public static <T extends @Nullable Object> Getter<Box<T>, @Nullable String> label()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxGetters",
          "public static <T extends @Nullable Object> @Nullable String getLabel(Box<T> source)");
    }

    @Test
    @DisplayName("@GenerateFolds keeps it in the fold's focus")
    void generateFoldsKeepsIt() {
      Compilation compilation = compile("GenerateFolds", new FoldProcessor());

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxFolds",
          "public static <T extends @Nullable Object> Fold<Box<T>, @Nullable String> label()");
      // A fold over a container focuses its elements, so the element annotation is the one that
      // describes what the fold arrives at.
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxFolds",
          "public static <T extends @Nullable Object> Fold<Box<T>, @Nullable String> tags()");
    }

    @Test
    @DisplayName("@GenerateFocus keeps the bound, and keeps only what the widening did not consume")
    void generateFocusKeepsWhatTheWideningDidNotConsume() {
      Compilation compilation = compile("GenerateFocus", new FocusProcessor());

      assertThat(compilation).succeeded();
      // A nullable component widens through .nullable(), which is Affine<@Nullable A, A>: the
      // widening is what rules the null out, so the focus it arrives at is non-null again.
      // Repeating @Nullable here would describe a value the affine never yields.
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxFocus",
          "public static <T extends @Nullable Object> AffinePath<Box<T>, String> label()");
      // A nullable *element* is not consumed by anything: .each() arrives at exactly it.
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxFocus",
          "public static <T extends @Nullable Object> TraversalPath<Box<T>, @Nullable String>"
              + " tags()");
    }

    @Test
    @DisplayName("@GenerateTraversals keeps it in the traversal's focus")
    void generateTraversalsKeepsIt() {
      Compilation compilation = compile("GenerateTraversals", new TraversalProcessor());

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.BoxTraversals", "Traversal<Box<T>, @Nullable String> tags()");
    }
  }

  @Nested
  @DisplayName("generators over a declared signature")
  class DeclaredSignatures {

    @Test
    @DisplayName("@GenerateIsos keeps it in either side of the isomorphism")
    void generateIsosKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new IsoProcessor())
              .compile(
                  source(
                      "com.example.Converters",
                      """
                      package com.example;

                      import org.jspecify.annotations.Nullable;
                      import org.higherkindedj.optics.Iso;
                      import org.higherkindedj.optics.annotations.GenerateIsos;

                      public class Converters {
                        @GenerateIsos
                        public static Iso<@Nullable String, @Nullable CharSequence> widen() {
                          return Iso.of(s -> s, c -> (String) c);
                        }
                      }
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.ConvertersIsos",
          "public static final Iso<@Nullable String, @Nullable CharSequence> widen");
    }

    @Test
    @DisplayName("@GenerateMerge keeps it in the parameters it repeats")
    void generateMergeKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new MergeProcessor())
              .compile(
                  source(
                      "com.example.Parts",
                      """
                      package com.example;

                      public final class Parts {
                        public record Head(String title) {}

                        public record Tail(String body) {}

                        public record Page(String title, String body) {}
                      }
                      """),
                  source(
                      "com.example.PageAssembly",
                      """
                      package com.example;

                      import org.jspecify.annotations.Nullable;
                      import org.higherkindedj.optics.annotations.GenerateMerge;

                      @GenerateMerge
                      public interface PageAssembly {
                        Parts.Page assemble(Parts.@Nullable Head head, Parts.Tail tail);
                      }
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.PageAssemblyImpl",
          "public Parts.Page assemble(Parts.@Nullable Head head, Parts.Tail tail)");
    }

    @Test
    @DisplayName("@GeneratePathBridge keeps it in the bridge's parameters, focus and bound")
    void generatePathBridgeKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new PathProcessor())
              .compile(
                  source(
                      "com.example.Lookup",
                      """
                      package com.example;

                      import java.util.Optional;
                      import org.jspecify.annotations.Nullable;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      @GeneratePathBridge
                      public interface Lookup<T extends @Nullable Object> {
                        @PathVia
                        Optional<@Nullable String> find(@Nullable String key);

                        @PathVia
                        Optional<@Nullable T> get(@Nullable T key);

                        @PathVia
                        <R extends @Nullable T> Optional<R> pick(R candidate);
                      }
                      """),
                  source(
                      "com.example.Names",
                      """
                      package com.example;

                      import java.util.Optional;
                      import org.jspecify.annotations.Nullable;
                      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
                      import org.higherkindedj.hkt.effect.annotation.PathVia;

                      public interface Names {
                        interface Base<E> {
                          @PathVia
                          Optional<@Nullable E> get(@Nullable E key);
                        }

                        @GeneratePathBridge
                        interface Strings extends Base<String> {}
                      }
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.LookupPaths",
          "public final class LookupPaths<T extends @Nullable Object>");
      // A bridge that narrows its own parameter below the delegate's rejects a call the delegate
      // accepts, so the wrapper is not usable everywhere the thing it wraps is.
      assertGeneratedCodeContains(
          compilation,
          "com.example.LookupPaths",
          "public OptionalPath<@Nullable String> find(@Nullable String key)");
      // Written on a type variable's use, it is lost to reading the method under the interface,
      // the declaring one included, and has to be put back from the declaration.
      assertGeneratedCodeContains(
          compilation,
          "com.example.LookupPaths",
          "public OptionalPath<@Nullable T> get(@Nullable T key)");
      assertGeneratedCodeContains(
          compilation,
          "com.example.LookupPaths",
          "public <R extends @Nullable T> OptionalPath<R> pick(R candidate)");
      // Inherited under an instantiation, it lands on the type that replaced the variable.
      assertGeneratedCodeContains(
          compilation,
          "com.example.StringsPaths",
          "public OptionalPath<@Nullable String> get(@Nullable String key)");
    }

    @Test
    @DisplayName("@GenerateMapping keeps it where the Impl restates a leaf, a marker or a group")
    void generateMappingKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .compile(
                  source(
                      "com.example.Types",
                      """
                      package com.example;

                      import org.jspecify.annotations.Nullable;

                      public final class Types {
                        private Types() {}

                        public record Tagged<N, V>(N name, V value) {}

                        public record TaggedDto<ND, VD>(ND name, VD value) {}

                        public record Person<T>(T name) {}

                        public record PersonDto<T>(T fullName) {}

                        public record Inner<T extends @Nullable Object>(T a, String b) {}

                        public record Outer<T extends @Nullable Object>(
                            Inner<@Nullable T> inner, String id) {}

                        public record OuterDto(@Nullable String a, String b, String id) {}
                      }
                      """),
                  source(
                      "com.example.Vocabulary",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.MapField;
                      import org.higherkindedj.optics.validated.ValidatedPrism;
                      import org.jspecify.annotations.Nullable;

                      public interface Vocabulary {
                        interface NameLeaf<N, ND> {
                          ValidatedPrism<@Nullable ND, @Nullable N> name();
                        }

                        interface Renames<T> {
                          @MapField(to = "fullName")
                          @Nullable T name();
                        }
                      }
                      """),
                  source(
                      "com.example.Specs",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.Flatten;
                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.MappingSpec;
                      import org.higherkindedj.optics.validated.ValidatedPrism;
                      import org.jspecify.annotations.Nullable;

                      public interface Specs {
                        @GenerateMapping
                        interface TaggedMapping<N, ND, V, VD>
                            extends Vocabulary.NameLeaf<N, ND>,
                                MappingSpec<Types.Tagged<N, V>, Types.TaggedDto<ND, VD>> {
                          ValidatedPrism<VD, @Nullable V> value();
                        }

                        @GenerateMapping
                        interface BoundMapping<V, VD>
                            extends Vocabulary.NameLeaf<String, String>,
                                MappingSpec<Types.Tagged<String, V>, Types.TaggedDto<String, VD>> {
                          ValidatedPrism<VD, V> value();
                        }

                        @GenerateMapping
                        interface PersonMapping<T>
                            extends Vocabulary.Renames<T>,
                                MappingSpec<Types.Person<T>, Types.PersonDto<T>> {}

                        @GenerateMapping
                        interface OuterMapping extends MappingSpec<Types.Outer<String>, Types.OuterDto> {
                          @Flatten
                          Types.Inner<@Nullable String> inner();
                        }
                      }
                      """));

      assertThat(compilation).succeeded();
      // The Impl restates each leaf as its field, accessor and of(...) parameter, own and
      // inherited alike: reading a member under the spec loses what was written on a type
      // variable's use.
      assertGeneratedCodeContains(
          compilation,
          "com.example.SpecsTaggedMappingImpl",
          "public ValidatedPrism<VD, @Nullable V> value()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.SpecsTaggedMappingImpl",
          "public ValidatedPrism<@Nullable ND, @Nullable N> name()");
      // Bound to String by the spec, the variable's use is still the nullable one the mix-in wrote.
      assertGeneratedCodeContains(
          compilation,
          "com.example.SpecsBoundMappingImpl",
          "public ValidatedPrism<@Nullable String, @Nullable String> name()");
      assertGeneratedCodeContains(
          compilation, "com.example.SpecsPersonMappingImpl", "public @Nullable T name()");
      // A flattened group is rebuilt under the domain's instantiation of the component.
      assertGeneratedCodeContains(
          compilation,
          "com.example.SpecsOuterMappingImpl",
          "new Types.Inner<@Nullable String>(a, b)");
    }

    @Test
    @DisplayName("@GenerateMapping keeps it where a mix-in's own extends clause wrote it")
    void generateMappingKeepsItThroughAMixinsOwnClause() {
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .compile(
                  source(
                      "com.example.Chain",
                      """
                      package com.example;

                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.MapField;
                      import org.higherkindedj.optics.annotations.MappingSpec;
                      import org.higherkindedj.optics.validated.ValidatedPrism;
                      import org.jspecify.annotations.Nullable;

                      public interface Chain {
                        record Tagged<N, V>(N name, V value) {}

                        record TaggedDto<ND, VD>(ND name, VD value) {}

                        record Person<T>(T name) {}

                        record PersonDto<T>(T fullName) {}

                        interface NameLeaf<N, ND> {
                          ValidatedPrism<ND, N> name();
                        }

                        interface NullableName<M, MD> extends NameLeaf<@Nullable M, MD> {}

                        interface Renames<R> {
                          @MapField(to = "fullName")
                          R name();
                        }

                        interface NullableRenames<Q> extends Renames<@Nullable Q> {}

                        @GenerateMapping
                        interface TaggedMapping<A, AD, V, VD>
                            extends NullableName<A, AD>,
                                MappingSpec<Tagged<A, V>, TaggedDto<AD, VD>> {
                          ValidatedPrism<VD, V> value();
                        }

                        @GenerateMapping
                        interface PersonMapping<T>
                            extends NullableRenames<T>, MappingSpec<Person<T>, PersonDto<T>> {}
                      }
                      """));

      assertThat(compilation).succeeded();
      // The member names its own variable bare; the clause binding it to the spec's said nullable.
      assertGeneratedCodeContains(
          compilation,
          "com.example.ChainTaggedMappingImpl",
          "public ValidatedPrism<AD, @Nullable A> name()");
      assertGeneratedCodeContains(
          compilation, "com.example.ChainPersonMappingImpl", "public @Nullable T name()");
    }

    @Test
    @DisplayName("@GenerateMapping keeps it in the type witness a nested generic spec is called by")
    void generateMappingKeepsItInANestedSpecsTypeWitness() {
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .compile(
                  source(
                      "com.example.Nest",
                      """
                      package com.example;

                      import java.util.List;
                      import java.util.Map;
                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.MappingSpec;
                      import org.higherkindedj.optics.annotations.UpdateSpec;
                      import org.jspecify.annotations.Nullable;

                      public interface Nest {
                        record Box<T>(T value) {}

                        record BoxDto<T>(T value) {}

                        @GenerateMapping
                        interface BoxMapping<T> extends MappingSpec<Box<T>, BoxDto<T>> {}

                        record Bin<T>(T items) {}

                        record BinDto<T>(T items) {}

                        @GenerateMapping
                        interface BinMapping<T> extends MappingSpec<Bin<T[]>, BinDto<T[]>> {}

                        record Pair<A, B>(A first, B second) {}

                        record PairDto<A, B>(A first, B second) {}

                        @GenerateMapping
                        interface PairMapping<T> extends MappingSpec<Pair<T, int[]>, PairDto<T, int[]>> {}

                        record Outer<X>(
                            Box<@Nullable X> box,
                            List<Box<@Nullable X>> boxes,
                            Bin<@Nullable X[]> bin,
                            Pair<@Nullable X, int[]> pair) {}

                        record OuterDto<X>(
                            BoxDto<X> box, List<BoxDto<X>> boxes, BinDto<X[]> bin, PairDto<X, int[]> pair) {}

                        @GenerateMapping
                        interface OuterMapping extends MappingSpec<Outer<String>, OuterDto<String>> {}

                        record Keyed<X>(Map<X, Box<@Nullable X>> byKey, Map<String, Box<@Nullable X>> byName) {}

                        record KeyedDto<X>(Map<X, BoxDto<X>> byKey, Map<String, BoxDto<X>> byName) {}

                        @GenerateMapping
                        interface KeyedMapping<X> extends MappingSpec<Keyed<X>, KeyedDto<X>> {}

                        record Patched(Box<@Nullable String> box) {}

                        class PatchedPatch {
                          private BoxDto<String> box;

                          public BoxDto<String> getBox() {
                            return box;
                          }

                          public void setBox(BoxDto<String> box) {
                            this.box = box;
                          }
                        }

                        @GenerateMapping
                        interface PatchedUpdate extends UpdateSpec<Patched, PatchedPatch> {}
                      }
                      """));

      assertThat(compilation).succeeded();
      // Read under Outer<String>, each component lost its @Nullable, and so did the binding; the
      // witness is named from the component's declaration instead: the whole component, a List's
      // element, and an array element of a spec whose domain names T[].
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestOuterMappingImpl",
          "NestBoxMappingImpl.<@Nullable String>instance().asValidatedPrism().build(domain.box())");
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestOuterMappingImpl",
          "NestBoxMappingImpl.<@Nullable String>instance().asValidatedPrism().buildAll(domain.boxes())");
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestOuterMappingImpl",
          "NestBinMappingImpl.<@Nullable String>instance()");
      // A part of the spec's domain that is no variable, the int[] beside T, takes no witness.
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestOuterMappingImpl",
          "NestPairMappingImpl.<@Nullable String>instance()");
      assertGeneratedCodeDoesNotContain(
          compilation, "com.example.NestOuterMappingImpl", "<String>instance()");
      // A Map's value, found past a key typed by the spec's own variable, and past one that is a
      // type of its own.
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestKeyedMappingImpl",
          "NestBoxMappingImpl.<@Nullable X>instance()");
      // A sparse update's domain is never generic, so there is nothing to recover, and nothing
      // lost.
      assertGeneratedCodeContains(
          compilation,
          "com.example.NestPatchedUpdateImpl",
          "NestBoxMappingImpl.<@Nullable String>instance()");
    }

    @Test
    @DisplayName("@ImportOptics keeps it in the lens a checked traversal composes through")
    void importOpticsKeepsItInTheLensATraversalComposesThrough() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(
                  source(
                      "com.external.Bag",
                      """
                      package com.external;

                      import java.util.List;
                      import org.jspecify.annotations.Nullable;

                      public final class Bag<U extends @Nullable Object> {
                        private final List<U> items;

                        public Bag(List<U> items) {
                          this.items = items;
                        }

                        public List<U> items() {
                          return items;
                        }

                        public Bag<U> withItems(List<U> items) {
                          return new Bag<>(items);
                        }
                      }
                      """),
                  source(
                      "com.test.BagSpec",
                      """
                      package com.test;

                      import com.external.Bag;
                      import java.util.List;
                      import org.higherkindedj.optics.Lens;
                      import org.higherkindedj.optics.Traversal;
                      import org.higherkindedj.optics.annotations.ImportOptics;
                      import org.higherkindedj.optics.annotations.OpticsSpec;
                      import org.higherkindedj.optics.annotations.ThroughField;
                      import org.higherkindedj.optics.annotations.Wither;
                      import org.jspecify.annotations.Nullable;

                      @ImportOptics
                      public interface BagSpec<U extends @Nullable Object> extends OpticsSpec<Bag<U>> {
                        @Wither("withItems")
                        Lens<Bag<U>, List<@Nullable U>> items();

                        @ThroughField(field = "items")
                        Traversal<Bag<U>, @Nullable U> eachItem();
                      }
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.test.Bag",
          "Lens<com.external.Bag<U>, List<@Nullable U>> lens = Bag.items()");
    }

    @Test
    @DisplayName("@ImportOptics keeps it in an optic generated for a type it does not own")
    void importOpticsKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(
                  source(
                      "com.external.Customer",
                      """
                      package com.external;

                      import org.jspecify.annotations.Nullable;

                      public record Customer(String name, @Nullable String nickname) {}
                      """),
                  source(
                      "com.myapp.optics.package-info",
                      """
                      @ImportOptics({com.external.Customer.class})
                      package com.myapp.optics;

                      import org.higherkindedj.optics.annotations.ImportOptics;
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.CustomerLenses",
          "public static Lens<Customer, @Nullable String> nickname()");
    }

    @Test
    @DisplayName("@GeneratePrisms keeps it in the sum type as the subtype's own clause names it")
    void generatePrismsKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new PrismProcessor())
              .compile(
                  source(
                      "com.example.Shape",
                      """
                      package com.example;

                      import org.jspecify.annotations.Nullable;
                      import org.higherkindedj.optics.annotations.GeneratePrisms;

                      @GeneratePrisms
                      public sealed interface Shape<T> {
                        record Tagged(String label) implements Shape<@Nullable String> {}
                      }
                      """));

      assertThat(compilation).succeeded();
      // A prism is written against the sum type the *subtype* names, and an implements clause is
      // a use: what is written there is part of the type the prism focuses from.
      assertGeneratedCodeContains(
          compilation,
          "com.example.ShapePrisms",
          "Prism<Shape<@Nullable String>, Shape.Tagged> tagged()");
    }

    @Test
    @DisplayName("@GenerateErrorEnvelope keeps it in the context builder it emits")
    void generateErrorEnvelopeKeepsIt() {
      Compilation compilation =
          javac()
              .withProcessors(new ErrorEnvelopeProcessor())
              .compile(
                  source(
                      "com.example.FooErrorContext",
                      """
                      package com.example;

                      import org.jspecify.annotations.Nullable;

                      public record FooErrorContext(String traceId, @Nullable String orderId) {}
                      """),
                  source(
                      "com.example.FooError",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.error.ErrorEnvelope;
                      import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

                      @GenerateErrorEnvelope
                      public sealed interface FooError {
                        ErrorEnvelope<FooErrorContext> envelope();

                        record OutOfStock(String product, ErrorEnvelope<FooErrorContext> envelope)
                            implements FooError {}
                      }
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.example.FooErrors", "public ContextBuilder orderId(@Nullable String");
    }
  }

  @Nested
  @DisplayName("what must not be copied")
  class NotCopied {

    @Test
    @DisplayName("a type-parameter annotation does not leak into a type-argument position")
    void typeParameterAnnotationDoesNotLeak() {
      // One TypeVariableName both declares the record's parameter and is written as the type
      // argument of `Box<T>` in every generated signature. `Box<@Marked T>` does not compile -
      // "annotation @Marked not applicable in this type context" - so the declaration annotation
      // has to stay behind. The bound, where JSpecify states nullability, is kept.
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor())
              .compile(
                  source(
                      "com.example.Marked",
                      """
                      package com.example;

                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Target;

                      @Target(ElementType.TYPE_PARAMETER)
                      public @interface Marked {}
                      """),
                  source(
                      "com.example.Box",
                      """
                      package com.example;

                      import org.jspecify.annotations.Nullable;
                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      public record Box<@Marked T extends @Nullable Object>(T value) {}
                      """));

      // succeeded(), not just "generated": the generated file is compiled in this same run, so a
      // leaked annotation would fail here rather than in a consumer's build.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.example.BoxLenses",
          "public static <T extends @Nullable Object> Lens<Box<T>, T> value()");
      assertGeneratedCodeDoesNotContain(compilation, "com.example.BoxLenses", "Box<@Marked T>");
    }
  }

  /**
   * The other half of the contract: an annotation the generated file cannot write is left off
   * rather than written, since the file lands where its generator puts it and writing one it cannot
   * name fails the build compiling it.
   */
  @Nested
  @DisplayName("an annotation the generated file cannot name is left off")
  class UnnameableAnnotations {

    @Test
    @DisplayName("a package-private annotation is kept at home and left off elsewhere")
    void aPackagePrivateAnnotationIsKeptAtHomeAndLeftOffElsewhere() {
      Compilation compilation =
          javac()
              .withProcessors(new LensProcessor(), new MappingProcessor())
              .compile(
                  source(
                      "com.home.Tag",
                      """
                      package com.home;

                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Target;

                      @Target(ElementType.TYPE_USE)
                      @interface Tag {}
                      """),
                  source(
                      "com.home.Named",
                      """
                      package com.home;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses
                      public record Named(@Tag String name) {}
                      """),
                  source(
                      "com.home.Moved",
                      """
                      package com.home;

                      import org.higherkindedj.optics.annotations.GenerateLenses;

                      @GenerateLenses(targetPackage = "com.away")
                      public record Moved(@Tag String name) {}
                      """),
                  source(
                      "com.home.NameLeaf",
                      """
                      package com.home;

                      import org.higherkindedj.optics.validated.ValidatedPrism;

                      public interface NameLeaf<N> {
                        ValidatedPrism<@Tag String, N> name();
                      }
                      """),
                  source(
                      "com.away.Specs",
                      """
                      package com.away;

                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.MappingSpec;
                      import org.higherkindedj.optics.validated.ValidatedPrism;

                      public interface Specs {
                        record Tagged<N, V>(N name, V value) {}

                        record TaggedDto<VD>(String name, VD value) {}

                        @GenerateMapping
                        interface TaggedMapping<N, V, VD>
                            extends com.home.NameLeaf<N>, MappingSpec<Tagged<N, V>, TaggedDto<VD>> {
                          ValidatedPrism<VD, V> value();
                        }
                      }
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.home.NamedLenses", "public static Lens<Named, @Tag String> name()");
      assertGeneratedCodeContains(
          compilation, "com.away.MovedLenses", "public static Lens<Moved, String> name()");
      // The mix-in's package-private annotation, restated by an Impl in the spec's package.
      assertGeneratedCodeContains(
          compilation,
          "com.away.SpecsTaggedMappingImpl",
          "public ValidatedPrism<String, N> name()");
    }

    @Test
    @DisplayName("@ImportOptics leaves off one private to the type it imports")
    void importOpticsLeavesOffAPrivateAnnotation() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(
                  source(
                      "com.external.Customer",
                      """
                      package com.external;

                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Target;

                      public record Customer(@Customer.Internal String name) {
                        @Target(ElementType.TYPE_USE)
                        private @interface Internal {}
                      }
                      """),
                  source(
                      "com.myapp.optics.package-info",
                      """
                      @ImportOptics({com.external.Customer.class})
                      package com.myapp.optics;

                      import org.higherkindedj.optics.annotations.ImportOptics;
                      """));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.optics.CustomerLenses",
          "public static Lens<Customer, String> name()");
    }
  }

  @Nested
  @DisplayName("@EffectAlgebra, whose own output is @NullMarked")
  class NullMarkedOutput {

    private static final JavaFileObject NOTE_OP =
        JavaFileObjects.forSourceString(
            "com.example.NoteOp",
            """
            package com.example;

            import org.jspecify.annotations.Nullable;
            import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

            @EffectAlgebra
            public sealed interface NoteOp<A> permits NoteOp.Write {
              record Write<A>(@Nullable String note) implements NoteOp<A> {}
            }
            """);

    @Test
    @DisplayName("the smart constructor keeps it, so it accepts what the record it wraps accepts")
    void smartConstructorKeepsIt() {
      Compilation compilation =
          javac().withProcessors(new EffectAlgebraProcessor()).compile(NOTE_OP);

      assertThat(compilation).succeeded();
      // Without it, `write(null)` is rejected under the file's own @NullMarked while
      // `new NoteOp.Write<>(null)` - the call the smart constructor exists to wrap - is not.
      assertGeneratedCodeContains(
          compilation,
          "com.example.NoteOpOps",
          "public static <A> Free<NoteOpKind.Witness, A> write(@Nullable String note)");
      // The Bound inner class repeats the signature, and would repeat the loss with it.
      assertGeneratedCodeContains(
          compilation,
          "com.example.NoteOpOps",
          "public <A> Free<G, A> write(@Nullable String note)");
    }

    @Test
    @DisplayName("the emitted file compiles, annotation and its import included")
    void theEmittedFileCompiles() {
      // The annotation has to be written into a file that names it: an emitted @Nullable with no
      // import is a generated file the consuming build cannot compile.
      Compilation compilation =
          javac().withProcessors(new EffectAlgebraProcessor()).compile(NOTE_OP);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation,
          "com.example.NoteOpOps",
          "public static <A> Free<NoteOpKind.Witness, A> write");
      GeneratorTestHelper.assertGeneratedCodeContainsRaw(
          compilation, "com.example.NoteOpOps", "import org.jspecify.annotations.Nullable;");
      GeneratorTestHelper.assertGeneratedCodeContainsRaw(
          compilation, "com.example.NoteOpOps", "@NullMarked");
    }
  }
}
