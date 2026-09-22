---
name: hkj-mapping
description: "Compile-time data assembly and mapping with Higher-Kinded-J. Use whenever the task involves: mapping a domain record to/from a wire DTO, request, response, entity or payload (@GenerateMapping, MappingSpec, @MapField); mapping a sealed domain hierarchy to a sealed DTO hierarchy; hand-written toDto()/fromDto()/toDomain()/toEntity() mapper or converter classes; replacing MapStruct/ModelMapper/Dozer or a reflective bean mapper; building one target record by merging several source records (@GenerateMerge); constructing a record from independently-validated parts, collecting every error (@GenerateAssembly, fields(), accumulate()); or giving a sealed error hierarchy a structured envelope with a typed context record (@GenerateErrorEnvelope, ErrorEnvelope, editContext). Also covers the arity rules (the hand-written fields()/accumulate() ladder stops at 16; @GenerateAssembly has no ceiling), parse/build law-checking with MappingLaws, and -parameters."
---

# Higher-Kinded-J Compile-Time Mapping and Assembly

You are helping a developer replace hand-written mapper, builder and converter boilerplate with
generated, reflection-free, **law-checked** code.

The shared idea across all four generators: **`build` is total, `parse` is fallible.** Going from a
validated domain type out to the wire cannot fail. Coming from the wire in can, so it returns
`Validated<NonEmptyList<FieldError>, Domain>` and reports **every** bad field, not just the first.

## When to load supporting files

- If the user wants a **complete worked DTO boundary**, load `reference/mapping-example.md`
- For **optics themselves** (`@GenerateLenses`, Focus DSL, `Edits`, `ValidatedPrism`), suggest `/hkj-optics`
- For **`ValidationPath` and the railway model**, suggest `/hkj-guide`
- For **testing the generated code**, suggest `/hkj-test` (`MappingLaws`)

---

## Which Generator?

| You have | You want | Use |
|----------|----------|-----|
| One domain record + one wire DTO | Convert both ways | **`@GenerateMapping`** |
| One domain record + a PATCH request bean | Fold the present fields, leave the absent | **`@GenerateMapping`** on **`UpdateSpec`** |
| N source records | One target record | **`@GenerateMerge`** |
| One record + independently-validated parts | Construct it, collecting every error | **`@GenerateAssembly`** |
| A sealed error hierarchy | Codes, timestamps, and a typed context | **`@GenerateErrorEnvelope`** |

All live in `org.higherkindedj.optics.annotations`.

---

## `@GenerateMapping`: record <-> DTO

Declare the mapping as a **spec interface**; the processor writes the implementation.

<!-- verify -->
```java
@GenerateMapping
public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
//                                                   ^domain    ^wire
```

Generated: `CustomerMappingImpl`, reached through `INSTANCE`. When code calls it more than once,
bind it once, typed as the Impl (the spec interface declares no methods), and reuse it:

<!-- verify -->
```java
CustomerMappingImpl customerMapping = CustomerMappingImpl.INSTANCE;           // bind once, reuse
CustomerDto dto = customerMapping.build(customer);                            // total
Validated<NonEmptyList<FieldError>, Customer> back =
    customerMapping.parse(dto);                                               // accumulating
```

Where the binding lives: a local for a few calls in one method, a field in the class that owns the
boundary (`private static final CustomerMappingImpl CUSTOMER_MAPPING`), or an injected surface
(see *Injecting and faking* below). Never declare it as a constant on the spec itself; *Common
Mistakes* says why.

Naming rule: the generated class joins the spec's enclosing simple names, then appends `Impl`. A
top-level `CustomerMapping` gives `CustomerMappingImpl`; one nested inside `Shop` gives
`ShopCustomerMappingImpl`.

### Components are matched by name

Same name, same type on both sides: mapped automatically, no declaration needed. The spec only ever
says what is *not* obvious.

| Need | Declare on the spec |
|------|---------------------|
| Rename a field on the wire | an **abstract** method named after the domain component, annotated `@MapField(to = "fullName")` |
| A component that must be **parsed** (`String` -> `EmailAddress`) | a zero-arg `default` method named after the domain component, returning `ValidatedPrism<Wire, Domain>` |
| A wire-only field **derived** from the domain | a zero-arg `default` method returning `Getter<Domain, WireType>` |
| A domain `Optional<T>` against a **nullable record** wire component `T` | `@OptionalBridge` on an abstract marker named after the domain component, or on that component's leaf |
| A `Map` whose **keys** differ on the two sides | a zero-arg `default` method returning `ValidatedPrism<WireKey, DomainKey>`, annotated `@MapKey("component")` - the method's own name is free |
| A nested domain record against a **flat** wire (`Address` vs `street`, `city`, `postcode`) | `@Flatten` on an abstract marker named after the domain component; the record's components then map by name |
| A **bean accessor with no partner** that is meant to stay out (a read-only `getId()`, a computed getter, a setter the domain does not model) | `@Unmapped` on an abstract marker named after the **accessor's property**; it withholds the refusal and changes nothing else |

<!-- verify -->
```java
public record Customer(String name, EmailAddress email) {}
public record CustomerDto(String name, String email) {}

@GenerateMapping
public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {

  // Customer.email is an EmailAddress; the DTO carries a String.
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw -> raw.contains("@")
            ? Validated.validNel(new EmailAddress(raw))
            : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}
```

A leaf `ValidatedPrism` beats an identity match, so declaring one is how you say "this component
needs validating on the way in".

The standard conversion families need no hand-written leaf: `StandardCodecs` (in
`org.higherkindedj.optics.validated`) ships stock factories — `uuid()`, `uri()`, `localDate()` /
`localDate(DateTimeFormatter)`, `instant()`, `offsetDateTime()` (+ formatter overload),
`enumByName(Class)` (error names the permitted constants), `bigDecimal()`, `intFromString()`,
`longFromString()`, `doubleFromString()`, `booleanStrict()`, `currency()`, `locale()`:

