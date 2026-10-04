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
