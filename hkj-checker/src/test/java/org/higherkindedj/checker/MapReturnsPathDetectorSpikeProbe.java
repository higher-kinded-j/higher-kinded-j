// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Spike instrumentation (test-only): at every {@code map} call on a {@code Chainable} receiver,
 * resolve the lambda/supplier return and record whether the candidate rule — flag iff the return is
 * itself a {@code Chainable} (silent nesting; the user meant {@code via}) — fires. The decisive
 * questions: does it stay silent on correct code and on plain-Java {@code map}, and is the
 * collection-of-effects pattern a real exception?
 */
public final class MapReturnsPathDetectorSpikeProbe implements Plugin {

  static final String CHAINABLE = "org.higherkindedj.hkt.effect.capability.Chainable";

  public static final List<String> OBSERVATIONS = new ArrayList<>();

  public static void reset() {
    OBSERVATIONS.clear();
  }

  @Override
  public String getName() {
    return "HKJMapReturnsPathDetectorSpike";
  }

  @Override
  public void init(JavacTask task, String... args) {
    Trees trees = Trees.instance(task);
    Types types = task.getTypes();
    Elements elements = task.getElements();

    task.addTaskListener(
        new TaskListener() {
          @Override
          public void finished(TaskEvent event) {
            if (event.getKind() != TaskEvent.Kind.ANALYZE || event.getCompilationUnit() == null) {
              return;
            }
            new TreePathScanner<Void, Void>() {
              @Override
              public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                evaluate(node);
                return super.visitMethodInvocation(node, unused);
              }

              private void evaluate(MethodInvocationTree node) {
                if (!(node.getMethodSelect() instanceof MemberSelectTree select)
                    || !select.getIdentifier().contentEquals("map")
                    || node.getArguments().isEmpty()) {
                  return;
                }
                TypeElement ch = elements.getTypeElement(CHAINABLE);
                if (ch == null) {
                  return;
                }
                TypeMirror chErasure = types.erasure(ch.asType());
                TypeMirror recv = typeOf(select.getExpression());
                if (!(recv instanceof DeclaredType)
                    || !types.isAssignable(types.erasure(recv), chErasure)) {
                  return; // not a Chainable receiver: out of scope (filters plain-Java map)
                }
                TypeMirror ret = lambdaReturn(node.getArguments().getFirst());
                String verdict;
                if (ret == null) {
                  verdict = "SKIP:unresolved-or-method-ref";
                } else if (ret.getKind() != TypeKind.DECLARED) {
                  verdict = "SKIP:non-declared-" + ret.getKind();
                } else if (types.isAssignable(types.erasure(ret), chErasure)) {
                  verdict = "FLAG:returns-Chainable";
                } else {
                  verdict = "OK:returns-plain";
                }
                OBSERVATIONS.add(
                    "recv=" + simple(recv) + " | ret=" + simple(ret) + " | " + verdict);
              }

              private TypeMirror typeOf(Tree t) {
                try {
                  return trees.getTypeMirror(new TreePath(getCurrentPath(), t));
                } catch (RuntimeException e) {
                  return null;
                }
              }

              private TypeMirror lambdaReturn(ExpressionTree arg) {
                if (!(arg instanceof LambdaExpressionTree lambda)) {
                  return null;
                }
                if (lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION) {
                  return typeOf((ExpressionTree) lambda.getBody());
                }
                ReturnTree[] first = new ReturnTree[1];
                new TreePathScanner<Void, Void>() {
                  @Override
                  public Void visitReturn(ReturnTree r, Void u) {
                    if (first[0] == null && r.getExpression() != null) {
                      first[0] = r;
                    }
                    return super.visitReturn(r, u);
                  }
                }.scan(new TreePath(getCurrentPath(), lambda.getBody()), null);
                return first[0] == null ? null : typeOf(first[0].getExpression());
              }
            }.scan(event.getCompilationUnit(), null);
          }
        });
  }

  private static String simple(TypeMirror t) {
    if (t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
      return te.getSimpleName().toString();
    }
    return t == null ? "<null>" : t + "/" + t.getKind();
  }
}
