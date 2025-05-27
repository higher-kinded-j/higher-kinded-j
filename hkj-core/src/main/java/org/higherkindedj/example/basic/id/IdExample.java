// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.id;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdentityMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trans.state_t.StateTKind;
import org.higherkindedj.hkt.trans.state_t.StateTKindHelper;
import org.higherkindedj.hkt.trans.state_t.StateTMonad;

/** see {<a href="https://higher-kinded-j.github.io/identity.html">Identity Monad</a>} */
public class IdExample {
  public static void main(String[] args) {
    IdExample idExample = new IdExample();
    idExample.createExample();
    idExample.monadExample();
    idExample.transformerExample();
  }

  public void createExample() {
    // Direct creation
    Id<String> idString = Id.of("Hello, Identity!");
    Id<Integer> idInt = Id.of(123);
    Id<String> idNull = Id.of(null); // Id can wrap null

    // Accessing the value
    String value = idString.value(); // "Hello, Identity!"
    Integer intValue = idInt.value(); // 123
    String nullValue = idNull.value(); // null
  }

  public void monadExample() {
    IdentityMonad idMonad = IdentityMonad.instance();

    // 1. 'of' (lifting a value)
    Kind<Id.Witness, Integer> kindInt = idMonad.of(42);
    Id<Integer> idFromOf = IdKindHelper.narrow(kindInt);
    System.out.println("From of: " + idFromOf.value()); // Output: From of: 42

    // 2. 'map' (applying a function to the wrapped value)
    Kind<Id.Witness, String> kindStringMapped = idMonad.map(i -> "Value is " + i, kindInt);
    Id<String> idMapped = IdKindHelper.narrow(kindStringMapped);
    System.out.println("Mapped: " + idMapped.value()); // Output: Mapped: Value is 42

    // 3. 'flatMap' (applying a function that returns an Id)
    Kind<Id.Witness, String> kindStringFlatMapped =
        idMonad.flatMap(
            i -> Id.of("FlatMapped: " + (i * 2)), // Function returns Id<String>
            kindInt);
    Id<String> idFlatMapped = IdKindHelper.narrow(kindStringFlatMapped);
    System.out.println("FlatMapped: " + idFlatMapped.value()); // Output: FlatMapped: 84

    // flatMap can also be called directly on Id if the function returns Id
    Id<String> directFlatMap = idFromOf.flatMap(i -> Id.of("Direct FlatMap: " + i));
    System.out.println(directFlatMap.value()); // Output: Direct FlatMap: 42

    // 4. 'ap' (applicative apply)
    Kind<Id.Witness, Function<Integer, String>> kindFunction = idMonad.of(i -> "Applied: " + i);
    Kind<Id.Witness, String> kindApplied = idMonad.ap(kindFunction, kindInt);
    Id<String> idApplied = IdKindHelper.narrow(kindApplied);
    System.out.println("Applied: " + idApplied.value()); // Output: Applied: 42
  }

  public void transformerExample() {
    // Conceptually, State<S, A> is StateT<S, Id.Witness, A>
    // We can create a StateTMonad instance using IdentityMonad as the underlying monad.
    StateTMonad<Integer, Id.Witness> stateMonadOverId =
        StateTMonad.instance(IdentityMonad.instance());

    // Example: A "State" computation that increments the state and returns the old state
    Function<Integer, Kind<Id.Witness, StateTuple<Integer, Integer>>> runStateFn =
        currentState -> Id.of(StateTuple.of(currentState + 1, currentState));

    // Create the StateT (acting as State)
    Kind<StateTKind.Witness<Integer, Id.Witness>, Integer> incrementAndGet =
        StateTKindHelper.stateT(runStateFn, IdentityMonad.instance());

    // Run it
    Integer initialState = 10;
    Kind<Id.Witness, StateTuple<Integer, Integer>> resultIdTuple =
        StateTKindHelper.runStateT(incrementAndGet, initialState);

    // Unwrap the Id and then the StateTuple
    Id<StateTuple<Integer, Integer>> idTuple = IdKindHelper.narrow(resultIdTuple);
    StateTuple<Integer, Integer> tuple = idTuple.value();

    System.out.println("Initial State: " + initialState); // Output: Initial State: 10
    System.out.println(
        "Returned Value (Old State): " + tuple.value()); // Output: Returned Value (Old State): 10
    System.out.println("Final State: " + tuple.state()); // Output: Final State: 11
  }
}
