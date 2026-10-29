<!-- description: Why Java needs higher-kinded types to write one method for any container, why a shared interface is not enough, and where Higher-Kinded-J's encoding comes from. -->

# Why Higher-Kinded Types?

_One method for every container, and why Java needs an encoding to write it._

![Two hands turning a Rubik's cube](../images/rubiks1932.jpg)

> **You:** I want one `map` for every container, so I write `<F, A, B> F<B> map(F<A> box, Function<A, B> f)`.
>
> **javac:** `unexpected type`. `F` is a type parameter, and a type parameter takes no type arguments of its own.
>
> **You:** Then my containers share an interface, `Mappable<A>`, and I write `map` once over that.
>
> **javac:** Hand that method a `Box<Integer>` and it gives back a `Mappable<String>`, not a `Box`. And `List` and `Optional` belong to the JDK, so they will never implement your interface.
>
> **You:** So I need a name for the container alone, without what it holds, that you can pass as an ordinary type.
>
> **javac:** A witness class can be that name. Write `Kind<ListKind.Witness, Integer>`, and I carry `ListKind.Witness` through your method like any other type.
>
> **You:** But a `Kind` has no `map`.
>
> **javac:** Then take a `Functor<F>` that maps inside `F`. I will check that its `F` and the box's `F` are the same.
>
> **You:** And give back a `Kind<F, B>`: the same container, holding the new value.

javac's three refusals are real, and [What javac refuses](#what-javac-refuses) shows each one. The rest of the page states every step plainly.

~~~admonish info title="What You'll Learn"
- Tell a type, a type constructor and a higher-kinded type apart
- Say why plain Java needs a method for each container, and why a shared interface does not fix it
- Read the one method that serves every container, written with Higher-Kinded-J
- Name the paper the encoding comes from, and what the encoding costs in Java
~~~

~~~admonish example title="See Example Code"
**The code on this page is [WhyHktBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/why/WhyHktBook.java)**: the page includes it, so the build compiles and runs it. It reuses `totals` from the chapter introduction's [FoundationsIntroBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/intro/FoundationsIntroBook.java). [WhyHktBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/why/WhyHktBookTest.java) holds each claim the page makes.
~~~

---

## Types, type constructors and higher kinds {#types-type-constructors-and-higher-kinds}

`String` is a type: you can declare a variable of it. `List` is a *type constructor*, which waits for a type argument and then gives a type, such as `List<String>`. (Java still accepts a raw `List`, for code older than generics.) A type constructor does for types what a function does for values.

A *higher-kinded* type goes one level further: its parameter stands for a type constructor rather than a type. The same step separates a higher-order function from an ordinary one:

| Level | Takes | Example |
|---|---|---|
| Function | values | `int square(int n)` |
| Higher-order function | a function | `<A, B> List<B> map(List<A> list, Function<A, B> f)` |
| Type constructor | types | `List<_>`, waiting for its element type |
| Higher-kinded type | a type constructor | `Functor<F<_>>`, where `F` stands for `List`, `Maybe` or any other container |

So `List` is not higher-kinded, because it takes a type. `Functor` is, because its `F` stands for a container rather than for what the container holds. Java has type constructors, and methods generic over types, but cannot declare `F<_>`. Higher-Kinded-J's `Functor<F>` simulates it, with `F` a witness type that stands in for the container.

Type theory calls the type of a type its *kind*. `String` has kind `*`, `List` has kind `* -> *`, and `Functor` asks for an `F` of kind `* -> *`. Higher-Kinded-J's `Kind<F, A>` borrows the word.

## One method for every container {#one-method-for-every-container}

![Cardboard boxes, plastic crates and metal flight cases, stacked side by side](../images/containers.png)

In plain Java, one line of logic needs a method for each container, because Java has no type for "any container", and each container spells `map` its own way:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/why/WhyHktBook.java:without}}
```

| Container | Plain Java | With Higher-Kinded-J |
|---|---|---|
| `List` | `totalsOfList`, with `stream().map(…).toList()` | `totals(functor(list()), …)` |
| `Optional` | `totalOfOptional`, with `map` | `totals(functor(optional()), …)` |
| `CompletableFuture` | `totalOfFuture`, with `thenApply` | `totals(functor(completableFuture()), …)` |

Three methods are no great burden for one line. The cost grows with what you build on them, since anything that combines or checks totals is written again for each container. With higher kinds you write the method once, for any `F`, and pass the `Functor` that knows how to map inside it. It is the same `totals` that the [chapter introduction](foundations_intro.md) runs on `Maybe`, `Either` and `List`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/intro/FoundationsIntroBook.java:generic}}
```

