---
name: hkj-optics
description: "Generate and compose HKJ optics: @GenerateLenses, @GenerateFocus, @GenerateTraversals, @GeneratePrisms, @ImportOptics, @OpticsSpec. Focus DSL navigation, FocusPath, AffinePath, TraversalPath, Lens, Prism, Iso, deep immutable record updates, collection navigation. Also: ValidatedPrism (parse-don't-validate smart constructors), Edits multi-edit builder and REST PATCH, Update<S> and Monoids.update(), optic-path labelling (segments()/pathString())"
---

# Higher-Kinded-J Optics

You are helping a developer use HKJ's optics system for type-safe immutable data navigation. The library generates optics from annotations on Java records and sealed interfaces.

## When to load supporting files

- If the user asks about **which optics compose with which**, load `reference/composition-rules.md`
- If the user asks about **recipes for specific data patterns** (nested optionals, sum types, collections), load `reference/cookbook.md`
- If the user asks about **custom container types, SPI, TraversableGenerator**, load `reference/container-types.md`
- If the user asks about **indexed optics** (position-aware transforms), load `reference/indexed-optics.md`
- For **bridging optics with effects** (.focus() on paths, toEitherPath), suggest `/hkj-bridge`
- For **choosing Path types or Effect Path API**, suggest `/hkj-guide`
- For **record <-> DTO mapping, merging N sources, or generated validated assembly** (`@GenerateMapping`, `@GenerateMerge`, `@GenerateAssembly`, `@GenerateErrorEnvelope`), suggest `/hkj-mapping`

---

## Annotations Reference

| Annotation | Place On | Generates |
|------------|----------|-----------|
| `@GenerateLenses` | `record` | `{Record}Lenses` class with `Lens<S, A>` for each field |
| `@GenerateFocus` | `record` | `{Record}Focus` class with `FocusPath`/`AffinePath`/`TraversalPath` builders; a field holding another annotated record, or a `List`, `Optional` or other container of one, gets a navigator for cross-type chaining |
| `@GenerateTraversals` | `record` with collection fields | `{Record}Traversals` with `Traversal<S, A>` for collection fields; a component that holds elements but has no generator (`Deque`, `SortedMap`, a raw `List`) draws a processor note (a note, not a warning: there is no per-component opt-out) |
| `@GeneratePrisms` | `sealed interface` or `enum` | `{Type}Prisms` class with a `Prism<S, A>` for each permitted subtype or constant |
| `@GenerateIsos` | a `static`, no-argument method returning an `Iso` with concrete type arguments | a companion class holding that `Iso<S, A>` as a static field |
| `@GenerateGetters` | `record` | `{Record}Getters` class with `Getter<S, A>` for each field |
| `@GenerateSetters` | `record` | `{Record}Setters` class with `Setter<S, A>` for each field |
| `@GenerateFolds` | `record` | `{Record}Folds` class with `Fold<S, A>` for each field |
| `@GenerateForComprehensions` | `record` | For-comprehension-aware traversals |
| `@ImportOptics` | `package-info.java` or `interface extends OpticsSpec<S>` | Lenses for external types (JDK, Jackson, etc.) via auto-detection |
| `OpticsSpec<S>` | Interface an `@ImportOptics` spec interface extends directly | Fine-grained optics for external types with custom copy strategies |

### Copy Strategy Annotations (for `OpticsSpec`)

| Annotation | When To Use | Example |
|------------|-------------|---------|
| `@ViaBuilder` | Type has a builder pattern | `@ViaBuilder(toBuilder = "toBuilder")` |
| `@Wither` | Type has `withX()` methods | `@Wither(value = "withAmount", getter = "getAmount")` |
| `@ViaCopyAndSet` | Mutable type with setters | `@ViaCopyAndSet(setter = "setHost")` |
| `@ViaConstructor` | Reconstruct via constructor | `@ViaConstructor(parameterOrder = {"x", "y"})` |

