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
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    @DisplayName("@TraverseField on a wildcard witness leaves the field alone")
    void traverseFieldOnAWildcardWitnessLeavesTheFieldAlone() {
      // The same as @TraverseField on a component that is not a Kind field: no Traverse can be
      // named, so the annotation is not acted on.
      Compilation compilation = compile(TRAVERSE_LIST + "Kind<?, String>");

      assertThat(compilation).succeeded();
      assertGeneratedCodeContainsRaw(
          compilation, "com.example.HolderFocus", "FocusPath<Holder, Kind<?, String>> members()");
    }
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
