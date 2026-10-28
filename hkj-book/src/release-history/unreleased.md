# Unreleased: 0.6.0

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.6.0-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

~~~admonish info title="At a glance"
- **javac checks what each step of a Path chain returns.** A step of another kind of Path, or one that fails with an error the chain does not carry, is a compile error in the IDE as well as the build.
- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter**: a `witheMail` beside `geteMail()` gives an `eMail()` lens.
- **A `@GenerateFocus` message that offers a navigator asks first for the renames it needs.** Following its fix line no longer leads into another note or error.
- **A `@GenerateMapping` bean refusal's fix line compiles once followed**, and names the near miss it passed over, such as `setx(int)` beside `Integer getX()`.
~~~

---

## Mapping {#mapping}

- **A bean property's fix line compiles once followed** ([#1149](https://github.com/higher-kinded-j/higher-kinded-j/issues/1149)). The setter offered for `eMails` is `seteMails`, not `setEMails`. A rename to a keyword such as `default` is offered no marker, and a marker replaces the spec's own rename. A near miss such as `setx(int)` beside `Integer getX()` is named. See [When an unpaired accessor is refused](../mapping/rules.md#unpaired-accessors).

---

## Optics {#optics}

- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)). A `witheMail(String)` beside `geteMail()`, as Lombok's `beanspec` capitalisation writes them, gives an `eMail()` lens, as `withEMail` beside `getEMail()` does. The processor says why it refuses a class whose only wither is `with2D(int)`. See [Wither Classes to Lenses](../optics/importing_optics.md#wither-classes-to-lenses).
- **A spec lens reads a getter inherited from two interfaces as javac does** ([#1136](https://github.com/higher-kinded-j/higher-kinded-j/issues/1136)). An abstract source that inherits `String name()` from one interface and `CharSequence name()` from another is now read as returning `String`, so a `Lens<Src, String>` over it builds. A `@ViaBuilder` chain reads an inherited `toBuilder()` or `build()` the same way. See [Compiler Messages](../optics/compiler_errors.md#-reads--not-the-lenss-focus-).
- **Messages that offer a navigator ask first for the renames it needs** ([#1272](https://github.com/higher-kinded-j/higher-kinded-j/issues/1272)). The note to widen `items` beside an `Items` with a navigator, and five other messages, now ask for one of the two to be renamed first. They also name a field such as `last` that the navigator could not reach. See [Two fields with one navigator name](../optics/focus_navigation.md#two-fields-with-one-navigator-name).
- **`OpticOpFunctor.map` applies its function** ([#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154)). It returned the instruction unchanged, so code that mapped a lifted `OpticOp` failed later with `ClassCastException`. Each `OpticOp` record now carries a continuation `k`, as an `@EffectAlgebra` operation does, and an interpreter hands each result to `op.k()` in place of wrapping it in `Free.pure`. See [What an interpreter actually is](../optics/interpreters.md#what-an-interpreter-actually-is).

---

## Effect Paths {#effect-paths}

- **javac checks each chain step's kind of Path and error type** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)). A step passed to `via` or another chaining method must be the chain's own kind of Path, so a mismatch is a compile error rather than an exception at run time. A step may still fail with a variant of the chain's error. See [The Family Parameter](../effect/capabilities.md#the-family-parameter).
- **`TrampolinePath` and `FreePath` combine three paths with `zipWith3`** ([#1302](https://github.com/higher-kinded-j/higher-kinded-j/issues/1302)): both already combined two paths with `zipWith`. The three-way combine stays lazy until `run()` or `foldMap`, so a deep chain stays stack-safe. See [Combinable (Applicative)](../effect/capabilities.md#combinable-applicative).

---

## Spring {#spring}

---

## Testing {#testing}

---

## Build and tooling {#build-and-tooling}

- **The HKJ checker names the fix beside javac's error for a mismatched step** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)). `error-type-mismatch` says where to type the chain, as in `write .<OrderError>mapError(...)`, or that the step's error needs mapping. `path-type-mismatch` now reads method-reference steps, `VResultPath` and the accumulating methods. See [Compile-Time Checks](../tooling/compile_checks.md).
- **An OpenRewrite recipe adds the Path family to capability types** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)). `org.higherkindedj.openrewrite.AddPathFamily` rewrites `Chainable<A>` to `Chainable<?, A>`, and likewise the other capability types, a `PathProvider`'s `createPath` included. See [0.6.0 Path family](../tooling/openrewrite.md#060-path-family).

---

## Documentation {#documentation}

- **`StreamPath` docs no longer call it lazy where the source is read at once** ([#1290](https://github.com/higher-kinded-j/higher-kinded-j/issues/1290)). The cheat sheet and home page describe sequences you can run more than once, and `toStreamPath` says the non-null focuses are collected into a list when it is called.
- **The StreamPath docs say when the source is read** ([#1278](https://github.com/higher-kinded-j/higher-kinded-j/issues/1278)). `Path.stream` reads its whole stream when it is called, so the stream must be finite. `Path.streamIterate`, `StreamPath.generate` and `StreamPath.fromSupplier` build a fresh stream each time the path runs, so `take` can limit an infinite one. See [Choosing the Right Streaming Tool](../monads/vstream.md#choosing-the-right-streaming-tool).
- **The Effect Paths glossary gains three entries** ([#1301](https://github.com/higher-kinded-j/higher-kinded-j/issues/1301)): `FreeApPath`, `FreePath` and `GenericPath`. Each gives a definition, its factories on `Path`, and its main operations. See [Effect Paths](../glossary/effect-paths.md#freeappath).
- **The Effect Paths glossary gains four more entries** ([#1258](https://github.com/higher-kinded-j/higher-kinded-j/issues/1258)): `ReaderPath`, `TrampolinePath`, `WithStatePath` and `WriterPath`. Each gives a definition, its factories on `Path`, its main operations and a link to its chapter page. See [Effect Paths](../glossary/effect-paths.md#readerpath).
- **Five examples keep one kind of Path and one error type through their chains.** Composition Patterns ran an `IOPath` step in an `EitherPath` chain, which throws when it runs; it now builds an `IOPath` chain. The Quickstart, a migration recipe and the `hkj-guide` and `hkj-spring` skills now name the chain's error type with a witness. See [Composition Patterns](../effect/composition.md#mistake-2-side-effects-in-map).
- **Generated Javadoc uses an article only before a type the processor chose** ([#1307](https://github.com/higher-kinded-j/higher-kinded-j/issues/1307)). A record, class, property or operation is named with none, as in `field of {@link Address}` and `Lifts the {@code Charge} operation`. A path or optic type keeps the article its name takes, as in `Creates an {@link AffinePath}`.

---

## Upgrading from 0.5.0 {#upgrading}

### Before you upgrade {#before-you-upgrade}

### What a running program can notice {#runtime-changes}

#### Optics {#runtime-optics}

- **Of two withers for one getter, `@ImportOptics` sets through the capitalised spelling** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)): the lens for a class with both `withEMail` and `witheMail` beside `eMail()` set through whichever the class declared first. It now sets through `withEMail`, and a note names `witheMail`, which a spec interface's `@Wither` can choose.
- **An optic interpreter that wraps each result in `Free.pure` skips the operation's continuation** ([#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154)): a program from `OpticPrograms` runs as before, but a function mapped onto an `OpticOp`, through `OpticOpFunctor` or `Free.translate`, is not applied. Hand the result to `op.k()` and return what it gives.

#### Effect Paths {#runtime-effect-paths}

- **A `ValidationPath` keeps its own semigroup through a step** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)): the result of `via`, `then`, `recoverWith` and `orElse` accumulated with the step's semigroup, and now accumulates with the receiver's. After `mapError`, whose placeholder semigroup throws, a later accumulation now throws even past a step that had a semigroup; use `mapErrorWith` instead.
- **An `EitherOrBothPath` keeps its own semigroup through a recovery** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)): the result of `recoverWith` and `orElse` accumulated with the recovery's semigroup, and now accumulates with the receiver's, as `via` and `then` already did.

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|
| Effect Paths | A chain step of another kind of Path, or with an error, environment, state, log or witness type the chain does not carry | Convert the step, map its error, or type the chain on the wider error where it starts, as [Common Compiler Errors](../effect/compiler_errors.md) shows | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | Code that names a capability type with its old arguments, such as `Effectful<A>`, or a `PathProvider` returning `Chainable<A>` | Write `Effectful<?, A>`, or `<P, A> Effectful<P, A>` in a helper that chains. The `AddPathFamily` recipe writes the first | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | A chain whose receiver is typed with a wildcard error, such as `EitherPath<? extends AppError, String>` | Widen it first, as in `p.<AppError>mapError(e -> e).via(...)`, or declare it as `EitherPath<AppError, String>` | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | A pattern or cast from a capability type to a concrete Path, such as `case MaybePath<String> m` on a `Chainable<?, String>`; a cast is unchecked, so it fails under `-Werror` | Match from `Composable<String>`, or build the concrete Path directly | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Optics | An optic interpreter or other code of your own that names an `OpticOp` record: a pattern or type such as `OpticOp.Get<?, ?>` or `OpticOp.Set<S, A>`, or a constructor call such as `new OpticOp.Get<>(source, getter)` | Add the third type argument, as in `OpticOp.Get<?, ?, ?>`, and pass a continuation, `Function.identity()`, to a constructor. Hand each result to `op.k()` and return what it gives, in place of `Free.pure(result)` | [#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154) |

### Deprecated for removal in 0.7.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.5.0](v0_5_0.md)