`@ViaCopyAndSet` takes `copyConstructor = "com.example.BaseConfig"`, a fully qualified **supertype**, only when the copy constructor is overloaded; the default passes the source unchanged. `@ViaConstructor` reads its `parameterOrder` from the constructor's parameter names when you leave it out (`Point(int x, int y)` gives `{"x", "y"}`); write it when no constructor's names give one (a class compiled with neither `-parameters` nor `-g`), when two constructors give different orders, or when a longer constructor might hold more. Either way the order is held to the constructor the call binds: a wrong length, or a value passed where a parameter named after another accessor stands, is refused at the spec method. A written order that rebuilds through a shorter constructor, leaving out a value a longer one (or a record component) takes, draws a warning; annotate the lens method `@SuppressWarnings("shorter-constructor")` where that constructor works the value out on purpose. A `@ViaCopyAndSet` setter spelt `withX` has its result handed back where every method of that name the new value can be passed to returns the source type; a `set` method is called for its effect. A `withX` setter that may return the source type but cannot be kept warns, and `@SuppressWarnings("dropped-result")` on the lens method quiets it where the method sets the copy. `@Wither` calls the method with the new value typed by the lens's focus, so where the wither is overloaded the focus type chooses the overload, as javac would: pick the focus to match the overload you mean. The processor refuses, at the spec method, a name the type does not have, a focus no overload takes, a choice javac could not make, a static method, and one returning anything but the source type. Every other name a strategy carries is checked there too: the `getter` it reads through (the lens method's own name for `@ViaCopyAndSet` and `@ViaConstructor`), `@ViaBuilder`'s `toBuilder`/`setter`/`build`, and each accessor in a `@ViaConstructor` `parameterOrder`. A read or rebuild javac would report as `[unchecked]` is refused there as well: an accessor that reads a raw `List` for a `List<String>` focus, a focus written raw for a method that takes `List<String>`, a setter called through a raw builder whose erasure changes its parameter. Declare the lens over the raw type, with `@SuppressWarnings("rawtypes")` on the lens method, where the class offers only raw members.

`@ViaBuilder` works with Lombok's `@Builder(toBuilder = true)` and `@SuperBuilder(toBuilder = true)`; with Lombok's `@Getter`, name the getter, `@ViaBuilder(getter = "getName")`. A `@SuperBuilder` subclass needs `toBuilder = true` of its own, or the `toBuilder()` it inherits builds its parent. A lens over a `@Singular` collection replaces it: the generated lens calls the `clearTags()` Lombok writes beside the setter first. A protobuf-java message works the same way: `@ViaBuilder(getter = "getTagsList", setter = "addAllTags")` for a repeated field, or `getter = "getPointsMap", setter = "putAllPoints"` for a map field, and its lens calls `clearTags()` or `clearPoints()` first.

---

## Step-by-Step: Setting Up Optics

### 1. Annotate your records

<!-- verify -->
```java
@GenerateLenses
@GenerateFocus
public record Shopper(String name, Address address, Optional<String> nickname) {}

@GenerateLenses
@GenerateFocus
public record Address(String street, String city, String postcode) {}
```

`ShopperFocus.address()` returns a navigator: the `FocusPath<Shopper, Address>` itself, with a method
for each `Address` field, which is what makes the cross-type chains below
(`ShopperFocus.address().city()`) exist. A navigator goes wherever a path goes. Navigators are on by
default; `@GenerateFocus(generateNavigators = false)` turns them off for a record.

### 2. Use generated Focus classes

<!-- verify -->
```java
// FocusPath: exactly one focus (wraps Lens)
FocusPath<Shopper, String> namePath = ShopperFocus.name();
String name = namePath.get(shopper);
Shopper renamed = namePath.set("Alice", shopper);

// Chain through nested records
FocusPath<Shopper, String> cityPath = ShopperFocus.address().city();
String city = cityPath.get(shopper);

// AffinePath: zero or one (Optional/nullable fields)
AffinePath<Shopper, String> nicknamePath = ShopperFocus.nickname();
Optional<String> nickname = nicknamePath.getOptional(shopper);
```

