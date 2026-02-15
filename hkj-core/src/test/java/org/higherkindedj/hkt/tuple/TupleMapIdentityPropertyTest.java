// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;

/**
 * Property-based tests for Tuple6 through Tuple12 map identity law using jqwik.
 *
 * <p>Verifies that mapping identity functions over each Tuple preserves equality, providing more
 * comprehensive coverage than example-based tests alone.
 */
@Label("Tuple6-12 Map Identity Property Tests")
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

  @Provide
  Arbitrary<Tuple9<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float>>
      tuple9s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L),
            Arbitraries.shorts().between((short) -100, (short) 100),
            Arbitraries.bytes().between((byte) -50, (byte) 50))
        .flatAs(
            (a, b, c, d, e, f, g, h) ->
                Arbitraries.floats()
                    .between(-100.0f, 100.0f)
                    .map(i -> new Tuple9<>(a, b, c, d, e, f, g, h, i)));
  }

  @Provide
  Arbitrary<Tuple10<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float, Integer>>
      tuple10s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L),
            Arbitraries.shorts().between((short) -100, (short) 100),
            Arbitraries.bytes().between((byte) -50, (byte) 50))
        .flatAs(
            (a, b, c, d, e, f, g, h) ->
                Combinators.combine(
                        Arbitraries.floats().between(-100.0f, 100.0f),
                        Arbitraries.integers().between(-1000, 1000))
                    .as((i, j) -> new Tuple10<>(a, b, c, d, e, f, g, h, i, j)));
  }

  @Provide
  Arbitrary<
          Tuple11<
              Integer,
              String,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String>>
      tuple11s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L),
            Arbitraries.shorts().between((short) -100, (short) 100),
            Arbitraries.bytes().between((byte) -50, (byte) 50))
        .flatAs(
            (a, b, c, d, e, f, g, h) ->
                Combinators.combine(
                        Arbitraries.floats().between(-100.0f, 100.0f),
                        Arbitraries.integers().between(-1000, 1000),
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10))
                    .as((i, j, k) -> new Tuple11<>(a, b, c, d, e, f, g, h, i, j, k)));
  }

  @Provide
  Arbitrary<
          Tuple12<
              Integer,
              String,
              Boolean,
              Double,
              Character,
              Long,
              Short,
              Byte,
              Float,
              Integer,
              String,
              Boolean>>
      tuple12s() {
    return Combinators.combine(
            Arbitraries.integers().between(-1000, 1000),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(-100.0, 100.0),
            Arbitraries.chars().alpha(),
            Arbitraries.longs().between(-10000L, 10000L),
            Arbitraries.shorts().between((short) -100, (short) 100),
            Arbitraries.bytes().between((byte) -50, (byte) 50))
        .flatAs(
            (a, b, c, d, e, f, g, h) ->
                Combinators.combine(
                        Arbitraries.floats().between(-100.0f, 100.0f),
                        Arbitraries.integers().between(-1000, 1000),
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                        Arbitraries.of(true, false))
                    .as((i, j, k, l) -> new Tuple12<>(a, b, c, d, e, f, g, h, i, j, k, l)));
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
  // Tuple9 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple9: map(id, ..., id) preserves equality")
  void tuple9MapIdentity(
      @ForAll("tuple9s")
          Tuple9<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float> tuple) {
    var result =
        tuple.map(
            Function.identity(),
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
  @Label("Tuple9: mapNinth(id) preserves equality")
  void tuple9MapNinthIdentity(
      @ForAll("tuple9s")
          Tuple9<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float> tuple) {
    assertThat(tuple.mapNinth(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple9: map composition law holds")
  void tuple9MapComposition(
      @ForAll("tuple9s")
          Tuple9<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float> tuple) {
    Function<Integer, Integer> f = x -> x + 1;
    Function<Integer, Integer> g = x -> x * 2;

    var mapThenMap = tuple.mapFirst(g).mapFirst(f);
    var mapComposed = tuple.mapFirst(g.andThen(f));

    assertThat(mapThenMap).isEqualTo(mapComposed);
  }

  // -------------------------------------------------------------------------
  // Tuple10 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple10: map(id, ..., id) preserves equality")
  void tuple10MapIdentity(
      @ForAll("tuple10s")
          Tuple10<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float, Integer>
              tuple) {
    var result =
        tuple.map(
            Function.identity(),
            Function.identity(),
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
  @Label("Tuple10: mapTenth(id) preserves equality")
  void tuple10MapTenthIdentity(
      @ForAll("tuple10s")
          Tuple10<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float, Integer>
              tuple) {
    assertThat(tuple.mapTenth(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple10: map composition law holds")
  void tuple10MapComposition(
      @ForAll("tuple10s")
          Tuple10<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float, Integer>
              tuple) {
    Function<Integer, Integer> f = x -> x + 1;
    Function<Integer, Integer> g = x -> x * 2;

    var mapThenMap = tuple.mapFirst(g).mapFirst(f);
    var mapComposed = tuple.mapFirst(g.andThen(f));

    assertThat(mapThenMap).isEqualTo(mapComposed);
  }

  // -------------------------------------------------------------------------
  // Tuple11 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple11: map(id, ..., id) preserves equality")
  void tuple11MapIdentity(
      @ForAll("tuple11s")
          Tuple11<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String>
              tuple) {
    var result =
        tuple.map(
            Function.identity(),
            Function.identity(),
            Function.identity(),
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
  @Label("Tuple11: mapEleventh(id) preserves equality")
  void tuple11MapEleventhIdentity(
      @ForAll("tuple11s")
          Tuple11<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String>
              tuple) {
    assertThat(tuple.mapEleventh(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple11: map composition law holds")
  void tuple11MapComposition(
      @ForAll("tuple11s")
          Tuple11<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String>
              tuple) {
    Function<Integer, Integer> f = x -> x + 1;
    Function<Integer, Integer> g = x -> x * 2;

    var mapThenMap = tuple.mapFirst(g).mapFirst(f);
    var mapComposed = tuple.mapFirst(g.andThen(f));

    assertThat(mapThenMap).isEqualTo(mapComposed);
  }

  // -------------------------------------------------------------------------
  // Tuple12 Properties
  // -------------------------------------------------------------------------

  @Property
  @Label("Tuple12: map(id, ..., id) preserves equality")
  void tuple12MapIdentity(
      @ForAll("tuple12s")
          Tuple12<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String,
                  Boolean>
              tuple) {
    var result =
        tuple.map(
            Function.identity(),
            Function.identity(),
            Function.identity(),
            Function.identity(),
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
  @Label("Tuple12: mapTwelfth(id) preserves equality")
  void tuple12MapTwelfthIdentity(
      @ForAll("tuple12s")
          Tuple12<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String,
                  Boolean>
              tuple) {
    assertThat(tuple.mapTwelfth(Function.identity())).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple12: map composition law holds")
  void tuple12MapComposition(
      @ForAll("tuple12s")
          Tuple12<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String,
                  Boolean>
              tuple) {
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

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple9")
  void tuple9FactoryConsistency(
      @ForAll("tuple9s")
          Tuple9<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float> tuple) {
    var fromFactory =
        Tuple.of(
            tuple._1(),
            tuple._2(),
            tuple._3(),
            tuple._4(),
            tuple._5(),
            tuple._6(),
            tuple._7(),
            tuple._8(),
            tuple._9());

    assertThat(fromFactory).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple10")
  void tuple10FactoryConsistency(
      @ForAll("tuple10s")
          Tuple10<Integer, String, Boolean, Double, Character, Long, Short, Byte, Float, Integer>
              tuple) {
    var fromFactory =
        Tuple.of(
            tuple._1(),
            tuple._2(),
            tuple._3(),
            tuple._4(),
            tuple._5(),
            tuple._6(),
            tuple._7(),
            tuple._8(),
            tuple._9(),
            tuple._10());

    assertThat(fromFactory).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple11")
  void tuple11FactoryConsistency(
      @ForAll("tuple11s")
          Tuple11<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String>
              tuple) {
    var fromFactory =
        Tuple.of(
            tuple._1(),
            tuple._2(),
            tuple._3(),
            tuple._4(),
            tuple._5(),
            tuple._6(),
            tuple._7(),
            tuple._8(),
            tuple._9(),
            tuple._10(),
            tuple._11());

    assertThat(fromFactory).isEqualTo(tuple);
  }

  @Property
  @Label("Tuple.of() factory produces same result as constructor for Tuple12")
  void tuple12FactoryConsistency(
      @ForAll("tuple12s")
          Tuple12<
                  Integer,
                  String,
                  Boolean,
                  Double,
                  Character,
                  Long,
                  Short,
                  Byte,
                  Float,
                  Integer,
                  String,
                  Boolean>
              tuple) {
    var fromFactory =
        Tuple.of(
            tuple._1(),
            tuple._2(),
            tuple._3(),
            tuple._4(),
            tuple._5(),
            tuple._6(),
            tuple._7(),
            tuple._8(),
            tuple._9(),
            tuple._10(),
            tuple._11(),
            tuple._12());

    assertThat(fromFactory).isEqualTo(tuple);
  }
}
