// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import java.util.Optional;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;

/** Service for warehouse operations, supporting split shipments. */
@GeneratePathBridge
public interface WarehouseService {

  /**
   * Retrieves warehouse information by ID.
   *
   * @param warehouseId the warehouse identifier
   * @return the warehouse information if found
   */
  @PathVia(doc = "Looks up warehouse information for split shipment processing")
  Optional<WarehouseInfo> getWarehouse(String warehouseId);
}
