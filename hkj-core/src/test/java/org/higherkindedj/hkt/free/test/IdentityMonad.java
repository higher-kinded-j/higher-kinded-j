// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.test;

import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * Monad instance for Identity.
 *
 * <p>This is a simple monad implementation for testing purposes.
 */
public class IdentityMonad implements Monad<IdentityKind.Witness> {

  /** Singleton instance. */
  public static final IdentityMonad INSTANCE = new IdentityMonad();

  private IdentityMonad() {}

  @Override
  public <A> Kind<IdentityKind.Witness, A> of(A value) {
    return IDENTITY.widen(new Identity<>(value));
  }

  @Override
  public <A, B> Kind<IdentityKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<IdentityKind.Witness, A> fa) {
    Identity<A> identity = IDENTITY.narrow(fa);
    return IDENTITY.widen(identity.map(f));
  }

  @Override
  public <A, B> Kind<IdentityKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<IdentityKind.Witness, B>> f,
      Kind<IdentityKind.Witness, A> ma) {
    Identity<A> identity = IDENTITY.narrow(ma);
    Identity<B> result = identity.flatMap(a -> IDENTITY.narrow(f.apply(a)));
    return IDENTITY.widen(result);
  }

  @Override
  public <A, B> Kind<IdentityKind.Witness, B> ap(
      Kind<IdentityKind.Witness, ? extends Function<A, B>> ff, Kind<IdentityKind.Witness, A> fa) {
    Identity<? extends Function<A, B>> identityF = IDENTITY.narrow(ff);
    Identity<A> identityA = IDENTITY.narrow(fa);
    return IDENTITY.widen(identityA.map(identityF.value()));
  }
}
