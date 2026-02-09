# Introduction to Higher-Kinded Types

![rubiks1932.jpg](../images/rubiks1932.jpg)

~~~admonish info title="What You'll Learn"
- The analogy between higher-order functions and higher-kinded types
- Why Java's type system limitations necessitate HKT simulation
- How abstractions over "container" types enable more reusable code
- The difference between first-order types, generic types, and higher-kinded types
- Real-world benefits: less boilerplate, more abstraction, better composition
~~~

~~~admonish tip title="Hands-On Learning"
Want to learn by doing? Try our **[Core Types Tutorial Track](../tutorials/coretypes_track.md)** with 7 interactive exercises that take you from `Kind<F, A>` basics to production workflows in ~60 minutes.
~~~

We can think about Higher-Kinded Types (HKT) by making an analogy with Higher-Order Functions (HOF).

_higher-kinded types are to types what higher-order functions are to functions._ 

They both represent a powerful form of abstraction, just at different levels.

### The Meaning of "Regular" and "Higher-Order"

**Functions** model Behaviour

- **First-Order (Regular) Function:** This kind of function operates on simple values. It takes a value(s) like a `int` and returns a value.

```java
// Take a value and return a value
int square(int num) {
    return num * num;
}    
```

- **Higher-Order Function:** This kind of function operates on _other functions_.  It can take functions as arguments and or return a new function as the result.  It abstracts over the **behaviour**.

```java
// Takes a Set of type A and a function fn that maps types of A to B,
//  returns a new Set of type B
<A, B> Set<B> mapper(Set<A> list, Function<A, B> fn) {
    // ... applies fn to each element of the set
}
```
`mapper` is a higher-order function because it takes the function `fn` as an argument.


**Types** model Structure

- **First-Order (Regular) Type:** A simple, concrete type like `int`, or `Set<Double>` represents a specific kind of data.
- **Higher-Kinded Type (HKT):** This is a "type that operates on types." More accurately, it's a generic type constructor that can itself be treated as a type parameter. It abstracts over structure or computational context.

Let us consider `Set<T>`. `Set` itself without the `T`, is a type constructor.  Think of it as a "function" for types: Supply it a type (like `Integer`), and it produces a new concrete type `Set<Integer>`.  

A higher-kinded type allows us to write code that is generic over `Set` itself, or `List`, or `CompletableFuture`.


### Generic code in Practice

**Functions**

_Without Higher-Order Functions:_

To apply different operations to a list, we would need to write separate loops for each one.

```java
List<String> results = new ArrayList<>();
for (int i : numbers) {
    results.add(intToString(i)); // Behaviour is hardcoded
}
```

_With Higher-Order Functions:_

We abstract the behaviour into a function and pass it in. This is much more flexible.

```java

// A map for List
<A, B> List<B> mapList(List<A> list, Function<A, B> f);

// A map for Optional
<A, B> Optional<B> mapOptional(Optional<A> opt, Function<A, B> f);

// A map for CompletableFuture
<A, B> CompletableFuture<B> mapFuture(CompletableFuture<A> future, Function<A, B> f);

```
Notice the repeated pattern: the core logic is the same, but the "container" is different.


_With Higher-Kinded Types:_

With Higher-Kinded-J we can abstract over the container `F` itself. This allows us to write one single, generic map function that works for any container structure or computational context that can be mapped over (i.e., any `Functor`). This is precisely what the `GenericExample.java` demonstrates.

```java
// F is a "type variable" that stands for List, Optional, etc.
// This is a function generic over the container F.
public static <F, A, B> Kind<F, B> map(
    Functor<F> functorInstance, // The implementation for F
    Kind<F, A> kindBox,         // The container with a value
    Function<A, B> f) {         // The behaviour to apply
    return functorInstance.map(f, kindBox);
}

```

Here, `Kind<F, A>` is the higher-kinded type that represents "some container F holding a value of type A."


Both concepts allow you to write more generic and reusable code by parametrising things that are normally fixed. **Higher-order functions parametrise behaviour, while higher-kinded types parametrise the structure that contains the behaviour.**

We will discuss the `GenericExample.java` in detail later, but you can take a peek at the code here

- [GenericExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/GenericExample.java)


## The Core Idea: Abstraction over Containers

