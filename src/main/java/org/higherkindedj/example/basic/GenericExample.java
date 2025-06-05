// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;

/** see {<a href="https://higher-kinded-j.github.io/usage=guide.html">Usage Guide</a>} */
public class GenericExample {

  // Generic function: Doubles the number inside any Functor context F_WITNESS.
  // Requires the specific Functor<F_WITNESS> instance to be passed in.
  public static <F_WITNESS, A, B> Kind<F_WITNESS, B> mapWithFunctor(
      Functor<F_WITNESS> functorInstance, // Pass the type class instance for F_WITNESS
      Function<A, B> fn,
      Kind<F_WITNESS, A> kindABox) { // The value wrapped in Kind

    // Use the map method from the provided Functor instance
    return functorInstance.map(fn, kindABox);
  }

  public static void main(String[] args) {
    GenericExample example = new GenericExample();
    example.genricExample();
    example.handlingUnwrapExceptions();
  }

  public void basicUsageExample() {
    Optional<String> myOptional = Optional.of("test");

    // Wrap it into the Higher-Kinded-J Kind type
    // F_WITNESS here is OptionalKind.Witness
    Kind<OptionalKind.Witness, String> optionalKind = OPTIONAL.widen(myOptional);
    OptionalMonad optionalMonad = new OptionalMonad();
    // --- Using map ---
    Function<String, Integer> lengthFunc = String::length;
    // Apply map using the monad instance
    Kind<OptionalKind.Witness, Integer> lengthKind = optionalMonad.map(lengthFunc, optionalKind);
    // lengthKind now represents Kind<OptionalKind.Witness, Integer> containing Optional.of(4) if
    // "test"

    // --- Using flatMap ---
    // Function A -> Kind<F_WITNESS, B>
    Function<Integer, Kind<OptionalKind.Witness, String>> checkLength =
        len -> OPTIONAL.widen(len > 3 ? Optional.of("Long enough") : Optional.empty());
    // Apply flatMap using the monad instance
    Kind<OptionalKind.Witness, String> checkedKind = optionalMonad.flatMap(checkLength, lengthKind);
    // checkedKind now represents Kind<OptionalKind.Witness, String> containing Optional.of("Long
    // enough")

    // --- Using MonadError (for Optional, error type is Void) ---
    Kind<OptionalKind.Witness, String> emptyKind =
        optionalMonad.raiseError(null); // Represents Optional.empty()
    // Handle the empty case (error state) using handleError
    Kind<OptionalKind.Witness, String> handledKind =
        optionalMonad.handleError(
            emptyKind, ignoredError -> "Default Value" // Provide a default value
            );

    // Continuing the Optional example:
    Optional<String> finalOptional = OPTIONAL.narrow(checkedKind);
    System.out.println("Final Optional: " + finalOptional); // Output: Optional[Long enough]

    Optional<String> handledOptional = OPTIONAL.narrow(handledKind);
    System.out.println("Handled Optional: " + handledOptional); // Output: Optional[Default Value]

    // Example for IO:
    IOMonad ioMonad = new IOMonad();
    Kind<IOKind.Witness, String> ioKind = IO_OP.delay(() -> "Hello from IO!");
    String ioResult = IO_OP.unsafeRunSync(ioKind); // unsafeRunSync is specific to IOKindHelper
    System.out.println(ioResult);
  }

  public void handlingUnwrapExceptions() {
    try {
      // ERROR: Attempting to unwrap null
      Optional<String> result = OPTIONAL.narrow(null);
    } catch (KindUnwrapException e) {
      System.err.println("Higher-Kinded-J Usage Error: " + e.getMessage());
      // Usage Error: Cannot unwrap null Kind for Optional
    }
  }

  public void genricExample() {
    // Get instances of the type classes for the specific types (F_WITNESS) we want to use
    ListMonad listMonad = ListMonad.INSTANCE; // Implements Functor<ListKind.Witness>
    OptionalMonad optionalMonad = new OptionalMonad(); // Implements Functor<OptionalKind.Witness>

    Function<Integer, Integer> doubleFn = x -> x * 2;

    // --- Use with List ---
    List<Integer> nums = List.of(1, 2, 3);
    // Wrap the List. F_WITNESS is ListKind.Witness
    Kind<ListKind.Witness, Integer> listKind = LIST.widen(nums);
    // Call the generic function, passing the ListMonad instance and the wrapped List
    Kind<ListKind.Witness, Integer> doubledListKind = mapWithFunctor(listMonad, doubleFn, listKind);
    System.out.println("Doubled List: " + LIST.narrow(doubledListKind)); // Output: [2, 4, 6]

    // --- Use with Optional (Present) ---
    Optional<Integer> optNum = Optional.of(10);
    // Wrap the Optional. F_WITNESS is OptionalKind.Witness
    Kind<OptionalKind.Witness, Integer> optKind = OPTIONAL.widen(optNum);
    // Call the generic function, passing the OptionalMonad instance and the wrapped Optional
    Kind<OptionalKind.Witness, Integer> doubledOptKind =
        mapWithFunctor(optionalMonad, doubleFn, optKind);
    System.out.println(
        "Doubled Optional: " + OPTIONAL.narrow(doubledOptKind)); // Output: Optional[20]

    // --- Use with Optional (Empty) ---
    Optional<Integer> emptyOpt = Optional.empty();
    Kind<OptionalKind.Witness, Integer> emptyOptKind = OPTIONAL.widen(emptyOpt);
    // Call the generic function, map does nothing on empty
    Kind<OptionalKind.Witness, Integer> doubledEmptyOptKind =
        mapWithFunctor(optionalMonad, doubleFn, emptyOptKind);
    System.out.println(
        "Doubled Empty Optional: "
            + OPTIONAL.narrow(doubledEmptyOptKind)); // Output: Optional.empty
  }
}
