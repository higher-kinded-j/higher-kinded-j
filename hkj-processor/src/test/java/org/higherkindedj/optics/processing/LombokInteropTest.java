// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins Lombok interop for bean-shaped wires: a {@code @Data} class has no source-level getters or
 * setters, so the bean analyser only sees them if Lombok's processor has materialised them in the
 * same javac run, and only if Lombok is listed first on the processor path (within a round, javac
 * invokes processors in listed order, and the bean analyser needs the accessors already
 * materialised). Both orders are pinned: the working one end to end, the broken one by its
 * diagnostic. The builders Lombok generates are pinned against its real output too:
 * {@code @Singular} collections, with their adders, and {@code @SuperBuilder}'s self-typed builder.
 * Lombok's {@code @With} beside {@code @GenerateFocus} on the same records is pinned too, as the
 * Optics chapter's introduction says it can be.
 */
@DisplayName("Lombok interop - bean wires")
class LombokInteropTest {

  private static Processor lombok() {
    try {
      return (Processor)
          Class.forName("lombok.launch.AnnotationProcessorHider$AnnotationProcessor")
              .getDeclaredConstructor()
              .newInstance();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Lombok processor not loadable", e);
    }
  }

  private static String generatedImpl(Compilation compilation, String name) {
    return compilation.generatedSourceFiles().stream()
        .filter(f -> f.getName().contains(name))
        .findFirst()
        .map(
            f -> {
              try {
                return f.getCharContent(true).toString();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
        .orElseThrow(() -> new AssertionError(name + " not generated"));
  }

  /** The Impl must read and write through the Lombok-materialised accessors, not merely exist. */
  private static void assertUsesLombokAccessors(Compilation compilation) {
    assertThat(compilation).succeeded();
    Assertions.assertThat(generatedImpl(compilation, "UserMappingImpl"))
        .contains("wire.setName(domain.name());")
        .contains("wire.setAge(domain.age());")
        .contains(".field(\"name\", hkj$ifPresent(wire.getName(), Validated::validNel))")
        .contains(".field(\"age\", Validated.validNel(wire.getAge()))");
  }

  @Test
  @DisplayName("Lombok's @With and @GenerateFocus on one record both generate, and make one change")
  void withAndGenerateFocusOnOneRecord() throws ReflectiveOperationException {
    JavaFileObject records =
        JavaFileObjects.forSourceString(
            "com.example.Employee",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @lombok.With
            @GenerateFocus(generateNavigators = true)
            record Address(String street, String city) {}

            @lombok.With
            @GenerateFocus(generateNavigators = true)
            record Company(String name, Address address) {}

            @lombok.With
            @GenerateFocus(generateNavigators = true)
            public record Employee(String name, Company company) {}
            """);
    // Compiles only if both the withers and the generated path exist on the same records
    JavaFileObject moves =
        JavaFileObjects.forSourceString(
            "com.example.Moves",
            """
            package com.example;

            public final class Moves {
              private Moves() {}

              public static Employee sample() {
                return new Employee("Alice", new Company("Initech", new Address("123 Fake St", "Anytown")));
              }

              public static Employee byWithers(Employee employee) {
                return employee.withCompany(
                    employee.company().withAddress(employee.company().address().withStreet("456 Main St")));
              }

              public static Employee byFocus(Employee employee) {
                return EmployeeFocus.company().address().street().set("456 Main St", employee);
              }
            }
            """);

    Compilation compilation =
        javac().withProcessors(lombok(), new FocusProcessor()).compile(records, moves);
    assertThat(compilation).succeeded();

    var result = new RuntimeCompilationHelper.CompiledResult(compilation);
    Object employee = result.invokeStatic("com.example.Moves", "sample");
    Object byWithers = result.invokeStatic("com.example.Moves", "byWithers", employee);
    Object byFocus = result.invokeStatic("com.example.Moves", "byFocus", employee);
    Assertions.assertThat(byFocus).isEqualTo(byWithers);
    Assertions.assertThat(byFocus.toString()).contains("street=456 Main St");
  }

  @Test
  @DisplayName("a @Data wire maps when Lombok is listed first; the reverse order is diagnosed")
  void lombokDataBeanWireMaps() {
    JavaFileObject domain =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            public record User(String name, int age) {}
            """);
    JavaFileObject wire =
        JavaFileObjects.forSourceString(
            "com.example.UserDto",
            """
            package com.example;

            @lombok.Data
            public class UserDto {
              private String name;
              private int age;
            }
            """);
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.UserMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface UserMapping extends MappingSpec<User, UserDto> {}
            """);
    // Order matters, and both directions are pinned. Lombok first: the accessors exist by the
    // time the bean analyser reads the wire, and the mapping generates against them.
    Compilation lombokFirst =
        javac().withProcessors(lombok(), new MappingProcessor()).compile(domain, wire, spec);
    assertUsesLombokAccessors(lombokFirst);

    // Mapping first: the analyser runs before Lombok has materialised the accessors, and the
    // wire is honestly diagnosed rather than half-mapped. This is why the documented processor
    // path lists Lombok before hkj-processor.
    Compilation mappingFirst =
        javac().withProcessors(new MappingProcessor(), lombok()).compile(domain, wire, spec);
    assertThat(mappingFirst).failed();
    assertThat(mappingFirst).hadErrorContaining("is not a usable bean-shaped wire");

    // And the compiled classes really carry the accessors: a full runtime round trip.
    var result = new RuntimeCompilationHelper.CompiledResult(lombokFirst);
    try {
      Object impl = result.instance("com.example.UserMappingImpl");
      Object user = result.newInstance("com.example.User", "Ada", 36);
      Object dto = RuntimeCompilationHelper.invoke(impl, "build", user);
      Assertions.assertThat(RuntimeCompilationHelper.invoke(dto, "getName")).isEqualTo("Ada");
      Assertions.assertThat(RuntimeCompilationHelper.invoke(dto, "getAge")).isEqualTo(36);
      assertThatValidated(validated(RuntimeCompilationHelper.invoke(impl, "parse", dto)))
          .isValid()
          .hasValue(user);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  /** One source file holding every type named, as Lombok and the specs see them together. */
  private static JavaFileObject lombokTypes(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.UpdateSpec;

        final class %s {
          private %s() {}
        %s
        }
        """
            .formatted(name, name, body));
  }

  /** A record built through its canonical constructor, which takes the arguments in order. */
  private static Object newRecord(
      RuntimeCompilationHelper.CompiledResult result, String name, Object... components)
      throws ReflectiveOperationException {
    Constructor<?> canonical = result.loadClass("com.example." + name).getDeclaredConstructors()[0];
    canonical.setAccessible(true);
    return canonical.newInstance(components);
  }

  @Test
  @DisplayName(
      "Lombok @Singular and @SuperBuilder builders map, writing each collection element once")
  void singularAndSuperBuilderBuildersMap() throws ReflectiveOperationException {
    JavaFileObject types =
        lombokTypes(
            "Shop",
            """
              // @Singular on a two-way @Value @Builder, and on a build-only @Builder.
              record Order(String id, List<String> tags) {}

              @lombok.Value
              @lombok.Builder
              static class OrderDto {
                String id;
                @lombok.Singular List<String> tags;
              }

              @GenerateMapping
              interface OrderMapping extends MappingSpec<Order, OrderDto> {}

              @lombok.Builder
              @lombok.ToString
              static class OrderRequest {
                private final String id;
                @lombok.Singular private final List<String> tags;
              }

              @GenerateMapping
              interface OrderRequestMapping extends MappingSpec<Order, OrderRequest> {}

              // A @Singular map, whose adder takes a key and a value.
              record Scores(String id, Map<String, Integer> scores) {}

              @lombok.Value
              @lombok.Builder
              static class ScoresDto {
                String id;
                @lombok.Singular Map<String, Integer> scores;
              }

              @GenerateMapping
              interface ScoresMapping extends MappingSpec<Scores, ScoresDto> {}

              // A build-only @Singular collection a domain Set fills element by element.
              record Tag(String value) {}

              record TagDto(String value) {}

              @GenerateMapping
              interface TagMapping extends MappingSpec<Tag, TagDto> {}

              record Post(String id, Set<Tag> tags) {}

              @lombok.Builder
              @lombok.ToString
              static class PostRequest {
                private final String id;
                @lombok.Singular private final List<TagDto> tags;
              }

              @GenerateMapping
              interface PostMapping extends MappingSpec<Post, PostRequest> {}

              // An adder named on request, the only writer taking one String.
              record Labels(int count, List<String> tags) {}

              @lombok.Builder
              @lombok.ToString
              static class LabelsRequest {
                private final int count;
                @lombok.Singular("label") private final List<String> tags;
              }

              @GenerateMapping
              interface LabelsMapping extends MappingSpec<Labels, LabelsRequest> {}

              // @SuperBuilder, whose build() is declared 'C build()', with a @Singular collection.
              record Crate(String id, String label, List<String> items) {}

              @lombok.experimental.SuperBuilder
              @lombok.Getter
              static class Box {
                private final String id;
              }

              @lombok.experimental.SuperBuilder
              @lombok.Getter
              static class CrateDto extends Box {
                private final String label;
                @lombok.Singular private final List<String> items;
              }

              @GenerateMapping
              interface CrateMapping extends MappingSpec<Crate, CrateDto> {}

              // A projection onto a @Singular collection: patch keeps what the bean lacks.
              record Listing(String id, String note, List<String> tags) {}

              @lombok.Value
              @lombok.Builder
              static class ListingDto {
                String id;
                @lombok.Singular List<String> tags;
              }

              @GenerateMapping
              interface ListingMapping extends MappingSpec<Listing, ListingDto> {}

              // Plain properties Lombok backs with fields of their own: one sharing the adder's
              // name at another type, one the collection's name begins with, one with a
              // @Builder.Default, and a second collection whose adder is named on request.
              record Change(String from, String to) {}

              record Ticket(String id, String status, List<Change> statuses) {}

              @lombok.Builder
              @lombok.ToString
              static class TicketRequest {
                private final String id;
                private final String status;
                @lombok.Singular private final List<Change> statuses;
              }

              @GenerateMapping
              interface TicketMapping extends MappingSpec<Ticket, TicketRequest> {}

              record Contact(
                  String address, String note, List<String> addressLines, List<String> people) {}

              @lombok.Builder
              @lombok.ToString
              static class ContactRequest {
                private final String address;
                @lombok.Builder.Default private final String note = "none";
                @lombok.Singular private final List<String> addressLines;
                @lombok.Singular("person") private final List<String> people;
              }

              @GenerateMapping
              interface ContactMapping extends MappingSpec<Contact, ContactRequest> {}
            """);
    Compilation compilation =
        javac().withProcessors(lombok(), new MappingProcessor()).compile(types);
    assertThat(compilation).succeeded();
    // The build-only collection setter is handed a copy, as every same-typed container is.
    Assertions.assertThat(generatedImpl(compilation, "ShopOrderRequestMappingImpl"))
        .contains("b.tags(hkj$copyOf(domain.tags()));");

    var result = new RuntimeCompilationHelper.CompiledResult(compilation);
    Object order = newRecord(result, "Shop$Order", "o1", List.of("x", "y"));

    Object orderMapping = result.instance("com.example.ShopOrderMappingImpl");
    Object orderDto = RuntimeCompilationHelper.invoke(orderMapping, "build", order);
    Assertions.assertThat(RuntimeCompilationHelper.invoke(orderDto, "getTags"))
        .isEqualTo(List.of("x", "y"));
    Assertions.assertThat(parsed(RuntimeCompilationHelper.invoke(orderMapping, "parse", orderDto)))
        .isEqualTo(order);

    Object requestMapping = result.instance("com.example.ShopOrderRequestMappingImpl");
    Assertions.assertThat(RuntimeCompilationHelper.invoke(requestMapping, "build", order))
        .hasToString("Shop.OrderRequest(id=o1, tags=[x, y])");

    Object scores = newRecord(result, "Shop$Scores", "s1", Map.of("a", 1));
    Object scoresMapping = result.instance("com.example.ShopScoresMappingImpl");
    Object scoresDto = RuntimeCompilationHelper.invoke(scoresMapping, "build", scores);
    Assertions.assertThat(RuntimeCompilationHelper.invoke(scoresDto, "getScores"))
        .isEqualTo(Map.of("a", 1));
    Assertions.assertThat(
            parsed(RuntimeCompilationHelper.invoke(scoresMapping, "parse", scoresDto)))
        .isEqualTo(scores);

    Object tag = newRecord(result, "Shop$Tag", "t1");
    Object post = newRecord(result, "Shop$Post", "p1", Set.of(tag));
    Assertions.assertThat(
            RuntimeCompilationHelper.invoke(
                result.instance("com.example.ShopPostMappingImpl"), "build", post))
        .hasToString("Shop.PostRequest(id=p1, tags=[TagDto[value=t1]])");

    Object labels = newRecord(result, "Shop$Labels", 2, List.of("x", "y"));
    Assertions.assertThat(
            RuntimeCompilationHelper.invoke(
                result.instance("com.example.ShopLabelsMappingImpl"), "build", labels))
        .hasToString("Shop.LabelsRequest(count=2, tags=[x, y])");

    Object crate = newRecord(result, "Shop$Crate", "c1", "fragile", List.of("i1", "i2"));
    Object crateMapping = result.instance("com.example.ShopCrateMappingImpl");
    Object crateDto = RuntimeCompilationHelper.invoke(crateMapping, "build", crate);
    Assertions.assertThat(parsed(RuntimeCompilationHelper.invoke(crateMapping, "parse", crateDto)))
        .isEqualTo(crate);

    Object listing = newRecord(result, "Shop$Listing", "l1", "kept", List.of("old"));
    Object listingMapping = result.instance("com.example.ShopListingMappingImpl");
    Object listingDto =
        RuntimeCompilationHelper.invoke(
            listingMapping,
            "build",
            newRecord(result, "Shop$Listing", "l2", "gone", List.of("new")));
    Assertions.assertThat(
            parsed(RuntimeCompilationHelper.invoke(listingMapping, "patch", listing, listingDto)))
        .isEqualTo(newRecord(result, "Shop$Listing", "l2", "kept", List.of("new")));

    Object ticket =
        newRecord(
            result,
            "Shop$Ticket",
            "t1",
            "OPEN",
            List.of(newRecord(result, "Shop$Change", "NEW", "OPEN")));
    Assertions.assertThat(
            RuntimeCompilationHelper.invoke(
                result.instance("com.example.ShopTicketMappingImpl"), "build", ticket))
        .hasToString(
            "Shop.TicketRequest(id=t1, status=OPEN, statuses=[Change[from=NEW, to=OPEN]])");

    Object contact =
        newRecord(result, "Shop$Contact", "a", "n", List.of("l1", "l2"), List.of("p1"));
    Assertions.assertThat(
            RuntimeCompilationHelper.invoke(
                result.instance("com.example.ShopContactMappingImpl"), "build", contact))
        .hasToString("Shop.ContactRequest(address=a, note=n, addressLines=[l1, l2], people=[p1])");
  }

  @Test
  @DisplayName(
      "a @Singular collection refuses the Optional bridge and a PATCH, and an adder it cannot tell"
          + " apart")
  void singularCollectionRefusals() {
    JavaFileObject types =
        lombokTypes(
            "Refused",
            """
              // The bridge, two-way and build-only.
              record Order(String id, Optional<List<String>> tags) {}

              @lombok.Value
              @lombok.Builder
              static class OrderDto {
                String id;
                @lombok.Singular List<String> tags;
              }

              @GenerateMapping
              interface OrderMapping extends MappingSpec<Order, OrderDto> {}

              @lombok.Builder
              static class OrderRequest {
                private final String id;
                @lombok.Singular private final List<String> tags;
              }

              @GenerateMapping
              interface OrderRequestMapping extends MappingSpec<Order, OrderRequest> {}

              // A PATCH bean, whose collection is never null.
              record Basket(String id, List<String> items) {}

              @lombok.Value
              @lombok.Builder
              static class BasketPatch {
                String id;
                @lombok.Singular List<String> items;
              }

              @GenerateMapping
              interface BasketPatchMapping extends UpdateSpec<Basket, BasketPatch> {}

              // Two adders named on request, each taking one String, neither after its collection.
              record Crew(List<String> people, List<String> media) {}

              @lombok.Builder
              static class CrewRequest {
                @lombok.Singular("person") private final List<String> people;
                @lombok.Singular("medium") private final List<String> media;
              }

              @GenerateMapping
              interface CrewMapping extends MappingSpec<Crew, CrewRequest> {}

              // The bridge in a @NullMarked scope, where the setter Lombok writes is non-null.
              @org.jspecify.annotations.NullMarked
              static final class Strict {
                private Strict() {}

                record Order(String id, Optional<List<String>> tags) {}

                @lombok.Value
                @lombok.Builder
                static class OrderDto {
                  String id;
                  @lombok.Singular List<String> tags;
                }

                @GenerateMapping
                interface OrderMapping extends MappingSpec<Order, OrderDto> {}
              }
            """);
    Compilation compilation =
        javac().withProcessors(lombok(), new MappingProcessor()).compile(types);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(6);
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Order.tags' is Optional<List<String>>, bridged to the @Singular bean"
                + " property 'tags' (not supported yet)");
    Assertions.assertThat(compilation.errors())
        .filteredOn(error -> error.getMessage(null).contains("bridged to the @Singular"))
        .extracting(error -> error.getMessage(null).contains("mark the field @Nullable"))
        .containsExactlyInAnyOrder(false, false, true);
    assertThat(compilation)
        .hadErrorContaining(
            "bean property 'items' on 'BasketPatch' is a @Singular collection, which cannot carry a"
                + " sparse update's absence (not supported yet)");
    assertThat(compilation)
        .hadErrorContaining(
            "the singular adder of the @Singular collection 'people' on 'CrewRequest' cannot be"
                + " told apart");
    assertThat(compilation)
        .hadErrorContaining(
            "the singular adder of the @Singular collection 'media' on 'CrewRequest' cannot be"
                + " told apart");
  }

  /** The value a generated parse or patch returned, which must be valid. */
  private static Object parsed(Object value) {
    Validated<NonEmptyList<FieldError>, Object> validated = validated(value);
    assertThatValidated(validated).isValid();
    return validated.get();
  }

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> validated(Object value) {
    return (Validated<NonEmptyList<FieldError>, Object>) value;
  }
}
