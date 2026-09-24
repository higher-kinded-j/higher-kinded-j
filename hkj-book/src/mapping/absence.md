# Absent Fields and Record Invariants

_Let a field's `null` mean absent, and get a record constructor's refusal back as an error, not an exception._

`parse` turns every `null` it reads from the wire into an error that names the field ([Null has an address, not a stack trace](basics.md#null-doctrine)). This page covers two things that rule leaves open. Declare a component whose `null` means *absent* with `@OptionalBridge`, and the domain receives an empty `Optional`. And when a record's own constructor refuses a value, `parse` returns the constructor's message at the record's path instead of throwing. For a PATCH endpoint, where an omitted field keeps its current value, see [Sparse PATCH](beans_patch.md).

~~~admonish info title="What You'll Learn"
- Declare a field whose `null` means *absent* with `@OptionalBridge`, and predict what `parse` does with it
- Predict where a constructor's refusal is reported, and write its message for the client
~~~

~~~admonish example title="See Example Code"
**The code on this page is [AbsenceBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java) and its [AbsenceBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/AbsenceBookTest.java)** - the page includes them directly, so they are compiled and run by the build.
~~~

## Optional fields: `@OptionalBridge` {#optional-bridge}

Sometimes a wire `null` is not a defect: it is how the client says *this field is absent*. A domain `Optional<String> nickname` against a wire `String nickname` is the shape, and real record DTOs take it, because a JSON binder writes absence as `null`, not as an `Optional`.

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

The annotation has **two placements**, and one question decides which a component takes: does the present value need a leaf?

| The present element | Where the annotation goes | What it declares |
| --- | --- | --- |
| Copies as-is, or is mapped by its own spec | An abstract marker method named after the domain component | `@OptionalBridge Optional<String> nickname();` (the return type restates the component) |
| Converts through a leaf | That component's own `default` leaf | `@OptionalBridge default ValidatedPrism<String, EmailAddress> altEmail()`, declared over the **element** types |

The two can never be combined, and not by choice: a marker and a same-named leaf are one method with two incompatible return types, which javac rejects before the processor sees it.

An element pair that already has a `@GenerateMapping` spec needs no leaf. The marker is enough, and a present value [nests through that spec](structure.md#optional-nested-objects), exactly as an unbridged component of that pair would. A bridged `List`, `Set`, array or `Map` lifts its elements the same way, through their spec or through a leaf over the element types.

~~~admonish warning title="Opt-in, never inferred"
The processor will not guess this. Without the annotation, `nickname = null` is a located `must not be null`, exactly as [the null rule](basics.md#null-doctrine) says. That is the right default, since on most record wires a `null` really is a defect. The bridge is the one place a spec overrides it, one component at a time, in writing. On a [bean wire](beans.md) the bridge is automatic, and declaring it anyway draws a note, not an error ([`@OptionalBridge` on a bean wire is redundant](rules.md#optional-bridge-on-a-bean-wire)).
~~~

~~~admonish note title="Under `@NullMarked`"
`build` writes `null` into the bridged wire component for an absent value, so declare it to take one: `@Nullable String nickname`. [A bridged component must take `null`](rules.md#bridged-component-nullable) says which declarations the processor refuses, and where the annotation goes on an array.
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

The refusal names the bridge first, and offers the whole-`Optional` leaf second. They are not alternatives for the same job: a leaf maps the pair but leaves `null` a located error, so only the bridge gives the field an absent state. Declaring the annotation *on* such a leaf is refused, rather than silently ignored.
~~~

---

## A record's own invariants {#constructor-invariants}

A domain record often guards itself, with a compact constructor that throws when its components disagree. `parse` keeps that guard and still returns a value. Once every component of the record has parsed, the generated code calls its canonical constructor. A `RuntimeException` the constructor throws becomes a `FieldError` at the record's own path, carrying the exception's message:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:invariant_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/AbsenceBook.java:invariant_usage}}
```

The second stay fails at `stays.1`, and the missing guest is still reported beside it. The rules:

- **The record is the address.** A cross-field invariant belongs to no single component, so it locates where the record does: under the component that holds it (`stays.1`), or unlabelled at the top level.
- **The constructor runs last.** It needs every component, so it runs only once all of them have parsed. A record therefore reports either its components' errors or its invariant, never both, while everything around it keeps accumulating as usual.
- **Write the message for the client.** It is what the client reads. An exception without a message, or with a blank one, reads `not a valid Stay`.
- **Any `RuntimeException` counts, bugs included.** The null guard keeps a `null` out of the constructor, but a constructor that divides by zero fails the same way: its message goes to the client, and its stack trace is dropped. Keep the constructor to checks on its arguments.
- **Put a one-field rule in a [leaf](basics.md#validated-leaves).** It then locates at the field, and accumulates with the record's other errors.

Every generated surface that can return an error keeps this guard, and the few that cannot let the exception through: [Which surfaces a constructor's refusal reaches](rules.md#constructor-refusal-surfaces).

~~~admonish tip title="You can ship now"
You can now let a field's `null` mean absent, one component at a time, and let a record's constructor refuse a value with an error that says where. The checkpoints that follow test both.
~~~

~~~admonish question title="Checkpoint: a null and a bad value, both bridged" id="check-absence-bridge"
`MemberMapping` bridges both `nickname` and `altEmail`, and `altEmail` converts through the email leaf. What does `MemberMappingImpl.INSTANCE.parse(new MemberDto("Ada", null, "not-an-email"))` return?

1. Invalid, with `nickname: must not be null` and `altEmail: not an email address`
2. Invalid, with `altEmail: not an email address` only
3. Valid, with both fields empty
4. Valid, with `nickname` empty and `altEmail` holding the raw string
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-absence-bridge-answer"
**2.** A bridged `null` reads as absent, so `nickname` becomes `Optional.empty()` with no error. A present bridged value still goes through its leaf, so the bad `altEmail` fails, located:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/AbsenceBookTest.java:bridge_null_and_bad}}
```

Where this lives: [Optional fields: `@OptionalBridge`](#optional-bridge).
~~~

~~~admonish question title="Checkpoint: where does the refusal land?" id="check-absence-address"
`Stay`'s constructor refuses a check-out that is not after its check-in. The same reversed stay is parsed twice: once on its own with `StayMappingImpl`, and once as the second stay of a reservation. Where does the refusal land each time?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-absence-address-answer"
**Unlabelled on its own, and at `stays.1` in the reservation.** A cross-field invariant belongs to no single component, so it locates where the record does:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/AbsenceBookTest.java:invariant_address}}
```

Where this lives: [A record's own invariants](#constructor-invariants).
~~~

---

~~~admonish info title="Key Takeaways"
* **Absence is declared, never guessed**: `@OptionalBridge` opts one `Optional` component into reading `null` as absent, and on a record wire nothing else does
* **A record's own invariant is located too**: an exception from its constructor becomes a `FieldError` at the record's path, beside every other error
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
