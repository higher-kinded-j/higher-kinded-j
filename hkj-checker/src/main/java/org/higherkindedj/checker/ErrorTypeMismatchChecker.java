// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Detects a silently-erased error-type {@code E} mismatch in an error-typed Path chain ({@code
 * EitherPath}/{@code ValidationPath}).
 *
 * <p>{@code via}/{@code flatMap}/{@code then} accept {@code Function/Supplier<? extends
 * Chainable<B>>} and {@code zipWith} a {@code Combinable<B>} — none of which carry the error type.
 * A step whose {@code E} differs from the receiver's therefore <em>compiles</em> (javac does not
 * error, see {@code ErrorTypeMismatchSpikeTest}); the wrong {@code E} is carried at runtime, a
 * latent {@code ClassCastException} when the error is consumed. Because there is no javac error to
 * sit beside, this checker is the sole signal and so defaults to a <b>warning</b>.
 *
 * <h2>Rule (pinned by the detector spike)</h2>
 *
 * <p>For {@code via}/{@code flatMap}/{@code then} (lambda/supplier return) and {@code zipWith}
 * (first argument), where the receiver and that step are the <em>same</em> error-typed category,
 * both error types are concrete {@link TypeKind#DECLARED} types, and the step's {@code E} is <b>not
 * assignable</b> to the receiver's {@code E} → diagnose.
 *
 * <p>The assignability test (not type identity) is essential: {@code EitherPath<Object,String>}
 * with a step yielding {@code EitherPath<String,…>} is safe (a {@code String} consumed as {@code
 * Object} never fails) and must not be flagged. Type variables, wildcards and unresolved
 * lambda/method-reference returns are skipped, preserving the no-false-positives policy.
 */
public final class ErrorTypeMismatchChecker implements CheckVisitor {

  private static final Set<String> LAMBDA_METHODS = Set.of("via", "flatMap", "then");
  private static final String ZIP_WITH = "zipWith";
  private static final Set<String> ERROR_TYPED =
      Set.of(
          "org.higherkindedj.hkt.effect.EitherPath", "org.higherkindedj.hkt.effect.ValidationPath");

  private final Trees trees;
  private final Types types;
  private final Diagnostic.Kind severity;

  /** Creates a checker reporting at {@link Diagnostic.Kind#WARNING} (sole-signal soak default). */
  public ErrorTypeMismatchChecker(Trees trees, Types types) {
    this(trees, types, Diagnostic.Kind.WARNING);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param types the model Types utility from the javac task
   * @param severity the severity at which the diagnostic is reported
   */
  public ErrorTypeMismatchChecker(Trees trees, Types types, Diagnostic.Kind severity) {
    this.trees = trees;
    this.types = types;
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    inspect(node, path);
  }

  private void inspect(MethodInvocationTree node, TreePath path) {
    if (!(node.getMethodSelect() instanceof MemberSelectTree select)
        || node.getArguments().isEmpty()) {
      return;
    }
    String method = select.getIdentifier().toString();
    boolean lambdaForm = LAMBDA_METHODS.contains(method);
    if (!lambdaForm && !ZIP_WITH.equals(method)) {
      return;
    }

    TypeMirror receiver = LambdaReturns.typeOf(trees, path, select.getExpression());
    String receiverFqn = declaredFqn(receiver);
    if (receiverFqn == null || !ERROR_TYPED.contains(receiverFqn)) {
      return; // not an error-typed Path receiver
    }
    TypeMirror receiverE = firstTypeArg(receiver);
    if (!concrete(receiverE)) {
      return; // receiver error type is a type variable / wildcard: no false positives
    }

    ExpressionTree arg0 = node.getArguments().getFirst();
    List<TypeMirror> steps =
        lambdaForm
            ? LambdaReturns.lambdaReturnTypes(trees, path, arg0)
            : singleton(LambdaReturns.typeOf(trees, path, arg0));

    // A lambda may mix a correct and an incorrect return; flag the first that breaks the rule.
    for (TypeMirror step : steps) {
      if (!receiverFqn.equals(declaredFqn(step))) {
        continue; // different category — a separate concern, out of scope here
      }
      TypeMirror stepE = firstTypeArg(step);
      if (!concrete(stepE)) {
        continue; // type variable / wildcard / unresolved: no false positives
      }
      if (!types.isAssignable(stepE, receiverE)) {
        trees.printMessage(
            severity,
            DiagnosticMessages.errorTypeMismatch(method, simpleName(receiverE), simpleName(stepE)),
            node,
            path.getCompilationUnit());
        return;
      }
    }
  }

  private static List<TypeMirror> singleton(TypeMirror t) {
    return t == null ? List.of() : List.of(t);
  }

  private static boolean concrete(TypeMirror t) {
    return t != null && t.getKind() == TypeKind.DECLARED;
  }

  private static String declaredFqn(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getQualifiedName().toString()
        : null;
  }

  private static TypeMirror firstTypeArg(TypeMirror t) {
    return t instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()
        ? dt.getTypeArguments().getFirst()
        : null;
  }

  private static String simpleName(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getSimpleName().toString()
        : String.valueOf(t);
  }
}
