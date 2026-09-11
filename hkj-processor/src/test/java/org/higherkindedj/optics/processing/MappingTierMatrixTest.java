// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every correspondence kind a component can take, on all four shapes the dense tiers emit: a full
 * mapping and a projection, each against a record wire and a bean wire.
 *
 * <p>One predicate decides both optic tiers: a mapping whose reads are all total earns {@code
 * asIso()} when full and {@code asLens()} when a projection; any partial read withholds the optic,
 * and a projection maps as the validated {@code patch} instead. What makes a read partial differs
 * by wire shape only in one place: a bean's reference property can be left unset, so its guarded
 * read is partial where the same read on a record is not. The matrix pins that rule kind by kind,
 * so the two tiers cannot drift apart and neither wire shape can regress on its own.
 *
 * <p>Each case is compiled once, together, under {@code -Xlint:unchecked,rawtypes -Werror}, and
 * then exercised at runtime: every write-back round-trips a domain value, and an unset reference
 * component either locates as {@code must not be null} or, when bridged from an {@code Optional},
 * reads as empty, on every shape alike.
 */
@DisplayName("MappingProcessor - tier selection across correspondence kinds and wire shapes")
class MappingTierMatrixTest {

  private static final String ROOT = "com.example.matrix";

  /**
   * One correspondence kind for component {@code x}: its domain and wire types, the spec vocabulary
   * it needs on each wire shape, whether its read is total on each, and a present domain value.
   *
   * @param name the display name
   * @param pkg the package suffix its sources live under
   * @param domainType the domain component's type
   * @param wireType the wire component's type, on both wire shapes
   * @param recordVocabulary the spec members a record-wire spec declares
   * @param beanVocabulary the spec members a bean-wire spec declares
   * @param recordTotal whether the read is total on a record wire
   * @param beanTotal whether the read is total on a bean wire
   * @param nullable whether the wire component can be null (false for a primitive)
   * @param bridged whether a null wire component reads as an empty domain Optional
   * @param nested whether the case needs the nested Tag spec in its package
   * @param value a present domain value for x derived from a seed, given a factory for the case's
   *     Tag; distinct seeds give distinct values
   */
  record Case(
      String name,
      String pkg,
      String domainType,
      String wireType,
      String recordVocabulary,
      String beanVocabulary,
      boolean recordTotal,
      boolean beanTotal,
      boolean nullable,
      boolean bridged,
      boolean nested,
      BiFunction<Function<String, Object>, String, Object> value) {

    @Override
    public String toString() {
      return name;
    }

    String qualified(String simpleName) {
      return ROOT + "." + pkg + "." + simpleName;
    }
  }

  private static final String TAG_LEAF =
      """
      default ValidatedPrism<String, Tag> x() {
        return ValidatedPrism.of(
            raw -> raw.isBlank()
                ? Validated.invalidNel(FieldError.of("must not be blank"))
                : Validated.validNel(new Tag(raw)),
            Tag::value);
      }
      """;

  private static final String NORMALISING_LEAF =
      """
      default ValidatedPrism<String, String> x() {
        return ValidatedPrism.of(raw -> Validated.validNel(raw.trim()), value -> value);
      }
      """;

