// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link HkjHttpClientProcessor} is registered with Gradle as isolating. Gradle then reruns it on a
 * client only when it recompiles the client, and deletes and regenerates exactly the files that
 * name the client as their originating element. Each generated file must therefore name the one
 * client it was generated from, or Gradle recompiles the whole source set on every change.
 */
@DisplayName("HkjHttpClientProcessor - an isolating processor")
class HkjHttpClientProcessorIsolationTest {

  private static final JavaFileObject TYPES =
      JavaFileObjects.forSourceString(
          "com.example.Types",
          """
          package com.example;

          public final class Types {
            public sealed interface ApiErr permits NotFound {}

            public record NotFound(String message) implements ApiErr {}

            public record UserDto(String id) {}
          }
          """);

  private static final JavaFileObject BASE =
      JavaFileObjects.forSourceString(
          "com.example.BaseApi",
          """
          package com.example;

          import org.higherkindedj.hkt.effect.EitherPath;
          import org.springframework.web.bind.annotation.PathVariable;
          import org.springframework.web.service.annotation.GetExchange;

          public interface BaseApi {
            @GetExchange("/{id}/profile")
            EitherPath<Types.ApiErr, Types.UserDto> getProfile(@PathVariable String id);
          }
          """);

  private static final JavaFileObject CLIENT =
      JavaFileObjects.forSourceString(
          "com.example.UserApi",
          """
          package com.example;

          import org.higherkindedj.hkt.effect.EitherPath;
          import org.higherkindedj.spring.client.HkjHttpClient;
          import org.springframework.web.bind.annotation.PathVariable;
          import org.springframework.web.service.annotation.GetExchange;
          import org.springframework.web.service.annotation.HttpExchange;

          @HttpExchange("/users")
          @HkjHttpClient
          public interface UserApi extends BaseApi {
            @GetExchange("/{id}")
            EitherPath<Types.ApiErr, Types.UserDto> getUser(@PathVariable String id);
          }
          """);

  private static final JavaFileObject GENERIC_CLIENT =
      JavaFileObjects.forSourceString(
          "com.example.Repo",
          """
          package com.example;

          import org.higherkindedj.hkt.effect.EitherPath;
          import org.higherkindedj.spring.client.HkjHttpClient;
          import org.springframework.web.bind.annotation.PathVariable;
          import org.springframework.web.service.annotation.GetExchange;
          import org.springframework.web.service.annotation.HttpExchange;

          @HttpExchange("/repo")
          @HkjHttpClient
          public interface Repo<T> {
            @GetExchange("/{id}")
            EitherPath<Types.ApiErr, T> find(@PathVariable String id);
          }
          """);

  @Test
  @DisplayName("is registered with Gradle as isolating")
  void isRegisteredWithGradleAsIsolating() throws IOException {
    final List<URL> registrations =
        Collections.list(
            HkjHttpClientProcessor.class
                .getClassLoader()
                .getResources("META-INF/gradle/incremental.annotation.processors"));
    final StringBuilder lines = new StringBuilder();
    for (URL registration : registrations) {
      try (InputStream in = registration.openStream()) {
        lines.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
      }
    }

    assertThat(lines.toString().lines().map(String::strip))
        .contains(HkjHttpClientProcessor.class.getName() + ",isolating");
  }

