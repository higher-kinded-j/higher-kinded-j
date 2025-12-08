# EffectPath Integration Perspective

> **Status**: Living Document (v1.0)
> **Focus**: Integration with FocusPath optics and existing higher-kinded-j infrastructure

## Overview

The power of EffectPath comes not just from effect composition, but from its seamless integration with the existing higher-kinded-j ecosystem, particularly the FocusPath optics DSL.

## The Unified Mental Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         "PATH" AS UNIVERSAL CONCEPT                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   FocusPath<S, A>        "A path through data structure S to focus on A"    │
│        │                                                                     │
│        ├── via(lens)      "Navigate through a field"                        │
│        ├── via(prism)     "Navigate through a variant"                      │
│        └── get(source)    "Extract the focused value"                       │
│                                                                              │
│   ════════════════════════════════════════════════════════════════════════  │
│                                                                              │
│   EffectPath<A>          "A path through effects to produce A"              │
│        │                                                                     │
│        ├── via(f)         "Navigate through an effectful computation"       │
│        ├── map(f)         "Transform the value"                             │
│        └── run()          "Execute and extract the effect"                  │
│                                                                              │
│   ════════════════════════════════════════════════════════════════════════  │
│                                                                              │
│   COMBINED: Navigate data AND compose effects with the same vocabulary      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Integration Patterns

### Pattern 1: Optic After Effect (Effect-First)

Navigate to a value through effects, then use optics to access nested data.

```java
// Scenario: Fetch user from database, then navigate to nested address
MaybePath<String> city = Path.maybe(userId)
    .via(id -> userRepo.findById(id))     // Effect: database lookup
    .map(user -> UserFocus.address()      // Optic: navigate to address
        .via(AddressFocus.city())
        .get(user));                       // Extract city from user
```

### Pattern 2: Effect After Optic (Optic-First)

Use optics to navigate data, then compose effects on the extracted value.

```java
// Scenario: Get email from user record, then validate it
User user = getCurrentUser();

EitherPath<ValidationError, String> validatedEmail =
    UserFocus.email()                      // Optic: navigate to email
        .get(user)                         // Extract email
        .pipe(email -> Path.right(email))  // Lift into effect path
        .via(this::validateEmail)          // Effect: validation
        .via(this::normalizeEmail);        // Effect: normalization
```

### Pattern 3: Effectful Optic Modification

Modify a focused field through a validated transformation.

```java
// Scenario: Update user's email with validation
Either<ValidationError, User> updatedUser = UserFocus.email()
    .modifyF(
        email -> Path.right(email)
            .via(this::validateEmail)
            .via(this::normalizeEmail)
            .run(),
        user,
        EitherFunctor.INSTANCE
    );
```

### Pattern 4: Optional Field Navigation

Handle optional fields that naturally align `AffinePath` with `MaybePath`.

```java
// AffinePath handles 0-or-1 values, just like Maybe
User user = new User("Alice", Maybe.nothing());  // No address

MaybePath<String> city = Path.maybe(user)
    .via(u -> UserFocus.optionalAddress()   // AffinePath<User, Address>
        .getMaybe(u))                       // Returns Maybe<Address>
    .via(addr -> AddressFocus.city()
        .getAsMaybe(addr));                 // Returns Maybe<String>

assertThat(city.run().isNothing()).isTrue();
```

### Pattern 5: Validated Collection Traversal

Traverse a collection with validation, accumulating errors.

```java
// Scenario: Validate all items in an order
ValidatedPath<List<Error>, Order> validatedOrder =
    OrderFocus.items()                      // FocusPath<Order, List<Item>>
        .each()                             // TraversalPath<Order, Item>
        .validateAll(                       // Bridge to ValidatedPath
            item -> validateItem(item),
            order
        );
```

## Bridge Methods

### On FocusPath

