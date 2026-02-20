// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;

/**
 * Implements the {@link Alternative} type class for {@link VStream}, using {@link
 * VStreamKind.Witness} as the higher-kinded type witness.
 *
 * <p>This implementation uses <b>concatenation semantics</b> for the {@link #orElse} operation,
 * consistent with list-like types. Given two streams, {@code orElse} produces a stream that emits
 * all elements from the first stream followed by all elements from the second stream.
 *
 * <p><b>Alternative Laws:</b>
 *
 * <ul>
 *   <li><b>Left Identity:</b> {@code orElse(empty(), () -> fa) == fa}
 *   <li><b>Right Identity:</b> {@code orElse(fa, () -> empty()) == fa}
 *   <li><b>Associativity:</b> {@code orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, ()
 *       -> fb), () -> fc)}
 * </ul>
 *
 * @see Alternative
 * @see VStreamMonad
 * @see VStream
 * @see VStreamKind
 * @see VStreamKind.Witness
 */
public class VStreamAlternative extends VStreamMonad implements Alternative<VStreamKind.Witness> {

  /** Singleton instance of {@code VStreamAlternative}. */
  public static final VStreamAlternative INSTANCE = new VStreamAlternative();

  /** Protected constructor to enforce the singleton pattern. */
  protected VStreamAlternative() {
    super();
  }

  /**
   * Returns an empty VStream, representing the identity element for the {@link #orElse} operation.
   *
   * @param <A> The phantom type parameter of the empty stream.
   * @return A {@code Kind<VStreamKind.Witness, A>} representing an empty VStream. Never null.
   */
  @Override
  public <A> Kind<VStreamKind.Witness, A> empty() {
    return VSTREAM.widen(VStream.empty());
  }

  /**
   * Concatenates two VStreams. The resulting stream emits all elements from {@code fa} followed by
   * all elements from the stream provided by {@code fb}.
   *
   * <p>The second argument is lazy (provided via {@link Supplier}) to avoid unnecessary computation
   * when only elements from the first stream are needed.
   *
   * @param <A> The type of elements in both streams.
   * @param fa The first non-null VStream.
   * @param fb A non-null {@link Supplier} providing the second VStream (evaluated lazily).
   * @return A {@code Kind<VStreamKind.Witness, A>} representing the concatenated stream. Never
   *     null.
   * @throws NullPointerException if {@code fa} or {@code fb} is null.
   */
  @Override
  public <A> Kind<VStreamKind.Witness, A> orElse(
      Kind<VStreamKind.Witness, A> fa, Supplier<Kind<VStreamKind.Witness, A>> fb) {
    Objects.requireNonNull(fa, "First alternative (fa) must not be null");
    Objects.requireNonNull(fb, "Second alternative supplier (fb) must not be null");

    VStream<A> streamA = VSTREAM.narrow(fa);
    // Defer evaluation of second stream until first is exhausted
    VStream<A> combined = streamA.concat(VStream.defer(() -> VSTREAM.narrow(fb.get())));
    return VSTREAM.widen(combined);
  }
}
