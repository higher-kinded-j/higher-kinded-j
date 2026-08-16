# Compile-Time Checks: the HKJ Checker

~~~admonish info title="What You'll Learn"
- Every check the HKJ compile-time checker performs, and what each catches
- How to configure (disable / change severity) individual checks
- The javac-plugin-only boundary, and the no-false-positives policy
~~~

The HKJ checker is a `javac` compiler plugin (`hkj-checker`) that catches
Higher-Kinded-J mistakes during compilation instead of at runtime (or,
for the silent ones, instead of never). It is the **authoritative
reference** for what is detected; the per-feature *Common Compiler
Errors* chapters cross-link here.

---

## Why a checker

Many HKJ mistakes are *silent*: the code type-checks (generics erasure
hides the problem) and only fails at runtime (or, worse, carries the
wrong value silently). A representative example:

```java
MaybePath<Integer> r = Path.just(1).via(n -> Path.io(() -> n + 1)); // IOPath, not MaybePath
```

This compiles, then throws `IllegalArgumentException` at runtime. The
checker turns this class of bug into a compile-time diagnostic.

~~~admonish warning title="javac-plugin-only boundary"
The checker runs inside `javac`, driven by the HKJ Gradle/Maven plugin
(or a manual `-Xplugin:HKJChecker`). **IDEs and non-`javac` compilers
do not run it**. There you still see only the raw compiler message (or
nothing, for the silent cases). The *Common Compiler Errors* chapters
remain the reference for that audience; this page is the reference for
what the checker adds on top in a plugin-driven build/CI.
~~~

---

## What the checker detects

Each check has an id (used for configuration). **Default** is the
severity when nothing is configured; *warn-default* checks are the
"sole signal over javac-accepted code" ones, kept at warning during
soak.

| Check id | Detects | Default | Companion to a javac error? |
|---|---|---|---|
| `path-type-mismatch` | Different Path types mixed in `via`/`then`/`zipWith`/`zipWith3`/`recoverWith`/`orElse` | error | No: sole signal (else runtime `IllegalArgumentException`) |
| `effect-composition` | `Interpreters.combine()` called with an unsupported arity | error | Companion |
| `transformer-missing-monad` | Zero-arg construction of `EitherT`/`OptionalT`/`MaybeT`/`ReaderT`/`StateT`/`WriterTMonad` (the outer `Monad<F>`, and `Monoid<W>` for WriterT, is required) | error | Companion |
| `free-switch-exhaustive` | A `switch` over `Free` matching `Pure`/`Suspend`/`FlatMapped` but missing `HandleError`/`Ap` | error | Companion |
| `discarded-effect` | A lazy effect (`Path`/`IO`/`Free`, the `Chainable` hierarchy) built then dropped as a bare statement, a silent no-op | error | No: sole signal |
| `state-t-mapt-arity` | `StateT.mapT(f)` missing the leading `Monad<G>` (only `StateT.mapT` takes it) | error | Companion |
| `error-type-mismatch` | An Either chain step whose error type `E` differs from the chain's and is silently erased through `Chainable<B>` (latent `ClassCastException`) | **warn** | No: sole signal |
| `kind-value-narrow` | `.value()` on a bare `Kind` (it is on the concrete transformer; narrow first) | error | Companion |
| `witness-arity` | A higher-kinded witness (type parameter or class) not `WitnessArity`-bounded, used as `Kind`/`Kind2`/`Monad`/`Functor`/`Applicative` | error | Companion |
| `via-non-path` | `via`/`flatMap`/`then` given a function that returns a plain value instead of a Path (use `map`) | error | Companion |
| `map-nests-effect` | `map` given a function that returns the **same** Path type: silently nests the effect; you meant `via` | **warn** | No: sole signal |
| `migration-nudge` | `Free.liftF`/`Free.suspend` (→ FreePath/`*Ops`) and `InjectInstances.injectLeft/injectRight/injectRightThen` (→ `@ComposeEffects`): valid code, an ergonomics nudge | **warn** | No: advisory |
| `raw-kind` | A raw `Kind`/`Kind2` used as a type (variable, parameter, field or cast): drops the witness type argument, the one compile-silent route to a wrong-witness `narrow()` and a runtime `KindUnwrapException` | **warn** | No: sole signal |

"Companion" checks add an actionable HKJ message beside `javac`'s own
cryptic error. "Sole signal" / "advisory" checks are the only
compile-time signal (the code otherwise compiles), which is why the
heuristic and advisory ones default to *warn*.

~~~admonish info title="Generated sources are not checked"
Every check is advice you are expected to act on, and nobody edits
annotation-processor output — so the checker skips any type annotated
`org.higherkindedj.optics.annotations.Generated`, wherever it sits in
its file, along with everything nested inside it.

Without that gate, `@EffectAlgebra`'s generated `*Ops` class would carry
a `migration-nudge` telling you to use the generated `*Ops` smart
constructors — from inside the very class that provides them. Your own
call sites are checked as normal.

Only HKJ's own marker gates this. `javax.annotation.processing.Generated`
and its `jakarta` counterpart are *not* honoured: JaCoCo excludes any
type carrying an annotation named `Generated`, so those markers are
routinely hand-applied to hand-written code — as HKJ does itself with
`org.higherkindedj.annotation.Generated`. Honouring them would let a
coverage marker silently switch off every check on a file.
~~~