```java
public sealed interface FocusPath<S, A> {

    /** Extract value and wrap in MaybePath */
    default MaybePath<A> getAsMaybe(S source) {
        return Path.maybe(get(source));
    }

    /** Extract value and chain into effect */
    default <B> MaybePath<B> getAndThen(S source, Function<A, MaybePath<B>> f) {
        return Path.maybe(get(source)).via(a -> f.apply(a).run());
    }

    /** Modify with validation, returning Either */
    default <E> Either<E, S> modifyValidated(
            S source,
            Function<A, Either<E, A>> validator) {
        return validator.apply(get(source))
            .map(newValue -> set(newValue, source));
    }

    /** Modify with effectful function */
    default <E> EitherPath<E, S> modifyWithPath(
            S source,
            Function<A, EitherPath<E, A>> f) {
        return f.apply(get(source))
            .map(newValue -> set(newValue, source));
    }
}
```

### On AffinePath

```java
public sealed interface AffinePath<S, A> {

    /** Natural alignment: AffinePath (0-or-1) -> MaybePath */
    default MaybePath<A> getMaybe(S source) {
        return Path.fromOptional(getOptional(source));
    }

    /** Navigate and then compose with effect */
    default <E, B> EitherPath<E, B> getAndValidate(
            S source,
            E errorIfMissing,
            Function<A, EitherPath<E, B>> f) {
        return getMaybe(source)
            .toEitherPath(errorIfMissing)
            .via(a -> f.apply(a).run());
    }
}
```

### On TraversalPath

```java
public sealed interface TraversalPath<S, A> {

    /** Validate all focused elements, accumulating errors */
    default <E> ValidatedPath<List<E>, S> validateAll(
            Function<A, Either<E, A>> validator,
            S source) {
        // Implementation uses Validated's error accumulation
    }

    /** Transform all focused elements with effect */
    default <F> Kind<F, S> modifyAllF(
            Function<A, Kind<F, A>> f,
            S source,
            Applicative<F> applicative) {
        // Existing method, now bridges to Path types naturally
    }
}
```

## Integration Points

### 1. EffectPath to FocusPath

```java
// Effect produces a value, optic navigates it
MaybePath<String> userName = Path.attempt(() -> userService.fetchUser(id))
    .toMaybePath()
    .map(user -> UserFocus.name().get(user));

// Or more fluently with bridge method
MaybePath<String> userName = Path.attempt(() -> userService.fetchUser(id))
    .toMaybePath()
    .via(user -> UserFocus.name().getAsMaybe(user).run());
```

### 2. FocusPath to EffectPath

```java
// Optic extracts value, effect validates it
User user = getCurrentUser();
EitherPath<Error, String> validatedEmail =
    Path.right(UserFocus.email().get(user))
        .via(this::validateEmail);
```

### 3. Combined Flow

```java
// Full pipeline: fetch -> navigate -> validate -> transform -> update
Either<Error, User> result = Path.attempt(() -> userRepo.findById(id))
    .toEitherPath(Error.notFound(id))
    .via(user -> UserFocus.email()
        .modifyWithPath(user, email ->
            Path.right(email)
                .via(this::validateEmail)
                .via(this::normalizeEmail)
        ).run())
    .run();
```

## Shared Vocabulary Reference

| Vocabulary | FocusPath Meaning | EffectPath Meaning |
|------------|-------------------|-------------------|
| `via` | Compose with another optic | Compose with effectful function |
| `then` | Alias for `via` | Alias for `via` |
| `get` | Extract focused value | N/A (effects are not values) |
| `run` | N/A (optics are not lazy) | Execute and extract effect |
| `map` | N/A (use `via`) | Transform success value |
| `traced` | Debug observation | Debug observation |

## Type Alignment

| FocusPath Type | EffectPath Type | Semantic Alignment |
|----------------|-----------------|-------------------|
| `FocusPath<S, A>` | `MaybePath<A>` (always Just) | Exactly one value |
| `AffinePath<S, A>` | `MaybePath<A>` | Zero or one value |
| `TraversalPath<S, A>` | `ListPath<A>` (future) | Zero or more values |

## Free Monad Integration

The optics Free monad DSL can bridge with EffectPath:

```java
// Build a program that combines optic operations with effects
Free<OpticOpKind.Witness, Either<Error, User>> program =
    OpticPrograms.get(source, UserFocus.email())
        .flatMap(email -> OpticPrograms.liftEffect(
            Path.right(email).via(this::validateEmail).run()
        ))
        .flatMap(validatedEmail -> OpticPrograms.set(
            source, UserFocus.email(), validatedEmail
        ));

// Execute with different interpreters
Either<Error, User> result = OpticInterpreters.direct().run(program);
```

