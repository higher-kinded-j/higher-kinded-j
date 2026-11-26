// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import static org.higherkindedj.spring.client.processor.RestatedSignature.CHILD;
import static org.higherkindedj.spring.client.processor.RestatedSignature.EXCHANGE;
import static org.higherkindedj.spring.client.processor.RestatedSignature.FACADE;
import static org.higherkindedj.spring.client.processor.RestatedSignature.overrides;
import static org.higherkindedj.spring.client.processor.RestatedSignature.suppressed;
import static org.higherkindedj.spring.client.processor.RestatedSignature.upstream;

import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.higherkindedj.spring.client.processor.RestatedSignature.Expected;

/**
 * Varargs parameters and raw types a client restates, each with what the generated files must carry
 * so that each takes the client's shape and none of them draws a warning. It is the table beside
 * {@link DeprecatedSignatures}, for a signature's shape rather than a deprecation.
 *
 * <p>The base suppresses its own warnings on the interface, so nothing of them is copied, and each
 * generated declaration answers for itself whether the base is alongside or in a jar.
 */
final class VarargsAndRawSignatures {

  /** The base module's types, beside {@code BaseApi}, which each shape declares. */
  private static final List<JavaFileObject> BASE_TYPES =
      List.of(
          upstream("ApiErr", "public class ApiErr {}"),
          upstream("UserDto", "public record UserDto(String id) {}"),
          upstream("GenErr", "public class GenErr<T> {}"),
          upstream(
              "GenSub", "@SuppressWarnings(\"rawtypes\") public class GenSub extends GenErr {}"),
          upstream(
              "Outer",
              "public class Outer<T> { public class Inner {} public static class Nested {} }"),
          upstream(
              "TakesArrayApi",
              """
              import org.higherkindedj.hkt.effect.EitherPath;
              import org.springframework.web.bind.annotation.RequestParam;
              import org.springframework.web.service.annotation.GetExchange;

              /**
               * The other half of a diamond: a base that takes an array where BaseApi has varargs.
               * Its name sorts after BaseApi's, so javac lists its method first among the client's.
               */
              public interface TakesArrayApi {
                @GetExchange("/find")
                EitherPath<ApiErr, String> find(@RequestParam("id") String[] ids);
              }
              """));

  private static final String FIND =
      """
        @GetExchange("/find")
        EitherPath<ApiErr, String> find(@RequestParam("id") String... ids);
      """;

  /** The client's own declaration of a method, beside those it inherits. */
  private static final String OWN_VARARGS =
      """
      package com.downstream;

      import com.upstream.ApiErr;
      import com.upstream.BaseApi;
      import org.higherkindedj.hkt.effect.EitherPath;
      import org.higherkindedj.spring.client.HkjHttpClient;
      import org.springframework.web.bind.annotation.RequestParam;
      import org.springframework.web.service.annotation.GetExchange;
      import org.springframework.web.service.annotation.HttpExchange;

      @HttpExchange("/users")
      @HkjHttpClient
      public interface ChildApi extends BaseApi {
        @GetExchange("/search")
        EitherPath<ApiErr, String> search(@RequestParam("q") String... terms);
      }
      """;

  private VarargsAndRawSignatures() {}

  /** A varargs parameter or a raw type a client restates. */
  enum Shape implements RestatedSignature {
    ARRAY(
        "an array parameter, the control",
        "",
        """
          @GetExchange("/find")
          EitherPath<ApiErr, String> find(@RequestParam("id") String[] ids);
        """,
        CHILD,
        Expected.in(
            FACADE, "@Override\\s+public EitherPath<ApiErr, String> find\\(String\\[] ids\\)"),
        Expected.in(EXCHANGE, "find\\(@RequestParam\\(\"id\"\\) String\\[] ids\\)"),
        Expected.notIn(FACADE, "SuppressWarnings"),
        Expected.notIn(EXCHANGE, "SuppressWarnings")),

    VARARGS(
        "a varargs parameter, inherited or the client's own",
        "",
        FIND,
        OWN_VARARGS,
        // Both declare it as the client does, so a call through the facade's own type may pass the
        // elements one by one.
        Expected.in(
            FACADE, "@Override\\s+public EitherPath<ApiErr, String> find\\(String\\.\\.\\. ids\\)"),
        Expected.in(
            FACADE,
            "@Override\\s+public EitherPath<ApiErr, String> search\\(String\\.\\.\\. terms\\)"),
        Expected.in(EXCHANGE, "find\\(@RequestParam\\(\"id\"\\) String\\.\\.\\. ids\\)"),
        Expected.in(EXCHANGE, "search\\(@RequestParam\\(\"q\"\\) String\\.\\.\\. terms\\)"),
        Expected.notIn(FACADE, "SuppressWarnings"),
        Expected.notIn(EXCHANGE, "SuppressWarnings")),

