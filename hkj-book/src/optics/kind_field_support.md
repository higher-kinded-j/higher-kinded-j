# Kind Field Support in Focus DSL

## _Automatic Traversal for Higher-Kinded Type Fields_

~~~admonish info title="What You'll Learn"
- How the Focus DSL handles `Kind<F, A>` record fields without any extra annotation
- Convention-based detection for the library witnesses (`ListKind`, `MaybeKind`, and the rest)
- Using `@TraverseField` for your own `Kind` types
- What the semantic classifications (`EXACTLY_ONE`, `ZERO_OR_ONE`, `ZERO_OR_MORE`) decide
- How `traverseOver()` and `headOption()` work together, and the one surprise in `headOption`'s set
~~~

~~~admonish example title="See Example Code"
[KindFieldFocusExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/KindFieldFocusExample.java)
~~~

Domain records in a Higher-Kinded-J codebase often hold their contents in a `Kind<F, A>`: a team's members as `Kind<ListKind.Witness, Member>`, an optional lead as `Kind<MaybeKind.Witness, Member>`. The processor recognises those witnesses and generates the traversal for you, so the field reads exactly like a plain `List` field would:

<!-- verify -->
```java
// Kind<ListKind.Witness, Member> members, and yet:
List<Member> everyone = TeamFocus.members().getAll(team);

Team levelled =
    TeamFocus.members()
        .via(MemberFocus.skills())
        .modifyAll(Fixture::improve, team);
```

---

## The Problem It Removes

Without automatic detection, every `Kind` field would need its `Traverse` instance threaded in by hand at each use site:

```java
// What you would otherwise write, once per field, per call site
TraversalPath<Team, Member> memberPath =
    FocusPath.of(TeamLenses.members()).<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
```

The processor knows which `Traverse` belongs to `ListKind.Witness`, so the generated method has already applied it.

---

## Convention-Based Detection

~~~admonish tip title="Why this matters"
The `Traverse` for a witness is resolved once, by the processor, rather than threaded through every call site. Without this a `Kind<F, A>` field means passing an explicit `Traverse` instance to each operation that walks it, which is the ceremony that makes higher-kinded code look expensive in Java. Here the witness in the field's type is enough for the processor to pick the instance and the path type together.
~~~

A *witness* is the marker type that stands in for the higher-kinded `F` (see [Higher-Kinded Types](../hkts/hkt_introduction.md)). The library's witnesses are recognised by name:

| Witness Type | Traverse Instance | Semantics | Generated Path |
|--------------|-------------------|-----------|----------------|
| `ListKind.Witness` | `ListTraverse.INSTANCE` | ZERO_OR_MORE | `TraversalPath` |
| `StreamKind.Witness` | `StreamTraverse.INSTANCE` | ZERO_OR_MORE | `TraversalPath` |
| `MaybeKind.Witness` | `MaybeTraverse.INSTANCE` | ZERO_OR_ONE | `AffinePath` |
| `OptionalKind.Witness` | `OptionalTraverse.INSTANCE` | ZERO_OR_ONE | `AffinePath` |
| `TryKind.Witness` | `TryTraverse.INSTANCE` | ZERO_OR_ONE | `AffinePath` |
| `EitherKind.Witness<E>` | `EitherTraverse.instance()` | ZERO_OR_ONE | `AffinePath` |
| `ValidatedKind.Witness<E>` | `ValidatedTraverse.instance()` | ZERO_OR_ONE | `AffinePath` |
| `IdKind.Witness` | `IdTraverse.INSTANCE` | EXACTLY_ONE | `AffinePath` |

Parameterised witnesses are handled too: an `EitherKind.Witness<String>` field generates a call to `EitherTraverse.<String>instance()` with the type argument carried through.

A wildcard resolves to the type it stands for, as it does for `List<? extends Member>` through `.each()`: `Kind<ListKind.Witness, ? extends Member>` widens to `TraversalPath<Team, Member>`, and an unbounded or super-bounded wildcard focuses `Object`. A wildcard inside a parameterised witness resolves the same way, so `Kind<EitherKind.Witness<? extends CharSequence>, Member>` reads `EitherTraverse.<CharSequence>instance()`. The traversal rebuilds the container rather than writing into it, so a component declared with the wildcard comes back holding a `Kind<ListKind.Witness, Member>`, which its declaration admits. A wildcard in the witness position itself, `Kind<?, Member>`, names no `Traverse` instance, and the field keeps its plain `FocusPath`, with or without `@TraverseField`, and with the annotation a note says so.

<!-- verify -->
```java
// One record, three witnesses, three path types
AffinePath<ApiResponse, Member> lead = ApiResponseFocus.lead();          // MaybeKind
TraversalPath<ApiResponse, String> warnings = ApiResponseFocus.warnings(); // ListKind
AffinePath<ApiResponse, String> result = ApiResponseFocus.result();      // EitherKind<String>

Optional<Member> theLead = lead.getOptional(response);
List<String> allWarnings = warnings.getAll(response);
```

---

## What the Semantics Decide

`KindSemantics` is cardinality for witnesses: it classifies a witness by how many values its type can hold, exactly as a container's `Cardinality` does for a plain field, and that classification picks the path type:

- **`ZERO_OR_MORE`** (`ListKind`, `StreamKind`): a `TraversalPath`, straight from `traverseOver`.
- **`ZERO_OR_ONE`** (`MaybeKind`, `OptionalKind`, `TryKind`, `EitherKind`, `ValidatedKind`): a `TraversalPath` narrowed with `headOption()` to an `AffinePath`.
- **`EXACTLY_ONE`** (`IdKind`): also narrowed to an `AffinePath`.

