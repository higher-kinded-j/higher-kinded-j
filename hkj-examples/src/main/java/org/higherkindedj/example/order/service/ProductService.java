// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.service;

import java.util.List;
import org.higherkindedj.example.order.error.OrderError;
import org.higherkindedj.example.order.model.Product;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.hkt.either.Either;

/** Service for product catalogue operations. */
@GeneratePathBridge
public interface ProductService {

  /**
   * Looks up a product by its ID.
   *
   * @param productId the product identifier
   * @return either an error or the product details
   */
  @PathVia(doc = "Looks up product details from the catalogue")
  Either<OrderError, Product> findById(ProductId productId);

  /**
   * Looks up multiple products by their IDs.
   *
   * @param productIds the product identifiers
   * @return either an error or the list of products
   */
  @PathVia(doc = "Looks up multiple products from the catalogue")
  Either<OrderError, List<Product>> findByIds(List<ProductId> productIds);

  /**
   * Checks if a product is currently available for purchase.
   *
   * @param productId the product identifier
   * @return either an error or true if available
   */
  @PathVia(doc = "Checks product availability")
  Either<OrderError, Boolean> isAvailable(ProductId productId);
}
