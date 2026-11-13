// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Machinery shared by the optics of hkj-api and hkj-core, and not part of the library's API.
 *
 * <p>The package is exported to hkj-core alone, so that the two modules build their results by one
 * rule rather than two copies of it. Code outside the library must not depend on it: it can change
 * in any release.
 */
@NullMarked
package org.higherkindedj.optics.internal;

import org.jspecify.annotations.NullMarked;
