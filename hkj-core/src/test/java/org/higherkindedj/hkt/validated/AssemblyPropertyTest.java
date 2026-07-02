// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.AssemblyArbitraries.errorsOf;
import static org.higherkindedj.hkt.validated.AssemblyArbitraries.warningsOf;

import java.util.List;
import java.util.stream.Stream;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Property tests for the accumulating assembly (issue #581): all errors collected, declaration
 * order, label-prepend composition through nesting, and equivalence with the existing {@code
 * zipWithAccum} primitives.
 */
class AssemblyPropertyTest {

  @Property(tries = 50)
  @Label("accumulate() collects every error, in declaration order")
  void allErrorsCollectedInDeclarationOrder(
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> va,
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> vb,
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> vc) {
    Validated<NonEmptyList<String>, Integer> result =
        Validated.accumulate().and(va).and(vb).and(vc).apply((a, b, c) -> a + b + c);

    List<String> expected = Stream.of(va, vb, vc).flatMap(v -> errorsOf(v).stream()).toList();
    if (expected.isEmpty()) {
      Integer sum = va.fold(_ -> 0, a -> a) + vb.fold(_ -> 0, b -> b) + vc.fold(_ -> 0, c -> c);
      assertThatValidated(result).isValid().hasValue(sum);
    } else {
      assertThatValidated(result).isInvalid();
      assertThat(errorsOf(result)).isEqualTo(expected);
    }
  }

  @Property(tries = 50)
  @Label("The builder is equivalent to zipWith3Accum on the same inputs")
  void equivalentToZipWith3Accum(
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> va,
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> vb,
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> vc) {
    Validated<NonEmptyList<String>, Integer> viaBuilder =
        Validated.accumulate().and(va).and(vb).and(vc).apply((a, b, c) -> a + b + c);
    Validated<NonEmptyList<String>, Integer> viaZip =
        Path.validatedNel(va)
            .zipWith3Accum(Path.validatedNel(vb), Path.validatedNel(vc), (a, b, c) -> a + b + c)
            .run();

    assertThat(viaBuilder).isEqualTo(viaZip);
  }

  @Property(tries = 50)
  @Label("The ValidationPath flavour agrees with the Validated flavour")
  void pathFlavourAgreesWithValidatedFlavour(
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> va,
      @ForAll("validateds") Validated<NonEmptyList<String>, Integer> vb) {
    Validated<NonEmptyList<String>, Integer> direct =
        Validated.accumulate().and(va).and(vb).apply(Integer::sum);
    Validated<NonEmptyList<String>, Integer> viaPath =
        Path.accumulate()
            .and(Path.validatedNel(va))
            .and(Path.validatedNel(vb))
            .apply(Integer::sum)
            .run();

    assertThat(viaPath).isEqualTo(direct);
  }

  @Property(tries = 50)
  @Label("field() prepends the label, and nesting composes one level deep")
  void labelPrependCompositionThroughNesting(
      @ForAll("labels") String outer,
      @ForAll("labels") String inner,
      @ForAll("labels") String message) {
    Validated<NonEmptyList<FieldError>, String> innerAssembly =
        Validated.fields()
            .field(inner, Validated.<FieldError, String>invalidNel(FieldError.of(message)))
            .apply(a -> a);
    Validated<NonEmptyList<FieldError>, String> outerAssembly =
        Validated.fields().field(outer, innerAssembly).apply(a -> a);

    List<String> paths =
        outerAssembly.fold(
            nel -> nel.map(FieldError::pathString).toJavaList(), _ -> List.<String>of());
    assertThat(paths).containsExactly(outer + "." + inner);
  }

  @Property(tries = 50)
  @Label("EitherOrBoth assembly: value present iff no Left; warnings concatenate in order")
  void eitherOrBothTolerantAccumulation(
      @ForAll("eitherOrBoths") EitherOrBoth<NonEmptyList<String>, Integer> ea,
      @ForAll("eitherOrBoths") EitherOrBoth<NonEmptyList<String>, Integer> eb,
      @ForAll("eitherOrBoths") EitherOrBoth<NonEmptyList<String>, Integer> ec) {
    EitherOrBoth<NonEmptyList<String>, Integer> result =
        EitherOrBoth.accumulate().and(ea).and(eb).and(ec).apply((a, b, c) -> a + b + c);

    List<String> expectedWarnings =
        Stream.of(ea, eb, ec).flatMap(e -> warningsOf(e).stream()).toList();
    boolean anyLeft = Stream.of(ea, eb, ec).anyMatch(e -> e.getRight().isNothing());

    assertThat(warningsOf(result)).isEqualTo(expectedWarnings);
    assertThat(result.getRight().isJust()).isEqualTo(!anyLeft);
  }

  @Provide
  Arbitrary<Validated<NonEmptyList<String>, Integer>> validateds() {
    return AssemblyArbitraries.validateds();
  }

  @Provide
  Arbitrary<EitherOrBoth<NonEmptyList<String>, Integer>> eitherOrBoths() {
    return AssemblyArbitraries.eitherOrBoths();
  }

  @Provide
  Arbitrary<String> labels() {
    return AssemblyArbitraries.labels();
  }
}
