// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContainsRaw;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for {@link FocusProcessor} Kind field support.
 *
 * <p>These tests verify that the processor correctly generates traverseOver() calls for Kind&lt;F,
 * A&gt; fields, using the appropriate Traverse instances from the library.
 */
@DisplayName("FocusProcessor Kind Field Tests")
public class FocusProcessorKindFieldTest {

  @Nested
  @DisplayName("ListKind Field Support")
  class ListKindFieldSupport {

    @Test
    @DisplayName("should generate TraversalPath with traverseOver for Kind<ListKind.Witness, A>")
    void shouldGenerateTraversalPathForListKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Team",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.list.ListKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Team(String name, Kind<ListKind.Witness, String> members) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Check that the generated code contains traverseOver with ListTraverse
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.TeamFocus", "TraversalPath<Team, String> members()");
      assertGeneratedCodeContainsRaw(compilation, "com.example.TeamFocus", "traverseOver");
      assertGeneratedCodeContainsRaw(compilation, "com.example.TeamFocus", "ListTraverse.INSTANCE");
    }

    @Test
    @DisplayName("should handle Kind field with nested record type")
    void shouldHandleNestedRecordTypeInKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Project",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.list.ListKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Project(
                  String name,
                  Kind<ListKind.Witness, Task> tasks
              ) {}

              record Task(String title, boolean done) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ProjectFocus", "TraversalPath<Project, Task> tasks()");
    }
  }

  @Nested
  @DisplayName("MaybeKind Field Support")
  class MaybeKindFieldSupport {

    @Test
    @DisplayName("should generate AffinePath with traverseOver for Kind<MaybeKind.Witness, A>")
    void shouldGenerateAffinePathForMaybeKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Config",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.maybe.MaybeKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Config(String name, Kind<MaybeKind.Witness, String> description) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ConfigFocus", "AffinePath<Config, String> description()");
      assertGeneratedCodeContainsRaw(compilation, "com.example.ConfigFocus", "traverseOver");
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ConfigFocus", "MaybeTraverse.INSTANCE");
      assertGeneratedCodeContainsRaw(compilation, "com.example.ConfigFocus", ".headOption()");
    }
  }

  @Nested
  @DisplayName("OptionalKind Field Support")
  class OptionalKindFieldSupport {

    @Test
    @DisplayName("should generate AffinePath for Kind<OptionalKind.Witness, A>")
    void shouldGenerateAffinePathForOptionalKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.optional.OptionalKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Profile(String name, Kind<OptionalKind.Witness, String> nickname) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ProfileFocus", "AffinePath<Profile, String> nickname()");
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ProfileFocus", "OptionalTraverse.INSTANCE");
    }
  }

  @Nested
  @DisplayName("StreamKind Field Support")
  class StreamKindFieldSupport {

    @Test
    @DisplayName("should generate TraversalPath for Kind<StreamKind.Witness, A>")
    void shouldGenerateTraversalPathForStreamKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.DataStream",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.stream.StreamKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record DataStream(String id, Kind<StreamKind.Witness, Integer> values) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.DataStreamFocus",
          "TraversalPath<DataStream, Integer> values()");
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.DataStreamFocus", "StreamTraverse.INSTANCE");
    }
  }

  @Nested
  @DisplayName("TryKind Field Support")
  class TryKindFieldSupport {

    @Test
    @DisplayName("should generate AffinePath for Kind<TryKind.Witness, A>")
    void shouldGenerateAffinePathForTryKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Result",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.trymonad.TryKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Result(String operation, Kind<TryKind.Witness, String> outcome) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ResultFocus", "AffinePath<Result, String> outcome()");
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ResultFocus", "TryTraverse.INSTANCE");
    }
  }

  @Nested
  @DisplayName("IdKind Field Support")
  class IdKindFieldSupport {

    @Test
    @DisplayName("should generate AffinePath for Kind<IdKind.Witness, A>")
    void shouldGenerateAffinePathForIdKind() {
      // Note: Even though IdKind always contains exactly one element, we return AffinePath
      // because traverseOver() returns TraversalPath and we narrow via headOption().
      // This is a type-safe approach that works correctly at runtime.
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Wrapper",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.id.IdKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Wrapper(String label, Kind<IdKind.Witness, String> value) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.WrapperFocus", "AffinePath<Wrapper, String> value()");
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.WrapperFocus", "IdTraverse.INSTANCE");
      assertGeneratedCodeContainsRaw(compilation, "com.example.WrapperFocus", ".headOption()");
    }
  }

  @Nested
  @DisplayName("Parameterised Witness Support")
  class ParameterisedWitnessSupport {

    @Test
    @DisplayName("should handle Kind<EitherKind.Witness<E>, A> with factory method")
    void shouldHandleEitherKindWithTypeParameter() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.Response",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.either.EitherKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Response(
                  String requestId,
                  Kind<EitherKind.Witness<String>, Integer> result
              ) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.ResponseFocus", "AffinePath<Response, Integer> result()");
      // Should use instance() factory method with type parameter
      assertGeneratedCodeContainsRaw(compilation, "com.example.ResponseFocus", "EitherTraverse");
      assertGeneratedCodeContainsRaw(compilation, "com.example.ResponseFocus", "instance()");
    }
  }

  @Nested
  @DisplayName("Custom Kind Field Support via @TraverseField")
  class CustomKindFieldSupport {

    @Test
    @DisplayName("should use @TraverseField annotation for custom Kind types")
    void shouldUseKindFieldAnnotationForCustomTypes() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.CustomData",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.list.ListKind;
              import org.higherkindedj.hkt.list.ListTraverse;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.optics.annotations.TraverseField;
              import org.higherkindedj.optics.annotations.KindSemantics;

              @GenerateFocus
              public record CustomData(
                  String name,
                  @TraverseField(
                      traverse = "org.higherkindedj.hkt.list.ListTraverse.INSTANCE",
                      semantics = KindSemantics.ZERO_OR_MORE
                  )
                  Kind<ListKind.Witness, String> items
              ) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.CustomDataFocus", "TraversalPath<CustomData, String> items()");
    }

    @Test
    @DisplayName("should respect ZERO_OR_ONE semantics from @TraverseField")
    void shouldRespectZeroOrOneSemanticsFromAnnotation() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.AnnotatedConfig",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.maybe.MaybeKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.optics.annotations.TraverseField;
              import org.higherkindedj.optics.annotations.KindSemantics;

              @GenerateFocus
              public record AnnotatedConfig(
                  String name,
                  @TraverseField(
                      traverse = "org.higherkindedj.hkt.maybe.MaybeTraverse.INSTANCE",
                      semantics = KindSemantics.ZERO_OR_ONE
                  )
                  Kind<MaybeKind.Witness, String> optional
              ) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.AnnotatedConfigFocus",
          "AffinePath<AnnotatedConfig, String> optional()");
    }
  }

  @Nested
  @DisplayName("Mixed Field Types")
  class MixedFieldTypes {

    @Test
    @DisplayName("should handle record with both standard and Kind fields")
    void shouldHandleMixedFieldTypes() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.MixedRecord",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.list.ListKind;
              import org.higherkindedj.hkt.maybe.MaybeKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record MixedRecord(
                  String name,
                  Optional<String> nickname,
                  List<String> tags,
                  Kind<ListKind.Witness, Integer> scores,
                  Kind<MaybeKind.Witness, String> description
              ) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Standard field
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.MixedRecordFocus", "FocusPath<MixedRecord, String> name()");

      // Optional field - uses .some()
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.MixedRecordFocus",
          "AffinePath<MixedRecord, String> nickname()");

      // List field - uses .each()
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.MixedRecordFocus", "TraversalPath<MixedRecord, String> tags()");

      // Kind<ListKind.Witness, Integer> - uses traverseOver()
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.MixedRecordFocus",
          "TraversalPath<MixedRecord, Integer> scores()");

      // Kind<MaybeKind.Witness, String> - uses traverseOver().headOption()
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.MixedRecordFocus",
          "AffinePath<MixedRecord, String> description()");
    }
  }

  @Nested
  @DisplayName("Unknown Kind Types")
  class UnknownKindTypes {

    @Test
    @DisplayName("should generate standard FocusPath for unrecognised Kind types")
    void shouldGenerateFocusPathForUnrecognisedKind() {
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.UnknownKindRecord",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              // Custom witness type not in registry
              interface CustomWitness extends WitnessArity<TypeArity.Unary> {}

              @GenerateFocus
              public record UnknownKindRecord(
                  String name,
                  Kind<CustomWitness, String> data
              ) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).succeeded();

      // Should fall back to standard FocusPath since witness is unknown
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.UnknownKindRecordFocus", "FocusPath<UnknownKindRecord");
    }
  }

  @Nested
  @DisplayName("Raw Parameterised Witness")
  class RawParameterisedWitness {

    @Test
    @DisplayName("should skip witness type arguments when a parameterised witness is used raw")
    void shouldSkipWitnessTypeArgumentsForRawWitness() {
      // EitherKind.Witness is registered as parameterised, but used RAW here so the generated
      // traverseOver call carries no witness type arguments. The record itself does not compile
      // (the raw witness violates Kind's WitnessArity bound), but annotation processing runs
      // before that bound check, so the generation path is still exercised.
      final var sourceFile =
          JavaFileObjects.forSourceString(
              "com.example.RawWitnessRecord",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.either.EitherKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              @SuppressWarnings("rawtypes")
              public record RawWitnessRecord(
                  String name, Kind<EitherKind.Witness, Integer> outcome) {}
              """);

      var compilation = javac().withProcessors(new FocusProcessor()).compile(sourceFile);

      assertThat(compilation).hadErrorContaining("not within bounds");
    }
  }

  @Nested
  @DisplayName("Wildcard Type Arguments")
  class WildcardTypeArguments {

    private static final String TRAVERSE_LIST =
        "@TraverseField(traverse = \"org.higherkindedj.hkt.list.ListTraverse.INSTANCE\","
            + " semantics = KindSemantics.ZERO_OR_MORE) ";

    /** A record declaring the given type parameters, with one component of the given type. */
    private Compilation compile(String typeParameters, String component) {
      return javac()
          .withProcessors(new FocusProcessor())
          .compile(
              JavaFileObjects.forSourceString(
                  "com.example.Holder",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.either.EitherKind;
                  import org.higherkindedj.hkt.list.ListKind;
                  import org.higherkindedj.hkt.maybe.MaybeKind;
                  import org.higherkindedj.optics.annotations.GenerateFocus;
                  import org.higherkindedj.optics.annotations.KindSemantics;
                  import org.higherkindedj.optics.annotations.TraverseField;

                  @GenerateFocus
                  public record Holder%s(%s members) {}
                  """
                      .formatted(typeParameters, component)));
    }

    private Compilation compile(String component) {
      return compile("", component);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " -> ",
        value = {
          "Kind<ListKind.Witness, ? extends CharSequence> -> TraversalPath<Holder, CharSequence>"
              + " -> .<org.higherkindedj.hkt.list.ListKind.Witness, java.lang.CharSequence>"
              + "traverseOver(org.higherkindedj.hkt.list.ListTraverse.INSTANCE)",
          "Kind<ListKind.Witness, ?> -> TraversalPath<Holder, Object>"
              + " -> .<org.higherkindedj.hkt.list.ListKind.Witness, java.lang.Object>traverseOver(",
          "Kind<ListKind.Witness, ? super String> -> TraversalPath<Holder, Object>"
              + " -> .<org.higherkindedj.hkt.list.ListKind.Witness, java.lang.Object>traverseOver(",
          "Kind<MaybeKind.Witness, ? extends CharSequence> -> AffinePath<Holder, CharSequence>"
              + " -> java.lang.CharSequence>traverseOver("
              + "org.higherkindedj.hkt.maybe.MaybeTraverse.INSTANCE).headOption()",
          "Kind<? extends ListKind.Witness, String> -> TraversalPath<Holder, String>"
              + " -> .<org.higherkindedj.hkt.list.ListKind.Witness, java.lang.String>traverseOver(",
          "Kind<EitherKind.Witness<?>, String> -> AffinePath<Holder, String>"
              + " -> .<org.higherkindedj.hkt.either.EitherKind.Witness<java.lang.Object>,"
              + " java.lang.String>traverseOver("
              + "org.higherkindedj.hkt.either.EitherTraverse.<java.lang.Object>instance())",
          "Kind<EitherKind.Witness<? extends CharSequence>, String> -> AffinePath<Holder, String>"
              + " -> org.higherkindedj.hkt.either.EitherTraverse.<java.lang.CharSequence>instance()",
        })
    @DisplayName("a wildcard resolves to the type it stands for")
    void aWildcardResolvesToTheTypeItStandsFor(
        String component, String pathType, String traverseCall) {
      // The traversal rebuilds the container rather than writing into it, so a component
      // declared with the wildcard comes back holding the resolved type, which it admits.
      Compilation compilation = compile(component);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", pathType + " members()");
      assertGeneratedCodeContainsRaw(compilation, "com.example.HolderFocus", traverseCall);
    }

    @Test
    @DisplayName("a wildcard bounded by a record type variable resolves to that variable")
    void aWildcardBoundedByARecordTypeVariableResolvesToThatVariable() {
      Compilation compilation = compile("<T>", "Kind<ListKind.Witness, ? extends T>");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "TraversalPath<Holder<T>, T> members()");
    }

    @Test
    @DisplayName("a wildcard witness names no Traverse, so the field keeps its plain path")
    void aWildcardWitnessKeepsThePlainPath() {
      Compilation compilation = compile("Kind<?, String>");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "FocusPath<Holder, Kind<?, String>> members()");
    }

    @Test
    @DisplayName("a type variable witness names no Traverse either")
    void aTypeVariableWitnessNamesNoTraverseEither() {
      Compilation compilation = compile("<F extends WitnessArity<?>>", "Kind<F, String>");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.HolderFocus",
          "FocusPath<Holder<F>, Kind<F, String>> members()");
      assertThat(traverseFieldNotes(compilation)).isEmpty();
    }

    @Test
    @DisplayName("@TraverseField resolves a wildcard in either position the same way")
    void traverseFieldResolvesAWildcardInEitherPosition() {
      Compilation compilation =
          compile(TRAVERSE_LIST + "Kind<? extends ListKind.Witness, ? extends CharSequence>");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "TraversalPath<Holder, CharSequence> members()");
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.HolderFocus",
          ".<org.higherkindedj.hkt.list.ListKind.Witness, java.lang.CharSequence>traverseOver(");
    }

    @Test
    @DisplayName("@TraverseField on a wildcard witness leaves the field alone, and says so")
    void traverseFieldOnAWildcardWitnessLeavesTheFieldAloneAndSaysSo() {
      Compilation compilation = compile(TRAVERSE_LIST + "Kind<?, String>");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "FocusPath<Holder, Kind<?, String>> members()");
      assertThat(compilation)
          .hadNoteContaining(
              "@TraverseField: the annotation on record component 'Holder.members' is not"
                  + " applied. The witness of Kind<?, String> is a wildcard that stands for no"
                  + " type, so no Traverse instance can be named for it. Declare the witness the"
                  + " Traverse is written for in place of the wildcard, such as"
                  + " Kind<TreeKind.Witness, String> for a Traverse<TreeKind.Witness>.");
    }
  }

  @Nested
  @DisplayName("@TraverseField Not Applied")
  class TraverseFieldNotApplied {

    private static final String TRAVERSE_LIST =
        "@TraverseField(traverse = \"org.higherkindedj.hkt.list.ListTraverse.INSTANCE\","
            + " semantics = KindSemantics.ZERO_OR_MORE) ";

    private static final String NOT_APPLIED =
        "@TraverseField: the annotation on record component 'Holder.f' is not applied. ";

    /** A record with the given components, each written out with its name. */
    private Compilation compile(String components) {
      return compile("", components);
    }

    /** A record declaring the given type parameters, with the given named components. */
    private Compilation compile(String typeParameters, String components) {
      return javac()
          .withProcessors(new FocusProcessor())
          .compile(
              JavaFileObjects.forSourceString(
                  "com.example.Holder",
                  """
                  package com.example;

                  import java.util.List;
                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.TypeArity;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.list.ListKind;
                  import org.higherkindedj.optics.annotations.GenerateFocus;
                  import org.higherkindedj.optics.annotations.KindSemantics;
                  import org.higherkindedj.optics.annotations.TraverseField;

                  @GenerateFocus
                  @SuppressWarnings("rawtypes")
                  public record Holder%s(%s) {}
                  """
                      .formatted(typeParameters, components)));
    }

    @Test
    @DisplayName(
        "a witness that is the record's own type variable draws the note and keeps the path")
    void aTypeVariableWitnessDrawsTheNoteAndKeepsThePath() {
      // A Traverse is written for one witness and a type variable stands for any, so no expression
      // the author could name would be a Traverse for it; the generated class stays sound.
      Compilation compilation =
          compile("<F extends WitnessArity<TypeArity.Unary>>", TRAVERSE_LIST + "Kind<F, String> f");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "FocusPath<Holder<F>, Kind<F, String>>");
      assertThat(compilation)
          .hadNoteContaining(
              NOT_APPLIED
                  + "The witness of Kind<F, String> names the record's type variable F, which"
                  + " stands for any witness, while a Traverse is written for one witness in"
                  + " particular, so no Traverse instance can be named for it. Declare the witness"
                  + " the Traverse is written for in place of F, such as"
                  + " Kind<TreeKind.Witness, String> for a Traverse<TreeKind.Witness>, or drop the"
                  + " annotation and apply traverseOver to the path yourself, with a Traverse<F> in"
                  + " hand where F is known.");
      assertThat(traverseFieldNotes(compilation)).hasSize(1);
    }

    @Test
    @DisplayName("a wildcard bounded by the record's type variable is treated the same way")
    void aWildcardBoundedByTheTypeVariableIsTreatedTheSameWay() {
      Compilation compilation =
          compile(
              "<F extends WitnessArity<TypeArity.Unary>>",
              TRAVERSE_LIST + "Kind<? extends F, String> f");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.HolderFocus",
          "FocusPath<Holder<F>, Kind<? extends F, String>>");
      assertThat(compilation)
          .hadNoteContaining(
              NOT_APPLIED
                  + "The witness of Kind<? extends F, String> names the record's type variable F,");
      assertThat(traverseFieldNotes(compilation)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiterString = " -> ",
        value = {
          "List<String> f -> List<String> is not declared as a Kind<F, A> component, and the"
              + " annotation names a Traverse for one. Drop the annotation, or declare the"
              + " component as the Kind<F, A> the Traverse is written for.",
          "int f -> int is not declared as a Kind<F, A> component, and the annotation names a"
              + " Traverse for one.",
          "Kind f -> Kind is written raw, so it names neither a witness to find a Traverse for nor"
              + " an element to focus. Declare both type arguments, such as"
              + " Kind<TreeKind.Witness, Tree> for a Traverse<TreeKind.Witness>.",
          "Kind<? super ListKind.Witness, String> f -> The witness of"
              + " Kind<? super ListKind.Witness, String> is a wildcard that stands for no type, so"
              + " no Traverse instance can be named for it. Declare the witness the Traverse is"
              + " written for in place of the wildcard, such as Kind<TreeKind.Witness, String> for"
              + " a Traverse<TreeKind.Witness>.",
        })
    @DisplayName("a component the annotation cannot act on draws one note saying why")
    void aComponentTheAnnotationCannotActOnDrawsOneNote(String component, String reason) {
      // The component keeps the path it would have had without the annotation, which compiles
      // and is correct as far as it goes; the note is the only sign the annotation was there.
      Compilation compilation = compile(TRAVERSE_LIST + component);

      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining(NOT_APPLIED + reason);
      assertThat(traverseFieldNotes(compilation)).hasSize(1);
    }

    @Test
    @DisplayName("the component keeps the path it would have had without the annotation")
    void theComponentKeepsThePathItWouldHaveHad() {
      Compilation compilation = compile(TRAVERSE_LIST + "List<String> f");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "TraversalPath<Holder, String> f()");
    }

    @Test
    @DisplayName("the note is written once however many navigators reach the component")
    void theNoteIsWrittenOnceHoweverManyNavigatorsReachTheComponent() {
      // Root navigates into Holder through two components, so the analysis runs for Holder.f
      // once per route as well as for its own static method; the note must not follow suit.
      JavaFileObject holder =
          JavaFileObjects.forSourceString(
              "com.example.Holder",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.optics.annotations.KindSemantics;
              import org.higherkindedj.optics.annotations.TraverseField;

              @GenerateFocus(generateNavigators = true)
              public record Holder(
                  @TraverseField(
                      traverse = "org.higherkindedj.hkt.list.ListTraverse.INSTANCE",
                      semantics = KindSemantics.ZERO_OR_MORE)
                  List<String> f) {}
              """);
      JavaFileObject root =
          JavaFileObjects.forSourceString(
              "com.example.Root",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Root(Holder holder, Holder again) {}
              """);

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(holder, root);

      assertThat(compilation).succeeded();
      assertThat(traverseFieldNotes(compilation)).hasSize(1);
    }

    @Test
    @DisplayName("without the annotation the same components draw no note")
    void withoutTheAnnotationTheSameComponentsDrawNoNote() {
      Compilation compilation = compile("List<String> a, int b, Kind c, Kind<?, String> d");

      assertThat(compilation).succeeded();
      assertThat(traverseFieldNotes(compilation)).isEmpty();
    }

    @Test
    @DisplayName("an annotation that is applied draws no note")
    void anAnnotationThatIsAppliedDrawsNoNote() {
      Compilation compilation = compile(TRAVERSE_LIST + "Kind<ListKind.Witness, String> f");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "TraversalPath<Holder, String> f()");
      assertThat(traverseFieldNotes(compilation)).isEmpty();
    }

    @Test
    @DisplayName("a type javac could not resolve is left to javac")
    void aTypeJavacCouldNotResolveIsLeftToJavac() {
      // javac reports the missing type itself; a note calling it "not a Kind" beside that would
      // only mislead.
      Compilation compilation = compile(TRAVERSE_LIST + "Missing f");

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      assertThat(traverseFieldNotes(compilation)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Unrecognised Library Witness")
  class UnrecognisedLibraryWitness {

    private static final String NOT_RECOGNISED =
        "@GenerateFocus: record component 'Holder.f' names a witness the processor does not"
            + " recognise. org.higherkindedj.hkt.nonemptylist.NonEmptyListKind.Witness is a Higher-Kinded-J witness"
            + " with no registered Traverse, so the component keeps a plain FocusPath focusing the"
            + " Kind. Add @TraverseField naming the Traverse for it, or apply traverseOver to the"
            + " path yourself.";

    private JavaFileObject holder(String settings, String component) {
      return JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import org.higherkindedj.hkt.Kind;
          import org.higherkindedj.hkt.TypeArity;
          import org.higherkindedj.hkt.WitnessArity;
          import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
          import org.higherkindedj.optics.annotations.GenerateFocus;
          import org.higherkindedj.optics.annotations.KindSemantics;
          import org.higherkindedj.optics.annotations.TraverseField;

          interface OwnWitness extends WitnessArity<TypeArity.Unary> {}

          @GenerateFocus(%s)
          public record Holder(%s f) {}
          """
              .formatted(settings, component));
    }

    @Test
    @DisplayName("a library witness with no registered Traverse draws one note")
    void aLibraryWitnessWithNoRegisteredTraverseDrawsOneNote() {
      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(holder("", "Kind<NonEmptyListKind.Witness, String>"));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation,
          "com.example.HolderFocus",
          "FocusPath<Holder, Kind<NonEmptyListKind.Witness, String>> f()");
      assertThat(compilation).hadNoteContaining(NOT_RECOGNISED);
      assertThat(unrecognisedWitnessNotes(compilation)).hasSize(1);
    }

    @Test
    @DisplayName("the note is written once however many navigators reach the component")
    void theNoteIsWrittenOnceHoweverManyNavigatorsReachTheComponent() {
      JavaFileObject root =
          JavaFileObjects.forSourceString(
              "com.example.Root",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Root(Holder holder, Holder again) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  holder("generateNavigators = true", "Kind<NonEmptyListKind.Witness, String>"),
                  root);

      assertThat(compilation).succeeded();
      assertThat(unrecognisedWitnessNotes(compilation)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "Kind<Missing, String>",
          "@TraverseField(traverse = \"org.higherkindedj.hkt.list.ListTraverse.INSTANCE\")"
              + " Kind<Missing, String>"
        })
    @DisplayName("a witness javac could not resolve is left to javac")
    void aWitnessJavacCouldNotResolveIsLeftToJavac(String component) {
      // javac reports the missing type itself; the analyser names the witness as written and
      // says nothing more, with or without the annotation.
      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(holder("", component));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      assertThat(unrecognisedWitnessNotes(compilation)).isEmpty();
      assertThat(traverseFieldNotes(compilation)).isEmpty();
    }

    @Test
    @DisplayName("a witness of your own draws no note")
    void aWitnessOfYourOwnDrawsNoNote() {
      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(holder("", "Kind<OwnWitness, String>"));

      assertThat(compilation).succeeded();
      assertThat(unrecognisedWitnessNotes(compilation)).isEmpty();
    }

    @Test
    @DisplayName("@TraverseField on the same component is applied, and draws no note")
    void traverseFieldOnTheSameComponentIsApplied() {
      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(
                  holder(
                      "",
                      "@TraverseField(traverse ="
                          + " \"org.higherkindedj.hkt.nonemptylist.NonEmptyListTraverse.INSTANCE\", semantics ="
                          + " KindSemantics.ZERO_OR_MORE) Kind<NonEmptyListKind.Witness, String>"));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "TraversalPath<Holder, String> f()");
      assertThat(unrecognisedWitnessNotes(compilation)).isEmpty();
      assertThat(traverseFieldNotes(compilation)).isEmpty();
    }
  }

  /** The notes the Focus processor wrote for a {@code @TraverseField} it did not apply. */
  private static List<String> traverseFieldNotes(Compilation compilation) {
    return notesStartingWith(compilation, "@TraverseField:");
  }

  /** The notes the Focus processor wrote for a library witness it does not recognise. */
  private static List<String> unrecognisedWitnessNotes(Compilation compilation) {
    return notesStartingWith(compilation, "@GenerateFocus: record component");
  }

  private static List<String> notesStartingWith(Compilation compilation, String prefix) {
    return compilation.notes().stream()
        .map(note -> note.getMessage(null))
        .filter(message -> message.startsWith(prefix))
        .toList();
  }

  @Nested
  @DisplayName("Modifying Through a Wildcard Element")
  class ModifyingThroughAWildcardElement {

    /** Compiled once for the class: the fixture is immutable and every test builds its own. */
    private static final CompiledResult RESULT =
        RuntimeCompilationHelper.compileWith(
            new FocusProcessor(),
            JavaFileObjects.forSourceString(
                "com.example.Holder",
                """
                package com.example;

                import org.higherkindedj.hkt.Kind;
                import org.higherkindedj.hkt.list.ListKind;
                import org.higherkindedj.optics.annotations.GenerateFocus;

                @GenerateFocus
                public record Holder(Kind<ListKind.Witness, ? extends CharSequence> members) {}
                """));

    private Object holder(List<String> members) {
      try {
        Constructor<?> constructor =
            RESULT.loadClass("com.example.Holder").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(LIST.widen(members));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not build com.example.Holder", e);
      }
    }

    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    private TraversalPath<Object, CharSequence> members() {
      try {
        return (TraversalPath<Object, CharSequence>)
            RESULT.invokeStatic("com.example.HolderFocus", "members");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError("could not read com.example.HolderFocus.members", e);
      }
    }

    @Test
    @DisplayName("reads the elements the wildcard stands for")
    void readsTheElementsTheWildcardStandsFor() {
      assertThat(members().getAll(holder(List.of("alice", "bob")))).containsExactly("alice", "bob");
    }

    @Test
    @DisplayName("rebuilds the container with the modified elements")
    void rebuildsTheContainerWithTheModifiedElements() {
      Object modified =
          members()
              .modifyAll(
                  name -> name.toString().toUpperCase(Locale.ROOT),
                  holder(List.of("alice", "bob")));

      @SuppressWarnings("unchecked") // the component erases to the Kind the helper narrows
      Kind<ListKind.Witness, CharSequence> rebuilt =
          (Kind<ListKind.Witness, CharSequence>) invoke(modified, "members");
      assertThat(LIST.narrow(rebuilt)).containsExactly("ALICE", "BOB");
    }
  }
}
