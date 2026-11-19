// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.NullAwayCompiler.compile;
import static org.higherkindedj.optics.processing.NullAwayCompiler.source;

import com.google.testing.compile.Compilation;
import java.util.List;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.NullAwayCompiler.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A generated mapping implementation passes a JSpecify nullness checker in a {@code @NullMarked}
 * package, as a consumer who checks generated code runs it: the copy helpers and the guards it
 * emits say what they do with a {@code null}, so a read is held to the nullness its declaration
 * gives it, and a nullable read is never written into a site declared non-null.
 *
 * <p>Every container shape a copy takes is here, at the top and inside, declared nullable and
 * non-null, on every tier that copies: a record or bean wire both ways, a builder, a sparse update
 * and a merge, beside a leaf, a {@code @MapKey} leaf and a record wide enough to parse in chunks.
 * Each test is one javac run per configuration, which also holds Error Prone's {@code
 * NullableOptional} check, run by default, to the guards. The {@code Optional} legs, bridged and
 * lifted, have one of their own, on every tier that parses them: over a record, a PATCH bean
 * holding {@code JsonNullable} and {@code Optional} properties, and a protobuf-java message, whose
 * {@code build} writes a field with a {@code hasX()} only when its {@code Optional} holds a value,
 * with a wildcard-carrying element and holder among them.
 *
 * <p>A record wire component declared nullable, read into a domain component declared non-null, is
 * here too, and so is a map's key declared so: such a mapping offers no {@code asIso()} or {@code
 * asLens()}, which would copy the read straight into the domain's component. A nullable type
 * argument passes through {@code parse}'s ladder at every depth, and {@code build} widens a copy
 * into a wire whose type arguments are declared more nullable, on every tier that copies one.
 */
@DisplayName("A nullness checker accepts a generated mapping implementation")
@ParameterizedClass(name = "{0}")
@EnumSource(Configuration.class)
class MappingNullnessTest {

  /**
   * Stands in for jackson-databind-nullable's {@code JsonNullable}, which the processor recognises
   * by name, outside the {@code @NullMarked} package, as the library is: the main suite's {@code
   * JsonNullableStub}, which this suite's classpath does not reach.
   */
  private static final JavaFileObject JSON_NULLABLE =
      source(
          "org.openapitools.jackson.nullable.JsonNullable",
          """
          package org.openapitools.jackson.nullable;

          public final class JsonNullable<T> {
            private static final JsonNullable<?> UNDEFINED = new JsonNullable<>(null, false);
            private final T value;
            private final boolean present;

            private JsonNullable(T value, boolean present) {
              this.value = value;
              this.present = present;
            }

            @SuppressWarnings("unchecked")
            public static <T> JsonNullable<T> undefined() {
              return (JsonNullable<T>) UNDEFINED;
            }

            public static <T> JsonNullable<T> of(T value) {
              return new JsonNullable<>(value, true);
            }

            public boolean isPresent() {
              return present;
            }

            public T get() {
              if (!present) {
                throw new java.util.NoSuchElementException("undefined");
              }
              return value;
            }
          }
          """);

  private final Configuration configuration;

  MappingNullnessTest(Configuration configuration) {
    this.configuration = configuration;
  }

