// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Detects {@code StateT.mapT(f)} called without the leading {@code Monad<G>} argument ({@code
 * transformers/common_errors.md} section 3).
 *
 * <p>{@code StateT} is the sole transformer whose {@code mapT} requires the target monad as an
 * extra first argument; every other transformer's {@code mapT} takes just the function. There is no
 * one-argument {@code StateT.mapT} overload, so {@code stateT.mapT(f)} is a genuine javac "method
 * mapT cannot be applied to given types" error — this checker adds the actionable companion
 * alongside it.
 *
 * <h2>Rule</h2>
 *
 * <p>A {@link MethodInvocationTree} selecting {@code mapT} off a receiver whose attributed type
 * resolves to {@code org.higherkindedj.hkt.state_t.StateT}, with exactly one argument (the
 * documented mistake: the function was supplied but the monad forgotten).
 *
 * <p>No false positives: gating on the resolved receiver type excludes the other transformers'
 * legitimate one-argument {@code mapT} and any unrelated {@code mapT}; when the receiver type
 * cannot be resolved the check is skipped silently.
 */
public final class StateTMapTArityChecker implements CheckVisitor {

  static final String STATE_T_FQN = "org.higherkindedj.hkt.state_t.StateT";

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /** Creates a checker reporting at {@link Diagnostic.Kind#ERROR}. */
  public StateTMapTArityChecker(Trees trees) {
    this(trees, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param severity the severity at which the companion diagnostic is reported
   */
  public StateTMapTArityChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = trees;
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    if (node.getArguments().size() == 1
        && node.getMethodSelect() instanceof MemberSelectTree select
        && select.getIdentifier().contentEquals("mapT")
        && receiverIsStateT(select.getExpression(), path)) {
      trees.printMessage(
          severity, DiagnosticMessages.stateTMapTArity(), node, path.getCompilationUnit());
    }
  }

  private boolean receiverIsStateT(ExpressionTree receiver, TreePath path) {
    TypeMirror t;
    try {
      t = trees.getTypeMirror(new TreePath(path, receiver));
    } catch (RuntimeException e) {
      return false; // cannot resolve: skip silently (no false positives)
    }
    if (!(t instanceof DeclaredType declared)) {
      return false;
    }
    return declared.asElement() instanceof TypeElement element
        && element.getQualifiedName().contentEquals(STATE_T_FQN);
  }
}
