// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

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
    @DisplayName("parameter binding annotations are preserved with explicit names injected")
    void parameterAnnotations() {
      // The explicit "id" name must be injected so the generated interface binds without the
      // consuming build needing -parameters.
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .contains("@PathVariable(\"id\")");
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

    @Test
    @DisplayName("the configuration declares the constructor Spring instantiates it through")
    void configurationConstructor() {
      // A public class that declares no constructor exposes the default one, which
      // -Xlint:missing-explicit-ctor reports in a named module exporting its package.
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClientConfiguration")
          .contentsAsUtf8String()
          .contains("public UserApiClientConfiguration()");
    }
  }

  @Nested
  @DisplayName("an endpoint that sends no body")
  class NoBody {

    @Test
    @DisplayName("Unit ignores the body: the native method returns ResponseEntity<Void>")
    void unitIgnoresTheBody() {
      Compilation compilation =
          compile(
              userApi(
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}\")",
                  "  EitherPath<UserError, org.higherkindedj.hkt.Unit> delete(@PathVariable String id);",
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}/later\")",
                  "  VTaskPath<Either<UserError, org.higherkindedj.hkt.Unit>> deleteLater(",
                  "      @PathVariable String id);",
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}/maybe\")",
                  "  MaybePath<org.higherkindedj.hkt.Unit> deleteIfThere(@PathVariable String id);"),
              USER_ERROR);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("ResponseEntity<Void> delete\\(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("HkjClientExchange.eitherUnit(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("HkjClientExchange.eitherVTaskUnit(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("HkjClientExchange.maybeUnit(");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("ResponseEntity<Void> deleteIfThere\\(");
    }

    @Test
    @DisplayName("Void is refused, with a fix line naming Unit")
    void voidIsRefused() {
      Compilation compilation =
          compile(
              userApi(
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}\")",
                  "  EitherPath<UserError, Void> delete(@PathVariable String id);"),
              USER_ERROR);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@HkjHttpClient success type Void cannot hold a value, but a success always holds"
                  + " one. Replace Void with org.higherkindedj.hkt.Unit");
    }

    @Test
    @DisplayName("Void is refused on a MaybePath too, where every 2xx would read as Nothing")
    void voidIsRefusedOnAMaybePath() {
      Compilation compilation =
          compile(
              userApi(
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}\")",
                  "  MaybePath<Void> deleteIfThere(@PathVariable String id);"),
              USER_ERROR);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Replace Void with org.higherkindedj.hkt.Unit");
    }

    @Test
    @DisplayName("a wildcard bounded by Void is refused, on each Path, since only Void fits it")
    void voidBehindAWildcardIsRefused() {
      Compilation compilation =
          compile(
              userApi(
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}\")",
                  "  EitherPath<UserError, ? extends Void> delete(@PathVariable String id);",
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}/later\")",
                  "  VTaskPath<Either<UserError, ? extends Void>> deleteLater(@PathVariable String id);",
                  "  @org.springframework.web.service.annotation.DeleteExchange(\"/{id}/maybe\")",
                  "  MaybePath<? extends Void> deleteIfThere(@PathVariable String id);"),
              USER_ERROR);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(3);
      assertThat(compilation).hadErrorContaining("Replace Void with org.higherkindedj.hkt.Unit");
    }

    @Test
    @DisplayName("a type variable bounded by Void is refused on a generic client")
    void voidBoundingATypeVariableIsRefused() {
      Compilation compilation = compile(repo("T extends Void"), USER_ERROR);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Replace Void with org.higherkindedj.hkt.Unit");
    }

    @Test
    @DisplayName("a type variable whose bounds include Void is refused")
    void voidInAnIntersectionBoundIsRefused() {
      Compilation compilation = compile(repo("T extends Void & java.io.Serializable"), USER_ERROR);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Replace Void with org.higherkindedj.hkt.Unit");
    }

    @Test
    @DisplayName("a wildcard or type variable that other types fit is not read as Void")
    void otherBoundsAreNotVoid() {
      Compilation unbounded =
          compile(
              userApi("  @GetExchange(\"/{id}\")", "  MaybePath<?> find(@PathVariable String id);"),
              USER_ERROR);
      Compilation numeric = compile(repo("T extends Number"), USER_ERROR);

      assertThat(unbounded).succeeded();
      assertThat(numeric).succeeded();
    }

    private static JavaFileObject repo(String typeParameter) {
      return JavaFileObjects.forSourceLines(
          "com.example.Repo",
          "package com.example;",
          "import org.higherkindedj.hkt.effect.EitherPath;",
          "import org.higherkindedj.spring.client.HkjHttpClient;",
          "import org.springframework.web.bind.annotation.PathVariable;",
          "import org.springframework.web.service.annotation.DeleteExchange;",
          "import org.springframework.web.service.annotation.HttpExchange;",
          "@HttpExchange(\"/repo\")",
          "@HkjHttpClient",
          "public interface Repo<" + typeParameter + "> {",
          "  @DeleteExchange(\"/{id}\")",
          "  EitherPath<UserError, T> delete(@PathVariable String id);",
          "}");
    }
  }

  @Nested
  @DisplayName("an endpoint whose errors carry nothing")
  class NoErrorBody {

    private static final String VOID_ERROR =
        "@HkjHttpClient error type Void cannot hold a value, but a Left always holds one. Replace"
            + " Void with org.higherkindedj.hkt.Unit: the default decoder then reads no error body,"
            + " and every error response gives Left(Unit.INSTANCE).";

    @Test
    @DisplayName("Unit is decoded by its own class, declared or named by an @OnStatus")
    void unitErrorGenerates() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<org.higherkindedj.hkt.Unit, UserDto> get(@PathVariable String id);",
                  "  @GetExchange(\"/{id}/later\")",
                  "  VTaskPath<Either<org.higherkindedj.hkt.Unit, UserDto>> getLater(",
                  "      @PathVariable String id);",
                  "  @GetExchange(\"/{id}/any\")",
                  "  @org.higherkindedj.spring.client.OnStatus(",
                  "      value = 404, error = org.higherkindedj.hkt.Unit.class)",
                  "  EitherPath<Object, UserDto> getAny(@PathVariable String id);"),
              USER_DTO);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("this.getDecoder = decoderFactory.create(Unit.class)");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("this.getLaterDecoder = decoderFactory.create(Unit.class)");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains(".on(404, Unit.class)");
    }

    @Test
    @DisplayName("a class of your own named Void is not java.lang.Void, and generates")
    void ownVoidGenerates() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<Void, UserDto> get(@PathVariable String id);"),
              USER_DTO,
              JavaFileObjects.forSourceLines(
                  "com.example.Void",
                  "package com.example;",
                  "public record Void(String code) {}"));

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiClient")
          .contentsAsUtf8String()
          .contains("decoderFactory.create(Void.class)");
    }

    @Test
    @DisplayName("a Void error is refused, with a fix line naming Unit")
    void voidErrorIsRefused() {
      JavaFileObject source =
          userApi(
              "  @GetExchange(\"/{id}\")",
              "  EitherPath<Void, UserDto> get(@PathVariable String id);");
      Compilation compilation = compile(source, USER_DTO);

      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(VOID_ERROR)
          .inFile(source)
          .onLineContaining("get(");
    }

    @Test
    @DisplayName("a VTaskPath with Void on both sides hears about each")
    void voidOnBothSidesOfAVTaskPath() {
      Compilation compilation =
          compile(
              userApi(
                  "  @GetExchange(\"/{id}\")",
                  "  VTaskPath<Either<Void, Void>> delete(@PathVariable String id);"));

      assertThat(compilation).hadErrorCount(2);
      assertThat(compilation).hadErrorContaining(VOID_ERROR);
      assertThat(compilation).hadErrorContaining("@HkjHttpClient success type Void");
    }

    @Test
    @DisplayName("a Void @OnStatus is refused, naming Unit only where a Unit would be assignable")
    void voidOnStatusIsRefused() {
      JavaFileObject source =
          userApi(
              "  @GetExchange(\"/{id}\")",
              "  @org.higherkindedj.spring.client.OnStatus(value = 404, error = Void.class)",
              "  EitherPath<Object, UserDto> anyError(@PathVariable String id);",
              "  @GetExchange(\"/{id}/typed\")",
              "  @org.higherkindedj.spring.client.OnStatus(value = 404, error = Void.class)",
              "  EitherPath<UserError, UserDto> typedError(@PathVariable String id);");
      Compilation compilation = compile(source, USER_DTO, USER_ERROR);

      assertThat(compilation).hadErrorCount(2);
      assertThat(compilation)
          .hadErrorContaining(
              "@OnStatus error type Void cannot hold a value, but a Left always holds one. Replace"
                  + " Void with org.higherkindedj.hkt.Unit: the default decoder then reads no error"
                  + " body, and every 404 response gives Left(Unit.INSTANCE).")
          .inFile(source)
          .onLineContaining("anyError(");
      assertThat(compilation)
          .hadErrorContaining("@OnStatus error type java.lang.Void is not assignable")
          .inFile(source)
          .onLineContaining("typedError(");
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

    @Test
    @DisplayName("an array written empty is copied as written, wherever the annotation is")
    void emptyArray() {
      // Neither Listed nor Typed has a default, so leaving out an array written empty would not
      // compile. They are on the client, a method, a parameter and a type, and nested in another
      // annotation.
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.Listed",
                  "package com.example;",
                  "import java.lang.annotation.ElementType;",
                  "import java.lang.annotation.Target;",
                  "@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})",
                  "public @interface Listed { String[] value(); String[] tags(); }"),
              JavaFileObjects.forSourceLines(
                  "com.example.Typed",
                  "package com.example;",
                  "import java.lang.annotation.ElementType;",
                  "import java.lang.annotation.Target;",
                  "@Target(ElementType.TYPE_USE)",
                  "public @interface Typed { String[] value(); }"),
              JavaFileObjects.forSourceLines(
                  "com.example.Holder",
                  "package com.example;",
                  "import java.lang.annotation.ElementType;",
                  "import java.lang.annotation.Target;",
                  "@Target(ElementType.METHOD)",
                  "public @interface Holder { Listed value(); }"),
              JavaFileObjects.forSourceLines(
                  "com.example.UserApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/users\")",
                  "@Listed(value = {}, tags = \"client\")",
                  "@HkjHttpClient",
                  "public interface UserApi {",
                  "  @GetExchange(\"/{id}\")",
                  "  @Listed(value = \"method\", tags = {})",
                  "  @Holder(@Listed(value = {}, tags = {}))",
                  "  EitherPath<UserError, @Typed({}) UserDto> getUser(",
                  "      @PathVariable @Listed(value = {}, tags = \"parameter\") String id);",
                  "}"),
              USER_DTO,
              USER_ERROR);

      // Each generated file is compiled here too, so one missing an empty array fails the build.
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch("@Listed\\(\\s*value = \\{},\\s*tags = \"client\"\\s*\\)");
      assertThat(compilation)
          .generatedSourceFile("com.example.UserApiHttpExchange")
          .contentsAsUtf8String()
          .contains("@Holder(@Listed(value = {}, tags = {}))");
    }
  }

  @Nested
  @DisplayName("aggregate Map/MultiValueMap binders are left unnamed")
  class AggregateBinders {

    // A Map/MultiValueMap parameter on @RequestParam/@RequestHeader/@CookieValue, or a Map on
    // @PathVariable, is an aggregate binder: unnamed, it binds *every* name/value pair. Injecting a
    // name would turn it into a single-entry binder, so the processor must leave these unnamed
    // while still naming ordinary scalar parameters.
    private final Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "com.example.AggApi",
                "package com.example;",
                "import java.util.Map;",
                "import org.higherkindedj.hkt.effect.EitherPath;",
                "import org.higherkindedj.spring.client.HkjHttpClient;",
                "import org.springframework.util.MultiValueMap;",
                "import org.springframework.web.bind.annotation.CookieValue;",
                "import org.springframework.web.bind.annotation.PathVariable;",
                "import org.springframework.web.bind.annotation.RequestHeader;",
                "import org.springframework.web.bind.annotation.RequestParam;",
                "import org.springframework.web.service.annotation.GetExchange;",
                "import org.springframework.web.service.annotation.HttpExchange;",
                "@HttpExchange(\"/agg\")",
                "@HkjHttpClient",
                "public interface AggApi {",
                "  @GetExchange(\"/search\")",
                "  EitherPath<UserError, UserDto> search(@RequestParam Map<String, String> params);",
                "  @GetExchange(\"/headers\")",
                "  EitherPath<UserError, UserDto> headers("
                    + "@RequestHeader MultiValueMap<String, String> headers);",
                "  @GetExchange(\"/cookies\")",
                "  EitherPath<UserError, UserDto> cookies(@CookieValue Map<String, String> cookies);",
                "  @GetExchange(\"/vars\")",
                "  EitherPath<UserError, UserDto> vars(@PathVariable Map<String, String> vars);",
                "  @GetExchange(\"/one\")",
                "  EitherPath<UserError, UserDto> one(@RequestParam String q);",
                "}"),
            USER_DTO,
            USER_ERROR);

    @Test
    @DisplayName("no explicit name is injected for aggregate Map/MultiValueMap parameters")
    void leavesAggregateBindersUnnamed() {
      assertThat(compilation).succeeded();
      var nativeIface =
          assertThat(compilation)
              .generatedSourceFile("com.example.AggApiHttpExchange")
              .contentsAsUtf8String();
      nativeIface.doesNotContain("@RequestParam(\"params\")");
      nativeIface.doesNotContain("@RequestHeader(\"headers\")");
      nativeIface.doesNotContain("@CookieValue(\"cookies\")");
      nativeIface.doesNotContain("@PathVariable(\"vars\")");
    }

    @Test
    @DisplayName("scalar parameters alongside aggregate binders still get an explicit name")
    void namesScalarParameters() {
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.AggApiHttpExchange")
          .contentsAsUtf8String()
          .contains("@RequestParam(\"q\")");
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
          .contains("ResponseErrorDecoders.<ApiErr>forDefault(decoderFactory, ApiErr.class)");
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
      JavaFileObject source =
          api(
              "  @GetExchange(\"/{id}\")",
              "  @OnStatus(value = 404, error = UserDto.class)",
              "  EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);");
      Compilation compilation = compile(source, USER_DTO, API_ERR, NOT_FOUND, CONFLICT, GENERIC);

      // A method the client declares itself is reported at its own declaration.
      assertThat(compilation)
          .hadErrorContaining("@OnStatus error type com.example.UserDto is not assignable")
          .inFile(source)
          .onLineContaining("getUser");
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

    @Test
    @DisplayName("an unresolvable override type in the client's own source is not called missing")
    void unresolvableLocalOverrideIsLeftToJavac() {
      Compilation compilation =
          compile(
              api(
                  "  @GetExchange(\"/{id}\")",
                  "  @OnStatus(value = 404, error = NotFund.class)",
                  "  EitherPath<ApiErr, UserDto> getUser(@PathVariable String id);"),
              USER_DTO,
              API_ERR,
              NOT_FOUND,
              CONFLICT,
              GENERIC);

      // javac names the unresolvable type itself. The missing-dependency advice belongs to a type
      // named in a class file, where javac says nothing, and would misdirect a typo.
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("classpath"));
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

    @Test
    @DisplayName("a method two interfaces map differently is refused, as it is implemented once")
    void diamondMappedDifferentlyIsRefused() {
      JavaFileObject diamond =
          JavaFileObjects.forSourceLines(
              "com.example.DiamondApi",
              "package com.example;",
              "import org.higherkindedj.spring.client.HkjHttpClient;",
              "import org.springframework.web.service.annotation.HttpExchange;",
              "@HttpExchange(\"/users\")",
              "@HkjHttpClient",
              "public interface DiamondApi extends AApi, BApi {}");
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.AApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface AApi {",
                  "  @GetExchange(\"/a/{id}\")",
                  "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
                  "}"),
              JavaFileObjects.forSourceLines(
                  "com.example.BApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface BApi {",
                  "  @GetExchange(\"/b/{id}\")",
                  "  EitherPath<UserError, UserDto> getUser(@PathVariable String id);",
                  "}"),
              diamond,
              USER_DTO,
              USER_ERROR);

      // Generated once, the client would send every call to one of the two paths.
      assertThat(compilation)
          .hadErrorContaining("@HkjHttpClient implements 'getUser' once for two methods DiamondApi")
          .inFile(diamond);
      assertThat(compilation)
          .hadErrorContaining(
              "which the generated client would write differently: the method's @GetExchange"
                  + " differs. Redeclare 'getUser' on DiamondApi as the client should have it.");
      Assertions.assertThat(compilation.errors())
          .singleElement()
          .satisfies(
              error ->
                  Assertions.assertThat(error.getMessage(null))
                      .contains("AApi.getUser(java.lang.String)")
                      .contains("BApi.getUser(java.lang.String)"));
    }

    @Test
    @DisplayName("a method two interfaces spell differently but map alike is generated once")
    void diamondSpeltDifferentlyButAlikeCompiles() {
      // An attribute's order, a default written out, a parameter's name, the value-or-name alias
      // and a name the processor injects all leave the generated client as it was.
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.AApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.OnStatus;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface AApi {",
                  "  @GetExchange(value = \"/{id}\", accept = \"application/json\")",
                  "  @OnStatus(value = 404, error = UserError.class)",
                  "  @OnStatus(value = 410, error = UserError.class)",
                  "  <X> EitherPath<UserError, X> getUser(@PathVariable String id, X hint);",
                  "}"),
              JavaFileObjects.forSourceLines(
                  "com.example.BApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.OnStatus;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface BApi {",
                  "  @GetExchange(accept = \"application/json\", value = \"/{id}\")",
                  "  @OnStatus(value = 410, error = UserError.class)",
                  "  @OnStatus(value = 404, error = UserError.class)",
                  "  <Y> EitherPath<UserError, Y> getUser(",
                  "      @PathVariable(name = \"id\", required = true) String userId, Y hint);",
                  "}"),
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

    @Test
    @DisplayName("a method two interfaces type differently is refused, naming the part")
    void diamondTypedDifferentlyIsRefused() {
      // The facade would promise one of the two contracts, chosen by the order javac lists them.
      JavaFileObject diamond =
          JavaFileObjects.forSourceLines(
              "com.example.DiamondApi",
              "package com.example;",
              "import org.higherkindedj.spring.client.HkjHttpClient;",
              "import org.springframework.web.service.annotation.HttpExchange;",
              "@HttpExchange(\"/users\")",
              "@HkjHttpClient",
              "public interface DiamondApi extends AApi, BApi {}");
      Compilation compilation =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.AApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.springframework.web.bind.annotation.RequestParam;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface AApi {",
                  "  @GetExchange(\"/\")",
                  "  EitherPath<UserError, UserDto> find(@RequestParam String query);",
                  "}"),
              JavaFileObjects.forSourceLines(
                  "com.example.BApi",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.jspecify.annotations.Nullable;",
                  "import org.springframework.web.bind.annotation.RequestParam;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "public interface BApi {",
                  "  @GetExchange(\"/\")",
                  "  EitherPath<UserError, UserDto> find(@RequestParam @Nullable String query);",
                  "}"),
              diamond,
              USER_DTO,
              USER_ERROR);

      assertThat(compilation)
          .hadErrorContaining(
              "which the generated client would write differently: parameter 1's type differs.")
          .inFile(diamond);
      assertThat(compilation).hadErrorCount(1);
    }

    private static final JavaFileObject DIAMOND =
        JavaFileObjects.forSourceLines(
            "com.example.DiamondApi",
            "package com.example;",
            "import org.higherkindedj.spring.client.HkjHttpClient;",
            "import org.springframework.web.service.annotation.HttpExchange;",
            "@HttpExchange(\"/users\")",
            "@HkjHttpClient",
            "public interface DiamondApi extends AApi, BApi {}");

    /**
     * An interface of one method, {@code AApi} or {@code BApi}, for {@code DiamondApi} to extend.
     */
    private static JavaFileObject side(String name, String method) {
      return JavaFileObjects.forSourceLines(
          "com.example." + name,
          "package com.example;",
          "import org.higherkindedj.hkt.effect.MaybePath;",
          "import org.higherkindedj.spring.client.OnStatus;",
          "import org.springframework.web.bind.annotation.PathVariable;",
          "import org.springframework.web.service.annotation.GetExchange;",
          "public interface " + name + " {",
          "  " + method,
          "}");
    }

    private static Compilation compileDiamond(String a, String b, JavaFileObject... more) {
      JavaFileObject[] sources = new JavaFileObject[more.length + 5];
      sources[0] = side("AApi", a);
      sources[1] = side("BApi", b);
      sources[2] = DIAMOND;
      sources[3] = USER_DTO;
      sources[4] = USER_ERROR;
      System.arraycopy(more, 0, sources, 5, more.length);
      return compile(sources);
    }

    @Test
    @DisplayName("a parameter's name counts where it names the binding")
    void diamondImplicitBindingNamesDiffer() {
      // An unnamed @PathVariable binds the parameter's name, so the two bind id and key.
      Compilation compilation =
          compileDiamond(
              "@GetExchange(\"/{id}\") MaybePath<UserDto> getUser(@PathVariable String id);",
              "@GetExchange(\"/{id}\") MaybePath<UserDto> getUser(@PathVariable String key);");

      assertThat(compilation)
          .hadErrorContaining(
              "which the generated client would write differently: parameter 1's @PathVariable"
                  + " differs.")
          .inFile(DIAMOND);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("two annotations sharing a simple name are each compared, by qualified name")
    void diamondAnnotationsSharingASimpleNameAreCompared() {
      // Keyed by simple name, the second @GetExchange took the first's place, and the paths
      // compared equal.
      JavaFileObject marker =
          JavaFileObjects.forSourceLines(
              "com.example.demo.GetExchange",
              "package com.example.demo;",
              "public @interface GetExchange {}");
      Compilation compilation =
          compileDiamond(
              "@GetExchange(\"/a/{id}\") @com.example.demo.GetExchange"
                  + " MaybePath<UserDto> getUser(@PathVariable String id);",
              "@GetExchange(\"/b/{id}\") @com.example.demo.GetExchange"
                  + " MaybePath<UserDto> getUser(@PathVariable String id);",
              marker);

      assertThat(compilation)
          .hadErrorContaining(
              "which the generated client would write differently: the method's"
                  + " @org.springframework.web.service.annotation.GetExchange differs.")
          .inFile(DIAMOND);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a MaybePath's @OnStatus does not count, and each declaration's is warned of")
    void diamondMaybePathOverridesDoNotCount() {
      // A MaybePath builds no decoder, so the client is the same whichever declaration it follows.
      // javac lists BApi's first, so AApi's is not the declaration the client follows.
      Compilation compilation =
          compileDiamond(
              "@GetExchange(\"/{id}\") @OnStatus(value = 404, error = UserError.class)"
                  + " MaybePath<UserDto> getUser(@PathVariable String id);",
              "@GetExchange(\"/{id}\") MaybePath<UserDto> getUser(@PathVariable String id);");

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining(
              "'getUser', inherited from 'AApi': @OnStatus has no effect on a MaybePath method");
    }

    /** A type-use annotation whose members are named as the diamond's type variables are. */
    private static final JavaFileObject TAG =
        JavaFileObjects.forSourceLines(
            "com.example.Tag",
            "package com.example;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "@Target(ElementType.TYPE_USE)",
            "public @interface Tag {",
            "  String value() default \"\";",
            "  String X() default \"\";",
            "  String Y() default \"\";",
            "}");

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = {
          "named as a package, a variable leaves a class's name as it is | <java> | MaybePath<String>",
          "named as a class, a variable leaves that class's name as it is | <String>"
              + " | MaybePath<java.lang.@Tag String>"
        })
    @DisplayName("a type variable is renumbered where it stands, never inside a class's name")
    void diamondClassNamesAreNotRenumbered(String description, String variable, String type) {
      // javapoet writes java.lang.String, so a variable named java or String matches part of the
      // written type. The parameter is a long, as String would name the variable.
      Compilation compilation =
          compileDiamond(
              "@GetExchange(\"/{id}\") "
                  + variable
                  + " "
                  + type
                  + " getUser(@PathVariable long id);",
              "@GetExchange(\"/{id}\") <Y> " + type + " getUser(@PathVariable long id);",
              TAG);

      assertThat(compilation).succeeded();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = {
          "renamed through a wildcard and an array is alike | ? super X | ? super Y | ",
          "a wildcard's kind still counts | ? super X | ? extends Y | the method's return type"
              + " differs."
        })
    @DisplayName("a type variable is renumbered inside a wildcard and an array")
    void diamondVariablesAreRenumberedThroughWildcardsAndArrays(
        String description, String aWildcard, String bWildcard, String differs) {
      Compilation compilation =
          compileDiamond(
              "@GetExchange(\"/{id}\") <X> MaybePath<java.util.List<"
                  + aWildcard
                  + ">[]> getUser(@PathVariable long id);",
              "@GetExchange(\"/{id}\") <Y> MaybePath<java.util.List<"
                  + bWildcard
                  + ">[]> getUser(@PathVariable long id);");

      if (differs == null) {
        assertThat(compilation).succeeded();
      } else {
        assertThat(compilation)
            .hadErrorContaining("which the generated client would write differently: " + differs)
            .inFile(DIAMOND);
      }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = {
          "the same value under renamed variables is alike | \"X\" | \"X\" | ",
          "different values are told apart | \"X\" | \"Y\" | the method's return type differs.",
          "the same member under renamed variables is alike | X = \"same\" | X = \"same\" | ",
          "different members are told apart | X = \"same\" | Y = \"same\" | the method's return"
              + " type differs."
        })
    @DisplayName("a type-use annotation is compared as written, its values and member names kept")
    void diamondAnnotationsAreComparedAsWritten(
        String description, String aArguments, String bArguments, String differs) {
      // The values and member names equal the method's own type variables' names, which are
      // renumbered elsewhere in the type.
      Compilation compilation =
          compileDiamond(
              "@GetExchange(\"/{id}\") <X> MaybePath<@Tag("
                  + aArguments
                  + ") String> getUser(@PathVariable String id);",
              "@GetExchange(\"/{id}\") <Y> MaybePath<@Tag("
                  + bArguments
                  + ") String> getUser(@PathVariable String id);",
              TAG);

      if (differs == null) {
        assertThat(compilation).succeeded();
      } else {
        assertThat(compilation)
            .hadErrorContaining("which the generated client would write differently: " + differs)
            .inFile(DIAMOND);
      }
    }
  }

  @Nested
  @DisplayName("gives the native interface the type-level @HttpExchange Spring would read")
  class TypeLevelExchange {

    /** Composed from {@code @HttpExchange}, as Spring's merged-annotation search reads it. */
    private static final JavaFileObject USERS_EXCHANGE =
        JavaFileObjects.forSourceLines(
            "com.example.UsersExchange",
            "package com.example;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "import java.lang.annotation.Target;",
            "import org.springframework.web.service.annotation.HttpExchange;",
            "@Retention(RetentionPolicy.RUNTIME)",
            "@Target(ElementType.TYPE)",
            "@HttpExchange(url = \"/composed\", accept = \"application/json\")",
            "public @interface UsersExchange {}");

    /** Composed from {@code @UsersExchange}, so {@code @HttpExchange} two levels down. */
    private static final JavaFileObject TEAM_EXCHANGE =
        JavaFileObjects.forSourceLines(
            "com.example.TeamExchange",
            "package com.example;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "import java.lang.annotation.Target;",
            "@Retention(RetentionPolicy.RUNTIME)",
            "@Target(ElementType.TYPE)",
            "@UsersExchange",
            "public @interface TeamExchange {}");

    /** Composed from {@code @HttpExchange}, with headers that default to one. */
    private static final JavaFileObject HEADED_EXCHANGE =
        JavaFileObjects.forSourceLines(
            "com.example.HeadedExchange",
            "package com.example;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "import java.lang.annotation.Target;",
            "import org.springframework.core.annotation.AliasFor;",
            "import org.springframework.web.service.annotation.HttpExchange;",
            "@Retention(RetentionPolicy.RUNTIME)",
            "@Target(ElementType.TYPE)",
            "@HttpExchange(\"/headed\")",
            "public @interface HeadedExchange {",
            "  @AliasFor(annotation = HttpExchange.class, attribute = \"headers\")",
            "  String[] headers() default \"X-Team=hkj\";",
            "}");

    /**
     * Composed from {@code @HttpExchange}, but with no {@code @Retention}, so Spring never sees it.
     */
    private static final JavaFileObject UNSEEN_EXCHANGE =
        JavaFileObjects.forSourceLines(
            "com.example.UnseenExchange",
            "package com.example;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Target;",
            "import org.springframework.web.service.annotation.HttpExchange;",
            "@Target(ElementType.TYPE)",
            "@HttpExchange(\"/unseen\")",
            "public @interface UnseenExchange {}");

    @Test
    @DisplayName("a client with none of its own takes its base's")
    void fromTheBase() {
      Compilation compilation =
          compile(Hierarchy.FROM_THE_BASE.sources().toArray(JavaFileObject[]::new));

      // Spring proxies ChildApiHttpExchange, which extends nothing, so without the base's
      // annotation every call would go to /me and send no Accept header.
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.ChildApiHttpExchange")
          .contentsAsUtf8String()
          .containsMatch(
              "@HttpExchange\\(\\s*url = \"/api/users\",\\s*accept = \"application/json\",");
    }

    /**
     * Interface hierarchies, each with the URI template Spring sends for it, or {@link
     * Sent#REFUSED}, and one declaration per interface. The last is the client, and {@code BaseApi}
     * declares the method.
     */
    enum Hierarchy {
      FROM_THE_BASE(
          "a base's, with every attribute",
          "/api/users/me",
          "@HttpExchange(url = \"/api/users\", accept = \"application/json\","
              + " contentType = \"application/json\", headers = \"X-Team=hkj\", version = \"2\")"
              + " interface BaseApi",
          "interface ChildApi extends BaseApi"),
      OWN_OVER_THE_BASE(
          "the client's own, not joined to its base's",
          "/users/me",
          "@HttpExchange(\"/api\") interface BaseApi",
          "@HttpExchange(\"/users\") interface ChildApi extends BaseApi"),
      NEARER_BASE(
          "the nearer of two bases in a line",
          "/near/me",
          "@HttpExchange(\"/far\") interface BaseApi",
          "@HttpExchange(\"/near\") interface MiddleApi extends BaseApi",
          "interface ChildApi extends MiddleApi"),
      DEPTH_FIRST(
          "the first base's own base before the second base",
          "/deep/me",
          "@HttpExchange(\"/deep\") interface BaseApi",
          "interface MiddleApi extends BaseApi",
          "@HttpExchange(\"/other\") interface OtherApi",
          "interface ChildApi extends MiddleApi, OtherApi"),
      DIAMOND(
          "past a base reached twice",
          "/right/me",
          "interface BaseApi",
          "interface LeftApi extends BaseApi",
          "@HttpExchange(\"/right\") interface RightApi extends BaseApi",
          "interface ChildApi extends LeftApi, RightApi"),
      COMPOSED_ON_THE_BASE(
          "one composed from it on a base",
          "/composed/me",
          "@UsersExchange interface BaseApi",
          "interface ChildApi extends BaseApi"),
      COMPOSED_TWO_DEEP(
          "one composed from it two levels down",
          "/composed/me",
          "@TeamExchange interface BaseApi",
          "interface ChildApi extends BaseApi"),
      COMPOSED_ON_THE_CLIENT(
          "the client's own composed one, not its base's",
          "/composed/me",
          "@HttpExchange(\"/api\") interface BaseApi",
          "@UsersExchange interface ChildApi extends BaseApi"),
      UNSEEN_ON_THE_CLIENT(
          "its base's, past one on the client not retained at run time",
          "/api/me",
          "@HttpExchange(\"/api\") interface BaseApi",
          "@UnseenExchange interface ChildApi extends BaseApi"),
      UNSEEN_ON_A_BASE(
          "a further base's, past one on a nearer base not retained at run time",
          "/api/me",
          "@HttpExchange(\"/api\") interface BaseApi",
          "@UnseenExchange interface MiddleApi extends BaseApi",
          "interface ChildApi extends MiddleApi"),
      EMPTY_ARRAY_ON_THE_BASE(
          "one composed from it with an array written empty, not its default",
          "/headed/me",
          "@HeadedExchange(headers = {}) interface BaseApi",
          "interface ChildApi extends BaseApi"),
      TWO_EQUAL_ON_ONE_BASE(
          "two that are equal on one base, which Spring takes as one",
          "/composed/me",
          "@HttpExchange(url = \"/composed\", accept = \"application/json\") @UsersExchange"
              + " interface BaseApi",
          "interface ChildApi extends BaseApi"),
      TWO_ON_ONE_BASE(
          "two that differ on one base, which Spring refuses",
          Sent.REFUSED,
          "@HttpExchange(\"/api\") @UsersExchange interface BaseApi",
          "interface ChildApi extends BaseApi"),
      NONE("none anywhere", "/me", "interface BaseApi", "interface ChildApi extends BaseApi");

      private final String description;
      private final String uriTemplate;
      private final List<String> declarations;

      Hierarchy(String description, String uriTemplate, String... declarations) {
        this.description = description;
        this.uriTemplate = uriTemplate;
        this.declarations = List.of(declarations);
      }

      /** The hierarchy's sources, with the types its method and annotations name. */
      List<JavaFileObject> sources() {
        return Stream.concat(
                Stream.of(
                    USER_DTO,
                    USER_ERROR,
                    USERS_EXCHANGE,
                    TEAM_EXCHANGE,
                    HEADED_EXCHANGE,
                    UNSEEN_EXCHANGE),
                declarations.stream().map(this::source))
            .toList();
      }

      private JavaFileObject source(String declaration) {
        String name = declaration.replaceFirst(".*interface (\\w+).*", "$1");
        boolean client = declaration.equals(declarations.getLast());
        return JavaFileObjects.forSourceLines(
            "com.example." + name,
            "package com.example;",
            "import org.higherkindedj.hkt.effect.EitherPath;",
            "import org.higherkindedj.spring.client.HkjHttpClient;",
            "import org.springframework.web.service.annotation.GetExchange;",
            "import org.springframework.web.service.annotation.HttpExchange;",
            (client ? "@HkjHttpClient " : "") + "public " + declaration + " {",
            name.equals("BaseApi")
                ? "@GetExchange(\"/me\") EitherPath<UserError, UserDto> me();"
                : "",
            "}");
      }

      @Override
      public String toString() {
        return description;
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Hierarchy.class)
    @DisplayName("the generated client sends what Spring sends for the client as a plain interface")
    void asSpringReadsIt(Hierarchy hierarchy) throws ReflectiveOperationException {
      Compilation compilation = compile(hierarchy.sources().toArray(JavaFileObject[]::new));
      assertThat(compilation).succeeded();
      ClassLoader classes = classesOf(compilation);

      // Spring itself is the reference: proxied as a plain HTTP interface, ChildApi has its
      // type-level annotation read through its whole hierarchy. The generated interface extends
      // nothing, so it must carry what that search finds. The URI template pins what Spring found,
      // so the two cannot agree by failing alike.
      Sent plain = requestSentBy(classes.loadClass("com.example.ChildApi"));
      Assertions.assertThat(plain.uriTemplate()).isEqualTo(hierarchy.uriTemplate);
      Assertions.assertThat(requestSentBy(classes.loadClass("com.example.ChildApiHttpExchange")))
          .isEqualTo(plain);
    }

    /**
     * What Spring sends when {@code me()} is called on a proxy of an HTTP interface: the URI
     * template, and the method, the headers and the version. Or that Spring refused the interface,
     * and with what.
     */
    private record Sent(String uriTemplate, String rest) {
      static final String REFUSED = "refused";
    }

    private static Sent requestSentBy(Class<?> service) throws ReflectiveOperationException {
      Recorder recorder = new Recorder();
      Object proxy;
      try {
        proxy = HttpServiceProxyFactory.builderFor(recorder).build().createClient(service);
      } catch (IllegalStateException refused) {
        // Not the message, which names the interface: the two differ there by design.
        return new Sent(Sent.REFUSED, refused.getClass().getName());
      }
      service.getMethod("me").invoke(proxy);
      HttpRequestValues sent = recorder.sent.getFirst();
      return new Sent(
          String.valueOf(sent.getUriTemplate()),
          sent.getHttpMethod() + " " + sent.getHeaders() + " version " + sent.getApiVersion());
    }

    /** Records each request Spring would send, and answers it with an empty response. */
    private static final class Recorder implements HttpExchangeAdapter {

      private final List<HttpRequestValues> sent = new ArrayList<>();

      @Override
      public boolean supportsRequestAttributes() {
        return false;
      }

      @Override
      public void exchange(HttpRequestValues values) {
        sent.add(values);
      }

      @Override
      public HttpHeaders exchangeForHeaders(HttpRequestValues values) {
        sent.add(values);
        return new HttpHeaders();
      }

      @Override
      public <T> @Nullable T exchangeForBody(
          HttpRequestValues values, ParameterizedTypeReference<T> type) {
        sent.add(values);
        return null;
      }

      @Override
      public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues values) {
        sent.add(values);
        return ResponseEntity.ok().build();
      }

      @Override
      public <T> ResponseEntity<T> exchangeForEntity(
          HttpRequestValues values, ParameterizedTypeReference<T> type) {
        sent.add(values);
        return ResponseEntity.ok().build();
      }
    }

    /** Loads the classes a compilation wrote, so Spring can read them as it would at run time. */
    private static ClassLoader classesOf(Compilation compilation) {
      return new ClassLoader(HkjHttpClientProcessorTest.class.getClassLoader()) {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          JavaFileObject file =
              compilation
                  .generatedFile(StandardLocation.CLASS_OUTPUT, name.replace('.', '/') + ".class")
                  .orElseThrow(() -> new ClassNotFoundException(name));
          try (InputStream in = file.openInputStream()) {
            byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
          } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
          }
        }
      };
    }
  }

  @Nested
  @DisplayName("reads the methods of a generic base as the client instantiates it")
  class GenericBase {

    private static final List<String> IMPORTS =
        List.of(
            "package com.example;",
            "import java.util.List;",
            "import java.util.Map;",
            "import org.higherkindedj.hkt.Unit;",
            "import org.higherkindedj.hkt.effect.EitherPath;",
            "import org.higherkindedj.hkt.effect.MaybePath;",
            "import org.higherkindedj.hkt.effect.VTaskPath;",
            "import org.higherkindedj.hkt.either.Either;",
            "import org.higherkindedj.spring.client.HkjHttpClient;",
            "import org.higherkindedj.spring.client.OnStatus;",
            "import org.jspecify.annotations.Nullable;",
            "import org.springframework.web.bind.annotation.PathVariable;",
            "import org.springframework.web.bind.annotation.RequestParam;",
            "import org.springframework.web.service.annotation.GetExchange;",
            "import org.springframework.web.service.annotation.HttpExchange;");

    /** An open error type and a subtype of it, for an override to narrow to. */
    private static final JavaFileObject API_ERR =
        JavaFileObjects.forSourceLines(
            "com.example.ApiErr", "package com.example;", "public interface ApiErr {}");

    private static final JavaFileObject NOT_FOUND =
        JavaFileObjects.forSourceLines(
            "com.example.NotFound",
            "package com.example;",
            "public record NotFound(String message) implements ApiErr {}");

    private static JavaFileObject source(String simpleName, String... body) {
      return JavaFileObjects.forSourceLines(
          "com.example." + simpleName, Stream.concat(IMPORTS.stream(), Stream.of(body)).toList());
    }

    private static JavaFileObject base(String declaration, String... members) {
      String[] body = new String[members.length + 2];
      body[0] = "public interface " + declaration + " {";
      for (int i = 0; i < members.length; i++) {
        body[i + 1] = "  @GetExchange(\"/{id}\") " + members[i];
      }
      body[body.length - 1] = "}";
      return source(declaration.replaceAll("<.*", ""), body);
    }

    private static JavaFileObject client(String declaration, String... redeclared) {
      String[] body = new String[redeclared.length + 4];
      body[0] = "@HttpExchange(\"/users\")";
      body[1] = "@HkjHttpClient";
      body[2] = "public interface " + declaration + " {";
      for (int i = 0; i < redeclared.length; i++) {
        body[i + 3] = "  @Override @GetExchange(\"/{id}\") " + redeclared[i];
      }
      body[body.length - 1] = "}";
      return source("ChildApi", body);
    }

    private static Compilation compileWith(JavaFileObject... sources) {
      JavaFileObject[] all = new JavaFileObject[sources.length + 4];
      System.arraycopy(sources, 0, all, 0, sources.length);
      all[sources.length] = USER_DTO;
      all[sources.length + 1] = USER_ERROR;
      all[sources.length + 2] = API_ERR;
      all[sources.length + 3] = NOT_FOUND;
      return compile(all);
    }

    private static Map<String, String> generatedSources(Compilation compilation) {
      return compilation.generatedSourceFiles().stream()
          .collect(Collectors.toMap(JavaFileObject::getName, GenericBase::contents));
    }

    private static String contents(JavaFileObject file) {
      try {
        return file.getCharContent(true).toString();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    /**
     * Where a base's type variable can stand, with the method the client would otherwise redeclare
     * in its place, its type arguments substituted by hand.
     */
    enum Position {
      ERROR_TYPE(
          "the error type",
          "<E>",
          "EitherPath<E, UserDto> get(@PathVariable String id);",
          "ChildApi extends Repo<UserError>",
          "EitherPath<UserError, UserDto> get(@PathVariable String id);"),
      SUCCESS_TYPE(
          "the success type",
          "<T>",
          "EitherPath<UserError, T> get(@PathVariable String id);",
          "ChildApi extends Repo<UserDto>",
          "EitherPath<UserError, UserDto> get(@PathVariable String id);"),
      PARAMETER_TYPE(
          "a parameter type",
          "<ID>",
          "EitherPath<UserError, UserDto> get(@PathVariable ID id);",
          "ChildApi extends Repo<String>",
          "EitherPath<UserError, UserDto> get(@PathVariable String id);"),
      RETURN_TYPE(
          "the whole return type",
          "<R>",
          "R get(@PathVariable String id);",
          "ChildApi extends Repo<EitherPath<UserError, UserDto>>",
          "EitherPath<UserError, UserDto> get(@PathVariable String id);"),
      NESTED(
          "an argument of the success type",
          "<T>",
          "EitherPath<UserError, List<T>> list();",
          "ChildApi extends Repo<UserDto>",
          "EitherPath<UserError, List<UserDto>> list();"),
      VTASK(
          "both sides of a VTaskPath's Either",
          "<E, T>",
          "VTaskPath<Either<E, T>> load(@PathVariable String id);",
          "ChildApi extends Repo<UserError, UserDto>",
          "VTaskPath<Either<UserError, UserDto>> load(@PathVariable String id);"),
      MAYBE(
          "a MaybePath's value",
          "<T>",
          "MaybePath<T> find(@PathVariable String id);",
          "ChildApi extends Repo<UserDto>",
          "MaybePath<UserDto> find(@PathVariable String id);"),
      UNIT(
          "a success type bound to Unit, whose body is ignored",
          "<T>",
          "EitherPath<UserError, T> delete(@PathVariable String id);",
          "ChildApi extends Repo<Unit>",
          "EitherPath<UserError, Unit> delete(@PathVariable String id);"),
      ON_STATUS(
          "an error type an @OnStatus override narrows",
          "<E>",
          "@OnStatus(value = 404, error = NotFound.class)"
              + " EitherPath<E, UserDto> get(@PathVariable String id);",
          "ChildApi extends Repo<ApiErr>",
          "@OnStatus(value = 404, error = NotFound.class)"
              + " EitherPath<ApiErr, UserDto> get(@PathVariable String id);"),
      ANNOTATED_USE(
          "a use of the variable the base annotates",
          "<T>",
          "EitherPath<UserError, @Nullable T> get(@PathVariable String id, @Nullable T fallback);",
          "ChildApi extends Repo<UserDto>",
          "EitherPath<UserError, @Nullable UserDto> get("
              + "@PathVariable String id, @Nullable UserDto fallback);"),
      ANNOTATED_ARGUMENT(
          "a type argument the client annotates",
          "<T>",
          "EitherPath<UserError, T> get(@PathVariable String id);",
          "ChildApi extends Repo<@Nullable UserDto>",
          "EitherPath<UserError, @Nullable UserDto> get(@PathVariable String id);"),
      AGGREGATE_BINDER(
          "a parameter bound to a Map, which stays unnamed",
          "<P>",
          "EitherPath<UserError, UserDto> search(@RequestParam P filters);",
          "ChildApi extends Repo<Map<String, String>>",
          "EitherPath<UserError, UserDto> search(@RequestParam Map<String, String> filters);"),
      GENERIC_CLIENT(
          "a type variable of a generic client",
          "<T>",
          "EitherPath<UserError, T> get(@PathVariable String id);",
          "ChildApi<X> extends Repo<X>",
          "EitherPath<UserError, X> get(@PathVariable String id);"),
      METHOD_TYPE_VARIABLE(
          "beside a type variable of the method's own",
          "<T>",
          "<X> EitherPath<UserError, X> find(@RequestParam T example);",
          "ChildApi extends Repo<UserDto>",
          "<X> EitherPath<UserError, X> find(@RequestParam UserDto example);"),
      METHOD_TYPE_VARIABLE_BOUND(
          "inside the bound of a type variable of the method's own",
          "<T>",
          "<X extends T> EitherPath<UserError, X> find(@RequestParam X example);",
          "ChildApi extends Repo<UserDto>",
          "<X extends UserDto> EitherPath<UserError, X> find(@RequestParam X example);"),
      INTERFACE_BOUNDS(
          "inside a bound of interfaces alone, which javac rebuilds with Object in front",
          "<T>",
          "<X extends Comparable<@Nullable T> & java.io.Serializable>"
              + " EitherPath<UserError, X> find(@RequestParam X example);",
          "ChildApi extends Repo<UserDto>",
          "<X extends Comparable<@Nullable UserDto> & java.io.Serializable>"
              + " EitherPath<UserError, X> find(@RequestParam X example);"),
      INTERFACE_BOUND_MAP(
          "a parameter bounded by a Map and an interface, which stays unnamed",
          "<T>",
          "<P extends Map<String, T> & java.io.Serializable>"
              + " EitherPath<UserError, UserDto> search(@RequestParam P filters);",
          "ChildApi extends Repo<String>",
          "<P extends Map<String, String> & java.io.Serializable>"
              + " EitherPath<UserError, UserDto> search(@RequestParam P filters);"),
      UNUSED_HIDDEN_NAME(
          "beside a type variable of the method's own that hides a name the signature never uses",
          "<T>",
          "<X> EitherPath<UserError, X> find(@RequestParam String query);",
          "ChildApi<X> extends Repo<X>",
          "<X> EitherPath<UserError, X> find(@RequestParam String query);");

      private final String description;
      private final String typeParameters;
      private final String inherited;
      private final String client;
      private final String redeclared;

      Position(
          String description,
          String typeParameters,
          String inherited,
          String client,
          String redeclared) {
        this.description = description;
        this.typeParameters = typeParameters;
        this.inherited = inherited;
        this.client = client;
        this.redeclared = redeclared;
      }

      @Override
      public String toString() {
        return description;
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Position.class)
    @DisplayName("generates the client that redeclaring each method with the arguments would")
    void sameAsRedeclaring(Position position) {
      JavaFileObject repo = base("Repo" + position.typeParameters, position.inherited);
      Compilation inherited = compileWith(repo, client(position.client));
      Compilation redeclared = compileWith(repo, client(position.client, position.redeclared));

      // Each is compiled with its generated sources, so either failing to build fails here.
      assertThat(inherited).succeeded();
      assertThat(redeclared).succeeded();
      Assertions.assertThat(generatedSources(inherited))
          .isNotEmpty()
          .isEqualTo(generatedSources(redeclared));
    }

    @Test
    @DisplayName("an error type a generic client leaves a type variable is refused")
    void errorTypeLeftAVariableIsRefused() {
      JavaFileObject child = client("ChildApi<E> extends Repo<E>");
      Compilation compilation =
          compileWith(
              base("Repo<E>", "EitherPath<E, UserDto> get(@PathVariable String id);"), child);

      assertThat(compilation)
          .hadErrorContaining(
              "'get', inherited from 'Repo': @HkjHttpClient error type cannot be a type variable")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an error type the client binds to Void is refused, naming Unit")
    void errorTypeBoundToVoidIsRefused() {
      JavaFileObject child = client("ChildApi extends Repo<Void>");
      Compilation compilation =
          compileWith(
              base("Repo<E>", "EitherPath<E, UserDto> get(@PathVariable String id);"), child);

      assertThat(compilation)
          .hadErrorContaining(
              "'get', inherited from 'Repo': @HkjHttpClient error type Void cannot hold a value")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an @OnStatus override is checked against the error type the client binds")
    void overrideCheckedAgainstTheBoundErrorType() {
      JavaFileObject child = client("ChildApi extends Repo<UserError>");
      Compilation compilation =
          compileWith(
              base(
                  "Repo<E>",
                  "@OnStatus(value = 404, error = NotFound.class)"
                      + " EitherPath<E, UserDto> get(@PathVariable String id);"),
              child);

      assertThat(compilation)
          .hadErrorContaining(
              "'get', inherited from 'Repo': @OnStatus error type com.example.NotFound is not"
                  + " assignable to the method's error type com.example.UserError.")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a variable an interface in between binds keeps what that clause writes")
    void intermediateClauseKeepsItsAnnotation() {
      // Substitution drops the annotation Mid writes on its use of M; the walk follows Repo's T to
      // Mid's clause, and Mid's M to the client's, to put it back.
      Compilation compilation =
          compileWith(
              base("Repo<E, T>", "EitherPath<E, T> get(@PathVariable String id);"),
              source(
                  "Mid", "public interface Mid<M> extends Repo<UserError, List<@Nullable M>> {}"),
              client("ChildApi extends Mid<UserDto>"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.ChildApiClient").get()))
          .contains("EitherPath<UserError, List<@Nullable UserDto>> get(String id)");
    }

    @Test
    @DisplayName("a raw base's method that names none of its type variables is read as declared")
    void rawBaseMethodWithoutVariables() {
      // javac reads a member reached through a raw type erased, the types the declaration writes
      // out in full included; the declaration itself is what the client means.
      Compilation compilation =
          compileWith(
              base("Repo<T>", "EitherPath<UserError, UserDto> get(@PathVariable String id);"),
              client("ChildApi extends Repo"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.ChildApiClient").get()))
          .contains("EitherPath<UserError, UserDto> get(String id)");
    }

    @Test
    @DisplayName("a raw base's method that names a type variable is refused, naming the raw type")
    void rawBaseMethodWithAVariableIsRefused() {
      JavaFileObject child = client("ChildApi extends Mid");
      Compilation compilation =
          compileWith(
              base("Repo<T>", "EitherPath<UserError, T> get(@PathVariable String id);"),
              source("Mid", "public interface Mid<M> extends Repo<M> {}"),
              child);

      // The client gave Repo nothing to bind because it named Mid raw, so Mid is the clause to fix.
      assertThat(compilation)
          .hadErrorContaining(
              "'get', inherited from 'Repo': @HkjHttpClient reads this method through the raw type"
                  + " Mid, named in ChildApi's extends clause, which leaves Repo's type variable T"
                  + " unbound. Give Mid its type arguments there, or redeclare 'get' on ChildApi.")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "EitherPath<UserError, UserDto> find(@RequestParam List<String> ids);",
          "<X> EitherPath<UserError, X> find(@RequestParam String query);"
        })
    @DisplayName("a raw base's method whose signature needs its erasure is refused")
    void rawBaseMethodErasedIsRefused(String method) {
      // javac reads the member as find(List) or find(String) returning a bare EitherPath, and the
      // declaration, restated, overrides neither.
      JavaFileObject child = client("ChildApi extends Repo");
      Compilation compilation = compileWith(base("Repo<T>", method), child);

      assertThat(compilation)
          .hadErrorContaining(
              "'find', inherited from 'Repo': @HkjHttpClient reads this method through the raw"
                  + " type Repo, named in ChildApi's extends clause, which erases its signature, so"
                  + " the generated client could not override it. Give Repo its type arguments"
                  + " there, or redeclare 'find' on ChildApi.")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a non-generic base reached through a raw interface keeps its types")
    void nonGenericBaseThroughARawInterfaceKeepsItsTypes() {
      // javac erases only the members of the interface named raw and those it declares generic.
      Compilation compilation =
          compileWith(
              base(
                  "Lookup", "EitherPath<UserError, UserDto> find(@RequestParam List<String> ids);"),
              source("Mid", "public interface Mid<M> extends Lookup {}"),
              client("ChildApi extends Mid"));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("a raw interface erases a generic base past a non-generic one too, as javac does")
    void rawInterfaceErasesPastANonGenericOne() {
      // The JLS gives a raw type's supertypes as their erasures, which leaves Fixed as it is and
      // Repo<UserDto> bound. javac erases on through Fixed, and reads get() as returning a bare
      // MaybePath, so the client is refused as for any raw read.
      JavaFileObject child = client("ChildApi extends Mid");
      Compilation compilation =
          compileWith(
              base("Repo<T>", "MaybePath<T> get(@PathVariable String id);"),
              source("Fixed", "public interface Fixed extends Repo<UserDto> {}"),
              source("Mid", "public interface Mid<M> extends Fixed {}"),
              child);

      assertThat(compilation)
          .hadErrorContaining(
              "'get', inherited from 'Repo': @HkjHttpClient reads this method through the raw type"
                  + " Mid, named in ChildApi's extends clause, which leaves Repo's type variable T"
                  + " unbound.")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a base's Map bound reached through another of the method's variables is unnamed")
    void chainedMapBoundStaysAnAggregateBinder() {
      // Under substitution javac rebuilds P's bound with Object in front, which erases to Object;
      // read through Q, it once took Q for a single value and named it.
      Compilation compilation =
          compileWith(
              base(
                  "Repo<T>",
                  "<P extends Map<String, T> & java.io.Serializable, Q extends P>"
                      + " EitherPath<UserError, UserDto> find(@RequestParam Q filters);"),
              client("ChildApi extends Repo<String>"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.ChildApiHttpExchange").get()))
          .contains("@RequestParam Q filters")
          .doesNotContain("\"filters\"");
    }

    @Test
    @DisplayName("an inner class of a parameterised type is not a concrete error type")
    void innerClassOfAParameterisedTypeIsRefused() {
      // Outer.Problem.class names the raw Outer's Problem, so the decoder could not be typed to it.
      JavaFileObject outer = source("Outer", "public class Outer<O> { public class Problem {} }");
      Compilation bound =
          compileWith(
              outer,
              base("Repo<E>", "EitherPath<E, UserDto> get(@PathVariable String id);"),
              client("ChildApi extends Repo<Outer<String>.Problem>"));
      Compilation written =
          compileWith(
              outer,
              source(
                  "ChildApi",
                  "@HttpExchange(\"/users\")",
                  "@HkjHttpClient",
                  "public interface ChildApi {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<Outer<String>.Problem, UserDto> get(@PathVariable String id);",
                  "}"));

      for (Compilation compilation : List.of(bound, written)) {
        assertThat(compilation)
            .hadErrorContaining(
                "@HkjHttpClient error type must be a concrete, non-generic class (so the decoder"
                    + " can bind it with E.class); found: com.example.Outer<java.lang.String>.Problem");
        assertThat(compilation).hadErrorCount(1);
      }
    }

    @Test
    @DisplayName("an inner class of a type that is not parameterised is a concrete error type")
    void innerClassOfAPlainTypeGenerates() {
      Compilation compilation =
          compileWith(
              source("Plain", "public class Plain { public class Problem {} }"),
              base("Repo<E>", "EitherPath<E, UserDto> get(@PathVariable String id);"),
              client("ChildApi extends Repo<Plain.Problem>"));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName(
        "an annotation whose arguments the client cannot name is left off, and others kept")
    void annotationArgumentsTheClientCannotNameAreLeftOff() {
      // The client is written into com.example, where a class or an enum package-private to
      // com.example.base cannot be named, though the annotations naming them can.
      Compilation compilation =
          compileWith(
              inBase(
                  "Tag",
                  "@Target(ElementType.TYPE_USE)",
                  "public @interface Tag {",
                  "  Class<?> value();",
                  "  Level level() default Level.LOW;",
                  "}"),
              inBase(
                  "Mark",
                  "@Target(ElementType.TYPE_USE)",
                  "public @interface Mark { Kind value(); }"),
              inBase(
                  "Wrap",
                  "@Target(ElementType.TYPE_USE)",
                  "public @interface Wrap { Tag value(); }"),
              inBase("Level", "public enum Level { LOW, HIGH }"),
              inBase("Kind", "enum Kind { SECRET }"),
              inBase("Internal", "class Internal {}"),
              inBase("Shown", "public class Shown {}"),
              inBase(
                  "Repo",
                  "public interface Repo<T> {",
                  "  @GetExchange(\"/{id}\") MaybePath<T> get(@PathVariable String id);",
                  "}"),
              inBase(
                  "Lookup",
                  "public interface Lookup extends Repo<@Tag(Internal.class) String> {",
                  "  @GetExchange(\"/kinds/{id}\")",
                  "  MaybePath<@Mark(Kind.SECRET) String> kind(@PathVariable String id);",
                  "  @GetExchange(\"/arrays/{id}\")",
                  "  MaybePath<@Tag(Internal[].class) String> array(@PathVariable String id);",
                  "  @GetExchange(\"/wrapped/{id}\")",
                  "  MaybePath<@Wrap(@Tag(Internal.class)) String> wrapped(@PathVariable String id);",
                  "  @GetExchange(\"/shown/{id}\")",
                  "  MaybePath<@Tag(value = Shown.class, level = Level.HIGH) String> shown(",
                  "      @PathVariable String id);",
                  "}"),
              source(
                  "ChildApi",
                  "@HttpExchange(\"/users\")",
                  "@HkjHttpClient",
                  "public interface ChildApi extends com.example.base.Lookup {}"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.ChildApiClient").get()))
          .contains("MaybePath<String> get(String id)")
          .contains("MaybePath<String> kind(String id)")
          .contains("MaybePath<String> array(String id)")
          .contains("MaybePath<String> wrapped(String id)")
          .contains("MaybePath<@Tag(value = Shown.class, level = Level.HIGH) String> shown(");
    }

    /** A source in {@code com.example.base}, a package the client is not written into. */
    private static JavaFileObject inBase(String simpleName, String... body) {
      return JavaFileObjects.forSourceLines(
          "com.example.base." + simpleName,
          Stream.concat(
                  Stream.of(
                      "package com.example.base;",
                      "import java.lang.annotation.ElementType;",
                      "import java.lang.annotation.Target;",
                      "import org.higherkindedj.hkt.effect.MaybePath;",
                      "import org.springframework.web.bind.annotation.PathVariable;",
                      "import org.springframework.web.service.annotation.GetExchange;"),
                  Stream.of(body))
              .toList());
    }

    @Test
    @DisplayName("a raw type argument is an unsupported return type, not a raw read")
    void rawTypeArgumentIsUnsupported() {
      JavaFileObject child = client("ChildApi extends Repo<EitherPath>");
      Compilation compilation =
          compileWith(base("Repo<R>", "R get(@PathVariable String id);"), child);

      assertThat(compilation)
          .hadErrorContaining(
              "'get', inherited from 'Repo': Unsupported @HkjHttpClient return type.")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "a type variable of the method's own that hides one the signature needs is refused")
    void hiddenClientVariableIsRefused() {
      // Restated, find(T) becomes <X> find(X), where X has come to mean the method's.
      JavaFileObject child = client("ChildApi<X> extends Repo<X>");
      Compilation compilation =
          compileWith(
              base("Repo<T>", "<X> EitherPath<UserError, X> find(@RequestParam T example);"),
              child);

      assertThat(compilation)
          .hadErrorContaining(
              "'find', inherited from 'Repo': @HkjHttpClient cannot restate this method: its type"
                  + " parameter X hides ChildApi's X, which the method's signature names once the"
                  + " type arguments ChildApi gives Repo are in place. Rename ChildApi's type"
                  + " parameter X, or the method's.")
          .inFile(child);
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a client that gives a generic client its type arguments gets a bean")
    void clientOfAGenericClientGetsABean() {
      Compilation compilation =
          compileWith(
              source(
                  "Repo",
                  "@HttpExchange(\"/items\")",
                  "@HkjHttpClient",
                  "public interface Repo<T> {",
                  "  @GetExchange(\"/{id}\") EitherPath<UserError, T> get(@PathVariable String id);",
                  "}"),
              client("ChildApi extends Repo<UserDto>"));

      assertThat(compilation).succeeded();
      // The generic client itself stays codegen-only.
      Assertions.assertThat(generatedSources(compilation).keySet())
          .anyMatch(name -> name.endsWith("/ChildApiClientConfiguration.java"))
          .noneMatch(name -> name.endsWith("/RepoClientConfiguration.java"));
    }

    @Test
    @DisplayName(
        "two methods the client's type arguments make one, mapped differently, are refused")
    void methodsMadeOneButMappedDifferentlyAreRefused() {
      // Erased, get(A) and get(B) are get(Number) and get(Comparable); as the client has them, both
      // are get(Integer), and the client implements that once.
      JavaFileObject child = client("ChildApi extends Repo<Integer, Integer>");
      Compilation compilation =
          compileWith(
              source(
                  "Repo",
                  "public interface Repo<A extends Number, B extends Comparable<B>> {",
                  "  @GetExchange(\"/a/{id}\")"
                      + " EitherPath<UserError, UserDto> get(@PathVariable A id);",
                  "  @GetExchange(\"/b/{id}\")"
                      + " EitherPath<UserError, UserDto> get(@PathVariable B id);",
                  "}"),
              child);

      assertThat(compilation)
          .hadErrorContaining("@HkjHttpClient implements 'get' once for two methods ChildApi")
          .inFile(child);
      Assertions.assertThat(compilation.errors())
          .singleElement()
          .satisfies(
              error ->
                  Assertions.assertThat(error.getMessage(null))
                      .contains("Repo.get(A)")
                      .contains("Repo.get(B)"));
    }

    @Test
    @DisplayName("a method bounded by interfaces alone, inherited twice alike, is generated once")
    void interfaceBoundDiamondOnceSubstituted() {
      // Substituted, X's bound is rebuilt with Object in front, which must not make the two
      // declarations erase apart.
      Compilation compilation =
          compileWith(
              base(
                  "Repo<T>",
                  "<X extends Comparable<T> & java.io.Serializable>"
                      + " EitherPath<UserError, X> find(@RequestParam X example);"),
              base(
                  "Lookup",
                  "<X extends Comparable<UserDto> & java.io.Serializable>"
                      + " EitherPath<UserError, X> find(@RequestParam X example);"),
              client("ChildApi extends Repo<UserDto>, Lookup"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.ChildApiHttpExchange").get()))
          .containsOnlyOnce(" find(");
    }

    @Test
    @DisplayName("a method two bases declare alike once substituted is generated once")
    void diamondOnceSubstituted() {
      // Read as declared, the two erase to get(Object) and get(String), and would both be kept.
      Compilation compilation =
          compileWith(
              base("Repo<ID>", "EitherPath<UserError, UserDto> get(@PathVariable ID id);"),
              base("Lookup", "EitherPath<UserError, UserDto> get(@PathVariable String id);"),
              client("ChildApi extends Repo<String>, Lookup"));

      assertThat(compilation).succeeded();
      Assertions.assertThat(
              contents(compilation.generatedSourceFile("com.example.ChildApiHttpExchange").get()))
          .containsOnlyOnce(" get(");
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
  @DisplayName("keeps the type-use annotations the author wrote")
  class TypeUseAnnotations {

    /**
     * A client is generated into the annotated interface's own package, which the consumer is free
     * to mark {@code @NullMarked}. Inside one an unannotated type means non-null, so a dropped
     * {@code @Nullable} does not leave the facade's contract unstated - it states the opposite of
     * the interface the facade exists to mirror.
     */
    private final Compilation compilation =
        compile(
            JavaFileObjects.forSourceLines(
                "com.example.NullableApi",
                "package com.example;",
                "import org.higherkindedj.hkt.effect.EitherPath;",
                "import org.higherkindedj.spring.client.HkjHttpClient;",
                "import org.jspecify.annotations.Nullable;",
                "import org.springframework.web.bind.annotation.PathVariable;",
                "import org.springframework.web.service.annotation.GetExchange;",
                "import org.springframework.web.service.annotation.HttpExchange;",
                "@HttpExchange(\"/users\")",
                "@HkjHttpClient",
                "public interface NullableApi {",
                "  @GetExchange(\"/{id}\")",
                "  EitherPath<UserError, @Nullable UserDto> get(",
                "      @PathVariable String id, @Nullable String trace);",
                "}"),
            USER_DTO,
            USER_ERROR);

    @Test
    @DisplayName("the native interface keeps it in the success type it unwraps to")
    void nativeInterfaceKeepsIt() {
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.NullableApiHttpExchange")
          .contentsAsUtf8String()
          .contains("ResponseEntity<@Nullable UserDto> get(");
    }

    @Test
    @DisplayName("the facade keeps it in the parameters and the return it mirrors")
    void facadeKeepsIt() {
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .generatedSourceFile("com.example.NullableApiClient")
          .contentsAsUtf8String()
          .contains("EitherPath<UserError, @Nullable UserDto> get(");
      // A facade that narrows a parameter below the interface's rejects a call the interface
      // accepts, so it is not usable everywhere the thing it fronts is.
      assertThat(compilation)
          .generatedSourceFile("com.example.NullableApiClient")
          .contentsAsUtf8String()
          .contains("@Nullable String trace");
    }

    @Test
    @DisplayName("a type-parameter annotation does not leak into a type-argument position")
    void typeParameterAnnotationDoesNotLeak() {
      // The same TypeVariableName declares the client's parameter and is written as the type
      // argument of `NullRepo<T>` in `implements`. `NullRepo<@Marked T>` does not compile, so a
      // declaration annotation has to stay behind.
      Compilation marked =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.Marked",
                  "package com.example;",
                  "import java.lang.annotation.ElementType;",
                  "import java.lang.annotation.Target;",
                  "@Target(ElementType.TYPE_PARAMETER)",
                  "public @interface Marked {}"),
              JavaFileObjects.forSourceLines(
                  "com.example.MarkedRepo",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/items\")",
                  "@HkjHttpClient",
                  "public interface MarkedRepo<@Marked T> {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, T> get(@PathVariable String id);",
                  "}"),
              USER_ERROR);

      // The generated sources are compiled in this same run, so a leaked annotation fails here.
      assertThat(marked).succeeded();
      assertThat(marked)
          .generatedSourceFile("com.example.MarkedRepoClient")
          .contentsAsUtf8String()
          .doesNotContain("@Marked");
    }

    @Test
    @DisplayName("a type parameter keeps the annotated Object bound javapoet would strip")
    void typeParameterBoundKeepsIt() {
      Compilation generic =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.NullRepo",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.jspecify.annotations.Nullable;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/items\")",
                  "@HkjHttpClient",
                  "public interface NullRepo<T extends @Nullable Object> {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, T> get(@PathVariable String id);",
                  "}"),
              USER_ERROR);

      assertThat(generic).succeeded();
      // Stripped to `<T>`, the client no longer admits the `NullRepo<@Nullable UserDto>` the
      // interface it implements permits.
      assertThat(generic)
          .generatedSourceFile("com.example.NullRepoHttpExchange")
          .contentsAsUtf8String()
          .contains("interface NullRepoHttpExchange<T extends @Nullable Object>");
      assertThat(generic)
          .generatedSourceFile("com.example.NullRepoClient")
          .contentsAsUtf8String()
          .containsMatch("class NullRepoClient<T extends @Nullable Object>");
    }

    @Test
    @DisplayName("a generic client's own method keeps it on a use of the client's type variable")
    void typeVariableUseKeepsIt() {
      // A method read as a member of the interface that declares it still has each type variable
      // substituted, by itself, and javac's substitution drops the annotation on the use.
      Compilation generic =
          compile(
              JavaFileObjects.forSourceLines(
                  "com.example.NullRepo",
                  "package com.example;",
                  "import org.higherkindedj.hkt.effect.EitherPath;",
                  "import org.higherkindedj.spring.client.HkjHttpClient;",
                  "import org.jspecify.annotations.Nullable;",
                  "import org.springframework.web.bind.annotation.PathVariable;",
                  "import org.springframework.web.service.annotation.GetExchange;",
                  "import org.springframework.web.service.annotation.HttpExchange;",
                  "@HttpExchange(\"/items\")",
                  "@HkjHttpClient",
                  "public interface NullRepo<T extends @Nullable Object> {",
                  "  @GetExchange(\"/{id}\")",
                  "  EitherPath<UserError, @Nullable T> get(@PathVariable String id, @Nullable T or);",
                  "}"),
              USER_ERROR);

      assertThat(generic).succeeded();
      assertThat(generic)
          .generatedSourceFile("com.example.NullRepoHttpExchange")
          .contentsAsUtf8String()
          .contains("ResponseEntity<@Nullable T> get(");
      assertThat(generic)
          .generatedSourceFile("com.example.NullRepoClient")
          .contentsAsUtf8String()
          .contains("EitherPath<UserError, @Nullable T> get(String id, @Nullable T or)");
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
    @DisplayName("errors on a parameterised error type instead of generating uncompilable code")
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
