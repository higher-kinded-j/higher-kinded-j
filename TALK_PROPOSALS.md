# Talk Proposals: higher-kinded-j for Functional Programmers

Two 40-minute talk proposals for functional programmers (possibly with Java experience).

---

## Talk 1: "Optics in Java — From Impossible to Inevitable"

### The Pitch (1 sentence)
Optics have always felt like they belong to Haskell and Scala — but with higher-kinded-j's annotation-driven code generation and Focus DSL, they become not only feasible in Java but genuinely useful for everyday immutable data manipulation.

### Why This Talk?
- Functional programmers know optics but assume Java can't support them properly
- Java's record types (Java 16+) create a real need for composable immutable updates
- higher-kinded-j proves the "impossible" wrong — and the result is surprisingly ergonomic

---

### Outline (40 minutes)

#### Act 1: The Problem Optics Solve (8 min)

**The Nested Immutable Update Problem**
- Java records give us immutability, but updating nested fields is painful:
  ```java
  // 4 levels deep — this is real Java code people write
  var updated = new Company(
      company.name(),
      new Department(
          company.department().name(),
          new Team(
              company.department().team().name(),
              company.department().team().members().stream()
                  .map(m -> m.name().equals("Alice")
                      ? new Member(m.name(), m.age() + 1) : m)
                  .toList())));
  ```
- This is the same problem Haskell solved with lenses in 2009
- The deeper the nesting, the worse it gets — and real domain models nest deeply

**What Are Optics? (Quick refresher for the audience)**
- A family of composable, first-class references into data structures
- The core types form a hierarchy based on "how many things can I focus on?":
  - **Iso** — lossless two-way conversion (exactly one, reversible)
  - **Lens** — focus on exactly one part ("has-a")
  - **Prism** — focus on zero or one part ("is-a", sum types)
  - **Affine** — focus on zero or one part (the Lens+Prism hybrid)
  - **Traversal** — focus on zero or more parts ("has-many")
  - **Fold** — read-only query over zero or more parts
  - **Getter/Setter** — read-only and write-only views

#### Act 2: Why Optics "Shouldn't" Work in Java (5 min)

**The type system gap**
- Optics rely on higher-kinded polymorphism: `modifyF :: Functor f => (a -> f a) -> s -> f s`
- Java has no `Functor f =>` constraint — no higher-kinded types at all
- No type classes, no implicit resolution, limited type inference
- The Haskell `lens` library uses ~30 type parameters in its general form

**Previous attempts and why they fell short**
- Manual getter/setter pairs — no composition
- Code generation of `withX()` methods — no polymorphism, no traversals
- Vavr/Monocle-Java ports — limited to Lens/Prism, no unified composition

#### Act 3: How higher-kinded-j Makes It Work (12 min)

**The HKT Foundation (brief)**
- The `Kind<F, A>` encoding — type witnesses simulate higher-kinded types
- `Functor<F>`, `Applicative<F>` — type classes as interfaces
- This unlocks the *real* `modifyF` signature in Java:
  ```java
  <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S source, Functor<F> functor);
  ```
- This single method is what makes the entire optics hierarchy composable

**The Optic Interface — One Method to Rule Them All**
- `Optic<S, T, A, B>` with a single `modifyF` method
- Every optic type (Lens, Prism, Traversal...) is a specialisation
- Composition falls out naturally: `andThen` just nests `modifyF` calls
- Show the composition table:
  - Lens + Lens = Lens
  - Lens + Prism = Affine
  - Prism + Lens = Affine
  - Anything + Traversal = Traversal

**Concrete Optic Types in higher-kinded-j**
- `Lens<S, A>` — `get(S) -> A` + `set(A, S) -> S` + `modifyF`
- `Prism<S, A>` — `getOptional(S) -> Optional<A>` + `build(A) -> S`
- `Iso<S, A>` — `get(S) -> A` + `reverseGet(A) -> S`
- `Affine<S, A>` — `getOptional(S) -> Optional<A>` + `set(A, S) -> S`
- `Traversal<S, A>` — pure `modifyF` over zero or more elements

**Paired Lenses — Solving the Coupled Fields Problem**
- `Lens.paired()` for atomic updates of coupled fields (e.g., Range(lo, hi))
- Avoids invalid intermediate states during sequential lens updates

#### Act 4: The Focus DSL — Making It Ergonomic (10 min)

**The Boilerplate Problem**
- Writing lenses by hand is tedious
- `@GenerateLenses` — annotation processor generates Lens definitions for records
- `@GenerateFocus` — generates Focus DSL path classes with fluent navigation

