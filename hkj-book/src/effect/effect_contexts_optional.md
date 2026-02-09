# Optional Contexts: Graceful Absence in Effects

> *"Sum tyms theres mor in the emty than the ful."*
>
> -- Russell Hoban, *Riddley Walker*

Hoban's riddler knew that absence can be as meaningful as presence. A database query that returns nothing isn't always an error; sometimes the record genuinely doesn't exist, and that's valuable information. `OptionalContext` and `JavaOptionalContext` model this graceful absence within effectful computations, giving you the compositional power of transformers without forcing non-existence into an error mould.

~~~admonish info title="What You'll Learn"
- The difference between `OptionalContext` (Maybe) and `JavaOptionalContext` (java.util.Optional)
- Creating optional contexts from nullable suppliers and existing values
- Chaining computations that may return nothing
- Providing fallbacks with `orElse()` and `orElseValue()`
- Converting to `ErrorContext` when absence becomes an error
~~~

---

## Two Flavours of Optionality

Higher-Kinded-J provides two optional context types:

| Context | Wraps | Uses |
|---------|-------|------|
| `OptionalContext<F, A>` | `MaybeT<F, A>` | Library's `Maybe<A>` type |
| `JavaOptionalContext<F, A>` | `OptionalT<F, A>` | Java's `java.util.Optional<A>` |

They're functionally equivalent. Choose based on what your codebase already uses:

- If you're working with code that uses `Optional`, use `JavaOptionalContext`
- If you're using Higher-Kinded-J's `Maybe` throughout, use `OptionalContext`
- If starting fresh, either works; `Maybe` is slightly more idiomatic for FP patterns

---

## The Problem

Consider a lookup chain:

```java
// Each might return null or Optional.empty()
User user = cache.get(userId);
if (user == null) {
    user = database.find(userId);
}
if (user == null) {
    user = legacySystem.lookup(userId);
}
if (user == null) {
    throw new UserNotFoundException(userId);
}
```

Four null checks. Three nested lookups. The actual logic (try cache, then database, then legacy) is obscured by defensive programming.

---

## The Solution

With `OptionalContext`:

```java
OptionalContext<IOKind.Witness, User> user =
    OptionalContext.<User>io(() -> cache.get(userId))
        .orElse(() -> OptionalContext.io(() -> database.find(userId)))
        .orElse(() -> OptionalContext.io(() -> legacySystem.lookup(userId)));

// Convert to ErrorContext when absence is actually an error
ErrorContext<IOKind.Witness, UserError, User> required =
    user.toErrorContext(new UserError("User not found: " + userId));
```

The lookup chain reads top-to-bottom. Fallbacks are explicit. The point where absence becomes an error is clear.

---

## Creating OptionalContexts

### From Nullable Suppliers

The `io()` factory handles null gracefully: null becomes empty:

```java
// If findById returns null, the context is empty
OptionalContext<IOKind.Witness, User> user =
    OptionalContext.io(() -> repository.findById(userId));

// Same pattern with JavaOptionalContext
JavaOptionalContext<IOKind.Witness, User> user =
    JavaOptionalContext.io(() -> repository.findById(userId));
```

### From Maybe/Optional-Returning Computations

When your code already returns the optional type:

```java
// Maybe-returning supplier
OptionalContext<IOKind.Witness, Config> config =
    OptionalContext.ioMaybe(() -> configLoader.load("app.properties"));

// Optional-returning supplier
JavaOptionalContext<IOKind.Witness, Config> config =
    JavaOptionalContext.ioOptional(() -> configLoader.load("app.properties"));
```

### Pure Values

For known values:

```java
// Present value
OptionalContext<IOKind.Witness, Integer> some = OptionalContext.some(42);
JavaOptionalContext<IOKind.Witness, Integer> present = JavaOptionalContext.some(42);

// Empty
OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
JavaOptionalContext<IOKind.Witness, Integer> absent = JavaOptionalContext.none();

// From existing Maybe/Optional
Maybe<User> maybe = Maybe.just(user);
OptionalContext<IOKind.Witness, User> fromMaybe = OptionalContext.fromMaybe(maybe);

Optional<User> optional = Optional.of(user);
JavaOptionalContext<IOKind.Witness, User> fromOpt = JavaOptionalContext.fromOptional(optional);
```

