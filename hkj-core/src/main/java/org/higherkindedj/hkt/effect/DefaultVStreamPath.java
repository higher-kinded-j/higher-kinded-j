// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.indexed.Pair;

/**
 * Default implementation of {@link VStreamPath}.
 *
 * <p>This class wraps a {@link VStream} and delegates all operations to it, providing the fluent
 * Effect Path API. All stream operations remain lazy; terminal operations bridge to {@link
 * VTaskPath} via {@link Path#vtaskPath(VTask)}.
 *
 * @param stream the underlying VStream; must not be null
 * @param <A> the type of elements in the stream
 */
record DefaultVStreamPath<A>(VStream<A> stream) implements VStreamPath<A> {

  DefaultVStreamPath {
    Objects.requireNonNull(stream, "stream must not be null");
  }

  @Override
  public VStream<A> run() {
    return stream;
  }

  // ===== Composable implementation =====

  @Override
  public <B> VStreamPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new DefaultVStreamPath<>(stream.map(mapper));
  }

  @Override
  public VStreamPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new DefaultVStreamPath<>(stream.peek(consumer));
  }

  @Override
  public VStreamPath<Unit> asUnit() {
    return new DefaultVStreamPath<>(stream.asUnit());
  }

  // ===== Chainable implementation =====

  @Override
  public <B> VStreamPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new DefaultVStreamPath<>(
        stream.flatMap(
            a -> {
              Chainable<B> chainable = mapper.apply(a);
              Objects.requireNonNull(chainable, "mapper must not return null");

              if (!(chainable instanceof VStreamPath<?> vsp)) {
                throw new IllegalArgumentException(
                    "via mapper must return VStreamPath, got: " + chainable.getClass());
              }

              @SuppressWarnings("unchecked")
              VStreamPath<B> typedResult = (VStreamPath<B>) vsp;
              return typedResult.run();
            }));
  }

  @Override
  public <B> VStreamPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(ignored -> supplier.get());
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> VStreamPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof VStreamPath<?> otherVStream)) {
      throw new IllegalArgumentException("Cannot zipWith non-VStreamPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    VStreamPath<B> typedOther = (VStreamPath<B>) otherVStream;

    return new DefaultVStreamPath<>(stream.zipWith(typedOther.run(), combiner::apply));
  }

  @Override
  public <B, C, D> VStreamPath<D> zipWith3(
      VStreamPath<B> second,
      VStreamPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    // Zip this with second, then zip the result with third
    VStream<D> zipped =
        stream
            .zipWith(second.run(), Pair::new)
            .zipWith(third.run(), (pair, c) -> combiner.apply(pair.first(), pair.second(), c));

    return new DefaultVStreamPath<>(zipped);
  }

  // ===== Stream-specific operations =====

  @Override
  public VStreamPath<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DefaultVStreamPath<>(stream.filter(predicate));
  }

  @Override
  public VStreamPath<A> take(long n) {
    return new DefaultVStreamPath<>(stream.take(n));
  }

  @Override
  public VStreamPath<A> drop(long n) {
    return new DefaultVStreamPath<>(stream.drop(n));
  }

  @Override
  public VStreamPath<A> takeWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DefaultVStreamPath<>(stream.takeWhile(predicate));
  }

  @Override
  public VStreamPath<A> dropWhile(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DefaultVStreamPath<>(stream.dropWhile(predicate));
  }

  @Override
  public VStreamPath<A> distinct() {
    return new DefaultVStreamPath<>(stream.distinct());
  }

  @Override
  public VStreamPath<A> concat(VStreamPath<A> other) {
    Objects.requireNonNull(other, "other must not be null");
    return new DefaultVStreamPath<>(stream.concat(other.run()));
  }

  // ===== Terminal operations (bridge to VTaskPath) =====

  @Override
  public VTaskPath<List<A>> toList() {
    return new DefaultVTaskPath<>(stream.toList());
  }

  @Override
  public VTaskPath<A> fold(A identity, BinaryOperator<A> op) {
    Objects.requireNonNull(op, "op must not be null");
    return new DefaultVTaskPath<>(stream.fold(identity, op));
  }

  @Override
  public <B> VTaskPath<B> foldLeft(B identity, BiFunction<B, A, B> f) {
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVTaskPath<>(stream.foldLeft(identity, f));
  }

  @Override
  public <M> VTaskPath<M> foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f) {
    Objects.requireNonNull(monoid, "monoid must not be null");
    Objects.requireNonNull(f, "f must not be null");
    return new DefaultVTaskPath<>(
        stream.foldLeft(monoid.empty(), (acc, a) -> monoid.combine(acc, f.apply(a))));
  }

  @Override
  public VTaskPath<Optional<A>> headOption() {
    return new DefaultVTaskPath<>(stream.headOption());
  }

  @Override
  public VTaskPath<Optional<A>> lastOption() {
    return new DefaultVTaskPath<>(stream.lastOption());
  }

  @Override
  public VTaskPath<Long> count() {
    return new DefaultVTaskPath<>(stream.count());
  }

  @Override
  public VTaskPath<Boolean> exists(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DefaultVTaskPath<>(stream.exists(predicate));
  }

  @Override
  public VTaskPath<Boolean> forAll(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DefaultVTaskPath<>(stream.forAll(predicate));
  }

  @Override
  public VTaskPath<Optional<A>> find(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new DefaultVTaskPath<>(stream.find(predicate));
  }

  @Override
  public VTaskPath<Unit> forEach(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return new DefaultVTaskPath<>(stream.forEach(consumer));
  }

  // ===== Focus bridge =====

  @Override
  public <B> VStreamPath<B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  @Override
  public <B> VStreamPath<B> focus(AffinePath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return new DefaultVStreamPath<>(
        stream.map(a -> path.getOptional(a)).filter(Optional::isPresent).map(Optional::get));
  }

  // ===== Conversions =====

  @Override
  public VTaskPath<A> first() {
    return new DefaultVTaskPath<>(
        stream
            .headOption()
            .map(opt -> opt.orElseThrow(() -> new NoSuchElementException("VStreamPath is empty"))));
  }

  @Override
  public VTaskPath<A> last() {
    return new DefaultVTaskPath<>(
        stream
            .lastOption()
            .map(opt -> opt.orElseThrow(() -> new NoSuchElementException("VStreamPath is empty"))));
  }

  @Override
  public StreamPath<A> toStreamPath() {
    List<A> elements = stream.toList().run();
    return StreamPath.fromList(elements);
  }

  @Override
  public ListPath<A> toListPath() {
    List<A> elements = stream.toList().run();
    return ListPath.of(elements);
  }

  @Override
  public NonDetPath<A> toNonDetPath() {
    List<A> elements = stream.toList().run();
    return NonDetPath.of(elements);
  }

  // ===== Object methods =====

  @Override
  public String toString() {
    return "VStreamPath(<stream>)";
  }
}
