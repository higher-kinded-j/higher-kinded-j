// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PathTypeRegistry")
class PathTypeRegistryTest {

  /** All registered Path types with their fully qualified names. */
  static Stream<Arguments> allPathTypes() {
    return Stream.of(
        Arguments.of("org.higherkindedj.hkt.effect.MaybePath", "MaybePath"),
        Arguments.of("org.higherkindedj.hkt.effect.EitherPath", "EitherPath"),
        Arguments.of("org.higherkindedj.hkt.effect.TryPath", "TryPath"),
        Arguments.of("org.higherkindedj.hkt.effect.IOPath", "IOPath"),
        Arguments.of("org.higherkindedj.hkt.effect.VTaskPath", "VTaskPath"),
        Arguments.of("org.higherkindedj.hkt.effect.DefaultVTaskPath", "DefaultVTaskPath"),
        Arguments.of("org.higherkindedj.hkt.effect.ValidationPath", "ValidationPath"),
        Arguments.of("org.higherkindedj.hkt.effect.IdPath", "IdPath"),
        Arguments.of("org.higherkindedj.hkt.effect.OptionalPath", "OptionalPath"),
        Arguments.of("org.higherkindedj.hkt.effect.GenericPath", "GenericPath"),
        Arguments.of("org.higherkindedj.hkt.effect.TrampolinePath", "TrampolinePath"),
        Arguments.of("org.higherkindedj.hkt.effect.FreePath", "FreePath"),
        Arguments.of("org.higherkindedj.hkt.effect.FreeApPath", "FreeApPath"),
        Arguments.of("org.higherkindedj.hkt.effect.ListPath", "ListPath"),
        Arguments.of("org.higherkindedj.hkt.effect.StreamPath", "StreamPath"),
        Arguments.of("org.higherkindedj.hkt.effect.VStreamPath", "VStreamPath"),
        Arguments.of("org.higherkindedj.hkt.effect.DefaultVStreamPath", "DefaultVStreamPath"),
        Arguments.of("org.higherkindedj.hkt.effect.NonDetPath", "NonDetPath"),
        Arguments.of("org.higherkindedj.hkt.effect.ReaderPath", "ReaderPath"),
        Arguments.of("org.higherkindedj.hkt.effect.WithStatePath", "WithStatePath"),
        Arguments.of("org.higherkindedj.hkt.effect.WriterPath", "WriterPath"),
        Arguments.of("org.higherkindedj.hkt.effect.LazyPath", "LazyPath"),
        Arguments.of("org.higherkindedj.hkt.effect.CompletableFuturePath", "CompletableFuturePath"),
        Arguments.of("org.higherkindedj.hkt.expression.ForPath", "ForPath"),
        Arguments.of("org.higherkindedj.optics.focus.FocusPath", "FocusPath"),
        Arguments.of("org.higherkindedj.optics.focus.AffinePath", "AffinePath"),
        Arguments.of("org.higherkindedj.optics.focus.TraversalPath", "TraversalPath"));
  }

  @Nested
  @DisplayName("registeredTypeCount")
  class RegisteredTypeCount {

    @Test
    @DisplayName("returns 27 for all registered Path types")
    void registeredTypeCount_returnsExpectedCount() {
      assertThat(PathTypeRegistry.registeredTypeCount()).isEqualTo(27);
    }
  }

  @Nested
  @DisplayName("isPathType")
  class IsPathType {

    @ParameterizedTest(name = "{1} is a registered Path type")
    @MethodSource("org.higherkindedj.checker.PathTypeRegistryTest#allPathTypes")
    @DisplayName("returns true for all registered Path types")
    void isPathType_registeredType_returnsTrue(String qualifiedName, String simpleName) {
      assertThat(PathTypeRegistry.isPathType(qualifiedName))
          .as("Expected %s to be a registered Path type", simpleName)
          .isTrue();
    }

    @ParameterizedTest(name = "{0} is not a Path type")
    @ValueSource(
        strings = {
          "java.lang.String",
          "java.util.Optional",
          "org.higherkindedj.hkt.maybe.Maybe",
          "com.example.CustomPath"
        })
    @DisplayName("returns false for non-Path types")
    void isPathType_unregisteredType_returnsFalse(String qualifiedName) {
      assertThat(PathTypeRegistry.isPathType(qualifiedName)).isFalse();
    }
  }

  @Nested
  @DisplayName("isPathTypeBySimpleName")
  class IsPathTypeBySimpleName {

    @ParameterizedTest(name = "{1} is recognised by simple name")
    @MethodSource("org.higherkindedj.checker.PathTypeRegistryTest#allPathTypes")
    @DisplayName("returns true for registered simple names")
    void isPathTypeBySimpleName_registered_returnsTrue(String qualifiedName, String simpleName) {
      assertThat(PathTypeRegistry.isPathTypeBySimpleName(simpleName)).isTrue();
    }

