// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

/**
 * Tutorial 27 domain: what a guest has told the hotel about contacting them. The opt-in is a {@code
 * boolean}, since a stored guest always has one; only the PATCH beans box it, so that a property
 * the request leaves out can answer {@code null}.
 */
public record GuestPreferences(String language, boolean marketingOptIn) {}
