# Glossary: Type Classes

~~~admonish info title="What This Page Covers"
- Functor, Monad, and the rest of the abstraction hierarchy.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## Applicative

**Definition:** A type class that extends Functor with the ability to lift pure values into a context and combine multiple independent computations.

**Core Operations:**
- `of(A value)` - Lift a pure value into the context
- `ap(Kind<F, Function<A,B>> ff, Kind<F, A> fa)` - Apply a wrapped function to a wrapped value
- `map2`, `map3`, etc. - Combine multiple wrapped values

**Example:**
```java
Applicative<OptionalKind.Witness> app = OptionalApplicative.INSTANCE;

// Lift pure values
Kind<OptionalKind.Witness, Integer> five = app.of(5);  // Optional[5]

// Combine independent values
Kind<OptionalKind.Witness, String> result = app.map2(
    app.of("Hello"),
    app.of("World"),
    (a, b) -> a + " " + b
);  // Optional["Hello World"]
```

**When To Use:** Combining multiple independent effects (form validation, parallel computations, configuration assembly).

**Related:** [Applicative Documentation](../functional/applicative.md)

---

## Bifunctor

**Definition:** A type class for types with two covariant parameters, allowing transformation of both sides independently or simultaneously.

**Core Operations:**
- `bimap(Function<A,C> f, Function<B,D> g, Kind2<F,A,B> fab)` - Transform both parameters
- `first(Function<A,C> f, Kind2<F,A,B> fab)` - Transform only the first parameter
- `second(Function<B,D> g, Kind2<F,A,B> fab)` - Transform only the second parameter

**Example:**
```java
Bifunctor<EitherKind.Witness> bifunctor = EitherBifunctor.INSTANCE;

Either<String, Integer> either = Either.right(42);
Kind2<EitherKind.Witness, String, Integer> kindEither = EITHER.widen(either);

// Transform both sides
Kind2<EitherKind.Witness, Integer, String> transformed =
    bifunctor.bimap(String::length, Object::toString, kindEither);
// Right("42")
```

**When To Use:** Transforming error and success channels, working with pairs/tuples, API format conversion.

**Related:** [Bifunctor Documentation](../functional/bifunctor.md)

---

## Coyoneda

**Definition:** The "free functor" that provides automatic Functor instances for any type constructor. Coyoneda stores a value and an accumulated transformation function, deferring actual mapping until lowering.

**Core Operations:**
- `lift(Kind<F, A> fa)` - Wrap a value in Coyoneda
- `map(Function<A, B> f)` - Compose functions (no Functor needed)
- `lower(Functor<F> functor)` - Apply accumulated function using provided Functor

**Example:**
```java
// Lift into Coyoneda - no Functor required for mapping!
Coyoneda<MyDSL, Integer> coyo = Coyoneda.lift(myInstruction);

// Chain maps - functions are composed, not applied
Coyoneda<MyDSL, String> mapped = coyo
    .map(x -> x * 2)
    .map(x -> x + 1)
    .map(Object::toString);

// Only when lowering is the Functor used (and functions applied once)
Kind<MyDSL, String> result = mapped.lower(myDslFunctor);
```

**Key Benefit:** Enables map fusion (multiple maps become one function composition) and eliminates the need for Functor instances on Free monad instruction sets.

**When To Use:** With Free monads to avoid implementing Functor for every DSL instruction type; optimising chains of map operations.

