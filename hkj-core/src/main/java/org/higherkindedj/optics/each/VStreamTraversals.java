// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.each;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.jspecify.annotations.NullMarked;

/**
 * Provides a {@link Traversal} instance for {@link VStream} elements.
 *
 * <p>The traversal materialises the VStream to a list, sequences each element effect using
 * copy-safe accumulation via {@link IndexedTraversals#sequenceList}, and reconstructs a VStream
 * from the result via {@link VStream#fromList}.
 *
 * <p><b>Materialisation Warning:</b> The VStream is fully consumed during traversal. This is only
 * safe for finite streams. Using this traversal on infinite streams will not terminate.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Traversal<VStream<String>, String> traversal = VStreamTraversals.forVStream();
 *
 * VStream<String> stream = VStream.fromList(List.of("hello", "world"));
 * VStream<String> upper = Traversals.modify(traversal, String::toUpperCase, stream);
 * // upper produces: ["HELLO", "WORLD"]
 *
 * List<String> all = Traversals.getAll(traversal, stream);
 * // all: ["hello", "world"]
 * }</pre>
 *
 * @see VStream
 * @see Traversal
 * @see EachInstances#vstreamEach()
 */
@NullMarked
public final class VStreamTraversals {

  /** Private constructor to prevent instantiation. */
  private VStreamTraversals() {}

  /**
   * Creates a {@link Traversal} for {@link VStream} elements.
   *
   * <p>The traversal materialises the stream to a list (consuming it), applies the effectful
   * function to each element and sequences the results using copy-safe accumulation via {@link
   * IndexedTraversals#sequenceList}, then converts the result back to a VStream.
   *
   * <p><b>Warning:</b> The VStream is fully evaluated during traversal. Only use with finite
   * streams. Infinite streams will cause non-termination.
   *
   * @param <A> the element type within the VStream
   * @return a Traversal over VStream elements; never null
   */
  public static <A> Traversal<VStream<A>, A> forVStream() {
    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, VStream<A>> modifyF(
          Function<A, Kind<G, A>> f, VStream<A> source, Applicative<G> applicative) {
        // Materialise the VStream to a list (consuming it)
        List<A> elements = source.toList().run();

        // Apply the effectful function to each element
        List<Kind<G, A>> modifiedEffects = new ArrayList<>(elements.size());
        for (A a : elements) {
          modifiedEffects.add(f.apply(a));
        }

        // Sequence effects using copy-safe accumulation, then convert back to VStream
        Kind<G, List<A>> result = IndexedTraversals.sequenceList(modifiedEffects, applicative);
        return applicative.map(VStream::fromList, result);
      }
    };
  }
}
