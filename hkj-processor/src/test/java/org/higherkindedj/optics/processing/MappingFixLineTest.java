// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A refusal's fix line is code the author can write and have work. Each group compiles the refused
 * shapes and asserts the line each refusal offers, then compiles every one of those lines pasted
 * into its spec, under {@code -Xlint:all -Werror} and with nothing else to say, and runs the result
 * where the line promises a behaviour. A fix line that does not compile, leads to another refusal,
 * or compiles and maps something else fails here.
 */
@DisplayName("MappingProcessor - fix lines that can be followed")
class MappingFixLineTest {

  private static final String IMPORTS =
      """
      package com.example;

      import java.util.*;
      import org.higherkindedj.hkt.validated.*;
      import org.higherkindedj.optics.annotations.*;
      import org.higherkindedj.optics.validated.*;

      """;

  private static final String UUID_ERROR =
      "not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)";

  private static final String LOCALE_ERROR = "not a BCP 47 language tag (expected e.g. en-GB)";

  private static JavaFileObject source(String body) {
    return JavaFileObjects.forSourceString("com.example.Model", IMPORTS + body);
  }

  private static Compilation compile(String body) {
    return javac().withProcessors(new MappingProcessor()).compile(source(body));
  }

  /** Compiles followed fix lines, asserting they succeed with nothing to say. */
  private static RuntimeCompilationHelper.CompiledResult compileFollowed(String body) {
    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor())
            // every type sits in one file, which the generated Impls then name from their own
            .withOptions("-Xlint:all,-processing,-auxiliaryclass", "-Werror")
            .compile(source(body));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(compilation.diagnostics())
        .as("notes on the followed fix lines")
        .noneMatch(d -> d.getKind() == Diagnostic.Kind.NOTE);
    return new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  /** Runs a static method of the {@code Probe} class a followed body declares. */
  @SuppressWarnings("unchecked") // reflective call into the generated Impl
  private static Validated<NonEmptyList<FieldError>, Object> probe(
      RuntimeCompilationHelper.CompiledResult result, String method)
      throws ReflectiveOperationException {
    return (Validated<NonEmptyList<FieldError>, Object>)
        result.invokeStatic("com.example.Probe", method);
  }

  @Nested
  @DisplayName("a whole-component leaf beside a @MapKey leaf")
  class ShadowedKeyLeaf {

    @Test
    @DisplayName("is refused on either tier, offering only what works once followed")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Ids(Map<Locale, UUID> ids, Map<Locale, String> notes) {}
              record IdsDto(Map<String, String> ids, Map<String, String> notes) {}
              @GenerateMapping
              interface IdsMapping extends MappingSpec<Ids, IdsDto> {
                @MapKey("notes")
                default ValidatedPrism<String, Locale> notesKey() { return StandardCodecs.locale(); }
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<Map<String, String>, Map<Locale, UUID>> ids() {
                  throw new UnsupportedOperationException();
                }
              }

              record Notes(Map<Locale, String> notes) {}
              record NotesDto(Map<String, String> notes) {}
              @GenerateMapping
              interface NotesMapping extends MappingSpec<Notes, NotesDto> {
                @MapKey("notes")
                default ValidatedPrism<String, Locale> notesKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<Map<String, String>, Map<Locale, String>> notes() {
                  throw new UnsupportedOperationException();
                }
              }

              record Encoded(Map<Locale, String> tags) {}
              record EncodedDto(String tags) {}
              @GenerateMapping
              interface EncodedMapping extends MappingSpec<Encoded, EncodedDto> {
                @MapKey("tags")
                default ValidatedPrism<String, Locale> tagsKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<String, Map<Locale, String>> tags() {
                  throw new UnsupportedOperationException();
                }
              }

              record Counted(Map<Locale, String> counts) {}
              record CountedDto(Map<String, String> counts) {}
              @GenerateMapping
              interface CountedMapping extends MappingSpec<Counted, CountedDto> {
                @MapKey("counts")
                default ValidatedPrism<Integer, Locale> countsKey() {
                  throw new UnsupportedOperationException();
                }
                default ValidatedPrism<Map<String, String>, Map<Locale, String>> counts() {
                  throw new UnsupportedOperationException();
                }
              }

              interface WholeIds {
                default ValidatedPrism<Map<String, String>, Map<Locale, UUID>> ids() {
                  throw new UnsupportedOperationException();
                }
              }
              record Shared(Map<Locale, UUID> ids) {}
              record SharedDto(Map<String, String> ids) {}
              @GenerateMapping
              interface SharedMapping extends MappingSpec<Shared, SharedDto>, WholeIds {
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
              }

              record Boxed(Optional<Map<Locale, UUID>> ids) {}
              class BoxedBean {
                private Map<String, String> ids;
                public Map<String, String> getIds() { return ids; }
                public void setIds(Map<String, String> ids) { this.ids = ids; }
              }
              @GenerateMapping
              interface BoxedMapping extends MappingSpec<Boxed, BoxedBean> {
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<Map<String, String>, Optional<Map<Locale, UUID>>> ids() {
                  throw new UnsupportedOperationException();
                }
              }

              record Bridged(Optional<Map<Locale, UUID>> ids) {}
              record BridgedDto(Map<String, String> ids) {}
              @GenerateMapping
              interface BridgedMapping extends MappingSpec<Bridged, BridgedDto> {
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
                @OptionalBridge
                default ValidatedPrism<Map<String, String>, Map<Locale, UUID>> ids() {
                  throw new UnsupportedOperationException();
                }
              }

              record Labelled(Optional<Map<Locale, String>> labels) {}
              record LabelledDto(Map<String, String> labels) {}
              @GenerateMapping
              interface LabelledMapping extends MappingSpec<Labelled, LabelledDto> {
                @MapKey("labels")
                default ValidatedPrism<String, Locale> labelsKey() { return StandardCodecs.locale(); }
                @OptionalBridge
                default ValidatedPrism<Map<String, String>, Map<Locale, String>> labels() {
                  throw new UnsupportedOperationException();
                }
              }

