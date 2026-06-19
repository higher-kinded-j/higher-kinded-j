# Draft GitHub Issue — `@DeriveMapping` (bidirectional record↔DTO mapper)

> **Working draft for review.** Postable content under **“Issue body — copy from here”**; **Reviewer notes** above are not part of the issue. The marquee feature (telescope analysis **A1**); derived from `BIDIRECTIONAL-MAPPING-DESIGN.md` and `PHASE2-MAPPER-ANALYSIS.md`. Depends on the refraction, assembly-builder, and labelling drafts (+ #549).

**Suggested title:** `[FEAT] Add @DeriveMapping — compile-time bidirectional record↔DTO mapper (lawful, effect-aware)`
**Suggested label:** `enhancement`

### Reviewer notes (decisions you may want to change)

- **Spec surface.** I show an annotation (`@DeriveMapping` + `@Rename`/`@Via`/`@Drop`) plus a `Codec` leaf. Alternatives: an `OpticsSpec`-style interface, or a value-level `Mapping.of(...)` builder. Pick the primary; I recommend codegen-first (consistent with `@GenerateLenses`).
- **`Mapping`/`Codec` naming.** Placeholders. `Codec<A,B>` = a leaf parse/render pair (= an `Iso` or a `Refraction`).
- **Lossy → `Lens`, refuse total inverse.** I propose the type *degrades* (no fake total `parse`) — confirm you want that strictness over telescope’s fabricate-defaults `backward`.
- **Generated law tests.** I propose emitting a law test per mapping into the existing harness — confirm that’s wanted (it’s a strong correctness story but adds generated test sources).
- **Scope split.** Records-only here; beans (A3), merge, third-party are separate drafts.

---

## Issue body — copy from here

## The gap

Entity↔DTO conversion is the most common boilerplate in mainstream Java, and HKJ has no first-class answer. `Iso` is a hand-built primitive (`Iso.of(get, reverseGet)`) and `@GenerateIsos` only *wraps* a user-supplied function pair — neither *derives* a mapping between two record shapes by matching fields, recursing into nested records, and lifting through `List`/`Optional`/`Map`. MapStruct owns this space but is one-directional-per-interface, reflection-free yet not composable with optics/effects; telescope is bidirectional but reflective-by-default and not lawful (it can fabricate a total inverse for a lossy mapping).

HKJ can do better than both, because **a mapping already lives in the optic lattice** (see `BIDIRECTIONAL-MAPPING-DESIGN.md`): derive the *weakest lawful optic the relationship admits* — `Iso` (lossless), `Lens` (lossy projection; `patch` falls out), or a `Refraction` (validated parse) — at **compile time**, **reflection-free**, **law-checked**, and **effect-aware** (a fallible `parse` accumulates every field error, located).

The machinery is largely already in `hkj-processor`: `external/TypeKindAnalyser` (classify record/sealed/enum/bean), `external/ContainerType` (List/Optional/Map lifting), the `Iso`/`Lens` emitters, and `ImportOpticsProcessor` (un-owned types). `@DeriveMapping` is **glue over existing analysers** plus a field-correspondence classifier and a `parse`-body generator.

## What “good” looks like (user perspective)

```java
// Lossless: empty body — same-named/typed fields auto-match, nested records recurse, containers lift.
@DeriveMapping
public interface UserMapping extends Mapping<User, UserDto> {}
// generates:  UserDto render(User);   User parse(UserDto);   Iso<User,UserDto> asIso();  + a round-trip law test
```

```java
// Renames + a validated leaf: the leaf's return type infects the mapping's parse type.
@DeriveMapping(
    renames = { @Rename(domain = "name", wire = "fullName") },
    via     = { @Via(field = "email", codec = EmailCodec.class) })   // EmailCodec.parse -> Validated
public interface UserMapping extends Mapping<User, UserDto> {}
// generates:
//   UserDto render(User);                                          // total
//   Validated<NonEmptyList<FieldError>, User> parse(UserDto);      // accumulating, path-tagged
//   (no pure `User parse(UserDto)` — the compiler forces error handling)
```

- **Empty happy path** — identical fields need no code.
- **The field correspondences select the result type** — lossless → `Iso` + total `parse`; lossy → `Lens` + `patch` (no fabricated inverse); any validated leaf → `parse : … → Validated<NEL<FieldError>, …>`, accumulating and located.
- **It’s a real optic** — `asIso()`/`asLens()` compose with the Focus DSL; a fallible `parse` drops onto the `ValidationPath` railway and into `hkj-spring` (one 422, every bad field by path).
- **Compile-time & reflection-free** — every target field accounted for or it does not compile; no runtime reflection.

## Things to explore

- **Classifier.** Match by name + type via `TypeKindAnalyser`; identity for same name+type; recurse when an in-scope `Mapping`/`Codec`/`Refraction` exists; lift via `ContainerType`; **compile error** on an unmapped target field or a same-name/different-type pair with no codec (exhaustiveness — extend `hkj-checker`).
- **Weakest-lawful-optic emission.** Lossless bijection → `Iso`; lossy projection → `Lens` (expose `render` + `patch`, *not* a fabricated total `parse`); validated leaf → the `Refraction`-backed `parse`.
- **`Codec<A,B>` leaf.** A parse/render pair = an `Iso` (total) or a `Refraction` (fallible). Reusable, testable, composes; the mapping is optic composition over per-field codecs.
- **`parse` body = the assembly builder** over per-field `Refraction`s, located via the labelling issue, auto-tagged from component names.
- **Generated law tests** into `LensLawsTestFactory`/`PrismLawsTestFactory`.
- **`hkj-spring`** — a `Mapping<A,B>` bean registry (mirroring telescope’s starter) + the `parse`→422 path.
- **Reuse, don’t rebuild** — compose `TypeKindAnalyser`/`ContainerType`/`Iso`-`Lens` emitters; this is glue.

## Ergonomic constraints

- **No HKT tax** — `render`/`parse`/`patch`/`asIso` are plain.
- **Codegen-first, reflection-free** — compile-time verified; no runtime reflection (consistent with `@GenerateLenses`, and the §reflection/codegen axis).
- **Truthful types** — `asIso()` only when lossless; `parse` is `Validated` exactly when something can fail; lossy mappings expose `patch`, never a fabricated inverse.
- **Located errors for free** — auto-tagged from record component names.
- **Composes with the rest of HKJ** — Focus DSL, Effect Paths, other mappings (nested), Traversals (collections).
- **Backward compatible** — additive; `Iso`/`Lens`/`@GenerateIsos` untouched.

## Dependencies & relationship to other issues

- **Depends on the refraction optic** (leaf validated parse), the **assembly builder** (the `parse` body), and **labelling/E7** (located errors) — transitively **#549**.
- **The mapper’s `patch` is A2’s `Patch`** (one mechanism); the mapper’s `parse` is the assembly builder + refractions. The mapper adds the *classifier*, the *emit*, and the *generated law test*.
- **Pairs with #551 (`Ior`)** — a tolerant mapping (parse-with-warnings).
- **Follow-ons** (separate drafts): multi-source merge, third-party (un-owned) mapping, JavaBean targets (A3).

## Scope

- **In:** `@DeriveMapping` for **records** (+ sealed via prisms); field-match/recurse/container-lift; weakest-lawful-optic emission (`Iso`/`Lens`/`Refraction`-backed `parse`); `@Rename`/`@Via`/`@Drop` rows; `Codec` leaf; generated law tests; `hkj-spring` `Mapping` registry + `parse`→422.
- **Out (defer / separate):** JavaBean source/target (A3 — fast-follow via existing `CopyStrategy`); multi-source merge; third-party listing; the value-level `Mapping.of(...)` runtime builder (secondary path).
