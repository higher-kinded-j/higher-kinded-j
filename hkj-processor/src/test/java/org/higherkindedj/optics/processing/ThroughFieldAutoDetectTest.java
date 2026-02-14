// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
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
    @DisplayName("should auto-detect ArrayList<String> as List")
    void shouldAutoDetectArrayListAsList() {
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
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.Container")
          .contentsAsUtf8String()
          .contains("Traversals.forList()");
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
    @DisplayName("should auto-detect HashSet<String> as Set")
    void shouldAutoDetectHashSetAsSet() {
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
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.HashContainer")
          .contentsAsUtf8String()
          .contains("Traversals.forSet()");
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
    @DisplayName("should auto-detect HashMap<String, Integer> as Map")
    void shouldAutoDetectHashMapAsMap() {
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
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.test.HashScores")
          .contentsAsUtf8String()
          .contains("Traversals.forMapValues()");
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
      assertThat(compilation).hadErrorContaining("Supported types: List, Set, Optional, Map");
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
}
