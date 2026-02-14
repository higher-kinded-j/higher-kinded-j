// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.spi;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;

/**
 * Test PathProvider implementation for Optional to enable SPI discovery testing.
 *
 * <p>This provider is registered via META-INF/services to allow testing of PathRegistry's
 * ServiceLoader-based discovery mechanism.
 */
public class TestOptionalPathProvider implements PathProvider<OptionalKind.Witness> {

  @Override
  public Class<?> witnessType() {
    return OptionalKind.Witness.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <A> Chainable<A> createPath(Kind<OptionalKind.Witness, A> kind) {
    Optional<A> optional = OptionalKindHelper.OPTIONAL.narrow(kind);
    return (Chainable<A>) Path.optional(optional);
  }

  @Override
  public Monad<OptionalKind.Witness> monad() {
    return OptionalMonad.INSTANCE;
  }
}