### 3. Chain for deep navigation

<!-- verify -->
```java
// A List of annotated records gets a navigator over its elements, so each hop chains
TraversalPath<OrgChart, String> allNames =
    OrgChartFocus.units()   // a navigator, and a TraversalPath<OrgChart, OrgUnit>
        .members()          // a navigator, and a TraversalPath<OrgChart, OrgMember>
        .name();            // TraversalPath<OrgChart, String>

List<String> names = allNames.getAll(company);
OrgChart updated = allNames.modifyAll(String::toUpperCase, company);
```

Navigators are generated for **nested record** fields (`ShopperFocus.address().city()` above) and for
containers of one that the record widens: an `Optional`, `Maybe`, `List`, `Set`, `Collection` or
`Either` always, and a `Map`, an array or a third-party collection under `widenCollections = true`
or when `widenFields` names the field. Containers nested in one another compose to the record they
hold, so `List<Optional<OrgMember>>` navigates too, and so does a `Kind<F, A>` of records the processor traverses. A hop
that widens the path keeps navigating. Each navigator has a form nested in it for each wider path
type, `AffineForm` and `TraversalForm`. From `OrderHistoryFocus.orders()`, a navigator over a `List<Order>`,
`orders().customer()` returns `OrderFocus.CustomerNavigator.TraversalForm`, so `.email()` chains. A path
method such as `filter` returns a plain path, so chain the next hop with `.via(...)`.
A generic nested record gets a navigator too, instantiated with the
field's type arguments: `Revision<EmailAddress> email` chains as `.email().current()`. A field that
names no single type for its parameters, such as `Revision<?>` or a raw `Revision`, gets no
navigator, and the processor says so in a note naming the path its method returns. No
`.via(RevisionFocus...)` chain reaches inside that path either. Declare the field with concrete type
arguments (`Revision<EmailAddress>`) to get the navigator, or compose a lens you write from
`Revision<?>` itself, as the note offers:

```java
Lens<Revision<?>, Object> current =
    Lens.of(Revision::current, (revision, value) -> new Revision<>(value, revision.changedAt()));
Object value = ContactHistoryFocus.lastEdit().via(current).get(history);
```

A navigator method composes the static Focus method for the field it navigates to, so the two always
report the same path type: `ShopperFocus.address().postcode()` is whatever `AddressFocus.postcode()`
is, one source type further out. Here that is a `FocusPath`; it would be an `AffinePath` for an
`Optional` field and a `TraversalPath` for a `List`.

A nested record's field named like one of the path's no-argument methods, such as `last`, `head`,
`each` or `segments`, cannot be navigated to, since the call would reach the path's own method. On
a record that leaves navigators to the default, the field that reaches it keeps its plain path,
with a note; on one that writes `generateNavigators = true`, the processor refuses it. Rename the
field, or keep the navigating field a plain path with `excludeFields`. An unwidened `Map` or other
container of such records draws a note asking for the rename before `widenFields`. The same goes for a form: a
target field named `headOption` or `toTraversal` leaves out the `TraversalForm`, and `toAffine` the
`AffineForm`, so a hop into it from a wider path returns a plain path. The record declaring the
field gets a note naming the field to rename, if it writes `generateNavigators = true`.
Two fields whose names differ only in the case of their first letter, such as `items` and `Items`,
would name one navigator class. Where both would have one, the first in declaration order keeps
it; the later one keeps its plain path, with a note on the default or an error under
`generateNavigators = true`. An earlier field with no navigator leaves the name free. A message offering
a field a navigator, such as a note to widen a container, asks for one of the two to be renamed
first where its change alone would end there.

