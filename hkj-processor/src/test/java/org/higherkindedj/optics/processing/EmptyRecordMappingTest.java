// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.laws.IsoLaws;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.laws.ValidatedPrismLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A record with no components, such as the empty variant of a sealed hierarchy, maps like any other
 * record: its {@code parse} has nothing to read and succeeds unless the record's own constructor
 * refuses, its {@code build} writes an empty wire or only the derived fields, and it nests, lifts
 * and dispatches as a component or a variant.
 *
 * <p>Every fixture compiles in one javac run, under {@code -Xlint:all -Werror}, and each case calls
 * one static method of the compiled {@code Probes} class, so the cases read as the calls they make.
 */
@DisplayName("A record with no components maps like any other")
class EmptyRecordMappingTest {

  private static final String PKG = "com.example.empty";

  private static RuntimeCompilationHelper.CompiledResult compiled;

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName,
        """
        package com.example.empty;

        import java.util.Arrays;
        import java.util.List;
        import org.higherkindedj.optics.Getter;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;

        """
            + body);
  }

  @BeforeAll
  static void compileFixtures() {
    JavaFileObject records =
        source(
            "Records",
            """
            public final class Records {
              private Records() {}

              public sealed interface Status permits Active, Deleted {}

              public record Active(String since) implements Status {}

              public record Deleted() implements Status {}

              public sealed interface StatusDto permits ActiveDto, DeletedDto {}

              public record ActiveDto(String since) implements StatusDto {}

              public record DeletedDto() implements StatusDto {}

              public record Pong() {}

              public record PongDto(String kind) {}

              public static final class PongBean {
                private String kind;

                public String getKind() {
                  return kind;
                }

                public void setKind(String kind) {
                  this.kind = kind;
                }
              }

              public static final class PongView {
                public String getKind() {
                  return "sent";
                }
              }

              public record Holder(String id, Deleted deleted, List<Deleted> history) {}

              public record HolderDto(String id, DeletedDto deleted, List<DeletedDto> history) {}

              public record Retired() {
                public Retired {
                  throw new IllegalStateException("retired");
                }
              }

              public record RetiredDto() {}

              public record Archive(String name, Retired retired) {}

              public record ArchiveDto(String name, RetiredDto retired) {}

              public record Box<T>() {}

              public record BoxDto<T>() {}
            }
            """);
    JavaFileObject specs =
        source(
            "Specs",
            """
            public final class Specs {
              private Specs() {}

              @GenerateMapping
              public interface DeletedMapping
                  extends MappingSpec<Records.Deleted, Records.DeletedDto> {}

              @GenerateMapping
              public interface ActiveMapping extends MappingSpec<Records.Active, Records.ActiveDto> {}

              @GenerateMapping
              public interface StatusMapping extends MappingSpec<Records.Status, Records.StatusDto> {}

              @GenerateMapping
              public interface PongMapping extends MappingSpec<Records.Pong, Records.PongDto> {
                default Getter<Records.Pong, String> kind() {
                  return Getter.of(pong -> "pong");
                }
              }

              @GenerateMapping
              public interface PongBeanMapping
                  extends MappingSpec<Records.Pong, Records.PongBean> {
                default Getter<Records.Pong, String> kind() {
                  return Getter.of(pong -> "pong");
                }
              }

              @GenerateMapping
              public interface PongViewMapping
                  extends MappingSpec<Records.Pong, Records.PongView> {}

              @GenerateMapping
              public interface HolderMapping extends MappingSpec<Records.Holder, Records.HolderDto> {}

              @GenerateMapping
              public interface RetiredMapping
                  extends MappingSpec<Records.Retired, Records.RetiredDto> {}

              @GenerateMapping
              public interface ArchiveMapping
                  extends MappingSpec<Records.Archive, Records.ArchiveDto> {}

              @GenerateMapping
              public interface BoxMapping<T> extends MappingSpec<Records.Box<T>, Records.BoxDto<T>> {}
            }
            """);
    JavaFileObject probes =
        source(
            "Probes",
            """
            public final class Probes {
              private Probes() {}

              public static Object deleted() {
                return new Records.Deleted();
              }

              public static Object deletedDto() {
                return new Records.DeletedDto();
              }

              public static Object deletedPrism() {
                return SpecsDeletedMappingImpl.INSTANCE.asValidatedPrism();
              }

              public static Object deletedIso() {
                return SpecsDeletedMappingImpl.INSTANCE.asIso();
              }

              public static Object dispatchParse() {
                return SpecsStatusMappingImpl.INSTANCE.parse(new Records.DeletedDto());
              }

              public static Object dispatchBuild() {
                return SpecsStatusMappingImpl.INSTANCE.build(new Records.Deleted());
              }

              public static Object dispatchParseActive() {
                return SpecsStatusMappingImpl.INSTANCE.parse(new Records.ActiveDto(null));
              }

              public static Object derivedParse() {
                return SpecsPongMappingImpl.INSTANCE.parse(new Records.PongDto("sent"));
              }

              public static Object derivedBuild() {
                return SpecsPongMappingImpl.INSTANCE.build(new Records.Pong());
              }

              public static Object pongDto() {
                return new Records.PongDto("pong");
              }

              public static Object derivedPrism() {
                return SpecsPongMappingImpl.INSTANCE.asValidatedPrism();
              }

              public static Object pong() {
                return new Records.Pong();
              }

              public static Object beanParse() {
                return SpecsPongBeanMappingImpl.INSTANCE.parse(new Records.PongBean());
              }

              public static Object beanBuild() {
                return SpecsPongBeanMappingImpl.INSTANCE.build(new Records.Pong()).getKind();
              }

              public static Object parseOnly() {
                return SpecsPongViewMappingImpl.INSTANCE
                    .asValidatedParse()
                    .parse(new Records.PongView());
              }

              public static Object nested() {
                return SpecsHolderMappingImpl.INSTANCE.parse(
                    new Records.HolderDto(
                        "h1", new Records.DeletedDto(), List.of(new Records.DeletedDto())));
              }

              public static Object nestedExpected() {
                return new Records.Holder(
                    "h1", new Records.Deleted(), List.of(new Records.Deleted()));
              }

              public static Object nestedNulls() {
                return SpecsHolderMappingImpl.INSTANCE.parse(
                    new Records.HolderDto(null, null, Arrays.asList(null, null)));
              }

              public static Object refused() {
                return SpecsRetiredMappingImpl.INSTANCE.parse(new Records.RetiredDto());
              }

              public static Object refusedNested() {
                return SpecsArchiveMappingImpl.INSTANCE.parse(
                    new Records.ArchiveDto(null, new Records.RetiredDto()));
              }

              public static Object generic() {
                return SpecsBoxMappingImpl.<String>instance().parse(new Records.BoxDto<String>());
              }

              public static Object genericExpected() {
                return new Records.Box<String>();
              }
            }
            """);
    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor(), new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all", "-Werror")
            .compile(records, specs, probes);
    assertThat(compilation).succeeded();
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static Object probe(String name) throws ReflectiveOperationException {
    return compiled.invokeStatic(PKG + ".Probes", name);
  }

  @SuppressWarnings("unchecked") // the probes return the generated Validated as Object
  private static Validated<NonEmptyList<FieldError>, Object> parsed(String name)
      throws ReflectiveOperationException {
    return (Validated<NonEmptyList<FieldError>, Object>) probe(name);
  }

  @SuppressWarnings("unchecked") // the probes return the generated prism as Object
  private static ValidatedPrism<Object, Object> prism(String name)
      throws ReflectiveOperationException {
    return (ValidatedPrism<Object, Object>) probe(name);
  }

  @Test
  @DisplayName(
      "an empty pair parses, builds and obeys the iso, round-trip and coherence laws of the"
          + " lossless tier")
  @SuppressWarnings("unchecked") // the probe returns the generated Iso as Object
  void emptyPairIsLossless() throws ReflectiveOperationException {
    Iso<Object, Object> iso = (Iso<Object, Object>) probe("deletedIso");
    ValidatedPrism<Object, Object> mapping = prism("deletedPrism");
    Object domain = probe("deleted");
    Object wire = probe("deletedDto");
    // An empty record has one value, so the samples cannot be independent of each other, as
    // MappingLaws' four-argument overload asks; each law it combines is checked on its own.
    IsoLaws.assertIsoLaws(iso, domain, wire);
    MappingLaws.assertBuildAgreesWithIso(iso, mapping, domain);
    MappingLaws.assertParseAgreesWithIso(iso, mapping, wire);
    ValidatedPrismLaws.assertParseBuild(mapping, domain);
    ValidatedPrismLaws.assertBuildParse(mapping, wire);
  }

  @Test
  @DisplayName("a sealed dispatch maps an empty variant on both sides")
  void sealedDispatchMapsTheEmptyVariant() throws ReflectiveOperationException {
    assertThatValidated(parsed("dispatchParse")).isValid().hasValue(probe("deleted"));
    assertThat(probe("dispatchBuild")).isEqualTo(probe("deletedDto"));
    assertThatValidated(parsed("dispatchParseActive"))
        .isInvalid()
        .hasFieldErrors("since: must not be null");
  }

  @Test
  @DisplayName(
      "a wire whose only component is derived parses the empty domain and builds the derived"
          + " value, on a record wire and a bean wire")
  void derivedOnlyWireMaps() throws ReflectiveOperationException {
    assertThatValidated(parsed("derivedParse")).isValid().hasValue(probe("pong"));
    assertThat(probe("derivedBuild")).isEqualTo(probe("pongDto"));
    MappingLaws.assertMappingLaws(prism("derivedPrism"), probe("pong"));
    assertThatValidated(parsed("beanParse")).isValid().hasValue(probe("pong"));
    assertThat(probe("beanBuild")).isEqualTo("pong");
  }

  @Test
  @DisplayName(
      "a parse-only bean over an empty domain parses, ignoring the getter it has no use for")
  void parseOnlyBeanMaps() throws ReflectiveOperationException {
    assertThatValidated(parsed("parseOnly")).isValid().hasValue(probe("pong"));
  }

  @Test
  @DisplayName(
      "an empty record nests and lifts through a list, and a null one is located like any other")
  void emptyRecordNestsAndLifts() throws ReflectiveOperationException {
    assertThatValidated(parsed("nested")).isValid().hasValue(probe("nestedExpected"));
    assertThatValidated(parsed("nestedNulls"))
        .isInvalid()
        .hasFieldErrors(
            "id: must not be null",
            "deleted: must not be null",
            "history.0: must not be null",
            "history.1: must not be null");
  }

  @Test
  @DisplayName(
      "an empty record's constructor refusal comes back from parse as an error, at its own path"
          + " when nested")
  void constructorRefusalIsLocated() throws ReflectiveOperationException {
    assertThatValidated(parsed("refused")).isInvalid().hasFieldErrors("retired");
    assertThatValidated(parsed("refusedNested"))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "retired: retired");
  }

  @Test
  @DisplayName("a generic empty pair parses through its generic Impl")
  void genericEmptyPairParses() throws ReflectiveOperationException {
    assertThatValidated(parsed("generic")).isValid().hasValue(probe("genericExpected"));
  }
}
