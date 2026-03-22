// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

/**
 * Request DTO for creating users. Used by both UserController and ValidationController.
 *
 * @param email the user's email address
 * @param firstName the user's first name
 * @param lastName the user's last name
 */
public record CreateUserRequest(String email, String firstName, String lastName) {}
