// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.json;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;

/** Shared contextual-type resolution for the HKJ deserialisers. */
final class HkjTypeResolution {

  private HkjTypeResolution() {}

  /**
   * Resolves the declared generic type for {@code rawClass} at the current binding site.
   *
   * <p>The <strong>contextual fallback</strong> is what makes nested binding work: for a collection
   * or map <em>element</em> (the property is the {@code List<Either<E, A>>} field, whose raw class
   * never matches) and for root-level {@code TypeReference} reads (no property at all), Jackson
   * pushes the element/root type as {@code ctxt.getContextualType()}. Without it those sites bind
   * to {@code Object} — the classic cause of {@code Right(LinkedHashMap)} instead of {@code
   * Right(User)}.
   *
   * <p>The property type is checked first only as a stable, precise source when it is present and
   * already matches; in practice it agrees with the contextual type (Jackson pushes the property
   * type as contextual before contextualising), so the ordering is not load-bearing — the fallback
   * is. Both are kept for clarity.
   *
   * @param ctxt the deserialisation context
   * @param property the bean property, if any
   * @param rawClass the HKJ type being deserialised
   * @return the matching parameterised type, or {@code null} when unresolvable (raw read)
   */
  static @Nullable JavaType resolveTargetType(
      DeserializationContext ctxt, @Nullable BeanProperty property, Class<?> rawClass) {
    if (property != null && property.getType().hasRawClass(rawClass)) {
      return property.getType();
    }
    JavaType contextual = ctxt.getContextualType();
    if (contextual != null && contextual.hasRawClass(rawClass)) {
      return contextual;
    }
    return null;
  }
}
