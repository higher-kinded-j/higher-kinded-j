// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.NullMarked;

/**
 * DSL for building optic programs using the Free monad.
 *
 * <p>This class provides methods to construct optic operations as Free monad programs that can be
 * interpreted in different ways:
 *
 * <ul>
 *   <li><b>Direct execution</b> - Normal optic operations
 *   <li><b>Logging</b> - Record all operations for audit trails
 *   <li><b>Validation</b> - Check constraints before executing
 *   <li><b>Optimization</b> - Fuse multiple operations for efficiency
 *   <li><b>Testing</b> - Mock operations without real data
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Build a program
 * Free<OpticOpKind.Witness, Person> program =
 *     OpticPrograms.get(person, PersonLenses.age())
 *         .flatMap(age -> {
 *             if (age < 18) {
 *                 return Free.pure(person);
 *             } else {
 *                 return OpticPrograms.modify(person, PersonLenses.age(), a -> a + 1)
 *                     .flatMap(p1 ->
 *                         OpticPrograms.set(p1, PersonLenses.status(), "ADULT"));
 *             }
 *         });
 *
 * // Interpret it
 * Person result = OpticInterpreters.direct().run(program);
 *
 * // Or log it
 * LoggingInterpreter logger = OpticInterpreters.logging();
 * Person result = logger.run(program);
 * logger.getLog().forEach(System.out::println);
 * }</pre>
 *
 * <h2>When to Use</h2>
 *
 * <p>Use the Free monad DSL when you need:
 *
 * <ul>
 *   <li>Complex multi-step optic workflows
 *   <li>Audit trails of what changed
 *   <li>Validation before modification
 *   <li>Performance optimization (batch/fuse operations)
 *   <li>Multiple execution strategies
 * </ul>
 *
 * <p>For simple operations, use {@link org.higherkindedj.optics.fluent.OpticOps} instead.
 */
@NullMarked
public final class OpticPrograms {

  private OpticPrograms() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Lifts an optic operation into the Free monad.
   *
   * @param op The operation to lift
   * @param <A> The result type
   * @return A Free monad program
   */
  private static <A> Free<OpticOpKind.Witness, A> liftOp(OpticOp<?, A> op) {
    Kind<OpticOpKind.Witness, A> kindOp = OpticOpKindHelper.OP.widen(op);
    return Free.liftF(kindOp, OpticOpFunctor.INSTANCE);
  }

  /**
   * Converts a Lens to a Getter.
   *
   * @param lens The lens to convert
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Getter that represents the lens
   */
  private static <S, A> Getter<S, A> lensToGetter(Lens<S, A> lens) {
    return lens::get;
  }

