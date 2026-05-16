// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic;

import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.writer.WriterKind;

/**
 * Demonstrates the uniform {@link Instances} facade for type-class lookup.
 *
 * <p>Instead of remembering whether a given type exposes its instance via a static {@code INSTANCE}
 * field, a generic {@code instance()} method, or an argument-taking constructor, every lookup has
 * the same shape: {@code Instances.x(...)}.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run -PmainClass=
 * org.higherkindedj.example.basic.InstancesFacadeExample}
 */
public final class InstancesFacadeExample {

  public static void main(String[] args) {
    new InstancesFacadeExample().run();
  }

  public void run() {
    zeroArgumentLookups();
    phantomTypedInference();
    argumentCarryingInstances();
  }

  private void zeroArgumentLookups() {
    System.out.println("--- Zero-argument lookups (one token, three levels) ---");

    // One canonical object, viewed at Functor / Applicative / Monad level.
    Functor<MaybeKind.Witness> functor = Instances.functor(maybe());
    Applicative<MaybeKind.Witness> applicative = Instances.applicative(maybe());
    Monad<MaybeKind.Witness> monad = Instances.monad(maybe());

    Kind<MaybeKind.Witness, Integer> value = MAYBE.widen(Maybe.just(21));
    Kind<MaybeKind.Witness, Integer> doubled = functor.map(n -> n * 2, value);

    System.out.println("functor.map  : " + MAYBE.narrow(doubled));
    System.out.println("applicative.of: " + MAYBE.narrow(applicative.of(99)));
    System.out.println(
        "monad.flatMap: "
            + MAYBE.narrow(monad.flatMap(n -> MAYBE.widen(Maybe.just(n + 1)), value)));
  }

  private void phantomTypedInference() {
    System.out.println("\n--- Phantom-typed inference ---");

    // <String> is inferred from the assignment target, exactly as
    // EitherMonad.<String>instance() would behave.
    Monad<EitherKind.Witness<String>> eitherMonad = Instances.monad(either());
    System.out.println("either() resolved to: " + eitherMonad.getClass().getSimpleName());
  }

  private void argumentCarryingInstances() {
    System.out.println("\n--- Argument-carrying instances (dependency in the signature) ---");

    // The compiler forces us to supply the Semigroup / Monoid / outer Monad.
    MonadError<ValidatedKind.Witness<String>, String> validated =
        Instances.validated(Semigroups.string());
    Monad<WriterKind.Witness<String>> writer = Instances.writer(Monoids.string());
    MonadError<
            EitherTKind.Witness<org.higherkindedj.hkt.optional.OptionalKind.Witness, String>,
            String>
        eitherT = Instances.eitherT(Instances.monad(optional()));

    System.out.println("validated: " + validated.getClass().getSimpleName());
    System.out.println("writer   : " + writer.getClass().getSimpleName());
    System.out.println("eitherT  : " + eitherT.getClass().getSimpleName());
  }
}
