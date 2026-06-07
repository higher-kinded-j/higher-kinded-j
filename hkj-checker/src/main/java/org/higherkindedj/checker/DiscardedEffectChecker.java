// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Detects a lazy HKJ effect that is built then discarded as a bare statement.
 *
 * <p>Every {@code Path}/{@code IO}/{@code Free} value is a deferred description of a computation
 * (see {@code Effectful}: "deferred until you explicitly run it"). Composing one and then dropping
 * it on the floor — {@code Path.right(x).map(f);} as a statement — compiles cleanly but does
 * nothing. The library ships no {@code @CheckReturnValue}, so this whole class of silent no-op is
 * otherwise invisible.
 *
 * <h2>Rule</h2>
 *
 * <p>An {@link ExpressionStatementTree} whose expression is a <em>result-producing</em> call (a
 * {@link MethodInvocationTree} or {@link NewClassTree}) and whose attributed type erase-assignable
 * to {@code org.higherkindedj.hkt.effect.capability.Chainable}, the sealed Path root.
 *
 * <p>Two consequences keep this false-positive-free:
 *
 * <ul>
 *   <li><b>Run effects exclude themselves.</b> Terminal operations ({@code unsafeRun()}, an
 *       interpreter {@code run}) return the contained value {@code A}, not a {@code Chainable}, so
 *       a statement that actually runs the effect is not flagged.
 *   <li><b>Only genuine discards are considered.</b> Assignments, compound assignments and {@code
 *       i++} are expression statements too, but the value is consumed; restricting to
 *       invocation/constructor expressions excludes them. Returned/passed effects are not
 *       statements at all.
 * </ul>
 *
 * <p><b>Accepted scope boundary:</b> a local declared and never run ({@code EitherPath<…> x =
 * Path.right(1);}) is a {@code VariableTree}, not an expression statement, and is deliberately out
 * of reach — detecting it needs dataflow and would risk flagging legitimate "build now, run later"
 * code, violating the no-false-positives policy.
 */
public final class DiscardedEffectChecker implements CheckVisitor {

  static final String CHAINABLE_FQN = "org.higherkindedj.hkt.effect.capability.Chainable";

  private final Trees trees;
  private final Types types;
  private final Elements elements;
  private final Diagnostic.Kind severity;

  /**
   * Creates a checker reporting at {@link Diagnostic.Kind#ERROR}.
   *
   * @param trees the {@link Trees} utility for AST and type resolution
   * @param types the {@link Types} utility for type operations
   * @param elements the {@link Elements} utility for element operations
   */
  public DiscardedEffectChecker(Trees trees, Types types, Elements elements) {
    this(trees, types, elements, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param types the model Types utility from the javac task
   * @param elements the model Elements utility from the javac task
   * @param severity the severity at which the diagnostic is reported
   */
  public DiscardedEffectChecker(
      Trees trees, Types types, Elements elements, Diagnostic.Kind severity) {
    this.trees = trees;
    this.types = types;
    this.elements = elements;
    this.severity = severity;
  }

  @Override
  public void onExpressionStatement(ExpressionStatementTree node, TreePath path) {
    ExpressionTree expr = node.getExpression();
    if ((expr instanceof MethodInvocationTree || expr instanceof NewClassTree)
        && isChainable(expr, path)) {
      trees.printMessage(
          severity,
          DiagnosticMessages.discardedEffect(simpleName(expr, path)),
          node,
          path.getCompilationUnit());
    }
  }

  private boolean isChainable(ExpressionTree expr, TreePath path) {
    TypeMirror t;
    try {
      t = trees.getTypeMirror(new TreePath(path, expr));
    } catch (RuntimeException e) {
      return false; // cannot resolve: skip silently (no false positives)
    }
    if (t == null
        || t.getKind() == TypeKind.VOID
        || t.getKind() == TypeKind.NONE
        || t.getKind() == TypeKind.ERROR
        || t.getKind().isPrimitive()) {
      return false;
    }
    TypeElement chainable = elements.getTypeElement(CHAINABLE_FQN);
    if (chainable == null) {
      return false; // the effect API is not on this compilation's classpath
    }
    return types.isAssignable(types.erasure(t), types.erasure(chainable.asType()));
  }

  private String simpleName(ExpressionTree expr, TreePath path) {
    TypeMirror t;
    try {
      t = trees.getTypeMirror(new TreePath(path, expr));
    } catch (RuntimeException e) {
      return "effect";
    }
    TypeMirror erased = types.erasure(t);
    if (erased instanceof DeclaredType dt) {
      return dt.asElement().getSimpleName().toString();
    }
    return "effect";
  }
}
