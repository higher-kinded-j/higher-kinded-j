// Copyright (c) 2025 Magnus Smith
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
}
