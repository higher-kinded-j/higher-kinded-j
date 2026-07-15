# Book snippet verification

The book's code is kept honest in two ways. **Prefer the first.**

| | How | Guarantee |
|---|---|---|
| **1. Include** (preferred) | The page `{{#include}}`s an anchored region of a compiled example in this module | Drift is **impossible**: the page renders the code the build compiles and runs |
| **2. Verify marker** | The page marks a fence `<!-- verify -->`; the gate compiles a copy of it | Drift is **caught**: the build fails if the code stops compiling |

Use (1) whenever the snippet can be real, runnable code. It is strictly stronger, and a runnable
example can also prove the *output* comments a page asserts, which the compile gate cannot. Fall back
to (2) when a page needs a shape that cannot be a runnable example (an abstract signature, a
`VResultPath<E, A>` written against type variables).

The exact counts live in the two ratchets (`MINIMUM_INCLUDES`, `MINIMUM_VERIFIED_SNIPPETS`) rather
than here, so they cannot go stale. Today the only markers left are `path_vresult`'s catalogue of
shapes written against abstract type variables, which is the one thing an include cannot express.

The book-facing examples live under `org.higherkindedj.example.book.*`, **one package per page**: the
types must be top-level (so the processor generates the names the book teaches), and two pages that
both want a `User` would otherwise collide.

## Includes

Anchor the Java, and include it from the page:

```java
// ANCHOR: leaf_spec
@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> { ... }
// ANCHOR_END: leaf_spec
```

    ``` java
    {{#include ../../../hkj-examples/src/main/java/.../RecordMappingBook.java:leaf_spec}}
    ```

Two things matter:

- **Declare the types top-level, not nested in a holder class.** A nested spec joins its enclosing
  simple names, so `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`. The book teaches
  `CustomerMappingImpl`, which is only true at top level. Nesting also renders the snippet indented.
- **mdbook does NOT fail on a missing anchor.** It renders an empty code block and says nothing. So
  a typo silently deletes the code from the page. `BookIncludeTest` closes that hole: every include
  must resolve to a real file, a real anchor, and a non-empty one.


Compiles the code snippets in `hkj-book` against the real library, so the documentation cannot drift
away from the API without failing the build.

It lives in `hkj-examples` (`src/test/java/org/higherkindedj/book/`) rather than in a module of its
own, because this module already wires everything it needs: the annotation processor, `hkj-test`,
JUnit and AssertJ. It is also where the runnable examples the book cites already live.

`hkj-book` is not a Gradle module, so nothing used to compile its code. The snippets were
hand-maintained and they drifted: two shipped examples did not compile, one documented a method as
taking a type it does not take, and one printed an output it does not produce. A reader copying those
examples got a compiler error. A coding assistant reading them generated code that would not build.

The gate closes that hole. It runs with `hkj-examples`' tests, as part of `gradle build`, so CI enforces it.

## Marking a snippet

Put `<!-- verify -->` on the line before the fence. It is an HTML comment, so it is invisible in the
rendered book.

```markdown
<!-- verify -->
``` java
Validated<NonEmptyList<FieldError>, User> user =
    Validated.fields()
        .field("name", parseName(dto.name()))
        .apply(User::new);
```
```

Each marked snippet is compiled **independently**, with the real HKJ classpath and the real
annotation processor, so a `@GenerateMapping` or `@GenerateAssembly` snippet is checked against
genuinely generated code, not a stand-in.

Snippets are compiled separately rather than a whole page at once because a page's snippets are
illustrations, not one program: two of them may legitimately show different `User` records.

## Fixtures: what a page elides

A page usually omits imports and domain types so the snippet stays about the thing it is teaching.
Supply them from `hkj-examples/src/test/resources/fixtures/<page-slug>.java`, where the slug is the page path with
non-alphanumerics replaced by `_` (`monads/validated_assembly.md` -> `monads_validated_assembly`).

A fixture may declare:

- **imports**: hoisted into the snippet's compilation unit
- **types**: `record User(...) {}`, emitted as top-level types
- **a `Fixture` class**: the snippet's wrapper extends it, so `static` members are in scope and a
  snippet can call `parseName(dto.name())` bare, exactly as the page writes it

A type the snippet declares for itself shadows the fixture's, so a page may show its own `User`
without colliding.

The fixtures are `.java` for IDE support but are **resources, not sources**: their imports exist for
the snippet they are spliced into, so Spotless excludes them (an "unused import" cleanup would
silently break them; see the `targetExclude` in `build.gradle.kts`).

## What a snippet may be

The extractor works out what each block is, so a page can be written naturally:

| The block is | It becomes |
|---|---|
| loose statements | the body of a method |
| a type declaration (`record Order(...) {}`, a `@GenerateMapping` spec) | a top-level type |
| a whole method (`VResultPath<E, A> process(...) { ... }`) | a member of the wrapper |
| **only** body-less signatures (`EitherOrBoth<L, R2> flatMap(Semigroup<L> sg, ...);`) | an `interface`, where a body-less method is legal and still type-checked |

Signature quotations are exactly the lines that drift, so they are compiled rather than skipped.

A **generic** fixture (`class Fixture<E, A, B>`) lends its type parameters to the snippet, which is
how a page can show `VResultPath<E, A>` as a *shape* without inventing a domain for it.

## When a snippet cannot compile

A block is left unmarked only when it cannot be a compilation unit at all, and today none are. The
two that once were (`record_mapping`'s `@GenerateErrorEnvelope` hierarchy and its `editContext`
interface `default` method) are now `{{#include}}`d from a real example, where they compile
naturally.

Prefer fixing a snippet over excluding it. Three blocks looked like prose at first and turned out to
be worth rescuing: a pseudo-code placeholder (`EmailAddress addr = /* a valid domain value */;`), two
bare expressions with no statement around them, and a merge spec whose records the page never showed.
Making each of them real code both brought it under the gate and improved the page.

The gate is opt-in so that prose *can* stay prose, not so that awkward code can hide.

## The ratchet

`MINIMUM_VERIFIED_SNIPPETS` is a floor on the number of marked snippets. Deleting a marker to silence
a failure drops the count and fails the build. Raise the floor as pages are brought under the gate.

If a snippet genuinely can no longer be verified, lower the floor deliberately and say why in the
commit message. That should be rare, and it should be visible in review.
