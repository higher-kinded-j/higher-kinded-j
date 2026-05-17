// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.util.TreePathScanner;
import java.util.List;

/**
 * The single AST pass that drives every enabled HKJ check.
 *
 * <p>Replaces the previous one-scan-per-check model: instead of {@code checks × N} traversals, a
 * single {@code TreePathScanner} visits each node once and fans out to the enabled {@link
 * CheckVisitor}s, passing the live {@link TreePathScanner#getCurrentPath() current path} so each
 * check resolves types exactly as it did when it was its own scanner. Checks are dispatched in list
 * order, so multi-diagnostic ordering on a node is stable.
 */
final class HkjCheckScanner extends TreePathScanner<Void, Void> {

  private final List<CheckVisitor> checks;

  HkjCheckScanner(List<CheckVisitor> checks) {
    this.checks = checks;
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
    for (CheckVisitor c : checks) {
      c.onMethodInvocation(node, getCurrentPath());
    }
    return super.visitMethodInvocation(node, unused);
  }

  @Override
  public Void visitNewClass(NewClassTree node, Void unused) {
    for (CheckVisitor c : checks) {
      c.onNewClass(node, getCurrentPath());
    }
    return super.visitNewClass(node, unused);
  }

  @Override
  public Void visitExpressionStatement(ExpressionStatementTree node, Void unused) {
    for (CheckVisitor c : checks) {
      c.onExpressionStatement(node, getCurrentPath());
    }
    return super.visitExpressionStatement(node, unused);
  }

  @Override
  public Void visitSwitch(SwitchTree node, Void unused) {
    for (CheckVisitor c : checks) {
      c.onSwitch(node, getCurrentPath());
    }
    return super.visitSwitch(node, unused);
  }

  @Override
  public Void visitSwitchExpression(SwitchExpressionTree node, Void unused) {
    for (CheckVisitor c : checks) {
      c.onSwitchExpression(node, getCurrentPath());
    }
    return super.visitSwitchExpression(node, unused);
  }

  @Override
  public Void visitParameterizedType(ParameterizedTypeTree node, Void unused) {
    for (CheckVisitor c : checks) {
      c.onParameterizedType(node, getCurrentPath());
    }
    return super.visitParameterizedType(node, unused);
  }
}
