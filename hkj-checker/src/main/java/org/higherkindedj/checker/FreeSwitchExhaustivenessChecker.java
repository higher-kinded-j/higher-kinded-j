// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Detects a {@code switch} over {@code Free} that pattern-matches the older variants but omits
 * {@code HandleError} and/or {@code Ap}.
 *
 * <p>This consolidates the {@code AddHandleErrorCaseRecipe} OpenRewrite diagnosis into compile-time
 * feedback. {@code Free} is sealed over {@code Pure}, {@code Suspend}, {@code FlatMapped}, {@code
 * HandleError} and {@code Ap}; an interpreter written before the error-handling/applicative
 * variants existed silently stops being exhaustive once they are used.
 *
 * <p>To keep the no-false-positives policy the switch is only inspected when its selector's
 * attributed type resolves to the HKJ {@code Free} type. Each case label's <em>pattern type</em> is
 * then resolved through the public {@code javax.lang.model} API and compared by fully-qualified
 * name to the {@code Free} variants — a semantic check, not textual matching, so guards, binding
 * names and comments cannot perturb it.
 */
public final class FreeSwitchExhaustivenessChecker implements CheckVisitor {

  private static final String FREE_FQN = "org.higherkindedj.hkt.free.Free";
  private static final Set<String> KNOWN_FREE_CASES =
      Set.of(FREE_FQN + ".Pure", FREE_FQN + ".Suspend", FREE_FQN + ".FlatMapped");
  private static final String HANDLE_ERROR_FQN = FREE_FQN + ".HandleError";
  private static final String AP_FQN = FREE_FQN + ".Ap";

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /** Creates a checker reporting at {@link Diagnostic.Kind#ERROR}. */
  public FreeSwitchExhaustivenessChecker(Trees trees) {
    this(trees, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param severity the severity at which the companion diagnostic is reported
   */
  public FreeSwitchExhaustivenessChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = trees;
    this.severity = severity;
  }

  @Override
  public void onSwitch(SwitchTree node, TreePath path) {
    inspect(node, node.getExpression(), node.getCases(), path);
  }

  @Override
  public void onSwitchExpression(SwitchExpressionTree node, TreePath path) {
    inspect(node, node.getExpression(), node.getCases(), path);
  }

  private void inspect(
      Tree switchNode, ExpressionTree selector, List<? extends CaseTree> cases, TreePath path) {
    if (!FREE_FQN.equals(declaredFqn(LambdaReturns.typeOf(trees, path, selector)))) {
      return; // selector is not the HKJ Free type: skip silently (no false positives)
    }
    boolean matchesFreeVariants = false;
    boolean hasHandleError = false;
    boolean hasAp = false;
    for (CaseTree c : cases) {
      for (CaseLabelTree label : c.getLabels()) {
        String fqn = patternTypeFqn(label, path);
        if (fqn == null) {
          continue;
        }
        if (KNOWN_FREE_CASES.contains(fqn)) {
          matchesFreeVariants = true;
        } else if (HANDLE_ERROR_FQN.equals(fqn)) {
          hasHandleError = true;
        } else if (AP_FQN.equals(fqn)) {
          hasAp = true;
        }
      }
    }
    if (matchesFreeVariants && (!hasHandleError || !hasAp)) {
      trees.printMessage(
          severity,
          DiagnosticMessages.freeSwitchMissingCases(!hasHandleError, !hasAp),
          switchNode,
          path.getCompilationUnit());
    }
  }

  /** The fully-qualified name of a pattern case label's matched type, or {@code null}. */
  private String patternTypeFqn(CaseLabelTree label, TreePath path) {
    if (!(label instanceof PatternCaseLabelTree patternLabel)) {
      return null; // constant / default / null label: not a Free variant
    }
    return declaredFqn(LambdaReturns.typeOf(trees, path, patternLabel.getPattern()));
  }

  private static String declaredFqn(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getQualifiedName().toString()
        : null;
  }
}
