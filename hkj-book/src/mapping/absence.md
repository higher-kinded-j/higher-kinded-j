# Absent Fields and Record Invariants

_Let a field's `null` mean absent, and get a record constructor's refusal back as an error, not an exception._

`parse` turns every `null` it reads from the wire into an error that names the field ([Null has an address, not a stack trace](basics.md#null-doctrine)). This page covers two things that rule leaves open. Declare a component whose `null` means *absent* with `@OptionalBridge`, and the domain receives an empty `Optional`. And when a record's own constructor refuses a value, `parse` returns the constructor's message at the record's path instead of throwing. For a PATCH endpoint, where an omitted field keeps its current value, see [Sparse PATCH](beans_patch.md).

~~~admonish info title="What You'll Learn"
- Declare a field whose `null` means *absent* with `@OptionalBridge`, and predict what `parse` does with it
- Predict where a constructor's refusal is reported, and what the client reads
~~~

~~~admonish example title="See Example Code"
**The code on this page is [AbsenceBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java) and its [AbsenceBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/AbsenceBookTest.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Optional fields: `@OptionalBridge` {#optional-bridge}

Sometimes a wire `null` is not a defect: it is how the client says *this field is absent*. A domain `Optional<String> nickname` against a wire `String nickname` is the shape. The DTO keeps a plain `String`, since DTO fields rarely use `Optional`, so absence arrives as `null`. An omitted property and an explicit `"nickname": null` both arrive as `null`, so both read as absent.

Say so per component with `@OptionalBridge`, and the pair maps in both directions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:bridge_spec}}
```

```
  build : empty ──▶ null                parse : null ──▶ Optional.empty()
          present ──▶ the value                 value ──▶ Optional.of(value)
                                                          (through its leaf or spec, if any)
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:bridge_usage}}
```

Where the annotation goes depends on one question: does the value inside the `Optional` need a leaf?

| The value inside the `Optional` | Where the annotation goes | What it declares |
| --- | --- | --- |
| Copies as-is, or is a record with a spec of its own | An abstract marker method named after the domain component | `@OptionalBridge Optional<String> nickname();` (the return type restates the component) |
| Converts through a leaf | That component's own `default` leaf | `@OptionalBridge default ValidatedPrism<String, EmailAddress> altEmail()`, over the types **inside** the `Optional` |

Use one placement or the other: Java cannot declare both, since they share a name. A record with a spec of its own needs no leaf, since a present value nests through that spec: [Optional nested objects](structure.md#optional-nested-objects).

~~~admonish note title="Under `@NullMarked`"
`build` writes `null` into the bridged wire component for an absent value, so declare it to take one: `@Nullable String nickname`. [A bridged component must take `null`](rules.md#bridged-component-nullable) lists the declarations the processor refuses.
~~~

~~~admonish example title="The same pair without the annotation, refused"
<!-- verify:rejects "Add '@OptionalBridge java.util.Optional<java.lang.String> nickname();' to the spec" -->
```java
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;

record Reader(String name, Optional<String> nickname) {}

record ReaderDto(String name, String nickname) {}

@GenerateMapping
interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {}
```

The processor says:

```
@GenerateMapping: target field 'ReaderDto.nickname' has no usable source. The types differ
(java.lang.String vs java.util.Optional<java.lang.String>) and no matching leaf method was
found. Found on Reader: [name, nickname]. Add '@OptionalBridge
java.util.Optional<java.lang.String> nickname();' to the spec, so an absent value reads as a
null wire component and back. Add 'default ValidatedPrism<java.lang.String,
java.util.Optional<java.lang.String>> nickname()' to the spec.
```

The refusal names the bridge first, and offers a whole-`Optional` leaf second. The processor refuses the annotation on such a leaf rather than ignoring it ([`is declared over the whole Optional`](compiler_errors.md#bridged-leaf-over-whole-optional)). On a leaf, the annotation takes the types inside the `Optional`, as `altEmail` shows.
~~~

~~~admonish warning title="Not checked for you: only the bridge lets a field be left out"
On a record wire the processor never infers absence, since on most record wires a `null` really is a defect. Of the two fixes the refusal offers, only `@OptionalBridge` gives the field an absent state. The whole-`Optional` leaf compiles too, but a `null` still becomes `must not be null`, so the client can never leave the field out. That leaf suits a wire that spells absence another way, such as an empty string.
~~~

A [bean wire](beans.md) bridges automatically, so it needs no annotation: [`@OptionalBridge` on a bean wire is redundant](rules.md#optional-bridge-on-a-bean-wire). A bridged component also changes which methods the spec generates, since absence is a real correspondence, not a copy: [Where a bean or a bridged component lands](rules.md#where-a-bean-or-bridged-component-lands).

---

## A record's own invariants {#constructor-invariants}

A domain record often guards itself, with a compact constructor that throws when its components disagree. `parse` keeps that guard, but reports a refusal as an error instead of throwing it. Once every component of the record has parsed, the generated code calls its canonical constructor. A `RuntimeException` the constructor throws becomes a `FieldError` at the record's own path, carrying the exception's message:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:invariant_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:invariant_usage}}
```

