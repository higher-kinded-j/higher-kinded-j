# Optics V: Integration and Recipes

> *"Anything worth doing is worth doing right."*
>
> — Hunter S. Thompson, *Fear and Loathing in Las Vegas*

---

Theory is useful; working code is better.

This chapter brings together everything from the previous four into practical patterns you can apply directly. The capstone example demonstrates a complete validation workflow: composing Lens, Prism, and Traversal to validate permissions nested deep within a form structure. It's the sort of problem that would require dozens of lines of imperative code, handled in a few declarative compositions.

The integration sections cover how optics work with higher-kinded-j's core types—extending Lenses and Traversals with additional capabilities, using Prisms for Optional, Either, and other standard containers. If you've wondered how to combine optics with the rest of the library, this is where you'll find answers.

The cookbook provides ready-to-use recipes for common problems: updating nested optionals, modifying specific sum type variants, bulk collection operations with filtering, configuration management, and audit trail generation. Each recipe includes the problem statement, solution code, and explanation of why it works.

Copy freely. That's what they're for.

---

## Which Optic Do I Need?

When facing a new problem, this flowchart helps:

```
                     ┌─────────────────────┐
                     │ What are you doing? │
                     └──────────┬──────────┘
                                │
           ┌────────────────────┼────────────────────┐
           ▼                    ▼                    ▼
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │   Reading   │     │  Modifying  │     │ Transforming│
    │    only?    │     │   values?   │     │   types?    │
    └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
           │                   │                   │
           ▼                   │                   ▼
    ┌─────────────┐            │            ┌─────────────┐
    │How many     │            │            │    ISO      │
    │targets?     │            │            └─────────────┘
    └──────┬──────┘            │
           │                   │
    ┌──────┴──────┐            │
    ▼             ▼            ▼
┌───────┐   ┌──────────┐  ┌─────────────┐
│ One   │   │Zero-more │  │How many     │
│       │   │          │  │targets?     │
└───┬───┘   └────┬─────┘  └──────┬──────┘
    │            │               │
    ▼            ▼        ┌──────┴──────┐
┌───────┐   ┌────────┐    ▼             ▼
│GETTER │   │ FOLD   │ ┌───────┐  ┌──────────┐
└───────┘   └────────┘ │ One   │  │Zero-more │
                       └───┬───┘  └────┬─────┘
                           │           │
                 ┌─────────┴───┐       │
                 ▼             ▼       ▼
           ┌──────────┐ ┌─────────┐ ┌──────────┐
           │ Required │ │Optional │ │TRAVERSAL │
           └────┬─────┘ └────┬────┘ └──────────┘
                │            │
                ▼            ▼
           ┌────────┐   ┌─────────┐
           │  LENS  │   │ PRISM   │
           └────────┘   └─────────┘
```

---

## The Complete Pipeline

Optics compose to handle complex real-world scenarios:

```
    Form
     │
     │ FormLenses.principal()        ← LENS (required field)
     ▼
    Principal (sealed interface)
     │
     │ PrincipalPrisms.user()        ← PRISM (might be Guest)
     ▼
    User
     │
     │ UserTraversals.permissions()  ← TRAVERSAL (list of perms)
     ▼
    List<Permission>
     │
     │ each                          ← focus on each
     ▼
    Permission
     │
     │ PermissionLenses.name()       ← LENS (required field)
     ▼
    String
     │
     │ validate(name)                ← effectful modification
     ▼
    Validated<Error, String>

    ═══════════════════════════════════════════════════════════
    Result: Validated<List<Error>, Form>
```

All permissions validated. All errors accumulated. Original structure preserved.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Composing Optics** — A complete validation workflow example
- **Core Type Integration** — Using optics with Either, Maybe, Validated, and Optional
- **Optics Extensions** — Extended capabilities for Lens and Traversal
- **Cookbook** — Ready-to-use recipes for common problems
- **Auditing Complex Data** — Real-world audit trail generation
~~~

---

## Chapter Contents

