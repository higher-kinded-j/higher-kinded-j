// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A generated Impl that names or calls a deprecated element answers for it on its class, whatever
 * the tier, so a {@code -Werror} build compiles. The deprecation is the author's: they chose to map
 * the deprecated type by declaring the spec, and their own {@code @SuppressWarnings} cannot reach a
 * generated file. The class is where the answer goes, since its implements clause names the spec,
 * and once the domain or the wire is deprecated nearly every member names it.
 *
 * <p>Every fixture compiles together under {@code -Xlint:all -Werror}, one wire record arriving
 * from a class file, as a record from another module does. Each row then names an Impl and the one
 * class-level {@code @SuppressWarnings} it carries, or none, which an unannotated row's Impl shows
 * it needs no suppression for. The same Impls are compiled again with that annotation taken off,
 * and each row's Impl must then warn in exactly the categories it suppressed, so each annotated row
 * proves its fixture reaches an element deprecated that way. Each fixture reaches one kind of
 * deprecated element, so each source the analysis reads is pinned on its own.
 */
@DisplayName(
    "MappingProcessor and MergeProcessor - a deprecated type or member in a generated Impl")
class MappingDeprecationTest {

  private static final String PKG = "com.example.deprecation";

  private static final String IMPORTS =
      """
      import java.util.List;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.ReadOnly;
      import org.higherkindedj.optics.annotations.UpdateSpec;
      import org.higherkindedj.optics.validated.ValidatedPrism;

      """;

  /** Mapping a deprecated v1 DTO: the spec's own suppression does not reach the Impl. */
  private static final String API =
      """
      final class Api {
        record Person(String name, int age) {}

        @Deprecated
        record PersonV1Dto(String name, int age) {}

        @GenerateMapping
        @SuppressWarnings("deprecation")
        interface PersonV1Mapping extends MappingSpec<Person, PersonV1Dto> {}
      }
      """;

  private static final String DEPRECATED_DOMAIN =
      """
      final class DeprecatedDomain {
        @Deprecated
        record Person(String name, int age) {}

        record PersonDto(String name, int age) {}

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
      }
      """;

  private static final String REMOVAL_WIRE =
      """
      final class RemovalWire {
        record Person(String name, int age) {}

        @Deprecated(forRemoval = true)
        record PersonDto(String name, int age) {}

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
      }
      """;

  /** A deprecated record component deprecates its accessor, which parse reads. */
  private static final String COMPONENT_ACCESSOR =
      """
      final class ComponentAccessor {
        record Person(String name, int age) {}

        @SuppressWarnings("deprecation")
        record PersonDto(@Deprecated String name, int age) {}

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
      }
      """;

  private static final String BEAN_ACCESSORS =
      """
      final class BeanAccessors {
        record Person(String name, int age) {}

        public static class PersonBean {
          private String name;
          private int age;

          public PersonBean() {}

          @Deprecated
          public String getName() { return name; }

          @Deprecated
          public void setName(String name) { this.name = name; }

          public int getAge() { return age; }

          public void setAge(int age) { this.age = age; }
        }

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonBean> {}
      }
      """;

  private static final String BUILDER_WIRE =
      """
      final class BuilderWire {
        record Person(String name, int age) {}

        public static final class PersonMsg {
          private final String name;
          private final int age;

          private PersonMsg(String name, int age) {
            this.name = name;
            this.age = age;
          }

          public String getName() { return name; }

          public int getAge() { return age; }

          @Deprecated
          public static Builder newBuilder() { return new Builder(); }

          public static final class Builder {
            private String name;
            private int age;

            @Deprecated
            public Builder setName(String name) {
              this.name = name;
              return this;
            }

            public Builder setAge(int age) {
              this.age = age;
              return this;
            }

            public PersonMsg build() { return new PersonMsg(name, age); }
          }
        }

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonMsg> {}
      }
      """;

