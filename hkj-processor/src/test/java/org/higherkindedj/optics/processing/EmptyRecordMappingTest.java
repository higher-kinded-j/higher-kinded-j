// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.stream.Stream;
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

  @Test
  @DisplayName("the generated Impls compile with no warning under -Xlint:all -Werror")
  void theImplsCompileWarningFree() {
    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor())
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(STATUS);

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName("an empty record nested as a component, or held in a list, round-trips")
  void aNestedOrListedEmptyRecordRoundTrips() throws Exception {
    JavaFileObject[] sources =
        Stream.concat(
                Stream.of(STATUS),
                Stream.of(
                    source(
                        "Order",
                        "public record Order(String id, Deleted marker,"
                            + " java.util.List<Deleted> history) {}"),
                    source(
                        "OrderDto",
                        "public record OrderDto(String id, DeletedDto marker,"
                            + " java.util.List<DeletedDto> history) {}"),
                    source(
                        "OrderMapping",
                        "@GenerateMapping public interface OrderMapping extends MappingSpec<Order,"
                            + " OrderDto> {}")))
            .toArray(JavaFileObject[]::new);
    var result = RuntimeCompilationHelper.compileWith(new MappingProcessor(), sources);
    Object impl = result.instance("com.example.OrderMappingImpl");
    Object deleted = result.newInstance("com.example.Deleted");
    var orderConstructor = result.loadClass("com.example.Order").getDeclaredConstructors()[0];
    Object order = orderConstructor.newInstance("o-1", deleted, List.of(deleted, deleted));

    assertThatValidated(parse(impl, invoke(impl, "build", order))).hasValue(order);

    var wireConstructor = result.loadClass("com.example.OrderDto").getDeclaredConstructors()[0];
    Object noMarker = wireConstructor.newInstance("o-1", null, List.of());
    assertThatValidated(parse(impl, noMarker))
        .isInvalid()
        .hasFieldErrors("marker: must not be null");
  }
}