~~~admonish note title="Why `AffinePath` for `EXACTLY_ONE`?"
An `IdKind` always holds exactly one value, so a `FocusPath` would be the honest type. The generation route does not allow it: `traverseOver` returns a `TraversalPath`, and the only narrowing available is `headOption()`, which lands on `AffinePath`. The result is correct at runtime, and one step weaker than the type could be.
~~~

---

## Custom `Kind` Types with `@TraverseField`

For a witness outside the library, name the `Traverse` instance and the cardinality:

```java
@GenerateFocus
record Forest(
    String name,
    @TraverseField(
        traverse = "com.example.TreeTraverse.INSTANCE",
        semantics = KindSemantics.ZERO_OR_MORE)
    Kind<TreeKind.Witness, Tree> trees) {}

// Generates: TraversalPath<Forest, Tree> trees()
```

| Attribute | Type | Description |
|-----------|------|-------------|
| `traverse` | `String` | A Java expression yielding the `Traverse` instance |
| `semantics` | `KindSemantics` | The cardinality (default `ZERO_OR_MORE`) |

The expression is emitted verbatim, so any of these forms works:

```java
@TraverseField(traverse = "com.example.TreeTraverse.INSTANCE")     // enum singleton
@TraverseField(traverse = "com.example.TreeTraverse.instance()")   // factory method
@TraverseField(traverse = "com.example.TreeTraverse.TRAVERSE")     // static field
```

A `Kind<F, A>` field with an unrecognised witness and no `@TraverseField` is not an error: the processor falls back to a plain `FocusPath` focusing the raw `Kind`, and you apply `traverseOver` yourself. For one of the library's own witnesses it says so with a note, since a Higher-Kinded-J witness with no registered `Traverse` is a gap rather than a choice.

The same fallback applies to a witness that names no `Traverse` instance, whether or not `@TraverseField` is present: a bare or `? super` wildcard, or one of the record's own type variables, since a `Traverse` is written for one witness and a type variable stands for any. With `@TraverseField` on a `Kind` whose witness is either of those, on a raw `Kind`, or on a component that is not declared as a `Kind` at all, the processor says so with a note naming the component and what would make the annotation apply, so an annotation that asked for a traversal is never dropped in silence. See the two notes' entries in [Compiler Errors](compiler_errors.md#traversefield-the-annotation-on-record-component-xy-is-not-applied-a-note).

---

## `headOption()`: Narrowing a Traversal

`headOption()` turns a `TraversalPath` into an `AffinePath`. Its read is the first focused element; its **write goes to every focused element**, because that is what the underlying traversal does:

<!-- verify -->
```java
TraversalPath<Member, Skill> skills = MemberFocus.skills();

AffinePath<Member, Skill> firstSkill = skills.headOption();
Optional<Skill> first = firstSkill.getOptional(alice);   // Optional[Skill[name=Java, proficiency=95]]

Member flattened = firstSkill.set(new Skill("Go", 50), alice);
// BOTH skills are now Skill[name=Go, proficiency=50]: the set is setAll underneath
```

~~~admonish warning title="The set is a `setAll`"
For a `ZERO_OR_ONE` witness this is exactly right: there is at most one element, so setting "all" of them sets the one. On a genuinely multi-element traversal it is a trap. When you mean the first element only, index it (`.at(0)` from the path focusing the container) rather than narrowing with `headOption()`.
~~~

---

## Composing with the Rest of the DSL

Kind field paths are ordinary paths, so filtering, conditional modification and chaining all apply:

<!-- verify -->
```java
// Members who have a weak skill
TraversalPath<Team, Member> needsTraining =
    TeamFocus.members().filter(m -> MemberFocus.skills().exists(s -> s.proficiency() < 50, m));

List<Member> juniors = needsTraining.getAll(team);

// Improve only their skills
Team afterTraining =
    TeamFocus.members()
        .modifyWhen(
            m -> MemberFocus.skills().exists(s -> s.proficiency() < 50, m),
            m -> MemberFocus.skills().modifyAll(Fixture::improve, m),
            team);
```

---

~~~admonish info title="Key Takeaways"
* **Convention over configuration.** The library witnesses are detected by name and the right `Traverse` is applied for you.
* **`KindSemantics` is cardinality for witnesses**, and picks the path type nearly as a container type does: zero-or-more gives a `TraversalPath`, zero-or-one an `AffinePath`. `EXACTLY_ONE` is the exception, landing on `AffinePath` where the type would justify a `FocusPath`.
* **`@TraverseField` opens the door to your own `Kind` types**, with the `Traverse` given as a verbatim expression.
* **`headOption()` reads the first and writes to all.** Sound for zero-or-one witnesses, a trap on a real traversal.
* **An unknown witness degrades gracefully** to a `FocusPath` over the raw `Kind`, which you can still `traverseOver` by hand.
~~~

~~~admonish tip title="See Also"
- [Focus DSL](focus_dsl.md): core concepts and path types
- [Type Class and Effect Integration](focus_effects.md): `traverseOver()` in the general case
- [Foldable and Traverse](../functional/foldable_and_traverse.md): the `Traverse` type class itself
- [Core Type Integration](core_type_integration.md): `Maybe`, `Either` and `Validated` alongside optics
~~~

~~~admonish tip title="Further Reading"
- **Monocle**: [Traversal](https://www.optics.dev/Monocle/docs/optics/traversal): scala's traversal, the same abstraction
- **Cats**: [Traverse](https://typelevel.org/cats/typeclasses/traverse.html): the type class this support is built on
~~~

---

**Previous:** [Focus DSL with External Libraries](focus_external_bridging.md)
**Next:** [Fluent API](fluent_api.md)
