// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A company with a name, headquarters, and departments.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code CompanyLenses} with static lens
 * methods for each field.
 */
@GenerateLenses
public record Company(String name, Address headquarters, List<Department> departments) {}
