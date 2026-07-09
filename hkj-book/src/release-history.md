# Release History

This page documents the evolution of Higher-Kinded-J from its initial release through to the current version. Each release builds on the foundations established by earlier versions, progressively adding type classes, monads, optics, and the Effect Path API.

~~~admonish info title="What You'll Find"
- Detailed release notes for recent versions (0.3.0–0.4.8) with links to documentation
- Summary release notes for earlier versions (pre-0.3.0)
- Links to GitHub release pages for full changelogs
~~~

---

## Recent Releases

### 0.4.8-SNAPSHOT (Latest)

**`@GenerateErrorEnvelope`: typed error envelopes, generated**

A sealed domain-error hierarchy is the `Left` of every `Either` in the library, but authoring one means re-declaring the same `code` / `message` / `timestamp` / `context` on every variant, with `context` typically an untyped `Map<String, Object>`. `@GenerateErrorEnvelope` removes both: each variant declares only its domain-specific components plus one `ErrorEnvelope<C>` component (a library record carrying the shared fields and a typed context), and the processor generates the `<Name>s` companion: per-variant factories (the `code` is the UPPER_SNAKE variant name, the `message` its humanised form, the timestamp read from a `TimeSource` so it is deterministic in tests), a fluent typed builder over the context record's components, and a `editContext` wither that rebuilds the concrete variant through an exhaustive switch. The context is records-as-schema (consistent with `@GenerateMapping`): consumers read `context.orderId()`, not `map.get("orderId")`. The context type is discovered structurally from the `ErrorEnvelope` component's type argument, never a class literal, and every diagnostic follows the what/why/fix standard. The order example's `OrderError` and `MarketError` are the worked migration ([#610](https://github.com/higher-kinded-j/higher-kinded-j/issues/610)). See [Record Mapping](optics/record_mapping.md#generating-error-envelopes-generateerrorenvelope).

**`@GenerateMerge`: one target from N sources, declared by a typed method**

