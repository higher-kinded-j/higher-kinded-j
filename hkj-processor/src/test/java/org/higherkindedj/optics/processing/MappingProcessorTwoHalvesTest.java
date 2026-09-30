// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectoryWithout;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The two-halves tier inferred: a mapping that builds and parses, nesting or dispatching to a spec
 * with two halves (one reading a {@code @ReadOnly} property), takes two halves too. Its {@code
 * parse} nests through that spec's parse half and its {@code build} through its build half, its
 * Impl carries {@code asValidatedParse()} and {@code asValidatedBuild()} and no {@code
 * asValidatedPrism()}, a note says so, and the tier carries on outwards. A site needing a whole
 * prism refuses such a spec, naming the read-only property it inherits.
 *
 * <p>Everything compiles under {@code -Xlint:unchecked,rawtypes -Werror}.
 */
@DisplayName("MappingProcessor - the two-halves tier, taken from what a mapping nests")
class MappingProcessorTwoHalvesTest {

  private static final String PKG = "com.example.halves";

  private static final String IMPORTS =
      """
      import java.util.List;
      import java.util.Map;
      import java.util.Optional;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.annotations.Flatten;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MapKey;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.OptionalBridge;
      import org.higherkindedj.optics.annotations.ReadOnly;
      import org.higherkindedj.optics.annotations.UpdateSpec;
      import org.higherkindedj.optics.validated.ValidatedPrism;

      """;

  private static final JavaFileObject PET =
      source("Pet", "public record Pet(Long id, String name) {}");

  /** openapi-generator's shape for a readOnly id: a getter, no setter, set by its constructor. */
  private static final JavaFileObject PET_MODEL =
      source(
          "PetModel",
          """
          public class PetModel {
            private Long id;
            private String name;

            public PetModel() {}

            public PetModel(Long id, String name) {
              this.id = id;
              this.name = name;
            }

            public Long getId() { return id; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
          }
          """);

  private static final JavaFileObject PET_MAPPING =
      source(
          "PetMapping",
          """
          @GenerateMapping
          public interface PetMapping extends MappingSpec<Pet, PetModel> {
            @ReadOnly
            Long id();
          }
          """);

  /** The issue's component: an ordinary two-way bean holding a Pet and a list of them. */
  private static final JavaFileObject ORDER =
      source("Order", "public record Order(String ref, Pet pet, List<Pet> extras) {}");

  private static final JavaFileObject ORDER_MODEL =
      source(
          "OrderModel",
          """
          public class OrderModel {
            private String ref;
            private PetModel pet;
            private List<PetModel> extras;

            public String getRef() { return ref; }
            public void setRef(String ref) { this.ref = ref; }
            public PetModel getPet() { return pet; }
            public void setPet(PetModel pet) { this.pet = pet; }
            public List<PetModel> getExtras() { return extras; }
            public void setExtras(List<PetModel> extras) { this.extras = extras; }
          }
          """);

  private static final JavaFileObject ORDER_MAPPING =
      source(
          "OrderMapping",
          """
          @GenerateMapping
          public interface OrderMapping extends MappingSpec<Order, OrderModel> {}
          """);

  private static final String ORDER_NOTE =
      "'OrderMapping' maps as two halves: the generated Impl carries parse, build,"
          + " asValidatedParse() and asValidatedBuild(), and no asValidatedPrism(). It nests"
          + " 'PetMapping' at 'pet', which has a read-only property 'id': build leaves that property"
          + " out, so parse cannot read back what build wrote. Two halves come from 'PetMapping' at"
          + " 'extras' too. Law-check each direction with the MappingLaws overloads for one"
          + " direction. To keep asValidatedPrism() anyway, declare a leaf joining the nested halves"
          + " for each of 'pet' and 'extras', at the cost of a round trip that loses what the nested"
          + " build leaves out.";

  /** The issue's sealed hierarchy, with Pet and its model among the subtypes. */
  private static final List<JavaFileObject> ANIMALS =
      List.of(
          source("Animal", "public sealed interface Animal permits Pet, Stray {}"),
          source("Pet", "public record Pet(Long id, String name) implements Animal {}"),
          source("Stray", "public record Stray(String name) implements Animal {}"),
          source(
              "AnimalModel", "public sealed interface AnimalModel permits PetModel, StrayModel {}"),
          source(
              "PetModel",
              """
              public final class PetModel implements AnimalModel {
                private Long id;
                private String name;

                public PetModel() {}

                public PetModel(Long id, String name) {
                  this.id = id;
                  this.name = name;
                }

                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """),
          source("StrayModel", "public record StrayModel(String name) implements AnimalModel {}"),
          PET_MAPPING,
          source(
              "StrayMapping",
              """
              @GenerateMapping
              public interface StrayMapping extends MappingSpec<Stray, StrayModel> {}
              """),
          source(
              "AnimalMapping",
              """
              @GenerateMapping
              public interface AnimalMapping extends MappingSpec<Animal, AnimalModel> {}
              """));

