# Profunctor Optics: Recipes

## _Wrapper adapters, migration recipes, and a complete worked example_

~~~admonish info title="What You'll Learn"
- How to build reusable wrapper-type lenses for strongly-typed value classes (`UserId`, `Email`, etc.)
- Migration patterns: exposing an old schema's view of new data, and when an `Iso` bridge is honest
- Where the runnable example demonstrates the adaptation styles end to end
- How adapted optics interact with effectful updates and deep composition
~~~

This page is a recipe shelf for the production-shaped problems that optic adaptation solves. The conceptual material, including the crucial distinction between the raw `Optic`-level operations and the typed routes (composition, `Iso`, `Lens.of`), lives in [Profunctor Optics](profunctor_optics.md); use this page when you need a copy-paste recipe.

---

## Working with Type-Safe Wrappers

**The Challenge**: You want to use string manipulation functions on wrapper types:

```java
// Strongly-typed wrappers
public record UserId(String value) {}
public record UserName(String value) {}
public record Email(String value) {}

@GenerateLenses
public record User(UserId id, UserName name, Email email, LocalDate createdAt) {}
```

**The Solution**: A single-field wrapper and its raw value are the textbook lossless pair, so give each wrapper an `Iso` and compose. `Lens >>> Iso = Lens`, so the full API survives:

```java
public class WrapperAdapters {

    // One Iso per wrapper: get unwraps, reverseGet rewraps
    public static final Iso<UserId, String> USER_ID_VALUE = Iso.of(UserId::value, UserId::new);
    public static final Iso<UserName, String> USER_NAME_VALUE = Iso.of(UserName::value, UserName::new);
    public static final Iso<Email, String> EMAIL_VALUE = Iso.of(Email::value, Email::new);

    // Composed lenses for User operations
    public static final Lens<User, String> USER_NAME_STRING =
        UserLenses.name().andThen(USER_NAME_VALUE);

    public static final Lens<User, String> USER_EMAIL_STRING =
        UserLenses.email().andThen(EMAIL_VALUE);

    // Usage examples
    public User normaliseUser(User user) {
        return USER_NAME_STRING.modify(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                return name;   // split("\\s+") on "" yields one empty token, and charAt(0) would throw
            }
            return Arrays.stream(trimmed.toLowerCase().split("\\s+"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(joining(" "));
        }, user);
    }

    public User updateEmailDomain(User user, String newDomain) {
        return USER_EMAIL_STRING.modify(email -> {
            String localPart = email.substring(0, email.indexOf('@'));
            return localPart + "@" + newDomain;
        }, user);
    }
}
```

The wrapper Isos are lawful (wrap-then-unwrap is the identity in both directions), so the law harness in `hkj-test` can hold them to it.

---

## Migration Patterns

Schema migrations are where adaptation earns its keep, but honesty about information loss decides the tool.

### Exposing the Old Schema's View of New Data

Version bridges are usually **lossy** (V1's `age` cannot reproduce V2's `birthDate`), so they are not Isos. The honest recipe is to build the V1-shaped view directly on V2 with `Lens.of`:

```java
public record PersonV1(String name, int age) {}

@GenerateLenses
public record PersonV2(String firstName, String lastName, LocalDate birthDate) {}

public class MigrationAdapters {

    // A V1-shaped "name" view over V2 data: reads join, writes split
    public static final Lens<PersonV2, String> V2_FULL_NAME =
        Lens.of(
            v2 -> v2.lastName().isEmpty()
                ? v2.firstName()                                 // no trailing separator, so set-then-get round-trips
                : v2.firstName() + " " + v2.lastName(),
            (v2, name) -> {
                String[] parts = name.split(" ", 2);
                return new PersonV2(
                    parts[0],
                    parts.length > 1 ? parts[1] : "",
                    v2.birthDate());
            });
}
```

Code written against "a person's name" keeps working during the migration, and nothing pretends the round trip through V1 preserves the birth date.

### Database Schema Evolution

