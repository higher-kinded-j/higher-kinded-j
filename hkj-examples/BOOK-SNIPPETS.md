# Documentation verification

The book's code is kept honest in three ways. **Prefer the first.**

| | How | Guarantee |
|---|---|---|
| **1. Include** (preferred) | The page `{{#include}}`s an anchored region of a compiled example in this module | Drift is **impossible**: the page renders the code the build compiles and runs |
| **2. Verify marker** | The page marks a fence `<!-- verify -->`; the gate compiles a copy of it | Drift is **caught**: the build fails if the code stops compiling |
| **3. Diagnostic marker** | The page marks a fence `<!-- verify:rejects "…" -->` or `<!-- verify:reports "…" -->`; the gate compiles it and holds the compiler to what the page quotes | Drift is **caught** for code the page shows in order to say it is *refused*, which neither of the others can express |

Use (1) whenever the snippet can be real, runnable code. It is strictly stronger, and a runnable
example can also prove the *output* comments a page asserts, which the compile gate cannot. Fall back
to (2) when a page needs a shape that cannot be a runnable example (an abstract signature, a
`VResultPath<E, A>` written against type variables).

(3) is for the opposite kind of snippet: the shape a page shows to say the processor rejects it. See
[Marking a snippet the processor refuses](#marking-a-snippet-the-processor-refuses).

The exact counts live in the three ratchets (`MINIMUM_INCLUDES`, `MINIMUM_VERIFIED_SNIPPETS`,
`MINIMUM_DIAGNOSTIC_SNIPPETS`) rather than here, so they cannot go stale. Markers today cover `path_vresult`'s catalogue of shapes written against abstract type variables (the one thing an include cannot express) and short teaser snippets such as the optics Fundamentals payoff, whose fixture-backed domain would be noise in a runnable example

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


Compiles the code in the repo's documentation against the real library, so a page cannot drift away
from the API without failing the build. Two roots are covered:

- **`hkj-book/src`**, the book.
- **`.claude/skills`**, the Claude Code skills, which the build plugins install into consumer
  projects. These were the last documentation nothing compiled, and it showed: a code review found
  four undefined identifiers in them in a single pass (`emailPrism()`, `unlabelled`, `outOfStock`,
  `notFound`). A skill is read by an assistant that generates code from it, so a wrong snippet there
  becomes code that does not build in someone's project.

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

````markdown
<!-- verify -->
```java
Validated<NonEmptyList<FieldError>, User> user =
    Validated.fields()
        .field("name", parseName(dto.name()))
        .apply(User::new);
```
````

Each marked snippet is compiled **independently**, with the real HKJ classpath and the real
annotation processor, so a `@GenerateMapping` or `@GenerateAssembly` snippet is checked against
genuinely generated code, not a stand-in.

Snippets are compiled separately rather than a whole page at once because a page's snippets are
illustrations, not one program: two of them may legitimately show different `User` records.

## Marking a snippet the processor refuses

The pages documenting what the processor *rejects* are the ones a processor change is most likely to
invalidate, and a marker meaning "this compiles" cannot express them at all. That is how three
`@MatchWhen` examples came to recommend a shape that has never compiled (#755), on a page whose
seven `{{#include}}`s were correct throughout.

Two further markers close the hole. Both quote the diagnostic the page claims, and the quote is the
half that matters: "still refused" says nothing about the wording, and the wording is what rots when
a message is reworded.

````markdown
<!-- verify:rejects "which the test cannot narrow to" -->
```java
@ImportOptics
interface ShapeOpticsSpec<T> extends OpticsSpec<Shape> {

    @InstanceOf(Circle.class)
    Prism<Shape, Circle<T>> circle();
}
```
````

| Marker | What it asserts |
|---|---|
| `<!-- verify -->` | the snippet compiles, with no error and no warning |
| `<!-- verify:rejects "…" -->` | the snippet does **not** compile, and one of the errors quotes the fragment |
| `<!-- verify:reports "…" -->` | the snippet compiles, and a note or a warning quotes the fragment |

`verify:reports` is for the diagnostics that do not stop a build: `@GenerateTraversals` raises a
**note** for a container no generator claims, and `@GeneratePathBridge` a **warning** when no
`@PathVia` method survives. Both are documented behaviour, and a compile check sees neither.

A fragment must be at least ten characters, so it cannot be whittled down until it matches any
message at all. Quote the distinctive middle of the message rather than the `@Annotation:` prefix,
and prefer the concrete names your reproducer produces (`narrows to 'Card', which is not a 'Cash'`)
over the `'...'` placeholders a page's heading uses: the snippet is yours, so the message is
predictable.

Not every documented diagnostic can be reproduced by one snippet, and the exceptions fall into three
recognisable classes. Two entries on `compiler_errors.md` are not processor behaviour at all (a
"cannot find symbol" that means the processor never ran, and a sealed interface declared in a method
body, which plain Java forbids). Two need something a single compilation unit cannot set up: two SPI
providers on the annotation processor path, and a `@ViaCopyAndSet` supertype that has to be
package-private in a package the reproducer cannot name without writing `bookverify` onto the page.
One states a symptom rather than a shape. Those stay prose; everything else on the page carries a
reproducer.

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
without colliding. A fixture's *imports*, though, are hoisted into every unit, so a fixture must
never single-type-import a name a snippet declares - that is a duplicate declaration, not a
shadow. Import that package **on demand** instead (`import org.higherkindedj.example.order.error.*;`):
an on-demand import is shadowed by the declaration, and still resolves the name for every other
snippet on the page.

Where building a value would mean assembling half a domain to say nothing about the code on the
page, a generic stand-in says so:

```java
static final ValidatedOrder order = sample();

static <A> A sample() {
  throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
}
```

Snippets that quote a *real* example are the best case: `hkj-examples`' own main sources are on the
gate's classpath, so a page about `hkj-examples/src/main/java/.../market` can name those types
directly and drift from the example is a compile error.

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

A statement that opens a brace keeps everything until it closes, so an anonymous class written
inside one (an interpreter, a comparator) stays where the page put it rather than being hoisted out
as a member of its own. A method signature may wrap before its parameter list, which a generic
return type routinely does.

A **generic** fixture (`class Fixture<E, A, B>`) lends its type parameters to the snippet, which is
how a page can show `VResultPath<E, A>` as a *shape* without inventing a domain for it. The
parameters may be bounded (`class Fixture<G extends WitnessArity<TypeArity.Unary>>`), which is what
a page about a Free program written against an unknown witness needs; the wrapper declares the
bound and passes the parameter on by name.

## When a snippet cannot compile

A block that is *meant* not to compile is not one of these: it goes under `verify:rejects`, above.

A block is left unmarked only when it cannot be a compilation unit at all. The two that once were
(`record_mapping`'s `@GenerateErrorEnvelope` hierarchy and its `editContext` interface `default`
method) are now `{{#include}}`d from a real example, where they compile naturally.

One in the effect chapter still is: `effect_handlers.md`'s `boundSet()` snippet calls a wiring
class the reader writes for their own composition, and the page has none of its own to call. The
algebras and the generated support around it are gated; that one line is not.

Some shapes recur across the book and are left unmarked deliberately:

- **The Foundations one-liner.** `repo.find(id).toEitherPath().focus().attributes().at(key)...`
  appears on about fifteen pages as the book's running motif. `.focus()` takes an optic and there
  is no `at(key)` for a map (that is `FocusPaths.mapAt`), so it is a mnemonic for the layers, not
  code. Correcting it is an editorial decision about the motif, not a sweep.
- **Declarations of the library's own types.** `Maybe`, `Either`, `EitherF`, `Inject` and `Const`
  are quoted with `{ ... }` bodies, or with their methods left body-less, to show their shape. A
  sealed type that permits the library's own classes cannot be declared beside them, and the page
  needs the real type in every other fence, so it cannot shadow it either. `trampoline_monad.md`
  quotes the shape a blog post published, which is the same case. Every page in the type-class
  chapter opens the same way, quoting `Functor`, `Applicative`, `Monad`, `Selective`,
  `Alternative`, `MonadZero`, `Bifunctor`, `Profunctor`, `Foldable`, `Traverse`, `Natural`,
  `Semigroup` or `Monoid`; the worked examples below each quotation are gated.
- **A `static` extension method quoted as a signature.** `getters.md` quotes
  `public static <S, A> Maybe<A> getMaybe(Getter<S, A> getter, S source)`. A signature-only
  snippet is wrapped in an interface, where `static` demands a body, so the one shape that would
  make a body-less method legal is the one this signature cannot take. Every worked example of
  `getMaybe` below the quotation is gated.
- **A `@SafeVarargs` factory quoted as a signature.** `stream_monad.md`'s creation reference
  quotes `fromArray(T... elements)`. A signature-only snippet is wrapped in an interface, where
  the annotation the real declaration carries is not legal, and without it javac raises a
  mandatory heap-pollution warning. The other four reference tables on that page are gated.
- **Snippets against a dependency the gate does not have.** `context_scoped.md`'s SLF4J bridge
  (`LoggerFactory`, `MDC`) and `vstream_performance.md`'s JMH configuration name libraries that
  are not on the gate's classpath, and putting them there to compile two snippets would be the
  tail wagging the dog.
- **A stack the page invents to make a point.** `transformer_capstone.md` builds its
  three-layer `TestStack` through a `buildTestStack` helper that no module provides; it stands for
  the boundary wiring a reader would write, not for an API.
- **Code whose only diagnostic comes from the HKJ checker.** `compile_checks.md` opens with a
  `via` that mixes two Path types and a discarded `IOPath`. Both compile; what refuses them is the
  checker, and the checker is a *javac plugin*, which the gate does not ask for. Turning it on is
  worth doing - it fires correctly on both, and on a dozen other pages - but it is a change to what
  every gated snippet must satisfy, so it belongs in its own change rather than this sweep.
- **A generic varargs call the caller cannot make quietly.** `alternative.md` shows
  `orElseAll(first, () -> second, () -> third, ...)`. `Alternative.orElseAll` is a `default`
  method, so it cannot carry `@SafeVarargs`, and every call with three or more alternatives raises
  a mandatory heap-pollution warning. The page's `Iterable` overload is gated beside it.
- **A shape written over free type variables.** `selective.md` and `natural_transformation.md`
  write `Kind<F, Choice<Error, Data>>` and `Natural<F, G>` with nothing binding `F` or `G`: the
  point is the shape the operation has for *any* effect. A fixture can lend its type parameters to
  a snippet, but only one set, and the same fixture serves the concrete `IO` and `Maybe` examples
  on the same page.
- **A name the page binds to two different records.**
  `forstate_comprehension.md` names the same `userLens`, `addressLens` and `initialWorkflow` for
  its order workflow and its offer workflow. Snippets compile independently against one shared
  fixture, so a name can mean one thing per page; the offer workflow is gated and the order
  workflow, which the page elides the lenses of in a comment anyway, is not.
- **The exception-based version a migration page is leaving behind.**
  `migrating_to_functional_errors.md` shows each step twice: the throwing code first, the
  functional code after. Both halves are gated wherever they can be, because the "before" is
  ordinary Spring; four are not, because they call a `findById` that throws where the page's own
  service returns `Either`, or reach for `@Valid`/`BindingResult`, which the gate does not carry.
  The functional half of every one of them is gated.
- **The library's own auto-configuration, quoted.** `spring_boot_integration.md` shows
  `HkjWebMvcAutoConfiguration` with its `properties` field elided. It is the same case as a quoted
  sealed type: the real class cannot be declared beside itself.
- **A reference table written as bare calls.** `glossary/effect-paths.md` lists the `Path`
  factories one per line - `Path.maybe(nullableValue)`, `Path.right(value)` - as a table, not as
  code. A bare expression is not a statement, and binding fifteen of them to names would bury the
  table it is.
- **A wrong-then-right pair a troubleshooting page shows together.**
  `tutorials/troubleshooting.md` is built out of them: a X half that does not compile *because that
  is the point* ("won't work - local class", "NPE here"), and a tick half beside it, usually binding
  the same name. Splitting each into its own block would lose the juxtaposition that makes the page
  readable. Elsewhere in the book, where both halves are ordinary code, they ARE split and both are
  gated - see `migrating_to_functional_errors.md`.
- **A complete runnable file repeated at the end of a page.** `optics/lenses.md` closes by
  putting the whole worked example back together, model included. Every part of it is gated above;
  compiling the repeat would need a second copy of the same records in one unit, and two
  `@GenerateLenses` records of the same simple name cannot both emit their companion. The same
  page's `targetPackage` entry is unmarked for a related reason: a companion generated into another
  package needs its source type to be `public`, and a snippet's types share one file, where only
  one may be.
- **Laws written as equations.** `coyoneda.md` states the functor laws as
  `coyo.map(x -> x) == coyo`. The `==` is the law's notation, not a reference comparison, and
  rewriting it as an assertion would obscure what it says.
- **Aliases a page invents for a type Java cannot abbreviate.** `eitherf.md` writes
  `Free<Composed, RiskScore>`, where `Composed` stands for a four-deep `EitherF` nesting, and
  `Free.translate(program, inject::inject, functorG)` over free `G` and `A`. Both are there to
  show a shape; the gated fence beside each shows the same call with `var`.

Prefer fixing a snippet over excluding it. Three blocks looked like prose at first and turned out to
be worth rescuing: a pseudo-code placeholder (`EmailAddress addr = /* a valid domain value */;`), two
bare expressions with no statement around them, and a merge spec whose records the page never showed.
Making each of them real code both brought it under the gate and improved the page.

The gate is opt-in so that prose *can* stay prose, not so that awkward code can hide.

## The ratchet

`MINIMUM_VERIFIED_SNIPPETS` is a floor on the number of marked snippets. Deleting a marker to silence
a failure drops the count and fails the build. Raise the floor as pages are brought under the gate.

`MINIMUM_DIAGNOSTIC_SNIPPETS` is a second floor, on the `verify:rejects` and `verify:reports`
snippets alone. The total cannot protect those: swapping a rejection check for an easy positive
snippet elsewhere leaves it untouched, and they are the only thing holding the pages that document
refusals to what the processor actually says.

If a snippet genuinely can no longer be verified, lower the floor deliberately and say why in the
commit message. That should be rare, and it should be visible in review.
