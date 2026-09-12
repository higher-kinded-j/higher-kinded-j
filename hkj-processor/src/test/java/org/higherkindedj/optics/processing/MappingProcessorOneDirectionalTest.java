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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * One-directional bean wires. A bean with getters and no way to be written maps parse-only, and one
 * that can be written but declares no getter maps build-only: the Impl carries only that
 * direction's surface, a note says which way the bean maps and why, and the mapping nests wherever
 * only its direction is used.
 *
 * <p>Everything compiles under {@code -Xlint:unchecked,rawtypes -Werror}, so a generated leg that
 * calls a half's bulk form at a type it does not accept fails here rather than in a user's build.
 */
@DisplayName("MappingProcessor - one-directional bean wires")
class MappingProcessorOneDirectionalTest {

  private static final String PKG = "com.example.oneway";

  private static final String IMPORTS =
      """
      import java.util.List;
      import java.util.Map;
      import java.util.Optional;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.Getter;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MapField;
      import org.higherkindedj.optics.annotations.MapKey;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.UpdateSpec;
      import org.higherkindedj.optics.validated.ValidatedPrism;

      """;

  private static final JavaFileObject EMAIL =
      source("EmailAddress", "public record EmailAddress(String value) {}");

  private static final JavaFileObject EMAILS =
      source(
          "Emails",
          """
          public final class Emails {
            private Emails() {}

            public static final ValidatedPrism<String, EmailAddress> EMAIL =
                ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    EmailAddress::value);
          }
          """);

  private static final JavaFileObject ORDER =
      source("Order", "public record Order(String id, EmailAddress contact, int quantity) {}");

  // A read model: constructed whole, read through getters, never written.
  private static final JavaFileObject VENDOR_ORDER =
      source(
          "VendorOrder",
          """
          public class VendorOrder {
            private final String id;
            private final String contact;
            private final int quantity;

            public VendorOrder(String id, String contact, int quantity) {
              this.id = id;
              this.contact = contact;
              this.quantity = quantity;
            }

            public String getId() { return id; }
            public String getContact() { return contact; }
            public int getQuantity() { return quantity; }
            // No domain component names it, so parse never reads it.
            public String getSupplierNote() { return "ignored"; }
          }
          """);

  private static final JavaFileObject VENDOR_ORDER_MAPPING =
      source(
          "VendorOrderMapping",
          """
          @GenerateMapping
          public interface VendorOrderMapping extends MappingSpec<Order, VendorOrder> {
            default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }
          }
          """);

  // A write model: filled through setters, never read back.
  private static final JavaFileObject ORDER_REQUEST =
      source(
          "OrderRequest",
          """
          public class OrderRequest {
            private String id;
            private String contact;
            private int quantity;
            private String label;

            public void setId(String id) { this.id = id; }
            public void setContact(String contact) { this.contact = contact; }
            public void setQuantity(int quantity) { this.quantity = quantity; }
            public void setLabel(String label) { this.label = label; }

            public String describe() { return id + "|" + contact + "|" + quantity + "|" + label; }
          }
          """);

  private static final JavaFileObject ORDER_REQUEST_MAPPING =
      source(
          "OrderRequestMapping",
          """
          @GenerateMapping
          public interface OrderRequestMapping extends MappingSpec<Order, OrderRequest> {
            default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }

            default Getter<Order, String> label() {
              return Getter.of(order -> order.id() + "#" + order.quantity());
            }
          }
          """);

  private static final JavaFileObject PERSON =
      source("Person", "public record Person(String name) {}");

  // The nested fixtures: one parse-only item, one build-only line, and one full item mapping.
  private static final JavaFileObject ITEM =
      source("Item", "public record Item(String sku, EmailAddress owner) {}");

  private static final JavaFileObject VENDOR_ITEM =
      source(
          "VendorItem",
          """
          public class VendorItem {
            private final String sku;
            private final String owner;

            public VendorItem(String sku, String owner) {
              this.sku = sku;
              this.owner = owner;
            }

            public String getSku() { return sku; }
            public String getOwner() { return owner; }
          }
          """);

  private static final JavaFileObject VENDOR_ITEM_MAPPING =
      source(
          "VendorItemMapping",
          """
          @GenerateMapping
          public interface VendorItemMapping extends MappingSpec<Item, VendorItem> {
            default ValidatedPrism<String, EmailAddress> owner() { return Emails.EMAIL; }
          }
          """);

  private static final JavaFileObject ITEM_DTO =
      source("ItemDto", "public record ItemDto(String sku, String owner) {}");

  private static final JavaFileObject ITEM_MAPPING =
      source(
          "ItemMapping",
          """
          @GenerateMapping
          public interface ItemMapping extends MappingSpec<Item, ItemDto> {
            default ValidatedPrism<String, EmailAddress> owner() { return Emails.EMAIL; }
          }
          """);

  private static final JavaFileObject LINE =
      source("Line", "public record Line(String sku, int qty) {}");

  private static final JavaFileObject LINE_REQUEST =
      source(
          "LineRequest",
          """
          public class LineRequest {
            private String sku;
            private int qty;

            public void setSku(String sku) { this.sku = sku; }
            public void setQty(int qty) { this.qty = qty; }

            public String describe() { return sku + " x" + qty; }
          }
          """);

