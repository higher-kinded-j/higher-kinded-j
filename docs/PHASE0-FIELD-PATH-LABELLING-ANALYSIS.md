# Phase 0 Analysis: Field-Path Labelling (`FieldError` location threading) — E7

**Status:** design analysis (no implementation)
**Sits in:** Phase 0 foundations — the last unspecified primitive. Shared dependency of the open-arity assembly builder, the validated refraction optic, and the bidirectional mapper. Companion to `docs/PHASE0-VALIDATED-ASSEMBLY-ANALYSIS.md`, `docs/DRAFT-ISSUE-open-arity-validated-assembly.md`, `docs/DRAFT-ISSUE-validated-refraction-optic.md`.
**One line:** *HKJ already threads a “where” through optic composition (`IndexedOptic`’s `Pair<I,J>` nesting) and the codegen already knows every field’s name — E7 is to specialise that index to a flat `Path` of segments and have the generator emit the names it already holds, turning existing machinery into a located-error system.*

Proposed API is marked **`SKETCH`**.

---

## 1. Why this is the last Phase 0 piece

Both drafted issues lean on a location model they don’t define: the assembly builder’s `.field("name", v)` and the refraction’s “each error path-located (`"address.zip"`)” both assume a `FieldError` that carries a composable path and a way to source the segments. Three downstream features must agree on **one** model or get three incompatible ones:

- **Assembly builder** — `.field(label, v)` sets a segment at the call site.
- **Refraction `parse`** — a leaf failure must know its location; nested parses prepend.
- **`@DeriveMapping`** — must tag errors automatically from record component names.

Specify it once, here.

## 2. What HKJ already has (grounded)

The substrate is real and the headline finding is that *location already flows through optic composition*:

- **`IndexedOptic<I, S, A>`** threads an index into modification: `imodifyF(BiFunction<I, A, Kind<F, A>>, S, app)` (`IndexedOptic.java:59`). The index is exactly a “where”.
- **Composition already accumulates location**: `iandThen(IndexedOptic<J, A, B>) → IndexedOptic<Pair<I, J>, S, B>` (`IndexedOptic.java:111`) — deeper composition nests the indices as `Pair<I, J>`. And `andThen(Optic<A,A,B,B>) → IndexedOptic<I, S, B>` (`:144`) *preserves* the outer index when composing with a plain optic. So a path through a mix of indexed and plain optics already carries its location.
- **`IndexedLens.index() : I`** and `iget(S) : Pair<I, A>` (`IndexedLens.java:54,89`) — an indexed lens carries its single segment.
- **`IndexedFold.ifoldMap(Monoid<M>, BiFunction<I, A, M>, S) : M`** (`IndexedFold.java:70`) — *collecting all located results into a monoid* is already a primitive. Collecting located validation failures is `ifoldMap` into the NEL-error monoid.
- **The codegen already holds the names**: `LensProcessor` reads `componentName = component.getSimpleName().toString()` (`LensProcessor.java:121`) and currently uses it only as the generated method’s identifier (`:129`). The label is right there — it just isn’t emitted as data on the optic.
- **No `FieldError` / error-path type exists** anywhere in core or spring. This is the one genuinely-new type.

**Architect’s takeaway:** E7 is not new machinery. It is (a) a flat `Path` segment model to replace the awkward `Pair<I,J>` nesting *for the error-path use case*, (b) a `FieldError` carrying it, and (c) a one-line codegen change to emit the names already in hand. The generic `IndexedOptic` overhaul is explicitly **out of scope** (§7).

## 3. The model

```java
// SKETCH — a path is a list of typed segments; a small sealed segment type.
sealed interface PathSegment permits Field, Index, Key, Case {
  record Field(String name) implements PathSegment {}   // record component / bean property
  record Index(int i)       implements PathSegment {}    // list / array position
  record Key(Object key)    implements PathSegment {}    // map key
  record Case(String tag)   implements PathSegment {}    // sum-type / prism case
}

// A Path is a Monoid: empty = root, combine = concat. render() => "address.items[3].zip".
record Path(List<PathSegment> segments) {
  static Path root();
  Path at(PathSegment s);            // append one segment (used by codegen / .field)
  Path concat(Path deeper);          // compose (the Semigroup op)
  String render();                   // "address.items[3].zip"
  // Monoid<Path> PATH = ...;         // identity = root, op = concat
}

// The one new error type — a located message. NEL-friendly (pairs with #549).
record FieldError(Path path, String message) {
  static FieldError at(Path p, String msg);
  FieldError under(PathSegment outer); // prepend a segment as the error bubbles up
}
```

