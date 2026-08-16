// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Detects a lazy HKJ effect that is built then discarded as a bare statement.
 *
 * <p>A {@code Deferred} path describes a computation that has not run: {@code IOPath}, {@code
 * VTaskPath}, {@code LazyPath}, {@code FreePath} and the rest of the {@code Deferred} permits
 * clause. Composing one and then dropping it on the floor — {@code Path.io(() -> x).map(f);} as a
 * statement — compiles cleanly but does nothing at all. The library ships no
 * {@code @CheckReturnValue}, so this whole class of silent no-op is otherwise invisible.
 *
 * <p>Eager paths are deliberately out of scope. {@code Path.just(x).map(f)} discards its result
 * too, but {@code f} has already run, so the statement is not a no-op and the diagnostic's advice
 * ("nothing happened") would be false. {@code Deferred} draws that line as a type-level fact, so
 * this check needs no list of its own and a newly added path type has to choose a side.
 *
 * <h2>Rule</h2>
 *
 * <p>An {@link ExpressionStatementTree} whose expression is a <em>result-producing</em> call (a
 * {@link MethodInvocationTree} or {@link NewClassTree}) and whose attributed type is
 * erase-assignable to {@code org.higherkindedj.hkt.effect.capability.Deferred}.
 *
 * <p>Two consequences keep this false-positive-free:
 *
 * <ul>
 *   <li><b>Run effects exclude themselves.</b> Terminal operations ({@code unsafeRun()}, an
 *       interpreter {@code run}) return the contained value {@code A}, not a {@code Deferred}, so a
 *       statement that actually runs the effect is not flagged.
 *   <li><b>Only genuine discards are considered.</b> Assignments, compound assignments and {@code
 *       i++} are expression statements too, but the value is consumed; restricting to
 *       invocation/constructor expressions excludes them. Returned/passed effects are not
 *       statements at all.
 *   <li><b>Pass-through calls build nothing.</b> A guard such as {@code
 *       Objects.requireNonNull(path, "…")} hands back the very effect it was given, so its value is
 *       not a discard: the effect already exists and is used further down. Any method whose return
 *       type is one of its own type variables, used again among its parameters, is pass-through by
 *       signature and is skipped.
 * </ul>
 *
 * <p><b>Accepted scope boundary:</b> a local declared and never run ({@code EitherPath<…> x =
 * Path.right(1);}) is a {@code VariableTree}, not an expression statement, and is deliberately out
 * of reach — detecting it needs dataflow and would risk flagging legitimate "build now, run later"
 * code, violating the no-false-positives policy.
 */
public final class DiscardedEffectChecker implements CheckVisitor {

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
  public DiscardedEffectChecker(Trees trees, Types types, Elements elements) {
    this(trees, types, elements, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param types the model Types utility from the javac task
   * @param elements the model Elements utility from the javac task
   * @param severity the severity at which the diagnostic is reported
   */
  public DiscardedEffectChecker(
      Trees trees, Types types, Elements elements, Diagnostic.Kind severity) {
    this.trees = trees;
    this.types = types;
    this.elements = elements;
    this.severity = severity;
  }

  @Override
  public void onExpressionStatement(ExpressionStatementTree node, TreePath path) {
    ExpressionTree built = constructedExpression(node.getExpression(), path);
    if (built != null && isDeferred(built, path)) {
      trees.printMessage(
          severity,
          DiagnosticMessages.discardedEffect(simpleName(built, path)),
          node,
          path.getCompilationUnit());
    }
  }

  /**
   * Resolves the expression that actually produced the discarded value, looking through
   * pass-through calls, or null when the statement built nothing.
   *
   * <p>A guard such as {@code Objects.requireNonNull(path, "…")} hands back the argument it was
   * given, so what the statement discards is whatever that argument was. Unwrapping rather than
   * skipping keeps both readings right: the argument is a name, so {@code
   * Objects.requireNonNull(existing, "…")} built nothing and is silent, while {@code
   * Objects.requireNonNull(Path.io(() -> 1))} did build an effect and is still reported. Nested
   * pass-throughs unwrap the whole way down.
   */
  private ExpressionTree constructedExpression(ExpressionTree expr, TreePath path) {
    ExpressionTree current = expr;
    // Bounded by the nesting depth of the expression, which is finite.
    while (current instanceof MethodInvocationTree || current instanceof NewClassTree) {
      ExpressionTree forwarded = passedThroughArgument(current, path);
      if (forwarded == null) {
        return current; // this call is what produced the value
      }
      current = forwarded;
    }
    return null; // a name, a field read, a literal: nothing was constructed here
  }

  /**
   * For a call that returns one of its own arguments, the argument it returns; null for any other
   * call. {@code <T> T requireNonNull(T, String)} is the archetype: the return type is a type
   * variable of the method that also types a parameter, so the result is necessarily that argument.
   *
   * <p>When several parameters share the returned type variable the first is followed. Guards take
   * one value, so the distinction has no practical instance; a wrong guess here can only make the
   * check quieter, never noisier.
   */
  private ExpressionTree passedThroughArgument(ExpressionTree expr, TreePath path) {
    if (!(expr instanceof MethodInvocationTree invocation)) {
      return null;
    }
    Element invoked;
    try {
      invoked = trees.getElement(new TreePath(path, expr));
    } catch (RuntimeException e) {
      return null;
    }
    if (!(invoked instanceof ExecutableElement method)
        || !(method.getReturnType() instanceof TypeVariable returned)) {
      return null;
    }
    Element returnedDeclaration = returned.asElement();
    List<? extends VariableElement> parameters = method.getParameters();
    List<? extends ExpressionTree> arguments = invocation.getArguments();
    int positions = Math.min(parameters.size(), arguments.size());
    for (int i = 0; i < positions; i++) {
      if (parameters.get(i).asType() instanceof TypeVariable given
          && given.asElement().equals(returnedDeclaration)) {
        return arguments.get(i);
      }
    }
    return null;
  }

  private boolean isDeferred(ExpressionTree expr, TreePath path) {
    TypeMirror t;
    try {
      t = trees.getTypeMirror(new TreePath(path, expr));
    } catch (RuntimeException e) {
      return false; // cannot resolve: skip silently (no false positives)
    }
    if (t == null
        || t.getKind() == TypeKind.VOID
        || t.getKind() == TypeKind.NONE
        || t.getKind() == TypeKind.ERROR
        || t.getKind().isPrimitive()) {
      return false;
    }
    TypeElement deferred = elements.getTypeElement(PathTypeRegistry.DEFERRED_FQN);
    if (deferred == null) {
      return false; // the effect API is not on this compilation's classpath
    }
    return types.isAssignable(types.erasure(t), types.erasure(deferred.asType()));
  }

  private String simpleName(ExpressionTree expr, TreePath path) {
    TypeMirror t;
    try {
      t = trees.getTypeMirror(new TreePath(path, expr));
    } catch (RuntimeException e) {
      return "effect";
    }
    TypeMirror erased = types.erasure(t);
    if (erased instanceof DeclaredType dt) {
      return dt.asElement().getSimpleName().toString();
    }
    return "effect";
  }
}
