// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.util.TreePath;

/**
 * A single HKJ check, expressed as per-node hooks rather than its own tree scan.
 *
 * <p>{@link HkjCheckScanner} performs <b>one</b> {@code TreePathScanner} pass over each compilation
 * unit and dispatches each visited node to every enabled check, so total cost is {@code O(N)} in
 * the number of AST nodes rather than {@code O(checks × N)}. Every hook receives the contiguous
 * {@link TreePath} to the node (equivalent to what a stand-alone {@code TreePathScanner} would see
 * via {@code getCurrentPath()}), preserving each check's type-resolution and diagnostic behaviour.
 *
 * <p>All hooks default to no-ops; a check overrides only the node kinds it inspects.
 */
interface CheckVisitor {

  default void onMethodInvocation(MethodInvocationTree node, TreePath path) {}

  default void onNewClass(NewClassTree node, TreePath path) {}

  default void onExpressionStatement(ExpressionStatementTree node, TreePath path) {}

  default void onSwitch(SwitchTree node, TreePath path) {}

  default void onSwitchExpression(SwitchExpressionTree node, TreePath path) {}

  default void onParameterizedType(ParameterizedTypeTree node, TreePath path) {}
}
