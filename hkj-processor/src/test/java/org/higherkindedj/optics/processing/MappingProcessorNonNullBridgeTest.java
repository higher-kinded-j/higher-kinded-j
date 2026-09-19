// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static java.util.stream.Collectors.joining;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An {@code Optional} bridge writes an empty value as {@code null}, so a write site declared
 * non-null is refused: a record component, or a setter's or builder setter's parameter, declared so
 * by a recognised annotation or by JSpecify's {@code @NullMarked} scope. A site that may take null,
 * or says nothing either way, still bridges. The refusals that offer a bridge as their fix ask for
 * the {@code @Nullable} it needs, so following them never leads here.
 *
 * <p>Each compilation carries several specs, each asserted in its own file, to keep the javac runs
 * few.
 */
@DisplayName("MappingProcessor - an Optional bridge onto a write site declared non-null")
class MappingProcessorNonNullBridgeTest {

  @TempDir Path tmp;

  private static final JavaFileObject MARKED_PACKAGE =
      JavaFileObjects.forSourceString(
          "com.marked.package-info",
          """
          @NullMarked
          package com.marked;

          import org.jspecify.annotations.NullMarked;
          """);

  private static final JavaFileObject PROFILE = profile("com.marked");

  private static final JavaFileObject EMAIL =
      JavaFileObjects.forSourceString(
          "com.marked.EmailAddress",
          """
          package com.marked;

          public record EmailAddress(String value) {}
          """);

  private static JavaFileObject profile(String packageName) {
    return JavaFileObjects.forSourceString(
        packageName + ".Profile",
        """
        package %s;

        import java.util.Optional;

        public record Profile(String name, Optional<String> nickname) {}
        """
            .formatted(packageName));
  }

  /** A two-way bean whose nickname setter parameter is declared as {@code parameter} reads. */
  private static JavaFileObject bean(String packageName, String name, String parameter) {
    return JavaFileObjects.forSourceString(
        packageName + "." + name,
        """
        package %1$s;

        import org.jspecify.annotations.Nullable;

        public class %2$s {
          private @Nullable String name;
          private @Nullable String nickname;
          public @Nullable String getName() { return name; }
          public void setName(@Nullable String v) { name = v; }
          public @Nullable String getNickname() { return nickname; }
          public void setNickname(%3$s v) { nickname = v; }
        }
        """
            .formatted(packageName, name, parameter));
  }

  /** A record wire whose nickname component is declared as {@code component} reads. */
  private static JavaFileObject record(String packageName, String name, String component) {
    return JavaFileObjects.forSourceString(
        packageName + "." + name,
        """
        package %1$s;

        public record %2$s(@org.jspecify.annotations.Nullable String name, %3$s nickname) {}
        """
            .formatted(packageName, name, component));
  }

  private static JavaFileObject spec(String packageName, String name, String domain, String wire) {
    return spec(packageName, name, domain, wire, "");
  }

  private static JavaFileObject spec(
      String packageName, String name, String domain, String wire, String body) {
    return JavaFileObjects.forSourceString(
        packageName + "." + name,
        """
        package %1$s;

        import java.util.Optional;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.OptionalBridge;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        @GenerateMapping
        public interface %2$s extends MappingSpec<%3$s, %4$s> {
        %5$s}
        """
            .formatted(packageName, name, domain, wire, body));
  }

  private static final String NICKNAME_MARKER =
      """
        @OptionalBridge
        Optional<String> nickname();
      """;

