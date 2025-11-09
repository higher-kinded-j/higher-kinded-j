// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.Function;
import org.jspecify.annotations.NullMarked;

/**
 * The Traverse type class represents data structures that can be traversed from left to right,
 * performing an Applicative (or Monadic) effect for each element and collecting the results.
 *
 * <p>Traverse combines the capabilities of {@link Functor} and {@link Foldable}, allowing you to
 * both map over a structure with an effectful function and collect the results into the same
 * structure shape. Unlike {@code map}, which applies a pure function, {@code traverse} applies a
 * function that produces effects and ensures those effects are properly sequenced according to the
 * {@link Applicative} being used.
 *
 * <h2>Key Operations</h2>
 *
 * <ul>
 *   <li>{@link #traverse(Applicative, Function, Kind)}: The fundamental operation that maps an
 *       effectful function over a structure and sequences the effects.
 *   <li>{@link #sequenceA(Applicative, Kind)}: A specialized version of traverse that "flips" a
 *       structure of effects into an effect of a structure.
 * </ul>
 *
 * <h2>Common Use Cases</h2>
 *
 * <pre>{@code
 * // Example 1: Validating a list of inputs
 * // Given: List<String> userInputs and a function String -> Validation<Error, Integer>
 * Traverse<ListKind.Witness> listTraverse = ...;
 * Kind<ValidationKind.Witness, Kind<ListKind.Witness, Integer>> result =
 *     listTraverse.traverse(validationApplicative, validateAndParse, userInputs);
 * // Result: Either all inputs are valid (Right(List<Integer>)) or
 * //         accumulates all validation errors (Left(List<Error>))
 *
 * // Example 2: Fetching data for a collection of IDs
 * // Given: List<UserId> ids and a function UserId -> IO<User>
 * Kind<IO.Witness, Kind<ListKind.Witness, User>> users =
 *     listTraverse.traverse(ioApplicative, id -> fetchUser(id), ids);
 * // Result: A single IO action that, when executed, fetches all users
 *
 * // Example 3: Sequencing a list of effects
 * // Given: List<IO<String>> effects
 * Kind<IO.Witness, Kind<ListKind.Witness, String>> sequenced =
 *     listTraverse.sequenceA(ioApplicative, effects);
 * // Result: A single IO action that executes all effects in sequence
 * }</pre>
 *
 * <h2>Relationship to Other Type Classes</h2>
 *
 * <ul>
 *   <li>Extends {@link Functor}: Every Traverse can map pure functions over its structure.
 *   <li>Extends {@link Foldable}: Every Traverse can fold its elements into a summary value.
 *   <li>More powerful than Functor: Can sequence effects, not just apply pure functions.
 *   <li>More structured than Foldable: Preserves the structure's shape during traversal.
 * </ul>
 *
 * <h2>Laws</h2>
 *
 * <p>A Traverse instance must satisfy the following laws to ensure predictable and composable
 * behaviour:
 *
 * <ol>
 *   <li><b>Naturality:</b> For any applicative transformation {@code t} (natural transformation
 *       between applicatives {@code F} and {@code G}):
 *       <pre>{@code
 * t.compose(traverse(appF, f, ta)) == traverse(appG, t.compose(f), ta)
 * }</pre>
 *       This law states that transforming between applicative contexts can happen either before or
 *       after traversal with the same result.
 *   <li><b>Identity:</b> Traversing with the Identity applicative and the identity function is
 *       itself the identity:
 *       <pre>{@code
 * traverse(Identity.applicative(), a -> Identity.of(a), ta) == Identity.of(ta)
 * }</pre>
 *       This ensures that traverse doesn't add unnecessary structure or effects.
 *   <li><b>Composition:</b> Traversing with a composed applicative is the same as traversing twice:
 *       <pre>{@code
 * traverse(Compose(F, G), f, ta) == F.map(traverse(G, _, ta), traverse(F, f, ta))
 * }</pre>
 *       Where {@code Compose(F, G)} represents the composition of two applicatives. This law
 *       ensures that nested traversals can be fused into a single traversal.
 * </ol>
 *
 * <p>These laws ensure that:
 *
 * <ul>
 *   <li>Traversals behave predictably regardless of the applicative effect being used
 *   <li>Traversals compose properly with other traversals
 *   <li>Traversals can be optimised through fusion
 *   <li>The order of effects is deterministic (left-to-right)
 * </ul>
 *
 * <h2>Design Notes</h2>
 *
 * <p>The traverse operation's power comes from its interaction with different {@link Applicative}
 * instances:
 *
 * <ul>
 *   <li><b>With {@code Option}/{@code Maybe}:</b> Returns {@code Some(structure)} if all
 *       applications succeed, {@code None} if any fails (fail-fast).
 *   <li><b>With {@code Validation}:</b> Accumulates all errors, allowing you to see all validation
 *       failures at once.
 *   <li><b>With {@code IO}/{@code Future}:</b> Sequences side effects, creating a single action
 *       that performs all effects.
 *   <li><b>With {@code List}:</b> Generates all possible combinations (cartesian product).
 *   <li><b>With {@code Const} (constant applicative):</b> Allows for pure folds without rebuilding
 *       the structure.
 * </ul>
 *
 * <h2>Implementation Guidelines</h2>
 *
 * <p>When implementing Traverse for a custom data structure:
 *
 * <ol>
 *   <li>Ensure elements are traversed left-to-right consistently
 *   <li>Use the applicative's {@code ap} and {@code of} operations to sequence effects
 *   <li>Preserve the structure's shape in the result
 *   <li>Consider implementing efficient versions of {@code sequenceA} and {@code foldMap} when
 *       possible
 * </ol>
 *
 * <h2>Performance Considerations</h2>
 *
 * <p>The default implementation of {@link #sequenceA(Applicative, Kind)} delegates to {@code
 * traverse} with an identity function. For some data structures, a specialized implementation may
 * be more efficient. Similarly, {@code foldMap} can often be implemented more efficiently than a
 * general traverse followed by extraction.
 *
 * @param <T> The type constructor of the traversable data structure (e.g., {@code
 *     ListKind.Witness}, {@code TreeKind.Witness}, {@code OptionKind.Witness}).
 * @see Applicative
 * @see Functor
 * @see Foldable
 */
