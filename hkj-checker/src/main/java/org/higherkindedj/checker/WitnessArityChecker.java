// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Detects a higher-kinded witness that does not satisfy {@code WitnessArity} (consolidates the
 * {@code AddArityBoundsToTypeParametersRecipe} and {@code AddWitnessArityToWitnessClass}
 * OpenRewrite recipes into compile-time feedback).
 *
 * <p>{@code Kind}/{@code Kind2}/{@code Monad}/{@code Functor}/{@code Applicative} all declare their
 * witness parameter as {@code <… extends WitnessArity<…>>}. Two common mistakes violate this and
 * produce javac's cryptic {@code type argument … is not within bounds} error: an unbounded type
 * parameter used in a witness position, and a {@code Witness}-style class that forgets {@code
 * implements WitnessArity}. This checker adds the actionable companion alongside that error.
 *
 * <h2>Rule (pinned by the detector spike)</h2>
 *
 * <p>At a {@code Kind}/{@code Kind2}/type-class usage, resolve the witness type argument {@code X}
 * and diagnose iff {@code X} resolves and is not assignable to {@code WitnessArity} — for a type
 * variable that means its upper bound, for a class its own type. Unresolved, wildcard and other
 * non-comparable kinds are skipped, so correct HKJ-style witnesses (including the whole real
 * library) are never flagged.
 */
public final class WitnessArityChecker implements CheckVisitor {

  static final String WITNESS_ARITY_FQN = "org.higherkindedj.hkt.WitnessArity";

  private static final Set<String> HKT_GENERICS =
      Set.of(
          "org.higherkindedj.hkt.Kind",
          "org.higherkindedj.hkt.Kind2",
          "org.higherkindedj.hkt.Monad",
          "org.higherkindedj.hkt.Functor",
          "org.higherkindedj.hkt.Applicative");

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
  public WitnessArityChecker(Trees trees, Types types, Elements elements) {
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
  public WitnessArityChecker(
      Trees trees, Types types, Elements elements, Diagnostic.Kind severity) {
    this.trees = trees;
    this.types = types;
    this.elements = elements;
    this.severity = severity;
  }

  @Override
  public void onParameterizedType(ParameterizedTypeTree node, TreePath path) {
    inspect(node, path);
  }

  private void inspect(ParameterizedTypeTree node, TreePath path) {
    if (node.getTypeArguments().isEmpty()) {
      return;
    }
    String genericFqn = declaredFqn(typeOf(path, node.getType()));
    if (genericFqn == null || !HKT_GENERICS.contains(genericFqn)) {
      return;
    }
    Tree witnessArg = node.getTypeArguments().getFirst();
    TypeMirror x = typeOf(path, witnessArg);
    if (x == null) {
      return; // unresolved: skip silently (no false positives)
    }
    TypeElement wa = elements.getTypeElement(WITNESS_ARITY_FQN);
    if (wa == null) {
      return; // WitnessArity not on the classpath: nothing to check
    }
    TypeMirror waErasure = types.erasure(wa.asType());

    boolean typeVariable;
    boolean satisfies;
    if (x.getKind() == TypeKind.TYPEVAR) {
      typeVariable = true;
      // Assignability on the type variable itself honours its (possibly intersection) bound;
      // erasing the upper bound would collapse `A & WitnessArity<…>` to `A` and false-positive.
      satisfies = types.isAssignable(x, waErasure);
    } else if (x.getKind() == TypeKind.DECLARED) {
      typeVariable = false;
      satisfies = types.isAssignable(types.erasure(x), waErasure);
    } else {
      return; // wildcard / error / other: skip silently
    }
    if (!satisfies) {
      trees.printMessage(
          severity,
          DiagnosticMessages.witnessArity(typeVariable, witnessArg.toString()),
          witnessArg,
          path.getCompilationUnit());
    }
  }

  private TypeMirror typeOf(TreePath path, Tree t) {
    try {
      return trees.getTypeMirror(new TreePath(path, t));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String declaredFqn(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getQualifiedName().toString()
        : null;
  }
}