---

## Configuration

Checks are configured through the plugin argument string. `javac`
splits `-Xplugin` on whitespace into the plugin name and its arguments:

```
-Xplugin:HKJChecker disable=<id>[,<id>...] severity=error|warn severity:<id>=error|warn
```

- `disable=<id>[,<id>...]`: turn the listed checks off entirely (e.g.
  `disable=map-nests-effect,discarded-effect`).
- `severity=error|warn`: the global default for the error-default
  checks (default `error`). It does **not** silently promote the
  warn-default checks (`error-type-mismatch`, `map-nests-effect`,
  `migration-nudge`, `raw-kind`). Those stay `warn` unless promoted
  explicitly.
- `severity:<id>=error|warn`: override one check. Wins over the global
  default and over a check's built-in default, so a team can promote a
  warn-default check, e.g. `severity:error-type-mismatch=error`, or
  downgrade a single error-default check.

Resolution order for a check: an explicit `severity:<id>=…` override;
else the warn-default checks stay `warn`; else the global `severity`.

Unknown ids and unparseable values are ignored rather than failing the
build. A typo in a compiler argument must never break compilation.

### Opting out one declaration

`disable=` is project-wide. To accept a single deliberate call site,
name the check id in `@SuppressWarnings` on the enclosing class, method
or variable — `hkj-checker` covers every check at once:

```java
@SuppressWarnings("migration-nudge") // the ergonomic layer the nudge points at
public final class StateOps {
    public static <S, A> Free<StateOpKind.Witness<S>, A> view(Getter<S, A> optic) {
        // ...
        return Free.liftF(StateOpKindHelper.STATE_OP.widen(op), functor());
    }
}
```

A local variable takes the token too, which is the narrowest scope
available:

```java
@SuppressWarnings("path-type-mismatch") // deliberate: converted downstream
var mixed = Path.just(1).via(_ -> Path.io(() -> 2));
```

Suppression is per check and accumulates outward-in: a token on a class
reaches everything inside it, other checks on the same statement keep
running, and a sibling declaration is unaffected. HKJ's own smart-
constructor layer (`StateOps`, `ErrorOps`, `OpticPrograms`) and the
`FreePath`/`FreeFactory` fluent API use exactly this — they are the
layer `migration-nudge` steers callers towards, so they must build the
`Free` primitives directly.

Two deliberate limits. The tokens reach the *error*-default checks as
well as the advisory ones, so `@SuppressWarnings("path-type-mismatch")`
will silence a build-failing diagnostic — scope it narrowly. And javac's
blanket `"all"` is **not** honoured: it is too common on legacy and
generated code for a silent switch-off to be safe, so an HKJ opt-out is
always spelled explicitly.

### Manual setup

The HKJ Gradle/Maven plugin enables the checker by default and passes
the `-Xplugin` argument through; see
[Build Plugins](gradle_plugin.md). Without the build plugin:

**Gradle**

```gradle
dependencies {
    annotationProcessor("io.github.higher-kinded-j:hkj-checker:<version>")
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xplugin:HKJChecker disable=effect-composition")
}
```

**Maven**

```xml
<annotationProcessorPaths>
  <path>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-checker</artifactId>
    <version>${hkj.version}</version>
  </path>
</annotationProcessorPaths>
<compilerArgs>
  <arg>-Xplugin:HKJChecker severity=warn</arg>
</compilerArgs>
```

---

## How it works

A standard `com.sun.source.util.Plugin` hooks the `ANALYZE` phase (after
type attribution). For each compilation unit a tree scanner runs the
enabled checks, resolving types via the public `javax.lang.model`
utilities (or, for the original mismatch check, javac's attributed
types) and emitting diagnostics at the exact source location. It is
part of normal compilation, with no separate build step.

---

## No false positives

The checker follows a strict **no-false-positives policy**: it is
better to miss a real mistake than to report a false one. When a type
cannot be resolved (type variables, wildcards, method references,
indirect construction, runtime polymorphism) the check is **skipped
silently**. The library's runtime checks remain the safety net for
anything the compiler cannot see.

~~~admonish info title="Key Takeaways"
* The checker catches 13 classes of HKJ mistake at compile time,
  including several that are otherwise entirely silent.
* Configure via `-Xplugin:HKJChecker disable=<id> severity=...`.
* javac-plugin-only: IDEs/non-javac builds still rely on the
  *Common Compiler Errors* chapters.
* No false positives: unresolved cases are skipped, not guessed.
~~~

~~~admonish tip title="See Also"
- [Build Plugins](gradle_plugin.md) - plugin setup and configuration
- [Effect Compiler Errors](../effect/compiler_errors.md),
  [Transformer Compiler Errors](../transformers/common_errors.md),
  [Optics Compiler Errors](../optics/compiler_errors.md) - the
  per-feature references (and the IDE/non-plugin audience)
~~~

---

**Previous:** [Manual Gradle and Maven Setup](manual_setup.md)
**Next:** [Migration Recipes](openrewrite.md)
