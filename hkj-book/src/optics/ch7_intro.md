# Look It Up

_Find the question you hold, then go to the page that answers it._

The pages a returning reader comes back to, holding a question rather than reading in order.

| You hold | Go to |
|---|---|
| A question a senior engineer asks before adopting optics: cost, allocation, caching, build time, team conventions | [Production Readiness](production_readiness.md) |
| An annotation, and you want what it generates | [Annotations at a Glance](annotations_at_a_glance.md) |
| A type you do not own, such as a Jackson node or a JOOQ record | [Optics for External Types](importing_optics.md) |
| A build question: Lombok beside the processor, incremental compilation, `-Werror` | [Production Readiness: build-time impact](production_readiness.md#build-time-impact) |
| A data shape, a task or a constraint, and you want the optic or API for it | [Decision Trees](decision_trees.md) |
| A common nested-update problem, and you want a recipe for it | [Cookbook](cookbook.md) |
| An optic, and you want to know whether it declares `get`, `set`, `getAll`, `matches`... | [Optic Capabilities](optic_capabilities.md) |
| One optic type, and you need another | [Conversions](conversions.md) |
| Two optics, and you want the type `andThen` returns | [Composition Rules](composition_rules.md) |
| A wither, a stream or a `switch`, and you want the optic that replaces it | [Coming from Lombok, Streams and Switch](from_java.md) |
| A name from Monocle or Haskell's `lens`, or a behaviour you expect from them | [Coming from Monocle or Haskell lens](from_monocle.md) |
| A Focus DSL question: a pattern, a pitfall, the FAQ | [Focus DSL Reference](focus_reference.md) |
| A compiler message from a generated optic | [Common Compiler Errors](compiler_errors.md) |

Most lookups resolve to one distinction: an optic either declares an operation or it does not, and when it does not, a conversion usually reaches it anyway:

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

`Fixture` is the compiled example's own setup: sample records, hand-written optics and validators. Nothing in it is library API.

[Optic Capabilities](optic_capabilities.md) is the table of what each optic declares, and [Conversions](conversions.md) the table of how to get from one to another.

---

**Previous:** [Interpreters](interpreters.md)
**Next:** [Production Readiness](production_readiness.md)
