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
 * Deprecated methods and types a client restates, each with what the generated files must carry so
 * that none of them draws a warning. {@link VarargsAndRawSignatures} is the table beside it for a
 * signature's shape.
 */
final class DeprecatedSignatures {

  /** The base module's types, beside {@code BaseApi}, which each shape declares. */
  static final List<JavaFileObject> BASE_TYPES =
      List.of(
          upstream("ApiErr", "public class ApiErr {}"),
          upstream("UserDto", "public record UserDto(String id) {}"),
          upstream("OldQuery", "@Deprecated public record OldQuery(String text) {}"),
          upstream("OldDto", "@Deprecated public record OldDto(String id) {}"),
          upstream("OldErr", "@Deprecated public class OldErr {}"),
          upstream("Gone", "@Deprecated(forRemoval = true) public class Gone extends ApiErr {}"),
          upstream("Doomed", "@Deprecated(forRemoval = true) public interface Doomed {}"),
          upstream("OldBound", "@Deprecated public interface OldBound {}"),
          upstream("Outer", "@Deprecated public class Outer { public record Inner(String id) {} }"),
          upstream(
              "OldMark",
              "@Deprecated @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy"
                  + ".RUNTIME) public @interface OldMark {}"),
          upstream(
              "DoomedMark",
              "@Deprecated(forRemoval = true) @java.lang.annotation.Retention("
                  + "java.lang.annotation.RetentionPolicy.RUNTIME) public @interface DoomedMark {}"),
          upstream(
              "Legacy",
              """
              /**
               * A type-use mark, deprecated by its javadoc alone.
               *
               * @deprecated mark nothing
               */
              @SuppressWarnings("dep-ann")
              @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
              public @interface Legacy {}
              """));

  private static final String GET =
      """
        @GetExchange("/{id}")
        EitherPath<ApiErr, UserDto> get(@PathVariable String id);
      """;

  /** One suppression of both categories, in name order. */
  private static final String BOTH =
      "@SuppressWarnings\\(\\{\\s*\"deprecation\",\\s*\"removal\"\\s*}\\)";

  private static final String CONFIGURATION = "com.downstream.ChildApiClientConfiguration";

  private DeprecatedSignatures() {}

  /** A deprecated method or type a client restates. */
  enum Shape implements RestatedSignature {
    NOTHING_DEPRECATED(
        "nothing deprecated, the control",
        "",
        GET,
        CHILD,
        Expected.notIn(FACADE, "SuppressWarnings"),
        Expected.notIn(EXCHANGE, "SuppressWarnings"),
        Expected.notIn(CONFIGURATION, "SuppressWarnings")),

