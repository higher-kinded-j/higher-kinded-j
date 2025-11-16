// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
/**
 * Demonstrates the Alternative type class through practical configuration loading examples.
 *
 * <p>The Alternative type class provides:
 *
 * <ul>
 *   <li><b>empty()</b> - Represents failure/absence
 *   <li><b>orElse()</b> - Combines alternatives with lazy evaluation
 *   <li><b>guard()</b> - Conditional success based on predicates
 *   <li><b>orElseAll()</b> - Chains multiple fallback sources
 * </ul>
 *
 * <p>Run {@link org.higherkindedj.example.basic.alternative.AlternativeConfigExample} to see
 * Alternative in action with configuration loading fallback chains.
 *
 * @see org.higherkindedj.hkt.Alternative
 * @see org.higherkindedj.example.basic.alternative.AlternativeConfigExample
 */
package org.higherkindedj.example.basic.alternative;
