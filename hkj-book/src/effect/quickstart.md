# Effect Path API Quickstart

~~~admonish info title="What You'll Learn"
- Your first `MaybePath` in about ten lines
- Combining `EitherPath` with `.via()` for a two-step workflow
- Composing three Path values with `ForPath` as a single readable expression
- Where to read next depending on what you want to do
~~~

This page assumes you have Higher-Kinded-J on your classpath. If not, start with the [book-level Quickstart](../quickstart.md).

You do not need to understand higher-kinded types, typeclasses, or the Kind encoding to use any of the code on this page. Path types are ordinary Java values you chain with familiar methods.

---

## 1. Handle absence with `MaybePath`

Some values just might not exist: a lookup that returns nothing, a nullable field. `MaybePath` lets you transform the value if it's present and skip the work if it isn't, without ever writing a null check.

```java
import org.higherkindedj.hkt.effect.Path;

var user = Path.maybe(userRepository.findById(id));   // Just(user) or Nothing
var greeting = user
    .map(User::name)                                   // runs only if Just
    .map(name -> "Hello, " + name);                    // still runs only if Just

String shown = greeting.run().orElse("Hello, stranger");
```

`run()` returns a plain `Maybe` you can interrogate with standard methods. You are never locked into the Path type.

---

## 2. Handle typed errors with `EitherPath`

When an operation can fail with a specific error type, `EitherPath` carries the error on the left and the value on the right. Successful steps chain with `.via()` (the HKJ name for monadic bind); a failure short-circuits the rest of the chain.

```java
import org.higherkindedj.hkt.effect.Path;

sealed interface AppError {
    record UserNotFound(String id) implements AppError {}
    record OrderFailed(String reason) implements AppError {}
}

EitherPath<AppError, Receipt> workflow =
    Path.maybe(userRepository.findById(userId))
        .toEitherPath(new AppError.UserNotFound(userId))   // Nothing → Left
        .via(user -> Path.either(orderService.create(user))) // chained step
        .map(Receipt::of);                                   // transform on success

Either<AppError, Receipt> result = workflow.run();
```

The whole pipeline has one shape, one failure mode, and one obvious place where each step's error handling lives.

---

## 3. Compose multiple paths with `ForPath`

`ForPath` is a for-comprehension designed specifically for Path types. Use it when your workflow reads better as a sequence of named bindings than as a chain of lambdas.

```java
import org.higherkindedj.hkt.effect.ForPath;

MaybePath<Summary> summary = ForPath
    .from(Path.maybe(userRepository.findById(userId)))
    .from(user -> Path.maybe(profileService.loadProfile(user)))
    .let((user, profile) -> profile.displayName())
    .yield((user, profile, name) -> new Summary(user.id(), name, profile.email()));
```

Every binding becomes available to every later step. Any `Nothing` in the chain short-circuits to `Nothing`. The same shape works for `EitherPath`, `TryPath`, `IOPath`, and the other Path types. See [ForPath Comprehension](forpath_comprehension.md).

---

## Where next?

- **Coming from imperative Java?** Read the [Migration Cookbook](migration_cookbook.md): side-by-side translations of `try/catch`, nullable lookups, `CompletableFuture`, and validation.
- **Choosing a Path type?** The [Path Types Overview](path_types.md) is a decision tree over the six core Path types.
- **Composing paths together?** [ForPath Comprehension](forpath_comprehension.md) is the idiomatic entry point for multi-step workflows.
- **Hardening for production?** [Patterns and Recipes](patterns.md) covers retries, fallback policies, parallelism, and integration testing.

~~~admonish tip title="Ready for hands-on?"
The [Effect API Tutorial](../tutorials/effect/effect_journey.md) is an exercise-driven companion to this chapter. Each lesson pairs a concept with a runnable Java exercise (in `Tutorial01_EffectPathBasics.java` and friends) and a worked solution. Recommended once you've finished this Quickstart.
~~~

---

**Previous:** [Effect Path API](ch_intro.md)
**Next:** [Core Paths](effect_path_overview.md)
