# Configuration Reference

This document provides a complete reference for all configuration properties available in the higher-kinded-j Spring Boot 4.0.3+ integration.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Web/MVC Configuration](#webmvc-configuration)
3. [JSON Serialization Configuration](#json-serialization-configuration)
4. [Async Configuration](#async-configuration)
5. [Virtual Thread Configuration](#virtual-thread-configuration)
6. [Effect Boundary Configuration](#effect-boundary-configuration)
7. [Client HTTP Configuration](#client-http-configuration)
8. [Complete Example](#complete-example)
9. [Disabling Features](#disabling-features)
10. [Error Status Code Strategy Bean](#error-status-code-strategy-bean)
11. [HTTP Header Injection on Errors](#http-header-injection-on-errors)
12. [Known Limitations](#known-limitations)

## Quick Start

All configuration is optional. The integration works out-of-the-box with sensible defaults.

**Minimal configuration (using defaults):**
```yaml
# No configuration needed! Everything works with defaults.
```

**Custom configuration:**
```yaml
hkj:
  web:
    default-error-status: 500
    error-status-mappings:
      ConflictError: 409
```

## Web/MVC Configuration

Configure return value handlers for Spring MVC controllers. The Effect Path API provides handlers for all major functional types.

### `hkj.web.either-path-enabled`

Enable or disable the `EitherPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `Either<Error, Value>` types which are automatically converted to HTTP responses

**Example:**
```yaml
hkj:
  web:
    either-path-enabled: false  # Disable EitherPath handler
```

### `hkj.web.maybe-path-enabled`

Enable or disable the `MaybePathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `Maybe<Value>` types with automatic handling of Nothing as HTTP 404

**Example:**
```yaml
hkj:
  web:
    maybe-path-enabled: false  # Disable MaybePath handler
```

### `hkj.web.try-path-enabled`

Enable or disable the `TryPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `Try<Value>` types with automatic exception handling

**Example:**
```yaml
hkj:
  web:
    try-path-enabled: false  # Disable TryPath handler
```

### `hkj.web.validation-path-enabled`

Enable or disable the `ValidationPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `Validated<Errors, Value>` types with automatic error accumulation

**Example:**
```yaml
hkj:
  web:
    validation-path-enabled: false  # Disable ValidationPath handler
```

### `hkj.web.io-path-enabled`

Enable or disable the `IOPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `IO<Value>` types with automatic execution handling

**Example:**
```yaml
hkj:
  web:
    io-path-enabled: false  # Disable IOPath handler
```

### `hkj.web.completable-future-path-enabled`

Enable or disable the `CompletableFuturePathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `CompletableFuturePath<Value>` types for async operations with functional error handling

**Example:**
```yaml
hkj:
  web:
    completable-future-path-enabled: false  # Disable async handler
```

### `hkj.web.vtask-path-enabled`

Enable or disable the `VTaskPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `VTaskPath<Value>` types for virtual thread async operations via `DeferredResult`

**Example:**
```yaml
hkj:
  web:
    vtask-path-enabled: false  # Disable VTaskPath handler
```

### `hkj.web.vstream-path-enabled`

Enable or disable the `VStreamPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `VStreamPath<Value>` types for SSE streaming on virtual threads

**Example:**
```yaml
hkj:
  web:
    vstream-path-enabled: false  # Disable VStreamPath handler
```

### `hkj.web.either-or-both-path-enabled`

Enable or disable the `EitherOrBothPathReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `EitherOrBothPath<W, A>` (or raw `EitherOrBoth<W, A>`) — the inclusive-or type whose `Both` case models "success with non-fatal warnings"

**Response contract:**

| Result | Status | Body | Header |
|--------|--------|------|--------|
| `Right(value)` | success status (200 or `@ResponseStatus`) | value, unwrapped | — |
| `Both(warnings, value)` | same success status | value, unwrapped | warnings JSON-encoded into `X-Hkj-Warnings` |
| `Left(warnings)` | resolved by `ErrorStatusCodeStrategy` (as for `EitherPath`) | `{"success": false, "error": <warnings>}` | `HttpHeaderCarrier` headers if implemented |

Warnings on the `Both` branch are never silently dropped, but the success body stays the bare value: clients that ignore the `X-Hkj-Warnings` header see a normal success response; clients that care parse the header's JSON.

**Example:**
```yaml
hkj:
  web:
    either-or-both-path-enabled: false  # Disable EitherOrBothPath handler
```

### `hkj.web.free-path-enabled`

Enable or disable the `FreePathReturnValueHandler` (Free-monad programs interpreted through the `EffectBoundary` bean). See [EFFECT_BOUNDARY.md](EFFECT_BOUNDARY.md) for the companion keys `free-path-failure-status` and `free-path-include-exception-details`.

- **Type:** `boolean`
- **Default:** `true`

### `hkj.web.either.default-error-status` (alias: `hkj.web.default-error-status`)

Default HTTP status code returned by the `EitherPathReturnValueHandler` for `Left` values whose
simple class name matches none of the built-in `ErrorStatusCodeMapper` heuristics (`NotFound`,
`Validation`/`Invalid`, `Forbidden`/`Authorization`, `Authentication`/`Unauthorized`).

- **Type:** `int`
- **Default:** `400`
- **Valid values:** Any HTTP status code (100-599)
- **Effect:** Used when error class name does not match any known patterns

The nested form `hkj.web.either.default-error-status` is the preferred path. The flat legacy
form `hkj.web.default-error-status` is retained as an alias and binds to the same underlying
value.

**Example (preferred nested form):**
```yaml
hkj:
  web:
    either:
      default-error-status: 500  # Use 500 for unmatched Left values
```

**Example (legacy flat form, still supported):**
```yaml
hkj:
  web:
    default-error-status: 500
```

### `hkj.web.maybe-nothing-status`

HTTP status code for MaybePath Nothing values.

- **Type:** `int`
- **Default:** `404`
- **Effect:** Status code returned when Maybe.nothing() is returned from a controller

**Example:**
```yaml
hkj:
  web:
    maybe-nothing-status: 204  # Use 204 No Content instead of 404
```

### `hkj.web.try-failure-status`

HTTP status code for TryPath Failure values.

- **Type:** `int`
- **Default:** `500`
- **Effect:** Status code returned when Try.failure() is returned

**Example:**
```yaml
hkj:
  web:
    try-failure-status: 400  # Use 400 for Try failures
```

### `hkj.web.validation-invalid-status`

HTTP status code for ValidationPath Invalid values.

- **Type:** `int`
- **Default:** `400`
- **Effect:** Status code returned when validation errors are present

**Example:**
```yaml
hkj:
  web:
    validation-invalid-status: 422  # Use 422 Unprocessable Entity
```

### `hkj.web.try-include-exception-details`

Include exception details in TryPath failure responses.

- **Type:** `boolean`
- **Default:** `false`
- **Effect:** When true, includes exception message and type in failure responses

**Example:**
```yaml
hkj:
  web:
    try-include-exception-details: true  # Enable for development
```

### `hkj.web.io-include-exception-details`

Include exception details in IOPath failure responses.

- **Type:** `boolean`
- **Default:** `false`
- **Effect:** When true, includes exception message and type in failure responses

**Example:**
```yaml
hkj:
  web:
    io-include-exception-details: true  # Enable for development
```

### `hkj.web.async-include-exception-details`

Include exception details in CompletableFuturePath failure responses.

- **Type:** `boolean`
- **Default:** `false`
- **Effect:** When true, includes exception message and type in async failure responses

**Example:**
```yaml
hkj:
  web:
    async-include-exception-details: true  # Enable for development
```

### `hkj.web.vtask-failure-status`

HTTP status code for VTaskPath failure values.

- **Type:** `int`
- **Default:** `500`
- **Effect:** Status code returned when VTask execution fails

**Example:**
```yaml
hkj:
  web:
    vtask-failure-status: 500
```

### `hkj.web.vstream-failure-status`

HTTP status code for VStreamPath failure values.

- **Type:** `int`
- **Default:** `500`
- **Effect:** Status code used for SSE error events when VStream execution fails

**Example:**
```yaml
hkj:
  web:
    vstream-failure-status: 500
```

### `hkj.web.vtask-include-exception-details`

Include exception details in VTaskPath failure responses.

- **Type:** `boolean`
- **Default:** `false`
- **Effect:** When true, includes exception message and type in VTask failure responses

**Example:**
```yaml
hkj:
  web:
    vtask-include-exception-details: true  # Enable for development
```

### `hkj.web.vstream-include-exception-details`

Include exception details in VStreamPath error events.

- **Type:** `boolean`
- **Default:** `false`
- **Effect:** When true, includes exception message and type in VStream SSE error events

**Example:**
```yaml
hkj:
  web:
    vstream-include-exception-details: true  # Enable for development
```

### `hkj.web.error-status-mappings`

Custom mappings from error class names to HTTP status codes.

- **Type:** `Map<String, Integer>`
- **Default:** Empty map
- **Key:** Simple class name of the error (e.g. `UserNotFoundError`) or its fully-qualified
  class name (useful when two error classes share a simple name across packages).
- **Value:** HTTP status code (e.g. 404)
- **Effect:** Takes precedence over the heuristics. If the error matches no mapping, the
  heuristics run; if those do not match either, the configured default applies.

**Example:**
```yaml
hkj:
  web:
    error-status-mappings:
      UserNotFoundError: 404
      ValidationError: 400
      AuthorizationError: 403
      AuthenticationError: 401
      MfaAlreadyEnrolledError: 409   # Conflict
      PaymentDeclinedError: 422      # Unprocessable Entity
      MfaThrottledError: 429         # Too Many Requests
      ServerError: 500
```

**Resolution order (applied to the `Left` value of an `EitherPath` / `Either`):**

1. Explicit mapping by simple class name
2. Explicit mapping by fully-qualified class name
3. Built-in token heuristic on the simple class name
4. `hkj.web.either.default-error-status` (or its legacy alias `hkj.web.default-error-status`)

**Built-in heuristics (token-aware, case-insensitive):**

The simple class name is split on CamelCase boundaries and lower-cased; the rule fires when
one of the listed tokens appears as a whole token (so `RevalidationError` no longer matches
the `validation` rule).

| Token(s) present                          | Status |
| ----------------------------------------- | ------ |
| `not` adjacent to `found`                 | 404    |
| `validation` or `invalid`                 | 400    |
| `authorization` or `forbidden`            | 403    |
| `authentication` or `unauthorized`        | 401    |
| (no match)                                | configured default |

For anything beyond table lookup — for example, a status that depends on a record's field
(`MfaThrottledError.retryAfter() ≥ 60 → 503`) — register a custom
[`ErrorStatusCodeStrategy` bean](#error-status-code-strategy-bean).

## JSON Serialization Configuration

Configure Jackson serialization for higher-kinded-j types.

### `hkj.json.custom-serializers-enabled`

Enable or disable the custom Jackson serializers for `Either`, `Validated`, `EitherOrBoth`, and `NonEmptyList`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** Controls registration of `HkjJacksonModule`
- **When disabled:** Nested HKJ types will use default Jackson serialization (may fail)

**Example:**
```yaml
hkj:
  json:
    custom-serializers-enabled: false  # Use default serialization
```

The JSON shapes produced by the module are **fixed** — there are no format-toggle properties. Nested values serialise as:

```json
// Either
{"isRight": true, "right": <value>}
{"isRight": false, "left": <error>}

// Validated
{"valid": true, "value": <value>}
{"valid": false, "errors": <errors>}

// EitherOrBoth
{"kind": "right", "right": <value>}
{"kind": "both", "left": <warnings>, "right": <value>}
{"kind": "left", "left": <warnings>}
```

`Maybe` and `Try` have no Jackson support: return them at the top level (where the return-value handlers apply) rather than nesting them inside DTOs. Top-level controller return values are shaped by the handlers, not by these serializers — see [JACKSON_SERIALIZATION.md](JACKSON_SERIALIZATION.md).

## Async Configuration

`CompletableFuturePath` services supply their own executor — the starter does not create a thread pool. Define an `Executor` bean in your application (the example module's `AsyncConfig` uses a `ThreadPoolTaskExecutor` named `hkjAsyncExecutor`) and pass it to `CompletableFuture.supplyAsync(...)`. Naming the bean `hkjAsyncExecutor` also enables the optional `hkj-async` health indicator (see [ACTUATOR.md](ACTUATOR.md)).

### `hkj.async.default-timeout-ms`

Default timeout for `CompletableFuturePath` responses in milliseconds.

- **Type:** `long`
- **Default:** `30000` (30 seconds)
- **Effect:** Maximum time to wait for the future to complete before the `DeferredResult` times out (HTTP 504)

**Example:**
```yaml
hkj:
  async:
    default-timeout-ms: 60000  # 60 seconds
```

## Virtual Thread Configuration

Configure virtual thread behaviour for VTaskPath and VStreamPath operations. Unlike the async configuration which manages a fixed thread pool, virtual threads require no pool sizing — they scale automatically with the JVM.

### `hkj.virtual-threads.default-timeout-ms`

Default timeout for VTaskPath responses in milliseconds.

- **Type:** `long`
- **Default:** `30000` (30 seconds)
- **Effect:** Maximum time to wait for VTask completion before timing out the DeferredResult

**Example:**
```yaml
hkj:
  virtual-threads:
    default-timeout-ms: 60000  # 60 seconds
```

### `hkj.virtual-threads.stream-timeout-ms`

Default timeout for VStreamPath streaming responses in milliseconds.

- **Type:** `long`
- **Default:** `60000` (60 seconds)
- **Effect:** Maximum time for the entire SSE stream before it is terminated. Set to 0 for no timeout.

**Example:**
```yaml
hkj:
  virtual-threads:
    stream-timeout-ms: 120000  # 2 minutes
```

## Effect Boundary Configuration

### `hkj.effect-boundary.enabled`

Master switch for `@EnableEffectBoundary` auto-configuration.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When `false`, the registrar does not create the `effectBoundary` bean

Interpreter validation is always fail-fast (a missing or ambiguous interpreter is a startup error), and environment-specific interpreter switching uses the `@Interpreter(profile = ...)` attribute — neither is configurable via properties. See [EFFECT_BOUNDARY.md](EFFECT_BOUNDARY.md) for the full guide.

## Client HTTP Configuration

Configure outbound calls made by `@HkjHttpClient`-generated clients. Two key spaces are involved: the
Spring-native `spring.http.serviceclient.*` keys (base URL, timeouts, versioning per service group)
and the HKJ-specific `hkj.client.*` keys (status → error-type decoding). For the full feature guide
see the [HTTP Client Reference](HTTP_CLIENT.md).

### `spring.http.serviceclient.<group>.*`

Per-group connection settings for the generated HTTP Service proxy. These are standard Spring Boot
keys (bound by the `spring-boot-restclient` module), not HKJ keys: `@HkjHttpClient` only generates
the `@ImportHttpServices` group that they target.

- **Type:** Per-group properties under a group key
- **Group key:** the decapitalised interface name (e.g. `UserClientApi` → `userClientApi`), or the
  explicit `@HkjHttpClient(group = "...")` value
- **Common keys:** `base-url`, `connect-timeout`, `read-timeout`, `default-headers.*`, `api-version`
- **Effect:** Applied to the `RestClient` backing every method on that client. A missing `base-url`
  means requests resolve against the application's own host, which is rarely intended.

**Example:**
```yaml
spring:
  http:
    serviceclient:
      userClientApi:                 # group = decapitalised interface name
        base-url: http://users.internal
        connect-timeout: 2s
        read-timeout: 2s
        default-headers:
          X-Api-Key: ${USERS_API_KEY}
```

> The `spring-boot-restclient` module supplies the auto-configuration that binds these keys. The
> `hkj-spring-boot-starter` pulls it in; a hand-assembled dependency set must add it, or the base URL
> is silently dropped.

### `hkj.client.status-error-mappings`

Global mappings from HTTP status code to the error type a failed response decodes into. This is the
client-side analogue of [`hkj.web.error-status-mappings`](#hkjweberror-status-mappings): the server
maps an error type to a status; the client maps a status back to an error type.

- **Type:** `Map<Integer, String>` (status code → fully-qualified error class name)
- **Default:** Empty map
- **Key:** HTTP status code (e.g. `404`)
- **Value:** Fully-qualified class name of the error type to decode into
- **Effect:** For a given method, a configured status whose type is **assignable** to that method's
  declared error type decodes into the subtype. Non-assignable mappings and unmapped statuses fall
  back to the method's declared error type. A class that cannot be loaded fails fast at startup.

**Example:**
```yaml
hkj:
  client:
    status-error-mappings:
      404: com.example.UserNotFoundError
      429: com.example.RateLimitError
```

**Precedence (highest first):**

1. A method's own `@OnStatus(value = ..., error = ...)` annotation
2. `hkj.client.status-error-mappings` (when the mapped type is assignable to the declared error type)
3. The method's declared error type

So a per-method `@OnStatus` always wins over the global map, and the global map only ever narrows to
a subtype of what the method already declares: it never widens or changes the declared error channel.

## Complete Example

Here is a complete configuration file with all options:

```yaml
hkj:
  web:
    either-path-enabled: true
    maybe-path-enabled: true
    try-path-enabled: true
    validation-path-enabled: true
    io-path-enabled: true
    completable-future-path-enabled: true
    vtask-path-enabled: true
    vstream-path-enabled: true
    either-or-both-path-enabled: true
    free-path-enabled: true
    default-error-status: 400
    maybe-nothing-status: 404
    try-failure-status: 500
    validation-invalid-status: 400
    vtask-failure-status: 500
    vstream-failure-status: 500
    free-path-failure-status: 500
    try-include-exception-details: false
    io-include-exception-details: false
    async-include-exception-details: false
    vtask-include-exception-details: false
    vstream-include-exception-details: false
    free-path-include-exception-details: false
    error-status-mappings:
      UserNotFoundError: 404
      ValidationError: 400
      AuthorizationError: 403
      AuthenticationError: 401
      ServerError: 500
      ConflictError: 409

  json:
    custom-serializers-enabled: true

  async:
    default-timeout-ms: 30000

  virtual-threads:
    default-timeout-ms: 30000
    stream-timeout-ms: 60000

  effect-boundary:
    enabled: true
```

## Disabling Features

### Disable Either Support

```yaml
hkj:
  web:
    either-path-enabled: false
```

**Effect:**
- Controllers returning `Either` will use default Spring MVC serialization
- May result in unexpected JSON output

### Disable Validation Support

```yaml
hkj:
  web:
    validation-path-enabled: false
```

**Effect:**
- Controllers returning `Validated` will use default Spring MVC serialization
- Error accumulation features will not work

### Disable Async Support

```yaml
hkj:
  web:
    completable-future-path-enabled: false
```

**Effect:**
- Controllers returning `CompletableFuturePath` will use default Spring MVC serialization
- Async error handling will not work

### Disable Jackson Serializers

```yaml
hkj:
  json:
    custom-serializers-enabled: false
```

**Effect:**
- Nested Either/Validated in DTOs will use default Jackson serialization
- Will likely fail with serialization errors

### Disable Virtual Thread Support

```yaml
hkj:
  web:
    vtask-path-enabled: false
    vstream-path-enabled: false
```

**Effect:**
- Controllers returning `VTaskPath` or `VStreamPath` will use default Spring MVC serialization
- Virtual thread async and SSE streaming features will not work

### Disable All Effect Path Handlers

```yaml
hkj:
  web:
    either-path-enabled: false
    maybe-path-enabled: false
    try-path-enabled: false
    validation-path-enabled: false
    io-path-enabled: false
    completable-future-path-enabled: false
    vtask-path-enabled: false
    vstream-path-enabled: false
    either-or-both-path-enabled: false
    free-path-enabled: false
  json:
    custom-serializers-enabled: false
```

**Effect:**
- All higher-kinded-j integration features disabled
- Application will behave as if the integration is not installed

## Environment-Specific Configuration

### Development

```yaml
hkj:
  web:
    default-error-status: 500  # Show all errors as 500 for debugging
    try-include-exception-details: true
    io-include-exception-details: true
    async-include-exception-details: true
    vtask-include-exception-details: true
    vstream-include-exception-details: true
```

### Production

```yaml
hkj:
  web:
    default-error-status: 400  # Client errors by default
    try-include-exception-details: false  # Never expose exceptions
    io-include-exception-details: false
    async-include-exception-details: false
    vtask-include-exception-details: false
    vstream-include-exception-details: false
    error-status-mappings:
      # Explicit mappings for security
      UserNotFoundError: 404
      ValidationError: 400
      AuthorizationError: 403
```

### Testing

```yaml
hkj:
  web:
    default-error-status: 400
    try-include-exception-details: true  # Helps debug test failures
  async:
    default-timeout-ms: 5000  # Shorter timeout
```

## Properties in application.properties Format

If you prefer `.properties` format:

```properties
# Web configuration
hkj.web.either-path-enabled=true
hkj.web.maybe-path-enabled=true
hkj.web.try-path-enabled=true
hkj.web.validation-path-enabled=true
hkj.web.io-path-enabled=true
hkj.web.completable-future-path-enabled=true
hkj.web.vtask-path-enabled=true
hkj.web.vstream-path-enabled=true
hkj.web.either-or-both-path-enabled=true
hkj.web.free-path-enabled=true
hkj.web.default-error-status=400
hkj.web.maybe-nothing-status=404
hkj.web.try-failure-status=500
hkj.web.validation-invalid-status=400
hkj.web.vtask-failure-status=500
hkj.web.vstream-failure-status=500
hkj.web.try-include-exception-details=false
hkj.web.vtask-include-exception-details=false
hkj.web.vstream-include-exception-details=false
hkj.web.error-status-mappings.UserNotFoundError=404
hkj.web.error-status-mappings.ValidationError=400

# JSON configuration
hkj.json.custom-serializers-enabled=true

# Async configuration
hkj.async.default-timeout-ms=30000

# Effect boundary configuration
hkj.effect-boundary.enabled=true

# Virtual thread configuration
hkj.virtual-threads.default-timeout-ms=30000
hkj.virtual-threads.stream-timeout-ms=60000

# Client HTTP configuration (@HkjHttpClient)
hkj.client.status-error-mappings.404=com.example.UserNotFoundError
hkj.client.status-error-mappings.429=com.example.RateLimitError
spring.http.serviceclient.userClientApi.base-url=http://users.internal
spring.http.serviceclient.userClientApi.read-timeout=2s
```

## Programmatic Configuration

You can also configure properties programmatically:

```java
@Configuration
public class HkjConfig {

    @Bean
    public HkjProperties hkjProperties() {
        HkjProperties properties = new HkjProperties();

        // Configure web
        properties.getWeb().setDefaultErrorStatus(500);
        properties.getWeb().getErrorStatusMappings().put("MyCustomError", 418);

        return properties;
    }
}
```

## Error Status Code Strategy Bean

For status decisions that property-table mappings cannot express — for example, a status that
depends on an error's field values — register a custom `ErrorStatusCodeStrategy` bean. The
auto-configuration declares its default with `@ConditionalOnMissingBean`, so your bean
replaces it without further wiring.

```java
@Bean
ErrorStatusCodeStrategy errorStatusCodeStrategy() {
    return (error, defaultStatus) -> switch (error) {
        case MfaThrottledError t when t.retryAfter() > 60 -> 503; // backoff escalates to 503
        case MfaThrottledError ignored -> 429;
        case PaymentDeclinedError ignored -> 422;
        // Delegate to the built-in heuristics + property mappings for everything else
        default -> ErrorStatusCodeMapper.determineStatusCode(error, defaultStatus);
    };
}
```

The strategy is invoked once per error response on the request thread (or the async completion
thread for `CompletableFuturePath` / `VTaskPath`), so implementations must be thread-safe and
side-effect-free.

## HTTP Header Injection on Errors

Error payloads can surface response headers (`Retry-After`, `WWW-Authenticate`, etc.) by
implementing `HttpHeaderCarrier`:

```java
public record MfaThrottledError(int retryAfterSeconds) implements DomainError, HttpHeaderCarrier {
    @Override
    public Map<String, String> headers() {
        return Map.of("Retry-After", Integer.toString(retryAfterSeconds));
    }
}
```

The headers are applied by `EitherPath`, `EitherOrBothPath`, `TryPath`, `ValidationPath`, `IOPath`,
`CompletableFuturePath`, `VTaskPath`, and `FreePath` handlers before the JSON body is written.
Internally the headers are added (not set), so multi-valued headers such as
`WWW-Authenticate`, `Set-Cookie`, and `Link` accumulate as separate header lines on the
response, matching the HTTP grammar; upstream headers set by filters or interceptors are also
preserved. For collection-typed payloads (e.g. `ValidationPath` `Invalid` values), every
element that implements `HttpHeaderCarrier` contributes its headers. `null` keys and `null`
values are skipped silently. For single-valued headers such as `Retry-After`, the carrier
should ensure the value appears at most once across all payload elements.

`VStreamPath` does not honour `HttpHeaderCarrier` — by the time an SSE error event is emitted,
the response status and headers are already committed, so headers must be set before the
stream begins.

## Known Limitations

These are the configuration surfaces adopters most often discover by hitting their edges. If
any of these block you, please open an issue.

| Area | Current state | Workaround |
| ---- | ------------- | ---------- |
| Per-class status mapping | Configurable via `hkj.web.error-status-mappings` (simple or fully-qualified class name) | Register a custom `ErrorStatusCodeStrategy` bean for field-dependent decisions |
| Heuristic flexibility | Tokenised CamelCase match — no regex / pluggable predicate | Provide explicit mappings in `hkj.web.error-status-mappings`; they take precedence |
| Response-header injection | Implement `HttpHeaderCarrier` on the error class | Not configurable via properties |
| `VStreamPath` headers | Status + headers committed before the first event | Set required headers via a `WebFilter` or controller advice before the stream starts |
| `MaybePath` Nothing payload | Single configurable status (`hkj.web.maybe-nothing-status`); no header injection — there is no error value to query | Wrap the result in `EitherPath<DomainError, T>` to gain mapping + headers |
| Per-request strategy override | The `ErrorStatusCodeStrategy` bean is process-wide | Inspect request context inside the strategy implementation if needed |
| `@ResponseStatus` on the handler method | Honoured for `Right` / `Valid` / success values only — error status comes from the strategy | Move per-error behaviour into mappings or a custom strategy |
| Client error decoding | Default `@HkjHttpClient` decoder expects the `{"success":false,"error":…}` envelope | Register a custom `ResponseErrorDecoder` / `ResponseErrorDecoderFactory` bean for foreign servers ([HTTP Client Reference](HTTP_CLIENT.md#error-decoding)) |

## See Also

- [Testing Guide](./example/TESTING.md) - How to test your configured application
- [`ErrorStatusFixtureController`](./example/src/main/java/org/higherkindedj/spring/example/controller/ErrorStatusFixtureController.java) - Canonical `@WebMvcTest` fixture exercising every status-mapping rule
- [Jackson Serialization](./JACKSON_SERIALIZATION.md) - Details on JSON serialization
- [HTTP Client Reference](./HTTP_CLIENT.md) - Declarative `@HkjHttpClient` clients and their configuration