Here it runs on the three containers the plain methods cover, and gives the same totals. `widen` takes each value into the encoding, `narrow` brings the result back, and `functor(list())` asks for the `Functor` that maps inside a `List`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/why/WhyHktBook.java:with}}
```

[How Higher-Kinded-J encodes it](#how-higher-kinded-j-encodes-it) says where each piece comes from, and [Type Arity](type-arity.md) explains the bound on `F`.

## What javac refuses {#what-javac-refuses}

The dialogue's three refusals, as javac reports them.

**A type parameter cannot take type arguments.** The first attempt names the container as `F` and gives it an element type:

<!-- verify:rejects "type parameter F" -->
```java
<F, A, B> F<B> map(F<A> box, Function<A, B> f);
```

javac answers `unexpected type`, with `found: type parameter F`. [How the Encoding Works](core-concepts.md#why-java-needs-an-encoding) meets the same refusal.

**A shared interface loses the container.** You can write `map` once over an interface, and a `Box` can even override it to return a `Box`. But a method written for any `Mappable` gives back a `Mappable`, and nothing in its type says it was a `Box`:

<!-- verify:rejects "no instance(s) of type variable(s)" -->
```java
interface Mappable<A> {
    <B> Mappable<B> map(Function<A, B> f);
}

record Box<A>(A value) implements Mappable<A> {
    public <B> Box<B> map(Function<A, B> f) {
        return new Box<>(f.apply(value));
    }
}

static <A, B> Mappable<B> mapAny(Mappable<A> box, Function<A, B> f) {
    return box.map(f);
}

Box<String> label = mapAny(new Box<>(42), n -> "No. " + n);
```

javac answers `incompatible types`: there are `no instance(s) of type variable(s)` that make the `Mappable<B>` from `mapAny` conform to `Box<String>`. The JDK's `List`, `Optional` and `CompletableFuture` could not implement `Mappable` anyway, since you cannot change them. The [FAQ](faq.md#why-cant-this-just-be-an-interface-mappablet) takes the same question further.

**The `Functor` must match the container.** Once the container has a name, `totals` takes that name as an ordinary type parameter. javac then checks that the `Functor` and the `Kind` name the same container, so the `Functor` for `Maybe`, given a `List`, is refused:

<!-- verify:rejects "incompatible equality constraints" -->
```java
totals(functor(maybe()), LIST.widen(List.of(order)));
```

javac answers that `totals` `cannot be applied to given types`, because the inference variable `F` has `incompatible equality constraints`: `ListKind.Witness` and `MaybeKind.Witness`.

## How Higher-Kinded-J encodes it {#how-higher-kinded-j-encodes-it}

Higher-Kinded-J follows Jeremy Yallop and Leo White's paper [Lightweight higher-kinded polymorphism](https://www.cl.cam.ac.uk/~jdy22/papers/lightweight-higher-kinded-polymorphism.pdf) (FLOPS 2014). It was written for OCaml, which can abstract over a type constructor only in its module language. The paper *defunctionalises* the type constructor instead. The constructor gets a name that is an ordinary type, and "the constructor applied to `A`" becomes one type that takes the name and `A`:

| In the paper | In Higher-Kinded-J | What it does |
|---|---|---|
| a brand, such as `List.t` | a witness, such as `ListKind.Witness` | Names a type constructor, and is never instantiated |
| `('a, 'f) app` | `Kind<F, A>` | Stands for the constructor that `F` names, applied to `A` |
| `inj` | `widen` | Moves a value into the encoding |
| `prj` | `narrow` | Moves it back out |

Type classes such as `Functor<F>` are then ordinary generic interfaces over `Kind<F, A>`, and each container supplies an instance of them. [How the Encoding Works](core-concepts.md) covers each piece, and [Lifting the Hood](lifting_the_hood.md) traces one call through them.

## What it costs {#what-it-costs}

The encoding asks more of you than a language with higher kinds built in:

- **Every value crosses twice.** `widen` takes it into the encoding, and `narrow` brings it back.
- **Every generic method takes its instance**, such as the `Functor` that `totals` asks for.
- **`narrow` casts, and checks the cast at run time.** Any class can implement `Kind<ListKind.Witness, A>`, so javac cannot prove that such a `Kind` holds a `List`. `narrow` throws `KindUnwrapException` when it does not, where a language with higher kinds has no cast to check. [When `narrow` throws](core-concepts.md#when-narrow-throws) says how that can happen.

Application code rarely needs to pay this. [Effect Paths](../effect/ch_intro.md) such as `EitherPath` give the same operations on concrete types, with no witness to write, and the [chapter introduction](foundations_intro.md) says which level fits your code. Higher kinds earn their place in code written once for any container, such as [`traverse`](../functional/foldable_and_traverse.md), [`For`](../functional/for_comprehension.md), or a method like `totals`.

---

~~~admonish info title="Hands-On Learning"
Practise `Kind<F, A>` from the first exercise in the [Core Types Tutorial Track](../tutorials/coretypes/ch_intro.md), which runs from widening a value to production workflows.
~~~

---

**Previous:** [Higher-Kinded Types](ch_intro.md)
**Next:** [Type Arity](type-arity.md)
