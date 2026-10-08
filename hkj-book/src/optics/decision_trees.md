# Decision Trees

_Pick the optic, the API, the advanced feature or the interpreter by answering one question at a time._

Each group introduction links here rather than drawing its own tree. Use this page when you need to route quickly to the right tool.

---

## Tree 1: Which optic do I need?

```mermaid
flowchart TD
    accTitle: Which optic do I need?
    accDescr: Reading only, exactly one target, is a Getter; reading only, zero or more, a Fold. Reading and writing exactly one target is a Lens; zero or one where the field may be absent, an Affine; zero or one where the value may be another variant, a Prism; zero or more, a Traversal. Converting between equivalent types is an Iso.
    Q{"What are you doing<br/>to the focus?"}
    Q -->|"reading only"| R{"How many<br/>targets?"}
    Q -->|"reading and writing"| M{"How many<br/>targets?"}
    Q -->|"converting between<br/>equivalent types"| I(["Iso"])

    R -->|"exactly one"| G(["Getter"])
    R -->|"zero or more"| F(["Fold"])

    M -->|"exactly one"| L(["Lens"])
    M -->|"zero or one:<br/>the field may be absent"| A(["Affine"])
    M -->|"zero or one:<br/>the value may be another variant"| P(["Prism"])
    M -->|"zero or more"| T(["Traversal"])

    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    class Q,R,M decision
    class I,G,F,L,A,P,T tier
```

Write-only access is the one case the tree does not reach: that is a [Setter](setters.md), and you arrive at it by knowing you never read.

| You have... | You want to... | Reach for |
|---|---|---|
| A required field on a record | Get and set | [Lens](lenses.md) |
| A variant of a sealed type | Match and modify the variant | [Prism](prisms.md) |
| An optional field (nullable, `Optional`-wrapped) | Get and set if present | [Affine](affine.md) |
| Two equivalent representations | Convert losslessly | [Iso](iso.md) |
| A collection field | Apply an operation to every element | [Traversal](traversals.md) |
| A collection field, read-only | Query, search, aggregate | [Fold](folds.md) |
| Read-only access to a single field | Get only | [Getter](getters.md) |
| Write-only access, one or many targets | Set or modify, never read | [Setter](setters.md) |

The two zero-or-one optics are not interchangeable. An `Affine` reaches a value that may be absent, a `Prism` one that may be another variant, and of the two, only the prism can build the whole structure back up from its value.

---

## Tree 2: Which API style?

```mermaid
flowchart TD
    accTitle: Which API style?
    accDescr: Start on the Focus DSL and stay there for a plain nested update. An update that can fail or accumulate errors moves to the Fluent API's OpticOps. One that must be inspected, audited or run several ways moves to the Free Monad DSL.
    S(["Start: Focus DSL<br/>CompanyFocus.headquarters().city()"]) --> Q{"Does the update<br/>need more?"}
    Q -->|"no: plain nested update"| S2(["stay on the Focus DSL"])
    Q -->|"it can fail, or accumulate errors"| FA(["Fluent API: OpticOps<br/>modifyEither, modifyAllValidated"])
    Q -->|"it must be inspected,<br/>audited or run several ways"| FM(["Free Monad DSL<br/>see Programs as Data"])

    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    classDef wire fill:#8caaee,stroke:#1e66f5,color:#232634
    class Q decision
    class S2,FA,FM tier
    class S wire
```

