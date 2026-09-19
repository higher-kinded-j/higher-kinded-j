// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An invariant the domain record's own constructor enforces, run against every generated surface
 * that returns a {@code Validated}: {@code parse} (top level, nested, flattened and chunked), the
 * validated {@code patch} (single and chunked ladders), the fallible merge (single and chunked) and
 * {@code @GenerateAssembly}'s {@code assemble()}. Each reports the constructor's exception as a
 * {@code FieldError} at the record's own path, accumulated with every error around it. The total
 * optics cannot return an error, and are pinned to propagating it. Beside them sit the shapes the
 * constructor thunk must keep compiling: a flattened group's lambda parameters inside a chunked
 * ladder, raw and nullable group types, and a spec member type named like the helper's {@code
 * Supplier}.
 *
 * <p>Every fixture compiles in one javac run, under {@code -Xlint:all -Werror}, and each case calls
 * one static method of the compiled {@code Probes} class, so the cases read as the calls they make.
 */
@DisplayName("Generated assemblies report a domain constructor's exception as a located error")
class ConstructorInvariantTest {

  private static final String PKG = "com.example.invariant";

  private static RuntimeCompilationHelper.CompiledResult compiled;

  /** {@code int f1, ..., int fN}. */
  private static String ints(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> "int f" + i)
        .collect(Collectors.joining(", "));
  }

  /** {@code , 3, 4, ..., count}: the arguments after {@code f1} and {@code f2}. */
  private static String rest(int count) {
    return IntStream.rangeClosed(3, count).mapToObj(i -> ", " + i).collect(Collectors.joining());
  }

  /** {@code , 1, 2, ..., count}: the arguments for {@code f1} to {@code fN}. */
  private static String args(int count) {
    return IntStream.rangeClosed(1, count).mapToObj(i -> ", " + i).collect(Collectors.joining());
  }

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName,
        """
        package com.example.invariant;

        import java.util.List;
        import java.util.UUID;
        import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.Flatten;
        import org.higherkindedj.optics.annotations.GenerateAssembly;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.GenerateMerge;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.StandardCodecs;
        import org.higherkindedj.optics.validated.ValidatedPrism;
        import org.jspecify.annotations.Nullable;

        """
            + body);
  }

  @BeforeAll
  static void compileFixtures() {
    String wide = ints(17);
    String wideSixteen = ints(16);
    JavaFileObject records =
        source(
            "Records",
            """
            public final class Records {
              private Records() {}

              public record Range(int lo, int hi) {
                public Range {
                  if (lo > hi) {
                    throw new IllegalArgumentException("lo > hi");
                  }
                }
              }

              public record RangeDto(int lo, int hi) {}

              public record Schedule(String name, List<Range> ranges) {}

              public record ScheduleDto(String name, List<RangeDto> ranges) {}

              public record Quiet(int n) {
                public Quiet {
                  if (n < 0) {
                    throw new IllegalStateException();
                  }
                }
              }

              public record QuietDto(int n) {}

              public record Blank(int n) {
                public Blank {
                  if (n < 0) {
                    throw new IllegalArgumentException(" ");
                  }
                }
              }

              public record BlankDto(int n) {}

              public record Pair(int c1, int c2) {
                public Pair {
                  if (c1 > c2) {
                    throw new IllegalArgumentException("c1 must not exceed c2");
                  }
                }
              }

              public record Big(String name, %3$s, Pair pair) {}

              public record BigDto(String name, %3$s, int c1, int c2) {}

              @SuppressWarnings("rawtypes")
              public record Tagged(List<List> tags, int n) {
                public Tagged {
                  if (n < 0) {
                    throw new IllegalArgumentException("n must not be negative");
                  }
                }
              }

              public record Post(String title, Tagged tagged) {}

              public record PostDto(String title, String tags, int n) {}

              public record Booked(String name, @Nullable Span span) {}

              public record BookedDto(String name, int from, int to) {}

              public record Rating(int stars) {
                public Rating {
                  if (stars < 0) {
                    throw new IllegalArgumentException("stars must not be negative");
                  }
                }
              }

              public record RatingDto(int stars) {}

              public record Span(int from, int to) {
                public Span {
                  if (from > to) {
                    throw new IllegalArgumentException("from must not exceed to");
                  }
                }
              }

              public record Booking(String name, Span span) {}

              public record BookingDto(String name, int from, int to) {}

              public record Slot(UUID code, int lo, int hi, String note) {
                public Slot {
                  if (lo > hi) {
                    throw new IllegalArgumentException("lo must not exceed hi");
                  }
                }
              }

              public record SlotDto(String code, int lo, int hi) {}

              public record SlotCardDto(int lo, int hi) {}

              public record Wide(%1$s) {
                public Wide {
                  if (f1 > f2) {
                    throw new IllegalArgumentException("f1 must not exceed f2");
                  }
                }
              }

              public record WideDto(%1$s) {}

              public record WideHolder(String name, Wide wide) {}

              public record WideHolderDto(String name, WideDto wide) {}

              public record WideSlot(UUID id, %2$s, String keep) {
                public WideSlot {
                  if (f1 > f2) {
                    throw new IllegalArgumentException("f1 must not exceed f2");
                  }
                }
              }

              public record WideSlotDto(String id, %2$s) {}

              public record Target(int lo, int hi, UUID id) {
                public Target {
                  if (lo > hi) {
                    throw new IllegalArgumentException("lo > hi");
                  }
                }
              }

              public record LoSource(int lo, String id) {}

              public record HiSource(int hi) {}

              public record WideTarget(UUID id, %1$s) {
                public WideTarget {
                  if (f1 > f2) {
                    throw new IllegalArgumentException("f1 must not exceed f2");
                  }
                }
              }

              public record WideSource(String id, %2$s) {}

              public record LastSource(int f17) {}

              @GenerateAssembly
              public record Signup(String password, String confirm) {
                public Signup {
                  if (!password.equals(confirm)) {
                    throw new IllegalArgumentException("passwords differ");
                  }
                }
              }
            }
            """
                .formatted(wide, wideSixteen, ints(15)));
    JavaFileObject specs =
        source(
            "Specs",
            """
            public final class Specs {
              private Specs() {}

              @GenerateMapping
              public interface RangeMapping extends MappingSpec<Records.Range, Records.RangeDto> {}

              @GenerateMapping
              public interface ScheduleMapping
                  extends MappingSpec<Records.Schedule, Records.ScheduleDto> {}

              @GenerateMapping
              public interface QuietMapping extends MappingSpec<Records.Quiet, Records.QuietDto> {}

              @GenerateMapping
              public interface BookingMapping
                  extends MappingSpec<Records.Booking, Records.BookingDto> {
                @Flatten
                Records.Span span();
              }

              @GenerateMapping
              public interface BlankMapping extends MappingSpec<Records.Blank, Records.BlankDto> {}

              // Wider than one ladder: the group's own terminal names its lambda parameters after
              // its members, c1 and c2, inside a chunk local's initialiser.
              @GenerateMapping
              public interface BigMapping extends MappingSpec<Records.Big, Records.BigDto> {
                @Flatten
                Records.Pair pair();
              }

              @GenerateMapping
              public interface PostMapping extends MappingSpec<Records.Post, Records.PostDto> {
                @Flatten
                Records.Tagged tagged();

                @SuppressWarnings("rawtypes")
                default ValidatedPrism<String, List<List>> tags() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(List.<List>of(List.of(raw))),
                      tags -> String.valueOf(tags.getFirst().getFirst()));
                }
              }

              @GenerateMapping
              public interface BookedMapping
                  extends MappingSpec<Records.Booked, Records.BookedDto> {
                @Flatten
                Records.Span span();
              }

              // The Impl inherits this member type, which must not shadow java.util.function.
              @GenerateMapping
              public interface RatingMapping extends MappingSpec<Records.Rating, Records.RatingDto> {
                record Supplier(String vendor) {}
              }

              @GenerateMapping
              public interface SlotMapping extends MappingSpec<Records.Slot, Records.SlotDto> {
                default ValidatedPrism<String, UUID> code() {
                  return StandardCodecs.uuid();
                }
              }

              @GenerateMapping
              public interface SlotCardMapping
                  extends MappingSpec<Records.Slot, Records.SlotCardDto> {}

              @GenerateMapping
              public interface WideMapping extends MappingSpec<Records.Wide, Records.WideDto> {}

              @GenerateMapping
              public interface WideHolderMapping
                  extends MappingSpec<Records.WideHolder, Records.WideHolderDto> {}

              @GenerateMapping
              public interface WideSlotMapping
                  extends MappingSpec<Records.WideSlot, Records.WideSlotDto> {
                default ValidatedPrism<String, UUID> id() {
                  return StandardCodecs.uuid();
                }
              }

              @GenerateMerge
              public interface TargetMerge {
                Validated<NonEmptyList<FieldError>, Records.Target> merge(
                    Records.LoSource lo, Records.HiSource hi);

                default ValidatedPrism<String, UUID> id() {
                  return StandardCodecs.uuid();
                }
              }

              @GenerateMerge
              public interface SupplierTargetMerge {
                Validated<NonEmptyList<FieldError>, Records.Target> merge(
                    Records.LoSource lo, Records.HiSource hi);

                default ValidatedPrism<String, UUID> id() {
                  return StandardCodecs.uuid();
                }

                enum Supplier {
                  ACME
                }
              }

              @GenerateMerge
              public interface WideTargetMerge {
                Validated<NonEmptyList<FieldError>, Records.WideTarget> merge(
                    Records.WideSource first, Records.LastSource last);

                default ValidatedPrism<String, UUID> id() {
                  return StandardCodecs.uuid();
                }
              }
            }
            """);
    JavaFileObject probes =
        source(
            "Probes",
            """
            public final class Probes {
              private Probes() {}

              static final String ID = "123e4567-e89b-12d3-a456-426614174000";

              static final Records.Slot SLOT = new Records.Slot(UUID.fromString(ID), 1, 2, "kept");

              public static Object topLevel() {
                return SpecsRangeMappingImpl.INSTANCE.parse(new Records.RangeDto(5, 1));
              }

              public static Object nested() {
                return SpecsScheduleMappingImpl.INSTANCE.parse(
                    new Records.ScheduleDto(
                        null, List.of(new Records.RangeDto(1, 2), new Records.RangeDto(5, 1))));
              }

              public static Object accepted() {
                return SpecsScheduleMappingImpl.INSTANCE.parse(
                    new Records.ScheduleDto("week", List.of(new Records.RangeDto(1, 2))));
              }

              public static Object unexplained() {
                return SpecsQuietMappingImpl.INSTANCE.parse(new Records.QuietDto(-1));
              }

              public static Object blank() {
                return SpecsBlankMappingImpl.INSTANCE.parse(new Records.BlankDto(-1));
              }

              public static Object chunkedGroup() {
                return SpecsBigMappingImpl.INSTANCE.parse(
                    new Records.BigDto(null%3$s, 5, 1));
              }

              public static Object rawGroup() {
                return SpecsPostMappingImpl.INSTANCE.parse(new Records.PostDto(null, "t", -1));
              }

              public static Object nullableGroup() {
                return SpecsBookedMappingImpl.INSTANCE.parse(new Records.BookedDto("n", 5, 1));
              }

              public static Object inheritedSupplier() {
                return SpecsRatingMappingImpl.INSTANCE.parse(new Records.RatingDto(-1));
              }

              public static Object inheritedSupplierMerge() {
                return SpecsSupplierTargetMergeImpl.INSTANCE.merge(
                    new Records.LoSource(5, ID), new Records.HiSource(1));
              }

              public static Object neverBoth() {
                return SpecsSlotMappingImpl.INSTANCE.patch(SLOT, new Records.SlotDto("nope", 5, 1));
              }

              public static ValidatedPrism<Records.ScheduleDto, Records.Schedule> schedulePrism() {
                return SpecsScheduleMappingImpl.INSTANCE.asValidatedPrism();
              }

              public static Records.ScheduleDto acceptedSchedule() {
                return new Records.ScheduleDto("week", List.of(new Records.RangeDto(1, 2)));
              }

              public static Records.ScheduleDto refusedSchedule() {
                return new Records.ScheduleDto("week", List.of(new Records.RangeDto(5, 1)));
              }

              public static Object flattened() {
                return SpecsBookingMappingImpl.INSTANCE.parse(new Records.BookingDto(null, 5, 1));
              }

              public static Object chunked() {
                return SpecsWideHolderMappingImpl.INSTANCE.parse(
                    new Records.WideHolderDto(null, new Records.WideDto(5, 1%1$s)));
              }

              public static Object patch() {
                return SpecsSlotMappingImpl.INSTANCE.patch(SLOT, new Records.SlotDto(ID, 5, 1));
              }

              public static Object patchAccepted() {
                return SpecsSlotMappingImpl.INSTANCE.patch(SLOT, new Records.SlotDto(ID, 1, 5));
              }

              public static Object chunkedPatch() {
                return SpecsWideSlotMappingImpl.INSTANCE.patch(
                    new Records.WideSlot(UUID.fromString(ID), 1, 2%2$s, "kept"),
                    new Records.WideSlotDto(ID, 5, 1%2$s));
              }

              public static Object merge() {
                return SpecsTargetMergeImpl.INSTANCE.merge(
                    new Records.LoSource(5, ID), new Records.HiSource(1));
              }

              public static Object chunkedMerge() {
                return SpecsWideTargetMergeImpl.INSTANCE.merge(
                    new Records.WideSource(ID, 5, 1%2$s), new Records.LastSource(17));
              }

              public static Object assembled() {
                return RecordsSignupAssembly.fields()
                    .password(Validated.validNel("secret"))
                    .confirm(Validated.validNel("secrets"))
                    .assemble();
              }

              public static Object reverseGet() {
                return SpecsRangeMappingImpl.INSTANCE.asIso().reverseGet(new Records.RangeDto(5, 1));
              }

              public static Object lensSet() {
                return SpecsSlotCardMappingImpl.INSTANCE
                    .asLens()
                    .set(new Records.SlotCardDto(5, 1), SLOT);
              }
            }
            """
                .formatted(rest(17), rest(16), args(15)));
    Compilation compilation =
        javac()
            .withProcessors(
                new MappingProcessor(),
                new MergeProcessor(),
                new AssemblyProcessor(),
                new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all", "-Werror")
            .compile(records, specs, probes);
    assertThat(compilation).succeeded();
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  @SuppressWarnings("unchecked") // the probes return the generated Validated as Object
  private static Validated<NonEmptyList<FieldError>, Object> probe(String name)
      throws ReflectiveOperationException {
    return (Validated<NonEmptyList<FieldError>, Object>)
        compiled.invokeStatic(PKG + ".Probes", name);
  }

  @Test
  @DisplayName("a top-level record's invariant is an unlabelled error, even on a lossless mapping")
  void topLevelInvariantIsUnlabelled() throws ReflectiveOperationException {
    assertThatValidated(probe("topLevel")).isInvalid().hasFieldErrors("lo > hi");
  }

  @Test
  @DisplayName(
      "a nested record's invariant locates at its own path, beside the errors accumulated"
          + " around it")
  void nestedInvariantLocatesAndAccumulates() throws ReflectiveOperationException {
    assertThatValidated(probe("nested"))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "ranges.1: lo > hi");
  }

  @Test
  @DisplayName("a value the constructor accepts parses to Valid")
  void acceptedValueParses() throws ReflectiveOperationException {
    assertThatValidated(probe("accepted")).isValid();
  }

  @Test
  @DisplayName("an exception without a message, or with a blank one, reads 'not a valid Quiet'")
  void unexplainedExceptionNamesTheRecord() throws ReflectiveOperationException {
    assertThatValidated(probe("unexplained")).isInvalid().hasFieldErrors("not a valid Quiet");
    assertThatValidated(probe("blank")).isInvalid().hasFieldErrors("not a valid Blank");
  }

  @Test
  @DisplayName(
      "a record reports its components' errors or its invariant, never both, since the"
          + " constructor needs every component")
  void componentErrorsPreemptTheInvariant() throws ReflectiveOperationException {
    assertThatValidated(probe("neverBoth"))
        .isInvalid()
        .hasFieldErrors("code: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)");
  }

  @Test
  @DisplayName(
      "a flattened group wider than one ladder keeps its lambda parameters clear of the chunk"
          + " locals, even when its members are named c1 and c2")
  void chunkedGroupCompilesAndLocates() throws ReflectiveOperationException {
    assertThatValidated(probe("chunkedGroup"))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "pair: c1 must not exceed c2");
  }

  @Test
  @DisplayName(
      "a flattened group with a raw member type compiles under -Werror, and a nullable group"
          + " type is still typed by its explicit type argument")
  void groupTypesCompileCleanly() throws ReflectiveOperationException {
    assertThatValidated(probe("rawGroup"))
        .isInvalid()
        .hasFieldErrors("title: must not be null", "tagged: n must not be negative");
    assertThatValidated(probe("nullableGroup"))
        .isInvalid()
        .hasFieldErrors("span: from must not exceed to");
  }

  @Test
  @DisplayName(
      "a member type named Supplier on the spec does not shadow the helper's Supplier, in a"
          + " mapping or a merge")
  void inheritedSupplierDoesNotShadowTheHelper() throws ReflectiveOperationException {
    assertThatValidated(probe("inheritedSupplier"))
        .isInvalid()
        .hasFieldErrors("stars must not be negative");
    assertThatValidated(probe("inheritedSupplierMerge")).isInvalid().hasFieldErrors("lo > hi");
  }

  @Test
  @DisplayName("the fallible-tier laws hold with a refused value as the non-parsing sample")
  @SuppressWarnings("unchecked") // the probes hand back the generated prism and wires as Object
  void lawsHoldWithARefusedSample() throws ReflectiveOperationException {
    MappingLaws.assertMappingLaws(
        (ValidatedPrism<Object, Object>) compiled.invokeStatic(PKG + ".Probes", "schedulePrism"),
        compiled.invokeStatic(PKG + ".Probes", "acceptedSchedule"),
        compiled.invokeStatic(PKG + ".Probes", "refusedSchedule"));
  }

  @Test
  @DisplayName("a flattened group's invariant locates under the group's component name")
  void flattenedGroupInvariantLocates() throws ReflectiveOperationException {
    assertThatValidated(probe("flattened"))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "span: from must not exceed to");
  }

  @Test
  @DisplayName("a record wider than one fields() ladder guards its constructor the same way")
  void chunkedInvariantLocates() throws ReflectiveOperationException {
    assertThatValidated(probe("chunked"))
        .isInvalid()
        .hasFieldErrors("name: must not be null", "wide: f1 must not exceed f2");
  }

  @Test
  @DisplayName("the validated patch reports the patched combination the constructor refuses")
  void patchReportsTheInvariant() throws ReflectiveOperationException {
    assertThatValidated(probe("patch")).isInvalid().hasFieldErrors("lo must not exceed hi");
    assertThatValidated(probe("patchAccepted")).isValid();
  }

  @Test
  @DisplayName("a patch wider than one fields() ladder reports it too")
  void chunkedPatchReportsTheInvariant() throws ReflectiveOperationException {
    assertThatValidated(probe("chunkedPatch")).isInvalid().hasFieldErrors("f1 must not exceed f2");
  }

  @Test
  @DisplayName("a fallible merge reports the merged combination the target refuses")
  void mergeReportsTheInvariant() throws ReflectiveOperationException {
    assertThatValidated(probe("merge")).isInvalid().hasFieldErrors("lo > hi");
  }

  @Test
  @DisplayName("a merge wider than one fields() ladder reports it too")
  void chunkedMergeReportsTheInvariant() throws ReflectiveOperationException {
    assertThatValidated(probe("chunkedMerge")).isInvalid().hasFieldErrors("f1 must not exceed f2");
  }

  @Test
  @DisplayName("@GenerateAssembly's assemble() reports the invariant")
  void assembleReportsTheInvariant() throws ReflectiveOperationException {
    assertThatValidated(probe("assembled")).isInvalid().hasFieldErrors("passwords differ");
  }

  @Test
  @DisplayName(
      "the total optics cannot return an error, so asIso().reverseGet and asLens().set"
          + " propagate the exception")
  void totalOpticsPropagate() {
    assertThatThrownBy(() -> compiled.invokeStatic(PKG + ".Probes", "reverseGet"))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage("lo > hi");
    assertThatThrownBy(() -> compiled.invokeStatic(PKG + ".Probes", "lensSet"))
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage("lo must not exceed hi");
  }
}
