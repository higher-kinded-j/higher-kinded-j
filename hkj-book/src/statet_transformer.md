# StateT Monad Transformer

The `StateT` monad transformer is a powerful construct that allows you to add state-management capabilities to an existing monadic context. Think of it as taking the [State Monad](./state_monad.html) and making it work on top of *another* monad, like `OptionalKind`, `EitherKind`, or `IOKind`.

This is incredibly useful when you have computations that are both stateful and involve other effects, such as:

* Potentially missing values (`OptionalKind`)
* Operations that can fail (`EitherKind`, `TryKind`)
* Side-effecting computations (`IOKind`)

## What is StateT?

At its core, a `StateT<S, F, A>` represents a computation that:

1. Takes an initial state of type `S`.
2. Produces a result of type `A` along with a new state of type `S`.
3. And this entire process of producing the `(newState, value)` pair is itself wrapped in an underlying monadic context `F`.

So, the fundamental structure of a `StateT` computation can be thought of as a function:
`S -> F<StateTuple<S, A>>`

Where:

* `S`: The type of the state.
* `F`: The witness type for the underlying monad (e.g., `OptionalKind.Witness`, `IOKind.Witness`).
* `A`: The type of the computed value.
* `StateTuple<S, A>`: A simple container holding a pair of `(state, value)`.


![statet_transformer.svg](images/puml/statet_transformer.svg)

## Key Classes and `Concepts`

* **`StateT<S, F, A>`**: The primary data type representing the stateful computation stacked on monad `F`. It holds the function `S -> Kind<F, StateTuple<S, A>>`.
* **`StateTKind<S, F, A>`**: The `Kind` representation for `StateT`, allowing it to be used with `higher-kinded-j`'s typeclasses like `Monad`. This is what you'll mostly interact with when using `StateT` in a generic monadic context.
* **`StateTKind.Witness<S, F>`**: The higher-kinded type witness for `StateT<S, F, _>`. Note that both the state type `S` and the underlying monad witness `F` are part of the `StateT` witness.
* **`StateTMonad<S, F>`**: The `Monad` instance for `StateT<S, F, _>`. It requires a `Monad` instance for the underlying monad `F` to function.
* **`StateTKindHelper`**: A utility class providing static methods for working with `StateTKind`, such as `narrow` (to convert `Kind<StateTKind.Witness<S, F>, A>` back to `StateT<S, F, A>`), `runStateT`, `evalStateT`, and `execStateT`.
* **`StateTuple<S, A>`**: A simple record-like class holding the pair `(S state, A value)`.

## Motivation: Why Use StateT?

Imagine you're processing a sequence of items, and for each item:

1. You need to update some running total (state).
2. The processing of an item might fail or return no result (e.g., `Optional`).

Without `StateT`, you might end up with deeply nested `Optional<StateTuple<S, A>>` and manually manage both the optionality and the state threading. `StateT<S, OptionalKind.Witness, A>` elegantly combines these concerns.

## Usage
~~~admonish example title="Example StateT"

- [StateTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/basic/trans/statet/StateTExample.java)
### Creating StateT Instances


You typically create `StateT` instances in a few ways:

1. **Directly with `StateT.create()`**:
   This is the most fundamental way, providing the state function and the underlying monad instance.

   ```java
 

   // Assume S = Integer (state type), F = OptionalKind.Witness, A = String (value type)
    OptionalMonad optionalMonad = new OptionalMonad() {};

    Function<Integer, Kind<OptionalKind.Witness, StateTuple<Integer, String>>> runFn =
        currentState -> {
          if (currentState < 0) {
            return OptionalKindHelper.wrap(Optional.empty());
          }
          return OptionalKindHelper.wrap(Optional.of(StateTuple.of(currentState + 1, "Value: " + currentState)));
        };

    StateT<Integer, OptionalKind.Witness, String> stateTExplicit =
        StateT.create(runFn, optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> stateTKind =
        stateTExplicit;
   ```
