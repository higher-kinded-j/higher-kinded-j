// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

import org.higherkindedj.example.order.model.value.Money;
import org.higherkindedj.example.order.model.value.ProductId;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Product details retrieved from the product catalogue.
 *
 * @param id the product identifier
 * @param name the product name
 * @param description product description
 * @param price the unit price
 * @param category product category
 * @param inStock whether the product is currently available
 */
@GenerateLenses
@GenerateFocus
public record Product(
    ProductId id, String name, String description, Money price, String category, boolean inStock) {}
