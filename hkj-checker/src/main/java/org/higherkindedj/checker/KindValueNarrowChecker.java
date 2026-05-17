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
 * Detects {@code value()} called on a bare {@code Kind} ({@code transformers/common_errors.md}
 * section 5).
 *
 * <p>{@code org.higherkindedj.hkt.Kind} is an empty marker interface, so {@code kind.value()} is a
 * genuine javac "cannot find symbol" error — {@code value()} lives on the concrete transformer
 * ({@code EitherT} and friends). This checker adds the actionable companion ("narrow first")
 * alongside javac's message.
 *
 * <h2>Rule</h2>
 *
 * <p>A no-argument {@code value()} {@link MethodInvocationTree} whose receiver's attributed type
 * resolves to exactly {@code org.higherkindedj.hkt.Kind}. Gating on the resolved receiver type
 * excludes concrete-transformer receivers (where {@code value()} resolves fine) and any unrelated
 * {@code value()}; unresolved receivers are skipped — no false positives.
 */
public final class KindValueNarrowChecker implements CheckVisitor {

  static final String KIND_FQN = "org.higherkindedj.hkt.Kind";

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /** Creates a checker reporting at {@link Diagnostic.Kind#ERROR}. */
  public KindValueNarrowChecker(Trees trees) {
    this(trees, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param severity the severity at which the companion diagnostic is reported
   */
  public KindValueNarrowChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = trees;
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    if (node.getArguments().isEmpty()
        && node.getMethodSelect() instanceof MemberSelectTree select
        && select.getIdentifier().contentEquals("value")
        && receiverIsBareKind(select.getExpression(), path)) {
      trees.printMessage(
          severity, DiagnosticMessages.kindValueNarrow(), node, path.getCompilationUnit());
    }
  }

  private boolean receiverIsBareKind(ExpressionTree receiver, TreePath path) {
    TypeMirror t;
    try {
      t = trees.getTypeMirror(new TreePath(path, receiver));
    } catch (RuntimeException e) {
      return false; // cannot resolve: skip silently (no false positives)
    }
    return t instanceof DeclaredType declared
        && declared.asElement() instanceof TypeElement element
        && element.getQualifiedName().contentEquals(KIND_FQN);
  }
}
