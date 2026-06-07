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
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * Spike instrumentation (test-only): maps the false-positive surface of the candidate item-4
 * detector — comparing the receiver's error type {@code E} against the lambda/supplier return's
 * {@code E} on an error-typed Path chain ({@code EitherPath}/{@code ValidationPath}), where javac
 * silently erases the mismatch (see {@code ErrorTypeMismatchSpikeTest}).
 *
 * <p>For each {@code via}/{@code flatMap}/{@code then} call on such a receiver it records: the
 * resolved receiver {@code E}, the resolved lambda-return {@code E}, whether each is a concrete
 * (comparable) type, and the verdict the candidate rule would reach. The characterization test
 * asserts the true-positive / no-false-positive matrix from these records rather than by
 * assumption.
 */
public final class ErrorTypeMismatchDetectorSpikeProbe implements Plugin {

  private static final Set<String> CHAIN_METHODS = Set.of("via", "flatMap", "then");
  private static final Set<String> ERROR_TYPED =
      Set.of(
          "org.higherkindedj.hkt.effect.EitherPath", "org.higherkindedj.hkt.effect.ValidationPath");

  /** "method | recvE=<..> | lamE=<..> | verdict=<FLAG|OK|SKIP:reason>". */
  public static final List<String> OBSERVATIONS = new ArrayList<>();

  public static void reset() {
    OBSERVATIONS.clear();
  }

  @Override
  public String getName() {
    return "HKJErrorTypeMismatchDetectorSpike";
  }

  @Override
  public void init(JavacTask task, String... args) {
    Trees trees = Trees.instance(task);
    Types types = task.getTypes();

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
                inspect(node);
                return super.visitMethodInvocation(node, unused);
              }

              private void inspect(MethodInvocationTree node) {
                if (!(node.getMethodSelect() instanceof MemberSelectTree select)
                    || !CHAIN_METHODS.contains(select.getIdentifier().toString())
                    || node.getArguments().isEmpty()) {
                  return;
                }
                String method = select.getIdentifier().toString();
                TypeMirror recv = typeOf(select.getExpression());
                String recvFqn = erasedFqn(recv);
                if (recvFqn == null || !ERROR_TYPED.contains(recvFqn)) {
                  return; // not an error-typed Path receiver; out of scope
                }
                TypeMirror recvE = firstTypeArg(recv);

                ExpressionTree arg0 = node.getArguments().getFirst();
                TypeMirror ret = lambdaReturnType(arg0);
                if (ret == null) {
                  record(method, recvE, null, "SKIP:unresolved-lambda-return");
                  return;
                }
                String retFqn = erasedFqn(ret);
                if (retFqn == null || !retFqn.equals(recvFqn)) {
                  // Different category (also silently erased) — a separate, out-of-scope concern.
                  record(method, recvE, null, "SKIP:return-not-same-error-typed-category");
                  return;
                }
                TypeMirror retE = firstTypeArg(ret);
                if (!concrete(recvE) || !concrete(retE)) {
                  record(method, recvE, retE, "SKIP:non-concrete-E");
                  return;
                }
                boolean same = types.isSameType(recvE, retE);
                record(method, recvE, retE, same ? "OK:same-E" : "FLAG:different-E");
              }

              private TypeMirror typeOf(Tree t) {
                try {
                  return trees.getTypeMirror(new TreePath(getCurrentPath(), t));
                } catch (RuntimeException e) {
                  return null;
                }
              }

              private TypeMirror lambdaReturnType(ExpressionTree arg) {
                if (!(arg instanceof LambdaExpressionTree lambda)) {
                  return null; // method ref / other: deliberately unresolved
                }
                if (lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION) {
                  return typeOf((ExpressionTree) lambda.getBody());
                }
                var found = new ReturnTree[1];
                new TreePathScanner<Void, Void>() {
                  @Override
                  public Void visitReturn(ReturnTree r, Void u) {
                    if (found[0] == null && r.getExpression() != null) {
                      found[0] = r;
                    }
                    return super.visitReturn(r, u);
                  }
                }.scan(new TreePath(getCurrentPath(), lambda.getBody()), null);
                return found[0] == null ? null : typeOf(found[0].getExpression());
              }
            }.scan(event.getCompilationUnit(), null);
          }
        });
  }

  private static boolean concrete(TypeMirror t) {
    return t != null
        && t.getKind() == TypeKind.DECLARED; // excludes TYPEVAR, WILDCARD, ERROR, NONE, etc.
  }

  private static String erasedFqn(TypeMirror t) {
    if (t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
      return te.getQualifiedName().toString();
    }
    return null;
  }

  private static TypeMirror firstTypeArg(TypeMirror t) {
    if (t instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
      return dt.getTypeArguments().getFirst();
    }
    return null;
  }

  private static void record(String method, TypeMirror recvE, TypeMirror retE, String verdict) {
    OBSERVATIONS.add(
        method
            + " | recvE="
            + (recvE == null ? "<none>" : recvE + "/" + recvE.getKind())
            + " | retE="
            + (retE == null ? "<none>" : retE + "/" + retE.getKind())
            + " | verdict="
            + verdict);
  }
}
