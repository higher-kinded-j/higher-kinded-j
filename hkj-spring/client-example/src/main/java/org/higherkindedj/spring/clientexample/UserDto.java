// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.clientexample;

/**
 * A user as returned by the remote users service (the {@code HkjSpringExampleApplication} server).
 *
 * <p>The fields mirror that service's {@code User} record, so a 2xx body deserialises directly. A
 * concrete type like this binds with Jackson with no extra annotations.
 *
 * @param id the user id
 * @param email the user's email address
 * @param firstName the user's first name
 * @param lastName the user's last name
 */
public record UserDto(String id, String email, String firstName, String lastName) {}