  /** A deprecated type on both sides, copied by identity, and inferred by parse's lambda. */
  private static final String COMPONENT_TYPE =
      """
      final class ComponentType {
        @Deprecated
        record Code(String value) {}

        record Item(String name, Code code, List<Code> history) {}

        record ItemDto(String name, Code code, List<Code> history) {}

        @GenerateMapping
        interface ItemMapping extends MappingSpec<Item, ItemDto> {}
      }
      """;

  private static final String PAYMENTS =
      """
      final class Payments {
        sealed interface Payment permits Card, Bank {}

        record Card(String number) implements Payment {}

        record Bank(String iban) implements Payment {}

        sealed interface PaymentDto permits CardDto, BankDto {}

        @Deprecated
        record CardDto(String number) implements PaymentDto {}

        record BankDto(String iban) implements PaymentDto {}

        @GenerateMapping
        interface CardMapping extends MappingSpec<Card, CardDto> {}

        @GenerateMapping
        interface BankMapping extends MappingSpec<Bank, BankDto> {}

        @GenerateMapping
        interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}
      }
      """;

  /** The leaf cache overrides the leaf and calls it, and javac counts both as a use. */
  private static final String DEFAULT_LEAF =
      """
      final class DefaultLeaf {
        record Tag(String value) {}

        record Item(String id, Tag tag) {}

        record ItemDto(String id, String tag) {}

        @GenerateMapping
        interface ItemMapping extends MappingSpec<Item, ItemDto> {
          @Deprecated
          default ValidatedPrism<String, Tag> tag() {
            return ValidatedPrism.of(raw -> Validated.validNel(new Tag(raw)), Tag::value);
          }
        }
      }
      """;

  private static final String ABSTRACT_LEAF =
      """
      final class AbstractLeaf {
        record Page<T>(T item, int total) {}

        record PageDto<D>(D item, int total) {}

        @GenerateMapping
        interface PageMapping<T, D> extends MappingSpec<Page<T>, PageDto<D>> {
          @Deprecated
          ValidatedPrism<D, T> item();
        }
      }
      """;

  /** The implements clause names the class enclosing the spec, which no member reaches. */
  private static final String DEPRECATED_HOLDER =
      """
      @Deprecated
      final class DeprecatedHolder {
        record Person(String name, int age) {}

        record PersonDto(String name, int age) {}

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
      }
      """;

  /** The class's type-parameter clause restates the bound, which no member reaches. */
  private static final String DEPRECATED_BOUND =
      """
      final class DeprecatedBound {
        @Deprecated
        interface Tagged {}

        record Box<T extends Tagged>(T value) {}

        record BoxDto<T extends Tagged>(T value) {}

        @GenerateMapping
        interface BoxMapping<T extends Tagged> extends MappingSpec<Box<T>, BoxDto<T>> {}
      }
      """;

  private static final String LENS_VIEW =
      """
      final class LensView {
        record Person(String name, int age) {}

        @Deprecated
        record View(String name) {}

        @GenerateMapping
        interface ViewMapping extends MappingSpec<Person, View> {}
      }
      """;

  private static final String PATCH_VIEW =
      """
      final class PatchView {
        record Person(String name, int age) {}

        public static class ViewBean {
          private String name;

          public ViewBean() {}

          @Deprecated
          public String getName() { return name; }

          @Deprecated
          public void setName(String name) { this.name = name; }
        }

        @GenerateMapping
        interface ViewMapping extends MappingSpec<Person, ViewBean> {}
      }
      """;

  private static final String ONE_WAY =
      """
      final class OneWay {
        record Person(String name, int age) {}

        public static class ReadBean {
          @Deprecated
          public String getName() { return ""; }

          public int getAge() { return 0; }
        }

        public static class WriteBean {
          public WriteBean() {}

          @Deprecated
          public void setName(String name) {}

          public void setAge(int age) {}
        }

        @GenerateMapping
        interface ReadMapping extends MappingSpec<Person, ReadBean> {}

        @GenerateMapping
        interface WriteMapping extends MappingSpec<Person, WriteBean> {}
      }
      """;