<!-- verify -->
```java
import static org.higherkindedj.optics.validated.StandardCodecs.*;

@GenerateMapping
public interface OrderMapping extends MappingSpec<Order, OrderDto> {
  default ValidatedPrism<String, LocalDate> placedOn() { return localDate(); }
  default ValidatedPrism<String, OrderStatus> status() { return enumByName(OrderStatus.class); }
  default ValidatedPrism<String, BigDecimal> total()   { return bigDecimal(); }
  default ValidatedPrism<String, UUID> id()            { return uuid(); }
}
```

Every codec accepts only the canonical form it renders (the `ValidatedPrism` section law): a
case-folded UUID or scientific-notation number is a located rejection, never a normalisation.
For date-times that bites two common producers: a zero offset must be `Z` (`+00:00` is rejected)
and fractions render without trailing zeros (JS `toISOString()`'s `.000Z`/`.500Z` are rejected) —
serve both with the formatter overload (`offsetDateTime(ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX"))`
for JS, an `xxx` offset pattern for `+00:00`). The number/boolean codecs need **box-typed** domain
components (`Integer`, not `int` — a `ValidatedPrism<String, int>` cannot exist). Under the star
import, a leaf whose component shares a factory name (`currency`, `locale`, `uuid`) must qualify:
`return StandardCodecs.currency();` — the unqualified call recurses into the leaf itself.
A wire whose canon the stock codecs and formatter overloads don't cover (an uppercase-UUID
producer, say) keeps its canon lawfully via `ValidatedPrism.canonical(message, parse, render)`:
the lenient throwing parser is fine because the render defines the canonical form and every
spelling the render cannot reproduce is a located rejection (render injectivity stays your
obligation — verify with `ValidatedPrismLaws`).
Codecs are never applied implicitly; a conversion exists only where a spec declares it.

Renaming, and deriving a wire-only field:

<!-- verify -->
```java
@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "fullName")
  String name();                       // abstract: it is a declaration, not an implementation
}

@GenerateMapping
public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
  default Getter<Profile, String> displayName() {         // filled on build, ignored on parse
    return Getter.of(p -> p.first() + " " + p.last());
  }
}
```

### `Optional` against a nullable wire component: `@OptionalBridge`