  private static Compilation compile(List<JavaFileObject> sources) {
    return javac()
        .withProcessors(new MappingProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(sources);
  }

  @Test
  @DisplayName(
      "a setter, a builder setter and a record component declared non-null by their @NullMarked"
          + " scope are each refused, naming the site, the scope and the fixes")
  void nullMarkedWriteSitesAreRefused() {
    JavaFileObject plainSetter = bean("com.marked", "ProfileBean", "String");
    JavaFileObject builder =
        JavaFileObjects.forSourceString(
            "com.marked.BuiltProfile",
            """
            package com.marked;

            import org.jspecify.annotations.Nullable;

            public final class BuiltProfile {
              private final @Nullable String name;
              private final @Nullable String nickname;
              private BuiltProfile(@Nullable String name, @Nullable String nickname) {
                this.name = name;
                this.nickname = nickname;
              }
              public @Nullable String getName() { return name; }
              public @Nullable String getNickname() { return nickname; }
              public static Builder builder() { return new Builder(); }
              public static final class Builder {
                private @Nullable String name;
                private @Nullable String nickname;
                public Builder name(@Nullable String name) { this.name = name; return this; }
                public Builder nickname(String nickname) { this.nickname = nickname; return this; }
                public BuiltProfile build() { return new BuiltProfile(name, nickname); }
              }
            }
            """);
    // Setters and no getters: built, never parsed, and still refused.
    JavaFileObject buildOnly =
        JavaFileObjects.forSourceString(
            "com.marked.ProfileCommand",
            """
            package com.marked;

            import org.jspecify.annotations.Nullable;

            public class ProfileCommand {
              private @Nullable String name;
              private @Nullable String nickname;
              public void setName(@Nullable String v) { name = v; }
              public void setNickname(String v) { nickname = v; }
            }
            """);
    JavaFileObject shapes =
        JavaFileObjects.forSourceString(
            "com.marked.Shapes",
            """
            package com.marked;

            import java.util.Optional;
            import org.jspecify.annotations.Nullable;

            public final class Shapes {
              public record ProfileDto(@Nullable String name, String nickname) {}

              // A projection: the wire leaves the name out.
              public record NickCard(String nickname) {}

              // JSpecify: @Nullable String[] makes the elements nullable, and leaves the array
              // non-null.
              public record Domain(String name, Optional<String[]> tags) {}

              public record TagsDto(String name, @Nullable String[] tags) {}

              // A plain type variable in a marked scope has the non-null bound Object.
              public record Holder(Optional<String> value) {}

              public record BoxDto<T>(T value) {}

              public record Member(String name, Optional<EmailAddress> altEmail) {}

              public record MemberDto(String name, String altEmail) {}
            }
            """);
    // A generic base bean's setter, inherited by the bean the spec names.
    JavaFileObject inherited =
        JavaFileObjects.forSourceString(
            "com.marked.Bases",
            """
            package com.marked;

            import org.jspecify.annotations.Nullable;

            public final class Bases {
              public static class BaseDto<T> {
                private @Nullable T value;
                public @Nullable T getValue() { return value; }
                public void setValue(T v) { value = v; }
              }

              public static class StringDto extends BaseDto<String> {}
            }
            """);
    JavaFileObject beanSpec = spec("com.marked", "ProfileBeanMapping", "Profile", "ProfileBean");
    JavaFileObject builderSpec =
        spec("com.marked", "BuiltProfileMapping", "Profile", "BuiltProfile");
    JavaFileObject buildOnlySpec =
        spec("com.marked", "ProfileCommandMapping", "Profile", "ProfileCommand");
    JavaFileObject recordSpec =
        spec("com.marked", "ProfileDtoMapping", "Profile", "Shapes.ProfileDto", NICKNAME_MARKER);
    JavaFileObject projectionSpec =
        spec("com.marked", "NickCardMapping", "Profile", "Shapes.NickCard", NICKNAME_MARKER);
    JavaFileObject arraySpec =
        spec(
            "com.marked",
            "TagsMapping",
            "Shapes.Domain",
            "Shapes.TagsDto",
            """
              @OptionalBridge
              Optional<String[]> tags();
            """);
    JavaFileObject typeVariableSpec =
        spec(
            "com.marked",
            "BoxMapping",
            "Shapes.Holder",
            "Shapes.BoxDto<String>",
            """
              @OptionalBridge
              Optional<String> value();
            """);
    JavaFileObject inheritedSpec =
        spec("com.marked", "StringMapping", "Shapes.Holder", "Bases.StringDto");
    // The bridge on the component's own leaf, which converts the element.
    JavaFileObject leafSpec =
        spec(
            "com.marked",
            "MemberMapping",
            "Shapes.Member",
            "Shapes.MemberDto",
            """
              @OptionalBridge
              default ValidatedPrism<String, EmailAddress> altEmail() {
                return ValidatedPrism.of(
                    raw -> org.higherkindedj.hkt.validated.Validated.validNel(new EmailAddress(raw)),
                    EmailAddress::value);
              }
            """);

    Compilation compilation =
        compile(
            List.of(
                MARKED_PACKAGE,
                PROFILE,
                EMAIL,
                plainSetter,
                builder,
                buildOnly,
                shapes,
                inherited,
                beanSpec,
                builderSpec,
                buildOnlySpec,
                recordSpec,
                projectionSpec,
                arraySpec,
                typeVariableSpec,
                inheritedSpec,
                leafSpec));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Profile.nickname' is Optional<String>, bridged to the bean property"
                + " 'nickname', whose setter setNickname(String) is declared non-null. The bridge"
                + " writes an empty Optional as null, and the parameter of setNickname(String)"
                + " carries no @Nullable inside @NullMarked package 'com.marked', so build would"
                + " write the null its declaration rules out, and either throw or leave a null its"
                + " type says it cannot hold. Mark the parameter of setNickname(String) @Nullable,"
                + " since it carries absence; or declare 'Profile.nickname' as String, dropping the"
                + " Optional; or add 'default ValidatedPrism<java.lang.String,"
                + " java.util.Optional<java.lang.String>> nickname()' to the spec, a leaf over the"
                + " whole Optional that encodes absence the way the wire does.")
        .inFile(beanSpec);
    assertThat(compilation)
        .hadErrorContaining("whose builder setter nickname(String) is declared non-null")
        .inFile(builderSpec);
    assertThat(compilation)
        .hadErrorContaining("whose setter setNickname(String) is declared non-null")
        .inFile(buildOnlySpec);
    // The record opts in with a marker, so dropping the Optional drops it too, and the leaf
    // takes its place.
    assertThat(compilation)
        .hadErrorContaining(
            "bridged to the record component 'ProfileDto.nickname', which is declared non-null."
                + " The bridge writes an empty Optional as null, and 'ProfileDto.nickname' carries"
                + " no @Nullable inside @NullMarked package 'com.marked'")
        .inFile(recordSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "Mark 'ProfileDto.nickname' @Nullable, since it carries absence; or declare"
                + " 'Profile.nickname' as String, dropping the Optional and its @OptionalBridge"
                + " marker; or add 'default ValidatedPrism<java.lang.String,"
                + " java.util.Optional<java.lang.String>> nickname()' to the spec in place of the"
                + " marker")
        .inFile(recordSpec);
    assertThat(compilation)
        .hadErrorContaining("bridged to the record component 'NickCard.nickname'")
        .inFile(projectionSpec);
    // The @Nullable already written marks the elements, so the fix says where it goes instead.
    assertThat(compilation)
        .hadErrorContaining(
            "Mark 'TagsDto.tags' @Nullable before its brackets, 'String @Nullable []', since a"
                + " @Nullable before the type marks the elements")
        .inFile(arraySpec);
    assertThat(compilation)
        .hadErrorContaining("bridged to the record component 'BoxDto.value', which is declared")
        .inFile(typeVariableSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "whose setter setValue(T) (declared on 'BaseDto') is declared non-null. The bridge"
                + " writes an empty Optional as null, and the parameter of setValue(T) (declared"
                + " on 'BaseDto') carries no @Nullable")
        .inFile(inheritedSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "dropping the Optional and the @OptionalBridge on its leaf; or add 'default"
                + " ValidatedPrism<java.lang.String,"
                + " java.util.Optional<com.marked.EmailAddress>> altEmail()' to the spec in place"
                + " of that leaf")
        .inFile(leafSpec);
    Assertions.assertThat(compilation.errors()).hasSize(9);
  }

