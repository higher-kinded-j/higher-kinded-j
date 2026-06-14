# Comparative Analysis: `eschizoid/telescope` vs. Higher-Kinded-J

**Author:** Architecture & code review (analysis only — no library code changed)
**Date:** 2026-06-14
**Subject:** [`eschizoid/telescope`](https://github.com/eschizoid/telescope) — an optics-based navigation/mapping DSL for Java — examined for patterns worth borrowing, and for places where Higher-Kinded-J (HKJ) is already stronger.
**Lens of evaluation:** HKJ's stated values — **correctness**, **type safety**, **ergonomics**.

---

## 1. Executive summary

Telescope and HKJ overlap on exactly one subsystem: **optics over immutable Java data**. Everything else in HKJ (HKT emulation, monads, monad transformers, `Free`/`FreeAp`/`Coyoneda`, effect handlers, `Reader`/`Writer`/`State`, `IO`, `VTask`/`VStream`, resilience, the Effect Path API, the Spring effect integration) has no analogue in telescope. So this is not a peer-to-peer comparison; it is a focused study of a single-purpose library that has polished its one niche to a high shine.

Telescope's design thesis is the inverse of HKJ's: **it hides the optic lattice entirely** (`Iso`/`Lens`/`Prism`/`Affine`/`Traversal` live behind JPMS qualified exports — users *cannot type those names*) and exposes a single `Telescope<S, A>` type that does read / update / convert / effectful-update. HKJ deliberately exposes the whole optic zoo as composable public API. Neither is "right"; they optimise for different audiences.

Three telescope capabilities are **genuine, tangible gaps in HKJ** and are worth adopting (in HKJ's own house style):

1. **Bidirectional, auto-derived record↔DTO mapping** (`Telescope.mapper(A.class, B.class, …)`). HKJ has `Iso` as a hand-built block but nothing that *derives* a structural `Iso` between two shapes by matching fields, recursing into nested records, and lifting through `List`/`Optional`/`Map`. This is the single most common Java boilerplate and HKJ has no answer to it today.
2. **An ergonomic sparse-PATCH / multi-edit primitive** (`Telescope.all(over(…), overIfPresent(…))`). Tailor-made for REST `PATCH` controllers where each nullable DTO field lands 1:1 on a domain object.
3. **Auto-detecting JavaBean write support** (`SETTERS → BUILDER → field-injection → CONSTRUCTOR`) so legacy/JPA/Lombok types get optics with near-zero ceremony.

Two further items are **craft lessons rather than features**: telescope's **error-message quality** is exemplary and worth matching, and its **time-to-first-success** is lower than HKJ's because navigation works reflectively with *zero build wiring*.

Conversely, HKJ is clearly ahead on **type safety** (cardinality-precise path types), **correctness breadth** (open `modifyF` over any `Applicative`; a far wider lawful surface with reusable law-test factories), and **tooling/ecosystem** (checker, OpenRewrite, plugins, BOM, Spring effect integration). Those should be protected, not traded away in pursuit of telescope's ergonomics.

---

## 2. What each project is (scope and scale)

| | **Higher-Kinded-J** | **Telescope** |
|---|---|---|
| Purpose | Composable effects **and** advanced optics; an HKT substrate for Java | Typed navigation + immutable update + bidirectional mapping over records/beans |
| Core size | `hkj-core` ≈ 268 KLOC; 15+ modules | 3 core modules (`core`, `internal`, `codegen`) + Spring/Quarkus/Lombok starters |
| Optic types | `Lens`, `Prism`, `Iso`, `Affine`, `Traversal`, `Getter`, `Setter`, `Fold`, `Each`, `At`, `Ixed`, indexed variants — **all public** | Same lattice — **all hidden** behind JPMS qualified exports |
| User-facing optic surface | `FocusPath` / `AffinePath` / `TraversalPath` **plus** the raw optics | A single `Telescope<S, A>` |
| Codegen | `@GenerateLenses/Focus/Prisms/Isos/Traversals`, `@ImportOptics`/`OpticsSpec`; **codegen-only** (no reflective fallback) | `@Focus`/`@BeanFocus`/`@Bridge`; **optional** — reflective by default, codegen for hot paths |
| Effects | Full Effect Path API + monads/transformers/Free + handlers | Four fixed effect terminals: `updateAsync`/`updateOptional`/`updateEither`/`updateValidated` |
| License | MIT | Apache-2.0 |

The headline is the **scope mismatch**: telescope is roughly "HKJ's optics package, taken alone, and pushed hard on the practical record↔DTO use case." Read the rest of this document with that framing.

---

## 3. The reflection-vs-codegen axis (MapStruct ↔ telescope ↔ HKJ)

Telescope explicitly benchmarks itself against **MapStruct**, whose entire historical pitch was *compile-time codegen, zero runtime reflection* — the very property that separated it from the older reflective mappers (Dozer, ModelMapper). Yet telescope's **default** path is reflective. That looks like conceding the match; it is actually a deliberate, and largely defensible, trade. Two distinctions carry the weight.

**Telescope reflects for *mechanism*, not for *resolution*.** Reflective mappers of the ModelMapper lineage are stringly-typed — a mistyped field name surfaces as a runtime failure. Telescope's navigation is instead driven by **method references** (`User::name`), which `javac` rejects if the accessor doesn't exist or its type doesn't line up; it then recovers the *name* from that already-type-checked reference via `SerializedLambda` introspection and reflects only to read and rebuild. So telescope keeps MapStruct's **type-safety** property and concedes only its **performance** one (~100 ns/field hop reflective vs ~10 ns hand-written). The single genuinely-unchecked door is the deliberately-named `fieldByName(String)` escape.

**What telescope *does* fully concede is compile-time verification of the *mapping*.** That is the real fault line:

| | Field correspondence verified… | Runtime reflection | First failure surfaces at |
|---|---|---|---|
| **MapStruct** | compile time (`javac`) | none | build |
| **Telescope — default** | mapper *construction* | yes (read / rebuild) | startup / first call |
| **Telescope — `@Bridge` / `@Focus`** | compile time | none | build |
| **HKJ — Focus DSL / optics** | compile time | none | build |

Telescope's `@Bridge`/`@Focus` codegen tier exists precisely to climb back onto MapStruct's rung, and on telescope's own numbers it gets there (deep tier 59 vs 50 ns/op — *"matches"*; flat/nested 1.2–1.9× behind, single-digit ns absolute). So the honest reading is *"reflective by default, codegen when it matters,"* not *"reflective instead of codegen"* — and the README is candid about the gap (262 ns reflective vs 45 ns codegen for the deep-field path).

**Why default to reflection at all, given the MapStruct comparison?** Because a *bidirectional, recursive, container-lifting* mapper is far harder to fully codegen than MapStruct's flat, one-directional methods — reflection ships the general case immediately and codegen handles the hot subset — and because zero build-wiring is a real adoption lever. The MapStruct comparison is therefore a **capability** claim (bidirectional + deep + effectful + composable), with reflection as the price of generality and `@Bridge` as the pressure-release.

**Why this matters for HKJ.** HKJ already sits on MapStruct's rung: the Focus DSL is codegen-only, compile-time-verified, reflection-free — it has *no* reflective tier. That repositions two recommendations below:

- It is the strongest argument *for* **A1**. A codegen-first bidirectional mapper would give HKJ **MapStruct's compile-time verification *and* telescope's bidirectionality/composability in one artifact** — the quadrant neither incumbent occupies (MapStruct: codegen but one-directional; telescope: bidirectional but reflective-by-default).
- It is the strongest argument *for* caution **D**. The reflective tier is the one part of telescope HKJ should specifically *not* import, because HKJ's existing default is already the stronger position on this axis. Adopt telescope's *capabilities* on HKJ's *foundation* — not its defaults.

---

## 4. Part A — Techniques in telescope worth adopting

Each item: **Observation → Evidence → Recommendation → Justification (tied to correctness / type-safety / ergonomics) → Effort & risk → Caveats.**

### A1. Bidirectional, auto-derived record↔DTO mapper  ★ highest value

**Observation.** Telescope derives a structural, *bidirectional* mapper between two shapes from one declaration, recursing into nested records/beans and auto-lifting containers:

```java
Mapper<Company, CompanyDto> m = Telescope.mapper(
    Company.class, CompanyDto.class,
    to(User::name, UserDto::fullName),     // explicit rename row
    to(Address::zip, AddressDto::postalCode));

CompanyDto dto      = m.forward(company);   // A → B
Company   restored  = m.backward(dto);      // B → A
Company   patched   = m.patch(company, sparseDto);  // overlay non-null fields
```

Same-named fields identity-map; nested records/POJOs recurse; `List<X>↔List<Y>`, `Optional<X>↔Optional<Y>`, `Map<K,X>↔Map<K,Y>` lift the inner `Iso` automatically (`Mapper.liftList/liftSet/liftOptional/liftMapValues`). It even offers MapStruct-style lifecycle hooks (`beforeForward`/`afterForward`/`beforeBackward`/`afterBackward`) and an N-source `Telescope.merge(...)` for "combine several sources into one target."

**Evidence.** `core/.../conversion/Mapper.java` (the `Iso<A,B>`-backed engine with `forward/backward/patch/asTelescope/lift*` and the four hook fields); `core/.../Merge.java` (auto-backfill by name+type with build-time guards); `module-info.java` documents the `forward/backward/patch/asTelescope/container-lift surface`.

**The HKJ gap is real.** HKJ's `Iso<S,A>` is a *hand-built* primitive (`Iso.of(get, reverseGet)`), and `@GenerateIsos` only generates an `Iso` from a user-written interface method — there is **no** auto-derivation that matches fields by name, recurses, and lifts through containers. Entity↔DTO conversion — the single most common boilerplate in mainstream Java — has no first-class story in HKJ today.

**Recommendation.** Add an **optics-derived bidirectional mapper** to HKJ, generated at compile time in HKJ's house style:

```java
@GenerateMapper(from = UserEntity.class, to = UserDto.class)
// rename/transform rows supplied via the spec, mirroring @ImportOptics:
//   @Rename(from = "name", to = "fullName")
//   @Drop("auditTrail")            // one-way / lossy fields excluded from the Iso
```

Generate (a) a real `Iso<UserEntity, UserDto>` that **composes with the existing Focus DSL and Effect Paths**, plus (b) a thin `Mapper` facade with `forward/backward/patch`. HKJ can do this *better than telescope* on all three values:

- **Type safety / correctness:** generate at compile time (no reflection), and make a same-named-but-non-isomorphic field pair a **compile error**, not a silent cast. Telescope catches type mismatches at *build* time in `Merge` (`"matched field 'x' by name but types differ"`); HKJ can catch them at *`javac`* time.
- **Correctness:** verify the round-trip law (`backward(forward(a)) == a`) in **generated** tests using HKJ's existing `LensLawsTestFactory`/`AffineLawsTestFactory`-style harness, so every generated mapper is law-checked.
- **Ergonomics + effects:** because the result is a genuine `Iso`, a *fallible* mapping (e.g. `String → Email` with validation) returns a `ValidationPath<E, Dto>` that **accumulates per-field conversion errors**, or an `EitherPath` that short-circuits — something MapStruct and telescope cannot express, and a natural fit for `hkj-spring`'s error-to-HTTP mapping. A `Mapper<A,B>` bean registry in the Spring starter mirrors telescope's `telescope-spring-boot-starter`.

**Justification.** Directly attacks the #1 ergonomics pain in real Java code, reuses HKJ's existing `Iso` substrate + codegen + law-testing rather than inventing machinery, and is *more* correct and type-safe than the incumbents. It also lands a unique selling point: **bidirectional, lawful, effect-aware mapping** that composes with the rest of HKJ. See §3 for why a *codegen-first* mapper occupies a quadrant neither MapStruct (codegen but one-directional) nor telescope (bidirectional but reflective-by-default) holds.

**Effort & risk.** Medium-high. The recursion/container-lifting and name-matching logic is non-trivial but well-understood (telescope is a working reference). Risk is contained because it produces ordinary `Iso` values.

**Caveat — heed telescope's own discipline.** Telescope has a pointed comment in `Mapper.java` (the *"lattice mandate carve-out"*) explaining why a hook with no inverse is **not** wrapped as a fake `Iso` — doing so would "silently violate the laws `OpticLawsTest` pins." HKJ must hold the same line: only *invertible* field correspondences become part of the `Iso`. Lossy/derived/one-way fields belong to a separate one-directional surface (a `Getter`/`Fold`-based forward-only mapper, telescope's `via`/`drop`/`compute` rows), never smuggled into the `Iso`.

### A2. Ergonomic sparse-PATCH / multi-edit primitive  ★ high value, low cost

**Observation.** Telescope folds several heterogeneous edits at different paths into one reusable `Function<S,S>`, with first-class support for the nullable-DTO PATCH shape:

```java
Telescope<Order, Order> applyPatch = Telescope.all(
    Edit.overIfPresent(ORDER_NUMBER,   req.orderNumber()),                 // null ⇒ identity slot
    Edit.overIfPresent(CUSTOMER_EMAIL, req.customerEmail(), String::toLowerCase),
    Edit.mapIfPresent (QUANTITY,       req.quantityDelta(), (d, q) -> q + d));
Order updated = applyPatch.apply(order);
```

`overIfPresent`/`mapIfPresent` make `null ⇒ no-op` the default, so a partial-update controller reads as a flat list of independent edits with the slot count visible at a glance (`core/.../Edit.java`, `Telescope.all(Edit[])`).

**Evidence.** `core/.../Edit.java` (`over`, `overIfPresent` ×2 overloads, `mapIfPresent`, `identity`), with docs explicitly positioning it as "the ergonomic shape for sparse-PATCH controllers where each request DTO field is nullable and you want to land it 1:1 on the domain."

**HKJ today.** HKJ can compose optics and has `Lens.paired(...)` for *coupled* fields, but there is no ergonomic "apply N **independent** edits, skipping absent values" combinator aimed at PATCH.

**Recommendation.** Add a small `Focus`-level combinator — e.g. `Focus.all(over(path, fn), overIfPresent(path, nullableValue), …)` returning a reusable `Function<S,S>` (or a `FocusPath<S,S>`). Then offer the HKJ-native upgrade telescope cannot: an **effectful** variant returning `ValidationPath<E,S>` (accumulate all field errors) or `EitherPath<E,S>` (short-circuit), so a controller can validate a partial update and report every bad field at once.

**Justification.** Pure ergonomics win for a very common web scenario; self-contained; composes with existing types; and the `ValidationPath` variant is a differentiator that slots straight into `hkj-spring`.

**Effort & risk.** Low. A few combinators plus tests. No architectural impact.

### A3. Auto-detecting JavaBean write support  ★ medium value (opt-in)

**Observation.** Telescope navigates mutable POJOs out of the box (`Telescope.ofBean(...)`) and **auto-detects how to write** them: `SETTERS` (Lombok `@Data` — the common case) → static `builder()` → no-arg ctor + field injection → all-args `CONSTRUCTOR`, with a per-target `writeBean(target, STRATEGY)` override when auto-detection can't decide (`internal/.../Beans.java`).

**HKJ today.** Beans are second-class: a user must hand-write an `@ImportOptics`/`OpticsSpec` interface with a per-field strategy annotation (`@ViaBuilder`, `@Wither`, `@ViaCopyAndSet`). For a 30-field JPA entity that is 30 rows of ceremony.

**Recommendation.** Add an **opt-in** auto-detecting bean mode to the optics processor (a `@GenerateLenses`-for-beans variant that introspects wither/builder/setter and picks a strategy per field, overridable). Keep it clearly secondary to records.

**Justification.** Broadens HKJ to the large non-record population (JPA entities, Lombok `@Data`) with far less ceremony — an adoption/ergonomics lever.

**Effort & risk.** Medium. **Caveat:** writing via setters mutates, which is at odds with HKJ's immutability-first, pure-FP core. Keep it opt-in, generated, and reflection-free; do **not** make it the default or let it leak mutation into the functional surface. (Telescope itself flags reflective bean access as a documented cost and an escape hatch, not the happy path.)

### A4. Steal telescope's error-message craftsmanship  ★ low cost, high ROI

**Observation.** Telescope's diagnostics are unusually good. They name the offending row index *and* the factory, list what *was* present, and prescribe the exact fix — including JPMS remediation:

> `Telescope.merge: forward called without a source for class com.x.User (required by the row writing target field 'name'). Sources bag contains: [Order, Address]. Pass a value of User via Sources.of(...) or Sources.builder().with(...).`

…and for module access: *"Add `opens com.x to io.github.eschizoid.telescope` … Switching the writeBean hint to CONSTRUCTOR/BUILDER/SETTERS reaches the bean …"* (`Merge.java`, `Beans.java`).

**Recommendation.** Audit HKJ's annotation-processor diagnostics (wrong `@GenerateLenses` target, missing wither, raw-`Kind` misuse) and any runtime optics/path failures against this bar: *what failed, why, and the precise next action*. The `hkj-checker` rules are the natural place to ensure messages are actionable.

**Justification.** Pure ergonomics polish, no architectural risk, and squarely on HKJ's values. Error messages are the most-read documentation in any library.

**Effort & risk.** Low; incremental.

### A5. Lower the time-to-first-success  ◆ adopt the goal, not the mechanism

**Observation.** Telescope navigates a record with **zero build wiring** — `Telescope.of(User.class).field(User::name)` works reflectively, and users opt into `@Focus` codegen later for hot paths *with the same API* (reflective deep path ≈ 262 ns/op; the codegen lens it desugars to ≈ 45 ns/op). HKJ's Focus DSL is **codegen-only**: without the annotation processor wired, there is no `Focus.of(Class)` to call.

**Recommendation.** Treat the *goal* — fast first success — as the takeaway, but reach it the HKJ way rather than by adding a reflective core. Concretely: make the `hkj-gradle-plugin` / `hkj-maven-plugin` auto-wire the processor in one line, and ship a copy-pasteable quickstart that produces a working `FocusPath` in under a minute. Only consider a **clearly-labelled** reflective `Focus.of(Class)` fallback (for prototyping / config-driven paths) if onboarding data shows the build-wiring step is a real drop-off point.

**Justification.** This is the one place telescope's *defaults* beat HKJ on ergonomics. But a reflective default would tax HKJ's correctness/type-safety values (runtime field-name failures, the `fieldByName`-style escape). So the right move is to keep codegen-first and remove the setup tax — not to import reflection-first semantics. (§3 lays out this verification/reflection axis in full.)

**Effort & risk.** Low–medium (mostly packaging/docs). Flagged as a **decision** for the maintainer, with a recommended lean toward "frictionless codegen, not reflection."

---

## 5. Part B — Where Higher-Kinded-J is already stronger

These are HKJ's advantages on the same three values. They should be protected; none of Part A is worth trading for them.

### B1. Cardinality-precise path types (type safety)
HKJ encodes focus cardinality in the **type**: `FocusPath<S,A>` (exactly one — `get` is total, returns `A`), `AffinePath<S,A>` (zero-or-one — `getOptional` returns `Optional<A>`), `TraversalPath<S,A>` (zero-or-more — `getAll` returns `List<A>`). Composition narrows precisely (`Lens∘Prism = Affine`). Telescope collapses **all** of these into one `Telescope<S,A>`; cardinality is a *runtime/semantic* property, so nothing stops a caller invoking a single-value `read` on a path that focuses zero or many elements — the type cannot forbid it. HKJ makes the illegal call **fail to compile**. This is a real, structural type-safety win, and it is core to HKJ's identity.

### B2. Open `modifyF` over *any* `Applicative` (correctness + extensibility)
HKJ's optics modify through `modifyF(Function<A, Kind<F,A>>, S, Applicative<F>)` for an **arbitrary** `F`. The proof that this openness pays off is in-tree: the `optics/fetch` package implements **Haxl-style request batching** by supplying a custom `FetchApplicative`, batching all leaf fetches of a traversal into one round-trip. Telescope hard-codes exactly four effect terminals (`CompletableFuture`/`Optional`/`Either`/`Validated`) via internal witnesses — a closed set. HKJ is open where telescope is closed; users can drop in effects telescope's authors never anticipated.

### B3. The effect–optics bridge and a vastly broader lawful surface
HKJ's `.focus()` bridge unifies optics with the full Effect Path API, and behind it sits an effect system telescope has no analogue for: monads, monad transformers (`EitherT`/`MaybeT`/`StateT`/`ReaderT`/`WriterT`/`OptionalT`), `Free`/`FreeAp`/`Coyoneda`, effect handlers, `Reader`/`Writer`/`State`, `IO`, `VTask`/`VStream`, resilience. Telescope is optics + mapping; HKJ is a different order of ambition, and the *integration* between the two domains is the thing neither MapStruct nor telescope can touch.

### B4. Reusable, property-based law-test infrastructure (correctness)
Telescope pins its optic laws in a single `OpticLawsTest`. HKJ's correctness net is wider and reusable: `@TestFactory`-driven `LensLawsTestFactory`, `PrismLawsTestFactory`, `AffineLawsTestFactory`, plus typeclass factories (`Functor`/`Applicative`/`Monad`/`MonadError`/`Traverse`/`Monoid`/`Alternative`/`Selective`) **and** a law test for every effect path (`EitherPathLawsTest`, `ValidationPathLawsTest`, `VTaskPathLawsTest`, … ~20 of them), several property-based via jqwik. New optics/instances inherit coverage by construction. *(Note: this corrects a first-pass impression that HKJ lacked a reusable harness — it has a notably strong one. The recommendation in A1 to law-check generated mappers should plug straight into it.)*

### B5. Codegen-first, reflection-free *by default* (correctness + performance)
HKJ's navigation is compile-time-generated and reflection-free in the hot path by default; telescope's default path is reflective (~100 ns per field hop) with codegen as an opt-in optimisation. HKJ trades a build step for "fast and compile-verified by default" — the right default for a library whose top value is correctness.

### B6. Ecosystem and governance depth
HKJ ships an annotation processor **plus** OpenRewrite recipes, a static checker (`hkj-checker`, e.g. the raw-`Kind` rule), Gradle/Maven plugins, a BOM, and a Spring Boot starter with effect/error-status integration. Telescope has codegen + Spring/Quarkus/Lombok starters. HKJ's migration and enforcement tooling is materially deeper — important for a library asking teams to adopt an FP style.

---

## 6. Part C — Prioritised recommendation summary

| # | Recommendation | Primary value | Effort | Risk | Priority |
|---|---|---|---|---|---|
| A1 | Auto-derived **bidirectional record↔DTO mapper** (compile-time `Iso`, law-checked, effect-aware) | Ergonomics + correctness | M–H | Low–Med | **1 (highest)** |
| A2 | **Sparse-PATCH / multi-edit** combinator (`Focus.all` + `overIfPresent`), with a `ValidationPath`/`EitherPath` variant | Ergonomics | Low | Low | **2** |
| A4 | **Error-message** quality audit (processor + runtime + checker) | Ergonomics | Low | None | **3** |
| A3 | **Opt-in auto-detecting bean** write strategies (generated, reflection-free) | Ergonomics (adoption) | Med | Med | 4 |
| A5 | **Lower time-to-first-success** (auto-wire plugins, quickstart; reflection only if data demands) | Ergonomics | Low–Med | Low | 5 |

---

## 7. Part D — Explicitly *not* recommended (and why)

- **Do not hide HKJ's optic lattice behind JPMS qualified exports.** Telescope's signature move — making `Lens`/`Prism`/`Iso`/`Traversal` untypeable — is right *for telescope's audience* (Java devs who want navigation without learning category theory). It is wrong for HKJ, whose identity and power-user value *is* the composable optic surface and the teaching of it. Adopt the *spirit* instead: keep the Focus DSL crisply positioned as the "front door" (the 90% path) and the raw optics as clearly-labelled "advanced/extension," so newcomers are never forced to learn what a `Prism` is to update a nested field. HKJ largely does this already; the action is documentation/positioning, not encapsulation.
- **Do not adopt reflection-first navigation as the default** (see §3 and A5). It conflicts with correctness/type-safety, and §3 shows HKJ's codegen-only default already occupies the stronger rung on the verification axis. Pursue frictionless codegen instead.
- **Do not let bean mutation leak into the functional core** (see A3). Keep any bean-write support opt-in, generated, and walled off from the pure surface.

---

## 8. Appendix — capability matrix (optics niche only)

| Capability | Higher-Kinded-J | Telescope | Takeaway |
|---|---|---|---|
| Raw optic lattice | Public, composable | Hidden (JPMS) | Different audiences; keep HKJ's exposed |
| Cardinality in the type | **Yes** (Focus/Affine/Traversal) | No (one `Telescope<S,A>`) | HKJ ahead on type safety |
| Navigate records | Codegen (`@GenerateFocus`) | Reflective default + `@Focus` | Telescope lower setup; HKJ faster/safer by default |
| Navigate beans/POJOs | Manual `OpticsSpec` per field | **Auto-detect write strategy** | **Adopt (opt-in) — A3** |
| Record↔DTO bidirectional mapping | **Absent** | `Telescope.mapper(...)` forward/backward/patch + container lift | **Adopt — A1 (top)** |
| Multi-source merge | Absent | `Telescope.merge(...)` (forward-only) | Optional add-on to A1 |
| Sparse PATCH / multi-edit | `Lens.paired` only | `Telescope.all(overIfPresent…)` | **Adopt — A2** |
| Effectful update | Open `modifyF` over **any** `Applicative` + Effect Path bridge | Fixed 4: async/optional/either/validated | HKJ ahead (open vs closed) |
| Error accumulation | `ValidationPath` (+ any accumulating `Applicative`) | `updateValidated` (fixed) | HKJ more general |
| Optic-law tests | Reusable `@TestFactory` harness + jqwik, optics **and** typeclasses **and** every effect path | Single `OpticLawsTest` | HKJ ahead on correctness breadth |
| Lawful-design discipline | Strong (HKJ ethos) | Strong (the "lattice mandate carve-out") | Kindred spirits — mirror it in A1 |
| Error-message quality | Good; audit recommended | **Exemplary** | **Match — A4** |
| Effects/monads/transformers/Free/handlers | Extensive | None | HKJ in a different category |
| Tooling (checker, OpenRewrite, plugins, BOM, Spring effects) | Extensive | Spring/Quarkus/Lombok starters | HKJ ahead on ecosystem |

---

### One-paragraph verdict

Telescope is a sharply-focused, law-disciplined optics DSL that has solved the *practical* record↔DTO and PATCH ergonomics that HKJ — for all its theoretical depth — has not yet addressed. Borrow its **bidirectional mapper**, its **sparse-PATCH edits**, its **bean ergonomics**, and its **error-message craft** — but build each on HKJ's stronger foundations (compile-time `Iso` derivation, open `Applicative`-driven effects, the Effect Path bridge, and the existing law-test harness), so that what HKJ ships is not a copy but a more correct, more type-safe, and more composable version of the same good ideas. Resist the one telescope choice that would cost HKJ its identity: hiding the optics.
