# Absent Fields and Record Invariants

_Declare the one field whose `null` means absent, and let a record's own constructor refuse a value._

[Record Mapping Basics](basics.md#null-doctrine) makes every wire `null` a located error. Two cases need more than that rule. A component whose `null` means *absent* is declared with `@OptionalBridge`. A domain record whose own constructor refuses a value still gets a located error, at the record's path.

~~~admonish info title="What You'll Learn"
- Declaring the one field where a `null` means *absent* instead, with `@OptionalBridge`
- How an invariant the domain's own constructor enforces reports, located at the record
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.
~~~

## Optional fields: `@OptionalBridge` {#optional-bridge}

Sometimes a wire `null` is not a defect: it is how the client says *this field is absent*. A domain `Optional<String> nickname` against a wire `String nickname` is the shape, and it is the shape real record DTOs take, because a JSON binder writes absence as `null` and not as an `Optional`.

Say so per component with `@OptionalBridge`, and the pair maps in both directions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:bridge_spec}}
```

```
  build : empty ──▶ null                parse : null ──▶ Optional.empty()
          present ──▶ the value                 value ──▶ Optional.of(value)
                                                          (through its leaf or spec, if any)
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:bridge_usage}}
```

The annotation has **two placements**, and which one a component takes is decided by one question: does the present value need a leaf?

| The present element | Where the annotation goes | What it declares |
| --- | --- | --- |
| Copies as-is, or is mapped by its own spec | An abstract marker method named after the domain component | `@OptionalBridge Optional<String> nickname();` (the return type restates the component) |
| Converts through a leaf | That component's own `default` leaf | `@OptionalBridge default ValidatedPrism<String, EmailAddress> altEmail()`, declared over the **element** types |

The two can never be combined, and not by choice: a marker and a same-named leaf are one method with two incompatible return types, which javac rejects before the processor sees it.

An element pair that already has a `@GenerateMapping` spec needs no leaf: the marker is enough, and a present value [nests through that spec](structure.md#optional-nested-objects) exactly as an unbridged component of that pair would. A bridged `List`, `Set`, array or `Map` lifts its elements the same way, through their spec or through a leaf over the element types.

~~~admonish warning title="Opt-in, never inferred"
The processor will not guess this. Without the annotation, `nickname = null` is a located `must not be null`, exactly as [the null rule](basics.md#null-doctrine) says, and that is the right default: on most record wires a `null` really is a defect. The bridge is the one place a spec overrides it, one component at a time, in writing.

A [bean wire](beans.md) needs no annotation: bean conventions leave `Optional` off property types, so the bridge is automatic there. Declaring it on a bean spec is redundant, and the processor says so with a note rather than an error, because a [shared mix-in vocabulary](codecs.md#shared-vocabulary-mix-in-interfaces) may legitimately serve both wire shapes.
~~~

~~~admonish note title="Under `@NullMarked`"
The bridged wire component is nullable by construction: `build` writes `null` into it for an absent value, so it must be declared to take one. Declare it `@Nullable String nickname`; [A bridged component must take `null`](rules.md#bridged-component-nullable) says which declarations the processor refuses, and where the annotation goes on an array.
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

The refusal names the bridge first, and offers the whole-`Optional` leaf second. The two are not alternatives for the same job: a leaf maps the pair, but leaves `null` a located error, so only the bridge gives the field an absent state. Declaring the annotation *on* such a leaf is refused rather than silently ignored.
~~~

~~~admonish example title="The annotation on a bean wire, reported as redundant"
<!-- verify:reports "@OptionalBridge on 'nickname' is redundant on a bean wire" -->
```java
import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;

record Guest(String name, Optional<String> nickname) {}

class GuestBean {
  private String name;
  private String nickname;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
}

@GenerateMapping
interface GuestMapping extends MappingSpec<Guest, GuestBean> {
  @OptionalBridge
  Optional<String> nickname();
}
```

The processor says:

```
@GenerateMapping: @OptionalBridge on 'nickname' is redundant on a bean wire. A bean wire
bridges a domain Optional to its nullable property automatically, because bean conventions
leave Optional off property types; the annotation opts a RECORD wire into the same
correspondence. Remove the annotation, or keep it if the vocabulary is shared with a
record-wire spec.
```

A note, not an error: the mapping is generated exactly as it would be without the annotation.
~~~

A bridged component is a non-identity correspondence, so the mapping does not gain `asIso()`, and a [projection](tiers.md) carrying one takes the validated `patch(Domain, Wire)` rather than `asLens()`, which is the same tier arithmetic a bean bridge has always had.

---

## A record's own invariants {#constructor-invariants}

A domain record often guards itself: a compact constructor that throws when its components disagree. `parse` keeps that guard and still returns a value. Once every component of the record has parsed, the generated code calls its canonical constructor, and a `RuntimeException` the constructor throws becomes a `FieldError` at the record's own path, carrying the exception's message:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:invariant_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:invariant_usage}}
```

The second stay fails at `stays.1`, and the missing guest is still reported beside it. The rules:

- **The record is the address.** A cross-field invariant belongs to no single component, so it locates where the record does: under the component that holds it (`stays.1`), or unlabelled at the top level.
- **The constructor runs last.** It needs every component, so it runs only once all of them have parsed. A record therefore reports either its components' errors or its invariant, never both, while everything around it keeps accumulating as usual.
- **The message is what the client reads**, so write it for them. An exception without a message, or with a blank one, reads `not a valid Stay`.
- **Any `RuntimeException` counts, bugs included.** The null guard keeps a `null` out of the constructor, but a constructor that divides by zero or dereferences something of its own fails the same way: its message goes to the client and its stack trace is dropped. Keep the constructor to checks on its arguments, with messages written for a client.
- **A rule about one field alone belongs in a [leaf](basics.md#validated-leaves)**: it locates at the field and accumulates with the record's other errors.

The same guard covers every surface that builds the record whole from parsed parts. Only `asIso().reverseGet`, a projection's `asLens().set` and a sparse update's `toValidated()` let the exception propagate instead: [which surfaces a refusal reaches](rules.md#constructor-refusal-surfaces).

---

~~~admonish info title="Key Takeaways"
* **Absence is declared, never guessed**: `@OptionalBridge` opts one `Optional` component into reading `null` as absent, and on a record wire nothing else does
* **A record's own invariant is located too**: an exception from its constructor becomes a `FieldError` at the record's path, beside every other error
~~~

~~~admonish tip title="See Also"
- [Null has an address, not a stack trace](basics.md#null-doctrine): The rule a bridge overrides, one component at a time
- [Optional nested objects](structure.md#optional-nested-objects): A bridged component whose element has a spec of its own
- [The null contract, precisely](rules.md#the-null-contract-precisely): What the null guard reaches, and which nulls stay the caller's bug
~~~

---

**Previous:** [Standard Codecs and Shared Vocabulary](codecs.md)
**Next:** [Nesting, Containers, and Sealed Hierarchies](structure.md)