  /** A read-only property maps as two halves, and parse reads its deprecated getter. */
  private static final String READ_ONLY_ID =
      """
      final class ReadOnlyId {
        record Pet(Long id, String name) {}

        public static class PetModel {
          private Long id;
          private String name;

          public PetModel() {}

          public PetModel(Long id, String name) {
            this.id = id;
            this.name = name;
          }

          @Deprecated
          public Long getId() { return id; }

          public String getName() { return name; }

          public void setName(String name) { this.name = name; }
        }

        @GenerateMapping
        interface PetMapping extends MappingSpec<Pet, PetModel> {
          @ReadOnly
          Long id();
        }
      }
      """;

  private static final String SPARSE_UPDATE =
      """
      final class SparseUpdate {
        record Person(String name, int age) {}

        public static class PersonPatch {
          private String name;
          private Integer age;

          public PersonPatch() {}

          @Deprecated
          public String getName() { return name; }

          @Deprecated
          public void setName(String name) { this.name = name; }

          public Integer getAge() { return age; }

          public void setAge(Integer age) { this.age = age; }
        }

        @GenerateMapping
        interface PersonUpdate extends UpdateSpec<Person, PersonPatch> {}
      }
      """;

  private static final String SPARSE_WIRE =
      """
      final class SparseWire {
        record Person(String name, int age) {}

        @Deprecated
        public static class PersonPatch {
          private String name;
          private Integer age;

          public PersonPatch() {}

          public String getName() { return name; }

          public void setName(String name) { this.name = name; }

          public Integer getAge() { return age; }

          public void setAge(Integer age) { this.age = age; }
        }

        @GenerateMapping
        interface PersonUpdate extends UpdateSpec<Person, PersonPatch> {}
      }
      """;

  /** protoc marks a field declared deprecated: its getter, and its builder's setter. */
  private static final String LEGACY_MEMBER =
      """
      final class LegacyMember {
        record Member(String id, String nickname) {}

        record Stamp(long seconds, int nanos) {}

        @GenerateMapping
        interface MemberMapping extends MappingSpec<Member, com.example.proto.LegacyProto.Account> {}

        @GenerateMapping
        interface MemberPatch extends UpdateSpec<Member, com.example.proto.LegacyProto.Account> {}

        @GenerateMapping
        interface StampMapping extends MappingSpec<Stamp, com.google.protobuf.Timestamp> {}
      }
      """;

  /** One class annotation merges a raw bound's rawtypes with both deprecation categories. */
  private static final String MIXED =
      """
      @SuppressWarnings("rawtypes")
      final class Mixed {
        @Deprecated
        record Code(String value) {}

        @Deprecated(forRemoval = true)
        record Legacy(String value) {}

        record Box<T extends List>(T value, Code code, Legacy legacy) {}

        record BoxDto<T extends List>(T value, Code code, Legacy legacy) {}

        @GenerateMapping
        interface BoxMapping<T extends List> extends MappingSpec<Box<T>, BoxDto<T>> {}
      }
      """;

  /** Deprecated members the Impls never call leave them without any suppression. */
  private static final String UNUSED =
      """
      final class Unused {
        record Person(String name, int age) {
          @Deprecated(forRemoval = true)
          Person(String name) {
            this(name, 0);
          }

          @Deprecated
          Person(String name, long age) {
            this(name, (int) age);
          }

          @Deprecated
          String legacy() { return name; }
        }

        record PersonDto(String name, int age) {
          @Deprecated
          static PersonDto of(String name) { return new PersonDto(name, 0); }
        }

        public static class PersonBean {
          private String name;
          private int age;

          public PersonBean() {}

          @Deprecated
          public PersonBean(String name) { this.name = name; }

          public String getName() { return name; }

          @Deprecated
          public String getName(java.util.Locale locale) { return name; }

          public void setName(String name) { this.name = name; }

          public int getAge() { return age; }

          public void setAge(int age) { this.age = age; }

          @Deprecated
          public void reset() { name = null; }
        }

        @GenerateMapping
        interface DtoMapping extends MappingSpec<Person, PersonDto> {}

        @GenerateMapping
        interface BeanMapping extends MappingSpec<Person, PersonBean> {
          @Deprecated
          static String helper() { return ""; }

          @Deprecated
          private String hidden() { return helper(); }
        }
      }
      """;

