# Configuration Reference

This document provides a complete reference for all configuration properties available in the higher-kinded-j Spring Boot 4.0.3+ integration.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Web/MVC Configuration](#webmvc-configuration)
3. [Validation Configuration](#validation-configuration)
4. [JSON Serialization Configuration](#json-serialization-configuration)
5. [Async Configuration](#async-configuration)
6. [Virtual Thread Configuration](#virtual-thread-configuration)
7. [Complete Example](#complete-example)
8. [Disabling Features](#disabling-features)

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
  json:
    either-format: SIMPLE
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
- **Key:** Simple class name of error (e.g., "UserNotFoundError")
- **Value:** HTTP status code (e.g., 404)
- **Effect:** Overrides default status code determination

**Example:**
```yaml
hkj:
  web:
    error-status-mappings:
      UserNotFoundError: 404
      ValidationError: 400
      AuthorizationError: 403
      AuthenticationError: 401
      ServerError: 500
```

**Default behaviour without mappings:**
```java
// Built-in heuristics (case-insensitive):
// - Contains "notfound" -> 404
// - Contains "validation" or "invalid" -> 400
// - Contains "authorization" or "forbidden" -> 403
// - Contains "authentication" or "unauthorized" -> 401
// - Default -> value of default-error-status property
```

## Validation Configuration

Configure validation behaviour for `Validated` types.

### `hkj.validation.enabled`

Enable or disable validation support.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** Global toggle for Validated functionality
- **Note:** Currently informational; all validation features are always available

**Example:**
```yaml
hkj:
  validation:
    enabled: false
```

### `hkj.validation.accumulate-errors`

Accumulate all validation errors vs. fail-fast on first error.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When true, `Validated` accumulates all errors; when false, stops at first error (behaves like Either)
- **Note:** Currently informational; error accumulation is controlled by using `Validated` vs `Either`

**Example:**
```yaml
hkj:
  validation:
    accumulate-errors: false  # Fail-fast behaviour
```

### `hkj.validation.max-errors`

Maximum number of errors to accumulate.

- **Type:** `int`
- **Default:** `0` (unlimited)
- **Effect:** Limits error accumulation to prevent unbounded error lists
- **Note:** Currently informational; will be implemented in future versions

**Example:**
```yaml
hkj:
  validation:
    max-errors: 10  # Stop after 10 errors
```

## JSON Serialization Configuration

Configure Jackson serialization for higher-kinded-j types.

### `hkj.json.custom-serializers-enabled`

Enable or disable custom Jackson serializers for Either and Validated.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** Controls registration of `HkjJacksonModule`
- **When disabled:** Either and Validated will use default Jackson serialization (may fail)

**Example:**
```yaml
hkj:
  json:
    custom-serializers-enabled: false  # Use default serialization
```

### `hkj.json.either-format`

JSON output format for Either types (when serialized by Jackson).

- **Type:** `SerializationFormat` enum
- **Default:** `TAGGED`
- **Valid values:**
  - `TAGGED`: `{"isRight": true/false, "right/left": value}`
  - `SIMPLE`: `{"value": ...}` or `{"error": ...}`
- **Effect:** Changes JSON structure for nested Either values
- **Note:** Does NOT affect top-level Either return values (those use return value handler)

**Example:**
```yaml
hkj:
  json:
    either-format: SIMPLE  # Simpler JSON structure
```

**Output comparison:**

**TAGGED format (default):**
```json
{
  "results": [
    {"isRight": true, "right": {"id": "1", "name": "Alice"}},
    {"isRight": false, "left": "Not found"}
  ]
}
```

**SIMPLE format:**
```json
{
  "results": [
    {"value": {"id": "1", "name": "Alice"}},
    {"error": "Not found"}
  ]
}
```

### `hkj.json.validated-format`

JSON output format for Validated types (when serialized by Jackson).

- **Type:** `SerializationFormat` enum
- **Default:** `TAGGED`
- **Valid values:**
  - `TAGGED`: `{"valid": true/false, "value/errors": ...}`
  - `SIMPLE`: `{"value": ...}` or `{"errors": ...}`

**Example:**
```yaml
hkj:
  json:
    validated-format: SIMPLE
```

### `hkj.json.maybe-format`

JSON output format for Maybe types (when serialized by Jackson).

- **Type:** `SerializationFormat` enum
- **Default:** `TAGGED`
- **Valid values:**
  - `TAGGED`: `{"present": true/false, "value": ...}`
  - `SIMPLE`: `{"value": ...}` or null

**Example:**
```yaml
hkj:
  json:
    maybe-format: SIMPLE
```

## Async Configuration

Configure async execution for CompletableFuturePath operations.

### `hkj.async.executor-core-pool-size`

Core thread pool size for async operations.

- **Type:** `int`
- **Default:** `10`
- **Effect:** Minimum number of threads in the pool

**Example:**
```yaml
hkj:
  async:
    executor-core-pool-size: 20
```

### `hkj.async.executor-max-pool-size`

Maximum thread pool size for async operations.

- **Type:** `int`
- **Default:** `20`
- **Effect:** Maximum number of threads in the pool

**Example:**
```yaml
hkj:
  async:
    executor-max-pool-size: 50
```

### `hkj.async.executor-queue-capacity`

Queue capacity for async executor.

- **Type:** `int`
- **Default:** `100`
- **Effect:** Number of tasks to queue when all threads are busy

**Example:**
```yaml
hkj:
  async:
    executor-queue-capacity: 500
```

### `hkj.async.executor-thread-name-prefix`

Thread name prefix for async executor threads.

- **Type:** `String`
- **Default:** `"hkj-async-"`
- **Effect:** Makes threads identifiable in logs and profilers

**Example:**
```yaml
hkj:
  async:
    executor-thread-name-prefix: "my-app-async-"
```

### `hkj.async.default-timeout-ms`

Default timeout for async operations in milliseconds.

- **Type:** `long`
- **Default:** `30000` (30 seconds)
- **Effect:** Maximum time to wait for async operations

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
    default-error-status: 400
    maybe-nothing-status: 404
    try-failure-status: 500
    validation-invalid-status: 400
    vtask-failure-status: 500
    vstream-failure-status: 500
    try-include-exception-details: false
    io-include-exception-details: false
    async-include-exception-details: false
    vtask-include-exception-details: false
    vstream-include-exception-details: false
    error-status-mappings:
      UserNotFoundError: 404
      ValidationError: 400
      AuthorizationError: 403
      AuthenticationError: 401
      ServerError: 500
      ConflictError: 409

  validation:
    enabled: true
    accumulate-errors: true
    max-errors: 0  # unlimited

  json:
    custom-serializers-enabled: true
    either-format: TAGGED
    validated-format: TAGGED
    maybe-format: TAGGED

  async:
    executor-core-pool-size: 10
    executor-max-pool-size: 20
    executor-queue-capacity: 100
    executor-thread-name-prefix: "hkj-async-"
    default-timeout-ms: 30000

  virtual-threads:
    default-timeout-ms: 30000
    stream-timeout-ms: 60000
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
  json:
    either-format: TAGGED  # More verbose for debugging
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
  json:
    either-format: SIMPLE  # Cleaner API responses
```

### Testing

```yaml
hkj:
  web:
    default-error-status: 400
    try-include-exception-details: true  # Helps debug test failures
  async:
    executor-core-pool-size: 2  # Smaller pool for tests
    executor-max-pool-size: 5
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

# Validation configuration
hkj.validation.enabled=true
hkj.validation.accumulate-errors=true
hkj.validation.max-errors=0

# JSON configuration
hkj.json.custom-serializers-enabled=true
hkj.json.either-format=TAGGED
hkj.json.validated-format=TAGGED
hkj.json.maybe-format=TAGGED

# Async configuration
hkj.async.executor-core-pool-size=10
hkj.async.executor-max-pool-size=20
hkj.async.executor-queue-capacity=100
hkj.async.executor-thread-name-prefix=hkj-async-
hkj.async.default-timeout-ms=30000

# Virtual thread configuration
hkj.virtual-threads.default-timeout-ms=30000
hkj.virtual-threads.stream-timeout-ms=60000
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

        // Configure JSON
        properties.getJson().setEitherFormat(HkjProperties.Jackson.SerializationFormat.SIMPLE);

        return properties;
    }
}
```

## See Also

- [Testing Guide](./example/TESTING.md) - How to test your configured application
- [Jackson Serialization](./JACKSON_SERIALIZATION.md) - Details on JSON serialization