              record Tags(Map<Locale, String> tags) {}
              class TagsPatch {
                private Map<String, String> tags;
                public Map<String, String> getTags() { return tags; }
                public void setTags(Map<String, String> tags) { this.tags = tags; }
              }
              @GenerateMapping
              interface TagsUpdate extends UpdateSpec<Tags, TagsPatch> {
                @MapKey("tags")
                default ValidatedPrism<String, Locale> tagsKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<Map<String, String>, Map<Locale, String>> tags() {
                  throw new UnsupportedOperationException();
                }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@MapKey(\"ids\") leaf 'idsKey()' never runs: 'ids()' is a leaf over the whole"
                  + " component 'ids'. A whole-component leaf is tried before a Map's key and value"
                  + " leaves.");
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'ids()' with the value leaf 'default ValidatedPrism<java.lang.String,"
                  + " java.util.UUID> ids()', which converts the values while 'idsKey()' converts"
                  + " the keys, or remove 'idsKey()' to keep converting 'ids' whole.");
      assertThat(compilation)
          .hadErrorContaining(
              "Remove 'notes()', so the keys convert through 'notesKey()' and the values copy, or"
                  + " remove 'notesKey()' to keep converting 'notes' whole.");
      // a wire that is no Map, and a key leaf over another wire key, leave the key leaf nothing
      // to convert, so only removing it is offered
      assertThat(compilation)
          .hadErrorContaining("Remove 'tagsKey()' to keep converting 'tags' whole.");
      assertThat(compilation)
          .hadErrorContaining("Remove 'countsKey()' to keep converting 'counts' whole.");
      // an inherited whole leaf cannot be replaced on this spec
      assertThat(compilation)
          .hadErrorContaining(
              "'ids()' (inherited from 'WholeIds') is a leaf over the whole component 'ids'.");
      assertThat(compilation)
          .hadErrorContaining("Remove 'idsKey()' to keep converting 'ids' whole.");
      // on a bean the whole Optional gives way to the bridge, and on a record the leaf keeps the
      // annotation the bridge needs there
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'ids()' with the value leaf '@OptionalBridge default"
                  + " ValidatedPrism<java.lang.String, java.util.UUID> ids()'");
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'labels()' with the marker '@OptionalBridge"
                  + " java.util.Optional<java.util.Map<java.util.Locale,java.lang.String>>"
                  + " labels();', so the keys convert through 'labelsKey()' and the values copy");
      assertThat(compilation)
          .hadErrorContaining(
              "Remove 'tags()', so the keys convert through 'tagsKey()' and the values copy");
      Assertions.assertThat(compilation.errors()).hasSize(9);
    }

    @Test
    @DisplayName("each line it offers compiles, and the value leaf leaves the keys validated")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Ids(Map<Locale, UUID> ids, Map<Locale, String> notes) {}
              record IdsDto(Map<String, String> ids, Map<String, String> notes) {}
              @GenerateMapping
              interface IdsMapping extends MappingSpec<Ids, IdsDto> {
                @MapKey("notes")
                default ValidatedPrism<String, Locale> notesKey() { return StandardCodecs.locale(); }
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<java.lang.String, java.util.UUID> ids() {
                  return StandardCodecs.uuid();
                }
              }

              record Notes(Map<Locale, String> notes) {}
              record NotesDto(Map<String, String> notes) {}
              @GenerateMapping
              interface NotesMapping extends MappingSpec<Notes, NotesDto> {
                @MapKey("notes")
                default ValidatedPrism<String, Locale> notesKey() { return StandardCodecs.locale(); }
              }

              record Encoded(Map<Locale, String> tags) {}
              record EncodedDto(String tags) {}
              @GenerateMapping
              interface EncodedMapping extends MappingSpec<Encoded, EncodedDto> {
                default ValidatedPrism<String, Map<Locale, String>> tags() {
                  return ValidatedPrism.of(raw -> Validated.validNel(Map.of()), tags -> "");
                }
              }

              record Counted(Map<Locale, String> counts) {}
              record CountedDto(Map<String, String> counts) {}
              @GenerateMapping
              interface CountedMapping extends MappingSpec<Counted, CountedDto> {
                default ValidatedPrism<Map<String, String>, Map<Locale, String>> counts() {
                  return ValidatedPrism.of(raw -> Validated.validNel(Map.of()), counts -> Map.of());
                }
              }

              interface WholeIds {
                default ValidatedPrism<Map<String, String>, Map<Locale, UUID>> ids() {
                  return ValidatedPrism.of(raw -> Validated.validNel(Map.of()), ids -> Map.of());
                }
              }
              record Shared(Map<Locale, UUID> ids) {}
              record SharedDto(Map<String, String> ids) {}
              @GenerateMapping
              interface SharedMapping extends MappingSpec<Shared, SharedDto>, WholeIds {}

              record Boxed(Optional<Map<Locale, UUID>> ids) {}
              class BoxedBean {
                private Map<String, String> ids;
                public Map<String, String> getIds() { return ids; }
                public void setIds(Map<String, String> ids) { this.ids = ids; }
              }
              @GenerateMapping
              interface BoxedMapping extends MappingSpec<Boxed, BoxedBean> {
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
                default ValidatedPrism<java.lang.String, java.util.UUID> ids() {
                  return StandardCodecs.uuid();
                }
              }

              record Bridged(Optional<Map<Locale, UUID>> ids) {}
              record BridgedDto(Map<String, String> ids) {}
              @GenerateMapping
              interface BridgedMapping extends MappingSpec<Bridged, BridgedDto> {
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
                @OptionalBridge
                default ValidatedPrism<java.lang.String, java.util.UUID> ids() {
                  return StandardCodecs.uuid();
                }
              }

              record Labelled(Optional<Map<Locale, String>> labels) {}
              record LabelledDto(Map<String, String> labels) {}
              @GenerateMapping
              interface LabelledMapping extends MappingSpec<Labelled, LabelledDto> {
                @MapKey("labels")
                default ValidatedPrism<String, Locale> labelsKey() { return StandardCodecs.locale(); }
                @OptionalBridge
                java.util.Optional<java.util.Map<java.util.Locale,java.lang.String>> labels();
              }

              record Tags(Map<Locale, String> tags) {}
              class TagsPatch {
                private Map<String, String> tags;
                public Map<String, String> getTags() { return tags; }
                public void setTags(Map<String, String> tags) { this.tags = tags; }
              }
              @GenerateMapping
              interface TagsUpdate extends UpdateSpec<Tags, TagsPatch> {
                @MapKey("tags")
                default ValidatedPrism<String, Locale> tagsKey() { return StandardCodecs.locale(); }
              }

              // An inherited key leaf is shared vocabulary: a spec mapping the component whole
              // leaves it inert rather than refused.
              interface LocaleKeys {
                @MapKey("ids")
                default ValidatedPrism<String, Locale> idsKey() { return StandardCodecs.locale(); }
              }
              @GenerateMapping
              interface WholeIdsMapping extends MappingSpec<Shared, SharedDto>, LocaleKeys {
                default ValidatedPrism<Map<String, String>, Map<Locale, UUID>> ids() {
                  return ValidatedPrism.of(raw -> Validated.validNel(Map.of()), ids -> Map.of());
                }
              }

              final class Probe {
                static Object ids() {
                  return IdsMappingImpl.INSTANCE.parse(
                      new IdsDto(
                          Map.of("not a locale!", "0f8fad5b-d9cb-469f-a165-70867728950e"),
                          Map.of()));
                }

                static Object bridged() {
                  return BridgedMappingImpl.INSTANCE.parse(new BridgedDto(Map.of("en", "nope")));
                }
              }
              """);
      assertThatValidated(probe(result, "ids"))
          .hasFieldErrors("ids.not a locale!: " + LOCALE_ERROR);
      assertThatValidated(probe(result, "bridged")).hasFieldErrors("ids.en: " + UUID_ERROR);
    }
  }

  @Nested
  @DisplayName("an element leaf or spec on a container that cannot lift")
  class ContainerThatCannotLift {

    private static final String RULE =
        "Element lifting, through a leaf or a spec, reaches one level into a component both sides"
            + " declare as the same container, named exactly: List, Set, Optional, an array of a"
            + " reference type, or a Map, whose values lift, each over an element type named rather"
            + " than a wildcard.";

    @Test
    @DisplayName("names the lifting rule and the declaration that would lift")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Tagged(SortedSet<UUID> ids) {}
              record TaggedDto(SortedSet<String> ids) {}
              @GenerateMapping
              interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {
                default ValidatedPrism<String, UUID> ids() { return StandardCodecs.uuid(); }
              }

              record Customer(String name) {}
              record CustomerDto(String name) {}
              @GenerateMapping
              interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
              record Order(ArrayList<Customer> customers) {}
              record OrderDto(ArrayList<CustomerDto> customers) {}
              @GenerateMapping
              interface OrderMapping extends MappingSpec<Order, OrderDto> {}

              record Names(List<? extends CharSequence> names) {}
              record NamesDto(ArrayList<CharSequence> names) {}
              @GenerateMapping
              interface NamesMapping extends MappingSpec<Names, NamesDto> {}

              record Maybe(Optional<List<UUID>> ids) {}
              record MaybeDto(Optional<List<String>> ids) {}
              @GenerateMapping
              interface MaybeMapping extends MappingSpec<Maybe, MaybeDto> {
                default ValidatedPrism<String, UUID> ids() { return StandardCodecs.uuid(); }
              }

              record Pool(Collection<UUID> ids) {}
              record PoolDto(Collection<String> ids) {}
              @GenerateMapping
              interface PoolMapping extends MappingSpec<Pool, PoolDto> {}

              record Batch(ArrayList<UUID> ids) {}
              record BatchDto(ArrayList<String> ids) {}
              @GenerateMapping
              interface BatchMapping extends MappingSpec<Batch, BatchDto> {}

              record Loose(List<?> ids) {}
              record LooseDto(ArrayList<String> ids) {}
              @GenerateMapping
              interface LooseMapping extends MappingSpec<Loose, LooseDto> {}

              record Pack<T>(ArrayList<T> ids) {}
              record PackDto<T>(ArrayList<T> ids) {}
              @GenerateMapping
              interface PackMapping extends MappingSpec<Pack<UUID>, PackDto<String>> {
                default ValidatedPrism<String, UUID> ids() { return StandardCodecs.uuid(); }
              }

              record Wrap<T>(T value) {}
              record WrapDto<TD>(TD value) {}
              @GenerateMapping
              interface WrapMapping<T, TD> extends MappingSpec<Wrap<T>, WrapDto<TD>> {
                ValidatedPrism<TD, T> value();
              }
              record Crate(ArrayList<Wrap<UUID>> items) {}
              record CrateDto(ArrayList<WrapDto<String>> items) {}
              @GenerateMapping
              interface CrateMapping extends MappingSpec<Crate, CrateDto> {}

              record Stamped(SortedSet<UUID> ids) {}
              class StampedPatch {
                private SortedSet<String> ids;
                public SortedSet<String> getIds() { return ids; }
                public void setIds(SortedSet<String> ids) { this.ids = ids; }
              }
              @GenerateMapping
              interface StampedUpdate extends UpdateSpec<Stamped, StampedPatch> {
                default ValidatedPrism<String, UUID> ids() { return StandardCodecs.uuid(); }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining(RULE);
      // the near miss spells the leaves that would do, and the fix replaces it
      assertThat(compilation)
          .hadErrorContaining(
              "returning exactly ValidatedPrism<java.util.SortedSet<java.lang.String>,"
                  + " java.util.SortedSet<java.util.UUID>> (wire first, domain second).");
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'ids()' with 'default ValidatedPrism<java.util.SortedSet<java.lang.String>,"
                  + " java.util.SortedSet<java.util.UUID>> ids()', or declare the component as"
                  + " java.util.Set<java.util.UUID> on 'Tagged' and as java.util.Set<java.lang.String>"
                  + " on 'TaggedDto', which then lifts through the leaf 'ids()'.");
      assertThat(compilation)
          .hadErrorContaining(
              "or declare the component as java.util.List<com.example.Customer> on 'Order' and as"
                  + " java.util.List<com.example.CustomerDto> on 'OrderDto', which then lifts"
                  + " through 'CustomerMapping'.");
      assertThat(compilation)
          .hadErrorContaining(
              "or declare the component as java.util.List<java.lang.CharSequence> on 'Names' and"
                  + " as java.util.List<java.lang.CharSequence> on 'NamesDto', which then copies.");
      // a container one level deeper names the elements the leaf would lift over
      assertThat(compilation)
          .hadErrorContaining(
              "or ValidatedPrism<java.util.List<java.lang.String>, java.util.List<java.util.UUID>>"
                  + " over the element types.");
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'ids()' with 'default ValidatedPrism<java.util.List<java.lang.String>,"
                  + " java.util.List<java.util.UUID>> ids()', a leaf over the element types.");
      // no declaration is offered where nothing would then map the elements: a Collection, no
      // source, a wildcard with no bound, a generic record, or an element-mapped spec whose own
      // leaf has no source
      for (String member : new String[] {"'PoolDto.ids'", "'BatchDto.ids'", "'LooseDto.ids'"}) {
        Assertions.assertThat(compilation.errors())
            .filteredOn(error -> error.getMessage(null).contains(member))
            .singleElement()
            .satisfies(
                error -> Assertions.assertThat(error.getMessage(null)).contains(RULE),
                error ->
                    Assertions.assertThat(error.getMessage(null)).doesNotContain("which then"));
      }
      for (String member : new String[] {"'PackDto.ids'", "'CrateDto.items'"}) {
        Assertions.assertThat(compilation.errors())
            .filteredOn(error -> error.getMessage(null).contains(member))
            .singleElement()
            .satisfies(
                error ->
                    Assertions.assertThat(error.getMessage(null)).doesNotContain("which then"));
      }
      // the sparse tier replaces the near miss too
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'ids()' with a leaf 'default"
                  + " ValidatedPrism<java.util.SortedSet<java.lang.String>,"
                  + " java.util.SortedSet<java.util.UUID>> ids()', or align the types.");
      Assertions.assertThat(compilation.errors()).hasSize(10);
    }

    @Test
    @DisplayName("the declarations and leaves it offers lift, copy and convert")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Tagged(java.util.Set<java.util.UUID> ids) {}
              record TaggedDto(java.util.Set<java.lang.String> ids) {}
              @GenerateMapping
              interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {
                default ValidatedPrism<String, UUID> ids() { return StandardCodecs.uuid(); }
              }

              record Customer(String name) {}
              record CustomerDto(String name) {}
              @GenerateMapping
              interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
              record Order(java.util.List<com.example.Customer> customers) {}
              record OrderDto(java.util.List<com.example.CustomerDto> customers) {}
              @GenerateMapping
              interface OrderMapping extends MappingSpec<Order, OrderDto> {}

              record Names(java.util.List<java.lang.CharSequence> names) {}
              record NamesDto(java.util.List<java.lang.CharSequence> names) {}
              @GenerateMapping
              interface NamesMapping extends MappingSpec<Names, NamesDto> {}

              record Maybe(Optional<List<UUID>> ids) {}
              record MaybeDto(Optional<List<String>> ids) {}
              @GenerateMapping
              interface MaybeMapping extends MappingSpec<Maybe, MaybeDto> {
                default ValidatedPrism<java.util.List<java.lang.String>, java.util.List<java.util.UUID>>
                    ids() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(raw.stream().map(UUID::fromString).toList()),
                      ids -> ids.stream().map(UUID::toString).toList());
                }
              }

              record Stamped(SortedSet<UUID> ids) {}
              class StampedPatch {
                private SortedSet<String> ids;
                public SortedSet<String> getIds() { return ids; }
                public void setIds(SortedSet<String> ids) { this.ids = ids; }
              }
              @GenerateMapping
              interface StampedUpdate extends UpdateSpec<Stamped, StampedPatch> {
                default ValidatedPrism<java.util.SortedSet<java.lang.String>,
                        java.util.SortedSet<java.util.UUID>>
                    ids() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new TreeSet<>(raw.stream().map(UUID::fromString).toList())),
                      ids -> new TreeSet<>(ids.stream().map(UUID::toString).toList()));
                }
              }

              final class Probe {
                static Object tagged() {
                  return TaggedMappingImpl.INSTANCE.parse(new TaggedDto(Set.of("nope")));
                }

                static Object order() {
                  return OrderMappingImpl.INSTANCE.parse(
                      new OrderDto(List.of(new CustomerDto("Ada"))));
                }
              }
              """);
      assertThatValidated(probe(result, "tagged")).hasFieldErrors("ids.nope: " + UUID_ERROR);
      assertThatValidated(probe(result, "order"))
          .hasValueSatisfying(
              order -> order.toString().equals("Order[customers=[Customer[name=Ada]]]"),
              "the lifted order");
    }
  }

  @Nested
  @DisplayName("a primitive component")
  class PrimitiveComponent {

    @Test
    @DisplayName("is never offered a leaf over a primitive")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Age(Integer years) {}
              record AgeDto(int years) {}
              @GenerateMapping
              interface AgeMapping extends MappingSpec<Age, AgeDto> {}

              record Span(long days) {}
              record SpanDto(int days) {}
              @GenerateMapping
              interface SpanMapping extends MappingSpec<Span, SpanDto> {}

              record Wide(long days) {}
              record WideDto(Integer days) {}
              @GenerateMapping
              interface WideMapping extends MappingSpec<Wide, WideDto> {
                default ValidatedPrism<Integer, Long> days() {
                  return ValidatedPrism.of(i -> Validated.validNel(i.longValue()), Long::intValue);
                }
              }

              record Stock(Optional<Integer> count) {}
              record StockDto(int count) {}
              @GenerateMapping
              interface StockMapping extends MappingSpec<Stock, StockDto> {}

              record Tally(int count) {}
              class TallyPatch {
                private String count;
                public String getCount() { return count; }
                public void setCount(String count) { this.count = count; }
              }
              @GenerateMapping
              interface TallyUpdate extends UpdateSpec<Tally, TallyPatch> {
                default ValidatedPrism<String, Integer> count() {
                  return StandardCodecs.intFromString();
                }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "A ValidatedPrism names reference types only, so no leaf converts a primitive"
                  + " component.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'AgeDto.years' and 'Age.years' both int, or both java.lang.Integer, so that"
                  + " they copy.");
      assertThat(compilation)
          .hadErrorContaining(
              "Align the component types, or declare the record component 'SpanDto.days' as"
                  + " java.lang.Integer and 'Span.days' as java.lang.Long, and add 'default"
                  + " ValidatedPrism<java.lang.Integer, java.lang.Long> days()' to the spec.");
      // the near miss is the leaf the wrappers need, so declaring them is all that is left
      assertThat(compilation)
          .hadErrorContaining(
              "A default method 'days()' exists but returns"
                  + " 'org.higherkindedj.optics.validated.ValidatedPrism<java.lang.Integer,"
                  + "java.lang.Long>'. A ValidatedPrism names reference types only");
      assertThat(compilation)
          .hadErrorContaining(
              "Align the component types, or declare 'Wide.days' as java.lang.Long, which the leaf"
                  + " 'days()' then converts.");
      assertThat(compilation)
          .hadErrorContaining(
              "Align the component types, or declare the record component 'StockDto.count' as"
                  + " java.lang.Integer, which can hold the null an empty Optional bridges to, and"
                  + " add '@OptionalBridge java.util.Optional<java.lang.Integer> count();' to the"
                  + " spec, so an absent value reads as a null wire component and back.");
      // on the sparse tier the property's wrapper writes straight in
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'count' on 'TallyPatch' as java.lang.Integer, which a sparse update writes"
                  + " straight into the int component, or declare 'Tally.count' as"
                  + " java.lang.Integer, which the leaf 'count()' then converts.");
      Assertions.assertThat(compilation.errors().toString())
          .doesNotContain("ValidatedPrism<int")
          .doesNotContain(", long>")
          .doesNotContain(", int>");
    }

    @Test
    @DisplayName("the types and leaves it offers map the pair")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Age(int years) {}
              record AgeDto(int years) {}
              @GenerateMapping
              interface AgeMapping extends MappingSpec<Age, AgeDto> {}

              record Span(java.lang.Long days) {}
              record SpanDto(java.lang.Integer days) {}
              @GenerateMapping
              interface SpanMapping extends MappingSpec<Span, SpanDto> {
                default ValidatedPrism<java.lang.Integer, java.lang.Long> days() {
                  return ValidatedPrism.of(i -> Validated.validNel(i.longValue()), Long::intValue);
                }
              }

              record Wide(java.lang.Long days) {}
              record WideDto(Integer days) {}
              @GenerateMapping
              interface WideMapping extends MappingSpec<Wide, WideDto> {
                default ValidatedPrism<Integer, Long> days() {
                  return ValidatedPrism.of(i -> Validated.validNel(i.longValue()), Long::intValue);
                }
              }

              record Stock(Optional<Integer> count) {}
              record StockDto(java.lang.Integer count) {}
              @GenerateMapping
              interface StockMapping extends MappingSpec<Stock, StockDto> {
                @OptionalBridge java.util.Optional<java.lang.Integer> count();
              }

              record Tally(java.lang.Integer count) {}
              class TallyPatch {
                private String count;
                public String getCount() { return count; }
                public void setCount(String count) { this.count = count; }
              }
              @GenerateMapping
              interface TallyUpdate extends UpdateSpec<Tally, TallyPatch> {
                default ValidatedPrism<String, Integer> count() {
                  return StandardCodecs.intFromString();
                }
              }

              final class Probe {
                static Object span() {
                  return SpanMappingImpl.INSTANCE.parse(new SpanDto(7));
                }

                static Object stock() {
                  return StockMappingImpl.INSTANCE.parse(new StockDto(null));
                }
              }
              """);
      assertThatValidated(probe(result, "span"))
          .hasValueSatisfying(span -> span.toString().equals("Span[days=7]"), "the widened span");
      assertThatValidated(probe(result, "stock"))
          .hasValueSatisfying(
              stock -> stock.toString().equals("Stock[count=Optional.empty]"), "an absent count");
    }
  }

  @Nested
  @DisplayName("a primitive wire member the Optional bridge reaches")
  class PrimitiveBridge {

    @Test
    @DisplayName("says it can never hold the bridge's null, and offers only what can be followed")
    void refused() {
      Compilation compilation =
          compile(
              """
              class PersonBean {
                private int age;
                public int getAge() { return age; }
                public void setAge(int age) { this.age = age; }
              }
              record Person(Optional<Integer> age) {}
              @GenerateMapping
              interface PersonMapping extends MappingSpec<Person, PersonBean> {}

              record Wide(Optional<Long> age) {}
              @GenerateMapping
              interface WideMapping extends MappingSpec<Wide, PersonBean> {}

              @GenerateMapping
              interface WideLeafMapping extends MappingSpec<Wide, PersonBean> {
                default ValidatedPrism<Integer, Long> age() {
                  return ValidatedPrism.of(i -> Validated.validNel(i.longValue()), Long::intValue);
                }
              }

              @GenerateMapping
              interface MarkedMapping extends MappingSpec<Person, PersonBean> {
                @OptionalBridge Optional<Integer> age();
              }

              @GenerateMapping
              interface MarkedWideMapping extends MappingSpec<Wide, PersonBean> {
                @OptionalBridge Optional<Long> age();
              }

              record AgeDto(int age) {}
              @GenerateMapping
              interface MarkedRecordMapping extends MappingSpec<Person, AgeDto> {
                @OptionalBridge Optional<Integer> age();
              }

              @GenerateMapping
              interface WholeOptionalMapping extends MappingSpec<Person, AgeDto> {
                @OptionalBridge
                default ValidatedPrism<Integer, Optional<Integer>> age() {
                  throw new UnsupportedOperationException();
                }
              }

              interface AgeBridge {
                @OptionalBridge Optional<Integer> age();
              }
              @GenerateMapping
              interface InheritedMarkerMapping extends MappingSpec<Person, PersonBean>, AgeBridge {}
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Person.age' is java.util.Optional<java.lang.Integer>, bridged to the"
                  + " bean property 'age', which is the primitive int.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'age' on 'PersonBean' as java.lang.Integer, or declare 'Person.age' as"
                  + " int.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'age' on 'PersonBean' as java.lang.Integer and add 'default"
                  + " ValidatedPrism<java.lang.Integer, java.lang.Long> age()' to the spec.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'age' on 'PersonBean' as java.lang.Integer, which the leaf 'age()' then"
                  + " converts.");
      // a marker on a bean wire is redundant once the wrapper bridges without it
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'age' on 'PersonBean' as java.lang.Integer, and remove the annotation,"
                  + " which a bean wire does not need.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'age' on 'PersonBean' as java.lang.Integer and add 'default"
                  + " ValidatedPrism<java.lang.Integer, java.lang.Long> age()', as the component's"
                  + " only spec method, to the spec.");
      // a record wire keeps the marker it needs, and a mix-in's marker is not this spec's to drop
      assertThat(compilation).hadErrorContaining("Declare 'age' on 'AgeDto' as java.lang.Integer.");
      assertThat(compilation)
          .hadErrorContaining(
              "@OptionalBridge on 'age' (inherited from 'AgeBridge') bridges to the primitive bean"
                  + " property 'age'.");
      assertThat(compilation)
          .hadErrorContaining("Declare 'age' on 'PersonBean' as java.lang.Integer.");
      // a leaf over the whole Optional is named with the wrapper, never the primitive
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the leaf as 'ValidatedPrism<java.lang.Integer, java.lang.Integer>'");
      Assertions.assertThat(compilation.errors().toString())
          .doesNotContain("nullable bean property 'age'")
          .doesNotContain("ValidatedPrism<int");
      Assertions.assertThat(compilation.errors()).hasSize(8);
    }

    @Test
    @DisplayName("the wrappers and leaves it offers bridge the pair")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              class PersonBean {
                private java.lang.Integer age;
                public java.lang.Integer getAge() { return age; }
                public void setAge(java.lang.Integer age) { this.age = age; }
              }
              record Person(Optional<Integer> age) {}
              @GenerateMapping
              interface PersonMapping extends MappingSpec<Person, PersonBean> {}

              class AgeBean {
                private int age;
                public int getAge() { return age; }
                public void setAge(int age) { this.age = age; }
              }
              record Plain(int age) {}
              @GenerateMapping
              interface PlainMapping extends MappingSpec<Plain, AgeBean> {}

              record Wide(Optional<Long> age) {}
              @GenerateMapping
              interface WideMapping extends MappingSpec<Wide, PersonBean> {
                default ValidatedPrism<java.lang.Integer, java.lang.Long> age() {
                  return ValidatedPrism.of(i -> Validated.validNel(i.longValue()), Long::intValue);
                }
              }

              @GenerateMapping
              interface MarkedMapping extends MappingSpec<Person, PersonBean> {}

              record AgeDto(java.lang.Integer age) {}
              @GenerateMapping
              interface MarkedRecordMapping extends MappingSpec<Person, AgeDto> {
                @OptionalBridge Optional<Integer> age();
              }

              final class Probe {
                static Object wide() {
                  PersonBean bean = WideMappingImpl.INSTANCE.build(new Wide(Optional.empty()));
                  return Validated.validNel(bean.getAge() == null ? "absent" : "present");
                }
              }
              """);
      assertThatValidated(probe(result, "wide")).hasValue("absent");
    }

    @Test
    @DisplayName("a bean's element leaf is offered bare, and replaces a marker only where one is")
    void beanLeafIsBare() {
      Compilation compilation =
          compile(
              """
              record Email(String value) {}
              class ContactBean {
                private String email;
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
              record Contact(Optional<Email> email) {}
              @GenerateMapping
              interface ContactMapping extends MappingSpec<Contact, ContactBean> {}

              @GenerateMapping
              interface MarkedContactMapping extends MappingSpec<Contact, ContactBean> {
                @OptionalBridge Optional<Email> email();
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'default ValidatedPrism<java.lang.String, com.example.Email> email()' on the"
                  + " spec, or align the element types.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'default ValidatedPrism<java.lang.String, com.example.Email> email()' as"
                  + " the component's only spec method, replacing any marker for it, or align the"
                  + " element types.");
      Assertions.assertThat(compilation.errors().toString())
          .doesNotContain("@OptionalBridge default");

      compileFollowed(
          """
          record Email(String value) {}
          class ContactBean {
            private String email;
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
          }
          record Contact(Optional<Email> email) {}
          @GenerateMapping
          interface ContactMapping extends MappingSpec<Contact, ContactBean> {
            default ValidatedPrism<java.lang.String, com.example.Email> email() {
              return ValidatedPrism.of(raw -> Validated.validNel(new Email(raw)), Email::value);
            }
          }
          """);
    }
  }

  @Nested
  @DisplayName("a domain Optional against a plain wire component")
  class OptionalAgainstPlain {

    @Test
    @DisplayName("is refused, offering the bridge, or else a leaf over the whole Optional")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Reader(String name, Optional<String> nickname) {}
              record ReaderDto(String name, String nickname) {}
              @GenerateMapping
              interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {}
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'ReaderDto.nickname' has no usable source.");
      assertThat(compilation)
          .hadErrorContaining(
              "Add '@OptionalBridge java.util.Optional<java.lang.String> nickname();' to the spec,"
                  + " so an absent value reads as a null wire component and back. Or add 'default"
                  + " ValidatedPrism<java.lang.String, java.util.Optional<java.lang.String>>"
                  + " nickname()' to the spec.");
    }

    @Test
    @DisplayName("either offer maps the pair on its own, and only the bridge reads null as absent")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Reader(String name, Optional<String> nickname) {}
              record ReaderDto(String name, String nickname) {}
              @GenerateMapping
              interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {
                @OptionalBridge java.util.Optional<java.lang.String> nickname();
              }

              @GenerateMapping
              interface ReaderLeafMapping extends MappingSpec<Reader, ReaderDto> {
                default ValidatedPrism<java.lang.String, java.util.Optional<java.lang.String>>
                    nickname() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(Optional.of(raw)), nickname -> nickname.orElse(""));
                }
              }

              final class Probe {
                static Object bridged() {
                  return ReaderMappingImpl.INSTANCE
                      .parse(new ReaderDto("Ada", null))
                      .map(Reader::nickname);
                }

                static Object leaf() {
                  return ReaderLeafMappingImpl.INSTANCE
                      .parse(new ReaderDto("Ada", null))
                      .map(Reader::nickname);
                }
              }
              """);
      assertThatValidated(probe(result, "bridged")).hasValue(Optional.empty());
      assertThatValidated(probe(result, "leaf")).hasFieldErrors("nickname: must not be null");
    }
  }

  @Nested
  @DisplayName("the Optional bridge offered beside a same-named method")
  class BridgeBesideAMethod {

    @Test
    @DisplayName("annotates an element leaf the spec has, and replaces any other method")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Email(String value) {}
              record Contact(Optional<Email> email) {}
              record ContactDto(String email) {}
              @GenerateMapping
              interface ContactMapping extends MappingSpec<Contact, ContactDto> {
                default ValidatedPrism<String, Email> email() {
                  return ValidatedPrism.of(raw -> Validated.validNel(new Email(raw)), Email::value);
                }
              }

              record Card(Optional<Email> email) {}
              record CardDto(String email) {}
              @GenerateMapping
              interface CardMapping extends MappingSpec<Card, CardDto> {
                default String email() { return ""; }
              }

              record Reader(Optional<String> nickname) {}
              record ReaderDto(String nickname) {}
              @GenerateMapping
              interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {
                default String nickname() { return ""; }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Annotate 'email()' with @OptionalBridge, so an absent value reads as a null wire"
                  + " component and a present one converts. Or replace 'email()' with 'default"
                  + " ValidatedPrism<java.lang.String, java.util.Optional<com.example.Email>>"
                  + " email()'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'email()' with '@OptionalBridge default ValidatedPrism<java.lang.String,"
                  + " com.example.Email> email()', a leaf over the ELEMENT types, so an absent"
                  + " value reads as a null wire component and a present one converts.");
      assertThat(compilation)
          .hadErrorContaining(
              "Replace 'nickname()' with '@OptionalBridge java.util.Optional<java.lang.String>"
                  + " nickname();', so an absent value reads as a null wire component and back.");
      Assertions.assertThat(compilation.errors().toString()).doesNotContain("Add '@OptionalBridge");
    }

    @Test
    @DisplayName("each bridge it offers maps the pair once followed")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Email(String value) {}
              record Contact(Optional<Email> email) {}
              record ContactDto(String email) {}
              @GenerateMapping
              interface ContactMapping extends MappingSpec<Contact, ContactDto> {
                @OptionalBridge
                default ValidatedPrism<String, Email> email() {
                  return ValidatedPrism.of(raw -> Validated.validNel(new Email(raw)), Email::value);
                }
              }

              record Reader(Optional<String> nickname) {}
              record ReaderDto(String nickname) {}
              @GenerateMapping
              interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {
                @OptionalBridge java.util.Optional<java.lang.String> nickname();
              }

              final class Probe {
                static Object contact() {
                  return ContactMappingImpl.INSTANCE
                      .parse(new ContactDto("ada@example.com"))
                      .map(contact -> contact.email().map(Email::value));
                }

                static Object reader() {
                  return ReaderMappingImpl.INSTANCE
                      .parse(new ReaderDto(null))
                      .map(Reader::nickname);
                }
              }
              """);
      assertThatValidated(probe(result, "contact")).hasValue(Optional.of("ada@example.com"));
      assertThatValidated(probe(result, "reader")).hasValue(Optional.empty());
    }
  }

  @Nested
  @DisplayName("an @OptionalBridge the wire does not need")
  class RedundantBridge {

    private static final String GUEST_BEAN =
        """
        class GuestBean {
          private String name;
          private String nickname;
          public String getName() { return name; }
          public void setName(String name) { this.name = name; }
          public String getNickname() { return nickname; }
          public void setNickname(String nickname) { this.nickname = nickname; }
        }
        """;

    @Test
    @DisplayName("is noted where the spec declares it, and the fix moves it to a shared mix-in")
    void noted() {
      Compilation compilation =
          compile(
              GUEST_BEAN
                  + """
                  record Guest(String name, Optional<String> nickname) {}
                  @GenerateMapping
                  interface GuestBeanMapping extends MappingSpec<Guest, GuestBean> {
                    @OptionalBridge Optional<String> nickname();
                  }

                  record GuestView(String name, Optional<String> nickname) {}
                  @GenerateMapping
                  interface GuestViewMapping extends MappingSpec<Guest, GuestView> {
                    @OptionalBridge Optional<String> nickname();
                  }
                  """);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "Remove the annotation. If a record-wire spec needs it too, declare the annotated"
                  + " method on a mix-in both specs extend; an inherited one draws no note.");
      assertThat(compilation)
          .hadNoteContaining(
              "Remove the annotation. If a spec whose wire component is a plain nullable one needs"
                  + " it too, declare the annotated method on a mix-in both specs extend; an"
                  + " inherited one draws no note.");
    }

    @Test
    @DisplayName("on a shared mix-in, serves the wire that needs it and is silent on the others")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              GUEST_BEAN
                  + """
                  record Guest(String name, Optional<String> nickname) {}
                  interface NicknameBridge {
                    @OptionalBridge Optional<String> nickname();
                  }
                  @GenerateMapping
                  interface GuestBeanMapping extends MappingSpec<Guest, GuestBean>, NicknameBridge {}

                  record GuestView(String name, Optional<String> nickname) {}
                  @GenerateMapping
                  interface GuestViewMapping extends MappingSpec<Guest, GuestView>, NicknameBridge {}

                  record GuestDto(String name, String nickname) {}
                  @GenerateMapping
                  interface GuestDtoMapping extends MappingSpec<Guest, GuestDto>, NicknameBridge {}

                  final class Probe {
                    static Object record() {
                      return GuestDtoMappingImpl.INSTANCE
                          .parse(new GuestDto("Ada", null))
                          .map(Guest::nickname);
                    }
                  }
                  """);
      assertThatValidated(probe(result, "record")).hasValue(Optional.empty());
    }
  }

  @Nested
  @DisplayName("two flattened groups that share a component name")
  class SharedFlattenedName {

    @Test
    @DisplayName("of one record type are offered one group, and of two are offered a rename")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Address(String street, String city) {}
              record Moves(String name, Address home, Address work) {}
              record MovesDto(String name, String street, String city) {}
              @GenerateMapping
              interface MovesMapping extends MappingSpec<Moves, MovesDto> {
                @Flatten Address home();
                @Flatten Address work();
              }

              record Office(String street, String floor) {}
              record Places(String name, Address home, Office work) {}
              record PlacesDto(String name, String street, String city, String floor) {}
              @GenerateMapping
              interface PlacesMapping extends MappingSpec<Places, PlacesDto> {
                @Flatten Address home();
                @Flatten Office work();
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Flatten only one of 'home' and 'work'; the other maps through a nested wire"
                  + " component, as an unflattened record component does.");
      assertThat(compilation)
          .hadErrorContaining(
              "would claim one wire component twice. Rename one of the two record components.");
      Assertions.assertThat(compilation.errors()).hasSize(2);
    }

    @Test
    @DisplayName("of one record type map with one group spread and the other nested")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Address(String street, String city) {}
              record AddressDto(String street, String city) {}
              @GenerateMapping
              interface AddressMapping extends MappingSpec<Address, AddressDto> {}

              record Moves(String name, Address home, Address work) {}
              record MovesDto(String name, String street, String city, AddressDto work) {}
              @GenerateMapping
              interface MovesMapping extends MappingSpec<Moves, MovesDto> {
                @Flatten Address home();
              }

              final class Probe {
                static Object moves() {
                  MovesDto dto =
                      new MovesDto("Ada", "1 Road", "Leeds", new AddressDto("2 Street", "York"));
                  return MovesMappingImpl.INSTANCE
                      .parse(dto)
                      .map(moves -> moves.home().street() + "/" + moves.work().street());
                }
              }
              """);
      assertThatValidated(probe(result, "moves")).hasValue("1 Road/2 Street");
    }
  }

  @Nested
  @DisplayName("a PATCH bean whose constructor the Impl cannot call")
  class UnreachablePatchConstructor {

    @Test
    @DisplayName("is accepted, since the sparse tier only reads it, and a builder stays its writer")
    void accepted() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              record Contact(String name, String email) {}
              class ContactPatch {
                private String name;
                private String email;
                private ContactPatch() {}
                static ContactPatch named(String name) {
                  ContactPatch patch = new ContactPatch();
                  patch.setName(name);
                  return patch;
                }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
              @GenerateMapping
              interface ContactUpdate extends UpdateSpec<Contact, ContactPatch> {}

              // A builder writes every property, whatever setters the bean also carries.
              final class BuiltPatch {
                private final String name;
                private final String email;
                private BuiltPatch(String name, String email) {
                  this.name = name;
                  this.email = email;
                }
                public static Builder builder() { return new Builder(); }
                public String getName() { return name; }
                public String getEmail() { return email; }
                public void setName(String ignored) {}
                public static final class Builder {
                  private String name;
                  private String email;
                  public Builder name(String name) { this.name = name; return this; }
                  public Builder email(String email) { this.email = email; return this; }
                  public BuiltPatch build() { return new BuiltPatch(name, email); }
                }
              }
              @GenerateMapping
              interface BuiltUpdate extends UpdateSpec<Contact, BuiltPatch> {}

              final class ListsPatch {
                private final List<String> tags;
                private ListsPatch(List<String> tags) { this.tags = tags; }
                public static Builder builder() { return new Builder(); }
                public List<String> getTags() { return tags; }
                public static final class Builder {
                  private List<String> tags;
                  public Builder tags(List<String> tags) { this.tags = tags; return this; }
                  public ListsPatch build() { return new ListsPatch(tags); }
                }
              }
              record Tagged(List<String> tags) {}
              @GenerateMapping
              interface ListsUpdate extends UpdateSpec<Tagged, ListsPatch> {}

              final class Probe {
                static Object named() {
                  return ContactUpdateImpl.INSTANCE
                      .updateFrom(ContactPatch.named("Grace"))
                      .apply(new Contact("Ada", "ada@example.com"));
                }

                static Object built() {
                  return BuiltUpdateImpl.INSTANCE
                      .updateFrom(BuiltPatch.builder().email("grace@example.com").build())
                      .apply(new Contact("Ada", "ada@example.com"));
                }
              }
              """);
      // the sent property lands, and the omitted one keeps its value
      assertThatValidated(probe(result, "named"))
          .hasValueSatisfying(
              contact -> contact.toString().equals("Contact[name=Grace, email=ada@example.com]"),
              "the patched contact");
      assertThatValidated(probe(result, "built"))
          .hasValueSatisfying(
              contact -> contact.toString().equals("Contact[name=Ada, email=grace@example.com]"),
              "the patched contact");
    }
  }

  @Nested
  @DisplayName("a sparse lookup only a one-directional spec could serve")
  class SparseOneDirectional {

    @Test
    @DisplayName("names the spec and what it lacks, as the dense lookup does, for elements too")
    void refused() {
      Compilation compilation =
          compile(
              """
              record Address(String street) {}
              class AddressBean {
                private String street;
                public void setStreet(String street) { this.street = street; }
              }
              @GenerateMapping
              interface AddressMapping extends MappingSpec<Address, AddressBean> {}

              record User(Address address) {}
              class UserPatch {
                private AddressBean address;
                public AddressBean getAddress() { return address; }
                public void setAddress(AddressBean address) { this.address = address; }
              }
              @GenerateMapping
              interface UserUpdate extends UpdateSpec<User, UserPatch> {}

              record Household(List<Address> addresses) {}
              class HouseholdPatch {
                private List<AddressBean> addresses;
                public List<AddressBean> getAddresses() { return addresses; }
                public void setAddresses(List<AddressBean> addresses) { this.addresses = addresses; }
              }
              @GenerateMapping
              interface HouseholdUpdate extends UpdateSpec<Household, HouseholdPatch> {}
              """);
      assertThat(compilation).failed();
      String hint =
          "'AddressMapping' maps this pair but is build-only (no parse), so it cannot be nested in"
              + " a sparse update, which parses what it reads.";
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains(hint))
          .hasSize(2);
    }
  }

  @Nested
  @DisplayName("an element-mapped spec nested where its leaves have no source")
  class ElementMappedNesting {

    private static final String BOX =
        """
        record Box<T, U>(T first, U second) {}
        record BoxDto<TD, UD>(TD first, UD second) {}
        @GenerateMapping
        interface CodecBox<T, TD, U, UD> extends MappingSpec<Box<T, U>, BoxDto<TD, UD>> {
          ValidatedPrism<TD, T> first();
          ValidatedPrism<UD, U> second();
        }
        record Holder(Box<UUID, Integer> box) {}
        record HolderDto(BoxDto<String, String> box) {}

        record Wrap<T>(T value) {}
        record WrapDto<TD>(TD value) {}
        @GenerateMapping
        interface WrapMapping<T, TD> extends MappingSpec<Wrap<T>, WrapDto<TD>> {
          ValidatedPrism<TD, T> value();
        }
        record Customer(String name) {}
        record CustomerDto(String name) {}
        record Account(Wrap<Customer> owner) {}
        record AccountDto(WrapDto<CustomerDto> owner) {}
        record Ticket(Wrap<UUID> id) {}
        record TicketDto(WrapDto<String> id) {}
        """;

    private static final String WHOLE_LEAF =
        "default ValidatedPrism<com.example.BoxDto<java.lang.String,java.lang.String>,"
            + " com.example.Box<java.util.UUID,java.lang.Integer>> box()";

    @Test
    @DisplayName("offers the leaf that element-mapped spec can take, and a spec only for records")
    void refused() {
      Compilation compilation =
          compile(
              BOX
                  + """
                  @GenerateMapping
                  interface HolderMapping extends MappingSpec<Holder, HolderDto> {}

                  @GenerateMapping
                  interface NearHolderMapping extends MappingSpec<Holder, HolderDto> {
                    default ValidatedPrism<String, UUID> box() { return StandardCodecs.uuid(); }
                  }

                  @GenerateMapping
                  interface AccountMapping extends MappingSpec<Account, AccountDto> {}

                  @GenerateMapping
                  interface TicketMapping extends MappingSpec<Ticket, TicketDto> {}
                  """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'CodecBox' has 2 leaves, and a leaf on this spec named after the component can"
                  + " stand in only for a single-leaf mapping, so each prism must come from another"
                  + " mapping, here or in a dependency compiled with hkj-processor on its processor"
                  + " path.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare '"
                  + WHOLE_LEAF
                  + "' on this spec, returning com.example.CodecBoxImpl.of(...).asValidatedPrism()"
                  + " with one ValidatedPrism per leaf, in the order 'first', 'second'.");
      // a leaf already named after the component is replaced, since the spec cannot hold both
      assertThat(compilation)
          .hadErrorContaining("Replace 'box()' with '" + WHOLE_LEAF + "' on this spec");
      // a single leaf takes the leaf named after the component, and a record pair a spec
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'default ValidatedPrism<com.example.CustomerDto, com.example.Customer>"
                  + " owner()' on this spec, or map com.example.CustomerDto to com.example.Customer"
                  + " with its own @GenerateMapping spec.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'default ValidatedPrism<java.lang.String, java.util.UUID> id()' on this"
                  + " spec.");
      Assertions.assertThat(compilation.errors()).hasSize(4);
    }

    @Test
    @DisplayName("each line it offers composes the Impl")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              BOX
                  + """
                  @GenerateMapping
                  interface HolderMapping extends MappingSpec<Holder, HolderDto> {
                    %s {
                      return CodecBoxImpl.of(StandardCodecs.uuid(), StandardCodecs.intFromString())
                          .asValidatedPrism();
                    }
                  }

                  @GenerateMapping
                  interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
                  @GenerateMapping
                  interface AccountMapping extends MappingSpec<Account, AccountDto> {}

                  @GenerateMapping
                  interface TicketMapping extends MappingSpec<Ticket, TicketDto> {
                    default ValidatedPrism<java.lang.String, java.util.UUID> id() {
                      return StandardCodecs.uuid();
                    }
                  }

                  final class Probe {
                    static Object holder() {
                      return HolderMappingImpl.INSTANCE.parse(
                          new HolderDto(new BoxDto<>("nope", "4")));
                    }
                  }
                  """
                      .formatted(WHOLE_LEAF));
      assertThatValidated(probe(result, "holder")).hasFieldErrors("box.first: " + UUID_ERROR);
    }
  }

  @Nested
  @DisplayName("a sealed pair with a subtype dispatch cannot delegate")
  class UndispatchableSubtype {

    @Test
    @DisplayName("names every such subtype as not supported yet, never asking for a spec")
    void refused() {
      Compilation compilation =
          compile(
              """
              sealed interface Node permits Leaf, Fixed, Branch {}
              record Leaf<T>(T value) implements Node {}
              enum Fixed implements Node { ONE }
              record Branch(String label) implements Node {}
              sealed interface NodeDto permits LeafDto, BranchDto {}
              record LeafDto<T>(T value) implements NodeDto {}
              record BranchDto(String label) implements NodeDto {}
              @GenerateMapping
              interface LeafMapping<T> extends MappingSpec<Leaf<T>, LeafDto<T>> {}
              @GenerateMapping
              interface NodeMapping extends MappingSpec<Node, NodeDto> {}

              sealed interface Shape permits Open, Poly, Solid {}
              non-sealed interface Open extends Shape {}
              abstract non-sealed class Poly implements Shape {}
              final class Solid implements Shape {}
              sealed interface ShapeDto permits OpenDto {}
              record OpenDto(String name) implements ShapeDto {}
              @GenerateMapping
              interface ShapeMapping extends MappingSpec<Shape, ShapeDto> {}

              sealed interface Tone permits Warm {}
              record Warm(String name) implements Tone {}
              sealed interface ToneDto permits WarmDto {}
              enum WarmDto implements ToneDto { RED }
              @GenerateMapping
              interface ToneMapping extends MappingSpec<Tone, ToneDto> {}
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "permitted subtype 'com.example.Leaf<T>' of 'Node' is generic, which sealed"
                  + " dispatch does not support yet.");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'Leaf' without type parameters, or drop this spec and map the pair with a"
                  + " hand-written ValidatedPrism<NodeDto, Node> leaf wherever it nests.");
      // every such subtype of a pair is named in the one compilation, on both sides
      assertThat(compilation)
          .hadErrorContaining(
              "permitted subtype 'com.example.Fixed' of 'Node' is an enum, which sealed dispatch"
                  + " does not support yet.");
      assertThat(compilation)
          .hadErrorContaining("permitted subtype 'com.example.LeafDto<T>' of 'NodeDto' is generic");
      assertThat(compilation)
          .hadErrorContaining(
              "permitted subtype 'com.example.Open' of 'Shape' is an interface that is not"
                  + " sealed");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'Open' as a sealed interface, mapped by a sealed spec of its own, or as a"
                  + " record");
      assertThat(compilation)
          .hadErrorContaining(
              "permitted subtype 'com.example.Poly' of 'Shape' is an abstract class");
      assertThat(compilation)
          .hadErrorContaining("permitted subtype 'com.example.Solid' of 'Shape' is a class");
      assertThat(compilation).hadErrorContaining("Declare 'Solid' as a record");
      assertThat(compilation)
          .hadErrorContaining(
              "a spec maps only a record or a sealed interface, or on the wire side a bean.");
      Assertions.assertThat(compilation.errors().toString()).doesNotContain("has no mapping spec");
      Assertions.assertThat(compilation.errors()).hasSize(7);
    }

    @Test
    @DisplayName(
        "declared as it offers, and beside a sealed interface or a bean, a subtype dispatches")
    void followed() throws ReflectiveOperationException {
      RuntimeCompilationHelper.CompiledResult result =
          compileFollowed(
              """
              sealed interface Node permits Leaf, Branch {}
              record Leaf(String value) implements Node {}
              record Branch(String label) implements Node {}
              sealed interface NodeDto permits LeafDto, BranchDto {}
              record LeafDto(String value) implements NodeDto {}
              record BranchDto(String label) implements NodeDto {}
              @GenerateMapping
              interface LeafMapping extends MappingSpec<Leaf, LeafDto> {}
              @GenerateMapping
              interface BranchMapping extends MappingSpec<Branch, BranchDto> {}
              @GenerateMapping
              interface NodeMapping extends MappingSpec<Node, NodeDto> {}

              sealed interface Animal permits Pet, Wild {}
              sealed interface Pet extends Animal permits Dog {}
              record Dog(String name) implements Pet {}
              record Wild(String name) implements Animal {}
              sealed interface AnimalDto permits PetDto, WildDto {}
              sealed interface PetDto extends AnimalDto permits DogDto {}
              record DogDto(String name) implements PetDto {}
              final class WildDto implements AnimalDto {
                private String name;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              @GenerateMapping
              interface DogMapping extends MappingSpec<Dog, DogDto> {}
              @GenerateMapping
              interface PetMapping extends MappingSpec<Pet, PetDto> {}
              @GenerateMapping
              interface WildMapping extends MappingSpec<Wild, WildDto> {}
              @GenerateMapping
              interface AnimalMapping extends MappingSpec<Animal, AnimalDto> {}

              final class Probe {
                static Object wild() {
                  WildDto wild = new WildDto();
                  wild.setName("Wolf");
                  return AnimalMappingImpl.INSTANCE.parse(wild);
                }
              }
              """);
      assertThatValidated(probe(result, "wild"))
          .hasValueSatisfying(
              animal -> animal.toString().equals("Wild[name=Wolf]"), "the parsed wild animal");
    }
  }
}
