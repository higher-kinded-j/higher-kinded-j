# Database Records with JOOQ

_Get lenses for a JOOQ record, or any type that copies through a builder, wither or constructor._

> *"First, solve the problem. Then, write the code."*
>
> – attributed to John Johnson

The problem is not "how do I update a field in a JOOQ record". It is "how do I express a domain transformation clearly while respecting immutability". Builders solve the immutability half; optics solve the composition half. A copy strategy is how you tell the processor which builder-shaped door this particular type opens.

~~~admonish info title="What You'll Learn"
- Generate lenses for a builder-pattern type with `@ViaBuilder`, naming the getter, setter, `toBuilder` and `build` where conventions differ
- Choose `@ViaBuilder`, `@Wither`, `@ViaConstructor` or `@ViaCopyAndSet` from how a type makes a modified copy
- Traverse a collection field's elements with `@ThroughField`, and fix the refusal of a concrete container type
- Check a `@ViaCopyAndSet` lens with `LensLaws` where a shallow or supertype copy constructor can lose state
- Read through a type that already implements `List`, such as JOOQ's `Result`, with no strategy at all
~~~

---

## The JOOQ Pattern

JOOQ generates immutable POJOs that copy through a builder, and it is far from alone: Lombok's `@Builder`, Immutables, AutoValue, Protocol Buffers and most hand-written immutable classes do the same.

```java
// The chapter's Customer, as a POJO that copies through a builder
public final class Customer {
  public String name() { ... }
  public EmailAddress email() { ... }
  public Builder toBuilder() { ... }

  public static final class Builder {
    public Builder name(String name) { ... }
    public Builder email(EmailAddress email) { ... }
    public Customer build() { ... }
  }
}
```

There is no wither and no all-args constructor, so `@ImportOptics` auto-detection has nothing to latch onto. Declare the shape instead:

<!-- verify -->
```java
@ImportOptics
interface CustomerOpticsSpec extends OpticsSpec<Customer> {

  @ViaBuilder
  Lens<Customer, String> name();

  @ViaBuilder
  Lens<Customer, EmailAddress> email();
}
```

`@ViaBuilder` with no arguments assumes the common conventions: the getter and the builder setter are both named after the optic method, the builder comes from `toBuilder()`, and it finishes with `build()`. That is enough to generate real lenses:

<!-- verify -->
```java
Customer updated =
    CustomerOptics.email().set(new EmailAddress("ada@work.example"), ada);
// a copy of ada with her new email; ada itself is unchanged

String name = CustomerOptics.name().get(ada);
```

~~~admonish tip title="Why this matters"
Four annotated lines replaced a copy method per field, and what you get back is not a bespoke helper: it is a `Lens`, so it composes with every other optic in the library. With a `customer()` lens declared the same way, `OrderOptics.customer().andThen(CustomerOptics.email())` is a lens from an order to its customer's email. It obeys the lens laws as long as the builder round-trips faithfully: `toBuilder()`, the setter and `build()` have to give back the value they were handed and leave every other component alone. A builder that normalises, defaults or drops a field breaks that, and no annotation can detect it for you.
~~~

---

## Non-Standard Naming

Conventions vary, so every part of the interaction is nameable:

<!-- verify -->
```java
// Lombok: @Builder(toBuilder = true, setterPrefix = "with"), JavaBean getters
@ViaBuilder(getter = "getId", setter = "withId")
Lens<Order, UUID> id();

// A legacy type that spells all four differently
@ViaBuilder(
    getter = "getName",       // how to read the current value
    toBuilder = "newBuilder",  // how to obtain a builder
    setter = "setName",        // how to set on the builder
    build = "create")          // how to finish
Lens<LegacyType, String> name();
```

---

## Reaching Into Collections with `@ThroughField`

The chapter's `Order`, as the same kind of builder POJO, holds its lines in a `List`. A lens to a `List` field is rarely what you want; you want a traversal into its elements. `@ThroughField` composes the two, detecting the right element traversal from the field's type:

<!-- verify -->
```java
@ImportOptics
interface OrderOpticsSpec extends OpticsSpec<Order> {

  @ViaBuilder
  Lens<Order, List<LineItem>> lines();

  @ThroughField(field = "lines")
  Traversal<Order, LineItem> eachLine();
}
```

<!-- verify -->
```java
// Read every line's price
List<BigDecimal> prices =
    Traversals.getAll(
        OrderOptics.eachLine().andThen(LineItemLenses.price()), order);
// one price per line, in the order's line order

// Raise all of them by 5%
Order raised =
    Traversals.modify(
        OrderOptics.eachLine().andThen(LineItemLenses.price()),
        price -> price.multiply(new BigDecimal("1.05")).setScale(2, RoundingMode.HALF_EVEN),
        order);

// Only the ones already above a threshold
Order discounted =
    Traversals.modify(
        OrderOptics.eachLine()
            .andThen(LineItemLenses.price())
            .filtered(price -> price.compareTo(new BigDecimal("20")) > 0),
        price -> price.subtract(new BigDecimal("5")),
        order);
```