The names in `includeFields`, `excludeFields` and `widenFields` are checked against the record's
components: one that matches none is an error offering the component it meant. A list that changes
nothing warns: `excludeFields` beside a non-empty `includeFields`, `includeFields` or
`excludeFields` with `generateNavigators = false`, `widenFields` beside `widenCollections = true`, or a `widenFields`
name whose field is already widened (a `List`, an `Optional`) or holds no container (a `String`).
An `includeFields` name whose field has no navigator warns too: a `String`, a record without
`@GenerateFocus` or a container of one, or a `Map` of `@GenerateFocus` records left unwidened (add it
to `widenFields`). A field that already draws a note, such as a `Box<?>`, gets no warning.

The nested record may live in a dependency, provided that module ran hkj-processor: the navigator
composes the `Focus` class the dependency published, whichever processor version built it. A
dependency without the processor gives no navigator, and a field whose type is not on this
module's compile classpath is left out of the navigator; the processor says which in a note.

---

## The Three Focus Path Types

```
     FocusPath<S, A>          Exactly one focus (Lens)
           |
     AffinePath<S, A>         Zero or one focus (Affine)
           |
     TraversalPath<S, A>      Zero or more focuses (Traversal)
```

Path types widen automatically when navigating through optional/collection fields.

### FocusPath (Exactly One)

| Method | Return | Description |
|--------|--------|-------------|
| `get(S)` | `A` | Extract the focused value |
| `set(A, S)` | `S` | Replace the focused value |
| `modify(fn, S)` | `S` | Transform the focused value |
| `toLens()` | `Lens<S, A>` | Extract underlying optic |

### AffinePath (Zero or One)

| Method | Return | Description |
|--------|--------|-------------|
| `getOptional(S)` | `Optional<A>` | Extract if present |
| `set(A, S)` | `S` | Write the value; creates the focus when the last step can build it and every earlier step is present, else a no-op (use `modify` for if-present) |
| `modify(fn, S)` | `S` | Transform if present |
| `matches(S)` | `boolean` | Check if value exists |
| `toAffine()` | `Affine<S, A>` | Extract underlying optic |

### TraversalPath (Zero or More)

| Method | Return | Description |
|--------|--------|-------------|
| `getAll(S)` | `List<A>` | Extract all focused values |
| `modifyAll(fn, S)` | `S` | Transform all focused values |
| `setAll(A, S)` | `S` | Set all to same value |
| `count(S)` | `int` | Count focused elements |
| `toTraversal()` | `Traversal<S, A>` | Extract underlying optic |

### Every Path Is Labelled

All three carry the field names they were built from:

| Method | Return | Description |
|--------|--------|-------------|
| `segments()` | `List<String>` | The field-name segments, in navigation order |
| `pathString()` | `String` | Rendered location, e.g. `"customer.email"` |

`@GenerateFocus` emits the labelled factory `FocusPath.of(lens, "fieldName")`, and `.via(...)`
concatenates segments as you compose. `pathString()` renders the same way `FieldError.pathString()`
does, which is what lets a failed parse **locate itself** with no manual bookkeeping (see `Edits`
below).

Hand-rolling a labelled path from a raw lens:

<!-- verify -->
```java
FocusPath<LineItem, String> sku = FocusPath.of(LineItemLenses.sku(), "sku");
```

---

## Multi-Edit: `Edits` and `Update<S>`

`org.higherkindedj.optics.edit`: apply many edits to one structure in a single pass. This is the
REST-`PATCH` building block: a request where each field is *optionally* present.

The split to understand:

| Builder | Leaves | Returns | Meaning |
|---------|--------|---------|---------|
| `Edits.combine(...)` | `Edit<S>` | `Update<S>` | Infallible: just a composed `S -> S` |
| `Edits.accumulate(...)` | `FallibleEdit<S>` | `Edits.Accumulated<S>` | Each edit may fail; **all** failures collected |
| `Edits.accumulate(focus, ...)` | `FallibleEdit<A>` | `Edits.Accumulated<S>` | The same, written onto a `Lens<S, A>` focus that is set back once: a constructor checking fields against each other sees only the final values, and its refusal is an `Invalid` |