`ReservationMapping` uses `StayMapping` for each stay without being told: [Nesting](structure.md#nesting-containers-and-recursion) explains how. The second stay fails at `stays.1`, and the missing guest is still reported beside it. The rules:

- **The record is the address.** A cross-field invariant belongs to no single component, so it locates where the record does: under the component holding it (`stays.1`), or at the top level, where the 422 renders an empty `"path": ""`.
- **The constructor runs last.** It runs only once every component has parsed. So a record reports its components' errors or its invariant, never both, and a client may meet the invariant on a second attempt.
- **Write the message for the client.** The 422 sends it verbatim, unlike the exception messages Spring Boot hides by default, so keep internal detail out. An exception without a message, or with a blank one, reads `not a valid Stay`.
- **Put a one-field rule in a [leaf](basics.md#validated-leaves).** It then locates at the field, and accumulates with the record's other errors.

~~~admonish warning title="Not checked for you: a constructor's bug reaches the client"
Any `RuntimeException` the constructor throws is reported this way, bugs included. The null check keeps a `null` out of the constructor, but a constructor that divides by zero fails the same way: the client reads the exception's message, and its stack trace is dropped. Keep the constructor to checks on its arguments.
~~~

Other generated methods that return errors report a refusal the same way, and the few that cannot return one let the exception through: [Which surfaces a constructor's refusal reaches](rules.md#constructor-refusal-surfaces).

~~~admonish tip title="You can ship now"
You can now accept requests that leave some fields out, and keep a record's own checks without turning a bad request into an exception.
~~~

~~~admonish question title="Checkpoint: where does `@OptionalBridge` go?" id="check-absence-bridge"
A patron may leave out a birthday, and one they send must be an ISO date that `StandardCodecs.localDate()` parses:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:patron_pair}}
```

Which declaration on `PatronMapping` does that?

1. `@OptionalBridge Optional<LocalDate> birthday();`
2. `@OptionalBridge default ValidatedPrism<String, LocalDate> birthday()`, returning `StandardCodecs.localDate()`
3. `@OptionalBridge default ValidatedPrism<String, Optional<LocalDate>> birthday()`, a leaf over the whole `Optional`
4. The same whole-`Optional` leaf, without the annotation
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-absence-bridge-answer"
**2.** The date inside the `Optional` needs a leaf, so the annotation goes on that leaf, over the types inside the `Optional`. The marker (1) is only for a value that copies or has a spec of its own, and nothing copies a `String` into a `LocalDate`. The processor refuses the annotation on a whole-`Optional` leaf (3). Without it (4), that leaf compiles but reads a `null` as `must not be null`, so the birthday could never be left out:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:patron_spec}}
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/AbsenceBookTest.java:patron_proof}}
```

Where this lives: [Optional fields: `@OptionalBridge`](#optional-bridge).
~~~

~~~admonish question title="Checkpoint: what does the client read?" id="check-absence-address"
`BulkDiscount` spreads a basket's discount over its items, and its constructor divides by `items`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:discount_spec}}
```

A client sends a discount with `items` set to `0`. What does `BasketMappingImpl.INSTANCE.parse(new BasketDto("B-7", new BulkDiscountDto(1000, 0)))` report?

1. Nothing: the `ArithmeticException` propagates out of `parse`
2. `discount: not a valid BulkDiscount`
3. `discount: / by zero`
4. `discount.items: / by zero`
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-absence-address-answer"
**3.** Any `RuntimeException` counts, bugs included, so the constructor's `ArithmeticException` becomes an error carrying its own message, `/ by zero`. The fallback `not a valid BulkDiscount` is only for an exception with no message. The record is the address, so the error sits at `discount`, the component holding the record, not at `items`:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/AbsenceBookTest.java:discount_proof}}
```

A client cannot act on `/ by zero`. Check `items` first, and throw with a message written for the client.

Where this lives: [A record's own invariants](#constructor-invariants).
~~~

---

~~~admonish info title="Key Takeaways"
* **Absence is declared, never guessed**: `@OptionalBridge` opts one `Optional` component into reading `null` as absent, and on a record wire nothing else does
* **A record's own invariant is located too**: an exception from its constructor becomes a `FieldError` at the record's path, beside the errors from the rest of the value, once its own components have parsed
~~~

~~~admonish tip title="See Also"
- [Sparse PATCH](beans_patch.md): When an omitted field should keep its current value, not become empty
- [Optional nested objects](structure.md#optional-nested-objects): A bridged component whose element has a spec of its own
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg): How these errors reach the client as one HTTP response
- [The null contract, precisely](rules.md#the-null-contract-precisely): What the null guard reaches, and which nulls stay the caller's bug
~~~

---

**Previous:** [Standard Codecs and Shared Vocabulary](codecs.md)
**Next:** [Nesting, Containers, and Sealed Hierarchies](structure.md)
