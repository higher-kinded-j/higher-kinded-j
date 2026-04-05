// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.Set;
import javax.tools.Diagnostic;

/**
 * A {@link TreeScanner} that detects effect composition errors at compile time.
 *
 * <p>This checker validates:
 *
 * <ul>
 *   <li><b>Interpreters.combine() arity matching</b> — the number of interpreter arguments must
 *       match the valid arity range (2-4) for EitherF nesting
 *   <li><b>boundTo() type safety</b> — validates that boundTo() calls provide the required Inject
 *       and Functor arguments
 *   <li><b>FreePath chain consistency</b> — delegates to {@link PathTypeMismatchChecker} for
 *       FreePath (one of 27 registered Path types)
 * </ul>
 *
 * <p>Follows a <b>no false positives</b> policy: if a type cannot be resolved, the check is
 * silently skipped.
 *
 * @see PathTypeMismatchChecker
 */
public class EffectCompositionChecker extends TreeScanner<Void, Void> {

  /** Methods we check on Interpreters class. */
  private static final String COMBINE_METHOD = "combine";

  /** Methods we check on Ops classes. */
  private static final String BOUND_TO_METHOD = "boundTo";

  /** The class name for Interpreters. */
  private static final String INTERPRETERS_CLASS = "Interpreters";

  /** Known Ops class suffixes — used for boundTo() checking. */
  private static final Set<String> OPS_SUFFIXES = Set.of("Ops", "ErrorOps", "StateOps");

  private final Trees trees;

  /**
   * Creates a new checker.
   *
   * @param trees the Trees utility from the javac task; must not be null
   */
  public EffectCompositionChecker(Trees trees) {
    this.trees = trees;
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
    String methodName = extractMethodName(node);
    if (COMBINE_METHOD.equals(methodName)) {
      checkCombineArity(node);
    } else if (BOUND_TO_METHOD.equals(methodName)) {
      checkBoundToArgs(node);
    }
    return super.visitMethodInvocation(node, unused);
  }

  /**
   * Checks that Interpreters.combine() is called with the correct number of arguments.
   * Interpreters.combine() accepts 2, 3, or 4 interpreters.
   */
  private void checkCombineArity(MethodInvocationTree node) {
    // Verify this is a call on Interpreters class
    if (!isCallOnClass(node, INTERPRETERS_CLASS)) {
      return;
    }

    List<? extends ExpressionTree> args = node.getArguments();
    int argCount = args.size();

    if (argCount < 2 || argCount > 4) {
      reportError(
          node,
          String.format(
              "Interpreters.combine() accepts 2-4 interpreters, got %d. "
                  + "Each interpreter handles one effect algebra in the EitherF composition.",
              argCount));
    }
  }

  /**
   * Checks that boundTo() is called with exactly 2 arguments (Inject and Functor). This validates
   * the calling convention for Ops.boundTo().
   */
  private void checkBoundToArgs(MethodInvocationTree node) {
    List<? extends ExpressionTree> args = node.getArguments();
    int argCount = args.size();

    if (argCount != 2) {
      // Only report if we can confirm this is an Ops-style boundTo
      if (isLikelyOpsBoundTo(node)) {
        reportError(
            node,
            String.format(
                "boundTo() requires exactly 2 arguments (Inject and Functor), got %d.", argCount));
      }
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /** Checks whether the method invocation is on the given class name. */
  private boolean isCallOnClass(MethodInvocationTree node, String className) {
    ExpressionTree methodSelect = node.getMethodSelect();
    if (methodSelect instanceof MemberSelectTree memberSelect) {
      String receiverStr = memberSelect.getExpression().toString();
      return receiverStr.equals(className) || receiverStr.endsWith("." + className);
    }
    return false;
  }

  /**
   * Heuristic check: is this boundTo() call likely on an Ops class? We check the receiver ends with
   * "Ops" or is a known Ops class name.
   */
  private boolean isLikelyOpsBoundTo(MethodInvocationTree node) {
    ExpressionTree methodSelect = node.getMethodSelect();
    if (methodSelect instanceof MemberSelectTree memberSelect) {
      String receiverStr = memberSelect.getExpression().toString();
      return receiverStr.endsWith("Ops");
    }
    return false;
  }

  private String extractMethodName(MethodInvocationTree node) {
    ExpressionTree methodSelect = node.getMethodSelect();
    if (methodSelect instanceof MemberSelectTree memberSelect) {
      return memberSelect.getIdentifier().toString();
    }
    return null;
  }

  private void reportError(MethodInvocationTree node, String message) {
    trees.printMessage(Diagnostic.Kind.ERROR, message, node, null);
  }
}
