# Conversions

## _Moving between optic types_

~~~admonish info title="What You'll Learn"
- The explicit conversion methods on each optic type (`asTraversal`, `asFold`, `asLens`, `reverse`).
- When the compiler implicitly widens an optic type during composition.
- The relationship between this page and the [Composition Rules](composition_rules.md): conversion is what happens to a single optic; composition is what happens when two optics combine.
~~~

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
| `prism.asTraversal()` | `Prism<S, A>` | `Traversal<S, A>` | Same as for lenses, lifting a prism into a traversal-shaped pipeline. |
| `prism.asFold()` | `Prism<S, A>` | `Fold<S, A>` | Read-only query of a sum-type variant. |
| `affine.asTraversal()` | `Affine<S, A>` | `Traversal<S, A>` | Lifting an affine for use in a traversal-shaped pipeline. |
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

`andThen` infers the result type automatically. You do not need to call `asTraversal()` before composing a `Lens` with a `Traversal`; the compiler picks the [Composition Rules](composition_rules.md) result.

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

~~~admonish info title="Key Takeaways"
* **Conversion is one optic changing shape; composition is two optics meeting.** `asTraversal()` is the first, `andThen` is the second, and you rarely need both at once.
* **Widening loses capability.** `asFold()` drops writing, `asLens()` drops `reverseGet`, and nothing converts back up: a `Traversal` cannot become a `Lens`, because it cannot promise exactly one focus.
* **`andThen` already widens for you.** Converting before composing is redundant; the [Composition Rules](composition_rules.md) settle the result type.
* **Convert when a type demands it**, storing an optic in a typed field or handing it to a utility that takes a `Traversal`.
* **`reverse()` is the one conversion that loses nothing.** An `Iso` is symmetric, so reversing it just swaps `get` and `reverseGet`.
~~~

~~~admonish tip title="See Also"
- [Composition Rules](composition_rules.md): the rules table for what type results from `andThen`
- [Optic Capabilities](optic_capabilities.md): which methods are available on each optic
- [Decision Trees](decision_trees.md): choosing the optic before you convert it
~~~

---

**Previous:** [Optic Capabilities](optic_capabilities.md)
**Next:** [Common Compiler Errors](compiler_errors.md)