  @Test
  @DisplayName(
      "a @NullMarked class or method scopes the rule as its package does, and an explicit non-null"
          + " annotation needs no scope")
  void narrowerScopesAndExplicitAnnotationsAreRefused() {
    // A method-level scope, placed on the setter itself.
    JavaFileObject methodScoped =
        JavaFileObjects.forSourceString(
            "com.plain.ScopedBean",
            """
            package com.plain;

            import org.jspecify.annotations.NullMarked;
            import org.jspecify.annotations.Nullable;

            public class ScopedBean {
              private @Nullable String name;
              private @Nullable String nickname;
              public @Nullable String getName() { return name; }
              public void setName(@Nullable String v) { name = v; }
              public @Nullable String getNickname() { return nickname; }
              @NullMarked
              public void setNickname(String v) { nickname = v; }
            }
            """);
    JavaFileObject classScoped =
        JavaFileObjects.forSourceString(
            "com.plain.Cards",
            """
            package com.plain;

            import org.jspecify.annotations.NullMarked;

            public final class Cards {
              @NullMarked
              public record CardDto(String name, String nickname) {}
            }
            """);
    JavaFileObject annotated =
        bean("com.plain", "AnnotatedBean", "@org.jspecify.annotations.NonNull String");
    JavaFileObject methodSpec = spec("com.plain", "ScopedMapping", "Profile", "ScopedBean");
    JavaFileObject classSpec =
        spec("com.plain", "CardMapping", "Profile", "Cards.CardDto", NICKNAME_MARKER);
    JavaFileObject annotatedSpec =
        spec("com.plain", "AnnotatedMapping", "Profile", "AnnotatedBean");

    Compilation compilation =
        compile(
            List.of(
                profile("com.plain"),
                methodScoped,
                classScoped,
                annotated,
                methodSpec,
                classSpec,
                annotatedSpec));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("carries no @Nullable inside @NullMarked method 'setNickname'")
        .inFile(methodSpec);
    assertThat(compilation)
        .hadErrorContaining("carries no @Nullable inside @NullMarked record 'Cards.CardDto'")
        .inFile(classSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "the parameter of setNickname(String) is annotated @NonNull, so build would write")
        .inFile(annotatedSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "Replace @NonNull on the parameter of setNickname(String) with @Nullable, since it"
                + " carries absence")
        .inFile(annotatedSpec);
    Assertions.assertThat(compilation.errors()).hasSize(3);
  }

