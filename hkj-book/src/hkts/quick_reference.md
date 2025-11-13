# Quick Reference Guide

This section provides at-a-glance summaries of all type classes in Higher-Kinded-J. Use this as a quick lookup while coding or to compare different type classes.

## Core Type Classes

### Functor
~~~admonish tip title="Functor Quick Reference"
**Core Method:** `map(Function<A,B> f, Kind<F,A> fa) -> Kind<F,B>`

**Purpose:** Transform values inside a context without changing the context structure

**Use When:** 
- You have a simple transformation function `A -> B`
- The context/container should remain unchanged
- No dependency between input and output contexts

**Laws:** 
- Identity: `map(identity) == identity`
- Composition: `map(g ∘ f) == map(g) ∘ map(f)`

**Common Instances:** List, Optional, Maybe, Either, IO, CompletableFuture

**Example:**
```java
// Transform string to length, preserving Optional context
Kind<OptionalKind.Witness, Integer> lengths = 
    optionalFunctor.map(String::length, optionalString);
```

**Think Of It As:** Applying a function "inside the box" without opening it
~~~

### Applicative
~~~admonish tip title="Applicative Quick Reference"
**Core Methods:** 
- `of(A value) -> Kind<F,A>` (lift pure value)
- `ap(Kind<F,Function<A,B>> ff, Kind<F,A> fa) -> Kind<F,B>` (apply wrapped function)

**Purpose:** Combine independent computations within a context

**Use When:**
- You need to combine multiple wrapped values
- Operations are independent (don't depend on each other's results)
- You want to accumulate errors from multiple validations

**Key Insight:** `map2`, `map3`, etc. are built on `ap` for combining 2, 3, or more values

**Laws:** Identity, Composition, Homomorphism, Interchange

**Common Patterns:**
- Form validation (collect all errors)
- Combining configuration values
- Parallel computations

**Example:**
```java
// Combine two independent validations
Kind<ValidatedKind.Witness<List<String>>, User> userLogin = 
    applicative.map2(
        validateUsername(input.username()),
        validatePassword(input.password()),
        User::new
    );
```

**Think Of It As:** Combining multiple "boxes" when contents are independent
~~~

### Monad
~~~admonish tip title="Monad Quick Reference"
**Core Method:** `flatMap(Function<A,Kind<F,B>> f, Kind<F,A> fa) -> Kind<F,B>`

**Purpose:** Sequence dependent computations within a context

**Use When:**
- Each step depends on the result of the previous step
- You need to chain operations that return wrapped values
- You want short-circuiting behaviour on failure

**Key Difference from Applicative:** Operations are sequential and dependent

**Laws:** 
- Left Identity: `flatMap(f, of(a)) == f(a)`
- Right Identity: `flatMap(of, m) == m`  
- Associativity: `flatMap(g, flatMap(f, m)) == flatMap(x -> flatMap(g, f(x)), m)`

**Utility Methods:**
- `as(B value, Kind<F,A> fa)` - replace value, keep effect
- `peek(Consumer<A> action, Kind<F,A> fa)` - side effect without changing value

**Example:**
```java
// Chain database operations where each depends on the previous
Kind<OptionalKind.Witness, Account> account = 
    monad.flatMap(userLogin -> 
        monad.flatMap(profile -> 
            findAccount(profile.accountId()),
            findProfile(userLogin.id())),
        findUser(userId));
```

**Think Of It As:** Chaining operations where each "opens the box" and "puts result in new box"
~~~

### MonadError
~~~admonish tip title="MonadError Quick Reference"
**Core Methods:**
- `raiseError(E error) -> Kind<F,A>` (create error state)
- `handleErrorWith(Kind<F,A> fa, Function<E,Kind<F,A>> handler) -> Kind<F,A>` (recover from error)

**Purpose:** Add explicit error handling to monadic computations

**Use When:**
- You need to handle specific error types
- You want to recover from failures in a workflow
- You need to distinguish between different kinds of failures

**Key Insight:** Error type `E` is fixed for each MonadError instance

**Common Error Types:**
- `Throwable` for CompletableFuture
- `Unit` for Optional/Maybe (absence as error)
- Custom domain error types for Either/Validated

