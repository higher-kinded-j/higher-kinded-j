# One Line, Six Layers

> _"Simple things should be simple, complex things should be possible."_
>
> – Alan Kay

---

Here is a single line of Higher-Kinded-J. It is the entire body of a service method that edits one attribute on one node in a repository.

```java
repo.find(id)
    .toEitherPath()
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)
    .flatMap(repo::save);
```

That is it. No null check, no try/catch, no nested if, no copy constructor, no explicit error mapping. The same skeleton works unchanged for *reparent*, *retag*, *override*, and *restore*. Change the verb in the middle and the line still reads like a sentence.

Foundations is the chapter where we explain why this composes. Before we begin, let us see how much of the library is hiding inside those eighty characters.

~~~admonish info title="What We'll Learn"
- Why this one line is a good map of the whole stack
- Which Foundations concept lights up at each token
- Which reading path through the chapter best fits our goals
~~~

---

## Six Layers in One Expression

Each token in the line corresponds to a layer that the rest of this chapter unpacks.

```
   repo.find(id)              .toEitherPath()      .focus().attributes().at(key)
   └── Effect Path ───────────┤                    └── Optic ──────────────────┐
       absence as MaybePath   │                        traversal into a record │
                              │                                                │
                              └── Natural transformation                       │
                                  MaybePath ~> EitherPath                      │
                                                                               │
   .modify(spec::validateAndCoerce)             .flatMap(repo::save);          │
   └── Functor (under the optic) ───┐           └── Monad ─────────────────────┘
       pure transform on the focus  │               sequencing dependent steps
                                    │               (Either short-circuits)
                                    │
                                    └── Type class instance dispatched at compile time
                                        EitherFunctor / EitherMonad
```

Six independent ideas, none of which needed to be wired together by hand.

| Token | Layer | Where in this chapter |
|-------|-------|------------------------|
| `repo.find(id)` | A concrete instance of an effect type | [Core Types](../monads/ch_intro.md) |
| `.toEitherPath()` | A natural transformation between effects | [Natural Transformation](../functional/natural_transformation.md) |
| `.focus().attributes().at(key)` | A profunctor optic composed from lens and traversal | [Profunctor](../functional/profunctor.md), [Optics chapter](../optics/ch_intro.md) |
| `.modify(spec::validateAndCoerce)` | A `Functor` `map` running through the optic | [Functor](../functional/functor.md) |
| `.flatMap(repo::save)` | The `Monad` instance for `EitherPath` | [Monad](../functional/monad.md) |
| The whole expression | A `Kind<F, A>` flowing through type-class methods | [Higher-Kinded Types](ch_intro.md) |

Notice that we never had to pick a strategy for "what happens on failure". `EitherPath` already encodes failure as a left rail, and every operation downstream of a failing step quietly skips itself. The interesting code is in the middle, where the domain logic lives.

---

## The Same Skeleton, Different Verbs

The promise of this style is that once we have the skeleton, the variations are almost free. The snippets below are illustrative; the exact `focus()` method names depend on what the [Optics](../optics/ch_intro.md) chapter generates for our domain records.

```java
// Edit one field
repo.find(id)
    .toEitherPath()
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)
    .flatMap(repo::save);

// Move a node under a new parent
repo.find(id)
    .toEitherPath()
    .focus().parent()
    .replace(newParentId)
    .flatMap(repo::save);

// Add a tag, without disturbing the rest of the record
repo.find(id)
    .toEitherPath()
    .focus().tags()
    .modify(tags -> tags.add(tag))
    .flatMap(repo::save);

// Restore from a snapshot
repo.findSnapshot(id, version)
    .toEitherPath()
    .flatMap(repo::save);
```

Four operations, one shape. The repeated structure is not duplication; it is the API. Foundations explains the abstractions that make that possible.

---

## How to Read the Rest of This Chapter

Foundations is not a tutorial; it is the engine-room tour for readers who have already been productive with the library and want to know what is humming under the floor.

We have written it so that three different readers can take three different routes through it.

~~~admonish tip title="Three Reading Paths"
**The mechanism tour (≈ 30 minutes)**

For readers who want to lift the bonnet on the line above and see how `Kind<F, A>`, witness types, and type-class instances cooperate at runtime.

1. [HKT Introduction](hkt_introduction.md)
2. [Core Concepts](core-concepts.md)
3. [Lifting the Hood](lifting_the_hood.md), an annotated trace of one `flatMap` call from user code through `widen`, dispatch, and `narrow`
4. [Functor](../functional/functor.md), [Monad](../functional/monad.md), and [MonadError](../functional/monad_error.md), each with a "Back to the one-liner" callout
5. [Foundations FAQ](faq.md) for the questions experienced Java readers ask

**The generic-code author (≈ 45 minutes)**

For readers who want to write code that works unchanged across `MaybePath`, `EitherPath`, `TryPath`, and friends.

1. [Usage Guide](usage-guide.md)
2. [Functor](../functional/functor.md) → [Applicative](../functional/applicative.md) → [Monad](../functional/monad.md) → [Traverse](../functional/foldable_and_traverse.md)
3. [Natural Transformation](../functional/natural_transformation.md)
4. The cookbook recipes scattered through Type Classes

**The library extender (≈ 60 minutes)**

For readers who want to give their own type a witness, `widen`/`narrow`, and a Monad instance, so that it joins the same composition story.

1. [Core Concepts](core-concepts.md)
2. [Type Arity](type-arity.md)
3. [Extending the Simulation](extending-simulation.md)
4. The "Things People Get Wrong" callouts in the Type Classes section
~~~

---

## What Foundations Is, and Is Not

Foundations is *not* a sales pitch. We assume the reader has already shipped code with the Effect Path API, or with Optics, or with Monad Transformers, and is here for depth rather than motivation.

Foundations *is* the chapter that explains why the eighty characters above hold together, and how to write our own eighty characters that do the same job for our own domain.

Everything that follows is, in some sense, a footnote on that one line.

---

**Next:** [Higher-Kinded Types](ch_intro.md)
