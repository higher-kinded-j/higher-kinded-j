# Check Your Understanding

_Ten questions on the Quickstart through the Capstone, each answer proved by the build._

These questions cover the pages from the [Quickstart](quickstart.md) to [the Capstone](capstone.md). They start with recall and end with writing specs of your own, and they do not follow page order, on purpose. Answer each question before you open its answer, in your head or on paper. Each answer ends with a link to the section that teaches it, and [Where to go next](#where-to-go-next) turns your score into a plan.

~~~admonish question title="Checkpoint 1: which direction can fail?" id="check-self-directions"
`PersonMappingImpl` has two methods, `build` and `parse`. Which of them can fail, and what does it hand back when it does?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-directions-answer"
**`parse`.** It returns a `Validated<NonEmptyList<FieldError>, Person>`: the domain value, or every bad field at once, each located by its path. `build` is total, so it returns the wire directly. The types say so:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/BasicsBook.java:basics_usage}}
```

Where this lives: [Your first mapping](basics.md#your-first-mapping).
~~~

~~~admonish question title="Checkpoint 2: the same leaf in every spec" id="check-self-mixin"
Every spec whose records carry an email repeats the same `default ValidatedPrism<String, EmailAddress> email()` method. Where do you declare that method once, so that every spec inherits it? And what does it do in a spec whose records have no email?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-mixin-answer"
**In a mix-in**: a plain interface holding the leaf, which each spec extends alongside `MappingSpec`. An inherited leaf counts as if the spec declared it wherever a component matches it, and stays inert where none does, so `TagMapping` is accepted too:

<!-- verify -->
```java
interface EmailVocabulary {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

record Lead(String name, EmailAddress email) {}

record LeadDto(String name, String email) {}

record Tag(String label) {}

record TagDto(String label) {}

@GenerateMapping
interface LeadMapping extends EmailVocabulary, MappingSpec<Lead, LeadDto> {}

@GenerateMapping
interface TagMapping extends EmailVocabulary, MappingSpec<Tag, TagDto> {}
```

Where this lives: [Shared vocabulary: mix-in interfaces](codecs.md#shared-vocabulary-mix-in-interfaces).
~~~

~~~admonish question title="Checkpoint 3: an `Optional` against a plain `String`" id="check-self-bridge"
Does `@GenerateMapping` accept this pair as written? Say what happens to a `null` phone, or what the processor asks you to add, and why.

<!-- verify:rejects "Add '@OptionalBridge java.util.Optional<java.lang.String> phone();' to the spec" -->
```java
record Guest(String name, Optional<String> phone) {}

record GuestDto(String name, String phone) {}

@GenerateMapping
interface GuestMapping extends MappingSpec<Guest, GuestDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-bridge-answer"
**Refused.** Of the two fixes the message offers, the bridge comes first:

```
Add '@OptionalBridge java.util.Optional<java.lang.String> phone();' to the spec
```

The processor will not guess that a `null` means *absent*: on most record wires a `null` really is a defect, reported as a located `must not be null`. So the bridge is declared one component at a time, in writing. The other fix, a leaf over the whole `Optional`, maps the pair but leaves `null` a located error, so only the bridge gives the field an absent state.

Where this lives: [Optional fields: `@OptionalBridge`](absence.md#optional-bridge).
~~~

~~~admonish question title="Checkpoint 4: write the leaves" id="check-self-leaves"
The processor refuses this spec. Write what it needs, using `StandardCodecs`.

<!-- verify:rejects "target field 'TicketDto.id' has no usable source. The types differ (java.lang.String vs java.util.UUID) and no matching leaf method was found. Found on Ticket: [id, priority]. Add 'default ValidatedPrism<java.lang.String, java.util.UUID> id()' to the spec." -->
```java
enum Priority { LOW, HIGH }

record Ticket(UUID id, Priority priority) {}

record TicketDto(String id, String priority) {}

@GenerateMapping
interface TicketMapping extends MappingSpec<Ticket, TicketDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-leaves-answer"
**Two leaves**, one for each component whose type differs on the wire. The processor never applies a codec on its own, so each conversion is declared. Each leaf is named after the domain component it parses, and its type arguments put the wire type first:

<!-- verify -->
```java
enum Priority { LOW, HIGH }

record Ticket(UUID id, Priority priority) {}

record TicketDto(String id, String priority) {}

@GenerateMapping
interface TicketMapping extends MappingSpec<Ticket, TicketDto> {
  default ValidatedPrism<String, UUID> id() {
    return StandardCodecs.uuid();
  }

  default ValidatedPrism<String, Priority> priority() {
    return StandardCodecs.enumByName(Priority.class);
  }
}
```

The refusal names the component and the leaf to add:

```
target field 'TicketDto.id' has no usable source. The types differ (java.lang.String vs
java.util.UUID) and no matching leaf method was found. Found on Ticket: [id, priority]. Add
'default ValidatedPrism<java.lang.String, java.util.UUID> id()' to the spec.
```

Where this lives: [Standard codecs](codecs.md#standard-codecs) and [Validated leaves](basics.md#validated-leaves).
~~~

~~~admonish question title="Checkpoint 5: predict the result" id="check-self-null"
`InvoiceMapping` nests `CustomerMapping`, whose `email` converts through a leaf. What does `InvoiceMappingImpl.INSTANCE.parse(new InvoiceDto("INV-2", new CustomerDto(null, "not-an-email")))` return?

1. It throws a `NullPointerException`
2. Invalid, with `customer.name: must not be null` only
3. Invalid, with `name: must not be null` and `email: not an email address`
4. Invalid, with `customer.name: must not be null` and `customer.email: not an email address`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-null-answer"
**4.** Every value `parse` reads is null-guarded, and a `null` becomes a located error beside every other bad field, never an exception. A nested spec's errors locate under the component that holds it:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:null_and_leaf}}
```

Where this lives: [Null has an address, not a stack trace](basics.md#null-doctrine) and [Nesting, containers, and recursion](structure.md#nesting-containers-and-recursion).
~~~

~~~admonish question title="Checkpoint 6: find the defect" id="check-self-leafname"
The processor refuses this spec for `Customer(String name, EmailAddress email)` and `CustomerDto(String name, String email)`. What is wrong, and how do you fix it?

<!-- verify:rejects "leaf 'emailAddress' names no component of Customer. A leaf is a zero-parameter 'default' named after the DOMAIN component it parses (or an inner component of a flattened one); an unmatched leaf would silently validate nothing." -->
```java
@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> emailAddress() {
    return EmailCodecs.EMAIL;
  }
}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-leafname-answer"
**The leaf is named after the type it produces, not the component it parses.** A leaf is found by its name, so `emailAddress()` matches nothing, and the message says what it expects:

```
leaf 'emailAddress' names no component of Customer. A leaf is a zero-parameter 'default' named
after the DOMAIN component it parses (or an inner component of a flattened one); an unmatched
leaf would silently validate nothing.
```

Rename it `email()`. A leaf declared on the spec must bind, because one that matches nothing would validate nothing, silently. A leaf inherited from a mix-in, as in Checkpoint 2, may stay inert instead.

Where this lives: [Validated leaves](basics.md#validated-leaves) and [Shared vocabulary: mix-in interfaces](codecs.md#shared-vocabulary-mix-in-interfaces).
~~~

~~~admonish question title="Checkpoint 7: predict the errors" id="check-self-invariant"
`Stay`'s constructor refuses a check-out that is not after its check-in, and `StayMapping` parses both dates with `StandardCodecs.localDate()`. A `ReservationDto` arrives with no guest. Its second stay checks in on `2026-03-09` and checks out on `07/03/2026`, meaning 7 March. Which errors does `ReservationMappingImpl.INSTANCE.parse` report?

1. `guest: must not be null` only
2. `guest`, and `stays.1: checkOut must be after checkIn`
3. `guest`, and `stays.1.checkOut: not an ISO-8601 date (expected e.g. 2026-07-28)`
4. All three
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-invariant-answer"
**3.** The constructor needs every component, so it runs only once they have all parsed. `checkOut` never parsed, so the invariant is never checked, while the missing guest still accumulates beside it:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:constructor_last}}
```

A record reports its components' errors or its invariant, never both.

Where this lives: [A record's own invariants](absence.md#constructor-invariants).
~~~

~~~admonish question title="Checkpoint 8: does the sealed dispatch compile?" id="check-self-sealed"
Each domain subtype has a spec. Does `PaymentMapping` compile? If it does, what does its `parse` do with each wire subtype? If it does not, what does the processor ask for?

<!-- verify:rejects "of 'PaymentDto' is never produced. parse must dispatch every wire subtype back to a domain subtype; this one has no mapping spec from any. Add a domain subtype and spec for it, or remove it from the sealed wire interface." -->
```java
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto, CashDto {}

record CardDto(String pan) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

record CashDto(String currency) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface BankMapping extends MappingSpec<Bank, BankDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-sealed-answer"
**It does not compile.** Sealed dispatch is exhaustive in both directions, so `parse` needs a domain subtype for every wire subtype. Nothing produces `CashDto`, so a `parse` that compiled would have nowhere to send one:

```
permitted subtype 'com.example.CashDto' of 'PaymentDto' is never produced. parse must dispatch
every wire subtype back to a domain subtype; this one has no mapping spec from any. Add a domain
subtype and spec for it, or remove it from the sealed wire interface.
```

Where this lives: [Sealed hierarchies](structure.md#sealed-hierarchies).
~~~

~~~admonish question title="Checkpoint 9: would you approve it?" id="check-self-trap"
A teammate used to MapStruct binds the mapper on the spec itself, so every caller can write `VisitorMapping.MAPPER`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java:trap_spec}}
```

It compiles, and the teammate's test, which calls `VisitorMapping.MAPPER.parse(...)`, passes. Would you approve it? If not, say what fails, and when.
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-trap-answer"
**No.** The Impl implements the spec, and the spec declares an instance method with a body (its email leaf). So initialising the Impl initialises the spec first. A program that reads `VisitorMapping.MAPPER` first is fine, which is why the test passes. A program that uses `VisitorMappingImpl.INSTANCE` first evaluates the constant while the Impl's `INSTANCE` is still `null`, and the constant keeps that `null` for good:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:trap_proof}}
```

Which class a program reaches first depends on its code paths, so the failure comes and goes. Bind the Impl in the calling code instead: a local, a field of the calling class, or an injected `ValidatedPrism`.

Where this lives: [Bind in the caller, not on the spec](basics.md#bind-in-the-caller).
~~~

~~~admonish question title="Checkpoint 10: map the shipment" id="check-self-create"
Write what maps this pair both ways. A bad parcel must be reported by its position in the list, and a `null` note must read as an absent one. Then say what path the client reads when the second parcel has no `sku`.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java:shipment_pair}}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-create-answer"
**Two specs**: one for the parcel pair, which lifts the list element by element, and one for the shipment. The shipment spec needs a `StandardCodecs` leaf for `id`, a rename from `parcels` to the wire's `items`, and a bridge for `note`. `sku` and `grams` match by name and type, so they need nothing:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java:shipment_spec}}
```

The client reads `parcels.1.sku`: error paths use the domain's names, even where the wire says `items`. The test checks all three requirements:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:shipment_proof}}
```

Where this lives: [Nesting, containers, and recursion](structure.md#nesting-containers-and-recursion), [Renames: `@MapField`](basics.md#renames-mapfield), [Standard codecs](codecs.md#standard-codecs) and [Optional fields: `@OptionalBridge`](absence.md#optional-bridge).
~~~

~~~admonish example title="See Example Code"
**The code on this page is [SelfCheckBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java) and [SelfCheckBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java)**: the page includes both directly, and the test asserts every result an answer predicts. The build also compiles every spec a question or answer shows, and checks each quoted refusal against the processor's own words. Open them after the ten, since they hold the answers.
~~~

---

## Where to go next {#where-to-go-next}

A question counts when every part of your answer was right before you opened it, and a spec you wrote counts when it declares the same members as the answer's. Whatever your score, open the *Where this lives* link of any answer you missed.

| Your score | Next step |
|---|---|
| 8 to 10, with Checkpoints 4 and 10 among them | You can ship a boundary. Read the rest of the chapter as a task calls for it: [What Your Spec Generates](tiers.md) for what a spec gets, and [Compiler Messages](compiler_errors.md) when the processor refuses one |
| Any other score from 5 to 9 | Reread the sections you missed, try those questions again, then carry on as for 8 to 10 |
| 4 or fewer | Go back to [Record Mapping Basics](basics.md) and read each page through to [the Capstone](capstone.md), working the [Boundary Mapping Journey](../tutorials/optics/boundary_mapping_journey.md) alongside, then take the questions again |

---

**Previous:** [Capstone: One 422, Every Bad Field](capstone.md)
**Next:** [What Your Spec Generates](tiers.md)