**Recovery Methods:**
- `handleError(fa, Function<E,A> handler)` - recover to pure value
- `recover(fa, A defaultValue)` - provide default value

**Example:**
```java
// Handle division by zero gracefully
Kind<EitherKind.Witness<String>, Double> result = 
    monadError.handleErrorWith(
        divideOperation,
        error -> monadError.of(0.0) // recover with default
    );
```

**Think Of It As:** try-catch for functional programming
~~~

### Selective
~~~admonish tip title="Selective Quick Reference"
**Core Methods:**
- `select(Kind<F,Choice<A,B>> fab, Kind<F,Function<A,B>> ff) -> Kind<F,B>` (conditional function application)
- `whenS(Kind<F,Boolean> cond, Kind<F,Unit> effect) -> Kind<F,Unit>` (conditional effect)
- `ifS(Kind<F,Boolean> cond, Kind<F,A> then, Kind<F,A> else) -> Kind<F,A>` (if-then-else)

**Purpose:** Execute effects conditionally with static structure (all branches known upfront)

**Use When:**
- You need conditional effects but want static analysis
- All possible branches should be visible at construction time
- You want more power than Applicative but less than Monad
- Building feature flags, conditional logging, or validation with alternatives

**Key Insight:** Sits between Applicative and Monad - provides conditional effects without full dynamic choice

**Common Patterns:**
- Feature flag activation
- Debug/production mode switching
- Multi-source configuration fallback
- Conditional validation

**Example:**
```java
// Only log if debug flag is enabled
Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

Kind<IOKind.Witness, Boolean> debugEnabled =
    IO_KIND.widen(IO.delay(() -> config.isDebug()));
Kind<IOKind.Witness, Unit> logEffect =
    IO_KIND.widen(IO.fromRunnable(() -> log.debug("Debug info")));

Kind<IOKind.Witness, Unit> conditionalLog = selective.whenS(debugEnabled, logEffect);
// Log effect only executes if debugEnabled is true
```

**Think Of It As:** If-then-else for functional programming with compile-time visible branches
~~~

## Data Combination Type Classes

### Semigroup
~~~admonish tip title="Semigroup Quick Reference"
**Core Method:** `combine(A a1, A a2) -> A`

**Purpose:** Types that can be combined associatively

**Key Property:** Associativity - `combine(a, combine(b, c)) == combine(combine(a, b), c)`

**Use When:**
- You need to combine/merge two values of the same type
- Order of combination doesn't matter (due to associativity)
- Building blocks for parallel processing

**Common Instances:**
- String concatenation: `"a" + "b" + "c"`
- Integer addition: `1 + 2 + 3`
- List concatenation: `[1,2] + [3,4] + [5,6]`
- Set union: `{1,2} ∪ {2,3} ∪ {3,4}`

**Example:**
```java
// Combine error messages
Semigroup<String> stringConcat = Semigroups.string("; ");
String combined = stringConcat.combine("Error 1", "Error 2");
// Result: "Error 1; Error 2"
```

**Think Of It As:** The `+` operator generalised to any type
~~~

### Monoid
~~~admonish tip title="Monoid Quick Reference"
**Core Methods:**
- `combine(A a1, A a2) -> A` (from Semigroup)
- `empty() -> A` (identity element)

**Purpose:** Semigroups with an identity/neutral element

**Key Properties:**
- Associativity (from Semigroup)
- Identity: `combine(a, empty()) == combine(empty(), a) == a`

**Use When:**
- You need a starting value for reductions/folds
- Implementing fold operations over data structures
- You might be combining zero elements

**Common Instances:**
- String: empty = `""`, combine = concatenation
- Integer addition: empty = `0`, combine = `+`
- Integer multiplication: empty = `1`, combine = `*`
- List: empty = `[]`, combine = concatenation
- Boolean AND: empty = `true`, combine = `&&`

**Example:**
```java
// Sum a list using integer addition monoid
Integer sum = listFoldable.foldMap(
    Monoids.integerAddition(),
    Function.identity(),
    numbersList
);
```

**Think Of It As:** Semigroup + a "starting point" for combinations
~~~

## Structure-Iterating Type Classes

### Foldable
~~~admonish tip title="Foldable Quick Reference"
**Core Method:** `foldMap(Monoid<M> monoid, Function<A,M> f, Kind<F,A> fa) -> M`

