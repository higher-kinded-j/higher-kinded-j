# Monad Transformers Quickstart

~~~admonish info title="What You'll Learn"
- Your first `EitherT` workflow combining typed errors with `CompletableFuture`
- Combining `OptionalT` and `For` for an async lookup chain in a few lines
- A two-capability MTL example showing polymorphic, stack-independent code
- Where to read next depending on what you want to do
~~~

This page assumes you have Higher-Kinded-J on your classpath. If not, start with the [book-level Quickstart](../quickstart.md).

~~~admonish note title="Path First, Stack Later"
Before reaching for a raw transformer, check whether the [Effect Path API](../effect/ch_intro.md) already covers your case. `EitherPath`, `MaybePath`, `OptionalPath`, `ReaderPath`, and `WithStatePath` wrap these transformers in a fluent API that handles witness types and `Kind` widening for you.

The transformer machinery on this page is for the cases where Path types do not fit, typically because you need a *different* outer monad (`CompletableFuture`, `IO`, a custom effect) or because you are writing polymorphic library code.
~~~

---

## 1. Combine async with typed errors using `EitherT`

The most common transformer use case: an asynchronous workflow whose steps can fail with typed domain errors. Without `EitherT` you end up nesting `thenCompose` inside `Either.fold` calls; with it the whole chain reads as a sequence.

```java
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTMonad;
import org.higherkindedj.hkt.expression.For;

sealed interface OrderError {
    record InvalidOrder(String reason) implements OrderError {}
    record OutOfStock(String sku)      implements OrderError {}
}

var futureMonad  = CompletableFutureMonad.INSTANCE;
var eitherTMonad = new EitherTMonad<CompletableFutureKind.Witness, OrderError>(futureMonad);

var workflow = For.from(eitherTMonad, EitherT.fromKind(validateOrder(order)))
    .from(validated -> EitherT.fromKind(checkInventory(validated)))
    .from(reserved  -> EitherT.fromKind(processPayment(reserved)))
    .yield((validated, reserved, receipt) -> receipt);
```

If any step yields `Left`, the rest are skipped and the error propagates through the `CompletableFuture`. The witness type appears in one place (the `eitherTMonad`); the body of the comprehension reads like ordinary sequential code.

---

## 2. Async lookup chains with `OptionalT`

Multi-step lookups where any step might return nothing are the bread and butter of `OptionalT`. The same `For` shape works with the change of monad.

```java
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.future.CompletableFutureMonad;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTMonad;

var futureMonad    = CompletableFutureMonad.INSTANCE;
var optionalTMonad = new OptionalTMonad<CompletableFutureKind.Witness>(futureMonad);

var prefsLookup = For.from(optionalTMonad, OptionalT.fromKind(fetchUserAsync(userId)))
    .from(user    -> OptionalT.fromKind(fetchProfileAsync(user.id())))
    .from(profile -> OptionalT.fromKind(fetchPrefsAsync(profile.userId())))
    .yield((user, profile, prefs) -> prefs);
```

If `fetchUserAsync` returns `Optional.empty()`, neither `fetchProfileAsync` nor `fetchPrefsAsync` is called. No nested `orElse(CompletableFuture.completedFuture(Optional.empty()))` boilerplate, no manual fallback wiring.

---

## 3. Capability-based code with MTL

When the same business logic must run against different stacks (production async, synchronous tests, audit interpreter) write it once against an MTL capability. Here a function reads configuration without naming a concrete transformer:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;

record AppConfig(String dbUrl, int maxRetries) {}

<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    buildConnectionString(MonadReader<F, AppConfig> env) {
  return For.from(env, env.ask())
      .yield(config -> config.dbUrl() + "?retries=" + config.maxRetries());
}
```

The function declares "I need to read an `AppConfig`" and nothing else. A test caller can supply a `ReaderTMonadReader<IdKind.Witness, AppConfig>` to run synchronously; a production caller can supply a `ReaderTMonadReader<CompletableFutureKind.Witness, AppConfig>`. The function does not change.

---

## Where next?

- **First time on this page?** Read [Stack Archetypes](archetypes.md), which presents seven named patterns covering most enterprise composition needs.
- **Need a quick lookup?** [Transformers at a Glance](transformers_at_a_glance.md) is a one-page reference card.
- **Coming from imperative Java or the Effect Path API?** The [Migration Cookbook](migration_cookbook.md) has side-by-side translations.
- **Need the underlying mechanics?** [Monad Transformers](transformers.md) explains why monads do not compose and what transformers fix.
- **Specific transformer?** Each of the per-transformer pages ([EitherT](eithert_transformer.md), [OptionalT](optionalt_transformer.md), [MaybeT](maybet_transformer.md), [ReaderT](readert_transformer.md), [StateT](statet_transformer.md), [WriterT](writert_transformer.md)) has its own worked example.
- **Stack-independent code?** Start with [MTL Capabilities](mtl_capabilities.md).

---

**Previous:** [Path or Transformer?](when_to_drop_to_transformers.md)
**Next:** [Transformers at a Glance](transformers_at_a_glance.md)
