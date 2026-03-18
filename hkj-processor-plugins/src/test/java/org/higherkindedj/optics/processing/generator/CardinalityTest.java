// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.higherkindedj.optics.processing.generator.apache.ApacheHashBagGenerator;
import org.higherkindedj.optics.processing.generator.apache.ApacheUnmodifiableListGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.ArrayGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.ListGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.MapValueGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.OptionalGenerator;
import org.higherkindedj.optics.processing.generator.basejdk.SetGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableBagGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableListGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableSetGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseImmutableSortedSetGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableBagGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableListGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableSetGenerator;
import org.higherkindedj.optics.processing.generator.eclipse.EclipseMutableSortedSetGenerator;
import org.higherkindedj.optics.processing.generator.guava.GuavaImmutableListGenerator;
import org.higherkindedj.optics.processing.generator.guava.GuavaImmutableSetGenerator;
import org.higherkindedj.optics.processing.generator.hkj.EitherGenerator;
import org.higherkindedj.optics.processing.generator.hkj.MaybeGenerator;
import org.higherkindedj.optics.processing.generator.hkj.TryGenerator;
import org.higherkindedj.optics.processing.generator.hkj.ValidatedGenerator;
import org.higherkindedj.optics.processing.generator.vavr.VavrListGenerator;
import org.higherkindedj.optics.processing.generator.vavr.VavrSetGenerator;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TraversableGenerator Cardinality")
class CardinalityTest {

  @Nested
  @DisplayName("ZERO_OR_ONE generators")
  class ZeroOrOneGenerators {

    @Test
    @DisplayName("OptionalGenerator should have ZERO_OR_ONE cardinality")
    void optionalGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_ONE, new OptionalGenerator().getCardinality());
    }

    @Test
    @DisplayName("MaybeGenerator should have ZERO_OR_ONE cardinality")
    void maybeGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_ONE, new MaybeGenerator().getCardinality());
    }

    @Test
    @DisplayName("EitherGenerator should have ZERO_OR_ONE cardinality")
    void eitherGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_ONE, new EitherGenerator().getCardinality());
    }

    @Test
    @DisplayName("TryGenerator should have ZERO_OR_ONE cardinality")
    void tryGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_ONE, new TryGenerator().getCardinality());
    }

    @Test
    @DisplayName("ValidatedGenerator should have ZERO_OR_ONE cardinality")
    void validatedGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_ONE, new ValidatedGenerator().getCardinality());
    }
  }

  @Nested
  @DisplayName("ZERO_OR_MORE generators")
  class ZeroOrMoreGenerators {

    @Test
    @DisplayName("ListGenerator should have ZERO_OR_MORE cardinality")
    void listGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new ListGenerator().getCardinality());
    }

    @Test
    @DisplayName("SetGenerator should have ZERO_OR_MORE cardinality")
    void setGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new SetGenerator().getCardinality());
    }

    @Test
    @DisplayName("MapValueGenerator should have ZERO_OR_MORE cardinality")
    void mapValueGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new MapValueGenerator().getCardinality());
    }

    @Test
    @DisplayName("ArrayGenerator should have ZERO_OR_MORE cardinality")
    void arrayGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new ArrayGenerator().getCardinality());
    }
  }

  @Nested
  @DisplayName("Eclipse Collections generators")
  class EclipseCollectionsGenerators {

    @Test
    @DisplayName("EclipseImmutableListGenerator should have ZERO_OR_MORE cardinality")
    void eclipseImmutableListGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new EclipseImmutableListGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseImmutableSetGenerator should have ZERO_OR_MORE cardinality")
    void eclipseImmutableSetGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new EclipseImmutableSetGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseImmutableBagGenerator should have ZERO_OR_MORE cardinality")
    void eclipseImmutableBagGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new EclipseImmutableBagGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseImmutableSortedSetGenerator should have ZERO_OR_MORE cardinality")
    void eclipseImmutableSortedSetGeneratorCardinality() {
      assertEquals(
          Cardinality.ZERO_OR_MORE, new EclipseImmutableSortedSetGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseMutableListGenerator should have ZERO_OR_MORE cardinality")
    void eclipseMutableListGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new EclipseMutableListGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseMutableSetGenerator should have ZERO_OR_MORE cardinality")
    void eclipseMutableSetGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new EclipseMutableSetGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseMutableBagGenerator should have ZERO_OR_MORE cardinality")
    void eclipseMutableBagGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new EclipseMutableBagGenerator().getCardinality());
    }

    @Test
    @DisplayName("EclipseMutableSortedSetGenerator should have ZERO_OR_MORE cardinality")
    void eclipseMutableSortedSetGeneratorCardinality() {
      assertEquals(
          Cardinality.ZERO_OR_MORE, new EclipseMutableSortedSetGenerator().getCardinality());
    }
  }

  @Nested
  @DisplayName("Guava generators")
  class GuavaGenerators {

    @Test
    @DisplayName("GuavaImmutableListGenerator should have ZERO_OR_MORE cardinality")
    void guavaImmutableListGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new GuavaImmutableListGenerator().getCardinality());
    }

    @Test
    @DisplayName("GuavaImmutableSetGenerator should have ZERO_OR_MORE cardinality")
    void guavaImmutableSetGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new GuavaImmutableSetGenerator().getCardinality());
    }
  }

  @Nested
  @DisplayName("Vavr generators")
  class VavrGenerators {

    @Test
    @DisplayName("VavrListGenerator should have ZERO_OR_MORE cardinality")
    void vavrListGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new VavrListGenerator().getCardinality());
    }

    @Test
    @DisplayName("VavrSetGenerator should have ZERO_OR_MORE cardinality")
    void vavrSetGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new VavrSetGenerator().getCardinality());
    }
  }

  @Nested
  @DisplayName("Apache Commons generators")
  class ApacheCommonsGenerators {

    @Test
    @DisplayName("ApacheHashBagGenerator should have ZERO_OR_MORE cardinality")
    void apacheHashBagGeneratorCardinality() {
      assertEquals(Cardinality.ZERO_OR_MORE, new ApacheHashBagGenerator().getCardinality());
    }

    @Test
    @DisplayName("ApacheUnmodifiableListGenerator should have ZERO_OR_MORE cardinality")
    void apacheUnmodifiableListGeneratorCardinality() {
      assertEquals(
          Cardinality.ZERO_OR_MORE, new ApacheUnmodifiableListGenerator().getCardinality());
    }
  }
}
