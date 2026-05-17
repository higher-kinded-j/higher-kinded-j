// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.List;

/**
 * Spike instrumentation only (not shipped; lives in test sources).
 *
 * <p>Registered as an extra javac plugin during the spike test. For every {@code Path.right/left}
 * call it records what javac actually left on the AST node at {@code ANALYZE} when inference
 * failed: did {@code ANALYZE} fire at all, is {@code JCExpression.type} null, erroneous, or a
 * recovered concrete type? The clean-vs-brittle verdict rests on these observations.
 */
public final class PathRightInferenceSpikeProbe implements Plugin {

  /** Observations are static so the test can read them after compilation. */
  public static final List<String> OBSERVATIONS = new ArrayList<>();

  public static boolean analyzeFired = false;

  public static void reset() {
    OBSERVATIONS.clear();
    analyzeFired = false;
  }

  @Override
  public String getName() {
    return "HKJSpikeProbe";
  }

  @Override
  public void init(JavacTask task, String... args) {
    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent event) {
            if (event.getKind() != TaskEvent.Kind.ANALYZE) {
              return;
            }
            analyzeFired = true;
            var cu = event.getCompilationUnit();
            if (cu == null) {
              OBSERVATIONS.add("ANALYZE fired but compilationUnit == null");
              return;
            }
            new TreeScanner<Void, Void>() {
              @Override
              public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                if (node.getMethodSelect() instanceof MemberSelectTree ms
                    && (ms.getIdentifier().contentEquals("right")
                        || ms.getIdentifier().contentEquals("left"))) {
                  OBSERVATIONS.add(describe(node, ms));
                }
                return super.visitMethodInvocation(node, unused);
              }
            }.scan(cu, null);
          }
        });
  }

  private static String describe(MethodInvocationTree node, MemberSelectTree ms) {
    String shape = ms.getExpression() + "." + ms.getIdentifier() + "(...)";
    boolean hasWitness = !node.getTypeArguments().isEmpty();
    if (!(node instanceof JCTree.JCExpression jc)) {
      return shape + " | not a JCExpression";
    }
    Type t = jc.type;
    String typeState;
    if (t == null) {
      typeState = "type==null";
    } else if (t.isErroneous()) {
      typeState = "type ERRONEOUS (" + t + ", tag=" + t.getTag() + ", tsym=" + t.tsym + ")";
    } else {
      typeState = "type RESOLVED (" + t + ", tsym=" + t.tsym + ")";
    }
    return shape + " | witness=" + hasWitness + " | " + typeState;
  }
}