  /**
   * Converts a Traversal to a Fold by running it with the Id monad.
   *
   * @param traversal The traversal to convert
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Fold that represents the traversal
   */
  private static <S, A> Fold<S, A> traversalToFold(Traversal<S, A> traversal) {
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        // Use the Const applicative to fold over the traversal efficiently
        // This is the standard functional programming approach that avoids side effects
        ConstApplicative<M> constApp = new ConstApplicative<>(monoid);

        // Apply the traversal with the Const applicative
        // The traversal will accumulate values using the monoid whilst traversing
        Kind<ConstKind.Witness<M>, S> result =
            traversal.modifyF(
                a -> ConstKindHelper.CONST.widen(new Const<>(f.apply(a))), source, constApp);

        // Extract the accumulated result from the Const
        Const<M, S> finalConst = ConstKindHelper.CONST.narrow(result);
        return finalConst.value();
      }
    };
  }

  // ============================================================================
  // Get Operations
  // ============================================================================

  /**
   * Creates a program that gets a value through a {@link Getter} or {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, String> program =
   *     OpticPrograms.get(person, PersonLenses.name());
   * }</pre>
   *
   * @param source The source structure
   * @param getter The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the focused value
   */
  public static <S, A> Free<OpticOpKind.Witness, A> get(S source, Getter<S, A> getter) {
    return liftOp(new OpticOp.Get<>(source, getter));
  }

  /**
   * Creates a program that gets a value through a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, String> program =
   *     OpticPrograms.get(person, PersonLenses.name());
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the focused value
   */
  public static <S, A> Free<OpticOpKind.Witness, A> get(S source, Lens<S, A> lens) {
    return liftOp(new OpticOp.Get<>(source, lensToGetter(lens)));
  }

  /**
   * Creates a program that gets the first value through a {@link Fold}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Optional<Address>> program =
   *     OpticPrograms.preview(person, addressPrism);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns an Optional of the first focused value
   */
  public static <S, A> Free<OpticOpKind.Witness, Optional<A>> preview(S source, Fold<S, A> fold) {
    return liftOp(new OpticOp.Preview<>(source, fold));
  }

  /**
   * Creates a program that gets the first value through a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Optional<Person>> program =
   *     OpticPrograms.preview(team, playerTraversal);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns an Optional of the first focused value
   */
  public static <S, A> Free<OpticOpKind.Witness, Optional<A>> preview(
      S source, Traversal<S, A> traversal) {
    return liftOp(new OpticOp.Preview<>(source, traversalToFold(traversal)));
  }

  /**
   * Creates a program that gets all values through a {@link Fold} or {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, List<String>> program =
   *     OpticPrograms.getAll(team, TeamTraversals.playerNames());
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns a list of all focused values
   */
  public static <S, A> Free<OpticOpKind.Witness, List<A>> getAll(S source, Fold<S, A> fold) {
    return liftOp(new OpticOp.GetAll<>(source, fold));
  }

  /**
   * Creates a program that gets all values through a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, List<String>> program =
   *     OpticPrograms.getAll(team, TeamTraversals.playerNames());
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns a list of all focused values
   */
  public static <S, A> Free<OpticOpKind.Witness, List<A>> getAll(
      S source, Traversal<S, A> traversal) {
    return liftOp(new OpticOp.GetAll<>(source, traversalToFold(traversal)));
  }

  // ============================================================================
  // Set Operations
  // ============================================================================

  /**
   * Creates a program that sets a value through a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Person> program =
   *     OpticPrograms.set(person, PersonLenses.age(), 30);
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param value The new value to set
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> set(S source, Lens<S, A> lens, A value) {
    return liftOp(new OpticOp.Set<>(source, lens, value));
  }

  /**
   * Creates a program that sets all values through a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Team> program =
   *     OpticPrograms.setAll(team, playerScores, 100);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param value The new value to set for all focused elements
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> setAll(
      S source, Traversal<S, A> traversal, A value) {
    return liftOp(new OpticOp.SetAll<>(source, traversal, value));
  }

  // ============================================================================
  // Modify Operations
  // ============================================================================

  /**
   * Creates a program that modifies a value through a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Person> program =
   *     OpticPrograms.modify(person, PersonLenses.age(), age -> age + 1);
   * }</pre>
   *
   * @param source The source structure
   * @param lens The lens to focus with
   * @param modifier The function to transform the focused value
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> modify(
      S source, Lens<S, A> lens, Function<A, A> modifier) {
    return liftOp(new OpticOp.Modify<>(source, lens, modifier));
  }

  /**
   * Creates a program that modifies all values through a {@link Traversal}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Team> program =
   *     OpticPrograms.modifyAll(team, playerScores, score -> score + 10);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param modifier The function to transform each focused value
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> modifyAll(
      S source, Traversal<S, A> traversal, Function<A, A> modifier) {
    return liftOp(new OpticOp.ModifyAll<>(source, traversal, modifier));
  }

  // ============================================================================
  // Query Operations
  // ============================================================================

  /**
   * Creates a program that checks if any focused element matches a predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Boolean> program =
   *     OpticPrograms.exists(team, playerAges, age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns true if any element matches
   */
  public static <S, A> Free<OpticOpKind.Witness, Boolean> exists(
      S source, Fold<S, A> fold, Predicate<A> predicate) {
    return liftOp(new OpticOp.Exists<>(source, fold, predicate));
  }

  /**
   * Creates a program that checks if any focused element matches a predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Boolean> program =
   *     OpticPrograms.exists(team, playerAges, age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns true if any element matches
   */
  public static <S, A> Free<OpticOpKind.Witness, Boolean> exists(
      S source, Traversal<S, A> traversal, Predicate<A> predicate) {
    return liftOp(new OpticOp.Exists<>(source, traversalToFold(traversal), predicate));
  }

  /**
   * Creates a program that checks if all focused elements match a predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Boolean> program =
   *     OpticPrograms.all(team, playerAges, age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns true if all elements match
   */
  public static <S, A> Free<OpticOpKind.Witness, Boolean> all(
      S source, Fold<S, A> fold, Predicate<A> predicate) {
    return liftOp(new OpticOp.All<>(source, fold, predicate));
  }

  /**
   * Creates a program that checks if all focused elements match a predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Boolean> program =
   *     OpticPrograms.all(team, playerAges, age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns true if all elements match
   */
  public static <S, A> Free<OpticOpKind.Witness, Boolean> all(
      S source, Traversal<S, A> traversal, Predicate<A> predicate) {
    return liftOp(new OpticOp.All<>(source, traversalToFold(traversal), predicate));
  }

  /**
   * Creates a program that counts the number of focused elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Integer> program =
   *     OpticPrograms.count(team, players);
   * }</pre>
   *
   * @param source The source structure
   * @param fold The optic to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the count
   */
  public static <S, A> Free<OpticOpKind.Witness, Integer> count(S source, Fold<S, A> fold) {
    return liftOp(new OpticOp.Count<>(source, fold));
  }

  /**
   * Creates a program that counts the number of focused elements.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Integer> program =
   *     OpticPrograms.count(team, players);
   * }</pre>
   *
   * @param source The source structure
   * @param traversal The traversal to focus with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the count
   */
  public static <S, A> Free<OpticOpKind.Witness, Integer> count(
      S source, Traversal<S, A> traversal) {
    return liftOp(new OpticOp.Count<>(source, traversalToFold(traversal)));
  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  /**
   * Creates a program that returns a pure value (no optic operation).
   *
   * <p>Useful for conditional logic in programs.
   *
   * <p>Example:
   *
   * <pre>{@code
   * OpticPrograms.get(person, PersonLenses.age())
   *     .flatMap(age -> {
   *         if (age < 18) {
   *             return OpticPrograms.pure(person);  // No changes
   *         } else {
   *             return OpticPrograms.set(person, statusLens, "ADULT");
   *         }
   *     });
   * }</pre>
   *
   * @param value The value to return
   * @param <A> The value type
   * @return A Free monad program that returns the value
   */
  public static <A> Free<OpticOpKind.Witness, A> pure(A value) {
    return Free.pure(value);
  }

  // ============================================================================
  // Focus DSL Operations
  // ============================================================================

  /**
   * Creates a program that gets a value through a {@link FocusPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, String> program =
   *     OpticPrograms.get(person, PersonFocus.name());
   * }</pre>
   *
   * @param source The source structure
   * @param path The focus path to navigate with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the focused value
   */
  public static <S, A> Free<OpticOpKind.Witness, A> get(S source, FocusPath<S, A> path) {
    return get(source, path.toLens());
  }

  /**
   * Creates a program that previews (optionally gets) a value through an {@link AffinePath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Optional<String>> program =
   *     OpticPrograms.preview(config, ConfigFocus.database().via(dbHostLens));
   * }</pre>
   *
   * @param source The source structure
   * @param path The affine path to navigate with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns an Optional of the focused value
   */
  public static <S, A> Free<OpticOpKind.Witness, Optional<A>> preview(
      S source, AffinePath<S, A> path) {
    return preview(source, path.toAffine().asFold());
  }

  /**
   * Creates a program that gets all values through a {@link TraversalPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, List<String>> program =
   *     OpticPrograms.getAll(team, TeamFocus.members().via(nameLens));
   * }</pre>
   *
   * @param source The source structure
   * @param path The traversal path to navigate with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns a list of all focused values
   */
  public static <S, A> Free<OpticOpKind.Witness, List<A>> getAll(
      S source, TraversalPath<S, A> path) {
    return getAll(source, path.toTraversal());
  }

  /**
   * Creates a program that sets a value through a {@link FocusPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Person> program =
   *     OpticPrograms.set(person, PersonFocus.age(), 30);
   * }</pre>
   *
   * @param source The source structure
   * @param path The focus path to navigate with
   * @param value The new value to set
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> set(S source, FocusPath<S, A> path, A value) {
    return set(source, path.toLens(), value);
  }

  /**
   * Creates a program that sets all values through a {@link TraversalPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Team> program =
   *     OpticPrograms.setAll(team, TeamFocus.scores(), 100);
   * }</pre>
   *
   * @param source The source structure
   * @param path The traversal path to navigate with
   * @param value The new value to set for all focused elements
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> setAll(
      S source, TraversalPath<S, A> path, A value) {
    return setAll(source, path.toTraversal(), value);
  }

  /**
   * Creates a program that modifies a value through a {@link FocusPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Person> program =
   *     OpticPrograms.modify(person, PersonFocus.age(), age -> age + 1);
   * }</pre>
   *
   * @param source The source structure
   * @param path The focus path to navigate with
   * @param modifier The function to transform the focused value
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> modify(
      S source, FocusPath<S, A> path, Function<A, A> modifier) {
    return modify(source, path.toLens(), modifier);
  }

  /**
   * Creates a program that modifies all values through a {@link TraversalPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Team> program =
   *     OpticPrograms.modifyAll(team, TeamFocus.scores(), score -> score + 10);
   * }</pre>
   *
   * @param source The source structure
   * @param path The traversal path to navigate with
   * @param modifier The function to transform each focused value
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the updated structure
   */
  public static <S, A> Free<OpticOpKind.Witness, S> modifyAll(
      S source, TraversalPath<S, A> path, Function<A, A> modifier) {
    return modifyAll(source, path.toTraversal(), modifier);
  }

  /**
   * Creates a program that checks if any focused element matches a predicate through a {@link
   * TraversalPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Boolean> program =
   *     OpticPrograms.exists(team, TeamFocus.ages(), age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param path The traversal path to navigate with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns true if any element matches
   */
  public static <S, A> Free<OpticOpKind.Witness, Boolean> exists(
      S source, TraversalPath<S, A> path, Predicate<A> predicate) {
    return exists(source, path.toTraversal(), predicate);
  }

  /**
   * Creates a program that checks if all focused elements match a predicate through a {@link
   * TraversalPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Boolean> program =
   *     OpticPrograms.all(team, TeamFocus.ages(), age -> age >= 18);
   * }</pre>
   *
   * @param source The source structure
   * @param path The traversal path to navigate with
   * @param predicate The predicate to test
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns true if all elements match
   */
  public static <S, A> Free<OpticOpKind.Witness, Boolean> all(
      S source, TraversalPath<S, A> path, Predicate<A> predicate) {
    return all(source, path.toTraversal(), predicate);
  }

  /**
   * Creates a program that counts the number of focused elements through a {@link TraversalPath}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Free<OpticOpKind.Witness, Integer> program =
   *     OpticPrograms.count(team, TeamFocus.players());
   * }</pre>
   *
   * @param source The source structure
   * @param path The traversal path to navigate with
   * @param <S> The source type
   * @param <A> The focused value type
   * @return A Free monad program that returns the count
   */
  public static <S, A> Free<OpticOpKind.Witness, Integer> count(
      S source, TraversalPath<S, A> path) {
    return count(source, path.toTraversal());
  }
}
