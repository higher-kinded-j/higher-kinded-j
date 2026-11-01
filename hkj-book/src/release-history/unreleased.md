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
- **A mapping over a deprecated type compiles under `-Werror`** ([#1381](https://github.com/higher-kinded-j/higher-kinded-j/issues/1381)). A deprecated v1 DTO, a bean's deprecated getter or a protobuf-java field declared `deprecated` drew warnings inside the generated Impl, where your own `@SuppressWarnings` cannot reach. A `@GenerateMapping` or `@GenerateMerge` Impl that names or calls a deprecated element now suppresses `deprecation`, or `removal`, on its own class. See [Build-time impact](../optics/production_readiness.md#build-time-impact).
- **Two more PATCH-bean fix lines compile once followed** ([#1293](https://github.com/higher-kinded-j/higher-kinded-j/issues/1293)). A legacy setter such as `setTimeout(Integer)` beside a rename of `timeout` is offered no `@Unmapped` marker, since the rename takes that name. A primitive word such as `setx(int)` is offered its wrapper, `setX(Integer)`. See [A method that only starts like an accessor](../mapping/rules.md#word-accessors).

---

## Optics {#optics}

- **`@ImportOptics` pairs a JavaBeans-spelt wither with its getter** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)). A `witheMail(String)` beside `geteMail()`, as Lombok's `beanspec` capitalisation writes them, gives an `eMail()` lens, as `withEMail` beside `getEMail()` does. The processor says why it refuses a class whose only wither is `with2D(int)`. See [Wither Classes to Lenses](../optics/importing_optics.md#wither-classes-to-lenses).
- **A spec lens reads a getter inherited from two interfaces as javac does** ([#1136](https://github.com/higher-kinded-j/higher-kinded-j/issues/1136)). An abstract source that inherits `String name()` from one interface and `CharSequence name()` from another is now read as returning `String`, so a `Lens<Src, String>` over it builds. A `@ViaBuilder` chain reads an inherited `toBuilder()` or `build()` the same way. See [Compiler Messages](../optics/compiler_errors.md#-reads--not-the-lenss-focus-).
- **Messages that offer a navigator ask first for the renames it needs** ([#1272](https://github.com/higher-kinded-j/higher-kinded-j/issues/1272)). The note to widen `items` beside an `Items` with a navigator, and five other messages, now ask for one of the two to be renamed first. They also name a field such as `last` that the navigator could not reach. See [Two fields with one navigator name](../optics/focus_navigation.md#two-fields-with-one-navigator-name).
- **`OpticOpFunctor.map` applies its function** ([#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154)). It returned the instruction unchanged, so code that mapped a lifted `OpticOp` failed later with `ClassCastException`. Each `OpticOp` record now carries a continuation `k`, as an `@EffectAlgebra` operation does, and an interpreter hands each result to `op.k()` in place of wrapping it in `Free.pure`. See [What an interpreter actually is](../optics/interpreters.md#what-an-interpreter-actually-is).

---

## Effect Paths {#effect-paths}

- **javac checks each chain step's kind of Path and error type** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)). A step passed to `via` or another chaining method must be the chain's own kind of Path, so a mismatch is a compile error rather than an exception at run time. A step may still fail with a variant of the chain's error. See [The Family Parameter](../effect/capabilities.md#the-family-parameter).
- **`EitherPath` gains `ensure`** ([#1338](https://github.com/higher-kinded-j/higher-kinded-j/issues/1338)): `ensure(test, onFail)` keeps a success whose value passes `test` and turns any other success into a failure built from its value, so a guard no longer needs an `if` returning a `Left`. See [EitherPath](../effect/path_either.md#error-handling).
- **`TrampolinePath` and `FreePath` combine three paths with `zipWith3`** ([#1302](https://github.com/higher-kinded-j/higher-kinded-j/issues/1302)): both already combined two paths with `zipWith`. The three-way combine stays lazy until `run()` or `foldMap`, so a deep chain stays stack-safe. See [Combinable (Applicative)](../effect/capabilities.md#combinable-applicative).
- **`NonDetPath` gains six list operations that `ListPath` already had** ([#1337](https://github.com/higher-kinded-j/higher-kinded-j/issues/1337)): `lastOption`, `anyMatch`, `allMatch`, `takeWhile`, `dropWhile` and `get`. Each behaves as its `ListPath` counterpart.
- **`NonDetPath` gains `foldRight`, `fold` and `foldMap`** ([#1322](https://github.com/higher-kinded-j/higher-kinded-j/issues/1322)): with `foldLeft`, it now has the four folds that `ListPath` and `StreamPath` offer, and each behaves as its `ListPath` counterpart. See the [Effect Path Cheatsheet](../cheatsheet.md).
- **`Monoid.of` builds a monoid from its empty value and its operation, and `Monoids.bigDecimalAddition()` sums money exactly** ([#1356](https://github.com/higher-kinded-j/higher-kinded-j/pull/1356)). `Monoid.of(Duration.ZERO, Duration::plus)` takes the place of an anonymous class, and an existing `Semigroup` can be the operation. See [Semigroup and Monoid](../functional/semigroup_and_monoid.md#a-monoid-of-your-own).
- **A `Selective` of your own runs only the branch its condition picks** ([#1358](https://github.com/higher-kinded-j/higher-kinded-j/pull/1358)). The default `whenS` ran its effect for a false condition, and the default `ifS` ran both branches. Both now leave the choice to `select`: one that keeps its contract skips what the condition does not pick, and one built from `ap` still runs every effect. See [Selective](../functional/selective.md).
- **`mapError` on `MaybePath`, `TryPath` and `CompletableFuturePath` carries the mapped error** ([#1332](https://github.com/higher-kinded-j/higher-kinded-j/issues/1332)): `recover` after it sees that error, where it used to see the old one. A success is a `Right` and the mapper is not called. `MaybePath` and `TryPath` return an `EitherPath`. `CompletableFuturePath` returns a `VResultPath`. See [Recoverable (MonadError)](../effect/capabilities.md#recoverable-monaderror).
- **`toEitherPath`, `toMaybePath` and `toTryPath` follow `recover` on a failed `CompletableFuturePath`** ([#1354](https://github.com/higher-kinded-j/higher-kinded-j/issues/1354)). A cancelled future gives `Left` of its `CancellationException`, or `Nothing`, instead of throwing. `toTryPath` holds the exception the future failed with, not a `CompletionException` around it.
- **A `VTaskPath` hands every handler the exception its task threw** ([#1396](https://github.com/higher-kinded-j/higher-kinded-j/pull/1396)). A checked one, such as an `IOException`, reached `runSafe`, `catching` and `toTryPath`, and any step after `via`, `then` or `guarantee`, wrapped in a `VTaskExecutionException`, so a retry on `IOException` gave up at once. Only `unsafeRun()`, which cannot throw one, still wraps it. See [VTaskPath](../effect/path_vtask.md#running-tasks).

---

## Spring {#spring}

---

## Testing {#testing}

---

## Build and tooling {#build-and-tooling}

- **The HKJ checker names the fix beside javac's error for a mismatched step** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)). `error-type-mismatch` says where to type the chain, as in `write .<OrderError>mapError(...)`, or that the step's error needs mapping. `path-type-mismatch` now reads method-reference steps, `VResultPath` and the accumulating methods. See [Compile-Time Checks](../tooling/compile_checks.md).
- **A build with an HKJ plugin applied can set the checker's options** ([#1336](https://github.com/higher-kinded-j/higher-kinded-j/issues/1336)). A hand-written `-Xplugin:HKJChecker severity=warn` failed the build with `plug-in not found: HKJChecker`. The Gradle and Maven plugins now add no checker argument when the build already names the checker. See [Configuring the checker](../tooling/compile_checks.md#configuration).
- **A build with the HKJ Gradle plugin can turn on Gradle's configuration cache** ([#1374](https://github.com/higher-kinded-j/higher-kinded-j/issues/1374)). With the cache on, `hkjInstallSkills` and `hkjDiagnostics` failed, and so did every build with `skills = true`, which runs `hkjInstallSkills`. Both tasks now run with it, and the next build reuses the cache. See [With the Plugin](../tooling/gradle_plugin.md#with-the-plugin).
- **An OpenRewrite recipe adds the Path family to capability types** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)). `org.higherkindedj.openrewrite.AddPathFamily` rewrites `Chainable<A>` to `Chainable<?, A>`, and likewise the other capability types, a `PathProvider`'s `createPath` included. See [0.6.0 Path family](../tooling/openrewrite.md#060-path-family).
- **`javadoc` runs in a build with the HKJ Gradle plugin applied** ([#1385](https://github.com/higher-kinded-j/higher-kinded-j/issues/1385)). With preview features on, the default, javadoc received `--enable-preview` without the release it needs, so `javadoc` failed, and so did `build` in a project that publishes a javadoc jar. The plugin now passes `--release` as well, unless the task passes its own `-source` or `--release`. See [Defaults](../tooling/gradle_plugin.md#defaults).

---

## Documentation {#documentation}

- **`StreamPath` docs no longer call it lazy where the source is read at once** ([#1290](https://github.com/higher-kinded-j/higher-kinded-j/issues/1290)). The cheat sheet and home page describe sequences you can run more than once, and `toStreamPath` says the non-null focuses are collected into a list when it is called.
- **The StreamPath docs say when the source is read** ([#1278](https://github.com/higher-kinded-j/higher-kinded-j/issues/1278)). `Path.stream` reads its whole stream when it is called, so the stream must be finite. `Path.streamIterate`, `StreamPath.generate` and `StreamPath.fromSupplier` build a fresh stream each time the path runs, so `take` can limit an infinite one. See [Choosing the Right Streaming Tool](../monads/vstream.md#choosing-the-right-streaming-tool).
- **The Effect Paths glossary gains three entries** ([#1301](https://github.com/higher-kinded-j/higher-kinded-j/issues/1301)): `FreeApPath`, `FreePath` and `GenericPath`. Each gives a definition, its factories on `Path`, and its main operations. See [Effect Paths](../glossary/effect-paths.md#freeappath).
- **The Effect Paths glossary gains four more entries** ([#1258](https://github.com/higher-kinded-j/higher-kinded-j/issues/1258)): `ReaderPath`, `TrampolinePath`, `WithStatePath` and `WriterPath`. Each gives a definition, its factories on `Path`, its main operations and a link to its chapter page. See [Effect Paths](../glossary/effect-paths.md#readerpath).
- **Five examples keep one kind of Path and one error type through their chains.** Composition Patterns ran an `IOPath` step in an `EitherPath` chain, which throws when it runs; it now builds an `IOPath` chain. The Quickstart, a migration recipe and the `hkj-guide` and `hkj-spring` skills now name the chain's error type with a witness. See [Composition Patterns](../effect/composition.md#mistake-2-side-effects-in-map).
- **Generated Javadoc uses an article only before a type the processor chose** ([#1307](https://github.com/higher-kinded-j/higher-kinded-j/issues/1307)). A record, class, property or operation is named with none, as in `field of {@link Address}` and `Lifts the {@code Charge} operation`. A path or optic type keeps the article its name takes, as in `Creates an {@link AffinePath}`.
- **The `hkj-guide` skill turns guard clauses into `ensure`** ([#1338](https://github.com/higher-kinded-j/higher-kinded-j/issues/1338)). A new migration recipe replaces a run of `if (!ok) return Either.left(...)` checks with one `ensure` step for each, and the skill's Error Handling table lists `ensure`.
- **The `Profunctor` javadoc states its composition law correctly** ([#1362](https://github.com/higher-kinded-j/higher-kinded-j/pull/1362)). Two of its functions were swapped, so the law as written fails. It no longer says profunctors model the optics, which are van Laarhoven optics, and `Bifunctor`'s javadoc names `Function` as the only `Profunctor` instance. See [Profunctor](../functional/profunctor.md).
- **The `VTask` javadoc says which thread runs a task** ([#1396](https://github.com/higher-kinded-j/higher-kinded-j/pull/1396)). `run()` runs it on the calling thread, and `runAsync()`, `Par` and `Scope` run tasks on virtual threads. It said every task ran on a virtual thread, as did the VTaskPath page and two glossary entries.

---

## Upgrading from 0.5.0 {#upgrading}

### Before you upgrade {#before-you-upgrade}

### What a running program can notice {#runtime-changes}

#### Core types {#runtime-core-types}

- **`Validated.map`, and `map2` to `map5` on its monad, throw `NullPointerException` when their function returns null** ([#1318](https://github.com/higher-kinded-j/higher-kinded-j/issues/1318)). They threw `KindUnwrapException`, so catching that, or `IllegalArgumentException`, around the call no longer catches it. See [Values, never null](../hkts/core-concepts.md#values-never-null).
- **`FunctionKindHelper.narrow` throws `KindUnwrapException`, as the other kind helpers do** ([#1318](https://github.com/higher-kinded-j/higher-kinded-j/issues/1318)). A null `Kind` threw `NullPointerException`, so catching that no longer catches it. Another type's `Kind` threw `IllegalArgumentException`, which `KindUnwrapException` extends, so catching that still does. See [When `narrow` throws](../hkts/core-concepts.md#when-narrow-throws).
- **A `Selective` of your own no longer runs the effects its condition skips** ([#1358](https://github.com/higher-kinded-j/higher-kinded-j/pull/1358)), unless its `select` is built from `ap`. The default `whenS` ran its effect on a false condition, and the default `ifS` ran both branches, so a failing effect's error could hide the condition's. Run any effect you relied on yourself. The library's own instances are unchanged.

#### Optics {#runtime-optics}

- **Of two withers for one getter, `@ImportOptics` sets through the capitalised spelling** ([#1148](https://github.com/higher-kinded-j/higher-kinded-j/issues/1148)): the lens for a class with both `withEMail` and `witheMail` beside `eMail()` set through whichever the class declared first. It now sets through `withEMail`, and a note names `witheMail`, which a spec interface's `@Wither` can choose.
- **An optic interpreter that wraps each result in `Free.pure` skips the operation's continuation** ([#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154)): a program from `OpticPrograms` runs as before, but a function mapped onto an `OpticOp`, through `OpticOpFunctor` or `Free.translate`, is not applied. Hand the result to `op.k()` and return what it gives.

#### Effect Paths {#runtime-effect-paths}

- **A `ValidationPath` keeps its own semigroup through a step** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)): the result of `via`, `then`, `recoverWith` and `orElse` accumulated with the step's semigroup, and now accumulates with the receiver's. After `mapError`, whose placeholder semigroup throws, a later accumulation now throws even past a step that had a semigroup; use `mapErrorWith` instead.
- **An `EitherOrBothPath` keeps its own semigroup through a recovery** ([#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314)): the result of `recoverWith` and `orElse` accumulated with the recovery's semigroup, and now accumulates with the receiver's, as `via` and `then` already did.
- **`mapError` on `MaybePath`, `TryPath` and `CompletableFuturePath` applies the mapper** ([#1332](https://github.com/higher-kinded-j/higher-kinded-j/issues/1332)): it used to hand back the same path, and it never called the mapper. A failure now runs the mapper, and `recover` sees that error. `MaybePath` and `TryPath` return an `EitherPath`. `CompletableFuturePath` returns a `VResultPath`. `mapException` still stays a `TryPath`.
- **`toEitherPath` and `toMaybePath` on a cancelled `CompletableFuturePath` no longer throw** ([#1354](https://github.com/higher-kinded-j/higher-kinded-j/issues/1354)). They give `Left` of the `CancellationException`, and `Nothing`. `toTryPath` on a failed future holds that future's exception. Code which caught a `CancellationException` around `toEitherPath` or `toMaybePath`, or matched a `CompletionException` in a `toTryPath` failure, no longer sees one.
- **A `VTaskPath` handler sees a checked exception as its task threw it** ([#1396](https://github.com/higher-kinded-j/higher-kinded-j/pull/1396)). Code that called `getCause()` on a `VTaskExecutionException` now receives the exception itself. That holds in `runSafe` and `catching`, in a handler or retry after `via`, in the future from `runAsync()`, and in `VTaskContext.runOrElseGet` and `StreamTraversal.modifyVTask`. `unsafeRun()` still wraps one.
- **`recover`, `recoverWith`, `mapError` and `toEitherPath` on a `CompletableFuturePath` keep a `CompletionException` that has no cause** ([#1398](https://github.com/higher-kinded-j/higher-kinded-j/issues/1398)). A handler that was given an empty `RuntimeException` now gets the `CompletionException`.

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|
| Effect Paths | A chain step of another kind of Path, or with an error, environment, state, log or witness type the chain does not carry | Convert the step, map its error, or type the chain on the wider error where it starts, as [Common Compiler Errors](../effect/compiler_errors.md) shows | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | Code that names a capability type with its old arguments, such as `Effectful<A>`, or a `PathProvider` returning `Chainable<A>` | Write `Effectful<?, A>`, or `<P, A> Effectful<P, A>` in a helper that chains. The `AddPathFamily` recipe writes the first | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | A chain whose receiver is typed with a wildcard error, such as `EitherPath<? extends AppError, String>` | Widen it first, as in `p.<AppError>mapError(e -> e).via(...)`, or declare it as `EitherPath<AppError, String>` | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | A pattern or cast from a capability type to a concrete Path, such as `case MaybePath<String> m` on a `Chainable<?, String>`; a cast is unchecked, so it fails under `-Werror` | Match from `Composable<String>`, or build the concrete Path directly | [#1314](https://github.com/higher-kinded-j/higher-kinded-j/issues/1314) |
| Effect Paths | A step after `mapError` on a `MaybePath`, `TryPath` or `CompletableFuturePath` that returns the Path it started from | Return an `EitherPath` from the step (a `VResultPath` after a `CompletableFuturePath`), or use `recover` or `mapException` before changing the error type | [#1332](https://github.com/higher-kinded-j/higher-kinded-j/issues/1332) |
| Optics | An optic interpreter or other code of your own that names an `OpticOp` record: a pattern or type such as `OpticOp.Get<?, ?>` or `OpticOp.Set<S, A>`, or a constructor call such as `new OpticOp.Get<>(source, getter)` | Add the third type argument, as in `OpticOp.Get<?, ?, ?>`, and pass a continuation, `Function.identity()`, to a constructor. Hand each result to `op.k()` and return what it gives, in place of `Free.pure(result)` | [#1154](https://github.com/higher-kinded-j/higher-kinded-j/issues/1154) |
| Build and tooling | Build logic that reads the `hkjInstallSkills` task's `outputDir` as a `Path` | Read `outputDir.get().asFile.toPath()`, which names the same directory | [#1374](https://github.com/higher-kinded-j/higher-kinded-j/issues/1374) |

### Deprecated for removal in 0.7.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.5.0](v0_5_0.md)
