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
- **Filtered Optics** – Apply predicates to narrow which elements a Traversal affects. Only modify items over a certain price, or extract elements matching a condition.
- **Indexed Optics** – Carry position information alongside values. Know which index you're modifying, or transform values based on their position in a collection.
- **Each Typeclass** – Provides canonical traversals for container types. Get a Traversal for any List, Map, Optional, or custom container through a uniform interface, with optional indexed access.
- **String Traversals** – Treat strings as collections of characters. Modify individual characters, filter by character properties, or transform text character-by-character.
- **At and Ixed** – Type classes for indexed access. `At` handles keys that may or may not exist (like Map entries); `Ixed` handles indices that should exist (like List positions).
- **Advanced Prism Patterns** – Beyond basic sum types: `nearly` matches values close to a target, `doesNotMatch` inverts a Prism's focus, and complex hierarchies compose cleanly.
- **Profunctor Optics** – Transform the input and output types of optics. Adapt an optic for a different representation without rewriting it.
~~~

---

## Chapter Contents

1. [Filtered Optics](filtered_optics.md) - Predicate-based targeting within traversals
2. [Indexed Optics](indexed_optics.md) - Position-aware operations on collections
3. [Each Typeclass](each_typeclass.md) - Canonical element-wise traversal for containers
4. [String Traversals](string_traversals.md) - Character-level operations on text
5. [Indexed Access](indexed_access.md) - At and Ixed type classes for indexed access patterns
6. [Advanced Prism Patterns](advanced_prism_patterns.md) - `nearly`, `doesNotMatch`, and complex matching
7. [Profunctor Optics](profunctor_optics.md) - Type adaptation with contramap, map, and dimap

---

**Next:** [Filtered Optics](filtered_optics.md)
