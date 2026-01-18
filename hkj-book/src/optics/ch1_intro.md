# Optics I: Fundamentals

> *"The best way to predict the future is to invent it... The second best way is to fund it. The third best way is to map it."*
>
> – Neal Stephenson, *Cryptonomicon*

---

Every Java developer has, at some point, stared at a screen wondering why updating a single field in an immutable record requires reconstructing half the object graph. The standard approach (manually copying and rebuilding each layer) works, technically speaking, in the same way that crossing the Atlantic in a rowing boat works. Possible, certainly. Pleasant, no.

Optics offer a rather more civilised alternative.

At their heart, optics are simply composable, reusable paths through data structures. A Lens focuses on a single field. A Prism handles cases that might not exist. An Iso converts between equivalent representations. None of this is particularly revolutionary in concept (functional programmers have been using these tools for decades), but the practical benefit is considerable: once you've defined a path, you can use it to get, set, or modify values without writing the same tedious reconstruction code repeatedly.

This chapter introduces the fundamental optics: Lens for product types (records with fields), Prism for sum types (sealed interfaces with variants), and Iso for reversible conversions. By the end, you'll understand not only how each works, but when to reach for one over another.

The composition rules table at the chapter's end is worth bookmarking. You'll refer to it more often than you might expect.

---

## The Optics Hierarchy

Before diving into individual optics, it helps to see how they relate to one another:

```
                    ┌─────────┐
                    │  Fold   │  (read-only, zero-or-more)
                    └────┬────┘
                         │
              ┌──────────┴──────────┐
              │                     │
         ┌────┴────┐          ┌─────┴─────┐
         │ Getter  │          │ Traversal │  (read-write, zero-or-more)
         └────┬────┘          └─────┬─────┘
              │                     │
              │               ┌─────┴─────┐
              │               │           │
         ┌────┴────┐    ┌─────┴─────┐ ┌───┴───┐
         │  Lens   │    │  Affine   │ │Setter │
         └────┬────┘    └─────┬─────┘ └───────┘
              │         (zero-or-one)
              │               │
              │         ┌─────┴─────┐
              │         │   Prism   │
              │         └─────┬─────┘
              │               │
              └───────┬───────┘
                 ┌────┴────┐
                 │   Iso   │  (exactly-one, reversible)
                 └─────────┘
```

Arrows indicate "can be used as" relationships. A Lens can be used anywhere a Getter or Fold is expected. An Iso (the most specific optic) can be used as any of the others. Affine sits between Traversal and Prism, representing precisely zero-or-one focus, making it ideal for optional fields.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Lenses** – Focus on exactly one field within a record. A Lens guarantees the field exists and provides both get and set operations.
- **Prisms** – Handle sum types (sealed interfaces) where a value might be one of several variants. A Prism can attempt to match a variant and construct new instances.
- **Affines** – For optional fields that may or may not be present. An Affine targets zero-or-one values, making it perfect for nullable fields or conditional access.
- **Isomorphisms** – Bidirectional, lossless conversions between equivalent types. An Iso can convert in both directions without losing information.
- **Composition** – Chain optics together to navigate arbitrarily deep structures. The composition of a Lens with a Prism produces an Affine, following predictable rules.
- **The Composition Rules** – A reference table showing what optic type results from composing any two optics. Keep this bookmarked; you'll need it.
- **Coupled Fields** – When record fields share invariants, sequential lens updates fail. Learn how `Lens.paired` provides atomic multi-field updates.
~~~

---

## Chapter Contents

1. [What Are Optics?](optics_intro.md) - Introduction to composable, reusable paths through data
2. [Lenses](lenses.md) - Focusing on required fields within records
3. [Prisms](prisms.md) - Safely handling sum types and optional variants
4. [Affines](affine.md) - Working with optional fields (zero-or-one focus)
5. [Isomorphisms](iso.md) - Lossless conversions between equivalent types
6. [Composition Rules](composition_rules.md) - A reference for what type results from combining optics
7. [Coupled Fields](coupled_fields.md) - Atomic updates for fields with shared invariants

---

**Next:** [What Are Optics?](optics_intro.md)
