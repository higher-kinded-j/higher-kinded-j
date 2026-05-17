// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.type.TypeMirror;

/**
 * Shared resolution of a {@code via}/{@code flatMap}/{@code then} functional argument's return
 * type, used by the chain checkers ({@code ErrorTypeMismatchChecker}, {@code
 * MapReturnsPathChecker}, {@code ViaNonPathChecker}) so the logic — and its no-false-positives
 * guarantees — live in one place.
 *
 * <p>For a block-bodied lambda <b>every</b> {@code return} expression in the lambda's own scope is
 * resolved (a lambda may mix a correct and an incorrect return; checking only the first would miss
 * the silent mismatch). The scan deliberately does <b>not</b> descend into nested lambdas or
 * local/anonymous classes: a {@code return} inside an inner lambda or anonymous class belongs to
 * <em>that</em> body, not the one being analysed. Method references and other non-lambda arguments
 * resolve to an empty list (deliberately unresolved → the caller skips); individual unresolved
 * {@code return} expressions are omitted rather than failing the whole lambda.
 */
final class LambdaReturns {

  private LambdaReturns() {}

  /**
   * Resolves the attributed type of a tree, relative to the given context path.
   *
   * @return the type, or {@code null} if it cannot be resolved (caller skips → no false positives)
   */
  static TypeMirror typeOf(Trees trees, TreePath context, Tree tree) {
    try {
      return trees.getTypeMirror(new TreePath(context, tree));
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Every resolvable return type of a lambda/supplier argument, in source order.
   *
   * <p>An expression-bodied lambda yields its single body type; a block-bodied lambda yields one
   * entry per value-returning {@code return} in its own scope. The list is empty when the argument
   * is a method reference, is unresolved, or the block body has no resolvable return — in every
   * such case the caller has nothing to test and skips, preserving the no-false-positives policy. A
   * caller diagnoses if <em>any</em> returned type breaks its rule: each such return is
   * independently a genuine bug, so flagging on it adds true positives without weakening the
   * guarantee.
   */
  static List<TypeMirror> lambdaReturnTypes(Trees trees, TreePath context, ExpressionTree arg) {
    List<TypeMirror> result = new ArrayList<>();
    if (!(arg instanceof LambdaExpressionTree lambda)) {
      return result; // method reference / other: deliberately unresolved
    }
    if (lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION) {
      TypeMirror t = typeOf(trees, context, (ExpressionTree) lambda.getBody());
      if (t != null) {
        result.add(t);
      }
      return result;
    }
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitReturn(ReturnTree r, Void u) {
        if (r.getExpression() != null) {
          // getCurrentPath() is a contiguous path down to this return, so the resolved
          // expression's scope is correct (its parent is a real, complete chain).
          TypeMirror t = typeOf(trees, getCurrentPath(), r.getExpression());
          if (t != null) {
            result.add(t);
          }
        }
        return super.visitReturn(r, u);
      }

      // A return inside a nested lambda or local/anonymous class belongs to that body, not
      // ours — do not descend into them.
      @Override
      public Void visitLambdaExpression(LambdaExpressionTree l, Void u) {
        return null;
      }

      @Override
      public Void visitClass(ClassTree c, Void u) {
        return null;
      }
    }.scan(new TreePath(context, lambda.getBody()), null);
    return result;
  }
}