  static Stream<Case> cases() {
    return Stream.of(
        new Case(
            "primitive identity",
            "primitive",
            "int",
            "int",
            "",
            "",
            true,
            true,
            false,
            false,
            false,
            (_, seed) -> (int) seed.charAt(0)),
        new Case(
            "reference identity",
            "reference",
            "String",
            "String",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> seed),
        new Case(
            "identity List",
            "identitylist",
            "List<String>",
            "List<String>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(seed)),
        new Case(
            "identity Map",
            "identitymap",
            "Map<String, Integer>",
            "Map<String, Integer>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> Map.of(seed, 1)),
        new Case(
            "identity List with a wildcard argument",
            "wildcardlist",
            "List<? extends CharSequence>",
            "List<? extends CharSequence>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(seed)),
        new Case(
            "converting leaf",
            "leaf",
            "Tag",
            "String",
            TAG_LEAF,
            TAG_LEAF,
            false,
            false,
            true,
            false,
            false,
            (tag, seed) -> tag.apply(seed)),
        new Case(
            "normalising leaf over an identity pair",
            "normalising",
            "String",
            "String",
            NORMALISING_LEAF,
            NORMALISING_LEAF,
            false,
            false,
            true,
            false,
            false,
            (_, seed) -> seed),
        new Case(
            "nested spec",
            "nested",
            "Tag",
            "TagDto",
            "",
            "",
            false,
            false,
            true,
            false,
            true,
            (tag, seed) -> tag.apply(seed)),
        new Case(
            "List lifted through an element leaf",
            "liftedlist",
            "List<Tag>",
            "List<String>",
            TAG_LEAF,
            TAG_LEAF,
            false,
            false,
            true,
            false,
            false,
            (tag, seed) -> List.of(tag.apply(seed))),
        new Case(
            "Optional lifted through an element leaf",
            "liftedoptional",
            "Optional<Tag>",
            "Optional<String>",
            TAG_LEAF,
            TAG_LEAF,
            false,
            false,
            true,
            false,
            false,
            (tag, seed) -> Optional.of(tag.apply(seed))),
        new Case(
            "Map lifted through a value leaf",
            "liftedmap",
            "Map<String, Tag>",
            "Map<String, String>",
            TAG_LEAF,
            TAG_LEAF,
            false,
            false,
            true,
            false,
            false,
            (tag, seed) -> Map.of("k", tag.apply(seed))),
        new Case(
            "Optional bridge",
            "bridge",
            "Optional<String>",
            "String",
            "@OptionalBridge Optional<String> x();\n",
            "",
            false,
            false,
            true,
            true,
            false,
            (_, seed) -> Optional.of(seed)),
        new Case(
            "Optional bridge through a leaf",
            "bridgedleaf",
            "Optional<Tag>",
            "String",
            "@OptionalBridge\n" + TAG_LEAF,
            TAG_LEAF,
            false,
            false,
            true,
            true,
            false,
            (tag, seed) -> Optional.of(tag.apply(seed))),
        new Case(
            "Optional bridge onto an identity List",
            "bridgedlist",
            "Optional<List<String>>",
            "List<String>",
            "@OptionalBridge Optional<List<String>> x();\n",
            "",
            false,
            false,
            true,
            true,
            false,
            (_, seed) -> Optional.of(List.of(seed))));
  }

  private static Compilation compilation;
  private static RuntimeCompilationHelper.CompiledResult result;

  @BeforeAll
  static void compileMatrix() {
    compilation =
        javac()
            .withProcessors(new MappingProcessor())
            .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
            .compile(cases().map(MappingTierMatrixTest::source).toArray(JavaFileObject[]::new));
    result = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  /**
   * The case's sources, in one holder: the domain record with an unprojected primitive {@code id},
   * then a full and a projection wire of each shape with a spec for each.
   */
  private static JavaFileObject source(Case c) {
    String beanProperties =
        """
          private %1$s x;
          public %1$s getX() { return x; }
          public void setX(%1$s x) { this.x = x; }
        """
            .formatted(c.wireType());
    String nestedSpec =
        c.nested()
            ? """
                public record TagDto(String value) {}

                @GenerateMapping
                public interface TagMapping extends MappingSpec<Tag, TagDto> {}
              """
            : "";
    return JavaFileObjects.forSourceString(
        c.qualified("M"),
        """
        package %1$s;

        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.OptionalBridge;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        public final class M {
          public record Tag(String value) {}
        %2$s
          public record D(int id, %3$s x) {}

          public record RecordFull(int id, %4$s x) {}

          public record RecordProjection(%4$s x) {}

          public static class BeanFull {
            private int id;
            public int getId() { return id; }
            public void setId(int id) { this.id = id; }
        %5$s  }

          public static class BeanProjection {
        %5$s  }

          @GenerateMapping
          public interface RecordFullMapping extends MappingSpec<D, RecordFull> {
        %6$s  }

          @GenerateMapping
          public interface RecordProjectionMapping extends MappingSpec<D, RecordProjection> {
        %6$s  }

          @GenerateMapping
          public interface BeanFullMapping extends MappingSpec<D, BeanFull> {
        %7$s  }

          @GenerateMapping
          public interface BeanProjectionMapping extends MappingSpec<D, BeanProjection> {
        %7$s  }
        }
        """
            .formatted(
                ROOT + "." + c.pkg(),
                nestedSpec,
                c.domainType(),
                c.wireType(),
                beanProperties,
                c.recordVocabulary(),
                c.beanVocabulary()));
  }

  @Test
  @DisplayName("every generated Impl in the matrix compiles without a warning")
  void matrixCompilesWithoutWarnings() {
    assertThat(compilation).succeededWithoutWarnings();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("a total read earns the optic on both tiers; a partial one withholds it on both")
  void tierFollowsTotality(Case c) {
    assertFullTier(c, "RecordFullMapping", c.recordTotal());
    assertFullTier(c, "BeanFullMapping", c.beanTotal());
    assertProjectionTier(c, "RecordProjectionMapping", c.recordTotal());
    assertProjectionTier(c, "BeanProjectionMapping", c.beanTotal());
  }

  private static void assertFullTier(Case c, String spec, boolean total) {
    String generated = generatedImpl(c, spec);
    Assertions.assertThat(generated)
        .as("%s: %s parses", c, spec)
        .contains("> parse(")
        .contains("asValidatedPrism() {");
    Assertions.assertThat(generated.contains("asIso() {"))
        .as("%s: %s emits asIso() exactly when every read is total", c, spec)
        .isEqualTo(total);
  }

  private static void assertProjectionTier(Case c, String spec, boolean total) {
    String generated = generatedImpl(c, spec);
    Assertions.assertThat(generated)
        .as("%s: a projection never parses", c)
        .doesNotContain("> parse(");
    Assertions.assertThat(generated.contains("asLens() {"))
        .as("%s: %s emits asLens() exactly when every read is total", c, spec)
        .isEqualTo(total);
    Assertions.assertThat(generated.contains("> patch("))
        .as("%s: %s emits the validated patch exactly when a read is partial", c, spec)
        .isEqualTo(!total);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("every emitted write-back round-trips a domain value, and writes idempotently")
  void writeBacksRoundTrip(Case c) throws ReflectiveOperationException {
    Object domain = domain(c, c.value().apply(tagFactory(c), "a"));
    for (String spec : List.of("RecordFullMapping", "BeanFullMapping")) {
      Object impl = impl(c, spec);
      assertThatValidated(parse(impl, invoke(impl, "build", domain)))
          .as("%s: %s parse(build(d))", c, spec)
          .isValid()
          .hasValue(domain);
      if (generatedImpl(c, spec).contains("asIso() {")) {
        Iso<Object, Object> iso = optic(invoke(impl, "asIso"));
        Assertions.assertThat(iso.reverseGet(iso.get(domain)))
            .as("%s: %s reverseGet(get(d))", c, spec)
            .isEqualTo(domain);
      }
    }
    for (String spec : List.of("RecordProjectionMapping", "BeanProjectionMapping")) {
      Object impl = impl(c, spec);
      // A wire built from a different value, so writing it genuinely changes the domain.
      Object other = invoke(impl, "build", domain(c, c.value().apply(tagFactory(c), "b")));
      if (generatedImpl(c, spec).contains("asLens() {")) {
        Lens<Object, Object> lens = optic(invoke(impl, "asLens"));
        Assertions.assertThat(lens.set(lens.get(domain), domain))
            .as("%s: %s set(get(d), d)", c, spec)
            .isEqualTo(domain);
        Object once = lens.set(other, domain);
        Assertions.assertThat(once).as("%s: %s set(w, d) changes d", c, spec).isNotEqualTo(domain);
        Assertions.assertThat(lens.set(other, once))
            .as("%s: %s set(w, set(w, d))", c, spec)
            .isEqualTo(once);
      } else {
        BiFunction<Object, Object, Validated<NonEmptyList<FieldError>, Object>> patch =
            (d, w) -> validated(invoke(impl, "patch", d, w));
        MappingLaws.assertPatchIdentity(patch, d -> invoke(impl, "build", d), domain);
        MappingLaws.assertPatchIdempotent(patch, domain, other);
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName(
      "an unset reference component locates as must not be null, or reads as empty when bridged,"
          + " on every shape that reads it")
  void unsetComponentIsLocatedOrEmpty(Case c) throws ReflectiveOperationException {
    if (!c.nullable()) {
      return;
    }
    Object domain = domain(c, c.value().apply(tagFactory(c), "a"));
    Object recordFull = recordWire(c, "RecordFull", 7, null);
    Object recordProjection = recordWire(c, "RecordProjection", (Object) null);
    Object beanFull = invoke(impl(c, "BeanFullMapping"), "build", domain);
    invoke(beanFull, "setX", (Object) null);
    Object beanProjection = invoke(impl(c, "BeanProjectionMapping"), "build", domain);
    invoke(beanProjection, "setX", (Object) null);

    assertUnset(c, parse(impl(c, "RecordFullMapping"), recordFull), domain);
    assertUnset(c, parse(impl(c, "BeanFullMapping"), beanFull), domain);
    // A total record projection copies by lens, which writes a hostile null through unchecked;
    // only the patch tier reads it, so only the patch tier is held to the doctrine here.
    if (!c.recordTotal()) {
      assertUnset(
          c,
          validated(invoke(impl(c, "RecordProjectionMapping"), "patch", domain, recordProjection)),
          domain);
    }
    assertUnset(
        c,
        validated(invoke(impl(c, "BeanProjectionMapping"), "patch", domain, beanProjection)),
        domain);
  }

  private static void assertUnset(
      Case c, Validated<NonEmptyList<FieldError>, Object> outcome, Object domain)
      throws ReflectiveOperationException {
    if (c.bridged()) {
      assertThatValidated(outcome).as("%s", c).isValid().hasValue(domain(c, Optional.empty()));
    } else {
      assertThatValidated(outcome).as("%s", c).isInvalid().hasFieldErrors("x: must not be null");
    }
  }

  // ---- runtime helpers ------------------------------------------------------------------------

  /**
   * A domain value {@code D(7, x)}, keeping the unprojected id fixed so a patch must preserve it.
   */
  private static Object domain(Case c, Object x) throws ReflectiveOperationException {
    return constructor(c, "D").newInstance(7, x);
  }

  private static Object recordWire(Case c, String simpleName, Object... components)
      throws ReflectiveOperationException {
    return constructor(c, simpleName).newInstance(components);
  }

  private static Constructor<?> constructor(Case c, String simpleName)
      throws ClassNotFoundException {
    // A record declares exactly one constructor here, the canonical one.
    return result.loadClass(c.qualified("M$" + simpleName)).getDeclaredConstructors()[0];
  }

  private static Function<String, Object> tagFactory(Case c) {
    return value -> {
      try {
        return result.newInstance(c.qualified("M$Tag"), value);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    };
  }

  private static Object impl(Case c, String spec) {
    return result.instance(c.qualified("M" + spec + "Impl"));
  }

  private static String generatedImpl(Case c, String spec) {
    String path = c.qualified("M" + spec + "Impl").replace('.', '/') + ".java";
    return compilation.generatedSourceFiles().stream()
        .filter(file -> file.getName().endsWith(path))
        .findFirst()
        .map(
            file -> {
              try {
                return file.getCharContent(true).toString();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .orElseThrow(() -> new AssertionError("generated source not found: " + path));
  }

  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return validated(invoke(impl, "parse", wire));
  }

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> validated(Object value) {
    return (Validated<NonEmptyList<FieldError>, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static <O> O optic(Object value) {
    return (O) value;
  }
}
