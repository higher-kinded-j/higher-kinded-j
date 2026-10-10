// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.util.List;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * The account a customer of the order service signs in to: the chapter's {@link Customer}, the
 * account's settings, and the addresses it has saved.
 */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
@GenerateTraversals(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record CustomerAccount(
    String accountId, Customer customer, ProfileSettings settings, List<Address> addresses) {}
