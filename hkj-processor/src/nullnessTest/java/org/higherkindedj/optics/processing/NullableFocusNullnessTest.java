// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Optics instantiated at a nullable type pass a JSpecify nullness checker, both where a consumer
 * writes them and where a generator emits them.
 *
 * <p>The checker is NullAway in JSpecify mode, configured as a consumer runs it: only
 * {@code @NullMarked} code is checked, and generated code is checked like any other. Inside a
 * {@code @NullMarked} scope an unbounded {@code <A>} means {@code <A extends Object>}, so {@code
 * Lens<Config, @Nullable String>} is legal only because the optic declares {@code A
 * extends @Nullable Object}. Nothing else in the build runs a nullness checker, so a declaration
 * that rejects a nullable type argument fails only here.
 */
@DisplayName("A nullness checker accepts optics at a nullable type")
class NullableFocusNullnessTest {

  private static final JavaFileObject NULL_MARKED_PACKAGE =
      JavaFileObjects.forSourceString(
          "com.example.package-info",
          """
          @NullMarked
          package com.example;

          import org.jspecify.annotations.NullMarked;
          """);

  /** What a consumer checking every source file, generated ones included, would see. */
  private static Compilation compileChecked(List<Processor> processors, JavaFileObject... sources) {
    return compile(processors, List.of(), sources);
  }

  private static Compilation compileChecked(JavaFileObject... sources) {
    return compileChecked(List.of(), sources);
  }

  /**
   * As {@link #compileChecked(List, JavaFileObject...)}, but with the generated classes left
   * unchecked, as a consumer who excludes generated code has them. Their signatures are still read
   * as annotated, so a use site is held to the types they declare.
   */
  private static Compilation compileCheckedOutsideGenerated(
      List<Processor> processors, JavaFileObject... sources) {
    return compile(
        processors,
        List.of(
            "-XepOpt:NullAway:ExcludedClassAnnotations=org.higherkindedj.optics.annotations.Generated"),
        sources);
  }

  private static Compilation compile(
      List<Processor> processors, List<String> extraErrorProneFlags, JavaFileObject... sources) {
    String errorProne =
        String.join(
            " ",
            Stream.concat(
                    Stream.of(
                        "-Xplugin:ErrorProne",
                        "-XepDisableAllChecks",
                        "-Xep:NullAway:ERROR",
                        "-XepOpt:NullAway:OnlyNullMarked=true",
                        "-XepOpt:NullAway:JSpecifyMode=true"),
                    extraErrorProneFlags.stream())
                .toList());
    return javac()
        .withProcessors(processors)
        .withOptions(
            // Error Prone looks for a plugin checker such as NullAway on the processor path only.
            "--processor-path",
            System.getProperty("java.class.path"),
            "-XDcompilePolicy=simple",
            "--should-stop=ifError=FLOW",
            errorProne)
        .compile(Stream.concat(Stream.of(NULL_MARKED_PACKAGE), Arrays.stream(sources)).toList());
  }

  private static JavaFileObject source(String qualifiedName, String body) {
    return JavaFileObjects.forSourceString(qualifiedName, body);
  }

  @Nested
  @DisplayName("where a null stays refused")
  class StillRefused {

