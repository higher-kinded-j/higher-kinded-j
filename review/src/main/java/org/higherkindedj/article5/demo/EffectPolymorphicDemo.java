// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.demo;

import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.article4.traversal.ExprTraversal;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

/**
 * Demonstrates effect-polymorphic optics using modifyF with different Higher-Kinded-J effects.
 *
 * <p>Key insight: The same traversal works with multiple computational effects. We write the
 * traversal once, then use it with Identity (pure), State (stateful), Validated (error
 * accumulating), etc. This demo showcases the real Higher-Kinded-J types.
 */
public final class EffectPolymorphicDemo {

  public static void main(String[] args) {
    System.out.println("Effect-Polymorphic Optics Demo with Higher-Kinded-J");
    System.out.println("====================================================");
    System.out.println();

    demoPureTransformation();
    demoCollectWithState();
    demoSameTraversalDifferentEffects();
  }

  private static void demoPureTransformation() {
    System.out.println("1. Pure transformation (using modify)");
    System.out.println("   -----------------------------------");

    // (x + 1) * (y + 2)
    Expr expr =
        new Binary(
            new Binary(new Variable("x"), BinaryOp.ADD, new Literal(1)),
            BinaryOp.MUL,
            new Binary(new Variable("y"), BinaryOp.ADD, new Literal(2)));

    System.out.println("   Original: " + expr.format());

    // Double all literals using the children traversal + recursion
    Expr doubled =
        ExprTraversal.transformBottomUp(
            expr, e -> e instanceof Literal(Integer i) ? new Literal(i * 2) : e);

    System.out.println("   After doubling literals: " + doubled.format());
    System.out.println();
  }

  private static void demoCollectWithState() {
    System.out.println("2. Collecting variables with Higher-Kinded-J State");
    System.out.println("   -------------------------------------------------");

    // (a + b) * (c + d)
    Expr expr =
        new Binary(
            new Binary(new Variable("a"), BinaryOp.ADD, new Variable("b")),
            BinaryOp.MUL,
            new Binary(new Variable("c"), BinaryOp.ADD, new Variable("d")));

    System.out.println("   Expression: " + expr.format());

    // Collect all variable names using State
    Set<String> variables = collectVariables(expr);

    System.out.println("   Variables found: " + variables);
    System.out.println();
  }

  private static Set<String> collectVariables(Expr expr) {
    // Define a collector that adds variable names to a Set
    Function<Expr, State<Set<String>, Expr>> collector =
        e ->
            e instanceof Variable(var name)
                ? State.<Set<String>>modify(
                        vars ->
                            Stream.concat(vars.stream(), Stream.of(name))
                                .collect(Collectors.toUnmodifiableSet()))
                    .map(v -> e)
                : State.pure(e);

    // Use a recursive approach to visit all nodes
    StateTuple<Set<String>, Expr> result =
        collectVariablesRecursive(expr, collector).run(new HashSet<>());
    return result.state();
  }

  private static State<Set<String>, Expr> collectVariablesRecursive(
      Expr expr, Function<Expr, State<Set<String>, Expr>> collector) {

    // First collect from this node
    State<Set<String>, Expr> thisNode = collector.apply(expr);

    // Then recursively collect from children
    return thisNode.flatMap(
        e ->
            switch (e) {
              case Literal _, Variable _ -> State.pure(e);
              case Binary(var l, var op, var r) ->
                  collectVariablesRecursive(l, collector)
                      .flatMap(
                          newL ->
                              collectVariablesRecursive(r, collector)
                                  .map(newR -> new Binary(newL, op, newR)));
              case Expr.Conditional(var c, var t, var el) ->
                  collectVariablesRecursive(c, collector)
                      .flatMap(
                          newC ->
                              collectVariablesRecursive(t, collector)
                                  .flatMap(
                                      newT ->
                                          collectVariablesRecursive(el, collector)
                                              .map(
                                                  newE -> new Expr.Conditional(newC, newT, newE))));
            });
  }

  private static void demoSameTraversalDifferentEffects() {
    System.out.println("3. Same traversal, different Higher-Kinded-J effects");
    System.out.println("   ---------------------------------------------------");
    System.out.println("   The children() traversal can work with ANY effect.");
    System.out.println("   Here we demonstrate with Identity and State:");
    System.out.println();

    Expr expr = new Binary(new Literal(1), BinaryOp.ADD, new Literal(2));
    System.out.println("   Expression: " + expr.format());

    // Pure transformation using modify (Identity effect)
    Traversal<Expr, Expr> children = ExprTraversal.children();
    Expr modified =
        Traversals.modify(
            children, e -> e instanceof Literal(Integer i) ? new Literal(i * 10) : e, expr);
    System.out.println("   With Identity (pure): " + modified.format());

    // State-based transformation using modifyF with Higher-Kinded-J State
    StateMonad<Integer> stateMonad = new StateMonad<>();

    Kind<StateKind.Witness<Integer>, Expr> stateKind =
        children.modifyF(
            e -> {
              if (e instanceof Literal(Integer i)) {
                // Count and transform using Higher-Kinded-J State
                State<Integer, Expr> countAndTransform =
                    State.<Integer>modify(count -> count + 1).map(v -> new Literal(i * 10));
                return STATE.widen(countAndTransform);
              }
              return STATE.widen(State.pure(e));
            },
            expr,
            stateMonad);

    StateTuple<Integer, Expr> stateResult = STATE.narrow(stateKind).run(0);
    System.out.printf(
        "   With State (counting): %s, count = %d%n",
        stateResult.value().format(), stateResult.state());
    System.out.println();

    System.out.println("   Key insight: The SAME traversal (children()) works with both");
    System.out.println("   pure and effectful transformations. This is effect polymorphism,");
    System.out.println("   powered by Higher-Kinded-J's Kind<F, A> abstraction.");
    System.out.println();
  }
}
