// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A client interface extending a base interface compiled into a dependency. The base module is
 * compiled on its own, without the processor, and laid out as class files on a directory, the shape
 * a jar has on a downstream classpath. What the base declares must mean the same to the client as
 * it does when the base is compiled alongside it.
 */
@DisplayName("HkjHttpClientProcessor - a base interface compiled into a dependency")
class HkjHttpClientProcessorClasspathTest {

  private static final List<File> TEST_CLASSPATH =
      Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
          .map(File::new)
          .toList();

  /** The base module's types. The error type is open, so a subtype's class file can go missing. */
  private static final List<JavaFileObject> BASE_TYPES =
      List.of(
          JavaFileObjects.forSourceString(
              "com.upstream.ApiErr", "package com.upstream; public interface ApiErr {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.NotFound",
              "package com.upstream; public record NotFound(String message) implements ApiErr {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.Conflict",
              "package com.upstream; public record Conflict(String message) implements ApiErr {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.Unrelated",
              "package com.upstream; public record Unrelated(String message) {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.UserDto", "package com.upstream; public record UserDto(String id) {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.Marked",
              "package com.upstream; @java.lang.annotation.Target(java.lang.annotation.ElementType"
                  + ".TYPE_USE) public @interface Marked {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.Old",
              "package com.upstream; @Deprecated @java.lang.annotation.Target("
                  + "java.lang.annotation.ElementType.TYPE_USE) public @interface Old {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.Dated",
              "package com.upstream; @java.lang.annotation.Target(java.lang.annotation.ElementType"
                  + ".TYPE_USE) public @interface Dated { @Deprecated String since() default \"\"; }"),
          JavaFileObjects.forSourceString(
              "com.upstream.Local",
              "package com.upstream; @java.lang.annotation.Target(java.lang.annotation.ElementType"
                  + ".TYPE_USE) @interface Local {}"),
          JavaFileObjects.forSourceString(
              "com.upstream.errors.Gone",
              "package com.upstream.errors;"
                  + " public record Gone(String message) implements com.upstream.ApiErr {}"));

  private static final JavaFileObject CHILD =
      JavaFileObjects.forSourceString(
          "com.downstream.ChildApi",
          """
          package com.downstream;

          import com.upstream.BaseApi;
          import org.higherkindedj.spring.client.HkjHttpClient;
          import org.springframework.web.service.annotation.HttpExchange;

          @HttpExchange("/users")
          @HkjHttpClient
          public interface ChildApi extends BaseApi {}
          """);

  /** Two overrides on one method, stored as the container, and a single one, stored bare. */
  private static final String OVERRIDES =
      """
        @GetExchange("/{id}")
        @OnStatus(value = 404, error = NotFound.class)
        @OnStatus(value = 409, error = Conflict.class)
        EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);

        @GetExchange("/{id}/profile")
        @OnStatus(value = 404, error = NotFound.class)
        EitherPath<ApiErr, UserDto> getProfile(@PathVariable String id);
      """;

  @TempDir Path tmp;

  /** The base module's sources: its types and a base interface with the given members. */
  private static List<JavaFileObject> baseSources(String members) {
    return baseSources("", members);
  }

  /**
   * The base module's sources: its types and a base interface with the given type parameters and
   * members.
   */
  private static List<JavaFileObject> baseSources(String typeParameters, String members) {
    JavaFileObject baseApi =
        JavaFileObjects.forSourceString(
            "com.upstream.BaseApi",
            """
            package com.upstream;

            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.MaybePath;
            import org.higherkindedj.spring.client.OnStatus;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.service.annotation.GetExchange;

            public interface BaseApi%s {
            %s
            }
            """
                .formatted(typeParameters, members));
    return Stream.concat(BASE_TYPES.stream(), Stream.of(baseApi)).toList();
  }

  /** The processor, over the test classpath plus the given class directories. */
  private static Compiler compiler(Path... classDirs) {
    List<File> classpath = new ArrayList<>(TEST_CLASSPATH);
    for (Path dir : classDirs) {
      classpath.add(dir.toFile());
    }
    return javac().withProcessors(new HkjHttpClientProcessor()).withClasspath(classpath);
  }

  /**
   * Compiles the base module and lays its class files out on a fresh directory. It runs no
   * processor, as a shared base interface carries no {@code @HkjHttpClient} of its own, and it
   * passes {@code -parameters}, which the book requires of a base in a jar.
   */
  private Path baseModule(String members) throws IOException {
    return baseModule("", members);
  }

  /**
   * {@link #baseModule(String)} for a base interface with the given type parameters, and any other
   * interfaces the module declares beside it.
   */
  private Path baseModule(String typeParameters, String members, JavaFileObject... others)
      throws IOException {
    Compilation compilation =
        javac()
            .withProcessors()
            .withOptions("-parameters")
            .withClasspath(TEST_CLASSPATH)
            .compile(
                Stream.concat(baseSources(typeParameters, members).stream(), Stream.of(others))
                    .toList());
    assertThat(compilation).succeeded();
    Path dir = Files.createTempDirectory(tmp, "base");
    layOut(compilation, dir);
    return dir;
  }

  /**
   * Compiles a type on its own against the test classpath, as another version of one the base
   * module declares, for {@link #layOut} to put in its place.
   */
  private static Compilation compileAlone(JavaFileObject source) {
    Compilation compilation =
        javac().withProcessors().withClasspath(TEST_CLASSPATH).compile(source);
    assertThat(compilation).succeeded();
    return compilation;
  }