    @Test
    @DisplayName("At's value sits inside an Optional, so it stays non-null")
    void atKeepsANonNullValue() {
      // An Optional cannot hold null, so At's A keeps the implicit non-null bound. The rejection
      // is also the proof that the checker ran at all.
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.MapSlot",
                  """
                  package com.example;

                  import java.util.Map;
                  import org.higherkindedj.optics.At;
                  import org.jspecify.annotations.Nullable;

                  class MapSlot {
                    @Nullable At<Map<String, String>, String, @Nullable String> slot;
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "type variable A of type org.higherkindedj.optics.At does not have a @Nullable upper"
                  + " bound");
    }

    @Test
    @DisplayName("Edit.set writes through a nullable focus, but never a null value")
    void editSetRefusesANullValue() {
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.ClearKey",
                  """
                  package com.example;

                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.edit.Edit;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.jspecify.annotations.Nullable;

                  class ClearKey {
                    record Config(@Nullable String apiKey) {}

                    static Edit<Config> clear() {
                      FocusPath<Config, @Nullable String> apiKey =
                          FocusPath.of(Lens.of(Config::apiKey, (c, k) -> new Config(k)));
                      return Edit.set(apiKey, null);
                    }
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("passing @Nullable parameter 'null' where @NonNull is required");
    }

    @Test
    @DisplayName("a bridge that holds the focus as it is takes no nullable focus into its type")
    void aBridgeThatHoldsTheFocusRefusesANullableOne() {
      // An Effect Path, Either, Try, Id and Free keep a non-null value, so a bridge with no way to
      // say what a null focus becomes cannot type one: the one-argument conversion's result cannot
      // be written, and a FocusPath at a nullable focus is refused at the call.
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Holds",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.free.OpticPrograms;
                  import org.jspecify.annotations.Nullable;

                  class Holds {
                    record Config(@Nullable String apiKey) {}

                    static final Lens<Config, @Nullable String> API_KEY =
                        Lens.of(Config::apiKey, (c, k) -> new Config(k));
                    static final FocusPath<Config, @Nullable String> KEY = FocusPath.of(API_KEY);

                    static void use(Config config) {
                      EitherPath<String, String> converted = KEY.toEitherPath(config);
                      EitherPath<String, Config> path = Path.right(config);
                      var focused = path.focus(KEY);
                      var program = OpticPrograms.get(config, API_KEY);
                    }
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "EitherPath<String, @Nullable String> cannot be converted to EitherPath<String,"
                  + " String>");
      assertThat(compilation)
          .hadErrorContaining(
              "FocusPath<Config, @Nullable String> cannot be converted to FocusPath<Config,"
                  + " String>");
      assertThat(compilation)
          .hadErrorContaining(
              "Lens<Config, @Nullable String> cannot be converted to Lens<Config, String>");
    }

    @Test
    @DisplayName("a validator sees the nullable focus, so dereferencing it unchecked is refused")
    void aValidatorSeesTheNullableFocus() {
      // The bridges hand a validator the focus as it is: its implicit parameter is typed
      // @Nullable, so a dereference without a check is reported, while a checked one passes.
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Unchecked",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.either.Either;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.extensions.LensExtensions;
                  import org.jspecify.annotations.Nullable;

                  class Unchecked {
                    record Config(@Nullable String apiKey) {}

                    static final Lens<Config, @Nullable String> API_KEY =
                        Lens.of(Config::apiKey, (c, k) -> new Config(k));

                    static Either<String, Config> trimmed(Config config) {
                      return LensExtensions.modifyEither(
                          API_KEY, k -> Either.right(k.trim()), config);
                    }
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("dereferenced expression 'k' is @Nullable");
    }

    @Test
    @DisplayName("a Right or a Success always holds a value, so a nullable one cannot build it")
    void aSuccessRefusesANullableValue() {
      // Each factory, and of on a monad that refuses null, takes a non-null value, so each call
      // with a nullable one is reported on its own line.
      JavaFileObject builds =
          source(
              "com.example.Builds",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.Path;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.either.EitherMonad;
              import org.higherkindedj.hkt.either_t.EitherT;
              import org.higherkindedj.hkt.id.IdKind;
              import org.higherkindedj.hkt.id.IdMonad;
              import org.higherkindedj.hkt.trymonad.Try;
              import org.higherkindedj.hkt.trymonad.TryKindHelper;
              import org.jspecify.annotations.Nullable;

              class Builds {
                static void build(@Nullable String raw) {
                  Either.<String, String>right(raw);
                  Try.<String>success(raw);
                  Path.<String, String>right(raw);
                  Path.<String>success(raw);
                  TryKindHelper.TRY.<String>success(raw);
                  EitherT.<IdKind.Witness, String, String>right(IdMonad.instance(), raw);
                  EitherMonad.<String>instance().<String>of(raw);
                }
              }
              """);

      Compilation compilation = compileChecked(builds);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(7);
      for (int line = 15; line <= 21; line++) {
        assertThat(compilation)
            .hadErrorContaining("passing @Nullable parameter 'raw' where @NonNull is required")
            .inFile(builds)
            .onLine(line);
      }
    }

    @Test
    @DisplayName("the value a Right or a Success hands back needs no null check")
    void aSuccessHandsBackANonNullValue() {
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Reads",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.either.Either;
                  import org.higherkindedj.hkt.trymonad.Try;

                  class Reads {
                    static int read(Either<String, String> either, Try<String> attempt)
                        throws Throwable {
                      return either.getRight().length()
                          + attempt.get().length()
                          + attempt.orElseGet(() -> "fallback").length();
                    }
                  }
                  """));

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("written by hand")
  class HandWritten {

    @Test
    @DisplayName("every optic type takes a nullable focus and a nullable source")
    void everyOpticAdmitsANullableTypeArgument() {
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Instantiations",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Map;
                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.id.IdKind;
                  import org.higherkindedj.optics.Affine;
                  import org.higherkindedj.optics.At;
                  import org.higherkindedj.optics.Each;
                  import org.higherkindedj.optics.Fold;
                  import org.higherkindedj.optics.Getter;
                  import org.higherkindedj.optics.Iso;
                  import org.higherkindedj.optics.Ixed;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.Optic;
                  import org.higherkindedj.optics.Prism;
                  import org.higherkindedj.optics.Setter;
                  import org.higherkindedj.optics.Traversal;
                  import org.higherkindedj.optics.focus.AffinePath;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.focus.TraversalPath;
                  import org.higherkindedj.optics.indexed.IndexedFold;
                  import org.higherkindedj.optics.indexed.IndexedLens;
                  import org.higherkindedj.optics.indexed.IndexedTraversal;
                  import org.jspecify.annotations.Nullable;

                  class Instantiations {
                    record Config(@Nullable String apiKey) {}

                    @Nullable Optic<@Nullable Config, @Nullable Config, @Nullable String, @Nullable String>
                        optic;
                    @Nullable Lens<@Nullable Config, @Nullable String> lens;
                    @Nullable Getter<@Nullable Config, @Nullable String> getter;
                    @Nullable Setter<@Nullable Config, @Nullable String> setter;
                    @Nullable Fold<@Nullable Config, @Nullable String> fold;
                    @Nullable Traversal<@Nullable Config, @Nullable String> traversal;
                    @Nullable Iso<@Nullable String, @Nullable String> iso;
                    @Nullable Prism<@Nullable String, @Nullable String> prism;
                    @Nullable Affine<@Nullable String, @Nullable String> affine;
                    @Nullable Each<List<@Nullable String>, @Nullable String> each;
                    @Nullable Ixed<Map<String, @Nullable String>, String, @Nullable String> ixed;
                    @Nullable IndexedLens<String, @Nullable Config, @Nullable String> indexedLens;
                    @Nullable IndexedFold<Integer, @Nullable Config, @Nullable String> indexedFold;
                    @Nullable IndexedTraversal<Integer, @Nullable Config, @Nullable String>
                        indexedTraversal;
                    @Nullable FocusPath<@Nullable Config, @Nullable String> focusPath;
                    @Nullable AffinePath<@Nullable Config, @Nullable String> affinePath;
                    @Nullable TraversalPath<@Nullable Config, @Nullable String> traversalPath;
                    @Nullable Kind<IdKind.Witness, @Nullable String> kind;
                    @Nullable At<@Nullable Map<String, String>, String, String> nullableSourceAt;
                  }
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("the shipped factories and documented shapes type as written")
    void shippedFactoriesTypeAsWritten() {
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Shapes",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.effect.MaybePath;
                  import org.higherkindedj.optics.Affine;
                  import org.higherkindedj.optics.Fold;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.Prism;
                  import org.higherkindedj.optics.Traversal;
                  import org.higherkindedj.optics.focus.AffinePath;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.focus.TraversalPath;
                  import org.higherkindedj.optics.util.Affines;
                  import org.higherkindedj.optics.util.Prisms;
                  import org.higherkindedj.optics.util.Traversals;
                  import org.jspecify.annotations.Nullable;

                  class Shapes {
                    record Config(@Nullable String apiKey) {}

                    static final Lens<Config, @Nullable String> API_KEY =
                        Lens.of(Config::apiKey, (config, apiKey) -> new Config(apiKey));

                    static void use(Config config, List<@Nullable String> tags) {
                      Config cleared = API_KEY.set(null, config);
                      @Nullable String apiKey = API_KEY.get(cleared);

                      // The nullable source the two shipped null-rulers declare.
                      Affine<@Nullable String, String> present = Affines.nullable();
                      Prism<@Nullable String, String> notNull = Prisms.notNull();
                      Affine<Config, String> presentKey = API_KEY.andThen(present);
                      Affine<Config, String> notNullKey = API_KEY.andThen(notNull);

                      // An Optional cannot hold null, so a read through one is typed non-null.
                      Optional<String> first = API_KEY.asFold().preview(config);
                      Optional<String> some = presentKey.getOptional(config);

                      // The documented Focus shapes.
                      FocusPath<Config, @Nullable String> apiKeyPath = FocusPath.of(API_KEY);
                      AffinePath<Config, String> safeApiKey = apiKeyPath.nullable();
                      AffinePath<Config, @Nullable String> apiKeyAffine = apiKeyPath.asAffine();
                      Optional<String> read = apiKeyAffine.getOptional(config);
                      MaybePath<String> maybe = apiKeyPath.toMaybePath(config);

                      // A nullable element, reached by the element traversal.
                      Traversal<List<@Nullable String>, @Nullable String> elements =
                          Traversals.forList();
                      TraversalPath<List<@Nullable String>, @Nullable String> each =
                          TraversalPath.of(elements);
                      List<@Nullable String> all = each.getAll(tags);
                      Optional<String> firstTag = each.preview(tags);
                      Fold<List<@Nullable String>, @Nullable String> tagFold = elements.asFold();
                    }
                  }
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("the factories and helpers over a nullable element type as written")
    void factoriesAndHelpersTypeAsWritten() {
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Helpers",
                  """
                  package com.example;

                  import java.util.Collection;
                  import java.util.List;
                  import java.util.Map;
                  import java.util.Optional;
                  import java.util.Set;
                  import org.higherkindedj.hkt.Unit;
                  import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                  import org.higherkindedj.hkt.validated.FieldError;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.hkt.maybe.Maybe;
                  import org.higherkindedj.optics.Affine;
                  import org.higherkindedj.optics.At;
                  import org.higherkindedj.optics.Each;
                  import org.higherkindedj.optics.EachIndexed;
                  import org.higherkindedj.optics.Ixed;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.Prism;
                  import org.higherkindedj.optics.Traversal;
                  import org.higherkindedj.optics.at.AtInstances;
                  import org.higherkindedj.optics.each.EachInstances;
                  import org.higherkindedj.optics.Getter;
                  import org.higherkindedj.optics.Iso;
                  import org.higherkindedj.optics.Setter;
                  import org.higherkindedj.optics.edit.Edit;
                  import org.higherkindedj.optics.edit.FallibleEdit;
                  import org.higherkindedj.optics.extensions.TraversalExtensions;
                  import org.higherkindedj.optics.indexed.IndexedLens;
                  import org.higherkindedj.optics.indexed.Pair;
                  import org.higherkindedj.optics.extensions.FoldExtensions;
                  import org.higherkindedj.optics.extensions.LensExtensions;
                  import org.higherkindedj.optics.fluent.OpticOps;
                  import org.higherkindedj.optics.focus.AffinePath;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.focus.FocusPaths;
                  import org.higherkindedj.optics.focus.TraversalPath;
                  import org.higherkindedj.optics.indexed.IndexedTraversal;
                  import org.higherkindedj.optics.ixed.IxedInstances;
                  import org.higherkindedj.optics.util.Affines;
                  import org.higherkindedj.optics.util.IndexedTraversals;
                  import org.higherkindedj.optics.util.ListPrisms;
                  import org.higherkindedj.optics.util.ListTraversals;
                  import org.higherkindedj.optics.util.Prisms;
                  import org.higherkindedj.optics.util.Traversals;
                  import org.jspecify.annotations.Nullable;

                  class Helpers {
                    record Config(@Nullable String apiKey, List<@Nullable String> tags) {}

                    static final Lens<Config, @Nullable String> API_KEY =
                        Lens.of(Config::apiKey, (c, apiKey) -> new Config(apiKey, c.tags()));
                    // NullAway does not carry a nested @Nullable into an implicit lambda parameter.
                    static final Lens<Config, List<@Nullable String>> TAGS =
                        Lens.of(
                            Config::tags,
                            (Config c, List<@Nullable String> tags) -> new Config(c.apiKey(), tags));

                    static void use(Config config, Map<String, @Nullable String> labels) {
                      // Element traversals and their Each instances.
                      Traversal<Set<@Nullable String>, @Nullable String> set = Traversals.forSet();
                      Traversal<Collection<@Nullable String>, @Nullable String> collection =
                          Traversals.forCollection();
                      Traversal<@Nullable String[], @Nullable String> array = Traversals.forArray();
                      Traversal<Map<String, @Nullable String>, @Nullable String> values =
                          Traversals.forMapValues();
                      Traversal<List<@Nullable String>, @Nullable String> firstTwo =
                          ListTraversals.taking(2);
                      IndexedTraversal<Integer, List<@Nullable String>, @Nullable String> indexed =
                          IndexedTraversals.forList();
                      EachIndexed<Integer, List<@Nullable String>, @Nullable String> listEach =
                          EachInstances.listEach();
                      Each<Map<String, @Nullable String>, @Nullable String> mapEach =
                          EachInstances.mapValuesEach();
                      Traversal<List<@Nullable String>, @Nullable String> fromEach = listEach.each();

                      // Positional optics: nullable element, focus read as absent.
                      Affine<List<@Nullable String>, @Nullable String> head = Affines.listHead();
                      Prism<List<@Nullable String>, @Nullable String> last = Prisms.listLast();
                      Affine<List<@Nullable String>, @Nullable String> listHead = ListPrisms.head();
                      Affine<List<@Nullable String>, @Nullable String> at =
                          FocusPaths.listAt(0);
                      Optional<String> first = head.getOptional(config.tags());

                      // A nullable source.
                      Affine<@Nullable String, String> nonNull = FocusPaths.nullable();
                      Prism<@Nullable String, Unit> isNull = Prisms.only(null);
                      Prism<@Nullable CharSequence, String> isString =
                          Prisms.instanceOf(String.class);

                      // At and Ixed: a nullable map value reads as absent.
                      At<Map<String, @Nullable String>, String, String> label = AtInstances.mapAt();
                      Optional<String> named = label.get("name", labels);
                      Ixed<Map<String, @Nullable String>, String, String> ix = IxedInstances.mapIx();
                      Ixed<List<@Nullable String>, Integer, String> listIx = IxedInstances.listIx();
                      Optional<String> second = IxedInstances.get(listIx, 1, config.tags());

                      // The Focus DSL's list navigation.
                      FocusPath<Config, List<@Nullable String>> tags = FocusPath.of(TAGS);
                      TraversalPath<Config, @Nullable String> eachTag = tags.each();
                      AffinePath<Config, @Nullable String> firstTag = tags.head();
                      AffinePath<Config, String> apiKey =
                          AffinePath.ofNullable(
                              Config::apiKey, (c, k) -> new Config(k, c.tags()));

                      // Helpers that read through an Optional or a Maybe.
                      Optional<String> previewed = OpticOps.preview(config, API_KEY.asFold());
                      @Nullable String read = OpticOps.getting(config).through(API_KEY);
                      Maybe<String> maybe = LensExtensions.getMaybe(API_KEY, config);
                      Maybe<String> maybeTag =
                          FoldExtensions.previewMaybe(TAGS.asFold().andThen(fromEach.asFold()), config);

                      // The factories and compositions over a nullable focus.
                      Prism<@Nullable String, String> prismOf =
                          Prism.of(Optional::ofNullable, k -> k);
                      Affine<Config, @Nullable String> affineOf =
                          Affine.of(
                              c -> Optional.ofNullable(c.apiKey()),
                              (Config c, @Nullable String k) -> new Config(k, c.tags()));
                      Iso<@Nullable String, Optional<String>> iso =
                          Iso.of(Optional::ofNullable, (Optional<String> o) -> o.orElse(null));
                      Getter<Config, @Nullable String> getter = Getter.of(Config::apiKey);
                      Setter<Config, @Nullable String> setter =
                          Setter.fromGetSet(
                              Config::apiKey,
                              (Config c, @Nullable String k) -> new Config(k, c.tags()));
                      IndexedLens<String, Config, @Nullable String> indexedLens =
                          IndexedLens.from("apiKey", API_KEY);
                      Lens<Config, Pair<@Nullable String, List<@Nullable String>>> both =
                          Lens.paired(
                              API_KEY,
                              TAGS,
                              (Config c, @Nullable String k, List<@Nullable String> t) ->
                                  new Config(k, t));
                      Pair<@Nullable String, String> pair = Pair.of(null, "b");
                      IndexedTraversal<Pair<Integer, Integer>, List<List<@Nullable String>>, @Nullable String>
                          nested =
                              IndexedTraversals.<List<@Nullable String>>forList()
                                  .iandThen(IndexedTraversals.<@Nullable String>forList());
                      Prism<List<@Nullable String>, Pair<@Nullable String, List<@Nullable String>>>
                          cons = ListPrisms.cons();
                      Prism<List<@Nullable String>, Pair<@Nullable String, List<@Nullable String>>>
                          listCons = FocusPaths.listCons();
                      AffinePath<Config, @Nullable String> someKey =
                          FocusPath.<Config>identity().some(affineOf);
                      AffinePath<@Nullable CharSequence, String> isText =
                          AffinePath.instanceOf(String.class);
                      Maybe<List<@Nullable String>> allTags =
                          TraversalExtensions.getAllMaybe(Traversals.forList(), config.tags());

                      // An edit writes a non-null value through a nullable focus.
                      Edit<Config> setKey = Edit.set(FocusPath.of(API_KEY), "key");
                      Edit<Config> clearKey = Edit.modify(FocusPath.of(API_KEY), k -> null);
                      Edit<Config> maybeKey = Edit.setIfPresent(FocusPath.of(API_KEY), null);
                      FallibleEdit<Config> parsedKey =
                          Edit.parseIfPresent(
                              FocusPath.of(API_KEY),
                              " key ",
                              (String raw) ->
                                  Validated.<NonEmptyList<FieldError>, String>valid(raw.trim()));
                    }
                  }
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("the bridges into an effect type say what a nullable focus becomes")
    void effectBridgesTypeAsWritten() {
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.Bridges",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.free.Free;
                  import org.higherkindedj.hkt.Monoids;
                  import org.higherkindedj.hkt.effect.EitherOrBothPath;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.higherkindedj.hkt.effect.IOPath;
                  import org.higherkindedj.hkt.effect.ListPath;
                  import org.higherkindedj.hkt.effect.MaybePath;
                  import org.higherkindedj.hkt.effect.NonDetPath;
                  import org.higherkindedj.hkt.effect.OptionalPath;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.StreamPath;
                  import org.higherkindedj.hkt.effect.TryPath;
                  import org.higherkindedj.hkt.effect.VStreamPath;
                  import org.higherkindedj.hkt.effect.VTaskPath;
                  import org.higherkindedj.hkt.effect.ValidationPath;
                  import org.higherkindedj.hkt.either.Either;
                  import org.higherkindedj.hkt.effect.PathOps;
                  import org.higherkindedj.hkt.expression.FilterableSteps2;
                  import org.higherkindedj.hkt.expression.For;
                  import org.higherkindedj.hkt.expression.ForPath;
                  import org.higherkindedj.hkt.expression.MaybePathSteps2;
                  import org.higherkindedj.hkt.maybe.MaybeKind;
                  import org.higherkindedj.hkt.maybe.MaybeKindHelper;
                  import org.higherkindedj.hkt.maybe.MaybeMonad;
                  import org.higherkindedj.hkt.state_op.StateOpKind;
                  import org.higherkindedj.hkt.state_op.StateOps;
                  import org.higherkindedj.hkt.maybe.Maybe;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.Prism;
                  import org.higherkindedj.optics.each.EachInstances;
                  import org.higherkindedj.optics.extensions.LensExtensions;
                  import org.higherkindedj.optics.extensions.PrismExtensions;
                  import org.higherkindedj.optics.extensions.TraversalExtensions;
                  import org.higherkindedj.optics.fluent.OpticOps;
                  import org.higherkindedj.optics.focus.AffinePath;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.focus.TraversalPath;
                  import org.higherkindedj.optics.free.OpticOpKind;
                  import org.higherkindedj.optics.free.OpticPrograms;
                  import org.higherkindedj.optics.util.Prisms;
                  import org.higherkindedj.optics.util.Traversals;
                  import org.jspecify.annotations.Nullable;

                  class Bridges {
                    record Config(@Nullable String apiKey) {}

                    static final Lens<Config, @Nullable String> API_KEY =
                        Lens.of(Config::apiKey, (c, k) -> new Config(k));
                    static final FocusPath<Config, @Nullable String> KEY = FocusPath.of(API_KEY);
                    static final AffinePath<Config, @Nullable String> SOME_KEY = KEY.asAffine();

                    static void use(Config config, List<@Nullable String> tags) {
                      // From an optic: a null focus becomes an error, or absent.
                      EitherPath<String, String> keyed = KEY.toEitherPath(config, "no key");
                      EitherPath<String, String> lazily = KEY.toEitherPath(config, () -> "no key");
                      TryPath<String> tried = KEY.toTryPath(config, IllegalStateException::new);
                      EitherPath<String, String> affine = SOME_KEY.toEitherPath(config, "no key");

                      TraversalPath<List<@Nullable String>, @Nullable String> each =
                          TraversalPath.of(Traversals.forList());
                      ListPath<String> listed = each.toListPath(tags);
                      NonDetPath<String> choices = each.toNonDetPath(tags);
                      StreamPath<String> streamed = each.toStreamPath(tags);
                      VStreamPath<String> vstreamed = each.toVStreamPath(tags);
                      String folded = each.fold(Monoids.string(), tags);
                      VTaskPath<List<Integer>> lengths =
                          each.traverseWith(
                              t -> Path.vtaskPure(t == null ? 0 : t.length()), tags);

                      // From an Effect Path: focus reads a null focus as absent.
                      MaybePath<String> maybe = Path.just(config).focus(KEY);
                      OptionalPath<String> optional = Path.present(config).focus(KEY);
                      MaybePath<String> maybeAffine = Path.just(config).focus(SOME_KEY);
                      OptionalPath<String> optionalAffine = Path.present(config).focus(SOME_KEY);
                      EitherPath<String, String> either =
                          Path.<String, Config>right(config).focus(SOME_KEY, "no key");
                      EitherPath<String, String> ruledOut =
                          Path.<String, Config>right(config).focus(KEY.nullable(), "no key");
                      // nullable() keeps the focus type, so a chained call infers it.
                      EitherPath<String, Integer> chainedLength =
                          Path.<String, Config>right(config)
                              .focus(KEY.nullable(), "no key")
                              .map(k -> k.length());
                      TryPath<String> tryFocus =
                          Path.success(config).focus(SOME_KEY, IllegalStateException::new);
                      IOPath<String> io =
                          Path.ioPure(config).focus(SOME_KEY, IllegalStateException::new);
                      VTaskPath<String> task =
                          Path.vtaskPure(config).focus(SOME_KEY, IllegalStateException::new);
                      VStreamPath<String> stream = Path.vstreamPure(config).focus(SOME_KEY);
                      MaybePath<String> id = Path.id(config).focus(SOME_KEY);
                      ValidationPath<String, String> valid =
                          Path.<String, Config>valid(config, (a, b) -> a + b)
                              .focus(SOME_KEY, "no key");
                      EitherOrBothPath<String, String> both =
                          Path.<String, Config>right(config, (a, b) -> a + b)
                              .focus(SOME_KEY, "no key");
                      MaybePathSteps2<Config, String> matched =
                          ForPath.from(Path.just(config)).match(SOME_KEY);
                      VTaskPath<Optional<Integer>> keyLength =
                          SOME_KEY.traverseWith(k -> Path.vtaskPure(k.length()), config);

                      // Helpers that hand a focus to Either, Validated or Maybe.
                      Either<String, String> got = LensExtensions.getEither(API_KEY, "none", config);
                      Validated<String, String> checked =
                          LensExtensions.getValidated(API_KEY, "none", config);
                      Either<String, Config> modified =
                          LensExtensions.modifyEither(
                              API_KEY,
                              k -> Either.right(k == null ? "key" : k),
                              config);
                      Maybe<Config> maybeModified =
                          OpticOps.modifyMaybe(
                              config,
                              API_KEY,
                              k -> Maybe.just(k == null ? "key" : k));
                      Either<String, Config> opsModified =
                          OpticOps.modifyEither(
                              config,
                              API_KEY,
                              k -> Either.<String, String>right(k == null ? "key" : k));
                      Validated<List<String>, List<@Nullable String>> all =
                          OpticOps.modifyAllValidated(
                              tags,
                              Traversals.<@Nullable String>forList(),
                              t -> Validated.<String, String>valid(t == null ? "-" : t));
                      Either<String, List<@Nullable String>> extended =
                          TraversalExtensions.modifyAllEither(
                              Traversals.<@Nullable String>forList(),
                              t -> Either.<String, String>right(t == null ? "-" : t),
                              tags);

                      // A prism, and the Each bridges, at a nullable element.
                      Prism<List<@Nullable String>, @Nullable String> head = Prisms.listHead();
                      Either<String, String> first = PrismExtensions.getEither(head, "none", tags);
                      VStreamPath<String> fromEach =
                          VStreamPath.fromEach(tags, EachInstances.<@Nullable String>listEach());
                      EitherPath<String, List<String>> traversed =
                          PathOps.traverseEachEither(
                              tags,
                              EachInstances.<@Nullable String>listEach(),
                              t -> Path.<String, String>right(t == null ? "-" : t));
                      Free<StateOpKind.Witness<List<@Nullable String>>, Optional<String>> peeked =
                          StateOps.preview(head);
                      FilterableSteps2<MaybeKind.Witness, List<@Nullable String>, String> forMatched =
                          For.from(MaybeMonad.INSTANCE, MaybeKindHelper.MAYBE.just(tags)).match(head);

                      // Optic programs: a read that can be absent, and the writes.
                      Free<OpticOpKind.Witness, Optional<String>> previewed =
                          OpticPrograms.preview(config, API_KEY.asFold());
                      Free<OpticOpKind.Witness, List<@Nullable String>> gotAll =
                          OpticPrograms.getAll(tags, Traversals.<@Nullable String>forList());
                      Free<OpticOpKind.Witness, Config> cleared =
                          OpticPrograms.set(config, API_KEY, null);
                      Free<OpticOpKind.Witness, Integer> counted =
                          OpticPrograms.count(tags, Traversals.<@Nullable String>forList());
                    }
                  }
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("every helper that hands a focus to an effect type takes a nullable one")
    void effectHelpersTypeAsWritten() {
      // Each call is refused on a declaration that keeps the focus non-null. A function that sees
      // only a matched focus, as a prism's does, takes it non-null, so it dereferences unchecked.
      Compilation compilation =
          compileChecked(
              source(
                  "com.example.EffectHelpers",
                  """
                  package com.example;

                  import java.util.List;
                  import java.util.Optional;
                  import org.higherkindedj.hkt.Semigroups;
                  import org.higherkindedj.hkt.TypeArity;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.effect.CompletableFuturePath;
                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.higherkindedj.hkt.effect.ListPath;
                  import org.higherkindedj.hkt.effect.MaybePath;
                  import org.higherkindedj.hkt.effect.NonDetPath;
                  import org.higherkindedj.hkt.effect.OptionalPath;
                  import org.higherkindedj.hkt.effect.Path;
                  import org.higherkindedj.hkt.effect.PathOps;
                  import org.higherkindedj.hkt.effect.TryPath;
                  import org.higherkindedj.hkt.effect.VStreamPath;
                  import org.higherkindedj.hkt.effect.VTaskPath;
                  import org.higherkindedj.hkt.effect.ValidationPath;
                  import org.higherkindedj.hkt.either.Either;
                  import org.higherkindedj.hkt.expression.ForIndexed;
                  import org.higherkindedj.hkt.expression.ForPath;
                  import org.higherkindedj.hkt.expression.ForState;
                  import org.higherkindedj.hkt.expression.ForTraversal;
                  import org.higherkindedj.hkt.expression.OptionalPathSteps2;
                  import org.higherkindedj.hkt.free.Free;
                  import org.higherkindedj.hkt.id.Id;
                  import org.higherkindedj.hkt.id.IdKind;
                  import org.higherkindedj.hkt.id.IdMonad;
                  import org.higherkindedj.hkt.maybe.Maybe;
                  import org.higherkindedj.hkt.maybe.MaybeKind;
                  import org.higherkindedj.hkt.maybe.MaybeKindHelper;
                  import org.higherkindedj.hkt.maybe.MaybeMonad;
                  import org.higherkindedj.hkt.state_op.StateOpKind;
                  import org.higherkindedj.hkt.state_op.StateOps;
                  import org.higherkindedj.hkt.trymonad.Try;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.optics.Iso;
                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.Prism;
                  import org.higherkindedj.optics.Traversal;
                  import org.higherkindedj.optics.each.EachInstances;
                  import org.higherkindedj.optics.extensions.LensExtensions;
                  import org.higherkindedj.optics.extensions.PrismExtensions;
                  import org.higherkindedj.optics.extensions.TraversalExtensions;
                  import org.higherkindedj.optics.fluent.OpticOps;
                  import org.higherkindedj.optics.focus.AffinePath;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.free.OpticOpKind;
                  import org.higherkindedj.optics.free.OpticPrograms;
                  import org.higherkindedj.optics.util.IndexedTraversals;
                  import org.higherkindedj.optics.util.Prisms;
                  import org.higherkindedj.optics.util.Traversals;
                  import org.jspecify.annotations.Nullable;

                  class EffectHelpers {
                    record Config(@Nullable String apiKey, List<@Nullable String> tags) {}

                    record Outer(Config config) {}

                    static final Lens<Config, @Nullable String> API_KEY =
                        Lens.of(Config::apiKey, (c, k) -> new Config(k, c.tags()));
                    // NullAway does not carry a nested @Nullable into an implicit lambda parameter.
                    static final Lens<Config, List<@Nullable String>> TAGS =
                        Lens.of(
                            Config::tags,
                            (Config c, List<@Nullable String> t) -> new Config(c.apiKey(), t));
                    static final AffinePath<Config, @Nullable String> SOME_KEY =
                        FocusPath.of(API_KEY).asAffine();
                    static final Traversal<List<@Nullable String>, @Nullable String> ELEMS =
                        Traversals.<@Nullable String>forList();
                    static final Prism<List<@Nullable String>, @Nullable String> HEAD =
                        Prisms.listHead();
                    static final Lens<Outer, Config> CONFIG =
                        Lens.of(Outer::config, (o, c) -> new Outer(c));
                    static final Lens<Config, Config> SELF = Lens.of(c -> c, (c, n) -> n);

                    static <G extends WitnessArity<TypeArity.Unary>> void use(
                        Config config,
                        List<@Nullable String> tags,
                        StateOps.Bound<List<@Nullable String>, G> bound) {
                      // LensExtensions: the function sees the focus as it is.
                      Maybe<Config> lm =
                          LensExtensions.modifyMaybe(API_KEY, k -> Maybe.just(k == null ? "x" : k), config);
                      Validated<String, Config> lv =
                          LensExtensions.modifyValidated(
                              API_KEY, k -> Validated.valid(k == null ? "x" : k), config);
                      Try<Config> lt =
                          LensExtensions.modifyTry(API_KEY, k -> Try.success(k == null ? "x" : k), config);
                      Either<String, Config> ls =
                          LensExtensions.setIfValid(
                              API_KEY, k -> Either.right(k == null ? "x" : k), null, config);

                      // PrismExtensions: only a matched, non-null focus reaches the function.
                      Validated<String, String> pv = PrismExtensions.getValidated(HEAD, "none", tags);
                      Maybe<List<@Nullable String>> pm =
                          PrismExtensions.modifyMaybe(HEAD, k -> Maybe.just(k.trim()), tags);
                      Either<String, List<@Nullable String>> pe =
                          PrismExtensions.modifyEither(HEAD, "none", k -> Either.right(k.trim()), tags);
                      Validated<String, List<@Nullable String>> pval =
                          PrismExtensions.modifyValidated(
                              HEAD, "none", k -> Validated.valid(k.trim()), tags);

                      // TraversalExtensions.
                      Maybe<List<@Nullable String>> tm =
                          TraversalExtensions.modifyAllMaybe(
                              ELEMS, t -> Maybe.just(t == null ? "-" : t), tags);
                      Validated<List<String>, List<@Nullable String>> tv =
                          TraversalExtensions.modifyAllValidated(
                              ELEMS, t -> Validated.valid(t == null ? "-" : t), tags);
                      List<@Nullable String> tw =
                          TraversalExtensions.modifyWherePossible(
                              ELEMS, t -> Maybe.just(t == null ? "-" : t), tags);
                      int tc =
                          TraversalExtensions.<String, List<@Nullable String>, @Nullable String>countValid(
                              ELEMS, t -> Either.right(t == null ? "-" : t), tags);
                      List<String> tce =
                          TraversalExtensions
                              .<String, List<@Nullable String>, @Nullable String>collectErrors(
                                  ELEMS, t -> Either.right(t == null ? "-" : t), tags);

                      // OpticOps and its fluent mirror.
                      Either<String, List<@Nullable String>> oae =
                          OpticOps.modifyAllEither(tags, ELEMS, t -> Either.right(t == null ? "-" : t));
                      Either<String, Config> be =
                          OpticOps.modifyingWithValidation(config)
                              .throughEither(API_KEY, k -> Either.right(k == null ? "x" : k));
                      Maybe<Config> bm =
                          OpticOps.modifyingWithValidation(config)
                              .throughMaybe(API_KEY, k -> Maybe.just(k == null ? "x" : k));
                      Validated<List<String>, List<@Nullable String>> bav =
                          OpticOps.modifyingWithValidation(tags)
                              .allThroughValidated(ELEMS, t -> Validated.valid(t == null ? "-" : t));
                      Either<String, List<@Nullable String>> bae =
                          OpticOps.modifyingWithValidation(tags)
                              .allThroughEither(ELEMS, t -> Either.right(t == null ? "-" : t));

                      // PathOps over an Each: the function sees each element as it is.
                      MaybePath<List<String>> pom =
                          PathOps.traverseEachMaybe(
                              tags,
                              EachInstances.<@Nullable String>listEach(),
                              t -> Path.just(t == null ? "-" : t));
                      ValidationPath<String, List<String>> pov =
                          PathOps.traverseEachValidated(
                              tags,
                              EachInstances.<@Nullable String>listEach(),
                              t -> Path.valid(t == null ? "-" : t, Semigroups.string()),
                              Semigroups.string());
                      TryPath<List<String>> pot =
                          PathOps.traverseEachTry(
                              tags,
                              EachInstances.<@Nullable String>listEach(),
                              t -> Path.success(t == null ? "-" : t));
                      MaybePath<List<Integer>> lengths =
                          PathOps.traverseMaybe(tags, t -> Path.just(t == null ? 0 : t.length()));

                      // StateOps and its Bound mirror.
                      Free<StateOpKind.Witness<List<@Nullable String>>, List<@Nullable String>> st =
                          StateOps.traverseOver(ELEMS, t -> t == null ? "-" : t);
                      Free<G, Optional<String>> bp = bound.preview(HEAD);
                      Free<G, List<@Nullable String>> bt =
                          bound.traverseOver(ELEMS, t -> t == null ? "-" : t);

                      // The comprehensions.
                      OptionalPathSteps2<Config, String> om =
                          ForPath.from(Path.present(config)).match(SOME_KEY);
                      ForState.Steps<MaybeKind.Witness, Config> steps =
                          ForState.withState(MaybeMonad.INSTANCE, MaybeKindHelper.MAYBE.just(config))
                              .update(API_KEY, null)
                              .modify(API_KEY, k -> k == null ? "x" : k)
                              .fromThen(c -> MaybeKindHelper.MAYBE.just("k"), API_KEY)
                              .traverse(TAGS, ELEMS, t -> MaybeKindHelper.MAYBE.just(t == null ? "-" : t));

                      // ForState through plain Steps, FilterableSteps and both zoomed kinds.
                      Traversal<Config, @Nullable String> eachTag = TAGS.andThen(ELEMS);
                      Iso<@Nullable String, @Nullable String> same = Iso.of(k -> k, k -> k);
                      ForState.Steps<IdKind.Witness, Config> plain =
                          ForState.withState(IdMonad.instance(), Id.of(config))
                              .update(API_KEY, null)
                              .modify(API_KEY, k -> k == null ? "x" : k)
                              .fromThen(c -> Id.of("k"), API_KEY)
                              .traverse(TAGS, ELEMS, t -> Id.of(t == null ? "-" : t))
                              .traverseOver(eachTag, t -> Id.of(t == null ? "-" : t))
                              .modifyThrough(eachTag, t -> t)
                              .modifyThrough(SELF.asTraversal(), API_KEY, k -> k)
                              .modifyVia(API_KEY, same, k -> k)
                              .updateVia(API_KEY, same, null);
                      ForState.FilterableSteps<MaybeKind.Witness, Config> matchedState =
                          ForState.withState(MaybeMonad.INSTANCE, MaybeKindHelper.MAYBE.just(config))
                              .matchThen(TAGS, HEAD, API_KEY)
                              .matchThen(c -> Optional.ofNullable(c.apiKey()), API_KEY)
                              .traverseOver(
                                  eachTag, t -> MaybeKindHelper.MAYBE.just(t == null ? "-" : t));
                      var zoomed =
                          ForState.withState(IdMonad.instance(), Id.of(new Outer(config)))
                              .zoom(CONFIG)
                              .update(API_KEY, null)
                              .modify(API_KEY, k -> k)
                              .fromThen(c -> Id.of("k"), API_KEY);
                      var filterableZoomed =
                          ForState.withState(
                                  MaybeMonad.INSTANCE, MaybeKindHelper.MAYBE.just(new Outer(config)))
                              .zoom(CONFIG)
                              .update(API_KEY, null)
                              .modify(API_KEY, k -> k)
                              .fromThen(c -> MaybeKindHelper.MAYBE.just("k"), API_KEY);

                      // ForTraversal and ForIndexed write through a lens to a nullable field.
                      var traversed =
                          ForTraversal.over(
                                  Traversals.<Config>forList(), List.of(config), IdMonad.instance())
                              .modify(API_KEY, k -> k)
                              .set(API_KEY, null);
                      var indexed =
                          ForIndexed.overIndexed(
                                  IndexedTraversals.<Config>forList(), List.of(config), IdMonad.instance())
                              .modify(API_KEY, (i, k) -> k)
                              .set(API_KEY, i -> null);

                      // PathOps' list traversers take a nullable element. Par.traverse does too, but
                      // Par is compiled with preview features, which this compiler cannot load.
                      EitherPath<String, List<Integer>> le =
                          PathOps.traverseEither(tags, t -> Path.right(t == null ? 0 : t.length()));
                      ValidationPath<String, List<Integer>> lva =
                          PathOps.traverseValidated(
                              tags,
                              t -> Path.valid(t == null ? 0 : t.length(), Semigroups.string()),
                              Semigroups.string());
                      TryPath<List<Integer>> ltr =
                          PathOps.traverseTry(tags, t -> Path.success(t == null ? 0 : t.length()));
                      OptionalPath<List<Integer>> lo =
                          PathOps.traverseOptional(tags, t -> Path.present(t == null ? 0 : t.length()));
                      NonDetPath<List<Integer>> ln =
                          PathOps.traverseNonDet(
                              tags, t -> Path.list(List.of(t == null ? 0 : t.length())));
                      ListPath<List<Integer>> ll =
                          PathOps.traverseListPath(
                              tags, t -> Path.listPath(List.of(t == null ? 0 : t.length())));
                      VTaskPath<List<Integer>> lvt =
                          PathOps.traverseVTask(tags, t -> Path.vtaskPure(t == null ? 0 : t.length()));
                      VTaskPath<List<Integer>> lvp =
                          PathOps.traverseVTaskPar(
                              tags, t -> Path.vtaskPure(t == null ? 0 : t.length()));
                      CompletableFuturePath<List<Integer>> lf =
                          PathOps.traverseFuture(
                              tags, t -> CompletableFuturePath.completed(t == null ? 0 : t.length()));
                      VStreamPath<Integer> lvs =
                          PathOps.traverseVStream(
                              tags, t -> Path.vstreamPure(t == null ? 0 : t.length()));

                      // The OpticPrograms the bridge fixture leaves out.
                      Free<OpticOpKind.Witness, Optional<String>> pt = OpticPrograms.preview(tags, ELEMS);
                      Free<OpticOpKind.Witness, Config> md = OpticPrograms.modify(config, API_KEY, k -> k);
                      Free<OpticOpKind.Witness, List<@Nullable String>> sa =
                          OpticPrograms.setAll(tags, ELEMS, null);
                      Free<OpticOpKind.Witness, List<@Nullable String>> ma =
                          OpticPrograms.modifyAll(tags, ELEMS, t -> t);
                      Free<OpticOpKind.Witness, Boolean> ex =
                          OpticPrograms.exists(tags, ELEMS, t -> t == null);
                      Free<OpticOpKind.Witness, Boolean> al = OpticPrograms.all(tags, ELEMS, t -> t != null);
                    }
                  }
                  """));

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("emitted by a generator")
  class Generated {

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

    private static JavaFileObject box(String annotation) {
      return source("com.example.Box", BOX.formatted(annotation, annotation));
    }

    private static JavaFileObject uses(String imports, String body) {
      return source(
          "com.example.Uses",
          """
          package com.example;

          import java.util.List;
          import java.util.Optional;
          %s
          import org.jspecify.annotations.Nullable;

          class Uses {
            static void use(Box<@Nullable String> box) {
          %s
            }
          }
          """
              .formatted(imports, body));
    }

    @Test
    @DisplayName("@GenerateLenses: the lens and the wither over a nullable component")
    void lenses() {
      Compilation compilation =
          compileChecked(
              List.of(new LensProcessor()),
              box("GenerateLenses"),
              uses(
                  "import org.higherkindedj.optics.Lens;",
                  """
                  Lens<Box<@Nullable String>, @Nullable String> label = BoxLenses.label();
                  Lens<Box<@Nullable String>, @Nullable String> value = BoxLenses.value();
                  Box<@Nullable String> cleared =
                      BoxLenses.withLabel(value.set(null, label.set(null, box)), null);
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("@GenerateGetters: the getter over a nullable component")
    void getters() {
      Compilation compilation =
          compileChecked(
              List.of(new GetterProcessor()),
              box("GenerateGetters"),
              uses(
                  "import org.higherkindedj.optics.Getter;",
                  """
                  Getter<Box<@Nullable String>, @Nullable String> label = BoxGetters.label();
                  @Nullable String read = label.get(box);
                  Optional<String> first = label.preview(box);
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("@GenerateSetters: the setter over a nullable component")
    void setters() {
      Compilation compilation =
          compileChecked(
              List.of(new SetterProcessor()),
              box("GenerateSetters"),
              uses(
                  "import org.higherkindedj.optics.Setter;",
                  """
                  Setter<Box<@Nullable String>, @Nullable String> label = BoxSetters.label();
                  Box<@Nullable String> cleared = label.set(null, box);
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("@GenerateFolds: the folds over a nullable component and nullable elements")
    void folds() {
      // A generated fold applies a Function<? super @Nullable String, ? extends M>, and NullAway
      // 0.14 does not carry the @Nullable through a ? super wildcard: it reports the call, though
      // the same call through a Function<@Nullable String, M> passes. So the generated body goes
      // unchecked, and the use site still holds its signatures to account.
      Compilation compilation =
          compileCheckedOutsideGenerated(
              List.of(new FoldProcessor()),
              box("GenerateFolds"),
              uses(
                  "import org.higherkindedj.optics.Fold;",
                  """
                  Fold<Box<@Nullable String>, @Nullable String> label = BoxFolds.label();
                  Fold<Box<@Nullable String>, @Nullable String> tags = BoxFolds.tags();
                  List<@Nullable String> all = tags.getAll(box);
                  Optional<String> first = tags.preview(box);
                  Optional<String> labelled = label.find(l -> l != null, box);
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("@GenerateTraversals: the traversal over a list of nullable elements")
    void traversals() {
      // The generated body hands traverseList a Function<@Nullable String, Kind<F, @Nullable
      // String>>, and NullAway 0.14 does not infer a type argument through the wildcards of
      // traverseList's parameters: it settles on String. So, as for folds, the generated body goes
      // unchecked and the use site holds the signature to account.
      Compilation compilation =
          compileCheckedOutsideGenerated(
              List.of(new TraversalProcessor()),
              box("GenerateTraversals"),
              uses(
                  "import org.higherkindedj.optics.Traversal;",
                  """
                  Traversal<Box<@Nullable String>, @Nullable String> tags = BoxTraversals.tags();
                  Optional<String> first = tags.asFold().preview(box);
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("@GenerateFocus: a widened nullable component, and a nullable element")
    void focus() {
      // A nullable component widens through nullable(), which rules the null out, so the focus is
      // non-null; a nullable element is not consumed by anything, so each() arrives at it as is.
      Compilation compilation =
          compileChecked(
              List.of(new FocusProcessor()),
              box("GenerateFocus"),
              uses(
                  """
                  import org.higherkindedj.optics.focus.AffinePath;
                  import org.higherkindedj.optics.focus.FocusPath;
                  import org.higherkindedj.optics.focus.TraversalPath;""",
                  """
                  FocusPath<Box<@Nullable String>, @Nullable String> value = BoxFocus.value();
                  Box<@Nullable String> cleared = value.set(null, box);
                  AffinePath<Box<@Nullable String>, String> label = BoxFocus.label();
                  Optional<String> read = label.getOptional(cleared);
                  TraversalPath<Box<@Nullable String>, @Nullable String> tags = BoxFocus.tags();
                  List<@Nullable String> all = tags.getAll(box);
                  Optional<String> first = tags.preview(box);
                  """));

      assertThat(compilation).succeeded();
    }
  }
}
