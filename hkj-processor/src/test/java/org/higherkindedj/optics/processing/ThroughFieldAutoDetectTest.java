// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mutation-killing tests for @ThroughField auto-detection.
 *
 * <p>These tests verify that the SpecInterfaceAnalyser correctly auto-detects traversal types for
 * various container fields and reports appropriate errors for unsupported cases.
 */
@DisplayName("@ThroughField Auto-Detection")
class ThroughFieldAutoDetectTest {

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ImportOpticsProcessor()).compile(sources);
  }

  @Nested
  @DisplayName("List Field Auto-Detection")
  class ListFieldAutoDetection {

    @Test
    @DisplayName("should auto-detect List<String> field")
    void shouldAutoDetectListField() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.Team",
              """
              package com.external;
              import java.util.List;
              public record Team(String name, List<String> members) {
                  public Team.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String name;
                      private List<String> members;
                      public Builder name(String n) { this.name = n; return this; }
                      public Builder members(List<String> m) { this.members = m; return this; }
                      public Team build() { return new Team(name, members); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TeamSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Team;

              @ImportOptics
              public interface TeamSpec extends OpticsSpec<Team> {
                  @ViaBuilder
                  Lens<Team, java.util.List<String>> members();

                  @ThroughField(field = "members")
                  Traversal<Team, String> eachMember();
              }
              """);

      Compilation compilation = compile(team, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Team")
          .contentsAsUtf8String()
          .contains("Traversals.forList()");
    }

    @Test
    @DisplayName("should traverse a List<String> field at runtime, read and write")
    void shouldTraverseListFieldAtRuntime() throws Exception {
      // The composed optic casts the rebuilt List back into the field. With the field declared
      // as the interface, the unmodifiable List the traversal hands back is accepted on the
      // write side; a field declared as ArrayList would throw here, which is why auto-detection
      // is exact.
      var container =
          JavaFileObjects.forSourceString(
              "com.external.Bag",
              """
              package com.external;
              import java.util.List;
              public record Bag(List<String> items) {
                  public Bag.Builder toBuilder() { return new Builder().items(items); }
                  public static class Builder {
                      private List<String> items;
                      public Builder items(List<String> i) { this.items = i; return this; }
                      public Bag build() { return new Bag(items); }
                  }
              }
              """);
      var spec =
          JavaFileObjects.forSourceString(
              "com.test.BagSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Bag;
              import java.util.List;

              @ImportOptics
              public interface BagSpec extends OpticsSpec<Bag> {
                  @ViaBuilder
                  Lens<Bag, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<Bag, String> eachItem();
              }
              """);

      var compiled = RuntimeCompilationHelper.compile(container, spec);
      Object bag =
          compiled
              .loadClass("com.external.Bag")
              .getConstructor(List.class)
              .newInstance(List.of("a", "b"));
      @SuppressWarnings("unchecked") // the generated optic is over types this test cannot name
      Traversal<Object, Object> eachItem =
          (Traversal<Object, Object>) compiled.invokeStatic("com.test.Bag", "eachItem");

      Assertions.assertThat(Traversals.getAll(eachItem, bag)).containsExactly("a", "b");
      Object shouted =
          Traversals.modify(eachItem, item -> item.toString().toUpperCase(Locale.ROOT), bag);

      Assertions.assertThat(Traversals.getAll(eachItem, shouted)).containsExactly("A", "B");
      Assertions.assertThat(shouted).isNotSameAs(bag);
      Assertions.assertThat(Traversals.getAll(eachItem, bag)).containsExactly("a", "b");
      Object rebuilt = RuntimeCompilationHelper.invoke(shouted, "items");
      Assertions.assertThat(rebuilt).isNotSameAs(RuntimeCompilationHelper.invoke(bag, "items"));
      Assertions.assertThatThrownBy(() -> ((List<?>) rebuilt).remove(0))
          .as("the traversal hands back an unmodifiable List, and the field took it")
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should detect from the spec's lens focus, not the accessor's return type")
    void shouldDetectFromTheDeclaredLensFocus() {
      // The composition is typed against the lens the spec declares. An accessor that returns an
      // ArrayList behind a Lens<WideBag, List<String>> is sound: the builder takes a List, and
      // the rebuilt List is what it receives.
      var bag =
          JavaFileObjects.forSourceString(
              "com.external.WideBag",
              """
              package com.external;
              import java.util.ArrayList;
              import java.util.List;
              public record WideBag(ArrayList<String> items) {
                  public WideBag.Builder toBuilder() { return new Builder().items(items); }
                  public static class Builder {
                      private List<String> items;
                      public Builder items(List<String> i) { this.items = i; return this; }
                      public WideBag build() { return new WideBag(new ArrayList<>(items)); }
                  }
              }
              """);
      var spec =
          JavaFileObjects.forSourceString(
              "com.test.WideBagSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.WideBag;
              import java.util.List;

              @ImportOptics
              public interface WideBagSpec extends OpticsSpec<WideBag> {
                  @ViaBuilder
                  Lens<WideBag, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<WideBag, String> eachItem();
              }
              """);

      Compilation compilation = compile(bag, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.WideBag")
          .contentsAsUtf8String()
          .contains("Traversals.forList()");
    }

    @Test
    @DisplayName("should refuse a spec that declares no lens for the field it traverses through")
    void shouldRefuseWhenTheSpecDeclaresNoLensForTheField() {
      // The generated traversal calls Sack.items(), the lens; without one the generated file
      // could only fail with "cannot find symbol". Same-named members that are not a lens (static
      // helpers here) do not count as one.
      var container =
          JavaFileObjects.forSourceString(
              "com.external.Sack",
              """
              package com.external;
              import java.util.List;
              public record Sack(List<String> items) {
                  public Sack.Builder toBuilder() { return new Builder().items(items); }
                  public static class Builder {
                      private List<String> items;
                      public Builder items(List<String> i) { this.items = i; return this; }
                      public Sack build() { return new Sack(items); }
                  }
              }
              """);
      var spec =
          JavaFileObjects.forSourceString(
              "com.test.SackSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import com.external.Sack;

              @ImportOptics
              public interface SackSpec extends OpticsSpec<Sack> {
                  static int items(int scale) { return scale; }
                  static String items(String label) { return label; }

                  @ThroughField(field = "items")
                  Traversal<Sack, String> eachItem();
              }
              """);

      Compilation compilation = compile(container, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ThroughField: 'SackSpec.eachItem' composes through a lens named 'items', which the"
                  + " spec does not declare. The generated traversal calls the spec's own lens for"
                  + " the field and composes the container traversal after it. Declare a Lens"
                  + " method named 'items' on the spec, with its copy strategy, or use"
                  + " @TraverseWith for a traversal that stands on its own.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should refuse an ArrayList<String> field, naming the List interface")
    void shouldRefuseArrayListField() {
      var container =
          JavaFileObjects.forSourceString(
              "com.external.Container",
              """
              package com.external;
              import java.util.ArrayList;
              public record Container(ArrayList<String> items) {
                  public Container.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private ArrayList<String> items;
                      public Builder items(ArrayList<String> i) { this.items = i; return this; }
                      public Container build() { return new Container(items); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.ContainerSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Container;
              import java.util.ArrayList;

              @ImportOptics
              public interface ContainerSpec extends OpticsSpec<Container> {
                  @ViaBuilder
                  Lens<Container, ArrayList<String>> items();

                  @ThroughField(field = "items")
                  Traversal<Container, String> eachItem();
              }
              """);

      Compilation compilation = compile(container, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ThroughField: 'ContainerSpec.eachItem' reaches field 'items', which is declared as"
                  + " 'ArrayList<String>' rather than as the List interface. The standard List"
                  + " traversal promises no more than a List, so what it rebuilds is not"
                  + " guaranteed to be an ArrayList, and a field it cannot be handed back to would"
                  + " throw ClassCastException on first use. Name a traversal that rebuilds it, for"
                  + " example @ThroughField(field = \"items\", traversal ="
                  + " \"com.example.MyTraversals.forArrayList()\") built with"
                  + " Traversals.forIterableCollecting or Traversals.forMapValuesCollecting, or,"
                  + " where the type is yours, declare the field as List.");
      assertThat(compilation).hadErrorCount(1);
    }

    /** The ArrayList container and its builder the explicit-traversal tests share. */
    private static final JavaFileObject ARRAY_LIST_CONTAINER =
        JavaFileObjects.forSourceString(
            "com.external.Crate",
            """
            package com.external;
            import java.util.ArrayList;
            public record Crate(ArrayList<String> items) {
                public Crate.Builder toBuilder() { return new Builder().items(items); }
                public static class Builder {
                    private ArrayList<String> items;
                    public Builder items(ArrayList<String> i) { this.items = i; return this; }
                    public Crate build() { return new Crate(items); }
                }
            }
            """);

    private static JavaFileObject crateSpec(String traversal) {
      return JavaFileObjects.forSourceString(
          "com.test.CrateSpec",
          """
          package com.test;
          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.Traversal;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.ThroughField;
          import org.higherkindedj.optics.annotations.ViaBuilder;
          import com.external.Crate;
          import java.util.ArrayList;

          @ImportOptics
          public interface CrateSpec extends OpticsSpec<Crate> {
              @ViaBuilder
              Lens<Crate, ArrayList<String>> items();

              @ThroughField(field = "items", traversal = "%s")
              Traversal<Crate, String> eachItem();
          }
          """
              .formatted(traversal));
    }

    @Test
    @DisplayName("the interface traversal over an ArrayList field throws on first use")
    void interfaceTraversalOverArrayListFieldThrowsAtRuntime() throws Exception {
      // The premise of the refusal, pinned through the explicit route that bypasses it: the
      // unmodifiable List forList() hands back cannot be handed to an ArrayList field, and the
      // read reaches the setter too.
      var compiled =
          RuntimeCompilationHelper.compile(
              ARRAY_LIST_CONTAINER,
              crateSpec("org.higherkindedj.optics.util.Traversals.forList()"));
      Object crate =
          compiled
              .loadClass("com.external.Crate")
              .getConstructor(ArrayList.class)
              .newInstance(new ArrayList<>(List.of("a")));
      @SuppressWarnings("unchecked") // the generated optic is over types this test cannot name
      Traversal<Object, Object> eachItem =
          (Traversal<Object, Object>) compiled.invokeStatic("com.test.Crate", "eachItem");

      Assertions.assertThatThrownBy(() -> Traversals.getAll(eachItem, crate))
          .isInstanceOf(ClassCastException.class);
    }

    @Test
    @DisplayName("a traversal that rebuilds the ArrayList, named explicitly, round-trips")
    void explicitTraversalRebuildingArrayListRoundTrips() throws Exception {
      // The remedy the refusal prescribes: forIterableCollecting rebuilds the declared type.
      var helper =
          JavaFileObjects.forSourceString(
              "com.test.CrateTraversals",
              """
              package com.test;
              import java.util.ArrayList;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.util.Traversals;
              public final class CrateTraversals {
                  private CrateTraversals() {}
                  public static Traversal<ArrayList<String>, String> forArrayList() {
                      return Traversals.forIterableCollecting(ArrayList::new);
                  }
              }
              """);
      var compiled =
          RuntimeCompilationHelper.compile(
              ARRAY_LIST_CONTAINER, helper, crateSpec("com.test.CrateTraversals.forArrayList()"));
      Object crate =
          compiled
              .loadClass("com.external.Crate")
              .getConstructor(ArrayList.class)
              .newInstance(new ArrayList<>(List.of("a", "b")));
      @SuppressWarnings("unchecked") // the generated optic is over types this test cannot name
      Traversal<Object, Object> eachItem =
          (Traversal<Object, Object>) compiled.invokeStatic("com.test.Crate", "eachItem");

      Assertions.assertThat(Traversals.getAll(eachItem, crate)).containsExactly("a", "b");
      Object shouted =
          Traversals.modify(eachItem, item -> item.toString().toUpperCase(Locale.ROOT), crate);
      Assertions.assertThat(RuntimeCompilationHelper.invoke(shouted, "items"))
          .isInstanceOf(ArrayList.class)
          .isEqualTo(new ArrayList<>(List.of("A", "B")));
    }
  }

  @Nested
  @DisplayName("Set Field Auto-Detection")
  class SetFieldAutoDetection {

    @Test
    @DisplayName("should auto-detect Set<String> field")
    void shouldAutoDetectSetField() {
      var tags =
          JavaFileObjects.forSourceString(
              "com.external.Tags",
              """
              package com.external;
              import java.util.Set;
              public record Tags(Set<String> values) {
                  public Tags.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private Set<String> values;
                      public Builder values(Set<String> v) { this.values = v; return this; }
                      public Tags build() { return new Tags(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TagsSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Tags;
              import java.util.Set;

              @ImportOptics
              public interface TagsSpec extends OpticsSpec<Tags> {
                  @ViaBuilder
                  Lens<Tags, Set<String>> values();

                  @ThroughField(field = "values")
                  Traversal<Tags, String> eachValue();
              }
              """);

      Compilation compilation = compile(tags, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Tags")
          .contentsAsUtf8String()
          .contains("Traversals.forSet()");
    }

    @Test
    @DisplayName("should refuse a HashSet<String> field, naming the Set interface")
    void shouldRefuseHashSetField() {
      var container =
          JavaFileObjects.forSourceString(
              "com.external.HashContainer",
              """
              package com.external;
              import java.util.HashSet;
              public record HashContainer(HashSet<String> items) {
                  public HashContainer.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private HashSet<String> items;
                      public Builder items(HashSet<String> i) { this.items = i; return this; }
                      public HashContainer build() { return new HashContainer(items); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.HashContainerSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.HashContainer;
              import java.util.HashSet;

              @ImportOptics
              public interface HashContainerSpec extends OpticsSpec<HashContainer> {
                  @ViaBuilder
                  Lens<HashContainer, HashSet<String>> items();

                  @ThroughField(field = "items")
                  Traversal<HashContainer, String> eachItem();
              }
              """);

      Compilation compilation = compile(container, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ThroughField: 'HashContainerSpec.eachItem' reaches field 'items', which is declared as"
                  + " 'HashSet<String>' rather than as the Set interface. The standard Set"
                  + " traversal promises no more than a Set, so what it rebuilds is not"
                  + " guaranteed to be a HashSet, and a field it cannot be handed back to would"
                  + " throw ClassCastException on first use. Name a traversal that rebuilds it, for"
                  + " example @ThroughField(field = \"items\", traversal ="
                  + " \"com.example.MyTraversals.forHashSet()\") built with"
                  + " Traversals.forIterableCollecting or Traversals.forMapValuesCollecting, or,"
                  + " where the type is yours, declare the field as Set.");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("Collection Field Auto-Detection")
  class CollectionFieldAutoDetection {

    @Test
    @DisplayName("should auto-detect Collection<String> field")
    void shouldAutoDetectCollectionField() {
      var tags =
          JavaFileObjects.forSourceString(
              "com.external.Tags",
              """
              package com.external;
              import java.util.Collection;
              public record Tags(Collection<String> values) {
                  public Tags.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private Collection<String> values;
                      public Builder values(Collection<String> v) { this.values = v; return this; }
                      public Tags build() { return new Tags(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TagsSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Tags;
              import java.util.Collection;

              @ImportOptics
              public interface TagsSpec extends OpticsSpec<Tags> {
                  @ViaBuilder
                  Lens<Tags, Collection<String>> values();

                  @ThroughField(field = "values")
                  Traversal<Tags, String> eachValue();
              }
              """);

      Compilation compilation = compile(tags, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Tags")
          .contentsAsUtf8String()
          .contains("Traversals.forCollection()");
    }

    @Test
    @DisplayName("should not auto-detect a Deque as a Collection")
    void shouldNotAutoDetectDequeAsCollection() {
      // forCollection() would hand a Deque field back a List, so the field is not matched: the
      // author is asked for an explicit traversal instead.
      var queue =
          JavaFileObjects.forSourceString(
              "com.external.Queue",
              """
              package com.external;
              import java.util.Deque;
              public record Queue(Deque<String> values) {
                  public Queue.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private Deque<String> values;
                      public Builder values(Deque<String> v) { this.values = v; return this; }
                      public Queue build() { return new Queue(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.QueueSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Queue;
              import java.util.Deque;

              @ImportOptics
              public interface QueueSpec extends OpticsSpec<Queue> {
                  @ViaBuilder
                  Lens<Queue, Deque<String>> values();

                  @ThroughField(field = "values")
                  Traversal<Queue, String> eachValue();
              }
              """);

      Compilation compilation = compile(queue, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ThroughField: 'QueueSpec.eachValue' reaches field 'values', which is declared as"
                  + " 'Deque<String>' rather than as the Collection interface. The standard"
                  + " Collection traversal promises no more than a Collection, so what it rebuilds"
                  + " is not guaranteed to be a Deque, and a field it cannot be handed back to"
                  + " would throw ClassCastException on first use. Name a traversal that rebuilds"
                  + " it, for example @ThroughField(field = \"values\", traversal ="
                  + " \"com.example.MyTraversals.forDeque()\") built with"
                  + " Traversals.forIterableCollecting or Traversals.forMapValuesCollecting, or,"
                  + " where the type is yours, declare the field as Collection.");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("Optional Field Auto-Detection")
  class OptionalFieldAutoDetection {

    @Test
    @DisplayName("should auto-detect Optional<String> field")
    void shouldAutoDetectOptionalField() {
      var maybe =
          JavaFileObjects.forSourceString(
              "com.external.Maybe",
              """
              package com.external;
              import java.util.Optional;
              public record Maybe(Optional<String> value) {
                  public Maybe.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private Optional<String> value;
                      public Builder value(Optional<String> v) { this.value = v; return this; }
                      public Maybe build() { return new Maybe(value); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.MaybeSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Maybe;
              import java.util.Optional;

              @ImportOptics
              public interface MaybeSpec extends OpticsSpec<Maybe> {
                  @ViaBuilder
                  Lens<Maybe, Optional<String>> value();

                  @ThroughField(field = "value")
                  Traversal<Maybe, String> maybeValue();
              }
              """);

      Compilation compilation = compile(maybe, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Maybe")
          .contentsAsUtf8String()
          .contains("Traversals.forOptional()");
    }
  }

  @Nested
  @DisplayName("Map Field Auto-Detection")
  class MapFieldAutoDetection {

    @Test
    @DisplayName("should auto-detect Map<String, Integer> field")
    void shouldAutoDetectMapField() {
      var scores =
          JavaFileObjects.forSourceString(
              "com.external.Scores",
              """
              package com.external;
              import java.util.Map;
              public record Scores(Map<String, Integer> values) {
                  public Scores.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private Map<String, Integer> values;
                      public Builder values(Map<String, Integer> v) { this.values = v; return this; }
                      public Scores build() { return new Scores(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.ScoresSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Scores;
              import java.util.Map;

              @ImportOptics
              public interface ScoresSpec extends OpticsSpec<Scores> {
                  @ViaBuilder
                  Lens<Scores, Map<String, Integer>> values();

                  @ThroughField(field = "values")
                  Traversal<Scores, Integer> eachScore();
              }
              """);

      Compilation compilation = compile(scores, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Scores")
          .contentsAsUtf8String()
          .contains("Traversals.forMapValues()");
    }

    @Test
    @DisplayName("should refuse a HashMap<String, Integer> field, naming the Map interface")
    void shouldRefuseHashMapField() {
      var hashScores =
          JavaFileObjects.forSourceString(
              "com.external.HashScores",
              """
              package com.external;
              import java.util.HashMap;
              public record HashScores(HashMap<String, Integer> values) {
                  public HashScores.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private HashMap<String, Integer> values;
                      public Builder values(HashMap<String, Integer> v) { this.values = v; return this; }
                      public HashScores build() { return new HashScores(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.HashScoresSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.HashScores;
              import java.util.HashMap;

              @ImportOptics
              public interface HashScoresSpec extends OpticsSpec<HashScores> {
                  @ViaBuilder
                  Lens<HashScores, HashMap<String, Integer>> values();

                  @ThroughField(field = "values")
                  Traversal<HashScores, Integer> eachScore();
              }
              """);

      Compilation compilation = compile(hashScores, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ThroughField: 'HashScoresSpec.eachScore' reaches field 'values', which is declared as"
                  + " 'HashMap<String, Integer>' rather than as the Map interface. The standard Map"
                  + " traversal promises no more than a Map, so what it rebuilds is not"
                  + " guaranteed to be a HashMap, and a field it cannot be handed back to would"
                  + " throw ClassCastException on first use. Name a traversal that rebuilds it, for"
                  + " example @ThroughField(field = \"values\", traversal ="
                  + " \"com.example.MyTraversals.forHashMap()\") built with"
                  + " Traversals.forIterableCollecting or Traversals.forMapValuesCollecting, or,"
                  + " where the type is yours, declare the field as Map.");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("Array Field Auto-Detection")
  class ArrayFieldAutoDetection {

    @Test
    @DisplayName("should auto-detect String[] field")
    void shouldAutoDetectArrayField() {
      var names =
          JavaFileObjects.forSourceString(
              "com.external.Names",
              """
              package com.external;
              public record Names(String[] values) {
                  public Names.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String[] values;
                      public Builder values(String[] v) { this.values = v; return this; }
                      public Names build() { return new Names(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.NamesSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Names;

              @ImportOptics
              public interface NamesSpec extends OpticsSpec<Names> {
                  @ViaBuilder
                  Lens<Names, String[]> values();

                  @ThroughField(field = "values")
                  Traversal<Names, String> eachName();
              }
              """);

      Compilation compilation = compile(names, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Names")
          .contentsAsUtf8String()
          .contains("Traversals.forArray()");
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("should report error for field not found")
    void shouldReportErrorForFieldNotFound() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.TeamNoField",
              """
              package com.external;
              import java.util.List;
              public record TeamNoField(String name) {
                  public TeamNoField.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String name;
                      public Builder name(String n) { this.name = n; return this; }
                      public TeamNoField build() { return new TeamNoField(name); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TeamNoFieldSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import com.external.TeamNoField;

              @ImportOptics
              public interface TeamNoFieldSpec extends OpticsSpec<TeamNoField> {
                  @ThroughField(field = "nonexistent")
                  Traversal<TeamNoField, String> eachMember();
              }
              """);

      Compilation compilation = compile(team, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("field 'nonexistent' not found");
    }

    @Test
    @DisplayName("should report error for unrecognised container type")
    void shouldReportErrorForUnrecognisedContainerType() {
      var custom =
          JavaFileObjects.forSourceString(
              "com.external.CustomContainer",
              """
              package com.external;
              public record CustomContainer(String value) {
                  public CustomContainer.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String value;
                      public Builder value(String v) { this.value = v; return this; }
                      public CustomContainer build() { return new CustomContainer(value); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.CustomContainerSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.CustomContainer;

              @ImportOptics
              public interface CustomContainerSpec extends OpticsSpec<CustomContainer> {
                  @ViaBuilder
                  Lens<CustomContainer, String> value();

                  @ThroughField(field = "value")
                  Traversal<CustomContainer, Character> eachChar();
              }
              """);

      Compilation compilation = compile(custom, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Cannot auto-detect traversal");
      assertThat(compilation)
          .hadErrorContaining("Supported types: List, Set, Collection, Optional, Map");
      // One problem, one error: a rejected hint must not also draw the missing-hint error.
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should report the raw-container error for a raw ArrayList, not the subtype one")
    void shouldReportRawErrorForRawArrayList() {
      // "Declare the field as List" would only lead to the raw-List refusal next, so the generic
      // message, which asks for an explicit traversal, is the one that names the whole remedy.
      var raw =
          JavaFileObjects.forSourceString(
              "com.external.RawBag",
              """
              package com.external;
              import java.util.ArrayList;
              @SuppressWarnings("rawtypes")
              public record RawBag(ArrayList items) {
                  public RawBag.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private ArrayList items;
                      public Builder items(ArrayList i) { this.items = i; return this; }
                      public RawBag build() { return new RawBag(items); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.RawBagSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.RawBag;
              import java.util.ArrayList;

              @ImportOptics
              @SuppressWarnings("rawtypes")
              public interface RawBagSpec extends OpticsSpec<RawBag> {
                  @ViaBuilder
                  Lens<RawBag, ArrayList> items();

                  @ThroughField(field = "items")
                  Traversal<RawBag, Object> eachItem();
              }
              """);

      Compilation compilation = compile(raw, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Cannot auto-detect traversal for field 'items'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should refuse an int[] field, which the Object-array traversal cannot rebuild")
    void shouldRefusePrimitiveArrayField() {
      var counts =
          JavaFileObjects.forSourceString(
              "com.external.Counts",
              """
              package com.external;
              public record Counts(int[] values) {
                  public Counts.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private int[] values;
                      public Builder values(int[] v) { this.values = v; return this; }
                      public Counts build() { return new Counts(values); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.CountsSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Counts;

              @ImportOptics
              public interface CountsSpec extends OpticsSpec<Counts> {
                  @ViaBuilder
                  Lens<Counts, int[]> values();

                  @ThroughField(field = "values")
                  Traversal<Counts, Integer> eachValue();
              }
              """);

      Compilation compilation = compile(counts, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ThroughField: 'CountsSpec.eachValue' reaches field 'values', which is declared as"
                  + " 'int[]', an array of a primitive. The standard array traversal traverses an"
                  + " Object array, which an int[] is not, so the generated traversal would throw"
                  + " ClassCastException on first use. Name a traversal that rebuilds an int[] with"
                  + " @ThroughField(field = \"values\", traversal = \"...\"), or, where the type"
                  + " is yours, declare the field as an array of the boxed type.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should name the interface for a non-generic implementation, which is not raw")
    void shouldRefuseNonGenericImplementationNamingTheInterface() {
      var tags =
          JavaFileObjects.forSourceString(
              "com.external.Tags",
              """
              package com.external;
              import java.util.ArrayList;
              public class Tags extends ArrayList<String> {}
              """);
      var holder =
          JavaFileObjects.forSourceString(
              "com.external.Tagged",
              """
              package com.external;
              public record Tagged(Tags tags) {
                  public Tagged.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private Tags tags;
                      public Builder tags(Tags t) { this.tags = t; return this; }
                      public Tagged build() { return new Tagged(tags); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TaggedSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Tagged;
              import com.external.Tags;

              @ImportOptics
              public interface TaggedSpec extends OpticsSpec<Tagged> {
                  @ViaBuilder
                  Lens<Tagged, Tags> tags();

                  @ThroughField(field = "tags")
                  Traversal<Tagged, String> eachTag();
              }
              """);

      Compilation compilation = compile(tags, holder, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'TaggedSpec.eachTag' reaches field 'tags', which is declared as 'Tags' rather than"
                  + " as the List interface.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should report error for a lens whose focus is a type variable")
    void shouldReportErrorForTypeVariableFocus() {
      // Detection reads the lens focus; a type variable names no container to detect.
      var box =
          JavaFileObjects.forSourceString(
              "com.external.Box",
              """
              package com.external;
              public final class Box<U> {
                  private final U value;
                  public Box(U value) { this.value = value; }
                  public U value() { return value; }
                  public Box<U> withValue(U value) { return new Box<>(value); }
              }
              """);
      var spec =
          JavaFileObjects.forSourceString(
              "com.test.BoxSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.Box;

              @ImportOptics
              public interface BoxSpec<U> extends OpticsSpec<Box<U>> {
                  @Wither("withValue")
                  Lens<Box<U>, U> value();

                  @ThroughField(field = "value")
                  Traversal<Box<U>, Object> eachValue();
              }
              """);

      Compilation compilation = compile(box, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Cannot auto-detect traversal for field 'value'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should report error for a primitive field, which is no container at all")
    void shouldReportErrorForPrimitiveField() {
      var counter =
          JavaFileObjects.forSourceString(
              "com.external.Counter",
              """
              package com.external;
              public record Counter(int count) {
                  public Counter.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private int count;
                      public Builder count(int c) { this.count = c; return this; }
                      public Counter build() { return new Counter(count); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.CounterSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Counter;

              @ImportOptics
              public interface CounterSpec extends OpticsSpec<Counter> {
                  @ViaBuilder
                  Lens<Counter, Integer> count();

                  @ThroughField(field = "count")
                  Traversal<Counter, Integer> eachCount();
              }
              """);

      Compilation compilation = compile(counter, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Cannot auto-detect traversal for field 'count'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should report error for raw List type")
    void shouldReportErrorForRawListType() {
      var raw =
          JavaFileObjects.forSourceString(
              "com.external.RawContainer",
              """
              package com.external;
              import java.util.List;
              @SuppressWarnings("rawtypes")
              public record RawContainer(List items) {
                  public RawContainer.Builder toBuilder() { return new Builder(); }
                  @SuppressWarnings("rawtypes")
                  public static class Builder {
                      private List items;
                      public Builder items(List i) { this.items = i; return this; }
                      public RawContainer build() { return new RawContainer(items); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.RawContainerSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.RawContainer;
              import java.util.List;

              @SuppressWarnings("rawtypes")
              @ImportOptics
              public interface RawContainerSpec extends OpticsSpec<RawContainer> {
                  @ViaBuilder
                  Lens<RawContainer, List> items();

                  @ThroughField(field = "items")
                  Traversal<RawContainer, Object> eachItem();
              }
              """);

      Compilation compilation = compile(raw, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Cannot auto-detect traversal");
      // One problem, one error: a rejected hint must not also draw the missing-hint error.
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("Explicit Traversal Override")
  class ExplicitTraversalOverride {

    @Test
    @DisplayName("should use explicit traversal when provided")
    void shouldUseExplicitTraversalWhenProvided() {
      var team =
          JavaFileObjects.forSourceString(
              "com.external.TeamExplicit",
              """
              package com.external;
              import java.util.List;
              public record TeamExplicit(String name, List<String> members) {
                  public TeamExplicit.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private String name;
                      private List<String> members;
                      public Builder name(String n) { this.name = n; return this; }
                      public Builder members(List<String> m) { this.members = m; return this; }
                      public TeamExplicit build() { return new TeamExplicit(name, members); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.TeamExplicitSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.TeamExplicit;
              import java.util.List;

              @ImportOptics
              public interface TeamExplicitSpec extends OpticsSpec<TeamExplicit> {
                  @ViaBuilder
                  Lens<TeamExplicit, List<String>> members();

                  @ThroughField(field = "members",
                               traversal = "org.higherkindedj.optics.util.Traversals.forList()")
                  Traversal<TeamExplicit, String> eachMember();
              }
              """);

      Compilation compilation = compile(team, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.TeamExplicit")
          .contentsAsUtf8String()
          .contains("Traversals.forList()");
    }
  }

  @Nested
  @DisplayName("Field Lookup Edge Cases")
  class FieldLookupEdgeCases {

    @Test
    @DisplayName("should skip methods with parameters")
    void shouldSkipMethodsWithParameters() {
      var withMethod =
          JavaFileObjects.forSourceString(
              "com.external.WithMethod",
              """
              package com.external;
              import java.util.List;
              public class WithMethod {
                  private List<String> items;

                  // This is not a getter - it has a parameter
                  public List<String> items(int index) { return items; }

                  // This IS a valid getter
                  public List<String> getItems() { return items; }

                  public WithMethod withItems(List<String> items) {
                      WithMethod copy = new WithMethod();
                      copy.items = items;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.WithMethodSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.WithMethod;
              import java.util.List;

              @ImportOptics
              public interface WithMethodSpec extends OpticsSpec<WithMethod> {
                  @Wither(value = "withItems", getter = "getItems")
                  Lens<WithMethod, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<WithMethod, String> eachItem();
              }
              """);

      Compilation compilation = compile(withMethod, spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should skip private methods")
    void shouldSkipPrivateMethods() {
      var withPrivate =
          JavaFileObjects.forSourceString(
              "com.external.WithPrivate",
              """
              package com.external;
              import java.util.List;
              public class WithPrivate {
                  private List<String> items;

                  // This is not accessible - it's private
                  private List<String> items() { return items; }

                  // This IS a valid getter
                  public List<String> getItems() { return items; }

                  public WithPrivate withItems(List<String> items) {
                      WithPrivate copy = new WithPrivate();
                      copy.items = items;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.WithPrivateSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.WithPrivate;
              import java.util.List;

              @ImportOptics
              public interface WithPrivateSpec extends OpticsSpec<WithPrivate> {
                  @Wither(value = "withItems", getter = "getItems")
                  Lens<WithPrivate, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<WithPrivate, String> eachItem();
              }
              """);

      Compilation compilation = compile(withPrivate, spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should skip static methods")
    void shouldSkipStaticMethods() {
      var withStatic =
          JavaFileObjects.forSourceString(
              "com.external.WithStatic",
              """
              package com.external;
              import java.util.List;
              public class WithStatic {
                  private List<String> items;

                  // This is not a getter - it's static
                  public static List<String> items() { return null; }

                  // This IS a valid getter
                  public List<String> getItems() { return items; }

                  public WithStatic withItems(List<String> items) {
                      WithStatic copy = new WithStatic();
                      copy.items = items;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.WithStaticSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.WithStatic;
              import java.util.List;

              @ImportOptics
              public interface WithStaticSpec extends OpticsSpec<WithStatic> {
                  @Wither(value = "withItems", getter = "getItems")
                  Lens<WithStatic, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<WithStatic, String> eachItem();
              }
              """);

      Compilation compilation = compile(withStatic, spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should find field via public field directly")
    void shouldFindFieldViaPublicField() {
      var withPublicField =
          JavaFileObjects.forSourceString(
              "com.external.WithPublicField",
              """
              package com.external;
              import java.util.List;
              public class WithPublicField {
                  public List<String> items;

                  public List<String> getItems() { return items; }

                  public WithPublicField withItems(List<String> items) {
                      WithPublicField copy = new WithPublicField();
                      copy.items = items;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.WithPublicFieldSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.WithPublicField;
              import java.util.List;

              @ImportOptics
              public interface WithPublicFieldSpec extends OpticsSpec<WithPublicField> {
                  @Wither(value = "withItems", getter = "getItems")
                  Lens<WithPublicField, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<WithPublicField, String> eachItem();
              }
              """);

      Compilation compilation = compile(withPublicField, spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should not find field via private field only")
    void shouldNotFindFieldViaPrivateFieldOnly() {
      var withPrivateFieldOnly =
          JavaFileObjects.forSourceString(
              "com.external.WithPrivateFieldOnly",
              """
              package com.external;
              import java.util.List;
              public class WithPrivateFieldOnly {
                  private List<String> items;

                  public WithPrivateFieldOnly withItems(List<String> items) {
                      WithPrivateFieldOnly copy = new WithPrivateFieldOnly();
                      copy.items = items;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.WithPrivateFieldOnlySpec",
              """
              package com.test;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import com.external.WithPrivateFieldOnly;

              @ImportOptics
              public interface WithPrivateFieldOnlySpec extends OpticsSpec<WithPrivateFieldOnly> {
                  @ThroughField(field = "items")
                  Traversal<WithPrivateFieldOnly, String> eachItem();
              }
              """);

      Compilation compilation = compile(withPrivateFieldOnly, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("field 'items' not found");
    }
  }

  @Nested
  @DisplayName("Field Lookup via Different Accessors")
  class FieldLookupViaAccessors {

    @Test
    @DisplayName("should find field via JavaBean getter")
    void shouldFindFieldViaJavaBeanGetter() {
      var bean =
          JavaFileObjects.forSourceString(
              "com.external.JavaBean",
              """
              package com.external;
              import java.util.List;
              public class JavaBean {
                  private List<String> items;

                  public List<String> getItems() { return items; }
                  public void setItems(List<String> items) { this.items = items; }

                  public JavaBean withItems(List<String> items) {
                      JavaBean copy = new JavaBean();
                      copy.items = items;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.JavaBeanSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.JavaBean;
              import java.util.List;

              @ImportOptics
              public interface JavaBeanSpec extends OpticsSpec<JavaBean> {
                  @Wither(value = "withItems", getter = "getItems")
                  Lens<JavaBean, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<JavaBean, String> eachItem();
              }
              """);

      Compilation compilation = compile(bean, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.JavaBean")
          .contentsAsUtf8String()
          .contains("Traversals.forList()");
    }

    @Test
    @DisplayName("should find field via record-style accessor")
    void shouldFindFieldViaRecordStyleAccessor() {
      var record =
          JavaFileObjects.forSourceString(
              "com.external.SimpleRecord",
              """
              package com.external;
              import java.util.List;
              public record SimpleRecord(List<String> items) {
                  public SimpleRecord.Builder toBuilder() { return new Builder(); }
                  public static class Builder {
                      private List<String> items;
                      public Builder items(List<String> i) { this.items = i; return this; }
                      public SimpleRecord build() { return new SimpleRecord(items); }
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.SimpleRecordSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.SimpleRecord;
              import java.util.List;

              @ImportOptics
              public interface SimpleRecordSpec extends OpticsSpec<SimpleRecord> {
                  @ViaBuilder
                  Lens<SimpleRecord, List<String>> items();

                  @ThroughField(field = "items")
                  Traversal<SimpleRecord, String> eachItem();
              }
              """);

      Compilation compilation = compile(record, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.SimpleRecord")
          .contentsAsUtf8String()
          .contains("Traversals.forList()");
    }

    @Test
    @DisplayName("should find field via boolean is-getter")
    void shouldFindFieldViaBooleanIsGetter() {
      var config =
          JavaFileObjects.forSourceString(
              "com.external.Config",
              """
              package com.external;
              import java.util.Optional;
              public class Config {
                  private Optional<Boolean> enabled;

                  public Optional<Boolean> isEnabled() { return enabled; }

                  public Config withEnabled(Optional<Boolean> enabled) {
                      Config copy = new Config();
                      copy.enabled = enabled;
                      return copy;
                  }
              }
              """);

      var spec =
          JavaFileObjects.forSourceString(
              "com.test.ConfigSpec",
              """
              package com.test;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ThroughField;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.Config;
              import java.util.Optional;

              @ImportOptics
              public interface ConfigSpec extends OpticsSpec<Config> {
                  @Wither(value = "withEnabled", getter = "isEnabled")
                  Lens<Config, Optional<Boolean>> enabled();

                  @ThroughField(field = "enabled")
                  Traversal<Config, Boolean> maybeEnabled();
              }
              """);

      Compilation compilation = compile(config, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Config")
          .contentsAsUtf8String()
          .contains("Traversals.forOptional()");
    }
  }

  @Test
  @DisplayName("auto-detects through a field whose type the spec instantiates")
  void autoDetectsThroughAnInstantiatedFieldType() {
    final var holder =
        JavaFileObjects.forSourceString(
            "com.external.Holder",
            """
            package com.external;

            public class Holder<T> {
                private T items;
                public T items() { return items; }
                public Holder<T> withItems(T items) {
                    Holder<T> copy = new Holder<>();
                    copy.items = items;
                    return copy;
                }
            }
            """);

    final var specInterface =
        JavaFileObjects.forSourceString(
            "com.myapp.HolderOpticsSpec",
            """
            package com.myapp;

            import com.external.Holder;
            import java.util.List;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            public interface HolderOpticsSpec extends OpticsSpec<Holder<List<String>>> {
                @Wither("withItems")
                Lens<Holder<List<String>>, List<String>> items();

                @ThroughField(field = "items")
                Traversal<Holder<List<String>>, String> eachItem();
            }
            """);

    // The accessor is declared 'T items()'. Read off the element that is a type variable, which
    // no container detection can match; under Holder<List<String>> it is List<String>.
    Compilation compilation = compile(holder, specInterface);

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName("auto-detects on a raw source type, whose members are not substituted")
  void autoDetectsOnARawSourceType() {
    final var holder =
        JavaFileObjects.forSourceString(
            "com.external.RawHolder",
            """
            package com.external;

            import java.util.List;

            public class RawHolder<T> {
                private List<String> items;
                public List<String> items() { return items; }
                public RawHolder<T> withItems(List<String> items) {
                    RawHolder<T> copy = new RawHolder<>();
                    copy.items = items;
                    return copy;
                }
            }
            """);

    final var specInterface =
        JavaFileObjects.forSourceString(
            "com.myapp.RawOpticsSpec",
            """
            package com.myapp;

            import com.external.RawHolder;
            import java.util.List;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            @SuppressWarnings("rawtypes")
            public interface RawOpticsSpec extends OpticsSpec<RawHolder> {
                @Wither("withItems")
                Lens<RawHolder, List<String>> items();

                @ThroughField(field = "items")
                Traversal<RawHolder, String> eachItem();
            }
            """);

    // Under a raw site javac erases every member, so asking it to substitute would turn a field
    // typed List<String> into a raw List and match no container. There is nothing to substitute.
    Compilation compilation = compile(holder, specInterface);

    assertThat(compilation).succeeded();
  }
}
