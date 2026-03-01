// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.spi.PathProvider;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamKindHelper;
import org.higherkindedj.hkt.vstream.VStreamMonad;

/**
 * PathProvider SPI implementation for {@link VStream}.
 *
 * <p>This provider enables dynamic path creation for VStream via {@link
 * org.higherkindedj.hkt.effect.spi.PathRegistry} and {@link
 * org.higherkindedj.hkt.effect.Path#from(Kind, Class)}. It is discovered automatically through
 * Java's {@link java.util.ServiceLoader} mechanism.
 *
 * <h2>Registration</h2>
 *
 * <p>This provider is registered in {@code
 * META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider}.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Create a VStream and widen to Kind
 * VStream<String> stream = VStream.of("a", "b", "c");
 * Kind<VStreamKind.Witness, String> kind = VStreamKindHelper.VSTREAM.widen(stream);
 *
 * // Discover and create path via SPI
 * Optional<Chainable<String>> path = Path.from(kind, VStreamKind.Witness.class);
 * }</pre>
 *
 * @see PathProvider
 * @see VStream
 * @see VStreamKind.Witness
 */
public class VStreamPathProvider implements PathProvider<VStreamKind.Witness> {

  @Override
  public Class<?> witnessType() {
    return VStreamKind.Witness.class;
  }

  @Override
  public <A> Chainable<A> createPath(Kind<VStreamKind.Witness, A> kind) {
    VStream<A> stream = VStreamKindHelper.VSTREAM.narrow(kind);
    return new DefaultVStreamPath<>(stream);
  }

  @Override
  public Monad<VStreamKind.Witness> monad() {
    return VStreamMonad.INSTANCE;
  }

  @Override
  public String name() {
    return "VStream";
  }
}
