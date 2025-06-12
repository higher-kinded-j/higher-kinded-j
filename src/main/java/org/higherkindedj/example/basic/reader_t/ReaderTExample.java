// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.reader_t;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;

/**
 * see {<a href="https://higher-kinded-j.github.io/readert_transformer.html">ReaderT
 * Transformer</a>}
 */
public class ReaderTExample {

  public static void main(String[] args) {
    ReaderTExample example = new ReaderTExample();
    example.createExample();
  }

  public void createExample() {
    // --- Setup ---
    // Outer Monad F = OptionalKind.Witness
    OptionalMonad optMonad = OptionalMonad.INSTANCE;

    // Environment Type R
    record Config(String setting) {}
    Config testConfig = new Config("TestValue");

    // --- Factory Methods ---

    // 1. `ReaderT.of(Function<R, Kind<F, A>> runFunction)`
    //    Constructs directly from the R -> F<A> function.
    Function<Config, Kind<OptionalKind.Witness, String>> runFn1 =
        cfg -> OPTIONAL.widen(Optional.of("Data based on " + cfg.setting()));
    ReaderT<OptionalKind.Witness, Config, String> rt1 = ReaderT.of(runFn1);
    // To run: OPTIONAL.narrow(rt1.run().apply(testConfig)) is Optional.of("Data based on
    // TestValue")
    System.out.println(OPTIONAL.narrow(rt1.run().apply(testConfig)));

    // 2. `ReaderT.lift(Monad<F> outerMonad, Kind<F, A> fa)`
    //    Lifts an existing monadic value `Kind<F, A>` into ReaderT.
    //    The resulting ReaderT ignores the environment R and always returns `fa`.
    Kind<OptionalKind.Witness, Integer> optionalValue = OPTIONAL.widen(Optional.of(123));
    ReaderT<OptionalKind.Witness, Config, Integer> rt2 = ReaderT.lift(optMonad, optionalValue);
    // To run: OPTIONAL.narrow(rt2.run().apply(testConfig)) is Optional.of(123)
    System.out.println(OPTIONAL.narrow(rt2.run().apply(testConfig)));

    Kind<OptionalKind.Witness, Integer> emptyOptional = OPTIONAL.widen(Optional.empty());
    ReaderT<OptionalKind.Witness, Config, Integer> rt2Empty = ReaderT.lift(optMonad, emptyOptional);
    // To run: OPTIONAL.narrow(rt2Empty.run().apply(testConfig)) is Optional.empty()

    // 3. `ReaderT.reader(Monad<F> outerMonad, Function<R, A> f)`
    //    Creates a ReaderT from a function R -> A. The result A is then lifted into F using
    // outerMonad.of(A).
    Function<Config, String> simpleReaderFn = cfg -> "Hello from " + cfg.setting();
    ReaderT<OptionalKind.Witness, Config, String> rt3 = ReaderT.reader(optMonad, simpleReaderFn);
    // To run: OPTIONAL.narrow(rt3.run().apply(testConfig)) is Optional.of("Hello from
    // TestValue")
    System.out.println(OPTIONAL.narrow(rt3.run().apply(testConfig)));

    // 4. `ReaderT.ask(Monad<F> outerMonad)`
    //    Creates a ReaderT that, when run, provides the environment R itself as the result, lifted
    // into F.
    //    The function is r -> outerMonad.of(r).
    ReaderT<OptionalKind.Witness, Config, Config> rt4 = ReaderT.ask(optMonad);
    // To run: OPTIONAL.narrow(rt4.run().apply(testConfig)) is Optional.of(new
    // Config("TestValue"))
    System.out.println(OPTIONAL.narrow(rt4.run().apply(testConfig)));

    // --- Using ReaderTKindHelper.READER_T to wrap/unwrap for Monad operations ---
    //    Avoid a cast with var ReaderTKind<OptionalKind.Witness, Config, String> kindRt1 =
    //        (ReaderTKind<OptionalKind.Witness, Config, String>) READER_T.widen(rt1);
    var kindRt1 = READER_T.widen(rt1);
    ReaderT<OptionalKind.Witness, Config, String> unwrappedRt1 = READER_T.narrow(kindRt1);
  }
}
