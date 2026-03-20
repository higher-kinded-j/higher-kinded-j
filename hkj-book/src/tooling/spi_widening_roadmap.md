# SPI Widening Feature: Future Enhancements Roadmap

## Overview

This document outlines planned enhancements for the SPI-based path widening system introduced in v1.1. Each item includes motivation, scope, and design considerations.

---

## 1. ZERO_OR_MORE Auto-Widening Opt-In for Static Focus Methods

### Motivation

Currently, `ZERO_OR_MORE` SPI types (e.g., `Map<K,V>`, arrays, Eclipse Collections) return `FocusPath` from static Focus methods for backwards compatibility. Users must manually call `.each(eachInstance)` to widen. This is inconsistent with `ZERO_OR_ONE` types which auto-widen to `AffinePath`.

### Proposed Design

Add an annotation attribute to `@GenerateFocus`:

```java
@GenerateFocus(widenCollections = true)
record Portfolio(String name, ImmutableList<Position> positions) {}
```

When `widenCollections = true`:
- Static Focus methods for `ZERO_OR_MORE` SPI fields return `TraversalPath` (not `FocusPath`)
- The generated code calls `.each(opticExpression)` automatically
- Default remains `false` to preserve backwards compatibility

### Scope

- Modify `FocusProcessor.analyseFieldType()` to check the `widenCollections` attribute
- Route `ZERO_OR_MORE` SPI matches through `SPI_ZERO_OR_MORE` widening when enabled
- Update documentation and add tutorial examples

---

## 2. Optional<NavigableType> Navigator Generation

### Motivation

When a record has an `Optional<Address>` field where `Address` is also `@GenerateFocus`-annotated, the navigator chain currently terminates at the `Optional` boundary. Users must manually unwrap with `.some()` before continuing navigation.

### Proposed Design

Generate "through-optional" navigator methods that automatically compose `.some()` and continue the navigator chain:

```java
// Current: manual unwrap
UserFocus.backup().some().city()

// Proposed: transparent navigation
UserFocus.backup().city()  // Returns AffinePath, transparently unwraps Optional
```

### Scope

- Modify `NavigatorClassGenerator` to detect `Optional<NavigableType>` and `Maybe<NavigableType>` fields
- Generate navigator methods that compose `.some()` with the inner type's navigator
- The resulting path must be `AffinePath` (or `TraversalPath` if the inner navigator widens further)
- Consider potential method name conflicts with the optional field's own methods

---

## 3. Wildcard Type Support in SPI Generators

### Motivation

Fields like `List<? extends Number>` or `Map<String, ? super Integer>` are common in real-world Java code but are not currently handled by SPI generators. The `supports()` method receives the raw `TypeMirror` which includes wildcard bounds, but generators may not account for them.

### Proposed Design

- Add a utility method to `TraversableGenerator` for extracting the effective type argument, resolving wildcards to their bounds
- Modify `FocusProcessor.extractTypeArgumentAt()` to resolve `? extends T` to `T` and `? super T` to `Object`
- Document the wildcard handling contract in the SPI Javadoc

### Scope

- Add `TypeMirror resolveWildcard(TypeMirror)` utility to `ProcessorUtils`
- Update `extractTypeArgumentAt()` in `FocusProcessor`
- Update `NavigatorClassGenerator` type argument extraction
- Add test cases for wildcard-bounded container fields

---

## 4. SPI Generator Priority/Ordering

### Motivation

When multiple SPI generators support the same type (detected by the conflict warning added in v1.1), the current behaviour uses the first match from `ServiceLoader` iteration order. This order is non-deterministic across JVM implementations and classpath orderings.

### Proposed Design

Add a `priority()` default method to `TraversableGenerator`:

```java
default int priority() { return 0; }
```

- Higher priority generators are consulted first
- Equal-priority generators that match the same type still emit a warning
- Explicit priority allows users to override built-in generators by registering a higher-priority alternative

### Scope

- Add `default int priority()` to `TraversableGenerator`
- Sort generators by priority (descending) before iteration in `FocusProcessor` and `NavigatorClassGenerator`
- Update SPI documentation

---

## 5. Composite/Nested Container Widening

### Motivation

Fields like `Optional<List<String>>` or `Either<Error, Map<String, Value>>` involve nested containers. Currently, only the outermost container is detected for widening. The inner container requires manual navigation.

### Proposed Design

Detect nested container patterns and generate composed widening:

```java
// Optional<List<String>> -> AffinePath composed with TraversalPath = TraversalPath
// The generator would emit: .some().each()
```

### Scope

- Recursive type analysis in `analyseFieldType()` to detect nested containers
- Compound widening code generation (chain of `.some()` and `.each()` calls)
- Update compound widening lattice table in documentation
- Significant complexity — consider as a later enhancement

---

## Priority Order

| Enhancement | Impact | Complexity | Suggested Version |
|-------------|--------|------------|-------------------|
| ZERO_OR_MORE opt-in | High | Low | 1.2 |
| Optional<NavigableType> navigators | High | Medium | 1.2 |
| Wildcard type support | Medium | Medium | 1.3 |
| SPI generator priority | Medium | Low | 1.3 |
| Composite container widening | High | High | 2.0 |
