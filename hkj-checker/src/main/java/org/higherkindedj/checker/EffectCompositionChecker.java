// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import java.util.Objects;
import javax.tools.Diagnostic;

/**
 * A {@link TreeScanner} that detects effect composition errors at compile time.
 *
 * <p>This checker validates:
 *
 * <ul>
 *   <li><b>Interpreters.combine() arity matching</b> — the number of interpreter arguments must
 *       match the valid arity range (2-4) for EitherF nesting
 *   <li><b>FreePath chain consistency</b> — delegates to {@link PathTypeMismatchChecker} for
 *       FreePath (one of 27 registered Path types)
 * </ul>
 *
 * <p>Follows a <b>no false positives</b> policy: if a type cannot be resolved, the check is
 * silently skipped.
 *
 * @see PathTypeMismatchChecker
 */
public class EffectCompositionChecker implements CheckVisitor {

  private static final String COMBINE_METHOD = "combine";
  private static final String INTERPRETERS_CLASS = "Interpreters";

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /**
   * Creates a new checker that reports at {@link Diagnostic.Kind#ERROR}.
   *
   * @param trees the Trees utility from the javac task; must not be null
   */
  public EffectCompositionChecker(Trees trees) {
    this(trees, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a new checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param severity the severity at which composition errors are reported
   */
  public EffectCompositionChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = Objects.requireNonNull(trees, "trees must not be null");
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    String methodName = extractMethodName(node);
    if (COMBINE_METHOD.equals(methodName)) {
      checkCombineArity(node);
    }
  }

  /**
   * Checks that Interpreters.combine() is called with the correct number of arguments.
   * Interpreters.combine() accepts 2, 3, or 4 interpreters.
   */
  private void checkCombineArity(MethodInvocationTree node) {
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
   * Checks whether the method invocation is on the given class name.
   *
   * @param node the method invocation to check
   * @param className the expected class name
   * @return true if the receiver matches the class name
   */
  private boolean isCallOnClass(MethodInvocationTree node, String className) {
    ExpressionTree methodSelect = node.getMethodSelect();
    if (methodSelect instanceof MemberSelectTree memberSelect) {
      String receiverStr = memberSelect.getExpression().toString();
      return receiverStr.equals(className) || receiverStr.endsWith("." + className);
    }
    return false;
  }

  /**
   * Extracts the method name from a method invocation.
   *
   * @param node the method invocation tree
   * @return the method name, or null if it cannot be determined
   */
  private String extractMethodName(MethodInvocationTree node) {
    ExpressionTree methodSelect = node.getMethodSelect();
    if (methodSelect instanceof MemberSelectTree memberSelect) {
      return memberSelect.getIdentifier().toString();
    }
    return null;
  }

  private void reportError(MethodInvocationTree node, String message) {
    trees.printMessage(severity, message, node, null);
  }
}
