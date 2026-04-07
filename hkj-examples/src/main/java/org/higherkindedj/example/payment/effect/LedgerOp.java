// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.effect;

import java.util.function.Function;
import org.higherkindedj.example.payment.model.CustomerId;
import org.higherkindedj.example.payment.model.LedgerEntry;
import org.higherkindedj.example.payment.model.Money;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.jspecify.annotations.NullMarked;

/**
 * Effect algebra for accounting ledger operations.
 *
 * <p>Uses continuation-passing style: each operation carries a {@code Function} from its natural
 * result type to {@code A}, enabling proper type inference at call sites.
 *
 * @param <A> the result type (constrained by the continuation)
 */
@NullMarked
@EffectAlgebra
public sealed interface LedgerOp<A> permits LedgerOp.RecordEntry, LedgerOp.GetBalance {

  /** Maps a function over the result type. */
  <B> LedgerOp<B> mapK(Function<? super A, ? extends B> f);

  /**
   * Record a ledger entry for a completed transaction.
   *
   * @param entry the ledger entry to record
   * @param k continuation from {@link LedgerEntry} to {@code A}
   * @param <A> the result type
   */
  record RecordEntry<A>(LedgerEntry entry, Function<LedgerEntry, A> k) implements LedgerOp<A> {
    @Override
    public <B> LedgerOp<B> mapK(Function<? super A, ? extends B> f) {
      return new RecordEntry<>(entry, k.andThen(f));
    }
  }

  /**
   * Query the current balance for an account.
   *
   * @param accountId the account to query
   * @param k continuation from {@link Money} to {@code A}
   * @param <A> the result type
   */
  record GetBalance<A>(CustomerId accountId, Function<Money, A> k) implements LedgerOp<A> {
    @Override
    public <B> LedgerOp<B> mapK(Function<? super A, ? extends B> f) {
      return new GetBalance<>(accountId, k.andThen(f));
    }
  }
}
