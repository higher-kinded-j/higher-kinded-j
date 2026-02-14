// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link Id} values.
 *
 * <p>{@code IdPath} is the simplest path type, representing a pure computation with no effects. It
 * always contains a value and never fails. This makes it useful for:
 *
 * <ul>
 *   <li>Testing path compositions without introducing effects
 *   <li>Lifting pure values into the Effect Path API
 *   <li>Generic programming where a path type is required
 * </ul>
 *
 * <h2>Creating IdPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * IdPath<User> path = Path.id(user);
 * IdPath<User> fromId = Path.idPath(Id.of(user));
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * IdPath<String> result = Path.id(user)
 *     .map(User::getName)
 *     .via(name -> Path.id(name.toUpperCase()));
 * }</pre>
 *
 * <h2>Converting to other paths</h2>
 *
 * <pre>{@code
 * MaybePath<User> maybe = Path.id(user).toMaybePath();
 * EitherPath<Error, User> either = Path.id(user).toEitherPath();
 * }</pre>
 *
 * @param <A> the type of the contained value
 */
public final class IdPath<A> implements Chainable<A> {

  private final Id<A> value;

  /**
   * Creates a new IdPath wrapping the given Id.
   *
   * @param value the Id to wrap; must not be null
   */
  IdPath(Id<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying Id value.
   *
   * @return the wrapped Id
   */
  public Id<A> run() {
    return value;
  }

  /**
   * Returns the contained value directly.
   *
   * <p>Since IdPath always contains a value, this method always succeeds.
   *
   * @return the contained value
   */
  public A get() {
    return value.value();
  }

  // ===== Conversions =====

  /**
   * Converts this IdPath to a MaybePath.
   *
   * <p>The resulting MaybePath will contain the value (Just) since IdPath always has a value. If
   * the value is null, the result is Nothing.
   *
   * @return a MaybePath containing the value
   */
  public MaybePath<A> toMaybePath() {
    A v = value.value();
    return v != null ? new MaybePath<>(Maybe.just(v)) : new MaybePath<>(Maybe.nothing());
  }

  /**
   * Converts this IdPath to an EitherPath.
   *
   * <p>The resulting EitherPath will be Right since IdPath always has a value.
   *
   * @param <E> the error type for the resulting EitherPath
   * @return an EitherPath containing the value as Right
   */
  public <E> EitherPath<E, A> toEitherPath() {
    return new EitherPath<>(Either.right(value.value()));
  }

  // ===== Composable implementation =====

  @Override
  public <B> IdPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new IdPath<>(value.map(mapper));
  }

  @Override
  public IdPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    consumer.accept(value.value());
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> IdPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof IdPath<?> otherId)) {
      throw new IllegalArgumentException("Cannot zipWith non-IdPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    IdPath<B> typedOther = (IdPath<B>) otherId;

    return new IdPath<>(Id.of(combiner.apply(this.value.value(), typedOther.value.value())));
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result
   */
  public <B, C, D> IdPath<D> zipWith3(
      IdPath<B> second,
      IdPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return new IdPath<>(
        Id.of(combiner.apply(this.value.value(), second.value.value(), third.value.value())));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> IdPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    Chainable<B> result = mapper.apply(value.value());
    Objects.requireNonNull(result, "mapper must not return null");

    if (!(result instanceof IdPath<?> idPath)) {
      throw new IllegalArgumentException(
          "via mapper must return IdPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    IdPath<B> typedResult = (IdPath<B>) idPath;
    return typedResult;
  }

  @Override
  public <B> IdPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    Chainable<B> result = supplier.get();
    Objects.requireNonNull(result, "supplier must not return null");

    if (!(result instanceof IdPath<?> idPath)) {
      throw new IllegalArgumentException(
          "then supplier must return IdPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    IdPath<B> typedResult = (IdPath<B>) idPath;
    return typedResult;
  }

  // ===== Focus Bridge Methods =====

  /**
   * Applies a {@link FocusPath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain, allowing structural navigation
   * inside an Id context.
   *
   * @param path the FocusPath to apply; must not be null
   * @param <B> the focused type
   * @return a new IdPath containing the focused value
   * @throws NullPointerException if path is null
   */
  public <B> IdPath<B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  /**
   * Applies an {@link AffinePath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain. Since IdPath always contains a
   * value, a MaybePath is returned to handle the case where the AffinePath doesn't match.
   *
   * @param path the AffinePath to apply; must not be null
   * @param <B> the focused type
   * @return a MaybePath containing the focused value if the path matches
   * @throws NullPointerException if path is null
   */
  public <B> MaybePath<B> focus(AffinePath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return path.getOptional(value.value()).map(Path::just).orElseGet(Path::nothing);
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof IdPath<?> other)) return false;
    return Objects.equals(value, other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return "IdPath(" + value.value() + ")";
  }
}