The two builders live on `Edits`; the **leaves live on `Edit`** (there is no `Edits.modify`). Either
qualify them, as below, or `import static org.higherkindedj.optics.edit.Edit.*;`.

<!-- verify -->
```java
// LineItem(String sku, Integer quantity, BigDecimal price)
// Infallible: normalise a few fields
Update<LineItem> normalise = Edits.combine(
    Edit.modify(LineItemFocus.sku(),   String::strip),
    Edit.modify(LineItemFocus.price(), price -> price.setScale(2, RoundingMode.HALF_EVEN)));

LineItem cleaned = normalise.apply(line);
```

<!-- verify -->
```java
// Fallible + sparse: a PATCH where any field may be absent (null = "not supplied")
// LineItemPatch(@Nullable String sku, @Nullable Integer qtyDelta, @Nullable String price);
// PriceParser.parse turns a String into a Validated<NonEmptyList<FieldError>, BigDecimal>
Validated<NonEmptyList<FieldError>, LineItem> patched =
    Edits.accumulate(
            Edit.parseIfPresent(LineItemFocus.price(), patch.price(), PriceParser::parse),
            Edit.modifyIfPresent(LineItemFocus.quantity(), patch.qtyDelta(), (d, q) -> q + d),
            Edit.setIfPresent(LineItemFocus.sku(), patch.sku()))
        .apply(line);
```

`Accumulated<S>` exits via `.apply(s)` (-> `Validated`), `.applyPath(s)` (-> `ValidationPath`, i.e.
straight into an effect pipeline), or `.toValidated()`.

**The leaf factories take a `FocusPath<S, A>` or a `Setter<S, A>`, never a raw `Lens`.** The
idiomatic source is a generated `@GenerateFocus` path, because its label is what locates a parse
failure. If you only have a `Lens`, wrap it: `FocusPath.of(lens, "sku")`.

| Leaf | Fails? | Notes |
|------|--------|-------|
| `Edit.set(path, value)` | No | Always applies |
| `Edit.modify(path, fn)` | No | Always applies |
| `Edit.setIfPresent(path, @Nullable value)` | No | Skipped when `null` |
| `Edit.modifyIfPresent(path, @Nullable b, (b, a) -> a)` | No | Skipped when `null` |
| `Edit.parseIfPresent(path, @Nullable b, parser)` | **Yes** | Skipped when `null`; returns `FallibleEdit<S>` |

Errors accumulate on `NonEmptyList<FieldError>`. This is a homogeneous fold, so unlike the assembly
builders there is **no arity ceiling**. `FallibleEdit.at("label")` **prepends** an outer segment (it does not replace the path);
a generated path already locates its own failures.

### `Update<S>`: the composition monoid

`org.higherkindedj.hkt.Update`: a named, reusable `S -> S` (`extends UnaryOperator<S>`), so it
drops into any `Function`-shaped API for free.

```java
Update<LineItem> normalise = Edits.combine(...);
Update<LineItem> full = normalise.andThen(applyDiscount);   // compose
Monoid<Update<LineItem>> m = Monoids.update();              // combine = "f then g", empty = identity
```

Gotcha: `andThen`'s argument must be typed `Update<S>` / `UnaryOperator<S>`. Pass a plain
`Function<S, S>` and Java selects `Function.andThen`, silently degrading the result to `Function`.

Fold many updates with `m.combineAll(updates)`: it applies them in a loop, where a long `andThen`
or `combine` chain nests one call per update and can overflow the stack.

---

## ValidatedPrism: Parse, Don't Validate

`org.higherkindedj.optics.validated.ValidatedPrism<S, A>`: the smart-constructor optic. `parse`
accumulates *reasons* for rejection; `build` is total.

Before hand-writing a leaf for a standard family (UUID, URI, dates, enums, BigDecimal, numbers,
boolean, Currency, Locale), check `StandardCodecs` in the same package: it ships lawful,
canonical-form codecs for all of them (see `/hkj-mapping`).

