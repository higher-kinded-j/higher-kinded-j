// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;

/**
 * Natural transformations between VStream and other collection/effect types.
 *
 * <p>This utility class provides type-safe conversions between VStream and Stream, List, and VTask
 * representations using the {@link NaturalTransformation} abstraction. These transformations
 * preserve element order and satisfy the naturality law:
 *
 * <pre>{@code
 * nt.apply(fa.map(f)) == nt.apply(fa).map(f)
 * }</pre>
 *
 * <h2>Lazy vs Materialising Transformations</h2>
 *
 * <ul>
 *   <li><b>Lazy (non-materialising):</b> {@link #streamToVStream()} and {@link #listToVStream()}
 *       wrap the source without evaluating elements. Safe for large or infinite inputs.
 *   <li><b>Materialising:</b> {@link #vstreamToStream()} and {@link #vstreamToList()} execute the
 *       VStream, collecting all elements. Only safe for finite streams.
 * </ul>
 *
 * <h2>Usage with GenericPath.mapK</h2>
 *
 * <pre>{@code
 * GenericPath<VStreamKind.Witness, String> vstreamPath = ...;
 * GenericPath<ListKind.Witness, String> listPath =
 *     vstreamPath.mapK(VStreamTransformations.vstreamToList(), ListMonad.INSTANCE);
 * }</pre>
 *
 * @see NaturalTransformation
 * @see VStream
 */
public final class VStreamTransformations {

  private VStreamTransformations() {}

  /**
   * Natural transformation from {@link Stream} to {@link VStream}.
   *
   * <p>The stream is consumed lazily via its iterator. This transformation does not materialise the
   * stream; elements are produced on demand when the resulting VStream is pulled.
   *
   * @return a natural transformation from StreamKind to VStreamKind
   */
  public static NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> streamToVStream() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<VStreamKind.Witness, A> apply(Kind<StreamKind.Witness, A> fa) {
        Stream<A> stream = STREAM.narrow(fa);
        return VSTREAM.widen(VStream.fromStream(stream));
      }
    };
  }

  /**
   * Natural transformation from {@link VStream} to {@link Stream}.
   *
   * <p><b>Warning:</b> This transformation materialises the entire VStream by collecting all
   * elements into a list, then creating a stream from that list. Only safe for finite streams.
   * Using this on an infinite stream will cause an {@link OutOfMemoryError}.
   *
   * @return a natural transformation from VStreamKind to StreamKind
   */
  public static NaturalTransformation<VStreamKind.Witness, StreamKind.Witness> vstreamToStream() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<StreamKind.Witness, A> apply(Kind<VStreamKind.Witness, A> fa) {
        VStream<A> vstream = VSTREAM.narrow(fa);
        List<A> elements = vstream.toList().run();
        return STREAM.widen(elements.stream());
      }
    };
  }

  /**
   * Natural transformation from {@link List} to {@link VStream}.
   *
   * <p>The list is consumed lazily element by element. This transformation does not copy the list;
   * elements are produced on demand when the resulting VStream is pulled.
   *
   * @return a natural transformation from ListKind to VStreamKind
   */
  public static NaturalTransformation<ListKind.Witness, VStreamKind.Witness> listToVStream() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<VStreamKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
        List<A> list = LIST.narrow(fa);
        return VSTREAM.widen(VStream.fromList(list));
      }
    };
  }

  /**
   * Natural transformation from {@link VStream} to {@link List}.
   *
   * <p><b>Warning:</b> This transformation materialises the entire VStream by collecting all
   * elements into a list. Only safe for finite streams. Using this on an infinite stream will cause
   * an {@link OutOfMemoryError}.
   *
   * @return a natural transformation from VStreamKind to ListKind
   */
  public static NaturalTransformation<VStreamKind.Witness, ListKind.Witness> vstreamToList() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<ListKind.Witness, A> apply(Kind<VStreamKind.Witness, A> fa) {
        VStream<A> vstream = VSTREAM.narrow(fa);
        List<A> elements = vstream.toList().run();
        return LIST.widen(elements);
      }
    };
  }
}