  @Test
  @DisplayName("every tier, over every container shape a copy takes, nullable and non-null")
  void everyTierPassesTheChecker() {
    Compilation compilation =
        compile(
            configuration,
            List.of(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor()),
            List.of("-Xep:NullableOptional:ERROR"),
            source(
                "com.example.Fixtures",
                """
                package com.example;

                import java.util.Collection;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Set;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.GenerateMerge;
                import org.higherkindedj.optics.annotations.MapKey;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.annotations.UpdateSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.Nullable;

                public final class Fixtures {
                  private Fixtures() {}

                  // An id the store assigns, absent until the first save, on both sides.
                  public record Account(
                      @Nullable String id,
                      String name,
                      Set<String> tags,
                      List<String> notes,
                      Map<String, String> labels,
                      String secret) {}

                  public record AccountView(
                      @Nullable String id,
                      String name,
                      Set<String> tags,
                      List<String> notes,
                      Map<String, String> labels) {}

                  @GenerateMapping
                  public interface AccountViewMapping extends MappingSpec<Account, AccountView> {}

                  // Every container shape a copy takes, at the top level and inside, nullable and
                  // non-null. Each record stays within one fields() ladder.
                  public record Flat(
                      List<String> list,
                      @Nullable List<String> maybeList,
                      Set<String> set,
                      @Nullable Set<String> maybeSet,
                      Collection<String> coll,
                      @Nullable Collection<String> maybeColl,
                      Map<String, String> map,
                      @Nullable Map<String, String> maybeMap,
                      String[] array,
                      String @Nullable [] maybeArray,
                      int[] ints,
                      int @Nullable [] maybeInts) {}

                  public record FlatDto(
                      List<String> list,
                      @Nullable List<String> maybeList,
                      Set<String> set,
                      @Nullable Set<String> maybeSet,
                      Collection<String> coll,
                      @Nullable Collection<String> maybeColl,
                      Map<String, String> map,
                      @Nullable Map<String, String> maybeMap,
                      String[] array,
                      String @Nullable [] maybeArray,
                      int[] ints,
                      int @Nullable [] maybeInts) {}

                  @GenerateMapping
                  public interface FlatMapping extends MappingSpec<Flat, FlatDto> {}

                  public record Nested(
                      List<List<String>> lists,
                      @Nullable List<List<String>> maybeLists,
                      Set<List<String>> setOfLists,
                      Collection<List<String>> collOfLists,
                      Map<String, List<String>> mapOfLists,
                      @Nullable Map<String, List<String>> maybeMapOfLists,
                      String[][] grid,
                      String @Nullable [][] maybeGrid,
                      List<String[]> listOfArrays,
                      Optional<List<String>> maybeValues,
                      Optional<int[]> maybeCodes) {}

                  public record NestedDto(
                      List<List<String>> lists,
                      @Nullable List<List<String>> maybeLists,
                      Set<List<String>> setOfLists,
                      Collection<List<String>> collOfLists,
                      Map<String, List<String>> mapOfLists,
                      @Nullable Map<String, List<String>> maybeMapOfLists,
                      String[][] grid,
                      String @Nullable [][] maybeGrid,
                      List<String[]> listOfArrays,
                      Optional<List<String>> maybeValues,
                      Optional<int[]> maybeCodes) {}

                  @GenerateMapping
                  public interface NestedMapping extends MappingSpec<Nested, NestedDto> {}

                  // A leaf, and a @MapKey leaf, are each read once and kept.
                  public record Code(String value) {}

                  public record Coded(Code code, Map<Code, List<String>> byCode) {}

                  public record CodedDto(String code, Map<String, List<String>> byCode) {}

                  @GenerateMapping
                  public interface CodedMapping extends MappingSpec<Coded, CodedDto> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }

                    @MapKey("byCode")
                    default ValidatedPrism<String, Code> byCodeKey() {
                      return code();
                    }
                  }

                  // Wider than one fields() ladder, over containers that copy at two levels.
                  public record Wide(
                      List<List<String>> l1,
                      List<List<String>> l2,
                      List<List<String>> l3,
                      List<List<String>> l4,
                      List<List<String>> l5,
                      List<List<String>> l6,
                      List<List<String>> l7,
                      List<List<String>> l8,
                      List<List<String>> l9,
                      List<List<String>> l10,
                      List<List<String>> l11,
                      List<List<String>> l12,
                      List<List<String>> l13,
                      List<List<String>> l14,
                      List<List<String>> l15,
                      List<List<String>> l16,
                      List<List<String>> l17) {}

                  public record WideDto(
                      List<List<String>> l1,
                      List<List<String>> l2,
                      List<List<String>> l3,
                      List<List<String>> l4,
                      List<List<String>> l5,
                      List<List<String>> l6,
                      List<List<String>> l7,
                      List<List<String>> l8,
                      List<List<String>> l9,
                      List<List<String>> l10,
                      List<List<String>> l11,
                      List<List<String>> l12,
                      List<List<String>> l13,
                      List<List<String>> l14,
                      List<List<String>> l15,
                      List<List<String>> l16,
                      List<List<String>> l17) {}

                  @GenerateMapping
                  public interface WideMapping extends MappingSpec<Wide, WideDto> {}

                  // Elements that may be null, at every depth, both ways through a bean.
                  public record Holed(
                      List<@Nullable String> holedList,
                      @Nullable Collection<@Nullable String> holedColl,
                      Map<String, @Nullable String> holedMap,
                      @Nullable List<@Nullable List<@Nullable String>> holedLists,
                      Set<@Nullable List<String>> holedSet,
                      Map<String, @Nullable List<String>> holedMapOfLists,
                      List<String @Nullable []> holedListOfArrays,
                      @Nullable String @Nullable [] holedArray,
                      String @Nullable [] @Nullable [] holedGrid,
                      Optional<List<@Nullable String>> holedValues) {}

                  public static class HoledCommand {
                    private @Nullable List<@Nullable String> holedList;
                    private @Nullable Collection<@Nullable String> holedColl;
                    private @Nullable Map<String, @Nullable String> holedMap;
                    private @Nullable List<@Nullable List<@Nullable String>> holedLists;
                    private @Nullable Set<@Nullable List<String>> holedSet;
                    private @Nullable Map<String, @Nullable List<String>> holedMapOfLists;
                    private @Nullable List<String @Nullable []> holedListOfArrays;
                    private @Nullable String @Nullable [] holedArray;
                    private String @Nullable [] @Nullable [] holedGrid;
                    private Optional<List<@Nullable String>> holedValues = Optional.empty();

                    public @Nullable List<@Nullable String> getHoledList() { return holedList; }
                    public @Nullable Collection<@Nullable String> getHoledColl() { return holedColl; }
                    public @Nullable Map<String, @Nullable String> getHoledMap() { return holedMap; }
                    public @Nullable List<@Nullable List<@Nullable String>> getHoledLists() { return holedLists; }
                    public @Nullable Set<@Nullable List<String>> getHoledSet() { return holedSet; }
                    public @Nullable Map<String, @Nullable List<String>> getHoledMapOfLists() { return holedMapOfLists; }
                    public @Nullable List<String @Nullable []> getHoledListOfArrays() { return holedListOfArrays; }
                    public @Nullable String @Nullable [] getHoledArray() { return holedArray; }
                    public String @Nullable [] @Nullable [] getHoledGrid() { return holedGrid; }
                    public Optional<List<@Nullable String>> getHoledValues() { return holedValues; }

                    public void setHoledList(List<@Nullable String> holedList) {
                      this.holedList = holedList;
                    }
                    public void setHoledColl(@Nullable Collection<@Nullable String> holedColl) {
                      this.holedColl = holedColl;
                    }
                    public void setHoledMap(Map<String, @Nullable String> holedMap) {
                      this.holedMap = holedMap;
                    }
                    public void setHoledLists(@Nullable List<@Nullable List<@Nullable String>> holedLists) {
                      this.holedLists = holedLists;
                    }
                    public void setHoledSet(Set<@Nullable List<String>> holedSet) {
                      this.holedSet = holedSet;
                    }
                    public void setHoledMapOfLists(Map<String, @Nullable List<String>> holedMapOfLists) {
                      this.holedMapOfLists = holedMapOfLists;
                    }
                    public void setHoledListOfArrays(List<String @Nullable []> holedListOfArrays) {
                      this.holedListOfArrays = holedListOfArrays;
                    }
                    public void setHoledArray(@Nullable String @Nullable [] holedArray) {
                      this.holedArray = holedArray;
                    }
                    public void setHoledGrid(String @Nullable [] @Nullable [] holedGrid) {
                      this.holedGrid = holedGrid;
                    }
                    public void setHoledValues(Optional<List<@Nullable String>> holedValues) {
                      this.holedValues = holedValues;
                    }
                  }

                  @GenerateMapping
                  public interface HoledMapping extends MappingSpec<Holed, HoledCommand> {}

                  // A raw container copies through its wildcard view.
                  @SuppressWarnings("rawtypes")
                  public record Raw(List list, @Nullable Map map, List<List> lists) {}

                  @SuppressWarnings("rawtypes")
                  public record RawDto(List list, @Nullable Map map, List<List> lists) {}

                  @GenerateMapping
                  public interface RawMapping extends MappingSpec<Raw, RawDto> {}

                  // A wire that may hold a null where the domain holds none: parse refuses it.
                  public record Required(
                      String name, List<String> tags, Map<String, String> labels, String[] codes,
                      Set<String> roles) {}

                  public static class RequiredForm {
                    private @Nullable String name;
                    private @Nullable List<String> tags;
                    private @Nullable Map<String, String> labels;
                    private String @Nullable [] codes;
                    private @Nullable Set<String> roles;

                    public @Nullable String getName() { return name; }
                    public void setName(@Nullable String name) { this.name = name; }
                    public @Nullable List<String> getTags() { return tags; }
                    public void setTags(@Nullable List<String> tags) { this.tags = tags; }
                    public @Nullable Map<String, String> getLabels() { return labels; }
                    public void setLabels(@Nullable Map<String, String> labels) {
                      this.labels = labels;
                    }
                    public String @Nullable [] getCodes() { return codes; }
                    public void setCodes(String @Nullable [] codes) { this.codes = codes; }
                    public @Nullable Set<String> getRoles() { return roles; }
                    public void setRoles(@Nullable Set<String> roles) { this.roles = roles; }
                  }

                  @GenerateMapping
                  public interface RequiredMapping extends MappingSpec<Required, RequiredForm> {}

                  public record Member(
                      String name, @Nullable String nickname, List<String> tags,
                      @Nullable List<String> aliases) {}

                  public static class MemberBean {
                    private @Nullable String name;
                    private @Nullable String nickname;
                    private @Nullable List<String> tags;
                    private @Nullable List<String> aliases;

                    public @Nullable String getName() { return name; }
                    public void setName(@Nullable String name) { this.name = name; }
                    public @Nullable String getNickname() { return nickname; }
                    public void setNickname(@Nullable String nickname) { this.nickname = nickname; }
                    public @Nullable List<String> getTags() { return tags; }
                    public void setTags(@Nullable List<String> tags) { this.tags = tags; }
                    public @Nullable List<String> getAliases() { return aliases; }
                    public void setAliases(@Nullable List<String> aliases) { this.aliases = aliases; }
                  }

                  @GenerateMapping
                  public interface MemberMapping extends MappingSpec<Member, MemberBean> {}

                  public static final class MemberCard {
                    private final List<String> tags;
                    private final @Nullable List<String> aliases;

                    private MemberCard(List<String> tags, @Nullable List<String> aliases) {
                      this.tags = tags;
                      this.aliases = aliases;
                    }

                    public List<String> getTags() { return tags; }
                    public @Nullable List<String> getAliases() { return aliases; }
                    public static Builder builder() { return new Builder(); }

                    public static final class Builder {
                      private List<String> tags = List.of();
                      private @Nullable List<String> aliases;

                      public Builder tags(List<String> tags) { this.tags = tags; return this; }
                      public Builder aliases(@Nullable List<String> aliases) {
                        this.aliases = aliases;
                        return this;
                      }
                      public MemberCard build() { return new MemberCard(tags, aliases); }
                    }
                  }

                  public record Card(List<String> tags, @Nullable List<String> aliases) {}

                  @GenerateMapping
                  public interface CardMapping extends MappingSpec<Card, MemberCard> {}

                  @GenerateMapping
                  public interface MemberPatchMapping extends UpdateSpec<Member, MemberBean> {}

                  public record Names(List<String> names, @Nullable List<String> aliases) {}

                  public record Labels(Map<String, String> labels, @Nullable String note) {}

                  public record Merged(
                      List<String> names, @Nullable List<String> aliases,
                      Map<String, String> labels, @Nullable String note) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Merged merge(Names names, Labels labels);
                  }

                  // A wire that may hold a null the domain rules out, which parse refuses: the
                  // mapping has no asIso(), and the projection maps as the validated patch.
                  public record Applicant(String name, List<String> skills) {}

                  public record ApplicantForm(@Nullable String name, @Nullable List<String> skills) {}

                  @GenerateMapping
                  public interface ApplicantFormMapping extends MappingSpec<Applicant, ApplicantForm> {}

                  public record Profile(String id, String name) {}

                  public record ProfileView(@Nullable String name) {}

                  @GenerateMapping
                  public interface ProfileViewMapping extends MappingSpec<Profile, ProfileView> {}

                  // The same for a map's key, which parse reports, at any level the copy reaches.
                  public record Keyed(Map<String, String> byKey, List<Map<String, String>> rows) {}

                  public record KeyedForm(
                      Map<@Nullable String, String> byKey,
                      List<Map<@Nullable String, String>> rows) {}

                  @GenerateMapping
                  public interface KeyedFormMapping extends MappingSpec<Keyed, KeyedForm> {}

                  public record Shelf(String id, Map<String, String> byKey) {}

                  public record ShelfView(Map<@Nullable String, String> byKey) {}

                  @GenerateMapping
                  public interface ShelfViewMapping extends MappingSpec<Shelf, ShelfView> {}
                }
                """));

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName("an Optional, bridged or lifted, on every tier, a PATCH bean's and a message's too")
  void optionalLegsPassTheChecker() {
    Compilation compilation =
        compile(
            configuration,
            List.of(new MappingProcessor(), new CompanionAnnotationProcessor()),
            List.of("-Xep:NullableOptional:ERROR"),
            JSON_NULLABLE,
            source(
                "com.example.Profiles",
                """
                package com.example;

                import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
                import com.google.protobuf.DescriptorProtos.EnumValueOptions;
                import java.util.List;
                import java.util.Optional;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.annotations.OptionalBridge;
                import org.higherkindedj.optics.annotations.UpdateSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.Nullable;
                import org.openapitools.jackson.nullable.JsonNullable;

                public final class Profiles {
                  private Profiles() {}

                  public record Code(String value) {}

                  public record Profile(
                      Optional<List<String>> nicknames, Optional<Code> code, Optional<Code> lead) {}

                  // The leaves every tier shares; a sparse update leaves the bridge markers inert.
                  public interface ProfileLeaves {
                    @OptionalBridge
                    Optional<List<String>> nicknames();

                    @OptionalBridge
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }

                    default ValidatedPrism<String, Code> lead() {
                      return code();
                    }
                  }

                  public record ProfileDto(
                      @Nullable List<String> nicknames, @Nullable String code, Optional<String> lead) {}

                  @GenerateMapping
                  public interface ProfileMapping
                      extends MappingSpec<Profile, ProfileDto>, ProfileLeaves {}

                  // A projection with a leaf: the validated patch.
                  public record ProfileView(@Nullable String code, Optional<String> lead) {}

                  @GenerateMapping
                  public interface ProfileViewMapping
                      extends MappingSpec<Profile, ProfileView>, ProfileLeaves {}

                  // A PATCH bean: each property null until a client sends it.
                  @SuppressWarnings("NullableOptional")
                  public static class ProfileForm {
                    private @Nullable JsonNullable<List<String>> nicknames;
                    private @Nullable JsonNullable<String> code;
                    private @Nullable Optional<String> lead;

                    public @Nullable JsonNullable<List<String>> getNicknames() { return nicknames; }
                    public void setNicknames(@Nullable JsonNullable<List<String>> nicknames) {
                      this.nicknames = nicknames;
                    }
                    public @Nullable JsonNullable<String> getCode() { return code; }
                    public void setCode(@Nullable JsonNullable<String> code) { this.code = code; }
                    public @Nullable Optional<String> getLead() { return lead; }
                    public void setLead(@Nullable Optional<String> lead) { this.lead = lead; }
                  }

                  @GenerateMapping
                  public interface ProfilePatch extends UpdateSpec<Profile, ProfileForm>, ProfileLeaves {}

                  // A protobuf-java message, from the protobuf-java Error Prone brings: build writes a
                  // field with a hasX() only when its Optional holds a value, and the update follows
                  // its FieldMask.
                  public record Constant(
                      Optional<Code> name, Optional<Integer> number, Optional<EnumValueOptions> options) {}

                  public interface ConstantLeaves {
                    default ValidatedPrism<String, Code> name() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  @GenerateMapping
                  public interface ConstantMapping
                      extends MappingSpec<Constant, EnumValueDescriptorProto>, ConstantLeaves {}

                  @GenerateMapping
                  public interface ConstantPatch
                      extends UpdateSpec<Constant, EnumValueDescriptorProto>, ConstantLeaves {}

                  // A wildcard-carrying element, named in the leg: through a null scan and a leaf.
                  public record Aliases(
                      Optional<List<? extends CharSequence>> names,
                      Optional<List<? extends CharSequence>> initials) {}

                  public static class AliasesBean {
                    private @Nullable List<? extends CharSequence> names;
                    private @Nullable String initials;

                    public @Nullable List<? extends CharSequence> getNames() { return names; }
                    public void setNames(@Nullable List<? extends CharSequence> names) {
                      this.names = names;
                    }
                    public @Nullable String getInitials() { return initials; }
                    public void setInitials(@Nullable String initials) { this.initials = initials; }
                  }

                  @GenerateMapping
                  public interface AliasesMapping extends MappingSpec<Aliases, AliasesBean> {
                    default ValidatedPrism<String, List<? extends CharSequence>> initials() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(List.of(raw)), values -> values.getFirst().toString());
                    }
                  }

                  // A wildcard holder, and a component declared Optional<? extends Code>.
                  public record Contact(Optional<Code> alias, Optional<? extends Code> backup) {}

                  public static class ContactForm {
                    private @Nullable JsonNullable<? extends String> alias;
                    private @Nullable JsonNullable<String> backup;

                    public @Nullable JsonNullable<? extends String> getAlias() { return alias; }
                    public void setAlias(@Nullable JsonNullable<? extends String> alias) {
                      this.alias = alias;
                    }
                    public @Nullable JsonNullable<String> getBackup() { return backup; }
                    public void setBackup(@Nullable JsonNullable<String> backup) { this.backup = backup; }
                  }

                  @GenerateMapping
                  public interface ContactPatch extends UpdateSpec<Contact, ContactForm> {
                    default ValidatedPrism<String, Code> alias() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }

                    default ValidatedPrism<String, Code> backup() {
                      return alias();
                    }
                  }

                  // What the processor offers for a @Nullable component a nested spec or a lifted
                  // leaf would be handed: an Optional the bridge carries.
                  public record Address(String city) {}

                  public record AddressDto(String city) {}

                  @GenerateMapping
                  public interface AddressMapping extends MappingSpec<Address, AddressDto> {}

                  public record Order(Optional<Address> address, Optional<List<Code>> codes) {}

                  public record OrderDto(@Nullable AddressDto address, @Nullable List<String> codes) {}

                  @GenerateMapping
                  public interface OrderMapping extends MappingSpec<Order, OrderDto> {
                    @OptionalBridge
                    Optional<Address> address();

                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }
                }
                """));

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName(
      "a nullable type argument, through parse's ladder and into a wire whose elements may be"
          + " null, and a sparse update over an array of nullable elements, sent as it is or"
          + " through a JsonNullable")
  void aNullableTypeArgumentPassesTheChecker() {
    Compilation compilation =
        compile(
            configuration,
            List.of(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor()),
            List.of("-Xep:NullableOptional:ERROR"),
            JSON_NULLABLE,
            source(
                "com.example.Arguments",
                """
                package com.example;

                import java.util.List;
                import java.util.Map;
                import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                import org.higherkindedj.hkt.validated.FieldError;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.Flatten;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.GenerateMerge;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.annotations.UpdateSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.Nullable;
                import org.openapitools.jackson.nullable.JsonNullable;

                public final class Arguments {
                  private Arguments() {}

                  public record Code(String value) {}

                  // The same nullable type argument on both sides, through parse and asIso.
                  public record Holed(
                      List<@Nullable String> tags,
                      Map<String, @Nullable String> byKey,
                      int count,
                      String name) {}

                  public record HoledDto(
                      List<@Nullable String> tags,
                      Map<String, @Nullable String> byKey,
                      int count,
                      String name) {}

                  // parse widens a copy holding no null into a record whose elements may be.
                  public record Grown(List<@Nullable String> tags, Map<String, @Nullable String> byKey) {}

                  public static class GrownView {
                    public List<String> getTags() { return List.of(); }
                    public Map<String, String> getByKey() { return Map.of(); }
                  }

                  @GenerateMapping
                  public interface GrownMapping extends MappingSpec<Grown, GrownView> {}

                  // The canonical constructor takes less than the component declares, so build
                  // hands it the copy as it is. A leaf withholds asIso.
                  public record Taken(List<String> tags, Code code) {}

                  public record TakenDto(List<@Nullable String> tags, String code) {
                    public TakenDto(List<String> tags, String code) {
                      this.tags = new java.util.ArrayList<>(tags);
                      this.code = code;
                    }
                  }

                  @GenerateMapping
                  public interface TakenMapping extends MappingSpec<Taken, TakenDto> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  // A nested array's cells widen nowhere, so parse reads them as it did.
                  public record Gridded(@Nullable String[][] grid) {}

                  public static class GriddedView {
                    public String[][] getGrid() { return new String[0][]; }
                  }

                  @GenerateMapping
                  public interface GriddedMapping extends MappingSpec<Gridded, GriddedView> {}

                  @GenerateMapping
                  public interface HoledMapping extends MappingSpec<Holed, HoledDto> {}

                  // Non-null elements built into a wire whose elements may be null. A leaf
                  // withholds asIso, whose reverse copies the other way.
                  public record Wid(
                      @Nullable List<String> maybe, List<List<String>> grid, String[] cells, Code code) {}

                  public record WidDto(
                      @Nullable List<@Nullable String> maybe,
                      List<List<@Nullable String>> grid,
                      @Nullable String[] cells,
                      String code) {}

                  @GenerateMapping
                  public interface WidMapping extends MappingSpec<Wid, WidDto> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  public record Tagged(List<String> tags) {}

                  public static class TaggedBean {
                    private List<@Nullable String> tags = List.of();

                    public List<@Nullable String> getTags() { return tags; }
                    public void setTags(List<@Nullable String> tags) { this.tags = tags; }
                  }

                  @GenerateMapping
                  public interface TaggedBeanMapping extends MappingSpec<Tagged, TaggedBean> {}

                  public static final class TaggedCard {
                    private final List<@Nullable String> tags;

                    private TaggedCard(List<@Nullable String> tags) { this.tags = tags; }

                    public List<@Nullable String> getTags() { return tags; }
                    public static Builder builder() { return new Builder(); }

                    public static final class Builder {
                      private List<@Nullable String> tags = List.of();

                      public Builder tags(List<@Nullable String> tags) {
                        this.tags = tags;
                        return this;
                      }
                      public TaggedCard build() { return new TaggedCard(tags); }
                    }
                  }

                  @GenerateMapping
                  public interface TaggedCardMapping extends MappingSpec<Tagged, TaggedCard> {}

                  public record Names(List<String> names) {}

                  public record HoledNames(List<@Nullable String> names) {}

                  public record Codes(String code) {}

                  public record Merged(List<@Nullable String> names, String code) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Merged merge(Names names, Codes codes);
                  }

                  @GenerateMerge
                  public interface CheckedAssembly {
                    Validated<NonEmptyList<FieldError>, Merged> merge(Names names, Codes codes);

                    default ValidatedPrism<String, String> code() {
                      return ValidatedPrism.of(Validated::validNel, code -> code);
                    }
                  }

                  @GenerateMerge
                  public interface HoledAssembly {
                    Validated<NonEmptyList<FieldError>, Merged> merge(HoledNames names, Codes codes);

                    default ValidatedPrism<String, String> code() {
                      return ValidatedPrism.of(Validated::validNel, code -> code);
                    }
                  }

                  public record Inner(List<@Nullable String> tags, String note) {}

                  public record Outer(String id, Inner inner) {}

                  public record OuterDto(String id, List<@Nullable String> tags, String note) {}

                  @GenerateMapping
                  public interface OuterMapping extends MappingSpec<Outer, OuterDto> {
                    @Flatten
                    Inner inner();
                  }

                  // A leaf's write-back can fail, so the projection maps as the validated patch.
                  public record Profile(String id, List<@Nullable String> tags, Code code) {}

                  public record ProfileView(List<@Nullable String> tags, String code) {}

                  @GenerateMapping
                  public interface ProfileViewMapping extends MappingSpec<Profile, ProfileView> {
                    default ValidatedPrism<String, Code> code() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }

                  public record Sparse(List<@Nullable String> tags, String name) {}

                  public static class SparseRequest {
                    private @Nullable List<@Nullable String> tags;
                    private @Nullable String name;

                    public @Nullable List<@Nullable String> getTags() { return tags; }
                    public void setTags(@Nullable List<@Nullable String> tags) { this.tags = tags; }
                    public @Nullable String getName() { return name; }
                    public void setName(@Nullable String name) { this.name = name; }
                  }

                  @GenerateMapping
                  public interface SparsePatch extends UpdateSpec<Sparse, SparseRequest> {}

                  // A sparse update over an array whose elements may be null, beside one whose
                  // elements the domain declares non-null.
                  public record Grid(String id, @Nullable String[] cells, String[] plain) {}

                  public static final class GridPatch {
                    private @Nullable String @Nullable [] cells;
                    private @Nullable String @Nullable [] plain;

                    public @Nullable String @Nullable [] getCells() { return cells; }

                    public void setCells(@Nullable String @Nullable [] cells) { this.cells = cells; }

                    public @Nullable String @Nullable [] getPlain() { return plain; }

                    public void setPlain(@Nullable String @Nullable [] plain) { this.plain = plain; }
                  }

                  @GenerateMapping
                  public interface GridPatchMapping extends UpdateSpec<Grid, GridPatch> {}

                  @SuppressWarnings("NullableOptional")
                  public static final class GridSent {
                    private @Nullable JsonNullable<@Nullable String @Nullable []> cells;

                    public @Nullable JsonNullable<@Nullable String @Nullable []> getCells() {
                      return cells;
                    }

                    public void setCells(@Nullable JsonNullable<@Nullable String @Nullable []> cells) {
                      this.cells = cells;
                    }
                  }

                  @GenerateMapping
                  public interface GridSentMapping extends UpdateSpec<Grid, GridSent> {}

                  // Chunked, beside a wildcard-typed leg a copy reads as its capture, and ending in a
                  // primitive the ladder hands on boxed.
                  public record Wide(
                      List<? extends Number> w,
                      List<@Nullable String> c0,
                      List<@Nullable String> c1,
                      List<@Nullable String> c2,
                      List<@Nullable String> c3,
                      List<@Nullable String> c4,
                      List<@Nullable String> c5,
                      List<@Nullable String> c6,
                      List<@Nullable String> c7,
                      List<@Nullable String> c8,
                      List<@Nullable String> c9,
                      List<@Nullable String> c10,
                      List<@Nullable String> c11,
                      List<@Nullable String> c12,
                      List<@Nullable String> c13,
                      List<@Nullable String> c14,
                      List<@Nullable String> c15,
                      double d) {}

                  public record WideDto(
                      List<? extends Number> w,
                      List<@Nullable String> c0,
                      List<@Nullable String> c1,
                      List<@Nullable String> c2,
                      List<@Nullable String> c3,
                      List<@Nullable String> c4,
                      List<@Nullable String> c5,
                      List<@Nullable String> c6,
                      List<@Nullable String> c7,
                      List<@Nullable String> c8,
                      List<@Nullable String> c9,
                      List<@Nullable String> c10,
                      List<@Nullable String> c11,
                      List<@Nullable String> c12,
                      List<@Nullable String> c13,
                      List<@Nullable String> c14,
                      List<@Nullable String> c15,
                      double d) {}

                  @GenerateMapping
                  public interface WideMapping extends MappingSpec<Wide, WideDto> {}
                }
                """));

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName(
      "a key that is itself a container, copied or passed through beside converted values, on"
          + " every tier that parses, a parse-only view whose keys may hold a null inside, values"
          + " whose type argument may be null, an array of nullable elements in any position, a"
          + " passed-through key both sides declare holding a nullable type argument, sent"
          + " through a JsonNullable too, and a key the domain narrows beside a list that may hold"
          + " a null")
  void aKeyScannedInsidePassesTheChecker() {
    Compilation compilation =
        compile(
            configuration,
            List.of(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor()),
            List.of("-Xep:NullableOptional:ERROR"),
            JSON_NULLABLE,
            source(
                "com.example.Paths",
                """
                package com.example;

                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Set;
                import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                import org.higherkindedj.hkt.validated.FieldError;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.GenerateMerge;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.annotations.OptionalBridge;
                import org.higherkindedj.optics.annotations.UpdateSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.Nullable;
                import org.openapitools.jackson.nullable.JsonNullable;

                public final class Paths {
                  private Paths() {}

                  public record Code(String value) {}

                  static ValidatedPrism<String, Code> code() {
                    return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                  }

                  // Both ways, the sides agreeing, over each kind of key and both value forms.
                  public record Shelf(
                      Map<List<String>, String> byPath,
                      Map<List<@Nullable String>, List<@Nullable String>> holed,
                      List<Map<Set<String>, String>> rows,
                      Map<String[], String> arrays,
                      Map<Optional<List<String>>, String> optionals,
                      Map<List<String>, Code> coded,
                      Optional<Map<List<String>, Code>> bridged) {}

                  public record ShelfDto(
                      Map<List<String>, String> byPath,
                      Map<List<@Nullable String>, List<@Nullable String>> holed,
                      List<Map<Set<String>, String>> rows,
                      Map<String[], String> arrays,
                      Map<Optional<List<String>>, String> optionals,
                      Map<List<String>, String> coded,
                      @Nullable Map<List<String>, String> bridged) {}

                  @GenerateMapping
                  public interface ShelfMapping extends MappingSpec<Shelf, ShelfDto> {
                    default ValidatedPrism<String, Code> coded() { return code(); }

                    @OptionalBridge
                    default ValidatedPrism<String, Code> bridged() { return code(); }
                  }

                  // A view that only parses, whose keys may hold a null inside, which parse reports.
                  public record Narrow(Map<List<String>, String> byPath, Map<List<String>, Code> coded) {}

                  public static final class NarrowView {
                    private final Map<List<@Nullable String>, String> byPath;
                    private final Map<List<@Nullable String>, String> coded;

                    public NarrowView(
                        Map<List<@Nullable String>, String> byPath,
                        Map<List<@Nullable String>, String> coded) {
                      this.byPath = byPath;
                      this.coded = coded;
                    }

                    public Map<List<@Nullable String>, String> getByPath() { return byPath; }

                    public Map<List<@Nullable String>, String> getCoded() { return coded; }
                  }

                  @GenerateMapping
                  public interface NarrowViewMapping extends MappingSpec<Narrow, NarrowView> {
                    default ValidatedPrism<String, Code> coded() { return code(); }
                  }

                  // A projection's patch, a sparse update and a fallible merge.
                  public record Rack(
                      String id, Map<List<String>, String> byPath, Map<List<String>, Code> coded) {}

                  public record RackView(Map<List<String>, String> byPath, Map<List<String>, String> coded) {}

                  @GenerateMapping
                  public interface RackViewMapping extends MappingSpec<Rack, RackView> {
                    default ValidatedPrism<String, Code> coded() { return code(); }
                  }

                  public static final class RackPatch {
                    private @Nullable Map<List<String>, String> byPath;
                    private @Nullable Map<List<String>, String> coded;

                    public @Nullable Map<List<String>, String> getByPath() { return byPath; }

                    public void setByPath(@Nullable Map<List<String>, String> byPath) {
                      this.byPath = byPath;
                    }

                    public @Nullable Map<List<String>, String> getCoded() { return coded; }

                    public void setCoded(@Nullable Map<List<String>, String> coded) {
                      this.coded = coded;
                    }
                  }

                  @GenerateMapping
                  public interface RackPatchMapping extends UpdateSpec<Rack, RackPatch> {
                    default ValidatedPrism<String, Code> coded() { return code(); }
                  }

                  public record Source(Map<List<String>, String> byPath) {}

                  public record Note(String note) {}

                  public record Merged(Map<List<String>, String> byPath, String note) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Validated<NonEmptyList<FieldError>, Merged> merge(Source source, Note note);

                    default ValidatedPrism<String, String> note() {
                      return ValidatedPrism.of(Validated::validNel, note -> note);
                    }
                  }

                  // Values a leaf converts from a wire type whose argument may be null, beside
                  // list keys, dense, bridged and sparse.
                  static ValidatedPrism<List<@Nullable String>, Code> joined() {
                    return ValidatedPrism.of(
                        raw -> Validated.validNel(new Code(String.valueOf(raw))),
                        code -> List.of(code.value()));
                  }

                  public record Joined(
                      Map<List<String>, Code> byPath, Optional<Map<List<String>, Code>> bridged) {}

                  public record JoinedDto(
                      Map<List<String>, List<@Nullable String>> byPath,
                      @Nullable Map<List<String>, List<@Nullable String>> bridged) {}

                  @GenerateMapping
                  public interface JoinedMapping extends MappingSpec<Joined, JoinedDto> {
                    default ValidatedPrism<List<@Nullable String>, Code> byPath() { return joined(); }

                    @OptionalBridge
                    default ValidatedPrism<List<@Nullable String>, Code> bridged() {
                      return joined();
                    }
                  }

                  public static final class JoinedPatch {
                    private @Nullable Map<List<String>, List<@Nullable String>> byPath;

                    public @Nullable Map<List<String>, List<@Nullable String>> getByPath() {
                      return byPath;
                    }

                    public void setByPath(@Nullable Map<List<String>, List<@Nullable String>> byPath) {
                      this.byPath = byPath;
                    }
                  }

                  @GenerateMapping
                  public interface JoinedPatchMapping extends UpdateSpec<Joined, JoinedPatch> {
                    default ValidatedPrism<List<@Nullable String>, Code> byPath() { return joined(); }
                  }

                  // An array whose elements may be null, as a key, a value and an element.
                  public record Cells(
                      @Nullable String[] holed,
                      Map<@Nullable String[], String> keys,
                      Map<String, @Nullable String[]> values,
                      List<@Nullable String[]> rows,
                      Map<String[], String> plainKeys) {}

                  public record CellsDto(
                      @Nullable String[] holed,
                      Map<@Nullable String[], String> keys,
                      Map<String, @Nullable String[]> values,
                      List<@Nullable String[]> rows,
                      Map<String[], String> plainKeys) {}

                  @GenerateMapping
                  public interface CellsMapping extends MappingSpec<Cells, CellsDto> {}

                  // Keys passed through beside converted values, both sides declaring a nullable
                  // type argument inside them: scanned containers, an array and a generic key,
                  // dense, bridged and sparse.
                  public record Tag<T extends @Nullable Object>(T value) {}

                  public record Agreed(
                      Map<List<@Nullable String>, Code> byPath,
                      Optional<Map<List<@Nullable String>, Code>> bridged,
                      Map<@Nullable String[], Code> byCell,
                      Map<Tag<@Nullable String>, Code> byTag,
                      Optional<Map<Tag<@Nullable String>, Code>> tags) {}

                  public record AgreedDto(
                      Map<List<@Nullable String>, String> byPath,
                      @Nullable Map<List<@Nullable String>, String> bridged,
                      Map<@Nullable String[], String> byCell,
                      Map<Tag<@Nullable String>, String> byTag,
                      @Nullable Map<Tag<@Nullable String>, String> tags) {}

                  @GenerateMapping
                  public interface AgreedMapping extends MappingSpec<Agreed, AgreedDto> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }

                    @OptionalBridge
                    default ValidatedPrism<String, Code> bridged() { return code(); }

                    default ValidatedPrism<String, Code> byCell() { return code(); }

                    default ValidatedPrism<String, Code> byTag() { return code(); }

                    @OptionalBridge
                    default ValidatedPrism<String, Code> tags() { return code(); }
                  }

                  public static final class AgreedPatch {
                    private @Nullable Map<List<@Nullable String>, String> byPath;

                    public @Nullable Map<List<@Nullable String>, String> getByPath() { return byPath; }

                    public void setByPath(@Nullable Map<List<@Nullable String>, String> byPath) {
                      this.byPath = byPath;
                    }
                  }

                  @GenerateMapping
                  public interface AgreedPatchMapping extends UpdateSpec<Agreed, AgreedPatch> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }

                  @SuppressWarnings("NullableOptional")
                  public static final class AgreedHeld {
                    private @Nullable JsonNullable<Map<List<@Nullable String>, String>> byPath;
                    private @Nullable JsonNullable<Map<List<@Nullable String>, String>> bridged;
                    private @Nullable JsonNullable<Map<Tag<@Nullable String>, String>> tags;

                    public @Nullable JsonNullable<Map<List<@Nullable String>, String>> getByPath() {
                      return byPath;
                    }

                    public void setByPath(
                        @Nullable JsonNullable<Map<List<@Nullable String>, String>> byPath) {
                      this.byPath = byPath;
                    }

                    public @Nullable JsonNullable<Map<List<@Nullable String>, String>> getBridged() {
                      return bridged;
                    }

                    public void setBridged(
                        @Nullable JsonNullable<Map<List<@Nullable String>, String>> bridged) {
                      this.bridged = bridged;
                    }

                    public @Nullable JsonNullable<Map<Tag<@Nullable String>, String>> getTags() {
                      return tags;
                    }

                    public void setTags(@Nullable JsonNullable<Map<Tag<@Nullable String>, String>> tags) {
                      this.tags = tags;
                    }
                  }

                  @GenerateMapping
                  public interface AgreedHeldMapping extends UpdateSpec<Agreed, AgreedHeld> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }

                    default ValidatedPrism<String, Code> bridged() { return code(); }

                    default ValidatedPrism<String, Code> tags() { return code(); }
                  }

                  // The sent map declared nullable inside its holder.
                  @SuppressWarnings("NullableOptional")
                  public static final class AgreedLoose {
                    private @Nullable JsonNullable<@Nullable Map<List<@Nullable String>, String>> byPath;
                    private @Nullable JsonNullable<@Nullable Map<List<@Nullable String>, String>> bridged;

                    public @Nullable JsonNullable<@Nullable Map<List<@Nullable String>, String>> getByPath() {
                      return byPath;
                    }

                    public void setByPath(
                        @Nullable JsonNullable<@Nullable Map<List<@Nullable String>, String>> byPath) {
                      this.byPath = byPath;
                    }

                    public @Nullable JsonNullable<@Nullable Map<List<@Nullable String>, String>> getBridged() {
                      return bridged;
                    }

                    public void setBridged(
                        @Nullable JsonNullable<@Nullable Map<List<@Nullable String>, String>> bridged) {
                      this.bridged = bridged;
                    }
                  }

                  @GenerateMapping
                  public interface AgreedLooseMapping extends UpdateSpec<Agreed, AgreedLoose> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }

                    default ValidatedPrism<String, Code> bridged() { return code(); }
                  }

                  // A key the domain narrows, beside a list that may hold a null, on a view that only
                  // parses: the list keeps the nullness its leg declares.
                  public record Beside(Map<List<String>, Code> byPath, List<@Nullable String> notes) {}

                  public static final class BesideView {
                    private final Map<List<@Nullable String>, String> byPath;
                    private final List<@Nullable String> notes;

                    public BesideView(
                        Map<List<@Nullable String>, String> byPath, List<@Nullable String> notes) {
                      this.byPath = byPath;
                      this.notes = notes;
                    }

                    public Map<List<@Nullable String>, String> getByPath() { return byPath; }

                    public List<@Nullable String> getNotes() { return notes; }
                  }

                  @GenerateMapping
                  public interface BesideMapping extends MappingSpec<Beside, BesideView> {
                    default ValidatedPrism<String, Code> byPath() { return code(); }
                  }
                }
                """));

    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName(
      "a leaf or nested spec lifted over a wire whose elements, keys or values may be null, on"
          + " every tier, and a caller's own bulk parse of such a container")
  void aLiftedLeafOverNullableElementsPassesTheChecker() {
    Compilation compilation =
        compile(
            configuration,
            List.of(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor()),
            List.of("-Xep:NullableOptional:ERROR"),
            source(
                "com.example.Lifted",
                """
                package com.example;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.Set;
                import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                import org.higherkindedj.hkt.validated.FieldError;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.MapKey;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.annotations.OptionalBridge;
                import org.higherkindedj.optics.annotations.UpdateSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.Nullable;

                public final class Lifted {
                  private Lifted() {}

                  public record Code(String value) {}

                  public record Row(String[] cells) {}

                  static ValidatedPrism<String, Code> code() {
                    return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                  }

                  public record Item(Code code) {}

                  public record ItemDto(String code) {}

                  @GenerateMapping
                  public interface ItemMapping extends MappingSpec<Item, ItemDto> {
                    default ValidatedPrism<String, Code> code() { return Lifted.code(); }
                  }

                  public record Pair<A, B>(A first, B second) {}

                  public record Lift(
                      List<Code> list,
                      Set<Code> set,
                      Code[] array,
                      Row[] grid,
                      Map<String, Code> values,
                      Map<String, Code> keyedValues,
                      Map<Code, String> keys,
                      Map<Code, Code> entries,
                      Map<Code, List<String>> rows,
                      List<Item> items,
                      Map<String, Item> byName,
                      List<Row> lines,
                      List<Code> pairs) {}

                  // An inbound DTO, whose elements, keys and values may each be null.
                  public record LiftDto(
                      List<@Nullable String> list,
                      @Nullable Set<@Nullable String> set,
                      @Nullable String[] array,
                      String[] @Nullable [] grid,
                      Map<String, @Nullable String> values,
                      Map<@Nullable String, String> keyedValues,
                      Map<@Nullable String, @Nullable String> keys,
                      Map<@Nullable String, @Nullable String> entries,
                      Map<@Nullable String, List<String>> rows,
                      List<@Nullable ItemDto> items,
                      Map<String, @Nullable ItemDto> byName,
                      List<String @Nullable []> lines,
                      List<@Nullable Pair<String, String>> pairs) {}

                  @GenerateMapping
                  public interface LiftMapping extends MappingSpec<Lift, LiftDto> {
                    default ValidatedPrism<String, Code> list() { return code(); }

                    default ValidatedPrism<String, Code> set() { return code(); }

                    default ValidatedPrism<String, Code> array() { return code(); }

                    default ValidatedPrism<String[], Row> grid() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Row(raw)), Row::cells);
                    }

                    default ValidatedPrism<String, Code> values() { return code(); }

                    default ValidatedPrism<String, Code> keyedValues() { return code(); }

                    @MapKey("keys")
                    default ValidatedPrism<String, Code> keysKey() { return code(); }

                    default ValidatedPrism<String, Code> entries() { return code(); }

                    @MapKey("entries")
                    default ValidatedPrism<String, Code> entriesKey() { return code(); }

                    @MapKey("rows")
                    default ValidatedPrism<String, Code> rowsKey() { return code(); }

                    default ValidatedPrism<String[], Row> lines() { return grid(); }

                    default ValidatedPrism<Pair<String, String>, Code> pairs() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(new Code(raw.first())),
                          code -> new Pair<>(code.value(), code.value()));
                    }
                  }

                  // A leaf over the lists it builds, and bridges through a wildcard and a variable.
                  public record Codes(List<String> values) {}

                  public record Grouped(List<Codes> groups) {}

                  public record GroupedDto(List<@Nullable List<String>> groups) {}

                  @GenerateMapping
                  public interface GroupedMapping extends MappingSpec<Grouped, GroupedDto> {
                    default ValidatedPrism<List<String>, Codes> groups() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Codes(raw)), Codes::values);
                    }
                  }

                  public record Wild(Optional<? extends List<Code>> codes) {}

                  public record Bound<O extends Optional<List<Code>>>(O codes) {}

                  public record WildDto(@Nullable List<@Nullable String> codes) {}

                  @GenerateMapping
                  public interface WildMapping extends MappingSpec<Wild, WildDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  @GenerateMapping
                  public interface BoundMapping
                      extends MappingSpec<Bound<Optional<List<Code>>>, WildDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  // A bean's setter, a getter-only list and a bridged set, then a record's bridge.
                  public record Order(List<Code> codes, List<Code> extras, Optional<Set<Code>> maybe) {}

                  public static final class OrderBean {
                    private @Nullable List<@Nullable String> codes;
                    private final List<@Nullable String> extras = new ArrayList<>();
                    private @Nullable Set<@Nullable String> maybe;

                    public @Nullable List<@Nullable String> getCodes() { return codes; }

                    public void setCodes(@Nullable List<@Nullable String> codes) { this.codes = codes; }

                    public List<@Nullable String> getExtras() { return extras; }

                    public @Nullable Set<@Nullable String> getMaybe() { return maybe; }

                    public void setMaybe(@Nullable Set<@Nullable String> maybe) { this.maybe = maybe; }
                  }

                  @GenerateMapping
                  public interface OrderBeanMapping extends MappingSpec<Order, OrderBean> {
                    default ValidatedPrism<String, Code> codes() { return code(); }

                    default ValidatedPrism<String, Code> extras() { return code(); }

                    default ValidatedPrism<String, Code> maybe() { return code(); }
                  }

                  public record Tagged(Optional<List<Code>> codes, Optional<Map<String, Code>> prices) {}

                  public record TaggedDto(
                      @Nullable List<@Nullable String> codes,
                      @Nullable Map<@Nullable String, String> prices) {}

                  @GenerateMapping
                  public interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {
                    @OptionalBridge
                    default ValidatedPrism<String, Code> codes() { return code(); }

                    @OptionalBridge
                    default ValidatedPrism<String, Code> prices() { return code(); }
                  }

                  // A projection, its sparse update, and a parse-only view into an array whose
                  // elements the domain declares nullable.
                  public record Profile(String id, List<Code> codes) {}

                  public record ProfileView(List<@Nullable String> codes) {}

                  @GenerateMapping
                  public interface ProfileViewMapping extends MappingSpec<Profile, ProfileView> {
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  public static final class ProfilePatch {
                    private @Nullable List<@Nullable String> codes;

                    public @Nullable List<@Nullable String> getCodes() { return codes; }

                    public void setCodes(@Nullable List<@Nullable String> codes) { this.codes = codes; }
                  }

                  @GenerateMapping
                  public interface ProfilePatchMapping extends UpdateSpec<Profile, ProfilePatch> {
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  public record Loose(@Nullable Code[] codes) {}

                  public static final class LooseView {
                    private final String[] codes;

                    public LooseView(String[] codes) { this.codes = codes; }

                    public String[] getCodes() { return codes; }
                  }

                  @GenerateMapping
                  public interface LooseViewMapping extends MappingSpec<Loose, LooseView> {
                    default ValidatedPrism<String, Code> codes() { return code(); }
                  }

                  // A caller validating what a JSON body held, nulls and all.
                  static Validated<NonEmptyList<FieldError>, List<Code>> list(
                      List<@Nullable String> raw) {
                    return code().parseAll(raw);
                  }

                  static Validated<NonEmptyList<FieldError>, Set<Code>> set(
                      Set<@Nullable String> raw) {
                    return code().parseAll(raw);
                  }

                  static Validated<NonEmptyList<FieldError>, Code[]> array(@Nullable String[] raw) {
                    return code().parseAll(raw, Code[]::new);
                  }

                  static Validated<NonEmptyList<FieldError>, Map<String, Code>> values(
                      Map<@Nullable String, @Nullable String> raw) {
                    return code().parseValues(raw);
                  }

                  static Validated<NonEmptyList<FieldError>, Map<Code, String>> keys(
                      Map<@Nullable String, @Nullable String> raw) {
                    return code().parseKeys(raw);
                  }

                  static Validated<NonEmptyList<FieldError>, Map<Code, Code>> entries(
                      Map<@Nullable String, @Nullable String> raw) {
                    return code().parseEntries(raw, code());
                  }
                }
                """),
            source(
                "com.example.Wide",
                """
                package com.example;

                import java.util.List;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.Nullable;

                // More than 16 components, so parse assembles in chunks.
                public final class Wide {
                  private Wide() {}

                  public record Code(String value) {}

                  public record Big(
                      String a1, String a2, String a3, String a4, String a5, String a6,
                      String a7, String a8, String a9, String a10, String a11, String a12,
                      String a13, String a14, String a15, String a16, List<Code> codes) {}

                  public record BigDto(
                      String a1, String a2, String a3, String a4, String a5, String a6,
                      String a7, String a8, String a9, String a10, String a11, String a12,
                      String a13, String a14, String a15, String a16,
                      List<@Nullable String> codes) {}

                  @GenerateMapping
                  public interface BigMapping extends MappingSpec<Big, BigDto> {
                    default ValidatedPrism<String, Code> codes() {
                      return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                    }
                  }
                }
                """));

    assertThat(compilation).succeeded();
  }
}
