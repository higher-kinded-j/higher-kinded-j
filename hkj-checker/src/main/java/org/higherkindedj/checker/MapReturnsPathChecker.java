// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Detects {@code map} given a function that returns the <em>same</em> Path type, silently nesting
 * the effect (the silent dual of {@link ViaNonPathChecker}).
 *
 * <p>{@code map} is the functor map: {@code p.map(x -> Path.right(x))} makes the result {@code
 * EitherPath<E, EitherPath<E, X>>} and <em>compiles</em> (javac does not error). The user almost
 * always meant {@code via}. Because there is no javac error to sit beside, this checker is the sole
 * signal and so defaults to a <b>warning</b>.
 *
 * <h2>Rule (pinned by the detector spike)</h2>
 *
 * <p>On {@code map} where the receiver is a {@code Chainable} (HKJ Path) and the lambda/supplier
 * return resolves to a {@link TypeKind#DECLARED} type of the <b>same Path category</b> as the
 * receiver (same erased type) → diagnose. The same-category restriction excludes the ambiguous
 * collection/cross-category cases (e.g. {@code ListPath.map(-> EitherPath)}, plausibly legitimate
 * applicative/traverse prep) and needs no maintained category list. Method references, unresolved
 * bodies, type variables and wildcards are skipped, and the {@code Chainable}-receiver gate filters
 * plain-Java {@code map} entirely — preserving the no-false-positives policy.
 */
public final class MapReturnsPathChecker implements CheckVisitor {

  private final Trees trees;
  private final Types types;
  private final Elements elements;
  private final Diagnostic.Kind severity;

  /**
   * Creates a checker reporting at {@link Diagnostic.Kind#WARNING} (sole-signal soak default).
   *
   * @param trees the {@link Trees} utility for AST and type resolution
   * @param types the {@link Types} utility for type operations
   * @param elements the {@link Elements} utility for element operations
   */
  public MapReturnsPathChecker(Trees trees, Types types, Elements elements) {
    this(trees, types, elements, Diagnostic.Kind.WARNING);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param types the model Types utility from the javac task
   * @param elements the model Elements utility from the javac task
   * @param severity the severity at which the diagnostic is reported
   */
  public MapReturnsPathChecker(
      Trees trees, Types types, Elements elements, Diagnostic.Kind severity) {
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
        || !select.getIdentifier().contentEquals("map")
        || node.getArguments().isEmpty()) {
      return;
    }
    TypeElement chainable = elements.getTypeElement(DiscardedEffectChecker.CHAINABLE_FQN);
    if (chainable == null) {
      return; // effect API not on the classpath
    }
    TypeMirror chainableErasure = types.erasure(chainable.asType());

    TypeMirror receiver = LambdaReturns.typeOf(trees, path, select.getExpression());
    String receiverFqn = declaredFqn(receiver);
    if (receiverFqn == null || !types.isAssignable(types.erasure(receiver), chainableErasure)) {
      return; // not an HKJ Chainable receiver: out of scope (filters plain-Java map)
    }

    List<TypeMirror> returns =
        LambdaReturns.lambdaReturnTypes(trees, path, node.getArguments().getFirst());
    // A lambda may mix a nesting and a non-nesting return; flag the first nesting one.
    for (TypeMirror ret : returns) {
      if (ret.getKind() != TypeKind.DECLARED) {
        continue; // type-var / wildcard: skip silently
      }
      // Same-category only: excludes the ambiguous collection/cross-category cases.
      if (receiverFqn.equals(declaredFqn(ret))) {
        trees.printMessage(
            severity,
            DiagnosticMessages.mapNestsEffect(simpleName(ret)),
            node,
            path.getCompilationUnit());
        return;
      }
    }
  }

  private static String declaredFqn(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getQualifiedName().toString()
        : null;
  }

  private static String simpleName(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getSimpleName().toString()
        : String.valueOf(t);
  }
}