### `@ThroughField` Auto-Detection

| Lens focus for the field | Auto-detected traversal |
|--------------------------|-------------------------|
| `List<A>` | `Traversals.forList()` |
| `Set<A>` | `Traversals.forSet()` |
| `Collection<A>` | `Traversals.forCollection()` |
| `Optional<A>` | `Traversals.forOptional()` |
| `A[]`, `A` a reference type | `Traversals.forArray()` |
| `Map<K, V>` | `Traversals.forMapValues()` |

Detection reads the focus of the spec's own lens for the field, which is the lens the generated traversal composes with, so a spec that declares no lens for the field is refused. The match is on the interface itself. A lens focusing something narrower is refused at the declaration: a concrete container (`ArrayList<LineItem>`, `HashSet<Tag>`, `TreeMap<K, V>`, `HashMap<K, V>`) or another interface (`Deque`, `SortedSet`). Each standard traversal promises no more than the interface type, `forList()` hands back an unmodifiable `List`, and the field could not take that value back, so the generated traversal would throw `ClassCastException` on first use. An array of a primitive (`int[]`) is refused for the same reason, since the array traversal walks an `Object[]`.

Two routes get you past a refusal. Where the type is yours, declare the field as the interface. Otherwise name a traversal that rebuilds the declared type, built with `Traversals.forIterableCollecting(ArrayList::new)` for a list-shaped container or `Traversals.forMapValuesCollecting(TreeMap::new)` for a map, exposed as a static method:

<!-- verify -->
```java
@ThroughField(field = "entries", traversal = "com.example.CustomTraversals.forMyContainer()")
Traversal<MyType, Entry> eachEntry();
```

The method's declared focus is held to what the traversal hands back the same way: the element type, `Map`'s value type, or `Object` where the element sits behind a super- or unbounded wildcard. A focus that does not contain that type could only compile through a cast, which would throw `ClassCastException` on first use where it narrows and let ill-typed writes through where it widens, so it is refused at the declaration. A wildcard focus over the element is accepted, as in `? extends CharSequence` over `String` elements.

Where the lens focus names its own type arguments outright, with no wildcard among them (a nested `List<List<?>>` still qualifies), the auto-detected composition is generated with no cast and no suppression, so javac checks it end to end. An explicit `traversal` keeps the cast, as the author's undertaking that theirs rebuilds the declared type.

~~~admonish warning title="`Traversal` has no instance `modify`"
Reads and writes through a bare `Traversal` go through the `Traversals` utility: `Traversals.getAll(traversal, source)` and `Traversals.modify(traversal, f, source)`. The instance methods are `andThen`, `filtered`, `filterBy`, `asFold`, `modifyF`, `modifyWhen` and `branch`; `asFold()` is how you reach the read side, as in `traversal.asFold().foldMap(...)`. (A `TraversalPath` from the [Focus DSL](focus_dsl.md) does carry `getAll` and `modifyAll` directly, which is often the more comfortable surface.)
~~~

---

## The Other Three Strategies

~~~admonish warning title="Overloaded constructors, withers and setters"
Every strategy writes the focus through something that can be overloaded: the constructor `parameterOrder` describes, the `@Wither` method, the builder's setter, the setter `@ViaCopyAndSet` calls. A lens focuses a primitive boxed, and Java offers an overloaded call the candidates needing no unboxing first, so a `Long` reaches a `withAmount(Number)` before a `withAmount(long)`.

The focus is therefore unboxed to its getter's type wherever one of the candidates takes exactly that type. The call then binds where `new Money(source.amount(), source.currency())` would, every argument the type its own getter hands back. Where no candidate takes that type, as when the constructor takes the wrapper, unboxing could move the call somewhere the boxed focus never reached, so it is passed as it is.

That leaves one shape to watch: a getter returning `Long` where the constructor or method takes `long`, beside one taking `Number`, reaches the `Number` one and sets whatever it computes. Have the getters return the types the constructor and methods take, and run `LensLaws` over the lens.
~~~

### `@Wither`: types with `withX()` methods

<!-- verify -->
```java
@ImportOptics
interface MoneyOpticsSpec extends OpticsSpec<Money> {

  @Wither(value = "withAmount", getter = "getAmount")
  Lens<Money, Long> amount();
}
```

