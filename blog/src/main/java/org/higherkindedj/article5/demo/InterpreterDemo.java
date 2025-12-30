// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.demo;

import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.article5.interpret.Environment;
import org.higherkindedj.article5.interpret.ExprInterpreter;
import org.higherkindedj.hkt.state.State;

/**
 * Demonstrates expression interpretation using Higher-Kinded-J's State monad.
 *
 * <p>Key concept: The State monad threads the environment through the computation implicitly. We
 * never pass the environment explicitly after the initial call to {@code run}. This demo showcases
 * the real Higher-Kinded-J State monad from {@code org.higherkindedj.hkt.state}.
 */
public final class InterpreterDemo {

  public static void main(String[] args) {
    System.out.println("Interpreter Demo: Higher-Kinded-J State Monad for Environment Threading");
    System.out.println("=======================================================================");
    System.out.println();

    demoSimpleArithmetic();
    demoVariableLookup();
    demoConditionalEvaluation();
    demoStateMonadComposition();
  }

  private static void demoSimpleArithmetic() {
    System.out.println("1. Simple arithmetic");
    System.out.println("   ------------------");

    // (2 + 3) * 4
    Expr expr =
        new Binary(
            new Binary(new Literal(2), BinaryOp.ADD, new Literal(3)), BinaryOp.MUL, new Literal(4));

    System.out.println("   Expression: " + expr.format());

    Object result = ExprInterpreter.eval(expr, Environment.empty());
    System.out.println("   Result: " + result);
    System.out.println();
  }

  private static void demoVariableLookup() {
    System.out.println("2. Variable lookup");
    System.out.println("   ----------------");

    // (x + 1) * 2 where x = 10
    Expr expr =
        new Binary(
            new Binary(new Variable("x"), BinaryOp.ADD, new Literal(1)),
            BinaryOp.MUL,
            new Literal(2));

    Environment env = Environment.of("x", 10);

    System.out.println("   Expression: " + expr.format());
    System.out.println("   Environment: x = 10");

    Object result = ExprInterpreter.eval(expr, env);
    System.out.println("   Result: " + result + " (expected: 22)");
    System.out.println();
  }

  private static void demoConditionalEvaluation() {
    System.out.println("3. Conditional evaluation");
    System.out.println("   -----------------------");

    // if (x > 0) then x else -x (absolute value)
    Expr absExpr =
        new Conditional(
            new Binary(new Variable("x"), BinaryOp.GT, new Literal(0)),
            new Variable("x"),
            new Binary(new Literal(0), BinaryOp.SUB, new Variable("x")));

    System.out.println("   Expression: " + absExpr.format());

    // Test with positive value
    Environment envPos = Environment.of("x", 5);
    Object resultPos = ExprInterpreter.eval(absExpr, envPos);
    System.out.println("   With x = 5: " + resultPos);

    // Test with negative value
    Environment envNeg = Environment.of("x", -7);
    Object resultNeg = ExprInterpreter.eval(absExpr, envNeg);
    System.out.println("   With x = -7: " + resultNeg);
    System.out.println();
  }

  private static void demoStateMonadComposition() {
    System.out.println("4. Higher-Kinded-J State monad composition");
    System.out.println("   ----------------------------------------");
    System.out.println("   The State monad threads state through flatMap.");
    System.out.println("   Here we compose multiple interpretations:");
    System.out.println();

    // a + b + c where a=1, b=2, c=3
    Expr expr =
        new Binary(
            new Binary(new Variable("a"), BinaryOp.ADD, new Variable("b")),
            BinaryOp.ADD,
            new Variable("c"));

    Environment env = Environment.of("a", 1, "b", 2, "c", 3);

    System.out.println("   Expression: " + expr.format());
    System.out.println("   Environment: a=1, b=2, c=3");

    // Show the State action composition
    State<Environment, Object> computation = ExprInterpreter.interpret(expr);
    System.out.println("   State action created (computation deferred)");

    Object result = computation.run(env).value();
    System.out.println("   After run: " + result);
    System.out.println();

    // Demonstrate that State is lazy
    System.out.println("   Key insight: Higher-Kinded-J's State is lazy. The computation");
    System.out.println("   only runs when we call run(). The same State action can be run");
    System.out.println("   with different environments:");

    Environment env2 = Environment.of("a", 10, "b", 20, "c", 30);
    Object result2 = computation.run(env2).value();
    System.out.println("   With a=10, b=20, c=30: " + result2);
    System.out.println();
  }
}
