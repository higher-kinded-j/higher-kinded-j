// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.NullAwayCompiler.compile;
import static org.higherkindedj.optics.processing.NullAwayCompiler.source;

import com.google.testing.compile.Compilation;
import java.util.List;
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
 * NullableOptional} check, run by default, to the guards.
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
}