---

## Transforming Values

### map: Transform Present Values

```java
OptionalContext<IOKind.Witness, String> name = OptionalContext.some("Alice");

OptionalContext<IOKind.Witness, String> upper = name.map(String::toUpperCase);
// → some("ALICE")

OptionalContext<IOKind.Witness, String> empty = OptionalContext.none();
OptionalContext<IOKind.Witness, String> stillEmpty = empty.map(String::toUpperCase);
// → none (map doesn't run on empty)
```

---

## Chaining Computations

### via: Chain Dependent Lookups

```java
OptionalContext<IOKind.Witness, Address> address =
    OptionalContext.<User>io(() -> userRepo.findById(userId))
        .via(user -> OptionalContext.io(() -> addressRepo.findByUserId(user.id())));
```

If the user isn't found, the address lookup never runs. If the user exists but has no address, the result is empty. Both cases produce the same `none()` outcome.

### flatMap: Type-Preserving Chain

```java
OptionalContext<IOKind.Witness, Profile> profile =
    lookupUser(userId)
        .flatMap(user -> lookupProfile(user.profileId()))
        .flatMap(profile -> enrichProfile(profile));
```

### then: Sequence Ignoring Values

```java
OptionalContext<IOKind.Witness, String> result =
    validateExists()
        .then(() -> fetchData())
        .then(() -> processResult());
```

---

## Providing Fallbacks

### orElse: Fallback to Another Context

The primary pattern for lookup chains:

```java
OptionalContext<IOKind.Witness, Config> config =
    OptionalContext.<Config>io(() -> loadFromEnvironment())
        .orElse(() -> OptionalContext.io(() -> loadFromFile()))
        .orElse(() -> OptionalContext.io(() -> loadFromDefaults()))
        .orElse(() -> OptionalContext.some(Config.hardcodedDefaults()));
```

Each fallback runs only if all previous attempts returned empty.

### orElseValue: Fallback to a Direct Value

When the fallback is known:

```java
OptionalContext<IOKind.Witness, Integer> count =
    OptionalContext.<Integer>io(() -> cache.getCount())
        .orElseValue(0);  // Default to zero if not cached
```

### recover: Transform Absence

When you need to compute the fallback:

```java
OptionalContext<IOKind.Witness, Config> config =
    loadConfig()
        .recover(unit -> {
            log.info("No config found, using defaults");
            return Config.defaults();
        });
```

The `unit` parameter is always `Unit.INSTANCE`; it's the "error" type for optionality, representing the absence of information about *why* the value is missing.

### recoverWith: Fallback to Another Computation

```java
OptionalContext<IOKind.Witness, Data> data =
    fetchFromPrimary()
        .recoverWith(unit -> fetchFromBackup());
```

---

## Converting to ErrorContext

Often, absence at some point becomes an error. The boundary is explicit:

```java
OptionalContext<IOKind.Witness, User> optionalUser =
    OptionalContext.<User>io(() -> userRepo.findById(userId));

// Absence → Typed Error
ErrorContext<IOKind.Witness, UserNotFound, User> requiredUser =
    optionalUser.toErrorContext(new UserNotFound(userId));

// Now we can use ErrorContext operations
Either<UserNotFound, User> result = requiredUser.runIO().unsafeRun();
```

This runs the underlying computation and converts the result. For deferred conversion, use the escape hatch to the raw transformer.

---

## Converting Between Optional Types

`JavaOptionalContext` can convert to `OptionalContext`:

```java
JavaOptionalContext<IOKind.Witness, User> javaOptional =
    JavaOptionalContext.io(() -> repo.find(id));

OptionalContext<IOKind.Witness, User> maybeContext =
    javaOptional.toOptionalContext();
```

---

## Execution

### runIO: Get an IOPath