  private static final String MERGE_SOURCE =
      """
      final class MergeSource {
        @Deprecated
        record User(String name, String email) {}

        record Account(String iban, int balance) {}

        record Profile(String name, String email, String iban, int balance) {}

        @GenerateMerge
        interface ProfileMerge {
          Profile merge(User user, Account account);
        }
      }
      """;

  /** The Impl overrides the merge method, and javac counts that as a use. */
  private static final String MERGE_METHOD =
      """
      final class MergeMethod {
        record User(String name) {}

        record Account(String iban) {}

        record Profile(String name, String iban) {}

        @GenerateMerge
        interface ProfileMerge {
          @Deprecated(forRemoval = true)
          Profile merge(User user, Account account);
        }
      }
      """;

  /** build calls the canonical constructor, deprecated here in its compact form. */
  private static final String COMPACT_CONSTRUCTOR =
      """
      final class CompactConstructor {
        record Person(String name, int age) {}

        record PersonDto(String name, int age) {
          @Deprecated
          PersonDto {}
        }

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
      }
      """;

  /** build reads the domain record's deprecated accessor. */
  private static final String DOMAIN_COMPONENT =
      """
      final class DomainComponent {
        @SuppressWarnings("deprecation")
        record Person(@Deprecated String name, int age) {}

        record PersonDto(String name, int age) {}

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
      }
      """;

  private static final String ARRAY_COMPONENT =
      """
      final class ArrayComponent {
        @Deprecated
        record Code(String value) {}

        record Item(String name, Code[] codes) {}

        record ItemDto(String name, Code[] codes) {}

        @GenerateMapping
        interface ItemMapping extends MappingSpec<Item, ItemDto> {}
      }
      """;

  private static final String NO_ARGS_CONSTRUCTOR =
      """
      final class NoArgsConstructor {
        record Person(String name, int age) {}

        public static class PersonBean {
          private String name;
          private int age;

          @Deprecated
          public PersonBean() {}

          public String getName() { return name; }

          public void setName(String name) { this.name = name; }

          public int getAge() { return age; }

          public void setAge(int age) { this.age = age; }
        }

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonBean> {}
      }
      """;

  /** A record from another module, read from its class file, with a deprecated constructor. */
  private static final String LIBRARY =
      """
      package com.example.deprecation.lib;

      public final class LibRecords {
        private LibRecords() {}

        public record PersonDto(String name, int age) {
          @Deprecated
          public PersonDto {}
        }
      }
      """;

  private static final String CLASSPATH_WIRE =
      """
      final class ClasspathWire {
        record Person(String name, int age) {}

        @GenerateMapping
        interface PersonMapping
            extends MappingSpec<Person, com.example.deprecation.lib.LibRecords.PersonDto> {}
      }
      """;

  private static final String MERGE_SOURCE_ACCESSOR =
      """
      final class MergeSourceAccessor {
        @SuppressWarnings("deprecation")
        record User(@Deprecated String name) {}

        record Account(String iban) {}

        record Profile(String name, String iban) {}

        @GenerateMerge
        interface ProfileMerge {
          Profile merge(User user, Account account);
        }
      }
      """;

  private static final String MERGE_TARGET_CONSTRUCTOR =
      """
      final class MergeTargetConstructor {
        record User(String name) {}

        record Account(String iban) {}

        record Profile(String name, String iban) {
          @Deprecated
          Profile {}
        }

        @GenerateMerge
        interface ProfileMerge {
          Profile merge(User user, Account account);
        }
      }
      """;

  /** A source's deprecated component that no target component reads is never called. */
  private static final String MERGE_UNREAD =
      """
      final class MergeUnread {
        @SuppressWarnings("deprecation")
        record User(String name, @Deprecated String legacy) {}

        record Account(String iban) {}

        record Profile(String name, String iban) {}

        @GenerateMerge
        interface ProfileMerge {
          Profile merge(User user, Account account);
        }
      }
      """;

