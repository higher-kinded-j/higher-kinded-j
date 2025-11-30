// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.interpret;

import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * Expression interpreter using Higher-Kinded-J's State monad for environment threading.
 *
 * <p>The State monad eliminates the need to pass the environment explicitly through every recursive
 * call. Instead, we use {@link State#get()} to access the environment and {@link State#flatMap} to
 * sequence operations.
 *
 * <p>This implementation uses Higher-Kinded-J's real State monad from {@code
 * org.higherkindedj.hkt.state}, demonstrating the library's full capabilities for stateful
 * computation.
 *
 * <p>Key insight: interpretation is SEQUENTIAL. The value of a variable depends on the current
 * environment, and we might modify the environment (in future extensions with let bindings). This
 * makes State (which is a Monad) the right abstraction.
 */
public final class ExprInterpreter {

  private ExprInterpreter() {}

  /**
   * Interpret an expression, returning a State action that computes the result.
   *
   * <p>Call {@code run(env)} on the result to run the interpretation with a starting environment.
   *
   * @param expr the expression to interpret
   * @return a State action computing the result
   */
  public static State<Environment, Object> interpret(Expr expr) {
    return switch (expr) {
      case Literal(var value) -> State.pure(value);
      case Variable(var name) -> State.<Environment>get().map(env -> env.lookup(name));
      case Binary(var left, var op, var right) -> interpretBinary(left, op, right);
      case Conditional(var cond, var then_, var else_) -> interpretConditional(cond, then_, else_);
    };
  }

  private static State<Environment, Object> interpretBinary(Expr left, BinaryOp op, Expr right) {
    return interpret(left)
        .flatMap(leftVal -> interpret(right).map(rightVal -> applyBinaryOp(op, leftVal, rightVal)));
  }

  private static State<Environment, Object> interpretConditional(
      Expr cond, Expr then_, Expr else_) {
    return interpret(cond).flatMap(condVal -> interpret((Boolean) condVal ? then_ : else_));
  }

  private static Object applyBinaryOp(BinaryOp op, Object left, Object right) {
    return switch (op) {
      case ADD -> (Integer) left + (Integer) right;
      case SUB -> (Integer) left - (Integer) right;
      case MUL -> (Integer) left * (Integer) right;
      case DIV -> (Integer) left / (Integer) right;
      case AND -> (Boolean) left && (Boolean) right;
      case OR -> (Boolean) left || (Boolean) right;
      case EQ -> left.equals(right);
      case NE -> !left.equals(right);
      case LT -> (Integer) left < (Integer) right;
      case LE -> (Integer) left <= (Integer) right;
      case GT -> (Integer) left > (Integer) right;
      case GE -> (Integer) left >= (Integer) right;
    };
  }

  /**
   * Convenience method to interpret an expression directly with an environment.
   *
   * <p>Uses Higher-Kinded-J's State.run() to execute the stateful computation.
   *
   * @param expr the expression to interpret
   * @param env the environment
   * @return the result value
   */
  public static Object eval(Expr expr, Environment env) {
    StateTuple<Environment, Object> result = interpret(expr).run(env);
    return result.value();
  }
}
