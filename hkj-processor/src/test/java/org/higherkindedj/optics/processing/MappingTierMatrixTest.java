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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * reads as empty, on every shape alike. A bridged bean property's field starts out holding a value,
 * as a DTO's initialiser gives it, so an empty Optional round-trips only because {@code build}
 * writes its absence rather than leaving the property as the bean was constructed.
 *
 * <p>The identity container rows are here for the second rule the dense tiers carry: a null
 * anywhere inside an identity container is a located invalid at its full path, however the
 * container is declared: nested, through a subtype or a supertype, raw, with a wildcard argument,
 * inside an {@code Optional}, or bridged. The scan's helper returns its argument's own type, so
 * none of those shapes costs the scan, and every one is compiled here under the same lint as the
 * rest. {@code MappingProcessorUpdateTest} pins the sparse half of the same rule.
 *
 * <p>A raw type is also a warning in generated code that the author's own suppression cannot reach,
 * so the rows naming one pin that each Impl member writing it out or inferring it answers for it
 * itself, and that the class itself carries none.
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

    /**
     * The suppression the case's own sources need. A raw container warns wherever it is declared,
     * and the holder owns those declarations; the generated Impls are separate compilation units,
     * so they answer for it themselves and stay held to {@code -Xlint:rawtypes -Werror} on their
     * own.
     */
    String suppression() {
      return Stream.of(domainType, wireType).anyMatch(type -> type.matches(RAW_CONTAINER))
          ? "@SuppressWarnings(\"rawtypes\")\n"
          : "";
    }
  }

  /**
   * A container a case declares without its type arguments, at any depth ({@code List}, {@code
   * Optional<List>}), which its own sources must then suppress.
   */
  private static final String RAW_CONTAINER = ".*\\b(?:List|Set|Map)\\b(?!<).*";

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
        // A raw container is scanned like a parameterised one: the helper takes its argument's
        // own type, so a raw argument needs no unchecked conversion. It stays an identity copy,
        // so it selects the same tier the parameterised List does.
        new Case(
            "raw identity List",
            "rawlist",
            "List",
            "List",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(seed)),
        new Case(
            "raw identity Set",
            "rawset",
            "Set",
            "Set",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> Set.of(seed)),
        new Case(
            "raw identity Map",
            "rawmap",
            "Map",
            "Map",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> Map.of(seed, 1)),
        // A raw type inside a type argument is what javac reports on a generated lambda's inferred
        // parameter, where a bare one is silent: the bean projection's patch assembles this
        // component through such a lambda.
        new Case(
            "raw List inside an identity List",
            "rawnested",
            "List<List>",
            "List<List>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(List.of(seed))),
        // The scan reaches every level the type names, and a container declared through a
        // subtype or a supertype of the one it holds.
        new Case(
            "nested identity List",
            "nestedlist",
            "List<List<String>>",
            "List<List<String>>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(List.of(seed))),
        new Case(
            "identity List of wildcard Lists",
            "wildcardlists",
            "List<? extends List<String>>",
            "List<? extends List<String>>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(List.of(seed))),
        new Case(
            "identity Map of Lists",
            "mapoflists",
            "Map<String, List<String>>",
            "Map<String, List<String>>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> Map.of(seed, List.of(seed))),
        new Case(
            "identity array of arrays",
            "arrayofarrays",
            "String[][]",
            "String[][]",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> new String[][] {{seed}}),
        new Case(
            "identity ArrayList",
            "arraylist",
            "java.util.ArrayList<String>",
            "java.util.ArrayList<String>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> new ArrayList<>(List.of(seed))),
        new Case(
            "identity Collection",
            "collection",
            "java.util.Collection<String>",
            "java.util.Collection<String>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> List.of(seed)),
        new Case(
            "identity LinkedHashMap",
            "linkedhashmap",
            "java.util.LinkedHashMap<String, Integer>",
            "java.util.LinkedHashMap<String, Integer>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> new LinkedHashMap<>(Map.of(seed, 1))),
        // An Optional cannot hold a null, but the List inside it can.
        new Case(
            "identity Optional of a List",
            "optionallist",
            "Optional<List<String>>",
            "Optional<List<String>>",
            "",
            "",
            true,
            false,
            true,
            false,
            false,
            (_, seed) -> Optional.of(List.of(seed))),
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
            (_, seed) -> Optional.of(List.of(seed))),
        // The bridge has to name a wildcard-carrying element rather than infer it: Optional is
        // invariant, so a captured argument would not be the type the component declares.
        new Case(
            "Optional bridge onto a wildcard List",
            "bridgedwildcard",
            "Optional<List<? extends CharSequence>>",
            "List<? extends CharSequence>",
            "@OptionalBridge Optional<List<? extends CharSequence>> x();\n",
            "",
            false,
            false,
            true,
            true,
            false,
            (_, seed) -> Optional.of(List.of(seed))),
        new Case(
            "Optional bridge onto a nested List",
            "bridgednestedlist",
            "Optional<List<List<String>>>",
            "List<List<String>>",
            "@OptionalBridge Optional<List<List<String>>> x();\n",
            "",
            false,
            false,
            true,
            true,
            false,
            (_, seed) -> Optional.of(List.of(List.of(seed)))),
        // A raw element reaches the marker's stub, the patch assembly and the bean's write, and
        // each answers for it.
        new Case(
            "Optional bridge onto a raw List",
            "bridgedraw",
            "Optional<List>",
            "List",
            "@OptionalBridge Optional<List> x();\n",
            "",
            false,
            false,
            true,
            true,
            false,
            (_, seed) -> Optional.of(List.of(seed))),
        // The bridged element nests through its own spec, as an unbridged Tag component would.
        new Case(
            "Optional bridge through a nested spec",
            "bridgednested",
            "Optional<Tag>",
            "TagDto",
            "@OptionalBridge Optional<Tag> x();\n",
            "",
            false,
            false,
            true,
            true,
            true,
            (tag, seed) -> Optional.of(tag.apply(seed))),
        // A bridged container lifts its elements as the unbridged one would: through their spec,
        // or through the element leaf.
        new Case(
            "Optional bridge onto a List through a nested spec",
            "bridgedlistnested",
            "Optional<List<Tag>>",
            "List<TagDto>",
            "@OptionalBridge Optional<List<Tag>> x();\n",
            "",
            false,
            false,
            true,
            true,
            true,
            (tag, seed) -> Optional.of(List.of(tag.apply(seed)))),
        new Case(
            "Optional bridge onto a List through an element leaf",
            "bridgedlistleaf",
            "Optional<List<Tag>>",
            "List<String>",
            "@OptionalBridge\n" + TAG_LEAF,
            TAG_LEAF,
            false,
            false,
            true,
            true,
            false,
            (tag, seed) -> Optional.of(List.of(tag.apply(seed)))));
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
   * The value a bridged bean property's field starts out holding: a present one, as a DTO's
   * initialiser gives it, so that only a {@code build} that writes absence can round-trip an empty
   * domain Optional.
   */
  private static String beanDefault(Case c) {
    return switch (c.wireType()) {
      case "String" -> "\"default\"";
      case "TagDto" -> "new TagDto(\"default\")";
      default -> "new java.util.ArrayList<>()";
    };
  }

  /**
   * The case's sources, in one holder: the domain record with an unprojected primitive {@code id},
   * then a full and a projection wire of each shape with a spec for each. A bridged bean property's
   * field is initialised to a present value.
   */
  private static JavaFileObject source(Case c) {
    String beanProperties =
        """
          private %1$s x%2$s;
          public %1$s getX() { return x; }
          public void setX(%1$s x) { this.x = x; }
        """
            .formatted(c.wireType(), c.bridged() ? " = " + beanDefault(c) : "");
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
        import java.util.Set;
        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.OptionalBridge;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        %8$spublic final class M {
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
                c.beanVocabulary(),
                c.suppression()));
  }

  @Test
  @DisplayName("every generated Impl in the matrix compiles without a warning")
  void matrixCompilesWithoutWarnings() {
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  @DisplayName(
      "a raw type is answered by the members that write it out or infer it, never by the whole"
          + " Impl, and a spec naming none carries no suppression")
  void rawTypesAreSuppressedMemberByMember() {
    String raw = "@SuppressWarnings\\(\"rawtypes\"\\)\\s+";
    Assertions.assertThat(
            generatedImpl(caseNamed("Optional bridge onto a raw List"), "RecordProjectionMapping"))
        .containsPattern(raw + "public Optional<List> x\\(\\)")
        .containsPattern(raw + "public [^(]* patch\\(")
        .doesNotContainPattern(raw + "public final class");
    Assertions.assertThat(
            generatedImpl(
                caseNamed("Optional bridge onto an identity List"), "RecordProjectionMapping"))
        .doesNotContain("rawtypes");
  }

  @Test
  @DisplayName(
      "every identity container is read through its null scan, raw and wildcard-argument ones"
          + " alike, and never through the plain guard")
  void everyIdentityContainerIsScanned() {
    // Only these three specs read: the record projection is total on all these cases, so it
    // copies by lens and parses nothing.
    for (String spec : List.of("RecordFullMapping", "BeanFullMapping", "BeanProjectionMapping")) {
      for (String name :
          List.of(
              "raw identity List",
              "raw identity Set",
              "identity List with a wildcard argument",
              "identity ArrayList",
              "identity Collection")) {
        Assertions.assertThat(generatedImpl(caseNamed(name), spec))
            .as("%s, %s", name, spec)
            .contains("hkj$allPresent(")
            .doesNotContain("hkj$ifPresent(");
      }
      for (String name : List.of("raw identity Map", "identity LinkedHashMap")) {
        Assertions.assertThat(generatedImpl(caseNamed(name), spec))
            .as("%s, %s", name, spec)
            .contains("hkj$valuesPresent(")
            .doesNotContain("hkj$ifPresent(");
      }
    }
  }

  @Test
  @DisplayName(
      "a valid identity container is handed over as a copy where its declared type has one, raw"
          + " included, and as the very reference the wire held where it does not")
  void aScannedContainerIsCopiedWhereItsTypeAllows() throws ReflectiveOperationException {
    for (Case c : List.of(caseNamed("raw identity List"), caseNamed("identity ArrayList"))) {
      Object held = new ArrayList<>(List.of("a"));
      Validated<NonEmptyList<FieldError>, Object> parsed =
          parse(impl(c, "RecordFullMapping"), recordWire(c, "RecordFull", 7, held));
      assertThatValidated(parsed).as("%s", c).isValid().hasValue(domain(c, held));
    }
    List<String> list = new ArrayList<>(List.of("a"));
    Assertions.assertThat(parsedX(caseNamed("raw identity List"), list))
        .as("a List has a copy of its own type, so the domain holds an equal one of its own")
        .isEqualTo(list)
        .isNotSameAs(list);
    ArrayList<String> held = new ArrayList<>(List.of("a"));
    Assertions.assertThat(parsedX(caseNamed("identity ArrayList"), held))
        .as("an ArrayList has no copy of its own type, so it is shared")
        .isSameAs(held);
  }

  /** The {@code x} a record-wire parse hands the domain for a wire holding {@code held}. */
  private static Object parsedX(Case c, Object held) throws ReflectiveOperationException {
    return component(
        parse(impl(c, "RecordFullMapping"), recordWire(c, "RecordFull", 7, held)).get(), "x");
  }

  /**
   * A wire value for one case's {@code x} with a {@code null} somewhere inside it, and where that
   * null locates.
   */
  record Holed(String caseName, Supplier<Object> value, String expected) {
    @Override
    public String toString() {
      return caseName;
    }
  }

  private static <T> List<T> withNull(T first) {
    return Arrays.asList(first, null);
  }

  private static <K> Map<K, Object> nullUnder(K key) {
    Map<K, Object> map = new LinkedHashMap<>();
    map.put(key, null);
    return map;
  }

  static Stream<Holed> holed() {
    return Stream.of(
        new Holed("identity List", () -> withNull("a"), "x.1: must not be null"),
        new Holed("identity Map", () -> nullUnder("k"), "x.k: must not be null"),
        new Holed(
            "identity List with a wildcard argument", () -> withNull("a"), "x.1: must not be null"),
        new Holed("raw identity List", () -> withNull("a"), "x.1: must not be null"),
        new Holed(
            "raw identity Set",
            () -> new HashSet<>(withNull("a")),
            "x: must not contain a null element"),
        new Holed("raw identity Map", () -> nullUnder("k"), "x.k: must not be null"),
        new Holed(
            "raw List inside an identity List",
            () -> List.of(withNull("a")),
            "x.0.1: must not be null"),
        new Holed("nested identity List", () -> List.of(withNull("a")), "x.0.1: must not be null"),
        new Holed(
            "identity List of wildcard Lists",
            () -> List.of(withNull("a")),
            "x.0.1: must not be null"),
        new Holed(
            "identity Map of Lists", () -> Map.of("k", withNull("a")), "x.k.1: must not be null"),
        new Holed(
            "identity array of arrays",
            () -> new String[][] {{"a"}, {"b", null}},
            "x.1.1: must not be null"),
        new Holed(
            "identity ArrayList", () -> new ArrayList<>(withNull("a")), "x.1: must not be null"),
        new Holed("identity Collection", () -> withNull("a"), "x.1: must not be null"),
        new Holed(
            "identity LinkedHashMap",
            () -> new LinkedHashMap<>(nullUnder("k")),
            "x.k: must not be null"),
        new Holed(
            "identity Optional of a List",
            () -> Optional.of(withNull("a")),
            "x.1: must not be null"),
        new Holed(
            "Optional bridge onto an identity List", () -> withNull("a"), "x.1: must not be null"),
        new Holed(
            "Optional bridge onto a wildcard List", () -> withNull("a"), "x.1: must not be null"),
        new Holed("Optional bridge onto a raw List", () -> withNull("a"), "x.1: must not be null"),
        new Holed(
            "Optional bridge onto a nested List",
            () -> List.of(withNull("a")),
            "x.0.1: must not be null"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("holed")
  @DisplayName(
      "a null anywhere inside an identity container locates at its full path, on every shape that"
          + " reads it")
  void aNullInsideLocatesAtItsFullPath(Holed h) throws ReflectiveOperationException {
    Case c = caseNamed(h.caseName());
    Object domain = domain(c, c.value().apply(tagFactory(c), "a"));
    Object beanFull = invoke(impl(c, "BeanFullMapping"), "build", domain);
    invoke(beanFull, "setX", h.value().get());
    Object beanProjection = invoke(impl(c, "BeanProjectionMapping"), "build", domain);
    invoke(beanProjection, "setX", h.value().get());

    assertThatValidated(
            parse(impl(c, "RecordFullMapping"), recordWire(c, "RecordFull", 7, h.value().get())))
        .as("%s: record parse", c)
        .isInvalid()
        .hasFieldErrors(h.expected());
    assertThatValidated(parse(impl(c, "BeanFullMapping"), beanFull))
        .as("%s: bean parse", c)
        .isInvalid()
        .hasFieldErrors(h.expected());
    // A total record projection copies by lens and reads nothing (see
    // unsetComponentIsLocatedOrEmpty).
    if (!c.recordTotal()) {
      assertThatValidated(
              validated(
                  invoke(
                      impl(c, "RecordProjectionMapping"),
                      "patch",
                      domain,
                      recordWire(c, "RecordProjection", h.value().get()))))
          .as("%s: record patch", c)
          .isInvalid()
          .hasFieldErrors(h.expected());
    }
    assertThatValidated(
            validated(invoke(impl(c, "BeanProjectionMapping"), "patch", domain, beanProjection)))
        .as("%s: bean patch", c)
        .isInvalid()
        .hasFieldErrors(h.expected());
  }

  private static Object component(Object record, String name) throws ReflectiveOperationException {
    return record.getClass().getMethod(name).invoke(record);
  }

  private static Case caseNamed(String name) {
    return cases()
        .filter(c -> c.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no case named: " + name));
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

  /**
   * The rows whose domain compares by {@code equals}: all but the one holding an array, which a
   * record compares by reference while every identity leg hands over a clone of it.
   */
  static Stream<Case> casesComparedByEquals() {
    return cases().filter(c -> !c.domainType().endsWith("[]"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("casesComparedByEquals")
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

  @Test
  @DisplayName(
      "an array round-trips elementwise on every write-back: each leg hands over a clone, which"
          + " the record's own equals, comparing an array by reference, does not see as equal")
  void anArrayRoundTripsElementwise() throws ReflectiveOperationException {
    Case c = caseNamed("identity array of arrays");
    Object domain = domain(c, c.value().apply(tagFactory(c), "a"));
    for (String spec : List.of("RecordFullMapping", "BeanFullMapping")) {
      Object impl = impl(c, spec);
      Validated<NonEmptyList<FieldError>, Object> parsed =
          parse(impl, invoke(impl, "build", domain));
      assertThatValidated(parsed).as("%s: parse(build(d))", spec).isValid();
      Assertions.assertThat(parsed.get())
          .as("%s: parse(build(d))", spec)
          .isNotEqualTo(domain)
          .usingRecursiveComparison()
          .isEqualTo(domain);
    }
    Iso<Object, Object> iso = optic(invoke(impl(c, "RecordFullMapping"), "asIso"));
    Assertions.assertThat(iso.reverseGet(iso.get(domain)))
        .as("reverseGet(get(d))")
        .usingRecursiveComparison()
        .isEqualTo(domain);
    Lens<Object, Object> lens = optic(invoke(impl(c, "RecordProjectionMapping"), "asLens"));
    Assertions.assertThat(lens.set(lens.get(domain), domain))
        .as("set(get(d), d)")
        .usingRecursiveComparison()
        .isEqualTo(domain);
    Object bean = impl(c, "BeanProjectionMapping");
    Validated<NonEmptyList<FieldError>, Object> patched =
        validated(invoke(bean, "patch", domain, invoke(bean, "build", domain)));
    assertThatValidated(patched).as("patch(d, build(d))").isValid();
    Assertions.assertThat(patched.get())
        .as("patch(d, build(d))")
        .usingRecursiveComparison()
        .isEqualTo(domain);
  }

  static Stream<Case> bridgedCases() {
    return cases().filter(Case::bridged);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bridgedCases")
  @DisplayName(
      "an empty bridged Optional round-trips on every shape, over a bean whose field starts out"
          + " holding a value")
  void emptyBridgeRoundTrips(Case c) throws ReflectiveOperationException {
    Object empty = domain(c, Optional.empty());
    for (String spec : List.of("RecordFullMapping", "BeanFullMapping")) {
      Object impl = impl(c, spec);
      assertThatValidated(parse(impl, invoke(impl, "build", empty)))
          .as("%s: %s parse(build(d))", c, spec)
          .isValid()
          .hasValue(empty);
    }
    for (String spec : List.of("RecordProjectionMapping", "BeanProjectionMapping")) {
      Object impl = impl(c, spec);
      MappingLaws.assertPatchIdentity(
          (d, w) -> validated(invoke(impl, "patch", d, w)), d -> invoke(impl, "build", d), empty);
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