**Purpose:** Reduce a data structure to a single summary value

**Use When:**
- You want to aggregate/summarise data in a structure
- You need different types of reductions (sum, concat, any/all, etc.)
- You want to count, find totals, or collapse collections

**Key Insight:** Different Monoids give different aggregations from same data

**Common Operations:**
- Sum numbers: use integer addition monoid
- Concatenate strings: use string monoid  
- Check all conditions: use boolean AND monoid
- Count elements: map to 1, use integer addition monoid

**Example:**
```java
// Multiple aggregations of the same list
List<Integer> numbers = List.of(1, 2, 3, 4, 5);

// Sum
Integer sum = foldable.foldMap(Monoids.integerAddition(), 
    Function.identity(), numbers); // 15

// Concatenate as strings  
String concat = foldable.foldMap(Monoids.string(), 
    String::valueOf, numbers); // "12345"

// Check all positive
Boolean allPositive = foldable.foldMap(Monoids.booleanAnd(), 
    n -> n > 0, numbers); // true
```

**Think Of It As:** Swiss Army knife for data aggregation
~~~

### Traverse
~~~admonish tip title="Traverse Quick Reference"
**Core Method:** `traverse(Applicative<G> app, Function<A,Kind<G,B>> f, Kind<F,A> fa) -> Kind<G,Kind<F,B>>`

**Purpose:** Apply an effectful function to each element and "flip" the contexts

**Use When:**
- You have a collection and want to apply an effect to each element
- You want to validate every item and collect all errors
- You need to "turn inside-out": `F<G<A>>` becomes `G<F<A>>`

**Key Operations:**
- `traverse`: apply function then flip
- `sequence`: just flip contexts (when you already have `F<G<A>>`)

**Common Patterns:**
- Validate every item in a list
- Make async calls for each element
- Parse/process each item, collecting all failures

**Example:**
```java
// Validate every string in a list, collect all errors
List<String> inputs = List.of("123", "abc", "456");

Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> result =
    listTraverse.traverse(
        validatedApplicative,
        this::parseInteger, // String -> Validated<List<String>, Integer>
        LIST.widen(inputs)
    );

// Result: either Valid(List[123, 456]) or Invalid(["abc is not a number"])
```

**Think Of It As:** Applying effects to collections while flipping the "nesting order"
~~~

## Dual-Parameter Type Classes

### Profunctor
~~~admonish tip title="Profunctor Quick Reference"
**Core Methods:**
- `lmap(Function<C,A> f, Kind2<P,A,B> pab) -> Kind2<P,C,B>` (contravariant on input)
- `rmap(Function<B,D> g, Kind2<P,A,B> pab) -> Kind2<P,A,D>` (covariant on output)  
- `dimap(Function<C,A> f, Function<B,D> g, Kind2<P,A,B> pab) -> Kind2<P,C,D>` (both)

**Purpose:** Adapt inputs and outputs of two-parameter types (especially functions)

**Use When:**
- Building flexible data transformation pipelines
- Creating API adapters that convert between different formats
- You need to preprocess inputs or postprocess outputs
- Building reusable validation or transformation logic

**Key Insight:** 
- `lmap` = preprocess the input (contravariant)
- `rmap` = postprocess the output (covariant)
- `dimap` = do both transformations

**Common Instance:** `Function<A,B>` is the canonical Profunctor

**Example:**
```java
// Adapt a string length function to work with integers and return formatted strings
Function<String, Integer> stringLength = String::length;

// Input adaptation: Integer -> String  
Kind2<FunctionKind.Witness, Integer, Integer> intToLength = 
    profunctor.lmap(Object::toString, FUNCTION.widen(stringLength));

// Output adaptation: Integer -> String
Kind2<FunctionKind.Witness, String, String> lengthToString = 
    profunctor.rmap(len -> "Length: " + len, FUNCTION.widen(stringLength));

// Both adaptations
Kind2<FunctionKind.Witness, Integer, String> fullAdaptation = 
    profunctor.dimap(Object::toString, len -> "Result: " + len, 
        FUNCTION.widen(stringLength));
```

**Think Of It As:** The adapter pattern for functional programming
~~~

## Decision Guide

