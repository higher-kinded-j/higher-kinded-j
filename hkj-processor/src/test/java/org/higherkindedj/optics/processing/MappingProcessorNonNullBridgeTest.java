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
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An {@code Optional} bridge writes an empty value as {@code null}, so a write site declared
 * non-null is refused: a record component, or a setter's or builder setter's parameter, declared so
 * by a recognised annotation or by JSpecify's {@code @NullMarked} scope. A site that may take null,
 * or says nothing either way, still bridges.
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
          + " scope are each refused, naming the site and the scope")
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
    JavaFileObject record =
        JavaFileObjects.forSourceString(
            "com.marked.ProfileDto",
            """
            package com.marked;

            public record ProfileDto(String name, String nickname) {}
            """);
    // JSpecify: @Nullable String[] makes the elements nullable, and leaves the array non-null.
    JavaFileObject arrays =
        JavaFileObjects.forSourceString(
            "com.marked.Tagged",
            """
            package com.marked;

            import java.util.Optional;
            import org.jspecify.annotations.Nullable;

            public final class Tagged {
              public record Domain(String name, Optional<String[]> tags) {}

              public record Dto(String name, @Nullable String[] tags) {}
            }
            """);
    JavaFileObject beanSpec = spec("com.marked", "ProfileBeanMapping", "Profile", "ProfileBean");
    JavaFileObject builderSpec =
        spec("com.marked", "BuiltProfileMapping", "Profile", "BuiltProfile");
    JavaFileObject recordSpec =
        spec("com.marked", "ProfileDtoMapping", "Profile", "ProfileDto", NICKNAME_MARKER);
    JavaFileObject arraySpec =
        spec(
            "com.marked",
            "TaggedMapping",
            "Tagged.Domain",
            "Tagged.Dto",
            """
              @OptionalBridge
              Optional<String[]> tags();
            """);

    Compilation compilation =
        compile(
            List.of(
                MARKED_PACKAGE,
                PROFILE,
                plainSetter,
                builder,
                record,
                arrays,
                beanSpec,
                builderSpec,
                recordSpec,
                arraySpec));

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "domain field 'Profile.nickname' is Optional<String>, bridged to the bean property"
                + " 'nickname', whose setter setNickname(String) is declared non-null. The bridge"
                + " writes an empty Optional as null, and the parameter of setNickname(String)"
                + " carries no @Nullable inside @NullMarked package 'com.marked', so build would"
                + " write the null its declaration rules out, and either throw or leave a null its"
                + " type says it cannot hold. Mark the parameter of setNickname(String) @Nullable,"
                + " since it carries absence; declare 'nickname' as String, dropping the Optional;"
                + " or declare 'default ValidatedPrism<java.lang.String,"
                + " java.util.Optional<java.lang.String>> nickname()', a leaf over the whole"
                + " Optional that encodes absence the way the wire does.")
        .inFile(beanSpec);
    assertThat(compilation)
        .hadErrorContaining("whose builder setter nickname(String) is declared non-null")
        .inFile(builderSpec);
    // The record opts in with the marker, so dropping the Optional drops it too, and the leaf
    // takes its place.
    assertThat(compilation)
        .hadErrorContaining(
            "bridged to the record component 'ProfileDto.nickname', which is declared non-null."
                + " The bridge writes an empty Optional as null, and 'ProfileDto.nickname' carries"
                + " no @Nullable inside @NullMarked package 'com.marked'")
        .inFile(recordSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "Mark 'ProfileDto.nickname' @Nullable, since it carries absence; declare 'nickname' as"
                + " String, dropping the Optional and its @OptionalBridge marker; or declare"
                + " 'default ValidatedPrism<java.lang.String, java.util.Optional<java.lang.String>>"
                + " nickname()' in place of the marker")
        .inFile(recordSpec);
    assertThat(compilation)
        .hadErrorContaining(
            "bridged to the record component 'Dto.tags', which is declared non-null")
        .inFile(arraySpec);
    Assertions.assertThat(compilation.errors()).hasSize(4);
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
            "com.plain.CardDto",
            """
            package com.plain;

            import org.jspecify.annotations.NullMarked;

            @NullMarked
            public record CardDto(String name, String nickname) {}
            """);
    JavaFileObject annotated =
        bean("com.plain", "AnnotatedBean", "@org.jspecify.annotations.NonNull String");
    JavaFileObject methodSpec = spec("com.plain", "ScopedMapping", "Profile", "ScopedBean");
    JavaFileObject classSpec =
        spec("com.plain", "CardMapping", "Profile", "CardDto", NICKNAME_MARKER);
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
        .hadErrorContaining("carries no @Nullable inside @NullMarked record 'com.plain.CardDto'")
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

  @Test
  @DisplayName("each recognised non-null annotation is refused outside any @NullMarked scope")
  void everyRecognisedNonNullAnnotationIsRefused() {
    List<JavaFileObject> sources = new ArrayList<>();
    sources.add(profile("com.libs"));
    List<JavaFileObject> specs = new ArrayList<>();
    int index = 0;
    for (String annotation : NullableAnnotations.NON_NULL_ANNOTATION_NAMES) {
      int dot = annotation.lastIndexOf('.');
      String packageName = annotation.substring(0, dot);
      String simpleName = annotation.substring(dot + 1);
      // A declaration annotation with no @Target applies to a parameter, whatever the library's
      // own placement; the name is what the processor reads.
      if (!packageName.equals("org.jspecify.annotations")) {
        sources.add(
            JavaFileObjects.forSourceString(
                annotation,
                "package %s; public @interface %s {}".formatted(packageName, simpleName)));
      }
      String bean = "Bean" + index;
      sources.add(bean("com.libs", bean, "@" + annotation + " String"));
      JavaFileObject spec = spec("com.libs", "Mapping" + index, "Profile", bean);
      specs.add(spec);
      index++;
    }
    sources.addAll(specs);

    Compilation compilation = compile(sources);

    assertThat(compilation).failed();
    for (JavaFileObject spec : specs) {
      assertThat(compilation)
          .hadErrorContaining("whose setter setNickname(String) is declared non-null")
          .inFile(spec);
    }
    Assertions.assertThat(compilation.errors()).hasSize(specs.size());
  }

  @Test
  @DisplayName(
      "a nullable site bridges: @Nullable from any library, a nullable array, @NullUnmarked, a"
          + " type variable, and a bean that is never written")
  void sitesThatMayTakeNullStillBridge() {
    JavaFileObject jspecify = bean("com.marked", "NullableBean", "@Nullable String");
    // Any annotation named Nullable reads as nullable: refusing a build needs certainty.
    JavaFileObject otherLibrary =
        JavaFileObjects.forSourceString(
            "com.other.Nullable", "package com.other; public @interface Nullable {}");
    JavaFileObject otherBean = bean("com.marked", "OtherBean", "@com.other.Nullable String");
    JavaFileObject record =
        JavaFileObjects.forSourceString(
            "com.marked.Shapes",
            """
            package com.marked;

            import java.util.Optional;
            import org.jspecify.annotations.NullUnmarked;
            import org.jspecify.annotations.Nullable;

            public final class Shapes {
              public record NullableDto(String name, @Nullable String nickname) {}

              public record Domain(String name, Optional<String[]> tags) {}

              public record ArrayDto(String name, String @Nullable [] tags) {}

              @NullUnmarked
              public record UnmarkedDto(String name, String nickname) {}

              public record Holder(Optional<String> value) {}

              public record BoxDto<T>(T value) {}

              // Getters only: parsed, never built, so nothing writes the bridge's null.
              public static final class ReadOnly {
                public String getName() { return "n"; }
                public @Nullable String getNickname() { return null; }
              }
            }
            """);
    List<JavaFileObject> specs =
        List.of(
            spec("com.marked", "NullableBeanMapping", "Profile", "NullableBean"),
            spec("com.marked", "OtherBeanMapping", "Profile", "OtherBean"),
            spec(
                "com.marked",
                "NullableDtoMapping",
                "Profile",
                "Shapes.NullableDto",
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
                "BoxDtoMapping",
                "Shapes.Holder",
                "Shapes.BoxDto<String>",
                """
                  @OptionalBridge
                  Optional<String> value();
                """),
            spec("com.marked", "ReadOnlyMapping", "Profile", "Shapes.ReadOnly"));
    List<JavaFileObject> sources = new ArrayList<>();
    sources.addAll(List.of(MARKED_PACKAGE, PROFILE, jspecify, otherLibrary, otherBean, record));
    sources.addAll(specs);

    Compilation compilation = compile(sources);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  @DisplayName(
      "a compiled bean's site is refused as a source one is, offering only the fixes its author"
          + " can make downstream")
  void compiledWriteSiteOffersOnlyDownstreamFixes() throws IOException {
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
            "'ProfileBean' is compiled, so its declaration cannot change here: declare 'nickname'"
                + " as String, dropping the Optional; or declare 'default"
                + " ValidatedPrism<java.lang.String, java.util.Optional<java.lang.String>>"
                + " nickname()'")
        .inFile(spec);
    Assertions.assertThat(compilation.errors())
        .singleElement()
        .satisfies(
            error -> Assertions.assertThat(error.getMessage(null)).doesNotContain("Mark the"));
  }
}
