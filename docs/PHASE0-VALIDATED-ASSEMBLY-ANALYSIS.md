# Phase 0 Analysis: Open-Arity Validated Assembly (the consumer layer for `NonEmptyList` / `Ior`)

> ⚠️ **Superseded for the corrected state by `FINAL-DESIGN-AND-ROADMAP.md`.** Key correction: `Tuple3..Tuple12` already ship, so curried-`ap` is argued on merits, not a tuple gap. Retained as provenance/detail.

**Status:** design analysis (no implementation)
**Roadmap context:** Phase 0 of the re-prioritised program in `TELESCOPE-COMPARATIVE-ANALYSIS.md` / `BIDIRECTIONAL-MAPPING-DESIGN.md` §10d — the foundation that gates **A2** (sparse-PATCH / accumulating multi-edit) and **A1** (the bidirectional mapper's validated `parse`).
**Grounded in two open maintainer issues:**
- **[#549 — `NonEmptyList`](https://github.com/higher-kinded-j/higher-kinded-j/issues/549)** — the non-empty error *carrier*. Already fully specified there; **this analysis does not re-derive it**, it assumes it lands as specced.
- **[#551 — `Ior`/`These`](https://github.com/higher-kinded-j/higher-kinded-j/issues/551)** — "success with warnings". Turns out to be the mapper's **tolerant-parse** mode; folded in below.

> One line: **#549 gives the carrier, #551 gives the warnings mode — this analysis supplies the missing *assembly* (combine N validated fields into a record) and the optic/mapper *consumer*, which neither issue covers.**

Claims about *existing* HKJ types are grounded in source; proposed API is marked **`SKETCH`**.

---

## 1. What HKJ already has (and the precise gap)

Accumulating validation is **not** missing — it is present but in a shape that does not scale to record assembly:

| Capability | Where | Shape |
|---|---|---|
| Pairwise accumulate | `Validated.ap(Validated<E,Fn>, Semigroup<E>)`; `Invalid.ap` → `semigroup.combine(e1,e2)` | curried apply, **Semigroup passed every call** |
| Pairwise accumulate (Path) | `ValidationPath.zipWithAccum(Accumulating<E,B>, BiFunction)` | switch over `Invalid`/`Valid`, `semigroup.combine(e1,e2)` |
| N-ary accumulate (Path) | `ValidationPath.zipWith3Accum(…)` and siblings | **implemented by `Tuple2`-nesting** + `Function3` (`this.zipWithAccum(second, Tuple2::new)…`) |
| Fixed-arity assembly | `Applicative.map2`…`map5` | caps at **5**; `Kind<F,_>` args (HKT ceremony) |

Two structural facts decide the design:

- **`FunctionN` exists up to `Function12`, and `Tuple3..Tuple12` already ship** (`Tuple.java`, generated via `@GenerateForComprehensions`) *(corrected per review — the earlier "only `Tuple2`" claim was false; see `FINAL-DESIGN-AND-ROADMAP.md` §3)*. So *both* a curried-`ap` builder and a tuple-growing builder are viable; the choice is on ergonomic merits, not a tuple gap.
- **`NonEmptyList` / `Ior` are greenfield** (no partial work in tree) — so the carrier and the warnings mode arrive clean, exactly as #549/#551 describe.

**The gap, precisely.** Assembling a record from *N* validated fields today means either (a) the `zipWithNAccum` family — a **proliferating, low-capped, `Tuple2`-nesting** set of methods that lives on `ValidationPath`, not `Validated`; (b) chained `ap` with the **`Semigroup` threaded** at every step; or (c) `map2..map5`, capped at 5 and in full `Kind` ceremony. **None** carries a per-field **label** (so errors can't say `"address.zip"`), and **none** is available as one ergonomic call on `Validated` itself. That is exactly what A1's `parse` and A2's accumulating multi-edit need.

---

## 2. The carrier — defer entirely to #549

#549 specifies `NonEmptyList` comprehensively: `record NonEmptyList<A>(A head, List<A> tail)`, total `head()`, `Functor`/`Applicative`/`Monad`/`Foldable`/`Traverse`/`Semigroup` (deliberately **no `Monoid`** — the absence of an empty NEL is the point), `Kind` plumbing, `Semigroups.nonEmptyList()`, a NEL-default `ValidationPath` entry (`Path.validNel` / singleton `Path.invalid(error)`), `fromList → Maybe`, `Iterable`, Jackson for `hkj-spring`. **This analysis assumes all of that lands and builds on it.**

The one consequence worth stating for *this* layer: a **NEL-default accumulating applicative** (carrier fixed to `NonEmptyList<E>`, semigroup = concatenation, supplied once) **removes the per-call `Semigroup` threading** that the current `ap`/`zipWithAccum` API forces. That alone fixes rough-edge **E3**. The assembly builder below is defined against that fixed-carrier applicative, so no `Semigroup` argument appears at any call site.

---

## 3. The assembly (E1) — the core of this analysis

**Goal:** combine *N* heterogeneous `Validated<NEL<FieldError>, Xᵢ>` into a `Validated<NEL<FieldError>, R>`, accumulating *all* field errors, with each error carrying its field path, in one readable expression — and reuse the *same* shape for `Ior` (tolerant) and for the generated mapper.

### Candidates

| # | Approach | Verdict |
|---|---|---|
| **A** | Extend the `mapN` / `zipWithNAccum` family (add `map6…`, `zipWith6Accum…`) | **Reject as primary.** Proliferation; still capped; full `Kind` ceremony or Path-only; no labels. A stopgap, not the shape. |
| **B** | A fluent **accumulating builder on curried `ap`** | **Recommend (hand-written).** Open to the `Function12` ceiling with *no* new tuples; NEL-default (no `Semigroup` arg); per-slot label sugar; one shape parameterised over the accumulating applicative (`Validated` **or** `Ior`). |
| **C** | `traverse` / `sequence` over a `List<Validated<…,X>>` | **Complementary.** The right tool for **collection-valued** fields (`List<Dto> → Validated<NEL, List<Domain>>`), not heterogeneous record construction. |
| **D** | **Codegen-emitted** assembly inside `@DeriveMapping` | **Recommend (mapper).** The processor knows the N fields + canonical constructor, so it emits the exact accumulation — **arity-unbounded**, labels auto from field names. |

**Why curried-`ap` (B) is still recommended over tuple-growing (A):** on *merits*, not on a tuple gap (`Tuple3..Tuple12` already exist). The curried-`ap` builder holds a `Validated<NEL, FunctionK<…>>` and each `.and(vNext)` applies `ap` — one shape, parameterisable over the accumulating applicative (`Validated`/`ValidationPath`/`Ior`), and it drops the per-call `Semigroup` via the NEL default. A `tupled()` surface remains available if wanted. (Also: `OpticOps.modifyAllAccumulating` is existing accumulating-modify prior art over a *single* traversal; this builder's novelty is *heterogeneous, multi-arity* assembly. Pin **left-to-right** accumulation order — NEL concat is non-commutative.)

### Recommended surface

```java
// SKETCH — hand-written assembly; NEL-default carrier, no Semigroup argument, field labels per slot.
Validated<NonEmptyList<FieldError>, User> user =
    Validated.accumulate()                              // fixed NEL carrier (issue #549)
        .field("name",  parseName(dto.name()))          // Validated<NEL<FieldError>, Name>
        .field("email", parseEmail(dto.email()))        // Validated<NEL<FieldError>, Email>
        .field("age",   parseAge(dto.age()))            // Validated<NEL<FieldError>, Age>
        .apply(User::new);                              // Function3<Name,Email,Age,User> — called only if all valid
//  All three invalid  ⇒  Invalid(NEL[ name: …, email: …, age: … ])  — every error, each path-tagged.
```

- `accumulate()` returns a builder over the NEL-default accumulating applicative.
- `.field(label, Validated<…,X>)` bumps the curried arity *and* tags this slot's errors with `label` (the §4 path step). A label-free `.and(Validated<…,X>)` overload exists for leaves that already carry their own paths.
- `.apply(FunctionN ctor)` is overloaded `apply(Function2)…apply(Function12)`, matching the accumulated arity (Java picks the right one by the ctor's shape). Records with >12 fields nest (assemble a sub-record first) — vanishingly rare.
- **Consolidation:** this single builder subsumes the `zipWith3Accum`/`zipWith4Accum`/… family. Those can remain (back-compat) but the builder becomes the documented front door.

### The same builder serves `Ior` (#551)

`Ior`'s `Both`-accumulation is the *same* "accumulate-on-the-left via `Semigroup`" mechanism `Validated` uses (the issue says so explicitly). So the builder is parameterised by the accumulating applicative:

```java
// SKETCH — tolerant assembly: problems become warnings, the value is still produced.
Ior<NonEmptyList<Warning>, Config> cfg =
    Ior.accumulate()
        .field("port",    parsePortLenient(raw.port()))     // Ior<NEL<Warning>, Port> — Right or Both
        .field("timeout", parseTimeoutLenient(raw.timeout()))
        .apply(Config::new);   // Both(warnings, config) if any slot warned; Right(config) if all clean
```

One builder shape, two carriers (`Validated` strict, `Ior` tolerant) — exactly the unification #551 asks for ("don't introduce a second, divergent way to accumulate").

---

## 4. Field-path labels (E7) — leaf-level, not builder-level

The cleanest place to attach `"address.zip"` is the **leaf**, not the combinator:

- A leaf parse produces `Validated<NEL<FieldError>, X>`; the `FieldError` carries a **path** that the leaf sets (`Email.parse(wire).mapError(e -> e.at("email"))`). Accumulation then just **concatenates NELs** — labels already attached, paths compose as you nest (`.at("address").at("zip")`).
- **In the mapper (codegen, D):** the processor *knows* the field name, so it wraps each leaf's errors with `.at(fieldName)` **automatically** — E7 is solved for free by codegen.
- **In hand-written assembly (B):** the `.field(label, v)` sugar tags the slot, so the path is one token at the call site.

This keeps the builder oblivious to labels (it just accumulates), and makes paths a property of the *error type* + the *leaf*, which composes correctly through nesting and through `andThen`.

---

## 5. The consumer — the validated prism (B2), strict and tolerant

The boundary primitive the mapper needs (analysis §10 B2) is a prism whose match is fallible-with-accumulation rather than `Optional`:

```java
// SKETCH — strict: parse may fail, build always succeeds ("parse, don't validate").
interface Refraction<S, A> {                 // a Prism generalised: Optional match -> Validated match
    Validated<NonEmptyList<FieldError>, A> parse(S source);   // wire -> domain (accumulating)
    S build(A value);                                         // domain -> wire (total)
    // modifyF over the NEL-accumulating Applicative gives composition; andThen composes refractions.
}

// SKETCH — tolerant variant (ties to #551): success may carry warnings.
interface TolerantRefraction<S, A> {
    Ior<NonEmptyList<Warning>, A> parse(S source);            // Right(a) or Both(warnings, a)
    S build(A value);
}
```

- `parse` is exactly what `Validated.accumulate()…apply(ctor)` (§3) produces — the assembly builder *is* the body of a generated `parse`.
- `build` is total (a valid domain always renders), so render stays a plain function / `Getter`.
- Composition: a `Refraction<Wire, Domain>` `andThen` a field `Refraction` accumulates via the same applicative; the `modifyF` route plugs straight into the optic lattice (it is `modifyF` over the NEL applicative — profunctorially `Star (Validated NEL)`, per the mapping design §5d).

This is the one genuinely *new* optic-shaped primitive; everything else is assembly + carrier.

---

## 6. Proving slice — end-to-end, both modes

The smallest thing that proves the headline ("accumulate every field error, each located") and exercises NEL + builder + refraction together:

```java
// SKETCH — strict parse of a 6-field record; one bad field per line, all reported at once.
record RegisterDto(String name, String email, String age, String country, String phone, String zip) {}
record User(Name name, Email email, Age age, Country country, Phone phone, Zip zip) {}

Validated<NonEmptyList<FieldError>, User> result =
    Validated.accumulate()
        .field("name",    Name.parse(dto.name()))
        .field("email",   Email.parse(dto.email()))
        .field("age",     Age.parse(dto.age()))
        .field("country", Country.parse(dto.country()))
        .field("phone",   Phone.parse(dto.phone()))
        .field("zip",     Zip.parse(dto.zip()))
        .apply(User::new);              // Function6 — within the Function12 ceiling

// Invalid(NEL[ "email: not an address", "age: not a number", "zip: bad format" ])
//   → in hkj-spring, one 422 body listing all three with their paths.
```

A tolerant twin using `Ior.accumulate()…apply(User::new)` returns `Both(NEL[warnings], user)` — the value still flows. The slice needs only: NEL (#549), the `accumulate()` builder (§3), and leaf `parse` smart-constructors (`Name.parse`, …). No processor, no `@DeriveMapping` — it validates the **foundation** before the mapper is built on it.

---

## 7. How this unblocks the roadmap

- **A1 (mapper `parse`)** — the generated `parse` body *is* `accumulate()…apply(canonicalCtor)` with labels auto-tagged from field names (D). The validated refraction (§5) is its boundary primitive.
- **A2 (accumulating multi-edit)** — `Focus.all(...)` with a `ValidationPath` result accumulates each edit's failure through the *same* builder/applicative; per-field paths come from the optics.
- **Library-wide** — the `accumulate()` builder + NEL default make *every* multi-field validation (Spring request bodies, config parsing, domain invariants) a flat, labelled, all-errors-at-once expression — value independent of whether the mapper ever ships.

---

## 8. Open decisions for maintainers

1. **Builder vs `TupleN`.** Recommend the **curried-`ap` builder** (no new tuples; reaches `Function12`). Adding `Tuple3..TupleN` is a *separate* call, justified only if a `tupled()` API is wanted elsewhere — not required here.
2. **One shape, three carriers.** Put `accumulate()` on `Validated`, `ValidationPath`, **and** `Ior` (#551), sharing one builder parameterised by the accumulating applicative — honouring #551's "don't introduce a second way to accumulate".
3. **Relegate the `zipWithNAccum` family?** Keep for back-compat, but document the builder as the front door; consider soft-deprecating `zipWith4Accum`+ to avoid the proliferation.
4. **`FieldError` shape.** Needs a composable **path** (`at(step)`), a message, and a `Semigroup` via NEL. Small new type; coordinate with #549's error-channel story and `hkj-spring`'s error→HTTP mapping.
5. **Issue coverage.** The **assembly builder + validated refraction are not captured by #549 or #551** — they are the *consumer* layer that makes both pay off for optics/mapping. Worth a companion issue ("open-arity accumulating assembly + validated prism"), explicitly dependent on #549 and cross-linked to #551.

---

## 9. Relationship to the existing issues — summary

| Layer | Owner | Status |
|---|---|---|
| Non-empty error **carrier** (`NonEmptyList`) | **#549** | specified; assumed as-is here |
| **Warnings** mode (`Ior`/`These`, value-plus-warnings) | **#551** | specified; folded in as the mapper's *tolerant* parse |
| Open-arity **assembly** (`accumulate()` builder, labels) | *this analysis* | **gap — propose as companion issue** |
| Validated **prism / refraction** (optic consumer) | *this analysis* / mapping design §5, §10-B2 | **gap — part of A1's foundation** |

The foundation issues point the right direction; what they don't yet cover is the *assembly* that turns "a non-empty list of errors" and "a warnings channel" into "construct this record from these N fields, reporting every problem with its path." That assembly — small, additive, built on `ap`/`zipWithAccum` that already exist — is the highest-leverage next build, because A1, A2, and all hand-written validation consume it.
