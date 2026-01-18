// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.optics.indexed.Pair;

/**
 * A **Lens** is an optic that provides a focused view into a part of a data structure. Think of it
 * as a functional, composable "magnifying glass" 🔎 for a field that is guaranteed to exist.
 *
 * <p>A Lens is the right tool for "has-a" relationships, such as a field within a record or a
 * property of a class (e.g., a {@code User} has an {@code Address}). It is defined by two core,
 * well-behaved operations: getting the part, and setting the part in an immutable way.
 *
 * <p>It extends the generic {@link Optic}, specializing it for {@code S = T} and {@code A = B}.
 *
 * @param <S> The source type of the whole structure (e.g., {@code User}).
 * @param <A> The target type of the focused part (e.g., {@code Address}).
 */
public interface Lens<S, A> extends Optic<S, S, A, A> {

  /**
   * Gets the focused part {@code A} from the whole structure {@code S}.
   *
   * @param source The whole structure.
   * @return The focused part.
   */
  A get(S source);

  /**
   * Sets a new value for the focused part {@code A}, returning a new, updated structure {@code S}.
   *
   * <p>This operation must be immutable; the original {@code source} object is not changed.
   *
   * @param newValue The new value for the focused part.
   * @param source The original structure.
   * @return A new structure with the focused part updated.
   */
  S set(A newValue, S source);

  /**
   * Modifies the focused part {@code A} using a pure function.
   *
   * <p>This is a convenient shortcut for {@code set(modifier.apply(get(source)), source)}.
   *
   * @param modifier The function to apply to the focused part.
   * @param source The whole structure.
   * @return A new structure with the modified part.
   */
  default S modify(Function<A, A> modifier, S source) {
    return set(modifier.apply(get(source)), source);
  }