Naming both halves explicitly is what makes this strategy work where auto-detection cannot: `@ImportOptics` requires the getter's return type to match the wither's parameter exactly, and here you simply say which pair to use. The wither still has to hand back the source type, read under the arguments the spec gives it: on a `Draft<T>`, a `Draft<String> withId(String)` serves an `OpticsSpec<Draft<String>>`, and a wither that returns the type raw, or as a supertype, is refused at the spec method. Where the wither is overloaded, the one checked is the one the call binds: the lens passes the new value typed by its focus, `Long` here, and javac chooses among the overloads by that type. A name the type does not have, or that the generated class cannot call, one none of whose overloads takes the focus, a choice javac could not make, and a `static` method are each refused at the spec method too. See the [wither entries](compiler_errors.md#wither--has-no-method--for-the-generated-lens-to-call) in Compiler Errors.

Every other method name a strategy carries is checked the same way, at the spec method rather than in the generated file: the `getter` each one reads through, which for `@ViaCopyAndSet` and `@ViaConstructor` is the lens method's own name; `@ViaBuilder`'s `toBuilder`, `setter` and `build`, each read on what the step before it hands back; and each accessor a `@ViaConstructor` `parameterOrder` names. An accessor that reads a type the lens cannot hand back as its focus is refused too, which is the [`LocalDate.getMonth()` pairing](importing_optics.md#wither-classes-to-lenses) a spec interface exists to sort out.

### `@ViaConstructor`: constructor-only value types

<!-- verify -->
```java
@ImportOptics
interface PointOpticsSpec extends OpticsSpec<Point> {

  @ViaConstructor(parameterOrder = {"x", "y"})
  Lens<Point, Integer> x();

  @ViaConstructor(parameterOrder = {"x", "y"})
  Lens<Point, Integer> y();
}
```

`parameterOrder` names the getters to call, in the order the constructor takes them. It has an empty default in the annotation, but the generated code needs it: without it the optic throws `UnsupportedOperationException` when invoked, so treat it as required.

### `@ViaCopyAndSet`: legacy types with a copy constructor and setters

<!-- verify -->
```java
@ImportOptics
interface ConfigOpticsSpec extends OpticsSpec<Config> {

  @ViaCopyAndSet(setter = "setHost")
  Lens<Config, String> host();
}
```

~~~admonish warning title="Lens laws and mutable types"
`@ViaCopyAndSet` copies, then mutates the copy. That is lawful only if the copy constructor really copies everything: a shallow copy that shares a mutable field means a "set" can be seen through the original, which breaks the lens laws in the most confusing way possible. Verify with `LensLaws` on a type where this matters.

`copyConstructor` adds a second way to lose state, and it is quieter. Naming a supertype selects the constructor that takes it, and that constructor can only copy what it can see: if `balance` is declared on `Ledger` and you name `LedgerBase`, every `set` returns a copy with `balance` back at its default. That is a perfectly *deep* copy of everything in scope; the field is simply not in scope. The processor cannot check this for you, so the narrower the type you name, the more `LensLaws` is worth running.
~~~

`copyConstructor` is for the one case the default cannot express: an overloaded constructor. `new Config(source)` picks the most specific applicable overload, which is what you want almost always, since a lone `Config(BaseConfig other)` already takes the source by widening. Name a supertype, fully qualified, and the source is passed under that type instead:

```java
public class Endpoint extends BaseEndpoint implements Audited {
  public Endpoint(BaseEndpoint other) { ... }
  public Endpoint(Audited other) { ... }   // new Endpoint(source) is ambiguous
  public void setHost(String host) { ... }
}
```

<!-- verify -->
```java
@ImportOptics
interface EndpointOpticsSpec extends OpticsSpec<Endpoint> {

  @ViaCopyAndSet(copyConstructor = "org.higherkindedj.example.book.optics.BaseEndpoint",
                 setter = "setHost")
  Lens<Endpoint, String> host();          // new Endpoint((BaseEndpoint) source)
}
```

The name is a plain string, so it is not resolved against the interface's imports: give it fully qualified, the class alone with no type arguments, and a nested class as `com.example.Outer.Base`. Four names are rejected at the declaration rather than generating a cast javac cannot compile: one that does not resolve, one naming a type `Endpoint` does not extend or implement, one the generated class cannot see, and one no `Endpoint` constructor accepts. What the processor cannot check is whether the constructor it picks copies everything; see the warning above.

---

## When You Need No Strategy at All

JOOQ's `into(Customer.class)` maps a `Result`'s rows into a plain `List` of the POJOs, and a plain `List` needs no strategy: the standard traversals already cover it.

<!-- verify -->
```java
Result<CustomerRecord> rows = ctx.selectFrom(CUSTOMER).where(CUSTOMER.ACTIVE.isTrue()).fetch();
List<Customer> customers = rows.into(Customer.class);

// Read straight through the list traversal
List<EmailAddress> emails =
    Traversals.getAll(
        Traversals.<Customer>forList().andThen(CustomerOptics.email()), customers);
```

Writing through the same traversal gives back a new `List<Customer>`. To write into the `Result` itself, traverse its `CustomerRecord`s with `Traversals.forIterableCollecting(rebuild)`, which takes the rebuild as a function from the new list to the container.

---

## Choosing a Strategy

How a type already makes a modified copy picks its strategy; a List, Set, Map, Optional or array needs none:

```mermaid
flowchart LR
    accTitle: Which copy strategy a type needs
    accDescr: A type that copies through toBuilder, a field setter and build takes ViaBuilder; one with a withField method takes Wither; one with an all-args constructor takes ViaConstructor; and one with a copy constructor plus setters takes ViaCopyAndSet. A type that is already a List, Set, Map, Optional or array needs nothing but the standard traversals.
    Q{"How does the type<br/>make a modified copy?"}
    Q -->|"toBuilder()<br/>.field(x).build()"| B["@ViaBuilder<br/>JOOQ, Lombok,<br/>Immutables, AutoValue"]
    Q -->|"withField(x)"| W["@Wither<br/>java.time, Guava,<br/>Immutables"]
    Q -->|"an all-args<br/>constructor"| C["@ViaConstructor<br/>simple value objects"]
    Q -->|"a copy constructor<br/>plus setters"| S["@ViaCopyAndSet<br/>legacy mutable types"]
    Q -->|"it is already a List, Set,<br/>Map, Optional or array"| N["nothing:<br/>use the standard traversals"]

    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    class Q decision
    class B,W,C,S,N tier
```

Start with `@ViaBuilder`: it is the pattern most generated code uses. Fall back to the others when the type does not fit.

---

## The fine print: strategy parameters

~~~admonish note title="Every parameter, with its default"
```java
@ViaBuilder(
    getter = "",              // default: the optic method's name
    toBuilder = "toBuilder",
    setter = "",              // default: the optic method's name
    build = "build")

@Wither(
    value = "withName",       // required: the wither method
    getter = "")              // default: the optic method's name

@ViaConstructor(
    parameterOrder = {"x", "y"})   // effectively required, see above

@ViaCopyAndSet(
    copyConstructor = "",     // default: pass the source unchanged; else a fully qualified supertype of S
    setter = "setHost")       // required

@ThroughField(
    field = "items",          // required: the container field
    traversal = "")           // default: auto-detected from the field type
```
~~~

---

~~~admonish info title="Key Takeaways"
* **`@ViaBuilder` is the default choice**, and covers JOOQ, Lombok, AutoValue and Protobuf between them. Immutables generates both a builder and withers, so either strategy works there.
* **Every name is overridable.** Getter, builder accessor, setter and build method can each be spelled out when a library's conventions differ, and `@ViaCopyAndSet(copyConstructor = ...)` picks between overloaded copy constructors.
* **`@ThroughField` reaches into collection fields**, auto-detecting the traversal for a field declared as `List`, `Set`, `Collection`, `Map`, `Optional` or an array; a concrete container type is refused with the remedy named, and an explicit `traversal` covers it.
* **`Traversal` reads and writes through `Traversals`**, not through a plain instance `modify`; `andThen`, `filtered`, `filterBy`, `asFold`, `modifyF`, `modifyWhen` and `branch` do live on the optic.
* **Not everything needs a strategy.** A type that already implements `List`, `Map` or `Optional` works with the standard traversals for reads, though rebuilding the exact container type needs `forIterableCollecting`.
~~~

~~~admonish tip title="See Also"
- [Optics for External Types](importing_optics.md): `@ImportOptics` and what auto-detection covers
- [Taming JSON with Jackson](optics_spec_interfaces.md): spec interfaces for predicate-based type discrimination
- [Focus DSL with External Libraries](focus_external_bridging.md): bridging Focus navigation into these generated optics
~~~

~~~admonish tip title="Further Reading"
- **jOOQ**: [jooq.org](https://www.jooq.org/): type-safe SQL in Java, and its [immutable POJO generation](https://www.jooq.org/doc/latest/manual/code-generation/codegen-pojos/)
- **Lombok**: [projectlombok.org](https://projectlombok.org/): `@Builder`, `@Value`, and the `setterPrefix` option this page's naming examples assume
- **Protocol Buffers**: [protobuf.dev](https://protobuf.dev/reference/java/java-generated/): generated builders, a natural `@ViaBuilder` target
~~~

---

**Previous:** [Taming JSON with Jackson](optics_spec_interfaces.md)
**Next:** [Focus DSL with External Libraries](focus_external_bridging.md)
