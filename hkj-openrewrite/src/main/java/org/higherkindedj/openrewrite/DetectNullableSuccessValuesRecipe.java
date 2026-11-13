// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.CAPTURING_FACTORIES;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.DEFERRED_FACTORIES;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.EAGER_MAPPERS;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.SUCCESS_TYPES;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.holdsVoid;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.valueArguments;
import static org.higherkindedj.openrewrite.NullLiterals.isNullLiteral;
import static org.higherkindedj.openrewrite.SwapTryFoldToFoldFailureFirstRecipe.isSuccessFirstFold;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Recipe that marks a value that may reach a success as null, which 0.5.0 refuses, where no {@code
 * null} is written for {@link DetectNullSuccessValuesRecipe} to see.
 *
 * <h2>Detection Pattern</h2>
 *
 * <ul>
 *   <li>A {@code @Nullable} parameter that its method passes straight to a success factory, as
 *       {@code value} is in {@code static <T> Either<E, T> right(@Nullable T value) { return
 *       Either.right(value); }}. The factories are those {@link DetectNullSuccessValuesRecipe}
 *       reads. A method like it whose parameter is not {@code @Nullable} is left alone, as is one
 *       that compares the parameter with {@code null} first.
 *   <li>A {@code null} passed to a method of the run that hands that parameter to a success
 *       factory, {@code @Nullable} or not, in any file of the run. It throws where the method hands
 *       it on.
 *   <li>A call to a method that may return null, returned by a lambda given to {@code Try.of}, a
 *       deferred factory such as {@code VTask.of}, or the {@code map} of a {@code Try}, a lazy
 *       type, an {@code Either} or a {@code Path}; and a method reference to one given to the same.
 *       A null there gives a {@code Failure}, fails when the computation runs, or throws.
 *   <li>A null check on the value of a success step, in a chain that starts at one of those
 *       factories or {@code map}s: {@code value == null} or {@code Objects.isNull(value)} in the
 *       function given to {@code map}, {@code flatMap}, {@code via}, {@code peek} or {@code
 *       ifRight}, or as the success function of {@code fold} or {@code foldFailureFirst}. Also a
 *       method reference there to a method whose parameter is {@code @Nullable}. 0.5.0 never hands
 *       such a step a null, so a value that used to arrive as null now fails the chain first.
 * </ul>
 *
 * <p>Each mark sits on the value that may be null: the parameter, the call, the method reference or
 * the null check.
 *
 * <h2>Which {@code @Nullable} counts</h2>
 *
 * <p>Any annotation named {@code Nullable} counts, as it does for NullAway. A declaration
 * annotation, such as Spring Framework 6's {@code org.springframework.lang.Nullable}, is read from
 * any method, a library's included. A type-use annotation, such as JSpecify's, is read only from a
 * method declared in the sources the recipe runs over: OpenRewrite's type model does not carry one,
 * so the recipe reads it from the declaration. So a call into a library annotated with JSpecify,
 * such as the Spring Data of Spring Boot 4, is not marked. A null check after it, in the same
 * chain, is.
 *
 * <h2>Why most marks say "may"</h2>
 *
 * <p>A {@code @Nullable} method may never return null where it is called, and a null check may be
 * one no value ever reached. So these marks are heuristics, kept out of {@link
 * DetectNullSuccessValuesRecipe}, whose marks are certain, and a migration can leave this recipe
 * out. A {@code null} passed to a method that hands it to a success factory is certain, and its
 * mark says so.
 */