~~~admonish warning title="Choosing the Right Type Class"
**Start Simple, Go Complex:**
1. **Functor** - Simple transformations, context unchanged
2. **Applicative** - Combine independent computations
3. **Selective** - Conditional effects with static structure
4. **Monad** - Chain dependent computations
5. **MonadError** - Add error handling to Monad
6. **Traverse** - Apply effects to collections
7. **Profunctor** - Adapt inputs/outputs of functions

**Decision Tree:**
- Need to transform values? → **Functor**
- Need to combine independent operations? → **Applicative**
- Need conditional effects with static structure? → **Selective**
- Need sequential dependent operations? → **Monad**
- Need error recovery? → **MonadError**
- Need to process collections with effects? → **Traverse**
- Need to adapt function interfaces? → **Profunctor**
- Need to aggregate/summarise data? → **Foldable**
- Need to combine values? → **Semigroup/Monoid**

**Common Patterns:**
- **Form validation:** Applicative (independent fields) or Traverse (list of fields)
- **Database operations:** Monad (dependent queries) + MonadError (failure handling)
- **API integration:** Profunctor (adapt formats) + Monad (chain calls)
- **Configuration:** Applicative (combine settings) + Reader (dependency injection)
- **Conditional effects:** Selective (feature flags, debug mode) or Monad (dynamic choice)
- **Configuration fallback:** Selective (try multiple sources with static branches)
- **Logging:** Writer (accumulate logs) + Monad (sequence operations)
- **State management:** State/StateT (thread state) + Monad (sequence updates)
~~~

## Type Hierarchy

~~~admonish info title="Type Class Relationships"
```
Functor
    ↑
Applicative ← Apply
    ↑
Selective
    ↑
   Monad
    ↑
MonadError

Semigroup
    ↑
  Monoid

Functor + Foldable
    ↑
 Traverse

(Two-parameter types)
Profunctor
Bifunctor
```

**Inheritance Meaning:**
- Every **Applicative** is also a **Functor**
- Every **Selective** is also an **Applicative** (and therefore a **Functor**)
- Every **Monad** is also a **Selective** (and therefore **Applicative** and **Functor**)
- Every **MonadError** is also a **Monad** (and therefore **Selective**, **Applicative**, and **Functor**)
- Every **Monoid** is also a **Semigroup**
- Every **Traverse** provides both **Functor** and **Foldable** capabilities

**Practical Implication:** If you have a `Monad<F>` instance, you can also use it as a `Selective<F>`, `Applicative<F>`, or `Functor<F>`.
~~~

## Common Monoid Instances

~~~admonish note title="Ready-to-Use Monoids"
**Numeric:**
- `Monoids.integerAddition()` - sum integers (empty = 0)
- `Monoids.integerMultiplication()` - multiply integers (empty = 1)

**Text:**
- `Monoids.string()` - concatenate strings (empty = "")
- `Monoids.string(delimiter)` - join with delimiter

**Boolean:**
- `Monoids.booleanAnd()` - logical AND (empty = true)
- `Monoids.booleanOr()` - logical OR (empty = false)

**Collections:**
- `Monoids.list()` - concatenate lists (empty = [])

**Custom:**
```java
// Create your own monoid
Monoid<MyType> myMonoid = new Monoid<MyType>() {
    public MyType empty() { return MyType.defaultValue(); }
    public MyType combine(MyType a, MyType b) { return a.mergeWith(b); }
};
```
~~~

## Performance Notes

~~~admonish warning title="Performance Considerations"
**When to Use HKT vs Direct Methods:**

**Use HKT When:**
- Writing generic code that works with multiple container types
- Building complex workflows with multiple type classes
- You need the power of type class composition

**Use Direct Methods When:**
- Simple, one-off transformations
- Performance-critical hot paths
- Working with a single, known container type

**Examples:**
```java
// Hot path - use direct method
Optional<String> result = optional.map(String::toUpperCase);

// Generic reusable code - use HKT
public static <F> Kind<F, String> normalise(Functor<F> functor, Kind<F, String> input) {
    return functor.map(String::toUpperCase, input);
}
```

**Memory:** HKT simulation adds minimal overhead (single wrapper object per operation)
**CPU:** Direct method calls vs type class method calls are comparable in modern JVMs
~~~
