// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Explains an error-type mismatch in an error-typed Path chain ({@code EitherPath}, {@code
 * ValidationPath}, {@code VResultPath}, {@code EitherOrBothPath}) by naming the fix.
 *
 * <p>A step passed to {@code via}, {@code flatMap}, {@code then}, {@code recoverWith}, {@code
 * orElse}, {@code zipWith} or {@code zipWith3} must fail with the chain's error type or a subtype
 * of it, and javac rejects one that does not. Its message names the capability types and often
 * points at the step rather than where the chain was typed, so this check adds a line beside it
 * that says which of three fixes applies:
 *
 * <ul>
 *   <li>The two errors share a supertype: the chain is typed too narrowly, often by a {@code
 *       mapError(Variant::new)} that opens an inner chain. The note names the supertype and the
 *       witness to write where the chain is typed, such as {@code .<OrderError>mapError(...)}.
 *   <li>The step is typed on {@code Object} or a similar catch-all: nothing gave its chain an error
 *       type. The note names the witness to write where the step's chain starts.
 *   <li>The errors are unrelated: the step's error needs mapping into the chain's.
 * </ul>
 *
 * <h2>Rule</h2>
 *
 * <p>The receiver and the step are the <em>same</em> error-typed Path, both error types are
 * concrete {@link TypeKind#DECLARED} types, and the step's is <b>not assignable</b> to the
 * receiver's. javac attributes a rejected step against the type it expected, so when the step's
 * attributed type has lost its error type, the check reads it from the source: the declared return
 * type of the method it calls or references, or, for a generic factory such as {@code
 * toEitherPath(PaymentFailed::new)} or {@code Path.left(new NotFound(id))}, the argument that fixes
 * the factory's error type. Type variables, wildcards and anything else it cannot resolve are
 * skipped, preserving the no-false-positives policy.
 */
public final class ErrorTypeMismatchChecker implements CheckVisitor {

  /** Methods whose first argument is a function or supplier returning the step. */
  private static final Set<String> LAMBDA_METHODS =
      Set.of("via", "flatMap", "then", "recoverWith", "orElse");

  /** Methods whose first argument, and for the three-way forms also the second, is a step. */
  private static final Set<String> ARGUMENT_METHODS =
      Set.of("zipWith", "zipWith3", "zipWithAccum", "zipWith3Accum", "andAlso", "andThen");

  private static final Set<String> THREE_WAY_METHODS = Set.of("zipWith3", "zipWith3Accum");

  private static final String PATH = "org.higherkindedj.hkt.effect.Path";
  private static final String VALIDATION_PATH = "org.higherkindedj.hkt.effect.ValidationPath";
  private static final String EITHER_OR_BOTH_PATH = "org.higherkindedj.hkt.effect.EitherOrBothPath";

  private static final Set<String> ERROR_TYPED =
      Set.of(
          "org.higherkindedj.hkt.effect.EitherPath",
          VALIDATION_PATH,
          "org.higherkindedj.hkt.effect.VResultPath",
          EITHER_OR_BOTH_PATH);

  /** The error-typed Paths whose error type changes only with a semigroup for the new type. */
  private static final Set<String> ACCUMULATING = Set.of(VALIDATION_PATH, EITHER_OR_BOTH_PATH);

  /** Supertypes too general to suggest as a chain's error type. */
  private static final Set<String> TOO_GENERAL =
      Set.of(
          "java.lang.Object",
          "java.lang.Record",
          "java.lang.Enum",
          "java.io.Serializable",
          "java.lang.Comparable",
          "java.lang.constant.Constable",
          "java.lang.constant.ConstantDesc");

  private final Trees trees;
  private final Types types;
  private final Diagnostic.Kind severity;

  /**
   * Creates a checker reporting at {@link Diagnostic.Kind#WARNING}, its built-in default.
   *
   * @param trees the {@link Trees} utility for AST and type resolution
   * @param types the {@link Types} utility for type operations
   */
  public ErrorTypeMismatchChecker(Trees trees, Types types) {
    this(trees, types, Diagnostic.Kind.WARNING);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param types the model Types utility from the javac task
   * @param severity the severity at which the diagnostic is reported
   */
  public ErrorTypeMismatchChecker(Trees trees, Types types, Diagnostic.Kind severity) {
    this.trees = trees;
    this.types = types;
    this.severity = severity;
  }

  @Override
  public void onMethodInvocation(MethodInvocationTree node, TreePath path) {
    if (!(node.getMethodSelect() instanceof MemberSelectTree select)
        || node.getArguments().isEmpty()) {
      return;
    }
    String method = select.getIdentifier().toString();
    List<TreePath> steps = steps(method, node, path);
    if (steps.isEmpty()) {
      return;
    }

    TreePath receiverPath = new TreePath(path, select.getExpression());
    TypeMirror receiver = typeOf(receiverPath);
    String pathType = declaredFqn(receiver);
    if (pathType == null || !ERROR_TYPED.contains(pathType)) {
      return; // not an error-typed Path receiver
    }
    TypeMirror receiverE = firstTypeArg(receiver);
    if (!concrete(receiverE)) {
      return; // receiver error type is a type variable / wildcard: no false positives
    }

    // A lambda may mix a correct and an incorrect return; explain the first that breaks the rule.
    for (TreePath step : branches(steps)) {
      TypeMirror stepE = stepErrorType(step, pathType);
      if (concrete(stepE) && !types.isAssignable(stepE, receiverE)) {
        trees.printMessage(
            severity,
            message(method, pathType, receiverPath, receiverE, step, stepE),
            node,
            path.getCompilationUnit());
        return;
      }
    }
  }

  /** The steps a call passes: what its function returns, or its path arguments. */
  private static List<TreePath> steps(String method, MethodInvocationTree node, TreePath path) {
    List<? extends ExpressionTree> args = node.getArguments();
    ExpressionTree first = args.getFirst();
    if (LAMBDA_METHODS.contains(method)) {
      return first instanceof MemberReferenceTree
          ? List.of(new TreePath(path, first))
          : LambdaReturns.lambdaReturnPaths(path, first);
    }
    List<TreePath> steps = new ArrayList<>();
    if (ARGUMENT_METHODS.contains(method)) {
      steps.add(new TreePath(path, first));
    }
    if (THREE_WAY_METHODS.contains(method) && args.size() > 2) {
      steps.add(new TreePath(path, args.get(1)));
    }
    return steps;
  }

  /** Each step, with a conditional expression replaced by its two branches. */
  private static List<TreePath> branches(List<TreePath> steps) {
    List<TreePath> result = new ArrayList<>();
    Deque<TreePath> pending = new ArrayDeque<>(steps);
    while (!pending.isEmpty()) {
      TreePath step = pending.removeFirst();
      switch (step.getLeaf()) {
        case ConditionalExpressionTree conditional -> {
          pending.addFirst(new TreePath(step, conditional.getFalseExpression()));
          pending.addFirst(new TreePath(step, conditional.getTrueExpression()));
        }
        case ParenthesizedTree parenthesized ->
            pending.addFirst(new TreePath(step, parenthesized.getExpression()));
        default -> result.add(step);
      }
    }
    return result;
  }

  /**
   * The error type of a step on {@code pathType}: its attributed type's, or, where javac attributed
   * it against the type it expected, the one its source names.
   */
  private TypeMirror stepErrorType(TreePath step, String pathType) {
    TypeMirror attributed = typeOf(step);
    if (pathType.equals(declaredFqn(attributed)) && concrete(firstTypeArg(attributed))) {
      return firstTypeArg(attributed);
    }
    if (!(elementOf(step) instanceof ExecutableElement method)
        || !pathType.equals(declaredFqn(method.getReturnType()))) {
      return null;
    }
    TypeMirror declared = firstTypeArg(method.getReturnType());
    if (concrete(declared)) {
      return declared;
    }
    return step.getLeaf() instanceof MethodInvocationTree call
            && declared instanceof TypeVariable variable
        ? errorTypeFromArguments(step, call, method, variable)
        : null;
  }

  /**
   * The error type a generic factory's argument gives it, such as {@code PaymentFailed} for {@code
   * toEitherPath(PaymentFailed::new)} or {@code NotFound} for {@code Path.left(new NotFound(id))}.
   */
  private TypeMirror errorTypeFromArguments(
      TreePath step, MethodInvocationTree call, ExecutableElement method, TypeVariable error) {
    int count = Math.min(method.getParameters().size(), call.getArguments().size());
    for (int i = 0; i < count; i++) {
      TypeMirror parameter = method.getParameters().get(i).asType();
      TreePath argument = new TreePath(step, call.getArguments().get(i));
      TypeMirror found =
          isVariable(parameter, error)
              ? typeOf(argument)
              : producesVariable(parameter, error) ? producedType(argument) : null;
      if (concrete(found)) {
        return found;
      }
    }
    return null;
  }

  /** Whether a {@code java.util.function} parameter type produces {@code error}. */
  private static boolean producesVariable(TypeMirror parameter, TypeVariable error) {
    if (!(parameter instanceof DeclaredType function)
        || !(function.asElement() instanceof TypeElement element)
        || !element.getQualifiedName().toString().startsWith("java.util.function.")
        || function.getTypeArguments().isEmpty()) {
      return false;
    }
    TypeMirror result = function.getTypeArguments().getLast();
    return isVariable(result, error)
        || (result instanceof WildcardType wildcard
            && isVariable(wildcard.getExtendsBound(), error));
  }

  /** The type a function argument produces: a constructor's class, or a method's return type. */
  private TypeMirror producedType(TreePath argument) {
    if (argument.getLeaf() instanceof LambdaExpressionTree lambda
        && lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION) {
      return typeOf(new TreePath(argument, lambda.getBody()));
    }
    if (argument.getLeaf() instanceof MemberReferenceTree
        && elementOf(argument) instanceof ExecutableElement target) {
      return target.getKind() == ElementKind.CONSTRUCTOR
          ? target.getEnclosingElement().asType()
          : target.getReturnType();
    }
    return null;
  }

  private String message(
      String method,
      String pathType,
      TreePath receiver,
      TypeMirror receiverE,
      TreePath step,
      TypeMirror stepE) {
    TypeMirror common = commonSupertype(receiverE, stepE);
    if (common != null) {
      TreePath site = typingSite(receiver, pathType, receiverE);
      return DiagnosticMessages.errorTypeNarrowChain(
          method,
          display(receiverE),
          display(stepE),
          display(common),
          witnessAt(site, pathType, common).orElseGet(() -> widening(site, pathType, common)));
    }
    if (tooGeneral(stepE)) {
      return DiagnosticMessages.errorTypeUntypedStep(
          method,
          display(stepE),
          display(receiverE),
          witnessAt(chainStart(step, pathType, stepE), pathType, receiverE).orElse(null));
    }
    return DiagnosticMessages.errorTypeUnrelatedStep(
        method,
        display(stepE),
        display(receiverE),
        ACCUMULATING.contains(pathType) ? ".mapErrorWith(...)" : ".mapError(...)");
  }

  /**
   * The most specific supertype of {@code chain} that {@code step} is assignable to, other than the
   * catch-alls in {@link #TOO_GENERAL}, or {@code null} when they share none.
   */
  private TypeMirror commonSupertype(TypeMirror chain, TypeMirror step) {
    Deque<TypeMirror> pending = new ArrayDeque<>(types.directSupertypes(chain));
    Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      TypeMirror candidate = pending.removeFirst();
      String name = declaredFqn(candidate);
      if (name == null || !seen.add(name)) {
        continue;
      }
      if (!TOO_GENERAL.contains(name) && types.isAssignable(step, candidate)) {
        return candidate;
      }
      pending.addAll(types.directSupertypes(candidate));
    }
    return null;
  }

  /**
   * Walks back along a chain to the call that gave it its error type: each earlier link that is the
   * same Path on the same error type is part of the same typed chain.
   */
  private TreePath typingSite(TreePath expression, String pathType, TypeMirror error) {
    TreePath site = expression;
    while (site.getLeaf() instanceof MethodInvocationTree call
        && call.getMethodSelect() instanceof MemberSelectTree select) {
      TreePath earlier = new TreePath(site, select.getExpression());
      TypeMirror type = typeOf(earlier);
      if (!pathType.equals(declaredFqn(type))
          || !concrete(firstTypeArg(type))
          || !types.isSameType(firstTypeArg(type), error)) {
        break;
      }
      site = earlier;
    }
    return site;
  }

  /**
   * Where a step's own chain starts: its typing site, followed through a local variable to the
   * variable's initialiser.
   */
  private TreePath chainStart(TreePath step, String pathType, TypeMirror error) {
    TreePath site = typingSite(step, pathType, error);
    if (site.getLeaf() instanceof IdentifierTree
        && elementOf(site) instanceof VariableElement variable
        && variable.getKind() == ElementKind.LOCAL_VARIABLE) {
      TreePath initialiser = initialiserOf(site, variable);
      if (initialiser != null) {
        return typingSite(initialiser, pathType, error);
      }
    }
    return site;
  }

  /** The initialiser of a local variable, found in the method or lambda that declares it. */
  private TreePath initialiserOf(TreePath use, VariableElement variable) {
    TreePath scope = use;
    while (scope != null
        && !(scope.getLeaf() instanceof MethodTree)
        && !(scope.getLeaf() instanceof ClassTree)) {
      scope = scope.getParentPath();
    }
    if (scope == null) {
      return null;
    }
    TreePath[] found = {null};
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitVariable(VariableTree declaration, Void unused) {
        if (declaration.getInitializer() != null
            && variable.equals(trees.getElement(getCurrentPath()))) {
          found[0] = new TreePath(getCurrentPath(), declaration.getInitializer());
        }
        return super.visitVariable(declaration, unused);
      }
    }.scan(scope, null);
    return found[0];
  }

  /**
   * The type witness to write on the call that gave a chain its error type, such as {@code
   * .<OrderError>mapError(...)} or {@code Path.<OrderError, User>right(...)}: present only where
   * the call is generic in the error type and no argument fixes it.
   */
  private Optional<String> witnessAt(TreePath site, String pathType, TypeMirror error) {
    if (!(site.getLeaf() instanceof MethodInvocationTree call)
        || !(elementOf(site) instanceof ExecutableElement method)
        || !pathType.equals(declaredFqn(method.getReturnType()))
        || !(method.getReturnType() instanceof DeclaredType returned)
        || returned.getTypeArguments().size() != 2
        || !(returned.getTypeArguments().getFirst() instanceof TypeVariable errorVariable)
        || !method.getTypeParameters().contains(errorVariable.asElement())
        || !method.getParameters().stream()
            .allMatch(parameter -> takesAnyError(parameter.asType(), errorVariable))) {
      return Optional.empty();
    }
    List<String> arguments = new ArrayList<>();
    for (TypeParameterElement parameter : method.getTypeParameters()) {
      if (parameter.equals(errorVariable.asElement())) {
        arguments.add(display(error));
      } else if (isVariable(returned.getTypeArguments().getLast(), parameter)
          && typeOf(site) instanceof DeclaredType inferred
          && inferred.getTypeArguments().size() == 2) {
        arguments.add(display(inferred.getTypeArguments().getLast()));
      } else {
        return Optional.empty();
      }
    }
    String witness = "<" + String.join(", ", arguments) + ">" + method.getSimpleName() + "(...)";
    return Optional.of(
        method.getModifiers().contains(Modifier.STATIC)
                && method.getEnclosingElement() instanceof TypeElement owner
            ? owner.getSimpleName() + "." + witness
            : "." + witness);
  }

  /**
   * Whether a parameter leaves the error type free for a witness: it does not name the type, takes
   * a value of it, or is a function producing one. A parameter such as {@code Semigroup<E>} or
   * {@code Either<E, A>} fixes it.
   */
  private static boolean takesAnyError(TypeMirror parameter, TypeVariable error) {
    return !mentions(parameter, error)
        || isVariable(parameter, error)
        || producesVariable(parameter, error);
  }

  private static boolean mentions(TypeMirror type, TypeVariable variable) {
    return switch (type) {
      case TypeVariable candidate -> candidate.asElement().equals(variable.asElement());
      case WildcardType wildcard ->
          (wildcard.getExtendsBound() != null && mentions(wildcard.getExtendsBound(), variable))
              || (wildcard.getSuperBound() != null && mentions(wildcard.getSuperBound(), variable));
      case DeclaredType declared ->
          declared.getTypeArguments().stream().anyMatch(argument -> mentions(argument, variable));
      default -> false;
    };
  }

  /**
   * The widening to write after a chain typed on a subtype of {@code error}: {@code mapError} with
   * a witness, or {@code mapErrorWith} with a semigroup for the wider type.
   */
  private String widening(TreePath site, String pathType, TypeMirror error) {
    return ACCUMULATING.contains(pathType)
        ? describe(site)
            + ".mapErrorWith(e -> e, semigroup), with a semigroup for "
            + display(error)
        : describe(site) + ".<" + display(error) + ">mapError(e -> e)";
  }

  /** A short name for the expression a fix is written after, such as {@code Path.either(...)}. */
  private String describe(TreePath site) {
    return switch (site.getLeaf()) {
      case IdentifierTree identifier -> identifier.getName().toString();
      case MethodInvocationTree call
          when call.getMethodSelect() instanceof MemberSelectTree select ->
          (elementOf(new TreePath(site, select.getExpression())) instanceof TypeElement owner
                  ? owner.getSimpleName() + "."
                  : "")
              + select.getIdentifier()
              + "(...)";
      case MethodInvocationTree call -> call.getMethodSelect() + "(...)";
      default -> "the path";
    };
  }

  private TypeMirror typeOf(TreePath path) {
    return LambdaReturns.typeOf(trees, path.getParentPath(), path.getLeaf());
  }

  private Element elementOf(TreePath path) {
    try {
      return trees.getElement(path);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isVariable(TypeMirror type, TypeVariable variable) {
    return isVariable(type, variable.asElement());
  }

  private static boolean isVariable(TypeMirror type, Element variable) {
    return type instanceof TypeVariable candidate && candidate.asElement().equals(variable);
  }

  private static boolean tooGeneral(TypeMirror type) {
    String name = declaredFqn(type);
    return name != null && TOO_GENERAL.contains(name);
  }

  private static boolean concrete(TypeMirror t) {
    return t != null && t.getKind() == TypeKind.DECLARED;
  }

  private static String declaredFqn(TypeMirror t) {
    return t instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te
        ? te.getQualifiedName().toString()
        : null;
  }

  private static TypeMirror firstTypeArg(TypeMirror t) {
    return t instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()
        ? dt.getTypeArguments().getFirst()
        : null;
  }

  /** A type as a user writes it: simple names, with its type arguments. */
  private static String display(TypeMirror t) {
    return switch (t) {
      case DeclaredType declared when declared.asElement() instanceof TypeElement element ->
          element.getSimpleName()
              + (declared.getTypeArguments().isEmpty()
                  ? ""
                  : declared.getTypeArguments().stream()
                      .map(ErrorTypeMismatchChecker::display)
                      .collect(Collectors.joining(", ", "<", ">")));
      case WildcardType wildcard when wildcard.getExtendsBound() != null ->
          "? extends " + display(wildcard.getExtendsBound());
      case WildcardType wildcard when wildcard.getSuperBound() != null ->
          "? super " + display(wildcard.getSuperBound());
      case WildcardType _ -> "?";
      default -> String.valueOf(t);
    };
  }
}