public class DetectNullableSuccessValuesRecipe
    extends ScanningRecipe<DetectNullableSuccessValuesRecipe.Declarations> {

  /** Creates a new instance of this recipe. */
  public DetectNullableSuccessValuesRecipe() {}

  /** The success steps whose function takes the success value as its first argument. */
  private static final Set<String> STEPS = Set.of("map", "flatMap", "via", "peek", "ifRight");

  /** The JDK's null checks that read as a comparison with null. */
  private static final List<MethodMatcher> NULL_CHECKS =
      List.of(
          new MethodMatcher("java.util.Objects isNull(..)"),
          new MethodMatcher("java.util.Objects nonNull(..)"));

  static final String NULLABLE_FORWARDED =
      "May be null, which %s refuses from 0.5.0: drop @Nullable, and pass Unit.INSTANCE or a"
          + " Maybe where there is no value";

  static final String NULL_FORWARDED =
      "Passes null on to %s, which now throws: pass Unit.INSTANCE for a step with nothing to"
          + " return";

  static final String NULL_TYPED_FORWARDED =
      "Passes null on to %s, which now throws: use a Maybe for a value that may be missing, or an"
          + " error that says why";

  static final String NULLABLE_SUPPLIER =
      "May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an"
          + " error that says why";

  static final String NULLABLE_DEFERRED =
      "May return null, which now fails when it runs: carry a missing value as a Maybe, or fail"
          + " with an error that says why";

  static final String NULLABLE_EAGER =
      "May return null, which now throws: carry a missing value as a Maybe";

  static final String NULL_CHECKED_STEP =
      "Never null from 0.5.0, so a missing value may now fail the chain before this check: carry it"
          + " as a Maybe from where it is read";

  static final String NULLABLE_STEP =
      "Accepts null, which a success never holds from 0.5.0, so a missing value may now fail the"
          + " chain before this step: carry it as a Maybe from where it is read";

  @Override
  public String getDisplayName() {
    return "Detect success values that may be null";
  }

  @Override
  public String getDescription() {
    return "Marks a @Nullable parameter a method passes on to a success factory, and a null passed"
        + " to such a method; a call to a method that may return null, returned to Try.of, a"
        + " deferred factory or a map; and a null check, or a @Nullable parameter, in a success"
        + " step that follows one of those. From 0.5.0 a success always holds a value, so such a"
        + " value now fails or throws where 0.4.x passed the null along.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "null");
  }

  @Override
  public Declarations getInitialValue(ExecutionContext ctx) {
    return new Declarations();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Declarations declarations) {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        if (md.getMethodType() != null && !md.isConstructor()) {
          declarations.add(
              md.getMethodType(), Declared.of(getCursor(), service(AnnotationService.class)));
        }
        return md;
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Declarations declarations) {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        Declared declared = Declared.of(getCursor(), service(AnnotationService.class));
        return md.withParameters(
            ListUtils.map(
                md.getParameters(),
                (index, parameter) ->
                    declared.nullableParameters().contains(index)
                            && declared.forwarded().containsKey(index)
                        ? SearchResult.found(
                            parameter,
                            NULLABLE_FORWARDED.formatted(declared.forwarded().get(index)))
                        : parameter));
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        JavaType.Method type = mi.getMethodType();
        if (type == null) {
          return mi;
        }
        Optional<String> handedTo = nullHandedOn(mi, declarations);
        if (handedTo.isPresent()) {
          return SearchResult.found(
              mi,
              (holdsVoid(type) ? NULL_FORWARDED : NULL_TYPED_FORWARDED).formatted(handedTo.get()));
        }
        J.MethodInvocation marked = mi;
        Optional<String> whenNull = nullResultMessage(marked);
        if (whenNull.isPresent()) {
          marked =
              marked.withArguments(
                  ListUtils.mapFirst(
                      marked.getArguments(),
                      function -> markNullableResult(function, whenNull.get(), declarations)));
        }
        int success = successFunction(marked);
        if (success >= 0 && startsAtALazyFactory(marked.getSelect())) {
          marked =
              marked.withArguments(
                  ListUtils.map(
                      marked.getArguments(),
                      (index, argument) ->
                          index == success ? markNullChecks(argument, declarations) : argument));
        }
        return marked;
      }
    };
  }

  /**
   * The factory a call hands a {@code null} on to, through a method of the run that passes the
   * parameter the {@code null} is given for straight to it.
   */
  private static Optional<String> nullHandedOn(J.MethodInvocation call, Declarations declarations) {
    List<Expression> args = call.getArguments();
    return declarations
        .of(call.getMethodType())
        .flatMap(
            declared ->
                declared.forwarded().entrySet().stream()
                    .filter(e -> e.getKey() < args.size() && isNullLiteral(args.get(e.getKey())))
                    .map(Map.Entry::getValue)
                    .findFirst());
  }

  /**
   * What a null result of the function given to a call does there: a capturing factory gives a
   * {@code Failure}, a deferred one fails when it runs, and an eager {@code map} throws.
   */
  private static Optional<String> nullResultMessage(J.MethodInvocation call) {
    if (CAPTURING_FACTORIES.stream().anyMatch(m -> m.matches(call))) {
      return Optional.of(NULLABLE_SUPPLIER);
    }
    if (DEFERRED_FACTORIES.stream().anyMatch(m -> m.matches(call))) {
      return Optional.of(NULLABLE_DEFERRED);
    }
    if (EAGER_MAPPERS.stream().anyMatch(m -> m.matches(call))) {
      return Optional.of(NULLABLE_EAGER);
    }
    return Optional.empty();
  }

  /**
   * The function, with a mark on each result read from a method that may return null: a method
   * reference to one, the lambda's body, or what each top-level {@code return} of its block
   * returns.
   */
  private static Expression markNullableResult(
      Expression function, String message, Declarations declarations) {
    return switch (function) {
      case J.MemberReference ref when declarations.mayReturnNull(ref.getMethodType()) ->
          SearchResult.found(ref, message);
      case J.Lambda lambda when lambda.getBody() instanceof Expression body ->
          readsNullable(body, declarations)
              ? lambda.withBody(SearchResult.found(body, message))
              : lambda;
      case J.Lambda lambda when lambda.getBody() instanceof J.Block block ->
          lambda.withBody(
              block.withStatements(
                  ListUtils.map(
                      block.getStatements(),
                      statement ->
                          statement instanceof J.Return ret
                                  && ret.getExpression() != null
                                  && readsNullable(ret.getExpression(), declarations)
                              ? ret.withExpression(SearchResult.found(ret.getExpression(), message))
                              : statement)));
      default -> function;
    };
  }

  /** Whether an expression is a call to a method that may return null, cast or in brackets. */
  private static boolean readsNullable(Expression expression, Declarations declarations) {
    return switch (expression) {
      case J.TypeCast cast -> readsNullable(cast.getExpression(), declarations);
      case J.Parentheses<?> brackets ->
          brackets.getTree() instanceof Expression inner && readsNullable(inner, declarations);
      case J.MethodInvocation call -> declarations.mayReturnNull(call.getMethodType());
      default -> false;
    };
  }

  /**
   * The argument that takes the success value of a step on a type whose success always holds one,
   * or -1 for any other call: the first, but the last for a {@code fold} or {@code
   * foldFailureFirst}, which take the failure first, unless the {@code fold} is 0.4.x's.
   */
  private static int successFunction(J.MethodInvocation call) {
    if (call.getSelect() == null
        || call.getSelect().getType() == null
        || !isSuccessType(call.getSelect().getType())) {
      return -1;
    }
    String name = call.getSimpleName();
    if (STEPS.contains(name)) {
      return 0;
    }
    if (name.equals("foldFailureFirst") || name.equals("fold")) {
      return isSuccessFirstFold(call) ? 0 : call.getArguments().size() - 1;
    }
    return -1;
  }

  private static boolean isSuccessType(JavaType type) {
    return SUCCESS_TYPES.stream().anyMatch(success -> TypeUtils.isAssignableTo(success, type));
  }

  /**
   * Whether a receiver is a chain that starts at, or passes through, a capturing or deferred
   * factory, or the {@code map} of a {@code Try} or a lazy type: where 0.4.x gave a success whose
   * value was null, and 0.5.0 gives a failure.
   */
  private static boolean startsAtALazyFactory(Expression receiver) {
    Expression link = unwrapped(receiver);
    while (link instanceof J.MethodInvocation call) {
      if (CAPTURING_FACTORIES.stream().anyMatch(m -> m.matches(call))
          || DEFERRED_FACTORIES.stream().anyMatch(m -> m.matches(call))) {
        return true;
      }
      link = unwrapped(call.getSelect());
    }
    return false;
  }

  /**
   * The step's function, with a mark on each null check of its value: in a lambda, each comparison
   * of its parameter with {@code null}; and a method reference to a method of the run whose
   * parameter is {@code @Nullable}.
   */
  private static Expression markNullChecks(Expression function, Declarations declarations) {
    return switch (function) {
      case J.MemberReference ref when declarations.acceptsNull(ref.getMethodType()) ->
          SearchResult.found(ref, NULLABLE_STEP);
      case J.Lambda lambda ->
          parameterName(lambda)
              .map(name -> lambda.withBody(markComparisonsWithNull(lambda.getBody(), name)))
              .orElse(lambda);
      default -> function;
    };
  }

  /** The name of a lambda's one parameter, unless it is unnamed or there are more. */
  private static Optional<String> parameterName(J.Lambda lambda) {
    List<J> parameters = lambda.getParameters().getParameters();
    if (parameters.size() != 1) {
      return Optional.empty();
    }
    String name =
        switch (parameters.getFirst()) {
          case J.VariableDeclarations declared ->
              declared.getVariables().getFirst().getSimpleName();
          case J.Identifier identifier -> identifier.getSimpleName();
          default -> "_";
        };
    return name.equals("_") ? Optional.empty() : Optional.of(name);
  }

  /**
   * The body, with a mark on each {@code name == null}, {@code name != null}, {@code
   * Objects.isNull(name)} and {@code Objects.nonNull(name)}. A nested lambda cannot reuse the name,
   * so each one found reads the step's value.
   */
  private static J markComparisonsWithNull(J body, String name) {
    return new JavaIsoVisitor<Integer>() {
      @Override
      public J.Binary visitBinary(J.Binary binary, Integer p) {
        J.Binary b = super.visitBinary(binary, p);
        return checkedForNull(b).filter(name::equals).isPresent()
            ? SearchResult.found(b, NULL_CHECKED_STEP)
            : b;
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
        J.MethodInvocation m = super.visitMethodInvocation(method, p);
        return checkedForNull(m).filter(name::equals).isPresent()
            ? SearchResult.found(m, NULL_CHECKED_STEP)
            : m;
      }
    }.visit(body, 0);
  }

  /** The variable a comparison with {@code null} reads, as {@code name == null} does. */
  private static Optional<String> checkedForNull(J.Binary comparison) {
    if (comparison.getOperator() != J.Binary.Type.Equal
        && comparison.getOperator() != J.Binary.Type.NotEqual) {
      return Optional.empty();
    }
    Expression other =
        isNullLiteral(comparison.getLeft())
            ? comparison.getRight()
            : isNullLiteral(comparison.getRight()) ? comparison.getLeft() : null;
    return variableName(other);
  }

  /** The variable a JDK null check reads, as {@code Objects.isNull(name)} does. */
  private static Optional<String> checkedForNull(J.MethodInvocation call) {
    return NULL_CHECKS.stream().anyMatch(check -> check.matches(call))
        ? variableName(call.getArguments().getFirst())
        : Optional.empty();
  }

  /** The name of the variable an expression is, in brackets or not. */
  private static Optional<String> variableName(Expression expression) {
    return unwrapped(expression) instanceof J.Identifier identifier
        ? Optional.of(identifier.getSimpleName())
        : Optional.empty();
  }

  /** The expression inside any brackets around it, or {@code null} for none. */
  private static Expression unwrapped(Expression expression) {
    Expression inner = expression;
    while (inner instanceof J.Parentheses<?> brackets
        && brackets.getTree() instanceof Expression bracketed) {
      inner = bracketed;
    }
    return inner;
  }

  /** Whether an annotation is named {@code Nullable}, whatever its package. */
  private static boolean isNullable(J.Annotation annotation) {
    return isNullable(annotation.getSimpleName());
  }

  /** Whether a simple or nested class name is {@code Nullable}'s, as in {@code Nullable}. */
  private static boolean isNullable(String className) {
    return className.equals("Nullable") || className.endsWith(".Nullable");
  }

  /**
   * What the run's sources declare about each method a call may reach, keyed by its declaring type,
   * name and number of parameters. The scan fills it, so that a call in one file reads a method
   * declared in another.
   */
  public static final class Declarations {

    private final Map<String, Declared> methods = new HashMap<>();

    private Declarations() {}

    private void add(JavaType.Method method, Declared declared) {
      methods.merge(key(method), declared, Declared::both);
    }

    private Optional<Declared> of(JavaType.Method method) {
      return method == null ? Optional.empty() : Optional.ofNullable(methods.get(key(method)));
    }

    /**
     * Whether a method may return null: it carries a declaration {@code @Nullable}, which the type
     * model keeps, or the run declares it with a {@code @Nullable} result, which it does not.
     */
    private boolean mayReturnNull(JavaType.Method method) {
      return method != null
          && (method.getAnnotations().stream().anyMatch(a -> isNullable(a.getClassName()))
              || of(method).map(Declared::returnsNullable).orElse(false));
    }

    /** Whether the run declares a method of one {@code @Nullable} parameter. */
    private boolean acceptsNull(JavaType.Method method) {
      return method != null
          && method.getParameterTypes().size() == 1
          && of(method).map(d -> d.nullableParameters().contains(0)).orElse(false);
    }

    private static String key(JavaType.Method method) {
      return method.getDeclaringType().getFullyQualifiedName()
          + '#'
          + method.getName()
          + '/'
          + method.getParameterTypes().size();
    }
  }

  /**
   * What one method's declaration says: whether it may return null, which of its parameters may be
   * null, and which it passes straight to a success factory, with that factory's name.
   */
  private record Declared(
      boolean returnsNullable, Set<Integer> nullableParameters, Map<Integer, String> forwarded) {

    /**
     * What the method declaration at a cursor says. A type-use annotation on its result or a
     * parameter's type counts, as one before its modifiers does.
     */
    static Declared of(Cursor declaration, AnnotationService annotations) {
      J.MethodDeclaration method = declaration.getValue();
      List<String> names =
          method.getParameters().stream()
              .map(
                  p ->
                      p instanceof J.VariableDeclarations parameter
                          ? parameter.getVariables().getFirst().getSimpleName()
                          : "")
              .toList();
      Set<Integer> nullable =
          IntStream.range(0, method.getParameters().size())
              .filter(
                  i ->
                      method.getParameters().get(i) instanceof J.VariableDeclarations parameter
                          && annotations
                              .getAllAnnotations(new Cursor(declaration, parameter))
                              .stream()
                              .anyMatch(DetectNullableSuccessValuesRecipe::isNullable))
              .boxed()
              .collect(Collectors.toUnmodifiableSet());
      Map<String, String> handedOn = handedOn(method.getBody(), Set.copyOf(names));
      handedOn.keySet().removeAll(checkedForNull(method.getBody()));
      Map<Integer, String> forwarded = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        if (handedOn.containsKey(names.get(i))) {
          forwarded.put(i, handedOn.get(names.get(i)));
        }
      }
      return new Declared(
          annotations.getAllAnnotations(declaration).stream()
              .anyMatch(DetectNullableSuccessValuesRecipe::isNullable),
          nullable,
          Map.copyOf(forwarded));
    }

    /**
     * What two overloads with the same name and number of parameters both say, since a call is
     * matched to a declaration by those alone.
     */
    Declared both(Declared other) {
      Map<Integer, String> forwardedByBoth = new LinkedHashMap<>(forwarded);
      forwardedByBoth.keySet().retainAll(other.forwarded.keySet());
      return new Declared(
          returnsNullable && other.returnsNullable,
          nullableParameters.stream()
              .filter(other.nullableParameters::contains)
              .collect(Collectors.toUnmodifiableSet()),
          Map.copyOf(forwardedByBoth));
    }

    /**
     * The variables a body compares with {@code null}. A parameter the method checks is not passed
     * on straight, even where the call that passes it is the same.
     */
    private static Set<String> checkedForNull(J.Block body) {
      Set<String> checked = new HashSet<>();
      if (body != null) {
        new JavaIsoVisitor<Set<String>>() {
          @Override
          public J.Binary visitBinary(J.Binary binary, Set<String> found) {
            DetectNullableSuccessValuesRecipe.checkedForNull(binary).ifPresent(found::add);
            return super.visitBinary(binary, found);
          }

          @Override
          public J.MethodInvocation visitMethodInvocation(
              J.MethodInvocation method, Set<String> found) {
            DetectNullableSuccessValuesRecipe.checkedForNull(method).ifPresent(found::add);
            return super.visitMethodInvocation(method, found);
          }
        }.visit(body, checked);
      }
      return checked;
    }

    /**
     * Each of the named parameters that a body passes straight to a success factory, cast or in
     * brackets, with the factory it is passed to.
     */
    private static Map<String, String> handedOn(J.Block body, Set<String> parameters) {
      Map<String, String> handed = new HashMap<>();
      if (body == null) {
        return handed;
      }
      new JavaIsoVisitor<Map<String, String>>() {
        @Override
        public J.MethodInvocation visitMethodInvocation(
            J.MethodInvocation method, Map<String, String> found) {
          for (String parameter : passed(valueArguments(method))) {
            found.putIfAbsent(parameter, factoryName(method.getMethodType()));
          }
          return super.visitMethodInvocation(method, found);
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, Map<String, String> found) {
          for (String parameter : passed(valueArguments(newClass))) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getType());
            found.putIfAbsent(
                parameter, type == null ? "a success constructor" : "new " + type.getClassName());
          }
          return super.visitNewClass(newClass, found);
        }

        /** The parameters among the values, each written as itself, cast or in brackets. */
        private List<String> passed(List<Expression> values) {
          return values.stream()
              .map(Declared::unwrappedCast)
              .filter(J.Identifier.class::isInstance)
              .map(value -> ((J.Identifier) value).getSimpleName())
              .filter(parameters::contains)
              .toList();
        }
      }.visit(body, handed);
      return handed;
    }

    private static Expression unwrappedCast(Expression expression) {
      return switch (expression) {
        case J.TypeCast cast -> unwrappedCast(cast.getExpression());
        case J.Parentheses<?> brackets when brackets.getTree() instanceof Expression inner ->
            unwrappedCast(inner);
        default -> expression;
      };
    }

    private static String factoryName(JavaType.Method method) {
      return method == null
          ? "a success factory"
          : method.getDeclaringType().getClassName() + "." + method.getName();
    }
  }
}