  /** Lays a compilation's class files out on a directory, over any already there. */
  private static void layOut(Compilation compilation, Path dir) throws IOException {
    for (JavaFileObject file : compilation.generatedFiles()) {
      if (file.getKind() != JavaFileObject.Kind.CLASS) {
        continue;
      }
      // /CLASS_OUTPUT/com/upstream/BaseApi.class -> com/upstream/BaseApi.class
      String path = file.getName();
      String marker = StandardLocation.CLASS_OUTPUT.getName() + "/";
      Path target = dir.resolve(path.substring(path.indexOf(marker) + marker.length()));
      Files.createDirectories(target.getParent());
      try (InputStream in = file.openInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    Optional<JavaFileObject> file =
        compilation.generatedFile(
            StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    Assertions.assertThat(file).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  @DisplayName("its @OnStatus overrides generate the same client as a base compiled alongside")
  void overridesCrossTheBoundary() throws IOException {
    Compilation across = compiler(baseModule(OVERRIDES)).compile(CHILD);
    assertThat(across).succeeded();
    Compilation together =
        compiler()
            .compile(Stream.concat(baseSources(OVERRIDES).stream(), Stream.of(CHILD)).toList());
    assertThat(together).succeeded();

    // Named first, so a regression reads as the override that went missing rather than as a diff
    // of two generated files. getUser keeps its overrides only if the container survives the class
    // file, getProfile only if the bare annotation does.
    Assertions.assertThat(generatedSource(across, "com.downstream.ChildApiClient"))
        .contains(
            "forDefault(decoderFactory, ApiErr.class).on(404, NotFound.class)"
                + ".on(409, Conflict.class).build()")
        .contains("forDefault(decoderFactory, ApiErr.class).on(404, NotFound.class).build()")
        .doesNotContain("decoderFactory.create(");

    // Then the whole files: the boundary must not change the client at all. The native interface
    // is compared too, since an override read from a class file must still not be copied onto it.
    for (String generated :
        List.of("com.downstream.ChildApiClient", "com.downstream.ChildApiHttpExchange")) {
      Assertions.assertThat(generatedSource(across, generated))
          .isEqualTo(generatedSource(together, generated));
    }
  }

  @Test
  @DisplayName("a generic base's methods are read as the client instantiates it")
  void genericBaseCrossesTheBoundary() throws IOException {
    String typeParameters = "<E, T, ID>";
    // Each type variable in its own position, one use of it annotated, and an override that is
    // assignable only to the error type the client binds.
    String members =
        """
          @GetExchange("/{id}")
          @OnStatus(value = 404, error = NotFound.class)
          EitherPath<E, @Marked T> getUser(@PathVariable ID id);
        """;
    JavaFileObject child =
        JavaFileObjects.forSourceString(
            "com.downstream.ChildApi",
            """
            package com.downstream;

            import com.upstream.ApiErr;
            import com.upstream.BaseApi;
            import com.upstream.UserDto;
            import org.higherkindedj.spring.client.HkjHttpClient;
            import org.springframework.web.service.annotation.HttpExchange;

            @HttpExchange("/users")
            @HkjHttpClient
            public interface ChildApi extends BaseApi<ApiErr, UserDto, String> {}
            """);

    Compilation across = compiler(baseModule(typeParameters, members)).compile(child);
    assertThat(across).succeeded();
    Compilation together =
        compiler()
            .compile(
                Stream.concat(baseSources(typeParameters, members).stream(), Stream.of(child))
                    .toList());
    assertThat(together).succeeded();

    Assertions.assertThat(generatedSource(across, "com.downstream.ChildApiClient"))
        .contains("forDefault(decoderFactory, ApiErr.class).on(404, NotFound.class).build()")
        .contains("EitherPath<ApiErr, @Marked UserDto> getUser(String id)");
    Assertions.assertThat(generatedSource(across, "com.downstream.ChildApiHttpExchange"))
        .contains("ResponseEntity<@Marked UserDto> getUser(@PathVariable(\"id\") String id)");
    for (String generated :
        List.of("com.downstream.ChildApiClient", "com.downstream.ChildApiHttpExchange")) {
      Assertions.assertThat(generatedSource(across, generated))
          .isEqualTo(generatedSource(together, generated));
    }
  }

  @Test
  @DisplayName("its type-level @HttpExchange reaches a client with none, as from a base alongside")
  void typeLevelExchangeCrossesTheBoundary() throws IOException {
    String members =
        """
          @GetExchange("/{id}")
          EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
        """;
    JavaFileObject usersApi =
        JavaFileObjects.forSourceString(
            "com.upstream.UsersApi",
            """
            package com.upstream;

            import org.springframework.web.service.annotation.HttpExchange;

            @HttpExchange(url = "/api/users", accept = "application/json")
            public interface UsersApi extends BaseApi {}
            """);
    JavaFileObject child =
        JavaFileObjects.forSourceString(
            "com.downstream.ChildApi",
            """
            package com.downstream;

            import com.upstream.UsersApi;
            import org.higherkindedj.spring.client.HkjHttpClient;

            @HkjHttpClient
            public interface ChildApi extends UsersApi {}
            """);

    Compilation across = compiler(baseModule("", members, usersApi)).compile(child);
    assertThat(across).succeeded();
    Compilation together =
        compiler()
            .compile(
                Stream.concat(baseSources(members).stream(), Stream.of(usersApi, child)).toList());
    assertThat(together).succeeded();

    // Read from the class file, the annotation is written onto the interface Spring proxies.
    Assertions.assertThat(generatedSource(across, "com.downstream.ChildApiHttpExchange"))
        .containsPattern(
            "@HttpExchange\\(\\s*url = \"/api/users\",\\s*accept = \"application/json\"\\s*\\)")
        .isEqualTo(generatedSource(together, "com.downstream.ChildApiHttpExchange"));
  }

  @Test
  @DisplayName("a client that writes its own @HttpExchange has no other annotation looked into")
  void ownTypeLevelExchangeLooksNoFurther() throws IOException {
    // Two levels under Tagged, an annotation names a type the client's classpath lacks. javac reads
    // that far only when something looks into Tagged's own annotations, and then warns of the
    // unknown enum constant, which -Werror makes fatal. A client that already says what Spring
    // reads must not have Tagged looked into. One that does not has it looked into, as Spring
    // would, and that warning is the cost. Tagged comes first, so the client's own is not met
    // before it.
    Path base =
        baseModule(
            "",
            """
              @GetExchange("/{id}")
              EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
            """,
            JavaFileObjects.forSourceString(
                "com.upstream.Level", "package com.upstream; public enum Level { LOW, HIGH }"),
            JavaFileObjects.forSourceString(
                "com.upstream.Graded",
                """
                package com.upstream;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.RUNTIME)
                public @interface Graded {
                  Level value();
                }
                """),
            JavaFileObjects.forSourceString(
                "com.upstream.Grouped",
                """
                package com.upstream;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.RUNTIME)
                @Graded(Level.HIGH)
                public @interface Grouped {}
                """),
            JavaFileObjects.forSourceString(
                "com.upstream.Tagged",
                """
                package com.upstream;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.RUNTIME)
                @Grouped
                public @interface Tagged {}
                """));
    Files.delete(base.resolve("com/upstream/Level.class"));
    Files.delete(base.resolve("com/upstream/Graded.class"));
    String child =
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import com.upstream.Tagged;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @Tagged
        %s
        @HkjHttpClient
        public interface ChildApi extends BaseApi {}
        """;

    Compilation own =
        compiler(base)
            .compile(
                JavaFileObjects.forSourceString(
                    "com.downstream.ChildApi", child.formatted("@HttpExchange(\"/users\")")));
    Compilation none =
        compiler(base)
            .compile(
                JavaFileObjects.forSourceString("com.downstream.ChildApi", child.formatted("")));

    assertThat(own).succeeded();
    assertThat(own).hadWarningCount(0);
    // The control: the same classpath warns once Tagged is looked into, so the count above means
    // something.
    assertThat(none).succeeded();
    assertThat(none).hadWarningContaining("unknown enum constant com.upstream.Level.HIGH");
  }

  @Test
  @DisplayName("an error type the client misspells is left to javac, though the base is a jar's")
  void misspeltBoundErrorTypeIsLeftToJavac() throws IOException {
    Path base =
        baseModule(
            "<E, T>",
            """
              @GetExchange("/{id}")
              EitherPath<E, T> getUser(@PathVariable String id);
            """);
    Compilation compilation =
        compiler(base)
            .compile(
                JavaFileObjects.forSourceString(
                    "com.downstream.ChildApi",
                    """
                    package com.downstream;

                    import com.upstream.BaseApi;
                    import com.upstream.UserDto;
                    import org.higherkindedj.spring.client.HkjHttpClient;
                    import org.springframework.web.service.annotation.HttpExchange;

                    @HttpExchange("/users")
                    @HkjHttpClient
                    public interface ChildApi extends BaseApi<ApiEr, UserDto> {}
                    """));

    // The client wrote the name, in source, so javac reports it there; the advice to add a
    // dependency is for a type a class file names, and would misdirect a typo.
    assertThat(compilation).hadErrorContaining("cannot find symbol");
    Assertions.assertThat(compilation.errors())
        .noneMatch(error -> error.getMessage(null).contains("classpath"));
  }

  @Test
  @DisplayName("an error type a jar's interface binds and the classpath lacks says so")
  void errorTypeBoundInAJarAndMissingSaysSo() throws IOException {
    // The jar's own UserBaseApi binds BaseApi's E: the class file names the type, as a method's
    // declaration would, and javac reports nothing about it.
    Path base =
        baseModule(
            "<E, T>",
            """
              @GetExchange("/{id}")
              EitherPath<E, T> getUser(@PathVariable String id);
            """,
            JavaFileObjects.forSourceString(
                "com.upstream.UserBaseApi",
                """
                package com.upstream;

                public interface UserBaseApi
                    extends BaseApi<com.upstream.errors.Gone, UserDto> {}
                """));
    Files.delete(base.resolve("com/upstream/errors/Gone.class"));
    JavaFileObject child =
        JavaFileObjects.forSourceString(
            "com.downstream.ChildApi",
            """
            package com.downstream;

            import com.upstream.UserBaseApi;
            import org.higherkindedj.spring.client.HkjHttpClient;
            import org.springframework.web.service.annotation.HttpExchange;

            @HttpExchange("/users")
            @HkjHttpClient
            public interface ChildApi extends UserBaseApi {}
            """);
    Compilation compilation = compiler(base).compile(child);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "'getUser', inherited from 'BaseApi': @HkjHttpClient error type"
                + " com.upstream.errors.Gone is not on this compilation's classpath; add the"
                + " dependency that declares it.")
        .inFile(child);
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("a problem with an inherited override is reported on the client, naming the method")
  void inheritedOverrideProblemsAreReportedOnTheClient() throws IOException {
    Compilation compilation =
        compiler(
                baseModule(
                    """
                      @GetExchange("/{id}")
                      @OnStatus(value = 404, error = Unrelated.class)
                      EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);

                      @GetExchange("/{id}/profile")
                      @OnStatus(value = 404, error = NotFound.class)
                      @OnStatus(value = 404, error = Conflict.class)
                      EitherPath<ApiErr, UserDto> getProfile(@PathVariable String id);

                      @GetExchange("/{id}/nickname")
                      @OnStatus(value = 404, error = NotFound.class)
                      MaybePath<String> findNickname(@PathVariable String id);
                    """))
            .compile(CHILD);

    // A method read from a class file has no file and no line of its own, so each diagnostic lands
    // on the interface the author wrote and says which method it means.
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "'getUser', inherited from 'BaseApi': @OnStatus error type com.upstream.Unrelated is"
                + " not assignable to the method's error type com.upstream.ApiErr.")
        .inFile(CHILD);
    assertThat(compilation)
        .hadWarningContaining(
            "'getProfile', inherited from 'BaseApi': Duplicate @OnStatus for status 404")
        .inFile(CHILD);
    assertThat(compilation)
        .hadWarningContaining(
            "'findNickname', inherited from 'BaseApi': @OnStatus has no effect on a MaybePath")
        .inFile(CHILD);
  }

  @Test
  @DisplayName("an inherited override naming a type missing from the classpath says so")
  void inheritedOverrideNamingAMissingTypeSaysSo() throws IOException {
    Path base =
        baseModule(
            """
              @GetExchange("/{id}")
              @OnStatus(value = 404, error = com.upstream.errors.Gone.class)
              EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
            """);
    // The base module could see the type; the client's classpath does not carry it.
    Files.delete(base.resolve("com/upstream/errors/Gone.class"));
    Compilation compilation = compiler(base).compile(CHILD);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "'getUser', inherited from 'BaseApi': @OnStatus error type com.upstream.errors.Gone is"
                + " not on this compilation's classpath; add the dependency that declares it.")
        .inFile(CHILD);
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName("an inherited method whose error type is missing from the classpath says so")
  void inheritedMethodWithAMissingErrorTypeSaysSo() throws IOException {
    Path base =
        baseModule(
            """
              @GetExchange("/{id}")
              @OnStatus(value = 404, error = NotFound.class)
              EitherPath<com.upstream.errors.Gone, UserDto> getUser(@PathVariable String id);
            """);
    Files.delete(base.resolve("com/upstream/errors/Gone.class"));
    Compilation compilation = compiler(base).compile(CHILD);

    // Not the override's "not assignable", and not a failure inside generated code naming a class
    // the author never wrote: the declared type itself is what the classpath lacks.
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "'getUser', inherited from 'BaseApi': @HkjHttpClient error type"
                + " com.upstream.errors.Gone is not on this compilation's classpath; add the"
                + " dependency that declares it.")
        .inFile(CHILD);
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  @DisplayName(
      "an annotation the client could not write cleanly is left off the method it restates")
  void anAnnotationTheClientCannotWriteIsLeftOff() throws IOException {
    // A type-use annotation the base module compiled against but did not pass on, as Gradle's
    // compileOnly does: read back from the class file unresolved, it cannot be written into the
    // client, which restates the inherited method's parameter and return types. A deprecated one,
    // or one setting a deprecated member, can, but would draw a warning there, and one
    // package-private to the base's own package cannot be named from the client's package at all.
    Path base =
        baseModule(
            """
              @GetExchange("/{id}")
              EitherPath<ApiErr, @Marked UserDto> getUser(
                  @PathVariable @Marked @Old @Dated(since = "1") @Local String id);
            """);
    Files.delete(base.resolve("com/upstream/Marked.class"));
    Compilation compilation = compiler(base).compile(CHILD);

    assertThat(compilation).succeeded();
    Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiClient"))
        .contains("getUser(String id)")
        .doesNotContain("Marked")
        .doesNotContain("Old")
        .doesNotContain("Dated")
        .doesNotContain("Local");
  }

  /**
   * The annotations a client copies from a base in a jar: kept as written where the client's
   * package can write them, refused where it cannot, and copied with their warnings suppressed
   * where they are deprecated.
   */
  @Nested
  @DisplayName("an annotation a jar's base carries, copied onto the generated interface")
  class CopiedAnnotations {

    /**
     * Annotations the base module declares for the cases below: some its own package alone can
     * name, some the client's classpath will lack once their class files are deleted, and some
     * deprecated.
     */
    private static final List<JavaFileObject> ANNOTATION_TYPES =
        List.of(
            annotationType(
                "LocalExchange",
                "@HttpExchange(\"/local\") @Target(ElementType.TYPE) @interface LocalExchange {}"),
            annotationType(
                "LocalGet",
                "@HttpExchange(method = \"GET\") @Target(ElementType.METHOD) @interface LocalGet {"
                    + " @AliasFor(annotation = HttpExchange.class) String value() default \"\"; }"),
            annotationType(
                "LocalBinding", "@Target(ElementType.PARAMETER) @interface LocalBinding {}"),
            annotationType(
                "AbsentExchange",
                "@HttpExchange(\"/absent\") @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})"
                    + " public @interface AbsentExchange {}"),
            annotationType("Wrapped", "@AbsentExchange public @interface Wrapped {}"),
            annotationType("Absent", "public @interface Absent {}"),
            JavaFileObjects.forSourceString(
                "com.upstream.ClassOnly",
                "package com.upstream; @java.lang.annotation.Retention("
                    + "java.lang.annotation.RetentionPolicy.CLASS) public @interface ClassOnly {}"),
            JavaFileObjects.forSourceString(
                "com.upstream.Tier", "package com.upstream; public enum Tier { LOW, HIGH }"),
            annotationType("Tiered", "public @interface Tiered { Tier value(); }"),
            annotationType("Revised", "public @interface Revised { int revision() default 0; }"),
            annotationType("Named", "public @interface Named { String value(); }"),
            annotationType(
                "Widened",
                "public @interface Widened { int n(); String text(); int small(); char letter(); }"),
            JavaFileObjects.forSourceString(
                "com.upstream.Hidden", "package com.upstream; class Hidden {}"),
            annotationType("Handled", "public @interface Handled { Class<?> value(); }"),
            annotationType(
                "OldExchange",
                "@Deprecated @HttpExchange(\"/old\") @Target(ElementType.TYPE)"
                    + " public @interface OldExchange {}"),
            annotationType(
                "Retired",
                "@Deprecated @Target({ElementType.METHOD, ElementType.PARAMETER})"
                    + " public @interface Retired {}"),
            annotationType(
                "Doomed",
                "@Deprecated(forRemoval = true) @Target(ElementType.PARAMETER)"
                    + " public @interface Doomed {}"),
            annotationType(
                "Versioned",
                "public @interface Versioned { @Deprecated String since() default \"\"; }"),
            JavaFileObjects.forSourceString(
                "com.upstream.Legacy",
                """
                package com.upstream;

                /**
                 * Deprecated by its javadoc alone.
                 *
                 * @deprecated use {@link Retired}
                 */
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                public @interface Legacy {}
                """),
            JavaFileObjects.forSourceString(
                "com.upstream.ExchangeApi",
                "package com.upstream; @org.springframework.web.service.annotation.HttpExchange("
                    + "\"/users\") public interface ExchangeApi {}"));

    /** A run-time annotation type in the base's package, declared as given. */
    private static JavaFileObject annotationType(String name, String declaration) {
      return JavaFileObjects.forSourceString(
          "com.upstream." + name,
          """
          package com.upstream;

          import java.lang.annotation.ElementType;
          import java.lang.annotation.Retention;
          import java.lang.annotation.RetentionPolicy;
          import java.lang.annotation.Target;
          import org.springframework.core.annotation.AliasFor;
          import org.springframework.web.service.annotation.HttpExchange;

          @Retention(RetentionPolicy.RUNTIME)
          %s
          """
              .formatted(declaration));
    }

    /** {@code UsersApi}, a base of the base interface carrying the given type-level annotations. */
    private static JavaFileObject usersApi(String annotations) {
      return JavaFileObjects.forSourceString(
          "com.upstream.UsersApi",
          """
          package com.upstream;

          import org.springframework.web.service.annotation.HttpExchange;

          %s
          public interface UsersApi extends BaseApi {}
          """
              .formatted(annotations));
    }

    /**
     * A client of {@code UsersApi}, and of any other interfaces given, with the given type-level
     * annotations and no {@code @HttpExchange} of its own.
     */
    private static JavaFileObject usersChild(String annotations, String... alsoExtends) {
      return JavaFileObjects.forSourceString(
          "com.downstream.ChildApi",
          """
          package com.downstream;

          import com.upstream.UsersApi;
          import org.higherkindedj.spring.client.HkjHttpClient;

          %s
          @HkjHttpClient
          public interface ChildApi extends UsersApi%s {}
          """
              .formatted(
                  annotations,
                  Stream.of(alsoExtends).map(type -> ", " + type).collect(Collectors.joining())));
    }

    /** The base module with these annotation types, and any other interfaces given. */
    private Path baseWith(String members, JavaFileObject... others) throws IOException {
      return baseModule(
          "",
          members,
          Stream.concat(ANNOTATION_TYPES.stream(), Stream.of(others))
              .toArray(JavaFileObject[]::new));
    }

    private static final String GET_USER =
        """
          @GetExchange("/{id}")
          EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
        """;

    /**
     * A type-level {@code @HttpExchange} a jar's base gives the client that its native interface
     * cannot carry, or that the processor cannot see. The base's annotations are on {@code
     * UsersApi}, which the client extends; each row names the class files the client's classpath
     * lacks, and the message the processor reports.
     */
    enum TypeLevelRefusal {
      NOT_PUBLIC(
          "composed through a package-private annotation",
          "@LocalExchange",
          List.of(),
          "@HkjHttpClient cannot copy @com.upstream.LocalExchange, the type-level @HttpExchange"
              + " ChildApi inherits from UsersApi, onto ChildApiHttpExchange, the interface Spring"
              + " proxies: it is not public, so ChildApiHttpExchange cannot name it; declare"
              + " @HttpExchange on ChildApi as the client should have it."),
      MISSING(
          "carried by an annotation the classpath lacks, which may be Spring's @HttpExchange",
          "@AbsentExchange",
          List.of("AbsentExchange"),
          "@HkjHttpClient cannot tell whether ChildApi inherits a type-level @HttpExchange from"
              + " UsersApi: UsersApi carries @com.upstream.AbsentExchange, which is not on this"
              + " compilation's classpath; add the dependency that declares it, or declare"
              + " @HttpExchange on ChildApi as the client should have it."),
      MISSING_UNDERNEATH(
          "carried by an annotation composed from one the classpath lacks",
          "@Wrapped",
          List.of("AbsentExchange"),
          "@HkjHttpClient cannot tell whether ChildApi inherits a type-level @HttpExchange from"
              + " UsersApi: UsersApi carries @com.upstream.Wrapped, composed from"
              + " @com.upstream.AbsentExchange, which is not on this compilation's classpath; add"
              + " the dependency that declares it, or declare @HttpExchange on ChildApi as the"
              + " client should have it.");

      private final String description;
      private final String annotations;
      private final List<String> deleted;
      private final String message;

      TypeLevelRefusal(
          String description, String annotations, List<String> deleted, String message) {
        this.description = description;
        this.annotations = annotations;
        this.deleted = deleted;
        this.message = message;
      }

      @Override
      public String toString() {
        return description;
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TypeLevelRefusal.class)
    @DisplayName("a type-level @HttpExchange the client cannot carry, or see, is refused")
    void aTypeLevelExchangeTheClientCannotCarryIsRefused(TypeLevelRefusal refusal)
        throws IOException {
      Path base = baseWith(GET_USER, usersApi(refusal.annotations));
      for (String type : refusal.deleted) {
        Files.delete(base.resolve("com/upstream/" + type + ".class"));
      }
      JavaFileObject child = usersChild("");
      Compilation compilation = compiler(base).compile(child);

      // One error, on the client, in place of errors inside generated code or a client that sends
      // its requests somewhere else.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining(refusal.message).inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    /**
     * An annotation a jar's base method carries, on itself or a parameter, that the client's
     * package cannot write and Spring reads. Each row gives the base's methods, the class files the
     * client's classpath lacks or has in another version, and the message the processor reports.
     * The rows that are not public read the modifiers from class files; {@code
     * HkjHttpClientProcessorTest} reads the same shapes from source.
     */
    enum MethodRefusal {
      NOT_PUBLIC(
          "a method's package-private mapping annotation",
          """
            @LocalGet("/{id}")
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.LocalGet"
              + " onto ChildApiHttpExchange, the interface Spring proxies: it is not public, so"
              + " ChildApiHttpExchange cannot name it; redeclare 'getUser' on ChildApi as the client"
              + " should have it."),
      MISSING(
          "a method's annotation the classpath lacks",
          """
            @GetExchange("/{id}")
            @Absent
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of("Absent"),
          List.of(),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Absent"
              + " onto ChildApiHttpExchange, the interface Spring proxies: it is not on this"
              + " compilation's classpath; add the dependency that declares it, or redeclare"
              + " 'getUser' on ChildApi as the client should have it."),
      VALUE_MISSING(
          "a method's annotation naming an enum the classpath lacks",
          """
            @GetExchange("/{id}")
            @Tiered(Tier.HIGH)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of("Tier"),
          List.of(),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Tiered"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Tier is not"
              + " on this compilation's classpath; add the dependency that declares it, or redeclare"
              + " 'getUser' on ChildApi as the client should have it."),
      CONSTANT_GONE(
          "a method's annotation naming a constant the classpath's version of its enum lacks",
          """
            @GetExchange("/{id}")
            @Tiered(Tier.HIGH)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(
              JavaFileObjects.forSourceString(
                  "com.upstream.Tier", "package com.upstream; public enum Tier { LOW }")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Tiered"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Tier on this"
              + " compilation's classpath has no constant HIGH; build against the version of it"
              + " BaseApi was compiled with, or redeclare 'getUser' on ChildApi as the client"
              + " should have it."),
      MEMBER_GONE(
          "a method's annotation setting a member the classpath's version of it lacks",
          """
            @GetExchange("/{id}")
            @Revised(revision = 2)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(annotationType("Revised", "public @interface Revised {}")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Revised"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Revised on"
              + " this compilation's classpath has no member revision(); build against the version"
              + " of it BaseApi was compiled with, or redeclare 'getUser' on ChildApi as the client"
              + " should have it."),
      MEMBER_RETYPED(
          "a method's annotation setting a member the classpath's version of it declares otherwise",
          """
            @GetExchange("/{id}")
            @Revised(revision = 2)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(annotationType("Revised", "public @interface Revised { String revision(); }")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Revised"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Revised on"
              + " this compilation's classpath declares revision() as another type; build against"
              + " the version of it BaseApi was compiled with, or redeclare 'getUser' on ChildApi"
              + " as the client should have it."),
      MEMBER_NARROWED(
          "a method's annotation setting a value too large for the type the classpath's version"
              + " declares",
          """
            @GetExchange("/{id}")
            @Revised(revision = 300)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(annotationType("Revised", "public @interface Revised { byte revision(); }")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Revised"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Revised on"
              + " this compilation's classpath declares revision() as another type; build against"
              + " the version of it BaseApi was compiled with, or redeclare 'getUser' on ChildApi"
              + " as the client should have it."),
      MEMBER_TYPE_MISSING(
          "a method's annotation setting a member the classpath's version of it declares with a"
              + " type the classpath lacks",
          """
            @GetExchange("/{id}")
            @Revised(revision = 2)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of("Grade"),
          List.of(
              annotationType(
                  "Revised", "public @interface Revised { Grade revision(); } enum Grade { LOW }")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Revised"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Grade is not"
              + " on this compilation's classpath; add the dependency that declares it, or redeclare"
              + " 'getUser' on ChildApi as the client should have it."),
      DEFAULT_ADDED(
          "a method's annotation whose classpath version adds a member with no default",
          """
            @GetExchange("/{id}")
            @Revised(revision = 2)
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(
              annotationType(
                  "Revised",
                  "public @interface Revised { int revision() default 0; String since(); }")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Revised"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Revised on"
              + " this compilation's classpath declares since() with no default; build against the"
              + " version of it BaseApi was compiled with, or redeclare 'getUser' on ChildApi as the"
              + " client should have it."),
      STRING_TO_CLASS(
          "a method's annotation setting a string where the classpath's version takes a class",
          """
            @GetExchange("/{id}")
            @Named("n")
            EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
          """,
          List.of(),
          List.of(annotationType("Named", "public @interface Named { Class<?> value(); }")),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Named"
              + " onto ChildApiHttpExchange, the interface Spring proxies: com.upstream.Named on"
              + " this compilation's classpath declares value() as another type; build against the"
              + " version of it BaseApi was compiled with, or redeclare 'getUser' on ChildApi as the"
              + " client should have it."),
      PARAMETER_NOT_PUBLIC(
          "a parameter's package-private annotation",
          """
            @GetExchange("/{id}")
            EitherPath<ApiErr, UserDto> getUser(@PathVariable @LocalBinding String id);
          """,
          List.of(),
          List.of(),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy"
              + " @com.upstream.LocalBinding on parameter 'id' onto ChildApiHttpExchange, the"
              + " interface Spring proxies: it is not public, so ChildApiHttpExchange cannot name it;"
              + " redeclare 'getUser' on ChildApi as the client should have it."),
      PARAMETER_VALUE_NOT_PUBLIC(
          "a parameter's annotation naming a package-private class",
          """
            @GetExchange("/{id}")
            EitherPath<ApiErr, UserDto> getUser(@PathVariable @Handled(Hidden.class) String id);
          """,
          List.of(),
          List.of(),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Handled"
              + " on parameter 'id' onto ChildApiHttpExchange, the interface Spring proxies:"
              + " com.upstream.Hidden is not public, so ChildApiHttpExchange cannot name it; redeclare"
              + " 'getUser' on ChildApi as the client should have it."),
      PARAMETER_MISSING(
          "a parameter's annotation the classpath lacks",
          """
            @GetExchange("/{id}")
            EitherPath<ApiErr, UserDto> getUser(@PathVariable @Absent String id);
          """,
          List.of("Absent"),
          List.of(),
          "'getUser', inherited from 'BaseApi': @HkjHttpClient cannot copy @com.upstream.Absent"
              + " on parameter 'id' onto ChildApiHttpExchange, the interface Spring proxies: it is"
              + " not on this compilation's classpath; add the dependency that declares it, or"
              + " redeclare 'getUser' on ChildApi as the client should have it.");

      private final String description;
      private final String members;
      private final List<String> deleted;
      private final List<JavaFileObject> replaced;
      private final String message;

      MethodRefusal(
          String description,
          String members,
          List<String> deleted,
          List<JavaFileObject> replaced,
          String message) {
        this.description = description;
        this.members = members;
        this.deleted = deleted;
        this.replaced = replaced;
        this.message = message;
      }

      @Override
      public String toString() {
        return description;
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MethodRefusal.class)
    @DisplayName("a method's or parameter's annotation the client cannot write is refused")
    void aMethodAnnotationTheClientCannotWriteIsRefused(MethodRefusal refusal) throws IOException {
      Path base = baseWith(refusal.members);
      for (JavaFileObject version : refusal.replaced) {
        layOut(compileAlone(version), base);
      }
      for (String type : refusal.deleted) {
        Files.delete(base.resolve("com/upstream/" + type + ".class"));
      }
      Compilation compilation = compiler(base).compile(CHILD);

      // One error, on the client, in place of errors inside generated code.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining(refusal.message).inFile(CHILD);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("one written against another version that javac takes as written is copied")
    void aValueJavacTakesAsWrittenIsCopied() throws IOException {
      // An int where the classpath's member is now a long, an int and a char that fit where it is
      // now a byte or a short, and a single string where it now takes an array of them, are
      // written as javac takes them in source, so the copy compiles against the version here, and
      // means what it meant.
      Path base =
          baseWith(
              """
                @GetExchange("/{id}")
                @Widened(n = 2, text = "t", small = 3, letter = 'c')
                EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
              """);
      layOut(
          compileAlone(
              annotationType(
                  "Widened",
                  "public @interface Widened { long n(); String[] text(); byte small();"
                      + " short letter(); }")),
          base);
      Compilation compilation = compiler(base).compile(CHILD);

      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .containsPattern(
              "@Widened\\(\\s*n = 2,\\s*text = \"t\",\\s*small = 3,\\s*letter = 'c'\\s*\\)");
    }

    @Test
    @DisplayName("one whose type the classpath has as a class, not an annotation, is left off")
    void anAnnotationWhoseTypeIsNoLongerOneIsLeftOff() throws IOException {
      // javac keeps a marker annotation whose type is now a class, and Spring cannot read a class
      // as an annotation, so the copy leaves it off in place of writing what would not compile.
      Path base =
          baseWith(
              """
                @GetExchange("/{id}")
                @Absent
                EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
              """);
      layOut(
          compileAlone(
              JavaFileObjects.forSourceString(
                  "com.upstream.Absent", "package com.upstream; public class Absent {}")),
          base);
      Compilation compilation = compiler(base).compile(CHILD);

      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .doesNotContain("Absent");
    }

    @Test
    @DisplayName("one left off plays no part in whether two declarations of a method agree")
    void aLeftOffAnnotationDoesNotSplitTwoDeclarations() throws IOException {
      // The client implements getUser once for BaseApi's and LeftApi's, which differ only in a
      // CLASS-retained annotation the classpath lacks, which the generated client leaves off.
      Path base =
          baseWith(
              GET_USER,
              JavaFileObjects.forSourceString(
                  "com.upstream.LeftApi",
                  """
                  package com.upstream;

                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.springframework.web.bind.annotation.PathVariable;
                  import org.springframework.web.service.annotation.GetExchange;

                  public interface LeftApi {
                    @GetExchange("/{id}")
                    @ClassOnly
                    EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
                  }
                  """));
      Files.delete(base.resolve("com/upstream/ClassOnly.class"));
      Compilation compilation =
          compiler(base)
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.downstream.ChildApi",
                      """
                      package com.downstream;

                      import com.upstream.BaseApi;
                      import com.upstream.LeftApi;
                      import org.higherkindedj.spring.client.HkjHttpClient;
                      import org.springframework.web.service.annotation.HttpExchange;

                      @HttpExchange("/users")
                      @HkjHttpClient
                      public interface ChildApi extends BaseApi, LeftApi {}
                      """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("one left off that shares a simple name with one copied plays no part either")
    void aLeftOffAnnotationSharingASimpleNameDoesNotSplitTwoDeclarations() throws IOException {
      // LeftApi's getUser also carries a CLASS-retained com.upstream.GetExchange, and its parameter
      // a com.upstream.PathVariable, both missing and left off. Only what the client copies names
      // a part, so Spring's two are named alike on both declarations.
      Path base =
          baseWith(
              GET_USER,
              classOnly("GetExchange"),
              classOnly("PathVariable"),
              JavaFileObjects.forSourceString(
                  "com.upstream.LeftApi",
                  """
                  package com.upstream;

                  import org.higherkindedj.hkt.effect.EitherPath;
                  import org.springframework.web.bind.annotation.PathVariable;
                  import org.springframework.web.service.annotation.GetExchange;

                  public interface LeftApi {
                    @GetExchange("/{id}")
                    @com.upstream.GetExchange
                    EitherPath<ApiErr, UserDto> getUser(
                        @PathVariable @com.upstream.PathVariable String id);
                  }
                  """));
      Files.delete(base.resolve("com/upstream/GetExchange.class"));
      Files.delete(base.resolve("com/upstream/PathVariable.class"));
      Compilation compilation =
          compiler(base)
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.downstream.ChildApi",
                      """
                      package com.downstream;

                      import com.upstream.BaseApi;
                      import com.upstream.LeftApi;
                      import org.higherkindedj.spring.client.HkjHttpClient;
                      import org.springframework.web.service.annotation.HttpExchange;

                      @HttpExchange("/users")
                      @HkjHttpClient
                      public interface ChildApi extends BaseApi, LeftApi {}
                      """));

      assertThat(compilation).succeeded();
    }

    /** A CLASS-retained annotation type in the base's package, as a tooling jar provides. */
    private static JavaFileObject classOnly(String name) {
      return JavaFileObjects.forSourceString(
          "com.upstream." + name,
          "package com.upstream; @java.lang.annotation.Retention("
              + "java.lang.annotation.RetentionPolicy.CLASS) public @interface "
              + name
              + " {}");
    }

    @Test
    @DisplayName("one the client carries itself that it cannot look into is copied as it stands")
    void theClientsOwnUnreadableAnnotationIsCopiedBesideABasesExchange() throws IOException {
      // The client's own annotations are all copied, so whatever Spring reads through @Wrapped it
      // reads on the generated interface too. Should that be an @HttpExchange, Spring refuses the
      // two at startup, as it refuses any two that differ; the processor need not.
      Path base = baseWith(GET_USER, usersApi("@HttpExchange(\"/users\")"));
      Files.delete(base.resolve("com/upstream/AbsentExchange.class"));
      Compilation compilation = compiler(base).compile(usersChild("@com.upstream.Wrapped"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .containsPattern("@Wrapped\\s+@HttpExchange\\(\"/users\"\\)");
    }

    @Test
    @DisplayName("a missing one its class file keeps for the compiler alone is left off a method")
    void aMissingClassRetainedAnnotationIsLeftOffAMethod() throws IOException {
      // Spring never reads a CLASS-retained annotation, as a compileOnly tooling jar provides on a
      // method or a parameter, so leaving it off changes nothing the client sends.
      Path base =
          baseWith(
              """
                @GetExchange("/{id}")
                @ClassOnly
                EitherPath<ApiErr, UserDto> getUser(@PathVariable @ClassOnly String id);
              """);
      Files.delete(base.resolve("com/upstream/ClassOnly.class"));
      Compilation compilation = compiler(base).compile(CHILD);

      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .contains("ResponseEntity<UserDto> getUser(@PathVariable(\"id\") String id)")
          .doesNotContain("ClassOnly");
    }

    @Test
    @DisplayName("a deprecated one is copied with its warning suppressed")
    void aDeprecatedAnnotationIsCopiedWithItsWarningSuppressed() throws IOException {
      // Spring 7 deprecates its own @Nullable, so a base written against Spring 6 carries one on
      // each optional parameter. A deprecated member counts too, as javac reports one where it is
      // set, and so does a deprecation written as a javadoc tag, which javac keeps in the class
      // file.
      Path base =
          baseWith(
              """
                @GetExchange("/{id}")
                @Retired
                EitherPath<ApiErr, UserDto> getUser(
                    @PathVariable @org.springframework.lang.Nullable @Doomed String id);

                @GetExchange("/{id}/profile")
                @Versioned(since = "1")
                EitherPath<ApiErr, UserDto> getProfile(@PathVariable String id);

                @GetExchange("/{id}/settings")
                @Legacy
                EitherPath<ApiErr, UserDto> getSettings(@PathVariable String id);
              """,
              usersApi("@OldExchange"));
      List<String> lint = List.of("-Xlint:deprecation", "-Xlint:removal");
      Compilation compilation = compiler(base).withOptions(lint).compile(usersChild(""));

      assertThat(compilation).succeeded();
      assertThat(compilation).hadWarningCount(0);
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .containsPattern("@OldExchange\\s+@SuppressWarnings\\(\"deprecation\"\\)\\s+public")
          .containsPattern(
              "@GetExchange\\(\"/\\{id}\"\\)\\s+@Retired\\s+@SuppressWarnings\\(\"deprecation\"\\)")
          .containsPattern(
              "@Versioned\\(\\s*since = \"1\"\\s*\\)\\s+@SuppressWarnings\\(\"deprecation\"\\)")
          .containsPattern("@Legacy\\s+@SuppressWarnings\\(\"deprecation\"\\)")
          .containsPattern(
              "@PathVariable\\(\"id\"\\) @Nullable @Doomed"
                  + " @SuppressWarnings\\(\\{\\s*\"deprecation\",\\s*\"removal\"\\s*}\\) String id");

      // The control: the same annotations, written in the client's own package, draw the warnings
      // the generated interface must not.
      Compilation written =
          compiler(base)
              .withOptions(lint)
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.downstream.Written",
                      """
                      package com.downstream;

                      @com.upstream.OldExchange
                      interface Written {
                        @com.upstream.Retired
                        void get(@com.upstream.Doomed String id);

                        @com.upstream.Versioned(since = "1")
                        void getProfile();

                        @com.upstream.Legacy
                        void getSettings();
                      }
                      """));
      assertThat(written).hadWarningContaining("OldExchange in com.upstream has been deprecated");
      assertThat(written).hadWarningContaining("Retired in com.upstream has been deprecated");
      assertThat(written)
          .hadWarningContaining(
              "Doomed in com.upstream has been deprecated and marked for removal");
      assertThat(written)
          .hadWarningContaining("since() in com.upstream.Versioned has been deprecated");
      assertThat(written).hadWarningContaining("Legacy in com.upstream has been deprecated");
    }

    @Test
    @DisplayName("a base's @HttpExchange is copied past another annotation the classpath lacks")
    void aMissingAnnotationBesideAnHttpExchangeIsPassedOver() throws IOException {
      Path base = baseWith(GET_USER, usersApi("@Absent @HttpExchange(\"/users\")"));
      Files.delete(base.resolve("com/upstream/Absent.class"));
      Compilation compilation = compiler(base).compile(usersChild(""));

      // Spring reads UsersApi's @HttpExchange whatever else UsersApi carries, so the search ends
      // there, and the missing annotation, which is not copied, does not matter.
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .contains("@HttpExchange(\"/users\")")
          .doesNotContain("Absent");
    }

    @Test
    @DisplayName("a missing annotation its class file keeps for the compiler alone is passed over")
    void aMissingClassRetainedAnnotationIsPassedOver() throws IOException {
      // A CLASS-retained annotation, as a compileOnly tooling jar provides, is in UsersApi's class
      // file for the compiler alone, so Spring never reads it, and the search goes on to the next
      // base, as Spring's does.
      Path base = baseWith(GET_USER, usersApi("@ClassOnly"));
      Files.delete(base.resolve("com/upstream/ClassOnly.class"));
      Compilation compilation = compiler(base).compile(usersChild("", "com.upstream.ExchangeApi"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ChildApiHttpExchange"))
          .contains("@HttpExchange(\"/users\")")
          .doesNotContain("ClassOnly");
    }
  }

  @Nested
  @DisplayName(
      "a deprecated method or type a jar's base declares, restated with its warning"
          + " suppressed")
  class DeprecatedSignatureSuppression {

    @ParameterizedTest(name = "{0}")
    @EnumSource(DeprecatedSignatures.Shape.class)
    @DisplayName("each compiles under every lint and -Werror")
    void compilesClean(DeprecatedSignatures.Shape shape) throws IOException {
      // The base module is compiled on its own, where javac reads its deprecations from the class
      // files, a javadoc tag's included.
      Compilation base =
          javac()
              .withProcessors()
              .withOptions("-parameters")
              .withClasspath(TEST_CLASSPATH)
              .compile(shape.baseSources());
      assertThat(base).succeeded();
      Path dir = Files.createTempDirectory(tmp, "base");
      layOut(base, dir);
      Compilation compilation =
          compiler(dir).withOptions(DeprecatedSignatures.LINT).compile(shape.client());

      DeprecatedSignatures.assertCompiledClean(shape, compilation);
    }
  }

  @Test
  @DisplayName("an inherited method with an unsupported return type is reported on the client")
  void inheritedUnsupportedReturnIsReportedOnTheClient() throws IOException {
    Compilation compilation =
        compiler(
                baseModule(
                    """
                      @GetExchange("/{id}")
                      UserDto getUser(@PathVariable String id);
                    """))
            .compile(CHILD);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "'getUser', inherited from 'BaseApi': Unsupported @HkjHttpClient return type.")
        .inFile(CHILD);
  }

  @Test
  @DisplayName("without a file-object lookup, a missing inherited type falls back to assignability")
  void unsupportedFileObjectLookupFallsBackToTheAssignabilityCheck() throws IOException {
    Path base =
        baseModule(
            """
              @GetExchange("/{id}")
              @OnStatus(value = 404, error = com.upstream.errors.Gone.class)
              EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);
            """);
    Files.delete(base.resolve("com/upstream/errors/Gone.class"));
    Compilation compilation =
        compiler(base)
            .withProcessors(withoutFileObjectLookup(new HkjHttpClientProcessor()))
            .compile(CHILD);

    // Elements.getFileObjectOf is a default method javac overrides and another compiler may not.
    // Without it the processor cannot confirm the method came from a class file, so it reports
    // what it can check rather than abandoning the client on the unsupported operation.
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "'getUser', inherited from 'BaseApi': @OnStatus error type com.upstream.errors.Gone is"
                + " not assignable")
        .inFile(CHILD);
    Assertions.assertThat(compilation.errors())
        .noneMatch(error -> error.getMessage(null).contains("Failed to generate"));
  }

  /**
   * The processor, handed an {@code Elements} whose {@code getFileObjectOf} is unsupported, as the
   * default method is in a compiler that does not implement it. Everything else is javac's own.
   */
  private static Processor withoutFileObjectLookup(Processor delegate) {
    return new Processor() {
      @Override
      public Set<String> getSupportedOptions() {
        return delegate.getSupportedOptions();
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return delegate.getSupportedAnnotationTypes();
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return delegate.getSupportedSourceVersion();
      }

      @Override
      public void init(ProcessingEnvironment env) {
        Elements elements = env.getElementUtils();
        Elements limited =
            proxy(
                Elements.class,
                (proxy, method, args) -> {
                  if (method.getName().equals("getFileObjectOf")) {
                    throw new UnsupportedOperationException();
                  }
                  return invoke(method, elements, args);
                });
        delegate.init(
            proxy(
                ProcessingEnvironment.class,
                (proxy, method, args) ->
                    method.getName().equals("getElementUtils")
                        ? limited
                        : invoke(method, env, args)));
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return delegate.process(annotations, roundEnv);
      }

      @Override
      public Iterable<? extends Completion> getCompletions(
          Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return delegate.getCompletions(element, annotation, member, userText);
      }
    };
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(
        Proxy.newProxyInstance(
            HkjHttpClientProcessorClasspathTest.class.getClassLoader(),
            new Class<?>[] {type},
            handler));
  }

  private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
