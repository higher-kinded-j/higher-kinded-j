# Optics IV: Java-Friendly APIs

> *"There was no particular reason to respect the language of the Establishment."*
>
> – Norman Mailer, *The Armies of the Night*

---

Optics originated in Haskell, a language with rather different conventions to Java. Method names like `view`, `over`, and `preview` don't match what Java developers expect, and the parameter ordering (value before source) feels backwards to anyone accustomed to the receiver-first style.

This chapter addresses that gap.

The Fluent API provides two complementary styles: static methods for concise one-liners, and builder chains for discoverable, IDE-friendly operations. Both produce identical results; the choice is aesthetic. Some developers prefer `OpticOps.get(person, ageLens)`. Others prefer `OpticOps.getting(person).through(ageLens)`. Neither is wrong.

Beyond syntax, this chapter introduces validation-aware modifications using Either, Maybe, and Validated: the core types that make optics genuinely useful in production code. When your field update might fail validation, `modifyEither` handles it cleanly. When you need to accumulate all validation errors rather than stopping at the first, `modifyAllValidated` is waiting.

The Free Monad DSL takes this further, separating program description from execution. You build an optic program as a data structure, then interpret it: directly for production, with logging for audits, or with validation for dry-runs. It sounds abstract. It becomes practical remarkably quickly.

---

## Two Styles, Same Result

```
┌─────────────────────────────────────────────────────────────┐
│  TRADITIONAL OPTICS STYLE                                   │
│                                                             │
│    ageLens.set(30, ageLens.get(person))                    │
│              ↑                    ↑                         │
│         value first         source last (!)                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  FLUENT BUILDER STYLE                                       │
│                                                             │
│    OpticOps.setting(person).through(ageLens, 30)           │
│                 ↑              ↑           ↑                │
│            source first    optic      value last            │
│                                                             │
│  Reads naturally: "Setting person, through age lens, to 30" │
└─────────────────────────────────────────────────────────────┘
```

---

## Programs as Data

The Free Monad DSL separates *what* from *how*:

```
    ┌─────────────────────────────────────────────────────────┐
    │  PROGRAM (Description)                                  │
    │                                                         │
    │   get(age) ─────► flatMap ─────► set(age + 1)          │
    │                                                         │
    │  A data structure representing operations               │
    │  No side effects yet!                                   │
    └────────────────────────┬────────────────────────────────┘
                             │
                             ▼
    ┌────────────────────────┴────────────────────────────────┐
    │                   INTERPRETERS                          │
    │                                                         │
    │  ┌─────────┐   ┌─────────┐   ┌───────────┐             │
    │  │ Direct  │   │ Logging │   │ Validating│             │
    │  │   Run   │   │  Audit  │   │  Dry-Run  │             │
    │  └────┬────┘   └────┬────┘   └─────┬─────┘             │
    │       │             │              │                    │
    │       ▼             ▼              ▼                    │
    │   Person       Audit Log     Valid/Invalid             │
    └─────────────────────────────────────────────────────────┘
```

Same program, different execution strategies.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Fluent API** – Static methods and builder patterns for natural Java syntax
- **Validation-Aware Modifications** – Using Either, Maybe, and Validated
- **Free Monad DSL** – Building optic programs as composable data
- **Interpreters** – Multiple execution strategies for the same program
~~~

---

## Chapter Contents

