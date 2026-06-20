# Declarative HTTP Clients: Typed Errors Across Services
## _Closing the Loop with `@HkjHttpClient`_

~~~admonish info title="What You'll Learn"
- Why a typed error collapses into a raw status code at the client boundary, and how `@HkjHttpClient` preserves it
- How to declare an HTTP client that returns `EitherPath`, `VTaskPath`, or `MaybePath`
- How to wire a client by configuration (base URL, timeouts) and autowire it by its own interface
- How a response decodes back into a typed error, and the three ways to override the mapping: `@OnStatus`, `hkj.client.status-error-mappings`, and a custom decoder
- How the `VTaskPath` variant adds retries, circuit breakers, and a `Retry-After` hook
- How to consume Server-Sent Events into a `VStreamPath`
~~~

~~~admonish note title="Before You Start"
This page builds on the Effect Path API. If `Either`, `EitherPath`, `.run()` / `.fold(...)`, or the
"railway" model are new, read [The Effect Path API](../effect/effect_path_overview.md) first: the
rest assumes your code already returns typed-error results. It also uses Spring's declarative HTTP
client (`@HttpExchange`, `@ImportHttpServices`), which needs **Spring Boot 4.1+ / Spring Framework 7**.
~~~

~~~admonish example title="See Example Code"
A complete, runnable client application that calls the server example lives in the [hkj-spring client-example module](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-spring/client-example/src/main/java/org/higherkindedj/spring/clientexample).
~~~

## Overview

The [Spring Boot Integration](spring_boot_integration.md) chapter is about the **inbound** edge: a controller returns `Either<DomainError, User>` and the framework encodes it as an HTTP response. `@HkjHttpClient` is the **outbound** inverse. When one service calls another, it decodes that response back into the same typed error, so the error channel survives the network hop.

This completes the loop. Both sides of a service-to-service call now speak the same language: errors are data, visible in the type signature, on the wire and back again.

---

## The Problem

You built a service that speaks typed errors. Now a second service calls it. With a plain Spring HTTP client, B's carefully typed `Left(UserNotFoundError)` arrives as an exception, and A is back to reading status codes:

```java
// Service A, calling service B with a raw RestClient
try {
    UserDto user = restClient.get().uri("/users/{id}", id).retrieve().body(UserDto.class);
    return Either.right(user);
} catch (RestClientResponseException ex) {
    // The typed error is gone. Reconstruct it from the status code.
    if (ex.getStatusCode().value() == 404) return Either.left(new UserNotFoundError(id));
    if (ex.getStatusCode().value() == 409) return Either.left(new ConflictError(id));
    throw ex; // ...and hope you covered every case
}
```

The typed error channel that the whole library is built around stops at the boundary. Every caller re-derives the same error from the same status code, by hand, forever.

---

## The Solution

Declare a single interface. Annotate it with `@HkjHttpClient` alongside the standard Spring `@HttpExchange` declarative-client annotations, and return an Effect Path:

```java
@HttpExchange("/users")
@HkjHttpClient
public interface UserClientApi {

  @GetExchange("/{id}")
  EitherPath<ApiError, UserDto> getUser(@PathVariable String id);

  @PostExchange
  VTaskPath<Either<ApiError, UserDto>> create(@RequestBody UserDto body);
}
```

Here `UserDto` is your own response record and `ApiError` your own error type. The caller then stays on the rails, on the success or failure track rather than a thrown status code:

```java
EitherPath<ApiError, UserDto> path = userClientApi.getUser("42");

// .run() performs the call and yields Either<ApiError, UserDto>;
// .fold collapses the two arms into one value: Left -> handleError, Right -> renderUser.
return path.run().fold(this::handleError, this::renderUser);
```

---

## The Railway View

`@HkjHttpClient` is a mirror of the server-side return-value handlers. The server **encodes** a typed error into a status code plus a JSON envelope; the client **decodes** it back:

