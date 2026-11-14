// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A same-typed component is copied as it is, a {@code null} included, so a domain component
 * declared {@code @Nullable} is refused where {@code build} copies it into a site declared
 * non-null: a record component, a setter's or a builder setter's parameter, declared so by a
 * recognised annotation or by JSpecify's {@code @NullMarked} scope. A plain merge copies its
 * sources the same way and is refused alike. A site or a component that says nothing, a site that
 * may take the null, a primitive, a leaf, a bean that is never written and a fallible merge, whose
 * guard reports a null, all compile as before.
 *
 * <p>Each compilation carries several specs, to keep the javac runs few.
 */
@DisplayName("A nullable component copied into a site declared non-null")
class NullableCopyIntoNonNullTest {

  @TempDir Path tmp;

  private static final JavaFileObject MARKED_PACKAGE =
      JavaFileObjects.forSourceString(
          "com.marked.package-info",
          """
          @NullMarked
          package com.marked;

          import org.jspecify.annotations.NullMarked;
          """);

  /** A declaration annotation named {@code Nullable}, which javac copies to field and accessor. */
  private static final JavaFileObject LEGACY_NULLABLE =
      JavaFileObjects.forSourceString(
          "com.legacy.Nullable",
          """
          package com.legacy;

          import java.lang.annotation.ElementType;
          import java.lang.annotation.Target;

          @Target({ElementType.FIELD, ElementType.METHOD})
          public @interface Nullable {}
          """);

  private static Compilation compile(JavaFileObject fixtures) {
    return javac()
        // The companion processor claims the vocabulary annotations the others only read; no
        // processor claims JSpecify's, which is all the processing lint would report.
        .withProcessors(
            new MappingProcessor(), new MergeProcessor(), new CompanionAnnotationProcessor())
        .withOptions("-Xlint:all,-processing", "-Werror")
        .compile(MARKED_PACKAGE, LEGACY_NULLABLE, fixtures);
  }

