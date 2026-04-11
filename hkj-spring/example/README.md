# Path Handlers Example

Demonstrates the Effect Path API return value handlers in a Spring Boot application. This example shows how to return `Either`, `Validated`, `IOPath`, `VTaskPath`, and `VStreamPath` directly from controllers with automatic HTTP response conversion.

## What This Example Shows

| Pattern | File | Description |
|---------|------|-------------|
| `EitherPath` return | `UserController.java` | Type-safe errors in controller signatures |
| `MaybePath` return | `UserController.java` | Optional values → 404 on absence |
| `ValidationPath` return | `ValidationController.java` | Accumulate all validation errors |
| `IOPath` return | `UserController.java` | Deferred side-effecting computations |
| `CompletableFuturePath` return | `AsyncController.java` | Async with typed errors |
| `VTaskPath` return | `VirtualThreadController.java` | Virtual thread async via DeferredResult |
| `VStreamPath` return | `VirtualThreadController.java` | SSE streaming on virtual threads |
| Structured concurrency | `VirtualThreadController.java` | `Scope.allSucceed()` with parallel tasks |
| Error status mapping | `application.yml` | Custom error class → HTTP status |
| Actuator metrics | `application.yml` | HKJ handler metrics via `/actuator/metrics` |

This corresponds to **Level 0** of the EffectBoundary adoption ladder: returning typed *Path values directly from controllers without composing multiple effects.

## Running

```bash
./gradlew :hkj-spring:example:bootRun
```

The application starts on port 8080.

## Endpoints

```bash
# EitherPath - Get user (200 OK)
curl http://localhost:8080/api/users/1

# EitherPath - Get non-existent user (404 Not Found)
curl http://localhost:8080/api/users/999

# MaybePath - Optional user (200 or 404)
curl http://localhost:8080/api/users/1/optional

# ValidationPath - Create user with validation
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User"}'

# IOPath - Config with deferred IO
curl http://localhost:8080/api/config

# CompletableFuturePath - Async user fetch
curl http://localhost:8080/api/users/1/async

# VTaskPath - Virtual thread user fetch
curl http://localhost:8080/api/vt/users/1

# VTaskPath - Structured concurrency enrichment
curl http://localhost:8080/api/vt/users/1/enriched

# VStreamPath - Stream users as SSE
curl -N http://localhost:8080/api/vt/users/stream

# VStreamPath - Stream tick events
curl -N http://localhost:8080/api/vt/ticks?count=5
```

## How It Works

Each Effect Path type has a dedicated return value handler that converts the result to an HTTP response:

1. Controller returns a *Path type (e.g., `EitherPath<E, A>`)
2. The handler detects the return type and calls the appropriate execution method
3. Success → HTTP 200 with JSON body
4. Failure → Appropriate HTTP error status with JSON error body

No additional configuration is needed. The starter auto-configures all handlers.

## Error Response Format

```json
{
  "success": false,
  "error": {
    "type": "UserNotFoundError",
    "message": "User with id 999 not found"
  }
}
```

Error types are mapped to HTTP status codes via pattern matching on the error class name (configurable via `hkj.web.error-status-mappings`).

## Testing

```bash
./gradlew :hkj-spring:example:test
```

## Configuration

All configuration in `application.yml` is optional. See [CONFIGURATION.md](../CONFIGURATION.md) for the full reference.

## Related

- [Effect Example](../effect-example/) — Multi-effect composition with EffectBoundary (Level 1+)
- [Configuration Reference](../CONFIGURATION.md) — Complete property reference
- [Actuator Support](../ACTUATOR.md) — Metrics and health checks