The forward-only companion to the mapper: annotate an interface whose single abstract method carries the whole declaration — `DashboardDto assemble(User user, Account account, Settings settings)` — and the processor generates the assembly. Each target component fills from the one source with a same-named component (identity, through a `ValidatedPrism` leaf, or through a sibling `@GenerateMapping` spec's `asValidatedPrism()` — failures locate as dotted paths). Ambiguous or unfilled components are what/why/fix compile errors, and the types are truthful in both directions: fallible fills demand the `Validated<NonEmptyList<FieldError>, Target>` return, identity-only merges must declare the plain target, and no inverse is ever generated ([#613](https://github.com/higher-kinded-j/higher-kinded-j/issues/613)).

**`TimeSource`: effectful, testable time**

`java.time.Clock` lifted into the effect world: `TimeSource.system()` / `of(clock)` / `fixed(instant)` with lazy `now() : IO<Instant>` and `nowAsync() : VTask<Instant>` reads — time as a composable effect, deterministic in tests. Deliberately *not* named `Clock`, so it never clashes with `java.time.Clock` in the files that use both; any JDK clock (`fixed`/`offset`/`tick`) lifts through `of()`. `hkj-test` gains `SteppableClock` (`startingAt`/`advance`/`set`, atomic stepping, contract-honouring `withZone`) so time-dependent code is exercised by moving the clock, not sleeping — the order example's reservation-expiry test now works exactly that way ([#609](https://github.com/higher-kinded-j/higher-kinded-j/issues/609)).

**`@GenerateMapping` Step-0 slice: the bidirectional mapper, de-risked**

The first cut of the record↔DTO mapper: annotate `interface UserMapping extends MappingSpec<User, UserDto>` with `@GenerateMapping` and the processor generates `UserMappingImpl` — a total `build(User) : UserDto` and an accumulating `parse(UserDto) : Validated<NonEmptyList<FieldError>, User>` assembled with `Validated.fields()`, every failure located by component name. Components match by name; a validated leaf is a typed `default` method returning a `ValidatedPrism`. `@MapField` renames, nesting (spec delegating to spec, failures located by dotted path), `List`/`Optional` container lifting, sealed-interface dispatch and the truthful emission tiers (lossless → `asIso()`, lossy projection → `asLens()`, fallible → accumulating `parse`) all ship in this slice; every diagnostic follows the what/why/fix standard from day one. Map value lifting and generated law tests arrive with the full mapper ([#600](https://github.com/higher-kinded-j/higher-kinded-j/issues/600)).

**Processor diagnostics: what / why / fix**

Annotation-processor errors now follow a shared three-part format (`Diagnostics` in `hkj-processor`): **what** is wrong (naming the offending element and its kind), **why** (what the processor found or needs), and the exact **fix** — e.g. `@GenerateFocus: can only be applied to records, but 'Config' is a class. The processor derives FocusPath methods from record components. Move the annotation to a record, or use @ImportOptics with an OpticsSpec interface for types you cannot change.` Adopted across the `@GenerateFocus`, `@GenerateLenses`, and `@ImportOptics` error paths; the standard is in place for every future processor ([#601](https://github.com/higher-kinded-j/higher-kinded-j/issues/601)).

**Codegen on-ramp: `-parameters` wired by both build plugins**

The Gradle and Maven plugins now add `-parameters` to compilation automatically (parameter names in class files — used by copy strategies and the upcoming mapper), completing the one-line setup story: apply the plugin and dependencies, annotation processors, preview flags, `-parameters`, and compile-time checks are all configured. The quickstart and plugin pages state the full list ([#602](https://github.com/higher-kinded-j/higher-kinded-j/issues/602)).

**`ValidatedPrism`: the smart-constructor optic for parse-don't-validate boundaries**

A `Prism` whose match accumulates reasons: `parse` returns `Validated<NonEmptyList<FieldError>, A>` (every failure, located), `build` is total. Nested composition short-circuits while sibling fields accumulate through the assembly builders or `Edits`; only build-preserving compositions exist (`ValidatedPrism`, `Iso`, `Prism`-with-a-reason — a `Lens` cell is deliberately absent because no total build survives it). Bridges: `fromIso`/`fromPrism(reason)`, reason-forgetting `toPrism()`/`toAffine()`, and `parsePath` onto the railway. Both round-trip laws ship as `ValidatedPrismLaws` in `hkj-test` — the law harness's first extension. Purely additive ([#597](https://github.com/higher-kinded-j/higher-kinded-j/issues/597)). See [Validated Prisms](optics/validated_prism.md).

**Published optic-law harness: law-test your own optics**

`hkj-test` gains `org.higherkindedj.optics.laws` — `IsoLaws`, `LensLaws`, `PrismLaws`, `AffineLaws`, `TraversalLaws` — flat `assert…` helpers in the established `hkt.laws` style, so every user can verify the defining properties of hand-written optics (`Lens.of(...)`, spec interfaces, codecs). Failures name the violated law with the offending values; guard rails reject vacuous fixtures. Also the published target for the upcoming `ValidatedPrism` laws and `@GenerateMapping`'s law-checked guarantee. Purely additive ([#596](https://github.com/higher-kinded-j/higher-kinded-j/issues/596)). See [Testing With hkj-test](tooling/test_assertions.md).

**Optic-path labelling: generated paths know their own names**

`@GenerateFocus` companions now emit the record-component name as a path segment (`FocusPath.of(lens, "email")`), and every path type surfaces `segments()` and `pathString()` (`"customer.address.zip"`). Composing paths concatenates segments; raw-optic and widening links (`each()`/`some()`/`nullable()`) contribute nothing. `Edit.parseIfPresent` locates parse failures with the path's own segments automatically, so sparse-PATCH errors arrive located with no `.at(...)` ceremony — an explicit `.at(label)` still prepends outward. Purely additive ([#592](https://github.com/higher-kinded-j/higher-kinded-j/issues/592)). See [Multi-Edit and Sparse Updates](optics/multi_edit.md).

**`Edits`: sparse, accumulating multi-edit over optics**

Apply N independent edits at different paths in one reusable operation. `Edits.combine` folds pure edits (`Edit.set`/`modify`/`setIfPresent`/`modifyIfPresent`, over a `FocusPath` or `Setter`) into a single `Update<S>` via `Monoids.update()`; `Edits.accumulate` adds the validated REST-`PATCH` shape — `parseIfPresent(path, raw, parser).at("email")` parses each incoming value independently, reports **every** bad field at once as located `FieldError`s in edit order (the `NonEmptyList` channel, with no arity ceiling), and applies the writes in one left-to-right pass only if everything validated. `…IfPresent` treats `null` as absent (the monoid identity), so sparse DTO fields land one-to-one with no `if` ceremony; a `ValidationPath` twin (`applyPath`) rides the railway. Purely additive ([#582](https://github.com/higher-kinded-j/higher-kinded-j/issues/582)). See [Multi-Edit and Sparse Updates](optics/multi_edit.md).

**`Update<S>` and `Monoids.update()`: the function-composition monoid**

A named, composable update: `Update<S>` extends `UnaryOperator<S>` (so it drops into any `Function`-shaped API for free) with `Update.identity()` and an `andThen` that stays in the type. `Monoids.update()` joins the existing noun factories — identity is the do-nothing update, `combine(f, g)` applies `f` first, then `g` — so any number of updates fold into one, applied left to right. Known in FP literature as the `Endo` monoid; the keystone for the upcoming `Edits` multi-edit builder ([#582](https://github.com/higher-kinded-j/higher-kinded-j/issues/582)). Purely additive ([#591](https://github.com/higher-kinded-j/higher-kinded-j/issues/591)). See [Semigroup and Monoid](functional/semigroup_and_monoid.md).

**`@GenerateAssembly`: per-record validated-assembly companions**

The accumulating assembly builder gains its codegen layer: annotate a record and the processor generates a same-package companion (`UserAssembly.fields().name(v).email(v).age(v).assemble()`) with one named, order-enforcing method per component. Labels come from the component names, the terminal `assemble()` invokes the canonical constructor, and the merge is a curried `Validated.ap` chain with `NonEmptyList.semigroup()`, so arity is exact with **no ceiling** and errors emerge in component-declaration order. A component typed as another annotated record accepts its sub-companion's result directly (`address.zip`). First slice of the record mapper feature ([#586](https://github.com/higher-kinded-j/higher-kinded-j/issues/586)). See [Accumulating Assembly](monads/validated_assembly.md).

**Open-arity accumulating assembly: build a record from N validated fields with `fields()` / `accumulate()`**

Assembling a record from N validated fields (a request DTO becomes a domain aggregate; raw config becomes a settings object) previously hit an arity wall (`map2..map5`), full `Kind` ceremony, and flat, unlocated error lists. The new staged builder assembles any arity up to 12 with every error collected in field-declaration order, no `Semigroup` argument, and no `Kind` in sight. Purely additive; `ap`, `zipWithAccum`, and the `mapN` family are unchanged ([#581](https://github.com/higher-kinded-j/higher-kinded-j/issues/581)).

- **Located errors:** `Validated.fields()` fixes the error channel to `NonEmptyList<FieldError>`; `field(label, value)` tags each slot, and nesting a sub-assembly prepends the outer segment (`address.zip`). The new `FieldError` record carries a composable path, and `assertThatFieldError` ships in `hkj-test`.
- **Generic flavour:** `accumulate()` takes any error payload `X`, carried as `NonEmptyList<X>`, with fields joining via `and(value)`.
- **One shape, three carriers:** the same entry points on `Validated` (strict), `Path`/`ValidationPath` (railway), and `EitherOrBoth` (tolerant: warnings accumulate while the value keeps flowing). The combination primitive behind the tolerant flavour is the new public `EitherOrBoth.zipWithAccum(other, semigroup, combiner)`, to which `EitherOrBothPath` now delegates.
- **Generated, not hand-written:** the stage families (`ValidatedFields1..12` and friends) are produced by the annotation processor via the new package-level `@GenerateAccumulators`, following the `@GenerateForComprehensions` machinery; only the entry stages and `FieldError` are hand-written. See [Accumulating Assembly](monads/validated_assembly.md).

**Internal: annotation-processor hygiene, repo-wide**

Every processor now reports `SourceVersion.latestSupported()` instead of a hardcoded release (no compiler warnings on newer JDKs), and every generated file carries its originating element through the `Filer`, enabling correct incremental annotation processing in Gradle: builds recompile only what the changed inputs actually produced. Generated output is byte-identical ([#588](https://github.com/higher-kinded-j/higher-kinded-j/issues/588)).

**`NonEmptyList`: a list that is never empty, and the streamlined validation error channel**

`Validated`/`ValidationPath` accumulate errors, but the channel users were steered toward was a plain `List<Error>`: a type that permits the impossible empty case (an *invalid* result always has at least one error), forces a `Semigroups.list()` argument at every call, and leaves `get(0)` partial. New `NonEmptyList<A>` encodes "at least one element" in the type, so `head`/`last`/`reduce`/`min`/`max` are **total** (no `Optional`, never throw). It is the canonical companion to `Validated`, mirroring Cats' `NonEmptyList`/`ValidatedNel`. Purely additive; the existing `Semigroups.list()` channel is unchanged ([#549](https://github.com/higher-kinded-j/higher-kinded-j/issues/549)).

- **Plain fluent type, no HKT tax:** `NonEmptyList.of(a, b, c).map(f).flatMap(g)`, plus `reduce(semigroup)`, `foldLeft`, `reverse`, `concat`/`append`/`prepend`, `toJavaList()`, and `Iterable`. Safe construction from possibly-empty data via `fromList`/`fromIterable`, which return `Maybe` and never throw. Deeply immutable (defensive-copied `tail`, unmodifiable `tail()`); never holds `null`.
- **Higher-kinded:** `NonEmptyList` implements its `Kind` directly (cast-free widen/narrow), with `Functor`/`Monad`/`Traverse`/`Foldable`/`Semigroup` instances reachable via `Witnesses.nonEmptyList()`. Deliberately **no `Monoid`/`MonadZero`**: there is no empty `NonEmptyList`, and the absence is the point.
- **Validation integration:** `Path.validNel` / `Path.invalidNel` / `Path.validatedNel` and `Validated.validNel` / `Validated.invalidNel` bake in `NonEmptyList.semigroup()`, so the common case drops the `Semigroup` argument and the manual `List.of(error)` wrapping, and `getError().head()` is total. Accumulation is left-to-right concatenation.
- **Tooling:** an `assertThatNonEmptyList` assertion in `hkj-test`, and Jackson support in `hkj-spring` (serialised as a JSON array; an empty `[]` is rejected on read so the non-empty guarantee survives the wire). See [NonEmptyList](monads/nonemptylist_monad.md).

**`PathOps` race / first-success combinators gain `NonEmptyList` overloads**

The five `PathOps` "first to succeed" combinators (`firstSuccess`, `raceVTask`, `firstVTaskSuccess`, `firstCompletedSuccess`, `raceIO`) took a `List` that must be non-empty and paid for the missing guarantee with a runtime `IllegalArgumentException` on an empty input. Each now has a `NonEmptyList`-accepting overload beside the `List` one: the typed form is total, so a statically-known set of competitors (a fixed set of fallbacks, a literal list) needs no empty guard and cannot throw. The `List` overloads are retained, undeprecated, for runtime-sized inputs; bridge the empty case to a value with `NonEmptyList.fromList(list).map(PathOps::firstSuccess)`. Purely additive at the API surface, building on `NonEmptyList`; the one source-compat note is that a bare `null`-literal argument to these methods now needs a cast to pick an overload ([#579](https://github.com/higher-kinded-j/higher-kinded-j/issues/579), [#585](https://github.com/higher-kinded-j/higher-kinded-j/pull/585)). See [Advanced Paths](effect/advanced_topics.md).

**`EitherOrBoth`: an inclusive-or for "success that also carries warnings"**

`Either` and `Validated` are both *exclusive*: a result is a failure or a success, never both. Neither models a successful value that also carries accumulated, non-fatal problems: config that parses but reports deprecations, an import that yields records *and* a skipped-rows list. New `EitherOrBoth<L, R>` (the inclusive-or known elsewhere as `Ior`/`These`) is a sealed type over `Left` | `Right` | `Both`, right-biased, with total `getLeft`/`getRight` accessors that return `Maybe` and never throw. Purely additive ([#551](https://github.com/higher-kinded-j/higher-kinded-j/issues/551); landed in [#583](https://github.com/higher-kinded-j/higher-kinded-j/pull/583)).

- **Accumulating `flatMap`:** `Left` short-circuits; a `Both` carries its warnings forward, combining them with a `Semigroup<L>` (default `NonEmptyList.semigroup()`). The companion `EitherOrBothMonad` is lawful for any associative semigroup. Note the monadic `ap` short-circuits: it is deliberately *not* `Validated`-style accumulation; that lives on the Path.
- **Higher-kinded:** implements its `Kind`/`Kind2` directly (cast-free), with a right-biased `Functor`/`Monad`/`Traverse`/`Foldable` and a `Bifunctor`; the monad is reached via `Instances.eitherOrBoth(semigroup)`.
- **`EitherOrBothPath`:** the railway wrapper, implementing both `Chainable` (short-circuit `via`/`zipWith`) and `Accumulating` (collect-everything `zipWithAccum`/`andAlso`), with `Path.right`/`left`/`both` and the `rightNel`/`leftNel`/`bothNel` shortcuts that bake in `NonEmptyList.semigroup()`.
- **Tooling:** an `assertThatEitherOrBoth` assertion in `hkj-test`; Jackson support in `hkj-spring` (a tag-based `{"kind": …}` representation) and an `EitherOrBothPathReturnValueHandler` (a `Both` returns 200 with the value and surfaces warnings in an `X-Hkj-Warnings` header, never silently dropped). See [EitherOrBoth](monads/either_or_both_monad.md) and [EitherOrBothPath](effect/path_either_or_both.md).

**`hkj-spring` Jackson deserializers now resolve their generic element types**

The `Either`, `Validated`, and `NonEmptyList` deserializers read inner values as `Object`, so a `TypeReference<…<CustomType>>` (or a typed field) produced `LinkedHashMap` elements and a `ClassCastException` on access. All three now implement Jackson 3.x contextual resolution (`ValueDeserializer.createContextual`), binding to the contained generic types taken from the property or `TypeReference`, so nested custom types round-trip to the real type, including end-to-end through a `Validated<NonEmptyList<Foo>, …>`. Raw `.class` reads keep the `Object` fallback ([#578](https://github.com/higher-kinded-j/higher-kinded-j/pull/578)).

**Worked-example hardening: correct, idiomatic `order` & `market` showcases, with `hkj-test` throughout**

The `example.order` and `example.market` showcases were reviewed and hardened so they demonstrate current HKJ functionality rather than the hand-written workarounds that sat beside it. Example- and documentation-only; no library API change.

---

### [v0.4.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.7) (26 June 2026)

**`@HkjHttpClient` — generated Effect-Path HTTP clients**

`hkj-spring` was server-side only: the `*PathReturnValueHandler`s map an `EitherPath`/`VTaskPath`/… returned from a controller into an HTTP response, but when service A called service B, B's typed error collapsed into a raw status code at A's boundary — losing the typed error channel the library is built around. The new `@HkjHttpClient` closes that gap with the client-side inverse, preserving the typed error end-to-end across services. Two additive modules (`hkj-spring/client` runtime + `hkj-spring/client-processor`), no breaking changes.

- **Runtime (`hkj-spring/client`):** `HkjClientExchange` folds an HTTP exchange into an Effect Path — `either` (2xx→`Right`, 4xx/5xx→`Left`), `eitherVTask` (deferred on a virtual thread, so callers get `withRetry`/`withCircuitBreaker`/`timeout`), and `maybe` (404/empty→`Nothing`). A pluggable `ResponseErrorDecoder` decodes the server's `{"success":false,"error":…}` envelope into the declared error type via the shared Jackson mapper; auto-configuration contributes the default factory.
- **Codegen (`hkj-spring/client-processor`):** annotating a Path-typed `@HttpExchange` interface generates a native `@HttpExchange` interface (return types unwrapped to `ResponseEntity<T>`, all mapping/parameter annotations copied through), a `…Client` implementation that dispatches by return type, and a `…ClientConfiguration` that wires the client via Spring 7 `@ImportHttpServices` — base URL/timeouts/versioning come from `spring.http.serviceclient.<group>.*`.
- A concrete error type decodes with no extra annotations; a sealed `DomainError` hierarchy needs `@JsonTypeInfo`/`@JsonSubTypes`. The processor is wired into `hkj-spring-boot-starter`; the `hkj-spring/client-example` module is a standalone client application that calls the server example over HTTP (with an end-to-end `MockRestServiceServer` test), and the [Declarative HTTP Clients](spring/declarative_http_clients.md) guide walks through it.
- **Additional capabilities:** `@OnStatus(value, error)` maps individual statuses to distinct error subtypes (404 → `UserNotFoundError`, …); generic `@HkjHttpClient` interfaces are supported codegen-only; `ClientErrorResponse.retryAfter()` exposes the server's `Retry-After` hint for back-off; and `HkjClientExchange.vstream(...)` consumes the server's SSE stream into a `VStreamPath<T>` (deferred, resource-safe). The runtime itself is written in the library's own idioms (`Try`/`Either`), and the client is documented across the `hkj-spring` module docs plus a dedicated `HTTP_CLIENT.md`.

**Spring Boot 4.1.0 / Framework 7.0.8 upgrade**

The `hkj-spring` modules move from Spring Boot 4.0.6 to 4.1.0 (Spring Framework 7.0.8), with the managed Jackson 3.x line advancing from 3.1.2 to 3.1.4 to match the `jackson-bom` shipped by Boot 4.1.0. Dependency-only, centralised in the version catalog; the modules compile and pass against 4.1.0 with no source changes and no new deprecations. No public API change ([#575](https://github.com/higher-kinded-j/higher-kinded-j/pull/575)).

**`Writer.of(log, value)` factory**

New `Writer.of(W log, @Nullable A value)` static factory for the common custom-log-plus-value case, sitting between `Writer.value(Monoid<W>, A)` (empty log) and `Writer.tell(W)` (`Unit` value). The `(log, value)` order mirrors the record components and accessors, and returning the plain `Writer<W, A>` launders the `@Nullable A` nullness contract that the raw constructor's diamond leaks at the call site. Purely additive ([#554](https://github.com/higher-kinded-j/higher-kinded-j/issues/554)).

**Consistent `recoverWith` / `recover` null-handling across `MonadError`**

`recoverWith(ma, fallback)` now rejects a null `ma`/`fallback` eagerly and identically on every `MonadError` instance (`TryMonad`, `OptionalMonad`, `VTask`, `CompletableFuture`, `EitherT`/`MaybeT`/`OptionalT`), replacing the previous state-dependent, mislabelled `NullPointerException` (`EitherMonad`/`ValidatedMonad` already guarded it). `recover(ma, value)` keeps its `@Nullable value`, so `recover(failure, null)` stays a valid `Success(null)`/`Nothing`/empty; `ValidatedMonad` keeps only `recoverWith` because its `of` rejects null. Behaviour-preserving except on null input ([#553](https://github.com/higher-kinded-j/higher-kinded-j/issues/553)).

**Internal: type-safety and soundness cleanups**

A sweep across hkj-core removing avoidable unchecked casts and holder indirection; behaviour-preserving with no public API change unless noted:

- Turned on `-Xlint:unchecked,rawtypes -Werror` across all modules, so any new unchecked or raw-type use must carry an explicit suppression; generated `@ComposeEffects` Support classes carry one so downstream lint-enabled builds stay clean ([#560](https://github.com/higher-kinded-j/higher-kinded-j/issues/560)).
- `Maybe`/`Either`/`Validated` roots now extend their `Kind` interfaces, making the five `widen`/`widen2` methods cast-free upcasts ([#561](https://github.com/higher-kinded-j/higher-kinded-j/issues/561)).
- Every remaining HKJ-owned type direct-implements its `Kind`: `widen` is an allocation-free upcast, seventeen `*Holder` records are deleted, and `narrow(null)` now uniformly raises `KindUnwrapException` (`Lazy` and the JDK-wrapped types keep their holders) ([#568](https://github.com/higher-kinded-j/higher-kinded-j/issues/568)).
- Funnelled the covariant `flatMap`/`recoverWith` reinterpretations through a private `covary` helper, and replaced the public API's last raw-`Kind` wrapper (`IndexedTraversal.asIndexedFold()`) with a typed `IdBox` ([#562](https://github.com/higher-kinded-j/higher-kinded-j/issues/562)).
- Consolidated `Free.foldMap`'s two stack-safe interpreters behind the single `Natural` path ([#563](https://github.com/higher-kinded-j/higher-kinded-j/issues/563)).

**`EachIndexed` — type-safe replacement for `Each.eachWithIndex()`**

`Each.eachWithIndex()` returned `Optional<IndexedTraversal<I, S, A>>` with a caller-chosen index type, so requesting the wrong index compiled and then failed at runtime with a `ClassCastException`. New `EachIndexed<I, S, A> extends Each<S, A>` carries the real index type at the type level and exposes `indexedTraversal()` directly (no `Optional`, no cast); the `EachInstances` factories now return it. `Each.eachWithIndex()` is **deprecated for removal in 0.5.0** and still works as a bridge, so existing code compiles. Additive plus one deprecation, no behaviour change for existing callers ([#564](https://github.com/higher-kinded-j/higher-kinded-j/issues/564)). See [Each type class](optics/each_typeclass.md) / [Indexed Optics](optics/indexed_optics.md).

**`raw-kind` checker rule**

The HKJ compiler plugin gains a `raw-kind` rule: a raw `Kind`/`Kind2` drops its witness type argument (the one route that lets a value tagged with one witness be narrowed through another, compiling silently and throwing `KindUnwrapException` at runtime), and javac accepts it, so the checker is the sole compile-time signal. Flags variable/parameter/field declarations and casts at **warn** by default (`disable=raw-kind`, `severity:raw-kind=error`); a properly parameterised `Kind<W, A>` is never flagged ([#565](https://github.com/higher-kinded-j/higher-kinded-j/issues/565)). Documented in [Compile-Time Checks](tooling/compile_checks.md).

---

### [v0.4.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.6) (7 June 2026)

**Optic-Driven Request Batching**

This release adds the user-facing surface around the `org.higherkindedj.optics.fetch` substrate so an optic traversal of N foci collapses to one batched backend call by swapping the strategy passed to `Optic.modifyF`. The optic core is untouched; the package is now exported and documented, the heterogeneous `Id -> Entity` case has a dedicated helper, and run-time failures land on the value channel as `Either` rather than as exceptions.

- [Optic-Driven Batching](optics/optic_batching.md): New chapter under Optics / Integration and Recipes, with three diagrams (N+1 vs batched, the optic + applicative + runner pipeline, the applicative-versus-`flatMap` round cost) and a language-agnostic primer link for the DataLoader/Haxl idea. `org.higherkindedj.optics.fetch` is now exported from the core module; `Done`/`Blocked`/`PendingKeys` remain package-private so consumers interact with `Fetch` only through its static factories and runners
- `SafeFetch`: Total runner that captures resolver exceptions, missing-key reports, loader failures, and deadlines as `Either.left` values on the value channel instead of thrown exceptions; `SafeFetch.runCached`, `runAsync`, and `runAsyncWithTimeout` never throw and the safe-async future never completes exceptionally. `SafeFetch.partition` splits a per-key `Either<E, V>` result list into aligned successes and failures so a partial-success batch is preserved end-to-end
- `SourceRouter.routed`: Composes per-source `BatchLoader`s with a classifier into one loader the substrate can call; one round fans out to one concurrent dispatch per source, so a list mixing user ids and product skus produces exactly one call per backend, not one call per key
- `BatchLoaders.chunked(loader, maxSize)`: Caps a single dispatch's size for backends that enforce a per-request limit (`$in` clause cap, HTTP query-string ceiling, GraphQL batch limit); the substrate still sees one round and the loader splits the keyset behind the curtain
- `FetchOptics.fetchEach(source, rebuild)`: The type-changing list-traversal the codegen does not produce (codegen optics are type-preserving, so a `Traversal<Team, UserId>` cannot directly describe loading each `UserId` into a `User`); builds an `Optic<S, T, A, B>` from a list-reader and a rebuild function so heterogeneous fetch composes with the rest of the optic graph
- Tutorial 21: New tutorial journey (exercise + teaching-solution) covering the four pieces (same-type batching, heterogeneous fetch, multi-source routing, railway errors) with five exercises and tiered hints; the solution carries the *Why this is idiomatic / Alternative / Common wrong attempt* commentary per exercise
- Architecture-rule update: The `optics.fetch` package is exempted from the "no specific HKT type dependencies" rule alongside the existing `optics.util`, `optics.extensions`, and `optics.fluent` exemptions, on the same basis: `SafeFetch`'s railway runner is built around `Either` by design

**Plan Introspection and Guardrails for Optic Batching**

The audit and safety-rail layer on top of [Optic-Driven Batching](optics/optic_batching.md): a way to fold a `Fetch` program into a structural plan without I/O, and a per-round guard that interposes between the program and its resolver to refuse runaway batches before they leave the JVM. Both compose with `SafeFetch` so refusal is a value, not a thrown exception.

- [Plan Introspection and Guardrails](optics/optic_batching_guardrails.md): New chapter under Optics / Integration and Recipes covering the offline `Plans.preflight` walk, the per-round `Guard` family, and the railway-safe refusal pattern.
- `Plans.preflight`: Folds a `Fetch` program into a `Plan<K>` with zero I/O. Each round's keyset is recorded in dispatch order; round 1 is universally observable, and later rounds are walked on stub values when the program's combine logic tolerates `null` (a `Plan.truncated()` flag is the honest signal otherwise).
- `Guards.maxKeysPerRound` / `maxRounds` / `maxBackendCalls` / `audit` / `none`: Standard guards that pass or refuse a round at the runner boundary; compose with `Guard::and`. A refusal aborts the run with `GuardViolationException` carrying the offending `roundIndex` and `pendingKeys`.
- `Guards.runCached` / `runAsync`: Drop-in replacements for the substrate runners with the guard interposed; the resolver is never called for a refused round.
- `SafeFetch.runCachedWithGuard` / `runAsyncWithGuard`: Railway variants that capture a refusal as `Either.left(GuardViolationException)`; the run never throws and the safe-async future never completes exceptionally.
- Tutorial 22 (exercise + teaching solution) covering the five pieces (preflight, truncation, refusal, audit, railway capture).

**Test-suite consolidation (internal)**

Mostly an internal refactor of the hkj-core test suite The one user-visible piece is in `hkj-test`: new reusable law helpers (`FunctorLaws`, `ApplicativeLaws`, `MonadLaws`, `SelectiveLaws`) and a `KindEquivalence.byEqualsAfter` helper that downstream users can call to verify their own type-class instances. The Kind-accepting overloads on `EitherAssert` / `MaybeAssert` / `TryAssert` / `IOAssert` / `LazyAssert` / `ReaderAssert` / `ValidatedAssert` / `WriterAssert` / `VStreamAssert` / `VTaskAssert` now match the auto-narrowing pattern of `ListAssert` / `OptionalKindAssert` / `StreamAssert` / `IdAssert`.

**N-ary Coupled Lenses**

The arity ladder above `Lens.paired`: a record with three or more cross-field invariants can now be updated atomically through a single `coupled3`..`coupled9` call instead of nested `paired` workarounds.

- [Coupled Fields chapter](optics/coupled_fields.md): "Three or More Coupled Fields" section rewritten to show the new ladder; the old "nest pairs / feature request" guidance is replaced.
- `CoupledLenses.coupled3` ... `coupled9` (in `org.higherkindedj.optics.util`): seven static factories, each with two overloads mirroring `Lens.paired` exactly (preserving form taking `(S, A, B, ...) -> S`; simple form taking the constructor reference `(A, B, ...) -> S`). Returns `Lens<S, TupleN<...>>` reconstructed atomically.
- `hkj-processor` adds `CoupledLensGenerator`, wired into the existing `@GenerateForComprehensions` trigger alongside the Tuple/For-step generators. Generation caps at arity 9 (cross-field invariants past that point are vanishing in practice); raising the cap is a one-line change to the generator.
- Tutorial 23 (exercise + teaching solution) demonstrating `coupled3` on a 3-field monotonic invariant and `coupled5` on the same shape at higher arity, including the canonical "chained set throws" failure mode that coupled lenses sidestep.

**API Deprecations Ahead of 0.5.0**

This release prepares users for a record-shape change to `StateT` that lands in 0.5.0. The single-argument runner methods and the explicit `monadF()` accessor are deprecated now so call sites can migrate ahead of time; the record component itself stays in place until 0.5.0.

- `StateT` runner methods accept an explicit `Monad<F>`: new overloads `StateT.evalStateT(state, monad)` and `StateT.execStateT(state, monad)` use the supplied monad rather than the one stored on the record. The matching helpers `StateTKindHelper.evalStateT(kind, state, monad)` and `execStateT(kind, state, monad)` are added on the same shape. New code should prefer these overloads; the single-argument forms are deprecated for removal in 0.5.0 ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445))
- `StateT.monadF()` deprecation: the explicit accessor is now `@Deprecated(forRemoval = true)`. In 0.5.0 the `monadF` record component itself is removed so that two `StateT` values with the same state function are considered equal regardless of which `Monad` instance they were constructed with. `equals`, `hashCode`, and `toString` therefore change in 0.5.0; until then the record-generated implementations still discriminate on the stored monad, which is the underlying defect the deprecation is staging
- Internal call sites in `MutableContext.evalWith` and `execWith` are migrated to the new two-argument overloads; library builds emit no deprecation warnings of their own

**`Try.fold` and `TryPath.fold` argument-order rename ahead of 0.5.0**

`Try.fold(successMapper, failureMapper)` and `TryPath.fold(successMapper, failureMapper)` are the two surfaces whose `fold` is *success-first*, against the error-first convention used by `Either.fold`, `Validated.fold`, `EitherF.fold`, `EitherPath.fold`, and `ValidationPath.fold`. A naked argument swap on `fold` would silently invert behaviour for the common case of parameter-ignoring lambdas (`v -> v`, `v -> null`, `Throwable::getMessage`) and for `Object`-typed method references, because both `Function` parameters have different generic bounds but lambdas often resolve to either. The fix is published under a distinct name so the change becomes a compile error rather than a runtime inversion.

- `Try.foldFailureFirst(failureMapper, successMapper)` and `TryPath.foldFailureFirst(failureMapper, successMapper)` are the canonical error-first replacements, added since 0.4.6. Their argument order matches `Either.fold` / `Validated.fold` / `EitherF.fold` / `EitherPath.fold` / `ValidationPath.fold`. The methods are named `foldFailureFirst` rather than overloading `match` (which would create a lambda-inference ambiguity with the existing `Try.match(Consumer, Consumer)`) or `fold` (which would silently invert behaviour for downstream call sites).
- `Try.fold(successMapper, failureMapper)` and `TryPath.fold(successMapper, failureMapper)` are now `@Deprecated(forRemoval = true)` and are removed in 0.5.0. Internal call sites across `hkj-core` (`TryApplicative`, `TryTraverse`, `TryPath`, `PathOps`, `VTaskContext`, `LensExtensions`, `Affines`), `hkj-test` (`VTaskPathAssert`, `VTaskContextAssert`), `hkj-spring` return-value handlers, `hkj-processor-plugins` (the `TryGenerator` annotation-processor template), and every runnable example, tutorial, and solution are migrated to `foldFailureFirst`; library builds emit no deprecation warnings of their own.
- The canonical name `fold` is planned to be reintroduced on both `Try` and `TryPath` with the error-first argument order in 0.6.0, once 0.5.0 has removed the success-first `fold` and every reachable call site has been forced through the renamed method. See [#452](https://github.com/higher-kinded-j/higher-kinded-j/issues/452) for the design rationale and the follow-up issue tracking the 0.6.0 reintroduction.
- OpenRewrite migration: a new `SwapTryFoldToFoldFailureFirstRecipe` is added to the existing `MigrateDeprecationsTo0_5_0` recipe group in `hkj-openrewrite`. The recipe matches `Try.fold(successMapper, failureMapper)` and `TryPath.fold(successMapper, failureMapper)` call sites and rewrites them to `foldFailureFirst(failureMapper, successMapper)`, atomically renaming the method and swapping the two arguments. It cannot be expressed as a stock `ChangeMethodName` invocation because the argument order changes; a naked rename would silently invert behaviour. Downstream consumers run `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` to migrate `Try.fold`, `TryPath.fold`, `StateTKind.narrowK`, and `KindValidator.narrowWithPattern` in one pass.

---

### [v0.4.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.5) (22 May 2026)

**A Uniform `Instances` Facade for Type-Class Lookup**

This release introduces `Instances`, a single static entry point for obtaining any built-in type-class instance, replacing the three inconsistent legacy idioms (a static `INSTANCE` field, a generic `instance()` method, or an argument-taking constructor) with one predictable shape discovered by capability through IDE autocomplete. The whole codebase — tests, runnable examples, and the book — is migrated to the one idiom.

- [Obtaining Instances](functional/instances_facade.md): New `org.higherkindedj.hkt.instances` package with the `Instances` facade and the `Witnesses` typed-token helper. `Instances.monad/applicative/functor(token)` are total (every canonical instance is at least a `Monad`); a single token yields all three by Java subtyping. Phantom-typed witnesses (`Either`, `Reader`, `Context`, `State`) still infer their type parameter from the assignment target, matching `EitherMonad.<L>instance()` behaviour. The facade is a thin static re-export of the existing accessors — not Spring-wired, not `PathRegistry`/`ServiceLoader`-backed — so compile-time safety is preserved and no built-in instance can be missing at runtime ([#522](https://github.com/higher-kinded-j/higher-kinded-j/issues/522))
- [Partial capability lookups](functional/instances_facade.md): `Instances.monadError`, `monadZero` and `alternative` for canonical instances that implement the richer capability (e.g. `Maybe`, `Optional`, `Try`, `Either`, `List`, `Stream`). The error type `E` of `monadError` is inferred from the assignment target; asking for a capability the instance does not have fails fast with a `ClassCastException`, exactly as calling a non-existent method would
- [Argument-carrying re-exports](functional/instances_facade.md): `Instances.validated(Semigroup)`, `writer(Monoid)`, `eitherT(outer)`, `maybeT(outer)`, `optionalT(outer)`, `readerT(outer)`, `stateT(outer)` and `writerT(outer, Monoid)`. The structurally-required dependency is now a compiler-enforced, self-documenting method parameter instead of something discovered by reading a constructor
- One-idiom migration: The `Instances` facade is adopted across ~196 test files, 66 runnable examples, and 71 book pages so the documentation and examples teach a single way to obtain an instance. The `MonadReader`/`MonadState` MTL capability classes and `Traverse`/`Selective`/`Foldable` remain a separate surface, intentionally out of scope for this facade and tracked separately
- Reference material: New [glossary](glossary.md) entry, a Type-Class Instances section in the [cheat sheet](cheatsheet.md), and a `InstancesFacadeExample` runnable example
- [Bifunctor law verification](functional/bifunctor.md): The reusable Bifunctor law harness (`LawTestPattern`/`TypeClassTestPattern`) now verifies the first-map (`first(f, fab) == bimap(f, id, fab)`) and second-map (`second(g, fab) == bimap(id, g, fab)`) consistency laws alongside the existing identity and composition laws, so every canonical instance (`Either`, `Tuple`, `Const`, `Validated`, `Writer`) is checked against all four laws; explicit named consistency tests added for `Either` (Left/Right) and `Tuple` ([#461](https://github.com/higher-kinded-j/higher-kinded-j/issues/461))
- [Collection-path fold family](functional/foldable_and_traverse.md): `ListPath` and `StreamPath` gain the monoid-style `fold(identity, op)` and the `Foldable`-style `foldMap(Monoid, fn)`, plus `foldRight` on `StreamPath`, matching the existing `VStreamPath`/`VStreamContext` fold surface so the same reduction reads identically across every sequence-like path and stays inside the path chain. Documented in the [cheat sheet](cheatsheet.md) and the Foldable chapter, with `CollectionPathsExample` updated to fold without unwrapping ([#462](https://github.com/higher-kinded-j/higher-kinded-j/issues/462))
- [Effect Path `toString()` standardisation](effect/effect_path_overview.md): A shared `PathToString` helper gives every Effect Path type one debugging-friendly, greppable `toString()` convention: the round-parenthesis wrapper form `TypeName(inner)`, a uniform angle-bracketed sentinel vocabulary (`<deferred>`, `<stream>`, `<empty>`, `<pending>`, `<failed>`), and bounded rendering for collection-backed paths (`ListPath`, `NonDetPath`, `WriterPath` logs) with an explicit `…(+k more)` marker so a large backing collection never produces an unbounded log line. `IdPath` is now null-safe and `LazyPath` never forces its computation when rendered; all changed path classes hold at 100% line/branch coverage ([#530](https://github.com/higher-kinded-j/higher-kinded-j/issues/530))

**Expanded `hkj-checker` Compile-Time Diagnostics**

This release grows the `hkj-checker` javac plugin from a single Path-type-mismatch check into a catalogue of twelve compile-time checks, adds per-check severity configuration, and consolidates the relevant OpenRewrite recipes into compile-time feedback. Several further candidate checks were investigated and deliberately not shipped — kept as passing characterization tests that document why — because the targeted error is unreachable on modern javac, already caught by the compiler, or only detectable via a rot-prone heuristic; the strict no-false-positives policy is preserved throughout.

- [Compile-Time Checks](tooling/compile_checks.md): Eleven new checks join `path-type-mismatch`: `effect-composition`, `transformer-missing-monad`, `free-switch-exhaustive`, `discarded-effect`, `state-t-mapt-arity`, `error-type-mismatch`, `kind-value-narrow`, `witness-arity`, `via-non-path`, `map-nests-effect`, and `migration-nudge`. Each is a companion to a real javac error or the sole signal for an otherwise-silent mistake (a discarded lazy effect, a silently-erased error type, a nested effect); the sole-signal heuristics default to a warning
- [Per-check severity](tooling/compile_checks.md): The plugin-argument grammar adds `severity:<id>=error|warn` alongside the global `severity=` and `disable=<id>`, so the warn-default checks can be promoted per project. Unknown ids and unparseable values are ignored so a typo never breaks the build
- Recipe consolidation: `migration-nudge` folds the `ConvertRawFreeToFreePath` and `DetectInjectBoilerplate` OpenRewrite diagnoses into advisory compile-time nudges; `free-switch-exhaustive` and `witness-arity` do the same for the Free-switch and `WitnessArity` recipes
- Documentation: `tooling/compile_checks.md` is now the authoritative checker catalogue (every check, its default severity, and the configuration grammar); the effect/transformers/optics *Common Compiler Errors* chapters were corrected where they described errors modern javac no longer emits and cross-linked to the catalogue

**Hardened `hkj-openrewrite` Recipes**

This release audits and hardens the `hkj-openrewrite` migration recipes — correctness fixes, broader detection, type-safe matching, new 0.5.0 deprecation recipes, and a near-quadrupled test suite (9 → 34 tests).

- Arity bounds: `AddArityBoundsToTypeParameters` now emits `TypeArity.Binary` for `Kind2`, `Bifunctor` and `Profunctor` (previously always `Unary`, which generated incorrect bounds), detects witness use across fields, local variables, the class hierarchy, nested generics and wildcard bounds (not just method signatures), and no longer emits malformed output (`<Fextends  …>`); the existing-bound intersection case is also fixed
- Type-safe detection: `ConvertRawFreeToFreePath` and `DetectInjectBoilerplate` use a type-attributed `MethodMatcher` instead of rendered-string matching (a user type named `Free`, fully-qualified calls, or static imports no longer mis-fire or get missed); `AddHandleErrorCase` now also handles switch *expressions* with whole-word case matching; the three detect-only recipes emit OpenRewrite search-result markers instead of rewriting source with TODO comments
- 0.5.0 deprecation migration: New `MigrateDeprecationsTo0_5_0` recipe group renames `StateTKind.narrowK` → `narrow` and `KindValidator.narrowWithPattern` → `narrowHolder`

**Library and Build Refinements**

This release also marks one wildcard-witness escape hatch for removal, makes the Java 25 toolchain self-provisioning, and lands the module-internal foundation for batched optic data access.

- `StateTKind.narrowK` deprecation: `StateTKind.narrowK` accepts a wildcard-witness `Kind<?, A>`, bypassing the HKT witness type safety enforced everywhere else in the library; it has no callers and the type-safe `narrow(Kind)` already covers the use case. It is now `@Deprecated(forRemoval = true)` for removal in 0.5.0, with the `MigrateDeprecationsTo0_5_0` OpenRewrite recipe automating the `narrowK` → `narrow` rename ([#455](https://github.com/higher-kinded-j/higher-kinded-j/issues/455))
- Java 25 toolchain auto-provisioning: `settings.gradle.kts` applies the `foojay-resolver-convention` plugin so Gradle downloads and provisions a matching Java 25 JDK automatically when the build machine does not already have one, removing a manual setup step for new contributors
- Request-batching substrate: New module-internal `org.higherkindedj.optics.fetch` package: a free-applicative-style `Fetch<K, V, A>` (`Done`/`Blocked`) and `FetchApplicative` that plug into the optic `modifyF` seam so a traversal whose focused values are loaded from a backend coalesces those N loads into one batched call (the classic N+1), with a transport- and datastore-neutral `BatchLoader` contract and round-based `runCached`/`runAsync` runners carrying a per-run request cache. The package is intentionally not exported — it is the foundation for later data-access capabilities and carries no public API yet ([#539](https://github.com/higher-kinded-j/higher-kinded-j/issues/539))

---

### [v0.4.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.4) (16 May 2026)

**The hkj-test Module, PCollections Integration, and Type Class Enrichments**

This release ships `hkj-test`, a new publishable module providing fluent AssertJ assertion helpers for every public Higher-Kinded-J type, validates and extends PCollections persistent-collection support across the HKT and optics infrastructure, enriches the type class hierarchy with `Alternative.orElseAll(Iterable)` and `MonadZero.filter`, makes `ForState.zoom` and `ReaderPath.magnify` optic-polymorphic, standardises the internal validation package, and refreshes the Tooling chapter to lead with the recommended build-plugin setup.

- [hkj-test Module](tooling/test_assertions.md): New publishable module (`io.github.higher-kinded-j:hkj-test`) with 19 user-facing assertion classes covering the discriminated unions (`Either`, `Maybe`, `Try`, `Validated`, `Lazy`), the Reader/Writer/State trio, the effect types (`IO`, `VTask`, `VStream`), every monad transformer, and the `Free`/`EitherF` algebras. Published as a JPMS module (`org.higherkindedj.test`) so a single dependency declaration suffices; Java 25 with `--enable-preview` can `import module org.higherkindedj.test;` to bring every helper into scope. Backed by an `AssertContract<S, A>` contract-test framework holding the module at a 100% line+instruction coverage gate, plus a new `/hkj-test` Claude Code skill
- [hkj-test Coverage Extension](tooling/test_assertions.md): Seven further assertion classes promoted from `hkj-core` test sources into the published artifact: the `List`/`OptionalKind`/`Stream`/`Id` Kind-narrowing wrappers (`assertThatList`, `assertThatOptionalKind`, `assertThatStream`, `assertThatId`) and the `VTaskPath`/`VStreamPath`/`VTaskContext` path-and-context assertions
- [PCollections HKT Compatibility](tooling/pcollections_integration.md): Validates that PCollections persistent collections (`PVector`, `PStack`) work through the existing `ListKind`/`ListMonad`/`ListTraverse`/`ListSelective`/`Alternative` infrastructure via `java.util.List` compatibility with no production code changes, backed by integration tests, jQwik property tests for the Functor/Monad/Foldable laws, JMH benchmarks, and a runnable example
- [PCollections Optics Generators](tooling/pcollections_optics.md): Seven `TraversableGenerator` plugins teaching `@GenerateTraversals` and `@GenerateFocus` to navigate PCollections types (`PVector`, `PStack`, `PSet`, `PSortedSet`, `PBag`, `PMap` values, `PSortedMap` values); auto-discovered when `org.pcollections` is on the annotation-processor classpath. The generator ecosystem grows from 23 to 30 implementations
- [Traversals.forMapValuesCollecting](optics/common_data_structure_traversals.md): Map-shaped companion to `forIterableCollecting`, with a bounded single-arg overload for `java.util.Map` subtypes (PCollections `PMap`/`PSortedMap`, Guava `ImmutableMap`) and an unbounded two-arg overload for non-`java.util.Map` types (Eclipse Collections, Vavr); `EachInstances.mapValuesEachCollecting` mirrors both for the Focus DSL
- [Alternative.orElseAll(Iterable)](functional/alternative.md): Dynamically-sized counterpart to the existing varargs `orElseAll` and analogue of Haskell's `asum`/`msum`, folding an iterable of alternatives via `orElse`. `ListMonad` and `StreamMonad` override it to avoid O(n^2) result copying and deeply-nested `Stream.concat` chains while preserving lazy evaluation
- [MonadZero.filter](functional/monad_zero.md): New default `filter(Predicate, Kind)` derived from `flatMap` + `of`/`zero`, with allocation-free `ListMonad`/`StreamMonad` overrides; the duplicated guard pattern is refactored out of `For.when`, `ForState.when`/`zoom`, and the `ForPath` comprehension builders to call `filter` directly
- [Axes of Transformer Transformation](transformers/transformer_axes.md): `ForState.zoom` now accepts `FocusPath`, `AffinePath` (short-circuiting via `MonadZero.zero()` when the focus is absent), and `Iso` in addition to `Lens`; `ReaderPath` gains optic-aware `magnify(Getter)` and `magnify(FocusPath)` overloads alongside the existing `local(Function)` escape hatch. New chapter plus a `MagnifyServiceLayerExample` and Tutorial 05 (Optic-Polymorphic Zoom and Magnify)
- Validation package standardised: the `Operation` enum gains `WIDEN`/`NARROW`/`OR_ELSE_ALL`/`FILTER`, `Validation` exposes `KIND`/`FUNCTION`/`TRANSFORMER`/`CORE` static fields, `FunctionValidator` gains `validateMap` (44 Functor/Monad sites migrated), and `KindValidator.narrowWithPattern` is `@Deprecated(forRemoval=true)` for removal in 0.5.0 in favour of the new `narrowHolder`

### Documentation & Tutorial Improvements
- [Build Plugins as the Documented Default](tooling/gradle_plugin.md): The Tooling chapter is reordered so [Build Plugins](tooling/gradle_plugin.md) leads as the recommended path and [Manual Setup](tooling/manual_setup.md) follows as the explicit fallback, with Previous/Next navigation rewired across the chapter to keep the sequence linear. The Spring Boot Quickstart gains an `hkj-bom` option (Gradle and Maven forms) so all HKJ module versions are declared once
- [Where to Start](where_to_start.md): New task-first landing page that asks "what are you trying to do?" before routing to the chapter-level decision trees, with five top-level branches (failure/absence, nested data, async/IO, sequencing, polymorphic code) plus a Combining Tools section covering the most common cross-axis combinations

---

### [v0.4.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.3) (7 May 2026)

**Pluggable HTTP Error Status Strategy, Header Carriers, and Documentation Refresh**

This release introduces pluggable error-to-status mapping for `hkj-spring`, lets domain errors inject custom HTTP headers (`Retry-After`, `WWW-Authenticate`, `Location`, ...), and delivers a comprehensive refresh of the hkj-book: the Effect Path API chapter restructured into five sub-chapters, optics documentation reorganised along Diátaxis lines, the Monad Transformers chapter rebuilt as a coherent learning path with a hands-on tutorial track, the Foundations chapter rewritten around a single recurring "one line, six layers" anchor, and refreshed hands-on materials with tiered hints and per-exercise teaching prose across every tutorial journey.

- [HttpHeaderCarrier](spring/spring_boot_integration.md): Mix-in interface for error values to inject custom HTTP headers into the response. All Effect Path return-value handlers now apply carrier headers before writing the JSON body, enabling `429 Too Many Requests` errors to surface `Retry-After`, `401 Unauthorized` errors to surface `WWW-Authenticate`, and `201 Created` / `301 Moved Permanently` outcomes to surface `Location`
- [ErrorStatusCodeStrategy](spring/spring_boot_integration.md): Pluggable strategy bean replacing the hard-coded heuristics in `ErrorStatusCodeMapper`. The default `DefaultErrorStatusCodeStrategy` combines explicit mappings from `hkj.web.error-status-mappings` (by simple or fully-qualified class name) with token-aware heuristics on the simple class name and the configured default status code; teams can supply a custom `ErrorStatusCodeStrategy` bean to override end-to-end
- [hkj.web.error-status-mappings](spring/spring_boot_integration.md): New configuration property for explicit error-class to HTTP-status mappings, supporting both simple and fully-qualified class names. Covers 4xx/5xx codes outside the heuristic table such as `409 Conflict`, `422 Unprocessable Entity`, `429 Too Many Requests`, and `503 Service Unavailable`
- [Tokenized class-name matching](spring/spring_boot_integration.md): `ErrorStatusCodeMapper` now splits class names on CamelCase boundaries and matches whole tokens, eliminating false positives like `RevalidationError` previously matching the `validation` heuristic

### Documentation & Tutorial Improvements
- [Effect Path API Restructure](effect/quickstart.md): The Effect Path API chapter is reorganised into five sub-chapters (Quickstart, Core Paths, Optics Integration, Advanced Paths, Reference) so a Java developer reaches runnable Effect Path code in under five minutes without advanced material blocking the beginner path. New API-level Effect Path quickstart with three runnable examples covering `MaybePath`, `EitherPath`, and `ForPath`
- [Manual Gradle and Maven Setup](tooling/manual_setup.md): Book-level Quickstart trimmed to lead with the recommended `hkj-gradle-plugin` and `hkj-maven-plugin` setup; full manual build-file configuration extracted to a new dedicated page so adopters who must wire dependencies by hand have one canonical reference
- [Optics Documentation Reorganised](optics/quickstart.md): Optics chapter restructured along Diátaxis lines: narrative pages focus on learning, while new dedicated reference pages serve returning readers. New [Quickstart](optics/quickstart.md), [Annotations at a Glance](optics/annotations_at_a_glance.md), [Optic Capabilities](optics/optic_capabilities.md), [Conversions](optics/conversions.md), [Decision Trees](optics/decision_trees.md), [Compiler Errors](optics/compiler_errors.md), and [Production Readiness](optics/production_readiness.md); 
- [Monad Transformers Learning Path](transformers/quickstart.md): Transformers chapter rebuilt as a coherent learning path: new [Quickstart](transformers/quickstart.md), [Transformers at a Glance](transformers/transformers_at_a_glance.md), [Migration Cookbook](transformers/migration_cookbook.md), [When to Drop to Transformers](transformers/when_to_drop_to_transformers.md), [Common Errors](transformers/common_errors.md), and [Transformer Capstone](transformers/transformer_capstone.md). 
- [Monad Transformers Hands-On Track](tutorials/transformers/transformers_journey.md): New tutorial journey in `hkj-examples`: Tutorial 01 (When Path Isn't Enough, EitherT entry), Tutorial 02 (Async with Absence, OptionalT/MaybeT), Tutorial 03 (Stacking Transformers), and Tutorial 04 (Polymorphic Capabilities). Default test task runs solutions; new `tutorialTest` task includes the in-progress exercises with predictable failures
- [Foundations Chapter Refresh](hkts/one_line_six_layers.md): Foundations reframed as the engine-room tour readers reach after shipping with the Effect Path API, Optics, or Monad Transformers, with three reading paths (mechanism tour, generic-code author, library extender) anchored on a single recurring "one line, six layers" service-method example. New pages: [One Line, Six Layers](hkts/one_line_six_layers.md), [Lifting the Hood](hkts/lifting_the_hood.md) (end-to-end trace through `widen` / dispatch / `narrow` with allocation costs), and [Foundations FAQ](hkts/faq.md) (ten direct answers including comparisons with Vavr, Cyclops, Arrow-Kt, and the Valhalla question). 
- [Hands-On Tutorial Refresh](tutorials/capstone/capstone_journey.md): Refreshed every tutorial journey: Tutorial 00 chapter anchor (One Line, Six Layers, setup-check exercise), new [Capstone Journey](tutorials/capstone/capstone_journey.md) building the chapter anchor up to a real workflow, tiered hint structure (Nudge / Strategy / Spoiler) on tutorial files, and hand-rolled per-exercise teaching prose on every `@Test` in every solution file in the *Why this is idiomatic / Alternative / Common wrong attempt* format. New `tutorialProgress` Gradle task counts `answerRequired()` placeholders across journeys and prints a per-journey progress bar

---

### [v0.4.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.2) (18 April 2026)

**EffectBoundary, Claude Code Skills, and Spring HTTP Ergonomics**

This release introduces `EffectBoundary` for gradual Spring adoption of Free-monad programs, delivers a complete `hkj-spring` order-processing showcase demonstrating the boundary pattern end-to-end, ships a suite of six Claude Code skills providing in-editor guidance to HKJ adopters, extends the Effect Path return-value handlers with `@ResponseStatus` honouring and a canonical `@WebMvcTest` slice-test recipe, widens the `Effectful` capability interface for cross-path error recovery, and adds `EitherPath.bimap` and `Try.attempt(CheckedSupplier)` alongside targeted bug fixes.

- [EffectBoundary](spring/effect_boundary_integration.md): Gradual adoption boundary bridging `Free` programs into the Effect Path handler ecosystem via IO-target (production) and Id-target (test) interpreters. Spring integration adds `@EnableEffectBoundary`, `@Interpreter` component meta-annotation, `@EffectTest` slice, `FreePathReturnValueHandler`, and `ObservableEffectBoundary` (Micrometer), letting teams adopt effects module-by-module without rewriting existing code
- [Effect Boundary Showcase](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-spring/effect-example): Complete Spring Boot order-processing example demonstrating the full boundary pattern: three effect algebras (`OrderOp`, `InventoryOp`, `NotifyOp`) composed into programs, interpreters discovered as Spring beans via `@Interpreter`, `OrderService` building pure `Free<F, A>` programs, `OrderController` invoking `boundary.runIO()` with the existing `IOPathReturnValueHandler`, `TestBoundary` + `Id` pure tests running in milliseconds, full MockMvc integration tests, and `ObservableEffectBoundary` metrics exposed via actuator
- [Claude Code Skills Suite](tooling/claude_code_skills.md): Six Claude Code skills (`/hkj-guide`, `/hkj-optics`, `/hkj-effects`, `/hkj-bridge`, `/hkj-spring`, `/hkj-arch`) providing contextual guidance on Path selection, optics generation, Free monads and effect algebras, effects-optics bridging, Spring adoption ladder, and functional-core architecture; auto-triggered on keywords or invoked directly
- [@ResponseStatus Support](spring/spring_boot_integration.md): All nine Effect Path return-value handlers (`EitherPath`, `MaybePath`, `TryPath`, `ValidationPath`, `IOPath`, `CompletableFuturePath`, `VTaskPath`, `FreePath`, `VStreamPath`) now honour `@ResponseStatus` on handler methods via the new `SuccessStatusResolver`, with controller-class fallback and meta-annotation support; POSTs can return canonical `201`, DELETEs can return `204` with body suppressed
- [@WebMvcTest Slice Recipe](spring/spring_boot_integration.md): Canonical slice-test pattern using `@ImportAutoConfiguration({HkjAutoConfiguration, HkjJacksonAutoConfiguration, HkjWebMvcAutoConfiguration})` with `@MockitoBean`, covering `Right` → `200` and tagged-error `Left` → `404`
- [Effectful Capability Widening](effect/capabilities.md): `handleError`, `handleErrorWith`, and `guarantee` now live on the sealed `Effectful` interface; `handleErrorWith` accepts `Function<? super Throwable, ? extends Effectful<A>>` so `IOPath` and `VTaskPath` can cross-recover while preserving the receiver's concrete type
- [EitherPath.bimap](effect/path_either.md): Transform error and success values in a single call; equivalent to `.mapError(errorFn).map(successFn)` with laziness on the unused branch
- [Try.attempt](monads/try_monad.md): New `Try.attempt(CheckedSupplier)` entry point for Java APIs that throw checked exceptions (`Files.readString`, `Class.forName`, JDBC, reflection). `CheckedSupplier<T, X extends Exception>` in `hkj-api` declares `throws X` on `get()`, avoiding the lambda target-type ambiguity of `Try.of(Supplier)`
- `hkj-checker` registered on `testAnnotationProcessor` and every source-set annotation-processor classpath via the Gradle plugin; the Maven plugin defensively appends HKJ entries to user-supplied `testAnnotationProcessorPaths`, resolving `error: plug-in not found: HKJChecker` during test compilation
- `hkj.web.either.default-error-status` property now binds and takes effect (#490); legacy flat path `hkj.web.default-error-status` preserved as a backward-compatible alias, with end-to-end `@WebMvcTest` regression coverage
- Test coverage uplift across `FocusProcessor`, `FoldProcessor`, `ForComprehensionProcessor`, the optics processors, `EffectAlgebra`/`ComposeEffects`/Path processors, and `KindFieldAnalyser`, plus a new `@ExcludeFromJacocoGeneratedReport` utility

---

### [v0.4.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.1) (8 April 2026)

**Effect Handlers, Spring Observability, and Monad Transformer Enhancements**

This release introduces algebraic effect handlers with annotation-driven code generation, delivers a complete payment processing example with four interpretation modes, adds FreePath for-comprehension support, extends Spring Boot integration with VTask/VStream metrics and virtual thread health monitoring, adds `mapT` to all monad transformers, and includes significant bug fixes for stack safety, traverse performance, and resilience patterns.

- [`@EffectAlgebra`](effect/effect_handlers.md) - Annotation processor generating five classes per sealed interface: Kind marker + Witness, KindHelper, Functor (auto-detects `mapK` for CPS vs cast-through), Ops (smart constructors + `Bound` inner class), and abstract interpreter skeleton with exhaustive `switch` dispatch
- [`@ComposeEffects`](effect/effect_handlers.md#composing-effects) - Annotation processor generating composition infrastructure for 2-4 effect algebras: `Inject` factory methods via right-nested `EitherF`, composed `Functor`, `BoundSet` record, and `interpret()` bridge method
- [`@Handles`](effect/effect_handlers.md#interpreting-programs) - Compile-time validation that interpreter classes handle all operations in an effect algebra; reports missing handlers as errors and extra handlers as warnings
- [EitherF](monads/eitherf.md) - Sum type for composing effect algebras via right-nesting, with `Inject` for embedding operations, `Free.translate` for program transformation, and `Interpreters.combine()` for 2-4 effect dispatch
- [HandleError](effect/effect_handlers.md#error-recovery): `Free.HandleError` wraps sub-programs with typed error recovery; delegates to `MonadError.handleErrorWith` when available, silently ignored otherwise. Supports subclass matching via `Class<E>` token
- [ErrorOp](monads/eitherf.md) - Effect algebra for typed error raising within Free programs, with `ErrorOps.raise()` smart constructor and `Bound<E, G>` for composed effects
- [StateOp](effect/effect_handlers.md) - Optics-native state effect algebra with 6 operations (`View`, `Over`, `Assign`, `Preview`, `TraverseOver`, `GetState`), CPS for correct functor mapping, and `StateOpInterpreter`/`IOStateOpInterpreter` interpreters
- [ProgramAnalyser](effect/effect_handlers.md#program-analysis) - Static analysis of Free program trees: counts instructions (`Suspend`), recovery points (`HandleError`), parallel scopes (`Ap`), and opaque regions (`FlatMapped`). All counts are lower bounds.
- [Payment Processing](examples/payment_processing.md) - Complete worked example with 4 effect algebras, 13 interpreters across production (`IO`), testing (`Id`), quote (fee estimation), and audit (`WriterT`) modes; 12 tests and 6 tutorials
- [Effect Handlers Introduction](effect/effect_handlers_intro.md) - Motivational documentation covering the DI gap, programs-as-data, DOP connection, terminology bridge mapping FP concepts to Java equivalents, and when-to-use guidance
- [FreePath For-Comprehensions](functional/for_comprehension.md) - FreePath as the 10th path type in the `ForPath` system, with `from()`, `let()`, `focus()`, `par()`, `traverse()`, `sequence()`, `flatTraverse()`, and `yield()` steps
- [FreePath.attempt()](effect/path_free.md) - Captures outcome as `Either<Throwable, A>`, mapping success to `Right` and handling errors as `Left`
- [mapT](transformers/ch_intro.md) - New method on all 6 monad transformers (`EitherT`, `MaybeT`, `OptionalT`, `WriterT`, `ReaderT`, `StateT`) for transforming the outer monad layer without unwrapping. Custom AssertJ assertions added for `WriterT`, `ReaderT`, and `StateT`
- [VTask/VStream Metrics](spring/spring_boot_integration.md) - `HkjMetricsService` records success/error counts and execution duration for `VTaskPathReturnValueHandler` and element counts for `VStreamPathReturnValueHandler`; metrics exposed via `/actuator/hkj` endpoint
- [Virtual Thread Health Indicator](spring/spring_boot_integration.md) - Spring Boot health indicator monitoring virtual thread availability with configurable threshold
- [OpenRewrite Recipes](tooling/ch_intro.md) - `AddHandleErrorCaseRecipe` for missing `HandleError`/`Ap` switch cases, `ConvertRawFreeToFreePathRecipe` for `FreePath` migration, `DetectInjectBoilerplateRecipe` for `@ComposeEffects` adoption
- [FList](monads/ch_intro.md) - Lightweight immutable cons-list replacing O(n^2) `LinkedList` copy in `ListTraverse`, `StreamTraverse`, and `VStreamTraverse` with O(n) cons accumulation
- Free.foldMap stack safety: added trampolining to prevent `StackOverflowError` on deep program chains
- FreeAp.foldMap stack safety: added trampolining for deep applicative trees
- CircuitBreaker: reset failure count on success in `HALF_OPEN` state
- ConstBifunctor: fix NPE in `second()` by applying function to second element
- IO.raceIO: fix `ClassCastException` in `firstVTaskSuccess` for checked exceptions
- Lazy: add reentrant-call detection to prevent infinite recursion
- Bulkhead: add permit-release guard to prevent negative permits
- VStreamPar.merge: join background producer thread on close to prevent thread leak
- VStreamThrottle: replace dual `AtomicLong` with `AtomicReference<WindowState>` CAS loop
- Free `F` parameter tightened from `WitnessArity<?>` to `WitnessArity<TypeArity.Unary>` across the entire hierarchy, eliminating raw type usage
- Additional edge case tests for `NavigatorClassGenerator`, `FocusProcessor`, and `ForPathStepGenerator`
- [JMH Benchmarks](benchmarks.md): 7 new benchmarks for EitherF dispatch, Free.translate, HandleError overhead, ProgramAnalyser traversal, and program construction cost

---

### [v0.4.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.4.0) (22 March 2026)

**SPI-Aware Path Widening, Expanded Plugin Ecosystem, and Focus DSL Restructure**

This release introduces SPI-aware path widening for the Focus DSL, allowing automatic `AffinePath` and `TraversalPath` generation based on container cardinality, expands the `TraversableGenerator` plugin ecosystem to 23 generators across 6 library families, adds `Traversal.asFold()` for read-only monoidal aggregation, restructures the Focus DSL documentation into dedicated pages, and delivers comprehensive test coverage and Javadoc quality improvements across processor modules.

- [SPI-Aware Path Widening](optics/focus_navigation.md): Automatic path type inference based on container cardinality: `ZERO_OR_ONE` produces `AffinePath`, `ZERO_OR_MORE` produces `TraversalPath`, eliminating manual `.each()` and `.some()` calls in generated navigators
- [Cardinality-Based Widening](optics/focus_containers.md): `TraversableGenerator` SPI extended with `Cardinality` enum, priority system (`PRIORITY_FALLBACK`, `PRIORITY_DEFAULT`, `PRIORITY_OVERRIDE`), `widenCollections` opt-in attribute, and wildcard type resolution for `? extends T`, `? super T`, and bare `?`
- [Nested Container Widening](optics/focus_containers.md): Compound types like `Optional<List<String>>` resolve correctly through recursive cardinality analysis, with navigator field collision detection
- [Generator Plugin Ecosystem](tooling/generator_plugins.md): 23 `TraversableGenerator` implementations across 6 library families: base JDK (Array, List, Set, Optional, MapValue), Apache Commons Collections4 (HashBag, UnmodifiableList), Eclipse Collections (ImmutableBag, MutableBag, ImmutableList, MutableList, ImmutableSet, MutableSet, ImmutableSortedSet, MutableSortedSet), Google Guava (ImmutableList, ImmutableSet), Vavr (List, Set), and HKJ native (Either, Maybe, Try, Validated)
- [Traversal.asFold()](optics/folds.md): Conversion from any `Traversal` to a read-only `Fold` for monoidal aggregation, existence checks, and length counting via new `ConstForFold` applicative functor
- [AffinePath](optics/focus_navigation.md): New `AffinePath<S, A>` for zero-or-one navigation in Focus DSL, with `Affine` optic interface supporting `getOrModify` and `set`
- [Portfolio Risk Analysis](examples/examples_portfolio_risk.md): Capstone example demonstrating container navigation, SPI widening, and nested optics composition
- Focus DSL documentation restructured into dedicated pages: [Containers](optics/focus_containers.md), [Navigation](optics/focus_navigation.md), [Effects](optics/focus_effects.md), and [Reference](optics/focus_reference.md)
- [Tutorial 19: Navigator Generation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java): 7 exercises on annotation-driven navigator code generation
- [Tutorial 20: Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java): 4 exercises on SPI-aware container type navigation
- Automated SPI service declarations via Avaje SPI processor for plugin discovery

---

### [v0.3.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.7) (15 March 2026)

**WriterT Transformer, For-Comprehension Power-Ups, Build Tooling, and Spring Virtual Thread Support**

This release introduces the `WriterT` monad transformer with MTL-style capability interfaces, enriches for-comprehensions with parallel composition, traversal operations, and optics integration, adds compile-time Path type checking via the new `hkj-checker` javac plugin, delivers one-line project setup through build tool plugins (`hkj-gradle-plugin` and `hkj-maven-plugin`), and extends Spring MVC with virtual-thread-native return value handlers for `VTaskPath` and `VStreamPath`.

- [WriterT](transformers/writert_transformer.md): Monad transformer for output accumulation across effect boundaries, wrapping `Kind<F, Pair<A, W>>` with automatic Monoid-based combining during `flatMap` chains
- [MonadWriter](transformers/mtl_writer.md): MTL-style capability interface with `tell`, `listen`, `pass`, `listens`, and `censor` for output accumulation
- [MonadReader](transformers/mtl_reader.md): MTL-style capability interface with `ask`, `local`, `reader`, and `asks` for shared environment access
- [MonadState](transformers/mtl_state.md): MTL-style capability interface with `get`, `put`, `modify`, and `gets` for stateful computation
- [par()](functional/for_par.md): Parallel/applicative composition for `For` and `ForPath` comprehensions; true concurrency on `VTask`, intent-documenting on sequential monads
- [traverse/sequence/flatTraverse](functional/for_traverse.md): Bulk effectful operations within comprehension chains: apply an effectful function across a structure, flip `Structure<Effect<A>>` to `Effect<Structure<A>>`, or traverse-and-flatten in one step
- [For-Comprehension Optics Integration](functional/for_optics.md): `through(Iso)` for type-safe value conversion in `For`; `traverseOver()`, `modifyThrough()`, `modifyVia()`, and `updateVia()` for optics-driven state operations in `ForState`
- [Fold Combinators](optics/folds.md): `Fold.plus()`, `Fold.empty()`, and `Fold.sum()` forming a monoid on folds for multi-path data extraction
- [Compile-Time Path Checks](tooling/compile_checks.md): `hkj-checker` javac plugin detecting Path type mismatches at compile time for `via`, `then`, `zipWith`, `zipWith3`, `recoverWith`, and `orElse`
- [Build Plugins](tooling/gradle_plugin.md): `hkj-gradle-plugin` (one-line Gradle setup) and `hkj-maven-plugin` (Maven lifecycle extension) that auto-configure HKJ dependencies, `--enable-preview` flags, compile-time checking, and optional Spring Boot integration
- [hkj-bom](tooling/gradle_plugin.md): Bill of Materials POM for version-aligned dependency management across all HKJ modules in both Gradle and Maven
- [Diagnostics](tooling/diagnostics.md): `hkjDiagnostics` Gradle task and `mvn hkj:diagnostics` goal reporting active dependencies, compiler arguments, and checks
- [VTaskPath Spring MVC](spring/spring_boot_integration.md): `VTaskPathReturnValueHandler` converting controller return values to async `DeferredResult` responses on virtual threads
- [VStreamPath SSE](spring/spring_boot_integration.md): `VStreamPathReturnValueHandler` converting controller return values to Server-Sent Events with pull-based backpressure, no Reactor required
- Dependency updates: Gradle 9.4.0, JUnit 6.0.3, Jackson 3.1.0, Spring Boot 4.0.3, jOOQ 3.20.11, javapoet 0.12.0, and others
- Faster `FunctionValidator` and `KindValidator` with simplified implementation
- Test reliability improvements: replaced `Thread.sleep` with Awaitility across test suite

---

### [v0.3.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.6) (6 March 2026)

**VStream Lazy Streaming, Resilience Patterns, and ForState Comprehensions**

This release introduces `VStream`, a lazy pull-based streaming type built on virtual threads with full HKT integration, adds four core resilience patterns (Circuit Breaker, Bulkhead, Retry, Saga) with Effect Path integration, extends `ForState` with filtering and pattern matching, and delivers a Market Data Pipeline capstone example.

- [VStream](monads/vstream.md): Lazy pull-based streaming on virtual threads with `Step` protocol (Emit/Done/Skip), factory methods (`of`, `range`, `iterate`, `generate`, `unfold`), transformation combinators, and error recovery
- [VStream HKT Integration](monads/vstream_hkt.md): `VStreamKind` witness type with Functor, Applicative, Monad, Foldable, Traverse, and Alternative type class instances
- [VStream Parallel Operations](monads/vstream_parallel.md): `VStreamPar` with `parEvalMap`, `parEvalMapUnordered`, `parEvalFlatMap`, `merge`, `parCollect`, and chunking combinators
- [VStream Resources](monads/vstream_resources.md): `bracket`/`onFinalize` resource lifecycle management and `VStreamReactive` bidirectional `Flow.Publisher` bridge with backpressure
- [VStreamPath](effect/path_vstream.md): Effect Path bridge with factory methods, `PathOps` operations, terminal operations bridging to `VTaskPath`, and optics focus bridge
- [Circuit Breaker](resilience/circuit_breaker.md): State machine (Closed/Open/HalfOpen) with configurable failure thresholds and recovery timeouts
- [Bulkhead](resilience/bulkhead.md): Concurrency limiting for isolating resource access
- [Retry](resilience/retry.md): Configurable retry policies with fixed delay, exponential backoff, and jitter
- [Saga](resilience/saga.md): Distributed transaction compensation with ordered rollback
- [Combined Resilience](resilience/combined.md): Composing multiple resilience patterns and Path API ergonomic methods: `retry()`, `circuitBreaker()`, `bulkhead()`, `timeout()`
- [ForState](functional/forstate_comprehension.md): Filtering (`when`), pattern matching (`matchThen`), traversals, zoom, and `toState()` bridge from For comprehensions at all arities (1–12)
- [traverseWith()](optics/focus_dsl.md): Parallel effectful optics traversal for `FocusPath`, `AffinePath`, and `TraversalPath` via `VTaskPath` and `StructuredTaskScope`
- [Market Data Pipeline](examples/examples_market_data.md): 14-feature capstone example demonstrating concurrent feed merging, parallel enrichment, risk assessment, windowed aggregation, anomaly detection, and circuit breaker failover
- Refreshed [Monads chapter](monads/ch_intro.md) with problem-first structure, real-world analogies, and consistent formatting
- `FunctionValidator` optimisation: deferred error-message construction avoids `String` allocation on the happy path; fixed Gradle benchmark commands (`-Pincludes`)
- Javadoc generation fix to include annotation-processor-generated sources (`Tuple2`–`Tuple12`, `MonadicSteps`, etc.)
- [JMH benchmarks](benchmarks.md) for VStream construction, combinators, terminals, and parallel operations

---

### [v0.3.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.5) (15 February 2026)

**Extended For-Comprehensions, VTask API Refinement, and Documentation Restructure**

This release extends for-comprehension arity to 12, simplifies the VTask API, adds Maybe-to-Either conversions, upgrades to JUnit 6, and delivers a comprehensive documentation restructure with quickstart guides, cheat sheets, migration cookbooks, and railway diagrams.

- [For-Comprehension Arity 12](functional/for_comprehension.md): `For` and `ForPath` now support up to 12 monadic bindings (previously 5), with generated `Tuple9`–`Tuple12` and `Function9`–`Function12`
- [VTask API](monads/vtask_monad.md): `VTask.run()` no longer declares `throws Throwable`; checked exceptions are wrapped in `VTaskExecutionException`
- [Maybe.toEither](monads/maybe_monad.md): New `toEither(L)` and `toEither(Supplier<L>)` conversion methods for seamless `Maybe` → `Either` transitions
- [Quickstart](quickstart.md): New getting-started guide with Gradle and Maven setup including `--enable-preview` configuration
- [Cheat Sheet](cheatsheet.md): Quick-reference for Path types, operators, escape hatches, and type conversions
- [Stack Archetypes](transformers/archetypes.md): 7 named transformer stack archetypes with colour-coded railway diagrams
- [Migration Cookbook](transformers/migration_cookbook.md): 6 recipes for migrating from try/catch, Optional chains, null checks, CompletableFuture, validation, and nested records
- [Compiler Error Guide](effect/compiler_errors.md): Solutions for the 5 most common Effect Path compiler errors
- [Effects-Optics Capstone](effect/capstone_focus_effect.md): Combined effects and optics pipeline example
- Railway operator diagrams for all 8 Effect Path operators and for EitherT, MaybeT, OptionalT transformers
- JUnit 6.0.2 upgrade (from 5.14.1) across all test modules
- Golden file test infrastructure with automated sync verification and pitest mutation coverage improvements

---

### [v0.3.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.4) (31 January 2026)

**External Type Optics and Examples Gallery**

This release introduces powerful optics generation for external types you cannot modify, plus a new Examples Gallery chapter documenting all runnable examples.

- [@ImportOptics](optics/importing_optics.md): Generate optics for JDK classes and third-party library types via auto-detection of withers and accessors
- [Spec Interfaces](optics/optics_spec_interfaces.md): Fine-grained control over external type optics with `OpticsSpec<S>` for complex types like Jackson's `JsonNode`
- [@ThroughField Auto-Detection](optics/copy_strategies.md#throughfield-auto-detection): Automatic traversal type detection for `List`, `Set`, `Optional`, arrays, and `Map` fields
- [Examples Gallery](examples/ch_intro.md): New chapter with categorised, runnable examples demonstrating core types, transformers, Effect Path API, and optics
- Comprehensive hkj-processor testing improvements with enhanced coverage

---

### [v0.3.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.3) (24 January 2026)

**Structured Concurrency, Atomic Optics, and Enhanced Examples**

This release introduces structured concurrency primitives, atomic coupled-field updates, and a comprehensive Order Workflow example demonstrating these patterns.

- [Structured Concurrency](monads/vtask_scope.md): `Scope` for parallel operations with `allSucceed()`, `anySucceed()`, `firstComplete()`, and `accumulating()` joiners
- [Resource Management](monads/vtask_resource.md): `Resource` for bracket-pattern cleanup with guaranteed release
- [Coupled Fields](optics/coupled_fields.md): `Lens.paired` for atomic multi-field updates bypassing invalid intermediate states
- [Order Workflow Overview](hkts/order-walkthrough.md): Reorganised documentation with focused sub-pages
- [Concurrency and Scale](hkts/order-concurrency.md): Context, Scope, Resource, VTaskPath patterns in practice
- `EnhancedOrderWorkflow`: Full workflow demonstrating Context, Scope, Resource, VTaskPath
- `OrderContext`: ScopedValue keys for trace ID, tenant isolation, and deadline enforcement
- [Scope & Resource Tutorials](tutorials/concurrency/scope_resource_journey.md): 18 exercises on concurrency patterns
- [Release History](release-history.md): New page documenting all releases

---

### [v0.3.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.2) (17 January 2026)

**Virtual Thread Concurrency with VTask**

This release introduces `VTask<A>`, a lazy computation effect leveraging Java 25's virtual threads for lightweight concurrent programming.

- [VTask](monads/vtask_monad.md): Lazy computation effect for virtual thread execution
- [Structured Concurrency](monads/vtask_scope.md): `Scope` for parallel operations with `allSucceed()`, `anySucceed()`, and `accumulating()` patterns
- [Resource Management](monads/vtask_resource.md): `Resource` for bracket-pattern cleanup guarantees
- [VTaskPath](effect/path_vtask.md): Integration with the Effect Path API
- [Concurrency and Scale](hkts/order-concurrency.md): Practical patterns in the Order Workflow example
- `Par` parallel combinators for concurrent execution
- Comprehensive benchmarks comparing virtual vs. platform threads

---

### [v0.3.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.1) (15 January 2026)

**Static Analysis Utilities**

This release adds utilities for statically analysing Free Applicative and Selective functors without execution.

- [Choosing Abstraction Levels](functional/abstraction_levels.md): Guide to selecting Functor, Applicative, Selective, or Monad
- [Free Applicative](monads/free_applicative.md): Static analysis with `FreeApAnalyzer`
- [Selective](functional/selective.md): Conditional effects with `SelectiveAnalyzer`
- Tutorial 11 on static analysis patterns

---

### [v0.3.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.0) (4 January 2026)

**Effect Path Focus Integration**

Major release introducing the unified Effect Path API and Focus DSL integration. Requires Java 25 baseline.

- [Effect Path Overview](effect/effect_path_overview.md): The railway model for composable effects
- [Path Types](effect/path_types.md): 17+ composable Path types including `EitherPath`, `MaybePath`, `IOPath`
- [Focus DSL](optics/focus_dsl.md): Type-safe optics with annotation-driven generation
- [Focus-Effect Integration](effect/focus_integration.md): Bridge between optics and effects
- [ForPath Comprehension](effect/forpath_comprehension.md): For-comprehension syntax for Path types
- [Spring Boot Integration](spring/spring_boot_integration.md): Spring Boot 4.0.1+ support
- [Order Workflow](hkts/order-walkthrough.md): Complete example demonstrating Effect Path patterns

---

## Earlier Releases

### [v0.2.8](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.8) (26 December 2025)

- Introduced `ForPath` for Path-native for-comprehension syntax
- Complete Spring Boot 4.0.1 migration of `hkj-spring` from `EitherT` to Effect Path API
- New return value handlers for Spring integration

### [v0.2.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.7) (20 December 2025)

- Effect Contexts: `ErrorContext`, `OptionalContext`, `ConfigContext`, `MutableContext`
- Bridge API enabling seamless transitions between optics and effects

### [v0.2.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.6) (19 December 2025)

- New Effect Path API with 17+ Path types
- Retry policies and parallel execution utilities
- Kind field support in Focus DSL

### [v0.2.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.5) (9 December 2025)

- Annotation-driven Focus DSL for fluent optics composition
- Free Applicative and Coyoneda functors
- Natural Transformation support

### [v0.2.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.4) (3 December 2025)

- Affine optic for focusing on zero or one element
- `ForTraversal`, `ForState`, and `ForIndexed` comprehension builders
- For-comprehension and optics integration

### [v0.2.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.3) (1 December 2025)

- Cross-optic composition (Lens + Prism → Traversal)
- Experimental Spring Boot starter
- Custom target package support for annotation processor

### [v0.2.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.2) (29 November 2025)

- Java 25 baseline
- Experimental `hkj-spring` module
- Validation helpers and ArchUnit architecture tests
- Thread-safety fix in Lazy memoisation

### [v0.2.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.1) (23 November 2025)

- 7-part Core Types tutorial series
- 9-part Optics tutorial (~150 minutes total)
- Versioned documentation system
- Property-based testing infrastructure
- JMH benchmarking framework

### [v0.2.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.0) (21 November 2025)

- Six new optic types: Fold, Getter, Setter, and indexed variants
- FreeMonad for DSL construction
- Trampoline for stack-safe recursion
- Const Functor
- Enhanced Monoid with new methods
- Alternative type class

### [v0.1.9](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.9) (14 November 2025)

- Selective type class for conditional effects
- Enhanced optics with `modifyWhen()` and `modifyBranch()`
- Bifunctor for Either, Tuple2, Validated, and Writer
- Higher-kinded Stream support

### [v0.1.8](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.8) (9 September 2025)

- Profunctor type class
- Profunctor operations in universal Optic interface

### [v0.1.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.7) (29 August 2025)

- Generated `with*` helper methods for records via `@GenerateLenses`
- `Traversals.forMap()` for key-specific Map operations
- Semigroup interface with Monoid extending it
- Validated Applicative with error accumulation

### [v0.1.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.6) (14 July 2025)

- Optics introduction: Lens, Iso, Prism, and Traversals
- Annotation-based optics generation
- Plugin architecture for extending Traversal types
- Modular release structure

### [v0.1.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.5) (12 June 2025)

- For comprehension with generators, bindings, guards, and yield
- Tuple1-5 and Function5 support

### [v0.1.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.4) (5 June 2025)

- Validated Monad
- Standardised widen/narrow pattern for KindHelpers (breaking change)

### [v0.1.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.3) (31 May 2025)

- First Maven Central publication
- 12 monads, 5 transformers
- Comprehensive documentation

### [v0.1.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.0) (3 May 2025)

- Initial release
- Core types: Either, Try, CompletableFuture, IO, Lazy, Reader, State, Writer
- EitherT transformer

---

~~~admonish tip title="See Also"
- [GitHub Releases](https://github.com/higher-kinded-j/higher-kinded-j/releases): Full changelogs and assets
- [Contributing](CONTRIBUTING.md): How to contribute to Higher-Kinded-J
~~~

---

**Previous:** [Glossary](glossary.md)
**Next:** [Benchmarks & Performance](benchmarks.md)
