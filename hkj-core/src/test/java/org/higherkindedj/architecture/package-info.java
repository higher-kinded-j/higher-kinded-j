// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Architecture tests for higher-kinded-j using ArchUnit.
 *
 * <p>This package contains architectural rules that enforce:
 *
 * <ul>
 *   <li>Module dependency constraints (API vs implementation separation)
 *   <li>Naming conventions (type class implementations, witness types, helpers)
 *   <li>Higher-kinded type patterns (witness types, Kind implementations)
 *   <li>Type class instance patterns (singleton, stateless)
 *   <li>Immutability requirements for functional types
 *   <li>Package structure organization
 * </ul>
 *
 * <p>These rules help maintain consistency and quality as the codebase grows.
 */
@NullMarked
package org.higherkindedj.architecture;

import org.jspecify.annotations.NullMarked;