In short: a higher-kinded type is a way to be generic over the container type itself.
Think about the different "container" types you use every day in Java: `List<T>`, `Optional<T>`, `Future<T>`, `Set<T>`. All of these are generic containers that hold a value of type `T`.
The problem is that you can't write a single method in Java that accepts any of these containers and performs an action, because `List`, `Optional`, and `Future` don't share a useful common interface. A higher-kinded type solves this by letting you write code that works with `F<T>`, where `F` itself is a variable representing the container type (`List`, `Optional`, etc.).

## Building Up from Java Generics

### Level 1: Concrete Types (like values)
A normal, complete type is like a value. It's a "thing".
```java
String myString;          // A concrete type
List<Integer> myIntList;  // Also a concrete type (a List of Integers)
```
### Level 2: Generic Types (like functions)
A generic type definition like `List<T>` is not a complete type. It's a type constructor. It's like a function at the type level: you give it a type (e.g., `String`), and it produces a concrete type (`List<String>`).
```java
// List<T> is a "type function" that takes one parameter, T.
// We can call it a type of kind: * -> *
// (It takes one concrete type to produce one concrete type)
```
You can't declare a variable of type `List`. You must provide the type parameter `T`.

### Level 3: Higher-Kinded Types (like functions that take other functions)
This is the part Java doesn't support directly. A higher-kinded type is a construct that is generic over the type constructor itself.
Imagine you want to write a single map function that works on any container. You want to write code that says: _"Given any container F holding type A, and a function to turn an A into a B, I will give you back a container F holding type B."_
In imaginary Java syntax, it would look like this:
```java
// THIS IS NOT REAL JAVA SYNTAX
public <F<?>, A, B> F<B> map(F<A> container, Function<A, B> func);
```
Here, `F `is the higher-kinded type parameter. It's a variable that can stand for `List`, `Optional`, `Future`, or any other `* -> *` type constructor.


## A Practical Analogy: The Shipping Company

![containers.png](../images/containers.png)

Think of it like working at a shipping company.
A concrete type `List<String>` is a "Cardboard Box full of Apples".
A generic type `List<T>` is a blueprint for a "Cardboard Box" that can hold anything (`T`).
Now, you want to write a single set of instructions (a function) for your robotic arm called addInsuranceLabel. You want these instructions to work on any kind of container.
Without HKTs (The Java Way): You have to write separate instructions for each container type.
```java
addInsuranceToCardboardBox(CardboardBox<T> box, ...)
addInsuranceToPlasticCrate(PlasticCrate<T> crate, ...)
addInsuranceToMetalCase(MetalCase<T> case, ...)
```

With HKTs (The Abstract Way): You write one generic set of instructions.
```java
addInsuranceToContainer(Container<T> container, ...)
```

A higher-kinded type is the concept of being able to write code that refers to `Container<T>`: an abstraction over the container or "context" that holds the data.


Higher-Kinded-J **simulates HKTs in Java** using a technique inspired by defunctionalisation. It allows you to define and use common functional abstractions like `Functor`, `Applicative`, and `Monad` (including `MonadError`) in a way that works *generically* across different simulated type constructors.

**Why bother?** Higher-Kinded-J unlocks several benefits:

* **Write Abstract Code:** Define functions and logic that operate polymorphically over different computational contexts (e.g., handle optionality, asynchronous operations, error handling, side effects, or collections using the *same* core logic).
* **Leverage Functional Patterns:** Consistently apply powerful patterns like `map`, `flatMap`, `ap`, `sequence`, `traverse`, and monadic error handling (`raiseError`, `handleErrorWith`) across diverse data types.
* **Build Composable Systems:** Create complex workflows and abstractions by composing smaller, generic pieces, as demonstrated in the included [Order Processing Example](order-walkthrough.md).
* **Understand HKT Concepts:** Provides a practical, hands-on way to understand HKTs and type classes even within Java's limitations.
* **Lay the Foundations:** Building on HKTs unlocks the possibilities for advanced abstractions like [Optics](../optics/optics_intro.md), which provide composable ways to access and modify nested data structures.

While Higher-Kinded-J introduces some boilerplate compared to languages with native HKT support, it offers a valuable way to explore these powerful functional programming concepts in Java.

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Concepts](core-concepts.md)

