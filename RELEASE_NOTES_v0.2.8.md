# Higher-Kinded-J v0.2.8 Release Notes

## Overview

This release introduces **ForPath**, a powerful for-comprehension builder for the Effect Path API, major Spring Boot 4.0.1 integration for `hkj-spring`, and completely rewritten examples showcasing modern functional Java patterns with railway-oriented programming.

## New Features

### ForPath: Path-Native For-Comprehension Builder (#292)

A new comprehension system that bridges the `For` comprehension API with Effect Paths, allowing composition of Path types using for-comprehension style while preserving Path semantics.

**Key capabilities:**
- Entry points for all Path types: `MaybePath`, `EitherPath`, `IOPath`, `NonDetPath`, `OptionalPath`, `TryPath`, `IdPath`, `GenericPath`
- Full comprehension operations: `from()`, `let()`, `when()`, `yield()`
- Optics integration with `focus()` and `match()` methods
- 100% test coverage

```java
// ForPath example: sequential step composition
ForPath.maybe(MaybePath.of(user))
    .from(u -> MaybePath.of(u.getAddress()))
    .from(addr -> MaybePath.of(addr.getCity()))
    .yield((user, address, city) -> new UserLocation(user.name(), city));
```

### Spring Boot 4.0.1 Integration for hkj-spring (#303)

Complete migration of `hkj-spring` from `EitherT` to the Effect Path API:

**New Return Value Handlers:**
- `CompletableFuturePathReturnValueHandler` - async effect handling
- `EitherPathReturnValueHandler` - either-based error handling
- `IOPathReturnValueHandler` - deferred IO operations
- `MaybePathReturnValueHandler` - optional value handling
- `TryPathReturnValueHandler` - exception-safe operations
- `ValidationPathReturnValueHandler` - accumulated validation errors

**Configuration Updates:**
- Property names updated to Path variants (`either-path-enabled`, etc.)
- Handler names updated to Path variants
- Version compatibility table added to documentation

**Example Application Updates:**
- `AsyncController` and `AsyncUserService` migrated to `CompletableFuturePath`
- All code examples updated to use Effect Path API style (`.via()` instead of `.flatMap()`)

## Example Rewrites

### Order Workflow Example - Complete Rewrite (#295)

A comprehensive showcase of the Effect Path API, Focus DSL, and modern functional Java patterns:

**Architectural Patterns:**
- Sealed error hierarchy with exhaustive pattern matching
- `EitherPath` composition with `via()` chains
- `ForPath` comprehensions for sequential workflow steps
- Recovery patterns (`recover`, `recoverWith`, `mapError`)
- Saga pattern with compensating transactions for order cancellation

**New Components:**
- **Resilience Package**: `RetryPolicy` with exponential backoff, timeout utilities
- **Audit Logging**: `AuditLogWriter` with `WriterPath` integration
- **Configuration**: `ConfigurableOrderWorkflow` with feature flags
- **Partial Fulfilment**: Workflow for handling partially available orders
- **Split Shipments**: Support for multi-warehouse order fulfilment

**Domain Models:**
- Complete set of domain records with `@GenerateLenses` and `@GenerateFocus` annotations
- Value types: `Money`, `Percentage`, `OrderId`, `CustomerId`, etc.
- Workflow models: `ProcessedOrder`, `OrderWorkflowState`, `CancellationResult`

### Draughts Game Example - Railway-Oriented Refactor (#297)

Updated to demonstrate Effect Path API and Focus DSL patterns:

- Replaced imperative validation with `EitherPath.via()` pipelines
- Stream-based iteration using `flatMap/filter/anyMatch`
- `MaybePath` for optional piece handling in board display
- Pure predicates and handlers for functional error handling
- Modern `Stream.concat`/`IntStream` patterns for game state initialization

## Documentation Improvements

### Learning Track Reorganisation (#299)
- Restructured tutorials to emphasize Effect Path API
- Clear progression paths for different learning goals

### Hands-On Practice Links (#298)
- Added practice links to all `hkj-book` documentation pages
- Direct connections between concepts and executable examples

### README and Home Page Updates (#300)
- Updated to highlight Effect Path API as the primary entry point
- Refreshed code examples and architecture diagrams

### Glossary Updates (#302)
- Added `via` and `recover` links to Path glossary entry
- Cross-references for better navigation

## Breaking Changes

### hkj-spring Module
- `EitherTReturnValueHandler` removed → use `EitherPathReturnValueHandler`
- `ValidatedReturnValueHandler` removed → use `ValidationPathReturnValueHandler`
- Configuration properties renamed from `hkj.either-t-enabled` to `hkj.either-path-enabled`
- Jackson serializers updated for Jackson 3.x compatibility

## Migration Guide

### Migrating from EitherT to Effect Path API in Spring

**Before (v0.2.7):**
```java
@GetMapping("/user/{id}")
public EitherT<CompletableFuture<?>, ServiceError, User> getUser(@PathVariable String id) {
    return userService.findById(id)
        .flatMap(user -> validateUser(user));
}
```

**After (v0.2.8):**
```java
@GetMapping("/user/{id}")
public CompletableFuturePath<ServiceError, User> getUser(@PathVariable String id) {
    return userService.findById(id)
        .via(user -> validateUser(user));
}
```

### Configuration Property Updates

```yaml
# Before (v0.2.7)
hkj:
  either-t-enabled: true
  validated-enabled: true

# After (v0.2.8)
hkj:
  either-path-enabled: true
  validation-path-enabled: true
```

## Statistics

- **8 PRs merged** since v0.2.7
- **~15,000 lines** of new code and documentation
- **100% test coverage** maintained for new features

## Contributors

- Magnus Smith (@MagnusSmith)

## Links

- [Documentation](https://higher-kinded-j.github.io/latest/home.html)
- [GitHub Repository](https://github.com/higher-kinded-j/higher-kinded-j)
- [Maven Central](https://central.sonatype.com/artifact/io.github.higher-kinded-j/hkj-core)