  private static final JavaFileObject LINE_REQUEST_MAPPING =
      source(
          "LineRequestMapping",
          """
          @GenerateMapping
          public interface LineRequestMapping extends MappingSpec<Line, LineRequest> {}
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

  private static String generatedSource(Compilation compilation, String simpleName) {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(PKG + "." + simpleName);
    Assertions.assertThat(file).as("generated %s", simpleName).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Instantiates a compiled class through its first declared constructor: a record's canonical. */
  private static Object create(
      RuntimeCompilationHelper.CompiledResult result, String simpleName, Object... args)
      throws ReflectiveOperationException {
    return result.loadClass(PKG + "." + simpleName).getDeclaredConstructors()[0].newInstance(args);
  }

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object mapping, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(mapping, "parse", wire);
  }

  private static boolean notesMention(Compilation compilation, String text) {
    return compilation.notes().stream()
        .anyMatch(note -> note.getMessage(Locale.ROOT).contains(text));
  }

  @Nested
  @DisplayName("Parse-only: a bean with getters and no way to be written")
  class ParseOnly {

    @Test
    @DisplayName(
        "emits parse and asValidatedParse() and no build, reading only what the domain needs")
    void emitsTheParseSurfaceOnly() throws ReflectiveOperationException {
      Compilation compilation = compile(EMAIL, EMAILS, ORDER, VENDOR_ORDER, VENDOR_ORDER_MAPPING);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "'VendorOrder' maps parse-only: the generated Impl carries parse and"
                  + " asValidatedParse(), and no build.");
      assertThat(compilation)
          .hadNoteContaining(
              "'VendorOrder' has getters but no setX setters and no builder that fills it,"
                  + " so nothing can write it.");

      String generated = generatedSource(compilation, "VendorOrderMappingImpl");
      Assertions.assertThat(generated)
          .contains("public Validated<NonEmptyList<FieldError>, Order> parse(VendorOrder wire)")
          .contains("public ValidatedParse<VendorOrder, Order> asValidatedParse()")
          .contains("return ValidatedParse.of(this::parse);")
          .doesNotContain("build(")
          .doesNotContain("asValidatedPrism")
          .doesNotContain("getSupplierNote");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance(PKG + ".VendorOrderMappingImpl");
      Object order =
          create(result, "Order", "o-1", create(result, "EmailAddress", "ada@corp.example"), 2);
      assertThatValidated(parse(impl, create(result, "VendorOrder", "o-1", "ada@corp.example", 2)))
          .isValid()
          .hasValue(order);
      assertThatValidated(parse(impl, create(result, "VendorOrder", null, "nope", 2)))
          .isInvalid()
          .hasFieldErrors("id: must not be null", "contact: not an email address");

      // The half the Impl exposes parses exactly as the Impl does.
      Object half = invoke(impl, "asValidatedParse");
      Object wire = create(result, "VendorOrder", "o-1", "ada@corp.example", 2);
      Assertions.assertThat(invoke(half, "parse", wire)).isEqualTo(parse(impl, wire));
    }

    @Test
    @DisplayName("a read model's List getter beside read-only getters does not make it two-way")
    void listGetterAmongReadOnlyGettersStaysParseOnly() {
      JavaFileObject tagged =
          source("Tagged", "public record Tagged(String name, List<String> tags) {}");
      JavaFileObject view =
          source(
              "TaggedView",
              """
              public class TaggedView {
                public String getName() { return "Ada"; }
                public List<String> getTags() { return List.of("a"); }
              }
              """);
      JavaFileObject spec =
          source(
              "TaggedViewMapping",
              """
              @GenerateMapping
              public interface TaggedViewMapping extends MappingSpec<Tagged, TaggedView> {}
              """);
      Compilation compilation = compile(tagged, view, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'TaggedView' maps parse-only");
      Assertions.assertThat(generatedSource(compilation, "TaggedViewMappingImpl"))
          .contains("wire.getName()")
          .doesNotContain("addAll");
    }

    @Test
    @DisplayName("a bean whose every getter is a getter-only List is still filled through them")
    void listOnlyBeanStaysTwoWay() {
      JavaFileObject crate = source("Crate", "public record Crate(List<String> items) {}");
      JavaFileObject dto =
          source(
              "CrateDto",
              """
              public class CrateDto {
                private final List<String> items = new java.util.ArrayList<>();
                public List<String> getItems() { return items; }
              }
              """);
      JavaFileObject spec =
          source(
              "CrateDtoMapping",
              """
              @GenerateMapping
              public interface CrateDtoMapping extends MappingSpec<Crate, CrateDto> {}
              """);
      Compilation compilation = compile(crate, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(notesMention(compilation, "maps parse-only")).isFalse();
      Assertions.assertThat(generatedSource(compilation, "CrateDtoMappingImpl"))
          .contains("wire.getItems().addAll(domain.items());")
          .contains("asValidatedPrism()");
    }

    @Test
    @DisplayName("a domain component the bean has no getter for is an error, not a projection")
    void missingGetterIsAnError() {
      JavaFileObject thin =
          source(
              "ThinOrder",
              """
              public class ThinOrder {
                public String getId() { return "o-1"; }
                public String getContact() { return "ada@corp.example"; }
              }
              """);
      JavaFileObject spec =
          source(
              "ThinOrderMapping",
              """
              @GenerateMapping
              public interface ThinOrderMapping extends MappingSpec<Order, ThinOrder> {
                default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }
              }
              """);
      Compilation compilation = compile(EMAIL, EMAILS, ORDER, thin, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadNoteContaining("'ThinOrder' maps parse-only");
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Order.quantity' has no wire counterpart named 'quantity'");
    }

    @Test
    @DisplayName("setters the Impl cannot reach leave the bean parse-only, and the note says why")
    void unreachableSettersNameTheConstructor() {
      JavaFileObject locked =
          source(
              "LockedPerson",
              """
              public class LockedPerson {
                private String name;
                public LockedPerson(String name) { this.name = name; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          source(
              "LockedPersonMapping",
              """
              @GenerateMapping
              public interface LockedPersonMapping extends MappingSpec<Person, LockedPerson> {}
              """);
      Compilation compilation = compile(PERSON, locked, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "'LockedPerson' has setters, but no no-args constructor the generated"
                  + " Impl can call from package 'com.example.oneway', so nothing can write it.");
    }

