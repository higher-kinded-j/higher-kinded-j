# Common Compiler Errors

_Find the message your compiler printed, then read what it means and how to fix it._

This page is for the moment a build fails and you want to know what the message means. Find the fragment your compiler printed in [Find your message](#find-your-message) and follow it to its entry. Every entry says what the message means, gives the fix, and keeps the reasoning in a **Why** you can open if you want it.

~~~admonish info title="Error, warning, or note?"
- An **error** stops the build. Almost everything here is an error.
- A **warning** stops the build only under `-Werror`. A processor warning cannot be suppressed, so the remedy is the fix rather than an annotation.
- A **note** stops nothing. It shows in the compiler output as `Note: ...`, and tells you that something you asked for was quietly not applied.

Rows below and headings on the page say which, wherever it is not an error.
~~~

---

## Find your message

**From `@GenerateLenses`, `@GenerateFocus`, `@GenerateTraversals` and friends** ([entries](#generatelenses--generatefocus--generatetraversals)):

| The message says | What it means |
|------------------|---------------|
| [`cannot find symbol: class XLenses`](#cannot-find-symbol-class-xlenses) | The processor has not run, or the IDE has not indexed the generated sources |
| [`can only be applied to records`](#generatelenses-can-only-be-applied-to-records-but-foo-is-a-class) | `@GenerateLenses` or a sibling is on a class |
| [`can only be applied to sealed interfaces or enums`](#the-generateprisms-annotation-can-only-be-applied-to-sealed-interfaces-or-enums) | `@GeneratePrisms` is on something else |
| [`does not bind`](#generateprisms-does-not-bind) | A subtype needs a parameter its clause leaves free |
| [`names a type variable`](#generateisos-the-iso-returned-by-x-names-a-type-variable) | The `@GenerateIsos` method's `Iso` type is not fully concrete |
| [`'x' is not static`](#generateisos-x-is-not-static) | The `@GenerateIsos` method is an instance method |
| [`'x' takes parameters`](#generateisos-x-takes-parameters) | The `@GenerateIsos` method takes arguments |
| [`does not return an Iso with both type arguments`](#generateisos-x-does-not-return-an-iso-with-both-type-arguments) | The `@GenerateIsos` method returns something else |
| [`cannot be reached from 'p'`](#generateisos-x-cannot-be-reached-from-p) | The `@GenerateIsos` method is not visible from the generated package |
| [`The generated companion is a top-level class`](#companion-cannot-be-reached) | A type the generated companion names is `private`, or hidden in another package |
| [`has a wildcard type argument`, `has a raw Either`](#generatefocus-record-component-xy-has-a-wildcard-type-argument-in-set-extends-t) | A widened SPI container, such as `Either`, is raw or has a wildcard |
| [`cannot navigate to`](#generatefocus-navigator-cannot-navigate) | A record that writes `generateNavigators = true` reaches a record with a field named like a no-argument path method |
| [`has no navigator into`](#generatefocus-no-navigator-into) | **Note.** The same clash, on a record that leaves navigators to the default: the field keeps its plain path |
| [`cannot share its class name`](#generatefocus-navigator-shares-class-name) | A record that writes `generateNavigators = true` has two fields whose names differ only in the case of their first letter |
| [`has its class name`](#generatefocus-no-navigator-shared-name) | **Note.** The same clash, on a record that leaves navigators to the default: the later field keeps its plain path |
| [`has no navigator over its`](#generatefocus-no-navigator-over-elements) | **Note.** A record that writes `generateNavigators = true` leaves a container of navigable records unwidened |
| [`has no AffineForm`, `has no TraversalForm`](#generatefocus-no-form) | **Note.** A record that writes `generateNavigators = true` reaches a record with a field named like a wider path's own method, so a hop from that path returns the plain path |
| [`which is not a component of`](#generatefocus-field-list-unknown-name) | `includeFields`, `excludeFields` or `widenFields` names a field the record does not have |
| [`whose field has no navigator`](#generatefocus-includefields-no-navigator) | **Warning.** `includeFields` names a component that has no navigator |
| [`changes nothing, because`](#generatefocus-field-list-unread) | **Warning.** A field list the record's other settings never read |
| [`is widened without it`, `holds no container widenFields widens`](#generatefocus-widenfields-cannot-widen) | **Warning.** A `widenFields` name whose path widening cannot change |
| [`no traversal was generated for component`](#generatetraversals-no-traversal-was-generated-for-component-xy-of-type-dequet-a-note) | **Note.** `@GenerateTraversals` found no generator for a container |
| [`Multiple TraversableGenerator SPI providers with equal priority`](#multiple-traversablegenerator-spi-providers-with-equal-priority-n-support-type-x-a-warning) | **Warning.** Two generators claim one type and neither outranks the other |
| [`the annotation on record component 'X.y' is not applied`](#traversefield-the-annotation-on-record-component-xy-is-not-applied-a-note) | **Note.** `@TraverseField` is on something that is not a `Kind` with a declared witness |
| [`names a witness the processor does not recognise`](#generatefocus-record-component-xy-names-a-witness-the-processor-does-not-recognise-a-note) | **Note.** A `Kind` field's witness has no registered `Traverse`, so nothing widens it |

**From `@ImportOptics` and spec interfaces** ([entries](#importoptics-and-opticsspec-interfaces)):

| The message says | What it means |
|------------------|---------------|
| [`lists no classes to import`](#importoptics-lists-no-classes) | **Warning.** The annotation names no class, so nothing is generated |
| [`which has no optics to import`](#importoptics-literal-names-no-type) | The class list holds a primitive, an array or `void` |
| [`and also lists classes to import`](#importoptics-spec-lists-classes) | A spec interface carries a class list it does not read |
| [`extends OpticsSpec only through`](#importoptics-indirect-spec) | A spec reaches `OpticsSpec<S>` through another interface |
| [`implements OpticsSpec but is not an interface`](#importoptics-spec-not-an-interface) | A class is written as a spec |
| [`inherits the optic method`](#importoptics-inherited-optic) | A spec's optic method is declared on another interface |
| [`carries no copy strategy annotation`](#importoptics-lens-method-x-carries-no-copy-strategy-annotation) | A spec `Lens` method names none of the four copy strategies |
| [`is a default method`](#xopticsspecfoo-is-a-default-method) | A spec interface method has a body |
| [`which is a type variable`](#xopticsspec-declares-opticsspecs-which-is-a-type-variable) | `OpticsSpec<S>` names a type parameter rather than a type |
| [`which names the raw type 'Box'`](#xopticsspec-declares-opticsspecbox-which-names-the-raw-type-box) | The source type is missing its type arguments |
| [`which hides the 'T' of its enclosing class`](#importoptics-type--names-the-type-parameter-t-of--which-hides-the-t-of-its-enclosing-class-) | An imported inner class reuses a type-parameter name of its enclosing class |
| [`rather than as the List interface`](#throughfield--reaches-field-items-which-is-declared-as-arrayliststring-rather-than-as-the-list-interface) | `@ThroughField`'s lens focuses a concrete container, or another interface |
| [`which the spec does not declare`](#throughfield--composes-through-a-lens-named-items-which-the-spec-does-not-declare) | `@ThroughField` has no lens for the field to compose with |
| [`which the spec declares static` or `private`](#throughfield--composes-through-a-lens-named-items-which-the-spec-does-not-declare) | `@ThroughField` names a lens the generated class does not carry |
| [`hands back as 'String'`](#throughfield--declares-focus-integer-over-field-items-of-type-liststring-whose-elements-the-standard-traversal-hands-back-as-string) | `@ThroughField`'s declared focus is not what the traversal returns |
| [`is not a subtype of source type`](#instanceof-target-comexamplefoo-is-not-a-subtype-of-source-type-comexamplebase) | `@InstanceOf` names a class outside the hierarchy |
| [`which no '...' can be`](#instanceof-never-matches) | No value of the source can be the class |
| [`so the bound has nothing to name`](#instanceof-unwritable-bound) | A pinned parameter is bounded by one the source leaves free |
| [`outside its parameter's bound`](#instanceof-out-of-bound) | An argument the source pins breaks its bound |
| [`which the test cannot narrow to`](#instanceof--declares-its-focus-as-circlet-which-the-test-cannot-narrow-to) | The focus promises a type argument `instanceof` cannot check |
| [`narrows to '...', which is not a '...'`](#instanceof--narrows-to--which-is-not-a-) | The `@InstanceOf` class is not assignable to the declared focus |
| [`does not resolve to a type`](#viacopyandset-copyconstructor-names--which-does-not-resolve-to-a-type) | `copyConstructor` is not a fully qualified class name |
| [`which 'S' does not extend or implement`](#viacopyandset-copyconstructor-names--which-s-does-not-extend-or-implement) | `copyConstructor` names a type that is not a supertype |
| [`is not public and so cannot be named from`](#viacopyandset-copyconstructor-names--which-is-not-public-and-so-cannot-be-named-from-) | `copyConstructor` names a type the generated class cannot see |
| [`and no constructor accepts`](#viacopyandset-copyconstructor-names--which--reaches-as--and-no-constructor-accepts) | No copy constructor takes the supertype you named |
| [`is written with a wildcard type argument`](#viacopyandset--is-written-with-a-wildcard-type-argument) | A constructor rebuild cannot be written for a wildcard source type |
| [`is an abstract class`, `is an interface`](#viacopyandset--is-written-with-a-wildcard-type-argument) | A constructor rebuild of an abstract class or interface |
| [`not the source type`](#wither--returns--not-the-source-type-) | The method a `@Wither` call binds returns something other than the source type |
| [`for the generated lens to call`](#wither--has-no-method--for-the-generated-lens-to-call) | `@Wither` names a method the source type does not have, or one the generated class cannot reach |
| [`takes the lens's focus type`](#wither-no-method--of--takes-the-lenss-focus-type-) | No method of the name `@Wither` gives takes the lens's focus |
| [`cannot choose between`](#wither-the-generated-call-to--cannot-choose-between--and-) | More than one overload takes the focus, none more closely than the rest |
| [`is static, so the generated lens cannot rebuild`](#wither--is-static-so-the-generated-lens-cannot-rebuild-a--through-it) | The overload the focus binds is a static method |
| [`for the generated lens to read the value it focuses`](#-has-no-method--for-the-generated-lens-to-read-the-value-it-focuses) | A strategy's `getter`, or the lens method that stands in for it, names no accessor |
| [`reads '...', not the lens's focus`](#-reads--not-the-lenss-focus-) | The accessor reads a value the lens cannot hand back as its focus |
| [`only through an unchecked conversion`, `so the generated call to ... is unchecked`](#spec-unchecked-conversion) | A read or rebuild through a raw type is unchecked |
| [`inherits 'toBuilder()' from`](#viabuilder-the-chain-the-lens-rebuilds-through) | A subclass inherits a `toBuilder()` whose builder builds its parent |
| [`for the generated lens to set through`](#-has-no-method--for-the-generated-lens-to-set-through) | A `setter` names no method of the type it is called on |
| [`hands back '...', which is not a builder`](#viabuilder-the-chain-the-lens-rebuilds-through) | A `@ViaBuilder` step leads somewhere the next call cannot be made |
| [`hands back a type bounded by`](#viabuilder-the-chain-the-lens-rebuilds-through) | None of the types a `@ViaBuilder` step is bounded by has the next method |
| [`stands in takes no value at all`](#-has-no-method--for-the-generated-lens-to-set-through) | A setter's parameter is the type a wildcard of the source, or of the builder, stands for |
| [`is static, so the generated lens cannot set through`](#-has-no-method--for-the-generated-lens-to-set-through) | The `setter` the focus binds is a static method |
| [`takes one argument`](#-has-no-method--for-the-generated-lens-to-set-through) | No method the `setter` names takes an argument, and the focus is a wildcard |
| [`names no argument for the lens's own`](#viaconstructor-order) | A `@ViaConstructor` order leaves out the lens method |
| [`'parameterOrder' passes`](#viaconstructor-order) | A `@ViaConstructor` order contradicts the constructor's parameter names |
| [`No constructor of '...' takes`](#viaconstructor-order) | No constructor takes a `@ViaConstructor` order's arguments |
| [`The generated call 'new ...' cannot choose between`](#viaconstructor-order) | Several constructors take those arguments equally well |
| [`No 'parameterOrder' is written`](#viaconstructor-order) | Parameter names give no one order to read |
| [`parameter named after the lens's own`](#viaconstructor-order) | No constructor takes a parameter for the lens |
| [`not a constructor it was read from`](#viaconstructor-order) | An order read from names binds another constructor |
| [`leaves out what '...' takes`](#viaconstructor-order) | An order read from names misses a longer constructor |
| [`passes nothing for`](#viaconstructor-shorter-constructor) | **Warning.** A written `@ViaConstructor` order rebuilds through a shorter constructor |
| [`drops what '...' returns`](#viacopyandset-dropped-result) | **Warning.** A `withX` setter's new value may be lost |
| [`pairs more than one wither with the field`](#importoptics--pairs-more-than-one-wither-with-the-field--a-note) | **Note.** Two withers reach one field name, and one lens is generated |
| [`focuses '...', which is not a '...'`](#importoptics--focuses--which-is-not-a-) | A generated prism's focus is a value rather than a variant of the source |
| [`cannot find symbol`, inside `XPrisms.java`](#cannot-find-symbol-inside-the-generated-xprismsjava-after-using-matchwhen) | A `@MatchWhen` predicate or getter name is misspelt |
| [`requires a prism hint annotation`](#prism-method-x-requires-a-prism-hint-annotation-instanceof-or-matchwhen) | A spec `Prism` method has neither `@InstanceOf` nor `@MatchWhen` |

**From `@GeneratePathBridge` and `@PathVia`** ([entries](#generatepathbridge-and-pathvia)):

| The message says | What it means |
|------------------|---------------|
| [`which no Path wraps`](#pathvia-the-return-type-of-x-is-y-which-no-path-wraps) | The method returns a type outside the bridged set |
| [`names the raw type 'Y'`](#pathvia-the-signature-of-x-names-the-raw-type-y) | The signature is missing type arguments somewhere |
| [`is the wildcard '?'`](#pathvia-the-error-type-of-the-validated-returned-by-x-is-the-wildcard-) | A bridged `Validated` names its error type as a wildcard |
| [`has the same name as 'Y's`](#pathvia-the-type-parameter-t-on-x-has-the-same-name-as-ys) | A method type parameter hides one of the interface's |
| [`the bridge cannot call 'x'`](#pathvia-the-bridge-cannot-call-x) | The method is `static` or `private` |
| [`is already taken`](#pathvia-the-bridge-signature-for-x-is-already-taken) | Two `@PathVia` methods produce the same bridge signature |
| [`is not a method name`](#pathvia-pathvianame---is-not-a-method-name) | `@PathVia(name = ...)` is not a Java identifier |
| [`cannot be reached from 'p'`](#generatepathbridge-on-x-the-signature-names-y-which-cannot-be-reached-from-p) | Under `targetPackage`, part of the signature is not visible there |
| [`no @PathVia method was found`](#generatepathbridge-no-pathvia-method-was-found-among-xs-members-a-warning) | **Warning.** The interface has nothing to bridge |

**From javac, on code you wrote** ([Focus DSL chains](#focus-dsl-chains), [Free Monad](#free-monad-dsl-programs)):

| The message says | What it means |
|------------------|---------------|
| [`List<Object> cannot be converted to`, after `traverseOver`](#traverseover-and-the-higher-kinded-witness-type) | `traverseOver`'s element type is not pinned; `each(EachInstances.fromTraverse(...))` pins it |
| [`Incompatible types`, after `.each().via()`](#incompatible-types-when-chaining-eachvia) | Usually one `.each()` too many |
| [`Cannot infer type argument(s)`](#cannot-infer-type-arguments-on-an-intermediate-each) | Only the final untyped `each()` in a chain can infer its element type |
| [`no suitable method found for each(...)`, or for `some` or `via`](#no-suitable-method-found-for-each) | The path does not focus on the container the typed step names |
| [`each() in org.higherkindedj.optics.focus.FocusPath has been deprecated and marked for removal`](#untyped-shortcut-marked-for-removal) | **Warning.** A no-argument shortcut, due to go in 0.6.0, has a typed form |
| [`::new` rejected as a `BiFunction`](#method-reference-new-doesnt-work-with-single-field-records-as-bifunction) | A single-component record has no two-argument constructor |
| [`Sealed or non-sealed local classes are not allowed`](#sealed-or-non-sealed-local-classes-are-not-allowed) | A sealed interface is declared inside a method body |
| [`Cannot resolve method 'flatMap(...)'`](#cannot-resolve-method-flatmapfunction) | Two `Free` witness types are being mixed |
| [`Free<F, A> cannot be converted to A`](#type-mismatch-freef-a-cannot-be-converted-to-a) | The program was never handed to an interpreter |

---

## Where the message came from

Three different things can reject your code, and knowing which one spoke narrows the search:

```mermaid
flowchart TD
    accTitle: Where a compiler message came from
    accDescr: Your declaration goes to the processor first, and a refusal there is reported at your declaration, which is most of this page. An accepted declaration becomes a generated file; javac refusing that file reports cannot find symbol inside a class you did not write. If javac refuses your call site instead, the message is a Focus DSL chain or Free Monad error; otherwise the build succeeds.
    D["Your declaration:<br/>@Generate*, or<br/>a spec interface"]
    P{"Does the processor<br/>accept it?"}
    R["Refused at your<br/>declaration.<br/>Most of this page"]
    G["Generated file written"]
    J{"Does javac accept<br/>the generated file?"}
    JG["cannot find symbol,<br/>inside a class<br/>you did not write"]
    C{"Does javac accept<br/>your call site?"}
    CC["Focus DSL chain and<br/>Free Monad errors"]
    OK["Builds"]

    D --> P
    P -->|no| R
    P -->|yes| G
    G --> J
    J -->|no| JG
    J -->|yes| C
    C -->|yes| OK
    C -->|no| CC

    classDef step fill:#8caaee,stroke:#1e66f5,color:#232634
    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef error fill:#e78284,stroke:#d20f39,color:#232634
    classDef ok fill:#a6d189,stroke:#40a02b,color:#232634
    class D,G step
    class P,J,C decision
    class R,JG,CC error
    class OK ok
```

Most of this page is the first branch. The processor reads your declaration, finds a shape it cannot write code for, and says so where you wrote it. Those messages name the element they rejected, so reading the processor's own output first is quicker than working backwards from a `cannot find symbol` further down the build.

A `cannot find symbol: class XLenses` sits outside the diagram altogether: it means the processor never ran.

---

## `@GenerateLenses` / `@GenerateFocus` / `@GenerateTraversals`

### "cannot find symbol: class XLenses"

The annotation processor has not run yet, or the IDE has not picked up the generated sources directory. With `@ImportOptics`, the class is also missing when a type it imports never appeared: the import waits for a type another processor writes, and javac reports that type as missing too. An error reported earlier, or a warning under `-Werror`, stops annotation processing before a waiting import is read, so fix those first.

**Fix.** Run a build (`./gradlew build` or `mvn compile`). After the build completes, refresh the project in your IDE so it indexes `build/generated/sources/annotationProcessor/java/main` (Gradle) or `target/generated-sources/annotations` (Maven).
### "@GenerateLenses: can only be applied to records, but 'Foo' is a class"

`@GenerateLenses`, `@GenerateFocus`, `@GenerateFolds`, `@GenerateGetters`, `@GenerateSetters` and `@GenerateTraversals` only apply to records.

**Fix.** Convert the class to a record. If the type is third-party and you cannot change it, use [`@ImportOptics`](importing_optics.md) on a `package-info.java` or a spec interface instead.

~~~admonish note title="Why" collapsible=true
The annotations target `TYPE`, so javac itself is happy; the message comes from the processor. The wording varies: `@GenerateLenses` and `@GenerateFocus` name the offending type, while the others emit the shorter "The @GenerateTraversals annotation can only be applied to records."
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "can only be applied to records" -->
```java
@GenerateLenses
class Voucher {}
```
~~~

### "The @GeneratePrisms annotation can only be applied to sealed interfaces or enums."

`@GeneratePrisms` requires a `sealed interface` or an `enum`. A sealed *abstract class* is rejected too, despite being sealed, because the processor tests the element kind rather than the modifier.

**Fix.** Make the type a sealed interface and declare its `permits` clause, or convert it to an enum.

~~~admonish warning title="A plain interface fails silently instead"
A non-sealed *interface* passes the processor's guard and produces an **empty** `XPrisms` class with no diagnostic at all. If your prisms class exists but has no methods, an unsealed interface is why.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "can only be applied to sealed interfaces or enums" -->
```java
@GeneratePrisms
abstract class Payment {}
```
~~~

### "@GeneratePrisms: 'Pair' declares [B], which 'Shape' does not bind" {#generateprisms-does-not-bind}

A permitted subtype needs a type parameter that its `implements` clause leaves free. `@ImportOptics` reports the same for an imported sealed interface. The message takes one of two forms:

| The message says | The shape |
|---|---|
| `'Pair' declares [B], which 'Shape' does not bind` | `Pair<A, B> implements Shape<A>`: the subtype's own `B` appears nowhere in its clause |
| `The prism for 'Hexagon' declares Y extends X, and 'Shape' does not bind [X]` | `Hexagon<Y extends X> implements Shape<Y>`, an inner class of `Shapes<X>`: a bound names the enclosing class's `X`, which the clause leaves free |

**Fix.** Bind the free parameter in the clause, as the message shows, giving the sealed interface a type parameter for it if it has none to spare. Where you cannot change the clause, as for a sealed interface from a library, write the prism by hand with `Prism.of`.

~~~admonish note title="Why" collapsible=true
A prism narrows by `instanceof`, which tests an erasure. A parameter the clause binds is pinned by the hierarchy: under `Tagged<T> implements Shape<T>`, a `Shape<T>` that is a `Tagged` can only be a `Tagged<T>`. A parameter it leaves free is pinned by nothing, so two callers could read one value at different types, and the second would get a `ClassCastException` from a call site that compiled cleanly.

An enclosing class's parameter the clause leaves free is written as a wildcard, as [Generating the Prisms](prisms.md#step-1-generating-the-prisms) shows. A bound that names it then has nothing to name: the prism has no `X` to write in `<Y extends X>`, and javac refuses `Shapes<?>.Hexagon<Y>` for such a `Y`.
~~~

~~~admonish example title="Declarations that produce it" collapsible=true
<!-- verify:rejects "'Pair' declares [B], which 'Shape' does not bind" -->
```java
@GeneratePrisms
sealed interface Shape<T> permits Pair {}

record Pair<A, B>(A a, B b) implements Shape<A> {}
```

<!-- verify:rejects "The prism for 'Hexagon' declares Y extends X, and 'Shape' does not bind [X]" -->
```java
class Shapes<X> {

    @GeneratePrisms
    sealed interface Shape<T> permits Shapes.Hexagon {}

    final class Hexagon<Y extends X> implements Shape<Y> {}
}
```
~~~

### "@GenerateIsos: the iso returned by 'x' names a type variable"

One of the returned `Iso`'s two type arguments is, or contains, a type variable. Both `<T> Iso<Box<T>, T> boxIso()` and an instance method of a `Holder<X>` returning `Iso<Box<X>, X>` do this.

**Fix.** Give the iso concrete type arguments where the method is declared (`Iso<Box<String>, String>`), or drop `@GenerateIsos` and call the method directly.

~~~admonish note title="Why" collapsible=true
What gets generated is a `public static final` field, and a field has nowhere to declare one, so it would name a variable nothing brings into scope.

Note this is about what the *iso* names, not what the method declares: `<T> Iso<Box, String> boxIso()` is fine, because `T` is inferred at the call and never reaches the field's type.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "names a type variable" -->
```java
record Box<T>(T value) {}

final class BoxIsos {

    @GenerateIsos
    static <T> Iso<Box<T>, T> box() {
        return Iso.of(Box::value, Box::new);
    }
}
```
~~~

### "@GenerateIsos: 'x' is not static"

The annotated method is an instance method. The generated field initialises itself with a static call, and there is no instance to make it on.

**Fix.** Make the method `static`.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "'quantity' is not static" -->
```java
record Quantity(int units) {}

final class QuantityIsos {

    @GenerateIsos
    Iso<Quantity, Integer> quantity() {
        return Iso.of(Quantity::units, Quantity::new);
    }
}
```
~~~

### "@GenerateIsos: 'x' takes parameters"

The annotated method takes arguments. The generated field initialises itself by calling the method with none, and there is nothing for it to pass.

**Fix.** Take the arguments away, or drop `@GenerateIsos` and call the method directly.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "'quantity' takes parameters" -->
```java
record Quantity(int units) {}

final class QuantityIsos {

    @GenerateIsos
    static Iso<Quantity, Integer> quantity(int scale) {
        return Iso.of(Quantity::units, Quantity::new);
    }
}
```
~~~

### "@GenerateIsos: 'x' does not return an Iso with both type arguments"

The method returns a `void`, a primitive, an array, a raw `Iso`, or something that is not an `Iso` at all. The generated field is typed from the two arguments of the returned `Iso`, and none of those carries them.

**Fix.** Return `Iso<S, A>` naming both, as `Iso<Point, Tuple2<Integer, Integer>>`.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "does not return an Iso with both type arguments" -->
```java
final class PointIsos {

    @GenerateIsos
    static String point() {
        return "not an Iso";
    }
}
```
~~~

### "@GenerateIsos: 'x' cannot be reached from 'p'"

The generated class lives in package `p` and calls the method from there, but the method, or a type enclosing it, is `private`, `protected` or package-private somewhere else. Most often seen with `targetPackage`.

**Fix.** Make the method and its enclosing types public, or generate into the package they are already visible from.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "cannot be reached from 'com.example.optics'" -->
```java
final class LengthIsos {

    @GenerateIsos(targetPackage = "com.example.optics")
    static Iso<String, Integer> length() {
        return Iso.of(String::length, "x"::repeat);
    }
}
```
~~~

### "@GenerateLenses: record component 'x' of 'X' names 'Y', which cannot be reached from 'p'" {#companion-cannot-be-reached}

A type the generated companion names is hidden from the package the companion is written into: it, or a class enclosing it, is `private`, or it comes from another package and is not `public`. `@GenerateLenses` and the other optics generators, `@ImportOptics`, `@GenerateAssembly`, `@GenerateErrorEnvelope`, `@EffectAlgebra` and `@PathSource` refuse such a type at the annotation. The message names where the companion meets the type: the annotated type itself (`record 'Shop.Item' cannot be reached from 'p'`), or a component, subtype, bound, field, optic, variant, operation, witness or error type.

**Fix.** Remove `private` from the class the message names. From another package, make it `public`; under `targetPackage`, removing `targetPackage` also works for a type beside the annotated one, and the message offers it where it does.

```
@GenerateLenses: record component 'sku' of 'Item' names 'Sku', which cannot be reached from
'com.example'. The generated companion is a top-level class in that package, where it names
every type it reads or writes, so each has to be visible from there. Remove 'private' from 'Sku'.
```

~~~admonish note title="Why" collapsible=true
The companion, `ItemLenses` here, is a top-level class beside `Shop`, not nested inside it, so it cannot see a type `Shop` keeps `private`. It names every type it reads or writes, some of them only in a lambda it leaves javac to infer, and javac checks those too. A traversal names only the components it traverses, so a `private` type on any other component compiles.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "names 'Sku', which cannot be reached from" -->
```java
class Shop {
    private record Sku(String value) {}

    @GenerateLenses
    record Item(Sku sku) {}
}
```
~~~

### "@GenerateFocus: record component 'X.y' has a wildcard type argument in Either<String, ? extends T>" {#generatefocus-record-component-xy-has-a-wildcard-type-argument-in-set-extends-t}

A container that the processor widens through the optic its SPI generator supplies is declared raw, or with a wildcard type argument. The same error covers `Either`, `Try`, `Validated`, `Map` and every other such container, and is also reported as *"has a raw Either"*. A `Map` or another collection draws it only once something widens it. The five built-in containers, `Optional`, `Maybe`, `List`, `Set` and `Collection`, never draw it.

**Fix.** Name the type arguments, `Either<String, Widget>` rather than `Either<String, ? extends Widget>`. For a wildcard, you can instead drop `@GenerateFocus` from the record and keep `@GenerateLenses` and `@GenerateTraversals`, which compose no optic instance. The lens keeps the component's type as written. The traversal focuses [the type the wildcard stands for](traversals.md#wildcard-element-types): `Widget` for `? extends Widget`, and `Object` for `?` or `? super Widget`. A raw container has no element type for `@GenerateTraversals` to focus, so it draws a note and no traversal there; keep `@GenerateLenses` alone for one. See [Custom Containers](focus_containers.md#supported-container-types).

~~~admonish note title="Why" collapsible=true
That optic, such as `Affines.eitherRight()`, has its own type arguments worked out from the component's type. A raw container gives javac nothing to work from, and a wildcard stands for no one type. A built-in container is exempt because the processor names its instance for the element the arguments stand for, and casts it to the type the field declares.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "has a wildcard type argument in Either<String, ? extends T>" -->
```java
@GenerateFocus
record Bag<T>(Either<String, ? extends T> item) {}
```
~~~

### "@GenerateFocus: the navigator for 'X.y' cannot navigate to 'T.z'" {#generatefocus-navigator-cannot-navigate}

`X` writes `generateNavigators = true`, its field `y` holds another `@GenerateFocus` record, `T`, and `T` has a field named like one of the path's own methods that take no arguments. On a `FocusPath` those are `last`, `head`, `tail`, `init`, `each`, `some`, `nullable`, `cons`, `snoc`, `headTail`, `initLast`, `segments`, `pathString`, `toLens`, `asAffine`, `asTraversal`, `asFold` and `toPath`. An `AffinePath` and a `TraversalPath` differ a little, and the processor reads each list from the class the navigator extends.

**Fix.** Rename the field, or keep `y` a plain path, as the message says. It names the change that does that on `X`: add `y` to `excludeFields`, remove it from `includeFields`, or, where `includeFields` names only `y`, remove `includeFields` and set `generateNavigators = false`. A plain path still reaches the clashing field through `.via(TFocus.z())`.

~~~admonish note title="Why" collapsible=true
A navigator is a path, so it already has each of those methods. A navigation method of the same name would clash with it, and leaving the field out would let `name().last()` compile against the path's own `last()`. That method reads the last element of a list, so on a record it throws `ClassCastException` when the path runs. It is deprecated, and a deprecated method clashes all the same while the path has it. A record that asks for navigators in so many words is refused; one that leaves the default draws [a note](#generatefocus-no-navigator-into) instead. A name that takes arguments, such as `get` or `count`, does not clash, because `get()` and `get(source)` are two methods.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "cannot navigate to 'FullName.last'" -->
```java
@GenerateFocus
record FullName(String first, String last) {}

@GenerateFocus(generateNavigators = true)
record Signatory(FullName name) {}
```

```
error: @GenerateFocus: the navigator for 'Signatory.name' cannot navigate to 'FullName.last'. A navigator is a FocusPath, which has its own last(), so SignatoryFocus.name().last() would call that method instead of navigating to the field. Rename 'FullName.last', or add excludeFields = "name" to @GenerateFocus on Signatory to keep 'name' a plain path.
```
~~~

### "@GenerateFocus: record component 'X.y' has no navigator into T" (a note) {#generatefocus-no-navigator-into}

`X` leaves navigators to the default, and the record `y` holds, `T`, has a field named like one of the path's own methods that take no arguments, the ones the [entry before](#generatefocus-navigator-cannot-navigate) lists. A navigator could not navigate to that field, so `y` gets none, and its method keeps the path it has without one. Another field of `X` may have a name that differs from `y`'s only in the case of its first letter. Where it has a navigator, or is declared before `y` and the rename would give it one, the note names both, since their navigators would [share a class name](#generatefocus-no-navigator-shared-name).

**Fix.** Rename the field to give `y` a navigator. Where the note also names two fields of `X`, rename either of them too. To keep the plain path and silence the note, make the change it names on `X`. Add `y` to `excludeFields` or remove it from `includeFields`; where `includeFields` names only `y`, remove `includeFields` and set `generateNavigators = false`.

~~~admonish note title="Why" collapsible=true
A record that leaves the default asked for no navigator in particular, so the field keeps the path it would have with navigators off, rather than failing the build. A record that writes `generateNavigators = true` is refused instead, by the entry before this one.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "record component 'Signatory.name' has no navigator into FullName" -->
```java
@GenerateFocus
record FullName(String first, String last) {}

@GenerateFocus
record Signatory(FullName name) {}
```

```
Note: @GenerateFocus: record component 'Signatory.name' has no navigator into FullName. A navigator is a FocusPath, which has its own last(), so it cannot navigate to 'FullName.last', and SignatoryFocus.name() keeps the path it has without one. Rename 'FullName.last' to navigate into FullName, or add excludeFields = "name" to @GenerateFocus on Signatory to keep the path without this note.
```
~~~

### "@GenerateFocus: the navigator for 'X.y' cannot share its class name with 'X.z'" {#generatefocus-navigator-shares-class-name}

`X` writes `generateNavigators = true`, and two of its fields that reach `@GenerateFocus` records have names that differ only in the case of their first letter, such as `pouches` and `Pouches`. A navigator class takes its field's name with the first letter in upper case, so both would be `PouchesNavigator`. The first field in declaration order keeps the navigator, and the message is at the later one.

**Fix.** Rename either field, or keep the later one a plain path, as the message says. It names the change that does that on `X`: add the later field to `excludeFields`, or, where `X` writes `includeFields`, remove it from that list, which still names the earlier field. Where the record the later field reaches has a field named like a path method, the message names it too, since a rename alone would draw [the cannot-navigate error](#generatefocus-navigator-cannot-navigate).

~~~admonish note title="Why" collapsible=true
Both classes would be nested in the record's `Focus` class, which cannot hold two classes of one name, so javac would report the second as already defined, in a file you cannot edit. The first field keeps its navigator, so a field added after it never takes that navigator away. A record that leaves navigators to the default draws [a note](#generatefocus-no-navigator-shared-name) instead. A message that offers a field a navigator, such as one asking for a container to be widened, asks for the rename first where its change alone would lead here.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "the navigator for 'Satchel.Pouches' cannot share its class name with 'Satchel.pouches'" -->
```java
@GenerateFocus
record Pouch(String label, int grams) {}

@GenerateFocus(generateNavigators = true)
record Satchel(List<Pouch> pouches, List<Pouch> Pouches) {}
```

```
error: @GenerateFocus: the navigator for 'Satchel.Pouches' cannot share its class name with 'Satchel.pouches'. 'Satchel.pouches' already has the navigator class PouchesNavigator, and a navigator for 'Pouches' would take the same name, since a navigator class takes its field's name with the first letter in upper case. Rename 'Satchel.Pouches' or 'Satchel.pouches', or add excludeFields = "Pouches" to @GenerateFocus on Satchel to keep 'Pouches' a plain path.
```
~~~

### "@GenerateFocus: record component 'X.y' has no navigator, because 'X.z' has its class name" (a note) {#generatefocus-no-navigator-shared-name}

`X` leaves navigators to the default, and `z`, declared before `y`, has a name that differs from `y`'s only in the case of its first letter. `z`'s navigator already has the class name `y`'s would take, as the [entry before](#generatefocus-navigator-shares-class-name) explains, so `y` keeps the path it has without one.

**Fix.** Rename either field to give both a navigator. To keep the plain path and silence the note, make the change it names on `X`: add `y` to `excludeFields`, or, where `X` writes `includeFields`, remove `y` from it.

~~~admonish note title="Why" collapsible=true
A record that leaves the default asked for no navigator in particular, so the later field keeps the path it would have with navigators off, rather than failing the build. A record that writes `generateNavigators = true` is refused instead, by the entry before this one.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "record component 'Satchel.Pouches' has no navigator, because 'Satchel.pouches' has its class name" -->
```java
@GenerateFocus
record Pouch(String label, int grams) {}

@GenerateFocus
record Satchel(List<Pouch> pouches, List<Pouch> Pouches) {}
```

```
Note: @GenerateFocus: record component 'Satchel.Pouches' has no navigator, because 'Satchel.pouches' has its class name. 'Satchel.pouches' already has the navigator class PouchesNavigator, and a navigator for 'Pouches' would take the same name, since a navigator class takes its field's name with the first letter in upper case. SatchelFocus.Pouches() keeps the path it has without one. Rename 'Satchel.Pouches' or 'Satchel.pouches' to give both a navigator, or add excludeFields = "Pouches" to @GenerateFocus on Satchel to keep the path without this note.
```
~~~

### "@GenerateFocus: record component 'X.y' has no navigator over its T elements" (a note) {#generatefocus-no-navigator-over-elements}

`X` writes `generateNavigators = true`, and `y` is a `Map`, an array or a third-party collection of `@GenerateFocus` records that `X` does not widen, or a container holding one, such as `List<Map<String, Item>>`. A navigator steps into a container only where the record widens it, so `y`'s method keeps a path to the whole container. If `includeFields` names `y`, it draws the [includeFields warning](#generatefocus-includefields-no-navigator) instead. Where `T` has a field named like a method of the widened path, such as `last`, the note names that field too, since widening `y` alone would draw [the cannot-navigate error](#generatefocus-navigator-cannot-navigate). Another field of `X` may have a name that differs from `y`'s only in the case of its first letter. Where it has a navigator, the note names both, since widening `y` alone would draw [the shared-class-name error](#generatefocus-navigator-shares-class-name).

**Fix.** Add `y` to `widenFields` on `X`, as the note says, and the method returns a navigator over the elements. Where the note names a field of `T`, rename it first. Where it names two fields of `X`, rename either first, and write the new name in `widenFields` if you renamed `y`. Or add `y` to `excludeFields` to keep the container whole. `widenCollections = true` widens every such container instead. To keep the container whole otherwise, drop `generateNavigators = true`, which is the default, and the note goes with it. Whichever you choose, find each call through a field of `T` the note names, such as `byTicker().last()`, by hand: it compiles against the path's own `last()`, so the compiler cannot point at it.

~~~admonish note title="Why" collapsible=true
Widening decides what a field's method focuses on, and a navigator only adds methods to that path, so turning navigators on never changes it. A record that writes `generateNavigators = true` out may still expect a navigator over a container's elements, so it is told which `widenFields` value gives one. A record that leaves navigators to the default asked for nothing, and is told nothing.

A call written for a navigator over the elements, such as `byTicker().ticker()`, does not compile against the container's path, so the compiler points at it. One through a field named like a path method, such as `byTicker().last()`, compiles against the path's own `last()` and fails when the path reads. Renaming the field does not change that call.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "record component 'Hamper.bottles' has no navigator over its Bottle elements" -->
```java
@GenerateFocus
record Bottle(String label, int volume) {}

@GenerateFocus(generateNavigators = true)
record Hamper(Bottle[] bottles) {}
```

```
Note: @GenerateFocus: record component 'Hamper.bottles' has no navigator over its Bottle elements. A navigator steps into a container only where the record widens it, and Hamper does not widen 'bottles'. Add widenFields = "bottles" to @GenerateFocus on Hamper to navigate into its elements.
```
~~~

~~~admonish example title="A declaration that draws it with a field to rename" collapsible=true
<!-- verify:reports "Rename 'Quote.last', then add widenFields" -->
```java
@GenerateFocus
record Quote(String ticker, double last) {}

@GenerateFocus(generateNavigators = true)
record Book(Map<String, Quote> byTicker) {}
```

```
Note: @GenerateFocus: record component 'Book.byTicker' has no navigator over its Quote elements. A navigator steps into a container only where the record widens it, and Book does not widen 'byTicker'. Widened, its navigator would be a TraversalPath, which has its own last(), so it could not navigate to 'Quote.last'. Rename 'Quote.last', then add widenFields = "byTicker" to @GenerateFocus on Book to navigate into its elements, or add excludeFields = "byTicker" to @GenerateFocus on Book to go without this note.
```
~~~

~~~admonish example title="A declaration that draws it with two fields to choose between" collapsible=true
<!-- verify:reports "Rename 'Satchel.pouches' or 'Satchel.Pouches', then add widenFields" -->
```java
@GenerateFocus
record Pouch(String label, int grams) {}

@GenerateFocus(generateNavigators = true)
record Satchel(Map<String, Pouch> pouches, List<Pouch> Pouches) {}
```

```
Note: @GenerateFocus: record component 'Satchel.pouches' has no navigator over its Pouch elements. A navigator steps into a container only where the record widens it, and Satchel does not widen 'pouches'. A navigator for 'pouches' would share its class name, PouchesNavigator, with the navigator for 'Satchel.Pouches', since a navigator class takes its field's name with the first letter in upper case. Rename 'Satchel.pouches' or 'Satchel.Pouches', then add widenFields = "pouches", or its new name, to @GenerateFocus on Satchel to navigate into its elements, or add excludeFields = "pouches" to @GenerateFocus on Satchel to go without this note.
```
~~~

### "@GenerateFocus: the navigator for 'X.y' has no TraversalForm" (a note) {#generatefocus-no-form}

`X` writes `generateNavigators = true`, and its field `y` holds another `@GenerateFocus` record, `T`. `T` has a field named like a no-argument method that only a wider path has: `headOption` or `toTraversal` on a `TraversalPath`, or `toAffine` on an `AffinePath`. `y` keeps its own navigator, but not its form for that path, so a hop into `y` from such a path returns the plain path. The note names `AffineForm` where that is the form left out. It goes to `X`, which declares `y`, since the form belongs to `y`'s navigator; a record whose hop into `y` returns the plain path is not told.

**Fix.** Rename the field of `T`, as the note says, and the navigator gets its form. To keep the name and go without the form, drop `generateNavigators = true` from `X`, since it is the default, and the note goes with it.

~~~admonish note title="Why" collapsible=true
A form is a navigator over the wider path, so it has that path's methods, and a navigation method of the same name would clash with one. Leaving the field out of the form would let `channels().feed().headOption()`, from a `List<Channel>`, compile against the path's own `headOption()`, which reads the first feed. So the form is left out. It is a note rather than an error because `y`'s own navigator is generated, and every hop from a path of its own type still chains.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "the navigator for 'Channel.feed' has no TraversalForm" -->
```java
@GenerateFocus
record Feed(String name, String headOption) {}

@GenerateFocus(generateNavigators = true)
record Channel(Feed feed) {}
```

```
Note: @GenerateFocus: the navigator for 'Channel.feed' has no TraversalForm. A TraversalForm would be a TraversalPath, which has its own headOption(), so it could not navigate to 'Feed.headOption', and a hop into 'feed' from a TraversalPath returns a plain TraversalPath. Rename 'Feed.headOption' to give the navigator for 'feed' a TraversalForm, or remove generateNavigators = true from @GenerateFocus on Channel to go without this note.
```
~~~

### "@GenerateFocus: includeFields on X names "y", which is not a component of X" {#generatefocus-field-list-unknown-name}

`includeFields`, `excludeFields` or `widenFields` names a field the record does not have: a typo, or a name a rename left behind. In `includeFields` it costs the most, since a non-empty list gives navigators only to the fields it names, so a list whose only name is a typo leaves every field without one.

**Fix.** Write the component's name. Where one is close, the message offers it: a near misspelling, a different case such as `"Positions"`, or an accessor spelling such as `"getAddress"`. Take out a name that is no longer wanted.

~~~admonish note title="Why" collapsible=true
The lists hold strings, which javac cannot check against the record, so the processor does. A name that matched nothing would otherwise change nothing, and say nothing about it. The message lists the components it found, so a name with no near miss can be put right from the message alone.
~~~

~~~admonish example title="A declaration that produces it"
<!-- verify:rejects "Did you mean 'kiosk'? Found on Hawker: [name, kiosk]" -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "kisok")
record Hawker(String name, Kiosk kiosk) {}
```

```
error: @GenerateFocus: includeFields on Hawker names "kisok", which is not a component of Hawker. includeFields gives navigators only to the components it names, so a name that matches none gives no component a navigator. Did you mean 'kiosk'? Found on Hawker: [name, kiosk]. Replace "kisok" with "kiosk" in includeFields on Hawker.
```
~~~

### "@GenerateFocus: includeFields on X names \"y\", whose field has no navigator" (a warning) {#generatefocus-includefields-no-navigator}

The name matches a component, but that component has no navigator. A `String`, a `List<String>` and a record without `@GenerateFocus` have no navigator target. A `List`, `Set`, `Collection`, `Optional` or `Maybe` of `@GenerateFocus` records has one, as do such containers nested in one another and a `Kind` the processor traverses. A `Map`, an array of records or another supported container has one only where the declaring record widens it.

**Fix.** Name a field that can return a navigator, from those the warning lists. For an unannotated record compiled alongside it, annotate its type with `@GenerateFocus`. The warning offers this only where the annotation alone gives the field a navigator, never for a record from a library or another module. For an unwidened container, add the field to `widenFields`. Where no field can navigate, remove `includeFields` and set `generateNavigators = false`. Follow the warning's fix line: leaving `includeFields` in place with navigators off draws a different warning.

A non-empty `includeFields` gives navigators only to the fields it names, so naming an ineligible field also takes navigators away from every eligible field it leaves out. The warning points at the offending name and stops a `-Werror` build. A field already explained by a target note or error gets no additional warning. The [unwidened-container note](#generatefocus-no-navigator-over-elements) becomes this warning when `includeFields` names the field.

- An unwidened `Map<String, Box<?>>`, with `Box<T>` annotated, is offered `widenFields` to step into its `Box` elements. That alone gives no navigator: the existing wildcard-target note then offers a concrete `Box<Object>`.
- A `Map<String, ? extends Kiosk>` holds `Kiosk` records, but its wildcard prevents widening. Write `Map<String, Kiosk>` and add the field to `widenFields`, as the warning says.
- A `List`, `Optional` or other container the method steps into, holding a record without `@GenerateFocus`, is offered the annotation, as a field of that record is.
- Another field's name may differ from this one's only in the case of its first letter. If that field has a navigator, or would gain one from the same fix, the fix asks for one of the two to be [renamed first](#generatefocus-navigator-shares-class-name). A later field that would only gain one counts under `generateNavigators = true`, where the clash is an error.
- When several entries need a replacement, the first in list order receives it and the rest are offered removal. If another entry has a navigator or a correction of its own, remove the invalid name instead. Following all fix lines leaves one list without duplicate names.

~~~admonish example title="A valid name whose field has no navigator"
<!-- verify:reports "includeFields on Hawker names" -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "id")
record Hawker(String id, Kiosk kiosk) {}
```

```
warning: @GenerateFocus: includeFields on Hawker names "id", whose field has no navigator. Navigator generation cannot reach a @GenerateFocus record through String. It needs a @GenerateFocus record or a supported container of one. Replace "id" in includeFields on Hawker with a navigator field from [kiosk].
```

The offered field gives a navigator:

<!-- verify -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "kiosk")
record Hawker(String id, Kiosk kiosk) {}
```
~~~

~~~admonish example title="A container that needs widening"
<!-- verify:reports "Hawker does not widen" -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "sites")
record Hawker(java.util.Map<String, Kiosk> sites) {}
```

```
warning: @GenerateFocus: includeFields on Hawker names "sites", whose field has no navigator. A navigator steps into a container only where the record widens it, and Hawker does not widen "sites". Add widenFields = "sites" to @GenerateFocus on Hawker.
```
~~~

~~~admonish example title="A wildcard container and its combined correction"
<!-- verify:reports "holds Kiosk records, but its wildcard type arguments prevent widening" -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "sites")
record Hawker(java.util.Map<String, ? extends Kiosk> sites, Kiosk kiosk) {}
```

```
warning: @GenerateFocus: includeFields on Hawker names "sites", whose field has no navigator. Map<String, ? extends Kiosk> holds Kiosk records, but its wildcard type arguments prevent widening as written. Write Map<String, Kiosk> in place of Map<String, ? extends Kiosk> and add widenFields = "sites" to @GenerateFocus on Hawker.
```

Write the concrete type and widen the field together:

<!-- verify -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "sites", widenFields = "sites")
record Hawker(java.util.Map<String, Kiosk> sites, Kiosk kiosk) {}

var towns = HawkerFocus.sites().town();
```
~~~

~~~admonish example title="A list of a record without the annotation"
<!-- verify:reports "Alternatively, annotate record Kiosk with @GenerateFocus" -->
```java
record Kiosk(String town) {}

@GenerateFocus(includeFields = "sites")
record Hawker(java.util.List<Kiosk> sites) {}
```

```
warning: @GenerateFocus: includeFields on Hawker names "sites", whose field has no navigator. Kiosk is a record without @GenerateFocus. Navigator generation cannot reach a @GenerateFocus record through List<Kiosk>. It needs a @GenerateFocus record or a supported container of one. Remove includeFields and set generateNavigators = false in @GenerateFocus on Hawker. Alternatively, annotate record Kiosk with @GenerateFocus.
```

Annotate the record, and the list navigates into it:

<!-- verify -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "sites")
record Hawker(java.util.List<Kiosk> sites) {}

var towns = HawkerFocus.sites().town();
```
~~~

### "@GenerateFocus: ... on X changes nothing, because ..." (a warning) {#generatefocus-field-list-unread}

A field list is set where the record's other settings never read it, so the generated class is the one you would get without it. There are three such pairs:

- `excludeFields` beside a non-empty `includeFields`, which alone decides which fields get navigators.
- `includeFields` or `excludeFields` with `generateNavigators = false`, where no field gets a navigator.
- `widenFields` beside `widenCollections = true`, which widens every field `widenFields` could name.

**Fix.** Remove the list, or the setting that leaves it unread, as the warning says. Either remedy compiles without the warning.

~~~admonish example title="A declaration that produces it"
<!-- verify:reports "excludeFields on Trader changes nothing, because includeFields is set beside it" -->
```java
@GenerateFocus
record Kiosk(String town) {}

@GenerateFocus(includeFields = "home", excludeFields = "relief")
record Trader(String name, Kiosk home, Kiosk relief) {}
```

```
warning: @GenerateFocus: excludeFields on Trader changes nothing, because includeFields is set beside it. A non-empty includeFields gives navigators to the components it names and to no others, so excludeFields is never read. Remove excludeFields from @GenerateFocus on Trader, or remove includeFields to give navigators to every component but those excludeFields names.
```
~~~

### "@GenerateFocus: widenFields on X names "y", whose T is widened without it" (a warning) {#generatefocus-widenfields-cannot-widen}

`widenFields` names a field whose path it cannot change. Either the field's method widens its container anyway, as it does a `List`, an `Optional` or an `Either`, or the field holds no container `widenFields` widens, such as a `String` or an `int[]`. The second reads *"which holds no container widenFields widens"*.

**Fix.** Take the name out of `widenFields`, as the warning says. `widenFields` is for a `Map`, an array of objects or a third-party collection, which a method otherwise leaves whole. A third-party collection needs a `TraversableGenerator` on the annotation processor path, such as those `hkj-processor-plugins` provides, and without one it holds no container `widenFields` widens; the warning offers that remedy too.

~~~admonish note title="Why" collapsible=true
`widenFields` widens what `widenCollections = true` widens, one field at a time: a zero-or-more container that a `TraversableGenerator` on the processor path supports. `List`, `Set`, `Collection`, `Optional` and `Maybe` are always widened, since the processor recognises those types, and a zero-or-one container such as `Either` always is too, so naming one changes nothing. An array of a primitive type is never widened, since `EachInstances.arrayEach()` is `Each<A[], A>` and no primitive is an `A`.
~~~

~~~admonish example title="A declaration that produces it"
<!-- verify:reports "whose List<String> is widened without it" -->
```java
@GenerateFocus(widenFields = "tags")
record Stall(String name, List<String> tags) {}
```

```
warning: @GenerateFocus: widenFields on Stall names "tags", whose List<String> is widened without it. StallFocus.tags() returns a TraversalPath whether widenFields names it or not. Remove widenFields from @GenerateFocus on Stall.
```
~~~

### "@GenerateTraversals: no traversal was generated for component 'X.y' of type `Deque<T>`" (a note)

`@GenerateTraversals` asks the `TraversableGenerator` SPI for each record component, and no generator on the annotation processor path claimed this one.

**Fix.** Declare the component as a container a generator supports: `List`, `Set`, `Collection`, `Map`, `Optional`, an array, or a type one of the [generator plugins](../tooling/generator_plugins.md) covers. For a raw container, give it its element type. For a third-party type, put a `TraversableGenerator` for it on the annotation processor path. A mixed record, one supported container beside one unsupported, keeps the traversals it can have and carries the note for the one it cannot; the note is the reminder, not a gate. A record that wants no traversal for any of its components should not carry `@GenerateTraversals` at all; `@GenerateLenses` on its own still gives every component a lens.

~~~admonish note title="Why" collapsible=true
The component unmistakably holds elements, being a `java.util.Collection` or a `java.util.Map` by erasure. Not generating for it is a gap rather than the expected outcome, and the generated class would otherwise compile with the method silently missing. The second sentence names the unsupported type (`No TraversableGenerator on the annotation processor path supports Deque`). The same note is raised, with a different second sentence, for a container a generator *did* claim but cannot read: a raw `List` or `Set` "is written without a type argument, so there is no element type to focus", and a generator whose focused type argument the type does not have says which argument it wanted.

A component that is not a container at all is passed over without comment: a `String`, an `int`, or a `java.nio.file.Path`, which implements `Iterable` and is why a bare `Iterable` is not the bar.

It is a note rather than a warning on purpose. `@GenerateTraversals` has no per-component opt-out, and a processor warning cannot be suppressed, so a warning would have failed every `-Werror` build with no remedy short of changing the record. A note shows in the compiler output as `Note: ...` and fails nothing.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "no traversal was generated for component" -->
```java
@GenerateTraversals
record Backlog(Deque<String> queue) {}
```
~~~

### "Multiple TraversableGenerator SPI providers with equal priority (N) support type X" (a warning)

Two generators on the annotation processor path both claim the type, and neither outranks the other: `supports()` answers true from both at the same `priority()`.

**Fix.** Rank one of the providers: return `PRIORITY_OVERRIDE` from the one that should win, or `PRIORITY_FALLBACK` from the one that should yield, or drop one from the annotation processor path. The message names both provider classes. A consuming build running javac with `-Werror` turns the warning into an error, so the ranking is the remedy, not optional tidiness. See [How Plugin Discovery Works](../tooling/generator_plugins.md#how-plugin-discovery-works).

~~~admonish note title="Why" collapsible=true
Selection is still deterministic, the first registered wins, but which one that is depends on registration order alone, which is what the warning points out. The same warning is raised whichever annotation asks: `@GenerateTraversals`, `@GenerateFocus` widening or `@ImportOptics`. `@GenerateFocus` raises it once per component.
~~~

### "@TraverseField: the annotation on record component 'X.y' is not applied" (a note)

`@TraverseField` names a `Traverse` for a `Kind<F, A>` component with a declared witness, and this component is not one.

**Fix.** Declare the component as the `Kind<F, A>` the `Traverse` is written for, `Kind<TreeKind.Witness, Tree>` for a `Traverse<TreeKind.Witness>`, with both type arguments given and a witness that is a type rather than a bare or `? super` wildcard, a type variable of the record, or a wildcard bounded by one; or drop the annotation and take the path the component gets on its own, widening it yourself with `each(EachInstances.fromTraverse(traverse))` where the witness is known. See [Custom `Kind` Types with `@TraverseField`](kind_field_support.md#custom-kind-types-with-traversefield).

~~~admonish note title="Why" collapsible=true
The second sentence says which way: the component is not declared as a `Kind` at all (`List<String> is not declared as a Kind<F, A> component`), the `Kind` is written raw and so names neither a witness nor an element, its witness is a bare or `? super` wildcard (`Kind<?, String>`) that stands for no type and so names no `Traverse` instance, or its witness is one of the record's own type variables (`Kind<F, String>` in a `Holder<F>`, or `Kind<? extends F, String>`, whose wildcard resolves to `F`), which stands for any witness, while a `Traverse` is written for one. The component keeps the path it would have had without the annotation, a plain `FocusPath`, or `.each(EachInstances.listEach())` for a `List`, which compiles and is correct as far as it goes; what is missing is the traversal the annotation asked for.

It is a note rather than an error because nothing is broken: the generated class is sound, and the same declaration without the annotation passes without comment. A warning cannot be suppressed and would fail a `-Werror` build with no remedy short of editing the record.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "the annotation on record component 'Inbox.messages' is not applied" -->
```java
@GenerateFocus
record Inbox(
    @TraverseField(traverse = "org.higherkindedj.hkt.list.ListTraverse.INSTANCE")
        List<String> messages) {}
```
~~~

### "@GenerateFocus: record component 'X.y' names a witness the processor does not recognise" (a note)

The component is a `Kind<F, A>` whose witness is one of Higher-Kinded-J's own, but not one the Focus processor has a `Traverse` registered for. Nothing widens it, so the generated method is a plain `FocusPath` focusing the `Kind`.

**Fix.** Add `@TraverseField` naming the `Traverse` instance for the witness, or keep the plain path and widen it yourself with `each(EachInstances.fromTraverse(traverse))`. See [Kind Field Support](kind_field_support.md#convention-based-detection).

~~~admonish note title="Why" collapsible=true
A witness of your own draws no note, since not traversing it is an ordinary choice; a library witness with no registered `Traverse` is a gap you would want to hear about. The note is written once for the component, however many navigators reach the record.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "names a witness the processor does not recognise" -->
```java
@GenerateFocus
record Batch(Kind<NonEmptyListKind.Witness, String> items) {}
```
~~~

---

## `@ImportOptics` and `OpticsSpec` interfaces

### "@ImportOptics: '...' lists no classes to import, so nothing is generated" (a warning) {#importoptics-lists-no-classes}

An `@ImportOptics` on a `package-info.java`, or on a class or interface that does not reach `OpticsSpec`, names no class to import, so it generates nothing.

**Fix.** List the classes to import, as `@ImportOptics({Order.class})`, or remove the annotation. On an interface you meant as a spec, extend `OpticsSpec<S>` for the type its optics are for.

~~~admonish note title="Why" collapsible=true
A warning, because a build that compiles can still be missing the classes it expected, and the first sign would otherwise be a `cannot find symbol` somewhere else. A processor warning cannot be suppressed, so under `-Werror` the remedy is the fix. A class or interface whose supertype another processor writes is read once the supertype exists, so the warning is not given before then.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:reports "lists no classes to import" -->
```java
@ImportOptics
class OrderImports {}
```
~~~

### "@ImportOptics: '....class' names a primitive type, which has no optics to import" {#importoptics-literal-names-no-type}

The class list holds a literal for a primitive type, an array type or `void`, such as `int.class` or `String[].class`. Optics are generated from the declaration of a class, interface, record or enum, and these have none. An array reads `names an array type`, and `void.class` reads `names void`.

**Fix.** Remove the literal from the list.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which has no optics to import" -->
```java
@ImportOptics({int.class})
class CountImports {}
```
~~~

### "@ImportOptics: '...' extends OpticsSpec<...> and also lists classes to import" {#importoptics-spec-lists-classes}

A spec interface also carries a class list. A spec generates the optics its own methods declare, for the type its `OpticsSpec<S>` names, and does not read a class list.

**Fix.** Move the class list to a `package-info.java` or to another class or interface, or remove it.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "and also lists classes to import" -->
```java
final class Session {

    public String user() {
        return "";
    }

    public Session withUser(String user) {
        return this;
    }
}

@ImportOptics({java.time.LocalDate.class})
interface SessionOpticsSpec extends OpticsSpec<Session> {

    @Wither(value = "withUser", getter = "user")
    Lens<Session, String> user();
}
```
~~~

### "@ImportOptics: '...' extends OpticsSpec only through '...'" {#importoptics-indirect-spec}

An interface that lists no classes reaches `OpticsSpec<S>` through another interface rather than declaring it itself. Reading a spec that way is not supported yet.

**Fix.** Declare `OpticsSpec<S>` on the spec itself. Where the interface in between names the source type, the message gives the clause to add beside it, `extends OpticsSpec<Session>, SessionBase`. Where it does not, because it is raw or passes on a type parameter, name the source type in its place: `extends OpticsSpec<Session>`.

An interface that lists classes, and does not declare `OpticsSpec<S>` itself, imports them instead, whatever it extends.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "extends OpticsSpec only through" -->
```java
final class Session {}

interface SessionBase extends OpticsSpec<Session> {}

@ImportOptics
interface SessionOpticsSpec extends SessionBase {}
```
~~~

### "@ImportOptics: '...' implements OpticsSpec but is not an interface" {#importoptics-spec-not-an-interface}

A class that lists no classes reaches `OpticsSpec<S>`, directly, through an interface or through a superclass. A spec is an interface: its abstract methods are the optics to generate, and the generated class stands in for it rather than extending it.

**Fix.** Declare the spec as an interface extending `OpticsSpec<S>` in place of the class. The message names the clause where it can: `Declare it as an interface extending OpticsSpec<Session>`.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "implements OpticsSpec but is not an interface" -->
```java
final class Session {}

@ImportOptics
abstract class SessionOpticsSpec implements OpticsSpec<Session> {}
```
~~~

### "@ImportOptics: '...' inherits the optic method '...' from '...'" {#importoptics-inherited-optic}

A spec interface inherits a method that returns an optic, abstract or `default`, from another interface. A spec generates optics from the methods it declares itself, and reading them from another interface is not supported yet, so the generated class would be missing that optic.

**Fix.** Declare the method on the spec itself, annotated as it is on the interface it comes from. The message writes the signature for the spec's own source type, `Lens<Order, UUID> id()` for a mix-in's `Lens<S, UUID> id()`; reached through a raw clause, it asks for the type arguments instead. For a `default` method, redeclare it on the spec as an abstract method with its copy strategy or hint annotation, which replaces the inherited one. Or move the default out of the mix-in into a static method that calls the generated statics. A method the spec redeclares is its own, and the one it inherits is no longer read.

~~~admonish note title="Why" collapsible=true
Leaving the method out would be quieter and worse: code calling it would fail with `cannot find symbol`, and a `@ThroughField` traversal composing through an inherited lens would fail inside the generated file. A spec extending another `@ImportOptics` spec draws it too, since its own generated class would lack the other's optics. An inherited method that returns something other than an optic, such as `int count()`, is not a declaration of an optic and leaves the spec as it is. So does one taking arguments or declaring type parameters of its own, which a spec could not declare as an optic either.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "inherits the optic method" -->
```java
final class Session {

    public String user() {
        return "";
    }

    public Session withUser(String user) {
        return this;
    }
}

interface UserOptics {

    @Wither(value = "withUser", getter = "user")
    Lens<Session, String> user();
}

@ImportOptics
interface SessionOpticsSpec extends OpticsSpec<Session>, UserOptics {}
```
~~~

### "@ImportOptics: Lens method 'x' carries no copy strategy annotation"

A method on an `OpticsSpec` interface returns `Lens<S, A>` but carries none of `@Wither`, `@ViaConstructor`, `@ViaCopyAndSet` or `@ViaBuilder`, so the processor has no way to know how the external type rebuilds itself.

**Fix.** Add the appropriate hint based on how the source type is copied. See [Optics for External Types](importing_optics.md) and [Database Records with JOOQ](copy_strategies.md) for the full strategy table.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "carries no copy strategy annotation" -->
```java
final class Session {

    public String user() {
        return "";
    }
}

@ImportOptics
interface SessionOpticsSpec extends OpticsSpec<Session> {

    Lens<Session, String> user();
}
```
~~~

### "'XOpticsSpec.foo' is a default method"

A spec interface declares a `default` method. A method body cannot be read during annotation processing, so there is nothing for the generated class to carry.

**Fix.** Keep the spec interface to annotated abstract methods. Composed optics belong in a `static` method on the interface, or in an ordinary utility class; either one calls the generated statics by name, for example `JsonNodeOptics.object().andThen(...)`.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "is a default method" -->
```java
final class Session {

    public String user() {
        return "";
    }
}

@ImportOptics
interface SessionOpticsSpec extends OpticsSpec<Session> {

    default Lens<Session, String> user() {
        return Lens.of(Session::user, (session, user) -> session);
    }
}
```
~~~

### "'XOpticsSpec' declares `OpticsSpec<S>`, which is a type variable"

The spec interface is generic, and its own type parameter is the source type: `interface SessionOpticsSpec<S extends Session> extends OpticsSpec<S>`.

**Fix.** Name the type the optics are for as the type argument, with its own type arguments where it has any: `OpticsSpec<Session>`. Where the bound names a single type that is not raw, the message suggests it for you.
A source type that is itself generic is supported, and the spec names its own type parameters: `interface BoxOpticsSpec<U> extends OpticsSpec<Box<U>>` generates `static <U> Lens<Box<U>, U> value()`. See [Spec Interfaces](optics_spec_interfaces.md#generic-spec-interfaces) for which parameters a generated method declares. It is only a bare type variable, standing for the whole source type, that has no source to read.

~~~admonish note title="Why" collapsible=true
Optics are generated against one named type, read for its members and rebuilt through its constructor, wither or setter, so a type parameter standing for whatever a caller picks has nothing to generate from. An array source type produces the same diagnostic with a different opening, `declares OpticsSpec<String[]>, which is an array type`, and the same remedy.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which is a type variable" -->
```java
class Session {}

@ImportOptics
interface SessionOpticsSpec<S extends Session> extends OpticsSpec<S> {

    @Wither("withUser")
    Lens<S, String> user();
}
```
~~~

### "'XOpticsSpec' declares `OpticsSpec<Box>`, which names the raw type 'Box'"

The source type names a generic type without its arguments.

**Fix.** Name the raw type's arguments in the `OpticsSpec` clause: `OpticsSpec<Box<String>>`, `OpticsSpec<Outer<String>.Holder>`, `OpticsSpec<Box<List<String>>>`. A spec whose optics should stay generic declares its own type parameters and passes them on, `interface BoxOpticsSpec<U> extends OpticsSpec<Box<U>>`. See [Spec Interfaces](optics_spec_interfaces.md#generic-spec-interfaces).

~~~admonish note title="Why" collapsible=true
Every generated optic repeats the source type verbatim, so the generated file, which you cannot edit, would carry a `[rawtypes]` warning that the `@SuppressWarnings` on your own spec does not cover, and a `@ViaConstructor` rebuild read under a raw type erases its parameters into an `[unchecked]` call besides. Three shapes draw the error: the source type itself written bare (`OpticsSpec<Box>` for a `Box<X>`), a member type behind a generic outer written bare (`OpticsSpec<Outer.Holder>`, raw by JLS 4.8 even though `Holder` declares nothing of its own), and a raw type argument (`OpticsSpec<Box<List>>`). Raw is not the same as bare: a non-generic source type, or a static nested type of a generic outer, has no arguments to supply and is accepted as written. A record component is different, and the optics generated for one carry that suppression for you: a component is the declared shape of your data, while an `OpticsSpec` clause is a signature written for the generator, where the type arguments are yours to supply. The focus type a spec method declares is the same kind of thing as a component, and its optic method carries the suppression too.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which names the raw type 'Box'" -->
```java
record Box<T>(T value) {

    Box<T> withValue(T value) {
        return new Box<>(value);
    }
}

@ImportOptics
interface BoxOpticsSpec extends OpticsSpec<Box> {

    @Wither("withValue")
    Lens<Box, String> value();
}
```
~~~

### "@ImportOptics: type '...' names the type parameter 'T' of '...', which hides the 'T' of its enclosing class '...'"

An inner class imported by class literal declares a type parameter under a name its enclosing class already uses.

**Fix.** Import it through a spec interface, which names the type under type parameters of its own, `interface InOpticsSpec<A, B> extends OpticsSpec<Outer<A>.In<B>>`, and give each field a `@Wither` lens. See [Generic Spec Interfaces](optics_spec_interfaces.md#generic-spec-interfaces).

~~~admonish note title="Why" collapsible=true
An inner class of a generic class is imported under its enclosing class's type parameters, because without them the type would be raw: `@ImportOptics({Outer.In.class})` names `Outer<X>.In<Y>` and declares both parameters on each generated method. Inside `In`, a parameter named like `Outer`'s hides it, which is harmless there, but one method cannot declare two type parameters with the same name. A static nested class has no enclosing instance type and takes only its own parameters, so it never draws this.

A class read from a jar draws it too, but its compiled signatures cannot tell the two parameters apart and read both as the inner one. For such a class the spec names them as one: `OpticsSpec<Outer<A>.In<A>>`.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which hides the 'T' of its enclosing class 'Outer'" -->
```java
class Outer<T> {

    final class In<T> {

        private final T item;

        In(T item) {
            this.item = item;
        }

        public T item() {
            return item;
        }

        public In<T> withItem(T item) {
            return new In<>(item);
        }
    }
}

@ImportOptics({Outer.In.class})
class OuterImports {}
```
~~~

### "@ThroughField: '...' reaches field 'items', which is declared as `ArrayList<String>` rather than as the List interface"

The spec's own lens for the field focuses something narrower than a container interface: a concrete container such as `ArrayList`, or another interface such as `Deque`. Auto-detection matches `List`, `Set`, `Collection`, `Map`, `Optional` and reference-type arrays, on the interface itself.

**Fix.** Name a traversal that rebuilds the declared type, `Traversals.forIterableCollecting(ArrayList::new)` for a list-shaped container or `Traversals.forMapValuesCollecting(TreeMap::new)` for a map, exposed as a static method and named fully qualified: `@ThroughField(field = "items", traversal = "com.example.MyTraversals.forArrayList()")`. Where the type is yours, declaring the field as the interface (`List<String>`) is the simpler route. See [`@ThroughField` auto-detection](copy_strategies.md#throughfield-auto-detection).

~~~admonish note title="Why" collapsible=true
The type the message names is that lens focus. Each standard traversal promises no more than the interface type (`Traversals.forList()` hands back an unmodifiable `List`), and the composed optic writes that value back into the field through the lens; a field declared as something narrower, a concrete container (`ArrayList`, `HashSet`, `TreeMap`) or another interface (`Deque`, `SortedSet`), cannot take it, so the generated traversal would throw `ClassCastException` on first use, on a read as well as a write. The message names the interface the field's type implements. An array of a primitive (`int[]`) draws the sibling message: the array traversal walks an `Object[]`, which an `int[]` is not.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "rather than as the List interface" -->
```java
final class Shelf {

    public ArrayList<String> items() {
        return new ArrayList<>();
    }

    public Shelf withItems(ArrayList<String> items) {
        return this;
    }
}

@ImportOptics
interface ShelfOpticsSpec extends OpticsSpec<Shelf> {

    @Wither("withItems")
    Lens<Shelf, ArrayList<String>> items();

    @ThroughField(field = "items")
    Traversal<Shelf, String> eachItem();
}
```
~~~

### "@ThroughField: '...' composes through a lens named 'items', which the spec does not declare"

A `@ThroughField` traversal is generated as the spec's own lens for the field composed with the container traversal, `Spec.items().andThen(...)`. The spec has to declare that `Lens<S, F> items()` alongside it, with its copy strategy, and this one does not. Without the lens the generated file could only fail with `cannot find symbol`, so the processor refuses the declaration instead.

**Fix.** Declare the lens method for the field on the spec, or use `@TraverseWith` to name a traversal over the source type that stands on its own.

A lens declared raw, `Lens items()`, reads as `which the spec declares raw`. It is refused for the same reason: the traversal composes onto what the lens focuses, and a raw lens says nothing about that. Declare it with both type arguments, as `Lens<Sack, List<String>>`.

A lens declared `static` or `private` reads as `which the spec declares static` or `private`. A method with a body stays on the spec, and the traversal composes through the generated class's lens, so declare `items` as an abstract `Lens` method with its copy strategy.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which the spec does not declare" -->
```java
final class Shelf {

    public List<String> items() {
        return List.of();
    }
}

@ImportOptics
interface ShelfOpticsSpec extends OpticsSpec<Shelf> {

    @ThroughField(field = "items")
    Traversal<Shelf, String> eachItem();
}
```
~~~

### "@ThroughField: '...' declares focus 'Integer' over field 'items' of type `List<String>`, whose elements the standard traversal hands back as 'String'"

The method's declared focus does not contain what the auto-detected traversal hands back. That is the container's elements, a `Map`'s values, an `Optional`'s element, or `Object` where the element sits behind a super- or unbounded wildcard.

**Fix.** Declare the focus as the type the message names, or name a traversal of your own with `@ThroughField(field = "items", traversal = "...")`, which is the author's undertaking that it rebuilds the declared shape.

~~~admonish note title="Why" collapsible=true
A focus that does not contain that type could only compile through a cast, throwing `ClassCastException` on the caller's first `getAll` or `modify` where it narrows, and letting ill-typed writes into the container where it widens. Containment, not sameness: a wildcard focus over the element (`? extends CharSequence` over `CharSequence` elements, or its element's supertype bound) stays accepted, and an extends-wildcard element is held to its bound (`List<? extends CharSequence>` hands back `CharSequence`).
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "hands back as 'String'" -->
```java
final class Shelf {

    public List<String> items() {
        return List.of();
    }

    public Shelf withItems(List<String> items) {
        return this;
    }
}

@ImportOptics
interface ShelfOpticsSpec extends OpticsSpec<Shelf> {

    @Wither("withItems")
    Lens<Shelf, List<String>> items();

    @ThroughField(field = "items")
    Traversal<Shelf, Integer> eachItem();
}
```
~~~

### "@InstanceOf target 'com.example.Foo' is not a subtype of source type 'com.example.Base'"

The class passed to `@InstanceOf(SubType.class)` is not a subclass of the optic's source type.

**Fix.** Verify that `SubType` extends or implements the spec's `<S>` parameter. If you are working with sum types that don't use a sealed hierarchy (such as Jackson's pre-3.x `JsonNode`), use `@MatchWhen` with predicate and getter method names instead.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "is not a subtype of source type" -->
```java
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

@ImportOptics
interface PaymentOpticsSpec extends OpticsSpec<Payment> {

    @InstanceOf(String.class)
    Prism<Payment, String> text();
}
```
~~~

### "@InstanceOf: '...' tests 'Grid', which no '...' can be" {#instanceof-never-matches}

The class `@InstanceOf` names is a subtype of the source's class, but its own declaration writes a different type where the source type writes one, so no value of the source would pass the test.

**Fix.** Name a class in `@InstanceOf` that the source type can be, or declare the prism on a spec of its own over the type the class declares, which the message names: `Node<Y[]>` for a `Grid<Y>`. `NodeOpticsSpec` keeps the source its other methods were written for:

<!-- verify -->
```java
class Node<T> {}

class Grid<Y> extends Node<Y[]> {}

class Counts extends Node<int[]> {}

@ImportOptics
interface NodeOpticsSpec extends OpticsSpec<Node<int[]>> {

    @InstanceOf(Counts.class)
    Prism<Node<int[]>, Counts> counts();
}

@ImportOptics
interface GridOpticsSpec<Y> extends OpticsSpec<Node<Y[]>> {

    @InstanceOf(Grid.class)
    Prism<Node<Y[]>, Grid<Y>> grid();
}
```

~~~admonish note title="Why" collapsible=true
A type argument has to be exactly the type the source writes in its place. A `Grid<Y>` is a `Node<Y[]>`, which is a `Node<int[]>` only with `Y` as `int`, and a type argument is never a primitive. The same holds wherever no choice of the class's parameters makes the type it declares the source type. A `Box<X> extends Node<List<X>>` is never a `Node<Set<String>>`, nor a `Node<List<?>>`, since no `List<X>` is exactly the type `List<?>`. A `Twin<X> extends Node<Pair<X, X>>` is never a `Node<Pair<String, Integer>>`. The prism would never match, so one of the two declarations is the mistake, and the processor says so at the spec method. A wildcard the source writes as one of its own arguments admits any type within its bounds, so only a type outside them is refused: no `Node<? extends Number>` is a `Node<Object>`. A type variable in the source could stand for any class, so it rules nothing out unless a primitive stands against it.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "tests 'Grid', which no" -->
```java
class Node<T> {}

class Grid<Y> extends Node<Y[]> {}

@ImportOptics
interface NodeOpticsSpec extends OpticsSpec<Node<int[]>> {

    @InstanceOf(Grid.class)
    Prism<Node<int[]>, Node<int[]>> grid();
}
```
~~~

### "@InstanceOf: '...' declares its focus as `Circle<T>`, which the test cannot narrow to"

The prism promises a type argument the test cannot check.

**Fix.** Declare the focus as `Circle<?>`, which is what the test earns, or narrow through a predicate and getter of the source type with `@MatchWhen`, which reads the argument off the source rather than inventing it. Where the source type does carry the argument, as in a `Circle<X> implements Shape<X>` reached from `Shape<T>`, the prism may promise it and the generated test names it. See [Spec Interfaces](optics_spec_interfaces.md#parameterised-targets).

Where what the test earns is no source type, no focus would build the source back: a `Box<?>` is no `Node<U>` for `Box<X> extends Node<List<X>>`. The message then offers another class, or the prism on a spec of its own over `Node<List<X>>`, as for [a test that would never match](#instanceof-never-matches).

~~~admonish note title="Why" collapsible=true
`@InstanceOf` takes a class constant, which is raw, and the generated `instanceof` runs after erasure, so the only arguments the narrowed value is known to have are the ones the source type pins down. `class Circle<X> extends Shape` reached from a `Shape` that declares no parameters pins none: every instantiation passes the same test, and a `Prism<Shape, Circle<T>>` would hand any of them back as the `T` the caller asked for, to fail on the first read.

An inner class of a generic class is narrowed the same way, its enclosing class's arguments included. From a source that pins nothing of `Outer`'s, `Outer.Plain.class` earns `Outer<?>.Plain`, so a focus of `Outer<String>.Plain` draws this message.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which the test cannot narrow to" -->
```java
class Shape {}

class Circle<X> extends Shape {}

@ImportOptics
interface ShapeOpticsSpec<T> extends OpticsSpec<Shape> {

    @InstanceOf(Circle.class)
    Prism<Shape, Circle<T>> circle();
}
```
~~~

### "@InstanceOf: '...' tests 'Outer.Inner', where Y extends X and '...' pins nothing to Outer's X" {#instanceof-unwritable-bound}

A parameter the test pins is bounded by a parameter the source pins nothing to. A bound that only names it, such as `Y extends List<X>`, reads as `List<?>`, and the argument the source pins has to be within it, as [an argument outside its bound](#instanceof-out-of-bound) explains.

**Fix.** Narrow through a predicate and getter of the source type with `@MatchWhen`, or write the prism by hand with `Prism.of`. Where you can change the classes, a source that pins `X` as well lets the test name it.

~~~admonish note title="Why" collapsible=true
The test writes a wildcard wherever the source pins nothing, so `Outer.Inner.class` narrowed from a `Node<U>` is tested as `Outer<?>.Inner<U>`. A bound of `X` on `Inner`'s parameter then has nothing to name, and javac refuses that type: `U` is not within the bound of a wildcard it cannot see. A permitted subtype of a sealed interface draws the same refusal from `@GeneratePrisms`, as [does not bind](#generateprisms-does-not-bind) shows.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "tests 'Outer.Inner', where Y extends X and" -->
```java
class Node<U> {}

class Outer<X> {

    class Inner<Y extends X> extends Node<Y> {}
}

@ImportOptics
interface NodeOpticsSpec<U> extends OpticsSpec<Node<U>> {

    @InstanceOf(Outer.Inner.class)
    Prism<Node<U>, Node<U>> inner();
}
```
~~~

### "@InstanceOf: '...' tests '...', where Y extends Number and U is not a 'Number'" {#instanceof-out-of-bound}

The source pins a parameter of the class `@InstanceOf` names to an argument outside that parameter's bound.

**Fix.** The message ends in one of two lines:

- **It declares a spec parameter where one bound will do.** The declaration, as in `interface NodeOpticsSpec<U extends Number>`, keeps every bound the parameter has, so it can replace the one there.
- **Otherwise it offers another class for `@InstanceOf`.** The message still lists every bound the argument has to meet, so a spec that can take them all may declare them itself, as in `U extends Number & Comparable<U>`.

Either way, `@MatchWhen` narrows through a predicate and getter of the source type instead.

~~~admonish note title="Why" collapsible=true
The test names the type a matching value must be, so `Gauge.class` narrowed from a `Node<U>` is tested as `Gauge<U>`. javac refuses a type argument outside its parameter's bound, and with `Y extends Number` and nothing said about `U`, the generated file would not compile. No `Node<U>` can be a `Gauge` unless `U` is a `Number`, which is why a bound on the spec is the fix. javac reads a parameter the source leaves free as the `?` the test writes for it. In `class Rack<A, B extends List<A>> extends Node<B>`, the argument the source pins to `B` has to be a `List<?>`.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "where Y extends Number and U is not a 'Number'" -->
```java
class Node<U> {}

class Gauge<Y extends Number> extends Node<Y> {}

@ImportOptics
interface NodeOpticsSpec<U> extends OpticsSpec<Node<U>> {

    @InstanceOf(Gauge.class)
    Prism<Node<U>, Node<U>> gauge();
}
```
~~~

### "@InstanceOf: '...' narrows to '...', which is not a '...'"

The class the annotation names is not one the prism's focus type accepts.

**Fix.** Name the class the focus declares, or declare the focus as a supertype of the narrowed type. A prism whose focus is deliberately wider than the test is fine, as in `@InstanceOf(ArrayList.class) Prism<Collection<T>, List<T>>`; it is only a focus the narrowed value cannot be assigned to that is rejected. Where the narrowed type is no source type, no supertype of it would do. The message then offers another class, the focus's own where that is a source type, or a spec of its own over the type the narrowed class declares.

~~~admonish note title="Why" collapsible=true
Either the two are unrelated, or the source type pins the target's argument to something the focus does not agree with: `OpticsSpec<Node<String>>` narrowed to `Leaf` can only be a `Leaf<String>`, whatever a `Prism<Node<String>, Leaf<U>>` says.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "narrows to 'Card', which is not a 'Bank'" -->
```java
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

@ImportOptics
interface PaymentOpticsSpec extends OpticsSpec<Payment> {

    @InstanceOf(Card.class)
    Prism<Payment, Bank> card();
}
```
~~~

### "@ViaCopyAndSet: copyConstructor names '...', which does not resolve to a type"

`copyConstructor` is a plain string, resolved as a fully qualified class name only: it is not read against the spec interface's imports, and it takes no type arguments.

**Fix.** Give the class's fully qualified name (`com.example.BaseConfig`; a nested class is `com.example.Outer.Base`), the class alone without type arguments, since the processor supplies those from the source type's own `extends` clause. Drop the attribute to pass the source unchanged.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which does not resolve to a type" -->
```java
// Endpoint is the legacy type from the copy-strategies page: two copy constructors,
// taking BaseEndpoint and Audited, and a setHost setter.
@ImportOptics
interface EndpointOpticsSpec extends OpticsSpec<Endpoint> {

    @ViaCopyAndSet(copyConstructor = "com.example.MissingBase", setter = "setHost")
    Lens<Endpoint, String> host();
}
```
~~~

### "@ViaCopyAndSet: copyConstructor names '...', which 'S' does not extend or implement"

The generated setter passes the source to the copy constructor as `(ParameterType) source`, so only a supertype of `S` can be named there.

**Fix.** Name a class or interface `S` extends or implements, or drop the attribute.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "does not extend or implement" -->
```java
@ImportOptics
interface EndpointOpticsSpec extends OpticsSpec<Endpoint> {

    @ViaCopyAndSet(copyConstructor = "java.lang.Thread", setter = "setHost")
    Lens<Endpoint, String> host();
}
```
~~~

### "@ViaCopyAndSet: copyConstructor names '...', which is not public and so cannot be named from '...'"

The generated optics class has to write the cast, so it has to be able to name the type. A package-private supertype is invisible from the package the optics class is generated into, even though `new S(source)`, which never names it, would have compiled.

**Fix.** Name a public supertype, generate into that package with `@ImportOptics(targetPackage = ...)`, or drop the attribute.
### "@ViaCopyAndSet: copyConstructor names '...', which '...' reaches as '...', and no constructor accepts"

The name is a genuine supertype, but no single-argument constructor of `S` takes the type `S` actually reaches it as. `java.lang.Object` and marker interfaces such as `Serializable` reach this often.

**Fix.** Name a supertype of `S` that one of the listed constructors takes, as the class alone without type arguments, or drop the attribute. The list carries type arguments and the attribute does not, so read it to recognise your supertype in it rather than to copy from it, and a listed type that is not a supertype of `S` cannot be named at all. The attribute is only needed when the copy constructor is overloaded; see [Copy Strategies](copy_strategies.md#viacopyandset-legacy-types-with-a-copy-constructor-and-setters).

~~~admonish note title="Why" collapsible=true
The message names both the type you gave and the one `S` reaches, which differ when `S`'s own `extends` clause pins the arguments: `class PNode<X> extends PBase<String>` reaches `PBase` as `PBase<String>`, whatever `X` is.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "and no constructor accepts" -->
```java
@ImportOptics
interface EndpointOpticsSpec extends OpticsSpec<Endpoint> {

    @ViaCopyAndSet(copyConstructor = "java.lang.Object", setter = "setHost")
    Lens<Endpoint, String> host();
}
```
~~~

### "@ViaCopyAndSet: '...' is written with a wildcard type argument"

The source type carries a wildcard, `OpticsSpec<Node<?>>`, and the strategy rebuilds it through a constructor.

**Fix.** Name the type the wildcard stands for, or switch to `@Wither` or `@ViaBuilder`, which rebuild through methods and name no constructor, so a wildcard source type is no obstacle there. For an abstract class or an interface, name a concrete class as the spec's source type, or switch to `@Wither` or `@ViaBuilder` likewise. Every other refusal's remedy offers only those two strategies for such a source type.

~~~admonish note title="Why" collapsible=true
`new Node<?>(...)` is not something that can be written, whatever the arguments. `@ViaConstructor` reports the same thing for the same reason. An inner class draws the sibling message, because its constructor call needs an enclosing instance the generated class has no way to reach. So do an abstract class and an interface, which cannot be instantiated at all.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "is written with a wildcard type argument" -->
```java
final class Slot<T> {

    private String label = "";

    Slot() {}

    Slot(Slot<T> other) {
        this.label = other.label;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}

@ImportOptics
interface SlotOpticsSpec extends OpticsSpec<Slot<?>> {

    @ViaCopyAndSet(setter = "setLabel")
    Lens<Slot<?>, String> label();
}
```
~~~

### "@ViaCopyAndSet: The generated lens returns the copy it calls '...' on, and drops what '...' returns" (a warning) {#viacopyandset-dropped-result}

The setter is spelt as a wither, `withTitle`, and may return the source type, but not every method of that name the new value can be passed to returns it. One returns a supertype, a raw type, `void`, or a type that holds the source type, such as an `Optional`. So the lens calls it for its effect and hands back the copy. A wither leaves the copy as it was, and then `set` changes nothing.

**Fix.** Name a setter that sets the copy, or rebuild the source type with `@ViaBuilder` or `@ViaConstructor`. Where `@Wither` would rebuild through the method the focus binds, the warning offers it in place of naming another setter. Where the method does set the copy it is called on, as a fluent `withTitle` returning its base type does, annotate the lens method `@SuppressWarnings("dropped-result")`, or the spec interface to cover each lens it declares.

~~~admonish note title="Why" collapsible=true
A setter spelt as a wither is taken at its word, and the lens hands back what it returns. That needs the result to be the source type whichever method of that name the call binds, so every method the new value can be passed to counts. A call that binds one returning no source type drops nothing, and does not warn. So a `void withTitle(Object)` beside a `withTitle(String)` that returns the source type warns, though a `String` binds the second. So does a retag whose type the call infers from its argument, such as `<U> Memo<U> withId(U)`. A `set` method is never taken at its word, since one that returns the source type may hand back the value it replaced, so it never warns.
~~~

For this declaration, the warning reads `The lens can hand back the result only where every method of that name the new value can be passed to returns a 'Hedge', and 'withTitle(String)' returns 'Shrub'`.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:reports "and 'withTitle(String)' returns 'Shrub'" -->
```java
class Shrub {

    protected String title = "";

    public Shrub withTitle(String title) {
        Shrub copy = new Shrub();
        copy.title = title;
        return copy;
    }
}

final class Hedge extends Shrub {

    Hedge() {}

    Hedge(Hedge other) {
        this.title = other.title;
    }

    public String getTitle() {
        return title;
    }
}

@ImportOptics
interface HedgeOpticsSpec extends OpticsSpec<Hedge> {

    @ViaCopyAndSet(setter = "withTitle")
    Lens<Hedge, String> getTitle();
}
```
~~~

### "@Wither: '...' returns '...', not the source type '...'"

The method a spec's `@Wither` call binds hands back something other than the source type the spec declares: that type raw, the type under other arguments, or a supertype.

**Fix.** Name a wither that returns the source type. Where the wither returns the type under fixed arguments, `Memo<String> withId(String)` on a `Memo<T>`, declare the spec over that instantiation, `OpticsSpec<Memo<String>>`, and it serves as it is. Otherwise rebuild the source type with `@ViaBuilder` or `@ViaConstructor`, or with `@ViaCopyAndSet` through a setter that sets the copy it is called on. The same wither named as that setter has its new value dropped, and draws [the dropped-result warning](#viacopyandset-dropped-result). See [Copy Strategies](copy_strategies.md#wither-types-with-withx-methods).

~~~admonish note title="Why" collapsible=true
The generated lens sets through the wither and hands its result back as the source type. A raw return gets there only by an unchecked conversion, in a generated file your own `@SuppressWarnings` does not reach, and any other type does not get there at all: `Base<String>`, inherited by a `Sub extends Base<String>`, is not a `Sub`. The wither is read on the source type as the spec names it, which is why the fixed-argument case above works, and a retag, `<U> Memo<U> withId(String)`, is accepted because the call infers `U` back to the spec's argument. A variable that only a wildcard argument stands for, as in a spec over `Memo<? extends Number>`, is not inferred, and such a wither is refused. A wither that returns the class's own parameter is not one of those: `S withName(String)` on a `Shape<S extends Shape<S>>` rebuilds a `Shape<?>`.

Where the name is overloaded, the method checked is the one the call binds. The lens passes the new value typed by its focus, and javac chooses among the overloads by that type, so beside a `withN(int)` that returns the source type, an `Object withN(Integer)` is the one an `Integer` focus calls, and the one refused. Where that choice rests on inference, a method whose parameter is one of its own type variables or one taking varargs, the name is accepted when one of the methods the call might bind is an instance method returning the source type, and javac checks the call itself. A focus written as a wildcard is read the same way, since such a lens infers its focus from the getter as much as from the wildcard. Imported by class literal instead, `@ImportOptics({Memo.class})`, such a wither is not paired at all, under the [pairing rule](importing_optics.md#wither-classes-to-lenses).
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "not the source type 'Memo<T>'" -->
```java
final class Memo<T> {

    private final String id;

    Memo(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @SuppressWarnings("rawtypes")
    public Memo withId(String id) {
        return new Memo<>(id);
    }
}

@ImportOptics
interface MemoOpticsSpec<T> extends OpticsSpec<Memo<T>> {

    @Wither(value = "withId", getter = "id")
    Lens<Memo<T>, String> id();
}
```
~~~

### "@Wither: '...' has no method '...' for the generated lens to call"

The method a spec's `@Wither` names is not one the generated class can call on the source type: the name is misspelt, or the method is `private`, or package-private in another package. Where the name is declared but out of reach, the message says so rather than claiming the type has no such method.

**Fix.** Correct the name. The message lists the source type's withers, its one-parameter instance methods that hand it back, with the value each one takes. It offers the `with` method you likely meant: one a near miss away, or one naming the same value, `withId` for `id` or `setId`. Where the type has none, rebuild it with `@ViaBuilder`, `@ViaConstructor` or `@ViaCopyAndSet`. See [Copy Strategies](copy_strategies.md#wither-types-with-withx-methods).

~~~admonish note title="Why" collapsible=true
The generated lens sets through `source.withX(newValue)`, so the method has to exist, declared or inherited, where the class generated beside your spec can reach it. The name is checked at the spec method rather than left to javac, whose `cannot find symbol` would point into the generated file.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "has no method 'withIdentifier' for the generated lens to call" -->
```java
final class Licence {

    private final String id;

    Licence(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Licence withId(String id) {
        return new Licence(id);
    }
}

@ImportOptics
interface LicenceOpticsSpec extends OpticsSpec<Licence> {

    @Wither(value = "withIdentifier", getter = "id")
    Lens<Licence, String> id();
}
```
~~~

### "@Wither: No method '...' of '...' takes the lens's focus type '...'"

The source type has methods of the name `@Wither` gives, and none of them takes the value the lens sets, whose type is the lens's focus. Where none takes one argument, the name is usually an accessor's, `id` written for `withId`, and the message offers the wither for that value. The `@ViaBuilder` and `@ViaCopyAndSet` messages offer the setter for that value the same way, in the strategy's convention.

**Fix.** Name a wither that takes the value the getter reads, or point `getter` at an accessor one of the listed methods takes and declare the focus as its type. The focus alone rarely settles it: the getter pins it from below, so a focus wide enough to read the getter is usually too wide for a parameter the getter's own type already failed. The exception is a focus wider than the wrapper of a primitive getter, `Number` over an `int` one, which is never unboxed: there the message offers the wrapper, `Integer`, as the focus to declare. Otherwise rebuild the source type with `@ViaBuilder`, `@ViaConstructor` or `@ViaCopyAndSet`. Where the parameter is a type the source type's wildcard stands for, the `T` of `withValue(T)` on a spec over `Cell<?>`, declare the spec over the type the wildcard stands for, a type parameter of the spec if need be: `interface CellOpticsSpec<T> extends OpticsSpec<Cell<T>>`.

~~~admonish note title="Why" collapsible=true
The generated setter passes the new value to the wither as the focus type, so a method taking another type cannot be called with it. A parameter that a wildcard argument of the source type stands in takes no value at all, since the type it stands for is unknown, which is why that case is fixed on the source type rather than the focus. A lens whose *focus* is a wildcard is not checked here: javac infers such a focus from the getter as much as from the wildcard, so which method the call binds is left to javac.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "No method 'withCount' of 'Tally' takes the lens's focus type 'String'" -->
```java
final class Tally {

    private final String count;

    Tally(String count) {
        this.count = count;
    }

    public String count() {
        return count;
    }

    public Tally withCount(Integer count) {
        return new Tally(String.valueOf(count));
    }
}

@ImportOptics
interface TallyOpticsSpec extends OpticsSpec<Tally> {

    @Wither(value = "withCount", getter = "count")
    Lens<Tally, String> count();
}
```
~~~

### "@Wither: The generated call to '...' cannot choose between '...' and '...'"

More than one overload of the wither takes the lens's focus, and none takes it more closely than the rest, so javac would report the generated call as ambiguous.

**Fix.** Declare the lens's focus as the parameter type of the overload you mean. Below, a `Lens<Ident, CharSequence>` reaches only `withId(CharSequence)`, and the getter's `String` is still a `CharSequence`.

~~~admonish note title="Why" collapsible=true
javac calls the overload whose parameter is the most specific of those the argument fits. A `String` fits both `Serializable` and `CharSequence`, and neither of those is a subtype of the other, so there is no most specific one. Naming the focus as one of the parameter types leaves that overload the only one the argument fits.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "cannot choose between 'withId(Serializable)' and 'withId(CharSequence)'" -->
```java
final class Ident {

    private final String id;

    Ident(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Ident withId(java.io.Serializable id) {
        return new Ident(id.toString());
    }

    public Ident withId(CharSequence id) {
        return new Ident(id.toString());
    }
}

@ImportOptics
interface IdentOpticsSpec extends OpticsSpec<Ident> {

    @Wither(value = "withId", getter = "id")
    Lens<Ident, String> id();
}
```
~~~

### "@Wither: '...' is static, so the generated lens cannot rebuild a '...' through it"

The overload of the wither that the lens's focus binds is a `static` method.

**Fix.** Declare the lens's focus as the parameter type of an instance overload, which is what binds it instead; or name an instance wither; or rebuild the source type with `@ViaBuilder`, `@ViaConstructor` or `@ViaCopyAndSet`.

~~~admonish note title="Why" collapsible=true
The generated lens calls the wither on the value it sets, and a static method never reads that value, so whatever it builds keeps none of the fields it was not passed. The call is checked among every method of the name, static ones included, because javac weighs them all: here a `String` binds the static `withId(CharSequence)` ahead of the instance `withId(Object)`.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "'withId(CharSequence)' is static" -->
```java
final class Token {

    private final String id;

    Token(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Token withId(CharSequence id) {
        return new Token(id.toString());
    }

    public Token withId(Object id) {
        return new Token(String.valueOf(id));
    }
}

@ImportOptics
interface TokenOpticsSpec extends OpticsSpec<Token> {

    @Wither(value = "withId", getter = "id")
    Lens<Token, String> id();
}
```
~~~

### "'...' has no method '...()' for the generated lens to read the value it focuses"

Every copy strategy reads with the same lambda, `source -> source.getX()`. `@Wither` and `@ViaBuilder` name that accessor in their `getter`; `@ViaCopyAndSet` and `@ViaConstructor` read through the lens method's own name. The name here is not a zero-parameter instance method the generated class can call: it is misspelt, `static`, takes an argument, or is out of reach.

The same shape of message covers every name a strategy calls with no argument, each ending on what the generated lens wanted it for:

| Ends on | The name that is missing |
|---------|--------------------------|
| `for the generated lens to read the value it focuses` | the `getter`, or the lens method that stands in for it |
| `for the generated lens to rebuild through` | `@ViaBuilder`'s `toBuilder` |
| `for the generated lens to finish the value it rebuilds` | `@ViaBuilder`'s `build`, read on what the setter hands back |
| `for the generated lens to read the argument '...'` | an accessor named in `@ViaConstructor`'s `parameterOrder` |

**Fix.** Point the strategy's attribute at a method the type declares. The message offers one where the name is a near miss, or the same accessor under another naming convention, however many letters apart: `getName` for `name`, `name` for `getName`, `isActive` for `getActive`. On a protobuf-java message it offers the getter that reads a repeated or map field whole, `getTagsList` for `tags` or `getTags`, and `getPointsMap` for `points`. No offer is ever a deprecated method, such as a map field's `getPoints()`, since the generated file's call to one fails a build under `-Werror`. Where the strategy has no such attribute, name the lens method after the accessor it reads.

~~~admonish note title="Why" collapsible=true
The generated optic calls the name as written. Checked here, the spec method is where the mistake is reported, rather than `cannot find symbol` inside a file you did not write. A `static` method is not one, since the lens calls it on the value it focuses; nor is one that takes an argument, since the lens passes none.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "has no method 'getIdentifier()' for the generated lens to read the value it focuses" -->
```java
final class Permit {

    private final String id;

    Permit(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Permit withId(String id) {
        return new Permit(id);
    }
}

@ImportOptics
interface PermitOpticsSpec extends OpticsSpec<Permit> {

    @Wither(value = "withId", getter = "getIdentifier")
    Lens<Permit, String> id();
}
```
~~~

### "'...()' reads '...', not the lens's focus '...'"

The accessor exists, and reads a value of another type. `LocalDate` is the standard example: `withMonth` takes an `int`, while `getMonth()` reads a `Month`, so the two do not make a lens between them.

**Fix.** Point the strategy's `getter` at an accessor that reads the focus, `getMonthValue()` for that pair, or declare the lens over what this one reads and rebuild through a method that takes it. Where the strategy reads through the lens method's own name, rename the lens method.

~~~admonish note title="Why" collapsible=true
A lens reads and writes one type. The getter pins it from below, so a focus the getter can fill and the wither can take has to be a type they share; where they share none, no focus makes the pair work, and the mismatch is better reported here than as an inference failure in generated code. A primitive accessor reads into its wrapper, `int` into `Integer`, so that pair is not a mismatch.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "reads 'Month', not the lens's focus 'Integer'" -->
```java
enum Month { JANUARY, FEBRUARY }

final class Renewal {

    private final int month;

    Renewal(int month) {
        this.month = month;
    }

    public Month getMonth() {
        return Month.values()[month - 1];
    }

    public int getMonthValue() {
        return month;
    }

    public Renewal withMonth(int month) {
        return new Renewal(month);
    }
}

@ImportOptics
interface RenewalOpticsSpec extends OpticsSpec<Renewal> {

    @Wither(value = "withMonth", getter = "getMonth")
    Lens<Renewal, Integer> month();
}
```
~~~

### Unchecked reads and rebuilds through a raw type {#spec-unchecked-conversion}

The external class declares an accessor, a builder or a supertype raw, or the spec writes the focus raw. The generated lens would read or rebuild through it in a way javac flags as unchecked. These messages report it, under the strategy's own tag:

| The message says | What it means |
|------------------|---------------|
| `'getItems()' reads the raw type 'List', which reaches the lens's focus 'List<String>' only through an unchecked conversion` | The accessor reads a raw type, or a class that extends one, and the lens hands it back as a focus with type arguments |
| `'withTyped(List<String>)' takes the lens's focus, the raw type 'List', only through an unchecked conversion to 'List<String>'` | The focus is written raw, and the method the lens rebuilds through takes the type with its arguments. For `@ViaConstructor` it reads `takes what 'getItems()' reads` where an accessor's value goes to the constructor |
| `'toBuilder()' hands back the raw type 'Docket.Builder', so the generated call to 'id(T)' on it is unchecked` | The builder comes back raw, and erasing it changes a parameter of the setter: a type variable such as `T`, or a type with arguments such as `List<String>` |
| `'Doc' inherits 'setValue(T)' from the raw type 'Base', so the generated call to it is unchecked` | The source type extends a raw type, and the method called is one it inherits from there |
| `'build()' returns the raw type 'Tote', which reaches the source type 'Tote<String>' only through an unchecked conversion` | The builder chain turns raw at some step, so `build()` returns the erasure of the source type |
| `'items(List<T>)' takes a type that names what a wildcard of 'Bin<?>' stands for` | The focus is written raw, and the method takes the type a wildcard stands for, in the source or in the builder `toBuilder()` hands back |

**Fix.** Read and rebuild through members that name their types with arguments: an accessor that reads `List<String>`, a method that takes it, a builder whose steps hand back `Builder<T>`. Where the class offers only raw members, declare the lens over the raw type they read, `Lens<Shelf, List>`, with `@SuppressWarnings("rawtypes")` on the lens method. Where a wildcard of the source stands in the parameter, declare the spec with a type argument in its place and the focus as the type the method then takes: `interface BinOpticsSpec<T> extends OpticsSpec<Bin<T>>` with `Lens<Bin<T>, List<T>>`. Where `toBuilder()` hands the wildcard back, point `toBuilder` at a method that hands back the builder with its type arguments. Otherwise rebuild with another strategy.

~~~admonish note title="Why" collapsible=true
A raw type is not refused in itself. A raw focus is your own declaration, and the optic generated for it carries the suppression, as it does for a record component. What is refused is generated code that converts a raw value to a type with arguments, or calls a method through a raw type that erases one of its parameters. javac flags both as unchecked in the generated file, which you cannot edit, and the `@SuppressWarnings` on your spec does not reach it. Under `-Xlint:unchecked` that is a warning, which `-Werror` turns into a failed build; without it, javac still notes that the file uses unchecked operations. A setter that erasure leaves as it is, such as `name(String)`, is called through a raw builder cleanly, so a chain through it that builds a type without arguments is accepted. Nor is a raw value refused where the type it reaches is reifiable, such as `List<?>` or `Class<?>`, which javac converts to with no warning. A `List<T>` read on a `Builder<?>` is not one of those: javac reads it as a list of the type the wildcard stands for, which a raw value reaches only unchecked. An accessor that declares a type variable of its own, such as `<T extends List> T items()`, is accepted too, since javac infers its type at the call.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "reads the raw type 'List', which reaches the lens's focus 'List<String>' only through an unchecked conversion" -->
```java
final class Shelf {

    private final List items;

    Shelf(List items) {
        this.items = items;
    }

    public List getItems() {
        return items;
    }

    public Shelf withItems(List items) {
        return new Shelf(items);
    }
}

@ImportOptics
interface ShelfOpticsSpec extends OpticsSpec<Shelf> {

    @Wither(value = "withItems", getter = "getItems")
    Lens<Shelf, List<String>> items();
}
```
~~~

### "'...' has no method '...' for the generated lens to set through"

The `setter` a `@ViaBuilder` or `@ViaCopyAndSet` names is not a method of the type it is called on: the builder for the first, the source type for the second.

Where the name exists, the call is held to what a wither's is, under the strategy's own tag: [`takes the lens's focus type`](#wither-no-method--of--takes-the-lenss-focus-type-) where no overload takes the focus, [`cannot choose between`](#wither-the-generated-call-to--cannot-choose-between--and-) where more than one does, and `is static, so the generated lens cannot set through it` where the one it binds is `static`, since a static method never reads the value it is called on. Where the focus is a wildcard and no method of the name takes one argument, the message says `takes one argument` instead. On a spec over `Bag<?>`, a `toBuilder()` declared to hand back a `Builder<List<T>>` hands back a builder of lists of the one type the wildcard stands for. No focus the spec can write is such a list. The message then says `A parameter that a wildcard of 'Bag<?>' stands in takes no value at all`, and lists the builder's methods as `toBuilder()` declares them. The fix is the wither's: declare the spec over the type the wildcard stands for. Where the wildcard is the builder's own, as in a `toBuilder()` that hands back a `Builder<?>`, point `toBuilder` at a method that hands back the builder with its type arguments.

**Fix.** Set the strategy's `setter` to a method that type declares, and one that takes the value the getter reads. The message offers one where the name is a near miss, or names the same value in the strategy's convention. Under `@ViaBuilder`, it offers a fluent builder's `name`, a protobuf builder's `setName` or a `withName` step, as the builder declares them. For a protobuf repeated or map field it offers the `addAllName` or `putAllName` that sets the field whole. Since the setter defaults to the lens method's name, a lens named `getName` is offered `name`. Under `@ViaCopyAndSet`, it offers only a `set` method, near miss or not. A bare or `with` method may hand back a copy, and `@Wither` is the strategy for one of those.

~~~admonish note title="Why" collapsible=true
`@ViaBuilder` sets through `source.toBuilder().setter(newValue).build()` and `@ViaCopyAndSet` through a copy, so the setter is looked for where the call is made rather than on the source type in both cases. A builder's setter that returns `void` ends the chain, which the [chain entry](#viabuilder-the-chain-the-lens-rebuilds-through) covers.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "has no method 'setHostname' for the generated lens to set through" -->
```java
final class Server {

    private String host;

    Server() {}

    Server(Server other) {
        this.host = other.host;
    }

    public String host() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}

@ImportOptics
interface ServerOpticsSpec extends OpticsSpec<Server> {

    @ViaCopyAndSet(setter = "setHostname")
    Lens<Server, String> host();
}
```
~~~

### @ViaBuilder: the chain the lens rebuilds through

`@ViaBuilder` rebuilds with `source.toBuilder().setter(newValue).build()`, and each step is called on what the step before it hands back. Two messages report a chain that does not lead back to the source type: `'toBuilder()' hands back 'int', which is not a builder to set through`, and `'build()' returns 'String', not the source type 'Waybill'`. A setter that returns `void` reads `hands back 'void', which is not a builder to build from`. A subclass whose `toBuilder()` comes from its parent reads `'Dog' inherits 'toBuilder()' from 'Animal'`, whether the parent's builder lacks the setter or builds an `Animal`.

**Fix.** Name the methods the type's own builder declares. A Lombok `@SuperBuilder` subclass needs `toBuilder = true` of its own, since the `toBuilder()` it inherits builds its parent. Where the type has no such chain, rebuild it with `@Wither`, `@ViaConstructor` or `@ViaCopyAndSet`.

~~~admonish note title="Why" collapsible=true
A builder chain is three calls, and only the first is made on the source type. Reading each step off the one before it is what catches a `build()` that hands back the builder, or a setter that returns nothing, before the generated file does. Each step is read as javac reads the call. So for the `PetBuilder<?, ?>` that Lombok's `@SuperBuilder(toBuilder = true)` hands back for a `Pet`, the setter returns a `PetBuilder`, and `build()` a `Pet`. A step whose type is bounded by more than one type, such as `B extends PetBuilder<B> & Serializable`, is read on the bound that declares the next call. Where unrelated bounds both declare it, which one the call binds is left to javac. Where none declares it, the message names them all, `hands back a type bounded by 'Tag.Base' and 'Tag.Texts', which have no method 'txet'`, and offers a near miss from any.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "not the source type 'Waybill'" -->
```java
final class Waybill {

    private final String id;

    Waybill(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Builder toBuilder() {
        return new Builder();
    }

    static final class Builder {

        private String id;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public String build() {
            return id;
        }
    }
}

@ImportOptics
interface WaybillOpticsSpec extends OpticsSpec<Waybill> {

    @ViaBuilder
    Lens<Waybill, String> id();
}
```
~~~

### @ViaConstructor: the order the lens rebuilds through {#viaconstructor-order}

`@ViaConstructor` rebuilds with `new S(source.a(), newValue, source.c())`: the accessor each name in the order reads, and the new value where the lens method's own name stands. The order is `parameterOrder`, or the one the constructor's parameter names give when you leave it out. These messages report an order the generated call cannot rebuild through:

| The message says | What it means |
|------------------|---------------|
| `'parameterOrder' names no argument for the lens's own 'id'` | The order never names the lens method, so the rebuild would set nothing |
| `'parameterOrder' passes 'source.y()' where 'Point(int x, int y)' takes 'x'` | A value goes where a parameter named after another accessor stands |
| `No constructor of 'Sum' takes a parameter named after the lens's own 'total()'` | Every parameter is named after another accessor, so no place takes the lens's value; `'Heat(double kelvin)' takes no parameter` when a written order binds such a constructor |
| `No constructor of 'Label' takes (String)` | No constructor takes the arguments: too few, too many, or a type out of place |
| `cannot choose between 'Duo(int a, Integer b)' and 'Duo(Integer a, int b)'` | More than one constructor takes them, none more closely than the rest |
| `No 'parameterOrder' is written, and no constructor of` | With no order written, no constructor's names give one; `more than one constructor` when two give different orders |
| `binds '...', not a constructor it was read from` | The order read from one constructor's names binds another constructor |
| `leaves out what '...' takes` | The order read from the names is a shorter constructor's, beside a longer one; for a record, beside its canonical constructor |

**Fix.** Where the constructor's parameter names give an order the call would rebuild through, the message ends on it, ready to paste: `@ViaConstructor(parameterOrder = {"x", "y"})`. Where the lens's focus is what no constructor takes, it names the type the getter reads, to declare as the focus instead. Where the names give no order, or the order read from them would rebuild through the wrong constructor, write the order of the one the lens should rebuild through. Where no constructor takes the lens's value, or javac cannot choose one, rebuild the source type with `@Wither`, `@ViaBuilder` or `@ViaCopyAndSet`. A lens named after an alias, `price()` for a parameter `cents`, can take the accessor's own name instead. A parameter can be named after an accessor that reads something else, as `Money(long amount, ...)` beside an `amount()` that reads pounds rather than pence. The processor still holds the order to that name, and the lens needs another strategy.

~~~admonish note title="Why" collapsible=true
A constructor call checks only types. `Point(int x, int y)` takes `new Point(source.y(), newValue)` as readily as the right order, and the lens then writes each value into the other's field. Setting `x` to 9 on a `Point(1, 2)` gives a `Point(2, 9)`, so a get after the set no longer reads 9. Only the parameter names tell the two apart. So a parameter named after an accessor that reads a value it takes, `x` after `x()`, `getX()` or `isX()`, has to be passed that accessor's value. A parameter named after nothing the type reads leaves its place to the order. A variable-arity constructor is held the same way: its fixed parameters by their names, and every value passed into its array by the array parameter's.

A constructor compiled from source always carries its names. One read from a class file carries them when it was compiled with `-parameters` or `-g`, and Gradle and Maven pass `-g` by default. Without either, javac reads the names as `arg0` and `arg1`, which name no accessor, so the order you write is held to the types alone, and one left out cannot be read. A record's canonical constructor is read by its components' names, which a class file always keeps.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "passes 'source.y()' where 'Point(int x, int y)' takes 'x'" -->
```java
final class Point {

    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }
}

@ImportOptics
interface PointOpticsSpec extends OpticsSpec<Point> {

    @ViaConstructor(parameterOrder = {"y", "x"})
    Lens<Point, Integer> x();
}
```

An order one argument short:

<!-- verify:rejects "No constructor of 'Label' takes (String)" -->
```java
final class Label {

    private final String id;
    private final String tag;

    Label(String id, String tag) {
        this.id = id;
        this.tag = tag;
    }

    public String id() {
        return id;
    }

    public String tag() {
        return tag;
    }
}

@ImportOptics
interface LabelOpticsSpec extends OpticsSpec<Label> {

    @ViaConstructor(parameterOrder = {"id"})
    Lens<Label, String> id();
}
```

Two constructors that each take two `Integer`s only by unboxing one of them:

<!-- verify:rejects "cannot choose between 'Duo(int a, Integer b)' and 'Duo(Integer a, int b)'" -->
```java
final class Duo {

    private final Integer a;
    private final Integer b;

    Duo(int a, Integer b) {
        this.a = a;
        this.b = b;
    }

    Duo(Integer a, int b) {
        this.a = a;
        this.b = b;
    }

    public Integer a() {
        return a;
    }

    public Integer b() {
        return b;
    }
}

@ImportOptics
interface DuoOpticsSpec extends OpticsSpec<Duo> {

    @ViaConstructor(parameterOrder = {"a", "b"})
    Lens<Duo, Integer> a();
}
```

A swap among a variable-arity constructor's fixed parameters, which says `passes 'source.note()' where 'Sticker(String label, String note, String... parts)' takes 'label'`:

<!-- verify:rejects "passes 'source.note()' where 'Sticker(String label, String note, String... parts)' takes 'label'" -->
```java
final class Sticker {

    private final String label;
    private final String note;
    private final String[] parts;

    Sticker(String label, String note, String... parts) {
        this.label = label;
        this.note = note;
        this.parts = parts.clone();
    }

    public String label() {
        return label;
    }

    public String note() {
        return note;
    }

    public String[] parts() {
        return parts.clone();
    }
}

@ImportOptics
interface StickerOpticsSpec extends OpticsSpec<Sticker> {

    @ViaConstructor(parameterOrder = {"note", "label"})
    Lens<Sticker, String> label();
}
```

No order written, for a lens no constructor takes a parameter for:

<!-- verify:rejects "No constructor of 'Sum' takes a parameter named after the lens's own 'total()'" -->
```java
final class Sum {

    private final int x;
    private final int y;

    Sum(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int total() {
        return x + y;
    }
}

@ImportOptics
interface SumOpticsSpec extends OpticsSpec<Sum> {

    @ViaConstructor
    Lens<Sum, Integer> total();
}
```
~~~

### "@ViaConstructor: 'parameterOrder' ... passes nothing for '...', which '...' takes" (a warning) {#viaconstructor-shorter-constructor}

A written order rebuilds through the constructor its call binds, even one that takes fewer values than the source holds. Beside `Fee(long amount, String currency)`, `{"amount"}` binds `Fee(long amount)`, and every set then hands back a `Fee` in that constructor's default currency. The warning names every value a longer constructor takes that the order passes nothing for, from each longer constructor; for a record, every component.

**Fix.** Pass it: where a constructor's names give an order that passes it, the warning offers that order, ready to paste, `@ViaConstructor(parameterOrder = {"amount", "currency"})`. A shorter constructor can work the value out on purpose: a `Rect(int w, int h)` works out the `area` a `Rect(int w, int h, int area)` record holds. Then annotate the lens method `@SuppressWarnings("shorter-constructor")`, or the spec interface to cover each lens it declares. Where no one constructor takes all the values, as `Knot(int x, int y)` and `Knot(int x, long z)` take one each, no order passes them all, and the warning says so rather than offer one.

~~~admonish note title="Why" collapsible=true
After every set, a value the order leaves out reads what the shorter constructor gives it, not what the source held. Setting `amount` to 9 on a `Fee(100, "EUR")` gives a `Fee(9, "GBP")`, so setting a value the lens has just read changes the source, which a lens may not do. A variable-arity constructor passed nothing for its array is shorter in the same way: `{"label"}` for `Sticker(String label, String... parts)` leaves `parts` empty.

Only the parameter names say which values a longer constructor takes, so a parameter named after nothing the type reads never counts. Two constructors of one length, such as `Temperature(double celsius)` and `Temperature(float fahrenheit)`, are two views of one value rather than a shorter and a longer one. A longer view still warns, as `Amount(long pounds, int pence)` beside `Amount(long cents)` does, and the warning says that no order can pass its values with the lens's own. It is a warning rather than an error because a constructor that works a value out keeps the lens lawful, and only you know which of yours do.
~~~

For this declaration, the warning reads `'parameterOrder' {"amount"} passes nothing for 'currency()', which 'Fee(long amount, String currency)' takes`.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:reports "passes nothing for 'currency()', which 'Fee(long amount, String currency)' takes" -->
```java
final class Fee {

    private final long amount;
    private final String currency;

    Fee(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    Fee(long amount) {
        this(amount, "GBP");
    }

    public long amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }
}

@ImportOptics
interface FeeOpticsSpec extends OpticsSpec<Fee> {

    @ViaConstructor(parameterOrder = {"amount"})
    Lens<Fee, Long> amount();
}
```
~~~

### "@ImportOptics: '...' pairs more than one wither with the field '...'" (a note)

A class imported by class literal spells one field's accessor more than one way, and has a wither for each: `int n()` with `withN(int)`, and `String getN()` with `withN(String)`. Both pair with the field `n`, and a class can carry only one lens of that name. A class with both `withEMail` and the JavaBeans `witheMail` draws the note too, since both set the field `eMail`.

**Fix.** Nothing, where the lens you wanted is the one generated: the note says which. Where you wanted the other, import the type through a [spec interface](optics_spec_interfaces.md) and name the pair yourself with `@Wither(value = "withN", getter = "getN")`, importing it there rather than by class literal, or the note stays on every build.

~~~admonish note title="Why" collapsible=true
The pairing rule tries the accessor spellings in order, `n()`, then `getN()`, then `isN()`, and the first that matches is the one generated. Reading them in that order rather than in the order the class happens to declare its members means the same class always yields the same lens. Where two withers pair with one getter, the processor prefers the capitalised spelling, `withEMail` ahead of `witheMail`, for the same reason. A note rather than a warning, because the class is someone else's to spell, and a note does not fail a `-Werror` build.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:reports "pairs more than one wither with the field" -->
```java
public final class Counter {

    private final int n;

    public Counter(int n) {
        this.n = n;
    }

    public int n() {
        return n;
    }

    public String getN() {
        return String.valueOf(n);
    }

    public Counter withN(int n) {
        return new Counter(n);
    }

    public Counter withN(String n) {
        return new Counter(Integer.parseInt(n));
    }
}

@ImportOptics({Counter.class})
class CounterImports {}
```
~~~

### "@ImportOptics: '...' focuses '...', which is not a '...'"

A prism runs both ways, and the generated one builds back with identity: it returns the value it narrowed.

**Fix.** Focus the variant that carries the value, `TextNode` rather than `String`, and read the payload with a further optic. Where the value type is the point, write that prism by hand with `Prism.of` and a build side that constructs the source, such as `TextNode::valueOf`. Where what an `@InstanceOf` test earns is no source type, no focus could take it and still be one. The message then offers another class, or a spec of its own over the type the class declares.

~~~admonish note title="Why" collapsible=true
That is only a source when the focus is one, so a focus that is a *value* rather than a variant has no build side the processor could write: `Prism<JsonNode, String>` would need to rebuild a `JsonNode` from a bare `String`, and nothing in the declaration says how. The requirement belongs to the prism rather than to either hint, so `@InstanceOf` and `@MatchWhen` are both held to it. That includes an `@InstanceOf` whose narrowing is sound but reaches the focus through a supertype the source does not share, `Prism<Base, Marker>` for a `Sub implements Base, Marker`.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "focuses 'String', which is not a 'Payment'" -->
```java
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

@ImportOptics
interface PaymentOpticsSpec extends OpticsSpec<Payment> {

    @MatchWhen(predicate = "isCard", getter = "pan")
    Prism<Payment, String> pan();
}
```
~~~

### "cannot find symbol", inside the generated `XPrisms.java`, after using `@MatchWhen`

The processor does **not** validate the strings in `@MatchWhen(predicate = "isFoo", getter = "asFoo")`. It splices them into the generated source verbatim, so a typo surfaces as an ordinary javac error inside generated code rather than as a processor message.

**Fix.** Check the names against the source type's API. Both methods must take no arguments; the predicate returns `boolean` and the getter returns the prism's target type.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "method isCrad()" -->
```java
sealed interface Payment permits Card, Bank {

    default boolean isCard() {
        return this instanceof Card;
    }

    default Card asCard() {
        return (Card) this;
    }
}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

@ImportOptics
interface PaymentOpticsSpec extends OpticsSpec<Payment> {

    @MatchWhen(predicate = "isCrad", getter = "asCard")
    Prism<Payment, Card> card();
}
```
~~~

### "Prism method 'x' requires a prism hint annotation: @InstanceOf or @MatchWhen"

A spec-interface method returning `Prism<S, A>` with neither hint.

**Fix.** Add `@InstanceOf` for a real subtype, or `@MatchWhen` for a check-and-extract API. The same rule applies to traversals: "Traversal method 'x' requires a traversal hint annotation: @TraverseWith or @ThroughField".

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "requires a prism hint annotation" -->
```java
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

@ImportOptics
interface PaymentOpticsSpec extends OpticsSpec<Payment> {

    Prism<Payment, Card> card();
}
```
~~~

---

## `@GeneratePathBridge` and `@PathVia`

Every message on this page is quoted as the processor emits it, with `'x'` standing in for the name it prints.

The bridge is a file you never wrote and cannot edit, so the errors below refuse a shape at your own declaration rather than emitting source that would fail, or warn, in the build that consumes it. The last entry is a warning rather than an error: the bridge it describes is written, it just has nothing in it.

### "@PathVia: the return type of 'x' is 'Y', which no Path wraps"

The method returns a type the bridge has no Path for. The bridged set is `Optional`, `Maybe`, `Either`, `Try`, `Validated` and `IO`; `CompletableFuture` is the type most often met outside it.

**Fix.** Return one of the six, or drop `@PathVia` and wrap the call by hand.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "which no Path wraps" -->
```java
@GeneratePathBridge
interface Orders {

    @PathVia
    CompletableFuture<String> find(String id);
}
```
~~~

### "@PathVia: the signature of 'x' names the raw type 'Y'"

A generic type is written without its arguments somewhere the bridge copies verbatim: `Optional` as the return type, `Optional<List>` as its argument, `List` as a parameter. Each becomes a `[rawtypes]` warning in the generated file, and the `@SuppressWarnings` on your own declaration does not cover a file it does not appear in.

**Fix.** Name the type arguments: `Optional<Item>` rather than `Optional`. A raw bound on the interface's own type parameter is not refused: the bridge repeats it in its class declaration and carries `@SuppressWarnings("rawtypes")` there.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "names the raw type" -->
```java
@GeneratePathBridge
interface Orders {

    @PathVia
    Optional find(String id);
}
```
~~~

### "@PathVia: the error type of the 'Validated' returned by 'x' is the wildcard '?'"

A `Validated` bridge names its error type twice: in the `ValidationPath` it returns, and in the `Semigroup` it asks the caller for.

**Fix.** Name the error type.

~~~admonish note title="Why" collapsible=true
A wildcard is a *different* captured type at each mention, so no argument satisfies both.

Only the error position is affected. `Validated<String, ? extends Number>` is fine, `Validated<List<? extends CharSequence>, String>` is fine because the wildcard is nested and denotes one type at both mentions, and so are wildcards in `Optional`, `Maybe`, `Either` and `Try` returns.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "is the wildcard" -->
```java
@GeneratePathBridge
interface Orders {

    @PathVia
    Validated<?, String> find(String id);
}
```
~~~

### "@PathVia: the type parameter 'T' on 'x' has the same name as 'Y's"

The bridge declares the interface's type parameters and the method's side by side, which the delegate never does; where the names collide, the method's hides the interface's.

**Fix.** Rename the method's type parameter.

~~~admonish note title="Why" collapsible=true
An inherited `<T extends U>` on a `Derived<T>` would be written `<T extends T>`, and a parameter typed by the interface's `T` would silently become the method's.

Only a collision the signature actually depends on is refused. `<T> Optional<T> get(T t)` on a `Derived<T>` names nothing it hides, and is generated unchanged.
~~~

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "has the same name as 'TextNarrower's" -->
```java
interface Narrower<U> {

    @PathVia
    <T extends U> Optional<T> narrow(T candidate);
}

@GeneratePathBridge
interface TextNarrower<T> extends Narrower<T> {}
```
~~~

### "@PathVia: the bridge cannot call 'x'"

The method is `static` or `private`. The bridge reaches its delegate through an interface reference, which gets at abstract and `default` members and nothing else.

**Fix.** Make it an abstract or `default` instance method, or drop `@PathVia` from it.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "the bridge cannot call" -->
```java
@GeneratePathBridge
interface Orders {

    @PathVia
    static Optional<String> find(String id) {
        return Optional.empty();
    }
}
```
~~~

### "@PathVia: the bridge signature for 'x' is already taken"

Two `@PathVia` methods land on the same generated name and parameter types, usually through `@PathVia(name = ...)`. One class cannot declare both.

**Fix.** Give one of them a distinct name, or drop `@PathVia` from it.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "is already taken" -->
```java
@GeneratePathBridge
interface Orders {

    @PathVia
    Optional<String> find(String id);

    @PathVia(name = "find")
    Optional<String> lookup(String id);
}
```
~~~

### "@PathVia: @PathVia(name = "...") is not a method name"

The `name` attribute is not a Java identifier, or it is a keyword. The bridge declares a method called exactly that.

**Fix.** Give a plain identifier, or drop the attribute to keep the delegate's own name.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "is not a method name" -->
```java
@GeneratePathBridge
interface Orders {

    @PathVia(name = "find-order")
    Optional<String> find(String id);
}
```
~~~

### "@GeneratePathBridge: on 'X', the signature names 'Y', which cannot be reached from 'p'"

`targetPackage` puts the bridge in package `p`, and something the bridge writes down, a parameter type, a return type, a bound or the delegate itself, is not visible there. The same message names *the bound on 'T'* when the culprit is a type parameter's bound.

**Fix.** Make public the class the message names, which may be one enclosing the type. Where the type sits beside the interface, removing `targetPackage` also works, since the bridge is then written there, and the message offers it where it does.

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- verify:rejects "cannot be reached from 'com.example.paths'" -->
```java
class Secret {}

@GeneratePathBridge(targetPackage = "com.example.paths")
interface Vault {

    @PathVia
    Optional<Secret> find(String id);
}
```
~~~

### "@GeneratePathBridge: no @PathVia method was found among 'X's members" (a warning)

No `@PathVia` method survives among the interface's members, so the bridge is written with a constructor and nothing else.

**Fix.** Put `@PathVia` on the methods to bridge, or drop `@GeneratePathBridge`.

~~~admonish note title="Why" collapsible=true
Usually that means none was ever written; it can also mean one was hidden, which the note below covers.

Inherited methods do count: a bridge for `StringStore extends Store<String>` picks up `Store`'s, read under `String`. But `@PathVia` is *not* inherited by an override, so a method that overrides an annotated one hides it unless it is annotated too, and that is the usual cause of this message on an interface whose parent is annotated.

A processor warning cannot be suppressed, so a build running `-Werror` treats this as an error.
~~~

~~~admonish example title="A declaration that draws it" collapsible=true
<!-- verify:reports "no @PathVia method was found among" -->
```java
@GeneratePathBridge
interface Orders {

    Optional<String> find(String id);
}
```
~~~

---

## Focus DSL chains

### `traverseOver` and the higher-kinded witness type

`traverseOver` takes its witness type from the `Traverse` argument, but its element type only from where the result goes. In a chain such as `.traverseOver(...).getAll(user)`, the element type falls back to `Object`, and javac reports `incompatible types: List<Object> cannot be converted to List<Role>`.

**Fix.** Widen through `each(EachInstances.fromTraverse(...))`, which takes the element type from the `Kind` the path focuses on, so it holds in a chain too:

<!-- verify -->
```java
TraversalPath<Operator, Role> allRoles =
    rolesPath.each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
```

~~~admonish note title="Why" collapsible=true
`traverseOver`'s element type appears only in its return type, so Java can infer it only from a target type, such as the variable the result is assigned to. The phantom error type in [Effect §1](../effect/compiler_errors.md#1-the-phantom-error-type-e-on-pathright) is inferred the same way. The receiver of a further method call is not a target, so the element type takes its bound, `Object`. `fromTraverse` returns an `Each` typed from the `Kind` it reads, as every `Each` is, so the path's own focus fixes the element. `traverseOver` is deprecated for removal in 0.6.0. [Focus DSL Reference](focus_reference.md#object-turns-up-after-traverseover) shows the failing chain beside the fix.
~~~

### "Incompatible types when chaining .each().via()"

Usually one `.each()` too many.

**Fix.** Drop the extra `.each()`, and break long chains into intermediate variables so each carries a concrete type:

<!-- verify -->
```java
TraversalPath<OrgChart, OrgUnit>   units    = OrgChartFocus.units();
TraversalPath<OrgChart, OrgMember> members  = units.via(OrgUnitFocus.members());
TraversalPath<OrgChart, Integer>   salaries = members.via(OrgMemberFocus.salary());
```

~~~admonish note title="Why" collapsible=true
A generated accessor for a collection component is *already* element-level, so `OrgChartFocus.units()` is a `TraversalPath<OrgChart, OrgUnit>` and adding `.each()` steps into an `OrgUnit` as though it were a list. Long chains can also overflow Java's inference budget.
~~~

~~~admonish warning title="The extra `.each()` compiles"
The untyped `each()` is `<E> TraversalPath<S, E>` and infers `E` from the assignment target, so a surplus hop type-checks and then fails at runtime when the list traversal is applied to something that is not a list. It is deprecated for removal in 0.6.0. Its typed form, `each(EachInstances.listEach())`, does not compile on a focus that is not a `List`.
~~~

### "Cannot infer type argument(s)" on an intermediate `.each()`

Only the *final* untyped `each()` in a chain can infer its element type from the target type. An intermediate one has nothing to infer from.

**Fix.** Use the typed form at each hop. It takes the element type from the `List` the path focuses on, so no hop needs it spelled out:

<!-- verify -->
```java
TraversalPath<OrgChart, Integer> allSalaries =
    FocusPath.of(OrgChartLenses.units())
        .each(EachInstances.listEach())
        .via(OrgUnitLenses.members())
        .each(EachInstances.listEach())
        .via(OrgMemberLenses.salary());
```

### "No suitable method found for each(...)" on a typed step {#no-suitable-method-found-for-each}

The path does not focus on the container the step names: `EachInstances.listEach()` takes a path that focuses on a `List`, and `FocusPaths.optionalSome()` one that focuses on an `Optional`.

**Fix.** Give the step the instance for what the path does focus on. A generated collection path already focuses on each element, so it needs no step of its own, and a `Set`, a `Map` or another container takes its own `Each`. A path over a `List<? extends T>` takes an `Iso` to `List<T>` first, and one over a `Kind<F, ? extends T>` an `Iso` to `Kind<F, T>`, as [0.6.0 deprecation migration](../tooling/openrewrite.md#060-deprecation-migration) shows.

<!-- verify:rejects "no suitable method found for each" -->
```java
// Rejected: the generated path already focuses on each OrgUnit, not on a List
TraversalPath<OrgChart, OrgUnit> units = OrgChartFocus.units().each(EachInstances.listEach());
```

~~~admonish note title="Why" collapsible=true
`listEach()` is an `Each<List<A>, A>`, and `each` takes an `Each` whose first type is the path's focus, so javac looks for an `A` that makes `List<A>` an `OrgUnit` and finds none. The deprecated no-argument `each()` compiled here, because it does not read its element type from the focus, and failed with `ClassCastException` when it ran. `some(FocusPaths.optionalSome())` on a focus that is not an `Optional`, and `via(FocusPaths.listAt(0))` on one that is not a `List`, give the same message, naming `some` or `via`.
~~~

### "each() in org.higherkindedj.optics.focus.FocusPath has been deprecated and marked for removal" (a warning) {#untyped-shortcut-marked-for-removal}

A Focus path's no-argument `each()`, `some()`, `head()`, `last()`, `tail()`, `init()`, `cons()`, `snoc()`, `headTail()` or `initLast()` is deprecated for removal in 0.6.0, and so are its `at(index)`, `atKey(key)` and `traverseOver(traverse)`. The warning names the method and the path type, a navigator included.

**Fix.** Write its typed form: the same verb, given an instance that names the container, such as `each(EachInstances.listEach())` or `some(FocusPaths.optionalSome())`. [Removals in 0.6.0](../release-history/upgrading.md#removals-in-060) gives each one. The `MigrateDeprecationsTo0_6_0` recipe rewrites the calls it can and marks the rest, as [0.6.0 deprecation migration](../tooling/openrewrite.md#060-deprecation-migration) says.

<!-- verify:reports "each() in org.higherkindedj.optics.focus.FocusPath has been deprecated and marked for removal" -->
```java
// Warned: each() takes its element type from the assignment, not from the path
TraversalPath<OrgChart, OrgUnit> units = FocusPath.of(OrgChartLenses.units()).each();
```

~~~admonish note title="Why" collapsible=true
The shortcut does not read its element type from the path's focus. So it compiles on any focus, and fails with `ClassCastException` when it runs on one that is not its container. The typed form reads the element from the focus, so on the wrong focus it does not compile, with [No suitable method found for each(...)](#no-suitable-method-found-for-each). The warning fails a build only under `-Werror`.
~~~

### "Method reference ::new doesn't work with single-field records as BiFunction"

A single-component record has no two-argument constructor, and `Lens.of`'s setter is a `BiFunction<S, A, S>` taking `(source, newValue)`. It is an arity mismatch, not an inference wobble.

**Fix.** Use an explicit lambda:

<!-- verify -->
```java
Lens<ShelfLabel, Barcode> lens = Lens.of(ShelfLabel::barcode, (label, code) -> new ShelfLabel(code));
```

### "Sealed or non-sealed local classes are not allowed"

Defining a sealed interface inside a method body. Java does not permit this regardless of HKJ.

**Fix.** Hoist the sealed interface to class or top level.

---

## Free Monad DSL programs

### "Cannot resolve method 'flatMap(Function<...>)'"

The `Free<F, A>` value's witness type does not match what the surrounding interpreter expects, or you are mixing `Free<OpticOpKind.Witness, ...>` with another `Free` instance.

**Fix.** Confirm that every step in the program uses the same `OpticPrograms` factory methods, and that interpreter calls are paired with the matching witness.
### "Type mismatch: Free<F, A> cannot be converted to A"

Forgetting to call an interpreter. A `Free` program is data; you must run it to get a result.

**Fix.** Pass the program to an interpreter:

<!-- verify -->
```java
Saver result = OpticInterpreters.direct().run(program);
```

---

## When the message does not match anything here

1. Is the project rebuilt from clean? Many "cannot find symbol" errors clear after `./gradlew clean build`.
2. Is the annotation processor on the classpath? See [Build Plugins](../tooling/gradle_plugin.md) for the canonical setup.
3. Is the IDE indexing the generated sources directory? Refresh the project after a build.
4. If it is none of these, please file an issue at the [Higher-Kinded-J GitHub repository](https://github.com/higher-kinded-j/higher-kinded-j) with the minimal reproducer and the full error.

~~~admonish tip title="See Also"
- [Annotations at a Glance](annotations_at_a_glance.md): which annotation to reach for, and what it generates
- [Optics for External Types](importing_optics.md): the `@ImportOptics` and spec-interface rules these errors enforce
- [Build Plugins](../tooling/gradle_plugin.md): the canonical processor setup
~~~

---

**Previous:** [Focus DSL Reference](focus_reference.md)
