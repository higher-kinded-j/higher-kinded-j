# Optic Capabilities

## _Which operations work on which optic_

~~~admonish info title="What You'll Learn"
- The cardinality of focus for each optic type (exactly one, zero or one, zero or more, none).
- Which read, write, modify, query, and effectful operations each optic supports.
- Which type conversion methods are available on each optic.
- Where collection-shaped operations live (some on optics, some on the `Traversals` and `Fold` utilities).
~~~

This is the lookup table for "can a `Prism` do `getAll`? does a `Getter` have `set`?" The narrative pages explain *why* each optic has the capabilities it does; this page just lists them.

---

## Cardinality at a glance

| Optic | Focus cardinality | Reads? | Writes? |
|---|---|---|---|
| `Lens<S, A>` | exactly one | yes | yes |
| `Iso<S, A>` | exactly one (reversible) | yes | yes |
| `Prism<S, A>` | zero or one | yes | yes (and can construct) |
| `Affine<S, A>` | zero or one | yes | yes |
| `Traversal<S, A>` | zero or more | yes | yes |
| `Fold<S, A>` | zero or more | yes | no |
| `Getter<S, A>` | exactly one | yes | no |
| `Setter<S, A>` | zero or more | no | yes |

---

## Method support

A `✓` means the interface **declares** the method, so you can call it directly on that optic. A blank cell means you cannot: either the operation makes no sense for that optic, or it is reachable only after a conversion. A note in the cell points at the utility class or conversion that gets you there.

That distinction matters, because most optics reach most operations *eventually*. A `Lens` has no `getAll`, but `lens.asFold().getAll(source)` works; a `Traversal` has no `modify`, but `Traversals.modify(traversal, f, source)` does. The table below is about what is on the type; [Conversions](conversions.md) is about how to get from one type to another.

One deliberate exception: `Fold` declares its own read-only `modifyF`, which runs the effects and returns the input unchanged. `Getter` extends `Fold` and inherits exactly that, so both are marked `✗` rather than `✓`. `Setter` inherits `modifyF` as an abstract obligation from `Optic`, so every concrete `Setter` implements it for real.

| Method | Lens | Iso | Prism | Affine | Traversal | Fold | Getter | Setter |
|---|---|---|---|---|---|---|---|---|
| `get(S) → A` | ✓ | ✓ |   |   |   |   | ✓ |   |
| `getOptional(S) → Optional<A>` |   |   | ✓ | ✓ |   | via `preview` | via `preview` |   |
| `getAll(S) → List<A>` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` or [`Traversals`](traversals.md) | ✓ | ✓ |   |
| `preview(S) → Optional<A>` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | ✓ | ✓ |   |
| `matches(S) → boolean` |   |   | ✓ | ✓ |   |   |   |   |
| `set(A, S) → S` | ✓ |   |   | ✓ | via `Traversals.modify(t, a -> v, s)` |   |   | ✓ |
| `modify(f, S) → S` | ✓ |   | ✓ | ✓ | via [`Traversals`](traversals.md) |   |   | ✓ |
| `modifyF(f, S, App) → Kind<F, S>` | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ (see note) | ✗ (see note) | ✓ |
| `build(A) → S` |   | ✓ (`reverseGet`) | ✓ |   |   |   |   |   |
| `foldMap(monoid, f, S) → M` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | ✓ | ✓ |   |
| `exists`, `all`, `find`, `isEmpty`, `length` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | via `asFold()` | ✓ | ✓ |   |

Two rows are worth reading twice. `Iso` carries only `get`, `reverseGet` and `modifyF`: it has no `set` and no `modify` of its own, because `reverseGet` already rebuilds the whole structure from the focus. And `Getter` extends `Fold`, so it inherits the entire query family, `getAll` and `preview` included; every other read-only capability in the `Fold` column applies to `Getter` too.

---

## Collection helpers (`Traversals` utility)

Bulk operations on `Traversal` values typically live on the `Traversals` utility class rather than the `Traversal` interface itself. The same applies to a handful of factory methods.

| Method | Purpose |
|---|---|
| `Traversals.modify(t, f, S)` | Apply `f` to every focused element |
| `Traversals.getAll(t, S)` | Collect every focused element into a `List` |
| `Traversals.filtered(predicate)` | A traversal that focuses only on matching elements |
| `Traversals.forList()` | Standard traversal over `List<A>` elements |
| `Traversals.forSet()` | Standard traversal over `Set<A>` elements |
| `Traversals.forCollection()` | Traversal over `Collection<A>` elements; a set source is rebuilt as a set, anything else as a `List` |
| `Traversals.forMap(key)` | Traversal focused on the value at `key` in a `Map` |
| `Traversals.forMapValues()` | Traversal over every value in a `Map` |
| `Traversals.forOptional()` | Traversal that focuses zero or one elements of an `Optional` |
| `Traversals.forArray()` | Traversal over the elements of an array |

Stay in the static-method utility for one-off bulk operations; reach for the [Fluent API](fluent_api.md) when you want method-chaining on a builder.

---

## Conversions and composition

All optic types expose `andThen(other)` for composition; the result type follows the rules in [Composition Rules](composition_rules.md). The conversion methods between optic types are catalogued in [Conversions](conversions.md).

~~~admonish info title="Key Takeaways"
* **A `✓` means the method is on the type.** A cell naming a conversion means you can still get there, one `asFold()`, `asTraversal()` or `Traversals` call later. An empty cell means the optic genuinely cannot: a `Setter` has no read at all, and `matches` is meaningless on an optic that always hits.
* **`Iso` is smaller than it looks.** `get`, `reverseGet` and `modifyF`: no `set` and no `modify`, because `reverseGet` already rebuilds the whole structure.
* **`Traversal` declares no plain read or write.** It has `modifyF` (plus `filtered`, `filterBy`, `branch` and `modifyWhen`); every `get`, `set` and `modify` goes through `Traversals` or `asFold()`.
* **`Getter` extends `Fold`**, so it inherits the whole query family: `getAll`, `preview`, `exists`, `all`, `find`, `isEmpty` and `length`.
* **`Setter` is zero-or-more and write-only.** It has `set` and `modify` and no way to read at all.
~~~

~~~admonish tip title="See Also"
- [Conversions](conversions.md): how to reach the capabilities a given optic lacks
- [Composition Rules](composition_rules.md): what type results from combining two optics
- [Decision Trees](decision_trees.md): choosing which optic you want in the first place
~~~

---

**Previous:** [Reference](ch7_intro.md)
**Next:** [Conversions](conversions.md)
