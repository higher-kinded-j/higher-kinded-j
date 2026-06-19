// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.client;

/**
 * A user as returned by the remote users service.
 *
 * @param id the user id
 * @param name the user's name
 */
public record UserDto(String id, String name) {}
