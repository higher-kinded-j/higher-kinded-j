# Polymorphic Optics: Type-Changing Updates

## *A small escape hatch for when the type change is the operation*

~~~admonish info title="What You'll Learn"
- Why the everyday optics in higher-kinded-j are deliberately monomorphic
- When to reach for the polymorphic surface in `org.higherkindedj.optics.poly`
- Building polymorphic lenses and isos with `PolyOptics.polyLens` and `PolyOptics.polyIso`
- The runners: `modifyF`, `modify`, `set`, and `get`
- Turning a `Functor` or `Traverse` instance into a polymorphic optic with `Optics.mapped` and `Optics.traversed`
- Composing a monomorphic head with a polymorphic leaf via `Optic#andThen`
- When to prefer the profunctor adapters (`contramap`, `map`, `dimap`) instead
~~~

~~~admonish title="Hands On Practice"
[Tutorial21_PolymorphicOptics.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial21_PolymorphicOptics.java)
~~~

~~~admonish title="Example Code"
[PolyOpticsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/poly/PolyOpticsExample.java)
~~~

---

## Why monomorphic by default

The base [`Optic`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/optics/Optic.java) interface in higher-kinded-j already carries four type parameters:

```java
public interface Optic<S, T, A, B> { ... }
```

Every shipped optic ([`Lens`](lenses.md), [`Prism`](prisms.md), [`Iso`](iso.md), [`Affine`](affine.md), [`Traversal`](traversals.md), [`Setter`](setters.md)) deliberately fixes `S = T` and `A = B`:

```java
public interface Lens<S, A> extends Optic<S, S, A, A> { ... }
```

That choice keeps the public surface short and predictable. `Lens<User, String>` reads at a glance; `Lens<User, User, String, String>` does not. Java has no type alias, so paying for four parameters in every signature is a real cost on every line of every API.

The vast majority of "I want to change types" needs in Java are not a call for polymorphic optics at all. They are a call for the [profunctor adapters](profunctor_optics.md), which let you reuse a monomorphic optic with a different source or target type.

~~~admonish tip title="Reach for the adapters first"
If you are bridging an external DTO to your internal record, a wrapper type to a raw value, or a V1 schema to a V2 schema, the right tool is `lens.contramap(...)`, `lens.map(...)`, or `lens.dimap(...)`. See [Profunctor Optics](profunctor_optics.md) and [Profunctor Optics: Recipes](profunctor_optics_recipes.md).
~~~

---

## When polymorphic optics are the right tool

There is one scenario where the type change is the operation, not an adapter around it: working with a generic container or wrapper, where modifying the inside changes the outside.

```java
record Box<A>(A value) {}

// We want a single optic that turns a Box<String> into a Box<Integer>.
// Profunctor adapters cannot express this, because the result type is Box<Integer>,
// not Box<String> with a different view.
```

Higher-kinded-j keeps this advanced surface separate, in the package `org.higherkindedj.optics.poly`, with two small classes:

| Class | Purpose |
|-------|---------|
| `PolyOptics` | Factories (`polyLens`, `polyIso`) and runners (`modifyF`, `modify`, `set`, `get`) over the raw `Optic` interface |
| `Optics` | Typeclass-driven factories: `mapped(Functor)` for a polymorphic Setter, `traversed(Traverse)` for a polymorphic Traversal |

---

## Building polymorphic lenses and isos

### `PolyOptics.polyLens`

A polymorphic lens needs a getter and a type-changing setter. The setter receives the original whole alongside the new part so other fields can be preserved:

```java
record Tagged<A>(String tag, A value) {}

Optic<Tagged<String>, Tagged<Integer>, String, Integer> value =
    PolyOptics.polyLens(Tagged::value, (t, n) -> new Tagged<>(t.tag(), n));

Tagged<Integer> result =
    PolyOptics.modify(value, Integer::parseInt, new Tagged<>("answer", "42"));
// result == new Tagged<>("answer", 42)
```

### `PolyOptics.polyIso`

An iso is a lossless two-way conversion. Because the wrapper has no other fields, the reverse direction does not need the original source:

```java
record UserId<A>(A raw) {}

Optic<UserId<String>, UserId<Integer>, String, Integer> raw =
    PolyOptics.polyIso(UserId::raw, UserId::new);

UserId<Integer> numeric = PolyOptics.modify(raw, Integer::parseInt, new UserId<>("123"));
// numeric == new UserId<>(123)
```

---

## The runners

The runners are static helpers in `PolyOptics` that hide the `Const` and `Identity` applicative tricks otherwise needed to use a polymorphic optic:

| Runner | Purpose |
|--------|---------|
| `modifyF(optic, f, source, applicative)` | Effectful modification under any `Applicative` |
| `modify(optic, f, source)` | Pure modification (Identity applicative) |
| `set(optic, value, source)` | Replace the focused part with a new value |
| `get(optic, source)` | Extract the focused part, lens-like only |

`get` only works for lens-shaped polymorphic optics (those built from `polyLens`, `polyIso`, and their compositions). If you point it at a prism / traversal-shaped optic, an `UnsupportedOperationException` points you back at `modifyF` with an appropriate applicative.

---

## Typeclass-driven polymorphic optics

The `Optics` class turns an HKJ typeclass instance into a polymorphic optic over its contents. This is the one place polymorphism gives us something the monomorphic surface cannot: composing a leaf step that <em>changes element type</em>.

### `Optics.mapped(Functor)`

`mapped` is a polymorphic <b>Setter</b>. It supports pure type-changing modification under the Identity applicative (via `PolyOptics.modify` and `PolyOptics.set`):

