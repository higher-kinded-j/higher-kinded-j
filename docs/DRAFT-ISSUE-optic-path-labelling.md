# Draft GitHub Issue — `FieldError` + Optic-Path Labelling

> **Working draft for review.** Everything under **“Issue body — copy from here”** is the postable content and follows the `#549`/`#551` template. The **Reviewer notes** above it are *not* part of the issue. Third in the dependent set (**#549 → assembly builder → refraction → labelling**); derived from `docs/PHASE0-FIELD-PATH-LABELLING-ANALYSIS.md`.

**Suggested title:** `[FEAT] Add FieldError + optic-path labelling (located, composable validation errors)`
**Suggested label:** `enhancement`

### Reviewer notes (decisions you may want to change)

- **Threading model.** I recommend a **hybrid**: optic/codegen path flows *top-down* (location-as-index, reusing the `IndexedOptic` spirit); hand assembly uses a *call-site label* (`.field("x", v)`). Alternative: pick one model for both. See “Things to explore”.
- **Reuse vs new.** I propose **reusing `IndexedOptic`’s** index-threading (specialised to a flat `Path`) rather than a parallel mechanism — but with a *path-concatenating* compose instead of the `Pair<I,J>`-nesting `iandThen`. If you’d rather a standalone lightweight `Located` wrapper, that’s a clean swap.
- **`PathSegment` shape.** I include `Field`/`Index`/`Key`/`Case`. Drop `Case` if prism/refraction labelling is deferred.
- **`render()` format.** `"address.items[3].zip"` is a guess — confirm the separator/array syntax (it becomes the `hkj-spring` 422 field key).
- **Scope of codegen change.** I scoped it to *emit the field name as a segment* (the processor already reads it). If you’d rather generate full indexed optics, that’s larger.

---

## Issue body — copy from here

## The gap

HKJ accumulates validation errors (`Validated`, `ValidationPath`, and — with #549 — `NonEmptyList`), but the errors are **anonymous**: a failure is a message with no record of *where in the structure* it occurred. A request DTO with three bad fields yields three messages and no `"address.zip"` to attach them to — the consumer threads location by hand.

The pieces to fix this already exist, unconnected:

- **Location already flows through optic composition** — `IndexedOptic<I, S, A>` threads an index into modification (`imodifyF(BiFunction<I, A, …>)`), and `iandThen` composes indices (`→ Pair<I, J>`). But it nests as `Pair<I,J>`, isn’t a flat renderable path, and isn’t wired to errors.
- **The codegen already knows every field’s name** — `LensProcessor` reads `component.getSimpleName()` and uses it as the generated method’s name. The label is in hand; it just isn’t emitted as data on the optic.
- **There is no `FieldError`** — no type pairs a message with a path.

So the “located, all-at-once” validation errors that the assembly builder (companion issue) and the refraction optic (companion issue) both promise have nothing to stand on. This issue supplies the one shared model.

## What “good” looks like (user perspective)

Every failure carries its location, composes through nesting, and renders for humans — **automatically** when generated:

```java
Validated<NonEmptyList<FieldError>, User> r =
    Validated.accumulate()
        .field("name",    Name.parse(""))                       // FieldError(name, "blank")
        .field("email",   Email.parse("nope"))                  // FieldError(email, "not an address")
        .field("address", addressRefraction.parse(dto.address())) // nested -> address.zip, address.city
        .apply(User::new);

// Invalid(NEL[ "name: blank",
//             "email: not an address",
//             "address.zip: not 5 digits",
//             "address.city: blank" ])
```

```java
// Rendered by hkj-spring into one 422 body keyed by path:
// { "errors": { "name": "blank", "email": "not an address",
//               "address.zip": "not 5 digits", "address.city": "blank" } }
```

- **Located by default** — each `FieldError` carries a `Path`; nested errors get the full path for free.
- **Auto-sourced** — record-field segments come from the codegen (the name it already holds); list/map segments from the existing indexed index; no hand-labelling in generated code.
- **No HKT, no path argument in leaf validators** — `Email.parse(String)` stays a plain smart constructor; it does not take a path.

## Things to explore

- **The model.** A sealed `PathSegment` (`Field(name)` · `Index(i)` · `Key(k)` · `Case(tag)`), a `Path` (segment list) that is a **`Monoid`** (identity = root, op = concat, plus `render()`), and a `FieldError(Path, message)` that rides the #549 NEL channel.
- **Threading — top-down vs bottom-up.** (A) location flows *into* the leaf as an index (the `IndexedOptic` model; leaf tags its own error); (B) leaf emits a path-less error and each layer prepends a segment (`FieldError.under(seg)`). **Recommend hybrid:** optic/codegen → (A) with a *path-concatenating* compose (flat `Path`, not `Pair<I,J>` nesting); hand assembly `.field(label,v)` → (B-flavoured) call-site segment. They meet in `FieldError`.
- **Reuse `IndexedOptic`.** Specialise its index to `Path` rather than adding a parallel mechanism; a `reindex : Pair<Path,Path> → Path` bridges the existing `iandThen` if reused verbatim. (Out of scope: refactoring the whole indexed family — see Scope.)
- **Codegen emission.** `@GenerateLenses`/`@GenerateFocus` emit the `componentName` (already read) as a `Field` segment so generated paths self-locate; `@DeriveMapping` derives segments from components automatically.
- **`render()` format.** Confirm `"a.b[3].c"` syntax — it becomes the `hkj-spring` 422 field key.
- **Laws & tests.** `Path` is a lawful `Monoid` (reuse `MonoidLawsTestFactory`); composition tracks optics (a composed optic’s path = concat of parts); refraction round-trip produces no errors.
- **`hkj-spring` integration.** Render `path.render()` as the error-body key; coordinate with the existing `ValidationPath` return-value handling.

## Ergonomic constraints

- **No HKT tax** — `Path`/`FieldError` are plain records; `Kind` never surfaces.
- **Leaf validators stay plain** — no path parameter threaded into hand-written smart constructors.
- **Auto-located when generated** — record-field and list/map segments are sourced automatically; hand-labelling is only for value-level `.field(...)`.
- **Composes for free** — nested errors inherit the full path through optic composition / builder nesting.
- **Human-renderable** — `render()` yields a stable `"address.zip"` key for APIs and logs.
- **Backward compatible** — purely additive; `Validated`/`ValidationPath`/`IndexedOptic`/the codegen all keep working; located errors are opt-in via the new carrier.

## Dependencies & relationship to other issues

- **Depends on #549 (`NonEmptyList`)** — `FieldError` is what the NEL error channel carries.
- **Underpins the assembly builder** (companion) — supplies `.field(label, v)`’s located errors.
- **Underpins the refraction optic** (companion) — supplies the located `parse` failures and their path composition.
- **Feeds the bidirectional mapper** — `@DeriveMapping` auto-tags from component names; one 422 lists every bad field by full path. (Mapper codegen is a separate feature.)

## Scope

- **In:** `PathSegment` / `Path` (Monoid + `render()`) / `FieldError`; a path-concatenating compose for located optics (reusing `IndexedOptic`’s index-threading); codegen emission of the field-name segment; `hkj-spring` rendering of `path.render()`.
- **Out (defer):** refactoring the generic `IndexedOptic` family to `Path`; `Tuple3..TupleN`; `@DeriveMapping` annotation-processor generation (the mapper feature, which *consumes* this).
