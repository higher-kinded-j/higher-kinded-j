# EffectPath + FocusPath Integration Design

> **Status**: Design Document (v1.1) - **Deferred to Phase 4+**
> **Last Updated**: 2025-12-13
> **Purpose**: Define integration strategy between EffectPath and FocusPath
> **Java Baseline**: Java 25 (RELEASE_25)
> **Implementation Phase**: Phase 4+ (deferred from original Phase 2 plan)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Deferral Rationale](#deferral-rationale)
3. [The Integration Opportunity](#the-integration-opportunity)
4. [Phase 4 Scope: Bridge Methods](#phase-4-scope)
5. [Phase 5+ Scope: Deep Integration](#phase-5-scope)
6. [API Design Options](#api-design-options)
7. [Implementation Examples](#implementation-examples)
8. [Recommendation](#recommendation)

---

## Executive Summary

FocusPath and EffectPath both use `via` as their central composition operator but navigate different domains:
- **FocusPath**: Navigates through *data structures* (lenses, prisms, traversals)
- **EffectPath**: Navigates through *effect types* (Maybe, Either, Try, IO)

Integration enables powerful compositions like:
```java
// Navigate to a field, then perform effectful validation
Path.right(order)
    .mapF(FocusPaths.lens(OrderLenses.email()))    // FocusPath -> extract field
    .via(email -> validateEmail(email))            // EffectPath -> validate
```

### Phase Breakdown

| Phase | Scope | Complexity |
|-------|-------|------------|
| **Phase 4** | Bridge methods on Path types | Low |
| **Phase 5** | Methods on FocusPath hierarchy | Medium |
| **Phase 6** | Unified composition operators | High |

---

## Deferral Rationale

FocusPath integration has been deferred from Phase 2 to Phase 4+ for the following reasons:

### 1. API Stability Feedback

The Effect Path API needs real-world usage and feedback before coupling it with the optics system. Deferring allows:
- Users to provide feedback on core Path API patterns
- Identification of common composition patterns organically
- Validation that the capability interface hierarchy is correct

### 2. FocusDSL Interaction Patterns

The FocusDSL provides a fluent builder pattern for optics. Before implementing EffectPath integration, we should consider:
- How `FocusDSL.focus(source).via(...)` might interact with `Path.right(source).focus(...)`
- Whether unified entry points make sense
- Potential API conflicts or redundancies

### 3. Implementation Risk Reduction

By completing Phase 2 (new Path types) and Phase 3 (annotations) first:
- Core Effect Path patterns will be proven
- More implementation experience will inform better integration design
- Breaking changes can be avoided by waiting for stability

### 4. Scope Management

Phase 2 already includes significant work:
- Four new Path types (ValidatedPath, IdPath, OptionalPath, GenericPath)
- Annotation processing infrastructure
- Cross-path conversions

Adding FocusPath integration would overload the phase.

---

## The Integration Opportunity

### Parallel Vocabularies

Both APIs share the `via` metaphor but with different semantics:

| Aspect | FocusPath | EffectPath |
|--------|-----------|------------|
| **Domain** | Data structure navigation | Effect type navigation |
| **`via` meaning** | Compose optics | Monadic bind |
| **Returns** | FocusPath/AffinePath/TraversalPath | Path type |
| **Failure mode** | AffinePath (zero or one) | Error type (E, Throwable) |

### Current Limitations

Without integration, combining optics with effects requires boilerplate:

```java
// Current: Verbose and manual
EitherPath<Error, String> result = Path.right(user)
    .map(u -> UserFocus.email().get(u))            // Manual lens application
    .via(email -> validateEmail(email));

// Desired: Fluent integration
EitherPath<Error, String> result = Path.right(user)
    .focus(UserFocus.email())                       // Bridge method
    .via(email -> validateEmail(email));
```

### Integration Touchpoints

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        INTEGRATION TOUCHPOINTS                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   EffectPath Types              Bridge Methods              FocusPath Types  │
│   ──────────────               ──────────────              ───────────────  │
│                                                                              │
│   MaybePath<A> ────────────►  .focus(Lens<A,B>)  ◄──────── FocusPath<A,B>   │
│   EitherPath<E,A> ──────────►  .focus(Affine<A,B>) ◄────── AffinePath<A,B>  │
│   TryPath<A> ───────────────►  .focusAll(Traversal<A,B>) ◄ TraversalPath    │
│   IOPath<A> ────────────────►  .modifyF(focus, f) ◄─────── All              │
│                                                                              │
│   Bidirectional Integration:                                                 │
│   - EffectPath can use FocusPath to extract/modify values                   │
│   - FocusPath.modifyF can use EffectPath as the effect functor              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 4 Scope: Bridge Methods

### What Phase 4 Will Achieve

**Conservative integration** that:
1. Adds bridge methods to EffectPath types
2. Does NOT modify FocusPath interfaces
3. Enables common use cases with minimal risk

### Phase 4 Bridge Method Categories

#### Category 1: Extract (Focus Get)

```java
// On all EffectPath types: extract a focused value
public <B> MaybePath<B> focus(Lens<A, B> lens) {
    return map(lens::get);
}

public <B> MaybePath<B> focus(FocusPath<A, B> focus) {
    return map(focus::get);
}

// For AffinePath (partial extraction)
public <B> MaybePath<B> focusMaybe(Affine<A, B> affine) {
    return via(a -> Path.maybe(affine.getOptional(a)));
}
```

#### Category 2: Modify (Focus Set/Modify)

```java
// On all EffectPath types: modify a focused field
public <B> MaybePath<A> modify(Lens<A, B> lens, Function<B, B> f) {
    return map(a -> lens.modify(f, a));
}

public <B> MaybePath<A> set(Lens<A, B> lens, B value) {
    return map(a -> lens.set(value, a));
}
```

#### Category 3: Effectful Modify (Focus ModifyF)

```java
// On all EffectPath types: effectful modification using focus
public <B> MaybePath<A> modifyF(
        Lens<A, B> lens,
        Function<B, MaybePath<B>> effectfulModifier) {
    return via(a -> {
        B focused = lens.get(a);
        return effectfulModifier.apply(focused)
            .map(newB -> lens.set(newB, a));
    });
}
```

### Phase 4 Interface Additions

```java
// MaybePath additions
public final class MaybePath<A> implements Recoverable<Unit, A> {

    // ... existing methods ...

    // === Focus Integration (Phase 4+) ===

    /**
     * Extracts a focused value using a Lens.
     *
     * @param lens the lens to apply
     * @param <B> the focused value type
     * @return a MaybePath containing the focused value
     */
    public <B> MaybePath<B> focus(Lens<A, B> lens) {
        Objects.requireNonNull(lens, "lens must not be null");
        return map(lens::get);
    }

    /**
     * Extracts a focused value using a FocusPath.
     */
    public <B> MaybePath<B> focus(FocusPath<A, B> focusPath) {
        Objects.requireNonNull(focusPath, "focusPath must not be null");
        return map(focusPath::get);
    }

    /**
     * Extracts a potentially absent focused value using an Affine.
     *
     * <p>This handles cases where the focus may not exist (e.g., optional fields).
     */
    public <B> MaybePath<B> focusMaybe(Affine<A, B> affine) {
        Objects.requireNonNull(affine, "affine must not be null");
        return via(a -> Path.maybe(affine.getOptional(a).orElse(null)));
    }

    /**
     * Extracts a potentially absent focused value using an AffinePath.
     */
    public <B> MaybePath<B> focusMaybe(AffinePath<A, B> affinePath) {
        Objects.requireNonNull(affinePath, "affinePath must not be null");
        return via(a -> Path.maybe(affinePath.getOptional(a).orElse(null)));
    }

    /**
     * Modifies a focused value.
     */
    public <B> MaybePath<A> modify(Lens<A, B> lens, Function<B, B> f) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(f, "f must not be null");
        return map(a -> lens.modify(f, a));
    }

    /**
     * Sets a focused value.
     */
    public <B> MaybePath<A> set(Lens<A, B> lens, B newValue) {
        Objects.requireNonNull(lens, "lens must not be null");
        return map(a -> lens.set(newValue, a));
    }

    /**
     * Performs an effectful modification on a focused value.
     *
     * <p>This enables patterns like:
     * <pre>{@code
     * Path.just(user)
     *     .modifyF(UserLenses.email(), email -> validateEmail(email))
     *     .via(user -> saveUser(user));
     * }</pre>
     *
     * @param lens the lens to the field to modify
     * @param effectfulModifier a function that takes the focused value and returns
     *        a MaybePath of the new value
     * @return a MaybePath of the modified outer structure
     */
    public <B> MaybePath<A> modifyF(
            Lens<A, B> lens,
            Function<B, MaybePath<B>> effectfulModifier) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(effectfulModifier, "effectfulModifier must not be null");
        return via(a -> {
            B focused = lens.get(a);
            return effectfulModifier.apply(focused)
                .map(newB -> lens.set(newB, a));
        });
    }
}
```

### Phase 4 EitherPath Additions

```java
// Similar pattern but returning EitherPath
public final class EitherPath<E, A> implements Recoverable<E, A> {

    // ... existing methods ...

    // === Focus Integration (Phase 4+) ===

    public <B> EitherPath<E, B> focus(Lens<A, B> lens) {
        Objects.requireNonNull(lens, "lens must not be null");
        return map(lens::get);
    }

    public <B> EitherPath<E, B> focus(FocusPath<A, B> focusPath) {
        Objects.requireNonNull(focusPath, "focusPath must not be null");
        return map(focusPath::get);
    }

    /**
     * Extracts a potentially absent focused value, providing an error if absent.
     */
    public <B> EitherPath<E, B> focusOrError(Affine<A, B> affine, E errorIfAbsent) {
        Objects.requireNonNull(affine, "affine must not be null");
        Objects.requireNonNull(errorIfAbsent, "errorIfAbsent must not be null");
        return via(a -> affine.getOptional(a)
            .<EitherPath<E, B>>map(Path::right)
            .orElseGet(() -> Path.left(errorIfAbsent)));
    }

    public <B> EitherPath<E, A> modify(Lens<A, B> lens, Function<B, B> f) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(f, "f must not be null");
        return map(a -> lens.modify(f, a));
    }

    public <B> EitherPath<E, A> set(Lens<A, B> lens, B newValue) {
        Objects.requireNonNull(lens, "lens must not be null");
        return map(a -> lens.set(newValue, a));
    }

    public <B> EitherPath<E, A> modifyF(
            Lens<A, B> lens,
            Function<B, EitherPath<E, B>> effectfulModifier) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(effectfulModifier, "effectfulModifier must not be null");
        return via(a -> {
            B focused = lens.get(a);
            return effectfulModifier.apply(focused)
                .map(newB -> lens.set(newB, a));
        });
    }
}
```

### Phase 4 Deliverables Summary

| Path Type | Focus Methods Added |
|-----------|---------------------|
| `MaybePath` | `focus`, `focusMaybe`, `modify`, `set`, `modifyF` |
| `EitherPath` | `focus`, `focusOrError`, `modify`, `set`, `modifyF` |
| `TryPath` | `focus`, `focusOrThrow`, `modify`, `set`, `modifyF` |
| `IOPath` | `focus`, `modify`, `set`, `modifyF` |
| `ValidatedPath` | `focus`, `modify`, `set`, `modifyF` |

---

## Phase 5+ Scope: Deep Integration

### What Phase 5+ Adds

1. **Methods on FocusPath hierarchy** returning EffectPath types
2. **Unified `via` composition** across both hierarchies
3. **Effect-aware traversal** operations

### Phase 5 FocusPath Additions

```java
// FocusPath additions (Phase 5)
public interface FocusPath<S, A> {

    // ... existing methods ...

    // === EffectPath Integration (Phase 5) ===

    /**
     * Creates a MaybePath that extracts the focused value.
     */
    default MaybePath<A> toMaybePath(S source) {
        return Path.just(get(source));
    }

    /**
     * Performs an effectful modification using MaybePath.
     *
     * <p>This bridges to FocusPath's existing modifyF but with EffectPath ergonomics.
     */
    default MaybePath<S> withMaybe(
            S source,
            Function<A, MaybePath<A>> effectfulModifier) {
        // Uses existing modifyF under the hood
        Kind<MaybeKind.Witness, S> result = modifyF(
            a -> MaybeKindHelper.widen(effectfulModifier.apply(a).run()),
            source,
            MaybeMonad.INSTANCE
        );
        return Path.maybe(MaybeKindHelper.narrow(result));
    }

    /**
     * Chains into an effectful operation.
     */
    default <E> EitherPath<E, S> withEither(
            S source,
            Function<A, EitherPath<E, A>> effectfulModifier) {
        // ...
    }
}
```

### Phase 5 AffinePath Additions

```java
// AffinePath additions (Phase 5)
public interface AffinePath<S, A> {

    // ... existing methods ...

    // === EffectPath Integration (Phase 5) ===

    /**
     * Creates a MaybePath from the potentially absent focused value.
     */
    default MaybePath<A> toMaybePath(S source) {
        return Path.maybe(getOptional(source).orElse(null));
    }

    /**
     * Creates an EitherPath with custom error if focus is absent.
     */
    default <E> EitherPath<E, A> toEitherPath(S source, E errorIfAbsent) {
        return getOptional(source)
            .<EitherPath<E, A>>map(Path::right)
            .orElseGet(() -> Path.left(errorIfAbsent));
    }
}
```

### Phase 5 TraversalPath Additions

```java
// TraversalPath additions (Phase 5)
public interface TraversalPath<S, A> {

    // ... existing methods ...

    // === EffectPath Integration (Phase 5) ===

    /**
     * Validates all focused elements, accumulating errors.
     *
     * <p>This is powerful for batch validation:
     * <pre>{@code
     * TraversalPath<Order, LineItem> items = OrderFocus.lineItems();
     * ValidatedPath<List<Error>, Order> result = items.validateAll(
     *     order,
     *     item -> validateLineItem(item),  // Returns ValidatedPath
     *     Semigroups.list()
     * );
     * }</pre>
     */
    default <E> ValidatedPath<E, S> validateAll(
            S source,
            Function<A, ValidatedPath<E, A>> validator,
            Semigroup<E> semigroup) {
        // ...
    }

    /**
     * Traverses all focused elements with an effectful operation.
     */
    default <F> Kind<F, S> traverseEffect(
            S source,
            Function<A, Kind<F, A>> effectfulOp,
            Applicative<F> applicative) {
        return modifyF(effectfulOp, source, applicative);
    }
}
```

---

## API Design Options

### Option A: Methods on EffectPath Only (Phase 4)

```java
// Usage pattern
Path.right(user)
    .focus(UserLenses.email())           // Extract
    .via(email -> validateEmail(email))  // Effect

Path.right(user)
    .modifyF(UserLenses.email(), email -> validateEmail(email))
    .via(user -> saveUser(user));
```

**Pros**: No changes to FocusPath, lower risk
**Cons**: One-directional integration

### Option B: Methods on Both Hierarchies (Phase 5)

```java
// From EffectPath -> FocusPath
Path.right(user)
    .focus(UserLenses.email())
    .via(email -> validateEmail(email));

// From FocusPath -> EffectPath
UserFocus.email()
    .toMaybePath(user)
    .via(email -> validateEmail(email));
```

**Pros**: Full bidirectional integration
**Cons**: Changes to FocusPath interfaces (risk)

### Option C: Separate Bridge Utility (Alternative)

```java
// Utility class approach
PathBridge.focus(Path.right(user), UserLenses.email())
    .via(email -> validateEmail(email));

// Or fluent builder
PathBridge.from(Path.right(user))
    .focus(UserLenses.email())
    .validate(email -> validateEmail(email))
    .build();
```

**Pros**: Zero changes to existing APIs
**Cons**: Less discoverable, more verbose

---

## Implementation Examples

### Example 1: Form Validation with Focus

```java
// Domain model
record RegistrationForm(
    String username,
    String email,
    String password,
    Optional<String> referralCode
) {}

// Lenses (generated or hand-written)
interface FormFocus {
    Lens<RegistrationForm, String> username();
    Lens<RegistrationForm, String> email();
    Lens<RegistrationForm, String> password();
    Affine<RegistrationForm, String> referralCode();
}

// Phase 4 usage: validate individual fields
EitherPath<ValidationError, String> validatedEmail = Path.right(form)
    .focus(FormFocus.email())
    .via(email -> EmailValidator.validate(email));

// Phase 4 usage: effectful modify
EitherPath<ValidationError, RegistrationForm> normalized = Path.right(form)
    .modifyF(FormFocus.email(), email -> EmailNormalizer.normalize(email))
    .modifyF(FormFocus.username(), user -> UsernameNormalizer.normalize(user));
```

### Example 2: Nested Structure Modification

```java
// Nested structure
record Company(String name, Address headquarters) {}
record Address(String street, String city, Optional<String> suite) {}

// Composed lenses
Lens<Company, Address> headquarters = CompanyLenses.headquarters();
Lens<Address, String> city = AddressLenses.city();
Lens<Company, String> companyCity = headquarters.andThen(city);

// Phase 4 usage: deep effectful modify
EitherPath<Error, Company> result = Path.right(company)
    .modifyF(companyCity, city -> CityValidator.validate(city));

// Which is equivalent to:
EitherPath<Error, Company> manual = Path.right(company)
    .via(c -> {
        String currentCity = companyCity.get(c);
        return CityValidator.validate(currentCity)
            .map(validCity -> companyCity.set(validCity, c));
    });
```

### Example 3: Batch Validation with Traversal (Phase 5+)

```java
// Order with line items
record Order(List<LineItem> items) {}
record LineItem(String sku, int quantity, BigDecimal price) {}

// TraversalPath to all line items
TraversalPath<Order, LineItem> allItems = TraversalFocusPath.fromTraversal(
    OrderTraversals.items()
);

// Phase 5 usage: validate all items, accumulating errors
ValidatedPath<List<ValidationError>, Order> result = allItems
    .validateAll(
        order,
        item -> LineItemValidator.validate(item),
        Semigroups.list()
    );

// The result contains either:
// - Valid(order) if all items validated
// - Invalid(List<Error>) with accumulated errors from all items
```

---

## Recommendation

### Phase 4 Deliverables

| Component | Description | Risk |
|-----------|-------------|------|
| `MaybePath.focus(Lens)` | Extract focused value | Low |
| `MaybePath.focusMaybe(Affine)` | Extract optional focus | Low |
| `MaybePath.modify(Lens, f)` | Modify focused value | Low |
| `MaybePath.modifyF(Lens, effectF)` | Effectful modification | Low |
| `EitherPath.focus(Lens)` | Extract focused value | Low |
| `EitherPath.focusOrError(Affine, error)` | Extract or fail | Low |
| `EitherPath.modifyF(Lens, effectF)` | Effectful modification | Low |
| Similar for `TryPath`, `IOPath` | | Low |

### Phase 5 Candidates

| Component | Description | Risk |
|-----------|-------------|------|
| `FocusPath.toMaybePath(source)` | Create EffectPath from focus | Medium |
| `AffinePath.toEitherPath(source, error)` | Create with error | Medium |
| `TraversalPath.validateAll(...)` | Batch validation | High |
| Unified `via` composition | Cross-hierarchy | High |

### Implementation Order

```
Phase 4:
1. Add focus bridge methods to MaybePath
2. Add focus bridge methods to EitherPath
3. Add focus bridge methods to TryPath
4. Add focus bridge methods to IOPath
5. Add focus bridge methods to ValidatedPath
6. Comprehensive tests for all combinations
7. Documentation with examples

Phase 5:
8. Methods on FocusPath returning EffectPath
9. Methods on AffinePath returning EffectPath
10. TraversalPath.validateAll for batch operations
11. Consider unified composition operators
```

---

## Success Criteria

Phase 4 integration is successful if:
1. Users can extract focused values into EffectPath chains
2. Users can perform effectful modifications on focused fields
3. Integration feels natural and discoverable
4. No breaking changes to existing FocusPath or EffectPath APIs
5. Clear migration path to deeper Phase 5 integration
