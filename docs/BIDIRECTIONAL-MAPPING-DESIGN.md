# Design Exploration: Bidirectional, Auto-Derived Record↔DTO Mapping in HKJ

**Status:** design exploration / vision note (no implementation)
**Audience:** HKJ maintainers and contributors
**Thesis in one line:** *A record↔DTO mapping is not a new abstraction to bolt on — it is an **optic**, and the design's job is to derive the **weakest lawful optic the relationship admits**, so that immutability, composability, and strong typing fall out of the lattice HKJ already owns.*

> This note builds on `docs/TELESCOPE-COMPARATIVE-ANALYSIS.md` (recommendation **A1**). It answers three questions: how bidirectional mapping fits HKJ's philosophy (immutability, composability, strong type guarantees); what the *next level* looks like beyond telescope/MapStruct; and where **profunctors** genuinely cross over (they do — this is the textbook use case).

All claims about *existing* HKJ types below are grounded in the current source. Proposed API is marked **`SKETCH`** and is illustrative, not final.

---

## 1. The core reframing: a mapping *is* an optic

Telescope models a mapping as a bespoke `Mapper<A,B>` object — an `Iso` wrapper with bolted-on `patch`, four hook fields, and `lift*` helpers. HKJ should not copy that shape. In HKJ the relationship between a domain record and a wire DTO is **already nameable in the optic lattice**, and the *kind* of optic is a precise statement about the relationship's information content:

| Domain ↔ wire relationship | Render (domain→wire) | Parse (wire→domain) | HKJ optic | Law that guarantees correctness |
|---|---|---|---|---|
| Lossless bijection (rename/reshape only) | total | total | **`Iso<Domain,Dto>`** | round-trip: `parse(render(d)) == d` |
| Dto is a **lossy projection** of domain | total (project) | needs a base domain (patch) | **`Lens<Domain,Dto>`** | GetPut / PutGet / PutPut |
| Wire→domain is a **validated parse** ("parse, don't validate") | total (build) | fallible / accumulating | **`Prism`-shaped**, with `match` generalised from `Optional` to `Validated<E,_>` | partial round-trip: `match(build(a)) == Valid(a)` |
| Sum / ADT case (sealed domain ↔ tagged DTO) | inject | match one case | **`Prism<Dto,Domain>`** | partial round-trip |

Three of these four are optics HKJ **already has** (`Iso`, `Lens`, `Prism`, each van Laarhoven-encoded with `modifyF`). The fourth — a prism whose `match` accumulates errors instead of returning `Optional` — is a principled *generalisation* of the existing `Prism`, not a new family. So the feature is mostly **derivation + classification**, not new runtime machinery.

The immediate payoffs of this reframing:

- **`patch` is not a feature — it is `Lens.set`.** Telescope's sparse overlay `mapper.patch(base, partialDto)` is exactly `lens.set(partialDto, base)`, which HKJ's `Lens` already provides with the law `set(get(s), s) == s` guaranteeing it round-trips. We get telescope's headline `patch` for free, *and* it is law-checked.
- **Container lifting is `Traversal` / `Each` composition**, which HKJ already has (`Traversals.forList`, `optionalSome`, `mapValues`). No `liftList`/`liftSet`/`liftOptional`/`liftMapValues` zoo to maintain.
- **Nested mapping is optic composition** (`Iso.andThen`, `Lens.andThen`). A `Mapping<Address,AddressDto>` composes into a `Mapping<User,UserDto>` for the `address` field by `andThen` — recursive derivation is just composition.

---

## 2. Fit with immutability

This is the cleanest of the three fits, because the optic lattice is already pure-by-construction:

