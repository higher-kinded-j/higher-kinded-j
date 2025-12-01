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
│  ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐                     │
│  │  A  │   │  B  │   │  C  │   │  D  │  ← Focuses on all   │
│  └──┬──┘   └──┬──┘   └──┬──┘   └──┬──┘                     │
│     │        │        │        │                           │
│     ▼        ▼        ▼        ▼                           │
│   getAll ──────────────────────────→ [A, B, C, D]          │
│   modify(f) ───────────────────────→ [f(A), f(B), ...]     │
│   set(X) ──────────────────────────→ [X, X, X, X]          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        FOLD                                 │
│  ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐                     │
│  │  A  │   │  B  │   │  C  │   │  D  │  ← Read-only        │
│  └──┬──┘   └──┬──┘   └──┬──┘   └──┬──┘                     │
│     │        │        │        │                           │
│     ▼        ▼        ▼        ▼                           │
│   getAll ──────────────────────────→ [A, B, C, D]          │
│   foldMap(monoid, f) ──────────────→ combined result       │
│   exists(predicate) ───────────────→ true/false            │
│   ✗ NO set or modify                                       │
└─────────────────────────────────────────────────────────────┘
```

Both can read. Only Traversal can write. Choose based on intent.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Traversals** – Bulk operations on collection elements
- **Folds** – Read-only queries with monoid-based aggregation
- **Getters** – Read-only focus on single values
- **Setters** – Write-only modification without reading
- **Common Data Structures** – Patterns for List, Map, Set, and more
- **Limiting Traversals** – First-N, take, and drop operations
~~~

---

## Chapter Contents

