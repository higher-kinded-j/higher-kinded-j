// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** Represents a user profile with settings and addresses. */
@GenerateLenses
@GenerateTraversals
public record UserProfile(
    String userId,
    String username,
    String email,
    ProfileSettings settings,
    List<UserAddress> addresses) {}
