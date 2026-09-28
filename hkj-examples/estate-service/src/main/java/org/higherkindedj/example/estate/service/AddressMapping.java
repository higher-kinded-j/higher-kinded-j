// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.estate.service;

import org.higherkindedj.example.estate.clients.AddressBean;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;

/** The address against the clients module's Lombok bean: every component copies. */
@GenerateMapping
public interface AddressMapping extends MappingSpec<Address, AddressBean> {}
