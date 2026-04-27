# Reference

> _"It is a capital mistake to theorise before one has data."_
>
> – Sir Arthur Conan Doyle, *A Scandal in Bohemia*

---

The reference cluster collects the lookup material that returning readers come back to: which operations does each optic support, how do you convert between optic types, what compile errors mean, what trade-offs come with the various APIs in production, and the decision trees that route you to the right tool for a given problem.

Each page in this cluster is structured as a quick-scan reference, not a tutorial. If you need the conceptual material, the earlier chapters cover it in depth.

~~~admonish info title="In This Chapter"
- **Optic Capabilities** – A unified table showing which operations (`get`, `set`, `modify`, `modifyF`, `getAll`, `preview`, `foldMap`, `matches`, `build`) each optic type supports, including the asymmetric specialists (`Getter`, `Setter`, `Fold`).
- **Conversions** – The methods for converting between optic types (`asTraversal`, `asFold`, `asLens`, `andThen`) and the rules governing what type results from composing two optics.
- **Common Compiler Errors** – The errors you are most likely to encounter from `@Generate*` annotations, `@ImportOptics`, the Focus DSL processor, and Free Monad DSL programs, with minimal triggers and fixes.
- **Production Readiness** – Honest answers about runtime cost, allocation, when to cache optics in `static final` fields, build-time impact of annotation processing, and team conventions.
- **Decision Trees** – The three top-level trees consolidated into one page: which optic for your data shape, which API style for your task, and which advanced feature for your specific need.
~~~

---

## Chapter Contents

1. [Optic Capabilities](optic_capabilities.md) - What operations each optic supports
2. [Conversions](conversions.md) - Converting between optic types
3. [Common Compiler Errors](compiler_errors.md) - Diagnosing errors from generated code
4. [Production Readiness](production_readiness.md) - Performance and operational concerns
5. [Decision Trees](decision_trees.md) - Choosing optic, API, and features

---

**Next:** [Optic Capabilities](optic_capabilities.md)