## Real-World Integration Examples

### Example 1: Form Validation

```java
// Domain types
record UserForm(String name, String email, Integer age) {}
record User(String name, String email, int age) {}
record ValidationError(String field, String message) {}

// Optics for form fields
interface FormFocus {
    FocusPath<UserForm, String> name();
    FocusPath<UserForm, String> email();
    FocusPath<UserForm, Integer> age();
}

// Validation pipeline
EitherPath<ValidationError, User> validateForm(UserForm form) {
    return Path.right(form)
        .via(f -> FormFocus.name().modifyValidated(f, this::validateName))
        .via(f -> FormFocus.email().modifyValidated(f, this::validateEmail))
        .via(f -> FormFocus.age().modifyValidated(f, this::validateAge))
        .map(validatedForm -> new User(
            validatedForm.name(),
            validatedForm.email(),
            validatedForm.age()
        ));
}
```

### Example 2: Nested Data Transformation

```java
// Domain: Company -> Department -> Employee -> Address
// Transform all employee addresses in a company

Either<Error, Company> updateAllAddresses(
        Company company,
        Function<Address, Either<Error, Address>> transformer) {

    return CompanyFocus.departments()
        .each()
        .via(DepartmentFocus.employees())
        .each()
        .via(EmployeeFocus.address())
        .modifyAllF(
            addr -> Path.from(transformer.apply(addr)).run(),
            company,
            new EitherApplicative<>()
        );
}
```

### Example 3: API Response Mapping

```java
// Map API response through multiple layers
record ApiResponse(int status, String body) {}
record User(long id, String name) {}

MaybePath<User> parseUserFromResponse(ApiResponse response) {
    return Path.maybe(response)
        .filter(r -> r.status() == 200)
        .via(r -> Path.attempt(() -> parseJson(r.body())).toMaybePath().run())
        .via(json -> JsonFocus.field("user").getMaybe(json))
        .map(userJson -> new User(
            JsonFocus.field("id").get(userJson).asLong(),
            JsonFocus.field("name").get(userJson).asString()
        ));
}
```

## Future Integration Possibilities

### 1. EffectfulPath Type

A combined type that carries both optic navigation AND effect composition:

```java
public interface EffectfulPath<S, F, A> {
    /** Apply to source, producing an effect containing the result */
    Kind<F, A> apply(S source);

    /** Compose with optic navigation */
    <B> EffectfulPath<S, F, B> via(Lens<A, B> lens);

    /** Compose with effectful function */
    <B> EffectfulPath<S, F, B> via(Function<A, Kind<F, B>> f);

    /** Factory from AffinePath */
    static <S, A> EffectfulPath<S, MaybeKind.Witness, A>
        fromAffinePath(AffinePath<S, A> affine) {
        return source -> MAYBE.widen(Maybe.fromOptional(affine.getOptional(source)));
    }
}
```

### 2. Bidirectional Integration

Prisms naturally align with Either:

```java
// Prism<Json, User> tries to parse Json as User
Prism<Json, User> userPrism = ...;

// Natural bridge to EitherPath
EitherPath<ParseError, User> parsed =
    userPrism.toEitherPath(json, ParseError.invalidFormat());
```

### 3. Natural Transformation Support

Convert between effect types while maintaining path composition:

```java
MaybePath<User> maybeUser = ...;

// Transform Maybe to Either
EitherPath<Error, User> eitherUser =
    maybeUser.mapK(MAYBE_TO_EITHER, Error.notFound());

// Transform back (lossy)
MaybePath<User> backToMaybe =
    eitherUser.mapK(EITHER_TO_MAYBE);
```

## Summary

Integration between EffectPath and FocusPath provides:

1. **Unified vocabulary** - `via`/`then` everywhere
2. **Natural bridges** - `getMaybe()`, `modifyWithPath()`, `validateAll()`
3. **Type alignment** - AffinePath ↔ MaybePath semantic match
4. **Combined workflows** - Navigate data AND compose effects fluently
5. **Free monad interop** - Reuse optic programs with effects

The key insight: **optics navigate data structure, effects navigate computation**. Both are "paths" through different dimensions, and higher-kinded-j unifies them.