```
   SERVER (hkj-spring)                                 CLIENT (@HkjHttpClient)

   EitherPath<ApiError, UserDto>                       EitherPath<ApiError, UserDto>
        │                                                     ▲
        │  ErrorStatusCodeStrategy                            │  ResponseErrorDecoder
        │  + {"success":false,"error":…}                      │  reads the envelope
        ▼                                                     │
   404  {"success":false,"error":{…}}  ────── HTTP ─────▶  404  {"success":false,"error":{…}}
```

---

## Quickstart

### Step 1: Add the Starter

The client lives in `hkj-spring-boot-starter`; if you already have it for the server side, you have the client too.

```gradle
// build.gradle.kts
dependencies {
    implementation("io.github.higher-kinded-j:hkj-spring-boot-starter:LATEST_VERSION")
}
```

### Step 2: Declare and Configure

Annotate the interface (as above), then set the base URL and timeouts in configuration. The group name defaults to the decapitalised interface name (`userClientApi`), or set it explicitly with `@HkjHttpClient(group = "...")`:

```yaml
spring:
  http:
    serviceclient:
      userClientApi:
        base-url: http://users.internal
        connect-timeout: 2s
        read-timeout: 2s
```

That is all the wiring. The generated `…ClientConfiguration` (see [What Gets Generated](#what-gets-generated)) declares the `@ImportHttpServices` group and is component-scanned along with the rest of your application, because it sits in the same package as your interface. Only if your client interfaces live outside your `@SpringBootApplication`'s scanned packages do you add an explicit `@ImportHttpServices(basePackages = "...")`.

### Step 3: Autowire by Interface

```java
@Autowired UserClientApi userClientApi;   // the generated UserClientApiClient is injected

EitherPath<ApiError, UserDto> path = userClientApi.getUser("42");
```

That is the whole happy path: annotate, configure, autowire.

---

## What Gets Generated

For an interface `UserClientApi`, the annotation processor generates three siblings in the same package:

~~~admonish note title="Generated artifacts"
- **`UserClientApiHttpExchange`** : a native Spring `@HttpExchange` interface with the same methods, the return type unwrapped to `ResponseEntity<T>` (where `T` is the success type of your Path), and every mapping annotation copied through. This is the piece Spring proxies.
- **`UserClientApiClient`** : implements `UserClientApi`, calling the proxied native interface and folding each outcome into the declared Path (a 2xx body to the success arm, a `RestClientResponseException` to a decoded typed error).
- **`UserClientApiClientConfiguration`** : a `@Configuration` that registers the native interface as an `@ImportHttpServices` group and exposes the client as a bean.
~~~

Every supported return type maps to the same native method shape, `ResponseEntity<T>`, where `T` is the Path's success type:

| Your method returns | Generated native method |
|---|---|
| `EitherPath<E, T>` | `ResponseEntity<T>` |
| `VTaskPath<Either<E, T>>` | `ResponseEntity<T>` |
| `MaybePath<T>` | `ResponseEntity<T>` |

You never reference the generated names. You autowire your own interface.

---

## Choosing a Return Type

Each method picks how the call is run and what shape the result takes. (`Right`/`Left` are the success and typed-error arms of `Either`; `Just`/`Nothing` are present/absent for `Maybe`.)

| Return type | Evaluation | 2xx | non-2xx | Use when |
|---|---|---|---|---|
| `EitherPath<E, T>` | Eager, blocks the calling thread | `Right(body)` | `Left(decoded error)` | A straightforward request/response call |
| `VTaskPath<Either<E, T>>` | Deferred onto a virtual thread | `Right(body)` | `Left(decoded error)` | You want retries, a circuit breaker, a timeout, or a `Retry-After` hook |
| `MaybePath<T>` | Eager, blocks the calling thread | `Just(body)` | 404 → `Nothing` | Absence is normal and untyped (a lookup that may miss) |

~~~admonish note title="Semantics worth pinning"
- **Empty 2xx body.** `EitherPath`/`VTaskPath` yield `Right(null)`; `MaybePath` yields `Nothing`. If an endpoint may legitimately return no body, declare `T` accordingly or guard the success value.
- **`MaybePath` only treats 404 as absence.** Other non-2xx statuses propagate as the original exception. `MaybePath` models "might be missing", not "might fail".
- **Thread-safety.** The generated client is a stateless singleton, safe for concurrent use; the eager variants block the caller, the deferred `VTaskPath` runs on a virtual thread when the task is run.
~~~

~~~admonish note title="Exception boundary"
Only an HTTP **error response** (`RestClientResponseException`) is folded into the typed error arm. Transport failures (connection refused, timeout) and undecodable bodies are not typed domain errors, so they propagate: synchronously from the eager `EitherPath`/`MaybePath` translators, and as a failed task from the deferred `VTaskPath`/`VStreamPath` translators.
~~~

---

## Decoding Errors

The default decoder reads the server's `{"success":false,"error":…}` envelope and binds the `error` node to your method's declared error type.

A **concrete** error type binds with no extra annotations. A **sealed** `DomainError` hierarchy needs Jackson polymorphic type information so the decoder can pick the subtype:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserNotFoundError.class, name = "not-found"),
    @JsonSubTypes.Type(value = ValidationError.class, name = "validation")
})
public sealed interface DomainError permits UserNotFoundError, ValidationError { }
```

~~~admonish warning title="Use a closed discriminator"
The error body comes from another service, so it is not fully trusted, and the discriminator
decides which class Jackson instantiates. With `Id.NAME` (above) the discriminator is a **logical
name** you registered, e.g. `{"type":"not-found"}`: Jackson resolves it against your `@JsonSubTypes`
list, so a response can only select one of your declared error subtypes. With `Id.CLASS` /
`Id.MINIMAL_CLASS` (or `ObjectMapper` default typing) the discriminator is instead a **fully
qualified class name** on the wire, e.g. `{"@class":"com.evil.Gadget"}`, which Jackson loads and
constructs: a malicious server could then have you instantiate any class on your classpath, the
classic Jackson deserialisation-gadget vector. Always use `Id.NAME` with explicit `@JsonSubTypes`
here; never `Id.CLASS`/`Id.MINIMAL_CLASS` or default typing.
~~~

### Overriding the status → error mapping

There are three ways to override how a status maps to an error type. They apply **in precedence order: a per-method `@OnStatus` beats the global `hkj.client.status-error-mappings`, which beats the method's declared type.**

#### 1. Per method: `@OnStatus`

**The problem:** a single endpoint returns different error subtypes for different statuses.

**The solution:** annotate the method. Each `error()` must be assignable to the method's declared error type (the processor checks this at compile time):

```java
@GetExchange("/{id}")
@OnStatus(value = 404, error = UserNotFoundError.class)
@OnStatus(value = 409, error = ConflictError.class)
EitherPath<DomainError, UserDto> getUser(@PathVariable String id);
```

#### 2. Global: `hkj.client.status-error-mappings`

**The problem:** the same status maps to the same error type across every client, and repeating `@OnStatus` everywhere is noise.

**The solution:** the client-side analogue of the server's `hkj.web.error-status-mappings`. Map a status to an error type once:

```yaml
hkj:
  client:
    status-error-mappings:
      404: com.example.UserNotFoundError
      429: com.example.RateLimitError
