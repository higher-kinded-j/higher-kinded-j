// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.traversal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;

/**
 * Fold utilities for collecting information from expression trees.
 *
 * <p>Folds traverse the entire tree and aggregate data using a monoid (a combine operation with a
 * zero element). This enables operations like:
 *
 * <ul>
 *   <li>Finding all variables in an expression
 *   <li>Counting nodes by type
 *   <li>Collecting all operators used
 *   <li>Finding common subexpressions
 * </ul>
 */
public final class ExprFold {

  private ExprFold() {}

  /**
   * Fold over an expression tree, extracting and combining data from each node.
   *
   * @param <A> the result type (must form a monoid with combine)
   * @param expr the expression to fold over
   * @param extract function to extract data from a single node
   * @param combine associative operation to combine results
   * @return the combined result from all nodes
   */
  public static <A> A foldMap(Expr expr, Function<Expr, A> extract, BinaryOperator<A> combine) {
    A current = extract.apply(expr);
    return switch (expr) {
      case Literal _, Variable _ -> current;
      case Binary(var l, _, var r) ->
          combine.apply(
              current, combine.apply(foldMap(l, extract, combine), foldMap(r, extract, combine)));
      case Conditional(var c, var t, var e) ->
          combine.apply(
              current,
              combine.apply(
                  foldMap(c, extract, combine),
                  combine.apply(foldMap(t, extract, combine), foldMap(e, extract, combine))));
    };
  }

  /**
   * Find all variable names in an expression.
   *
   * @param expr the expression to search
   * @return a set of all variable names
   */
  public static Set<String> findVariables(Expr expr) {
    return foldMap(
        expr,
        e ->
            switch (e) {
              case Variable(var name) -> Set.of(name);
              default -> Set.of();
            },
        ExprFold::unionSets);
  }

  /**
   * Find all literal values in an expression.
   *
   * @param expr the expression to search
   * @return a set of all literal values
   */
  public static Set<Object> findLiterals(Expr expr) {
    return foldMap(
        expr,
        e ->
            switch (e) {
              case Literal(var value) -> Set.of(value);
              default -> Set.of();
            },
        ExprFold::unionSets);
  }

  /**
   * Find all binary operators used in an expression.
   *
   * @param expr the expression to search
   * @return a set of all operators
   */
  public static Set<BinaryOp> findOperators(Expr expr) {
    return foldMap(
        expr,
        e ->
            switch (e) {
              case Binary(_, var op, _) -> Set.of(op);
              default -> Set.of();
            },
        ExprFold::unionSets);
  }

  /**
   * Count nodes by type in an expression.
   *
   * @param expr the expression to count
   * @return counts for each node type
   */
  public static NodeCounts countNodes(Expr expr) {
    return foldMap(
        expr,
        e ->
            switch (e) {
              case Literal _ -> NodeCounts.ONE_LITERAL;
              case Variable _ -> NodeCounts.ONE_VARIABLE;
              case Binary _ -> NodeCounts.ONE_BINARY;
              case Conditional _ -> NodeCounts.ONE_CONDITIONAL;
            },
        NodeCounts::add);
  }

  /**
   * Calculate the depth of an expression tree.
   *
   * @param expr the expression to measure
   * @return the maximum depth (leaves have depth 1)
   */
  public static int depth(Expr expr) {
    return switch (expr) {
      case Literal _, Variable _ -> 1;
      case Binary(var l, _, var r) -> 1 + Math.max(depth(l), depth(r));
      case Conditional(var c, var t, var e) -> 1 + Math.max(depth(c), Math.max(depth(t), depth(e)));
    };
  }

  /**
   * Find common subexpressions that appear more than once.
   *
   * @param expr the expression to analyse
   * @return a map from subexpression to occurrence count (only entries with count &gt; 1)
   */
  public static Map<Expr, Integer> findCommonSubexpressions(Expr expr) {
    Map<Expr, Integer> counts = new HashMap<>();
    countSubexpressions(expr, counts);
    // Filter to only repeated subexpressions
    Map<Expr, Integer> result = new HashMap<>();
    for (Map.Entry<Expr, Integer> entry : counts.entrySet()) {
      if (entry.getValue() > 1) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  private static void countSubexpressions(Expr expr, Map<Expr, Integer> counts) {
    counts.merge(expr, 1, Integer::sum);
    switch (expr) {
      case Binary(var l, _, var r) -> {
        countSubexpressions(l, counts);
        countSubexpressions(r, counts);
      }
      case Conditional(var c, var t, var e) -> {
        countSubexpressions(c, counts);
        countSubexpressions(t, counts);
        countSubexpressions(e, counts);
      }
      default -> {}
    }
  }

  /**
   * Check if an expression contains any variables.
   *
   * @param expr the expression to check
   * @return true if any variables are present
   */
  public static boolean hasVariables(Expr expr) {
    return switch (expr) {
      case Variable _ -> true;
      case Literal _ -> false;
      case Binary(var l, _, var r) -> hasVariables(l) || hasVariables(r);
      case Conditional(var c, var t, var e) ->
          hasVariables(c) || hasVariables(t) || hasVariables(e);
    };
  }

  /**
   * Check if an expression is a constant (contains no variables).
   *
   * @param expr the expression to check
   * @return true if the expression has no variables
   */
  public static boolean isConstant(Expr expr) {
    return !hasVariables(expr);
  }

  // Helper to combine sets
  private static <T> Set<T> unionSets(Set<T> a, Set<T> b) {
    if (a.isEmpty()) return b;
    if (b.isEmpty()) return a;
    Set<T> result = new HashSet<>(a);
    result.addAll(b);
    return result;
  }
}
