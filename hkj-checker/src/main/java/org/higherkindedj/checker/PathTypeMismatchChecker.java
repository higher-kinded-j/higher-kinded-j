// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.tools.Diagnostic;

/**
 * A {@link TreeScanner} that detects Path type mismatches in method invocations.
 *
 * <p>This scanner visits method invocation nodes in the AST and checks whether the receiver's
 * concrete Path type matches the type returned by lambda arguments or passed as parameters. It
 * targets the following methods:
 *
 * <ul>
 *   <li>{@code via(Function)} and {@code flatMap(Function)} - checks the lambda's return type
 *   <li>{@code then(Supplier)} - checks the supplier's return type
 *   <li>{@code zipWith(Combinable, BiFunction)} - checks the first argument's type
 *   <li>{@code zipWith3(Combinable, Combinable, Function3)} - checks the first two arguments' types
 *   <li>{@code recoverWith(Function)} - checks the lambda's return type
 *   <li>{@code orElse(Supplier)} - checks the supplier's return type
 * </ul>
 *
 * <p>This checker follows a <b>no false positives</b> policy: if a type cannot be resolved, the
 * check is silently skipped.
 */
public class PathTypeMismatchChecker extends TreeScanner<Void, Void> {

  private static final Set<String> CHECKED_METHODS =
      Set.of("via", "flatMap", "then", "zipWith", "zipWith3", "recoverWith", "orElse");

  /** Methods where the first argument is the "other" Path to check. */
  private static final Set<String> ARGUMENT_TYPE_METHODS = Set.of("zipWith", "zipWith3");

  /** Methods where we check the return type of the lambda/supplier argument. */
  private static final Set<String> LAMBDA_RETURN_METHODS =
      Set.of("via", "flatMap", "then", "recoverWith", "orElse");

  private final Trees trees;

  /**
   * Creates a new checker using the given Trees instance.
   *
   * @param trees the Trees utility from the javac task; must not be null
   */
  public PathTypeMismatchChecker(Trees trees) {
    this.trees = trees;
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
    String methodName = extractMethodName(node);
    if (methodName != null && CHECKED_METHODS.contains(methodName)) {
      checkPathTypeMismatch(node, methodName);
    }
    return super.visitMethodInvocation(node, unused);
  }

  private void checkPathTypeMismatch(MethodInvocationTree node, String methodName) {
    // Resolve the receiver type
    Optional<String> receiverType = resolveReceiverPathType(node);
    if (receiverType.isEmpty()) {
      return; // Cannot resolve receiver; skip silently (no false positives)
    }

    if (ARGUMENT_TYPE_METHODS.contains(methodName)) {
      checkArgumentTypes(node, methodName, receiverType.get());
    } else if (LAMBDA_RETURN_METHODS.contains(methodName)) {
      checkLambdaReturnType(node, methodName, receiverType.get());
    }
  }

  /**
   * For zipWith/zipWith3, check the concrete type of the first argument (and second for zipWith3).
   */
  private void checkArgumentTypes(
      MethodInvocationTree node, String methodName, String receiverCategory) {
    List<? extends ExpressionTree> args = node.getArguments();
    if (args.isEmpty()) {
      return;
    }

    // Check the first argument (the "other" Combinable)
    checkArgumentType(node, args.getFirst(), methodName, receiverCategory);

    // For zipWith3, also check the second argument
    if ("zipWith3".equals(methodName) && args.size() >= 2) {
      checkArgumentType(node, args.get(1), methodName, receiverCategory);
    }
  }

  private void checkArgumentType(
      MethodInvocationTree node,
      ExpressionTree arg,
      String methodName,
      String receiverCategory) {
    Optional<String> argType = resolveExpressionPathType(arg);
    if (argType.isEmpty()) {
      return;
    }

    if (!argType.get().equals(receiverCategory)) {
      reportMismatch(node, methodName, receiverCategory, argType.get());
    }
  }

  /** For via/flatMap/then/recoverWith/orElse, check the return type of the lambda/supplier. */
  private void checkLambdaReturnType(
      MethodInvocationTree node, String methodName, String receiverCategory) {
    List<? extends ExpressionTree> args = node.getArguments();
    if (args.isEmpty()) {
      return;
    }

    ExpressionTree lambdaArg = args.getFirst();

    // Try to resolve the lambda's return type from its body
    Optional<String> returnType = resolveLambdaReturnPathType(lambdaArg);
    if (returnType.isEmpty()) {
      return; // Cannot resolve; skip silently
    }

    if (!returnType.get().equals(receiverCategory)) {
      reportMismatch(node, methodName, receiverCategory, returnType.get());
    }
  }

