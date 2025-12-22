// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service.impl;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.example.order.model.WarehouseInfo;
import org.higherkindedj.example.order.service.WarehouseService;

/** In-memory implementation of WarehouseService for testing and examples. */
public class InMemoryWarehouseService implements WarehouseService {

  private final Map<String, WarehouseInfo> warehouses = new ConcurrentHashMap<>();

  public InMemoryWarehouseService() {
    // Pre-populate with sample warehouses
    warehouses.put(
        "WAREHOUSE-UK-01",
        WarehouseInfo.uk("WAREHOUSE-UK-01", "UK Distribution Centre", new BigDecimal("3.99")));

    warehouses.put(
        "WAREHOUSE-UK-02",
        WarehouseInfo.uk("WAREHOUSE-UK-02", "UK Secondary Warehouse", new BigDecimal("4.99")));

    warehouses.put(
        "WAREHOUSE-EU-01",
        WarehouseInfo.eu(
            "WAREHOUSE-EU-01", "EU Distribution Centre (Netherlands)", new BigDecimal("7.99")));

    warehouses.put(
        "WAREHOUSE-INTL-01",
        WarehouseInfo.international(
            "WAREHOUSE-INTL-01", "US Distribution Centre", "USA", new BigDecimal("14.99")));
  }

  /**
   * Adds a warehouse to the service.
   *
   * @param warehouse the warehouse to add
   */
  public void addWarehouse(WarehouseInfo warehouse) {
    warehouses.put(warehouse.warehouseId(), warehouse);
  }

  @Override
  public Optional<WarehouseInfo> getWarehouse(String warehouseId) {
    return Optional.ofNullable(warehouses.get(warehouseId));
  }
}
