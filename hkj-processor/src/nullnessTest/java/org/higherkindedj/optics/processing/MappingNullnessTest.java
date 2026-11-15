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
 * They share one javac run per configuration, which also holds Error Prone's {@code
 * NullableOptional} check, run by default, to the guards. The {@code Optional} legs, bridged and
 * lifted, have a run of their own, on every tier that parses them: over a record, a PATCH bean
 * holding {@code JsonNullable} and {@code Optional} properties, and a protobuf-java message, whose
 * {@code build} writes a field with a {@code hasX()} only when its {@code Optional} holds a value,
 * with a wildcard-carrying element and holder among them.
 *
 * <p>Each two-way pair declares the same nullness on both sides, and a nullable element type is
 * written by {@code build} into a bean that is only written: a checker does not infer a nullable
 * type argument through the {@code parse} ladder, and {@code asIso()} reads a wire component
 * straight into the domain's.
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

                  // Elements that may be null, written by build into a bean that is only written.
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
                }
                """));

    assertThat(compilation).succeeded();
  }
}