<!-- verify -->
```java
ValidatedPrism<String, EmailAddress> EMAIL =
    ValidatedPrism.of(EmailParser::parse, EmailAddress::value);

Validated<NonEmptyList<FieldError>, EmailAddress> parsed = EMAIL.parse(raw);
ValidationPath<NonEmptyList<FieldError>, EmailAddress> asPath = EMAIL.parsePath(raw);  // -> effects
String back = EMAIL.build(address);                                                     // total
```

| Method | Notes |
|--------|-------|
| `parse(S)` | `Validated<NonEmptyList<FieldError>, A>` (accumulates reasons) |
| `parsePath(S)` | Same, as a `ValidationPath` (straight into a pipeline) |
| `build(A)` | Total. An `A` is by construction renderable as an `S` |
| `parseAll(list)` / `buildAll(list)` | Bulk forms over a `List`; `parseAll` locates failures by index, `buildAll` is total |
| `parseValues(map)` / `buildValues(map)` | Bulk forms over a `Map`'s values; `parseValues` locates failures by key, `buildValues` is total |
| `toPrism()` / `toAffine()` | Downgrade to a plain optic |

Compose with another `ValidatedPrism` (short-circuits), an `Iso`, or a `Prism` (supplying the
`FieldError` for the no-match case). Build one from an existing optic with `ValidatedPrism.fromIso`
/ `fromPrism`, or wrap a throwing parser with `ValidatedPrism.canonical(message, parse, render)`:
the render defines the canonical form and the per-value guard rejects every spelling it cannot
reproduce, so the section law needs no hand-written check (render injectivity stays your
obligation — verify with `ValidatedPrismLaws`).

**You cannot compose a `ValidatedPrism` with a `Lens`, and that is deliberate**: a `Lens` has no
total `B -> S` build, so the result could not honour `build`. To validate sibling fields, use
`Validated.fields()` (see `/hkj-guide`), not prism composition.

---

## Collection Navigation Methods

| Method | On Path Type | Produces | Description |
|--------|-------------|----------|-------------|
| `.each(EachInstances.listEach())` | FocusPath on `List` | TraversalPath | Traverse all elements, rebuilding the list |
| `.each(EachInstances.setEach())` | FocusPath on `Set` | TraversalPath | Traverse all elements, rebuilding the set |
| `.each(EachInstances.collectionEach())` | FocusPath on `Collection` | TraversalPath | Traverse all elements, rebuilding a set as a set and anything else as a list |
| `.each(Each)` | FocusPath on custom container | TraversalPath | Traverse with custom Each instance |
| `.via(FocusPaths.listAt(index))` | FocusPath on `List` | AffinePath | Access specific index |
| `.via(FocusPaths.mapAt(key))` | FocusPath on `Map` | AffinePath | Access the value at a key |
| `.some(FocusPaths.optionalSome())` | FocusPath on `Optional` | AffinePath | Unwrap Optional |
| `.some(Affine)` | FocusPath on `Either`/`Try`/etc | AffinePath | Unwrap with custom Affine |
| `.nullable()` | FocusPath on `@Nullable` field | AffinePath | Handle null safely |
| `.filter(predicate)` | TraversalPath | TraversalPath | Keep only matching elements |
| `AffinePath.instanceOf(Class)` | Static factory | AffinePath | Safe downcast; compose via `.via()` |

The no-argument `each()`, `some()`, `head()`, `last()`, `tail()`, `init()`, `cons()`, `snoc()`, `headTail()` and `initLast()`, and `at(index)`, `atKey(key)` and `traverseOver(traverse)`, are deprecated for removal in 0.6.0: they compile on any focus and throw `ClassCastException` on the wrong one. Write the typed form above instead; a list step is `.via(FocusPaths.listHead())` and so on, and `traverseOver(t)` is `.each(EachInstances.fromTraverse(t))`. In existing code, the `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0` recipe rewrites them and marks the calls a typed form does not fit.

---

## Importing Optics for External Types

### Quick: `@ImportOptics`

