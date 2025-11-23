// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.domain;

import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * User domain model. {@literal @}GenerateLenses automatically generates UserLenses class with
 * lenses for each field.
 */
@GenerateLenses
public record User(String id, String email, String firstName, String lastName) {}
