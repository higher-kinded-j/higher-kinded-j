# Unreleased: 0.5.0

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.5.0-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

~~~admonish info title="At a glance"
- **`@PathSource` has a page of its own**, beside `GenericPath` in the Effect Path chapter.
- **`StateT` is built from its state function alone**, and `mapT` takes only the transformation, as on every other transformer.
~~~

---

## Mapping {#mapping}

---

## Optics {#optics}

---

## Effect Paths {#effect-paths}

- **`StateT` is built from its state function alone** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): its constructor, `create` and `StateTKindHelper.stateT` take no `Monad`, and `mapT` takes only the transformation, as on every other transformer. The record holds only that function, so `equals`, `hashCode` and `toString` leave the `Monad` out. See [Running StateT Computations](../transformers/statet_transformer.md#running-statet-computations).
- **`@PathSource` points an effect algebra to `FreePath`** ([#1008](https://github.com/higher-kinded-j/higher-kinded-j/issues/1008)): a Path over a witness that `@EffectAlgebra` generates now draws a note at the witness. The algebra has a `Functor` and no `Monad` to pass to the Path's `of` and `pure`, so the note shows how to wrap its programs in a `FreePath`. See [A witness another processor writes](../effect/path_source.md#a-witness-another-processor-writes).

---

## Spring {#spring}

---

## Testing {#testing}

---

## Build and tooling {#build-and-tooling}

- **`MigrateDeprecationsTo0_5_0` drops the `Monad` that `StateT` no longer takes** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): its new `RemoveStateTMonadArgument` recipe rewrites the constructor, `create`, `stateT` and `mapT` calls. Run it from `hkj-openrewrite` 0.5.0 while the project is on 0.4.11, since it matches the 0.4.x signatures. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).
- **The `state-t-mapt-arity` check is retired** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): every `mapT` now takes the function alone, so there is no missing argument to report. A `disable=` or `severity:` entry that names it has no effect. The `kind-value-narrow` message now names the right accessor: `run()` on `ReaderT` and `WriterT`, and `runStateT(state)` on `StateT`. See [Compile-Time Checks](../tooling/compile_checks.md).

---

## Documentation {#documentation}

- **`@PathSource` has a page of its own** ([#999](https://github.com/higher-kinded-j/higher-kinded-j/issues/999)): [Custom Paths with `@PathSource`](../effect/path_source.md) says when a generated Path is worth it over `GenericPath`, what each capability generates, and how `errorType` types recovery.

---

## Upgrading from 0.4.11 {#upgrading}

### Before you upgrade {#before-you-upgrade}

- **Run `MigrateDeprecationsTo0_5_0` before you move to 0.5.0** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): take `hkj-openrewrite` 0.5.0 for the run, and keep the project on 0.4.11 until it finishes. Its `StateT` recipe matches the 0.4.x signatures, so it rewrites nothing once the build resolves 0.5.0. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

### What a running program can notice {#runtime-changes}

#### Effect Paths {#runtime-effect-paths}

- **`StateT` equality leaves out the `Monad`** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): two `StateT` values wrapping the same function instance are equal, with equal hash codes, whichever `Monad` built them. `toString` prints only `runStateTFn`, so a test comparing the old text fails.

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|
| Effect Paths | A `Monad` passed to `new StateT<>(fn, monad)`, `StateT.create`, `StateTKindHelper.stateT` or `StateT.mapT` | Run the recipe as [Before you upgrade](#before-you-upgrade) says, or drop the argument by hand | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | A two-component `StateT` record pattern, or `StateT::create` or `StateT::new` as a `BiFunction` | Match the one component, or take a `Function`, by hand | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | A `StateT` in `var` or a chained call over a witness with a type argument, such as `EitherKind.Witness<E>`, which now infers `Object` for it | Name the types, as in `StateT.<S, EitherKind.Witness<E>, A>create(fn)`, or declare the variable | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | `evalStateT` and `execStateT` without a `Monad`, deprecated since 0.4.6 | Pass the outer `Monad<F>`, the one `Instances.stateT` took, as the last argument | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | `StateT.monadF()`, deprecated since 0.4.6 | Keep a reference to the outer `Monad<F>` you built the stack with, and use it in place of the call | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |

### Deprecated for removal in 0.6.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.4.11](v0_4_11.md)