```java
// package-info.java
@ImportOptics(java.time.LocalDate.class)
package com.myapp.optics;
import org.higherkindedj.optics.annotations.ImportOptics;
```

Generates `LocalDateLenses` with `year()`, `dayOfMonth()` and `dayOfYear()` by auto-detecting wither methods. A wither pairs only with a getter returning exactly what it takes, so there is no month lens: `withMonth(int)` meets `getMonth()`, which returns `Month`. A lower-case letter straight after `with` makes a word, not a wither (`withdraw`), unless a capital follows it, as in the JavaBeans `witheMail`, which pairs with `geteMail()` as `withEMail` does with `getEMail()`. The lens is named after what follows `with`, its first letter in lower case, so `withURL` gives `uRL()`.

### Full Control: `OpticsSpec`

`OpticsSpec<S>` is a marker interface. Declare an interface that extends it directly, annotate the interface with `@ImportOptics`, and declare the optics you want via abstract methods with `@InstanceOf` prisms:

<!-- verify -->
```java
@ImportOptics
public interface JsonNodeOpticSpec extends OpticsSpec<JsonNode> {
    @InstanceOf(ObjectNode.class)
    Prism<JsonNode, ObjectNode> asObject();

    @InstanceOf(ArrayNode.class)
    Prism<JsonNode, ArrayNode> asArray();

    @InstanceOf(StringNode.class)
    Prism<JsonNode, StringNode> asText();
}
```

(Jackson 3 renamed `TextNode` to `StringNode`, and moved to the `tools.jackson` packages.)

The processor generates a companion class with the requested prisms and lenses.

---

## Container Type Support

Fields of these types are automatically recognised by `@GenerateFocus`:

**Zero-or-one (-> AffinePath):** `Optional`, `Either`, `Try`, `Validated`, `Maybe`
**Zero-or-more (-> TraversalPath):** `List`, `Set`, `Collection`, `Map`, `T[]`, plus Eclipse Collections, Guava, Vavr, Apache Commons, PCollections types (31 total via SPI)

Nested containers like `Optional<List<String>>` are detected automatically with composed widening.

---

## Manual Lens Composition

When not using `@GenerateFocus`, compose optics manually with `andThen`:

<!-- verify -->
```java
Lens<Shopper, Address> addressLens = ShopperLenses.address();
Lens<Address, String> cityLens = AddressLenses.city();

// Compose: Shopper -> Address -> String
Lens<Shopper, String> shopperCityLens = addressLens.andThen(cityLens);
String city = shopperCityLens.get(shopper);
Shopper updated = shopperCityLens.set("Paris", shopper);
```

Optics compose according to the hierarchy: Lens + Lens = Lens, Lens + Prism = Affine, Lens + Traversal = Traversal, etc. See `reference/composition-rules.md` for the full matrix.

---

## Common Mistakes

1. **Expecting `@GenerateFocus` to need `@GenerateLenses`**: it builds its own lenses inline, so either annotation works alone. Add `@GenerateLenses` when you also want the classic `{Record}Lenses` class and its `withFoo` helpers.
2. **Wrong composition order**: `A.andThen(B)` means "first focus with A, then within that focus with B." Order matters.
3. **Using `map` instead of `focus`**: On Effect Paths, use `.focus(optic)` to navigate structure; `.map()` transforms the value. See `/hkj-bridge`.
4. **Forgetting to rebuild after adding annotations**: Generated classes appear in `build/generated/sources/annotationProcessor/`.
5. **Using `@GeneratePrisms` on a record**: Prisms are for sealed interfaces (sum types). Use `@GenerateLenses` for records (product types).
6. **A `private` type nested beside an annotated type**: the generated `{Record}Lenses`, `{Record}Focus` or other companion is a top-level class in the type's package, or in `targetPackage`, so the processor refuses a `private` record, component type or permitted subtype there (`cannot be reached from`). Leave the nested type package-private; under `targetPackage`, make it `public` or remove `targetPackage`.