**FocusPath, AffinePath, TraversalPath**
- Three path types mirroring the optic hierarchy:
  - `FocusPath<S, A>` wraps a Lens (exactly one)
  - `AffinePath<S, A>` wraps an Affine (zero or one)
  - `TraversalPath<S, A>` wraps a Traversal (zero or more)
- Path types widen automatically during composition:
  ```java
  FocusPath.via(Lens)      → FocusPath      // still exact
  FocusPath.via(Prism)     → AffinePath     // might not exist
  FocusPath.via(Traversal) → TraversalPath  // might be many
  ```

**The Fluent Navigation API**
```java
// Navigate deep into nested structures
String city = CompanyFocus.headquarters().city().get(company);

// Update deeply nested values
Company updated = CompanyFocus.headquarters().city()
    .set("Edinburgh", company);

// Traverse into collections
List<String> allNames = TeamFocus.members().each()
    .via(MemberFocus.name()).getAll(team);

// Collection operations: each(), at(i), atKey(k), head(), last(), some()
```

**Generated Navigators**
- `@GenerateFocus(generateNavigators = true)` — cross-type fluent navigation
- `CompanyFocus.headquarters().city()` instead of `.via(AddressFocus.city().toLens())`
- Depth limiting (`maxNavigatorDepth`) and field filtering (`includeFields`/`excludeFields`)

**The Effect Bridge**
- FocusPath bridges to EffectPath: `.toMaybePath()`, `.toEitherPath()`, `.toTryPath()`
- `traverseWith()` — apply effectful functions to focused elements
- Optics + Effects = powerful validation, async fetch, error handling pipelines

#### Act 5: Free Monad Optic Programs (5 min)

**Optics as Data**
- `OpticOp<S, A>` — sealed interface of operations: Get, Set, Modify, GetAll, Exists, Count...
- `OpticPrograms` — builds Free monad programs from optic operations
- Programs are *data structures* describing what to do, not imperative code

**Multiple Interpreters**
- `DirectOpticInterpreter` — normal execution
- `LoggingOpticInterpreter` — records an audit trail of all operations
- `ValidationOpticInterpreter` — checks constraints before committing changes
- The same program, different execution strategies

**Example:**
```java
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, PersonFocus.name())
        .flatMap(name ->
            OpticPrograms.modify(person, PersonFocus.age(), a -> a + 1));

Person result = OpticInterpreters.direct().run(program);
```

#### Closing (2 min)
- Optics in Java: once "impossible", now ergonomic with the right foundations
- The key insight: higher-kinded types unlock `modifyF`, and `modifyF` unlocks everything
- Annotation processing eliminates boilerplate
- The Focus DSL makes it feel natural for Java developers
- "The best abstractions are the ones you barely notice you're using"

---

## Talk 2: "Composable Effects in Java — Building on Java's Strengths"

### The Pitch (1 sentence)
Instead of fighting Java's type system to replicate Haskell's monad transformers, higher-kinded-j's EffectPath API embraces Java's strengths — sealed interfaces, fluent APIs, virtual threads, structured concurrency, and annotation processing — to deliver composable effects that feel native.

### Why This Talk?
- Functional programmers know effect systems (cats-effect, ZIO, mtl) — but Java can't do them the same way
- The interesting question: what happens when you *don't* try to port Haskell, but instead ask "how would Java want effects to work?"
- The answer: nominal typed path wrappers + capability interfaces + code generation

---

### Outline (40 minutes)

#### Act 1: The Effect Composition Problem (7 min)

**What are composable effects?**
- Code that chains operations where each step might: fail, be absent, produce side effects, run asynchronously, accumulate errors...
- In Haskell: monads + do-notation + type classes
- In Scala: for-comprehensions + cats/ZIO
- In Java: ...?

**What Java currently offers**
- `Optional` — partiality, but doesn't compose with error handling
- `CompletableFuture` — async, but callback hell for chaining
- `Stream` — collection processing, but no error handling
- `try/catch` — exception handling, but doesn't compose
- Each exists in isolation — no unified composition model

**The Monad Transformer approach (and why Java resists it)**
- Haskell solution: `EitherT (ReaderT IO)` — stack transformers
- Java problems: no HKT means no polymorphic `lift`, limited type inference makes transformer stacks painful, `Kind<Kind<Kind<...>>>` nesting
- "If you squint hard enough, you can make it work, but you wouldn't want to maintain it"

#### Act 2: The EffectPath Design Philosophy (8 min)

