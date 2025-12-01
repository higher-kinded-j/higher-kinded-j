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
         │  Lens   │◄───│   Prism   │ │Setter │
         └────┬────┘    └───────────┘ └───────┘
              │              (zero-or-one)
              │
         ┌────┴────┐
         │   Iso   │  (exactly-one, reversible)
         └─────────┘
```

Arrows indicate "can be used as" relationships. A Lens can be used anywhere a Getter or Fold is expected. An Iso (the most specific optic) can be used as any of the others.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Lenses** – Focusing on required fields within records
- **Prisms** – Safely handling sum types and optional variants
- **Isomorphisms** – Lossless conversions between equivalent types
- **Composition** – Chaining optics to navigate deep structures
- **The Composition Rules** – A reference for what type results from combining optics
~~~

---

## Chapter Contents

