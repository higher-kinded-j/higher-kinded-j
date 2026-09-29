// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.edit.Edit;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.edit.FallibleEdit;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Accumulating every failure of an input that fails throughout: a bulk parse, {@code
 * Edits.accumulate}, {@code PathOps.traverseValidated}, and a generated mapping's null scan of a
 * list it copies as it is.
 *
 * <p>The cost should grow with the number of failures. {@code BenchmarkAssertionsTest} compares the
 * two sizes: ten times the failures should cost about ten times as long, where an accumulation that
 * copied every earlier failure at each step would cost about a hundred.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class AccumulationBenchmark {

  record Box(int value) {}

  private static final FocusPath<Box, Integer> VALUE =
      FocusPath.of(Lens.of(Box::value, (box, value) -> new Box(value)));

  private static final ValidatedPrism<String, Integer> INT = StandardCodecs.intFromString();

  @Param({"10000", "100000"})
  private int failures;

  private List<String> wire;
  private List<FallibleEdit<Box>> edits;
  private TagsWire nulls;

  @Setup
  public void setup() {
    wire = IntStream.range(0, failures).mapToObj(i -> "x" + i).toList();
    edits = wire.stream().map(raw -> Edit.parseIfPresent(VALUE, raw, INT::parse)).toList();
    nulls = new TagsWire(Collections.nCopies(failures, null));
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, List<Integer>> parseAll() {
    return INT.parseAll(wire);
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, Update<Box>> editsAccumulate() {
    return Edits.accumulate(edits).toValidated();
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, List<Integer>> traverseValidated() {
    return PathOps.traverseValidated(
            wire, raw -> Path.validatedNel(INT.parse(raw)), NonEmptyList.semigroup())
        .run();
  }

  @Benchmark
  public Validated<NonEmptyList<FieldError>, Tags> generatedNullScan() {
    return TagsMappingImpl.INSTANCE.parse(nulls);
  }
}

/** A list the generated mapping copies as it is, so it scans the list for nulls. */
record Tags(List<String> tags) {}

record TagsWire(List<String> tags) {}

@GenerateMapping
interface TagsMapping extends MappingSpec<Tags, TagsWire> {}
