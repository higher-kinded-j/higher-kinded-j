// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Advisory migration nudges (consolidates the {@code ConvertRawFreeToFreePathRecipe} and {@code
 * DetectInjectBoilerplateRecipe} OpenRewrite recipes into compile-time feedback).
 *
 * <p>Both target <em>valid</em> code, not errors — they point at a more ergonomic API:
 *
 * <ul>
 *   <li>{@code Free.liftF(...)} / {@code Free.suspend(...)} → the {@code FreePath} fluent API or
 *       generated {@code *Ops} smart constructors;
 *   <li>{@code InjectInstances.injectLeft/injectRight/injectRightThen(...)} →
 *       {@code @ComposeEffects} and its generated Support class.
 * </ul>
 *
 * <p>Because this is advice over compiling code (sole signal, never a real error), it is
 * <b>warn-default</b> — promotable per project via {@code severity:migration-nudge=error} for teams
 * that want to enforce the migration. Detection resolves the invoked method's <em>element</em> (not
 * a textual receiver), so it is correct for both qualified and static-import call sites and is
 * gated on the declaring type's fully-qualified name — no false positives on unrelated same-named
 * methods. Unresolved invocations are skipped.
 */
public final class MigrationNudgeChecker implements CheckVisitor {

  private static final String FREE_FQN = "org.higherkindedj.hkt.free.Free";
  private static final String INJECT_FQN = "org.higherkindedj.hkt.inject.InjectInstances";
  private static final Set<String> FREE_FACTORIES = Set.of("liftF", "suspend");
  private static final Set<String> INJECT_FACTORIES =
      Set.of("injectLeft", "injectRight", "injectRightThen");

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /**
   * Creates a checker reporting at {@link Diagnostic.Kind#WARNING} (advisory default).
   *
   * @param trees the {@link Trees} utility for AST and type resolution
   */
  public MigrationNudgeChecker(Trees trees) {
    this(trees, Diagnostic.Kind.WARNING);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param severity the severity at which the nudge is reported
   */
  public MigrationNudgeChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = trees;
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    Element el;
    try {
      el = trees.getElement(path);
    } catch (RuntimeException e) {
      el = null;
    }
    if (el instanceof ExecutableElement m
        && m.getKind() == ElementKind.METHOD
        && m.getEnclosingElement() instanceof TypeElement owner) {
      String fqn = owner.getQualifiedName().toString();
      String method = m.getSimpleName().toString();
      if (FREE_FQN.equals(fqn) && FREE_FACTORIES.contains(method)) {
        report(node, path, DiagnosticMessages.rawFreeConstruction(method));
      } else if (INJECT_FQN.equals(fqn) && INJECT_FACTORIES.contains(method)) {
        report(node, path, DiagnosticMessages.injectBoilerplate(method));
      }
    }
  }

  private void report(MethodInvocationTree node, TreePath path, String message) {
    trees.printMessage(severity, message, node, path.getCompilationUnit());
  }
}
