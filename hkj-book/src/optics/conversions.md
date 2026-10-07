# Conversions

_The methods that turn one optic type into another, and what each conversion gives up._

There are two ways an optic changes type in Higher-Kinded-J:

1. You **explicitly convert** it by calling a method like `asTraversal()` or `reverse()`.
2. You **compose** it with another optic via `andThen`, and the result type is dictated by the [Composition Rules](composition_rules.md).

This page covers (1). For (2), see the rules table.

---

## Explicit conversion methods

| Method | On | Returns | Use when |
|---|---|---|---|
| `lens.asTraversal()` | `Lens<S, A>` | `Traversal<S, A>` | You need to call a traversal-only API (`Traversals.modify`, traversal `andThen`) on a lens. |
| `lens.asFold()` | `Lens<S, A>` | `Fold<S, A>` | You want to express read-only intent or use fold-only operations like `exists`, `find`, `all`. |
| `prism.asTraversal()` | `Prism<S, A>` | `Traversal<S, A>` | Same as for lenses: to call a `Traversal`-only utility such as `Traversals.modify`. |
| `prism.asFold()` | `Prism<S, A>` | `Fold<S, A>` | Read-only query of a sum-type variant. |
| `affine.asTraversal()` | `Affine<S, A>` | `Traversal<S, A>` | To call a `Traversal`-only utility such as `Traversals.modify`. |
| `affine.asFold()` | `Affine<S, A>` | `Fold<S, A>` | Read-only access to the optional field. |
| `iso.asLens()` | `Iso<S, A>` | `Lens<S, A>` | When you only need the forward direction; the `reverseGet` capability is dropped. |
| `iso.asTraversal()` | `Iso<S, A>` | `Traversal<S, A>` | Same lifting as for a lens. |
| `iso.asFold()` | `Iso<S, A>` | `Fold<S, A>` | Read-only access; `reverseGet` is dropped. |
| `iso.reverse()` | `Iso<S, A>` | `Iso<A, S>` | Swaps the direction of the iso. The new optic's `get` is the old `reverseGet` and vice versa. |
| `getter.asFold()` | `Getter<S, A>` | `Fold<S, A>` | Lifting a getter into a context that expects a fold. |
| `setter.asTraversal()` | `Setter<S, A>` | `Traversal<S, A>` | Lifting a setter for use in traversal-shaped pipelines. |
| `traversal.asFold()` | `Traversal<S, A>` | `Fold<S, A>` | Discarding write capability to express read-only intent or to call fold-only operations. |

You cannot widen the other direction: a `Traversal` does not become a `Lens`, because it has no guarantee of focusing on exactly one element. The nearest thing is [`Traversals.partsOf`](traversals.md), which gives you a `Lens<S, List<A>>` onto the focused elements as a list: a lens onto *all* of them, not onto *one* of them, which is precisely the guarantee a `Traversal` cannot make.

---

## Implicit lifting during composition

`andThen` infers the result type automatically. Any two of `Iso`, `Lens`, `Prism`, `Affine` and `Traversal` compose directly, with no `asTraversal()` first; the [composition rules table](composition_rules.md#composition-rules-table) gives the result for each pair.

<!-- verify -->
```java
Lens<User, List<Order>> ordersLens = UserLenses.orders();
Traversal<List<Order>, Order> listTraversal = Traversals.forList();

// No conversion required; the result is a Traversal because the rules say so.
Traversal<User, Order> userOrders = ordersLens.andThen(listTraversal);
```

You only need an explicit `asTraversal()` when the API you are calling requires a `Traversal` parameter and you have a `Lens` value not in a composition context, for example when storing the optic in a `Traversal`-typed field.

---

## When to convert versus when to compose

| Situation | Use |
|---|---|
| Storing an optic in a typed field | Explicit conversion (`static final Traversal<...> X = lens.asTraversal();`) |
| Calling a static utility (`Traversals.modify`, `Fold` query helpers) | Explicit conversion |
| Chaining optics together | Composition with `andThen`, no explicit conversion needed |
| Switching between read-only and read-write intent | Explicit `asFold()` |
| Reversing the direction of an `Iso` | `reverse()` |

---

~~~admonish tip title="See Also"
- [Composition Rules](composition_rules.md): the rules table for what type results from `andThen`
- [Optic Capabilities](optic_capabilities.md): which methods are available on each optic
- [Decision Trees](decision_trees.md): choosing the optic before you convert it
~~~

---

**Previous:** [Optic Capabilities](optic_capabilities.md)
**Next:** [Composition Rules](composition_rules.md)
