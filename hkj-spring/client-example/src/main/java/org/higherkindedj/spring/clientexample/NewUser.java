// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.clientexample;

/**
 * The request body for creating a user, matching the remote service's {@code CreateUserRequest}.
 *
 * @param email the new user's email address
 * @param firstName the new user's first name
 * @param lastName the new user's last name
 */
public record NewUser(String email, String firstName, String lastName) {}