2. **Lifting values with `StateTMonad.of()`**:
   This lifts a pure value `A` into the `StateT` context. The state remains unchanged, and the underlying monad `F` will wrap the result using its own `of` method.

   ```java
    StateTMonad<Integer, OptionalKind.Witness> stateTMonad = StateTMonad.instance(optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> pureStateT =
        stateTMonad.of("pure value");

 
    Optional<StateTuple<Integer, String>> pureResult =
        OptionalKindHelper.unwrap(StateTKindHelper.runStateT(pureStateT, 10));
    System.out.println("Pure StateT result: " + pureResult);
   // When run with state 10, this will result in Optional.of(StateTuple(10, "pure value"))
   ```

### Running StateT Computations

To execute a `StateT` computation and extract the result, you use methods from `StateTKindHelper` or directly from the `StateT` object:

* **`runStateT(initialState)`**: Executes the computation with an `initialState` and returns the result wrapped in the underlying monad: `Kind<F, StateTuple<S, A>>`.

  ```java
  // Continuing the stateTKind from above:
  Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultOptionalTuple =
        StateTKindHelper.runStateT(stateTKind, 10);

    Optional<StateTuple<Integer, String>> actualOptional = OptionalKindHelper.unwrap(resultOptionalTuple);

    if (actualOptional.isPresent()) {
      StateTuple<Integer, String> tuple = actualOptional.get();
      System.out.println("New State (from stateTExplicit): " + tuple.state());
      System.out.println("Value (from stateTExplicit): " + tuple.value());
    } else {
      System.out.println("actualOptional was empty for initial state 10");
    }

    // Example with negative initial state (expecting empty Optional)
    Kind<OptionalKind.Witness, StateTuple<Integer, String>> resultEmptyOptional =
        StateTKindHelper.runStateT(stateTKind, -5);
    Optional<StateTuple<Integer, String>> actualEmpty = OptionalKindHelper.unwrap(resultEmptyOptional);
    // Output: Is empty: true
    System.out.println("Is empty (for initial state -5): " + actualEmpty.isEmpty());

  ```
* **`evalStateT(initialState)`**: Executes and gives you `Kind<F, A>` (the value, discarding the final state).
* **`execStateT(initialState)`**: Executes and gives you `Kind<F, S>` (the final state, discarding the value).

### Composing StateT Actions

Like any monad, `StateT` computations can be composed using `map` and `flatMap`.

* **`map(Function<A, B> fn)`**: Transforms the value `A` to `B` within the `StateT` context, leaving the state transformation logic and the underlying monad `F`'s effect untouched for that step.

  ```java
      Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> initialComputation =
        StateT.create(s -> OptionalKindHelper.wrap(Optional.of(StateTuple.of(s + 1, s * 2))), optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> mappedComputation =
        stateTMonad.map(
            val -> "Computed: " + val,
            initialComputation);

    // Run mappedComputation with initial state 5:
    // 1. initialComputation runs: state becomes 6, value is 10. Wrapped in Optional.
    // 2. map's function ("Computed: " + 10) is applied to 10.
    // Result: Optional.of(StateTuple(6, "Computed: 10"))
    Optional<StateTuple<Integer, String>> mappedResult =
        OptionalKindHelper.unwrap(StateTKindHelper.runStateT(mappedComputation, 5));
    System.out.print("Mapped result (initial state 5): ");
    mappedResult.ifPresentOrElse(System.out::println, () -> System.out.println("Empty"));
    // Output: StateTuple[state=6, value=Computed: 10]
  ```