  /**
   * Modifies the focused part {@code A} with a function that returns a new value within an
   * effectful context {@code F} (a {@link Functor}).
   *
   * <p>This is the core effectful operation, allowing for updates that might be asynchronous (e.g.,
   * {@code CompletableFuture}) or failable (e.g., {@code Optional}).
   *
   * @param <F> The witness type for the {@link Functor} context.
   * @param f The function to apply, returning the new part in a context.
   * @param source The whole structure.
   * @param functor The {@link Functor} instance for the context {@code F}.
   * @return The updated structure {@code S}, itself wrapped in the context {@code F}.
   */
  <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S source, Functor<F> functor);

  /**
   * {@inheritDoc}
   *
   * <p>This default implementation satisfies the {@link Optic} interface by delegating to the
   * {@link Functor}-based {@code modifyF} method, as every {@link Applicative} is also a {@link
   * Functor}.
   */
  @Override
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
    return this.modifyF(f, s, (Functor<F>) app);
  }

  /**
   * Views this {@code Lens} as a {@link Traversal}.
   *
   * <p>This is always possible because a {@code Lens} is fundamentally a {@code Traversal} that
   * focuses on exactly one element.
   *
   * @return A {@link Traversal} that represents this {@code Lens}.
   */
  default Traversal<S, A> asTraversal() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        return Lens.this.modifyF(f, s, app);
      }
    };
  }

  /**
   * Views this {@code Lens} as a {@link Fold}.
   *
   * <p>This is always possible because a {@code Lens} is a read-only query that focuses on exactly
   * one element.
   *
   * @return A {@link Fold} that represents this {@code Lens}.
   */
  default Fold<S, A> asFold() {
    Lens<S, A> self = this;
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        return f.apply(self.get(source));
      }
    };
  }

  /**
   * Composes this {@code Lens<S, A>} with another {@code Lens<A, B>} to create a new {@code Lens<S,
   * B>}.
   *
   * <p>This specialized version is kept for efficiency and to ensure the result is correctly and
   * conveniently typed as a {@code Lens}.
   *
   * @param other The {@link Lens} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Lens} that focuses from {@code S} to {@code B}.
   */
  default <B> Lens<S, B> andThen(Lens<A, B> other) {
    Lens<S, A> self = this;
    return new Lens<>() {
      @Override
      public B get(S source) {
        return other.get(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        return self.set(other.set(newValue, self.get(source)), source);
      }

      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<B, Kind<F, B>> f, S source, Functor<F> functor) {
        return self.modifyF(a -> other.modifyF(f, a, functor), source, functor);
      }
    };
  }

  /**
   * Composes this {@code Lens<S, A>} with an {@code Iso<A, B>} to produce a new {@code Lens<S, B>}.
   *
   * <p>This is possible because composing a one-way focus with a lossless, two-way conversion
   * results in a new one-way focus. This specialized overload ensures the result is correctly and
   * conveniently typed as a {@link Lens}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Domain model
   * record User(String name, EmailAddress email) {}
   * record EmailAddress(String value) {}
   *
   * // Lens from User to EmailAddress
   * Lens<User, EmailAddress> emailLens = Lens.of(
   *     User::email,
   *     (user, email) -> new User(user.name(), email)
   * );
   *
   * // Iso from EmailAddress to String
   * Iso<EmailAddress, String> emailIso = Iso.of(
   *     EmailAddress::value,
   *     EmailAddress::new
   * );
   *
   * // Compose: Lens + Iso = Lens
   * Lens<User, String> userEmailStringLens = emailLens.andThen(emailIso);
   * }</pre>
   *
   * @param iso The {@link Iso} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Lens} that focuses from {@code S} to {@code B}.
   */
  default <B> Lens<S, B> andThen(final Iso<A, B> iso) {
    Lens<S, A> self = this;
    return Lens.of(s -> iso.get(self.get(s)), (s, b) -> self.set(iso.reverseGet(b), s));
  }

  /**
   * Composes this {@code Lens<S, A>} with a {@code Prism<A, B>} to create an {@code Affine<S, B>}.
   *
   * <p>The composition follows the standard optic composition rule: Lens >>> Prism = Affine. The
   * result is an Affine because the Prism may not match (resulting in zero-or-one focus), but the
   * Lens guarantees we can always set a value.
   *
   * <p>This composition is useful when navigating into optional parts of a structure:
   *
   * <ul>
   *   <li>A record field that contains a sum type (sealed interface)
   *   <li>A nullable field wrapped in Optional
   *   <li>Any "has-a" followed by "is-a" relationship
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Domain model
   * record Config(Optional<DatabaseConfig> database) {}
   * record DatabaseConfig(String url, int port) {}
   *
   * // Optics
   * Lens<Config, Optional<DatabaseConfig>> databaseLens = Lens.of(
   *     Config::database,
   *     (config, db) -> new Config(db)
   * );
   * Prism<Optional<DatabaseConfig>, DatabaseConfig> somePrism = Prisms.some();
   * Lens<DatabaseConfig, String> urlLens = Lens.of(
   *     DatabaseConfig::url,
   *     (db, url) -> new DatabaseConfig(url, db.port())
   * );
   *
   * // Compose: Lens >>> Prism = Affine
   * Affine<Config, DatabaseConfig> dbAffine = databaseLens.andThen(somePrism);
   *
   * // Use the affine
   * Config config = new Config(Optional.of(new DatabaseConfig("localhost", 5432)));
   * Optional<DatabaseConfig> db = dbAffine.getOptional(config);
   * // Returns Optional[DatabaseConfig[url=localhost, port=5432]]
   *
   * Config emptyConfig = new Config(Optional.empty());
   * Optional<DatabaseConfig> empty = dbAffine.getOptional(emptyConfig);
   * // Returns Optional.empty()
   *
   * // Can chain further with another lens
   * Affine<Config, String> urlAffine = dbAffine.andThen(urlLens);
   * }</pre>
   *
   * @param prism The {@link Prism} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(final Prism<A, B> prism) {
    Lens<S, A> self = this;
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return prism.getOptional(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        return self.set(prism.build(newValue), source);
      }
    };
  }

  /**
   * Composes this {@code Lens<S, A>} with an {@code Affine<A, B>} to create an {@code Affine<S,
   * B>}.
   *
   * <p>The result is an Affine because the inner Affine may not find a value (zero-or-one focus).
   *
   * @param affine The {@link Affine} to compose with.
   * @param <B> The type of the final focused part.
   * @return A new {@link Affine} that focuses from {@code S} to {@code B}.
   */
  default <B> Affine<S, B> andThen(final Affine<A, B> affine) {
    Lens<S, A> self = this;
    return new Affine<>() {
      @Override
      public Optional<B> getOptional(S source) {
        return affine.getOptional(self.get(source));
      }

      @Override
      public S set(B newValue, S source) {
        A a = self.get(source);
        return self.set(affine.set(newValue, a), source);
      }
    };
  }

  /**
   * Composes this {@code Lens<S, A>} with a {@code Traversal<A, B>} to create a {@code Traversal<S,
   * B>}.
   *
   * <p>The composition follows the standard optic composition rule: Lens >>> Traversal = Traversal.
   * The result is a Traversal because the inner Traversal may focus on zero or more elements.
   *
   * <p>This composition is useful when navigating into collections or multiple targets:
   *
   * <ul>
   *   <li>A record field that contains a list
   *   <li>A field containing multiple optional values
   *   <li>Any "has-a" followed by "has-many" relationship
   * </ul>
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Domain model
   * record Team(String name, List<Player> players) {}
   * record Player(String name, int score) {}
   *
   * // Optics
   * Lens<Team, List<Player>> playersLens = Lens.of(
   *     Team::players,
   *     (team, players) -> new Team(team.name(), players)
   * );
   * Traversal<List<Player>, Player> allPlayers = Traversals.forList();
   *
   * // Compose: Lens >>> Traversal = Traversal
   * Traversal<Team, Player> teamPlayersTraversal = playersLens.andThen(allPlayers);
   *
   * // Use the traversal
   * Team team = new Team("Red", List.of(new Player("Alice", 100), new Player("Bob", 85)));
   * List<Player> players = Traversals.getAll(teamPlayersTraversal, team);  // [Alice, Bob]
   * }</pre>
   *
   * @param traversal The {@link Traversal} to compose with.
   * @param <B> The type of the final focused parts.
   * @return A new {@link Traversal} that focuses from {@code S} to {@code B}.
   */
  default <B> Traversal<S, B> andThen(final Traversal<A, B> traversal) {
    Lens<S, A> self = this;
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<B, Kind<F, B>> f, S source, Applicative<F> app) {
        A a = self.get(source);
        Kind<F, A> modifiedA = traversal.modifyF(f, a, app);
        return app.map(newA -> self.set(newA, source), modifiedA);
      }
    };
  }

  /**
   * Creates a {@code Lens} from its two fundamental operations: a getter and a setter.
   *
   * @param getter A function to extract the part {@code A} from the structure {@code S}.
   * @param setter A function to immutably update the part {@code A} within the structure {@code S}.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the focused part.
   * @return A new {@code Lens} instance.
   */
  static <S, A> Lens<S, A> of(Function<S, A> getter, BiFunction<S, A, S> setter) {
    return new Lens<>() {
      @Override
      public A get(S source) {
        return getter.apply(source);
      }

      @Override
      public S set(A newValue, S source) {
        return setter.apply(source, newValue);
      }

      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
          Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
        Kind<F, A> fa = f.apply(this.get(source));
        return functor.map(a -> this.set(a, source), fa);
      }
    };
  }

  /**
   * Combines two lenses focusing on different parts of the same source into a single lens focusing
   * on a pair of both parts.
   *
   * <p>This enables atomic updates of coupled fields that participate in invariants. The {@code
   * reconstructor} function is called with both new values simultaneously, avoiding invalid
   * intermediate states that would occur with sequential updates.
   *
   * <p><b>The Problem:</b> When fields are coupled by invariants (e.g., {@code lo <= hi} in a
   * range), sequential lens updates can create invalid intermediate states:
   *
   * <pre>{@code
   * record Range(int lo, int hi) {
   *     Range { if (lo > hi) throw new IllegalArgumentException(); }
   * }
   *
   * // Sequential update fails:
   * // Range(1, 2) -> set lo=11 -> Range(11, 2) THROWS!
   * }</pre>
   *
   * <p><b>The Solution:</b> Use {@code paired} to update both fields atomically:
   *
   * <pre>{@code
   * Lens<Range, Integer> loLens = Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));
   * Lens<Range, Integer> hiLens = Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));
   *
   * // Create paired lens with atomic reconstruction
   * Lens<Range, Pair<Integer, Integer>> boundsLens = Lens.paired(
   *     loLens,
   *     hiLens,
   *     (r, lo, hi) -> new Range(lo, hi)  // Called once with final values
   * );
   *
   * // Shift safely - no intermediate state
   * Range shifted = boundsLens.modify(
   *     p -> Pair.of(p.first() + 10, p.second() + 10),
   *     new Range(1, 2)
   * );
   * // Result: Range(11, 12)
   * }</pre>
   *
   * @param first The lens focusing on the first part {@code A}.
   * @param second The lens focusing on the second part {@code B}.
   * @param reconstructor Function to atomically rebuild {@code S} from both values. Receives the
   *     original source (for accessing other fields not covered by the lenses) plus both new
   *     values.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the first focused part.
   * @param <B> The type of the second focused part.
   * @return A lens focusing on a {@link Pair} of both parts.
   */
  static <S, A, B> Lens<S, Pair<A, B>> paired(
      Lens<S, A> first, Lens<S, B> second, Function3<S, A, B, S> reconstructor) {
    return Lens.of(
        s -> Pair.of(first.get(s), second.get(s)),
        (s, pair) -> reconstructor.apply(s, pair.first(), pair.second()));
  }

  /**
   * Combines two lenses focusing on different parts of the same source into a single lens focusing
   * on a pair of both parts, using a simple constructor.
   *
   * <p>This is a convenience overload for when the two focused fields fully determine the structure
   * (i.e., there are no other fields to preserve). Use this when {@code S} can be reconstructed
   * from just {@code A} and {@code B}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * record Range(int lo, int hi) {
   *     Range { if (lo > hi) throw new IllegalArgumentException(); }
   * }
   *
   * Lens<Range, Integer> loLens = Lens.of(Range::lo, (r, lo) -> new Range(lo, r.hi()));
   * Lens<Range, Integer> hiLens = Lens.of(Range::hi, (r, hi) -> new Range(r.lo(), hi));
   *
   * // Simple form - Range has only lo and hi fields
   * Lens<Range, Pair<Integer, Integer>> boundsLens = Lens.paired(
   *     loLens,
   *     hiLens,
   *     Range::new  // Constructor reference
   * );
   *
   * // Scale the range
   * Range doubled = boundsLens.modify(
   *     p -> Pair.of(p.first() * 2, p.second() * 2),
   *     new Range(5, 10)
   * );
   * // Result: Range(10, 20)
   * }</pre>
   *
   * @param first The lens focusing on the first part {@code A}.
   * @param second The lens focusing on the second part {@code B}.
   * @param constructor Function to construct {@code S} from both values.
   * @param <S> The type of the whole structure.
   * @param <A> The type of the first focused part.
   * @param <B> The type of the second focused part.
   * @return A lens focusing on a {@link Pair} of both parts.
   * @see #paired(Lens, Lens, Function3) for when other fields need to be preserved
   */
  static <S, A, B> Lens<S, Pair<A, B>> paired(
      Lens<S, A> first, Lens<S, B> second, BiFunction<A, B, S> constructor) {
    return paired(first, second, (s, a, b) -> constructor.apply(a, b));
  }

  /**
   * Conditionally set a new value based on a predicate. Returns the original structure if the
   * predicate is false.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Only update email if it's valid
   * Kind<F, User> result = emailLens.setIf(
   *   email -> email.contains("@"),
   *   "newemail@example.com",
   *   user,
   *   selective
   * );
   * }</pre>
   */
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> setIf(
      Predicate<? super A> predicate, A newValue, S source, Selective<F> selective) {
    return selective.ifS(
        selective.of(predicate.test(newValue)),
        selective.of(set(newValue, source)),
        selective.of(source));
  }

  /**
   * Modify the value only when the current value meets a condition. Useful for conditional field
   * updates based on current state.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Only increment counter if below threshold
   * Kind<F, Stats> result = counterLens.modifyWhen(
   *   count -> count < 100,
   *   count -> count + 1,
   *   stats,
   *   selective
   * );
   * }</pre>
   */
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyWhen(
      Predicate<? super A> shouldModify,
      Function<A, A> modifier,
      S source,
      Selective<F> selective) {
    A current = get(source);
    return selective.ifS(
        selective.of(shouldModify.test(current)),
        selective.of(set(modifier.apply(current), source)),
        selective.of(source));
  }

  /**
   * Branch between two modification strategies based on current value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Different processing for positive/negative values
   * Kind<F, Account> result = balanceLens.modifyBranch(
   *   balance -> balance > 0,
   *   balance -> applyInterest(balance),
   *   balance -> applyOverdraftFee(balance),
   *   account,
   *   selective
   * );
   * }</pre>
   */
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyBranch(
      Predicate<? super A> predicate,
      Function<A, A> thenModifier,
      Function<A, A> elseModifier,
      S source,
      Selective<F> selective) {
    A current = get(source);
    return selective.ifS(
        selective.of(predicate.test(current)),
        selective.of(set(thenModifier.apply(current), source)),
        selective.of(set(elseModifier.apply(current), source)));
  }
}