  /**
   * A recognised non-null annotation, the {@code @Target} its published artefact carries, the
   * elements the case needs, and the use written on the site.
   */
  record Vendor(String annotation, List<String> targets, String elements, String use) {}

  /**
   * Each recognised name, with its real target: a stand-in with a wider target than the real one
   * proves nothing about where the real one lands. JSpecify's is on the test classpath and is used
   * as published. JSR-305's is written with an explicit {@code when = ALWAYS}, and JetBrains' with
   * a {@code value}, so an element on a non-null annotation is read as well as its absence.
   */
  private static final List<Vendor> VENDORS =
      List.of(
          new Vendor("org.jspecify.annotations.NonNull", List.of("TYPE_USE"), "", ""),
          new Vendor(
              "javax.annotation.Nonnull",
              List.of(),
              "javax.annotation.meta.When when() default javax.annotation.meta.When.ALWAYS;",
              "(when = javax.annotation.meta.When.ALWAYS)"),
          new Vendor("jakarta.annotation.Nonnull", List.of(), "", ""),
          new Vendor(
              "org.jetbrains.annotations.NotNull",
              List.of("METHOD", "FIELD", "PARAMETER", "LOCAL_VARIABLE", "TYPE_USE"),
              "String value() default \"\";",
              "(\"never absent\")"),
          new Vendor(
              "androidx.annotation.NonNull",
              List.of(
                  "METHOD", "PARAMETER", "FIELD", "LOCAL_VARIABLE", "ANNOTATION_TYPE", "PACKAGE"),
              "",
              ""),
          new Vendor(
              "edu.umd.cs.findbugs.annotations.NonNull",
              List.of("FIELD", "METHOD", "PARAMETER", "LOCAL_VARIABLE"),
              "",
              ""),
          new Vendor(
              "lombok.NonNull",
              List.of("FIELD", "METHOD", "PARAMETER", "LOCAL_VARIABLE", "TYPE_USE"),
              "",
              ""),
          new Vendor(
              "org.checkerframework.checker.nullness.qual.NonNull",
              List.of("TYPE_USE", "TYPE_PARAMETER"),
              "",
              ""));

