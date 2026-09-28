// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.laws.IsoLaws;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A record with no components maps like any other: its {@code parse} assembles no fields, which is
 * always valid. The common way to meet one is an empty variant of a sealed hierarchy, which the
 * dispatch cannot leave out.
 */
@DisplayName("@GenerateMapping maps a record with no components")
class EmptyRecordMappingTest {

  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import org.higherkindedj.optics.Getter;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;

        """
            + body);
  }

  private static final JavaFileObject[] STATUS = {
    source("Status", "public sealed interface Status permits Active, Deleted {}"),
    source("Active", "public record Active(String since) implements Status {}"),
    source("Deleted", "public record Deleted() implements Status {}"),
    source("StatusDto", "public sealed interface StatusDto permits ActiveDto, DeletedDto {}"),
    source("ActiveDto", "public record ActiveDto(String since) implements StatusDto {}"),
    source("DeletedDto", "public record DeletedDto() implements StatusDto {}"),
    source(
        "ActiveMapping",
        "@GenerateMapping public interface ActiveMapping extends MappingSpec<Active, ActiveDto> {}"),
    source(
        "DeletedMapping",
        "@GenerateMapping public interface DeletedMapping extends MappingSpec<Deleted, DeletedDto>"
            + " {}"),
    source(
        "StatusMapping",
        "@GenerateMapping public interface StatusMapping extends MappingSpec<Status, StatusDto> {}"),
  };

  @SuppressWarnings("unchecked") // the generated parse's result, read reflectively
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
  }

  @Test
  @DisplayName(
      "an empty variant of a sealed hierarchy round-trips, and the dispatch keeps its laws")
  void anEmptySealedVariantRoundTrips() throws Exception {
    var result = RuntimeCompilationHelper.compileWith(new MappingProcessor(), STATUS);
    Object status = result.instance("com.example.StatusMappingImpl");
    Object deleted = result.newInstance("com.example.Deleted");

    Object wire = invoke(status, "build", deleted);
    Assertions.assertThat(wire).isEqualTo(result.newInstance("com.example.DeletedDto"));
    assertThatValidated(parse(status, wire)).hasValue(deleted);

    @SuppressWarnings("unchecked") // the generated prism, read reflectively
    var prism = (ValidatedPrism<Object, Object>) invoke(status, "asValidatedPrism");
    Object missingSince =
        result
            .loadClass("com.example.ActiveDto")
            .getDeclaredConstructors()[0]
            .newInstance((Object) null);
    MappingLaws.assertMappingLaws(prism, wire, missingSince);
  }

  @Test
  @DisplayName("an empty pair maps both ways, and its Iso and prism keep their laws")
  void anEmptyPairKeepsItsLaws() throws Exception {
    var result = RuntimeCompilationHelper.compileWith(new MappingProcessor(), STATUS);
    Object impl = result.instance("com.example.DeletedMappingImpl");
    Object deleted = result.newInstance("com.example.Deleted");

    @SuppressWarnings("unchecked") // the generated prism, read reflectively
    var prism = (ValidatedPrism<Object, Object>) invoke(impl, "asValidatedPrism");
    @SuppressWarnings("unchecked") // the generated Iso, read reflectively
    var iso = (Iso<Object, Object>) invoke(impl, "asIso");
    // A record with no components has one value, so there is no wire sample independent of the
    // domain sample for the combined Iso and prism overload: each half is checked on its own.
    MappingLaws.assertMappingLaws(prism, deleted);
    IsoLaws.assertIsoLaws(iso, deleted, result.newInstance("com.example.DeletedDto"));
  }

  @Test
  @DisplayName("a wire filled only by derived fields parses an empty domain")
  void aDerivedOnlyWireParses() throws Exception {
    var result =
        RuntimeCompilationHelper.compileWith(
            new MappingProcessor(),
            source("Pong", "public record Pong() {}"),
            source("PongDto", "public record PongDto(String kind) {}"),
            source(
                "PongMapping",
                """
                @GenerateMapping
                public interface PongMapping extends MappingSpec<Pong, PongDto> {
                  default Getter<Pong, String> kind() {
                    return Getter.of(pong -> "pong");
                  }
                }
                """));
    Object impl = result.instance("com.example.PongMappingImpl");
    Object pong = result.newInstance("com.example.Pong");

    Object wire = invoke(impl, "build", pong);
    Assertions.assertThat(wire).isEqualTo(result.newInstance("com.example.PongDto", "pong"));
    assertThatValidated(parse(impl, wire)).hasValue(pong);
  }
}
