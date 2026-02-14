// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.kind;

import static org.assertj.core.api.Assertions.*;

import java.util.Optional;
import org.higherkindedj.optics.annotations.KindSemantics;
import org.higherkindedj.optics.processing.kind.KindRegistry.KindMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link KindRegistry}.
 *
 * <p>Tests the registry's ability to look up known Kind witness types and extract their
 * corresponding Traverse instances and semantic classifications.
 */
@DisplayName("KindRegistry Tests")
class KindRegistryTest {

  @Nested
  @DisplayName("Known Type Lookups")
  class KnownTypeLookups {

    @Test
    @DisplayName("should find ListKind.Witness with correct mapping")
    void shouldFindListKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.list.ListKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.list.ListTraverse.INSTANCE");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_MORE);
      assertThat(mapping.get().isParameterised()).isFalse();
    }

    @Test
    @DisplayName("should find MaybeKind.Witness with correct mapping")
    void shouldFindMaybeKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.maybe.MaybeKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.maybe.MaybeTraverse.INSTANCE");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_ONE);
      assertThat(mapping.get().isParameterised()).isFalse();
    }

    @Test
    @DisplayName("should find OptionalKind.Witness with correct mapping")
    void shouldFindOptionalKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.optional.OptionalKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.optional.OptionalTraverse.INSTANCE");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_ONE);
    }

    @Test
    @DisplayName("should find StreamKind.Witness with correct mapping")
    void shouldFindStreamKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.stream.StreamKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.stream.StreamTraverse.INSTANCE");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_MORE);
    }

    @Test
    @DisplayName("should find TryKind.Witness with correct mapping")
    void shouldFindTryKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.trymonad.TryKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.trymonad.TryTraverse.INSTANCE");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_ONE);
    }

    @Test
    @DisplayName("should find IdKind.Witness with correct mapping")
    void shouldFindIdKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.id.IdKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.id.IdTraverse.INSTANCE");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.EXACTLY_ONE);
    }

    @Test
    @DisplayName("should find EitherKind.Witness with factory method")
    void shouldFindEitherKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.either.EitherKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.either.EitherTraverse.instance()");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_ONE);
      assertThat(mapping.get().isParameterised()).isTrue();
    }

    @Test
    @DisplayName("should find ValidatedKind.Witness with factory method")
    void shouldFindValidatedKindWitness() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.validated.ValidatedKind.Witness");

      assertThat(mapping).isPresent();
      assertThat(mapping.get().traverseExpression())
          .isEqualTo("org.higherkindedj.hkt.validated.ValidatedTraverse.instance()");
      assertThat(mapping.get().semantics()).isEqualTo(KindSemantics.ZERO_OR_ONE);
      assertThat(mapping.get().isParameterised()).isTrue();
    }
  }

  @Nested
  @DisplayName("Unknown Type Lookups")
  class UnknownTypeLookups {

    @Test
    @DisplayName("should return empty for unknown witness type")
    void shouldReturnEmptyForUnknownWitness() {
      Optional<KindMapping> mapping = KindRegistry.lookup("com.example.CustomKind.Witness");

      assertThat(mapping).isEmpty();
    }

    @Test
    @DisplayName("should return empty for non-existent library type")
    void shouldReturnEmptyForNonExistentLibraryType() {
      Optional<KindMapping> mapping =
          KindRegistry.lookup("org.higherkindedj.hkt.nonexistent.FooKind.Witness");

      assertThat(mapping).isEmpty();
    }
  }

  @Nested
  @DisplayName("Kind Interface Detection")
  class KindInterfaceDetection {

    @Test
    @DisplayName("should identify Kind interface correctly")
    void shouldIdentifyKindInterface() {
      assertThat(KindRegistry.isKindInterface("org.higherkindedj.hkt.Kind")).isTrue();
      assertThat(KindRegistry.isKindInterface("org.higherkindedj.hkt.list.ListKind")).isFalse();
      assertThat(KindRegistry.isKindInterface("java.util.List")).isFalse();
    }
  }

  @Nested
  @DisplayName("Library Witness Detection")
  class LibraryWitnessDetection {

    @Test
    @DisplayName("should identify library witness types")
    void shouldIdentifyLibraryWitnessTypes() {
      assertThat(KindRegistry.isLibraryWitness("org.higherkindedj.hkt.list.ListKind.Witness"))
          .isTrue();
      assertThat(KindRegistry.isLibraryWitness("org.higherkindedj.hkt.maybe.MaybeKind.Witness"))
          .isTrue();
    }

    @Test
    @DisplayName("should not identify custom witness types as library types")
    void shouldNotIdentifyCustomWitnessAsLibrary() {
      assertThat(KindRegistry.isLibraryWitness("com.example.CustomKind.Witness")).isFalse();
      assertThat(KindRegistry.isLibraryWitness("java.util.Optional")).isFalse();
    }
  }

  @Nested
  @DisplayName("Type Argument Extraction")
  class TypeArgumentExtraction {

    @Test
    @DisplayName("should extract base witness type from parameterised type")
    void shouldExtractBaseWitnessType() {
      String parameterised = "org.higherkindedj.hkt.either.EitherKind.Witness<String>";

      String base = KindRegistry.extractBaseWitnessType(parameterised);

      assertThat(base).isEqualTo("org.higherkindedj.hkt.either.EitherKind.Witness");
    }

    @Test
    @DisplayName("should return unchanged for non-parameterised type")
    void shouldReturnUnchangedForNonParameterised() {
      String simple = "org.higherkindedj.hkt.list.ListKind.Witness";

      String base = KindRegistry.extractBaseWitnessType(simple);

      assertThat(base).isEqualTo(simple);
    }

    @Test
    @DisplayName("should extract type arguments from parameterised witness")
    void shouldExtractTypeArguments() {
      String parameterised = "EitherKind.Witness<String>";

      String typeArgs = KindRegistry.extractWitnessTypeArgs(parameterised);

      assertThat(typeArgs).isEqualTo("String");
    }

    @Test
    @DisplayName("should handle nested type arguments")
    void shouldHandleNestedTypeArguments() {
      String nested = "EitherKind.Witness<List<String>>";

      String typeArgs = KindRegistry.extractWitnessTypeArgs(nested);

      assertThat(typeArgs).isEqualTo("List<String>");
    }

    @Test
    @DisplayName("should return empty for non-parameterised witness")
    void shouldReturnEmptyForNonParameterisedWitness() {
      String simple = "ListKind.Witness";

      String typeArgs = KindRegistry.extractWitnessTypeArgs(simple);

      assertThat(typeArgs).isEmpty();
    }
  }

  // =============================================================================
  // Boundary Condition Tests - Kill ConditionalsBoundaryMutator
  // =============================================================================

  @Nested
  @DisplayName("Boundary Condition Tests")
  class BoundaryConditionTests {

    @Test
    @DisplayName("extractBaseWitnessType with angle bracket at position 0 should return unchanged")
    void extractBaseWithAngleBracketAtZero() {
      // Tests: angleBracket > 0 boundary (angle bracket at position 0 means no prefix)
      String input = "<String>";
      String result = KindRegistry.extractBaseWitnessType(input);
      // angleBracket == 0, so condition > 0 is false, should return unchanged
      assertThat(result).isEqualTo("<String>");
    }

    @Test
    @DisplayName("extractBaseWitnessType with angle bracket at position 1 should extract")
    void extractBaseWithAngleBracketAtOne() {
      // Tests: angleBracket > 0 boundary (angle bracket at position 1)
      String input = "A<B>";
      String result = KindRegistry.extractBaseWitnessType(input);
      assertThat(result).isEqualTo("A");
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with start at 0 and valid end should return unchanged")
    void extractTypeArgsWithStartAtZero() {
      // Tests: start > 0 boundary
      String input = "<String>";
      String result = KindRegistry.extractWitnessTypeArgs(input);
      // start == 0, so condition start > 0 is false, should return empty
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with start at 1 should extract")
    void extractTypeArgsWithStartAtOne() {
      // Tests: start > 0 boundary
      String input = "A<B>";
      String result = KindRegistry.extractWitnessTypeArgs(input);
      assertThat(result).isEqualTo("B");
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with end equal to start should return empty")
    void extractTypeArgsWithEndEqualToStart() {
      // Tests: end > start boundary - only one angle bracket found
      String input = "A<";
      String result = KindRegistry.extractWitnessTypeArgs(input);
      // No closing >, lastIndexOf('>') returns -1 which is not > start
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractWitnessTypeArgs with end just after start should extract single char")
    void extractTypeArgsWithEndJustAfterStart() {
      // Tests: end > start boundary
      String input = "X<Y>";
      String result = KindRegistry.extractWitnessTypeArgs(input);
      assertThat(result).isEqualTo("Y");
    }

    @Test
    @DisplayName("isKindInterface should return false for empty string")
    void isKindInterfaceEmptyString() {
      assertThat(KindRegistry.isKindInterface("")).isFalse();
    }

    @Test
    @DisplayName("isKindInterface should return false for partial match")
    void isKindInterfacePartialMatch() {
      assertThat(KindRegistry.isKindInterface("org.higherkindedj.hkt.Kin")).isFalse();
      assertThat(KindRegistry.isKindInterface("org.higherkindedj.hkt.Kind2")).isFalse();
    }

    @Test
    @DisplayName("isLibraryWitness should return false for exact package without dot suffix")
    void isLibraryWitnessExactPackage() {
      // Tests: startsWith("org.higherkindedj.hkt.") - note the trailing dot
      assertThat(KindRegistry.isLibraryWitness("org.higherkindedj.hkt")).isFalse();
      assertThat(KindRegistry.isLibraryWitness("org.higherkindedj.hktx.Foo")).isFalse();
    }

    @Test
    @DisplayName("isLibraryWitness should return true for package with dot suffix")
    void isLibraryWitnessWithDotSuffix() {
      assertThat(KindRegistry.isLibraryWitness("org.higherkindedj.hkt.X")).isTrue();
    }
  }
}
