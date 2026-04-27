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
| `Setter<S, A>` | exactly one | no | yes |

---

## Method support

A `✓` means the optic usefully supports the operation. A blank cell means the operation is unavailable or has no observable effect (for example, `Fold` technically inherits `modifyF` for compositional reasons but cannot reconstruct the source structure, so it is treated as unavailable here). A note in the cell points at the utility class where a related operation lives.

| Method | Lens | Iso | Prism | Affine | Traversal | Fold | Getter | Setter |
|---|---|---|---|---|---|---|---|---|
| `get(S) → A` | ✓ | ✓ |   |   |   |   | ✓ |   |
| `getOptional(S) → Optional<A>` | ✓ | ✓ | ✓ | ✓ | via [`Traversals`](traversals.md) | via `preview` | ✓ |   |
| `getAll(S) → List<A>` | ✓ | ✓ |   |   | via [`Traversals`](traversals.md) | ✓ | ✓ |   |
| `preview(S) → Optional<A>` |   |   |   |   |   | ✓ |   |   |
| `matches(S) → boolean` |   |   | ✓ | ✓ |   |   |   |   |
| `set(A, S) → S` | ✓ | ✓ | ✓ | ✓ | via [`Traversals`](traversals.md) |   |   | ✓ |
| `modify(f, S) → S` | ✓ | ✓ | ✓ | ✓ | via [`Traversals`](traversals.md) |   |   | ✓ |
| `modifyF(f, S, App) → Kind<F, S>` | ✓ | ✓ | ✓ | ✓ | ✓ |   |   | ✓ |
| `build(A) → S` |   | ✓ (reverseGet) | ✓ |   |   |   |   |   |
| `foldMap(monoid, f, S) → M` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |   |
| `exists(predicate, S) → boolean` |   |   |   |   |   | ✓ |   |   |
| `all(predicate, S) → boolean` |   |   |   |   |   | ✓ |   |   |
| `find(predicate, S) → Optional<A>` |   |   |   |   |   | ✓ |   |   |
| `isEmpty(S) → boolean` |   |   |   |   |   | ✓ |   |   |
| `length(S) → int` |   |   |   |   |   | ✓ |   |   |

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
| `Traversals.forMap(key)` | Traversal focused on the value at `key` in a `Map` |
| `Traversals.forMapValues()` | Traversal over every value in a `Map` |
| `Traversals.forOptional()` | Traversal that focuses zero or one elements of an `Optional` |
| `Traversals.forArray()` | Traversal over the elements of an array |

Stay in the static-method utility for one-off bulk operations; reach for the [Fluent API](fluent_api.md) when you want method-chaining on a builder.

---

## Conversions and composition

All optic types expose `andThen(other)` for composition; the result type follows the rules in [Composition Rules](composition_rules.md). The conversion methods between optic types are catalogued in [Conversions](conversions.md).

---

**Previous:** [Reference](ch7_intro.md)
**Next:** [Conversions](conversions.md)