    @Test
    @DisplayName("returns false for unknown simple names")
    void isPathTypeBySimpleName_unknown_returnsFalse() {
      assertThat(PathTypeRegistry.isPathTypeBySimpleName("CustomPath")).isFalse();
      assertThat(PathTypeRegistry.isPathTypeBySimpleName("String")).isFalse();
    }
  }

  @Nested
  @DisplayName("getPathCategory")
  class GetPathCategory {

    @ParameterizedTest(name = "{0} maps to {1}")
    @MethodSource("org.higherkindedj.checker.PathTypeRegistryTest#allPathTypes")
    @DisplayName("returns simple name for registered types")
    void getPathCategory_registered_returnsSimpleName(String qualifiedName, String simpleName) {
      assertThat(PathTypeRegistry.getPathCategory(qualifiedName)).isPresent().hasValue(simpleName);
    }

    @Test
    @DisplayName("returns empty for unregistered types")
    void getPathCategory_unregistered_returnsEmpty() {
      assertThat(PathTypeRegistry.getPathCategory("java.lang.String")).isEmpty();
    }
  }

  @Nested
  @DisplayName("areSamePathFamily")
  class AreSamePathFamily {

    @ParameterizedTest(name = "{1} is same family as itself")
    @MethodSource("org.higherkindedj.checker.PathTypeRegistryTest#allPathTypes")
    @DisplayName("returns true when both types are the same (reflexive)")
    void areSamePathFamily_sameType_returnsTrue(String qualifiedName, String simpleName) {
      assertThat(PathTypeRegistry.areSamePathFamily(qualifiedName, qualifiedName))
          .as("Expected %s to be the same family as itself", simpleName)
          .isTrue();
    }

    @Test
    @DisplayName("returns false for different Path types")
    void areSamePathFamily_differentTypes_returnsFalse() {
      assertThat(
              PathTypeRegistry.areSamePathFamily(
                  "org.higherkindedj.hkt.effect.MaybePath", "org.higherkindedj.hkt.effect.IOPath"))
          .isFalse();
    }

    @Test
    @DisplayName("returns false when either type is unknown")
    void areSamePathFamily_unknownType_returnsFalse() {
      assertThat(
              PathTypeRegistry.areSamePathFamily(
                  "org.higherkindedj.hkt.effect.MaybePath", "java.lang.String"))
          .isFalse();

      assertThat(
              PathTypeRegistry.areSamePathFamily(
                  "java.lang.String", "org.higherkindedj.hkt.effect.MaybePath"))
          .isFalse();
    }

    @Test
    @DisplayName("returns false when both types are unknown")
    void areSamePathFamily_bothUnknown_returnsFalse() {
      assertThat(PathTypeRegistry.areSamePathFamily("java.lang.String", "java.lang.Integer"))
          .isFalse();
    }

    @Test
    @DisplayName("is symmetric")
    void areSamePathFamily_symmetric() {
      String maybe = "org.higherkindedj.hkt.effect.MaybePath";
      String io = "org.higherkindedj.hkt.effect.IOPath";
      assertThat(PathTypeRegistry.areSamePathFamily(maybe, io))
          .isEqualTo(PathTypeRegistry.areSamePathFamily(io, maybe));
    }
  }

  @Nested
  @DisplayName("suggestedConversion")
  class SuggestedConversion {

    @Test
    @DisplayName("suggests toEitherPath() when target is EitherPath")
    void suggestedConversion_toEitherPath_returnsSuggestion() {
      Optional<String> conversion = PathTypeRegistry.suggestedConversion("MaybePath", "EitherPath");
      assertThat(conversion).isPresent().hasValue("toEitherPath()");
    }

    @Test
    @DisplayName("suggests toMaybePath() when target is MaybePath")
    void suggestedConversion_toMaybePath_returnsSuggestion() {
      Optional<String> conversion = PathTypeRegistry.suggestedConversion("EitherPath", "MaybePath");
      assertThat(conversion).isPresent().hasValue("toMaybePath()");
    }

    @Test
    @DisplayName("returns empty when no conversion exists")
    void suggestedConversion_noConversion_returnsEmpty() {
      assertThat(PathTypeRegistry.suggestedConversion("MaybePath", "IOPath")).isEmpty();
    }
  }

  @Nested
  @DisplayName("allSimpleNames")
  class AllSimpleNames {

    @Test
    @DisplayName("returns all registered simple names")
    void allSimpleNames_containsAllExpectedNames() {
      assertThat(PathTypeRegistry.allSimpleNames())
          .contains("MaybePath", "EitherPath", "TryPath", "IOPath", "FocusPath", "ForPath");
    }

    @Test
    @DisplayName("has same size as registeredTypeCount")
    void allSimpleNames_sameSize() {
      assertThat(PathTypeRegistry.allSimpleNames()).hasSize(PathTypeRegistry.registeredTypeCount());
    }
  }
}