```

For each method, a configured status whose type is **assignable** to that method's declared error type decodes into the subtype; non-assignable and unmapped statuses fall back to the declared type. An unresolvable class name fails fast at startup.

#### 3. Wholesale: a custom decoder bean

**The problem:** you call a non-HKJ server that does not emit the envelope.

**The solution:** supply a `ResponseErrorDecoder` (or replace the `ResponseErrorDecoderFactory` bean) that maps the status and body to your error type however you like. Without one, a foreign or empty body raises `ResponseErrorDecodeException`.

---

## Resilience with `VTaskPath`

Because the `VTaskPath` variant defers the call onto a virtual thread, the standard resilience combinators compose directly on the result:

```java
Either<ApiError, UserDto> result =
    userClientApi.create(body)                 // VTaskPath<Either<ApiError, UserDto>>
        .withRetry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(100)))
        .withCircuitBreaker(breaker)
        .timeout(Duration.ofSeconds(2))
        .unsafeRun();
```

`Retry-After` is a **hook**, not automatic: when the server signals back-off (typically an `HttpHeaderCarrier` error on a 429 or 503), a custom decoder reads it via `ClientErrorResponse.retryAfter()` and feeds it into the retry policy.

~~~admonish info title="Hands-On Learning"
The runnable [end-to-end test](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/client-example/src/test/java/org/higherkindedj/spring/clientexample/UserClientApiEndToEndTest.java) drives the generated client against a `MockRestServiceServer`: a 200 becomes `Right`, a 404 envelope becomes `Left(ApiError)`, and the deferred `create` posts a body and yields `Right` when run.
~~~

---

## Streaming with `VStreamPath`

A streaming endpoint that the server renders with a `VStreamPath` ([SSE on virtual threads](spring_boot_integration.md#vstreampath-sse-streaming)) is consumed with the runtime translator, which decodes each SSE `data:` frame, ends on `event: complete`, and is deferred and resource-safe:

```java
VStreamPath<Tick> ticks =
    HkjClientExchange.vstream(
        () -> restClient.get().uri("/ticks").retrieve().body(InputStream.class),
        Tick.class,
        jsonMapper);
