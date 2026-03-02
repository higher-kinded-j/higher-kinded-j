// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.IndexedTraversals;

/**
 * A traversal-like optic that preserves laziness when streaming focused elements.
 *
 * <p>Unlike standard {@link Traversal} which materialises all elements via an {@link Applicative}
 * context, {@code StreamTraversal} provides a lazy {@link VStream} of focused elements. This is the
 * right choice when:
 *
 * <ul>
 *   <li>The source structure may contain many elements
 *   <li>You want to process elements on demand without loading all into memory
 *   <li>You need effectful modification on virtual threads via {@link #modifyVTask}
 * </ul>
 *
 * <h2>Comparison with Traversal</h2>
 *
 * <table>
 * <caption>StreamTraversal vs Traversal</caption>
 * <tr><th>Aspect</th><th>Traversal</th><th>StreamTraversal</th></tr>
 * <tr><td>Element access</td><td>Materialises via Applicative</td><td>Lazy VStream</td></tr>
 * <tr><td>Memory</td><td>All elements in memory</td><td>On-demand production</td></tr>
 * <tr><td>Effect type</td><td>Any Applicative</td><td>VTask (virtual threads)</td></tr>
 * <tr><td>Infinite sources</td><td>Not safe</td><td>Safe for stream()</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * StreamTraversal<VStream<String>, String> traversal = StreamTraversal.forVStream();
 *
 * VStream<String> source = VStream.of("hello", "world");
 *
 * // Lazy streaming - no materialisation
 * VStream<String> elements = traversal.stream(source);
 *
 * // Pure modification
 * VStream<String> upper = traversal.modify(String::toUpperCase, source);
 *
 * // Effectful modification on virtual threads
 * VTask<VStream<String>> enriched = traversal.modifyVTask(
 *     s -> VTask.of(() -> fetchEnrichment(s)),
 *     source
 * );
 * }</pre>
 *
 * @param <S> the source structure type
 * @param <A> the focused element type
 * @see Traversal
 * @see VStream
 */
public interface StreamTraversal<S, A> {

  /**
   * Streams all focused elements lazily.
   *
   * <p>The returned VStream does not materialise the source structure. Elements are produced on
   * demand when the stream is pulled.
   *
   * @param source the source structure; must not be null
   * @return a lazy VStream of focused elements; never null
   */
  VStream<A> stream(S source);

  /**
   * Modifies all focused elements via a pure function, reconstructing the structure.
   *
   * <p>Depending on the structure S, this may need to materialise internally. For example, a
   * VStream-backed StreamTraversal will map lazily, but a List-backed one will need to traverse and
   * rebuild the list.
   *
   * @param f the modification function; must not be null
   * @param source the source structure; must not be null
   * @return the modified structure; never null
   */
  S modify(Function<A, A> f, S source);

  /**
   * Modifies all focused elements via an effectful function on virtual threads.
   *
   * <p>Each element is transformed using a {@link VTask}, which executes on a virtual thread. The
   * returned VTask, when run, produces the modified structure.
   *
   * <p>The primary advantage over {@link Traversal#modifyF} is that this method works directly with
   * VTask rather than requiring a generic Applicative, making it simpler to use for virtual thread
   * workloads.
   *
   * @param f the effectful modification function; must not be null
   * @param source the source structure; must not be null
   * @return a VTask producing the modified structure; never null
   */
  VTask<S> modifyVTask(Function<A, VTask<A>> f, S source);

  /**
   * Composes this StreamTraversal with another, creating a StreamTraversal that focuses through
   * both levels.
   *
   * <p>The resulting StreamTraversal first extracts elements from S via this traversal, then
   * extracts sub-elements from each A via the other traversal.
   *
   * @param other the inner StreamTraversal; must not be null
   * @param <B> the type of elements focused by the inner traversal
   * @return a composed StreamTraversal; never null
   */
  default <B> StreamTraversal<S, B> andThen(StreamTraversal<A, B> other) {
    Objects.requireNonNull(other, "other must not be null");
    StreamTraversal<S, A> self = this;
    return new StreamTraversal<>() {
      @Override
      public VStream<B> stream(S source) {
        return self.stream(source).flatMap(other::stream);
      }

      @Override
      public S modify(Function<B, B> f, S source) {
        return self.modify(a -> other.modify(f, a), source);
      }

      @Override
      public VTask<S> modifyVTask(Function<B, VTask<B>> f, S source) {
        return self.modifyVTask(a -> other.modifyVTask(f, a), source);
      }
    };
  }

  /**
   * Composes this StreamTraversal with a Lens, creating a StreamTraversal that focuses through the
   * traversal then into a field via the lens.
   *
   * @param lens the Lens to compose with; must not be null
   * @param <B> the type of the lens focus
   * @return a composed StreamTraversal; never null
   */
  default <B> StreamTraversal<S, B> andThen(Lens<A, B> lens) {
    Objects.requireNonNull(lens, "lens must not be null");
    StreamTraversal<S, A> self = this;
    return new StreamTraversal<>() {
      @Override
      public VStream<B> stream(S source) {
        return self.stream(source).map(lens::get);
      }

      @Override
      public S modify(Function<B, B> f, S source) {
        return self.modify(a -> lens.modify(f, a), source);
      }

      @Override
      public VTask<S> modifyVTask(Function<B, VTask<B>> f, S source) {
        return self.modifyVTask(a -> f.apply(lens.get(a)).map(b -> lens.set(b, a)), source);
      }
    };
  }