| Your task | Use |
|---|---|
| Update a nested record field | [Focus DSL](focus_dsl.md) |
| Compose optics across types you own | [Focus DSL](focus_dsl.md) |
| Validate as you modify (`Either`, `Validated`, `Maybe`) | [Updates That Can Fail](fluent_api.md) |
| Fan out an effect across a collection | [`modifyF`](fluent_api.md#part-3-arbitrary-effects-with-modifyf) |
| Build optic operations as data, run later | [Free Monad DSL](free_monad_dsl.md) |
| Audit trail of every optic operation | [Free Monad DSL with logging interpreter](interpreters.md) |
| Reuse an optic for a type you cannot annotate | [`@ImportOptics`](importing_optics.md) or an [`OpticsSpec`](optics_spec_interfaces.md) interface |
| Adapt an optic to a different data shape | [Compose when the source nests, an `Iso` when the shapes are equivalent, `Lens.of` when it is lopsided, `dimap` when it is one-way](profunctor_optics.md) |

---

## Tree 3: Which advanced feature?

```mermaid
flowchart TD
    accTitle: Which advanced feature?
    accDescr: When only some elements should be touched, use filtered optics. When the position matters as well as the value, use indexed optics. When the source or target is the wrong shape, use profunctor optics.
    Q{"What is the<br/>constraint?"}
    Q -->|"only some elements<br/>should be touched"| F(["Filtered optics"])
    Q -->|"the position matters<br/>as well as the value"| I(["Indexed optics"])
    Q -->|"the source or target<br/>is the wrong shape"| P(["Profunctor optics"])

    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    class Q decision
    class F,I,P tier
```

| Your problem | Reach for |
|---|---|
| "Apply only to elements matching a predicate" | [Filtered Optics](filtered_optics.md) |
| "I need the index alongside each element" | [Indexed Optics](indexed_optics.md) |
| "Access by key in a `Map`" | [Indexed Access](indexed_access.md): the `At` (full CRUD) and `Ixed` (read/update) type classes |
| "Apply over every element of a custom container" | [Each Typeclass](each_typeclass.md) |
| "Operate on individual characters of a `String`" | [String Traversals](string_traversals.md) |
| "Adapt a lens for a different source record type" | Compose: `outerLens.andThen(innerLens)` ([Profunctor Optics](profunctor_optics.md)) |
| "The two shapes hold the same information" | An [`Iso`](iso.md), then compose: `Iso.andThen(Lens) = Lens` |
| "One-way conversion inside an effectful pipeline" | `optic.dimap(...)` ([Profunctor Optics](profunctor_optics.md)) |
| "Match a value by a predicate, not by type" | [`Prisms.nearly`](advanced_prism_patterns.md#predicate-matching-with-prismsnearly) |

---

## Tree 4: Which interpreter?

```mermaid
flowchart TD
    accTitle: Which interpreter to run a program with
    accDescr: For the result, run it with direct. For the result and a record of the steps, run it with logging. For a report of problems instead of the result, run it with validating. For anything else, such as mocks, metrics or permissions, write your own natural transformation.
    Q{"What do you want<br/>from the program?"}
    Q -->|"the result"| D(["direct()<br/>run it"])
    Q -->|"the result, and<br/>a record of the steps"| L(["logging()<br/>run it and keep a trail"])
    Q -->|"the result discarded,<br/>and a report instead"| V(["validating()<br/>run it and report problems"])
    Q -->|"something else:<br/>mocks, metrics, permissions"| O(["your own<br/>natural transformation"])

    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    class Q decision
    class D,L,V,O tier
```

| You want from the program | Interpreter |
|---|---|
| The result | `direct()`, in [Interpreters](interpreters.md#part-2-the-direct-interpreter) |
| The result, and a record of every step | `logging()`, in [Interpreters](interpreters.md#part-3-the-logging-interpreter) |
| A report of problems instead of the result | `validating()`, which still runs the program: [Interpreters](interpreters.md#part-4-the-validation-interpreter) |
| Something else: mocks, metrics, permissions | Your own, in [Interpreters](interpreters.md#part-5-creating-custom-interpreters) |

---

~~~admonish tip title="See Also"
- [Optic Capabilities](optic_capabilities.md): what each optic can do once you have chosen one
- [Composition Rules](composition_rules.md): what type results from composing two optics
- [Annotations at a Glance](annotations_at_a_glance.md): which annotation generates each optic
~~~

---

**Previous:** [Annotations at a Glance](annotations_at_a_glance.md)
**Next:** [Cookbook](cookbook.md)