@NullMarked
public interface Traverse<T> extends Functor<T>, Foldable<T> {

  /**
   * Traverses this structure from left to right, applying an effectful function {@code f} to each
   * element {@code A}, and collecting the results into the same structure shape, all within an
   * {@link Applicative} context {@code G}.
   *
   * <p>This is the fundamental operation of the Traverse type class. It combines mapping and
   * sequencing: applying a function that produces effects to each element, and then "sequencing"
   * those effects according to the rules of the applicative being used.
   *
   * <p><b>Behaviour varies by Applicative:</b>
   *
   * <ul>
   *   <li><b>Optional/Maybe:</b> Short-circuits on the first {@code None} encountered, otherwise
   *       returns {@code Some} of the mapped structure.
   *   <li><b>Validation:</b> Accumulates all errors using the validation's semigroup, or returns a
   *       valid result if all applications succeed.
   *   <li><b>IO/Future:</b> Creates a single composite effect that, when executed, runs all effects
   *       in sequence and collects results.
   *   <li><b>List:</b> Generates all possible combinations (cartesian product) of results.
   *   <li><b>Either:</b> Returns the first error encountered (left), or the successfully mapped
   *       structure (right).
   * </ul>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Validate a list of strings, converting each to an integer
   * Traverse<ListKind.Witness> listTraverse = ...;
   * Applicative<ValidationKind.Witness> validationApp = ...;
   *
   * Function<String, Kind<ValidationKind.Witness, Integer>> parseAndValidate = s -> {
   *   try {
   *     int value = Integer.parseInt(s);
   *     return value > 0 ? valid(value) : invalid("Must be positive");
   *   } catch (NumberFormatException e) {
   *     return invalid("Not a number: " + s);
   *   }
   * };
   *
   * Kind<ListKind.Witness, String> inputs = ListKind.of("1", "2", "abc", "-5");
   * Kind<ValidationKind.Witness, Kind<ListKind.Witness, Integer>> result =
   *     listTraverse.traverse(validationApp, parseAndValidate, inputs);
   * // Result: Invalid with errors ["Not a number: abc", "Must be positive"]
   * }</pre>
   *
   * <p><b>Key Properties:</b>
   *
   * <ul>
   *   <li>Preserves the structure's shape (same number and arrangement of elements)
   *   <li>Processes elements left-to-right
   *   <li>Effects are sequenced according to the applicative's rules
   *   <li>Type-safe: The compiler ensures all effects are handled
   * </ul>
   *
   * @param <G> The type constructor of the applicative effect (e.g., {@code IO.Witness}, {@code
   *     ValidationKind.Witness}, {@code OptionKind.Witness}).
   * @param <A> The type of elements in the input structure {@code ta}.
   * @param <B> The type of elements in the output structure, after applying the effectful function
   *     {@code f}.
   * @param applicative The {@link Applicative} instance for the effect type {@code G}, which
   *     determines how effects are sequenced and combined. Must not be null.
   * @param f A function from {@code A} to {@code Kind<G, B>}, producing an effectful computation
   *     for each element. The function must not be null. The wildcard bounds allow for flexible
   *     variance: the function can accept any supertype of {@code A} and return any subtype of
   *     {@code B} wrapped in the effect {@code G}.
   * @param ta The traversable structure {@code Kind<T, A>} to traverse (e.g., a {@code List<A>}, a
   *     {@code Tree<A>}). Must not be null.
   * @return A {@code Kind<G, Kind<T, B>>}, which represents the structure {@code T} containing
   *     elements of type {@code B}, all wrapped in the applicative effect {@code G}. For example:
   *     {@code IO<List<User>>}, {@code Validation<Error, Tree<Int>>}, or {@code
   *     Optional<Set<String>>}. Guaranteed to be non-null.
   * @throws NullPointerException if {@code applicative}, {@code f}, or {@code ta} is null
   *     (implementation-dependent).
   */
  <G, A, B> Kind<G, Kind<T, B>> traverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<T, A> ta);

  /**
   * Sequences a structure of applicative effects {@code Kind<T, Kind<G, A>>} into an applicative
   * effect of a structure {@code Kind<G, Kind<T, A>>}.
   *
   * <p>This operation "flips" the nesting of type constructors, taking a structure (like a List)
   * where each element is an effect (like an IO action), and turning it into a single effect that
   * produces the structure of results.
   *
   * <p>This is a specialized version of {@link #traverse(Applicative, Function, Kind)} where the
   * function {@code f} is the identity function (i.e., the effects are already present in the
   * structure, we just need to sequence them).
   *
   * <p><b>Common Use Cases:</b>
   *
   * <ul>
   *   <li>Executing a collection of IO actions and collecting their results
   *   <li>Validating a collection of validations and accumulating errors
   *   <li>Converting {@code List<Optional<T>>} to {@code Optional<List<T>>}
   *   <li>Converting {@code Tree<Future<T>>} to {@code Future<Tree<T>>}
   * </ul>
   *
   * <p><b>Example Usage:</b>
   *
   * <pre>{@code
   * // Execute multiple database queries and collect results
   * Traverse<ListKind.Witness> listTraverse = ...;
   * Applicative<IO.Witness> ioApp = ...;
   *
   * // A list of IO actions
   * Kind<ListKind.Witness, Kind<IO.Witness, User>> queries = ListKind.of(
   *     fetchUser(1),
   *     fetchUser(2),
   *     fetchUser(3)
   * );
   *
   * // Sequence into a single IO action
   * Kind<IO.Witness, Kind<ListKind.Witness, User>> allUsers =
   *     listTraverse.sequenceA(ioApp, queries);
   * // When executed, performs all queries in sequence and returns List<User>
   *
   * // Example with Optional: fail-fast semantics
   * Kind<ListKind.Witness, Kind<OptionKind.Witness, Integer>> maybeInts = ListKind.of(
   *     Optional.of(1),
   *     Optional.empty(),  // This causes the whole thing to be None
   *     Optional.of(3)
   * );
   * Kind<OptionKind.Witness, Kind<ListKind.Witness, Integer>> result =
   *     listTraverse.sequenceA(optionApp, maybeInts);
   * // Result: Optional.empty() because one element was empty
   * }</pre>
   *
   * <p><b>Relationship to traverse:</b>
   *
   * <pre>{@code
   * sequenceA(app, tga) == traverse(app, identity, tga)
   * }</pre>
   *
   * where {@code identity} is the function {@code ga -> ga}.
   *
   * <p><b>Type Transformation:</b>
   *
   * <pre>
   * Structure<Effect<A>> ──sequenceA──> Effect<Structure<A>>
   *
   * Examples:
   * List<Optional<Int>>  ──>  Optional<List<Int>>
   * Tree<IO<String>>     ──>  IO<Tree<String>>
   * Set<Validation<E,A>> ──>  Validation<E, Set<A>>
   * </pre>
   *
   * @param <G> The type constructor of the applicative effect that is currently nested inside the
   *     structure.
   * @param <A> The type of elements wrapped in the effect {@code G} within the structure {@code T}.
   * @param applicative The {@link Applicative} instance for the effect type {@code G}, which
   *     determines how the effects are sequenced. Must not be null.
   * @param tga The traversable structure where each element is already an applicative effect:
   *     {@code Kind<T, Kind<G, A>>}. For example, a {@code List<IO<String>>} or {@code
   *     Tree<Optional<Int>>}. Must not be null.
   * @return An applicative effect {@code Kind<G, Kind<T, A>>} that, when "run" or "extracted",
   *     produces the structure of results. For example, an {@code IO<List<String>>} or {@code
   *     Optional<Tree<Int>>}. Guaranteed to be non-null.
   * @throws NullPointerException if {@code applicative} or {@code tga} is null
   *     (implementation-dependent).
   */
  default <G, A> Kind<G, Kind<T, A>> sequenceA(
      Applicative<G> applicative, Kind<T, Kind<G, A>> tga) {
    // Implementation using traverse with identity function
    // The cast for '? extends A' to 'A' is generally safe here due to how sequence is used.
    // The function f is A -> Kind<G, A>, where A is Kind<G,A> from tga.
    // So it becomes Kind<G,A> -> Kind<G, Kind<G,A>> which is not what we want.
    // The A in traverse's f: A -> Kind<G,B> is the inner A of Kind<T,A>
    // Here, A is Kind<G,A_val> for tga :: Kind<T, Kind<G,A_val>>.
    // So f is (Kind<G, A_val> element) -> (Kind<G, A_val> element)
    // B becomes A_val.
    // The result of traverse is Kind<G, Kind<T, A_val>>
    return traverse(applicative, (Kind<G, A> ga) -> ga, tga);
  }
}