  /**
   * Converts this StreamTraversal to a standard {@link Traversal}.
   *
   * <p><b>Warning:</b> The resulting Traversal materialises all elements via the Applicative
   * context. This loses the laziness advantage of StreamTraversal. Only safe for finite sources.
   *
   * @return a materialising Traversal; never null
   */
  default Traversal<S, A> toTraversal() {
    StreamTraversal<S, A> self = this;
    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, S> modifyF(
          Function<A, Kind<G, A>> f, S source, Applicative<G> applicative) {
        VStream<A> elements = self.stream(source);
        List<A> elementList = elements.toList().run();

        List<Kind<G, A>> effects = new ArrayList<>(elementList.size());
        for (A a : elementList) {
          effects.add(f.apply(a));
        }

        Kind<G, List<A>> sequenced = IndexedTraversals.sequenceList(effects, applicative);
        return applicative.map(
            list -> {
              java.util.Iterator<A> iter = list.iterator();
              return self.modify(_ -> iter.next(), source);
            },
            sequenced);
      }
    };
  }

  /**
   * Creates a StreamTraversal from a standard {@link Traversal}.
   *
   * <p>The resulting StreamTraversal uses the Traversal to extract and modify elements. The {@link
   * #stream} method materialises via the identity applicative, so laziness is not preserved. Use
   * this as a bridge when you have an existing Traversal.
   *
   * @param traversal the Traversal to wrap; must not be null
   * @param <S> the source type
   * @param <A> the element type
   * @return a StreamTraversal wrapping the Traversal; never null
   */
  static <S, A> StreamTraversal<S, A> fromTraversal(Traversal<S, A> traversal) {
    Objects.requireNonNull(traversal, "traversal must not be null");
    return new StreamTraversal<>() {
      @Override
      public VStream<A> stream(S source) {
        List<A> elements = org.higherkindedj.optics.util.Traversals.getAll(traversal, source);
        return VStream.fromList(elements);
      }

      @Override
      public S modify(Function<A, A> f, S source) {
        return org.higherkindedj.optics.util.Traversals.modify(traversal, f, source);
      }

      @Override
      public VTask<S> modifyVTask(Function<A, VTask<A>> f, S source) {
        List<A> elements = org.higherkindedj.optics.util.Traversals.getAll(traversal, source);
        List<VTask<A>> tasks = new ArrayList<>(elements.size());
        for (A a : elements) {
          tasks.add(f.apply(a));
        }
        return VTask.of(
            () -> {
              List<A> results = new ArrayList<>(tasks.size());
              for (VTask<A> task : tasks) {
                results.add(task.run());
              }
              // Reconstruct the structure with modified elements
              java.util.Iterator<A> iter = results.iterator();
              return org.higherkindedj.optics.util.Traversals.modify(
                  traversal, _ -> iter.next(), source);
            });
      }
    };
  }

  /**
   * Creates a StreamTraversal for {@link VStream} elements.
   *
   * <p>This is the canonical instance. The {@link #stream} method returns the source VStream
   * unchanged (identity). The {@link #modify} method maps each element lazily. The {@link
   * #modifyVTask} method applies the effectful function to each element using {@link
   * VStream#mapTask}.
   *
   * @param <A> the element type
   * @return a StreamTraversal for VStream elements; never null
   */
  static <A> StreamTraversal<VStream<A>, A> forVStream() {
    return new StreamTraversal<>() {
      @Override
      public VStream<A> stream(VStream<A> source) {
        return source;
      }

      @Override
      public VStream<A> modify(Function<A, A> f, VStream<A> source) {
        return source.map(f);
      }

      @Override
      public VTask<VStream<A>> modifyVTask(Function<A, VTask<A>> f, VStream<A> source) {
        return VTask.succeed(source.mapTask(f));
      }
    };
  }

  /**
   * Creates a StreamTraversal for {@link List} elements.
   *
   * <p>The {@link #stream} method creates a lazy VStream from the list. The {@link #modify} method
   * produces a new list with each element transformed. The {@link #modifyVTask} method applies the
   * effectful function to each element sequentially.
   *
   * @param <A> the element type
   * @return a StreamTraversal for List elements; never null
   */
  static <A> StreamTraversal<List<A>, A> forList() {
    return new StreamTraversal<>() {
      @Override
      public VStream<A> stream(List<A> source) {
        return VStream.fromList(source);
      }

      @Override
      public List<A> modify(Function<A, A> f, List<A> source) {
        List<A> result = new ArrayList<>(source.size());
        for (A a : source) {
          result.add(f.apply(a));
        }
        return List.copyOf(result);
      }

      @Override
      public VTask<List<A>> modifyVTask(Function<A, VTask<A>> f, List<A> source) {
        return VTask.of(
            () -> {
              List<A> result = new ArrayList<>(source.size());
              for (A a : source) {
                result.add(f.apply(a).run());
              }
              return List.copyOf(result);
            });
      }
    };
  }
}