    @Test
    @DisplayName("an Optional component bridges from a nullable getter, absence reading as empty")
    void optionalBridgesFromAGetter() throws ReflectiveOperationException {
      JavaFileObject memo =
          source("Memo", "public record Memo(String id, Optional<String> text) {}");
      JavaFileObject view =
          source(
              "MemoView",
              """
              public class MemoView {
                private final String id;
                private final String text;
                public MemoView(String id, String text) { this.id = id; this.text = text; }
                public String getId() { return id; }
                public String getText() { return text; }
              }
              """);
      JavaFileObject spec =
          source(
              "MemoViewMapping",
              """
              @GenerateMapping
              public interface MemoViewMapping extends MappingSpec<Memo, MemoView> {}
              """);
      Compilation compilation = compile(memo, view, spec);
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance(PKG + ".MemoViewMappingImpl");
      assertThatValidated(parse(impl, create(result, "MemoView", "m-1", null)))
          .isValid()
          .hasValue(create(result, "Memo", "m-1", Optional.empty()));
    }

    @Test
    @DisplayName("a derived field declared on a parse-only spec has nothing to fill")
    void localDerivedFieldIsRefused() {
      JavaFileObject spec =
          source(
              "VendorOrderMapping",
              """
              @GenerateMapping
              public interface VendorOrderMapping extends MappingSpec<Order, VendorOrder> {
                default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }

                default Getter<Order, String> supplierNote() { return Getter.of(Order::id); }
              }
              """);
      Compilation compilation = compile(EMAIL, EMAILS, ORDER, VENDOR_ORDER, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "derived field 'supplierNote' has nothing to fill: 'VendorOrder' maps parse-only.");
      assertThat(compilation)
          .hadErrorContaining("give 'VendorOrder' setters or a builder, so the mapping builds it");
    }

