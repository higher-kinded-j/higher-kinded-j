// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ParameterizedTypeTree;
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
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Spike instrumentation (test-only): at every {@code Kind}/{@code Kind2}/type-class usage, resolve
 * the witness type argument {@code X} and record whether the candidate unifying rule — flag iff
 * {@code X} resolves and is not assignable to {@code WitnessArity} — fires. The decisive question
 * is the false-positive surface: it must stay silent on correct HKJ-style code.
 */
public final class WitnessArityDetectorSpikeProbe implements Plugin {

  static final String WITNESS_ARITY = "org.higherkindedj.hkt.WitnessArity";
  private static final Set<String> HKT_GENERICS =
      Set.of(
          "org.higherkindedj.hkt.Kind",
          "org.higherkindedj.hkt.Kind2",
          "org.higherkindedj.hkt.Monad",
          "org.higherkindedj.hkt.Functor",
          "org.higherkindedj.hkt.Applicative");

  public static final List<String> OBSERVATIONS = new ArrayList<>();

  public static void reset() {
    OBSERVATIONS.clear();
  }

  @Override
  public String getName() {
    return "HKJWitnessArityDetectorSpike";
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
              public Void visitParameterizedType(ParameterizedTypeTree node, Void unused) {
                evaluate(node);
                return super.visitParameterizedType(node, unused);
              }

              private void evaluate(ParameterizedTypeTree node) {
                if (node.getTypeArguments().isEmpty()) {
                  return;
                }
                TypeMirror generic = typeOf(node.getType());
                String genericFqn = fqn(generic);
                if (genericFqn == null || !HKT_GENERICS.contains(genericFqn)) {
                  return;
                }
                Tree witnessArg = node.getTypeArguments().getFirst();
                TypeMirror x = typeOf(witnessArg);
                String verdict = classify(x, types, elements);
                OBSERVATIONS.add(
                    genericFqn.substring(genericFqn.lastIndexOf('.') + 1)
                        + "<"
                        + witnessArg
                        + ",…> | x="
                        + (x == null ? "<null>" : x + "/" + x.getKind())
                        + " | "
                        + verdict);
              }

              private TypeMirror typeOf(Tree t) {
                try {
                  return trees.getTypeMirror(new TreePath(getCurrentPath(), t));
                } catch (RuntimeException e) {
                  return null;
                }
              }
            }.scan(event.getCompilationUnit(), null);
          }
        });
  }

  private static String classify(TypeMirror x, Types types, Elements elements) {
    if (x == null) {
      return "SKIP:unresolved";
    }
    TypeElement wa = elements.getTypeElement(WITNESS_ARITY);
    if (wa == null) {
      return "SKIP:no-WitnessArity-on-classpath";
    }
    TypeMirror waErasure = types.erasure(wa.asType());
    if (x.getKind() == TypeKind.TYPEVAR) {
      TypeMirror bound = ((TypeVariable) x).getUpperBound();
      return types.isAssignable(types.erasure(bound), waErasure)
          ? "OK:typevar-bounded"
          : "FLAG:typevar-unbounded";
    }
    if (x.getKind() == TypeKind.DECLARED) {
      return types.isAssignable(types.erasure(x), waErasure)
          ? "OK:declared-is-WitnessArity"
          : "FLAG:declared-not-WitnessArity";
    }
    return "SKIP:other-kind-" + x.getKind();
  }

  private static String fqn(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getQualifiedName().toString()
        : null;
  }
}