* **`flatMap(Function<A, Kind<StateTKind.Witness<S, F>, B>> fn)`**: Sequences two `StateT` computations. The state from the first computation is passed to the second. The effects of the underlying monad `F` are also sequenced according to `F`'s `flatMap`.

  ```java
      // stateTMonad and optionalMonad are defined
    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Integer> firstStep =
        StateT.create(s -> OptionalKindHelper.wrap(Optional.of(StateTuple.of(s + 1, s * 10))), optionalMonad);

    Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>> secondStepFn =
        prevValue -> StateT.create(
            s -> {
              if (prevValue > 100) {
                return OptionalKindHelper.wrap(Optional.of(StateTuple.of(s + prevValue, "Large: " + prevValue)));
              } else {
                return OptionalKindHelper.wrap(Optional.empty());
              }
            },
            optionalMonad);

    Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String> combined =
        stateTMonad.flatMap(secondStepFn, firstStep);

    // Run with initial state 15
    // 1. firstStep(15): state=16, value=150. Wrapped in Optional.of.
    // 2. secondStepFn(150) is called. It returns a new StateT.
    // 3. The new StateT is run with state=16:
    //    Its function: s' (which is 16) -> Optional.of(StateTuple(16 + 150, "Large: 150"))
    //    Result: Optional.of(StateTuple(166, "Large: 150"))
    Optional<StateTuple<Integer, String>> combinedResult =
        OptionalKindHelper.unwrap(StateTKindHelper.runStateT(combined, 15));
    System.out.print("Combined result (initial state 15): ");
    combinedResult.ifPresentOrElse(System.out::println, () -> System.out.println("Empty"));

    // Output: StateTuple[state=166, value=Large: 150]

    // Run with initial state 5
    // 1. firstStep(5): state=6, value=50. Wrapped in Optional.of.
    // 2. secondStepFn(50) is called.
    // 3. The new StateT is run with state=6:
    //    Its function: s' (which is 6) -> Optional.empty()
    //    Result: Optional.empty()
    Optional<StateTuple<Integer, String>> combinedEmptyResult =
        OptionalKindHelper.unwrap(StateTKindHelper.runStateT(combined, 5));
    // Output: true
    System.out.println("Is empty from small initial (state 5 for combined): " + combinedEmptyResult.isEmpty());
  ```
~~~

### State-Specific Operations

While `higher-kinded-j`'s `StateT` provides the core monadic structure, you'll often want common state operations like `get`, `set`, `modify`. These can be constructed using `StateT.create` or `StateTKind.lift`.
~~~admonish example title="_get()_"
Retrieves the current state as the value.

  ```java
  public static <S, F> Kind<StateTKind.Witness<S, F>, S> get(Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, S>>> runFn = s -> monadF.of(StateTuple.of(s, s));
    return StateT.create(runFn, monadF);
  }
  // Usage: stateTMonad.flatMap(currentState -> ..., get(optionalMonad))
  ```
~~~

~~~admonish example title="_set(S newState)_"
Replaces the current state with `newState`. The value is often `Void` or `Unit`.

  ```java
  public static <S, F> Kind<StateTKind.Witness<S, F>, Void> set(S newState, Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, Void>>> runFn = s -> monadF.of(StateTuple.of(newState, (Void) null));
    return StateT.create(runFn, monadF);
  }
  ```
~~~

~~~admonish example title="_modify(Function<S, S> f)_"
  
Modifies the state using a function.

  ```java
  public static <S, F> Kind<StateTKind.Witness<S, F>, Void> modify(Function<S, S> f, Monad<F> monadF) {
    Function<S, Kind<F, StateTuple<S, Void>>> runFn = s -> monadF.of(StateTuple.of(f.apply(s), (Void) null));
    return StateT.create(runFn, monadF);
  }
  ```
 ~~~

~~~admonish example title="_gets(Function<S, A> f)_"
Retrieves a value derived from the current state.

```java
public static <S, F, A> Kind<StateTKind.Witness<S, F>, A> gets(Function<S, A> f, Monad<F> monadF) {
Function<S, Kind<F, StateTuple<S, A>>> runFn = s -> monadF.of(StateTuple.of(s, f.apply(s)));
return StateT.create(runFn, monadF);
}
  ```
~~~

~~~admonish example title="Example: Example: Simple Stack Operations with _StateT<List<Integer>, OptionalKind.Witness, ...>"

- [StateTStackExample.java](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/example/basic/trans/statet/StateTStackExample.java)

Let's simulate stack operations where the stack is a `List<Integer>` and operations might be absent if, for example, popping an empty stack.