  /** Declares the vendor annotation with the {@code @Target} its published artefact carries. */
  private static JavaFileObject standIn(Vendor vendor) {
    int lastDot = vendor.annotation().lastIndexOf('.');
    String target =
        vendor.targets().isEmpty()
            ? ""
            : vendor.targets().stream()
                .map("ElementType."::concat)
                .collect(joining(", ", "@Target({", "})"));
    return JavaFileObjects.forSourceString(
        vendor.annotation(),
        """
        package %s;
        import java.lang.annotation.*;
        %s
        @Retention(RetentionPolicy.CLASS)
        public @interface %s { %s }
        """
            .formatted(
                vendor.annotation().substring(0, lastDot),
                target,
                vendor.annotation().substring(lastDot + 1),
                vendor.elements()));
  }

  private static final JavaFileObject WHEN =
      JavaFileObjects.forSourceString(
          "javax.annotation.meta.When",
          "package javax.annotation.meta; public enum When { ALWAYS, UNKNOWN, MAYBE, NEVER }");

  @Test
  @DisplayName(
      "each recognised non-null annotation, at its real target, is refused on a setter and on a"
          + " record component outside any @NullMarked scope")
  void everyRecognisedNonNullAnnotationIsRefused() {
    Assertions.assertThat(NullableAnnotations.NON_NULL_ANNOTATION_NAMES)
        .containsExactlyInAnyOrderElementsOf(VENDORS.stream().map(Vendor::annotation).toList());
    List<JavaFileObject> beanSpecs =
        IntStream.range(0, VENDORS.size())
            .mapToObj(i -> spec("com.libs", "BeanMapping" + i, "Profile", "Bean" + i))
            .toList();
    List<JavaFileObject> recordSpecs =
        IntStream.range(0, VENDORS.size())
            .mapToObj(
                i -> spec("com.libs", "DtoMapping" + i, "Profile", "Dto" + i, NICKNAME_MARKER))
            .toList();
    List<JavaFileObject> sources =
        Stream.of(
                Stream.of(profile("com.libs"), WHEN),
                VENDORS.stream()
                    .filter(vendor -> !vendor.annotation().startsWith("org.jspecify."))
                    .map(MappingProcessorNonNullBridgeTest::standIn),
                IntStream.range(0, VENDORS.size())
                    .boxed()
                    .flatMap(
                        i -> {
                          String use = "@" + VENDORS.get(i).annotation() + VENDORS.get(i).use();
                          return Stream.of(
                              bean("com.libs", "Bean" + i, use + " String"),
                              record("com.libs", "Dto" + i, use + " String"));
                        }),
                beanSpecs.stream(),
                recordSpecs.stream())
            .flatMap(stream -> stream)
            .toList();

    Compilation compilation = compile(sources);

    assertThat(compilation).failed();
    for (int i = 0; i < VENDORS.size(); i++) {
      String annotation = VENDORS.get(i).annotation();
      String simpleName = annotation.substring(annotation.lastIndexOf('.') + 1);
      assertThat(compilation)
          .hadErrorContaining("the parameter of setNickname(String) is annotated @" + simpleName)
          .inFile(beanSpecs.get(i));
      assertThat(compilation)
          .hadErrorContaining("'Dto" + i + ".nickname' is annotated @" + simpleName)
          .inFile(recordSpecs.get(i));
    }
    Assertions.assertThat(compilation.errors()).hasSize(2 * VENDORS.size());
  }

