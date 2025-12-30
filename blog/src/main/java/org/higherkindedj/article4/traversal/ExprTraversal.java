// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.traversal;

import java.util.function.Function;
import java.util.function.Predicate;

import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.hkt.*;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Traversals for the expression AST using higher-kinded-j.
 *
 * <p>This class provides {@link Traversal} implementations for working with expression trees. The
 * traversals are effect-polymorphic, meaning the same traversal can be used with different
 * computational contexts via the {@code modifyF} method:
 *
 * <ul>
 *   <li>Pure transformations with Identity
 *   <li>Fallible transformations with Optional or Either
 *   <li>Error-accumulating transformations with Validated
 * </ul>
 *
 * <p>The key operations are:
 *
 * <ul>
 *   <li>{@link Traversals#getAll} — extract all focused elements
 *   <li>{@link Traversals#modify} — apply a pure transformation to all focused elements
 *   <li>{@link Traversal#modifyF} — apply an effectful transformation
 * </ul>
 */
public final class ExprTraversal {

  private ExprTraversal() {}

  /**
   * A traversal targeting all immediate children of an expression.
   *
   * <p>This traversal does not descend recursively—it only visits direct children:
   *
   * <ul>
   *   <li>{@link Literal} and {@link Variable} have no children (zero elements)
   *   <li>{@link Binary} has two children: left and right
   *   <li>{@link Conditional} has three children: cond, thenBranch, elseBranch
   * </ul>
   *
   * <p>The traversal is effect-polymorphic: use {@code modifyF} with any {@code Applicative} to
   * perform effectful transformations over all children.
   *
   * @return a traversal over immediate child expressions
   */
  public static Traversal<Expr, Expr> children() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, Expr> modifyF(
          Function<Expr, Kind<F, Expr>> f, Expr source, Applicative<F> applicative) {
        return switch (source) {
          case Literal _ -> applicative.of(source);
          case Variable _ -> applicative.of(source);
          case Binary(var l, var op, var r) ->
              applicative.map2(f.apply(l), f.apply(r), (newL, newR) -> new Binary(newL, op, newR));
          case Conditional(var c, var t, var e) ->
              applicative.map3(
                  f.apply(c),
                  f.apply(t),
                  f.apply(e),
                  (newC, newT, newE) -> new Conditional(newC, newT, newE));
        };
      }
    };
  }

  /**
   * A traversal targeting the root expression only.
   *
   * <p>This traversal focuses on a single element (the expression itself). For visiting all nodes
   * in a tree, use the pure transformation helpers {@link #transformBottomUp} or {@link
   * #transformTopDown}, which provide proper recursive semantics.
   *
   * <p>Note: Effect-polymorphic recursive tree traversal requires monadic sequencing, which is
   * beyond what {@code Applicative} provides. For effectful operations over all nodes, consider
   * collecting nodes first with {@code getAll} on composed traversals, or use the pure
   * transformation helpers.
   *
   * @return a traversal focusing on the root expression
   */
  public static Traversal<Expr, Expr> self() {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, Expr> modifyF(
          Function<Expr, Kind<F, Expr>> f, Expr source, Applicative<F> applicative) {
        return f.apply(source);
      }
    };
  }

  /**
   * Transform all nodes in the tree from leaves to root (bottom-up).
   *
   * <p>This is a convenience method for pure transformations. Each node is transformed after its
   * children have been transformed.
   *
   * @param expr the expression to transform
   * @param f the transformation function
   * @return the transformed expression
   */
  public static Expr transformBottomUp(Expr expr, Function<Expr, Expr> f) {
    // First transform all children
    Expr transformed = Traversals.modify(children(), f.compose(e -> transformBottomUp(e, f)), expr);
    // Then transform this node
    return f.apply(transformed);
  }

  /**
   * Transform all nodes in the tree from root to leaves (top-down).
   *
   * <p>Each node is transformed before its children are visited.
   *
   * @param expr the expression to transform
   * @param f the transformation function
   * @return the transformed expression
   */
  public static Expr transformTopDown(Expr expr, Function<Expr, Expr> f) {
    // First transform this node
    Expr transformed = f.apply(expr);
    // Then transform all children
    return Traversals.modify(children(), child -> transformTopDown(child, f), transformed);
  }
}