  @Test
  @DisplayName("each file it generates names the client, and only the client, as its origin")
  void eachGeneratedFileNamesTheClientAsItsOrigin() {
    final Recording processor = new Recording();

    final Compilation compilation = javac().withProcessors(processor).compile(TYPES, BASE, CLIENT);

    assertThat(compilation).succeeded();
    assertThat(processor.origins)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "com.example.UserApiHttpExchange", List.of("com.example.UserApi"),
                "com.example.UserApiClient", List.of("com.example.UserApi"),
                "com.example.UserApiClientConfiguration", List.of("com.example.UserApi")));
  }

  @Test
  @DisplayName("looks nothing up beyond the client it is handed, and reads no other file")
  void looksNothingUpBeyondTheClient() {
    // An isolating processor may decide only from what its client reaches. A lookup by name could
    // reach another type of the compilation, which Gradle would not recompile with the client.
    final JavaClasses processor =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.higherkindedj.spring.client.processor");

    assertThat(callers(processor, Elements.class, "getTypeElement"))
        .as("name each new lookup's target, and check Gradle recompiles the client when it changes")
        .containsExactlyInAnyOrder(
            "HkjHttpClientProcessor.process",
            "HkjHttpClientProcessor.lookUp",
            "HkjHttpClientProcessor.isMapType",
            // The names the client's own generated classes take, checked before they are written.
            // The answer can only refuse the client, never change what is written for it, so a
            // class Gradle does not recompile with the client is safe to look up here.
            "ClassFiles.replacesUngeneratedClass");
    assertThat(callers(processor, RoundEnvironment.class, "getRootElements")).isEmpty();
    assertThat(callers(processor, Filer.class, "getResource")).isEmpty();
    assertThat(callers(processor, Filer.class, "createResource")).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"UserApiHttpExchange", "UserApiClient", "UserApiClientConfiguration"})
  @DisplayName("refuses each generated class's name a hand-written class file already has")
  void refusesANameAHandWrittenClassFileHas(final String generated, @TempDir final Path unchanged)
      throws IOException {
    // Gradle's incremental build recompiles the client alone, and reads an unchanged hand-written
    // class from its class file, which javac's Filer would write over.
    final Recording processor = new Recording();
    final Compilation compilation =
        javac()
            .withProcessors(processor)
            .withClasspath(classpathWith(handWritten(generated, unchanged)))
            .compile(TYPES, BASE, CLIENT);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@HkjHttpClient: the generated class 'com.example."
                + generated
                + "' has the name of a class on the classpath.")
        .inFile(CLIENT);
    assertThat(compilation).hadErrorCount(1);
    // Refused before any of the client's three files is written.
    assertThat(processor.origins).isEmpty();
  }

  @Test
  @DisplayName("a generic client writes no configuration, so a class of that name is no clash")
  void aGenericClientLeavesTheConfigurationNameFree(@TempDir final Path unchanged)
      throws IOException {
    final Compilation compilation =
        javac()
            .withProcessors(new HkjHttpClientProcessor())
            .withClasspath(classpathWith(handWritten("RepoClientConfiguration", unchanged)))
            .compile(TYPES, GENERIC_CLIENT);

    assertThat(compilation).succeeded();
    assertThat(compilation.generatedSourceFile("com.example.RepoClient")).isPresent();
  }

  @Test
  @DisplayName("writes again the classes a previous build generated, as from source")
  void writesAgainWhatAPreviousBuildGenerated(@TempDir final Path previous) throws IOException {
    final Compilation first =
        javac().withProcessors(new HkjHttpClientProcessor()).compile(TYPES, BASE, CLIENT);
    assertThat(first).succeeded();

    final Compilation again =
        javac()
            .withProcessors(new HkjHttpClientProcessor())
            .withClasspath(classpathWith(layOut(first, previous)))
            .compile(TYPES, BASE, CLIENT);

    assertThat(again).succeeded();
    assertThat(again.generatedSourceFile("com.example.UserApiClient")).isPresent();
  }

  /** A hand-written class {@code com.example.<simpleName>}, its class file laid out under dir. */
  private static Path handWritten(final String simpleName, final Path dir) throws IOException {
    final Compilation handWritten =
        javac()
            .withProcessors()
            .compile(
                JavaFileObjects.forSourceString(
                    "com.example." + simpleName,
                    "package com.example; public class " + simpleName + " {}"));
    assertThat(handWritten).succeeded();
    return layOut(handWritten, dir);
  }

  /** The test classpath, with {@code dir} in front. */
  private static List<File> classpathWith(final Path dir) {
    final List<File> classpath = new ArrayList<>(List.of(dir.toFile()));
    Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
        .map(File::new)
        .forEach(classpath::add);
    return classpath;
  }

  /** Lays a compilation's class files out under {@code dir}, as a jar or a build's output has. */
  private static Path layOut(final Compilation compilation, final Path dir) throws IOException {
    for (final JavaFileObject file : compilation.generatedFiles()) {
      if (file.getKind() != JavaFileObject.Kind.CLASS) {
        continue;
      }
      // /CLASS_OUTPUT/com/example/UserApiClient.class -> com/example/UserApiClient.class
      final String path = file.getName();
      final String marker = StandardLocation.CLASS_OUTPUT.getName() + "/";
      final Path target = dir.resolve(path.substring(path.indexOf(marker) + marker.length()));
      Files.createDirectories(target.getParent());
      try (InputStream in = file.openInputStream()) {
        Files.copy(in, target);
      }
    }
    return dir;
  }

  /** Each method of {@code classes} that calls {@code owner.name}, as {@code Class.method}. */
  private static Set<String> callers(
      final JavaClasses classes, final Class<?> owner, final String name) {
    return classes.stream()
        .flatMap(type -> type.getMethodCallsFromSelf().stream())
        .filter(call -> call.getTarget().getOwner().isAssignableTo(owner))
        .filter(call -> call.getTarget().getName().equals(name))
        .map(call -> call.getOriginOwner().getSimpleName() + "." + call.getOrigin().getName())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The client processor, recording the originating elements each source it writes names. */
  private static final class Recording extends HkjHttpClientProcessor {

    private final Map<String, List<String>> origins = new TreeMap<>();

    /** The client processor's, as its {@code @SupportedAnnotationTypes} is not inherited. */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return new HkjHttpClientProcessor().getSupportedAnnotationTypes();
    }

    @Override
    public synchronized void init(final ProcessingEnvironment env) {
      final Filer filer = env.getFiler();
      final Filer recording =
          proxy(
              Filer.class,
              (method, arguments) -> {
                if (method.getName().equals("createSourceFile")) {
                  origins.put(
                      arguments[0].toString(),
                      Arrays.stream((Element[]) arguments[1])
                          .map(origin -> ((TypeElement) origin).getQualifiedName().toString())
                          .toList());
                }
                return method.invoke(filer, arguments);
              });
      super.init(
          proxy(
              ProcessingEnvironment.class,
              (method, arguments) ->
                  method.getName().equals("getFiler") ? recording : method.invoke(env, arguments)));
    }
  }

  /** A call a proxy forwards, the target's own exceptions unwrapped. */
  @FunctionalInterface
  private interface Forward {
    Object call(Method method, Object[] arguments) throws ReflectiveOperationException;
  }

  private static <T> T proxy(final Class<T> type, final Forward forward) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (_, method, arguments) -> {
              try {
                return forward.call(method, arguments);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            }));
  }
}
