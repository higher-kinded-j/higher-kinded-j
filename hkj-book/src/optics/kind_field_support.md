# Kind Field Support in Focus DSL

## _Automatic Traversal for Higher-Kinded Type Fields_

~~~admonish info title="What You'll Learn"
- How the Focus DSL automatically handles `Kind<F, A>` record fields
- Convention-based detection for library types (ListKind, MaybeKind, etc.)
- Using `@TraverseField` for custom Kind types
- Understanding semantic classifications: EXACTLY_ONE, ZERO_OR_ONE, ZERO_OR_MORE
- How `traverseOver()` and `headOption()` work together
- Composing Kind field paths with other Focus DSL operations
~~~

~~~admonish example title="Example Code"
[KindFieldFocusExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/KindFieldFocusExample.java)
~~~

When working with Higher-Kinded-J, you often model domain data using `Kind<F, A>` wrapped fields. For example, a team might have members wrapped in `Kind<ListKind.Witness, Member>` or an optional lead wrapped in `Kind<MaybeKind.Witness, Member>`. The Focus DSL annotation processor automatically detects these fields and generates the appropriate traversal code.

---

## The Problem: Manual traverseOver Calls

Without automatic Kind field support, you would need to manually compose `traverseOver()` calls:

```java
// Without automatic support - verbose manual composition
@GenerateFocus
record Team(String name, Kind<ListKind.Witness, Member> members) {}

// Generated code would just return FocusPath to the raw Kind type:
// FocusPath<Team, Kind<ListKind.Witness, Member>> members()

// Forcing you to manually add traverseOver:
TraversalPath<Team, Member> memberPath = TeamFocus.members()
    .<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
```

---

## The Solution: Automatic Kind Detection

With Kind field support enabled, the processor automatically generates the correct path type:

```java
@GenerateFocus
record Team(String name, Kind<ListKind.Witness, Member> members) {}

// Generated code automatically includes traverseOver:
// TraversalPath<Team, Member> members() {
//     return FocusPath.of(...)
//         .<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
// }

// Usage is now straightforward:
List<Member> allMembers = TeamFocus.members().getAll(team);
Team updated = TeamFocus.members().modifyAll(Member::promote, team);
```

---

## Convention-Based Detection

The processor automatically recognises standard Higher-Kinded-J types by their witness type:

| Witness Type | Traverse Instance | Semantics | Generated Path |
|--------------|-------------------|-----------|----------------|
| `ListKind.Witness` | `ListTraverse.INSTANCE` | ZERO_OR_MORE | `TraversalPath` |
| `MaybeKind.Witness` | `MaybeTraverse.INSTANCE` | ZERO_OR_ONE | `AffinePath` |
| `OptionalKind.Witness` | `OptionalTraverse.INSTANCE` | ZERO_OR_ONE | `AffinePath` |
| `StreamKind.Witness` | `StreamTraverse.INSTANCE` | ZERO_OR_MORE | `TraversalPath` |
| `TryKind.Witness` | `TryTraverse.INSTANCE` | ZERO_OR_ONE | `AffinePath` |
| `IdKind.Witness` | `IdTraverse.INSTANCE` | EXACTLY_ONE | `AffinePath` |
| `EitherKind.Witness<E>` | `EitherTraverse.instance()` | ZERO_OR_ONE | `AffinePath` |
| `ValidatedKind.Witness<E>` | `ValidatedTraverse.instance()` | ZERO_OR_ONE | `AffinePath` |

### Example: Multiple Kind Fields

```java
@GenerateFocus
record ApiResponse(
    String requestId,
    Kind<MaybeKind.Witness, User> user,           // -> AffinePath
    Kind<ListKind.Witness, Warning> warnings,      // -> TraversalPath
    Kind<EitherKind.Witness<Error>, Data> result   // -> AffinePath
) {}

// Generated methods:
// AffinePath<ApiResponse, User> user()
// TraversalPath<ApiResponse, Warning> warnings()
// AffinePath<ApiResponse, Data> result()
```