    @Test
    @DisplayName("an inherited derived field stays inert, so one vocabulary serves both directions")
    void inheritedDerivedFieldIsInert() {
      JavaFileObject vocabulary =
          source(
              "OrderVocabulary",
              """
              public interface OrderVocabulary {
                default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }

                default Getter<Order, String> label() {
                  return Getter.of(order -> order.id() + "#" + order.quantity());
                }
              }
              """);
      JavaFileObject parseOnly =
          source(
              "VendorOrderMapping",
              """
              @GenerateMapping
              public interface VendorOrderMapping
                  extends MappingSpec<Order, VendorOrder>, OrderVocabulary {}
              """);
      JavaFileObject buildOnly =
          source(
              "OrderRequestMapping",
              """
              @GenerateMapping
              public interface OrderRequestMapping
                  extends MappingSpec<Order, OrderRequest>, OrderVocabulary {}
              """);
      Compilation compilation =
          compile(
              EMAIL, EMAILS, ORDER, VENDOR_ORDER, ORDER_REQUEST, vocabulary, parseOnly, buildOnly);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "OrderRequestMappingImpl"))
          .contains("wire.setLabel(label().get(domain));");
    }

    @Test
    @DisplayName("a spec member named asValidatedParse collides with the parse-only surface")
    void asValidatedParseCollides() {
      JavaFileObject spec =
          source(
              "VendorOrderMapping",
              """
              @GenerateMapping
              public interface VendorOrderMapping extends MappingSpec<Order, VendorOrder> {
                default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }

                default String asValidatedParse() { return ""; }
              }
              """);
      Compilation compilation = compile(EMAIL, EMAILS, ORDER, VENDOR_ORDER, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "collides with the 'asValidatedParse' member the generated VendorOrderMappingImpl"
                  + " emits for this tier (a parse-only mapping)");
    }
  }

  @Nested
  @DisplayName("Build-only: a bean that can be written but declares no getter")
  class BuildOnly {

    @Test
    @DisplayName("emits build and asValidatedBuild() and no parse, derived fields included")
    void emitsTheBuildSurfaceOnly() throws ReflectiveOperationException {
      Compilation compilation = compile(EMAIL, EMAILS, ORDER, ORDER_REQUEST, ORDER_REQUEST_MAPPING);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "'OrderRequest' maps build-only: the generated Impl carries build and"
                  + " asValidatedBuild(), and no parse.");

      String generated = generatedSource(compilation, "OrderRequestMappingImpl");
      Assertions.assertThat(generated)
          .contains("public OrderRequest build(Order domain)")
          .contains("wire.setContact(contact().build(domain.contact()));")
          .contains("wire.setLabel(label().get(domain));")
          .contains("public ValidatedBuild<OrderRequest, Order> asValidatedBuild()")
          .contains("return ValidatedBuild.of(this::build);")
          .doesNotContain("parse(")
          .doesNotContain("asValidatedPrism");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance(PKG + ".OrderRequestMappingImpl");
      Object order =
          create(result, "Order", "o-1", create(result, "EmailAddress", "ada@corp.example"), 2);
      Assertions.assertThat(invoke(invoke(impl, "build", order), "describe"))
          .isEqualTo("o-1|ada@corp.example|2|o-1#2");
      Assertions.assertThat(
              invoke(invoke(invoke(impl, "asValidatedBuild"), "build", order), "describe"))
          .isEqualTo("o-1|ada@corp.example|2|o-1#2");
    }

    @Test
    @DisplayName("a builder-only bean narrower than the domain builds what it carries")
    void builderOnlyNarrowBean() throws ReflectiveOperationException {
      JavaFileObject ticket =
          source(
              "Ticket",
              """
              public final class Ticket {
                private final String id;
                private Ticket(String id) { this.id = id; }
                public String render() { return "ticket " + id; }
                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private String id;
                  public Builder id(String id) { this.id = id; return this; }
                  // Copies a whole ticket in: no property's setter.
                  public Builder from(Ticket other) { this.id = other.id; return this; }
                  public Builder mergeFrom(Builder other) { this.id = other.id; return this; }
                  public Ticket build() { return new Ticket(id); }
                }
              }
              """);
      JavaFileObject spec =
          source(
              "TicketMapping",
              """
              @GenerateMapping
              public interface TicketMapping extends MappingSpec<Order, Ticket> {}
              """);
      Compilation compilation = compile(EMAIL, ORDER, ticket, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'Ticket' maps build-only");
      Assertions.assertThat(generatedSource(compilation, "TicketMappingImpl"))
          .contains("var b = Ticket.builder();")
          .contains("b.id(domain.id());")
          .contains("return b.build();");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance(PKG + ".TicketMappingImpl");
      Object order =
          create(result, "Order", "o-1", create(result, "EmailAddress", "ada@corp.example"), 2);
      Assertions.assertThat(invoke(invoke(impl, "build", order), "render")).isEqualTo("ticket o-1");
    }

    @Test
    @DisplayName("a writer no domain component sources is an error that offers a derived field")
    void unsourcedWriterOffersADerivedField() {
      JavaFileObject spec =
          source(
              "OrderRequestMapping",
              """
              @GenerateMapping
              public interface OrderRequestMapping extends MappingSpec<Order, OrderRequest> {
                default ValidatedPrism<String, EmailAddress> contact() { return Emails.EMAIL; }
              }
              """);
      Compilation compilation = compile(EMAIL, EMAILS, ORDER, ORDER_REQUEST, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("build-only field 'OrderRequest.label' has no domain source.");
      assertThat(compilation)
          .hadErrorContaining(
              "declare a derived field 'default Getter<Order, String> label()' that computes it");
    }

    @Test
    @DisplayName("a primitive writer no domain component sources is not offered a derived field")
    void unsourcedPrimitiveWriterOffersNoDerivedField() {
      JavaFileObject request =
          source(
              "RankRequest",
              """
              public class RankRequest {
                public void setName(String name) {}
                public void setRank(int rank) {}
              }
              """);
      JavaFileObject spec =
          source(
              "RankRequestMapping",
              """
              @GenerateMapping
              public interface RankRequestMapping extends MappingSpec<Person, RankRequest> {}
              """);
      Compilation compilation = compile(PERSON, request, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("build-only field 'RankRequest.rank' has no domain source.");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(Locale.ROOT).contains("declare a derived field"));
    }

    @Test
    @DisplayName("a rename that leaves a same-named writer without a source is an error")
    void renameLeavesAWriterUnsourced() {
      JavaFileObject request =
          source(
              "PersonRequest",
              """
              public class PersonRequest {
                public void setName(String name) {}
                public void setFullName(String fullName) {}
              }
              """);
      JavaFileObject spec =
          source(
              "PersonRequestMapping",
              """
              @GenerateMapping
              public interface PersonRequestMapping extends MappingSpec<Person, PersonRequest> {
                @MapField(to = "fullName")
                String name();
              }
              """);
      Compilation compilation = compile(PERSON, request, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("domain component 'name' sources more than one wire component.");
      assertThat(compilation)
          .hadErrorContaining(
              "a @MapField rename moves its domain component to the wire component it names");
    }

    @Test
    @DisplayName("a bridged Optional writes only when present")
    void optionalBridgeWritesWhenPresent() {
      JavaFileObject memo =
          source("Memo", "public record Memo(String id, Optional<String> text) {}");
      JavaFileObject request =
          source(
              "MemoRequest",
              """
              public class MemoRequest {
                public void setId(String id) {}
                public void setText(String text) {}
              }
              """);
      JavaFileObject spec =
          source(
              "MemoRequestMapping",
              """
              @GenerateMapping
              public interface MemoRequestMapping extends MappingSpec<Memo, MemoRequest> {}
              """);
      Compilation compilation = compile(memo, request, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "MemoRequestMappingImpl"))
          .contains("domain.text().ifPresent(v -> wire.setText(v));");
    }

    @Test
    @DisplayName("a spec member named asValidatedBuild collides with the build-only surface")
    void asValidatedBuildCollides() {
      JavaFileObject spec =
          source(
              "LineRequestMapping",
              """
              @GenerateMapping
              public interface LineRequestMapping extends MappingSpec<Line, LineRequest> {
                default String asValidatedBuild() { return ""; }
              }
              """);
      Compilation compilation = compile(LINE, LINE_REQUEST, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "collides with the 'asValidatedBuild' member the generated LineRequestMappingImpl"
                  + " emits for this tier (a build-only mapping)");
    }
  }

  @Nested
  @DisplayName("Beans no direction fits")
  class Refusals {

    @Test
    @DisplayName("a bean whose getters and setters never share a name is refused, naming both")
    void disjointAccessorsAreRefused() {
      JavaFileObject misspelt =
          source(
              "Misspelt",
              """
              public class Misspelt {
                public String getName() { return null; }
                public void setNmae(String name) {}
              }
              """);
      JavaFileObject spec =
          source(
              "MisspeltMapping",
              """
              @GenerateMapping
              public interface MisspeltMapping extends MappingSpec<Person, Misspelt> {}
              """);
      Compilation compilation = compile(PERSON, misspelt, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'Misspelt' is not a usable bean-shaped wire: no property it reads is one it can"
                  + " write.");
      assertThat(compilation).hadErrorContaining("It reads [name] and writes [nmae].");
      Assertions.assertThat(notesMention(compilation, "maps parse-only")).isFalse();
    }

    @Test
    @DisplayName("a write-only bean with no reachable no-args constructor is refused, naming it")
    void writeOnlyBeanWithoutConstructorIsRefused() {
      JavaFileObject request =
          source(
              "ClosedRequest",
              """
              public class ClosedRequest {
                public ClosedRequest(String seed) {}
                public void setName(String name) {}
              }
              """);
      JavaFileObject spec =
          source(
              "ClosedRequestMapping",
              """
              @GenerateMapping
              public interface ClosedRequestMapping extends MappingSpec<Person, ClosedRequest> {}
              """);
      Compilation compilation = compile(PERSON, request, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'ClosedRequest' is not a usable bean-shaped wire: it has setters but no getters, and"
                  + " no no-args constructor the generated Impl can call from package"
                  + " 'com.example.oneway'.");
    }

    @Test
    @DisplayName("a bean with nothing to read or write is refused")
    void accessorlessBeanIsRefused() {
      JavaFileObject opaque = source("Opaque", "public class Opaque { private String name; }");
      JavaFileObject spec =
          source(
              "OpaqueMapping",
              """
              @GenerateMapping
              public interface OpaqueMapping extends MappingSpec<Person, Opaque> {}
              """);
      Compilation compilation = compile(PERSON, opaque, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'Opaque' is not a usable bean-shaped wire: it has no property to read or write.");
    }

    @Test
    @DisplayName("a bean read and written both ways carries no direction note")
    void twoWayBeanHasNoNote() {
      JavaFileObject dto =
          source(
              "PersonBean",
              """
              public class PersonBean {
                private String name;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          source(
              "PersonBeanMapping",
              """
              @GenerateMapping
              public interface PersonBeanMapping extends MappingSpec<Person, PersonBean> {}
              """);
      Compilation compilation = compile(PERSON, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(notesMention(compilation, "maps parse-only")).isFalse();
      Assertions.assertThat(notesMention(compilation, "maps build-only")).isFalse();
    }

    @Test
    @DisplayName("a sparse update refuses a read-only PATCH bean, which cannot carry absence")
    void sparseRefusesReadOnlyBean() {
      JavaFileObject view =
          source(
              "PersonView",
              """
              public class PersonView {
                public String getName() { return "Ada"; }
              }
              """);
      JavaFileObject spec =
          source(
              "PersonViewPatch",
              """
              @GenerateMapping
              public interface PersonViewPatch extends UpdateSpec<Person, PersonView> {}
              """);
      Compilation compilation = compile(PERSON, view, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'PersonView' has getters but no way to be written, so it cannot serve as a sparse"
                  + " update's PATCH bean.");
      assertThat(compilation).hadErrorContaining("or extend MappingSpec instead");
    }

    @Test
    @DisplayName("a sparse update refuses a write-only PATCH bean, which has nothing to read")
    void sparseRefusesWriteOnlyBean() {
      JavaFileObject request =
          source(
              "PersonRequest",
              """
              public class PersonRequest {
                public void setName(String name) {}
              }
              """);
      JavaFileObject spec =
          source(
              "PersonRequestPatch",
              """
              @GenerateMapping
              public interface PersonRequestPatch extends UpdateSpec<Person, PersonRequest> {}
              """);
      Compilation compilation = compile(PERSON, request, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'PersonRequest' declares no getters, so a sparse update has nothing to read from"
                  + " it.");
    }
  }

  @Nested
  @DisplayName("Nesting: a one-directional mapping serves the sites that use only its direction")
  class Nesting {

    @Test
    @DisplayName("a parse-only mapping lifts a parse-only one through every container, located")
    void parseOnlyNestsParseOnly() throws ReflectiveOperationException {
      JavaFileObject basket =
          source(
              "Basket",
              """
              public record Basket(
                  String id,
                  List<Item> items,
                  Optional<Item> featured,
                  Map<String, Item> byShelf,
                  Map<EmailAddress, Item> byOwner,
                  Item gift) {}
              """);
      JavaFileObject vendorBasket =
          source(
              "VendorBasket",
              """
              public class VendorBasket {
                private final String id;
                private final List<VendorItem> items;
                public VendorBasket(String id, List<VendorItem> items) {
                  this.id = id;
                  this.items = items;
                }
                public String getId() { return id; }
                public List<VendorItem> getItems() { return items; }
                public Optional<VendorItem> getFeatured() { return Optional.empty(); }
                public Map<String, VendorItem> getByShelf() { return Map.of(); }
                public Map<String, VendorItem> getByOwner() { return Map.of(); }
                // A whole mapping nests into a parse-only one as well.
                public ItemDto getGift() { return new ItemDto("gift", "gift@corp.example"); }
              }
              """);
      JavaFileObject spec =
          source(
              "VendorBasketMapping",
              """
              @GenerateMapping
              public interface VendorBasketMapping extends MappingSpec<Basket, VendorBasket> {
                @MapKey("byOwner")
                default ValidatedPrism<String, EmailAddress> byOwnerKey() { return Emails.EMAIL; }
              }
              """);
      Compilation compilation =
          compile(
              EMAIL,
              EMAILS,
              ITEM,
              VENDOR_ITEM,
              VENDOR_ITEM_MAPPING,
              ITEM_DTO,
              ITEM_MAPPING,
              basket,
              vendorBasket,
              spec);
      assertThat(compilation).succeeded();

      String generated = generatedSource(compilation, "VendorBasketMappingImpl");
      Assertions.assertThat(generated)
          .contains(
              "hkj$ifPresent(wire.getItems(),"
                  + " VendorItemMappingImpl.INSTANCE.asValidatedParse()::parseAll)")
          .contains("VendorItemMappingImpl.INSTANCE.asValidatedParse().parse(v)")
          .contains("VendorItemMappingImpl.INSTANCE.asValidatedParse()::parseValues")
          .contains(
              "byOwnerKey().parseEntries(m, VendorItemMappingImpl.INSTANCE.asValidatedParse())")
          .contains(
              "hkj$ifPresent(wire.getGift(), ItemMappingImpl.INSTANCE.asValidatedPrism()::parse)");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance(PKG + ".VendorBasketMappingImpl");
      Object wire =
          create(
              result,
              "VendorBasket",
              "b-1",
              List.of(
                  create(result, "VendorItem", "a", "a@corp.example"),
                  create(result, "VendorItem", "b", "nope")));
      assertThatValidated(parse(impl, wire))
          .isInvalid()
          .hasFieldErrors("items.1.owner: not an email address");
    }

    @Test
    @DisplayName("a build-only mapping lifts a build-only one through every container")
    void buildOnlyNestsBuildOnly() throws ReflectiveOperationException {
      JavaFileObject cart =
          source(
              "Cart",
              """
              public record Cart(
                  String id,
                  List<Line> lines,
                  Line[] extras,
                  Optional<Line> pinned,
                  Map<String, Line> byCode,
                  Map<EmailAddress, Line> byOwner,
                  Item gift) {}
              """);
      JavaFileObject cartRequest =
          source(
              "CartRequest",
              """
              public class CartRequest {
                private List<LineRequest> lines;
                public void setId(String id) {}
                public void setLines(List<LineRequest> lines) { this.lines = lines; }
                public void setExtras(LineRequest[] extras) {}
                public void setPinned(Optional<LineRequest> pinned) {}
                public void setByCode(Map<String, LineRequest> byCode) {}
                public void setByOwner(Map<String, LineRequest> byOwner) {}
                // A whole mapping nests into a build-only one as well.
                public void setGift(ItemDto gift) {}
                public List<LineRequest> lines() { return lines; }
              }
              """);
      JavaFileObject spec =
          source(
              "CartRequestMapping",
              """
              @GenerateMapping
              public interface CartRequestMapping extends MappingSpec<Cart, CartRequest> {
                @MapKey("byOwner")
                default ValidatedPrism<String, EmailAddress> byOwnerKey() { return Emails.EMAIL; }
              }
              """);
      Compilation compilation =
          compile(
              EMAIL,
              EMAILS,
              LINE,
              LINE_REQUEST,
              LINE_REQUEST_MAPPING,
              ITEM,
              ITEM_DTO,
              ITEM_MAPPING,
              cart,
              cartRequest,
              spec);
      assertThat(compilation).succeeded();

      String generated = generatedSource(compilation, "CartRequestMappingImpl");
      Assertions.assertThat(generated)
          .contains(
              "wire.setLines(LineRequestMappingImpl.INSTANCE.asValidatedBuild().buildAll(domain.lines()));")
          .contains(
              "wire.setExtras(LineRequestMappingImpl.INSTANCE.asValidatedBuild().buildAll(domain.extras(),"
                  + " LineRequest[]::new));")
          .contains(
              "wire.setPinned(domain.pinned().map(LineRequestMappingImpl.INSTANCE.asValidatedBuild()::build));")
          .contains(
              "wire.setByCode(LineRequestMappingImpl.INSTANCE.asValidatedBuild().buildValues(domain.byCode()));")
          .contains(
              "wire.setByOwner(byOwnerKey().buildEntries(domain.byOwner(),"
                  + " LineRequestMappingImpl.INSTANCE.asValidatedBuild()));")
          .contains(
              "wire.setGift(ItemMappingImpl.INSTANCE.asValidatedPrism().build(domain.gift()));");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance(PKG + ".CartRequestMappingImpl");
      Class<?> lineClass = result.loadClass(PKG + ".Line");
      Object lines = java.lang.reflect.Array.newInstance(lineClass, 0);
      Object cartValue =
          create(
              result,
              "Cart",
              "c-1",
              List.of(create(result, "Line", "apple", 3), create(result, "Line", "pear", 1)),
              lines,
              Optional.empty(),
              Map.of(),
              Map.of(),
              create(result, "Item", "gift", create(result, "EmailAddress", "gift@corp.example")));
      List<?> built = (List<?>) invoke(invoke(impl, "build", cartValue), "lines");
      Assertions.assertThat(built)
          .extracting(line -> invoke(line, "describe"))
          .containsExactly("apple x3", "pear x1");
    }

    @Test
    @DisplayName("a mapping that builds cannot nest a parse-only one, and the hint says why")
    void fullMappingRefusesParseOnly() {
      JavaFileObject shelf = source("Shelf", "public record Shelf(Item item) {}");
      JavaFileObject shelfDto = source("ShelfDto", "public record ShelfDto(VendorItem item) {}");
      JavaFileObject spec =
          source(
              "ShelfMapping",
              """
              @GenerateMapping
              public interface ShelfMapping extends MappingSpec<Shelf, ShelfDto> {}
              """);
      Compilation compilation =
          compile(EMAIL, EMAILS, ITEM, VENDOR_ITEM, VENDOR_ITEM_MAPPING, shelf, shelfDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'ShelfDto.item' has no usable source.");
      assertThat(compilation)
          .hadErrorContaining(
              "'VendorItemMapping' maps this pair but is parse-only (no build), so it cannot be"
                  + " nested in a mapping that builds and parses.");
    }

    @Test
    @DisplayName("a mapping that parses cannot nest a build-only one, and the hint says why")
    void fullMappingRefusesBuildOnly() {
      JavaFileObject holder = source("Holder", "public record Holder(Line line) {}");
      JavaFileObject holderDto =
          source("HolderDto", "public record HolderDto(LineRequest line) {}");
      JavaFileObject spec =
          source(
              "HolderMapping",
              """
              @GenerateMapping
              public interface HolderMapping extends MappingSpec<Holder, HolderDto> {}
              """);
      Compilation compilation =
          compile(LINE, LINE_REQUEST, LINE_REQUEST_MAPPING, holder, holderDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'LineRequestMapping' maps this pair but is build-only (no parse), so it cannot be"
                  + " nested in a mapping that builds and parses.");
    }

    @Test
    @DisplayName("a projection offers no build to nest, even into a mapping that only builds")
    void projectionDoesNotNestIntoBuildOnly() {
      JavaFileObject tag = source("Tag", "public record Tag(String label, int weight) {}");
      JavaFileObject summary = source("TagSummary", "public record TagSummary(String label) {}");
      JavaFileObject summaryMapping =
          source(
              "TagSummaryMapping",
              """
              @GenerateMapping
              public interface TagSummaryMapping extends MappingSpec<Tag, TagSummary> {}
              """);
      JavaFileObject box = source("Box", "public record Box(Tag tag) {}");
      JavaFileObject boxRequest =
          source(
              "BoxRequest",
              """
              public class BoxRequest {
                public void setTag(TagSummary tag) {}
              }
              """);
      JavaFileObject boxMapping =
          source(
              "BoxRequestMapping",
              """
              @GenerateMapping
              public interface BoxRequestMapping extends MappingSpec<Box, BoxRequest> {}
              """);
      Compilation compilation = compile(tag, summary, summaryMapping, box, boxRequest, boxMapping);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'TagSummaryMapping' maps this pair but is a projection, whose build is not offered"
                  + " for nesting, so it cannot be nested in a mapping that only builds.");
    }

    @Test
    @DisplayName("each one-directional mapping refuses the other direction's nested spec")
    void oneWayMappingsRefuseTheOtherWay() {
      JavaFileObject shelf = source("Shelf", "public record Shelf(Item item) {}");
      JavaFileObject shelfRequest =
          source(
              "ShelfRequest",
              """
              public class ShelfRequest {
                public void setItem(VendorItem item) {}
              }
              """);
      JavaFileObject shelfRequestMapping =
          source(
              "ShelfRequestMapping",
              """
              @GenerateMapping
              public interface ShelfRequestMapping extends MappingSpec<Shelf, ShelfRequest> {}
              """);
      JavaFileObject holder = source("Holder", "public record Holder(Line line) {}");
      JavaFileObject holderView =
          source(
              "HolderView",
              """
              public class HolderView {
                public LineRequest getLine() { return null; }
              }
              """);
      JavaFileObject holderViewMapping =
          source(
              "HolderViewMapping",
              """
              @GenerateMapping
              public interface HolderViewMapping extends MappingSpec<Holder, HolderView> {}
              """);
      Compilation compilation =
          compile(
              EMAIL,
              EMAILS,
              ITEM,
              VENDOR_ITEM,
              VENDOR_ITEM_MAPPING,
              LINE,
              LINE_REQUEST,
              LINE_REQUEST_MAPPING,
              shelf,
              shelfRequest,
              shelfRequestMapping,
              holder,
              holderView,
              holderViewMapping);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'VendorItemMapping' maps this pair but is parse-only (no build), so it cannot be"
                  + " nested in a mapping that only builds.");
      assertThat(compilation)
          .hadErrorContaining(
              "'LineRequestMapping' maps this pair but is build-only (no parse), so it cannot be"
                  + " nested in a mapping that only parses.");
    }

    @Test
    @DisplayName("a sparse update nests a parse-only mapping, since it only parses")
    void sparseUpdateNestsParseOnly() {
      JavaFileObject stock = source("Stock", "public record Stock(String id, Item item) {}");
      JavaFileObject patch =
          source(
              "StockPatch",
              """
              public class StockPatch {
                private String id;
                private VendorItem item;
                public String getId() { return id; }
                public void setId(String id) { this.id = id; }
                public VendorItem getItem() { return item; }
                public void setItem(VendorItem item) { this.item = item; }
              }
              """);
      JavaFileObject spec =
          source(
              "StockPatchMapping",
              """
              @GenerateMapping
              public interface StockPatchMapping extends UpdateSpec<Stock, StockPatch> {}
              """);
      Compilation compilation =
          compile(EMAIL, EMAILS, ITEM, VENDOR_ITEM, VENDOR_ITEM_MAPPING, stock, patch, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "StockPatchMappingImpl"))
          .contains("VendorItemMappingImpl.INSTANCE.asValidatedParse()::parse");
    }

    @Test
    @DisplayName("a merge fills from a parse-only mapping, and refuses a build-only one")
    void mergeFillsFromParseOnly() {
      JavaFileObject label = source("Label", "public record Label(String note) {}");
      JavaFileObject delivery =
          source("Delivery", "public record Delivery(String ref, VendorItem item) {}");
      JavaFileObject parcel =
          source("Parcel", "public record Parcel(String note, String ref, Item item) {}");
      JavaFileObject assembly =
          source(
              "ParcelAssembly",
              """
              @GenerateMerge
              public interface ParcelAssembly {
                Validated<NonEmptyList<FieldError>, Parcel> assemble(Label label, Delivery delivery);
              }
              """);
      Compilation compilation =
          compile(
              EMAIL,
              EMAILS,
              ITEM,
              VENDOR_ITEM,
              VENDOR_ITEM_MAPPING,
              label,
              delivery,
              parcel,
              assembly);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "ParcelAssemblyImpl"))
          .contains("VendorItemMappingImpl.INSTANCE.asValidatedParse()::parse");

      JavaFileObject order = source("Picking", "public record Picking(LineRequest line) {}");
      JavaFileObject held = source("Held", "public record Held(String note, Line line) {}");
      JavaFileObject refused =
          source(
              "HeldAssembly",
              """
              @GenerateMerge
              public interface HeldAssembly {
                Validated<NonEmptyList<FieldError>, Held> assemble(Label label, Picking picking);
              }
              """);
      Compilation refusedCompilation =
          compile(LINE, LINE_REQUEST, LINE_REQUEST_MAPPING, label, order, held, refused);
      assertThat(refusedCompilation).failed();
      assertThat(refusedCompilation)
          .hadErrorContaining(
              "'LineRequestMapping' maps this pair but is build-only (no parse), so it cannot fill"
                  + " a merge.");
    }

    @Test
    @DisplayName("sealed dispatch needs both directions of every subtype pair")
    void sealedDispatchRefusesParseOnly() {
      JavaFileObject shapes =
          source(
              "Shape",
              """
              public sealed interface Shape permits Shape.Circle {
                record Circle(String radius) implements Shape {}
              }
              """);
      JavaFileObject views =
          source(
              "ShapeView",
              """
              public sealed interface ShapeView permits ShapeView.CircleView {
                final class CircleView implements ShapeView {
                  private final String radius;
                  public CircleView(String radius) { this.radius = radius; }
                  public String getRadius() { return radius; }
                }
              }
              """);
      JavaFileObject circle =
          source(
              "CircleViewMapping",
              """
              @GenerateMapping
              public interface CircleViewMapping
                  extends MappingSpec<Shape.Circle, ShapeView.CircleView> {}
              """);
      JavaFileObject dispatch =
          source(
              "ShapeViewMapping",
              """
              @GenerateMapping
              public interface ShapeViewMapping extends MappingSpec<Shape, ShapeView> {}
              """);
      Compilation compilation = compile(shapes, views, circle, dispatch);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'CircleViewMapping' maps it but is parse-only (no build), so it cannot take part in"
                  + " dispatch.");
    }
  }
}