**Related:** [Coyoneda Documentation](../monads/coyoneda.md), [Map Fusion](#map-fusion)

---

## Free Applicative

**Definition:** The applicative counterpart to Free Monad. Whilst Free Monad captures sequential, dependent computations, Free Applicative captures independent computations that can potentially run in parallel.

**Core Operations:**
- `pure(A value)` - Lift a pure value
- `lift(Kind<F, A> fa)` - Lift a single instruction
- `map2(FreeAp<F, B> fb, BiFunction<A, B, C> f)` - Combine independent computations
- `foldMap(Natural<F, G> nat, Applicative<G> app)` - Interpret using a natural transformation

**Example:**
```java
// Independent computations - can run in parallel
FreeAp<DbOp, User> userFetch = FreeAp.lift(new GetUser(userId));
FreeAp<DbOp, List<Post>> postsFetch = FreeAp.lift(new GetPosts(userId));

// Combine them - neither depends on the other's result
FreeAp<DbOp, UserProfile> profile = userFetch.map2(
    postsFetch,
    UserProfile::new
);

// Smart interpreter can execute both in parallel or batch them
Kind<CompletableFutureKind.Witness, UserProfile> result =
    profile.foldMap(parallelInterpreter, cfApplicative);
```

**When To Use:** Parallel data fetching, validation that accumulates all errors, batching independent operations, static analysis of programs before execution.

**Related:** [Free Applicative Documentation](../monads/free_applicative.md), [Free Monad](../monads/free_monad.md)

---

## Functor

**Definition:** The most basic type class for types that can be "mapped over". Allows transforming values inside a context without changing the context structure.

**Core Operation:**
- `map(Function<A,B> f, Kind<F,A> fa)` - Apply a function to the wrapped value

**Example:**
```java
Functor<ListKind.Witness> functor = ListFunctor.INSTANCE;

Kind<ListKind.Witness, String> strings = LIST.widen(List.of("one", "two"));
Kind<ListKind.Witness, Integer> lengths = functor.map(String::length, strings);
// [3, 3]
```

**Laws:**
- Identity: `map(x -> x, fa) == fa`
- Composition: `map(g.compose(f), fa) == map(g, map(f, fa))`

**When To Use:** Simple transformations where the context (container structure) stays the same.

**Related:** [Functor Documentation](../functional/functor.md)

---

## Instances Facade

**Definition:** A single static entry point, `Instances`, for obtaining any built-in type-class instance, replacing the three legacy idioms (a static `INSTANCE` field, a generic `instance()` method, or an argument-taking constructor) with one predictable shape.

**Core Operations:**
- `Instances.monad(token)` / `applicative` / `functor` - total lookups (every canonical instance is at least a Monad)
- `Instances.monadError(token)` / `monadZero` / `alternative` - partial lookups, valid only where the instance implements that capability
- `Instances.validated(semigroup)` / `writer(monoid)` / `eitherT(outer)` / `maybeT` / `optionalT` / `readerT` / `stateT` / `writerT(outer, monoid)` - argument-carrying re-exports whose required dependency is in the signature

**Example:**
```java
import static org.higherkindedj.hkt.instances.Witnesses.*;

Monad<MaybeKind.Witness>               m = Instances.monad(maybe());
MonadError<EitherKind.Witness<String>, String> e = Instances.monadError(either()); // String inferred
MonadError<ValidatedKind.Witness<E>, E> v = Instances.validated(Semigroups.list());
```

**Why It Matters:** Discoverable by capability via autocomplete; phantom types still infer from the assignment target; compile-time safe (a thin static re-export, not registry/`ServiceLoader`-backed).

**Related:** [Obtaining Instances](../functional/instances_facade.md), [Witnesses](type-system.md#witnessarity)

---

## Map Fusion

**Definition:** An optimisation where multiple consecutive `map` operations are combined into a single function composition, reducing the number of traversals over a data structure.

**Example:**
```java
// Without fusion: three separate traversals
list.map(x -> x * 2)      // Traversal 1
    .map(x -> x + 1)      // Traversal 2
    .map(Object::toString); // Traversal 3

// With Coyoneda: one traversal
Coyoneda.lift(list)
    .map(x -> x * 2)       // Just composes functions
    .map(x -> x + 1)       // Just composes functions
    .map(Object::toString) // Just composes functions
    .lower(listFunctor);   // ONE traversal with composed function
```

**How It Works:** Coyoneda stores the accumulated function `f.andThen(g).andThen(h)` instead of applying each map immediately. The composed function is applied once during `lower()`.

**When To Use:** Chains of transformations on expensive-to-traverse structures; automatically enabled when using Coyoneda.

**Related:** [Coyoneda](#coyoneda), [Coyoneda Documentation](../monads/coyoneda.md)

---

## Monad

**Definition:** A type class that extends Applicative with the ability to chain dependent computations (flatMap/bind).

**Core Operation:**
- `flatMap(Function<A, Kind<F,B>> f, Kind<F,A> ma)` - Chain computations where each depends on the previous result

**Additional Operations:**
- `flatMap2/3/4/5(...)` - Combine multiple monadic values with a function that returns a monadic value (similar to `map2/3/4/5` but with effectful combining function)
- `as(B value, Kind<F,A> ma)` - Replace the result while preserving the effect
- `peek(Consumer<A> action, Kind<F,A> ma)` - Perform side effect without changing the value

**Example:**
```java
Monad<OptionalKind.Witness> monad = Instances.monadError(optional());

// Chain dependent operations
Kind<OptionalKind.Witness, String> result =
    monad.flatMap(
        userId -> monad.flatMap(
            profile -> findAccount(profile.accountId()),
            findProfile(userId)
        ),
        findUser("user123")
    );

// Combine multiple monadic values with effectful result
Kind<OptionalKind.Witness, Order> order =
    monad.flatMap2(
        findUser("user123"),
        findProduct("prod456"),
        (user, product) -> validateAndCreateOrder(user, product)
    );
```

**Laws:**
- Left Identity: `flatMap(f, of(a)) == f(a)`
- Right Identity: `flatMap(of, m) == m`
- Associativity: `flatMap(g, flatMap(f, m)) == flatMap(x -> flatMap(g, f(x)), m)`

**When To Use:** Sequential operations where each step depends on the previous result (database queries, async workflows, error handling pipelines).

**Related:** [Monad Documentation](../functional/monad.md)

---

## Monad Transformer

**Definition:** A type constructor that adds capabilities to an existing monad, allowing multiple effects to be combined in a single computation. Transformers "stack" effects, enabling you to work with combinations like "async computation that may fail" or "IO that produces optional values".

**Common Transformers in Higher-Kinded-J:**
| Transformer | Adds | Example Stack |
|-------------|------|---------------|
| `EitherT<F, E, A>` | Error handling | `EitherT<IO, Error, A>` - IO that may fail |
| `MaybeT<F, A>` | Optionality | `MaybeT<CompletableFuture, A>` - async optional |
| `StateT<F, S, A>` | State threading | `StateT<Either, State, A>` - stateful with errors |

**Example:**
```java
// Problem: combining async + error handling manually is verbose
CompletableFuture<Either<Error, User>> fetchUser(String id);
CompletableFuture<Either<Error, Profile>> fetchProfile(User user);

// Without transformer: nested flatMaps
CompletableFuture<Either<Error, Profile>> result =
    fetchUser(id).thenCompose(eitherUser ->
        eitherUser.fold(
            error -> CompletableFuture.completedFuture(Either.left(error)),
            user -> fetchProfile(user)
        )
    );

// With EitherT: flat composition
EitherT<CompletableFutureKind.Witness, Error, Profile> result =
    EitherT.fromKind(CF.widen(fetchUser(id)), cfMonad)
        .flatMap(user -> EitherT.fromKind(CF.widen(fetchProfile(user)), cfMonad));

// Run to get the nested type back
CompletableFuture<Either<Error, Profile>> unwrapped =
    CF.narrow(result.value());
```

**How It Works:**
1. Wrap your nested type in the transformer: `EitherT.fromKind(...)`
2. Use standard monad operations (`map`, `flatMap`) on the transformer
3. The transformer handles the effect interleaving automatically
4. Unwrap when done using `.value()` or `.run()`

**Lift Operations:**
```java
// Lift the outer monad into the transformer
EitherT<IOKind.Witness, Error, String> lifted =
    EitherT.liftF(IO.delay(() -> "hello"), ioMonad);

// Lift an Either into the transformer
EitherT<IOKind.Witness, Error, Integer> fromEither =
    EitherT.fromEither(Either.right(42), ioMonad);
```

**When To Use:**
- Combining async operations with error handling
- Threading state through effectful computations
- When you need multiple effects but want flat composition
- Building effect stacks for complex workflows

**Trade-offs:**
- Adds complexity and potential performance overhead
- Stack order matters: `EitherT<IO, E, A>` ≠ `IOT<Either<E, _>, A>`
- Consider [Effect Paths](effect-paths.md#effect-path) for simpler use cases

**Related:** [Monad](#monad), [MonadError](#monaderror), [EitherT Documentation](../transformers/eithert_transformer.md)

---

## MonadError

**Definition:** A type class that extends Monad with explicit error handling capabilities for a specific error type.

**Core Operations:**
- `raiseError(E error)` - Create an error state
- `handleErrorWith(Kind<F,A> ma, Function<E, Kind<F,A>> handler)` - Recover from errors

**Example:**
```java
MonadError<EitherKind.Witness<String>, String> monadError = EitherMonadError.instance();

Kind<EitherKind.Witness<String>, Double> result =
    monadError.handleErrorWith(
        divideOperation,
        error -> monadError.of(0.0)  // Provide default on error
    );
```

**When To Use:** Workflows that need explicit error handling and recovery (validation, I/O operations, API calls).

**Related:** [MonadError Documentation](../functional/monad_error.md)

---

## Monoid

**Definition:** A type class for types that have an associative binary operation (`combine`) and an identity element (`empty`). Extends Semigroup by adding the identity element, making it safe for reducing empty collections.

**Core Operations:**
- `empty()` - The identity element
- `combine(A a1, A a2)` - Associative binary operation (from Semigroup)
- `combineAll(Iterable<A> elements)` - Combine all elements in a collection
- `combineN(A value, int n)` - Combine a value with itself n times
- `isEmpty(A value)` - Test if a value equals the empty element

**Example:**
```java
Monoid<Integer> intAddition = Monoids.integerAddition();

// Identity law: empty is the neutral element
intAddition.combine(5, intAddition.empty());  // 5
intAddition.combine(intAddition.empty(), 5);  // 5

// Combine a collection
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Integer sum = intAddition.combineAll(numbers);  // 15

// Repeated application
Integer result = intAddition.combineN(3, 4);  // 12 (3+3+3+3)

// Working with Optional values
Monoid<Optional<Integer>> maxMonoid = Monoids.maximum();
Optional<Integer> max = maxMonoid.combineAll(
    List.of(Optional.of(5), Optional.empty(), Optional.of(10))
);  // Optional[10]
```

**Common Instances in `Monoids` utility:**
- `integerAddition()`, `longAddition()`, `doubleAddition()` - Numeric addition
- `integerMultiplication()`, `longMultiplication()`, `doubleMultiplication()` - Numeric multiplication
- `string()` - String concatenation
- `list()`, `set()` - Collection concatenation/union
- `booleanAnd()`, `booleanOr()` - Boolean operations
- `firstOptional()`, `lastOptional()` - First/last non-empty Optional
- `maximum()`, `minimum()` - Max/min value aggregation with Optional
- `update()` - Left-to-right composition of `Update<S>` transformations

**Laws:**
- Left Identity: `combine(empty(), a) == a`
- Right Identity: `combine(a, empty()) == a`
- Associativity: `combine(a, combine(b, c)) == combine(combine(a, b), c)` (from Semigroup)

**When To Use:** Aggregating data (summing values, concatenating strings), reducing collections, folding data structures, accumulating results in parallel computations.

**Related:** [Semigroup and Monoid Documentation](../functional/semigroup_and_monoid.md), [Update](#update)

---

## Natural Transformation

**Definition:** A polymorphic function between type constructors. Given type constructors `F` and `G`, a natural transformation is a function that converts `F[A]` to `G[A]` for any type `A`, without knowing or inspecting what `A` is.

**Core Operation:**
- `apply(Kind<F, A> fa)` - Transform from context F to context G

**Example:**
```java
// Natural transformation from Maybe to List
Natural<MaybeKind.Witness, ListKind.Witness> maybeToList = new Natural<>() {
    @Override
    public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
        Maybe<A> maybe = MAYBE.narrow(fa);
        List<A> list = maybe.map(List::of).orElse(List.of());
        return LIST.widen(list);
    }
};

// Use with Free monad interpretation
Free<ConsoleOpKind.Witness, String> program = ...;
Kind<IOKind.Witness, String> executable = program.foldMap(interpreter, ioMonad);
```

**The Naturality Law:** For any function `f: A -> B`:
```
nat.apply(functor.map(f, fa)) == functor.map(f, nat.apply(fa))
```

**When To Use:** Free monad/applicative interpretation, type conversions between containers, monad transformer lifting.

**Related:** [Natural Transformation Documentation](../functional/natural_transformation.md)

---

## Profunctor

**Definition:** A type class for types that are contravariant in their first parameter (input) and covariant in their second parameter (output). The canonical example is `Function<A, B>`.

**Core Operations:**
- `lmap(Function<C,A> f, Kind2<P,A,B> pab)` - Pre-process the input (contravariant)
- `rmap(Function<B,D> g, Kind2<P,A,B> pab)` - Post-process the output (covariant)
- `dimap(Function<C,A> f, Function<B,D> g, Kind2<P,A,B> pab)` - Transform both simultaneously

**Example:**
```java
Profunctor<FunctionKind.Witness> prof = FunctionProfunctor.INSTANCE;

Function<String, Integer> stringLength = String::length;
Kind2<FunctionKind.Witness, String, Integer> kindFunc = FUNCTION.widen(stringLength);

// Adapt to work with integers (converting to string first)
Kind2<FunctionKind.Witness, Integer, Integer> intLength =
    prof.lmap(Object::toString, kindFunc);
```

**When To Use:** Building adaptable pipelines, API adapters, validation frameworks that need to work with different input/output formats.

**Related:** [Profunctor Documentation](../functional/profunctor.md)

---

## Selective

**Definition:** A type class that sits between Applicative and Monad, providing conditional effects with static structure. All branches must be known upfront, enabling static analysis.

**Core Operations:**
- `select(Kind<F, Choice<A,B>> fab, Kind<F, Function<A,B>> ff)` - Conditionally apply a function
- `whenS(Kind<F, Boolean> cond, Kind<F, Unit> effect)` - Execute effect only if condition is true
- `ifS(Kind<F, Boolean> cond, Kind<F, A> then, Kind<F, A> else)` - If-then-else with visible branches

**Example:**
```java
Selective<IOKind.Witness> selective = IOSelective.INSTANCE;

// Only log if debug is enabled
Kind<IOKind.Witness, Boolean> debugEnabled =
    IO_KIND.widen(IO.delay(() -> config.isDebug()));
Kind<IOKind.Witness, Unit> logEffect =
    IO_KIND.widen(IO.fromRunnable(() -> log.debug("Debug info")));

Kind<IOKind.Witness, Unit> conditionalLog = selective.whenS(debugEnabled, logEffect);
```

**When To Use:** Feature flags, conditional logging, configuration-based behaviour, multi-source fallback strategies.

**Related:** [Selective Documentation](../functional/selective.md)

---

## Semigroup

**Definition:** A type class for types that have an associative binary operation. The most fundamental algebraic structure for combining values.

**Core Operation:**
- `combine(A a1, A a2)` - Associative binary operation

**Example:**
```java
Semigroup<String> stringConcat = Semigroups.string();
String result = stringConcat.combine("Hello", " World");  // "Hello World"

// With custom delimiter
Semigroup<String> csvConcat = Semigroups.string(", ");
String csv = csvConcat.combine("apple", "banana");  // "apple, banana"

// For error accumulation in Validated
Semigroup<String> errorAccumulator = Semigroups.string("; ");
Applicative<Validated.Witness<String>> validator =
    Instances.validated(errorAccumulator);
// Errors are combined: "Field A is invalid; Field B is required"
```

**Common Instances in `Semigroups` utility:**
- `string()` - Basic string concatenation
- `string(String delimiter)` - String concatenation with delimiter
- `list()` - List concatenation
- `set()` - Set union
- `first()` - Always takes the first value
- `last()` - Always takes the last value

**Laws:**
- Associativity: `combine(a, combine(b, c)) == combine(combine(a, b), c)`

**When To Use:** Error accumulation (especially with Validated), combining partial results, building aggregators where an empty/identity value doesn't make sense.

**Related:** [Semigroup and Monoid Documentation](../functional/semigroup_and_monoid.md)

---

## Update

**Definition:** A named, reusable transformation of a value (`S -> S`). `Update<S>` extends `UnaryOperator<S>`, so it drops straight into `Stream.map`, `Optional.map`, and any `Function`-shaped API, whilst its `andThen` stays in the `Update` type so compositions chain fluently. In functional-programming literature this is the `Endo` monoid (from *endomorphism*, a function from a type to itself); Higher-Kinded-J names it `Update` for clarity.

**Example:**
```java
Update<Order> normalise     = order -> order.withEmail(order.email().toLowerCase());
Update<Order> applyDiscount = order -> order.withTotal(order.total().multiply(DISCOUNT));

// Compose in the type; the monoid's identity is the do-nothing update
Monoid<Update<Order>> m = Monoids.update();
Update<Order> pipeline = m.combineAll(List.of(normalise, applyDiscount));  // applied left to right
Order result = pipeline.apply(order);
```

**Why it matters:** because the identity is "change nothing", an absent or skipped step contributes nothing to the fold. That property powers sparse partial updates: the [Edits](optics.md#edits) builder folds optic-targeted edits through `Monoids.update()`, with absent fields contributing the identity.

**Related:** [Semigroup and Monoid](../functional/semigroup_and_monoid.md), [Monoid](#monoid), [Edits](optics.md#edits)