---

## Semantic Classifications

Kind types are classified by their cardinality, which determines the generated path type:

### EXACTLY_ONE

Types that always contain exactly one element (e.g., `IdKind`).

```java
record Wrapper(Kind<IdKind.Witness, String> value) {}

// Generates AffinePath (type-safe narrowing from TraversalPath)
AffinePath<Wrapper, String> valuePath = WrapperFocus.value();

// Always has a value (IdKind semantics)
Optional<String> value = valuePath.getOptional(wrapper);
```

~~~admonish note title="Why AffinePath for EXACTLY_ONE?"
Although `IdKind` always contains exactly one element, the generated code uses `traverseOver()` which returns `TraversalPath`. We narrow this to `AffinePath` via `headOption()` for type safety. This is a safe approach that works correctly at runtime.
~~~

### ZERO_OR_ONE

Types that contain zero or one element (e.g., `MaybeKind`, `OptionalKind`, `TryKind`, `EitherKind`).

```java
record Config(Kind<MaybeKind.Witness, String> apiKey) {}

// Generates AffinePath
AffinePath<Config, String> keyPath = ConfigFocus.apiKey();

// May or may not have a value
Optional<String> key = keyPath.getOptional(config);
boolean hasKey = keyPath.matches(config);
```

### ZERO_OR_MORE

Types that contain zero or more elements (e.g., `ListKind`, `StreamKind`).

```java
record Team(Kind<ListKind.Witness, Member> members) {}

// Generates TraversalPath
TraversalPath<Team, Member> membersPath = TeamFocus.members();

// Multiple values
List<Member> allMembers = membersPath.getAll(team);
int count = membersPath.count(team);
```

---

## Custom Kind Types with @TraverseField

For Kind types not in the Higher-Kinded-J library, use `@TraverseField` to configure the traversal:

```java
// Custom Kind type
public class TreeKind {
    public enum Witness {}
}

// Custom Traverse implementation
public enum TreeTraverse implements Traverse<TreeKind.Witness> {
    INSTANCE;
    // ... implementation
}

// Use @TraverseField to register the mapping
@GenerateFocus
record Forest(
    String name,
    @TraverseField(
        traverse = "com.example.TreeTraverse.INSTANCE",
        semantics = KindSemantics.ZERO_OR_MORE
    )
    Kind<TreeKind.Witness, Tree> trees
) {}

// Generated: TraversalPath<Forest, Tree> trees()
```

### @TraverseField Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `traverse` | `String` | Fully qualified expression for the Traverse instance |
| `semantics` | `KindSemantics` | Cardinality classification (default: `ZERO_OR_MORE`) |

### Traverse Expression Examples

```java
// Enum singleton
@TraverseField(traverse = "com.example.TreeTraverse.INSTANCE")

// Factory method
@TraverseField(traverse = "com.example.EitherTraverse.instance()")

// Static field
@TraverseField(traverse = "com.example.MyTraverse.TRAVERSE")
```

---

## The headOption() Method

When the processor generates code for `ZERO_OR_ONE` or `EXACTLY_ONE` semantics, it uses the `headOption()` method to narrow `TraversalPath` to `AffinePath`:

```java
// Generated code for MaybeKind field:
public static AffinePath<Config, String> apiKey() {
    return FocusPath.of(...)
        .<MaybeKind.Witness, String>traverseOver(MaybeTraverse.INSTANCE)
        .headOption();
}
```

### How headOption() Works

`headOption()` converts a `TraversalPath` (zero or more) to an `AffinePath` (zero or one):

- **Get**: Returns the first element if present via `preview()`
- **Set**: Updates all focused elements via `setAll()`

```java
TraversalPath<List<String>, String> listPath = TraversalPath.of(Traversals.forList());

// Narrow to first element only
AffinePath<List<String>, String> firstPath = listPath.headOption();

// Get first element
Optional<String> first = firstPath.getOptional(List.of("a", "b", "c")); // Optional["a"]

// Set updates all elements (preserves traversal semantics)
List<String> updated = firstPath.set("X", List.of("a", "b", "c")); // ["X", "X", "X"]
```