- **No mutation, ever.** `Iso.reverseGet`, `Lens.set`, and `Prism.build` rebuild via the record's canonical constructor (HKJ's optics are reflection-free, generated). A "backward" or a "patch" produces a *new* record; the input is untouched.
- **Structural sharing.** Composed lenses rebuild only the spine they touch (`self.set(other.set(...), source)` in `Lens.andThen`), sharing untouched subtrees — the same property HKJ's deep updates already enjoy.
- **The lossy case stays honest about mutation-free reconstruction.** A `Lens<Domain,Dto>` *requires a base domain value* to "go backward" precisely because the dropped fields cannot be conjured. That is the immutable, total-function truth of the situation, encoded in the type — versus telescope's `Mapper.backward(dto)`, which must fabricate defaults for dropped fields (an unsound total inverse). HKJ refuses to offer that; see §4.
- **Beans remain the opt-in exception.** Mutable JavaBean support (analysis A3) stays generated, reflection-free, and walled off from this pure surface — it never becomes the default for the mapping feature.

> Philosophy check: nothing here asks HKJ to relax immutability. The mapping feature is *more* immutable-faithful than telescope, because the type of the derived optic tells you exactly when reconstruction needs prior state.

---

## 3. Fit with composability

Because a derived mapping is an ordinary optic (or, in the fallible case, an ordinary function into an Effect Path), **every combinator HKJ already ships applies with zero new surface area.**

**3a. Composes with the Focus DSL.** A `Mapping<User,UserDto>` that is an `Iso`/`Lens` drops straight into a `FocusPath` chain — telescope's `asTelescope()`, but lawful and statically typed:

```java
// SKETCH — composing a derived mapping into a focus path
FocusPath.of(orderUserLens)        // User inside an Order
    .via(userMapping.asLens())     // Lens<User,UserDto>  (derived)
    .via(UserDtoFocus.email())     // continue navigating the DTO
    .modify(String::toLowerCase, order);
```

**3b. Composes with the Effect-Path railway.** This is the ergonomic jackpot. A *fallible* mapping is, by construction, a function `Dto -> Validated<E, Domain>`, which is exactly what `Path.validated(...)` / `ValidationPath.via(...)` consume. The mapping is not a side utility — it is a **station on the railway**:

```java
// SKETCH — a controller boundary, mapping + validation + persistence + HTTP error in one railway
public EitherPath<ApiError, OrderView> create(CreateOrderRequest req) {
    return Path.validated(orderMapping.parse(req))      // Dto -> Validated<NonEmptyList<FieldError>, Order>
        .mapError(ApiError::validation)                 // accumulate -> one 422 body with every bad field
        .toEitherPath()
        .via(order -> Path.tryOf(() -> repo.save(order)).toEitherPath(ApiError::persistence))
        .map(orderMapping::render);                     // Order -> OrderView (total)
}
```

`hkj-spring` then maps `ApiError` to an HTTP status with the field details already attached — the mapper, the validator, the persistence effect, and the error-to-status strategy *all compose in one expression*. MapStruct (throws) and telescope (`updateValidated` is a separate method, not the derivation) cannot express this.

**3c. Composes recursively and over containers** via `andThen`, `Each`, and `Traversal` — already covered in §1.

**3d. Composes as *evidence* (mapping-as-instance).** A `Mapping<A,B>` can be treated like a resolvable instance: "*given* a `Mapping<Address,AddressDto>`, *derive* a `Mapping<User,UserDto>`." This is implicit/given-style resolution implemented by the processor, and it is literally optic composition under the hood. It gives the feature a clean compositional algebra: `compose`, `invert` (only when `Iso`), `lift` (to container), `dimap` (adapt ends — see §5).

---

## 4. Fit with strong type guarantees

This is where HKJ can decisively out-design both incumbents. Each guarantee is enforced by `javac`, not by a runtime check or a comment.

