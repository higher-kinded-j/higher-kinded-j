// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Traverse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing naming conventions.
 *
 * <p>These rules ensure consistent naming patterns across the codebase:
 *
 * <ul>
 *   <li>Type class implementations follow {@code <TypeName><TypeClass>} pattern
 *   <li>Kind interfaces follow {@code <TypeName>Kind} pattern
 *   <li>Helper classes follow {@code <TypeName>KindHelper} pattern
 * </ul>
 */
@DisplayName("Naming Convention Rules")
class NamingConventionRules {

  private static JavaClasses productionClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
  }

  /**
   * Functor implementations must be named with "Functor" suffix.
   *
   * <p>Examples: MaybeFunctor, EitherFunctor, TryFunctor
   *
   * <p>Note: Traverse implementations (which extend Functor) follow "Traverse" naming. Applicative
   * implementations are also excluded as they follow their own naming pattern.
   */
  @Test
  @DisplayName("Functor implementations should be named <Type>Functor")
  void functor_implementations_should_end_with_functor() {
    classes()
        .that()
        .implement(Functor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotImplement(Applicative.class) // Exclude Applicatives which also implement Functor
        .and()
        .doNotImplement(Traverse.class) // Traverse implementations use Traverse suffix
        .should()
        .haveSimpleNameEndingWith("Functor")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Monad implementations must be named with "Monad" suffix.
   *
   * <p>Examples: MaybeMonad, EitherMonad, ListMonad
   *
   * <p>Note: Selective implementations (which extend Monad) follow the "Selective" naming
   * convention. Alternative implementations (which extend Applicative, and may extend Monad) follow
   * the "Alternative" naming convention.
   */
  @Test
  @DisplayName("Monad implementations should be named <Type>Monad")
  void monad_implementations_should_end_with_monad() {
    classes()
        .that()
        .implement(Monad.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotImplement(Selective.class) // Selective implementations use Selective suffix
        .and()
        .doNotImplement(Alternative.class) // Alternative implementations use Alternative suffix
        .should()
        .haveSimpleNameEndingWith("Monad")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Applicative implementations must be named with "Applicative" suffix.
   *
   * <p>Examples: MaybeApplicative, EitherApplicative
   *
   * <p>Note: Classes that implement Monad (which extends Applicative) are excluded as they follow
   * the Monad naming convention. Selective and Alternative implementations are also excluded.
   * Anonymous and inner classes are also excluded.
   */
  @Test
  @DisplayName("Applicative implementations should be named <Type>Applicative")
  void applicative_implementations_should_end_with_applicative() {
    classes()
        .that()
        .implement(Applicative.class)
        .and()
        .areNotInterfaces()
        .and()
        .areNotAnonymousClasses()
        .and()
        .areNotMemberClasses() // Exclude inner classes
        .and()
        .doNotImplement(Monad.class) // Monads are named with Monad suffix
        .and()
        .doNotImplement(Selective.class) // Selective implementations use Selective suffix
        .and()
        .doNotImplement(Alternative.class) // Alternative implementations use Alternative suffix
        .should()
        .haveSimpleNameEndingWith("Applicative")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Alternative implementations must be named with "Alternative" suffix.
   *
   * <p>Examples: VStreamAlternative
   *
   * <p>Note: Classes that implement Alternative via MonadZero (which bundles Monad + Alternative
   * into a single class named with "Monad" suffix) are excluded since they follow the Monad naming
   * convention. Selective implementations that inherit Alternative transitively (via extending a
   * MonadZero class) are also excluded since they follow the Selective naming convention.
   */
  @Test
  @DisplayName("Alternative implementations should be named <Type>Alternative")
  void alternative_implementations_should_end_with_alternative() {
    classes()
        .that()
        .implement(Alternative.class)
        .and()
        .areNotInterfaces()
        .and()
        .haveSimpleNameNotEndingWith("Monad") // MonadZero classes use Monad suffix
        .and()
        .doNotImplement(Selective.class) // Selective implementations use Selective suffix
        .should()
        .haveSimpleNameEndingWith("Alternative")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Foldable implementations must be named with "Foldable" suffix.
   *
   * <p>Examples: MaybeFoldable, ListFoldable
   *
   * <p>Note: Classes implementing Traverse (which extends Foldable) follow Traverse naming.
   */
  @Test
  @DisplayName("Foldable implementations should be named <Type>Foldable")
  void foldable_implementations_should_end_with_foldable() {
    classes()
        .that()
        .implement(Foldable.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotImplement(Traverse.class) // Traverse implementations use Traverse suffix
        .should()
        .haveSimpleNameEndingWith("Foldable")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Traverse implementations must be named with "Traverse" suffix.
   *
   * <p>Examples: MaybeTraverse, ListTraverse, EitherTraverse
   */
  @Test
  @DisplayName("Traverse implementations should be named <Type>Traverse")
  void traverse_implementations_should_end_with_traverse() {
    classes()
        .that()
        .implement(Traverse.class)
        .and()
        .areNotInterfaces()
        .should()
        .haveSimpleNameEndingWith("Traverse")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * KindHelper classes must end with "KindHelper" suffix.
   *
   * <p>These classes provide widen() and narrow() conversion utilities.
   */
  @Test
  @DisplayName("Kind helper classes should be named <Type>KindHelper")
  void kind_helper_classes_should_end_with_kind_helper() {
    classes()
        .that()
        .haveSimpleNameContaining("KindHelper")
        .should()
        .haveSimpleNameEndingWith("KindHelper")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Classes with "Kind" in name (but not KindHelper) should end with "Kind" or "Kind2".
   *
   * <p>These are the Kind interface implementations for each type. Kind2 is used for types with two
   * type parameters (like Either&lt;L, R&gt;, Tuple2&lt;A, B&gt;).
   */
  @Test
  @DisplayName("Kind interfaces should be named <Type>Kind")
  void kind_interfaces_should_end_with_kind() {
    classes()
        .that()
        .haveSimpleNameContaining("Kind")
        .and()
        .areInterfaces()
        .and()
        .haveSimpleNameNotContaining("KindHelper")
        .should()
        .haveSimpleNameEndingWith("Kind")
        .orShould()
        .haveSimpleNameEndingWith("Kind2") // Kind2 for types with two type parameters
        .orShould()
        .haveSimpleName("Kind") // The base Kind interface itself
        .allowEmptyShould(true)
        .check(productionClasses);
  }
}
