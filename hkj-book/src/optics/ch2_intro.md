# Optics II: Collections

> *"The world is full of abandoned meanings."*
>
> – Don DeLillo, *White Noise*

---

Single values are straightforward enough. The challenge, as with so many things in programming, arrives when you need to handle *many* of them.

Consider an order containing a list of items, each with a price. Applying a discount to every price using standard Java means a stream, a map, a collector, and the nagging suspicion that there must be a better way. There is.

Traversals operate on zero-or-more values, typically the elements of a collection embedded within a larger structure. Where a Lens says "there is exactly one thing here," a Traversal says "there may be several things here, and I'd like to work with all of them, please." The politeness is implicit.

Folds are Traversal's read-only cousin. If you need to query, search, aggregate, or summarise without modification, a Fold makes your intent explicit. This matters more than it might seem: code that cannot accidentally modify data is code that behaves predictably at three in the morning when something has gone wrong.

This chapter covers both, along with Getters and Setters (the asymmetric specialists) and practical patterns for working with common Java collections. The monoid-based aggregation in Folds may initially seem academic, but it has a way of becoming indispensable once you've used it a few times.

---

## Traversal vs Fold

The distinction is worth understanding clearly:

```
┌─────────────────────────────────────────────────────────────┐
│                      TRAVERSAL                              │
│  ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐                      │
│  │  A  │   │  B  │   │  C  │   │  D  │  ← Focuses on all    │
│  └──┬──┘   └──┬──┘   └──┬──┘   └──┬──┘                      │
│     │        │        │        │                            │
│     ▼        ▼        ▼        ▼                            │
│   getAll ──────────────────────────→ [A, B, C, D]           │
│   modify(f) ───────────────────────→ [f(A), f(B), ...]      │
│   set(X) ──────────────────────────→ [X, X, X, X]           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        FOLD                                 │
│  ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐                      │
│  │  A  │   │  B  │   │  C  │   │  D  │  ← Read-only         │
│  └──┬──┘   └──┬──┘   └──┬──┘   └──┬──┘                      │
│     │        │        │        │                            │
│     ▼        ▼        ▼        ▼                            │
│   getAll ──────────────────────────→ [A, B, C, D]           │
│   foldMap(monoid, f) ──────────────→ combined result        │
│   exists(predicate) ───────────────→ true/false             │
│   ✗ NO set or modify                                        │
└─────────────────────────────────────────────────────────────┘
```

Both can read. Only Traversal can write. Choose based on intent.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Traversals** – Focus on zero-or-more elements within a structure. Apply the same modification to every item in a list, or extract all values matching a path.
- **Folds** – Read-only traversal that aggregates results using a Monoid. Sum all prices, count matching elements, or check if any element satisfies a predicate.
- **Getters** – A read-only Lens. When you need to extract a value but never modify it, a Getter documents that intent in the type.
- **Setters** – A write-only optic. Modify values without reading them first, useful when the modification doesn't depend on the current value.
- **Common Data Structures** – Ready-made traversals for Java's standard collections. Iterate over List elements, Map entries, Set members, and more.
- **Limiting Traversals** – Take the first N elements, skip elements, or focus only on specific indices. Control exactly which elements a Traversal affects.
- **List Decomposition** – Functional list patterns using cons (head/tail) and snoc (init/last). Decompose lists from either end for pattern matching and recursive algorithms.
~~~

---

## Chapter Contents

1. [Traversals](traversals.md) - Bulk operations on collection elements
2. [Folds](folds.md) - Read-only queries with monoid-based aggregation
3. [Getters](getters.md) - Read-only focus on single values
4. [Setters](setters.md) - Write-only modification without reading
5. [Common Data Structures](common_data_structure_traversals.md) - Patterns for List, Map, Set, and more
6. [Limiting Traversals](limiting_traversals.md) - First-N, take, and drop operations
7. [List Decomposition](list_decomposition.md) - Cons and snoc patterns for list manipulation

---

**Previous:** [Coupled Fields](coupled_fields.md)
**Next:** [Traversals](traversals.md)