On a **record** wire a `null` is an error by default, and that default stays. Where a wire component
genuinely encodes *absence* as `null` (Jackson's default for an optional field), mark the domain
`Optional` component and the pair maps both ways:

<!-- verify -->
```java
public record Member(String name, Optional<String> nickname, Optional<EmailAddress> altEmail) {}
public record MemberDto(String name, @Nullable String nickname, @Nullable String altEmail) {} // absent: null

@GenerateMapping
public interface MemberMapping extends MappingSpec<Member, MemberDto> {

  // Element copies, or its pair has its own spec: a bare abstract marker restating the component.
  @OptionalBridge
  Optional<String> nickname();

  // A leaf converts it: the SAME annotation on that component's leaf, over the ELEMENT types.
  @OptionalBridge
  default ValidatedPrism<String, EmailAddress> altEmail() {
    return ValidatedPrism.of(
        raw -> raw.contains("@")
            ? Validated.validNel(new EmailAddress(raw))
            : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}
// build: empty -> null, present -> the value.  parse: null -> Optional.empty(), value -> through its leaf or spec.
```

- The two placements **cannot be combined**: a marker and a same-named leaf are one method with
  incompatible return types, which javac rejects.
- An element pair with its **own spec nests through it**: `@OptionalBridge Optional<Address>
  address();` against a nullable `AddressDto` needs no leaf when an `AddressMapping` exists (a bean
  wire needs nothing at all), and failures locate inside (`address.city: must not be null`). An
  element leaf wins over the spec.
- A bridged **container lifts** as the unbridged one would: `@OptionalBridge Optional<List<Contact>>
  contacts();` against a nullable `List<ContactDto>` (an optional JSON array) lifts element by
  element through `ContactMapping` or an element leaf, and failures locate at the index
  (`contacts.1.email`). `Set`, arrays and `Map` values lift alike, and a `@MapKey` leaf converts the keys of a bridged `Map`. A container whose
  elements nothing converts is refused naming the element pair.
- **Never inferred.** Without the annotation the component is `must not be null`, as usual.
- A **bean** wire bridges automatically (bean conventions leave `Optional` off property types), so
  the annotation is redundant there and draws a note, not an error — one mix-in can serve both
  wire shapes.
- Rejected where it cannot mean anything: a non-`Optional` (or raw `Optional`) domain component,
  a primitive wire component, a leaf declared over the whole `Optional` rather than the element, a
  sealed mapping, and a locally declared one on an `UpdateSpec` (whose `null` already means *leave
  unchanged*). An already-`Optional` wire component needs no bridge, so that one is a note.
- A **write site declared non-null** is refused on either wire, since `build` would write the
  empty `Optional`'s `null` there: a record component, setter or builder-setter parameter carrying
  a non-null annotation (`@NonNull`, `@Nonnull`, `@NotNull`, Lombok's), or declared inside a
  JSpecify `@NullMarked` scope without `@Nullable`. Mark it `@Nullable` (any annotation named
  `Nullable` or `CheckForNull` counts; on an array, `String @Nullable []`); for a compiled class you
  cannot change, drop the `Optional` or declare a leaf over the whole `Optional`
  (`ValidatedPrism<String, Optional<String>>`), which wins over the bridge. A type-variable site
  follows its bounds: plain `<T>` under `@NullMarked` is non-null, `<T extends @Nullable Object>`
  bridges.
- **Inherited bridges stay inert** wherever they cannot apply, so one mix-in serves a record spec,
  a bean spec and a PATCH sibling.
- A present **container** is still scanned for null elements (`tags.1: must not be null`): the
  bridge excuses absence, not a null inside a value that was sent.
- A bridged component is a non-identity correspondence, so the mapping withholds `asIso()` and a
  bridged projection takes the validated `patch` rather than `asLens()`.

### Nesting and collections come free

A leaf prism declared for a component applies **elementwise** through `List`, `Set`, reference
arrays, `Optional` and a `Map`'s values (and, with `@MapKey`, its keys). A primitive array
(`int[]`) is copied whole - a `ValidatedPrism` cannot focus a primitive:

<!-- verify -->
```java
public record Team(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}
public record TeamDto(String name, List<String> members, Optional<String> lead) {}

@GenerateMapping
public interface TeamMapping extends MappingSpec<Team, TeamDto> {
  default ValidatedPrism<String, EmailAddress> members() { return emailPrism(); }
  default ValidatedPrism<String, EmailAddress> lead()    { return emailPrism(); }

  // `private static`, not `default`: a zero-arg `default` returning a ValidatedPrism is matched
  // BY NAME against a domain component, so a shared helper must stay out of that namespace.
  private static ValidatedPrism<String, EmailAddress> emailPrism() {
    return ValidatedPrism.of(
        raw -> raw.contains("@")
            ? Validated.validNel(new EmailAddress(raw))
            : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}
```

And a nested record resolves through its **sibling spec**, so this spec is empty and still maps
`Customer` <-> `CustomerDto` inside the invoice:

<!-- verify -->
```java
public record Invoice(String id, Customer customer) {}
public record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}   // nothing to say
```

Recursive records (a `Tree` of `Tree`) work too.

The sibling may live in a **dependency**: every generated Impl of a `MappingSpec` is accompanied
by an index entry (an empty class in `org.higherkindedj.mapping.index`, in the jar), and a
downstream compilation reads the index and resolves nested, sealed and merge pairs against those
specs exactly as against its own, generic ones included. Rules: the dependency must have been
compiled with `hkj-processor` on its processor path; a spec in the compilation shadows a classpath
spec for the same pair (a note names the shadowed one); two dependencies mapping one pair are
ambiguous, listed with `(classpath)` provenance, and a leaf delegating to the one meant settles a
nested component while a sealed subtype pair, which has no leaf, takes a spec of your own (it
shadows both); an entry whose Impl has gone missing (a partial build output, a jar that dropped
it) is never chosen and the failing use site names the dependency to rebuild; a spec extending a
mix-in, or a mix-in's supertype, that is off the consumer's compile classpath is never chosen
either, and the use site names the missing type (declare that module, or have the dependency
expose it with `api` rather than `implementation`); named modules (a
`module-info`) neither write nor read the index, not supported yet, so there the delegating leaf
is the route; and two spec-carrying jars must not be used as automatic modules together, the
shared index package being a split package there, so a library bound for such a module path
turns the index off with the processor option `-Ahkj.mapping.index=false` (no entries written,
none read).

### Flattening a nested record onto a flat wire

When the wire is flat where the domain nests (an `Address` record on the domain side, three plain
fields on a wire someone else fixed), no single wire component carries the address, so a leaf
cannot map it. `@Flatten` on an abstract marker named after the domain component spreads it by
**name**, both directions:

<!-- verify -->
```java
public record PostalAddress(String street, String city, String postcode) {}
public record Vendor(String name, PostalAddress address) {}
public record VendorDto(String name, String street, String city, String postcode) {}  // fixed, flat

@GenerateMapping
public interface VendorMapping extends MappingSpec<Vendor, VendorDto> {
  @Flatten
  PostalAddress address();   // build: street = domain.address().street() ...; parse: assembles it
}
```

`parse` assembles the record through its own `fields()` ladder inside the outer one, so failures
accumulate and locate under the DOMAIN path (`address.street: must not be null`), a name the flat
wire never sent. The whole vocabulary applies inside the group by name: a `@MapField(to = ...)`
rename named after an inner component (`String street();`) points it at another wire field, a
`default ValidatedPrism<...> postcode()` leaf converts one, an `@OptionalBridge` named after an
inner `Optional` bridges it, an inner record nests through its own spec, containers lift. An
all-identity group keeps `asIso()`; a mapping carrying a group nests in other specs like any other.
Names must be unambiguous: an inner component may not share a name with a domain component or
another group's (so two components of the same record type cannot both be spread), a derived field
may not be named after one, and a wire component named after the flattened one must be fed by a
rename from another component. Spreading is one level deep: a record inside the group nests
through its own spec, and a marker naming an inner component is refused. Not supported yet (each a
diagnostic): bean wires, generic specs, projections, sparse `UpdateSpec`s, and a group wider than
one `fields()` ladder (16).

Generic records map three ways. **Concrete instantiations** (`extends MappingSpec<Page<User>,
PageDto<UserDto>>`): components classify under the substitution, so leaves/nesting/containers and
the null doctrine apply unchanged. **Threaded specs** (`PageMapping<T> extends MappingSpec<Page<T>,
PageDto<T>>`): one generic Impl serves every instantiation via `PageMappingImpl.<T>instance()` (the
`EitherMonad.instance()` convention); same-variable elements copy by identity; multi-parameter and
bounded variables thread. **Element-mapped specs** (`Page<T> <-> PageDto<TDto>` with an abstract
`ValidatedPrism<TDto, T> items();` leaf): the generated Impl takes one prism per abstract leaf
through `XImpl.of(...)` (declaration order: the spec's own leaves, then each mix-in's in
`extends`-clause order, depth first; stateful, so no singleton). All three NEST: concrete
registrations directly, threaded specs by type-argument unification at the use site
(`PageMappingImpl.<String>instance()`, incl. a generic outer passing its own variable), and
element-mapped specs by composition (`of(entries())`, element pairs resolved via the using spec's
component-named leaf for single-leaf specs, else recursively via the registry; unresolvable pairs
diagnosed, failures
located through the composed path `entries.items.1`). All generic mappings are record-to-record
only (bean wires and `UpdateSpec` stay concrete). Raw and wildcard shapes are diagnosed; array
arguments are concrete and unify structurally. An abstract leaf on a concrete or sealed spec is
diagnosed (nothing defers its parser), and so is a leaf or rename declaring type parameters of
its own: a constructor-supplied field and a stub have nowhere to declare them.

### Shared vocabulary: mix-in interfaces

A spec may extend plain **mix-in interfaces** alongside `MappingSpec`/`UpdateSpec`; inherited
renames, leaves, derived fields, `@OptionalBridge` markers and `@Flatten` markers count as if
declared on the spec, collected transitively with Java's own precedence (a local override hides
the mix-in's member; a diamond counts once; unrelated mix-ins agreeing on an abstract rename or
marker fold into one stub returning the narrowest declared type, two renames that both bind and
disagree on the target are diagnosed naming both interfaces, and a group with no narrowest return
is refused naming every declaration). An inherited member binding to nothing stays inert; a local
one is an error. A leaf, bridge, `@MapKey` key leaf or `@Flatten` marker binds against the
extending spec's **domain** (the key leaf by the name in its annotation, the rest by the method
name); a derived field binds against its **wire**; a rename binds on both, and is inert when either
end is missing, so a projection, a PATCH bean covering a subset, or a sealed dispatch with no
components at all extends the same vocabulary. Only a `@Flatten` marker is refused rather than left
inert: on a sealed pair, where it cannot bind at all, and on an `UpdateSpec` whose PATCH bean
carries inner properties of the group that nothing else fills, where it binds but the sparse tier
has no edit for the spread. Interface statics are not
inherited.
Rejected with diagnostics naming the offender: a mix-in that is itself a mapping spec (directly
or transitively extends `MappingSpec`/`UpdateSpec`), and a generic mix-in reached raw (a generic
mix-in used with type arguments is read under the spec's instantiation and is fine). A member
whose type the spec's package cannot see is refused: the Impl is generated there and writes the
type out in full. `@GenerateMerge` specs still declare everything directly. Diagnostics about
inherited members name the declaring interface.

### Injecting and faking (Spring)

Register the surface you consume as a bean, not the spec or Impl: `ValidatedPrism<Wire, Domain>`
via `Impl.INSTANCE.asValidatedPrism()` (concrete; threaded specs use `instance()`, element-mapped
specs build the Impl once with `of(prisms)` in the `@Bean` method) for full mappings (they build and parse);
`ValidatedParse<Wire, Domain>` (`asValidatedParse()`) or `ValidatedBuild<Wire, Domain>`
(`asValidatedBuild()`) for a one-directional bean mapping; `Function`/`BiFunction` method
references for a bare `build`, `patch` and `updateFrom`. `ValidatedPrism` is SEALED, and so are
its two halves: fakes are built as values with `ValidatedPrism.of(...)` (or `ValidatedParse.of`,
`ValidatedBuild.of`), never mocked
(Mockito rejects sealed types). Spring resolves the generic type, so per-pair codecs coexist;
same-pair duplicates need `@Qualifier`. Injection is optional: concrete and threaded Impls are
stateless pure functions, element-mapped ones immutable values, and calling the Impl directly
(`INSTANCE`, `instance()`, or one shared `of(...)` instance) loses nothing but the substitution
seam.

### Sealed hierarchies dispatch exhaustively

A `MappingSpec` may be declared over two **sealed interfaces**, not just two records. Give each
permitted subtype pair its own spec; the parent dispatches over them:

```java
@GenerateMapping public interface CardMapping    extends MappingSpec<Card, CardDto> {}
@GenerateMapping public interface BankMapping    extends MappingSpec<Bank, BankDto> {}
@GenerateMapping public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}

// PaymentMappingImpl.build dispatches:
//   case Card v -> CardMappingImpl.INSTANCE.build(v);
//   case Bank v -> BankMappingImpl.INSTANCE.build(v);
```

A domain subtype with no spec, or a wire subtype nothing produces, is a **compile error**: the
dispatch cannot be partial. Each subtype must be one a spec can map: a record or a sealed
interface, or on the wire side a bean. A generic subtype, an enum, or any other class is not
supported yet.

### What else gets generated

Depending on what the mapping can honour:

| Mapping is | You also get |
|------------|--------------|
| Lossless both ways | `asIso()` |
| A lossy projection (domain -> wire only) | all-identity (on a bean wire, all-primitive too): `asLens()`; any fallible correspondence, or on a bean wire any reference property (it can be unset): validated `patch(domain, wire) : Validated<NonEmptyList<FieldError>, Domain>` (dense, the opposite of `UpdateSpec`'s sparse `updateFrom` below; null reads become located errors, a bridged `Optional` reads empty); **no** `parse` either way. Law-check: `MappingLaws.assertMappingLaws(Impl.INSTANCE::patch, Impl.INSTANCE::build, current, validWire, invalidWire)` (the valid wire must parse and change the domain) |
| Full (it builds and parses) | `asValidatedPrism()` |
| A bean wire with getters and nothing that writes it | `parse` + `asValidatedParse()`, **no** `build` |
| A bean wire that is written and declares no getter | `build` + `asValidatedBuild()`, **no** `parse` |
| Carrying **any** derived field | **no `asIso()`**: the round trip recomputes the derived component, so it is not an identity |

Combining a derived field **with** a projection (a wire otherwise smaller than the domain) is
rejected outright: the projection's `asLens()` write-back could never honour a component that
`build` recomputes; drop the derived methods, or widen the wire. A bean with no getters is another
matter: it maps build-only whatever its width, derived fields included.

### Things worth knowing

- **A null component read is a located `FieldError`, never an exception — on both wire shapes.**
  A JSON binder leaves a missing property `null` on a record component just as on an unset bean
  property, so every reference-typed `parse` read is null-guarded (`must not be null`, accumulating,
  locating through nesting: `customer.name: must not be null`). The doctrine reaches inside
  containers, each locating the way its container does: by index in a `List` or array
  (`emails.1: must not be null`), by key in a `Map`, and by the element's own rendering in a
  `Set` (`emails.nope`) - a null set element is the unlocated `emails: must not contain a null
  element`, a set holding at most one. An identity container is scanned at every level its type
  names (`grid.0.1` in a `List<List<String>>`, `byKey.k.1` in a `Map<String, List<String>>`), an
  `Optional` holding a container included, and however it is declared: any `Collection` or `Map`
  subtype or supertype (`ArrayList`, `Collection`, `LinkedHashMap`), raw, wildcard-argument, or a
  type variable bounded by one. A `@MapKey` map's copied values are scanned too. A class that
  fixes its own element (`class Node extends ArrayList<Node>`) is scanned one level into itself.
  `Iterable`, `Stream`, `Maybe` and `NonEmptyList` are not looked inside yet. A primitive array
  carries no element scan. What stays the caller's `NullPointerException`: a null *wire* itself,
  and a null map *key* (a structurally broken map, not a wrong value). A null container *component* is guarded like any reference read
  (`emails: must not be null`); only calling `parseAll`/`parseValues` directly with a null
  list/map is the caller's error. A lossless record mapping keeps `asIso()` (its guards cover hostile bindings, with
  parse-iso coherence scoped to non-null wires the domain accepts); a bean's guarded reads still cost the Iso tier,
  because an unset bean property is a representable state. The same guard covers every
  reference-typed source read on a `@GenerateMerge` `assemble`'s fallible path (a plain-return
  merge stays total by its declaration).