- `Path` is a **`Monoid`** — reuse HKJ’s `Monoid`/`Semigroup` surface so `ifoldMap` and the assembly builder combine paths the same way they combine everything else.
- `FieldError` is the carrier the NEL channel (#549) holds: `Validated<NonEmptyList<FieldError>, A>`.

## 4. Where each segment comes from (per optic kind)

| Optic kind | Segment | Source |
|---|---|---|
| Lens / Focus (record field) | `Field(name)` | **codegen** — `componentName`, already read by `LensProcessor` |
| `IndexedTraversal` (list) | `Index(i)` | already carried as the index `I` |
| `Map` traversal | `Key(k)` | already carried as the index `I` |
| Prism / Refraction (sum case) | `Case(tag)` | the prism/refraction case label |
| Iso | *(none)* | transparent — Iso is a pure reshape, contributes no segment |
| Assembly `.field("x", v)` | `Field("x")` | the call-site label |

The first four already exist as `IndexedOptic` indices or as data the processor holds; the model just *names* them as `PathSegment`s.

## 5. How location threads — two models, and the recommendation

There are two ways to get the path onto a `FieldError`:

- **(A) Top-down (index flows in).** The optic hands `(path, value)` to the leaf via `imodifyF`; the leaf tags its `FieldError` with the path it was given. This *is* the `IndexedOptic` model (`I = Path`), reusing existing composition. The leaf knows its location.
- **(B) Bottom-up (`at(step)` prepend).** The leaf emits a path-less error; each composition layer prepends its segment as the error bubbles up (`FieldError.under(segment)`). This is the `FieldError.at(step)` shape the drafts mention; it decouples the leaf from its location but makes every `andThen` remap errors.

**Recommendation — a hybrid, split by surface:**

- **Optic / codegen path → top-down (A).** Thread the path as the index (`I = Path`) using the existing `IndexedOptic` spirit, but with a **path-concatenating compose** (append to a flat `Path`) instead of the generic `iandThen` that nests `Pair<I,J>`. Flat path, no `Pair` unwrapping, location known at the leaf. (A thin `reindex : Pair<Path,Path> → Path` over `iandThen` is the bridge if you prefer to reuse `iandThen` verbatim.)
- **Hand assembly → call-site label (B-flavoured).** `.field("x", v)` supplies the segment; the builder prepends it to any `FieldError`s `v` produced. `.field(label, v)` is sugar over `.and(v)` that wraps the result with `under(Field(label))`.

They **meet in `FieldError`**: whichever surface produced it, the error carries a flat `Path`. `hkj-spring` renders `path.render()` as the 422 body’s field key.

> Why not pure (A) everywhere? Hand-written leaf validators (`Email.parse`) shouldn’t have to accept a path argument — too invasive. Why not pure (B)? Forcing every `andThen` to remap errors is more overhead and easy to forget in custom optics. The split puts each where it’s natural.

## 6. The codegen change (small, because the name is already there)

- **`@GenerateLenses` / `@GenerateFocus`** — emit the `componentName` (already read at `LensProcessor.java:121`) as a `Field(name)` segment on the generated optic, so a generated `FocusPath` self-locates. Either generate a located/indexed variant, or attach the label to the existing optic.
- **`@DeriveMapping`** (mapper, separate feature) — derive each field’s `Field(name)` segment from the record component automatically, so a generated `parse` produces fully-located errors with **zero** hand-labelling. This is the auto-tagging the mapper design and both drafts promise.

## 7. Explicitly out of scope

- **No overhaul of the generic `IndexedOptic` family.** E7 *reuses its spirit* (index = location) and adds a flat `Path` specialisation for errors. Refactoring all indexed optics to `Path` is a separate, larger question and not required.
- **No `Tuple3+`** (the `Pair`-nesting stays for generic indexed composition; the `Path` model sidesteps it for errors).
- **`@DeriveMapping` codegen** itself is the mapper feature, not E7 — E7 only provides the labelling the mapper consumes.

## 8. Integration points (the whole reason E7 exists)

- **Assembly builder** — `.field(label, v)` sets a segment; accumulated `FieldError`s are located.
- **Refraction `parse`** — a leaf parse failure is `Invalid(NEL(FieldError(path, msg)))`; composing refractions concatenates paths.
- **Mapper** — auto-tags from component names; one 422 lists every bad field by its full path.
- **`hkj-spring`** — `path.render()` → the field key in the error body (`{"errors":{"address.zip":"…"}}`).

## 9. Laws & tests

- **`Path` is a lawful `Monoid`** — identity (`root`), associativity of `concat`; reuse `MonoidLawsTestFactory`.
- **Composition tracks optics** — the path of a composed optic equals the concat of the parts’ paths (so a nested error gets its full path for free); a focused property test.
- **Round-trip with the refraction** — `parse(build(a))` is `Valid` and produces no `FieldError`s.

## 10. Worked example (the payoff)

```java
// SKETCH — every failure located, accumulated, rendered.
Validated<NonEmptyList<FieldError>, User> r =
    Validated.accumulate()
        .field("name",    Name.parse(""))            // Invalid -> FieldError(name, "blank")
        .field("email",   Email.parse("nope"))       // Invalid -> FieldError(email, "not an address")
        .field("address", addressRefraction.parse(dto.address())) // nested: address.zip, address.city …
        .apply(User::new);

// Invalid(NEL[
//   "name: blank",
//   "email: not an address",
//   "address.zip: not 5 digits",
//   "address.city: blank" ])
```

## 11. Recommended issue shape

A small standalone issue — *“optic-path labelling / `FieldError`”* — third in the dependent set (**#549 → assembly → refraction → labelling**), drafted in `docs/DRAFT-ISSUE-optic-path-labelling.md`. It depends on #549 (the carrier), underpins the assembly builder and refraction (located errors), and feeds the mapper (auto-tagging). It is deliberately small: one `PathSegment`/`Path`/`FieldError` model, a path-concatenating compose, and a one-field codegen emission — built on machinery HKJ already ships.