    VARARGS_NOT_REIFIABLE(
        "a varargs parameter of a type javac does not reify, beside those of types it does",
        "<T>",
        """
          @PostExchange("/lists")
          EitherPath<ApiErr, String> lists(@RequestBody List<String>... lists);

          @PostExchange("/below")
          EitherPath<ApiErr, String> below(@RequestBody List<? super UserDto>... lists);

          @PostExchange("/each")
          <X> EitherPath<ApiErr, String> each(@RequestBody X... items);

          @PostExchange("/grids")
          EitherPath<ApiErr, String> grids(@RequestBody List<String>[]... grids);

          @PostExchange("/inners")
          EitherPath<ApiErr, String> inners(@RequestBody Outer<String>.Inner... inners);

          @PostExchange("/bound")
          EitherPath<ApiErr, String> bound(@RequestBody T... items);

          @PostExchange("/any")
          EitherPath<ApiErr, String> any(@RequestBody List<?>... lists);

          @PostExchange("/objects")
          EitherPath<ApiErr, String> objects(@RequestBody List<? extends Object>... lists);
        """,
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import com.upstream.UserDto;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        public interface ChildApi extends BaseApi<UserDto> {}
        """,
        // javac reports possible heap pollution where each is declared: of a parameterised type, a
        // type variable, an array of one, or a class nested in one. The client binds T to UserDto,
        // and javac reifies a type whose arguments are all ? or ? extends Object.
        Expected.in(FACADE, overrides("unchecked", "EitherPath<ApiErr, String> lists\\(")),
        Expected.in(EXCHANGE, suppressed("unchecked", "ResponseEntity<String> lists\\(")),
        Expected.in(FACADE, overrides("unchecked", "EitherPath<ApiErr, String> below\\(")),
        Expected.in(EXCHANGE, suppressed("unchecked", "ResponseEntity<String> below\\(")),
        Expected.in(FACADE, overrides("unchecked", "<X> EitherPath<ApiErr, String> each\\(")),
        Expected.in(EXCHANGE, suppressed("unchecked", "<X> ResponseEntity<String> each\\(")),
        Expected.in(FACADE, overrides("unchecked", "EitherPath<ApiErr, String> grids\\(")),
        Expected.in(EXCHANGE, suppressed("unchecked", "ResponseEntity<String> grids\\(")),
        Expected.in(FACADE, overrides("unchecked", "EitherPath<ApiErr, String> inners\\(")),
        Expected.in(EXCHANGE, suppressed("unchecked", "ResponseEntity<String> inners\\(")),
        Expected.in(
            FACADE,
            "@Override\\s+public EitherPath<ApiErr, String> bound\\(UserDto\\.\\.\\. items\\)"),
        Expected.in(EXCHANGE, "@PostExchange\\(\"/bound\"\\)\\s+ResponseEntity<String> bound\\("),
        Expected.in(FACADE, "@Override\\s+public EitherPath<ApiErr, String> any\\("),
        Expected.in(EXCHANGE, "@PostExchange\\(\"/any\"\\)\\s+ResponseEntity<String> any\\("),
        Expected.in(FACADE, "@Override\\s+public EitherPath<ApiErr, String> objects\\("),
        Expected.in(
            EXCHANGE, "@PostExchange\\(\"/objects\"\\)\\s+ResponseEntity<String> objects\\(")),

    VARARGS_REDECLARED(
        "a varargs parameter the client redeclares as an array",
        "",
        FIND,
        """
        package com.downstream;

        import com.upstream.ApiErr;
        import com.upstream.BaseApi;
        import org.higherkindedj.hkt.effect.EitherPath;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.bind.annotation.RequestParam;
        import org.springframework.web.service.annotation.GetExchange;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        @SuppressWarnings("overrides")
        public interface ChildApi extends BaseApi {
          @Override
          @GetExchange("/find")
          EitherPath<ApiErr, String> find(@RequestParam("id") String[] ids);
        }
        """,
        // The facade follows the client, and still overrides the base's varargs method. javac reads
        // that warning's suppression from the class alone.
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"overrides\"\\)\\s+"
                + "public final class ChildApiClient implements ChildApi"),
        Expected.in(
            FACADE, "@Override\\s+public EitherPath<ApiErr, String> find\\(String\\[] ids\\)"),
        Expected.in(EXCHANGE, "find\\(@RequestParam\\(\"id\"\\) String\\[] ids\\)")),

    VARARGS_DIAMOND(
        "a method two bases declare, one with varargs and one with an array",
        "",
        FIND,
        """
        package com.downstream;

        import com.upstream.TakesArrayApi;
        import com.upstream.BaseApi;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        public interface ChildApi extends BaseApi, TakesArrayApi {}
        """,
        // A call through the client may pass the elements one by one, so both take varargs, which
        // takes an array as well. The facade then overrides TakesArrayApi's method.
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"overrides\"\\)\\s+"
                + "public final class ChildApiClient implements ChildApi"),
        Expected.in(
            FACADE, "@Override\\s+public EitherPath<ApiErr, String> find\\(String\\.\\.\\. ids\\)"),
        Expected.in(EXCHANGE, "find\\(@RequestParam\\(\"id\"\\) String\\.\\.\\. ids\\)")),

    RAW_TYPES(
        "a raw type in a parameter, the success type, a type argument, a wildcard's bound, a bound,"
            + " an enclosing type or a varargs parameter",
        "",
        """
          @PostExchange("/raw")
          @SuppressWarnings("rawtypes")
          EitherPath<ApiErr, UserDto> raw(@RequestBody List body);

          @GetExchange("/success")
          EitherPath<ApiErr, List> success(@PathVariable String id);

          @GetExchange("/maybe")
          MaybePath<Map<String, List>> maybe(@PathVariable String id);

          @PostExchange("/wildcard")
          EitherPath<ApiErr, UserDto> wildcard(@RequestBody List<? extends List> body);

          @PostExchange("/bound")
          <X extends Comparable<X> & List> EitherPath<ApiErr, UserDto> bound(@RequestBody X body);

          @PostExchange("/inner")
          EitherPath<ApiErr, UserDto> inner(@RequestBody Outer.Inner body);

          @PostExchange("/nested")
          EitherPath<ApiErr, UserDto> nested(@RequestBody Outer.Nested body);

          @PostExchange("/many")
          EitherPath<ApiErr, UserDto> many(@RequestBody List... bodies);
        """,
        CHILD,
        // The base's own suppression on raw, which the native method copies where the base is
        // alongside, merges with the native method's. A static nested class of a generic class is
        // not raw, and javac reifies a raw type, so a varargs one draws rawtypes alone.
        Expected.in(
            FACADE, overrides("rawtypes", "EitherPath<ApiErr, UserDto> raw\\(List body\\)")),
        Expected.in(
            EXCHANGE,
            suppressed("rawtypes", "ResponseEntity<UserDto> raw\\(@RequestBody List body")),
        Expected.notIn(EXCHANGE, "\"rawtypes\",\\s*\"rawtypes\""),
        Expected.in(FACADE, overrides("rawtypes", "EitherPath<ApiErr, List> success\\(")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<List> success\\(")),
        Expected.in(FACADE, overrides("rawtypes", "MaybePath<Map<String, List>> maybe\\(")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<Map<String, List>> maybe\\(")),
        Expected.in(FACADE, overrides("rawtypes", "EitherPath<ApiErr, UserDto> wildcard\\(")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<UserDto> wildcard\\(")),
        Expected.in(FACADE, overrides("rawtypes", "<X extends Comparable<X> & List> EitherPath")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "<X extends Comparable<X> & List> Response")),
        Expected.in(
            FACADE, overrides("rawtypes", "EitherPath<ApiErr, UserDto> inner\\(Outer.Inner")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<UserDto> inner\\(")),
        Expected.in(FACADE, "@Override\\s+public EitherPath<ApiErr, UserDto> nested\\("),
        Expected.in(
            EXCHANGE, "@PostExchange\\(\"/nested\"\\)\\s+ResponseEntity<UserDto> nested\\("),
        Expected.in(
            FACADE,
            overrides("rawtypes", "EitherPath<ApiErr, UserDto> many\\(List\\.\\.\\. bodies")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<UserDto> many\\("))),

    RAW_ERROR_TYPE(
        "a raw error type",
        "",
        """
          @GetExchange("/{id}")
          EitherPath<GenErr, UserDto> get(@PathVariable String id);
        """,
        CHILD,
        // The decoder's field names it as a type argument, and the constructor as a class literal,
        // which is never raw. The native method writes the success type alone.
        Expected.in(
            FACADE,
            suppressed("rawtypes", "private final ResponseErrorDecoder<GenErr> getDecoder;")),
        Expected.in(FACADE, overrides("rawtypes", "EitherPath<GenErr, UserDto> get\\(")),
        Expected.notIn(FACADE, "@SuppressWarnings\\([^)]*\\)\\s+public ChildApiClient\\("),
        Expected.notIn(EXCHANGE, "SuppressWarnings")),

    RAW_ERROR_CHAIN(
        "a raw error type with an @OnStatus override",
        "",
        """
          @GetExchange("/{id}")
          @OnStatus(value = 404, error = GenSub.class)
          EitherPath<GenErr, UserDto> get(@PathVariable String id);
        """,
        CHILD,
        // The status-dispatch chain names the error type as its type argument.
        Expected.in(
            FACADE,
            suppressed("rawtypes", "private final ResponseErrorDecoder<GenErr> getDecoder;")),
        Expected.in(FACADE, suppressed("rawtypes", "public ChildApiClient\\(")),
        Expected.in(FACADE, overrides("rawtypes", "EitherPath<GenErr, UserDto> get\\("))),

    RAW_CLIENT_BOUND(
        "a raw bound of a generic client's type parameter",
        "<T>",
        """
          @GetExchange("/{id}")
          EitherPath<ApiErr, T> get(@PathVariable String id);
        """,
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import java.util.List;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        @SuppressWarnings("rawtypes")
        public interface ChildApi<T extends List> extends BaseApi<T> {}
        """,
        // Both redeclare the client's type parameter. The native interface's suppression merges
        // into the one it copies from the client.
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"rawtypes\"\\)\\s+"
                + "public final class ChildApiClient<T extends List>"),
        Expected.in(
            EXCHANGE,
            "@SuppressWarnings\\(\"rawtypes\"\\)\\s+"
                + "public interface ChildApiHttpExchange<T extends List>"),
        Expected.notIn(EXCHANGE, "\"rawtypes\",\\s*\"rawtypes\""),
        Expected.in(FACADE, "@Override\\s+public EitherPath<ApiErr, T> get\\(")),

