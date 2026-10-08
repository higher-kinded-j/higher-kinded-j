<!-- description: Narrow a traversal to the elements you want, by a predicate, an index or a key, and work with characters and words in text. -->

# Precision and Filtering

> *"I believe the angle and direction of the lines are full of secret meaning."*
>
> – J.G. Ballard, *Crash*

---

Sometimes you don't want *all* the elements. You want the expensive ones, or the ones at certain indices, or the value at one key of a map. Filtering and indexing narrow a traversal's focus to exactly that subset. Here is the destination, before any theory: a bulk update to the chapter's `Order`, narrowed to only the lines that cost more than £10. The build compiles and runs it, and a test holds each value the comments show:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/groups/GroupIntrosBook.java:precision}}
```

~~~admonish tip title="Why this matters"
The predicate travels with the path, not with the loop body. Write `if` checks inside iteration and every caller must remember them; build the filter into the optic and the rule is declared once, composes with any lens or traversal, and cannot be forgotten at a call site. The same holds for position: an indexed traversal carries the index in the type instead of in a manually-threaded counter.
~~~

Most readers only need filtered traversals, covered first. The rest solve specific problems: reach for indexed optics when position drives the logic, `At` and `Ixed` for per-key access to a map or a list, `Each` for one canonical traversal per container, and string traversals for text. Read those pages when you hit them, not before. [Decision Trees](decision_trees.md#tree-3-which-advanced-feature) routes a constraint to its page.

---

## Pages in this group

1. [Filtered Optics](filtered_optics.md): A predicate that travels with the path
2. [Indexed Optics](indexed_optics.md): Position-aware operations on collections
   - [Indexed Optics: Advanced Patterns](indexed_optics_advanced.md): Paired indices and audit trails
3. [Each Type Class](each_typeclass.md): One canonical traversal per container
4. [String Traversals](string_traversals.md): Characters, words and lines
5. [Indexed Access](indexed_access.md): `At` and `Ixed` for keys and indices

~~~admonish info title="Hands-On Learning"
The [Traversals & Practice Journey](../tutorials/optics/traversals_journey.md) (28 exercises) covers filtering and indexed patterns alongside the basics.
~~~

---

**Previous:** [List Decomposition](list_decomposition.md)
**Next:** [Filtered Optics](filtered_optics.md)
