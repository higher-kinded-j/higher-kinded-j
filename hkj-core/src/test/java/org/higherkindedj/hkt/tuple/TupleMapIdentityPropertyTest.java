// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;

/**
 * Property-based tests for Tuple6, Tuple7, and Tuple8 map identity law using jqwik.
 *
 * <p>Verifies that mapping identity functions over each Tuple preserves equality, providing more
 * comprehensive coverage than example-based tests alone.
 */
@Label("Tuple6-8 Map Identity Property Tests")
class TupleMapIdentityPropertyTest {

  // -------------------------------------------------------------------------
  // Arbitraries
  // -------------------------------------------------------------------------

  @Provide
  Arbitrary<Tuple6<Integer, String, Boolean, Double, Character, Long>> tuple6s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L))
        .as(Tuple6::new);
  }

  @Provide
  Arbitrary<Tuple7<Integer, String, Boolean, Double, Character, Long, Short>> tuple7s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L),
            Arbitraries.shorts().between((short) -100, (short) 100))
        .as(Tuple7::new);
  }

  @Provide
  Arbitrary<Tuple8<Integer, String, Boolean, Double, Character, Long, Short, Byte>> tuple8s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L),
            Arbitraries.shorts().between((short) -100, (short) 100),
            Arbitraries.bytes().between((byte) -50, (byte) 50))
        .as(Tuple8::new);
  }

  // -------------------------------------------------------------------------
  // Tuple6 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple6: map(id, id, id, id, id, id) preserves equality")
  void tuple6MapIdentity(
      @ForAll("tuple6s") Tuple6<Integer, String, Boolean, Double, Character, Long> tuple) {
    var result =
        tuple.map(
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity());

    assertThat(result).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple6: mapFirst(id) preserves equality")
  void tuple6MapFirstIdentity(
      @ForAll("tuple6s") Tuple6<Integer, String, Boolean, Double, Character, Long> tuple) {
    assertThat(tuple.mapFirst(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple6: mapSixth(id) preserves equality")
  void tuple6MapSixthIdentity(
      @ForAll("tuple6s") Tuple6<Integer, String, Boolean, Double, Character, Long> tuple) {
    assertThat(tuple.mapSixth(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple6: map composition law holds")
  void tuple6MapComposition(
      @ForAll("tuple6s") Tuple6<Integer, String, Boolean, Double, Character, Long> tuple) {
    // map(f) . map(g) == map(f . g) for the first element
    Function<Integer, Integer> f = x -> x + 1;
    Function<Integer, Integer> g = x -> x * 2;

    var mapThenMap = tuple.mapFirst(g).mapFirst(f);
    var mapComposed = tuple.mapFirst(g.andThen(f));

    assertThat(mapThenMap).isEqualTo(mapComposed);
  }

  // -------------------------------------------------------------------------
  // Tuple7 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple7: map(id, id, id, id, id, id, id) preserves equality")
  void tuple7MapIdentity(
      @ForAll("tuple7s") Tuple7<Integer, String, Boolean, Double, Character, Long, Short> tuple) {
    var result =
        tuple.map(
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity());

    assertThat(result).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple7: mapSeventh(id) preserves equality")
  void tuple7MapSeventhIdentity(
      @ForAll("tuple7s") Tuple7<Integer, String, Boolean, Double, Character, Long, Short> tuple) {
    assertThat(tuple.mapSeventh(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple7: map composition law holds")
  void tuple7MapComposition(
      @ForAll("tuple7s") Tuple7<Integer, String, Boolean, Double, Character, Long, Short> tuple) {
    Function<Integer, Integer> f = x -> x + 1;
    Function<Integer, Integer> g = x -> x * 2;

    var mapThenMap = tuple.mapFirst(g).mapFirst(f);
    var mapComposed = tuple.mapFirst(g.andThen(f));

    assertThat(mapThenMap).isEqualTo(mapComposed);
  }

  // -------------------------------------------------------------------------
  // Tuple8 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple8: map(id, id, id, id, id, id, id, id) preserves equality")
  void tuple8MapIdentity(
      @ForAll("tuple8s")
          Tuple8<Integer, String, Boolean, Double, Character, Long, Short, Byte> tuple) {
    var result =
        tuple.map(
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity());

    assertThat(result).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple8: mapEighth(id) preserves equality")
  void tuple8MapEighthIdentity(
      @ForAll("tuple8s")
          Tuple8<Integer, String, Boolean, Double, Character, Long, Short, Byte> tuple) {
    assertThat(tuple.mapEighth(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple8: map composition law holds")
  void tuple8MapComposition(
      @ForAll("tuple8s")
          Tuple8<Integer, String, Boolean, Double, Character, Long, Short, Byte> tuple) {
    Function<Integer, Integer> f = x -> x + 1;
    Function<Integer, Integer> g = x -> x * 2;

    var mapThenMap = tuple.mapFirst(g).mapFirst(f);
    var mapComposed = tuple.mapFirst(g.andThen(f));

    assertThat(mapThenMap).isEqualTo(mapComposed);
  }

  // -------------------------------------------------------------------------
  // Cross-arity: Tuple.of() factory consistency
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple6")
  void tuple6FactoryConsistency(
      @ForAll("tuple6s") Tuple6<Integer, String, Boolean, Double, Character, Long> tuple) {
    var fromFactory =
        Tuple.of(tuple._1(), tuple._2(), tuple._3(), tuple._4(), tuple._5(), tuple._6());

    assertThat(fromFactory).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple7")
  void tuple7FactoryConsistency(
      @ForAll("tuple7s") Tuple7<Integer, String, Boolean, Double, Character, Long, Short> tuple) {
    var fromFactory =
        Tuple.of(
            tuple._1(), tuple._2(), tuple._3(), tuple._4(), tuple._5(), tuple._6(), tuple._7());

    assertThat(fromFactory).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple8")
  void tuple8FactoryConsistency(
      @ForAll("tuple8s")
          Tuple8<Integer, String, Boolean, Double, Character, Long, Short, Byte> tuple) {
    var fromFactory =
        Tuple.of(
            tuple._1(),
            tuple._2(),
            tuple._3(),
            tuple._4(),
            tuple._5(),
            tuple._6(),
            tuple._7(),
            tuple._8());

    assertThat(fromFactory).isEqualTo(tuple);
  }
}