1. **Compile-time exhaustiveness.** Every target field must be accounted for — auto-matched, renamed, computed, or explicitly dropped — or the mapping *does not compile*. Telescope validates at mapper *construction* (startup); HKJ validates at `javac` (build). Same principle as `@Bridge`, applied to the whole field set.
2. **No fuzzy matching.** Same-named, same-typed → auto. Same-named, *different* type with no in-scope `Mapping`/codec → **compile error**, never a silent cast. Name mismatch → **compile error** unless an explicit rename row is supplied. (Telescope's "no fuzzy matching" rule, promoted from runtime to compile time.)
3. **Lossy mappings cannot fake a total inverse.** If the DTO drops a domain field, the relationship is a `Lens`, and the generated API offers `render` + `patch(base, dto)` — **not** a total `parse : Dto -> Domain`. To obtain a total parse you must supply defaults for the dropped fields, which *promotes the relationship back to an `Iso`*. The unsound "reconstruct from nothing" telescope permits is simply untypeable.
4. **Fallibility infects the type.** If *any* field's parse returns `Validated`/`Either`/an effect, the generated `parse` return type becomes `Validated<NonEmptyList<FieldError>, Domain>` (or the relevant Effect Path). You **cannot** call a pure `parse` on a mapping that can fail — the compiler forces you to handle the failure, and the accumulation collects *every* bad field, not just the first.
5. **Field-path-tagged errors, derived not hand-written.** Because each field correspondence is an optic, a failure in `address.zip` can carry the path `"address.zip"` synthesised from the composed optic (HKJ's indexed optics — `IndexedLens`, `Pair` — already carry labels). Error messages get their location for free; no stringly-typed field names in validators.
6. **Generated law tests.** The processor emits a jqwik law test per derived mapping (round-trip for `Iso`, GetPut/PutGet/PutPut for `Lens`, partial round-trip for the validated prism), plugged into the existing `LensLawsTestFactory` / `PrismLawsTestFactory` harness. **Every derived mapping is mechanically proven lawful** — a guarantee telescope pins for its lattice but not for user mappings.

> The headline: *the shape of your field correspondences determines the shape — and the guarantees — of the generated mapper, checked at compile time.* Lossless → `Iso` with a round-trip test. Lossy → `Lens` you can only patch. Fallible → a `parse` you must error-handle, with all errors accumulated and located.

---

## 5. The profunctor crossover — the heart of the "next level"

Short answer to "is there crossover with Profunctor?": **yes, and it is not incidental — the bidirectional mapper is the canonical reason profunctor optics exist.** Here is the precise, honest picture.

### 5a. What HKJ already has (closer than you'd expect)

- **`Optic<S,T,A,B>`** — HKJ's base optic is the **type-changing, four-parameter** shape and its own javadoc says it is *"an abstract representation of an optic using the Profunctor representation."* It already exposes `dimap`, `contramap`, and `map` alongside the van Laarhoven `modifyF`.
- **`Profunctor<P>`** typeclass with `dimap`/`lmap`/`rmap` and documented laws; its javadoc names *"optics, parsers, serialisers, and other **bidirectional transformations**"* as the motivation.
- **`FunctionProfunctor`** — the canonical `(->)` profunctor instance.

So HKJ is best described as **van Laarhoven at the engine, with a profunctor face**. That hybrid is the right substrate for this feature.

### 5b. Why bidirectional mapping is *the* profunctor use case

A profunctor-encoded optic is a single value `o : p a b -> p s t` that is **polymorphic in the profunctor `p`**. Instantiating `p` at different profunctors recovers different capabilities *from one declaration*:

| Instantiate `p` at… | …and the optic yields | …which for a mapping is |
|---|---|---|
| `(->)` (`FunctionProfunctor`) | `s -> t` | **render** (forward) |
| `Tagged` (ignores input) | `b -> t` | **build / reverseGet** (the *reverse* direction) |
| `Forget r` (`a -> r`) | `s -> r` | **project / getter / fold** |
| `Star f` (`a -> f b`) | `s -> f t` | **effectful / validated parse** (this *is* `modifyF`) |

The point: **forward, reverse, projection, and validated-parse are the same artifact viewed through different profunctors.** Van Laarhoven recovers `get`/`modify`/`modifyF` from *functor* choices (`Const`/`Identity`/general `Applicative`) — which HKJ does — but it treats the **build/reverse direction as a separate method** (`Iso.reverseGet`, `Prism.build`). For a feature whose entire premise is that *both directions are first-class*, the profunctor encoding is the more unified home, because it derives the reverse direction (`Tagged`) on the same footing as the forward one.

### 5c. `dimap` solves telescope's hook problem — lawfully, and enforced by types

Telescope adds `beforeForward`/`afterForward`/`beforeBackward`/`afterBackward` as four bolted-on `Function`/`BiFunction` fields, with a long comment (the *"lattice mandate carve-out"*) explaining why a hook with no inverse must **not** be wrapped as a fake `Iso` — doing so would silently violate the round-trip law.

HKJ does not need bolted-on hooks — end adaptation already lives in the lattice — but **which** lattice operation you reach for matters, because the raw profunctor adapters expose an encoding seam (§5f). Two cases:

- **Invertible adaptation → `andThen(Iso)`, not raw `dimap`.** `Iso.andThen(Iso)` already composes an invertible adapter onto either end *and narrows back to `Iso`* (confirmed in `Iso.java`). This is the right tool for "normalise/canonicalise through an invertible codec": lawful, and you stay in the friendly two-param type with `get`/`reverseGet` intact. No fake `Iso` is possible, because both legs are genuine `Iso`s.
- **One-way (non-invertible) adaptation → a *narrowing* `rmap`/`lmap`, not raw `dimap`.** A post-map with no inverse must cost you the inverse — but it should not cost you `get` as well. The raw `Optic.dimap`/`contramap`/`map` drop all the way to the bare four-param `Optic` (no `get`, no `reverseGet` — §5f), which is more degradation than the situation warrants. The design fix is *narrowing* overloads that degrade by exactly **one rung** to a still-useful named type — e.g. `Iso.rmap(Function<A,B>) : Getter<S,B>` (read-only, but keeps `get`), `Lens.lmap(Iso<C,S>) : Lens<C,A>`. The type still degrades exactly when invertibility is lost — telescope's carve-out enforced by *type*, not by comment — but it lands somewhere ergonomic.

> Design action: add *narrowing* `rmap`/`lmap` (and an invertible `dimap(Iso, Iso)`) on the concrete optics so end-adaptation stays in named types; leave the base `Optic.dimap`/`contramap`/`map` as the off-diagonal escape hatch. Do **not** override them on `Iso`/`Prism` to *re-widen* into a fake `Iso`. See §5f for why the seam exists and stays hidden by default.

### 5d. The accumulating-validation parse ships **today**, on existing machinery

The most ergonomically valuable piece needs **no** profunctor strength classes. "Parse every field, accumulate all errors" is precisely `modifyF` over the **`Validated` Applicative**, which HKJ has (`ValidatedMonad.map2`, `Validated.ap(_, Semigroup)`, `ValidatedTraverse`, `ValidationPath.zipWithAccum`). Profunctorially that is `Star (Validated e)`; in HKJ terms it is the existing van Laarhoven `modifyF` with the accumulating applicative passed in. So:

- **Tier-3 accumulating parse is buildable now** on `Iso`/`Lens`/`Prism` + `Validated` + a `Semigroup<NonEmptyList<FieldError>>`.
- The profunctor name (`Star`) is the *explanation*, not a prerequisite.

### 5e. The honest gap for *full* profunctor encoding

To re-encode optics as `forall p. C p => p a b -> p s t` with the classic strength hierarchy, HKJ would need types it **does not** have today: profunctor **`Strong`** (`first'`/`second'` — the categorical reason a lens carries the un-focused fields), profunctor **`Choice`** (the prism's case handling — note HKJ's existing `Choice<L,R>` is an unrelated sum type used by `Selective`, *not* the profunctor class), **`Wander`/`Traversing`** (traversals), and the `Star`/`Forget`/`Tagged` instances. That is a real project with non-trivial Java ergonomics (no higher-rank types; you simulate `forall p` with a generic method taking a `Profunctor`/`Strong` witness).

**Recommendation:** do **not** re-encode the whole optic library. Instead:

1. **Ship the mapper on the van Laarhoven optics + the existing `Profunctor` face** (Tiers 0–3 below). This delivers the entire ergonomic story with today's primitives.
2. **If** HKJ later wants the ultimate unification, introduce a *focused* profunctor-encoded `Mapping` substrate as a bounded context (the mapping subsystem only), reusing `Profunctor`/`Kind2` and adding `Strong`/`Choice`/`Tagged`/`Star` *there*, with bridges that emit ordinary `Iso`/`Lens` so it still composes with the Focus DSL. The mapping subsystem is the ideal sandbox because its whole reason for being is first-class bidirectionality.

### 5f. Encoding seams: where van Laarhoven meets the profunctor face — and how they stay hidden

A hybrid encoding can leak. Here is exactly where the two representations meet, what the seam costs, and why the Mapping feature never pays it.

**The seam, precisely.** HKJ's concrete optics are the type-*preserving diagonal*: `Iso<S,A>`, `Lens<S,A>`, `Prism<S,A>`, `Affine<S,A>`, `Traversal<S,A>` all `extends Optic<S,S,A,A>`, and the directional methods (`get`/`set`/`reverseGet`/`build`) live on those subtypes. The profunctor adapters — `contramap`, `map`, `dimap` — are declared **only on the base `Optic<S,T,A,B>`** and return the **bare four-param `Optic`**. No concrete optic overrides them to narrow. So invoking one on an `Iso`/`Lens`:

1. **Falls off the diagonal.** `contramap`/`map`/`dimap` move `S`, `T`, `A`, or `B` independently, yielding e.g. `Optic<C,S,A,A>` whose first two parameters differ — structurally *not* a two-param `Iso<,>`/`Lens<,>` (those force `S = T`). The named types do not exist off the diagonal; this is structural, not a missing override.
2. **Loses the directional methods.** The base `Optic` surface is *only* `modifyF`, `andThen`, `contramap`, `map`, `dimap` — no `get`, `set`, `reverseGet`, or `build`. After a raw `dimap` you hold a `modifyF` and must re-derive reads via `Const`/`Identity` by hand.
3. **Offers no ramp back.** `asLens`/`asFold`/`reverse` live on `Iso`, not on the base `Optic`. Once at `Optic<C,U,A,B>` the library gives no route back to a named type.

**Why it's hidden for this feature (and the Focus DSL).** Both audiences that matter — the codegen `@DeriveMapping`/`Codec` user and the ordinary optic composer — stay on the diagonal end to end:

| Surface | Operations used | Touches the profunctor face? |
|---|---|---|
| `Mapping` API | `render` / `parse` / `patch` / `asIso` / `asLens` | no |
| Composition | the narrowing `andThen` overloads (`Iso.andThen(Iso)=Iso`, …) | no |
| Reads / writes | `get` / `set` / `reverseGet` / `modify` on the subtype | no |
| Invertible end-adaptation | `andThen(Iso)` (§5c) | no |
| One-way end-adaptation | the narrowing `rmap` / `lmap` (§5c) — *the only contact point* | lands in a named type, not bare `Optic` |

The profunctor adapters sit on a base interface these users never downcast to. They are the **off-diagonal escape hatch** for genuinely type-changing transforms — power-user territory, not the feature's surface.

**The one place the dual encoding is unavoidably visible** is using the `Profunctor` *typeclass* directly (`FunctionProfunctor.INSTANCE.dimap(...)` over `Kind2<…>`), which drags in the `Kind2`/`Witness`/`widen`/`narrow` ceremony — the binary-arity sibling of the `Kind`/`Applicative` ceremony `modifyF` already has. That surface is for **instance authors**, not Mapping users or optic composers; neither audience ever types `Kind2`.

**Correctness footnote.** The degradation-to-bare-`Optic` is the *safeguard*, not a defect: a `dimap`-ped `Iso` is not typed as an `Iso`, so it cannot be misused as one (there is no `reverseGet` to call). The narrowing `rmap`/`lmap` overloads recover the *ergonomics* (land in `Getter`/`Lens`) without weakening the *guarantee* (you still lose the inverse exactly when invertibility is lost).

---

## 6. The ergonomic surface — "very strong fit"

Codegen-first (per §3 of the comparative analysis: HKJ's correctness/perf default), mirroring `@ImportOptics`/`OpticsSpec`. The design principle that makes it sing: **the *type* of each field correspondence selects the semantics and infects the generated API.**

### 6a. The happy path is empty

```java
// SKETCH
@DeriveMapping
public interface UserMapping extends Mapping<User, UserDto> {}
// Same-named, same-typed fields auto-match. Nested records recurse (needs an in-scope
// Mapping<Address,AddressDto> — itself @DeriveMapping or hand-written). List/Optional/Map lift.
// Lossless + total both ways  =>  generated UserMapping exposes:
//     UserDto render(User u);        // total
//     User    parse(UserDto d);      // total  (ONLY because lossless)
//     Iso<User,UserDto> asIso();     // composes with the Focus DSL
//   + a generated jqwik round-trip law test.
```

### 6b. The leaf abstraction is a reusable, typed `Codec` — and a `Codec` *is* an optic

For any field whose two sides differ, the user supplies a `Codec` (a typed, testable, reusable conversion). Its method signatures — not a string DSL — declare the semantics:

```java
// SKETCH — a leaf codec: parse is fallible (Validated), render is total.
public final class EmailCodec implements Codec<String, EmailAddress> {
    public Validated<FieldError, EmailAddress> parse(String wire) { return EmailAddress.parse(wire); }
    public String render(EmailAddress domain) { return domain.value(); }
}
//   total parse + total render  == an Iso<String,EmailAddress>
//   fallible parse + total render == a validated Prism (a "smart constructor")
```

```java
// SKETCH — wiring codecs + renames; names compile-VERIFIED by the processor (not runtime).
@DeriveMapping(
    renames = { @Rename(domain = "name", wire = "fullName") },
    via     = { @Via(field = "email", codec = EmailCodec.class) },
    drops   = { @Drop(field = "auditTrail", parseDefault = AuditTrail.Empty.class) }
)
public interface UserMapping extends Mapping<User, UserDto> {}
// EmailCodec.parse returns Validated  =>  fallibility infects the whole mapping:
//     UserDto              render(User u);                                   // total
//     Validated<NonEmptyList<FieldError>, User> parse(UserDto d);            // accumulating, path-tagged
//   No pure `User parse(UserDto)` is generated — the compiler forces error handling.
```

Why this is a strong fit:
- **Progressive disclosure.** You write *only* the fields that differ; the way you write them (the codec's return type) picks the semantics. A rename is one annotation row; a validated field is a tiny codec class.
- **Codecs are compositional and testable.** A `Codec<A,B>` is just the user-facing name for "the optic relating two leaf types," reusable across mappings and unit-testable in isolation. Mapping derivation = **optic composition over per-field codecs** — the same `andThen` story, all the way down.
- **The generated API tells the truth.** `asIso()` only exists when lossless; `parse` is `Validated` exactly when something can fail; lossy mappings expose `patch`, not a fake total inverse.
- **It lands on the railway** (§3b) and into `hkj-spring` error mapping with no glue.

### 6c. A value-level escape hatch (secondary)

For dynamic/config-driven cases, a runtime builder (`Mapping.of(Domain.class, Dto.class, rename(...), via(...))`) mirrors telescope's `Telescope.mapper`. Per §3 of the analysis, this is the *secondary* path (reflective, runtime-verified); codegen is primary.

---

## 7. A tiered roadmap (what "next level" looks like, concretely)

| Tier | Capability | Built on | New machinery needed | Value |
|---|---|---|---|---|
| **0 — parity** | same-name auto-match, renames, container lift, bidirectional for lossless | `Iso` + `Each`/`Traversal` + codegen | structural derivation processor | matches telescope/MapStruct |
| **1 — lattice-native** | classify relationship → emit the *weakest lawful optic* (`Iso`/`Lens`/`Prism`); `patch` = `Lens.set`; nested = `andThen` | existing optics | the classifier | correctness + composability, *no bespoke `Mapper`* |
| **2 — strong guarantees** | compile-time exhaustiveness; lossy can't fake total parse; generated law tests | `*LawsTestFactory` | exhaustiveness checks (extend `hkj-checker`) | the type-safety headline |
| **3 — railway / effects** | fallibility infects `parse` type; accumulating, path-tagged validation; drops into Effect Paths + `hkj-spring` | `Validated` + `ValidationPath` + `modifyF` | `NonEmptyList<FieldError>` + path synthesis | the ergonomic jackpot — *ships on today's primitives* |
| **4 — profunctor unification** *(optional, visionary)* | one profunctor-polymorphic `Mapping` value yields render/build/project/validate by profunctor choice; lawful `dimap` end-adaptation | `Profunctor` + `Kind2` | `Strong`/`Choice`/`Wander` + `Star`/`Forget`/`Tagged`, scoped to the mapping subsystem | maximal elegance; the categorical capstone |

Tiers 0–3 are the recommended build and deliver the full user-visible story. Tier 4 is the research-grade capstone — pursue only if the elegance is judged worth the Java-generics cost, and only as a bounded context with bridges back to van Laarhoven.

---

## 8. Honest gaps & small supporting additions

- **`NonEmptyList<E>`** does not exist in HKJ (accumulation is via a user `Semigroup<E>`). A small `NonEmptyList` (or a `FieldErrors` carrier with a `Semigroup` instance) would let an `Invalid` *guarantee ≥1 error at the type level*, strengthening Tier-3's guarantee. Low effort, broadly useful beyond mapping.
- **Iso container-lift helpers** (`Iso<List<A>,List<B>>` etc.) are absent; the design routes container lifting through `Traversal`/`Each` instead, which is arguably cleaner — but a couple of `Iso.liftList/liftOptional/liftMapValues` conveniences would smooth leaf-codec reuse. Optional.
- **Profunctor strength classes** (`Strong`, profunctor-`Choice`, `Wander`) and instances (`Star`, `Forget`, `Tagged`) are absent — needed only for Tier 4. Do not add unless/until Tier 4 is greenlit.
- **Field-path synthesis** for error messages leans on indexed optics (`IndexedLens`, `Pair`) that exist but are not auto-generated by the focus/lens codegen. Tier 3 would extend codegen to thread a label per hop.

---

## 9. What the next level looks like — summary

1. **Mapping is an optic, not a `Mapper`.** Derive the *weakest lawful optic the relationship admits* (`Iso` ⊃ `Lens` ⊃ validated-`Prism`). `patch`, container-lift, nested, and compose-with-Focus all fall out of the lattice.
2. **The type encodes the relationship's truth.** Lossless → total bidirectional `Iso` with a round-trip law test. Lossy → a `Lens` you can only `patch`. Fallible → a `parse` you *must* error-handle, accumulating every bad field with its path. All `javac`-enforced.
3. **It lives on the railway.** A fallible mapping *is* a `ValidationPath` station; mapping + validation + persistence + HTTP-error compose in one expression with `hkj-spring`. This is the "very strong ergonomic fit."
4. **Profunctors are the categorical foundation, and HKJ is already half-way there.** `dimap` gives lawful, type-degrading end-adaptation (telescope's hooks, done right); the accumulating parse is `modifyF` over `Validated` (profunctorially `Star`). Full profunctor encoding (Tier 4) is a reachable capstone, scoped to the mapping subsystem, not a library-wide rewrite.

### One-paragraph verdict

Telescope proves the *demand* for bidirectional record↔DTO mapping; HKJ can answer it at a level neither telescope nor MapStruct reaches — not by shipping a `Mapper` class, but by recognising that the feature already lives in the optic lattice. Derive the weakest lawful optic, let immutability and composition come from the lattice, let the type system enforce exhaustiveness and forbid unsound inverses, let fallibility infect the `parse` type and accumulate onto the Effect-Path railway, and let `dimap` host end-adaptation lawfully. Profunctors are not a tangent here — they are the reason the whole thing coheres, and HKJ's `Optic` already wears the profunctor face. Build Tiers 0–3 on the van Laarhoven engine HKJ has today; hold Tier 4 as the categorical capstone for when the elegance is worth the generics.
