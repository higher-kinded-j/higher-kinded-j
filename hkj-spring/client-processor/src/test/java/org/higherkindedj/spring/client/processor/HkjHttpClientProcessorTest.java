// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HkjHttpClientProcessor")
class HkjHttpClientProcessorTest {

  private static final JavaFileObject USER_DTO =
      JavaFileObjects.forSourceLines(
          "com.example.UserDto",
          "package com.example;",
          "public record UserDto(String id, String name) {}");

  private static final JavaFileObject USER_ERROR =
      JavaFileObjects.forSourceLines(
          "com.example.UserError",
          "package com.example;",
          "public record UserError(String code, String message) {}");

  private static Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new HkjHttpClientProcessor()).compile(sources);
  }

  private static JavaFileObject userApi(String... methodLines) {
    String[] header = {
      "package com.example;",
      "import org.higherkindedj.hkt.effect.EitherPath;",
      "import org.higherkindedj.hkt.effect.MaybePath;",
      "import org.higherkindedj.hkt.effect.VTaskPath;",
      "import org.higherkindedj.hkt.either.Either;",
      "import org.higherkindedj.spring.client.HkjHttpClient;",
      "import org.springframework.web.bind.annotation.PathVariable;",
      "import org.springframework.web.bind.annotation.RequestBody;",
      "import org.springframework.web.service.annotation.GetExchange;",
      "import org.springframework.web.service.annotation.HttpExchange;",
      "import org.springframework.web.service.annotation.PostExchange;",
      "@HttpExchange(\"/users\")",
      "@HkjHttpClient",
      "public interface UserApi {"
    };
    String[] lines = new String[header.length + methodLines.length + 1];
    System.arraycopy(header, 0, lines, 0, header.length);
    System.arraycopy(methodLines, 0, lines, header.length, methodLines.length);
    lines[lines.length - 1] = "}";
    return JavaFileObjects.forSourceLines("com.example.UserApi", lines);
  }

  @Nested
  @DisplayName("generates a compiling client for the three supported return types")
  class HappyPath {

    private final Compilation compilation =
        compile(
            userApi(
                "  @GetExchange(\"/{id}\")",
                "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
                "  @PostExchange",
                "  VTaskPath<Either<UserError, UserDto>> create(@RequestBody UserDto body);",
                "  @GetExchange(\"/{id}/opt\")",
                "  MaybePath<UserDto> find(@PathVariable String id);"),
            USER_DTO,
            USER_ERROR);

    @Test
    @DisplayName("the generated sources compile against real Spring + HKJ types")
    void succeeds() {
      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.UserApiHttpExchange");
      assertThat(compilation).generatedSourceFile("com.example.UserApiClient");
      assertThat(compilation).generatedSourceFile("com.example.UserApiClientConfiguration");
    }

    @Test
    @DisplayName("the native interface unwraps to ResponseEntity and copies mapping annotations")
    void nativeInterface() {
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("ResponseEntity<UserDto> getUser");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .contains("@GetExchange");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .contains("@HttpExchange");
    }

    @Test
    @DisplayName("parameter binding annotations are preserved on the native interface")
    void parameterAnnotations() {
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("getUser\\(@PathVariable");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("create\\(@RequestBody");
    }

    @Test
    @DisplayName("the facade dispatches each method to the right HkjClientExchange translator")
    void facadeDispatch() {
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("HkjClientExchange.either(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("HkjClientExchange.eitherVTask(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("HkjClientExchange.maybe(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("decoderFactory.create(UserError.class)");
    }

    @Test
    @DisplayName("the configuration registers an @ImportHttpServices group and a client bean")
    void configuration() {
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClientConfiguration")
          .contentsAsUtf8String()
          .contains("@ImportHttpServices");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClientConfiguration")
          .contentsAsUtf8String()
          .contains("UserApiHttpExchange.class");
    }
  }

  @Nested
  @DisplayName("copies arbitrary mapping-annotation attributes through faithfully")
  class CopyThrough {

    @Test
    @DisplayName("multi-attribute exchange annotations are reproduced on the native interface")
    void multiAttribute() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(url = \"/{id}\", accept = \"application/json\")",
                  "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .contains("accept = \"application/json\"");
    }
  }

  @Nested
  @DisplayName("@OnStatus per-status error overrides")
  class OnStatusOverrides {

    private static final JavaFileObject API_ERR =
        JavaFileObjects.forSourceLines(
            "com.example.ApiErr",
            "package com.example;",
            "public sealed interface ApiErr permits NotFound, Conflict, Generic {}");
    private static final JavaFileObject NOT_FOUND =
        JavaFileObjects.forSourceLines(
            "com.example.NotFound",
            "package com.example;",
            "public record NotFound(String message) implements ApiErr {}");
    private static final JavaFileObject CONFLICT =
        JavaFileObjects.forSourceLines(
            "com.example.Conflict",
            "package com.example;",
            "public record Conflict(String message) implements ApiErr {}");
    private static final JavaFileObject GENERIC =
        JavaFileObjects.forSourceLines(
            "com.example.Generic",
            "package com.example;",
            "public record Generic(String code, String message) implements ApiErr {}");

    private JavaFileObject api(String... methodLines) {
      String[] header = {
        "package com.example;",
        "import org.higherkindedj.hkt.effect.EitherPath;",
        "import org.higherkindedj.spring.client.HkjHttpClient;",
        "import org.higherkindedj.spring.client.OnStatus;",
        "import org.springframework.web.bind.annotation.PathVariable;",
        "import org.springframework.web.service.annotation.GetExchange;",
        "import org.springframework.web.service.annotation.HttpExchange;",
        "@HttpExchange(\"/users\")",
        "@HkjHttpClient",
        "public interface OsApi {"
      };
      String[] lines = new String[header.length + methodLines.length + 1];
      System.arraycopy(header, 0, lines, 0, header.length);
      System.arraycopy(methodLines, 0, lines, header.length, methodLines.length);
      lines[lines.length - 1] = "}";
      return JavaFileObjects.forSourceLines("com.example.OsApi", lines);
    }

    @Test
    @DisplayName("generates a status-dispatching decoder for mapped statuses")
    void dispatchingDecoder() {
      Compilation compilation =
          compile(
              api(
                  "  @GetExchange(\"/{id}\")",
                  "  @OnStatus(value = 404, error = NotFound.class)",
                  "  @OnStatus(value = 409, error = Conflict.class)",
                  "  EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              API_ERR,
              NOT_FOUND,
              CONFLICT,
              GENERIC);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.OsApiClient")
          .contentsAsUtf8String()
          .contains("ResponseErrorDecoders.<ApiErr>forDefault(this.decoderFactory, ApiErr.class)");
      assertThat(compilation)
          .generatedSourceFile("com.example.OsApiClient")
          .contentsAsUtf8String()
          .contains(".on(404, NotFound.class)");
      assertThat(compilation)
          .generatedSourceFile("com.example.OsApiClient")
          .contentsAsUtf8String()
          .contains(".on(409, Conflict.class)");
      // @OnStatus must not leak onto the native interface.
      assertThat(compilation)
          .generatedSourceFile("com.example.OsApiHttpExchange")
          .contentsAsUtf8String()
          .doesNotContain("@OnStatus");
    }

    @Test
    @DisplayName("errors when an override type is not assignable to the declared error type")
    void incompatibleOverride() {
      Compilation compilation =
          compile(
              api(
                  "  @GetExchange(\"/{id}\")",
                  "  @OnStatus(value = 404, error = UserDto.class)",
                  "  EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              API_ERR,
              NOT_FOUND,
              CONFLICT,
              GENERIC);

      assertThat(compilation).hadErrorContaining("not assignable");
    }

    @Test
    @DisplayName("warns when @OnStatus is placed on a MaybePath method")
    void onStatusOnMaybeWarns() {
      Compilation compilation =
          compile(
              api(
                  "  @GetExchange(\"/{id}\")",
                  "  @OnStatus(value = 404, error = NotFound.class)",
                  "  org.higherkindedj.hkt.effect.MaybePath<UserDto> find(@PathVariable String id);"),
              USER_DTO,
              API_ERR,
              NOT_FOUND,
              CONFLICT,
              GENERIC);

      assertThat(compilation).succeeded();
      assertThat(compilation).hadWarningContaining("no effect on a MaybePath");
    }

    @Test
    @DisplayName("errors on an array @OnStatus error type instead of crashing")
    void arrayOverrideType() {
      Compilation compilation =
          compile(
              api(
                  "  @GetExchange(\"/{id}\")",
                  "  @OnStatus(value = 404, error = UserDto[].class)",
                  "  EitherPath<Object, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              API_ERR,
              NOT_FOUND,
              CONFLICT,
              GENERIC);

      assertThat(compilation).hadErrorContaining("concrete, non-generic");
    }

    @Test
    @DisplayName("warns on a duplicate @OnStatus for the same status")
    void duplicateStatusWarns() {
      Compilation compilation =
          compile(
              api(
                  "  @GetExchange(\"/{id}\")",
                  "  @OnStatus(value = 404, error = NotFound.class)",
                  "  @OnStatus(value = 404, error = Conflict.class)",
                  "  EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              API_ERR,
              NOT_FOUND,
              CONFLICT,
              GENERIC);

      assertThat(compilation).hadWarningContaining("Duplicate @OnStatus");
    }
  }

  @Nested
  @DisplayName("generates inherited abstract @HttpExchange methods")
  class Inheritance {

    @Test
    @DisplayName("a client extending a base interface generates the inherited methods")
    void inheritedMethods() {
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.BaseApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface BaseApi {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
                  "}"),
              JavaFileObjects.forSourceLines(
                  "com.example.ChildApi",
                  "package com.example;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/users\")",
                  "@HkjHttpClient",
                  "public interface ChildApi extends BaseApi {}"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.ChildApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("ResponseEntity<UserDto> getUser");
    }

    @Test
    @DisplayName("a method redeclared with @Override generates a compiling native interface")
    void redeclaredWithOverride() {
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.BaseApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface BaseApi {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
                  "}"),
              JavaFileObjects.forSourceLines(
                  "com.example.ChildApi",
                  "package com.example;",
                  "import java.lang.Override;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/users\")",
                  "@HkjHttpClient",
                  "public interface ChildApi extends BaseApi {",
                  "  @Override @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
                  "}"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
      // @Override must not be copied onto the (super-interface-less) native interface.
      assertThat(compilation)
          .generatedSourceFile("com.example.ChildApiHttpExchange")
          .contentsAsUtf8String()
          .doesNotContain("@Override");
    }

    @Test
    @DisplayName("a diamond of two interfaces declaring the same method compiles (no duplicate)")
    void diamondInheritance() {
      JavaFileObject method =
          JavaFileObjects.forSourceLines(
              "com.example.AApi",
              "package com.example;",
              "import org.higherkindedj.hkt.effect.EitherPath;",
              "import org.springframework.web.bind.annotation.PathVariable;",
              "import org.springframework.web.service.annotation.GetExchange;",
              "public interface AApi {",
              "  @GetExchange(\"/{id}\")",
              "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
              "}");
      JavaFileObject bApi =
          JavaFileObjects.forSourceLines(
              "com.example.BApi",
              "package com.example;",
              "import org.higherkindedj.hkt.effect.EitherPath;",
              "import org.springframework.web.bind.annotation.PathVariable;",
              "import org.springframework.web.service.annotation.GetExchange;",
              "public interface BApi {",
              "  @GetExchange(\"/{id}\")",
              "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
              "}");

      Compilation compilation =
          compile(
              method,
              bApi,
              JavaFileObjects.forSourceLines(
                  "com.example.DiamondApi",
                  "package com.example;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/users\")",
                  "@HkjHttpClient",
                  "public interface DiamondApi extends AApi, BApi {}"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("handles parameter names that shadow generated fields")
  class ParameterNameShadowing {

    @Test
    @DisplayName("a parameter named http or decoderFactory does not shadow the facade fields")
    void shadowingParams() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/{http}\")",
                  "  EitherPath<UserError, UserDto> a(@PathVariable String http);",
                  "  @GetExchange(\"/df/{decoderFactory}\")",
                  "  EitherPath<UserError, UserDto> b(@PathVariable String decoderFactory);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("this.http.a(http)");
    }
  }

  @Nested
  @DisplayName("naming and determinism")
  class Conventions {

    @Test
    @DisplayName("derives the group name with JavaBeans rules (leading acronym unchanged)")
    void acronymGroupName() {
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.URLClientApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/x\")",
                  "@HkjHttpClient",
                  "public interface URLClientApi {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, UserDto> get(@PathVariable String id);",
                  "}"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.URLClientApiClientConfiguration")
          .contentsAsUtf8String()
          .contains("group = \"URLClientApi\"");
    }

    @Test
    @DisplayName("generates methods in a deterministic (sorted) order")
    void deterministicMethodOrder() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/z/{id}\")",
                  "  EitherPath<UserError, UserDto> zulu(@PathVariable String id);",
                  "  @GetExchange(\"/a/{id}\")",
                  "  EitherPath<UserError, UserDto> alpha(@PathVariable String id);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).succeeded();
      // Sorted by erased signature: alpha(...) before zulu(...) regardless of declaration order.
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("(?s)\\balpha\\b.*\\bzulu\\b");
    }
  }

  @Nested
  @DisplayName("supports generic interfaces (codegen-only)")
  class Generics {

    private final Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "com.example.Repo",
                "package com.example;",
                "import org.higherkindedj.hkt.effect.EitherPath;",
                "import org.higherkindedj.spring.client.HkjHttpClient;",
                "import org.springframework.web.bind.annotation.PathVariable;",
                "import org.springframework.web.service.annotation.GetExchange;",
                "import org.springframework.web.service.annotation.HttpExchange;",
                "@HttpExchange(\"/items\")",
                "@HkjHttpClient",
                "public interface Repo<T> {",
                "  @GetExchange(\"/{id}\")",
                "  EitherPath<UserError, T> get(@PathVariable String id);",
                "}"),
            USER_ERROR);

    @Test
    @DisplayName("native interface and facade carry the type parameter and compile")
    void carriesTypeParameter() {
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.RepoHttpExchange")
          .contentsAsUtf8String()
          .contains("interface RepoHttpExchange<T>");
      assertThat(compilation)
          .generatedSourceFile("com.example.RepoClient")
          .contentsAsUtf8String()
          .containsMatch("class RepoClient<T> implements Repo<T>");
    }

    @Test
    @DisplayName("no @Configuration is generated for a generic interface")
    void noConfiguration() {
      boolean hasConfig =
          compilation.generatedSourceFiles().stream()
              .anyMatch(f -> f.getName().contains("RepoClientConfiguration"));
      org.assertj.core.api.Assertions.assertThat(hasConfig).isFalse();
    }

    @Test
    @DisplayName("errors when the error type is a type variable")
    void errorTypeAsTypeVariable() {
      Compilation badCompilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.BadRepo",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/x\")",
                  "@HkjHttpClient",
                  "public interface BadRepo<E> {",
                  "  @GetExchange(\"/x\")",
                  "  EitherPath<E, UserDto> get();",
                  "}"),
              USER_DTO);

      assertThat(badCompilation).hadErrorContaining("type variable");
    }
  }

  @Nested
  @DisplayName("rejects invalid usage")
  class Validation {

    @Test
    @DisplayName("errors when applied to a class")
    void notAnInterface() {
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.NotIface",
                  "package com.example;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "@HkjHttpClient public class NotIface {}"));

      assertThat(compilation).hadErrorContaining("interfaces");
    }

    @Test
    @DisplayName("errors on an unsupported return type")
    void unsupportedReturn() {
      Compilation compilation =
          compile(
              userApi("  @GetExchange(\"/x\")", "  String wrong(@PathVariable String id);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).hadErrorContaining("Unsupported");
    }

    @Test
    @DisplayName("points VStreamPath returns at the manual SSE translator")
    void vstreamPathHint() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/stream\")",
                  "  org.higherkindedj.hkt.effect.VStreamPath<UserDto> stream();"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).hadErrorContaining("HkjClientExchange.vstream");
    }

    @Test
    @DisplayName("errors on an array error type instead of crashing")
    void arrayErrorType() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError[], UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).hadErrorContaining("concrete, non-generic");
    }

    @Test
    @DisplayName("errors on a parameterized error type instead of generating uncompilable code")
    void parameterizedErrorType() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<java.util.List<UserError>, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).hadErrorContaining("concrete, non-generic");
    }

    @Test
    @DisplayName("errors when VTaskPath does not wrap an Either")
    void vtaskWithoutEither() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/x\")", "  VTaskPath<UserDto> wrong(@PathVariable String id);"),
              USER_DTO,
              USER_ERROR);

      assertThat(compilation).hadErrorContaining("Unsupported");
    }
  }
}
