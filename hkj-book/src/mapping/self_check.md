# Check Your Understanding

_Ten questions on the pages so far: answer each one before you open it._

These questions cover the pages from the [Quickstart](quickstart.md) to [the Capstone](capstone.md). They start with recall and end with writing specs of your own, and they mix the pages on purpose. Answer each one before you open its answer, in your head or on paper. Every answer is proved by the build, and ends with a link to the section that teaches it. [Where to go next](#where-to-go-next) turns your score into a next step.

~~~admonish example title="See Example Code"
**The answers include [SelfCheckBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java)** and **[SelfCheckBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java)**, so every result an answer predicts is asserted by a green test. The questions that show a spec compile it on every build.
~~~

~~~admonish question title="Checkpoint 1: which direction can fail?" id="check-self-directions"
`PersonMappingImpl` carries the two methods every full mapping gets, `build` and `parse`. Which of them can fail, and what does it hand back when it does?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-directions-answer"
**`parse`.** It returns a `Validated<NonEmptyList<FieldError>, Person>`: the domain value, or every bad field at once, each located by name. `build` is total, so it returns the wire directly. The types say so:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_usage}}
```

Where this lives: [Your first mapping](basics.md#your-first-mapping).
~~~

~~~admonish question title="Checkpoint 2: why not just guess?" id="check-self-bridge"
Does `@GenerateMapping` accept this pair? If not, what does it ask for, and why does it not read a `null` phone as absent on its own?

<!-- verify:rejects "Add '@OptionalBridge java.util.Optional<java.lang.String> phone();' to the spec" -->
```java
record Guest(String name, Optional<String> phone) {}

record GuestDto(String name, String phone) {}

@GenerateMapping
interface GuestMapping extends MappingSpec<Guest, GuestDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-bridge-answer"
**Refused.** The message offers the bridge first:

```
Add '@OptionalBridge java.util.Optional<java.lang.String> phone();' to the spec
```

On a record wire a `null` is a defect by default, and `parse` reports it as a located `must not be null`. Whether a `null` means *absent* is a fact about the endpoint's contract, which no type shows, so the spec says so in writing, one component at a time. Where this lives: [Optional fields: `@OptionalBridge`](absence.md#optional-bridge).
~~~

~~~admonish question title="Checkpoint 3: one leaf, many specs" id="check-self-mixin"
Every record pair in your API that carries an email needs the same email leaf. Where do you declare it once, so that every spec shares it? And what does that leaf do in a spec whose records have no email?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-mixin-answer"
**In a mix-in**: a plain interface holding the leaf, which each spec extends alongside `MappingSpec`. An inherited leaf binds wherever a component shares its name, and stays inert where none does, so `TagMapping` is accepted too:

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

~~~admonish question title="Checkpoint 4: write the leaves" id="check-self-leaves"
The processor refuses this spec. Write what it needs, using the stock codecs.

<!-- verify:rejects "has no usable source" -->
```java
enum Priority { LOW, HIGH }

record Ticket(UUID id, Priority priority) {}

record TicketDto(String id, String priority) {}

@GenerateMapping
interface TicketMapping extends MappingSpec<Ticket, TicketDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-leaves-answer"
**Two leaves**, one for each component whose type differs on the wire. Each is named after the domain component it parses, and reads wire type first:

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

The refusal names one component at a time, `id` first:

```
target field 'TicketDto.id' has no usable source
```

So a fix for `id` alone brings back the same message for `priority`. Where this lives: [Standard codecs](codecs.md#standard-codecs).
~~~

~~~admonish question title="Checkpoint 5: predict the result" id="check-self-null"
`CustomerMapping` converts `email` through a leaf. What does `CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, "not-an-email"))` return?

1. It throws a `NullPointerException`
2. Invalid, with `name: must not be null` only
3. Invalid, with `email: not an email address` only
4. Invalid, with both, in declaration order
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-null-answer"
**4.** Every value `parse` reads is null-guarded, and a `null` becomes a located error beside every other bad field, never an exception. Nothing stops at the first failure:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:null_and_leaf}}
```