  /**
   * One Impl and the class-level suppression it carries, {@code ""} for none.
   *
   * @param description the display name
   * @param impl the Impl's simple name
   * @param suppression the annotation's value as written, such as {@code "deprecation"}
   */
  record Expected(String description, String impl, String suppression) {
    @Override
    public String toString() {
      return description;
    }
  }

  private static final String DEPRECATION = "\"deprecation\"";

  static Stream<Expected> rows() {
    return Stream.of(
        new Expected("a deprecated v1 wire record", "ApiPersonV1MappingImpl", DEPRECATION),
        new Expected(
            "a deprecated domain record", "DeprecatedDomainPersonMappingImpl", DEPRECATION),
        new Expected(
            "a wire record deprecated for removal", "RemovalWirePersonMappingImpl", "\"removal\""),
        new Expected(
            "a deprecated record component's accessor",
            "ComponentAccessorPersonMappingImpl",
            DEPRECATION),
        new Expected(
            "a bean's deprecated getter and setter", "BeanAccessorsPersonMappingImpl", DEPRECATION),
        new Expected(
            "a builder's deprecated factory and setter",
            "BuilderWirePersonMappingImpl",
            DEPRECATION),
        new Expected(
            "a deprecated component type, copied and inferred",
            "ComponentTypeItemMappingImpl",
            DEPRECATION),
        new Expected(
            "a deprecated sealed variant's own mapping", "PaymentsCardMappingImpl", DEPRECATION),
        new Expected(
            "its sibling variant's mapping, which names none", "PaymentsBankMappingImpl", ""),
        new Expected(
            "the sealed dispatch, whose switch names the variant",
            "PaymentsPaymentMappingImpl",
            DEPRECATION),
        new Expected("a deprecated default leaf", "DefaultLeafItemMappingImpl", DEPRECATION),
        new Expected(
            "a deprecated abstract leaf, element-mapped",
            "AbstractLeafPageMappingImpl",
            DEPRECATION),
        new Expected("a deprecated spec", "DeprecatedMemberMappingImpl", DEPRECATION),
        new Expected(
            "a deprecated class enclosing the spec",
            "DeprecatedHolderPersonMappingImpl",
            DEPRECATION),
        new Expected(
            "a deprecated type-parameter bound", "DeprecatedBoundBoxMappingImpl", DEPRECATION),
        new Expected("a lens over a deprecated projection", "LensViewViewMappingImpl", DEPRECATION),
        new Expected(
            "a patch over a bean projection's deprecated accessors",
            "PatchViewViewMappingImpl",
            DEPRECATION),
        new Expected("a parse-only bean's deprecated getter", "OneWayReadMappingImpl", DEPRECATION),
        new Expected(
            "a build-only bean's deprecated setter", "OneWayWriteMappingImpl", DEPRECATION),
        new Expected(
            "two halves, parse reading a deprecated getter",
            "ReadOnlyIdPetMappingImpl",
            DEPRECATION),
        new Expected(
            "a deprecated compact constructor on the wire",
            "CompactConstructorPersonMappingImpl",
            DEPRECATION),
        new Expected(
            "a domain record component's deprecated accessor",
            "DomainComponentPersonMappingImpl",
            DEPRECATION),
        new Expected("an array of a deprecated type", "ArrayComponentItemMappingImpl", DEPRECATION),
        new Expected(
            "a bean's deprecated no-argument constructor",
            "NoArgsConstructorPersonMappingImpl",
            DEPRECATION),
        new Expected(
            "a wire record from a class file, with a deprecated constructor",
            "ClasspathWirePersonMappingImpl",
            DEPRECATION),
        new Expected(
            "a sparse update's deprecated accessors", "SparseUpdatePersonUpdateImpl", DEPRECATION),
        new Expected(
            "a sparse update over a deprecated bean", "SparseWirePersonUpdateImpl", DEPRECATION),
        new Expected(
            "a protobuf-java field declared deprecated",
            "LegacyMemberMemberMappingImpl",
            DEPRECATION),
        new Expected(
            "a FieldMask update over that field", "LegacyMemberMemberPatchImpl", DEPRECATION),
        new Expected(
            "a protobuf-java message with no deprecated field", "LegacyMemberStampMappingImpl", ""),
        new Expected(
            "a raw bound beside both deprecation categories",
            "MixedBoxMappingImpl",
            "{\"deprecation\", \"rawtypes\", \"removal\"}"),
        new Expected(
            "a record's deprecated method and constructors, never called",
            "UnusedDtoMappingImpl",
            ""),
        new Expected(
            "a bean's deprecated overload and members, and the spec's static and private methods",
            "UnusedBeanMappingImpl",
            ""),
        new Expected(
            "a merge from a deprecated source", "MergeSourceProfileMergeImpl", DEPRECATION),
        new Expected(
            "a merge method deprecated for removal", "MergeMethodProfileMergeImpl", "\"removal\""),
        new Expected(
            "a merge source's deprecated accessor",
            "MergeSourceAccessorProfileMergeImpl",
            DEPRECATION),
        new Expected(
            "a merge target's deprecated constructor",
            "MergeTargetConstructorProfileMergeImpl",
            DEPRECATION),
        new Expected(
            "a merge source's deprecated accessor that no fill reads",
            "MergeUnreadProfileMergeImpl",
            ""));
  }

