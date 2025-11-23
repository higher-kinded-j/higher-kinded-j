# Configuration Reference

This document provides a complete reference for all configuration properties available in the higher-kinded-j Spring Boot integration.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Web/MVC Configuration](#webmvc-configuration)
3. [Validation Configuration](#validation-configuration)
4. [JSON Serialization Configuration](#json-serialization-configuration)
5. [Async Configuration](#async-configuration)
6. [Complete Example](#complete-example)
7. [Disabling Features](#disabling-features)

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

Configure return value handlers for Spring MVC controllers.

### `hkj.web.either-response-enabled`

Enable or disable the `EitherReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `Either<Error, Value>` types which are automatically converted to HTTP responses

**Example:**
```yaml
hkj:
  web:
    either-response-enabled: false  # Disable Either handler
```

### `hkj.web.validated-response-enabled`

Enable or disable the `ValidatedReturnValueHandler`.

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** When enabled, controllers can return `Validated<Errors, Value>` types with automatic error accumulation

**Example:**
```yaml
hkj:
  web:
    validated-response-enabled: false  # Disable Validated handler
```

### `hkj.web.default-error-status`

Default HTTP status code for Left/Invalid values when the error type is unknown.

- **Type:** `int`
- **Default:** `400`
- **Valid values:** Any HTTP status code (100-599)
- **Effect:** Used when error class name doesn't match any known patterns

**Example:**
```yaml
hkj:
  web:
    default-error-status: 500  # Use 500 for unknown errors
```

### `hkj.web.async-either-t-enabled`

Enable or disable EitherT async support (for future implementation).

- **Type:** `boolean`
- **Default:** `true`
- **Effect:** Reserved for future EitherT async return value handler
- **Note:** Not yet implemented

**Example:**
```yaml
hkj:
  web:
    async-either-t-enabled: false
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

**Default behavior without mappings:**
```java
// Built-in heuristics (case-insensitive):
// - Contains "notfound" → 404
// - Contains "validation" or "invalid" → 400
// - Contains "authorization" or "forbidden" → 403
// - Contains "authentication" or "unauthorized" → 401
// - Default → value of default-error-status property
```

## Validation Configuration

Configure validation behavior for `Validated` types.

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
    accumulate-errors: false  # Fail-fast behavior
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

- **Type:** `EitherFormat` enum
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

## Async Configuration

Configure async execution for future EitherT support.

### `hkj.async.executor-core-pool-size`

Core thread pool size for async operations.

- **Type:** `int`
- **Default:** `10`
- **Effect:** Minimum number of threads in the pool
- **Note:** For future EitherT async support

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
- **Note:** For future EitherT async support

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
- **Note:** For future EitherT async support

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
- **Note:** For future EitherT async support

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
- **Note:** For future EitherT async support

**Example:**
```yaml
hkj:
  async:
    default-timeout-ms: 60000  # 60 seconds
```

## Complete Example

Here's a complete configuration file with all options:

```yaml
hkj:
  web:
    either-response-enabled: true
    validated-response-enabled: true
    default-error-status: 400
    async-either-t-enabled: true
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

  async:
    executor-core-pool-size: 10
    executor-max-pool-size: 20
    executor-queue-capacity: 100
    executor-thread-name-prefix: "hkj-async-"
    default-timeout-ms: 30000
```

## Disabling Features

### Disable Either Support

```yaml
hkj:
  web:
    either-response-enabled: false
```

**Effect:**
- Controllers returning `Either` will use default Spring MVC serialization
- May result in unexpected JSON output

### Disable Validated Support

```yaml
hkj:
  web:
    validated-response-enabled: false
```

**Effect:**
- Controllers returning `Validated` will use default Spring MVC serialization
- Error accumulation features won't work

### Disable Jackson Serializers

```yaml
hkj:
  json:
    custom-serializers-enabled: false
```

**Effect:**
- Nested Either/Validated in DTOs will use default Jackson serialization
- Will likely fail with serialization errors

### Disable Everything

```yaml
hkj:
  web:
    either-response-enabled: false
    validated-response-enabled: false
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
  json:
    either-format: TAGGED  # More verbose for debugging
```

### Production

```yaml
hkj:
  web:
    default-error-status: 400  # Client errors by default
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
  async:
    executor-core-pool-size: 2  # Smaller pool for tests
    executor-max-pool-size: 5
    default-timeout-ms: 5000  # Shorter timeout
```

## Properties in application.properties Format

If you prefer `.properties` format:

```properties
# Web configuration
hkj.web.either-response-enabled=true
hkj.web.validated-response-enabled=true
hkj.web.default-error-status=400
hkj.web.async-either-t-enabled=true
hkj.web.error-status-mappings.UserNotFoundError=404
hkj.web.error-status-mappings.ValidationError=400

# Validation configuration
hkj.validation.enabled=true
hkj.validation.accumulate-errors=true
hkj.validation.max-errors=0

# JSON configuration
hkj.json.custom-serializers-enabled=true
hkj.json.either-format=TAGGED

# Async configuration
hkj.async.executor-core-pool-size=10
hkj.async.executor-max-pool-size=20
hkj.async.executor-queue-capacity=100
hkj.async.executor-thread-name-prefix=hkj-async-
hkj.async.default-timeout-ms=30000
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
        properties.getJson().setEitherFormat(HkjProperties.Json.EitherFormat.SIMPLE);

        return properties;
    }
}
```

## See Also

- [Testing Guide](./example/TESTING.md) - How to test your configured application
- [Jackson Serialization](./JACKSON_SERIALIZATION.md) - Details on JSON serialization
- [Implementation Roadmap](./IMPLEMENTATION_ROADMAP.md) - Future configuration options
