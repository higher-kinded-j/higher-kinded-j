# Optics III: Precision and Filtering

> *"I believe the angle and direction of the lines are full of secret meaning."*
>
> – J.G. Ballard, *Crash*

---

Sometimes you don't want *all* the elements. Sometimes you want the expensive ones. Or the ones at specific indices. Or the ones that match a condition known only at runtime.

Optics handle this through filtering and indexing: techniques that narrow focus to exactly the subset you need. A filtered Traversal only operates on elements matching a predicate. An indexed Traversal carries position information alongside each element. Together, they provide surgical precision that would otherwise require verbose, error-prone manual iteration.

The At and Ixed type classes extend this precision to maps and indexed collections, offering principled ways to access, insert, or remove elements at specific keys. If you've ever written `map.get(key)` followed by null checks and conditional puts, you'll appreciate what these abstractions provide.

This chapter also revisits Prisms with advanced patterns: the `nearly` prism for predicate-based matching, `doesNotMatch` for exclusion filtering, and composition strategies for complex sealed interface hierarchies. These are the tools you reach for when the basic patterns no longer suffice.

Fair warning: some of this material is dense. It rewards careful reading.

---

## Filtering in Action

The concept is straightforward; the power is in the composition:

```
    Order Items: [Laptop, Mouse, Monitor, Keyboard]
                    │       │       │        │
                    ▼       ▼       ▼        ▼
    Unfiltered:   [✓]     [✓]     [✓]      [✓]

    filtered(price > £100):
                  [✓]     [ ]     [✓]      [ ]
                   │               │
                   ▼               ▼
    Focused:   [Laptop]       [Monitor]

    → modify(applyDiscount) only affects Laptop and Monitor
```

The filter becomes part of the optic itself, not scattered through your business logic.

---

## Indexed Access

When position matters:

```
    List: ["A", "B", "C", "D"]
           │     │     │     │
    Index: 0     1     2     3

    ┌─────────────────────────────────────────────┐
    │  IndexedTraversal                           │
    │                                             │
    │  getAll → [(0,"A"), (1,"B"), (2,"C"), ...]  │
    │                                             │
    │  modifyIndexed((i, v) -> v + i)             │
    │    → ["A0", "B1", "C2", "D3"]               │
    └─────────────────────────────────────────────┘
```

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Filtered Optics** – Predicate-based targeting within traversals
- **Indexed Optics** – Position-aware operations on collections
- **String Traversals** – Character-level operations on text
- **At and Ixed** – Type classes for indexed access patterns
- **Advanced Prism Patterns** – `nearly`, `doesNotMatch`, and complex matching
- **Profunctor Optics** – Type adaptation with contramap, map, and dimap
~~~

---

## Chapter Contents