  @TempDir static Path classes;

  private static Compilation compilation;
  private static Compilation unsuppressed;

  @BeforeAll
  static void compile() throws IOException {
    Compilation library =
        javac()
            .compile(
                JavaFileObjects.forSourceString("com.example.deprecation.lib.LibRecords", LIBRARY));
    assertThat(library).succeeded();
    List<File> classpath = classpathWith(classDirectory(library, classes));
    List<JavaFileObject> fixtures =
        Stream.of(
                holder("Api", API),
                holder("DeprecatedDomain", DEPRECATED_DOMAIN),
                holder("RemovalWire", REMOVAL_WIRE),
                holder("ComponentAccessor", COMPONENT_ACCESSOR),
                holder("BeanAccessors", BEAN_ACCESSORS),
                holder("BuilderWire", BUILDER_WIRE),
                holder("ComponentType", COMPONENT_TYPE),
                holder("Payments", PAYMENTS),
                holder("DefaultLeaf", DEFAULT_LEAF),
                holder("AbstractLeaf", ABSTRACT_LEAF),
                holder("DeprecatedHolder", DEPRECATED_HOLDER),
                holder("DeprecatedBound", DEPRECATED_BOUND),
                holder("LensView", LENS_VIEW),
                holder("PatchView", PATCH_VIEW),
                holder("OneWay", ONE_WAY),
                holder("ReadOnlyId", READ_ONLY_ID),
                holder("SparseUpdate", SPARSE_UPDATE),
                holder("SparseWire", SPARSE_WIRE),
                holder("LegacyMember", LEGACY_MEMBER),
                holder("Mixed", MIXED),
                holder("Unused", UNUSED),
                holder("MergeSource", MERGE_SOURCE),
                holder("MergeMethod", MERGE_METHOD),
                holder("CompactConstructor", COMPACT_CONSTRUCTOR),
                holder("DomainComponent", DOMAIN_COMPONENT),
                holder("ArrayComponent", ARRAY_COMPONENT),
                holder("NoArgsConstructor", NO_ARGS_CONSTRUCTOR),
                holder("ClasspathWire", CLASSPATH_WIRE),
                holder("MergeSourceAccessor", MERGE_SOURCE_ACCESSOR),
                holder("MergeTargetConstructor", MERGE_TARGET_CONSTRUCTOR),
                holder("MergeUnread", MERGE_UNREAD),
                // A deprecated spec in a file of its own, as a v1 spec on its way out would be.
                holder("Member", "public record Member(String name) {}\n"),
                holder("MemberDto", "public record MemberDto(String name) {}\n"),
                holder(
                    "DeprecatedMemberMapping",
                    """
                    @GenerateMapping
                    @Deprecated
                    public interface DeprecatedMemberMapping extends MappingSpec<Member, MemberDto> {}
                    """))
            .toList();
    compilation =
        javac()
            .withProcessors(
                new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor())
            .withClasspath(classpath)
            .withOptions("-Xlint:all", "-Werror")
            .compile(fixtures);
    assertThat(compilation).succeeded();
    unsuppressed =
        javac()
            .withClasspath(classpath)
            .withOptions("-proc:none", "-Xlint:all", "-Xmaxwarns", "10000")
            .compile(
                Stream.concat(
                        fixtures.stream(),
                        compilation.generatedSourceFiles().stream()
                            .map(MappingDeprecationTest::withoutClassSuppression))
                    .toList());
  }