  @Test
  @DisplayName(
      "a site that may take null bridges: any annotation named Nullable or CheckForNull, a"
          + " qualified @Nonnull, a nullable array, @NullUnmarked, a type variable that may stand"
          + " for a nullable type, and a bean that is never written")
  void sitesThatMayTakeNullStillBridge() {
    JavaFileObject jspecify = bean("com.marked", "NullableBean", "@Nullable String");
    // Any annotation named Nullable reads as nullable: refusing a build needs certainty. This one
    // reaches the field and the parameter but not an explicitly declared accessor.
    JavaFileObject otherLibrary =
        JavaFileObjects.forSourceString(
            "com.other.Nullable",
            """
            package com.other;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target({ElementType.FIELD, ElementType.PARAMETER})
            public @interface Nullable {}
            """);
    JavaFileObject checkForNull =
        JavaFileObjects.forSourceString(
            "javax.annotation.CheckForNull",
            "package javax.annotation; public @interface CheckForNull {}");
    JavaFileObject nonnull =
        JavaFileObjects.forSourceString(
            "javax.annotation.Nonnull",
            """
            package javax.annotation;

            public @interface Nonnull {
              javax.annotation.meta.When when() default javax.annotation.meta.When.ALWAYS;
            }
            """);
    JavaFileObject otherBean = bean("com.marked", "OtherBean", "@com.other.Nullable String");
    JavaFileObject checkedBean =
        bean("com.marked", "CheckedBean", "@javax.annotation.CheckForNull String");
    // JSR-305's when = MAYBE says the value may be null, so it is no refusal, even marked.
    JavaFileObject maybeBean =
        bean(
            "com.marked",
            "MaybeBean",
            "@javax.annotation.Nonnull(when = javax.annotation.meta.When.MAYBE) String");
    // An unrelated annotation says nothing, and outside a marked scope nothing else does either.
    JavaFileObject plainBean = bean("com.plain", "PlainBean", "@Deprecated String");
    JavaFileObject shapes =
        JavaFileObjects.forSourceString(
            "com.marked.Shapes",
            """
            package com.marked;

            import java.util.Optional;
            import org.jspecify.annotations.NullUnmarked;
            import org.jspecify.annotations.Nullable;

            public final class Shapes {
              public record NullableDto(String name, @Nullable String nickname) {}

              public record AccessorDto(String name, @com.other.Nullable String nickname) {
                public String nickname() {
                  return nickname;
                }
              }

              public record Domain(String name, Optional<String[]> tags) {}

              public record ArrayDto(String name, String @Nullable [] tags) {}

              @NullUnmarked
              public record UnmarkedDto(String name, String nickname) {}

              public record Holder(Optional<String> value) {}

              public record LooseDto<T extends @Nullable Object>(T value) {}

              // Getters only: parsed, never built, so nothing writes the bridge's null.
              public static final class ReadOnly {
                public String getName() { return "n"; }
                public @Nullable String getNickname() { return null; }
              }
            }
            """);
    JavaFileObject plainShapes =
        JavaFileObjects.forSourceString(
            "com.plain.PlainShapes",
            """
            package com.plain;

            import java.util.Optional;

            public final class PlainShapes {
              public record Holder(Optional<String> value) {}

              public record BoxDto<T>(T value) {}
            }
            """);
    String valueMarker =
        """
          @OptionalBridge
          Optional<String> value();
        """;
    List<JavaFileObject> specs =
        List.of(
            spec("com.marked", "NullableBeanMapping", "Profile", "NullableBean"),
            spec("com.marked", "OtherBeanMapping", "Profile", "OtherBean"),
            spec("com.marked", "CheckedBeanMapping", "Profile", "CheckedBean"),
            spec("com.marked", "MaybeBeanMapping", "Profile", "MaybeBean"),
            spec("com.plain", "PlainBeanMapping", "Profile", "PlainBean"),
            spec(
                "com.marked",
                "NullableDtoMapping",
                "Profile",
                "Shapes.NullableDto",
                NICKNAME_MARKER),
            spec(
                "com.marked",
                "AccessorDtoMapping",
                "Profile",
                "Shapes.AccessorDto",
                NICKNAME_MARKER),
            spec(
                "com.marked",
                "ArrayDtoMapping",
                "Shapes.Domain",
                "Shapes.ArrayDto",
                """
                  @OptionalBridge
                  Optional<String[]> tags();
                """),
            spec(
                "com.marked",
                "UnmarkedDtoMapping",
                "Profile",
                "Shapes.UnmarkedDto",
                NICKNAME_MARKER),
            spec(
                "com.marked",
                "LooseDtoMapping",
                "Shapes.Holder",
                "Shapes.LooseDto<String>",
                valueMarker),
            spec(
                "com.plain",
                "PlainBoxMapping",
                "PlainShapes.Holder",
                "PlainShapes.BoxDto<String>",
                valueMarker),
            spec("com.marked", "ReadOnlyMapping", "Profile", "Shapes.ReadOnly"));
    List<JavaFileObject> sources =
        Stream.concat(
                Stream.of(
                    MARKED_PACKAGE,
                    PROFILE,
                    profile("com.plain"),
                    WHEN,
                    jspecify,
                    otherLibrary,
                    checkForNull,
                    nonnull,
                    otherBean,
                    checkedBean,
                    maybeBean,
                    plainBean,
                    shapes,
                    plainShapes),
                specs.stream())
            .toList();

    Compilation compilation = compile(sources);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  @DisplayName(
      "the refusals that offer a bridge, or a wrapper to bridge onto, ask for the @Nullable a"
          + " non-null component needs, and stop calling it nullable")
  void bridgeOffersAskForTheNullableTheyNeed() {
    JavaFileObject shapes =
        JavaFileObjects.forSourceString(
            "com.marked.Offers",
            """
            package com.marked;

            import java.util.Optional;

            public final class Offers {
              public record PlainDto(String name, String nickname) {}

              public record Member(String name, Optional<EmailAddress> altEmail) {}

              public record MemberDto(String name, String altEmail) {}

              public record Aged(String name, Optional<Integer> age) {}

              public record AgeDto(String name, int age) {}
            }
            """);
    // No marker: the pair has no usable source, and the marker is the first fix offered.
    JavaFileObject unmarkedSpec =
        spec("com.marked", "PlainDtoMapping", "Profile", "Offers.PlainDto");
    // A marker, but nothing converts the element.
    JavaFileObject unconvertedSpec =
        spec(
            "com.marked",
            "MemberMapping",
            "Offers.Member",
            "Offers.MemberDto",
            """
              @OptionalBridge
              Optional<EmailAddress> altEmail();
            """);
    // A marker onto a primitive, offered the wrapper, which is as non-null as the primitive.
    JavaFileObject primitiveSpec =
        spec(
            "com.marked",
            "AgeMapping",
            "Offers.Aged",
            "Offers.AgeDto",
            """
              @OptionalBridge
              Optional<Integer> age();
            """);

    Compilation compilation =
        compile(
            List.of(
                MARKED_PACKAGE,
                PROFILE,
                EMAIL,
                shapes,
                unmarkedSpec,
                unconvertedSpec,
                primitiveSpec));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Add '@OptionalBridge java.util.Optional<java.lang.String> nickname();' to the spec,"
                + " and mark 'PlainDto.nickname' @Nullable, so an absent value reads as a null"
                + " wire component and back.")
        .inFile(unmarkedSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "is Optional<com.marked.EmailAddress>, bridged to the record component 'altEmail' of"
                + " type java.lang.String")
        .inFile(unconvertedSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "The bridge writes null for an absent value, so also mark 'MemberDto.altEmail'"
                + " @Nullable.")
        .inFile(unconvertedSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "Declare 'age' on 'AgeDto' as java.lang.Integer, and mark 'AgeDto.age' @Nullable.")
        .inFile(primitiveSpec);
    Assertions.assertThat(compilation.errors()).hasSize(3);
  }