- **A domain record's own invariant is a located `FieldError` too.** Once every component has
  parsed, the canonical constructor runs, and a `RuntimeException` it throws (a compact
  constructor's `if (lo > hi) throw new IllegalArgumentException("lo > hi")`) becomes an error at
  the record's own path carrying the exception's message: `ranges.1: lo > hi` for a nested record,
  unlabelled at the top level, beside every error accumulated around it (`not a valid Range` when
  the message is missing or blank). It covers every surface that builds the record whole from
  parsed parts: `parse`, the validated `patch`, a flattened group, a fallible merge and
  `@GenerateAssembly`'s `assemble()`. A sparse `UpdateSpec` constructs once, from the values the
  PATCH ends on, and its `apply` reports the refusal unlabelled. The exception propagates from the
  total optics, `asIso().reverseGet` and a projection's `asLens().set`, and from the `Update` a
  sparse update's `toValidated()` hands back. Any `RuntimeException` counts, so a constructor bug reaches the client as its
  message, with the stack trace dropped: keep the constructor to argument checks with
  client-facing messages, and single-field rules in leaves, which locate at the field and
  accumulate with the record's other errors. For `MappingLaws`, a patch or parse-only rejection
  sample must fail on a component: a top-level invariant error is unlabelled by design.
- **A `Map` lifts its values, and its keys through `@MapKey`.** Without a key leaf the keys pass
  through by identity and their types must match. `@MapKey("component")` names the domain
  component (the method name is free, since the value leaf already owns the component's name):
  `@MapKey("notes") default ValidatedPrism<String, Tag> noteKey() { ... }`. Both a failing key
  and a failing value locate by the SOURCE key. Two keys parsing to one domain key are a located
  `duplicates an earlier key`; a `Set` that collapses is silent, because its survivors are equal.
  A leaf over the whole `Map` would pre-empt the key leaf, so a key leaf the spec declares is
  refused beside one, wherever it is declared; use a value leaf beside the key leaf instead.
- **Lifting needs the same container on both sides, named exactly, one level deep.** A `List`
  against a `Set`, or an array against a `List`, is a plain type mismatch, and so is an
  `ArrayList`, a `SortedSet`, a `Collection` or a wildcard element (`List<? extends Customer>`):
  declare the same exact container on both sides (`List<Customer>` against `List<CustomerDto>`)
  to lift. A leaf over the inner elements of
  `Optional<List<String>>` is not lifted twice. A record carrying an array component has identity
  `equals`, so `MappingLaws` cannot law-check it - assert the round trip elementwise.
- **The mapped record need not be yours.** The annotation sits on *your spec interface*, never on
  the record, so third-party and library records map fine.
- **The wire need not be a record.** A bean-shaped DTO maps too - detected in three shapes: a no-args
  constructor with `setX` setters; an immutable bean with a static `builder()`/`newBuilder()`
  (Lombok, Immutables, AutoValue, protobuf); or the JAXB convention, where a getter-only `List`
  is filled with `getItems().addAll(...)` - that one must name its element type, since `addAll`
  cannot be written over a raw or wildcard receiver, so a raw or wildcard getter-only `List` is
  refused on a mapping that builds (a sparse `UpdateSpec` only reads it, and keeps working).
  `build` fills through setters or the builder, `parse` reads
  through getters under the same null guard as a record wire, and a domain `Optional<T>` bridges to
  a nullable bean property `T` with no declaration (a record wire opts in per component with
  `@OptionalBridge`; an empty one writes `null`, so the setter or builder setter must take it,
  and one declared non-null is refused),
  except onto a getter-only `List`, which has no unset state to carry absence;
  see `reference/mapping-example.md`. A bean projection with a reference
  property takes the validated `patch` (the property can be unset); an all-primitive one keeps
  `asLens()`.
- **A bean crossed one way maps that way.** Getters and nothing that writes it: parse-only
  (`parse` + `asValidatedParse()`; every domain component needs a getter, extra getters are
  ignored, and a derived field declared there is refused). Writers and no getters: build-only
  (`build` + `asValidatedBuild()`; every writer needs a domain component or a derived field). A
  note names the direction. It nests only where its direction is used (a parse-only spec in a
  parse-only mapping, an `UpdateSpec` or a merge; a build-only spec in a build-only mapping), an
  `UpdateSpec` refuses either as its own PATCH bean, and a bean whose getters and setters never
  share a name is refused as a likely typo.
- **Accessors pair by name.** A property is a getter (`getX()`, or `isX()` returning `boolean` or
  `Boolean`) and a writer with the same name. An unpaired accessor is left out, which is fine for a
  computed getter or a builder's singular adder, but one named after a domain component the bean
  carries under no other name (its own, or a `@MapField` rename's) is refused on both tiers, with a
  near accessor of the other kind named as the likely misspelling. An `UpdateSpec` also refuses any
  `setX` setter with no getter, since the client's value would arrive and be ignored. Where the
  accessor is meant to stay out, `@Unmapped` on an abstract marker named after its property says so,
  and the mapping is generated exactly as it was before the marker.
- **A type another processor generates is waited for.** A wire or domain type, or a component,
  bean property, builder or mix-in method read from one, that another annotation processor writes
  in the same compilation (an Immutables value, a schema-generated DTO) does not exist until the
  round after it is written. The `@GenerateMapping` or `@GenerateMerge` spec is generated once it
  exists, and so is any spec nesting it. If it never appears, the spec generates nothing: javac
  names the missing type, and code using the spec's `Impl` reports the `Impl` missing too.
- **No component ceiling** on `parse`, the validated `patch`, or `@GenerateMerge`'s fallible
  merge: each is assembled via `Validated.fields()` ladders, chunked and combined applicatively
  past 16 legs, so a flat 20-30 field DTO maps without nesting. Error semantics are identical to
  a narrow mapping. The only width bound is the JVM's constructor parameter-slot limit on the
  record itself (254 components in practice, fewer with `long`/`double`).
- **It is law-checked.** Assert `build`/`parse` round-trip with `MappingLaws.assertMappingLaws(...)`
  from `hkj-test`.

### Sparse PATCH write-back: `UpdateSpec`

A REST `PATCH` body sends only the fields to change; every other property arrives `null`, meaning
*not provided, leave unchanged* - the opposite of a `parse`, where `null` is broken data. Opt into
that contract by extending **`UpdateSpec`** instead of `MappingSpec`. The wire must be **bean-shaped**
(a record component is always present, so it cannot signal absence).

<!-- verify -->
```java
@GenerateMapping
public interface UserPatchMapping extends UpdateSpec<User, UserPatchBean> {}
//                                                   ^domain ^PATCH request bean
```

The Impl exposes a *single* method, `updateFrom(Wire) : Edits.Accumulated<Domain>` - no
`build`/`parse`/`as*`. It folds the present (non-null) fields into an update and skips the absent
ones:

<!-- verify -->
```java
UserPatchBean patch = new UserPatchBean();
patch.setEmail("new@corp.example");                              // name absent -> unchanged
Edits.Accumulated<User> update = UserPatchMappingImpl.INSTANCE.updateFrom(patch);
Validated<NonEmptyList<FieldError>, User> updated = update.apply(user);  // or applyPath / toValidated
```

- **Present + valid** -> set (or parsed through its leaf), folded in. **Present + invalid** -> a
  located `FieldError`, accumulating. **Absent (null)** -> skipped.
- One spec names **one tier**: extending both `MappingSpec` and `UpdateSpec` is rejected, since one
  Impl carries one tier and the two emit disjoint members. A pair of tiers is a pair of specs
  sharing a mix-in.
- A **primitive** wire property is rejected (it can never be absent); use a wrapper type, which
  writes straight into a primitive domain component. The PATCH bean's constructor does not matter:
  `updateFrom` only reads it, so setters count beside a private no-args constructor (a bean with
  a builder keeps the builder as its writer). A domain
  `Optional<T>` bridged from a plain property is rejected too, `@OptionalBridge` declared on the
  sparse spec itself included ("set to empty" has no encoding, and `null` is already spoken for).
  An `Optional`-typed wire property expresses it instead: present-empty sets empty, absent (null)
  leaves unchanged. Caveats: Jackson binds an explicit JSON `null` to `Optional.empty()` (sent-null
  clears on this property shape), and the bean field must default to `null`, NOT `Optional.empty()`,
  or omitting the field clears the domain value.
- **Inherited vocabulary stays inert**: a bridge, a derived field, or a rename whose `to` this bean
  does not carry is never consulted here, so one mix-in serves a full spec and its PATCH sibling
  even when the bean covers a subset. A derived field the `UpdateSpec` declares itself is refused
  (there is no `build` for it to feed), as is a `@Flatten` marker it declares itself; an inherited
  `@Flatten` marker is refused only when this bean carries inner properties of the group that
  nothing else fills.
- A present **container** (a pair declared as exactly `List`/`Set`/reference array/`Optional`/
  `Map`) parses through the element leaf named after the component — the same vocabulary the dense tiers lift, so one mix-in
  serves a full spec and its PATCH sibling. Replacement is wholesale; each failing element is
  located (`phones.1`). A whole-container leaf (`ValidatedPrism<List<S>, List<A>>`) wins as the
  more specific declaration. Nested specs do not lift here — delegate via an element leaf to the
  nested Impl's `asValidatedPrism()`. Same-typed identity containers are null-scanned at every
  depth, as on the dense tiers (`tags.1: must not be null`; a set's unlocated, as `tags: must not
  contain a null element`), an `Optional` holding a container included. `@MapKey` applies here
  too.
- A **getter-only `List`** property is rejected here: its getter creates the list on first call,
  so it never reads `null` and an omitted field would clear the domain value. Give it a setter,
  and a getter that answers `null` until it is set (no initialiser, no list created on first call).
  (The dense tier keeps the same property - it writes every component, so absence means nothing
  there.)
- **Every PATCH bean getter must answer `null` until its property is set**, unchecked: no
  signature shows a default. A field initialiser (`private List<String> tags = new ArrayList<>()`,
  `private String status = "ACTIVE"`), a value the constructor or builder assigns, or a getter that
  creates one on first call reads as sent on every request that omits it, and `updateFrom`
  overwrites the domain value with the default. Leave the fields uninitialised and unassigned by
  the constructor, and let each getter return what was set; for a generated DTO, have the generator
  leave containers `null` (openapi-generator: `containerDefaultToNull`) and give the PATCH
  schema's properties no `default:`, which renders as an initialiser.
- Coverage is one-sided: a domain component with no wire property is simply never changed.
- The domain record is **constructed once**, from the values the PATCH ends on, so a constructor
  checking fields against each other (`lo <= hi`) never sees a half-applied PATCH: moving both ends
  of a range is `Valid`. A final value it refuses is an unlabelled `FieldError` carrying the
  exception's message (`not a valid Range` when it has none, or a blank one); `toValidated()`'s
  `Update` throws there instead, having no error channel. An empty PATCH hands back the current
  value without constructing. The generated fold is `Edits.accumulate(focus, ...)`.
- Law-check it with the sparse overload:
  `MappingLaws.assertMappingLaws(Impl.INSTANCE::updateFrom, current, absentWire, validWire, invalidWire)`,
  with `absentWire` a freshly constructed bean (what a binder makes of `{}`) and `current` unlike
  any default (non-empty containers), so a default the bean gives itself fails the identity law.
  `invalidWire` must fail a field: a constructor's refusal is unlabelled, so a leafless domain
  calls `MappingLaws.assertSparseIdentity` and `assertSparseIdempotent` on their own.

---

## `@GenerateMerge`: N sources -> one target

The spec is a plain interface, with no marker supertype. **The single abstract method signature *is*
the declaration.**

<!-- verify -->
```java
@GenerateMerge
public interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

Dashboard d = DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
```

Each component of `Dashboard` is filled from the source that has a component of that name. Merging
is **forward-only**: there is no inverse, because in general you cannot split a merged record back
into its sources. The types say so rather than pretending otherwise.

Two things are **compile errors**, by design:

- a target component that no source can fill
- a target component that *two* sources could fill (ambiguous)

For a fallible merge, return `Validated<NonEmptyList<FieldError>, Target>`:

```java
@GenerateMerge
public interface DashboardAssembly {
  Validated<NonEmptyList<FieldError>, Dashboard> assemble(User user, Account account);

  default ValidatedPrism<String, EmailAddress> email() { ... }   // leaf, as with @GenerateMapping
}
```

A component can also resolve through a sibling `@GenerateMapping` spec; nested failures then locate
themselves as dotted paths, e.g. `"customer.email"`.

---

## `@GenerateAssembly`: construct one record, collecting every error

Put it on the record. You get a staged builder with one method per component, in declaration order.

<!-- verify -->
```java
@GenerateAssembly
record SignupUser(String name, String email, int age) {}
```

<!-- verify -->
```java
Validated<NonEmptyList<FieldError>, SignupUser> result =
    SignupUserAssembly.fields()
        .name(Validated.validNel("Ada"))
        .email(Validated.validNel("ada@example.com"))
        .age(Validated.validNel(36))
        .assemble();          // every invalid field reported, not just the first
```

Generated class is `<Record>Assembly` (nested records join their enclosing names).

### Nested records compose

If a component's type is *itself* `@GenerateAssembly`-annotated, its companion's result drops
straight in, and the outer component name **prefixes the inner error paths** (`address.zip`):

```java
@GenerateAssembly record Address(String zip, String city) {}
@GenerateAssembly record Customer(String name, Address address) {}

Validated<NonEmptyList<FieldError>, Customer> customer =
    CustomerAssembly.fields()
        .name(parseName(dto.name()))
        .address(AddressAssembly.fields()          // a sub-companion's result, used directly
            .zip(parseZip(dto.zip()))
            .city(parseCity(dto.city()))
            .assemble())
        .assemble();                                // a bad zip reports as "address.zip"
```

**`@GenerateAssembly` does not support generic records.** Annotating one is a compile error; use the hand-written
`fields()` ladder for those, and for any record you cannot annotate.

**When to prefer the hand-written ladder instead.** `Validated.fields()` / `Validated.accumulate()`
(and their `Path` and `EitherOrBoth` twins, described in `/hkj-guide`) do the same job without annotating
the record. End a labelled ladder with `construct(Record::new, "not a valid Record")` rather than
`apply(Record::new)` when the constructor may refuse the fields: `apply` runs the function you hand
it, so the exception propagates, while `construct`, like `assemble()`, reports it as an unlabelled
`FieldError`. Use `@GenerateAssembly` when you want the component *names* checked by the compiler at
each stage; use the ladder for ad-hoc assembly or for a record you do not own.

**The ladder is capped at 16 components; `@GenerateAssembly` is not.** The generator emits a curried
`Validated.ap` chain at exactly the record's arity, so a 17-component record is fine. The ladder
stops at `ValidatedFields16`, whose `apply`/`construct` complete the assembly with no further `.field(...)`. So for a
wide record, the annotation is the answer, not the workaround.

---

## `@GenerateErrorEnvelope`: structured errors with a typed context

This is a **core + codegen** feature, not a web one. Put it on a sealed error hierarchy to give
every variant a code, a message, a timestamp, and a **typed context**.

```java
@GeneratePrisms
@GenerateErrorEnvelope
public sealed interface OrderError permits OrderError.OutOfStock, OrderError.PaymentDeclined {
  ErrorEnvelope<OrderErrorContext> envelope();

  default String code() { return envelope().code(); }
}

// The context is an ordinary record. Every component must be nullable.
record OrderErrorContext(OrderId orderId, TraceId traceId) {}
```

`ErrorEnvelope<C>` (`org.higherkindedj.hkt.error`) is a record
`(String code, String message, Instant timestamp, C context)`.

The processor generates an `OrderErrors` companion: per-variant factories, a fluent `context()`
builder, and `editContext`:

<!-- verify -->
```java
OrderError error = OrderErrors.editContext(
    OrderErrors.outOfStock(products),
    ctx -> ctx.orderId(orderId).traceId(traceId));    // attach context as it propagates
```

The typed context record **replaces the `Map<String, Object>` context bag**: records as schema, so a
typo is a compile error and the fields are discoverable.

Timestamps are read from an injected `TimeSource` (see `/hkj-arch`), which is what makes error
records deterministic in tests.

> **No Spring integration.** `hkj-spring` does not inspect `ErrorEnvelope`. Error -> HTTP status is
> still resolved by **class name** (`ErrorStatusCodeMapper` / `DefaultErrorStatusCodeStrategy`).
> Adding `@GenerateErrorEnvelope` does not change your status codes.

---

## Setup

Nothing beyond the standard processor wiring. If you use the Gradle or Maven plugin, **`-parameters`
is added for you**; do not set it by hand. `@GenerateMapping`'s copy strategies rely on it to read
constructor parameter names.

Processor diagnostics name the offending element, the reason, and the fix, so read the compile error
before rearranging the spec.

---

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Annotating the *record* with `@GenerateMapping` | It goes on the **spec interface**. That is what lets you map records you do not own |
| Declaring the instance on the spec, MapStruct-style (`CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;` inside `CustomerMapping`, or inside a mix-in it extends) | Compiles, but reads `null` (a `NullPointerException` at the first `MAPPER.parse(...)`) whenever the Impl is used before the constant is first read and the interface holding it declares a leaf, a derived field or any `private` helper: the JVM initialises that interface inside the Impl's own initialisation, before `INSTANCE` is assigned. Concurrent first use can deadlock instead. Bind it in the caller: a local, a `private static final CustomerMappingImpl` field there, or an injected surface |
| Expecting `parse` from a lossy projection | A projection drops data, so it cannot be inverted. You get `asLens()` (all-identity) or the validated `patch` (leaf-carrying, or a bean with a reference property), not `parse` |
| A PATCH request bean on `MappingSpec` | A bean smaller than the domain compiles as a projection whose `patch` is dense: an unset property is `must not be null`, and an unset bridged `Optional` clears the value. For null-means-keep, extend `UpdateSpec` |
| One spec extending both `MappingSpec` and `UpdateSpec` | Refused. One Impl carries one tier and the two emit disjoint members. Declare a spec per tier and share renames and leaves through a plain mix-in both extend |
| Expecting `build` from a getter-only bean | Nothing can write it, so it maps parse-only, and a note says why. Give it a no-args constructor with setters, or a builder, and it maps both ways |
| A read-only or write-only bean on an `UpdateSpec` | Refused: a sparse update reads `null` as absent, which only a bean that is written can leave unset, and a write-only bean has nothing to read |
| A misspelt accessor (`getEmial()` beside `setEmail(String)`) | The two do not pair, so neither is a property. Named after a domain component, the unpaired one is refused, and the diagnostic names the near accessor to rename. Pair every accessor the mapping uses, or mark a deliberate one `@Unmapped` |
| Expecting `@GenerateMerge` to give you a reverse split | Merging is forward-only by design |
| `Validated.fields()` will not take a 17th field | The **ladder** stops at 16. `@GenerateAssembly` has no ceiling, so annotate the record instead (`FOR_COMPREHENSION` is a separate ceiling, still 12) |
| A JAXB getter-only `List` on an `UpdateSpec` | Its getter creates the list on first call, so it never reads `null`: an omitted field would clear the domain list rather than leave it alone. Rejected; give the property a setter, and a getter that answers `null` until it is set |
| A PATCH bean that gives itself a default (a field initialiser such as `tags = new ArrayList<>()`, a constructor assignment, a getter that creates its value) | Not detected: the getter never answers `null`, so an omitted field reads as its default and `updateFrom` writes it over the domain. Let every getter answer `null` until set, and law-check with a freshly constructed bean as the all-absent wire and a current value unlike any default, which catches it |
| A bridged bean property whose setter refuses `null` (`List.copyOf(v)`, a protobuf or Immutables builder) | `build` writes `null` for an empty `Optional`. A setter declared non-null (`@NonNull`, or `@NullMarked` without `@Nullable`) is refused when it compiles: mark the parameter `@Nullable`. One that refuses `null` without declaring it throws from `build`: guard the copy (`v == null ? null : List.copyOf(v)`); for a generated builder, drop the `Optional` or declare a leaf over the whole `Optional` that encodes absence the builder's way |
| Bridging a domain `Optional<List<T>>` onto a JAXB getter-only `List` | The getter creates the list on first call, so absence has nowhere to live and would read back as a present empty list. Declare the component `List<T>`, where empty *is* nothing, or give the property both a setter and a getter that returns what the setter stored (a lazily creating getter loses absence on the read even with a setter) |
| Two nested specs generating the same `Impl` | Nested specs join their enclosing simple names; rename one |
| Assuming sealed hierarchies are unsupported | They are supported. Give each permitted subtype pair a spec; the parent dispatches |
| `@GenerateAssembly` on a **generic** record | Not supported. Use the hand-written `fields()` ladder |
| Expecting `asIso()` alongside a derived field | A derived field is recomputed on the round trip, so the mapping is not an identity |
| Adding `-parameters` manually | The plugin already does it |
| Assuming `@GenerateErrorEnvelope` changes HTTP status codes | It does not. Status is mapped by error **class name** |