---

## Composition with Other Focus Operations

Kind field paths compose naturally with other Focus DSL operations:

### Chaining Navigation

```java
@GenerateFocus
record Project(String name, Kind<ListKind.Witness, Team> teams) {}

@GenerateFocus
record Team(String name, Kind<ListKind.Witness, Member> members) {}

@GenerateFocus
record Member(String name, Kind<ListKind.Witness, Skill> skills) {}

// Chain through multiple Kind fields
TraversalPath<Project, Team> teamsPath = ProjectFocus.teams();
TraversalPath<Team, Member> membersPath = TeamFocus.members();
TraversalPath<Member, Skill> skillsPath = MemberFocus.skills();

// Compose for deep navigation
for (Team team : teamsPath.getAll(project)) {
    for (Member member : membersPath.getAll(team)) {
        List<Skill> skills = skillsPath.getAll(member);
        // Process skills...
    }
}
```

### Filtering

```java
// Filter members with specific skills
TraversalPath<Team, Member> seniorDevs = TeamFocus.members()
    .filter(m -> MemberFocus.skills().exists(s -> s.proficiency() > 90, m));

List<Member> experts = seniorDevs.getAll(team);
```

### Conditional Modification

```java
// Improve skills only for members with low proficiency
Team updated = TeamFocus.members().modifyWhen(
    member -> MemberFocus.skills().exists(s -> s.proficiency() < 50, member),
    member -> MemberFocus.skills().modifyAll(Skill::improve, member),
    team
);
```

---

## Parameterised Witness Types

Some witness types have type parameters (e.g., `EitherKind.Witness<E>`, `ValidatedKind.Witness<E>`). The processor handles these automatically:

```java
@GenerateFocus
record Response(
    Kind<EitherKind.Witness<String>, User> user
) {}

// Generated code uses factory method with proper type parameters:
// public static AffinePath<Response, User> user() {
//     return FocusPath.of(...)
//         .<EitherKind.Witness<String>, User>traverseOver(
//             EitherTraverse.<String>instance()
//         )
//         .headOption();
// }
```

---

## Unknown Kind Types

If the processor encounters a `Kind<F, A>` field with an unrecognised witness type and no `@TraverseField` annotation, it falls back to generating a standard `FocusPath` to the raw `Kind` type:

```java
// Unknown witness type without annotation
record Data(Kind<UnknownKind.Witness, String> value) {}

// Falls back to: FocusPath<Data, Kind<UnknownKind.Witness, String>> value()
```

To enable automatic traversal for unknown types, add `@TraverseField` with the appropriate configuration.

---

## Key Takeaways

~~~admonish info title="Key Takeaways"
* **Convention over configuration** - Library Kind types are automatically detected and handled
* **Semantic classification** - EXACTLY_ONE, ZERO_OR_ONE, ZERO_OR_MORE determine the generated path type
* **@TraverseField** - Enables custom Kind type support with explicit Traverse configuration
* **headOption()** - Narrows TraversalPath to AffinePath for zero-or-one semantics
* **Composable** - Kind field paths integrate seamlessly with filtering, modification, and chaining
~~~

---

~~~admonish tip title="See Also"
- [Focus DSL](focus_dsl.md) - Core Focus DSL concepts and navigation
- [Foldable and Traverse](../functional/foldable_and_traverse.md) - Understanding the Traverse type class
- [Core Type Integration](core_type_integration.md) - Working with Maybe, Either, Validated in optics
~~~

~~~admonish tip title="Further Reading"
- **Monocle Documentation**: [Traversal](https://www.optics.dev/Monocle/docs/optics/traversal) - Scala's traversal implementation
- **Cats Documentation**: [Traverse](https://typelevel.org/cats/typeclasses/traverse.html) - The Traverse type class
~~~

---

**Previous:** [Focus DSL](focus_dsl.md)
**Next:** [Fluent API](fluent_api.md)
