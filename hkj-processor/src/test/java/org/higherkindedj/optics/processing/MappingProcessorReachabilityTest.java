// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A generated Impl is a top-level class in its spec's package, so every type the mapping crosses
 * has to be visible from there: the spec, its domain and wire, each component the wire carries on
 * both sides, and the same for a merge. A private type nested beside the spec, or a package-private
 * one from another package, is refused at the spec with a what/why/fix message, never left to fail
 * inside the generated file. A domain component the wire does not carry is carried over from the
 * domain without being named, so its type needs no visibility.
 */
@DisplayName("MappingProcessor - every type a mapping crosses is visible from the spec's package")
class MappingProcessorReachabilityTest {

  private static final String IMPORTS =
      """
      package com.example;

      import java.util.ArrayList;
      import java.util.List;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.Getter;
      import org.higherkindedj.optics.annotations.Flatten;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MapField;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.UpdateSpec;
      import org.higherkindedj.optics.validated.ValidatedPrism;

      """;

  private static final String WHY =
      " The generated Impl is a top-level class in the spec's package, where it names every type"
          + " the mapping crosses, so each has to be visible from there. ";

  /** A package-private holder class in {@code com.example}, its body nested inside it. */
  private static JavaFileObject holder(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name, IMPORTS + "final class " + name + " {\n" + body + "}\n");
  }

  /** A source file in its own package, written whole. */
  private static JavaFileObject source(String qualifiedName, String text) {
    return JavaFileObjects.forSourceString(qualifiedName, text);
  }

  private static Compilation compile(String options, JavaFileObject... sources) {
    return javac()
        .withProcessors(new MappingProcessor(), new MergeProcessor())
        .withOptions(List.of(options.split(" ")))
        .compile(sources);
  }

  @Nested
  @DisplayName("a type the spec's package cannot reach is refused at the spec")
  class Refused {

    /**
     * One compilation for every route: each holder declares one spec whose mapping crosses a type
     * nested {@code private} beside it, and the processor refuses each where it is declared.
     */
    private static final class Routes {
      static final Compilation COMPILATION =
          compile(
              "-Xlint:all,-processing",
              holder(
                  "Copy",
                  """
                  private record Sku(String value) {}
                  record Stock(Sku sku) {}
                  record StockDto(Sku sku) {}
                  @GenerateMapping interface StockMapping extends MappingSpec<Stock, StockDto> {}
                  """),
              holder(
                  "Leaf",
                  """
                  private record Sku(String value) {}
                  record Line(Sku sku) {}
                  record LineDto(String sku) {}
                  @GenerateMapping interface LineMapping extends MappingSpec<Line, LineDto> {
                    default ValidatedPrism<String, Sku> sku() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Sku(raw)), Sku::value);
                    }
                  }
                  """),
              holder(
                  "Lifted",
                  """
                  private record Sku(String value) {}
                  record Basket(List<Sku> skus) {}
                  record BasketDto(List<Sku> skus) {}
                  @GenerateMapping interface BasketMapping extends MappingSpec<Basket, BasketDto> {}
                  """),
              holder(
                  "WireOnly",
                  """
                  private record Code(String value) {}
                  record Label(String code) {}
                  record LabelDto(Code code) {}
                  @GenerateMapping interface LabelMapping extends MappingSpec<Label, LabelDto> {
                    default ValidatedPrism<Code, String> code() {
                      return ValidatedPrism.of(code -> Validated.validNel(code.value()), Code::new);
                    }
                  }
                  """),
              holder(
                  "BeanWire",
                  """
                  private record Code(String value) {}
                  record Tag(String code) {}
                  static final class TagBean {
                    private Code code;
                    public Code getCode() { return code; }
                    public void setCode(Code code) { this.code = code; }
                  }
                  @GenerateMapping interface TagMapping extends MappingSpec<Tag, TagBean> {
                    default ValidatedPrism<Code, String> code() {
                      return ValidatedPrism.of(code -> Validated.validNel(code.value()), Code::new);
                    }
                  }
                  """),
              holder(
                  "Flattened",
                  """
                  private record Grid(String ref) {}
                  record Spot(Grid grid, String street) {}
                  record Place(String name, Spot spot) {}
                  record PlaceDto(String name, Grid grid, String street) {}
                  @GenerateMapping interface PlaceMapping extends MappingSpec<Place, PlaceDto> {
                    @Flatten
                    Spot spot();
                  }
                  """),
              holder(
                  "Derived",
                  """
                  private record Stamp(String at) {}
                  record Order(String id) {}
                  record OrderDto(String id, Stamp stamp) {}
                  @GenerateMapping interface OrderMapping extends MappingSpec<Order, OrderDto> {
                    default Getter<Order, Stamp> stamp() {
                      return Getter.of(order -> new Stamp(order.id()));
                    }
                  }
                  """),
              holder(
                  "Projection",
                  """
                  private record Sku(String value) {}
                  record Parcel(Sku sku, String note) {}
                  record ParcelView(Sku sku) {}
                  @GenerateMapping interface ParcelMapping extends MappingSpec<Parcel, ParcelView> {}
                  """),
              holder(
                  "ParseOnly",
                  """
                  private record Sku(String value) {}
                  record Reading(Sku sku) {}
                  static final class ReadingBean {
                    private final Sku sku;
                    ReadingBean(Sku sku) { this.sku = sku; }
                    public Sku getSku() { return sku; }
                  }
                  @GenerateMapping
                  interface ReadingMapping extends MappingSpec<Reading, ReadingBean> {}
                  """),
              holder(
                  "ParseOnlyWire",
                  """
                  private record Code(String value) {}
                  record Meter(String code) {}
                  static final class MeterBean {
                    private final Code code;
                    MeterBean(Code code) { this.code = code; }
                    public Code getCode() { return code; }
                  }
                  @GenerateMapping interface MeterMapping extends MappingSpec<Meter, MeterBean> {
                    default ValidatedPrism<Code, String> code() {
                      return ValidatedPrism.of(code -> Validated.validNel(code.value()), Code::new);
                    }
                  }
                  """),
              holder(
                  "BuildOnly",
                  """
                  private record Sku(String value) {}
                  record Receipt(Sku sku) {}
                  static final class ReceiptBean {
                    private Sku sku;
                    public void setSku(Sku sku) { this.sku = sku; }
                  }
                  @GenerateMapping
                  interface ReceiptMapping extends MappingSpec<Receipt, ReceiptBean> {}
                  """),
              holder(
                  "Patch",
                  """
                  private record Sku(String value) {}
                  record Profile(Sku sku, String name) {}
                  static final class ProfilePatch {
                    private Sku sku;
                    public Sku getSku() { return sku; }
                    public void setSku(Sku sku) { this.sku = sku; }
                  }
                  @GenerateMapping
                  interface ProfileUpdate extends UpdateSpec<Profile, ProfilePatch> {}
                  """),
              holder(
                  "Payments",
                  """
                  sealed interface Pay permits Card, Cash {}
                  record Card(String number) implements Pay {}
                  private record Cash(Integer pence) implements Pay {}
                  sealed interface PayDto permits CardDto, CashDto {}
                  record CardDto(String number) implements PayDto {}
                  record CashDto(Integer pence) implements PayDto {}
                  @GenerateMapping interface CardMapping extends MappingSpec<Card, CardDto> {}
                  @GenerateMapping interface CashMapping extends MappingSpec<Cash, CashDto> {}
                  @GenerateMapping interface PayMapping extends MappingSpec<Pay, PayDto> {}
                  """),
              holder(
                  "HiddenSpec",
                  """
                  record Note(String text) {}
                  record NoteDto(String text) {}
                  @GenerateMapping private interface NoteMapping extends MappingSpec<Note, NoteDto> {}
                  """),
              holder(
                  "HiddenDomain",
                  """
                  private record Secret(String text) {}
                  record SecretDto(String text) {}
                  @GenerateMapping
                  interface SecretMapping extends MappingSpec<Secret, SecretDto> {}
                  """),
              holder(
                  "HiddenWire",
                  """
                  record Memo(String text) {}
                  private record MemoDto(String text) {}
                  @GenerateMapping interface MemoMapping extends MappingSpec<Memo, MemoDto> {}
                  """),
              holder(
                  "GenericArgument",
                  """
                  private record Sku(String value) {}
                  record Page<T>(List<T> items) {}
                  record PageDto<T>(List<T> items) {}
                  @GenerateMapping
                  interface SkuPageMapping extends MappingSpec<Page<Sku>, PageDto<Sku>> {}
                  """),
              holder(
                  "GenericBound",
                  """
                  private interface Kind {}
                  record Shelf<T>(List<T> items) {}
                  record ShelfDto<T>(List<T> items) {}
                  @GenerateMapping
                  interface ShelfMapping<T extends Kind> extends MappingSpec<Shelf<T>, ShelfDto<T>> {}
                  """),
              holder(
                  "PatchHead",
                  """
                  private record Draft(String title) {}
                  static final class DraftPatch {
                    private String title;
                    public String getTitle() { return title; }
                    public void setTitle(String title) { this.title = title; }
                  }
                  @GenerateMapping interface DraftUpdate extends UpdateSpec<Draft, DraftPatch> {}
                  """),
              holder(
                  "MergeComponent",
                  """
                  private record Sku(String value) {}
                  record Head(Sku sku) {}
                  record Body(String text) {}
                  record Card(Sku sku, String text) {}
                  @GenerateMerge interface CardMerge { Card merge(Head head, Body body); }
                  """),
              holder(
                  "MergeTarget",
                  """
                  record Head(String title) {}
                  record Body(String text) {}
                  private record Summary(String title, String text) {}
                  @GenerateMerge interface SummaryMerge { Summary merge(Head head, Body body); }
                  """),
              holder(
                  "MergeSource",
                  """
                  private record Head(String title) {}
                  record Body(String text) {}
                  record Page(String title, String text) {}
                  @GenerateMerge interface PageMerge { Page merge(Head head, Body body); }
                  """),
              holder(
                  "InferredCopy",
                  """
                  private record Sku(String value) {}
                  static final class Grid extends ArrayList<List<Sku>> {}
                  record Rack(Grid grid) {}
                  record RackDto(Grid grid) {}
                  @GenerateMapping interface RackMapping extends MappingSpec<Rack, RackDto> {}
                  """),
              holder(
                  "InferredPatch",
                  """
                  private record Sku(String value) {}
                  static final class Grid extends ArrayList<List<Sku>> {}
                  record Bay(Grid grid) {}
                  static final class BayPatch {
                    private Grid grid;
                    public Grid getGrid() { return grid; }
                    public void setGrid(Grid grid) { this.grid = grid; }
                  }
                  @GenerateMapping interface BayUpdate extends UpdateSpec<Bay, BayPatch> {}
                  """),
              holder(
                  "InferredMerge",
                  """
                  private record Sku(String value) {}
                  static final class Grid extends ArrayList<List<Sku>> {}
                  record Left(Grid grid) {}
                  record Right(String count) {}
                  record Floor(Grid grid, Integer count) {}
                  @GenerateMerge interface FloorMerge {
                    Validated<NonEmptyList<FieldError>, Floor> merge(Left left, Right right);

                    default ValidatedPrism<String, Integer> count() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(Integer.valueOf(raw)), String::valueOf);
                    }
                  }
                  """),
              holder(
                  "Unconverted",
                  """
                  private record Sku(String value) {}
                  record SkuDto(String value) {}
                  record Order(Sku sku) {}
                  record OrderDto(SkuDto sku) {}
                  @GenerateMapping interface OrderMapping extends MappingSpec<Order, OrderDto> {}
                  """),
              holder(
                  "BuiltBean",
                  """
                  record Tag(String code) {}
                  static final class TagBean {
                    private String code;
                    public String getCode() { return code; }
                    public static Builder builder() { return new Builder(); }
                    private static final class Builder {
                      private String code;
                      public Builder code(String code) { this.code = code; return this; }
                      public TagBean build() {
                        TagBean bean = new TagBean();
                        bean.code = code;
                        return bean;
                      }
                    }
                  }
                  @GenerateMapping interface TagMapping extends MappingSpec<Tag, TagBean> {}
                  """),
              holder(
                  "TwoHidden",
                  """
                  private record Sku(String value) {}
                  private record Code(String value) {}
                  record Pair(Sku sku, Code code) {}
                  record PairDto(Sku sku, Code code) {}
                  @GenerateMapping interface PairMapping extends MappingSpec<Pair, PairDto> {}
                  """));
    }

    private static void refuses(String what) {
      assertThat(Routes.COMPILATION).hadErrorContaining(what);
    }

    @Test
    @DisplayName("each spec is refused at its declaration, once for each type it cannot reach")
    void refusedAtEverySpecOnly() {
      assertThat(Routes.COMPILATION).failed();
      // One refusal per spec, bar two in Payments, the variant's own spec and the sealed spec
      // dispatching to it, and two in TwoHidden, one for each hidden type.
      assertThat(Routes.COMPILATION).hadErrorCount(30);
      Assertions.assertThat(Routes.COMPILATION.errors())
          .allSatisfy(
              error ->
                  Assertions.assertThat(error.getMessage(null))
                      .containsAnyOf("@GenerateMapping: ", "@GenerateMerge: ")
                      .contains("cannot be reached from 'com.example'."));
    }

    @Test
    @DisplayName("a component copied as it is, naming the type, the package and the fix")
    void identityCopy() {
      refuses(
          "@GenerateMapping: record component 'sku' of 'Stock' names 'Sku', which cannot be"
              + " reached from 'com.example'."
              + WHY
              + "Remove 'private' from 'Sku'.");
    }

    @Test
    @DisplayName("a component a default leaf converts")
    void defaultLeaf() {
      refuses("record component 'sku' of 'Line' names 'Sku', which cannot be reached from");
    }

    @Test
    @DisplayName("an element a container lifts")
    void liftedElement() {
      refuses("record component 'skus' of 'Basket' names 'Sku', which cannot be reached from");
    }

    @Test
    @DisplayName("a wire component whose domain side is visible")
    void wireComponent() {
      refuses("record component 'code' of 'LabelDto' names 'Code', which cannot be reached from");
    }

    @Test
    @DisplayName("a bean property")
    void beanProperty() {
      refuses("bean property 'code' of 'TagBean' names 'Code', which cannot be reached from");
    }

    @Test
    @DisplayName("a component inside a flattened group")
    void flattenedMember() {
      refuses("record component 'grid' of 'Spot' names 'Grid', which cannot be reached from");
    }

    @Test
    @DisplayName("a wire component a derived field fills")
    void derivedField() {
      refuses("record component 'stamp' of 'OrderDto' names 'Stamp', which cannot be reached");
    }

    @Test
    @DisplayName("a component a projection carries")
    void projectionCarried() {
      refuses("record component 'sku' of 'Parcel' names 'Sku', which cannot be reached from");
    }

    @Test
    @DisplayName("a component a parse-only or build-only bean mapping carries")
    void oneDirectionalBean() {
      refuses("record component 'sku' of 'Reading' names 'Sku', which cannot be reached from");
      refuses("bean property 'code' of 'MeterBean' names 'Code', which cannot be reached from");
      refuses("record component 'sku' of 'Receipt' names 'Sku', which cannot be reached from");
    }

    @Test
    @DisplayName("a component a PATCH covers")
    void patchCovered() {
      refuses("record component 'sku' of 'Profile' names 'Sku', which cannot be reached from");
    }

    @Test
    @DisplayName("a permitted subtype, at the sealed spec as well as its own")
    void permittedSubtype() {
      refuses(
          "permitted subtype 'Payments.Cash' of 'Pay' cannot be reached from 'com.example'."
              + WHY
              + "Remove 'private' from 'Cash'.");
      refuses("domain type 'Payments.Cash' cannot be reached from 'com.example'.");
    }

    @Test
    @DisplayName("the spec, its domain or its wire itself")
    void declaredTypes() {
      refuses(
          "spec 'NoteMapping' cannot be reached from 'com.example'."
              + WHY
              + "Remove 'private' from 'NoteMapping'.");
      refuses("domain type 'HiddenDomain.Secret' cannot be reached from 'com.example'.");
      refuses("wire type 'HiddenWire.MemoDto' cannot be reached from 'com.example'.");
      refuses("domain type 'PatchHead.Draft' cannot be reached from 'com.example'.");
    }

    @Test
    @DisplayName("a type argument of the domain, or a bound of the spec's type parameter")
    void genericArgumentAndBound() {
      refuses(
          "domain type 'GenericArgument.Page<GenericArgument.Sku>' names 'Sku', which cannot be"
              + " reached from 'com.example'.");
      refuses(
          "type parameter 'T' of 'ShelfMapping' names 'Kind', which cannot be reached from"
              + " 'com.example'.");
    }

    @Test
    @DisplayName("a merge's target, source or component")
    void merge() {
      refuses(
          "@GenerateMerge: target component 'Card.sku' names 'Sku', which cannot be reached from"
              + " 'com.example'."
              + WHY
              + "Remove 'private' from 'Sku'.");
      refuses(
          "@GenerateMerge: merge target 'MergeTarget.Summary' cannot be reached from"
              + " 'com.example'.");
      refuses(
          "@GenerateMerge: source parameter 'head' names 'Head', which cannot be reached from"
              + " 'com.example'.");
    }

    @Test
    @DisplayName("a hidden component a leaf or spec would convert, before either is asked for")
    void beforeClassification() {
      // Asked first, so the processor never offers a leaf over a type the Impl cannot name.
      refuses(
          "record component 'sku' of 'Order' names 'Sku', which cannot be reached from"
              + " 'com.example'.");
      Assertions.assertThat(Routes.COMPILATION.errors())
          .noneSatisfy(
              error -> Assertions.assertThat(error.getMessage(null)).contains("OrderDto.sku"));
    }

    @Test
    @DisplayName("an element type a container's own declaration names, wherever reads are scanned")
    void inferredElementType() {
      refuses("record component 'grid' of 'Rack' names 'Sku', which cannot be reached from");
      refuses("record component 'grid' of 'Bay' names 'Sku', which cannot be reached from");
      refuses("target component 'Floor.grid' names 'Sku', which cannot be reached from");
    }

    @Test
    @DisplayName("the builder a bean wire is written through")
    void beanBuilder() {
      refuses(
          "builder 'BuiltBean.TagBean.Builder' of 'TagBean' cannot be reached from 'com.example'."
              + WHY
              + "Remove 'private' from 'Builder'.");
    }

    @Test
    @DisplayName("each hidden type once, in one compilation")
    void everyHiddenType() {
      refuses("record component 'sku' of 'Pair' names 'Sku', which cannot be reached from");
      refuses("record component 'code' of 'Pair' names 'Code', which cannot be reached from");
    }
  }

  @Nested
  @DisplayName("a type the Impl never names needs no visibility")
  class CarriedOver {

    @Test
    @DisplayName("a projection drops it, or only a container no read scans declares it")
    void projectionDropsIt() throws ReflectiveOperationException {
      Compilation compilation =
          compile(
              "-Xlint:all,-processing -Werror",
              holder(
                  "Dropped",
                  """
                  private record Sku(String value) {}
                  record Parcel(String note, Sku sku) {}
                  record ParcelView(String note) {}
                  @GenerateMapping interface ParcelMapping extends MappingSpec<Parcel, ParcelView> {}

                  static String relabel(String note) {
                    Parcel parcel = new Parcel("old", new Sku("S1"));
                    return DroppedParcelMappingImpl.INSTANCE
                        .asLens()
                        .set(new ParcelView(note), parcel)
                        .toString();
                  }
                  """),
              holder(
                  "Checked",
                  """
                  private record Sku(String value) {}
                  record Parcel(Integer weight, Sku sku) {}
                  record ParcelView(String weight) {}
                  @GenerateMapping interface ParcelMapping extends MappingSpec<Parcel, ParcelView> {
                    default ValidatedPrism<String, Integer> weight() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(Integer.valueOf(raw)), String::valueOf);
                    }
                  }

                  static String reweigh(String weight) {
                    Parcel parcel = new Parcel(1, new Sku("S1"));
                    return CheckedParcelMappingImpl.INSTANCE
                        .patch(parcel, new ParcelView(weight))
                        .toString();
                  }
                  """),
              holder(
                  "Unscanned",
                  """
                  private record Sku(String value) {}
                  @SuppressWarnings("serial") // never serialised
                  static final class Grid extends ArrayList<List<Sku>> {}
                  record Rack(Grid grid, String name) {}
                  record RackView(Grid grid) {}
                  @GenerateMapping interface RackMapping extends MappingSpec<Rack, RackView> {}
                  static final class RackBean {
                    private Grid grid;
                    public void setGrid(Grid grid) { this.grid = grid; }
                  }
                  record Shelf(Grid grid) {}
                  @GenerateMapping interface ShelfMapping extends MappingSpec<Shelf, RackBean> {}
                  record Left(Grid grid) {}
                  record Right(String name) {}
                  @GenerateMerge interface RackMerge { Rack merge(Left left, Right right); }
                  """),
              holder(
                  "Merged",
                  """
                  private record Sku(String value) {}
                  record Head(String title, Sku sku) {}
                  record Body(String text) {}
                  record Page(String title, String text) {}
                  @GenerateMerge interface PageMerge { Page merge(Head head, Body body); }
                  """));
      assertThat(compilation).succeededWithoutWarnings();
      RuntimeCompilationHelper.CompiledResult result =
          new RuntimeCompilationHelper.CompiledResult(compilation);
      Assertions.assertThat(result.invokeStatic("com.example.Dropped", "relabel", "new"))
          .isEqualTo("Parcel[note=new, sku=Sku[value=S1]]");
      Assertions.assertThat(result.invokeStatic("com.example.Checked", "reweigh", "7"))
          .isEqualTo("Valid(Parcel[weight=7, sku=Sku[value=S1]])");
    }
  }

  @Nested
  @DisplayName("the fix it offers can be followed")
  class FixLines {

    private static final JavaFileObject STOCK_SHELF =
        source(
            "com.example.stock.Shelf",
            """
            package com.example.stock;

            public final class Shelf {
              private record Hidden(String value) {}
              public record Item(Hidden hidden) {}
              public record ItemDto(Hidden hidden) {}
            }
            """);

    private static final JavaFileObject STOCK_BATCH =
        source(
            "com.example.stock.Batch",
            """
            package com.example.stock;

            record Batch(String code) {}
            """);

    private static final JavaFileObject STOCK_RACK =
        source(
            "com.example.stock.Rack",
            """
            package com.example.stock;

            final class Rack {
              public record Slot(String code) {}
            }
            """);

    private static final JavaFileObject STOCK_LOT =
        source(
            "com.example.stock.Lot",
            """
            package com.example.stock;

            public record Lot(Batch batch) {}
            """);

    private static final JavaFileObject STOCK_LOT_DTO =
        source(
            "com.example.stock.LotDto",
            """
            package com.example.stock;

            public record LotDto(Batch batch) {}
            """);

    @Test
    @DisplayName("each is named by what hides the type")
    void offered() {
      Compilation compilation =
          compile(
              "-Xlint:all,-processing",
              holder(
                  "Vault",
                  """
                  private static final class Box {
                    private record Key(String value) {}
                  }
                  record Door(Box.Key key) {}
                  record DoorDto(Box.Key key) {}
                  @GenerateMapping interface DoorMapping extends MappingSpec<Door, DoorDto> {}
                  """),
              holder(
                  "Renamed",
                  """
                  private record Sku(String value) {}
                  record Item(Sku sku) {}
                  record ItemDto(Sku code) {}
                  @GenerateMapping interface ItemMapping extends MappingSpec<Item, ItemDto> {
                    @MapField(to = "code")
                    Sku sku();
                  }
                  """),
              STOCK_SHELF,
              source(
                  "com.example.ShelfItemMapping",
                  """
                  package com.example;

                  import com.example.stock.Shelf;
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  interface ShelfItemMapping extends MappingSpec<Shelf.Item, Shelf.ItemDto> {}
                  """),
              STOCK_RACK,
              source(
                  "com.example.stock.Shipment",
                  """
                  package com.example.stock;

                  public record Shipment(Rack.Slot slot) {}
                  """),
              source(
                  "com.example.stock.ShipmentDto",
                  """
                  package com.example.stock;

                  public record ShipmentDto(Rack.Slot slot) {}
                  """),
              source(
                  "com.example.ShipmentMapping",
                  """
                  package com.example;

                  import com.example.stock.Shipment;
                  import com.example.stock.ShipmentDto;
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  interface ShipmentMapping extends MappingSpec<Shipment, ShipmentDto> {}
                  """),
              STOCK_BATCH,
              STOCK_LOT,
              STOCK_LOT_DTO,
              source(
                  "com.example.LotMapping",
                  """
                  package com.example;

                  import com.example.stock.Lot;
                  import com.example.stock.LotDto;
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  interface LotMapping extends MappingSpec<Lot, LotDto> {}
                  """));
      assertThat(compilation).hadErrorCount(5);
      assertThat(compilation)
          .hadErrorContaining(
              "record component 'key' of 'Door' names 'Key', which cannot be reached from"
                  + " 'com.example'."
                  + WHY
                  + "Remove 'private' from 'Box' and 'Key'.");
      // A member the spec declares is held to the same rule, and offered the same fix.
      assertThat(compilation)
          .hadErrorContaining(
              "@MapField method 'sku' names 'Sku', which cannot be reached from 'com.example'."
                  + WHY
                  + "Remove 'private' from 'Sku'.");
      assertThat(compilation)
          .hadErrorContaining(
              "record component 'hidden' of 'Item' names 'Hidden', which cannot be reached from"
                  + " 'com.example'."
                  + WHY
                  + "Make 'Hidden' public.");
      assertThat(compilation)
          .hadErrorContaining(
              "record component 'slot' of 'Shipment' names 'Slot', which cannot be reached from"
                  + " 'com.example'."
                  + WHY
                  + "Make 'Rack', which encloses 'Slot', public, or declare the spec in"
                  + " 'com.example.stock'.");
      assertThat(compilation)
          .hadErrorContaining(
              "record component 'batch' of 'Lot' names 'Batch', which cannot be reached from"
                  + " 'com.example'."
                  + WHY
                  + "Make 'Batch' public, or declare the spec in 'com.example.stock'.");
    }

    @Test
    @DisplayName("a spec in the unnamed package is told so")
    void unnamedPackage() {
      Compilation compilation =
          compile(
              "-Xlint:all,-processing",
              source(
                  "Shop",
                  """
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  final class Shop {
                    private record Sku(String value) {}
                    record Item(Sku sku) {}
                    record ItemDto(Sku sku) {}
                    @GenerateMapping interface ItemMapping extends MappingSpec<Item, ItemDto> {}
                  }
                  """));
      assertThat(compilation)
          .hadErrorContaining(
              "record component 'sku' of 'Item' names 'Sku', which cannot be reached from the"
                  + " unnamed package.");
    }

    @Test
    @DisplayName("each compiles once followed, with nothing to say, and maps")
    void followed() throws ReflectiveOperationException {
      Compilation compilation =
          compile(
              "-Xlint:all,-processing -Werror",
              holder(
                  "Vault",
                  """
                  static final class Box {
                    record Key(String value) {}
                  }
                  record Door(Box.Key key) {}
                  record DoorDto(Box.Key key) {}
                  @GenerateMapping interface DoorMapping extends MappingSpec<Door, DoorDto> {}

                  static Validated<NonEmptyList<FieldError>, Door> open(String key) {
                    return VaultDoorMappingImpl.INSTANCE.parse(new DoorDto(new Box.Key(key)));
                  }
                  """),
              source(
                  "com.example.stock.Shelf",
                  """
                  package com.example.stock;

                  public final class Shelf {
                    public record Hidden(String value) {}
                    public record Item(Hidden hidden) {}
                    public record ItemDto(Hidden hidden) {}
                  }
                  """),
              source(
                  "com.example.ShelfItemMapping",
                  """
                  package com.example;

                  import com.example.stock.Shelf;
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  interface ShelfItemMapping extends MappingSpec<Shelf.Item, Shelf.ItemDto> {}
                  """),
              source(
                  "com.example.stock.Rack",
                  """
                  package com.example.stock;

                  public final class Rack {
                    public record Slot(String code) {}
                  }
                  """),
              source(
                  "com.example.stock.Shipment",
                  """
                  package com.example.stock;

                  public record Shipment(Rack.Slot slot) {}
                  """),
              source(
                  "com.example.stock.ShipmentDto",
                  """
                  package com.example.stock;

                  public record ShipmentDto(Rack.Slot slot) {}
                  """),
              source(
                  "com.example.ShipmentMapping",
                  """
                  package com.example;

                  import com.example.stock.Shipment;
                  import com.example.stock.ShipmentDto;
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  interface ShipmentMapping extends MappingSpec<Shipment, ShipmentDto> {}
                  """),
              STOCK_BATCH,
              STOCK_LOT,
              STOCK_LOT_DTO,
              source(
                  "com.example.stock.LotMapping",
                  """
                  package com.example.stock;

                  import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                  import org.higherkindedj.hkt.validated.FieldError;
                  import org.higherkindedj.hkt.validated.Validated;
                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  public interface LotMapping extends MappingSpec<Lot, LotDto> {
                    static Validated<NonEmptyList<FieldError>, Lot> receive(String code) {
                      return LotMappingImpl.INSTANCE.parse(new LotDto(new Batch(code)));
                    }
                  }
                  """));
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(compilation.diagnostics())
          .as("notes on the followed fix lines")
          .noneMatch(d -> d.getKind() == Diagnostic.Kind.NOTE);
      RuntimeCompilationHelper.CompiledResult result =
          new RuntimeCompilationHelper.CompiledResult(compilation);
      Assertions.assertThat(result.invokeStatic("com.example.Vault", "open", "K1"))
          .hasToString("Valid(Door[key=Key[value=K1]])");
      Assertions.assertThat(result.invokeStatic("com.example.stock.LotMapping", "receive", "B7"))
          .hasToString("Valid(Lot[batch=Batch[code=B7]])");
    }
  }
}
