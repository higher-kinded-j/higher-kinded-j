# Reference

> _"It is a capital mistake to theorise before one has data."_
>
> – Sir Arthur Conan Doyle, *A Scandal in Bohemia*

---

The reference cluster collects the lookup material that returning readers come back to: which operations does each optic support, how do you convert between optic types, what compile errors mean, what trade-offs come with the various APIs in production, and the decision trees that route you to the right tool for a given problem.

Each page in this cluster is structured as a quick-scan reference, not a tutorial. If you need the conceptual material, the earlier chapters cover it in depth.

Most lookups here resolve to one distinction, so it is worth stating before the tables do. An optic either declares an operation or it does not, and when it does not, a conversion usually reaches it anyway:

<!-- verify -->
```java
Lens<Order, String> customer = OrderLenses.customer();

String name = customer.get(Fixture.order);
// "Alice": get is declared on Lens

// customer.getAll(Fixture.order);
// will not compile: getAll is on Fold, not Lens

Fold<Order, String> asFold = customer.asFold();
List<String> all = asFold.getAll(Fixture.order);
// ["Alice"]: the same access, one conversion later
```

[Optic Capabilities](optic_capabilities.md) is the table of what each optic declares; [Conversions](conversions.md) is the table of how to get from one to another. Between them they answer most of what brings a returning reader back.

~~~admonish info title="In This Chapter"
- **Optic Capabilities** – A unified table showing which operations (`get`, `set`, `modify`, `modifyF`, `getAll`, `preview`, `foldMap`, `matches`, `build`) each optic type supports, including the asymmetric specialists (`Getter`, `Setter`, `Fold`).
- **Conversions** – The methods for converting between optic types (`asTraversal`, `asFold`, `asLens`, `andThen`) and the rules governing what type results from composing two optics.
- **Common Compiler Errors** – The errors you are most likely to encounter from `@Generate*` annotations, `@ImportOptics`, the Focus DSL processor, and Free Monad DSL programs, with minimal triggers and fixes.
- **Production Readiness** – Honest answers about runtime cost, allocation, when to cache optics in `static final` fields, build-time impact of annotation processing, and team conventions.
- **Decision Trees** – The three top-level trees consolidated into one page: which optic for your data shape, which API style for your task, and which advanced feature for your specific need.
~~~

~~~admonish tip title="See Also"
- [Composition Rules](composition_rules.md): the `andThen` result table these pages refer back to
- [Annotations at a Glance](annotations_at_a_glance.md): which annotation generates each optic
- [Quickstart](quickstart.md): the shortest route in, if you arrived here first
~~~

---

## Chapter Contents

1. [Optic Capabilities](optic_capabilities.md): what operations each optic supports
2. [Conversions](conversions.md): converting between optic types
3. [Common Compiler Errors](compiler_errors.md): diagnosing errors from generated code
4. [Production Readiness](production_readiness.md): performance and operational concerns
5. [Decision Trees](decision_trees.md): choosing optic, API, and features

---

**Next:** [Optic Capabilities](optic_capabilities.md)
