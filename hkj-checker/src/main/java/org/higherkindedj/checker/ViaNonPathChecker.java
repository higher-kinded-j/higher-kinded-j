// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Detects {@code via}/{@code flatMap}/{@code then} given a function that returns a plain value
 * instead of a {@code Chainable} ({@code effect/compiler_errors.md} section 3).
 *
 * <p>{@code via} is the Path-level {@code flatMap}: its function must return a Path. Passing a
 * function that returns a plain {@code B} is a genuine javac "method … cannot be applied" error;
 * the actionable fix is "use {@code map} instead". This checker adds that companion alongside
 * javac's message.
 *
 * <h2>Rule</h2>
 *
 * <p>On {@code via}/{@code flatMap}/{@code then} where the receiver is a {@code Chainable} (HKJ
 * Path) and the lambda/supplier return resolves to a concrete {@link TypeKind#DECLARED} type that
 * is <b>not</b> assignable to {@code Chainable} → diagnose. Method references, unresolved bodies,
 * type variables, wildcards and {@code void} bodies are skipped, and a Path-returning function is
 * never flagged — preserving the no-false-positives policy.
 */
public final class ViaNonPathChecker implements CheckVisitor {

  private static final Set<String> CHAIN_METHODS = Set.of("via", "flatMap", "then");

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
  public ViaNonPathChecker(Trees trees, Types types, Elements elements) {
    this(trees, types, elements, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param types the model Types utility from the javac task
   * @param elements the model Elements utility from the javac task
   * @param severity the severity at which the companion diagnostic is reported
   */
  public ViaNonPathChecker(Trees trees, Types types, Elements elements, Diagnostic.Kind severity) {
    this.trees = trees;
    this.types = types;
    this.elements = elements;
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    inspect(node, path);
  }

  private void inspect(MethodInvocationTree node, TreePath path) {
    if (!(node.getMethodSelect() instanceof MemberSelectTree select)
        || !CHAIN_METHODS.contains(select.getIdentifier().toString())
        || node.getArguments().isEmpty()) {
      return;
    }
    TypeElement chainable = elements.getTypeElement(DiscardedEffectChecker.CHAINABLE_FQN);
    if (chainable == null) {
      return; // effect API not on the classpath
    }
    TypeMirror chainableErasure = types.erasure(chainable.asType());

    TypeMirror receiver = LambdaReturns.typeOf(trees, path, select.getExpression());
    if (!isDeclaredAssignableTo(receiver, chainableErasure)) {
      return; // not an HKJ chain receiver: out of scope
    }

    List<TypeMirror> returns =
        LambdaReturns.lambdaReturnTypes(trees, path, node.getArguments().getFirst());
    // A lambda may mix a Path-returning and a plain-value return; flag the first plain one.
    for (TypeMirror ret : returns) {
      if (ret.getKind() != TypeKind.DECLARED) {
        continue; // void / type-var / wildcard: skip silently
      }
      if (!types.isAssignable(types.erasure(ret), chainableErasure)) {
        trees.printMessage(
            severity,
            DiagnosticMessages.viaNonPath(select.getIdentifier().toString(), simpleName(ret)),
            node,
            path.getCompilationUnit());
        return;
      }
    }
  }

  private boolean isDeclaredAssignableTo(TypeMirror t, TypeMirror target) {
    return t instanceof DeclaredType && types.isAssignable(types.erasure(t), target);
  }

  private static String simpleName(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getSimpleName().toString()
        : String.valueOf(t);
  }
}
