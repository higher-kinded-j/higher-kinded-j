// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.vstream;

import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamAlternative;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamMonad;
import org.higherkindedj.hkt.vstream.VStreamTraverse;

/**
 * Demonstrates VStream's HKT integration: type class instances, polymorphic programming, Foldable,
 * Traverse, and Alternative operations.
 *
 * <p>See <a href="https://higher-kinded-j.github.io/usage-guide.html">Usage Guide</a>
 */
public class VStreamHKTExample {

  public static void main(String[] args) {
    VStreamHKTExample example = new VStreamHKTExample();

    System.out.println("=== VStream Monad (flatMap via HKT) ===");
    example.monadFlatMap();

    System.out.println("\n=== Foldable (foldMap with Monoids) ===");
    example.foldableExample();

    System.out.println("\n=== Alternative (stream concatenation and guard) ===");
    example.alternativeExample();

    System.out.println("\n=== Polymorphic Function ===");
    example.polymorphicFunction();
  }

  /** Demonstrates VStreamMonad flatMap via the HKT interface. */
  void monadFlatMap() {
    VStreamMonad monad = VStreamMonad.INSTANCE;

    Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3));

    // Each element is expanded into a sub-stream
    Kind<VStreamKind.Witness, Integer> result =
        monad.flatMap(i -> VSTREAM.widen(VStream.of(i, i * 10)), stream);

    List<Integer> collected = VSTREAM.narrow(result).toList().run();
    System.out.println("flatMap expansion: " + collected);
    // Output: [1, 10, 2, 20, 3, 30]
  }

  /** Demonstrates VStreamTraverse (Foldable) with monoid-based aggregation. */
  void foldableExample() {
    VStreamTraverse foldable = VStreamTraverse.INSTANCE;

    Kind<VStreamKind.Witness, Integer> stream = VSTREAM.widen(VStream.of(1, 2, 3, 4, 5));

    // Sum using integer addition monoid
    Monoid<Integer> sumMonoid =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };

    Integer sum = foldable.foldMap(sumMonoid, Function.identity(), stream);
    System.out.println("Sum of 1..5: " + sum);
    // Output: 15

    // String concatenation monoid
    Kind<VStreamKind.Witness, String> words = VSTREAM.widen(VStream.of("Hello", " ", "World"));

    Monoid<String> stringMonoid =
        new Monoid<>() {
          @Override
          public String empty() {
            return "";
          }

          @Override
          public String combine(String a, String b) {
            return a + b;
          }
        };

    String joined = foldable.foldMap(stringMonoid, Function.identity(), words);
    System.out.println("Joined: " + joined);
    // Output: Hello World
  }

  /** Demonstrates VStreamAlternative for stream concatenation and guard filtering. */
  void alternativeExample() {
    VStreamAlternative alt = VStreamAlternative.INSTANCE;

    // Concatenation via orElse
    Kind<VStreamKind.Witness, Integer> a = VSTREAM.widen(VStream.of(1, 2));
    Kind<VStreamKind.Witness, Integer> b = VSTREAM.widen(VStream.of(3, 4));
    Kind<VStreamKind.Witness, Integer> combined = alt.orElse(a, () -> b);

    System.out.println("orElse concatenation: " + VSTREAM.narrow(combined).toList().run());
    // Output: [1, 2, 3, 4]

    // guard filtering
    Kind<VStreamKind.Witness, Unit> trueGuard = alt.guard(true);
    Kind<VStreamKind.Witness, Unit> falseGuard = alt.guard(false);

    System.out.println("guard(true): " + VSTREAM.narrow(trueGuard).toList().run());
    // Output: [Unit.INSTANCE]
    System.out.println("guard(false): " + VSTREAM.narrow(falseGuard).toList().run());
    // Output: []
  }

  /** Demonstrates writing a generic function parameterised by Monad that works with VStream. */
  void polymorphicFunction() {
    // A generic function that works with ANY Monad
    Kind<VStreamKind.Witness, String> result =
        greetAll(VStreamMonad.INSTANCE, VSTREAM.widen(VStream.of("Alice", "Bob", "Charlie")));

    System.out.println("Polymorphic greetAll: " + VSTREAM.narrow(result).toList().run());
    // Output: [Hello, Alice!, Hello, Bob!, Hello, Charlie!]
  }

  /** Generic function parameterised by Monad, usable with any monad type. */
  static <M extends WitnessArity<TypeArity.Unary>> Kind<M, String> greetAll(
      Monad<M> monad, Kind<M, String> names) {
    return monad.flatMap(name -> monad.of("Hello, " + name + "!"), names);
  }
}