  @Test
  @DisplayName(
      "a record component, a setter, a builder setter, a flattened member, a container and a plain"
          + " merge's target are each refused, naming the site, why and the two fixes")
  void everySiteIsRefused() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceString(
                "com.marked.Refused",
                """
                package com.marked;

                import java.util.List;
                import org.higherkindedj.optics.annotations.Flatten;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.GenerateMerge;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.jspecify.annotations.NonNull;
                import org.jspecify.annotations.NullUnmarked;
                import org.jspecify.annotations.Nullable;

                public final class Refused {
                  private Refused() {}

                  public record Account(@Nullable String id, String name) {}

                  public record AccountView(String id, String name) {}

                  @GenerateMapping
                  public interface AccountViewMapping extends MappingSpec<Account, AccountView> {}

                  @NullUnmarked
                  public record LooseView(@NonNull String id, String name) {}

                  @GenerateMapping
                  public interface LooseViewMapping extends MappingSpec<Account, LooseView> {}

                  public static class AccountBean {
                    private @Nullable String id;
                    private @Nullable String name;

                    public @Nullable String getId() { return id; }
                    public void setId(String id) { this.id = id; }
                    public @Nullable String getName() { return name; }
                    public void setName(@Nullable String name) { this.name = name; }
                  }

                  @GenerateMapping
                  public interface AccountBeanMapping extends MappingSpec<Account, AccountBean> {}

                  // A property no field of its own name backs.
                  public static class AccountKept {
                    private @Nullable String kept;
                    private @Nullable String name;

                    public @Nullable String getId() { return kept; }
                    public void setId(String id) { this.kept = id; }
                    public @Nullable String getName() { return name; }
                    public void setName(@Nullable String name) { this.name = name; }
                  }

                  @GenerateMapping
                  public interface AccountKeptMapping extends MappingSpec<Account, AccountKept> {}

                  public static final class AccountCard {
                    private final @Nullable String id;
                    private final @Nullable String name;

                    private AccountCard(@Nullable String id, @Nullable String name) {
                      this.id = id;
                      this.name = name;
                    }

                    public @Nullable String getId() { return id; }
                    public @Nullable String getName() { return name; }
                    public static Builder builder() { return new Builder(); }

                    public static final class Builder {
                      private @Nullable String id;
                      private @Nullable String name;

                      public Builder id(String id) { this.id = id; return this; }
                      public Builder name(@Nullable String name) { this.name = name; return this; }
                      public AccountCard build() { return new AccountCard(id, name); }
                    }
                  }

                  @GenerateMapping
                  public interface AccountCardMapping extends MappingSpec<Account, AccountCard> {}

                  public record Tagged(@Nullable List<String> tags) {}

                  public record TaggedDto(List<String> tags) {}

                  @GenerateMapping
                  public interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {}

                  public record Note(@Nullable String text) {}

                  public record Entry(String title, Note note) {}

                  public record EntryDto(String title, String text) {}

                  @GenerateMapping
                  public interface EntryMapping extends MappingSpec<Entry, EntryDto> {
                    @Flatten
                    Note note();
                  }

                  // A declaration annotation, which lands on the field and the accessor.
                  public record Legacy(@com.legacy.Nullable String code) {}

                  public record LegacyDto(String code) {}

                  @GenerateMapping
                  public interface LegacyMapping extends MappingSpec<Legacy, LegacyDto> {}

                  public record Names(List<String> names) {}

                  public record Labels(@Nullable String note) {}

                  public record Merged(List<String> names, String note) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Merged merge(Names names, Labels labels);
                  }
                }
                """));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Account.id' is declared @Nullable, and build copies it to the record"
                + " component 'AccountView.id', which is declared non-null. A same-typed component"
                + " is copied as it is, a null included, and 'AccountView.id' carries no @Nullable"
                + " inside @NullMarked package 'com.marked', so build would write a null its"
                + " declaration rules out, and either throw or leave a null its type says it cannot"
                + " hold. As 'Account.id' may be absent, mark 'AccountView.id' @Nullable; or"
                + " declare 'Account.id' non-null, if build never meets it null.");
    assertThat(compilation)
        .hadErrorContaining(
            "'LooseView.id' is annotated @NonNull, so build would write a null its declaration"
                + " rules out, and either throw or leave a null its type says it cannot hold. As"
                + " 'Account.id' may be absent, replace @NonNull on 'LooseView.id' with"
                + " @Nullable;");
    assertThat(compilation)
        .hadErrorContaining(
            "build copies it to the bean property 'id', whose setter setId(String) is declared"
                + " non-null. A same-typed component is copied as it is, a null included, and the"
                + " parameter of setId(String) carries no @Nullable");
    assertThat(compilation)
        .hadErrorContaining(
            "build copies it to the bean property 'id', whose builder setter id(String) is"
                + " declared non-null.");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Tagged.tags' is declared @Nullable, and build copies it to the record"
                + " component 'TaggedDto.tags'");
    // A flattened member is named on the record that declares it.
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Note.text' is declared @Nullable, and build copies it to the record"
                + " component 'EntryDto.text'");
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Legacy.code' is declared @Nullable, and build copies it to the record"
                + " component 'LegacyDto.code'");
    assertThat(compilation)
        .hadErrorContaining(
            "source component 'Labels.note' is declared @Nullable, and the merge copies it to the"
                + " record component 'Merged.note', which is declared non-null. A plain merge"
                + " copies each source value as it is, a null included, and 'Merged.note' carries"
                + " no @Nullable inside @NullMarked package 'com.marked', so the merge would write"
                + " a null its declaration rules out, and either throw or leave a null its type"
                + " says it cannot hold. As 'Labels.note' may be absent, mark 'Merged.note'"
                + " @Nullable; or declare 'Labels.note' non-null, if the merge never meets it null;"
                + " or add 'default ValidatedPrism<java.lang.String,"
                + " java.lang.String> note()' to the merge and return"
                + " Validated<NonEmptyList<FieldError>, Merged>, so the merge, now fallible,"
                + " reports a null as 'note: must not be null'.");
    assertThat(compilation)
        .hadErrorContaining(
            "As 'Account.id' may be absent, mark the parameter of setId(String) @Nullable; or"
                + " declare 'Account.id' non-null");
    Assertions.assertThat(compilation.errors()).hasSize(9);
  }

  @Test
  @DisplayName(
      "a record read from a class file takes the null its canonical constructor's parameter"
          + " accepts")
  void aCompiledConstructorThatTakesANullIsAccepted() throws IOException {
    Compilation upstream =
        javac()
            .compile(
                JavaFileObjects.forSourceString(
                    "com.upstream.Defaulted",
                    """
                    package com.upstream;

                    import org.jspecify.annotations.NullMarked;
                    import org.jspecify.annotations.Nullable;

                    @NullMarked
                    public record Defaulted(String id, String name) {
                      public Defaulted(@Nullable String id, String name) {
                        this.id = id == null ? "" : id;
                        this.name = name;
                      }
                    }
                    """));
    assertThat(upstream).succeeded();
    Path classes = classDirectory(upstream, tmp.resolve("upstream"));

    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor())
            .withClasspath(classpathWith(classes))
            .withOptions("-Xlint:all,-processing", "-Werror")
            .compile(
                MARKED_PACKAGE,
                JavaFileObjects.forSourceString(
                    "com.marked.Downstream",
                    """
                    package com.marked;

                    import com.upstream.Defaulted;
                    import org.higherkindedj.optics.annotations.GenerateMapping;
                    import org.higherkindedj.optics.annotations.MappingSpec;
                    import org.jspecify.annotations.Nullable;

                    public final class Downstream {
                      private Downstream() {}

                      public record Account(@Nullable String id, String name) {}

                      @GenerateMapping
                      public interface DefaultedMapping extends MappingSpec<Account, Defaulted> {}
                    }
                    """));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  @DisplayName("a writer Lombok generates is fixed on the field it is written from")
  void aLombokWriterIsFixedOnItsField() {
    Compilation compilation =
        javac()
            .withProcessors(LombokInteropTest.lombok(), new MappingProcessor())
            .compile(
                MARKED_PACKAGE,
                JavaFileObjects.forSourceString(
                    "com.marked.Lomboked",
                    """
                    package com.marked;

                    import org.higherkindedj.optics.annotations.GenerateMapping;
                    import org.higherkindedj.optics.annotations.MappingSpec;
                    import org.jspecify.annotations.Nullable;

                    public final class Lomboked {
                      private Lomboked() {}

                      public record Account(@Nullable String id, String name) {}

                      @lombok.Data
                      public static class AccountData {
                        private String id;
                        private @Nullable String name;
                      }

                      @GenerateMapping
                      public interface AccountDataMapping extends MappingSpec<Account, AccountData> {}

                      public static class AccountFields {
                        @lombok.Getter @lombok.Setter private String id;
                        @lombok.Getter @lombok.Setter private @Nullable String name;
                      }

                      @GenerateMapping
                      public interface AccountFieldsMapping
                          extends MappingSpec<Account, AccountFields> {}

                      @lombok.Builder
                      @lombok.Getter
                      public static final class AccountCard {
                        private final String id;
                        private final @Nullable String name;
                      }

                      @GenerateMapping
                      public interface AccountCardMapping extends MappingSpec<Account, AccountCard> {}

                      // A @SuperBuilder's builder writes the field its parent declares.
                      @lombok.experimental.SuperBuilder
                      @lombok.Getter
                      public static class BaseCard {
                        private final String id;
                      }

                      @lombok.experimental.SuperBuilder
                      @lombok.Getter
                      public static class FullCard extends BaseCard {
                        private final @Nullable String name;
                      }

                      @GenerateMapping
                      public interface FullCardMapping extends MappingSpec<Account, FullCard> {}

                      // Lombok writes no setter beside one written by hand.
                      @lombok.Data
                      public static class AccountHand {
                        private @Nullable String id;
                        private @Nullable String name;

                        public void setId(String id) {
                          this.id = id;
                        }
                      }

                      @GenerateMapping
                      public interface AccountHandMapping extends MappingSpec<Account, AccountHand> {}
                    }
                    """));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "whose setter setId(String), which Lombok writes from the field 'AccountData.id', is"
                + " declared non-null. A same-typed component is copied as it is, a null included,"
                + " and the field 'AccountData.id' carries no @Nullable inside @NullMarked package"
                + " 'com.marked'");
    assertThat(compilation)
        .hadErrorContaining(
            "As 'Account.id' may be absent, mark the field 'AccountFields.id' @Nullable;");
    assertThat(compilation)
        .hadErrorContaining(
            "whose builder setter id(String), which Lombok writes from the field"
                + " 'AccountCard.id', is declared non-null.");
    assertThat(compilation)
        .hadErrorContaining(
            "As 'Account.id' may be absent, mark the parameter of setId(String) @Nullable;");
    assertThat(compilation)
        .hadErrorContaining(
            "which Lombok writes from the field 'BaseCard.id', is declared non-null.");
    Assertions.assertThat(compilation.errors()).hasSize(5);
  }

  @Test
  @DisplayName(
      "a site that may take the null or says nothing, a primitive, a leaf, a bean never written and"
          + " a fallible merge all compile as before")
  void everyOtherPairCompiles() {
    Compilation compilation =
        compile(
            JavaFileObjects.forSourceString(
                "com.marked.Accepted",
                """
                package com.marked;

                import java.util.List;
                import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
                import org.higherkindedj.hkt.validated.FieldError;
                import org.higherkindedj.hkt.validated.Validated;
                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.GenerateMerge;
                import org.higherkindedj.optics.annotations.MappingSpec;
                import org.higherkindedj.optics.validated.ValidatedPrism;
                import org.jspecify.annotations.NullUnmarked;
                import org.jspecify.annotations.Nullable;

                public final class Accepted {
                  private Accepted() {}

                  public record Account(@Nullable String id, String name) {}

                  public record AccountDto(@Nullable String id, String name) {}

                  @GenerateMapping
                  public interface AccountDtoMapping extends MappingSpec<Account, AccountDto> {}

                  @NullUnmarked
                  public record LooseDto(String id, String name) {}

                  @GenerateMapping
                  public interface LooseDtoMapping extends MappingSpec<Account, LooseDto> {}

                  // A primitive never holds a null, whatever it is annotated.
                  public record Counted(@Nullable int count) {}

                  public record CountedDto(int count) {}

                  @GenerateMapping
                  public interface CountedMapping extends MappingSpec<Counted, CountedDto> {}

                  public record Plain(String id, List<String> tags) {}

                  public record PlainDto(String id, List<String> tags) {}

                  @GenerateMapping
                  public interface PlainMapping extends MappingSpec<Plain, PlainDto> {}

                  public record Coded(@Nullable String code) {}

                  public record CodedDto(String code) {}

                  // A leaf converts the value instead: it is not a copy, and its build is the
                  // leaf's own to answer for.
                  @GenerateMapping
                  public interface CodedMapping extends MappingSpec<Coded, CodedDto> {
                    default ValidatedPrism<String, String> code() {
                      return ValidatedPrism.of(Validated::validNel, code -> code);
                    }
                  }

                  // The canonical constructor build calls takes a null, whatever the component says.
                  public record Defaulted(String id, String name) {
                    public Defaulted(@Nullable String id, String name) {
                      this.id = id == null ? "" : id;
                      this.name = name;
                    }
                  }

                  @GenerateMapping
                  public interface DefaultedMapping extends MappingSpec<Account, Defaulted> {}

                  // Getters only: parsed, and never built.
                  public static class AccountView {
                    public String getId() { return "id"; }
                    public String getName() { return "name"; }
                  }

                  @GenerateMapping
                  public interface AccountViewMapping extends MappingSpec<Account, AccountView> {}

                  public record Names(List<String> names, String code) {}

                  public record Labels(@Nullable String note) {}

                  public record Merged(List<String> names, String code, @Nullable String note) {}

                  @GenerateMerge
                  public interface MergedAssembly {
                    Merged merge(Names names, Labels labels);
                  }

                  public record Checked(List<String> names, String code, String note) {}

                  // A fallible merge guards every read, so a null note is a failure, not a copy.
                  @GenerateMerge
                  public interface CheckedAssembly {
                    Validated<NonEmptyList<FieldError>, Checked> merge(Names names, Labels labels);

                    default ValidatedPrism<String, String> code() {
                      return ValidatedPrism.of(Validated::validNel, code -> code);
                    }
                  }
                }
                """));

    assertThat(compilation).succeededWithoutWarnings();
  }
}