  @Test
  @DisplayName(
      "a compiled bean's site is refused as a source one is, and the @Nullable fix says where the"
          + " bean is declared")
  void compiledWriteSiteSaysWhereToMarkIt() throws IOException {
    Compilation upstream =
        compile(
            List.of(
                JavaFileObjects.forSourceString(
                    "com.upstream.package-info",
                    """
                    @NullMarked
                    package com.upstream;

                    import org.jspecify.annotations.NullMarked;
                    """),
                bean("com.upstream", "ProfileBean", "String")));
    assertThat(upstream).succeeded();
    Path classes = classDirectory(upstream, tmp.resolve("upstream"));
    JavaFileObject spec =
        spec("com.downstream", "ProfileMapping", "Profile", "com.upstream.ProfileBean");

    Compilation compilation =
        javac()
            .withProcessors(new MappingProcessor())
            .withClasspath(classpathWith(classes))
            .compile(profile("com.downstream"), spec);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "the parameter of setNickname(String) carries no @Nullable inside @NullMarked package"
                + " 'com.upstream'")
        .inFile(spec);
    assertThat(compilation)
        .hadErrorContaining(
            "Mark the parameter of setNickname(String) @Nullable where 'ProfileBean' is declared,"
                + " if you build it, since it carries absence; or declare 'Profile.nickname' as"
                + " String, dropping the Optional; or add")
        .inFile(spec);
    Assertions.assertThat(compilation.errors()).hasSize(1);
  }
}