    RAW_TYPE_ARGUMENT(
        "a raw type the client binds a base's type variable to",
        "<T>",
        """
          @PostExchange("/one")
          EitherPath<ApiErr, String> one(@RequestBody T item);

          @PostExchange("/many")
          EitherPath<ApiErr, String> many(@RequestBody T... items);
        """,
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import java.util.List;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        @SuppressWarnings("rawtypes")
        public interface ChildApi extends BaseApi<List> {}
        """,
        // The client wrote the raw type; each restated method writes it out where T stood.
        Expected.in(FACADE, overrides("rawtypes", "EitherPath<ApiErr, String> one\\(List item\\)")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<String> one\\(")),
        Expected.in(
            FACADE, overrides("rawtypes", "EitherPath<ApiErr, String> many\\(List\\.\\.\\. items")),
        Expected.in(EXCHANGE, suppressed("rawtypes", "ResponseEntity<String> many\\(")));

    private final String description;
    private final String typeParameters;
    private final String members;
    private final String client;
    private final List<Expected> expected;

    Shape(
        String description,
        String typeParameters,
        String members,
        String client,
        Expected... expected) {
      this.description = description;
      this.typeParameters = typeParameters;
      this.members = members;
      this.client = client;
      this.expected = List.of(expected);
    }

    @Override
    public List<JavaFileObject> baseSources() {
      JavaFileObject baseApi =
          JavaFileObjects.forSourceString(
              "com.upstream.BaseApi",
              """
              package com.upstream;

              import java.util.List;
              import java.util.Map;
              import org.higherkindedj.hkt.effect.EitherPath;
              import org.higherkindedj.hkt.effect.MaybePath;
              import org.higherkindedj.spring.client.OnStatus;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.RequestBody;
              import org.springframework.web.bind.annotation.RequestParam;
              import org.springframework.web.service.annotation.GetExchange;
              import org.springframework.web.service.annotation.PostExchange;

              @SuppressWarnings({"rawtypes", "unchecked"})
              public interface BaseApi%s {
              %s
              }
              """
                  .formatted(typeParameters, members));
      return Stream.concat(BASE_TYPES.stream(), Stream.of(baseApi)).toList();
    }

    @Override
    public JavaFileObject client() {
      return JavaFileObjects.forSourceString("com.downstream.ChildApi", client);
    }

    @Override
    public List<Expected> expected() {
      return expected;
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