The same recipe scales to entity migrations. The V1-shaped views live in one adapter class, so when the migration completes you delete one file:

```java
// Old database entity
public record CustomerEntityV1(Long id, String name, String email) {}

// New database entity
@GenerateLenses
public record CustomerEntityV2(
    Long id, String firstName, String lastName, String emailAddress, boolean active) {}

public class SchemaAdapters {

    public static final Lens<CustomerEntityV2, String> FULL_NAME =
        Lens.of(
            v2 -> v2.firstName() + " " + v2.lastName(),
            (v2, name) -> {
                String[] parts = name.split(" ", 2);
                return new CustomerEntityV2(
                    v2.id(),
                    parts[0],
                    parts.length > 1 ? parts[1] : "",
                    v2.emailAddress(),
                    v2.active());
            });

    public static final Lens<CustomerEntityV2, String> EMAIL =
        Lens.of(
            CustomerEntityV2::emailAddress,
            (v2, email) -> new CustomerEntityV2(
                v2.id(), v2.firstName(), v2.lastName(), email, v2.active()));
}
```

~~~admonish note title="When schemas really are equivalent"
If the two versions hold exactly the same information in different shapes, write the conversion pair as an `Iso` and compose old optics through it: `Iso >>> Lens = Lens`. Reach for the raw `Optic.dimap` bridge only when the pipeline is effectful and lives in `modifyF` anyway; see [Profunctor Optics](profunctor_optics.md#the-three-profunctor-operations).
~~~

---

## Complete, Runnable Example

[OpticProfunctorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/profunctor/OpticProfunctorExample.java) works each adaptation style end to end (hand-building with `Lens.of`, rather than the raw `Optic` operations), and it compiles and runs on every build:

- **The nested-source ("contramap") wish**: a `Person` first-name lens reaching through `Employee`, hand-built with `Lens.of`
- **Map-style**: a `LocalDate` lens exposed as a formatted-string lens (read formats, write parses)
- **Dimap-style**: a hobbies traversal driven across the `PersonDto` wire shape through `modifyF`, with the conversion pair supplied at the boundary
- **Wrapper integration**: a hand-built value lens reaching through a `UserName` wrapper
- **API adapter**: the full internal-model-to-DTO round trip

Run it from the repository to see each scenario's printed before/after output.

---

## Integration with Existing Optics

Adapted optics integrate exactly like the optics they wrap:

- **Typed adapters** (composition, `Iso`, `Lens.of`) are ordinary lenses and traversals: they compose with `andThen`, convert with `asTraversal()`/`asFold()`, and accept effects through `modifyF`, including accumulating validation with `Instances.validated(...)`.
- **Raw `Optic` bridges** (`contramap`/`map`/`dimap`) compose with other optics via `Optic.andThen` and run through `modifyF`. They do not offer `get`/`set` directly, which is precisely why the typed routes are preferred outside effectful pipelines.

This adaptability is what lets you keep well-tested optics through data-format changes without rewriting core business logic.

---

~~~admonish info title="Key Takeaways"
* **Wrappers want Isos**: `Iso.of(Wrapper::value, Wrapper::new)` composed after a lens keeps the whole API, lawfully
* **Lossy version bridges want `Lens.of`**: expose the old schema's view of the new data, and never call a lossy pair an Iso
* **Keep adapters in one place**: a migration adapter class is deleted in one commit when the migration lands
* **Effectful pipelines can use raw `dimap`**: an `Optic` bridge plus `modifyF` reuses domain logic across wire shapes
~~~

~~~admonish tip title="See Also"
- [Profunctor Optics](profunctor_optics.md): the concepts and the typed-versus-`Optic` distinction these recipes apply
- [Isomorphisms](iso.md): the lawful lossless conversions behind the wrapper recipe
- [Validated Prisms](validated_prism.md): the right home for conversions that can fail
~~~

---

**Previous:** [Profunctor Optics](profunctor_optics.md)
**Next:** [Java-Friendly APIs](ch4_intro.md)