```java
Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> elems =
    Optics.mapped(ListMonad.INSTANCE);

Kind<ListKind.Witness, Integer> ints =
    PolyOptics.modify(elems, Integer::parseInt, LIST.widen(List.of("1", "2", "3")));
// ints narrows to List.of(1, 2, 3)
```

If you call `modifyF` with a non-Identity applicative on a `mapped(Functor)` optic, it raises a clear error pointing at `traversed(Traverse)` instead.

### `Optics.traversed(Traverse)`

`traversed` is a polymorphic <b>Traversal</b>. It runs under any `Applicative`, so you can short-circuit on `Either`, accumulate errors with `Validated`, run async, and so on:

```java
Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
    Optics.traversed(ListTraverse.INSTANCE);

ValidatedMonad<List<String>> validatedApp = ValidatedMonad.instance(Semigroups.list());

Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseValidated =
    s -> {
      try { return VALIDATED.widen(Validated.valid(Integer.parseInt(s))); }
      catch (NumberFormatException e) {
        return VALIDATED.widen(Validated.invalid(List.of("bad: " + s)));
      }
    };

Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> result =
    PolyOptics.modifyF(trav, parseValidated,
        LIST.widen(List.of("1", "x", "2", "y")), validatedApp);

// VALIDATED.narrow(result).getError() == List.of("bad: x", "bad: y")
```

### Choosing between `mapped` and `traversed`

| Situation | Use |
|-----------|-----|
| Pure type-changing map | `Optics.mapped(Functor)` |
| Effectful traversal under any Applicative | `Optics.traversed(Traverse)` |
| Need to accumulate errors with Validated | `Optics.traversed(Traverse)` |
| Need to short-circuit on Either / Try | `Optics.traversed(Traverse)` |

---

## Composing monomorphic heads with polymorphic leaves

The whole point of a separate polymorphic surface is that it composes with the existing monomorphic optics via `Optic#andThen`. A typical shape is "a polymorphic lens onto a collection field, then a polymorphic traversal over the elements":

```java
record Page<A>(int number, List<A> rows) {}

Optic<Page<String>, Page<Integer>, List<String>, List<Integer>> rowsLens =
    PolyOptics.polyLens(Page::rows, (p, rs) -> new Page<>(p.number(), rs));

// Bridge plain List <-> Kind<ListKind.Witness, ?>.
Optic<List<String>, List<Integer>,
      Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>>
    listAsKind = PolyOptics.polyIso(LIST::widen, LIST::narrow);

Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer>
    allRows = Optics.traversed(ListTraverse.INSTANCE);

Optic<Page<String>, Page<Integer>, String, Integer> pageRows =
    rowsLens.andThen(listAsKind).andThen(allRows);

Page<Integer> result =
    PolyOptics.modify(pageRows, Integer::parseInt, new Page<>(1, List.of("1", "2", "3")));
// result == new Page<>(1, List.of(1, 2, 3))
```

The same composition under `Validated` accumulates errors across the rows; under `Either` it short-circuits on the first failure.

---

## When NOT to use polymorphic optics

Most "I want to change types" needs are <em>not</em> a call for polymorphic optics. Reach for the simpler tools first.

| Need | Tool |
|------|------|
| Reuse an optic with a different source or target type | `lens.contramap`, `lens.map`, `lens.dimap` (see [Profunctor Optics](profunctor_optics.md)) |
| Bridge an external DTO to your internal record | Profunctor `dimap` |
| Map a wrapper to its raw value | A monomorphic [`Iso`](iso.md) |
| Update coupled fields atomically | [`Lens.paired`](coupled_fields.md) |
| Accumulate validation errors across a record | The Effect Path API + a monomorphic traversal |
| Type-changing update of a generic wrapper | `PolyOptics.polyLens` / `polyIso` |
| Type-changing element map over an HKT | `Optics.mapped` / `Optics.traversed` |

---

## Summary

~~~admonish info title="Key Takeaways"
* **Monomorphic by default** — The everyday optics keep two type parameters because four is too noisy in Java without type aliases.
* **Profunctor adapters first** — Most type-change needs are best solved by `dimap` / `map` / `contramap` on the existing optics.
* **`PolyOptics` is an escape hatch** — Reach for it when authoring a generic wrapper whose type change is the operation.
* **Typeclass-driven leaves** — `Optics.mapped` and `Optics.traversed` lift `Functor` and `Traverse` instances into polymorphic optics that compose with monomorphic heads via `Optic#andThen`.
* **`mapped` is a Setter; `traversed` is a Traversal** — Use `mapped` for pure modifications; `traversed` for any applicative.
~~~

~~~admonish info title="Hands-On Learning"
Practice polymorphic optics in [Tutorial 21: Polymorphic Optics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial21_PolymorphicOptics.java) (7 exercises plus a diagnostic, ~12 minutes).
~~~

~~~admonish tip title="See Also"
- [Profunctor Optics](profunctor_optics.md) — The first place to look when you want to reuse an optic with a different type.
- [Profunctor Optics: Recipes](profunctor_optics_recipes.md) — Wrapper types, V1 / V2 migration, and API integration patterns.
- [Composition Rules](composition_rules.md) — How `andThen` chooses the result optic across the family.
- [Lenses](lenses.md), [Isomorphisms](iso.md), [Traversals](traversals.md) — The monomorphic optics that polymorphic leaves compose with.
~~~

---

**Previous:** [Profunctor Optics: Recipes](profunctor_optics_recipes.md)
**Next:** [Java-Friendly APIs](ch4_intro.md)
