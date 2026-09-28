// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Read-only properties: a getter a two-way bean declares with no setter, which a {@code @ReadOnly}
 * marker reads on {@code parse} and {@code build} leaves unwritten. The Impl then carries both
 * directions, each as its own half, and no whole prism, so it nests only where one direction is
 * used. A marker the spec declares that reads nothing is refused; an inherited one binds where it
 * can and is otherwise inert.
 *
 * <p>Everything compiles under {@code -Xlint:unchecked,rawtypes -Werror}.
 */
@DisplayName("MappingProcessor - read-only bean properties")
class MappingProcessorReadOnlyTest {

  private static final String PKG = "com.example.readonly";

  private static final String IMPORTS =
      """
      import java.util.List;
      import java.util.Optional;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.Getter;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MapField;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.ReadOnly;
      import org.higherkindedj.optics.annotations.Unmapped;
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

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + IMPORTS + body);
  }

  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withProcessors(new MappingProcessor(), new MergeProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(sources);
  }

  private static RuntimeCompilationHelper.CompiledResult compiled(JavaFileObject... sources) {
    Compilation compilation = compile(sources);
    assertThat(compilation).succeededWithoutWarnings();
    return new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  /** Instantiates a compiled class through its constructor taking as many arguments as given. */
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

  @Nested
  @DisplayName("A read-only property")
  class Reads {

    @Test
    @DisplayName(
        "is read by parse and left unwritten by build, and the Impl carries each half and no prism")
    void parseReadsItAndBuildLeavesItOut() throws ReflectiveOperationException {
      var result = compiled(PET, PET_MODEL, PET_MAPPING);
      Object mapping = impl(result, "PetMapping");

      assertThatValidated(parse(mapping, create(result, "PetModel", 7L, "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", 7L, "Rex"));
      Object built = invoke(mapping, "build", create(result, "Pet", 7L, "Rex"));
      Assertions.assertThat(invoke(built, "getId")).isNull();
      Assertions.assertThat(invoke(built, "getName")).isEqualTo("Rex");
      // parse cannot read a built wire back whole: the component build never wrote is absent.
      assertThatValidated(parse(mapping, built)).isInvalid().hasFieldErrors("id: must not be null");

      Assertions.assertThat(declared(mapping))
          .containsExactly("asValidatedBuild", "asValidatedParse", "build", "id", "parse");
      Assertions.assertThat(invoke(invoke(mapping, "asValidatedParse"), "parse", built))
          .isEqualTo(parse(mapping, built));
      Assertions.assertThat(
              invoke(
                  invoke(
                      invoke(mapping, "asValidatedBuild"),
                      "build",
                      create(result, "Pet", 1L, "Ada")),
                  "getName"))
          .isEqualTo("Ada");
    }

    @Test
    @DisplayName("stubs its bare marker, which throws when called")
    void theMarkerIsStubbed() {
      var result = compiled(PET, PET_MODEL, PET_MAPPING);
      Assertions.assertThatThrownBy(() -> invoke(impl(result, "PetMapping"), "id"))
          .hasRootCauseInstanceOf(UnsupportedOperationException.class)
          .rootCause()
          .hasMessage(
              "@ReadOnly markers declare properties the mapping only reads and are not invocable");
    }

    @Test
    @DisplayName("converts through the component's leaf, which carries the marker")
    void theMarkerSitsOnTheLeaf() throws ReflectiveOperationException {
      JavaFileObject petId = source("PetId", "public record PetId(String value) {}");
      JavaFileObject pet = source("Pet", "public record Pet(PetId id, String name) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private String id;
                private String name;

                public PetModel() {}

                public PetModel(String id, String name) {
                  this.id = id;
                  this.name = name;
                }

                public String getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                default ValidatedPrism<String, PetId> id() {
                  return ValidatedPrism.of(
                      raw -> raw.isBlank()
                          ? Validated.invalidNel(FieldError.of("blank"))
                          : Validated.validNel(new PetId(raw)),
                      PetId::value);
                }
              }
              """);
      var result = compiled(petId, pet, model, spec);
      Object mapping = impl(result, "PetMapping");

      assertThatValidated(parse(mapping, create(result, "PetModel", "p-1", "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", create(result, "PetId", "p-1"), "Rex"));
      assertThatValidated(parse(mapping, create(result, "PetModel", " ", "Rex")))
          .isInvalid()
          .hasFieldErrors("id: blank");
      Object built =
          invoke(mapping, "build", create(result, "Pet", create(result, "PetId", "p-1"), "Rex"));
      Assertions.assertThat(invoke(built, "getId")).isNull();
    }

    @Test
    @DisplayName("is named after the bean's property when a rename maps the component to it")
    void renamedComponent() throws ReflectiveOperationException {
      JavaFileObject pet = source("Pet", "public record Pet(Long petId, String name) {}");
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @MapField(to = "id")
                Long petId();

                @ReadOnly
                Long id();
              }
              """);
      var result = compiled(pet, PET_MODEL, spec);

      assertThatValidated(parse(impl(result, "PetMapping"), create(result, "PetModel", 3L, "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", 3L, "Rex"));
    }

    @Test
    @DisplayName("is left out of a builder's writes, as of a setter bean's")
    void builderBean() throws ReflectiveOperationException {
      JavaFileObject model =
          source(
              "PetModel",
              """
              public final class PetModel {
                private final Long id;
                private final String name;

                public PetModel(Long id, String name) {
                  this.id = id;
                  this.name = name;
                }

                public Long getId() { return id; }
                public String getName() { return name; }

                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private String name;

                  public Builder name(String name) {
                    this.name = name;
                    return this;
                  }

                  public PetModel build() { return new PetModel(null, name); }
                }
              }
              """);
      var result = compiled(PET, model, PET_MAPPING);
      Object mapping = impl(result, "PetMapping");

      Object built = invoke(mapping, "build", create(result, "Pet", 7L, "Rex"));
      Assertions.assertThat(invoke(built, "getName")).isEqualTo("Rex");
      assertThatValidated(parse(mapping, create(result, "PetModel", 7L, "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", 7L, "Rex"));
    }

    @Test
    @DisplayName("bridges a domain Optional, reading an unset property as empty")
    void optionalBridge() throws ReflectiveOperationException {
      JavaFileObject pet = source("Pet", "public record Pet(Optional<Long> id, String name) {}");
      var result = compiled(pet, PET_MODEL, PET_MAPPING);
      Object mapping = impl(result, "PetMapping");

      Object built = invoke(mapping, "build", create(result, "Pet", Optional.of(7L), "Rex"));
      assertThatValidated(parse(mapping, built))
          .isValid()
          .hasValue(create(result, "Pet", Optional.empty(), "Rex"));
      assertThatValidated(parse(mapping, create(result, "PetModel", 7L, "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", Optional.of(7L), "Rex"));
    }

    @Test
    @DisplayName("sits beside a derived field, which build still fills")
    void besideDerivedField() throws ReflectiveOperationException {
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;
                private String label;

                public PetModel() {}

                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getLabel() { return label; }
                public void setLabel(String label) { this.label = label; }
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                Long id();

                default Getter<Pet, String> label() {
                  return Getter.of(pet -> pet.name().toUpperCase());
                }
              }
              """);
      var result = compiled(PET, model, spec);

      Object built = invoke(impl(result, "PetMapping"), "build", create(result, "Pet", 7L, "Rex"));
      Assertions.assertThat(invoke(built, "getLabel")).isEqualTo("REX");
      Assertions.assertThat(invoke(built, "getId")).isNull();
    }

    @Test
    @DisplayName(
        "may be a getter-only List, which build then leaves alone rather than filling it through"
            + " its getter")
    void getterOnlyList() throws ReflectiveOperationException {
      JavaFileObject pet = source("Pet", "public record Pet(List<String> tags, String name) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private List<String> tags;
                private String name;

                public PetModel() {}

                public PetModel(List<String> tags, String name) {
                  this.tags = tags;
                  this.name = name;
                }

                public List<String> getTags() { return tags; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                List<String> tags();
              }
              """);
      var result = compiled(pet, model, spec);
      Object mapping = impl(result, "PetMapping");

      // The getter answers null, so a build writing through it would throw.
      Object built = invoke(mapping, "build", create(result, "Pet", List.of("a"), "Rex"));
      Assertions.assertThat(invoke(built, "getTags")).isNull();
      List<String> tags = new java.util.ArrayList<>(List.of("a"));
      Validated<NonEmptyList<FieldError>, Object> parsed =
          parse(mapping, create(result, "PetModel", tags, "Rex"));
      assertThatValidated(parsed).isValid().hasValue(create(result, "Pet", List.of("a"), "Rex"));
      // parse copies the list, as it does a list the bean also writes.
      Assertions.assertThat(invoke(parsed.get(), "tags")).isNotSameAs(tags);
    }

    @Test
    @DisplayName("on every property leaves nothing to build, so the bean maps parse-only")
    void everyPropertyReadOnly() throws ReflectiveOperationException {
      JavaFileObject tagged = source("Tagged", "public record Tagged(List<String> tags) {}");
      JavaFileObject model =
          source(
              "TaggedModel",
              """
              public class TaggedModel {
                private final List<String> tags;

                public TaggedModel() { this(null); }

                public TaggedModel(List<String> tags) { this.tags = tags; }

                public List<String> getTags() { return tags; }
              }
              """);
      JavaFileObject spec =
          source(
              "TaggedMapping",
              """
              @GenerateMapping
              public interface TaggedMapping extends MappingSpec<Tagged, TaggedModel> {
                @ReadOnly
                List<String> tags();
              }
              """);
      Compilation compilation = compile(tagged, model, spec);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "'TaggedModel' maps parse-only: the generated Impl carries parse and"
                  + " asValidatedParse(), and no build. Every property of 'TaggedModel' is"
                  + " read-only, so build would write nothing.");
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object mapping = impl(result, "TaggedMapping");

      Assertions.assertThat(declared(mapping)).containsExactly("asValidatedParse", "parse", "tags");
      assertThatValidated(parse(mapping, create(result, "TaggedModel", List.of("a"))))
          .isValid()
          .hasValue(create(result, "Tagged", List.of("a")));
    }

    @Test
    @DisplayName(
        "may be a getter-only List of a wildcard, which build could not fill, read through its"
            + " leaf")
    void wildcardGetterOnlyList() throws ReflectiveOperationException {
      JavaFileObject pet = source("Pet", "public record Pet(List<String> tags, String name) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private List<? extends String> tags;
                private String name;

                public PetModel() {}

                public PetModel(List<? extends String> tags, String name) {
                  this.tags = tags;
                  this.name = name;
                }

                public List<? extends String> getTags() { return tags; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                default ValidatedPrism<List<? extends String>, List<String>> tags() {
                  return ValidatedPrism.of(tags -> Validated.validNel(List.copyOf(tags)), tags -> tags);
                }
              }
              """);
      var result = compiled(pet, model, spec);

      assertThatValidated(
              parse(impl(result, "PetMapping"), create(result, "PetModel", List.of("a"), "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", List.of("a"), "Rex"));
    }

    @Test
    @DisplayName(
        "may be declared on a mix-in a MappingSpec and an UpdateSpec share, alongside @Unmapped:"
            + " the mapping reads it, and the update leaves it out")
    void sharedMixin() throws ReflectiveOperationException {
      JavaFileObject vocabulary =
          source(
              "PetVocabulary",
              """
              public interface PetVocabulary {
                @ReadOnly
                @Unmapped
                Long id();
              }
              """);
      JavaFileObject mapping =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel>, PetVocabulary {}
              """);
      JavaFileObject patch =
          source(
              "PetPatchMapping",
              """
              @GenerateMapping
              public interface PetPatchMapping extends UpdateSpec<Pet, PetModel>, PetVocabulary {}
              """);
      var result = compiled(PET, PET_MODEL, vocabulary, mapping, patch);

      assertThatValidated(parse(impl(result, "PetMapping"), create(result, "PetModel", 3L, "Rex")))
          .isValid()
          .hasValue(create(result, "Pet", 3L, "Rex"));
      Object update =
          invoke(
              invoke(
                  impl(result, "PetPatchMapping"),
                  "updateFrom",
                  create(result, "PetModel", 9L, "Ada")),
              "apply",
              create(result, "Pet", 3L, "Rex"));
      assertThatValidated(castValidated(update))
          .isValid()
          .hasValue(create(result, "Pet", 3L, "Ada"));
      Assertions.assertThatThrownBy(() -> invoke(impl(result, "PetPatchMapping"), "id"))
          .rootCause()
          .hasMessage(
              "@Unmapped and @ReadOnly markers declare accessors the mapping leaves out or only"
                  + " reads, and are not invocable");
    }

    @Test
    @DisplayName(
        "inherited with @Unmapped by a spec over a narrower bean, stays inert there, and the"
            + " @Unmapped maps the projection")
    void inheritedOnAProjection() throws ReflectiveOperationException {
      JavaFileObject vocabulary =
          source(
              "PetVocabulary",
              """
              public interface PetVocabulary {
                @ReadOnly
                @Unmapped
                Long id();
              }
              """);
      JavaFileObject summary =
          source(
              "PetSummary",
              """
              public class PetSummary {
                private Long id;
                private String name;

                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject pet = source("Pet", "public record Pet(Long id, String name, Integer age) {}");
      JavaFileObject spec =
          source(
              "PetSummaryMapping",
              """
              @GenerateMapping
              public interface PetSummaryMapping extends MappingSpec<Pet, PetSummary>, PetVocabulary {}
              """);
      var result = compiled(pet, summary, vocabulary, spec);

      Assertions.assertThat(declared(impl(result, "PetSummaryMapping")))
          .contains("build", "patch")
          .doesNotContain("parse", "asValidatedParse");
    }

    @Test
    @DisplayName(
        "inherited alone by a spec over a narrower bean, stays inert, so the getter is refused and"
            + " offered @Unmapped on the mix-in's marker")
    void inheritedAloneOnAProjection() {
      JavaFileObject vocabulary =
          source(
              "PetVocabulary",
              """
              public interface PetVocabulary {
                @ReadOnly
                Long id();
              }
              """);
      JavaFileObject pet = source("Pet", "public record Pet(Long id, String name, Integer age) {}");
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel>, PetVocabulary {}
              """);
      Compilation compilation = compile(pet, PET_MODEL, vocabulary, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "Add setId(Long) to 'PetModel'. Or, if getId() is not meant to carry 'Pet.id',"
                  + " annotate 'id()' in 'PetVocabulary' with @Unmapped.");
    }

    @Test
    @DisplayName("inherited where the bean already writes the property, stays inert")
    void inheritedInert() throws ReflectiveOperationException {
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;

                public PetModel() {}

                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject vocabulary =
          source(
              "PetVocabulary",
              """
              public interface PetVocabulary {
                @ReadOnly
                Long id();
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel>, PetVocabulary {}
              """);
      var result = compiled(PET, model, vocabulary, spec);
      Object mapping = impl(result, "PetMapping");

      Assertions.assertThat(declared(mapping)).contains("asValidatedPrism");
      Object built = invoke(mapping, "build", create(result, "Pet", 7L, "Rex"));
      Assertions.assertThat(invoke(built, "getId")).isEqualTo(7L);
    }

    @Test
    @DisplayName(
        "inherited as an abstract ValidatedPrism by a generic record spec, is the leaf its of(...)"
            + " takes, and otherwise inert")
    void inheritedAbstractLeafOnAGenericSpec() {
      JavaFileObject box = source("Box", "public record Box<A>(A value) {}");
      JavaFileObject boxDto = source("BoxDto", "public record BoxDto<B>(B value) {}");
      JavaFileObject vocabulary =
          source(
              "BoxVocabulary",
              """
              public interface BoxVocabulary<A, B> {
                @ReadOnly
                ValidatedPrism<B, A> value();
              }
              """);
      JavaFileObject spec =
          source(
              "BoxMapping",
              """
              @GenerateMapping
              public interface BoxMapping<A, B>
                  extends MappingSpec<Box<A>, BoxDto<B>>, BoxVocabulary<A, B> {}
              """);
      Compilation compilation = compile(box, boxDto, vocabulary, spec);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .generatedSourceFile(PKG + ".BoxMappingImpl")
          .contentsAsUtf8String()
          .contains("public static <A, B> BoxMappingImpl<A, B> of(ValidatedPrism<B, A> value)");
    }

    @Test
    @DisplayName("inherited by a sealed mapping, is stubbed there and inert")
    void inheritedBySealed() {
      JavaFileObject sources =
          source(
              "Shapes",
              """
              public final class Shapes {
                private Shapes() {}

                public sealed interface Shape permits Circle {}
                public record Circle(double r) implements Shape {}
                public sealed interface ShapeDto permits CircleDto {}
                public record CircleDto(double r) implements ShapeDto {}

                public interface Vocabulary {
                  @ReadOnly
                  Long id();
                }

                @GenerateMapping
                public interface CircleMapping extends MappingSpec<Circle, CircleDto> {}

                @GenerateMapping
                public interface ShapeMapping extends MappingSpec<Shape, ShapeDto>, Vocabulary {}
              }
              """);
      assertThat(compile(sources)).succeededWithoutWarnings();
    }
  }

  @SuppressWarnings("unchecked") // reflective call into the generated Impl
  private static Validated<NonEmptyList<FieldError>, Object> castValidated(Object value) {
    return (Validated<NonEmptyList<FieldError>, Object>) value;
  }

  @Nested
  @DisplayName("A mapping with a read-only property nests")
  class Nesting {

    @Test
    @DisplayName("in a mapping that only parses, through its parse half, lifted too")
    void inParseOnly() throws ReflectiveOperationException {
      JavaFileObject order =
          source("Order", "public record Order(String ref, Pet pet, List<Pet> others) {}");
      JavaFileObject view =
          source(
              "OrderView",
              """
              public class OrderView {
                private final PetModel pet;

                public OrderView(PetModel pet) { this.pet = pet; }

                public String getRef() { return "o-1"; }
                public PetModel getPet() { return pet; }
                public List<PetModel> getOthers() { return List.of(pet); }
              }
              """);
      JavaFileObject spec =
          source(
              "OrderViewMapping",
              """
              @GenerateMapping
              public interface OrderViewMapping extends MappingSpec<Order, OrderView> {}
              """);
      var result = compiled(PET, PET_MODEL, PET_MAPPING, order, view, spec);

      Object pet = create(result, "Pet", 7L, "Rex");
      assertThatValidated(
              parse(
                  impl(result, "OrderViewMapping"),
                  create(result, "OrderView", create(result, "PetModel", 7L, "Rex"))))
          .isValid()
          .hasValue(create(result, "Order", "o-1", pet, List.of(pet)));
    }

    @Test
    @DisplayName("in a mapping that only builds, through its build half, lifted too")
    void inBuildOnly() throws ReflectiveOperationException {
      JavaFileObject order =
          source("Order", "public record Order(String ref, Pet pet, List<Pet> others) {}");
      JavaFileObject command =
          source(
              "OrderCommand",
              """
              public class OrderCommand {
                private PetModel pet;
                private List<PetModel> others;

                public void setRef(String ref) {}
                public void setPet(PetModel pet) { this.pet = pet; }
                public void setOthers(List<PetModel> others) { this.others = others; }

                public String describe() { return pet.getName() + others.get(0).getName(); }
              }
              """);
      JavaFileObject spec =
          source(
              "OrderCommandMapping",
              """
              @GenerateMapping
              public interface OrderCommandMapping extends MappingSpec<Order, OrderCommand> {}
              """);
      var result = compiled(PET, PET_MODEL, PET_MAPPING, order, command, spec);

      Object built =
          invoke(
              impl(result, "OrderCommandMapping"),
              "build",
              create(
                  result,
                  "Order",
                  "o-1",
                  create(result, "Pet", 7L, "Rex"),
                  List.of(create(result, "Pet", 8L, "Ada"))));
      Assertions.assertThat(invoke(built, "describe")).isEqualTo("RexAda");
    }

    @Test
    @DisplayName("in a sparse update and a merge, which parse")
    void inUpdateAndMerge() {
      JavaFileObject order = source("Order", "public record Order(String ref, Pet pet) {}");
      JavaFileObject patch =
          source(
              "OrderPatch",
              """
              public class OrderPatch {
                private String ref;
                private PetModel pet;

                public String getRef() { return ref; }
                public void setRef(String ref) { this.ref = ref; }
                public PetModel getPet() { return pet; }
                public void setPet(PetModel pet) { this.pet = pet; }
              }
              """);
      JavaFileObject patchSpec =
          source(
              "OrderPatchMapping",
              """
              @GenerateMapping
              public interface OrderPatchMapping extends UpdateSpec<Order, OrderPatch> {}
              """);
      JavaFileObject merge =
          source(
              "OrderMerge",
              """
              public interface OrderMerge {
                record Ref(String ref) {}
                record Source(PetModel pet) {}

                @GenerateMerge
                interface Merger {
                  Validated<NonEmptyList<FieldError>, Order> merge(Ref ref, Source source);
                }
              }
              """);
      Compilation compilation =
          compile(PET, PET_MODEL, PET_MAPPING, order, patch, patchSpec, merge);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .generatedSourceFile(PKG + ".OrderPatchMappingImpl")
          .contentsAsUtf8String()
          .contains("PetMappingImpl.INSTANCE.asValidatedParse()");
    }

    @Test
    @DisplayName(
        "as a read-only component of another such mapping, whose only read of it is its parse")
    void asReadOnlyComponent() throws ReflectiveOperationException {
      JavaFileObject order =
          source("Order", "public record Order(Long id, String ref, Pet pet) {}");
      JavaFileObject model =
          source(
              "OrderModel",
              """
              public class OrderModel {
                private Long id;
                private String ref;
                private PetModel pet;

                public OrderModel() {}

                public OrderModel(Long id, String ref, PetModel pet) {
                  this.id = id;
                  this.ref = ref;
                  this.pet = pet;
                }

                public Long getId() { return id; }
                public String getRef() { return ref; }
                public void setRef(String ref) { this.ref = ref; }
                public PetModel getPet() { return pet; }
              }
              """);
      JavaFileObject spec =
          source(
              "OrderMapping",
              """
              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderModel> {
                @ReadOnly
                Long id();

                @ReadOnly
                PetModel pet();
              }
              """);
      var result = compiled(PET, PET_MODEL, PET_MAPPING, order, model, spec);

      assertThatValidated(
              parse(
                  impl(result, "OrderMapping"),
                  create(result, "OrderModel", 1L, "o-1", create(result, "PetModel", 7L, "Rex"))))
          .isValid()
          .hasValue(create(result, "Order", 1L, "o-1", create(result, "Pet", 7L, "Rex")));
    }

    @Test
    @DisplayName("nowhere that builds and parses it, naming every read-only property")
    void notInAFullMappingNamingEach() {
      JavaFileObject pet = source("Pet", "public record Pet(Long id, String name, Long age) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;
                private Long age;

                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Long getAge() { return age; }
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                Long id();

                @ReadOnly
                Long age();
              }
              """);
      JavaFileObject order = source("Order", "public record Order(String ref, List<Pet> pets) {}");
      JavaFileObject orderModel =
          source(
              "OrderModel",
              """
              public class OrderModel {
                private String ref;
                private List<PetModel> pets;

                public String getRef() { return ref; }
                public void setRef(String ref) { this.ref = ref; }
                public List<PetModel> getPets() { return pets; }
                public void setPets(List<PetModel> pets) { this.pets = pets; }
              }
              """);
      JavaFileObject orderSpec =
          source(
              "OrderMapping",
              """
              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderModel> {}
              """);
      Compilation compilation = compile(pet, model, spec, order, orderModel, orderSpec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'PetMapping' maps this pair but has read-only properties [id, age] (no"
                  + " asValidatedPrism), so it cannot be nested in a mapping that builds and"
                  + " parses: its build leaves those properties out");
      assertThat(compilation)
          .hadErrorContaining(
              "To nest it here anyway, add 'default ValidatedPrism<com.example.readonly.PetModel,"
                  + " com.example.readonly.Pet> pets() { return"
                  + " ValidatedPrism.of(PetMappingImpl.INSTANCE::parse,"
                  + " PetMappingImpl.INSTANCE::build); }' to the spec, a leaf over the element"
                  + " types: this mapping's parse then cannot read back what its build wrote"
                  + " either.");
    }

    @Test
    @DisplayName(
        "as a read-only component, a leaf standing in for an ambiguous spec carries the marker")
    void ambiguousReadOnlyComponent() {
      JavaFileObject order = source("Order", "public record Order(String ref, Pet pet) {}");
      JavaFileObject model =
          source(
              "OrderModel",
              """
              public class OrderModel {
                private String ref;
                private PetModel pet;

                public String getRef() { return ref; }
                public void setRef(String ref) { this.ref = ref; }
                public PetModel getPet() { return pet; }
              }
              """);
      JavaFileObject second =
          source(
              "OtherPetMapping",
              """
              @GenerateMapping
              public interface OtherPetMapping extends MappingSpec<Pet, PetModel> {
                @ReadOnly
                Long id();
              }
              """);
      JavaFileObject spec =
          source(
              "OrderMapping",
              """
              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderModel> {
                @ReadOnly
                PetModel pet();
              }
              """);
      Compilation compilation = compile(PET, PET_MODEL, PET_MAPPING, second, order, model, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Add the leaf '@ReadOnly default ValidatedPrism<com.example.readonly.PetModel,"
                  + " com.example.readonly.Pet> pet()', as the component's only spec method,"
                  + " delegating to the spec you want");
    }

    @Test
    @DisplayName("nowhere that builds and parses it, and the refusal says why")
    void notInAFullMapping() {
      JavaFileObject order = source("Order", "public record Order(String ref, Pet pet) {}");
      JavaFileObject model =
          source(
              "OrderModel",
              """
              public class OrderModel {
                private String ref;
                private PetModel pet;

                public String getRef() { return ref; }
                public void setRef(String ref) { this.ref = ref; }
                public PetModel getPet() { return pet; }
                public void setPet(PetModel pet) { this.pet = pet; }
              }
              """);
      JavaFileObject spec =
          source(
              "OrderMapping",
              """
              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderModel> {}
              """);
      Compilation compilation = compile(PET, PET_MODEL, PET_MAPPING, order, model, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'PetMapping' maps this pair but has a read-only property 'id' (no"
                  + " asValidatedPrism), so it cannot be nested in a mapping that builds and"
                  + " parses: its build leaves that property out, so its parse cannot read back"
                  + " what its build wrote.");
      assertThat(compilation)
          .hadErrorContaining(
              "Nest 'PetMapping' where a mapping only parses or only builds, or where the property"
                  + " holding it is itself @ReadOnly. To nest it here anyway, add 'default"
                  + " ValidatedPrism<com.example.readonly.PetModel, com.example.readonly.Pet> pet()"
                  + " { return ValidatedPrism.of(PetMappingImpl.INSTANCE::parse,"
                  + " PetMappingImpl.INSTANCE::build); }' to the spec: this mapping's parse then"
                  + " cannot read back what its build wrote either.");
    }
  }

  @Nested
  @DisplayName("A @ReadOnly marker the spec declares is refused")
  class Refusals {

    private Compilation refused(JavaFileObject... sources) {
      Compilation compilation = compile(sources);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      return compilation;
    }

    private JavaFileObject petSpec(String members) {
      return source(
          "PetMapping",
          "@GenerateMapping\npublic interface PetMapping extends MappingSpec<Pet, PetModel> {\n"
              + members
              + "\n}\n");
    }

    @Test
    @DisplayName("on a sparse update, which builds nothing")
    void onSparse() {
      JavaFileObject spec =
          source(
              "PetPatchMapping",
              """
              @GenerateMapping
              public interface PetPatchMapping extends UpdateSpec<Pet, PetModel> {
                @ReadOnly
                Long id();
              }
              """);
      assertThat(refused(PET, PET_MODEL, spec))
          .hadErrorContaining(
              "@ReadOnly method 'id' has no meaning on a sparse update. The marker reads a getter"
                  + " with no setter as a read-only property, which parse reads and build leaves"
                  + " unwritten, and an UpdateSpec has neither: it folds what a client sends into"
                  + " an update. Replace @ReadOnly with @Unmapped to leave the accessor out of the"
                  + " update, or remove the marker. A @ReadOnly marker a mix-in declares for a"
                  + " MappingSpec stays inert here.");
    }

    @Test
    @DisplayName("on a bean only read, whose getters are all properties already")
    void onParseOnlyBean() {
      JavaFileObject view =
          source(
              "PetModel",
              """
              public class PetModel {
                public Long getId() { return 1L; }
                public String getName() { return "Rex"; }
              }
              """);
      assertThat(refused(PET, view, petSpec("@ReadOnly Long id();")))
          .hadErrorContaining(
              "@ReadOnly method 'id' has nothing to mark read-only: 'PetModel' is"
                  + " only read. The marker"
                  + " reads a getter with no setter as a read-only property, and each getter of a"
                  + " bean that is only read is a property already: parse reads those the domain"
                  + " needs. Remove the marker.");
    }

    @Test
    @DisplayName("on a bean only written, which has no getter")
    void onBuildOnlyBean() {
      JavaFileObject command =
          source(
              "PetModel",
              """
              public class PetModel {
                public void setId(Long id) {}
                public void setName(String name) {}
              }
              """);
      assertThat(refused(PET, command, petSpec("@ReadOnly Long id();")))
          .hadErrorContaining(
              "@ReadOnly method 'id' has nothing to mark read-only: 'PetModel' is"
                  + " only written. The marker"
                  + " reads a getter with no setter as a read-only property, and a bean that is"
                  + " only written has no getter, and its mapping no parse. Remove the marker.");
    }

    @Test
    @DisplayName("on a record wire, whose components are all read and written")
    void onRecordWire() {
      JavaFileObject dto = source("PetModel", "public record PetModel(Long id, String name) {}");
      assertThat(refused(PET, dto, petSpec("@ReadOnly Long id();")))
          .hadErrorContaining(
              "@ReadOnly method 'id' has nothing to mark read-only: 'PetModel' is"
                  + " a record. The marker reads"
                  + " a getter with no setter as a read-only property, and a record's components"
                  + " are all read and written. Remove the marker.");
    }

    @Test
    @DisplayName("naming a property the bean reads and writes")
    void namingAPairedProperty() {
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;

                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      assertThat(refused(PET, model, petSpec("@ReadOnly Long id();")))
          .hadErrorContaining(
              "@ReadOnly method 'id' names a property 'PetModel' maps. The marker reads a getter"
                  + " with no setter as a read-only property, and 'id' is read and written, so the"
                  + " mapping carries it both ways. Remove the marker; to leave the property"
                  + " unwritten, remove its writer from 'PetModel'.");
    }

    @Test
    @DisplayName("naming a setter, which parse cannot read")
    void namingASetter() {
      JavaFileObject pet = source("Pet", "public record Pet(String name) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private String name;

                public void setTag(String tag) {}
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      assertThat(refused(pet, model, petSpec("@ReadOnly String tag();")))
          .hadErrorContaining(
              "@ReadOnly method 'tag' names a setter, setTag(String), with no getter. The marker"
                  + " reads a getter with no setter as a read-only property, and 'PetModel' has no"
                  + " getter for 'tag' for parse to read. Name the marker after an unpaired getter,"
                  + " or remove it.");
    }

    @Test
    @DisplayName("naming a getter no domain component maps to")
    void namingAComputedGetter() {
      JavaFileObject pet = source("Pet", "public record Pet(String name) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private String name;

                public String getSummary() { return name; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      assertThat(refused(pet, model, petSpec("@ReadOnly String summary();")))
          .hadErrorContaining(
              "@ReadOnly method 'summary' names a getter, getSummary(), that no domain component"
                  + " maps to. The marker reads a getter with no setter as a read-only property,"
                  + " and parse reads it into the domain component that maps under its name. No"
                  + " component of 'Pet' needs 'summary': none maps under it, or the one that does"
                  + " is carried by another property of 'PetModel'. A getter no component needs is"
                  + " left out without a marker. Remove the marker, or map the component it should"
                  + " fill to it with '@MapField(to = \"summary\")'.");
    }

    @Test
    @DisplayName("naming nothing the bean leaves unpaired, with the likely name")
    void namingNothing() {
      assertThat(refused(PET, PET_MODEL, petSpec("@ReadOnly Long idd();")))
          .hadErrorContaining(
              "@ReadOnly method 'idd' names no getter 'PetModel' leaves unpaired. The marker reads"
                  + " a getter with no setter as a read-only property. Getters 'PetModel' leaves"
                  + " unpaired that a domain component maps to: [id]. Name the marker after the"
                  + " getter's property, or remove it. Did you mean 'id()'?");
    }

    @Test
    @DisplayName("naming nothing, on a bean that leaves no getter unpaired")
    void namingNothingWithNoCandidate() {
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;

                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      assertThat(refused(PET, model, petSpec("@ReadOnly Long ident();")))
          .hadErrorContaining(
              "@ReadOnly method 'ident' names no getter 'PetModel' leaves unpaired. The marker"
                  + " reads a getter with no setter as a read-only property. 'PetModel' leaves no"
                  + " getter unpaired that a domain component maps to. Remove the marker.");
    }

    @Test
    @DisplayName("marked @Unmapped too")
    void markedUnmappedToo() {
      assertThat(refused(PET, PET_MODEL, petSpec("@ReadOnly @Unmapped Long id();")))
          .hadErrorContaining(
              "@ReadOnly method 'id' is marked @Unmapped too. @ReadOnly reads the getter on parse,"
                  + " and @Unmapped leaves it out of the mapping, so the two contradict each other"
                  + " on a MappingSpec.");
    }

    @Test
    @DisplayName("on a bean still narrower than the domain, which maps as a projection")
    void onProjection() {
      JavaFileObject pet = source("Pet", "public record Pet(Long id, String name, int age) {}");
      assertThat(refused(pet, PET_MODEL, petSpec("@ReadOnly Long id();")))
          .hadErrorContaining(
              "'PetModel' maps as a projection, which has no parse to read the read-only"
                  + " property 'id'. 'PetModel' has no property for [age] of 'Pet'. A read-only"
                  + " property is one parse reads and build leaves out, and a projection has no"
                  + " parse: it builds, and writes a wire back onto a domain value it is given."
                  + " Give 'PetModel' a property for [age], so that parse can read the whole"
                  + " domain; or, to map the projection, replace @ReadOnly with @Unmapped on"
                  + " 'id()'.");
    }

    @Test
    @DisplayName("on a projection, naming each read-only property the spec declares")
    void onProjectionNamingEach() {
      JavaFileObject pet =
          source("Pet", "public record Pet(Long id, String name, Long age, String email) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;
                private Long age;

                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Long getAge() { return age; }
              }
              """);
      Compilation compilation =
          refused(pet, model, petSpec("@ReadOnly Long id();\n@ReadOnly Long age();"));
      assertThat(compilation)
          .hadErrorContaining(
              "'PetModel' maps as a projection, which has no parse to read the read-only"
                  + " properties [id, age].");
      assertThat(compilation).hadErrorContaining("replace @ReadOnly with @Unmapped on [id, age].");
    }

    @Test
    @DisplayName("on a sealed mapping, which has no properties")
    void onSealed() {
      JavaFileObject sources =
          source(
              "Shapes",
              """
              public final class Shapes {
                private Shapes() {}

                public sealed interface Shape permits Circle {}
                public record Circle(double r) implements Shape {}
                public sealed interface ShapeDto permits CircleDto {}
                public record CircleDto(double r) implements ShapeDto {}

                @GenerateMapping
                public interface CircleMapping extends MappingSpec<Circle, CircleDto> {}

                @GenerateMapping
                public interface ShapeMapping extends MappingSpec<Shape, ShapeDto> {
                  @ReadOnly
                  Long id();
                }
              }
              """);
      assertThat(refused(sources))
          .hadErrorContaining("@ReadOnly on 'id' has no meaning on a sealed mapping.");
    }

    @Test
    @DisplayName("on a method with a body that is no leaf")
    void onANonLeafBody() {
      assertThat(refused(PET, PET_MODEL, petSpec("@ReadOnly default Long id() { return 1L; }")))
          .hadErrorContaining("@ReadOnly method 'id' is neither a marker nor a leaf.");
    }

    @Test
    @DisplayName("beside a spec member named after a half the Impl emits")
    void collidingWithAHalf() {
      assertThat(
              refused(
                  PET,
                  PET_MODEL,
                  petSpec(
                      "@ReadOnly Long id();\ndefault String asValidatedBuild() { return \"\"; }")))
          .hadErrorContaining(
              "collides with the 'asValidatedBuild' member the generated PetMappingImpl emits for"
                  + " this tier (a mapping with a read-only property)");
    }

    @Test
    @DisplayName("on an abstract method returning ValidatedPrism, which reads as a leaf")
    void onAnAbstractLeaf() {
      JavaFileObject petId = source("PetId", "public record PetId(Long value) {}");
      JavaFileObject pet = source("Pet", "public record Pet(PetId id, String name) {}");
      assertThat(
              refused(
                  petId, pet, PET_MODEL, petSpec("@ReadOnly ValidatedPrism<Long, PetId> id();")))
          .hadErrorContaining(
              "abstract leaf 'id' needs a generic spec. A concrete pair's leaf carries its own"
                  + " parser as a 'default' body; only a generic spec defers the element mapping"
                  + " to the generated 'of(...)' factory. Give the method a body ('default'), or"
                  + " make the spec generic in the element types.");
    }

    @Test
    @DisplayName("on a marker declaring parameters")
    void withParameters() {
      assertThat(refused(PET, PET_MODEL, petSpec("@ReadOnly Long id(int unused);")))
          .hadErrorContaining(
              "@ReadOnly method 'id' must not declare parameters. The marker is named after the"
                  + " accessor the mapping reads and never writes; the generated stub implements"
                  + " it without parameters. Remove the parameters.");
    }
  }

  @Nested
  @DisplayName("Without a marker")
  class WithoutAMarker {

    @Test
    @DisplayName("a getter named after a component is still refused, and offered @ReadOnly")
    void theMisspellingGuardStays() {
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {}
              """);
      Compilation compilation = compile(PET, PET_MODEL, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'id' on 'PetModel' has a getter, getId(), but no setter, so the"
                  + " mapping leaves it out. A mapping carries only the properties a bean both"
                  + " reads and writes, so 'Pet.id' would go unmapped without a word: build would"
                  + " never write it. Add setId(Long) to 'PetModel'. Or, if 'id' is read-only,"
                  + " declare '@ReadOnly Long id();' on the spec: parse reads getId(), and build"
                  + " leaves 'id' unwritten. Or, if getId() is not meant to carry 'Pet.id',"
                  + " declare '@Unmapped Long id();' on the spec.");
    }

    @Test
    @DisplayName(
        "a getter on a bean that would still be narrower than the domain is not offered it")
    void noOfferOnAProjection() {
      JavaFileObject pet = source("Pet", "public record Pet(Long id, String name, Integer age) {}");
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {}
              """);
      Compilation compilation = compile(pet, PET_MODEL, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Add setId(Long) to 'PetModel'. Or, if getId() is not meant to carry 'Pet.id',"
                  + " declare '@Unmapped Long id();' on the spec.");
      Assertions.assertThat(compilation.errors().toString()).doesNotContain("@ReadOnly");
    }

    @Test
    @DisplayName("a getter is not offered it where an @Unmapped getter leaves the bean narrower")
    void noOfferBesideAnUnmappedGetter() {
      JavaFileObject pet = source("Pet", "public record Pet(Long id, String name, Integer age) {}");
      JavaFileObject model =
          source(
              "PetModel",
              """
              public class PetModel {
                private Long id;
                private String name;

                public Long getId() { return id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Integer getAge() { return 3; }
              }
              """);
      JavaFileObject spec =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @Unmapped
                Integer age();
              }
              """);
      Compilation compilation = compile(pet, model, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Add setId(Long) to 'PetModel'.");
      Assertions.assertThat(compilation.errors().toString()).doesNotContain("@ReadOnly");
    }

    @Test
    @DisplayName(
        "a getter whose name a helper method takes is offered markers in the helper's place, and a"
            + " marker's own annotation beside it")
    void offersOnASameNamedMethod() {
      JavaFileObject helper =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                default String id() { return "helper"; }
              }
              """);
      Compilation replaced = compile(PET, PET_MODEL, helper);
      assertThat(replaced).failed();
      assertThat(replaced)
          .hadErrorContaining(
              "Or, if 'id' is read-only, replace 'id()' with '@ReadOnly Long id();': parse reads"
                  + " getId(), and build leaves 'id' unwritten. Or, if getId() is not meant to"
                  + " carry 'Pet.id', replace 'id()' with '@Unmapped Long id();'.");

      JavaFileObject pet = source("Pet", "public record Pet(Optional<Long> id, String name) {}");
      JavaFileObject bridged =
          source(
              "PetMapping",
              """
              @GenerateMapping
              public interface PetMapping extends MappingSpec<Pet, PetModel> {
                @org.higherkindedj.optics.annotations.OptionalBridge
                Optional<Long> id();
              }
              """);
      Compilation annotated = compile(pet, PET_MODEL, bridged);
      assertThat(annotated).failed();
      assertThat(annotated)
          .hadErrorContaining(
              "Or, if 'id' is read-only, annotate 'id()' with @ReadOnly: parse reads getId(), and"
                  + " build leaves 'id' unwritten. Or, if getId() is not meant to carry 'Pet.id',"
                  + " annotate 'id()' with @Unmapped.");
    }
  }
}
