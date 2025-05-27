// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.trans.readert;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.trans.reader_t.ReaderT;
import org.higherkindedj.hkt.trans.reader_t.ReaderTKindHelper;

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
    Monad<OptionalKind.Witness> optMonad = new OptionalMonad();

    // Environment Type R
    record Config(String setting) {}
    Config testConfig = new Config("TestValue");

    // --- Factory Methods ---

    // 1. `ReaderT.of(Function<R, Kind<F, A>> runFunction)`
    //    Constructs directly from the R -> F<A> function.
    Function<Config, Kind<OptionalKind.Witness, String>> runFn1 =
        cfg -> OptionalKindHelper.wrap(Optional.of("Data based on " + cfg.setting()));
    ReaderT<OptionalKind.Witness, Config, String> rt1 = ReaderT.of(runFn1);
    // To run: OptionalKindHelper.unwrap(rt1.run().apply(testConfig)) is Optional.of("Data based on
    // TestValue")
    System.out.println(OptionalKindHelper.unwrap(rt1.run().apply(testConfig)));

    // 2. `ReaderT.lift(Monad<F> outerMonad, Kind<F, A> fa)`
    //    Lifts an existing monadic value `Kind<F, A>` into ReaderT.
    //    The resulting ReaderT ignores the environment R and always returns `fa`.
    Kind<OptionalKind.Witness, Integer> optionalValue = OptionalKindHelper.wrap(Optional.of(123));
    ReaderT<OptionalKind.Witness, Config, Integer> rt2 = ReaderT.lift(optMonad, optionalValue);
    // To run: OptionalKindHelper.unwrap(rt2.run().apply(testConfig)) is Optional.of(123)
    System.out.println(OptionalKindHelper.unwrap(rt2.run().apply(testConfig)));

    Kind<OptionalKind.Witness, Integer> emptyOptional = OptionalKindHelper.wrap(Optional.empty());
    ReaderT<OptionalKind.Witness, Config, Integer> rt2Empty = ReaderT.lift(optMonad, emptyOptional);
    // To run: OptionalKindHelper.unwrap(rt2Empty.run().apply(testConfig)) is Optional.empty()

    // 3. `ReaderT.reader(Monad<F> outerMonad, Function<R, A> f)`
    //    Creates a ReaderT from a function R -> A. The result A is then lifted into F using
    // outerMonad.of(A).
    Function<Config, String> simpleReaderFn = cfg -> "Hello from " + cfg.setting();
    ReaderT<OptionalKind.Witness, Config, String> rt3 = ReaderT.reader(optMonad, simpleReaderFn);
    // To run: OptionalKindHelper.unwrap(rt3.run().apply(testConfig)) is Optional.of("Hello from
    // TestValue")
    System.out.println(OptionalKindHelper.unwrap(rt3.run().apply(testConfig)));

    // 4. `ReaderT.ask(Monad<F> outerMonad)`
    //    Creates a ReaderT that, when run, provides the environment R itself as the result, lifted
    // into F.
    //    The function is r -> outerMonad.of(r).
    ReaderT<OptionalKind.Witness, Config, Config> rt4 = ReaderT.ask(optMonad);
    // To run: OptionalKindHelper.unwrap(rt4.run().apply(testConfig)) is Optional.of(new
    // Config("TestValue"))
    System.out.println(OptionalKindHelper.unwrap(rt4.run().apply(testConfig)));

    // --- Using ReaderTKindHelper to wrap/unwrap for Monad operations ---
    //    Avoid a cast with var ReaderTKind<OptionalKind.Witness, Config, String> kindRt1 =
    //        (ReaderTKind<OptionalKind.Witness, Config, String>) ReaderTKindHelper.wrap(rt1);
    var kindRt1 = ReaderTKindHelper.wrap(rt1);
    ReaderT<OptionalKind.Witness, Config, String> unwrappedRt1 = ReaderTKindHelper.unwrap(kindRt1);
  }
}
