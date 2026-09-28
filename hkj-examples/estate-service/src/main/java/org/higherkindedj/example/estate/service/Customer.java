// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.service;

import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.example.estate.api.EmailAddress;

/** The service's customer: an id, a name, a checked email, an optional nickname and an address. */
public record Customer(
    UUID id, String name, EmailAddress email, Optional<String> nickname, Address address) {}
