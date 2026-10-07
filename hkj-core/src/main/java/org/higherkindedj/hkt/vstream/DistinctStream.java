// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The stream {@link VStream#distinct()} returns, from one position of its source. It records the
 * position at which each element first appeared, and emits an element only there, so a tail pulled
 * again replays the same elements.
 *
 * @param <A> The element type.
 */
final class DistinctStream<A> extends DerivedStream<A, A> {

  /** Stands for a null element, which a {@link ConcurrentHashMap} cannot hold as a key. */
  private static final Object NULL_ELEMENT = new Object();

  private final long position;
  private final ConcurrentHashMap<Object, Long> firstSeen;

  DistinctStream(VStream<A> source, long position, ConcurrentHashMap<Object, Long> firstSeen) {
    super(source);
    this.position = position;
    this.firstSeen = firstSeen;
  }

  @Override
  public VTask<Step<A>> pull() {
    return upstream
        .pull()
        .map(
            step ->
                switch (step) {
                  case Step.Emit<A> e -> {
                    Object key = e.value() == null ? NULL_ELEMENT : e.value();
                    Long first = firstSeen.putIfAbsent(key, position);
                    VStream<A> tail = new DistinctStream<>(e.tail(), position + 1, firstSeen);
                    yield first == null || first == position
                        ? new Step.Emit<>(e.value(), tail)
                        : new Step.Skip<>(tail);
                  }
                  case Step.Skip<A> s ->
                      new Step.Skip<>(new DistinctStream<>(s.tail(), position, firstSeen));
                  case Step.Done<A> _ -> new Step.Done<>();
                });
  }
}