    DEPRECATED_METHODS(
        "a method deprecated, for removal, or by its javadoc tag alone",
        "",
        """
          @Deprecated(forRemoval = true)
          @GetExchange("/{id}")
          EitherPath<ApiErr, UserDto> get(@PathVariable String id);

          @Deprecated
          @GetExchange("/{id}/profile")
          EitherPath<ApiErr, UserDto> profile(@PathVariable String id);

          /**
           * Reads the settings.
           *
           * @deprecated read the profile
           */
          @GetExchange("/{id}/settings")
          EitherPath<ApiErr, UserDto> settings(@PathVariable String id);
        """,
        CHILD,
        // The facade overrides each and calls the native method, which carries the copied
        // @Deprecated; a javadoc tag is not copied, so only the override reports that one.
        Expected.in(FACADE, overrides("removal", "EitherPath<ApiErr, UserDto> get\\(")),
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, UserDto> profile\\(")),
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, UserDto> settings\\(")),
        Expected.in(EXCHANGE, "@Deprecated\\(\\s*forRemoval = true\\s*\\)\\s+@GetExchange"),
        Expected.notIn(EXCHANGE, "SuppressWarnings")),

    REDECLARED(
        "a method deprecated for removal that the client redeclares with its own suppression",
        "",
        """
          @Deprecated(forRemoval = true)
          @GetExchange("/{id}")
          EitherPath<ApiErr, UserDto> get(@PathVariable String id);
        """,
        """
        package com.downstream;

        import com.upstream.ApiErr;
        import com.upstream.BaseApi;
        import com.upstream.UserDto;
        import org.higherkindedj.hkt.effect.EitherPath;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.bind.annotation.PathVariable;
        import org.springframework.web.service.annotation.GetExchange;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        public interface ChildApi extends BaseApi {
          @Override
          @SuppressWarnings("removal")
          @GetExchange("/{id}")
          EitherPath<ApiErr, UserDto> get(@PathVariable String id);
        }
        """,
        // The facade still overrides the base's method through the client's.
        Expected.in(FACADE, overrides("removal", "EitherPath<ApiErr, UserDto> get\\("))),

    SIGNATURE_TYPES(
        "a deprecated type in a parameter, a type argument, the success type, an enclosing class"
            + " or a type parameter's bound",
        "",
        """
          @PostExchange("/search")
          EitherPath<ApiErr, UserDto> search(@RequestBody OldQuery query);

          @PostExchange("/many")
          EitherPath<ApiErr, UserDto> many(@RequestBody List<OldQuery> queries);

          @GetExchange("/{id}/old")
          EitherPath<ApiErr, OldDto> old(@PathVariable String id);

          @GetExchange("/{id}/inner")
          MaybePath<Outer.Inner> inner(@PathVariable String id);

          @PostExchange("/save")
          <X extends Doomed> EitherPath<ApiErr, UserDto> save(@RequestBody X body);

          @PostExchange("/rank")
          <X extends Comparable<X> & Doomed> EitherPath<ApiErr, UserDto> rank(@RequestBody X body);
        """,
        CHILD,
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, UserDto> search\\(")),
        Expected.in(
            EXCHANGE, suppressed("deprecation", "ResponseEntity<UserDto> search\\(@RequestBody")),
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, UserDto> many\\(")),
        Expected.in(EXCHANGE, suppressed("deprecation", "ResponseEntity<UserDto> many\\(")),
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, OldDto> old\\(")),
        Expected.in(EXCHANGE, suppressed("deprecation", "ResponseEntity<OldDto> old\\(")),
        Expected.in(FACADE, overrides("deprecation", "MaybePath<Outer.Inner> inner\\(")),
        Expected.in(EXCHANGE, suppressed("deprecation", "ResponseEntity<Outer.Inner> inner\\(")),
        Expected.in(FACADE, overrides("removal", "<X extends Doomed> EitherPath")),
        Expected.in(EXCHANGE, suppressed("removal", "<X extends Doomed> ResponseEntity")),
        Expected.in(FACADE, overrides("removal", "<X extends Comparable<X> & Doomed> EitherPath")),
        Expected.in(
            EXCHANGE, suppressed("removal", "<X extends Comparable<X> & Doomed> ResponseEntity"))),

    ERROR_TYPES(
        "a deprecated error type, or @OnStatus error type",
        "",
        """
          @GetExchange("/{id}")
          EitherPath<OldErr, UserDto> get(@PathVariable String id);

          @GetExchange("/{id}/profile")
          @OnStatus(value = 404, error = Gone.class)
          EitherPath<ApiErr, UserDto> profile(@PathVariable String id);
        """,
        CHILD,
        // Each decoder names its error classes, in its field and in the constructor that builds
        // it. The native method writes the success type alone.
        Expected.in(FACADE, overrides("deprecation", "EitherPath<OldErr, UserDto> get\\(")),
        Expected.in(
            FACADE,
            "@SuppressWarnings\\(\"deprecation\"\\)\\s+"
                + "private final ResponseErrorDecoder<OldErr> getDecoder;"),
        Expected.in(
            FACADE,
            "@SuppressWarnings\\(\\{\\s*\"deprecation\",\\s*\"removal\"\\s*}\\)\\s+"
                + "public ChildApiClient\\("),
        Expected.in(FACADE, "@Override\\s+public EitherPath<ApiErr, UserDto> profile\\("),
        Expected.notIn(
            FACADE, "@SuppressWarnings\\([^)]*\\)\\s+private final ResponseErrorDecoder<ApiErr>"),
        Expected.notIn(EXCHANGE, "SuppressWarnings")),

    CATEGORY_ORDER(
        "both categories drawn, by a copied annotation and by the signature, in either order",
        "",
        """
          @OldMark
          @PostExchange("/marked")
          EitherPath<ApiErr, UserDto> marked(@RequestBody Doomed body);

          @DoomedMark
          @Deprecated(forRemoval = true)
          @PostExchange("/doomed")
          EitherPath<ApiErr, UserDto> doomed(@RequestBody OldQuery query);
        """,
        CHILD,
        // One suppression each, in name order, whichever draws first: the signature before the
        // copies, the copies before the signature, an override before a named type.
        Expected.in(EXCHANGE, "@OldMark\\s+@PostExchange\\(\"/marked\"\\)\\s+" + BOTH),
        Expected.in(
            EXCHANGE,
            "@DoomedMark\\s+@Deprecated\\(\\s*forRemoval = true\\s*\\)\\s+"
                + "@PostExchange\\(\"/doomed\"\\)\\s+"
                + BOTH),
        Expected.in(
            FACADE, "@Override\\s+" + BOTH + "\\s+public EitherPath<ApiErr, UserDto> doomed\\(")),

    CLIENT_SUPPRESSION(
        "a deprecated parameter type the client suppresses for itself",
        "",
        """
          @PostExchange("/search")
          EitherPath<ApiErr, UserDto> search(@RequestBody OldQuery query);
        """,
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        @SuppressWarnings("deprecation")
        public interface ChildApi extends BaseApi {}
        """,
        // The client's suppression is copied onto the native interface alone; the facade's method
        // answers for itself.
        Expected.in(EXCHANGE, "@SuppressWarnings\\(\"deprecation\"\\)\\s+public interface"),
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, UserDto> search\\("))),

    CLIENT_BOUND(
        "a deprecated bound of a generic client's type parameter",
        "<T>",
        """
          @GetExchange("/{id}")
          EitherPath<ApiErr, T> get(@PathVariable String id);
        """,
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import com.upstream.OldBound;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        @SuppressWarnings("deprecation")
        public interface ChildApi<T extends OldBound> extends BaseApi<T> {}
        """,
        // Both redeclare the client's type parameter. The native interface's suppression merges
        // into the one it copies from the client.
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"deprecation\"\\)\\s+"
                + "public final class ChildApiClient<T extends OldBound>"),
        Expected.in(
            EXCHANGE,
            "@SuppressWarnings\\(\"deprecation\"\\)\\s+"
                + "public interface ChildApiHttpExchange<T extends OldBound>"),
        Expected.notIn(EXCHANGE, "\"deprecation\",\\s*\"deprecation\"")),

    OWN_NESTED_TYPE(
        "a deprecated type nested in the client, which its own source names with no warning",
        "<T>",
        """
          @GetExchange("/{id}")
          EitherPath<ApiErr, T> get(@PathVariable String id);
        """,
        """
        package com.downstream;

        import com.upstream.ApiErr;
        import com.upstream.BaseApi;
        import org.higherkindedj.hkt.effect.EitherPath;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.bind.annotation.RequestBody;
        import org.springframework.web.service.annotation.HttpExchange;
        import org.springframework.web.service.annotation.PostExchange;

        @HttpExchange("/users")
        @HkjHttpClient
        public interface ChildApi<T extends ChildApi.Old> extends BaseApi<T> {
          @Deprecated
          interface Old {}

          @PostExchange("/save")
          EitherPath<ApiErr, T> save(@RequestBody Old body);
        }
        """,
        // javac exempts a use within the outermost class that declares the type, which the
        // generated files are not.
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"deprecation\"\\)\\s+"
                + "public final class ChildApiClient<T extends ChildApi.Old>"),
        Expected.in(
            EXCHANGE,
            "@SuppressWarnings\\(\"deprecation\"\\)\\s+"
                + "public interface ChildApiHttpExchange<T extends ChildApi.Old>"),
        Expected.in(FACADE, overrides("deprecation", "EitherPath<ApiErr, T> save\\(ChildApi.Old")),
        Expected.in(EXCHANGE, suppressed("deprecation", "ResponseEntity<T> save\\(@RequestBody"))),

    DEPRECATED_CLIENT(
        "a deprecated client",
        "",
        GET,
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @Deprecated
        @HttpExchange("/users")
        @HkjHttpClient
        public interface ChildApi extends BaseApi {}
        """,
        // The facade implements the client and keeps the native interface, which carries the
        // copied @Deprecated; the configuration names both.
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"deprecation\"\\)\\s+"
                + "public final class ChildApiClient implements ChildApi"),
        Expected.in(
            CONFIGURATION,
            "@Generated\\s+@SuppressWarnings\\(\"deprecation\"\\)\\s+@Configuration"),
        Expected.in(EXCHANGE, "@Deprecated\\s+@HttpExchange")),

    NESTED_CLIENT(
        "a client nested in a class deprecated for removal",
        "",
        GET,
        "Apis",
        """
        package com.downstream;

        import com.upstream.BaseApi;
        import org.higherkindedj.spring.client.HkjHttpClient;
        import org.springframework.web.service.annotation.HttpExchange;

        @Deprecated(forRemoval = true)
        public class Apis {
          @HttpExchange("/users")
          @HkjHttpClient
          public interface ChildApi extends BaseApi {}
        }
        """,
        Expected.in(
            FACADE,
            "@Generated\\s+@SuppressWarnings\\(\"removal\"\\)\\s+"
                + "public final class ChildApiClient implements Apis.ChildApi"),
        Expected.in(
            CONFIGURATION, "@Generated\\s+@SuppressWarnings\\(\"removal\"\\)\\s+@Configuration")),

    TYPE_USE_TAG(
        "a type-use annotation deprecated by its javadoc tag alone",
        "",
        """
          @GetExchange("/{id}")
          EitherPath<ApiErr, @Legacy UserDto> get(@PathVariable @Legacy String id);
        """,
        CHILD,
        // Left off, as a type-use annotation deprecated by @Deprecated is.
        Expected.in(FACADE, "@Override\\s+public EitherPath<ApiErr, UserDto> get\\(String id\\)"),
        Expected.notIn(FACADE, "Legacy"),
        Expected.notIn(EXCHANGE, "Legacy"));

    private final String description;
    private final String typeParameters;
    private final String members;
    private final String clientType;
    private final String client;
    private final List<Expected> expected;

    Shape(
        String description,
        String typeParameters,
        String members,
        String client,
        Expected... expected) {
      this(description, typeParameters, members, "ChildApi", client, expected);
    }

    Shape(
        String description,
        String typeParameters,
        String members,
        String clientType,
        String client,
        Expected... expected) {
      this.description = description;
      this.typeParameters = typeParameters;
      this.members = members;
      this.clientType = clientType;
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
              import org.higherkindedj.hkt.effect.EitherPath;
              import org.higherkindedj.hkt.effect.MaybePath;
              import org.higherkindedj.spring.client.OnStatus;
              import org.springframework.web.bind.annotation.PathVariable;
              import org.springframework.web.bind.annotation.RequestBody;
              import org.springframework.web.service.annotation.GetExchange;
              import org.springframework.web.service.annotation.PostExchange;

              @SuppressWarnings({"deprecation", "removal", "dep-ann"})
              public interface BaseApi%s {
              %s
              }
              """
                  .formatted(typeParameters, members));
      return Stream.concat(BASE_TYPES.stream(), Stream.of(baseApi)).toList();
    }

    @Override
    public JavaFileObject client() {
      return JavaFileObjects.forSourceString("com.downstream." + clientType, client);
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
