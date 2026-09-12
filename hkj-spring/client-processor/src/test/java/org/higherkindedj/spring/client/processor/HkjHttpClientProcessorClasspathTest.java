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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    List<JavaFileObject> sources = new ArrayList<>(BASE_TYPES);
    sources.add(
        JavaFileObjects.forSourceString(
            "com.upstream.BaseApi",
            """
            package com.upstream;

            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.MaybePath;
            import org.higherkindedj.spring.client.OnStatus;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.service.annotation.GetExchange;

            public interface BaseApi {
            %s
            }
            """
                .formatted(members)));
    return sources;
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
   * Compiles the base module and lays its class files out on a directory. It runs no processor, as
   * a shared base interface carries no {@code @HkjHttpClient} of its own, and it passes {@code
   * -parameters}, which the book requires of a base in a jar.
   */
  private Path baseModule(String members) throws IOException {
    Compilation compilation =
        javac()
            .withProcessors()
            .withOptions("-parameters")
            .withClasspath(TEST_CLASSPATH)
            .compile(baseSources(members));
    assertThat(compilation).succeeded();
    Path dir = tmp.resolve("base");
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
        Files.copy(in, target);
      }
    }
    return dir;
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
  @DisplayName("its @OnStatus overrides generate the client a base compiled alongside generates")
  void overridesCrossTheBoundary() throws IOException {
    Compilation across = compiler(baseModule(OVERRIDES)).compile(CHILD);
    assertThat(across).succeeded();
    List<JavaFileObject> sources = new ArrayList<>(baseSources(OVERRIDES));
    sources.add(CHILD);
    Compilation together = compiler().compile(sources);
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
                + " not assignable to the method's declared error type com.upstream.ApiErr.")
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
}
