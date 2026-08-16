// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * The single AST pass that drives every enabled HKJ check.
 *
 * <p>A single {@code TreePathScanner} visits each node once and fans out to the enabled {@link
 * CheckVisitor}s, so total cost is {@code O(N)} in the number of AST nodes rather than {@code
 * O(checks × N)}. Every hook receives the live {@link TreePathScanner#getCurrentPath() current
 * path}, so each check resolves types against the same attributed tree it would see from its own
 * scanner. Checks are dispatched in list order, so multi-diagnostic ordering on a node is stable.
 *
 * <h2>What is not checked</h2>
 *
 * <p>Two gates keep diagnostics off code whose reader cannot act on them. Both are carried in the
 * scan's parameter — the set of check ids suppressed for the subtree being visited — so scoping is
 * enforced by the traversal rather than by mutable scanner state.
 *
 * <ul>
 *   <li>A type annotated {@link #HKJ_GENERATED} is skipped whole. Every check is advice its reader
 *       is expected to act on, and nobody edits annotation-processor output; the migration nudges
 *       would go further and tell a {@code @EffectAlgebra} user to reach for the generated {@code
 *       *Ops} smart constructors from inside the generated {@code *Ops} class itself.
 *   <li>A class, method or variable declaring {@code @SuppressWarnings} with a check id — or with
 *       {@link CheckerConfig#SUPPRESS_ALL} — silences that check for everything inside it.
 *       Suppression accumulates outward-in, so an enclosing declaration's tokens still apply within
 *       nested ones. This is the only way to accept a single deliberate call site; the {@code
 *       disable=} plugin argument is project-wide.
 * </ul>
 *
 * <p>Only HKJ's own marker gates the first rule. The {@code javax}/{@code jakarta}
 * {@code @Generated} annotations are also applied by hand — JaCoCo excludes any type carrying an
 * annotation named {@code Generated}, and this project's own {@code
 * org.higherkindedj.annotation.Generated} exists for exactly that — so honouring them would let a
 * coverage marker on hand-written code silently disable every check on the file. For the same
 * reason javac's blanket {@code @SuppressWarnings("all")} is not honoured: opting out of an HKJ
 * check is spelled explicitly.
 */
final class HkjCheckScanner extends TreePathScanner<Void, Set<String>> {

  /** The marker HKJ's annotation processors apply to every type they generate. */
  private static final String HKJ_GENERATED = "org.higherkindedj.optics.annotations.Generated";

  /**
   * One enabled check, paired with the id used to configure and to suppress it.
   *
   * @param id the check id, as listed on {@link CheckerConfig}
   * @param visitor the check itself
   */
  record Check(String id, CheckVisitor visitor) {}

  private final Trees trees;
  private final List<Check> checks;

  HkjCheckScanner(Trees trees, List<Check> checks) {
    this.trees = trees;
    this.checks = List.copyOf(checks);
  }

  /** The set a scan starts from: nothing suppressed. */
  static Set<String> nothingSuppressed() {
    return Set.of();
  }

  @Override
  public Void visitClass(ClassTree node, Set<String> suppressed) {
    Element declaration = declarationAtCurrentPath();
    if (isGenerated(declaration)) {
      return null; // generated: skip the type and everything in it
    }
    return super.visitClass(node, extendedWith(suppressed, declaration));
  }

  @Override
  public Void visitMethod(MethodTree node, Set<String> suppressed) {
    Set<String> inScope = extendedWith(suppressed, declarationAtCurrentPath());
    dispatch(inScope, (check, path) -> check.onMethod(node, path));
    return super.visitMethod(node, inScope);
  }

  @Override
  public Void visitVariable(VariableTree node, Set<String> suppressed) {
    Set<String> inScope = extendedWith(suppressed, declarationAtCurrentPath());
    dispatch(inScope, (check, path) -> check.onVariable(node, path));
    return super.visitVariable(node, inScope);
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onMethodInvocation(node, path));
    return super.visitMethodInvocation(node, suppressed);
  }

  @Override
  public Void visitNewClass(NewClassTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onNewClass(node, path));
    return super.visitNewClass(node, suppressed);
  }

  @Override
  public Void visitExpressionStatement(ExpressionStatementTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onExpressionStatement(node, path));
    return super.visitExpressionStatement(node, suppressed);
  }

  @Override
  public Void visitSwitch(SwitchTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onSwitch(node, path));
    return super.visitSwitch(node, suppressed);
  }

  @Override
  public Void visitSwitchExpression(SwitchExpressionTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onSwitchExpression(node, path));
    return super.visitSwitchExpression(node, suppressed);
  }

  @Override
  public Void visitParameterizedType(ParameterizedTypeTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onParameterizedType(node, path));
    return super.visitParameterizedType(node, suppressed);
  }

  @Override
  public Void visitTypeCast(TypeCastTree node, Set<String> suppressed) {
    dispatch(suppressed, (check, path) -> check.onTypeCast(node, path));
    return super.visitTypeCast(node, suppressed);
  }

  /** Hands the node to every enabled check the surrounding declarations have not suppressed. */
  private void dispatch(Set<String> suppressed, CheckHook hook) {
    TreePath path = getCurrentPath();
    for (Check check : checks) {
      if (suppressed.isEmpty()
          || !(suppressed.contains(check.id())
              || suppressed.contains(CheckerConfig.SUPPRESS_ALL))) {
        hook.accept(check.visitor(), path);
      }
    }
  }

  /** One check's per-node hook, bound to the node being visited. */
  @FunctionalInterface
  private interface CheckHook {
    void accept(CheckVisitor check, TreePath path);
  }

  /**
   * Resolves the declaration at the current path, or null when javac cannot. A checker must never
   * break the compilation it is inspecting, so a resolution failure degrades to "no annotations
   * found" rather than propagating.
   */
  private Element declarationAtCurrentPath() {
    try {
      return trees.getElement(getCurrentPath());
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Adds any {@code @SuppressWarnings} tokens on the given declaration to those already in force.
   * Resolution goes through the element model rather than the annotation's syntax, so a
   * fully-qualified use, a constant-valued argument and the single-element form all read the same,
   * and a same-named annotation from another package is not mistaken for {@code
   * java.lang.SuppressWarnings}.
   */
  private static Set<String> extendedWith(Set<String> suppressed, Element declaration) {
    if (declaration == null) {
      return suppressed;
    }
    SuppressWarnings declared;
    try {
      declared = declaration.getAnnotation(SuppressWarnings.class);
    } catch (RuntimeException e) {
      return suppressed;
    }
    if (declared == null || declared.value().length == 0) {
      return suppressed;
    }
    Set<String> widened = new HashSet<>(suppressed);
    widened.addAll(Arrays.asList(declared.value()));
    return Set.copyOf(widened);
  }

  /** Reports whether the given type was emitted by an HKJ annotation processor. */
  private static boolean isGenerated(Element declaration) {
    if (declaration == null) {
      return false;
    }
    for (AnnotationMirror mirror : declaration.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().asElement() instanceof TypeElement marker
          && HKJ_GENERATED.contentEquals(marker.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }
}