  private static JavaFileObject holder(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + IMPORTS + body);
  }

  /** The Impl as the annotation processor wrote it. */
  private static String generated(String impl) {
    return compilation.generatedSourceFiles().stream()
        .filter(file -> file.getName().endsWith("/" + PKG.replace('.', '/') + "/" + impl + ".java"))
        .findFirst()
        .map(MappingDeprecationTest::content)
        .orElseThrow(() -> new AssertionError("no generated " + impl));
  }

  private static String content(JavaFileObject file) {
    try {
      return file.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The annotation the class declaration carries after {@code @Generated}, as a group. */
  private static final Pattern CLASS_SUPPRESSION =
      Pattern.compile(
          "(?m)^(@Generated\\n)(?:@SuppressWarnings\\((.*)\\)\\n)?(public final class )");

  /** A deprecation category as a suppression names it. */
  private static final Pattern DEPRECATION_KEY = Pattern.compile("\"(?:deprecation|removal)\"");

  /** The category each of javac's deprecation warnings is suppressed by, keyed by its code. */
  private static final Map<String, String> DEPRECATION_CODES =
      Map.of(
          "compiler.warn.has.been.deprecated", "\"deprecation\"",
          "compiler.warn.has.been.deprecated.for.removal", "\"removal\"");

  /** A generated file again, under its own name, with its class-level suppression taken off. */
  private static JavaFileObject withoutClassSuppression(JavaFileObject file) {
    String path = file.toUri().getPath();
    String name =
        path.substring(path.indexOf("/SOURCE_OUTPUT/") + "/SOURCE_OUTPUT/".length())
            .replaceFirst("\\.java$", "")
            .replace('/', '.');
    return JavaFileObjects.forSourceString(
        name, CLASS_SUPPRESSION.matcher(content(file)).replaceFirst("$1$3"));
  }

  @Test
  @DisplayName("every fixture compiles under -Xlint:all -Werror without a warning")
  void compilesWithoutWarnings() {
    assertThat(compilation).succeededWithoutWarnings();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("the class carries the expected suppression, and no member suppresses deprecation")
  void classCarriesTheSuppression(Expected row) {
    String source = generated(row.impl());
    Matcher header = CLASS_SUPPRESSION.matcher(source);
    assertThat(header.find()).as("the class declaration of %s", row.impl()).isTrue();
    assertThat(Optional.ofNullable(header.group(2)).orElse("")).isEqualTo(row.suppression());
    assertThat(DEPRECATION_KEY.matcher(source).results().count())
        .as("deprecation is suppressed on the class alone")
        .isEqualTo(DEPRECATION_KEY.matcher(row.suppression()).results().count());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("with the class suppression taken off, the Impl warns in exactly its categories")
  void withoutItTheImplWarns(Expected row) {
    Set<String> drawn =
        unsuppressed.warnings().stream()
            .filter(warning -> warning.getSource() != null)
            .filter(warning -> warning.getSource().getName().endsWith("/" + row.impl() + ".java"))
            .map(warning -> DEPRECATION_CODES.get(warning.getCode()))
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    assertThat(drawn)
        .isEqualTo(
            DEPRECATION_KEY
                .matcher(row.suppression())
                .results()
                .map(MatchResult::group)
                .collect(Collectors.toUnmodifiableSet()));
  }

  @Test
  @DisplayName("the unsuppressed recompile itself builds, so its warnings are the Impls' own")
  void unsuppressedRecompileBuilds() {
    assertThat(unsuppressed).succeeded();
  }
}
