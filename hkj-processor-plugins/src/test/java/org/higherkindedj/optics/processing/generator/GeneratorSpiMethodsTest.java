// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
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

/** Tests that all plugin generators return correct cardinality, optic expression, and imports. */
@DisplayName("Generator SPI Methods")
class GeneratorSpiMethodsTest {

  @Nested
  @DisplayName("ZERO_OR_ONE generators")
  class ZeroOrOneGenerators {

    @Test
    @DisplayName("EitherGenerator should return ZERO_OR_ONE cardinality")
    void eitherCardinality() {
      var gen = new EitherGenerator();
      assertEquals(Cardinality.ZERO_OR_ONE, gen.getCardinality());
      assertEquals("Affines.eitherRight()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.util.Affines"), gen.getRequiredImports());
      assertEquals(1, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("TryGenerator should return ZERO_OR_ONE cardinality")
    void tryCardinality() {
      var gen = new TryGenerator();
      assertEquals(Cardinality.ZERO_OR_ONE, gen.getCardinality());
      assertEquals("Affines.trySuccess()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.util.Affines"), gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("ValidatedGenerator should return ZERO_OR_ONE cardinality")
    void validatedCardinality() {
      var gen = new ValidatedGenerator();
      assertEquals(Cardinality.ZERO_OR_ONE, gen.getCardinality());
      assertEquals("Affines.validatedValid()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.util.Affines"), gen.getRequiredImports());
      assertEquals(1, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("MaybeGenerator should return ZERO_OR_ONE cardinality")
    void maybeCardinality() {
      var gen = new MaybeGenerator();
      assertEquals(Cardinality.ZERO_OR_ONE, gen.getCardinality());
      assertEquals("Affines.just()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.util.Affines"), gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("OptionalGenerator should return ZERO_OR_ONE cardinality")
    void optionalCardinality() {
      var gen = new OptionalGenerator();
      assertEquals(Cardinality.ZERO_OR_ONE, gen.getCardinality());
      assertEquals("Affines.some()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.util.Affines"), gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }
  }

  @Nested
  @DisplayName("ZERO_OR_MORE generators")
  class ZeroOrMoreGenerators {

    @Test
    @DisplayName("ListGenerator should return ZERO_OR_MORE cardinality")
    void listCardinality() {
      var gen = new ListGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals("EachInstances.listEach()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.each.EachInstances"), gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("SetGenerator should return ZERO_OR_MORE cardinality")
    void setCardinality() {
      var gen = new SetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals("EachInstances.setEach()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.each.EachInstances"), gen.getRequiredImports());
    }

    @Test
    @DisplayName("MapValueGenerator should return ZERO_OR_MORE cardinality")
    void mapCardinality() {
      var gen = new MapValueGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals("EachInstances.mapValuesEach()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.each.EachInstances"), gen.getRequiredImports());
      assertEquals(1, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("ArrayGenerator should return ZERO_OR_MORE cardinality")
    void arrayCardinality() {
      var gen = new ArrayGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals("EachInstances.arrayEach()", gen.generateOpticExpression());
      assertEquals(Set.of("org.higherkindedj.optics.each.EachInstances"), gen.getRequiredImports());
    }
  }

  @Nested
  @DisplayName("Eclipse Collections generators")
  class EclipseCollectionsGenerators {

    private static final Set<String> EACH_AND_LISTS =
        Set.of(
            "org.higherkindedj.optics.each.EachInstances",
            "org.eclipse.collections.api.factory.Lists");
    private static final Set<String> EACH_AND_SETS =
        Set.of(
            "org.higherkindedj.optics.each.EachInstances",
            "org.eclipse.collections.api.factory.Sets");
    private static final Set<String> EACH_AND_BAGS =
        Set.of(
            "org.higherkindedj.optics.each.EachInstances",
            "org.eclipse.collections.api.factory.Bags");
    private static final Set<String> EACH_AND_SORTED_SETS =
        Set.of(
            "org.higherkindedj.optics.each.EachInstances",
            "org.eclipse.collections.api.factory.SortedSets");

    @Test
    @DisplayName("EclipseImmutableListGenerator")
    void eclipseImmutableList() {
      var gen = new EclipseImmutableListGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> Lists.immutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_LISTS, gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("EclipseMutableListGenerator")
    void eclipseMutableList() {
      var gen = new EclipseMutableListGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> Lists.mutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_LISTS, gen.getRequiredImports());
    }

    @Test
    @DisplayName("EclipseImmutableSetGenerator")
    void eclipseImmutableSet() {
      var gen = new EclipseImmutableSetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> Sets.immutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_SETS, gen.getRequiredImports());
    }

    @Test
    @DisplayName("EclipseMutableSetGenerator")
    void eclipseMutableSet() {
      var gen = new EclipseMutableSetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> Sets.mutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_SETS, gen.getRequiredImports());
    }

    @Test
    @DisplayName("EclipseImmutableBagGenerator")
    void eclipseImmutableBag() {
      var gen = new EclipseImmutableBagGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> Bags.immutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_BAGS, gen.getRequiredImports());
    }

    @Test
    @DisplayName("EclipseMutableBagGenerator")
    void eclipseMutableBag() {
      var gen = new EclipseMutableBagGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> Bags.mutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_BAGS, gen.getRequiredImports());
    }

    @Test
    @DisplayName("EclipseImmutableSortedSetGenerator")
    void eclipseImmutableSortedSet() {
      var gen = new EclipseImmutableSortedSetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> SortedSets.immutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_SORTED_SETS, gen.getRequiredImports());
    }