```java
public class StateTStackExample {

  private static final OptionalMonad OPT_MONAD = new OptionalMonad();
  private static final StateTMonad<List<Integer>, OptionalKind.Witness> ST_OPT_MONAD =
      StateTMonad.instance(OPT_MONAD);

  // Helper to lift a state function into StateT<List<Integer>, OptionalKind.Witness, A>
  private static <A> Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, A> liftOpt(
      Function<List<Integer>, Kind<OptionalKind.Witness, StateTuple<List<Integer>, A>>> f) {
    return StateTKindHelper.stateT(f, OPT_MONAD);
  }

  // push operation
  public static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Void> push(Integer value) {
    return liftOpt(stack -> {
      List<Integer> newStack = new LinkedList<>(stack);
      newStack.add(0, value); // Add to front
      return OptionalKindHelper.wrap(Optional.of(StateTuple.of(newStack, null)));
    });
  }

  // pop operation
  public static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> pop() {
    return liftOpt(stack -> {
      if (stack.isEmpty()) {
        return OptionalKindHelper.wrap(Optional.empty()); // Cannot pop from empty stack
      }
      List<Integer> newStack = new LinkedList<>(stack);
      Integer poppedValue = newStack.remove(0);
      return OptionalKindHelper.wrap(Optional.of(StateTuple.of(newStack, poppedValue)));
    });
  }

  public static void main(String[] args) {
    Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> computation =
        ST_OPT_MONAD.flatMap(v1 -> // v1 is Void from push(10)
                ST_OPT_MONAD.flatMap(v2 -> // v2 is Void from push(20)
                        ST_OPT_MONAD.flatMap(popped1 -> // popped1 should be 20
                                ST_OPT_MONAD.flatMap(popped2 -> { // popped2 should be 10
                                  // Do something with popped1 and popped2
                                  System.out.println("Popped in order: " + popped1 + ", then " + popped2);
                                  // Final value of the computation
                                  return ST_OPT_MONAD.of(popped1 + popped2);
                                }, pop())
                            , pop())
                    , push(20))
            , push(10));


    List<Integer> initialStack = Collections.emptyList();
    Kind<OptionalKind.Witness, StateTuple<List<Integer>, Integer>> resultWrapped =
        StateTKindHelper.runStateT(computation, initialStack);

    Optional<StateTuple<List<Integer>, Integer>> resultOpt =
        OptionalKindHelper.unwrap(resultWrapped);

    resultOpt.ifPresentOrElse(
        tuple -> {
          System.out.println("Final value: " + tuple.value());       // Expected: 30
          System.out.println("Final stack: " + tuple.state());       // Expected: [] (empty)
        },
        () -> System.out.println("Computation resulted in empty Optional.")
    );

    // Example of popping an empty stack
    Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> popEmptyStack = pop();
    Optional<StateTuple<List<Integer>, Integer>> emptyPopResult =
        OptionalKindHelper.unwrap(StateTKindHelper.runStateT(popEmptyStack, Collections.emptyList()));
    System.out.println("Popping empty stack was successful: " + emptyPopResult.isPresent()); // false
  }
}
```
~~~

~~~ admonish important  title="Key Points:"

## Relationship to State Monad

The [State Monad](./state_monad.md) (`State<S, A>`) can be seen as a specialized version of `StateT`. Specifically, `State<S, A>` is equivalent to `StateT<S, Id, A>`, where `Id` is the Identity monad (a monad that doesn't add any effects, simply `Id<A> = A`). `higher-kinded-j` provides an `Id` monad. `State<S, A>` can be seen as an equivalent to `StateT<S, Id.Witness, A>`.

## Further Reading

* [State Monad](./state_monad.md): Understand the basics of stateful computations.
* [Monad Transformers](.transformers.md): General concept of monad transformers.
* Documentation for the underlying monads you might use with `StateT`, such as:
  * [OptionalKind](./optional_monad.md)
  * [EitherKind](./either_monad.md)
  * [IOKind](./io_monad.md)

Using `StateT`, helps write cleaner, more composable code when dealing with computations that involve both state and other monadic effects.

~~~