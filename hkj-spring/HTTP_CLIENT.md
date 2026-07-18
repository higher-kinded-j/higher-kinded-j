# HTTP Client: `@HkjHttpClient` for Spring Boot

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Generated Artifacts](#generated-artifacts)
- [Supported Return Types](#supported-return-types)
- [Error Decoding](#error-decoding)
- [Per-Status Error Types (`@OnStatus`)](#per-status-error-types-onstatus)
- [Global Status Mapping (config)](#global-status-mapping-config)
- [Connection Configuration](#connection-configuration)
- [Resilience and Retry-After](#resilience-and-retry-after)
- [Streaming (SSE)](#streaming-sse)
- [Generic Clients](#generic-clients)
- [Security](#security)
- [Related Documentation](#related-documentation)

## Overview

The Effect Path return-value handlers are the **server** side: a controller returns an `EitherPath`
and a typed error becomes a 4xx/5xx response carrying `{"success": false, "error": <E>}`.
`@HkjHttpClient` is the **client** side. When this service calls another over HTTP, the response is
folded back into an Effect Path with the typed error decoded, so the typed error channel is
preserved end-to-end across services rather than collapsing into a status code that the caller has
to reverse-engineer.

You write a Path-typed `@HttpExchange` interface; the annotation processor generates the Spring
proxy, the decoding facade, and the configuration that wires them. You autowire the interface and
call it; failures arrive as a `Left` of your declared error type.

> This page is the reference. For a guided, narrative walkthrough see the hkj-book chapter
> [Declarative HTTP Clients](https://higher-kinded-j.github.io/spring/declarative_http_clients.html)
> (`hkj-book/src/spring/declarative_http_clients.md`).

## Quick Start

Annotate a Path-typed `@HttpExchange` interface:

```java
@HttpExchange("/users")
@HkjHttpClient
public interface UserClientApi {

  @GetExchange("/{id}")
  EitherPath<ApiError, UserDto> getUser(@PathVariable String id);

  @PostExchange                                                  // deferred on a virtual thread →
  VTaskPath<Either<ApiError, UserDto>> create(@RequestBody UserDto body);   // withRetry/timeout/…
}
```

Autowire the **interface itself** and use it. The generated `UserClientApiClient` is registered as a
bean of your interface type, so you never reference the generated class names:

```java
@Autowired UserClientApi userClientApi;          // the generated UserClientApiClient is injected

EitherPath<ApiError, UserDto> path = userClientApi.getUser("42");
path.run().fold(this::handleError, this::renderUser);   // typed error, no status-code archaeology
```

Point it at the target service with configuration (no `base-url` in the annotation):

```yaml
spring:
  http:
    serviceclient:
      userClientApi:                 # group = decapitalised interface name
        base-url: http://users.internal
        read-timeout: 2s
```

## Generated Artifacts

For an interface `Foo`, the processor emits three siblings:

| Generated type | Role |
| -------------- | ---- |
| `FooHttpExchange` | A native `@HttpExchange` interface with the Path return types unwrapped to `ResponseEntity<T>`, and the mapping/parameter annotations (`@GetExchange`, `@PathVariable`, …) copied through. Spring's `HttpServiceProxyFactory` proxies this. |
| `FooClient` | Implements your `Foo` interface. Each method dispatches to the matching `FooHttpExchange` method via `HkjClientExchange` and decodes a `Left` from the response envelope. Registered as the `Foo` bean. |
| `FooClientConfiguration` | A `@Configuration` that registers the group with Spring 7 `@ImportHttpServices` and exposes the `FooClient` bean. |

The codegen is deterministic (methods are collected via `getAllMembers`, de-duplicated by erased
signature, and sorted), so repeated builds produce byte-identical sources.

## Supported Return Types

| Declared return type | Folded into | Notes |
| -------------------- | ----------- | ----- |
| `EitherPath<E, T>` | `Right(body)` on 2xx, `Left(decoded)` on a 4xx/5xx envelope | Eager: the call runs when the method is invoked. |
| `VTaskPath<Either<E, T>>` | the same `Either`, deferred on a virtual thread | Lazy: layer `withRetry` / `timeout` / `withCircuitBreaker` on the returned path before running it. |
| `MaybePath<T>` | `Just(body)` on 2xx, `Nothing` on 404 or empty body | Other failures propagate as the original exception. |

The runtime translators live in `HkjClientExchange`:

```java
HkjClientExchange.either(Supplier<ResponseEntity<T>>, ResponseErrorDecoder<E>) → EitherPath<E, T>
HkjClientExchange.eitherVTask(Supplier<ResponseEntity<T>>, ResponseErrorDecoder<E>) → VTaskPath<Either<E, T>>
HkjClientExchange.maybe(Supplier<ResponseEntity<T>>) → MaybePath<T>
HkjClientExchange.vstream(Supplier<InputStream>, Class<T>, JsonMapper) → VStreamPath<T>
```

The generated facade calls these for you; they are also usable directly when you want a translator
without codegen.

## Error Decoding

A failed response (`RestClientResponseException`) is turned into your declared error type by a
`ResponseErrorDecoder<E>`. Rather than wiring one decoder bean per error type, the generated client
autowires a single `ResponseErrorDecoderFactory` and builds a per-method decoder for whatever error
type that method declares. Each per-method decoder is created **once**, in the generated client's
constructor, and reused for every call — a custom `ResponseErrorDecoderFactory` therefore sees only
construction-time state, not per-call state:

```java
@FunctionalInterface
public interface ResponseErrorDecoderFactory {
  <E> ResponseErrorDecoder<E> create(Class<E> errorType);
  default <E> ResponseErrorDecoder<E> plain(Class<E> errorType) { return create(errorType); }
}
```

The default bean is `JsonResponseErrorDecoderFactory`, backed by the application's Jackson
`JsonMapper`. It expects the server envelope `{"success": false, "error": <E>}` and deserialises
`<E>`. A concrete error type decodes with no annotations; a sealed `DomainError` hierarchy needs a
closed `@JsonTypeInfo`/`@JsonSubTypes` discriminator (see
[Jackson Serialization](JACKSON_SERIALIZATION.md#client-side-deserialization-hkjhttpclient)).

**Talking to non-HKJ servers.** Against a server that does not emit the envelope (or returns an empty
or foreign body) the default decoder raises `ResponseErrorDecodeException`. Register a custom
`ResponseErrorDecoder` (or a whole `ResponseErrorDecoderFactory` bean, replacing the default) that
maps the status and body to your error type. Each failure is presented to the decoder as a
`ClientErrorResponse` record exposing the status (`statusValue()`), headers, body, and a parsed
`retryAfter()`.

## Per-Status Error Types (`@OnStatus`)

Map individual statuses to distinct error subtypes on a single method with the repeatable `@OnStatus`
annotation. Each subtype must be assignable to the method's declared error type:

```java
@GetExchange("/{id}")
@OnStatus(value = 404, error = UserNotFoundError.class)
@OnStatus(value = 409, error = ConflictError.class)
EitherPath<DomainError, UserDto> getUser(@PathVariable String id);
```

A method's `@OnStatus` is built with the factory's `plain(...)` path, so it bypasses the global
configuration map below and always wins.

## Global Status Mapping (config)

The client-side analogue of the server's `hkj.web.error-status-mappings`: map a status to an error
type once, for every client, with no annotations.

```yaml
hkj:
  client:
    status-error-mappings:
      404: com.example.UserNotFoundError
      429: com.example.RateLimitError
```

For each method, a configured status whose type is **assignable** to that method's declared error
type decodes into the subtype; non-assignable mappings and unmapped statuses fall back to the
declared type. A class that cannot be resolved fails fast at startup. Full key reference:
[CONFIGURATION.md](CONFIGURATION.md#hkjclientstatus-error-mappings).

**Precedence (highest first):** `@OnStatus` (per-method) → `hkj.client.status-error-mappings`
(global) → the method's declared error type. The global map only ever narrows to a subtype of what
the method already declares; it never changes the declared error channel.

## Connection Configuration

Base URL, timeouts, default headers and API versioning are standard Spring Boot
`spring.http.serviceclient.<group>.*` properties, bound by the `spring-boot-restclient` module that
the starter pulls in. `@HkjHttpClient` only generates the `@ImportHttpServices` group those keys
target. The group key is the decapitalised interface name (`UserClientApi` → `userClientApi`) unless
you set `@HkjHttpClient(group = "...")`. See
[CONFIGURATION.md](CONFIGURATION.md#springhttpserviceclientgroup) for the key reference.

## Resilience and Retry-After

A `VTaskPath<Either<E, T>>` method is deferred, so you can compose resilience before running it:

```java
userClientApi.create(body)              // VTaskPath<Either<ApiError, UserDto>>
    .withRetry(3)
    .timeout(Duration.ofSeconds(2))
    .run();
```

`ClientErrorResponse.retryAfter()` parses a server `Retry-After` header (delta-seconds or HTTP-date)
into an `Optional<Duration>`. Read it inside a custom decoder to seed a back-off schedule rather than
retrying blindly.

## Streaming (SSE)

A streaming endpoint that the server renders with a `VStreamPath` (its `VStreamPathReturnValueHandler`
SSE format) is consumed with the runtime translator. Decoding is lazy and resource-safe: the response
stream is opened when the path is pulled and closed when it is drained to completion or fails (bracket
semantics over `VStream.unfold`):

```java
VStreamPath<Tick> ticks =
    HkjClientExchange.vstream(
        () -> restClient.get().uri("/ticks").retrieve().body(InputStream.class),
        Tick.class, jsonMapper);

List<Tick> all = ticks.run().toList();   // draining terminal
```

Each `data:` frame decodes into the element type; an `event: complete` frame ends the stream; an
`event: error` frame fails it with `SseStreamException`. Each line is capped at 1 MiB to bound memory
against a hostile or buggy upstream. Prefer a draining or `take`-bounded terminal: a short-circuiting
terminal such as `headOption()`/`find(...)` may return before the stream completes and leave the
HTTP response open.

## Generic Clients

A generic `@HkjHttpClient` interface is supported **codegen-only**: the native interface and the
facade carry the type parameters, but the `@ImportHttpServices`/`@Bean` wiring is skipped, because a
generic client cannot be registered as a singleton bean. Instantiate the generated facade for a
concrete type rather than autowiring it.

## Security

The client deserialises error bodies from another service, so treat them as untrusted input. Pin a
**closed** discriminator (`@JsonTypeInfo(use = Id.NAME)`) on sealed error types and never enable
`Id.CLASS` / default typing for a type decoded from a remote response. SSE frames are length-bounded.
Scope any outbound credentials to the trust boundary you are crossing. See
[SECURITY.md](SECURITY.md#outbound-calls-hkjhttpclient) for the full rationale.

## Related Documentation

- [Declarative HTTP Clients (hkj-book guide)](https://higher-kinded-j.github.io/spring/declarative_http_clients.html) - narrative walkthrough
- [Configuration Reference](CONFIGURATION.md#client-http-configuration) - `hkj.client.*` and `spring.http.serviceclient.*` keys
- [Jackson Serialization](JACKSON_SERIALIZATION.md#client-side-deserialization-hkjhttpclient) - decoding error bodies
- [Security Integration](SECURITY.md#outbound-calls-hkjhttpclient) - trust boundaries for outbound calls
- [hkj-spring README](README.md) - module overview
