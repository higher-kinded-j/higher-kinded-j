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

  /**
   * Invoked for each method invocation node visited during the scan.
   *
   * @param node the method invocation node
   * @param path the tree path to {@code node}
   */
  default void onMethodInvocation(MethodInvocationTree node, TreePath path) {}

  /**
   * Invoked for each {@code new} class instantiation node visited during the scan.
   *
   * @param node the new class node
   * @param path the tree path to {@code node}
   */
  default void onNewClass(NewClassTree node, TreePath path) {}

  /**
   * Invoked for each expression statement node visited during the scan.
   *
   * @param node the expression statement node
   * @param path the tree path to {@code node}
   */
  default void onExpressionStatement(ExpressionStatementTree node, TreePath path) {}

  /**
   * Invoked for each {@code switch} statement node visited during the scan.
   *
   * @param node the switch statement node
   * @param path the tree path to {@code node}
   */
  default void onSwitch(SwitchTree node, TreePath path) {}

  /**
   * Invoked for each {@code switch} expression node visited during the scan.
   *
   * @param node the switch expression node
   * @param path the tree path to {@code node}
   */
  default void onSwitchExpression(SwitchExpressionTree node, TreePath path) {}

  /**
   * Invoked for each parameterized type node visited during the scan.
   *
   * @param node the parameterized type node
   * @param path the tree path to {@code node}
   */
  default void onParameterizedType(ParameterizedTypeTree node, TreePath path) {}
}