  /**
   * Resolves the concrete Path type of the receiver expression.
   *
   * @return the Path category simple name, or empty if not a known Path type
   */
  private Optional<String> resolveReceiverPathType(MethodInvocationTree node) {
    ExpressionTree methodSelect = node.getMethodSelect();
    ExpressionTree receiver = null;

    if (methodSelect instanceof MemberSelectTree memberSelect) {
      receiver = memberSelect.getExpression();
    }

    if (receiver == null) {
      return Optional.empty();
    }

    return resolveExpressionPathType(receiver);
  }

  /**
   * Resolves the concrete Path type of an expression by examining its attributed type from javac.
   */
  private Optional<String> resolveExpressionPathType(ExpressionTree expr) {
    try {
      Type type = getAttributedType(expr);
      if (type == null) {
        return Optional.empty();
      }
      return resolvePathTypeFromType(type);
    } catch (Exception e) {
      // If type resolution fails for any reason, skip silently (no false positives)
      return Optional.empty();
    }
  }

  /** Attempts to resolve the return type of a lambda expression or supplier. */
  private Optional<String> resolveLambdaReturnPathType(ExpressionTree lambdaArg) {
    if (lambdaArg instanceof LambdaExpressionTree lambda) {
      return resolveLambdaBodyReturnType(lambda);
    }
    // For method references or other expressions, we cannot easily determine
    // the return type without deep type analysis, so skip
    return Optional.empty();
  }

  /** Resolves the return type from a lambda body. */
  private Optional<String> resolveLambdaBodyReturnType(LambdaExpressionTree lambda) {
    // For expression lambdas (e.g., x -> Path.just(x)), the body is the expression
    if (lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION) {
      return resolveExpressionPathType((ExpressionTree) lambda.getBody());
    }

    // For statement lambdas, scan for return statements
    ReturnTypeFinder finder = new ReturnTypeFinder();
    finder.scan(lambda.getBody(), null);
    return finder.getReturnPathType();
  }

  /**
   * Gets the attributed type from a javac expression tree node.
   *
   * <p>After the ANALYZE phase, javac attaches resolved types to expression nodes via the internal
   * {@code JCTree.JCExpression.type} field. This method accesses that field through the internal
   * javac API (accessible via {@code --add-exports}).
   */
  private Type getAttributedType(ExpressionTree expr) {
    if (expr instanceof JCTree.JCExpression jcExpr) {
      return jcExpr.type;
    }
    return null;
  }

  /** Resolves a javac Type to a Path type category if it is a known Path type. */
  private Optional<String> resolvePathTypeFromType(Type type) {
    String qualifiedName = extractQualifiedName(type);
    if (qualifiedName == null) {
      return Optional.empty();
    }
    return PathTypeRegistry.getPathCategory(qualifiedName);
  }

  /** Extracts the fully qualified name from a javac Type, stripping generic type parameters. */
  private String extractQualifiedName(Type type) {
    if (type.tsym != null) {
      return type.tsym.getQualifiedName().toString();
    }
    // Fallback: parse from toString, stripping generics
    String str = type.toString();
    int angleBracket = str.indexOf('<');
    if (angleBracket >= 0) {
      str = str.substring(0, angleBracket);
    }
    return str;
  }

  /** Extracts the method name from a method invocation. */
  private String extractMethodName(MethodInvocationTree node) {
    ExpressionTree methodSelect = node.getMethodSelect();
    if (methodSelect instanceof MemberSelectTree memberSelect) {
      return memberSelect.getIdentifier().toString();
    }
    return null;
  }

  /** Reports a Path type mismatch diagnostic at the given node's source location. */
  private void reportMismatch(
      MethodInvocationTree node, String methodName, String expectedType, String actualType) {
    String message = DiagnosticMessages.pathTypeMismatch(methodName, expectedType, actualType);
    trees.printMessage(Diagnostic.Kind.ERROR, message, node, null);
  }

  /**
   * Inner scanner that finds return statements in a lambda body and resolves their Path types.
   *
   * <p>Collects the first resolvable return type found. If multiple return statements return
   * different types, only the first is used.
   */
  private class ReturnTypeFinder extends TreeScanner<Void, Void> {
    private String returnPathType;

    @Override
    public Void visitReturn(ReturnTree node, Void unused) {
      if (returnPathType == null && node.getExpression() != null) {
        resolveExpressionPathType(node.getExpression()).ifPresent(type -> returnPathType = type);
      }
      return super.visitReturn(node, unused);
    }

    Optional<String> getReturnPathType() {
      return Optional.ofNullable(returnPathType);
    }
  }
}