```

The streaming case is consumed through this translator rather than through a generated `@HttpExchange` method, so wire the source stream yourself.

---

## Generic Clients

A generic `@HkjHttpClient` interface is supported **codegen-only**: the native interface and facade carry the type parameters, but the `@ImportHttpServices`/`@Bean` wiring is skipped, because a generic client cannot be a singleton bean. You instantiate the facade for a concrete type argument yourself.

~~~admonish warning title="Common Mistakes"
- **A sealed error type with no `@JsonTypeInfo`.** Jackson cannot pick the subtype, so decoding fails. Add the type info, or use a concrete error type.
- **Client interfaces outside the component scan.** If your `@HkjHttpClient` interfaces are not under your `@SpringBootApplication`'s scanned packages, the generated configuration is not picked up and Spring never creates the proxy. Add an explicit `@ImportHttpServices(basePackages = "...")`.
- **Expecting a transport failure to become a `Left`.** Connection-refused and timeout are not domain errors; they propagate. Use the `VTaskPath` variant and `runSafe()` to capture them as the failure arm of `Try<Either<E, T>>`.
- **Short-circuiting an SSE stream.** Drain it (`toList()`) or bound it (`take(n).toList()`); a `headOption()`/`find(...)` returns before the stream completes and may leave the HTTP response open.
- **Inheriting methods from a precompiled base.** A super-interface in a dependency jar must be compiled with `-parameters`, or its `@PathVariable`/`@RequestParam` arguments bind to `arg0`-style names. Interfaces compiled in your own build are fine.
- **An `@OnStatus` error type that is not assignable to the method's declared error type.** This is a compile error, by design.
~~~

---

~~~admonish info title="Key Takeaways"
* **`@HkjHttpClient` is the client-side inverse of the server handlers.** Server encodes a typed error to a status plus envelope; client decodes it back, preserving the error channel across services.
* **Three return types, one annotation.** `EitherPath` for blocking calls, `VTaskPath<Either>` for deferred calls with resilience, `MaybePath` for untyped absence.
* **Wiring is configuration, not code.** Base URL and timeouts come from `spring.http.serviceclient.<group>.*`; you autowire your own interface.
* **Three levels of error-mapping override.** Per-method `@OnStatus`, global `hkj.client.status-error-mappings`, or a custom decoder, in that precedence.
~~~

~~~admonish tip title="See Also"
- [Spring Boot Integration](spring_boot_integration.md) : the inbound side this mirrors, including the server's `ErrorStatusCodeStrategy` and `hkj.web.error-status-mappings`
- [The Effect Path API](../effect/effect_path_overview.md) : the railway model, `Either`, and `.run()`/`.fold(...)` that the client results plug into
- [Resilience Patterns](../resilience/ch_intro.md) : `RetryPolicy`, `CircuitBreaker`, and `Bulkhead` used by the `VTaskPath` variant
~~~

---

**Previous:** [Spring Boot Integration](spring_boot_integration.md)
**Next:** [Migrating to Functional Errors](migrating_to_functional_errors.md)