  @TempDir Path tmp;

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + IMPORTS + body);
  }

  private static Compiler compiler(Path... classDirs) {
    return javac()
        .withProcessors(new MappingProcessor(), new MergeProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .withClasspath(classpathWith(classDirs));
  }

  private static Compilation compile(List<JavaFileObject> sources) {
    return compiler().compile(sources);
  }

  private static Compilation compile(JavaFileObject... sources) {
    return compile(List.of(sources));
  }

  private static RuntimeCompilationHelper.CompiledResult compiled(List<JavaFileObject> sources) {
    Compilation compilation = compile(sources);
    assertThat(compilation).succeededWithoutWarnings();
    return new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static RuntimeCompilationHelper.CompiledResult compiled(JavaFileObject... sources) {
    return compiled(List.of(sources));
  }

  private static Object create(
      RuntimeCompilationHelper.CompiledResult result, String simpleName, Object... args)
      throws ReflectiveOperationException {
    return Arrays.stream(result.loadClass(PKG + "." + simpleName).getDeclaredConstructors())
        .filter(constructor -> constructor.getParameterCount() == args.length)
        .findFirst()
        .orElseThrow()
        .newInstance(args);
  }

  private static Object impl(RuntimeCompilationHelper.CompiledResult result, String spec) {
    return result.instance(PKG + "." + spec + "Impl");
  }

  @SuppressWarnings("unchecked") // reflective call into the generated Impl
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object mapping, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(mapping, "parse", wire);
  }

  /** The public methods an Impl declares itself, by name. */
  private static List<String> declared(Object mapping) {
    return Arrays.stream(mapping.getClass().getDeclaredMethods())
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .map(Method::getName)
        .sorted()
        .toList();
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    Optional<JavaFileObject> file =
        compilation.generatedFile(
            StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    Assertions.assertThat(file).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static List<String> notes(Compilation compilation) {
    return compilation.notes().stream().map(note -> note.getMessage(null)).toList();
  }

  @Nested
  @DisplayName("A mapping that builds and parses, nesting a spec with two halves")
  class Nesting {

    @Test
    @DisplayName(
        "takes two halves itself: parse reads the read-only property, build leaves it out, and a"
            + " note says why")
    void takesTwoHalves() throws ReflectiveOperationException {
      Compilation compilation =
          compile(PET, PET_MODEL, PET_MAPPING, ORDER, ORDER_MODEL, ORDER_MAPPING);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).hadNoteContaining(ORDER_NOTE);
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);

      Object mapping = impl(result, "OrderMapping");
      Assertions.assertThat(declared(mapping))
          .containsExactly("asValidatedBuild", "asValidatedParse", "build", "parse");
      Object wire = create(result, "OrderModel");
      invoke(wire, "setRef", "o-1");
      invoke(wire, "setPet", create(result, "PetModel", 7L, "Rex"));
      invoke(wire, "setExtras", List.of(create(result, "PetModel", 8L, "Ada")));
      assertThatValidated(parse(mapping, wire))
          .isValid()
          .hasValue(
              create(
                  result,
                  "Order",
                  "o-1",
                  create(result, "Pet", 7L, "Rex"),
                  List.of(create(result, "Pet", 8L, "Ada"))));

      Object built =
          invoke(
              mapping,
              "build",
              create(
                  result,
                  "Order",
                  "o-1",
                  create(result, "Pet", 7L, "Rex"),
                  List.of(create(result, "Pet", 8L, "Ada"))));
      Assertions.assertThat(invoke(invoke(built, "getPet"), "getId")).isNull();
      Assertions.assertThat(invoke(invoke(built, "getPet"), "getName")).isEqualTo("Rex");
      assertThatValidated(parse(mapping, built))
          .hasFieldErrors("pet.id: must not be null", "extras.0.id: must not be null");
      Assertions.assertThat(generatedSource(compilation, PKG + ".OrderMappingImpl"))
          .contains(
              "Generated mapping for {@link OrderMapping}, which nests a mapping with two halves,"
                  + " {@code PetMapping}: total {@code build} and accumulating, located {@code"
                  + " parse}, each through the half it nests.");
    }

    @Test
    @DisplayName(
        "through every container, the Optional bridge and a map's key leaf, each through the half"
            + " its direction uses")
    void throughEveryContainer() throws ReflectiveOperationException {
      JavaFileObject tag = source("Tag", "public record Tag(Long id, String label) {}");
      JavaFileObject tagModel =
          source(
              "TagModel",
              """
              public class TagModel {
                private Long id;
                private String label;

                public TagModel() {}

                public TagModel(Long id, String label) {
                  this.id = id;
                  this.label = label;
                }

                public Long getId() { return id; }
                public String getLabel() { return label; }
                public void setLabel(String label) { this.label = label; }
              }
              """);
      JavaFileObject tagMapping =
          source(
              "TagMapping",
              """
              @GenerateMapping
              public interface TagMapping extends MappingSpec<Tag, TagModel> {
                @ReadOnly
                Long id();
              }
              """);
      JavaFileObject kennel =
          source(
              "Kennel",
              """
              public record Kennel(
                  Optional<Pet> spare,
                  Optional<Pet> lodger,
                  Pet[] cages,
                  Map<String, Pet> byName,
                  Map<Integer, Pet> byNumber,
                  List<Tag> tags) {}
              """);
      JavaFileObject kennelDto =
          source(
              "KennelDto",
              """
              public record KennelDto(
                  PetModel spare,
                  Optional<PetModel> lodger,
                  PetModel[] cages,
                  Map<String, PetModel> byName,
                  Map<String, PetModel> byNumber,
                  List<TagModel> tags) {}
              """);
      JavaFileObject spec =
          source(
              "KennelMapping",
              """
              @GenerateMapping
              public interface KennelMapping extends MappingSpec<Kennel, KennelDto> {
                @OptionalBridge
                Optional<Pet> spare();

                @MapKey("byNumber")
                default ValidatedPrism<String, Integer> byNumberKey() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(Integer.valueOf(raw)), String::valueOf);
                }
              }
              """);
      Compilation compilation =
          compile(PET, PET_MODEL, PET_MAPPING, tag, tagModel, tagMapping, kennel, kennelDto, spec);
      assertThat(compilation).succeededWithoutWarnings();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object mapping = impl(result, "KennelMapping");
      Assertions.assertThat(declared(mapping)).doesNotContain("asValidatedPrism");
      Assertions.assertThat(generatedSource(compilation, PKG + ".KennelMappingImpl"))
          .contains("which nests mappings with two halves, {@code PetMapping}, {@code TagMapping}");

      Object pet = create(result, "Pet", 7L, "Rex");
      Object petArray = java.lang.reflect.Array.newInstance(result.loadClass(PKG + ".Pet"), 1);
      java.lang.reflect.Array.set(petArray, 0, pet);
      Object domain =
          create(
              result,
              "Kennel",
              Optional.of(pet),
              Optional.of(pet),
              petArray,
              java.util.Map.of("rex", pet),
              java.util.Map.of(1, pet),
              List.of(create(result, "Tag", 3L, "vip")));
      Object built = invoke(mapping, "build", domain);
      Assertions.assertThat(invoke(invoke(built, "spare"), "getId")).isNull();
      assertThatValidated(parse(mapping, built))
          .hasFieldErrors(
              "spare.id: must not be null",
              "lodger.id: must not be null",
              "cages.0.id: must not be null",
              "byName.rex.id: must not be null",
              "byNumber.1.id: must not be null",
              "tags.0.id: must not be null");
    }

    @Test
    @DisplayName("carries the tier outwards, a chain of three naming each spec on the way")
    void chainOfThree() {
      JavaFileObject customer =
          source("Customer", "public record Customer(String name, List<Order> orders) {}");
      JavaFileObject customerDto =
          source(
              "CustomerDto", "public record CustomerDto(String name, List<OrderModel> orders) {}");
      JavaFileObject spec =
          source(
              "CustomerMapping",
              """
              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
              """);
      var result =
          compiled(
              PET,
              PET_MODEL,
              PET_MAPPING,
              ORDER,
              ORDER_MODEL,
              ORDER_MAPPING,
              customer,
              customerDto,
              spec);
      Assertions.assertThat(declared(impl(result, "CustomerMapping")))
          .containsExactly("asValidatedBuild", "asValidatedParse", "build", "parse");
      Assertions.assertThat(notes(result.compilation()))
          .anyMatch(
              note ->
                  note.contains(
                      "It nests 'OrderMapping' at 'orders', which nests 'PetMapping' at 'pet',"
                          + " which has a read-only property 'id'"));
    }

    @Test
    @DisplayName("gives the reason its registration took, which a refusal nesting it names too")
    void theNoteAndARefusalAgree() {
      // Declared before the Aisle spec it nests, the Lean spec takes its halves from whichever of
      // the two it nests has them first; its note and the refusal below name the same one.
      JavaFileObject leanMapping =
          source(
              "LeanMapping",
              """
              @GenerateMapping
              public interface LeanMapping extends MappingSpec<Lean, LeanDto> {}
              """);
      JavaFileObject lean = source("Lean", "public record Lean(Aisle aisle, Pet pet) {}");
      JavaFileObject leanDto =
          source("LeanDto", "public record LeanDto(AisleDto aisle, PetModel pet) {}");
      JavaFileObject aisle = source("Aisle", "public record Aisle(Pet pet) {}");
      JavaFileObject aisleDto = source("AisleDto", "public record AisleDto(PetModel pet) {}");
      JavaFileObject aisleMapping =
          source(
              "AisleMapping",
              """
              @GenerateMapping
              public interface AisleMapping extends MappingSpec<Aisle, AisleDto> {}
              """);
      JavaFileObject shelf = source("Shelf", "public record Shelf(Lean lean, String secret) {}");
      JavaFileObject shelfView = source("ShelfView", "public record ShelfView(LeanDto lean) {}");
      JavaFileObject shelfMapping =
          source(
              "ShelfMapping",
              """
              @GenerateMapping
              public interface ShelfMapping extends MappingSpec<Shelf, ShelfView> {}
              """);
      Compilation compilation =
          compile(
              leanMapping,
              PET,
              PET_MODEL,
              PET_MAPPING,
              lean,
              leanDto,
              aisle,
              aisleDto,
              aisleMapping,
              shelf,
              shelfView,
              shelfMapping);
      assertThat(compilation).failed();
      String note =
          notes(compilation).stream()
              .filter(message -> message.contains("'LeanMapping' maps as two halves"))
              .findFirst()
              .orElseThrow();
      String reason = note.substring(note.indexOf(" It ") + 4, note.indexOf(": build leaves"));
      assertThat(compilation)
          .hadErrorContaining(
              "'LeanMapping' maps this pair but " + reason + " (no asValidatedPrism)");
    }

    @Test
    @DisplayName("names every read-only property it inherits")
    void namesEveryReadOnlyProperty() {
      JavaFileObject horse =
          source("Horse", "public record Horse(Long id, Long age, String name) {}");
      JavaFileObject horseModel =
          source(
              "HorseModel",
              """
              public class HorseModel {
                private Long id;
                private Long age;
                private String name;

                public Long getId() { return id; }
                public Long getAge() { return age; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject horseMapping =
          source(
              "HorseMapping",
              """
              @GenerateMapping
              public interface HorseMapping extends MappingSpec<Horse, HorseModel> {
                @ReadOnly
                Long id();

                @ReadOnly
                Long age();
              }
              """);
      JavaFileObject stable = source("Stable", "public record Stable(Horse horse) {}");
      JavaFileObject stableDto =
          source("StableDto", "public record StableDto(HorseModel horse) {}");
      JavaFileObject spec =
          source(
              "StableMapping",
              """
              @GenerateMapping
              public interface StableMapping extends MappingSpec<Stable, StableDto> {}
              """);
      Compilation compilation = compile(horse, horseModel, horseMapping, stable, stableDto, spec);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "It nests 'HorseMapping' at 'horse', which has read-only properties [id, age]: build"
                  + " leaves those properties out, so parse cannot read back what build wrote.");
    }

    @Test
    @DisplayName("from a flattened component, named by its path through the group")
    void fromAFlattenedComponent() {
      JavaFileObject visit = source("Visit", "public record Visit(String ref, Stay stay) {}");
      JavaFileObject stay = source("Stay", "public record Stay(Pet pet, String room) {}");
      JavaFileObject visitDto =
          source("VisitDto", "public record VisitDto(String ref, PetModel pet, String room) {}");
      JavaFileObject spec =
          source(
              "VisitMapping",
              """
              @GenerateMapping
              public interface VisitMapping extends MappingSpec<Visit, VisitDto> {
                @Flatten
                Stay stay();
              }
              """);
      var result = compiled(PET, PET_MODEL, PET_MAPPING, visit, stay, visitDto, spec);
      Assertions.assertThat(declared(impl(result, "VisitMapping")))
          .doesNotContain("asValidatedPrism");
      Assertions.assertThat(notes(result.compilation()))
          .anyMatch(note -> note.contains("It nests 'PetMapping' at 'stay.pet'"));
    }

    @Test
    @DisplayName("keeps its whole prism where a leaf joins the halves, and draws no note")
    void aLeafKeepsTheWholePrism() {
      JavaFileObject owner = source("Owner", "public record Owner(Pet pet) {}");
      JavaFileObject ownerDto = source("OwnerDto", "public record OwnerDto(PetModel pet) {}");
      JavaFileObject spec =
          source(
              "OwnerMapping",
              """
              @GenerateMapping
              public interface OwnerMapping extends MappingSpec<Owner, OwnerDto> {
                default ValidatedPrism<PetModel, Pet> pet() {
                  return ValidatedPrism.of(PetMappingImpl.INSTANCE::parse, PetMappingImpl.INSTANCE::build);
                }
              }
              """);
      var result = compiled(PET, PET_MODEL, PET_MAPPING, owner, ownerDto, spec);
      Assertions.assertThat(declared(impl(result, "OwnerMapping"))).contains("asValidatedPrism");
      Assertions.assertThat(notes(result.compilation()))
          .noneMatch(note -> note.contains("two halves"));
    }

    @Test
    @DisplayName(
        "with a read-only property of its own, nesting itself, has two halves whatever it nests,"
            + " draws no note, and names only the other spec it nests")
    void aReadOnlyPropertyOfItsOwn() {
      JavaFileObject node =
          source("Node", "public record Node(Long id, String label, List<Node> kids, Pet pet) {}");
      JavaFileObject nodeModel =
          source(
              "NodeModel",
              """
              public class NodeModel {
                private Long id;
                private String label;
                private List<NodeModel> kids;
                private PetModel pet;

                public Long getId() { return id; }
                public String getLabel() { return label; }
                public void setLabel(String label) { this.label = label; }
                public List<NodeModel> getKids() { return kids; }
                public void setKids(List<NodeModel> kids) { this.kids = kids; }
                public PetModel getPet() { return pet; }
                public void setPet(PetModel pet) { this.pet = pet; }
              }
              """);
      JavaFileObject spec =
          source(
              "NodeMapping",
              """
              @GenerateMapping
              public interface NodeMapping extends MappingSpec<Node, NodeModel> {
                @ReadOnly
                Long id();
              }
              """);
      Compilation compilation = compile(PET, PET_MODEL, PET_MAPPING, node, nodeModel, spec);
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(notes(compilation)).noneMatch(note -> note.contains("two halves"));
      Assertions.assertThat(generatedSource(compilation, PKG + ".NodeMappingImpl"))
          .contains("NodeMappingImpl.INSTANCE.asValidatedBuild().buildAll(domain.kids())")
          .contains(
              "which reads it; it nests a mapping with two halves, {@code PetMapping}, each"
                  + " direction through the half it nests.")
          .doesNotContain("{@code NodeMapping}");
    }

    @Test
    @DisplayName("round a cycle, names only where the halves come from, not what takes them back")
    void aCycleNamesWhereTheHalvesComeFrom() {
      // Ring nests Link, Link nests Knot, and Knot nests the Pet, where the halves come from, and
      // Ring back, which takes them from Knot by way of Link.
      JavaFileObject ring = source("Ring", "public record Ring(String name, Link link) {}");
      JavaFileObject ringDto =
          source("RingDto", "public record RingDto(String name, LinkDto link) {}");
      JavaFileObject link = source("Link", "public record Link(List<Knot> knots) {}");
      JavaFileObject linkDto = source("LinkDto", "public record LinkDto(List<KnotDto> knots) {}");
      JavaFileObject knot = source("Knot", "public record Knot(Pet pet, List<Ring> rings) {}");
      JavaFileObject knotDto =
          source("KnotDto", "public record KnotDto(PetModel pet, List<RingDto> rings) {}");
      JavaFileObject specs =
          source(
              "RingSpecs",
              """
              public interface RingSpecs {
                @GenerateMapping
                interface RingMapping extends MappingSpec<Ring, RingDto> {}

                @GenerateMapping
                interface LinkMapping extends MappingSpec<Link, LinkDto> {}

                @GenerateMapping
                interface KnotMapping extends MappingSpec<Knot, KnotDto> {}
              }
              """);
      Compilation compilation =
          compile(specs, ring, ringDto, link, linkDto, knot, knotDto, PET, PET_MODEL, PET_MAPPING);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "'KnotMapping' maps as two halves: the generated Impl carries parse, build,"
                  + " asValidatedParse() and asValidatedBuild(), and no asValidatedPrism(). It"
                  + " nests 'PetMapping' at 'pet', which has a read-only property 'id': build"
                  + " leaves that property out, so parse cannot read back what build wrote."
                  + " Law-check each direction with the MappingLaws overloads for one direction."
                  + " To keep asValidatedPrism() anyway, declare a leaf joining the nested halves"
                  + " for 'pet',");
      assertThat(compilation)
          .hadNoteContaining(
              "It nests 'LinkMapping' at 'link', which nests 'KnotMapping' at 'knots', which nests"
                  + " 'PetMapping' at 'pet', which has a read-only property 'id'");
      Assertions.assertThat(generatedSource(compilation, PKG + ".RingSpecsKnotMappingImpl"))
          .contains("which nests a mapping with two halves, {@code PetMapping}:");
    }

    @Test
    @DisplayName("nests in turn where a mapping only parses, only builds, patches or merges")
    void nestsOneWayInTurn() {
      JavaFileObject shop = source("Shop", "public record Shop(String name, Order order) {}");
      JavaFileObject view =
          source(
              "ShopView",
              """
              public class ShopView {
                private final OrderModel order;

                public ShopView(OrderModel order) { this.order = order; }

                public String getName() { return "shop"; }
                public OrderModel getOrder() { return order; }
              }
              """);
      JavaFileObject command =
          source(
              "ShopCommand",
              """
              public class ShopCommand {
                public void setName(String name) {}
                public void setOrder(OrderModel order) {}
              }
              """);
      JavaFileObject patch =
          source(
              "ShopPatch",
              """
              public class ShopPatch {
                private String name;
                private OrderModel order;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public OrderModel getOrder() { return order; }
                public void setOrder(OrderModel order) { this.order = order; }
              }
              """);
      JavaFileObject specs =
          source(
              "ShopSpecs",
              """
              public interface ShopSpecs {
                @GenerateMapping
                interface ShopViewMapping extends MappingSpec<Shop, ShopView> {}

                @GenerateMapping
                interface ShopCommandMapping extends MappingSpec<Shop, ShopCommand> {}

                @GenerateMapping
                interface ShopPatchMapping extends UpdateSpec<Shop, ShopPatch> {}

                record Name(String name) {}

                record Source(OrderModel order) {}

                @GenerateMerge
                interface Merger {
                  Validated<NonEmptyList<FieldError>, Shop> merge(Name name, Source source);
                }
              }
              """);
      Compilation compilation =
          compile(
              PET,
              PET_MODEL,
              PET_MAPPING,
              ORDER,
              ORDER_MODEL,
              ORDER_MAPPING,
              shop,
              view,
              command,
              patch,
              specs);
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(generatedSource(compilation, PKG + ".ShopSpecsShopViewMappingImpl"))
          .contains("OrderMappingImpl.INSTANCE.asValidatedParse()::parse");
      Assertions.assertThat(generatedSource(compilation, PKG + ".ShopSpecsShopCommandMappingImpl"))
          .contains("OrderMappingImpl.INSTANCE.asValidatedBuild().build(domain.order())");
      Assertions.assertThat(generatedSource(compilation, PKG + ".ShopSpecsShopPatchMappingImpl"))
          .contains("OrderMappingImpl.INSTANCE.asValidatedParse()::parse");
      Assertions.assertThat(generatedSource(compilation, PKG + ".ShopSpecsMergerImpl"))
          .contains("OrderMappingImpl.INSTANCE.asValidatedParse()::parse");
    }
  }

  @Nested
  @DisplayName("Sealed dispatch to a spec with two halves")
  class Dispatch {

    @Test
    @DisplayName("routes each direction through the subtype spec's half, and takes two halves")
    void dispatchesThroughHalves() throws ReflectiveOperationException {
      Compilation compilation = compile(ANIMALS);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "'AnimalMapping' maps as two halves: the generated Impl carries parse, build,"
                  + " asValidatedParse() and asValidatedBuild(), and no asValidatedPrism(). It"
                  + " dispatches 'com.example.halves.Pet' to 'PetMapping', which has a read-only"
                  + " property 'id': build leaves that property out, so parse cannot read back what"
                  + " build wrote. Law-check each direction with the MappingLaws overloads for one"
                  + " direction.");
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object mapping = impl(result, "AnimalMapping");
      Assertions.assertThat(declared(mapping))
          .containsExactly("asValidatedBuild", "asValidatedParse", "build", "parse");

      assertThatValidated(parse(mapping, create(result, "PetModel", 7L, "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", 7L, "Rex"));
      assertThatValidated(parse(mapping, create(result, "StrayModel", "Tom")))
          .isValid()
          .hasValue(create(result, "Stray", "Tom"));
      Object built = invoke(mapping, "build", create(result, "Pet", 7L, "Rex"));
      Assertions.assertThat(invoke(built, "getId")).isNull();
      Assertions.assertThat(invoke(built, "getName")).isEqualTo("Rex");
      Assertions.assertThat(invoke(mapping, "build", create(result, "Stray", "Tom")))
          .isEqualTo(create(result, "StrayModel", "Tom"));
    }

    @Test
    @DisplayName("names every subtype it takes two halves from")
    void namesEverySubtype() {
      Compilation compilation =
          compile(
              source("Beast", "public sealed interface Beast permits Hound, Tabby {}"),
              source("Hound", "public record Hound(Long id, String name) implements Beast {}"),
              source("Tabby", "public record Tabby(Long id, String name) implements Beast {}"),
              source(
                  "BeastModel",
                  "public sealed interface BeastModel permits HoundModel, TabbyModel {}"),
              source(
                  "HoundModel",
                  """
                  public final class HoundModel implements BeastModel {
                    private Long id;
                    private String name;

                    public Long getId() { return id; }
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                  }
                  """),
              source(
                  "TabbyModel",
                  """
                  public final class TabbyModel implements BeastModel {
                    private Long id;
                    private String name;

                    public Long getId() { return id; }
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                  }
                  """),
              source(
                  "BeastSpecs",
                  """
                  public interface BeastSpecs {
                    @GenerateMapping
                    interface HoundMapping extends MappingSpec<Hound, HoundModel> {
                      @ReadOnly
                      Long id();
                    }

                    @GenerateMapping
                    interface TabbyMapping extends MappingSpec<Tabby, TabbyModel> {
                      @ReadOnly
                      Long id();
                    }

                    @GenerateMapping
                    interface BeastMapping extends MappingSpec<Beast, BeastModel> {}
                  }
                  """));
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "It dispatches 'com.example.halves.Hound' to 'HoundMapping', which has a read-only"
                  + " property 'id': build leaves that property out, so parse cannot read back what"
                  + " build wrote. Two halves come from 'TabbyMapping' for"
                  + " 'com.example.halves.Tabby' too. Law-check each direction with the MappingLaws"
                  + " overloads for one direction.");
    }

    @Test
    @DisplayName("nested as a component of a mapping that builds and parses, gives it two halves")
    void asAComponent() {
      JavaFileObject zoo =
          source("Zoo", "public record Zoo(String name, Animal star, List<Animal> all) {}");
      JavaFileObject zooModel =
          source(
              "ZooModel",
              """
              public class ZooModel {
                private String name;
                private AnimalModel star;
                private List<AnimalModel> all;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public AnimalModel getStar() { return star; }
                public void setStar(AnimalModel star) { this.star = star; }
                public List<AnimalModel> getAll() { return all; }
                public void setAll(List<AnimalModel> all) { this.all = all; }
              }
              """);
      JavaFileObject spec =
          source(
              "ZooMapping",
              """
              @GenerateMapping
              public interface ZooMapping extends MappingSpec<Zoo, ZooModel> {}
              """);
      Compilation compilation =
          compile(Stream.concat(ANIMALS.stream(), Stream.of(zoo, zooModel, spec)).toList());
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "It nests 'AnimalMapping' at 'star', which dispatches 'com.example.halves.Pet' to"
                  + " 'PetMapping', which has a read-only property 'id'");
      Assertions.assertThat(generatedSource(compilation, PKG + ".ZooMappingImpl"))
          .contains("AnimalMappingImpl.INSTANCE.asValidatedBuild().buildAll(domain.all())")
          .doesNotContain("asValidatedPrism");
    }

    @Test
    @DisplayName(
        "to a subtype whose spec cannot take part names that spec, and offers no second one")
    void aSpecThatCannotTakePart() {
      Compilation compilation =
          compile(
              source("Animal", "public sealed interface Animal permits Pet, Stray {}"),
              source("Pet", "public record Pet(String name) implements Animal {}"),
              source("Stray", "public record Stray(String name) implements Animal {}"),
              source(
                  "AnimalModel",
                  "public sealed interface AnimalModel permits PetView, StrayModel {}"),
              source(
                  "PetView",
                  """
                  public final class PetView implements AnimalModel {
                    private final String name;

                    public PetView(String name) { this.name = name; }

                    public String getName() { return name; }
                  }
                  """),
              source(
                  "StrayModel", "public record StrayModel(String name) implements AnimalModel {}"),
              source(
                  "PetMapping",
                  """
                  @GenerateMapping
                  public interface PetMapping extends MappingSpec<Pet, PetView> {}
                  """),
              source(
                  "StrayMapping",
                  """
                  @GenerateMapping
                  public interface StrayMapping extends MappingSpec<Stray, StrayModel> {}
                  """),
              source(
                  "AnimalMapping",
                  """
                  @GenerateMapping
                  public interface AnimalMapping extends MappingSpec<Animal, AnimalModel> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'PetMapping' maps it but is parse-only (no build), so it cannot take part in"
                  + " dispatch. Make 'PetMapping' build and parse the whole pair, rather than"
                  + " declaring a second spec for 'com.example.halves.Pet', which would make the"
                  + " pair ambiguous; or drop 'AnimalMapping' and map the pair with a hand-written"
                  + " ValidatedPrism<AnimalModel, Animal> leaf wherever it nests.");
    }
  }

  @Nested
  @DisplayName("Where a whole prism is needed")
  class WholePrism {

    @Test
    @DisplayName("a projection refuses the outer, naming the read-only property it inherits")
    void aProjection() {
      JavaFileObject shop =
          source("Shop", "public record Shop(String name, Order order, String secret) {}");
      JavaFileObject view =
          source("ShopView", "public record ShopView(String name, OrderModel order) {}");
      JavaFileObject spec =
          source(
              "ShopMapping",
              """
              @GenerateMapping
              public interface ShopMapping extends MappingSpec<Shop, ShopView> {}
              """);
      Compilation compilation =
          compile(PET, PET_MODEL, PET_MAPPING, ORDER, ORDER_MODEL, ORDER_MAPPING, shop, view, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'OrderMapping' maps this pair but nests 'PetMapping' at 'pet', which has a"
                  + " read-only property 'id' (no asValidatedPrism), so it cannot be nested in a"
                  + " projection, which needs a whole prism for its write-back: its build leaves"
                  + " that property out, so its parse cannot read back what its build wrote.");
    }

    @Test
    @DisplayName(
        "an element-mapped spec's of(...) refuses it as an argument, and offers no second spec")
    void anElementArgument() {
      JavaFileObject page = source("Page", "public record Page<T>(List<T> items) {}");
      JavaFileObject pageDto = source("PageDto", "public record PageDto<D>(List<D> items) {}");
      JavaFileObject pageMapping =
          source(
              "PageMapping",
              """
              @GenerateMapping
              public interface PageMapping<T, D> extends MappingSpec<Page<T>, PageDto<D>> {
                ValidatedPrism<D, T> items();
              }
              """);
      JavaFileObject catalog = source("Catalog", "public record Catalog(Page<Pet> page) {}");
      JavaFileObject catalogDto =
          source("CatalogDto", "public record CatalogDto(PageDto<PetModel> page) {}");
      JavaFileObject spec =
          source(
              "CatalogMapping",
              """
              @GenerateMapping
              public interface CatalogMapping extends MappingSpec<Catalog, CatalogDto> {}
              """);
      Compilation compilation =
          compile(
              PET, PET_MODEL, PET_MAPPING, page, pageDto, pageMapping, catalog, catalogDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'PetMapping' maps this pair but has a read-only property 'id' (no"
                  + " asValidatedPrism), so it cannot be an of(...) argument of an element-mapped"
                  + " spec, which takes a whole ValidatedPrism");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'default ValidatedPrism<com.example.halves.PetModel,"
                  + " com.example.halves.Pet> page()' on this spec.");
    }

    @Test
    @DisplayName(
        "a spec whose whole-prism site was ambiguous until another took two halves is classified"
            + " again, and takes its tier")
    void reclassifiedOnceAnotherTakesHalves() {
      JavaFileObject leafOrderMapping =
          source(
              "WholeOrderMapping",
              """
              @GenerateMapping
              public interface WholeOrderMapping extends MappingSpec<Order, OrderModel> {
                default ValidatedPrism<PetModel, Pet> pet() {
                  return ValidatedPrism.of(PetMappingImpl.INSTANCE::parse, PetMappingImpl.INSTANCE::build);
                }

                default ValidatedPrism<PetModel, Pet> extras() {
                  return pet();
                }
              }
              """);
      JavaFileObject page = source("Page", "public record Page<T>(List<T> items) {}");
      JavaFileObject pageDto = source("PageDto", "public record PageDto<D>(List<D> items) {}");
      JavaFileObject pageMapping =
          source(
              "PageMapping",
              """
              @GenerateMapping
              public interface PageMapping<T, D> extends MappingSpec<Page<T>, PageDto<D>> {
                ValidatedPrism<D, T> items();
              }
              """);
      JavaFileObject catalog =
          source("Catalog", "public record Catalog(Page<Order> page, Pet featured) {}");
      JavaFileObject catalogDto =
          source(
              "CatalogDto",
              "public record CatalogDto(PageDto<OrderModel> page, PetModel featured) {}");
      JavaFileObject spec =
          source(
              "CatalogMapping",
              """
              @GenerateMapping
              public interface CatalogMapping extends MappingSpec<Catalog, CatalogDto> {}
              """);
      Compilation compilation =
          compile(
              PET,
              PET_MODEL,
              PET_MAPPING,
              ORDER,
              ORDER_MODEL,
              ORDER_MAPPING,
              leafOrderMapping,
              page,
              pageDto,
              pageMapping,
              catalog,
              catalogDto,
              spec);
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(generatedSource(compilation, PKG + ".CatalogMappingImpl"))
          .contains("PageMappingImpl.of(WholeOrderMappingImpl.INSTANCE.asValidatedPrism())")
          .contains("PetMappingImpl.INSTANCE.asValidatedBuild().build(domain.featured())")
          .doesNotContain("public ValidatedPrism<CatalogDto, Catalog> asValidatedPrism()");
    }
  }

  @Nested
  @DisplayName("From a dependency's class files, through the index")
  class Dependency {

    private static final String UP = "com.halves.up";

    private final JavaFileObject upstream =
        JavaFileObjects.forSourceString(
            UP + ".Up",
            """
            package com.halves.up;

            import java.util.List;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.ReadOnly;

            public final class Up {
              public record Pet(Long id, String name) {}

              public static class PetModel {
                private Long id;
                private String name;
                public PetModel() {}
                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }

              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                Long id();
              }

              public record Order(String ref, Pet pet) {}
              public record OrderDto(String ref, PetModel pet) {}

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderDto> {}

              public record Customer(String name, List<Order> orders) {}
              public record CustomerDto(String name, List<OrderDto> orders) {}

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}

              public record Tag(String label) {}
              public record TagDto(String label) {}

              @GenerateMapping
              public interface TagMapping extends MappingSpec<Tag, TagDto> {}
            }
            """);

    private final JavaFileObject twoWay =
        JavaFileObjects.forSourceString(
            "com.halves.down.Accounts",
            """
            package com.halves.down;

            import com.halves.up.Up;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            public final class Accounts {
              public record Account(String id, Up.Customer customer) {}
              public record AccountDto(String id, Up.CustomerDto customer) {}

              @GenerateMapping
              public interface AccountMapping extends MappingSpec<Account, AccountDto> {}
            }
            """);

    private final JavaFileObject projection =
        JavaFileObjects.forSourceString(
            "com.halves.down.Ledgers",
            """
            package com.halves.down;

            import com.halves.up.Up;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            public final class Ledgers {
              public record Ledger(Up.Customer customer, String secret) {}
              public record LedgerView(Up.CustomerDto customer) {}

              @GenerateMapping
              public interface LedgerMapping extends MappingSpec<Ledger, LedgerView> {}
            }
            """);

    /**
     * A dependency's sealed hierarchy, whose Pet spec is refused dispatch once its Impl is lost.
     */
    private final JavaFileObject zoo =
        JavaFileObjects.forSourceString(
            "com.halves.zoo.Zoo",
            """
            package com.halves.zoo;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            public final class Zoo {
              public sealed interface Animal permits Pet, Stray {}
              public record Pet(String name) implements Animal {}
              public record Stray(String name) implements Animal {}

              public sealed interface AnimalDto permits PetDto, StrayDto {}
              public record PetDto(String name) implements AnimalDto {}
              public record StrayDto(String name) implements AnimalDto {}

              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetDto> {}

              @GenerateMapping
              public interface StrayMapping extends MappingSpec<Stray, StrayDto> {}
            }
            """);

    private Path zooWithoutPetImpl() throws IOException {
      Compilation compilation = compiler().compile(zoo);
      assertThat(compilation).succeeded();
      return classDirectoryWithout(
          compilation, tmp.resolve("zoo"), "com.halves.zoo.ZooPetMappingImpl");
    }

    private Path upstream(String dir, String... removed) throws IOException {
      Compilation compilation = compiler().compile(upstream);
      assertThat(compilation).succeeded();
      return removed.length == 0
          ? classDirectory(compilation, tmp.resolve(dir))
          : classDirectoryWithout(compilation, tmp.resolve(dir), removed);
    }

    @Test
    @DisplayName(
        "a two-way site takes two halves from a dependency's inferred spec, and a projection is"
            + " refused, naming the chain it inherits")
    void throughTheIndex() throws IOException {
      Path up = upstream("up");
      Compilation compilation = compiler(up).compile(twoWay);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "'AccountMapping' maps as two halves: the generated Impl carries parse, build,"
                  + " asValidatedParse() and asValidatedBuild(), and no asValidatedPrism(). It"
                  + " nests 'com.halves.up.Up.CustomerMapping (classpath)' at 'customer', which"
                  + " nests 'com.halves.up.Up.OrderMapping (classpath)' at 'orders', which nests"
                  + " 'com.halves.up.Up.PetMapping (classpath)' at 'pet', which has a read-only"
                  + " property 'id'");
      Assertions.assertThat(
              generatedSource(compilation, "com.halves.down.AccountsAccountMappingImpl"))
          .contains("UpCustomerMappingImpl.INSTANCE.asValidatedBuild().build(domain.customer())")
          .contains("UpCustomerMappingImpl.INSTANCE.asValidatedParse()::parse");

      Compilation refused = compiler(up).compile(projection);
      assertThat(refused).failed();
      assertThat(refused)
          .hadErrorContaining(
              "'com.halves.up.Up.CustomerMapping (classpath)' maps this pair but nests"
                  + " 'com.halves.up.Up.OrderMapping (classpath)' at 'orders', which nests"
                  + " 'com.halves.up.Up.PetMapping (classpath)' at 'pet', which has a read-only"
                  + " property 'id' (no asValidatedPrism), so it cannot be nested in a projection");
    }

    @Test
    @DisplayName(
        "a dependency's spec whose own classification cannot get through here still has two"
            + " halves, without a reason to name")
    void withoutAReason() throws IOException {
      // Without the Pet spec's index entry, the dependency's Order spec cannot be classified here,
      // so only its Impl says it has two halves, and the Customer spec nesting it has no reason
      // to take from it.
      Compilation compilation =
          compiler(
                  upstream(
                      "partial", "org.higherkindedj.mapping.index.com$halves$up$Up$PetMapping"))
              .compile(projection);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'com.halves.up.Up.CustomerMapping (classpath)' maps this pair but nests or"
                  + " dispatches to a mapping with a read-only property (no asValidatedPrism), so it"
                  + " cannot be nested in a projection, which needs a whole prism for its"
                  + " write-back: its build leaves that property out");
    }

    @Test
    @DisplayName(
        "a sealed subtype whose dependency's spec cannot take part is offered a spec of this"
            + " compilation, which takes precedence")
    void aDependencySpecThatCannotTakePart() throws IOException {
      Path zooDir = zooWithoutPetImpl();
      String dispatch =
          """
          package com.halves.down;

          import com.halves.zoo.Zoo;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          public final class Park {
            @GenerateMapping
            public interface AnimalMapping extends MappingSpec<Zoo.Animal, Zoo.AnimalDto> {}
          %s}
          """;
      Compilation refused =
          compiler(zooDir)
              .compile(
                  JavaFileObjects.forSourceString("com.halves.down.Park", dispatch.formatted("")));
      assertThat(refused).failed();
      assertThat(refused)
          .hadErrorContaining(
              "Declare a @GenerateMapping spec for 'com.halves.zoo.Zoo.Pet' in this compilation"
                  + " that builds and parses the whole pair, which takes precedence over the"
                  + " dependency's; or drop 'AnimalMapping' and map the pair with a hand-written"
                  + " ValidatedPrism<AnimalDto, Animal> leaf wherever it nests.");

      Compilation followed =
          compiler(zooDir)
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.halves.down.Park",
                      dispatch.formatted(
                          """
                            @GenerateMapping
                            public interface PetMapping extends MappingSpec<Zoo.Pet, Zoo.PetDto> {}
                          """)));
      assertThat(followed).succeeded();
    }

    @Test
    @DisplayName(
        "an element pair whose dependency's spec cannot serve is still offered a spec of this"
            + " compilation")
    void anElementPairWithADependencySpec() throws IOException {
      Compilation compilation =
          compiler(zooWithoutPetImpl())
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.halves.down.Shelf",
                      """
                      package com.halves.down;

                      import com.halves.zoo.Zoo;
                      import java.util.List;
                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.MappingSpec;
                      import org.higherkindedj.optics.validated.ValidatedPrism;

                      public final class Shelf {
                        public record Page<T>(List<T> items) {}
                        public record PageDto<D>(List<D> items) {}

                        @GenerateMapping
                        public interface PageMapping<T, D>
                            extends MappingSpec<Page<T>, PageDto<D>> {
                          ValidatedPrism<D, T> items();
                        }

                        public record Catalog(Page<Zoo.Pet> page) {}
                        public record CatalogDto(PageDto<Zoo.PetDto> page) {}

                        @GenerateMapping
                        public interface CatalogMapping extends MappingSpec<Catalog, CatalogDto> {}
                      }
                      """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "or map com.halves.zoo.Zoo.PetDto to com.halves.zoo.Zoo.Pet with its own"
                  + " @GenerateMapping spec.");
    }
  }
}