    @Test
    @DisplayName("EclipseMutableSortedSetGenerator")
    void eclipseMutableSortedSet() {
      var gen = new EclipseMutableSortedSetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> SortedSets.mutable.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(EACH_AND_SORTED_SETS, gen.getRequiredImports());
    }
  }

  @Nested
  @DisplayName("Guava generators")
  class GuavaGenerators {

    @Test
    @DisplayName("GuavaImmutableListGenerator")
    void guavaImmutableList() {
      var gen = new GuavaImmutableListGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(ImmutableList::copyOf)",
          gen.generateOpticExpression());
      assertEquals(
          Set.of(
              "org.higherkindedj.optics.each.EachInstances",
              "com.google.common.collect.ImmutableList"),
          gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("GuavaImmutableSetGenerator")
    void guavaImmutableSet() {
      var gen = new GuavaImmutableSetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(ImmutableSet::copyOf)",
          gen.generateOpticExpression());
      assertEquals(
          Set.of(
              "org.higherkindedj.optics.each.EachInstances",
              "com.google.common.collect.ImmutableSet"),
          gen.getRequiredImports());
    }
  }

  @Nested
  @DisplayName("Vavr generators")
  class VavrGenerators {

    @Test
    @DisplayName("VavrListGenerator")
    void vavrList() {
      var gen = new VavrListGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> List.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(
          Set.of("org.higherkindedj.optics.each.EachInstances", "io.vavr.collection.List"),
          gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("VavrSetGenerator")
    void vavrSet() {
      var gen = new VavrSetGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(list -> HashSet.ofAll(list))",
          gen.generateOpticExpression());
      assertEquals(
          Set.of("org.higherkindedj.optics.each.EachInstances", "io.vavr.collection.HashSet"),
          gen.getRequiredImports());
    }
  }

  @Nested
  @DisplayName("Apache Commons generators")
  class ApacheCommonsGenerators {

    @Test
    @DisplayName("ApacheHashBagGenerator")
    void apacheHashBag() {
      var gen = new ApacheHashBagGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(HashBag::new)", gen.generateOpticExpression());
      assertEquals(
          Set.of(
              "org.higherkindedj.optics.each.EachInstances",
              "org.apache.commons.collections4.bag.HashBag"),
          gen.getRequiredImports());
      assertEquals(0, gen.getFocusTypeArgumentIndex());
    }

    @Test
    @DisplayName("ApacheUnmodifiableListGenerator")
    void apacheUnmodifiableList() {
      var gen = new ApacheUnmodifiableListGenerator();
      assertEquals(Cardinality.ZERO_OR_MORE, gen.getCardinality());
      assertEquals(
          "EachInstances.fromIterableCollecting(UnmodifiableList::new)",
          gen.generateOpticExpression());
      assertEquals(
          Set.of(
              "org.higherkindedj.optics.each.EachInstances",
              "org.apache.commons.collections4.list.UnmodifiableList"),
          gen.getRequiredImports());
    }
  }
}
