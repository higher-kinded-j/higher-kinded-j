package org.simulation.hkt.either;


import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * Represents a value of one of two possible types, Left or Right.
 * By convention, Left is used for failure/error values and Right is used for success values.
 * Operations like map and flatMap are right-biased, meaning they operate on the Right value
 * and pass Left values through unchanged.
 *
 * @param <L> the type of the Left value
 * @param <R> the type of the Right value
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

  /**
   * Checks if this Either is a Left.
   */
  boolean isLeft();

  /**
   * Checks if this Either is a Right.
   */
  boolean isRight();

  /**
   * Gets the Left value. Throws NoSuchElementException if this is a Right.
   *
   * @throws NoSuchElementException if this is a Right
   */
  L getLeft() throws NoSuchElementException;

  /**
   * Gets the Right value. Throws NoSuchElementException if this is a Left.
   *
   * @throws NoSuchElementException if this is a Left
   */
  R getRight() throws NoSuchElementException;

  /**
   * Applies one of two functions depending on whether this is a Left or a Right.
   *
   * @param leftMapper  the function to apply if this is a Left
   * @param rightMapper the function to apply if this is a Right
   * @param <T>         the target type of the mapping functions
   * @return the result of applying the appropriate function
   */
  <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper);

  /**
   * If this is a Right, applies the given function to the Right value.
   * If this is a Left, returns this Left unchanged.
   * This operation is right-biased.
   *
   * @param mapper the function to apply to the Right value
   * @param <R2>   the type of the Right value of the resulting Either
   * @return a new Either potentially with a mapped Right value, or the original Left
   */
  <R2> Either<L, R2> map(Function<? super R, ? extends R2> mapper);

  /**
   * If this is a Right, applies the given Either-bearing function to the Right value.
   * If this is a Left, returns this Left unchanged.
   * This operation is right-biased.
   *
   * @param mapper the function to apply to the Right value, which returns an Either
   * @param <R2>   the type of the Right value of the resulting Either
   * @return the result of applying the function if this is a Right, or the original Left
   */
  <R2> Either<L, R2> flatMap(Function<? super R, ? extends Either<L, ? extends R2>> mapper);

  /**
   * Performs the given action on the Left value if this is a Left.
   *
   * @param action the action to perform
   */
  void ifLeft(Consumer<? super L> action);

  /**
   * Performs the given action on the Right value if this is a Right.
   *
   * @param action the action to perform
   */
  void ifRight(Consumer<? super R> action);

  // --- Static Factory Methods ---

  /**
   * Creates a Left instance.
   */
  static <L, R> Either<L, R> left(L value) {
    // Allow nulls for Left value based on common Either usage
    return new Left<>(value);
  }

  /**
   * Creates a Right instance.
   */
  static <L, R> Either<L, R> right(R value) {
    // Allow nulls for Right value, though often non-null is desired
    // Could add a non-null check here if needed: Objects.requireNonNull(value);
    return new Right<>(value);
  }


  /**
   * Represents the Left side of an Either.
   */
  final record Left<L, R>(L value) implements Either<L, R> {
    @Override
    public boolean isLeft() {
      return true;
    }

    @Override
    public boolean isRight() {
      return false;
    }

    @Override
    public L getLeft() {
      return value;
    }

    @Override
    public R getRight() {
      throw new NoSuchElementException("Cannot getRight() on a Left");
    }

    @Override
    public <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
      Objects.requireNonNull(leftMapper, "leftMapper cannot be null");
      return leftMapper.apply(value);
    }

    @Override
    public <R2> Either<L, R2> map(Function<? super R, ? extends R2> mapper) {
      Objects.requireNonNull(mapper, "mapper function cannot be null");
      // Map does nothing on Left, return this instance cast appropriately
      @SuppressWarnings("unchecked")
      Either<L, R2> self = (Either<L, R2>) this;
      return self;
    }

    @Override
    public <R2> Either<L, R2> flatMap(Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
      Objects.requireNonNull(mapper, "mapper function cannot be null");
      // flatMap does nothing on Left, return this instance cast appropriately
      @SuppressWarnings("unchecked")
      Either<L, R2> self = (Either<L, R2>) this;
      return self;
    }

    @Override
    public void ifLeft(Consumer<? super L> action) {
      Objects.requireNonNull(action, "action cannot be null");
      action.accept(value);
    }

    @Override
    public void ifRight(Consumer<? super R> action) {
      Objects.requireNonNull(action, "action cannot be null");
      // Do nothing for Left
    }

    @Override
    public String toString() {
      return "Left(" + value + ")";
    }
  }

  /**
   * Represents the Right side of an Either.
   */
  final record Right<L, R>(R value) implements Either<L, R> {
    @Override
    public boolean isLeft() {
      return false;
    }

    @Override
    public boolean isRight() {
      return true;
    }

    @Override
    public L getLeft() {
      throw new NoSuchElementException("Cannot getLeft() on a Right");
    }

    @Override
    public R getRight() {
      return value;
    }

    @Override
    public <T> T fold(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
      Objects.requireNonNull(rightMapper, "rightMapper cannot be null");
      return rightMapper.apply(value);
    }

    @Override
    public <R2> Either<L, R2> map(Function<? super R, ? extends R2> mapper) {
      Objects.requireNonNull(mapper, "mapper function cannot be null");
      // Apply the mapper and wrap the result in a new Right
      return new Right<>(mapper.apply(value));
    }

    @Override
    public <R2> Either<L, R2> flatMap(Function<? super R, ? extends Either<L, ? extends R2>> mapper) {
      Objects.requireNonNull(mapper, "mapper function cannot be null");
      // Apply the mapper, which already returns an Either
      Either<L, ? extends R2> result = mapper.apply(value);
      Objects.requireNonNull(result, "flatMap mapper returned null Either");
      // Cast needed due to <? extends R2>
      @SuppressWarnings("unchecked")
      Either<L, R2> typedResult = (Either<L, R2>) result;
      return typedResult;
    }

    @Override
    public void ifLeft(Consumer<? super L> action) {
      Objects.requireNonNull(action, "action cannot be null");
      // Do nothing for Right
    }

    @Override
    public void ifRight(Consumer<? super R> action) {
      Objects.requireNonNull(action, "action cannot be null");
      action.accept(value);
    }

    @Override
    public String toString() {
      return "Right(" + value + ")";
    }
  }

}