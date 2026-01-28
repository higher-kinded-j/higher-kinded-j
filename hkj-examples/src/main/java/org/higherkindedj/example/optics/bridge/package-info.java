/**
 * Focus DSL bridging examples demonstrating how to compose Focus paths with spec interface optics
 * for external library types.
 *
 * <p>This package shows how to:
 *
 * <ul>
 *   <li>Create spec interfaces for Immutables-style value objects
 *   <li>Bridge Focus DSL navigation into external types
 *   <li>Maintain IDE discoverability across library boundaries
 *   <li>Build domain-specific optics that cross type boundaries
 * </ul>
 *
 * <p>The external types in the {@code external} subpackage simulate Immutables-generated classes
 * with builders and wither methods. Real Immutables classes work identically.
 *
 * @see org.higherkindedj.example.optics.bridge.external.Address
 * @see org.higherkindedj.example.optics.bridge.FocusBridgingExample
 */
package org.higherkindedj.example.optics.bridge;
