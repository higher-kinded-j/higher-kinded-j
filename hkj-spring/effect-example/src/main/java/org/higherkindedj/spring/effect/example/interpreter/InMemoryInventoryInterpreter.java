// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.effect.example.interpreter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.spring.autoconfigure.effect.Interpreter;
import org.higherkindedj.spring.effect.example.effect.InventoryOp;
import org.higherkindedj.spring.effect.example.effect.InventoryOpKind;
import org.higherkindedj.spring.effect.example.effect.InventoryOpKindHelper;

/**
 * In-memory interpreter for {@link InventoryOp} targeting the IO monad.
 *
 * <p>Manages stock levels in a concurrent map. Items not in the map are assumed to have 100 units.
 */
@Interpreter(InventoryOp.class)
public class InMemoryInventoryInterpreter
    implements Natural<InventoryOpKind.Witness, IOKind.Witness> {

  private final ConcurrentMap<String, Integer> stock = new ConcurrentHashMap<>();

  /** Default stock for unknown items. */
  private static final int DEFAULT_STOCK = 100;

  @Override
  @SuppressWarnings("unchecked")
  public <A> Kind<IOKind.Witness, A> apply(Kind<InventoryOpKind.Witness, A> fa) {
    InventoryOp<A> op = InventoryOpKindHelper.INVENTORY_OP.narrow(fa);
    return switch (op) {
      case InventoryOp.CheckStock<A> check ->
          IOKindHelper.IO_OP.widen(
              IO.delay(
                  () -> {
                    int available = stock.getOrDefault(check.itemId(), DEFAULT_STOCK);
                    return check.k().apply(available);
                  }));
      case InventoryOp.Reserve<A> reserve ->
          IOKindHelper.IO_OP.widen(
              IO.delay(
                  () -> {
                    int available = stock.getOrDefault(reserve.itemId(), DEFAULT_STOCK);
                    if (available >= reserve.quantity()) {
                      stock.put(reserve.itemId(), available - reserve.quantity());
                      return reserve.k().apply(true);
                    }
                    return reserve.k().apply(false);
                  }));
    };
  }

  /** Set stock level for testing. */
  public void setStock(String itemId, int quantity) {
    stock.put(itemId, quantity);
  }
}