**The Three-Layer Architecture**
1. **Layer 1 (Raw HKT)**: `Kind<F, A>`, `Monad<F>`, type witnesses — category theory encoded in Java
2. **Layer 2 (EffectPath — the primary API)**: Thin nominal wrappers — IOPath, MaybePath, EitherPath, etc.
3. **Layer 3 (Contexts)**: Monad transformers — ErrorContext, ConfigContext — for advanced users

Most users work at Layer 2. The key insight: **hide HKT behind nominal types**.

**Nominal Types Over Structural**
- Each effect gets its own class: `MaybePath<A>`, `EitherPath<E, A>`, `IOPath<A>`
- Not `Kind<MaybeKind.Witness, A>` — that's Layer 1 internals
- Java's strength: explicit types, excellent IDE support, compile-time safety
- "If your type is visible in the API, it should be a real type, not an encoding"

**Capability Interfaces — Sealed Type Hierarchy**
```
Composable<A>  [Functor — map, peek]
    │
Combinable<A>  [Applicative — zipWith]
    │
Chainable<A>   [Monad — via, flatMap, then]
    ├── Recoverable<E,A>  [MonadError — recover, mapError]
    ├── Effectful<A>      [Side effects — unsafeRun, runSafe]
    └── Accumulating<E,A> [Error accumulation]
```
- `sealed interface` ensures the compiler knows all implementations
- Each capability is a *Java-native* concept with exhaustive pattern matching
- Not `Monad<F>` — but `Chainable<A>` — same laws, different vocabulary

#### Act 3: Building on Java's Strengths (12 min)

**Strength 1: Fluent APIs / Method Chaining**
```java
EitherPath<ApiError, Profile> result = Path.right(userId)
    .via(id -> userRepo.findById(id))        // chain dependent computation
    .map(User::getName)                       // transform the value
    .via(name -> validateName(name))          // another dependent step
    .recover(err -> Profile.defaultProfile()) // error recovery
    .map(Profile::normalise);                 // final transformation
```
- Every operation returns the same path type (or a compatible one)
- IDE autocomplete guides you through the API
- Reads top-to-bottom like a pipeline, not inside-out like Haskell

**Strength 2: The `via` Vocabulary**
- Named to match the Focus DSL: `FocusPath.via(lens)` navigates data, `EffectPath.via(fn)` navigates effects
- Consistent mental model: "navigate through" whether data or effects
- `flatMap` available as alias for traditional FP audiences

**Strength 3: Virtual Threads (Java 21+) — VTaskPath**
```java
VTaskPath<UserProfile> profile =
    fetchUser.parZipWith(fetchOrders,
        (user, orders) -> new UserProfile(user, orders));

CompletableFuture<UserProfile> future = profile.runAsync();
```
- 1M+ concurrent tasks on virtual threads
- `parZipWith` — applicative parallelism, not sequential binding
- `bracket`, `guarantee`, `race` — structured concurrency primitives
- Not a port of cats-effect's IO — *built on* Java's virtual thread runtime

**Strength 4: Structured Concurrency / Resource Safety**
```java
IOPath<String> content = IOPath.bracket(
    () -> Files.newInputStream(path),          // acquire
    in -> new String(in.readAllBytes()),        // use
    in -> { try { in.close(); } catch (IOException e) {} }  // release
);

IOPath<Config> safe = Path.io(() -> loadConfig())
    .guarantee(() -> cleanup());  // runs even on exception
```
- bracket/guarantee pattern from functional effect systems
- Works with Java's `AutoCloseable`:
  ```java
  IOPath.withResource(
      () -> Files.newBufferedReader(path),
      reader -> reader.lines().collect(joining("\n"))
  );
  ```

**Strength 5: Sealed Interfaces — Exhaustive Capabilities**
```java
// The compiler KNOWS all subtypes of Recoverable
public sealed interface Recoverable<E, A> extends Chainable<A>
    permits MaybePath, EitherPath, TryPath, ValidationPath, CompletableFuturePath {}
```
- Can pattern match on capabilities
- No surprise implementations — the hierarchy is closed
- Enables optimisations the compiler can verify

**Strength 6: Annotations — @GeneratePathBridge**
```java
@GeneratePathBridge
public interface UserService {
    @PathVia
    Optional<User> findById(Long id);

    @PathVia
    Either<ApiError, Order> placeOrder(OrderRequest req);
}

// Generated: UserServicePaths
// findById returns OptionalPath<User>
// placeOrder returns EitherPath<ApiError, Order>
```
- Zero reflection, compile-time code generation
- Bridges imperative service interfaces to the effect world
- Type mapping: `Optional` → `OptionalPath`, `Either` → `EitherPath`, etc.