Where this lives: [Null has an address, not a stack trace](basics.md#null-doctrine).
~~~

~~~admonish question title="Checkpoint 6: find the defect" id="check-self-typo"
The processor refuses this spec. Find the defect. Then say why a leaf declared on the spec must match a component, when the same leaf inherited from a mix-in, as in Checkpoint 3, need not.

<!-- verify:rejects "leaf 'emial' names no component of Customer" -->
```java
@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> emial() {
    return EmailCodecs.EMAIL;
  }
}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-typo-answer"
**The leaf is misspelt**, and the message says so:

```
leaf 'emial' names no component of Customer. ... Did you mean 'email()'?
```

A leaf the spec declares is there to validate one component, so one that matches nothing is a mistake: in the processor's words, it "would silently validate nothing". A mix-in serves specs whose records differ, so its leaf may bind in one spec and not in another. Where this lives: [How the two `default` families are told apart](rules.md#how-the-two-default-families-are-told-apart).
~~~

~~~admonish question title="Checkpoint 7: predict the errors" id="check-self-invariant"
`Stay`'s constructor refuses a check-out that is not after its check-in. This reservation has no guest. Its second stay was meant to leave on 7 March, before its 9 March arrival, but the client wrote that date as `07/03/2026`. Which errors does `ReservationMappingImpl.INSTANCE.parse` report?

1. `guest: must not be null` only
2. `guest`, and `stays.1: checkOut must be after checkIn`
3. `guest`, and `stays.1.checkOut: not an ISO-8601 date`
4. All three
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-invariant-answer"
**3.** The constructor needs every component, so it runs only once they have all parsed. `checkOut` never parsed, so the invariant is never checked, while the missing guest still accumulates beside it:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:constructor_last}}
```

A record reports its components' errors or its invariant, never both. Where this lives: [A record's own invariants](absence.md#constructor-invariants).
~~~

~~~admonish question title="Checkpoint 8: what is missing?" id="check-self-sealed"
This sealed pair has a spec for `Card` and none for `Bank`. Does `PaymentMapping` compile? If it did, what would its `build` have to do with a `Bank`?

<!-- verify:rejects "of 'Payment' has no mapping spec" -->
```java
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto {}

record CardDto(String pan) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-sealed-answer"
**It does not compile**, and the message says what to declare:

```
permitted subtype 'Bank' of 'Payment' has no mapping spec. ... Declare a @GenerateMapping spec
for 'Bank', here or in a dependency compiled with hkj-processor on its processor path.
```

A dispatch that compiled anyway would need a branch that throws for `Bank` at run time. Sealed dispatch covers every permitted subtype, in both directions, or it does not compile. Where this lives: [Sealed hierarchies](structure.md#sealed-hierarchies).
~~~

~~~admonish question title="Checkpoint 9: would you approve it?" id="check-self-trap"
A teammate used to MapStruct binds the mapper on the spec itself, so every caller can write `VisitorMapping.MAPPER`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java:trap_spec}}
```

It compiles. Would you approve it?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-trap-answer"
**No.** The Impl implements the spec, and the spec declares a method with a body, its email leaf, so initialising the Impl initialises the spec first. When a program uses `VisitorMappingImpl.INSTANCE` before it reads `VisitorMapping.MAPPER`, the constant is evaluated while the Impl's `INSTANCE` is still unassigned, and it keeps that `null` for good:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:trap_proof}}
```

Which class a program reaches first depends on its code paths, so the failure comes and goes. Bind the Impl in the calling code instead: a local, a field of the calling class, or an injected `ValidatedPrism`. Where this lives: [Bind in the caller, not on the spec](basics.md#bind-in-the-caller).
~~~

~~~admonish question title="Checkpoint 10: write the specs" id="check-self-create"
Write the specs that map this pair both ways. A bad parcel must be reported by its position in the list, and a `null` note must read as an absent one.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java:shipment_pair}}
```
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-self-create-answer"
**Two specs**: one for the parcel pair, which the list lifts, and one for the shipment, with a stock leaf for `id` and a bridge for `note`. `sku` and `grams` match by name and type, so they need nothing:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/SelfCheckBook.java:shipment_spec}}
```

The test holds both requirements: the bad parcel locates as `parcels.1.sku`, and a `null` note parses as `Optional.empty()`:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/SelfCheckBookTest.java:shipment_proof}}
```

Where this lives: [Nesting, containers, and recursion](structure.md#nesting-containers-and-recursion), [Standard codecs](codecs.md#standard-codecs) and [Optional fields: `@OptionalBridge`](absence.md#optional-bridge).
~~~

---

## Where to go next {#where-to-go-next}

Count the answers you got right.

- **8 to 10**: you can ship a boundary. The rest of the chapter is on demand: [What Your Spec Generates](tiers.md) when you need to know what a spec gets, and [Rules and Limits](rules.md) when the processor refuses one.
- **5 to 7**: open the *Where this lives* link of each answer you missed, reread that section, then try the question again.
- **4 or fewer**: go back to [Record Mapping Basics](basics.md) and read on in order to [the Capstone](capstone.md), then take the questions again.

---

**Previous:** [Capstone: One 422, Every Bad Field](capstone.md)
**Next:** [What Your Spec Generates](tiers.md)