```java
// For OptionalContext
OptionalContext<IOKind.Witness, User> optionalCtx = OptionalContext.some(user);
IOPath<Maybe<User>> maybeIO = optionalCtx.runIO();

// For JavaOptionalContext
JavaOptionalContext<IOKind.Witness, User> javaCtx = JavaOptionalContext.some(user);
IOPath<Optional<User>> optionalIO = javaCtx.runIO();

// Execute
Maybe<User> maybeResult = maybeIO.unsafeRun();
Optional<User> optionalResult = optionalIO.unsafeRun();
```

### runIOOrElse: Value or Default

```java
User user = userContext.runIOOrElse(User.guest());
```

### runIOOrThrow: Value or Exception

```java
User user = userContext.runIOOrThrow();  // Throws NoSuchElementException if empty
```

---

## Real-World Patterns

### Cache-Through Pattern

```java
public OptionalContext<IOKind.Witness, Product> getProduct(String id) {
    return OptionalContext.<Product>io(() -> cache.get(id))
        .orElse(() -> {
            Product product = database.find(id);
            if (product != null) {
                cache.put(id, product);  // Populate cache
            }
            return product == null
                ? OptionalContext.none()
                : OptionalContext.some(product);
        });
}
```

### Configuration Layering

```java
public OptionalContext<IOKind.Witness, String> getSetting(String key) {
    return OptionalContext.<String>io(() -> System.getenv(key))          // Environment first
        .orElse(() -> OptionalContext.io(() -> System.getProperty(key))) // System property
        .orElse(() -> OptionalContext.io(() -> configFile.get(key)))     // Config file
        .orElse(() -> OptionalContext.io(() -> defaults.get(key)));      // Defaults last
}
```

### Validation with Optional Fields

```java
record UserInput(String name, String email, String phone) {}

public OptionalContext<IOKind.Witness, String> getContactMethod(UserInput input) {
    return OptionalContext.<String>io(() -> nullIfBlank(input.email()))
        .orElse(() -> OptionalContext.io(() -> nullIfBlank(input.phone())));
    // Returns the first available contact method
}

private String nullIfBlank(String s) {
    return s == null || s.isBlank() ? null : s;
}
```

### Graceful Degradation

```java
public OptionalContext<IOKind.Witness, DashboardData> loadDashboard(String userId) {
    return OptionalContext.<DashboardData>io(() -> fullDashboardService.load(userId))
        .orElse(() -> OptionalContext.io(() -> simplifiedDashboard(userId)))
        .orElse(() -> OptionalContext.some(DashboardData.empty()));
}
```

---

## Escape Hatch

When you need the raw transformer:

```java
OptionalContext<IOKind.Witness, User> ctx = OptionalContext.some(user);
MaybeT<IOKind.Witness, User> transformer = ctx.toMaybeT();

JavaOptionalContext<IOKind.Witness, User> jCtx = JavaOptionalContext.some(user);
OptionalT<IOKind.Witness, User> transformer = jCtx.toOptionalT();
```

---

## Summary

| Operation | Purpose |
|-----------|---------|
| `io(supplier)` | Create from nullable supplier |
| `ioMaybe(supplier)` / `ioOptional(supplier)` | Create from Maybe/Optional supplier |
| `some(value)` | Create present context |
| `none()` | Create empty context |
| `map(f)` | Transform present value |
| `via(f)` / `flatMap(f)` | Chain dependent computation |
| `orElse(supplier)` | Provide fallback context |
| `orElseValue(value)` | Provide fallback value |
| `toErrorContext(error)` | Convert absence to typed error |
| `runIO()` | Extract IOPath for execution |

Optional contexts embrace the wisdom that emptiness can be meaningful. Not every missing value is a bug. Sometimes there's more in the empty than the full.

~~~admonish tip title="See Also"
- [MaybeT Transformer](../transformers/maybet_transformer.md) - The transformer behind OptionalContext
- [OptionalT Transformer](../transformers/optionalt_transformer.md) - The transformer behind JavaOptionalContext
- [Maybe Monad](../monads/maybe_monad.md) - The Maybe type
- [Optional Monad](../monads/optional_monad.md) - Working with java.util.Optional
~~~

---

**Previous:** [ErrorContext](effect_contexts_error.md)
**Next:** [ConfigContext](effect_contexts_config.md)