#### Act 4: ForPath — Java's For-Comprehensions (5 min)

**Scala's for-comprehensions, Java-style**
```java
MaybePath<Integer> result =
    ForPath.from(Path.just(10))
        .from(a -> Path.just(a * 2))
        .let(t -> t._1() + t._2())
        .yield((original, doubled, sum) -> sum);  // 30
```
- `from()` = monadic bind (like Scala's `<-`)
- `let()` = pure value binding (like Scala's `=`)
- `when()` = guard conditions
- `yield()` = final result with access to all bindings

**Multi-effect comprehensions**
```java
// Works with EitherPath for error handling
EitherPath<String, Integer> validated =
    ForPath.from(Path.right(10))
        .from(a -> a > 0 ? Path.right(a) : Path.left("negative"))
        .yield((a, b) -> a + b);

// Works with IOPath for deferred effects
IOPath<String> composed =
    ForPath.from(Path.io(() -> readFile()))
        .from(content -> Path.io(() -> process(content)))
        .yield((raw, processed) -> raw + processed);
```

#### Act 5: The Full Effect Palette (5 min)

**A tour of the 20+ path types**
- **MaybePath** — optional values (Just/Nothing)
- **EitherPath** — typed errors (Left/Right)
- **TryPath** — exception capture
- **IOPath** — deferred, lazy side effects
- **VTaskPath** — virtual thread concurrency
- **ValidationPath** — error *accumulation* with Semigroups
- **ReaderPath** — dependency injection
- **StatePath** — immutable state threading
- **WriterPath** — logging / accumulation
- **FreePath/FreeApPath** — DSL interpreters
- **CompletableFuturePath** — Java future interop
- **TrampolinePath** — stack-safe recursion
- **StreamPath/VStreamPath** — lazy/concurrent streams

**Type Conversions**
```java
MaybePath<User> maybe = ...;
EitherPath<Error, User> either = maybe.toEitherPath(Error.notFound());
TryPath<User> tryPath = maybe.toTryPath(() -> new NoSuchElement());
IOPath<User> io = maybe.toIOPath(() -> new MissingUserException());
```

**The Optics Bridge**
```java
IOPath<String> config = Path.io(() -> readConfig())
    .focus(configLens)          // Navigate data via optics
    .map(String::trim);         // Back in the effect world
```

#### Act 6: Why This Approach Works for Java (3 min)

**Comparison with Haskell/Scala approach**

| Aspect                | Haskell/Scala        | higher-kinded-j EffectPath |
|-----------------------|----------------------|----------------------------|
| Type inference        | Heavy, implicit      | Explicit, nominal          |
| Vocabulary            | `>>=`, `flatMap`     | `via` (matches Focus DSL)  |
| Effect types          | Structural/encoded   | Nominal classes per effect  |
| IDE support           | Limited              | Excellent (sealed types)   |
| Concurrency           | Green threads (IO)   | Virtual threads (VTaskPath)|
| Code generation       | Derivation/macros    | Annotation processing      |
| Error handling        | MonadError typeclass  | Recoverable interface      |
| Null safety           | N/A                  | @NullMarked throughout     |

**The key philosophical difference:**
- Haskell: "Everything is a type class constraint — the compiler figures it out"
- higher-kinded-j: "Everything is a named type — the IDE shows you what's available"
- Same mathematical foundations, different ergonomic trade-offs

#### Closing (2 min)
- Java's "limitations" (no HKT, limited inference) forced creative design
- The result: an API that's *more discoverable* than traditional FP effect systems
- Sealed interfaces + nominal types = Java-native type safety
- Virtual threads + annotations = Java-native performance and DX
- "Don't port Haskell to Java. Ask what Java wants to become."

---

## Cross-Cutting Themes

Both talks share common threads that could be referenced in either:

1. **The modifyF insight** — one polymorphic method enables an entire ecosystem
2. **Annotation processing as the Java answer to type class derivation**
3. **The via vocabulary** — unified naming across data navigation and effect composition
4. **Sealed interfaces** — Java's contribution to type safety that Haskell doesn't have
5. **The three-layer architecture** — HKT foundations hidden behind ergonomic APIs

## Suggested Demo Code

For either talk, the best demos from the codebase:
- `hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/` — Focus DSL demos
- `hkj-examples/src/main/java/org/higherkindedj/example/effect/` — EffectPath demos
- The annotation processing: `@GenerateLenses`, `@GenerateFocus`, `@GeneratePathBridge`
- The test suites: comprehensive property-based tests showing law compliance
